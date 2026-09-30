package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyLifecycle;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleRequest;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.infrastructure.observability.PartyPersistenceObservability;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies durable full-scope replay storage and real transaction-scoped locking across independent PostgreSQL sessions. */
@QuarkusTest
@Timeout(60)
class PartyLifecycleIdempotencyPersistenceTest {

    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String KEY = "exact-key";

    @Inject
    Mutiny.SessionFactory sessionFactory;
    @Inject
    PartyLifecycleIdempotencyPersistence persistence;
    @Inject
    PartyLifecycleSnapshotCodec codec;
    @Inject
    NaturalPersonPersistenceMapper mapper;
    @Inject
    PartyPersistenceObservability observations;

    @Test
    @RunOnVertxContext
    void equalScopesWaitForTheWinnerAndReadItsCommittedOriginalResult(UniAsserter asserter) {
        var scope = new Scope(new TenantId(UUID.randomUUID()), PartyLifecycleAction.ARCHIVE, KEY);
        NaturalPerson original = fixture(scope);
        CompletedPartyLifecycle completed = completed(original, scope.action());
        var held = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
        long lockId = lockId(scope);
        asserter.execute(() -> seed(original));
        asserter.assertThat(() -> {
            Uni<Void> winner = transaction(session -> lock(persistence, session, scope)
                    .chain(() -> persistence.recordCompletion(session, scope.tenant(), scope.action(), scope.key(), completed))
                    .call(session::flush).invoke(() -> held.complete(null))
                    .chain(() -> Uni.createFrom().completionStage(release)));
            Uni<Optional<CompletedPartyLifecycle>> contender = Uni.createFrom().completionStage(held)
                    .chain(() -> transaction(session -> lock(persistence, session, scope)
                            .chain(() -> persistence.findCompleted(session, scope.tenant(), scope.action(), scope.key()))));
            Uni<Void> coordinator = Uni.createFrom().completionStage(held)
                    .chain(() -> awaitContender(lockId)).invoke(() -> release.complete(null));
            return Uni.combine().all().unis(winner, contender, coordinator).asTuple()
                    .map(tuple -> tuple.getItem2().orElseThrow())
                    .eventually(() -> release.complete(null));
        }, result -> assertEquals(completed, result));
        asserter.assertThat(() -> count(scope.tenant()), total -> assertEquals(1, total));
        asserter.assertThat(() -> transaction(session -> session.createNativeQuery(
                        "select pg_try_advisory_xact_lock(:lockId)", Boolean.class)
                .setParameter("lockId", lockId).getSingleResult()), acquired -> assertEquals(Boolean.TRUE, acquired));
    }

    @Test
    @RunOnVertxContext
    void unrelatedScopesCanAcquireTheirOwnLocksWhileTheFirstScopeIsHeld(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        var first = new Scope(tenant, PartyLifecycleAction.ACTIVATE, KEY);
        List<Scope> others = List.of(new Scope(tenant, PartyLifecycleAction.DEACTIVATE, KEY),
                new Scope(new TenantId(UUID.randomUUID()), first.action(), KEY),
                new Scope(tenant, first.action(), " " + KEY + " "), new Scope(tenant, first.action(), "EXACT-KEY"));
        asserter.execute(() -> transaction(firstSession -> lock(persistence, firstSession, first)
                .chain(() -> transaction(secondSession -> Multi.createFrom().iterable(others)
                        .onItem().transformToUniAndConcatenate(scope -> {
                            assertNotEquals(lockId(first), lockId(scope));
                            return secondSession.createNativeQuery("select pg_try_advisory_xact_lock(:lockId)", Boolean.class)
                                    .setParameter("lockId", lockId(scope)).getSingleResult()
                                    .invoke(acquired -> assertEquals(Boolean.TRUE, acquired));
                        }).collect().asList().invoke(results -> assertEquals(others.size(), results.size()))))));
    }

    @Test
    @RunOnVertxContext
    void forcedLockCollisionsNeverMergeTenantActionOrExactKeyScopes(UniAsserter asserter) {
        var colliding = new PartyLifecycleIdempotencyPersistence(codec, ignored -> 7L, observations);
        var tenant = new TenantId(UUID.randomUUID());
        List<Scope> scopes = List.of(new Scope(tenant, PartyLifecycleAction.ACTIVATE, KEY),
                new Scope(tenant, PartyLifecycleAction.DEACTIVATE, KEY), new Scope(tenant, PartyLifecycleAction.ARCHIVE, KEY),
                new Scope(new TenantId(UUID.randomUUID()), PartyLifecycleAction.ACTIVATE, KEY),
                new Scope(tenant, PartyLifecycleAction.ACTIVATE, " " + KEY + " "),
                new Scope(tenant, PartyLifecycleAction.ACTIVATE, "EXACT-KEY"),
                new Scope(tenant, PartyLifecycleAction.ARCHIVE, "🙂".repeat(128)));
        for (Scope scope : scopes) {
            NaturalPerson original = fixture(scope);
            CompletedPartyLifecycle accepted = completed(original, scope.action());
            asserter.execute(() -> seed(original));
            asserter.execute(() -> transaction(session -> lock(colliding, session, scope)
                    .chain(() -> colliding.findCompleted(session, scope.tenant(), scope.action(), scope.key()))
                    .invoke(result -> assertTrue(result.isEmpty()))
                    .chain(() -> colliding.recordCompletion(session, scope.tenant(), scope.action(), scope.key(), accepted))));
            asserter.putData(scope.toString(), accepted);
        }
        for (Scope scope : scopes) {
            asserter.assertThat(() -> read(colliding, scope), result ->
                    assertEquals(asserter.getData(scope.toString()), result.orElseThrow()));
        }
        asserter.assertThat(() -> count(tenant), total -> assertEquals(6, total));
    }

    @Test
    @RunOnVertxContext
    void existingPrimaryKeyRejectsAnotherCompletionWithoutReplacingTheSavedResult(UniAsserter asserter) {
        var scope = new Scope(new TenantId(UUID.randomUUID()), PartyLifecycleAction.ARCHIVE, KEY);
        NaturalPerson first = fixture(scope);
        NaturalPerson second = fixture(scope);
        CompletedPartyLifecycle accepted = completed(first, scope.action());
        CompletedPartyLifecycle conflicting = completed(second, scope.action());
        asserter.execute(() -> seed(first));
        asserter.execute(() -> seed(second));
        asserter.execute(() -> transaction(session -> persistence.recordCompletion(
                session, scope.tenant(), scope.action(), scope.key(), accepted)));
        asserter.assertFailedWith(() -> transaction(session -> persistence.recordCompletion(
                session, scope.tenant(), scope.action(), scope.key(), conflicting)), failure ->
                assertTrue(PersistenceExceptionTranslator.isConstraint(failure, "23505", "pk_api_idempotency_records")));
        asserter.assertThat(() -> read(persistence, scope), result -> assertEquals(accepted, result.orElseThrow()));
        asserter.assertThat(() -> count(scope.tenant()), total -> assertEquals(1, total));
    }

    @Test
    @RunOnVertxContext
    void rollbackReleasesTheScopeAndLeavesItsKeyReusable(UniAsserter asserter) {
        var scope = new Scope(new TenantId(UUID.randomUUID()), PartyLifecycleAction.ARCHIVE, KEY);
        NaturalPerson original = fixture(scope);
        CompletedPartyLifecycle accepted = completed(original, scope.action());
        var rejected = new IllegalStateException("Controlled rollback after snapshot flush");
        asserter.execute(() -> seed(original));
        asserter.assertFailedWith(() -> transaction(session -> lock(persistence, session, scope)
                .chain(() -> persistence.recordCompletion(session, scope.tenant(), scope.action(), scope.key(), accepted))
                .call(session::flush).chain(() -> Uni.createFrom().failure(rejected))), failure -> assertEquals(rejected, failure));
        asserter.assertThat(() -> read(persistence, scope), result -> assertTrue(result.isEmpty()));
        asserter.execute(() -> transaction(session -> lock(persistence, session, scope)
                .chain(() -> persistence.recordCompletion(session, scope.tenant(), scope.action(), scope.key(), accepted))));
        asserter.assertThat(() -> read(persistence, scope), result -> assertEquals(accepted, result.orElseThrow()));
    }

    private Uni<Void> awaitContender(long lockId) {
        return sessionFactory.openSession().flatMap(session -> Multi.createBy().repeating().uni(() ->
                        session.createNativeQuery("""
                                select count(*) filter (where granted) = 1 and count(*) filter (where not granted) = 1
                                from pg_locks where locktype = 'advisory' and classid::bigint = :upper
                                    and objid::bigint = :lower and objsubid = 1
                                """, Boolean.class)
                                .setParameter("upper", lockId >>> 32).setParameter("lower", lockId & 0xffffffffL)
                                .getSingleResult().onItem().delayIt().by(Duration.ofMillis(5)))
                .until(Boolean.TRUE::equals).collect().last().ifNoItem().after(TIMEOUT).fail()
                .eventually(session::close)).replaceWithVoid();
    }

    private Uni<Optional<CompletedPartyLifecycle>> read(PartyLifecycleIdempotencyPersistence helper, Scope scope) {
        return transaction(session -> lock(helper, session, scope)
                .chain(() -> helper.findCompleted(session, scope.tenant(), scope.action(), scope.key())));
    }

    private Uni<Long> count(TenantId tenant) {
        return transaction(session -> session.createSelectionQuery(
                        "select count(record) from ApiIdempotencyRecordEntity record where record.id.tenantId = :tenant", Long.class)
                .setParameter("tenant", tenant.value()).getSingleResult());
    }

    private Uni<Void> seed(NaturalPerson party) {
        return transaction(session -> session.persist(mapper.toEntity(party)));
    }

    private <T> Uni<T> transaction(Function<Mutiny.Session, Uni<T>> work) {
        return sessionFactory.openSession().flatMap(session -> session.withTransaction(transaction ->
                        session.createNativeQuery("SET TRANSACTION ISOLATION LEVEL READ COMMITTED").executeUpdate()
                                .chain(() -> work.apply(session)).ifNoItem().after(TIMEOUT).fail())
                .eventually(session::close));
    }

    private static Uni<Void> lock(PartyLifecycleIdempotencyPersistence helper, Mutiny.Session session, Scope scope) {
        return helper.serializeReplayKey(session, scope.tenant(), scope.action(), scope.key());
    }

    private static long lockId(Scope scope) {
        return PartyLifecycleIdempotencyPersistence.lockId(PartyLifecycleIdempotencyPersistence.id(
                scope.tenant(), scope.action(), scope.key()));
    }

    private static NaturalPerson fixture(Scope scope) {
        return NaturalPerson.restore(new PartyId(UUID.randomUUID()), scope.tenant(), "Original Historical Label",
                scope.action() == PartyLifecycleAction.DEACTIVATE ? PartyRecordStatus.ACTIVE : PartyRecordStatus.DRAFT,
                PartyVersion.initial(), AuditInfo.initial(CLOCK.instant(), "original-creator"),
                new NaturalPersonDetails("Original", "Person", null, LocalDate.of(1990, Month.JANUARY, 1), null, "GB"));
    }

    private static CompletedPartyLifecycle completed(NaturalPerson original, PartyLifecycleAction action) {
        Party result = switch (action) {
            case ACTIVATE -> original.activate(CLOCK.instant().plusSeconds(1), "original-updater");
            case DEACTIVATE -> original.deactivate(CLOCK.instant().plusSeconds(1), "original-updater");
            case ARCHIVE -> original.archive(CLOCK.instant().plusSeconds(1), "original-updater");
        };
        return new CompletedPartyLifecycle(new PartyLifecycleRequest(action, original.partyId(), original.version()),
                PartyDetailsResult.fromAggregate(result));
    }

    /** Keeps every component of the replay scope explicit in concurrency and collision fixtures. */
    private record Scope(TenantId tenant, PartyLifecycleAction action, String key) {
    }
}
