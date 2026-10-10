package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.pgclient.PgException;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Coordinates independent database sessions to observe key/scope contention, exact replay identity and fresh locked state. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(120)
class PartyIdentifierLockCoordinationTest {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final Instant CREATED = LocalDate.of(2026, Month.OCTOBER, 4).atStartOfDay(ZoneOffset.UTC).toInstant();
    private static final int COLLISION = 7;
    private static final String TENANT = "tenant";
    private static final String IDENTIFIER = "identifier";

    @Inject
    Mutiny.SessionFactory sessions;
    @Inject
    PartyIdentifierReplayPersistence replay;
    @Inject
    PartyIdentifierMutationReader reader;

    @Test
    @RunOnVertxContext
    void waitingEquivalentKeyReadsTheCommittedOriginalCompletion(UniAsserter asserter) {
        Fixture fixture = fixture();
        var identity = key(fixture, "same-key");
        var completed = completed(fixture);
        var locks = collidingLocks();
        var held = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
        asserter.execute(() -> seed(fixture));
        asserter.execute(() -> {
            Uni<Void> winner = transaction(session -> locks.serializeReplayKey(session, identity)
                    .invoke(() -> held.complete(null)).chain(() -> stage(release))
                    .chain(() -> replay.findCompleted(session, identity)).invoke(result -> assertTrue(result.isEmpty()))
                    .chain(() -> reader.findOwnedParty(session, fixture.party().tenantId(), fixture.party().id()))
                    .invoke(owner -> assertEquals(PartyType.NATURAL_PERSON, owner.orElseThrow()))
                    .call(() -> locks.serializeScope(session, fixture.scope()))
                    .chain(() -> reader.findForUpdate(session, fixture.scope(), fixture.identifier().id()))
                    .invoke(target -> assertEquals(fixture.identifier().id(), target.orElseThrow().identifierId().value()))
                    .replaceWithVoid()
                    .call(() -> replay.recordCompletion(session, identity, completed)).call(session::flush));
            Uni<Void> contender = stage(held).chain(() -> transaction(session -> locks.serializeReplayKey(session, identity)
                    .chain(() -> replay.findCompleted(session, identity)).invoke(result -> assertEquals(completed, result.orElseThrow())).replaceWithVoid()));
            Uni<Void> monitor = stage(held).chain(() -> exclusiveOwnerProbe(fixture))
                    .chain(() -> awaitContender(PartyIdentifierMutationLocks.REPLAY_NAMESPACE)).invoke(() -> release.complete(null));
            return joined(winner, contender, monitor);
        });
    }

    @Test
    @RunOnVertxContext
    void forcedCollisionOnlySerializesAndOtherNamespaceRemainsIndependent(UniAsserter asserter) {
        Fixture fixture = fixture();
        var first = key(fixture, "Exact-key");
        var second = key(fixture, " Exact-key ");
        var locks = collidingLocks();
        var held = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
        asserter.execute(() -> seed(fixture));
        asserter.execute(() -> {
            Uni<Void> winner = transaction(session -> locks.serializeReplayKey(session, first).invoke(() -> held.complete(null))
                    .chain(() -> stage(release)).call(() -> replay.recordCompletion(session, first, completed(fixture))).call(session::flush));
            Uni<Void> contender = stage(held).chain(() -> transaction(session -> locks.serializeReplayKey(session, second)
                    .chain(() -> replay.findCompleted(session, second)).invoke(result -> assertTrue(result.isEmpty())).replaceWithVoid()));
            Uni<Void> monitor = stage(held).chain(() -> transaction(session -> locks.serializeScope(session, fixture.scope())))
                    .chain(() -> awaitContender(PartyIdentifierMutationLocks.REPLAY_NAMESPACE)).invoke(() -> release.complete(null));
            return joined(winner, contender, monitor);
        });
        asserter.assertThat(() -> transaction(session -> replay.findCompleted(session, first)), result -> assertEquals(completed(fixture), result.orElseThrow()));
    }

    @Test
    @RunOnVertxContext
    void scopeWaitRestoresCommittedStateInsteadOfPreliminaryVersion(UniAsserter asserter) {
        Fixture fixture = fixture();
        var scope = fixture.scope();
        var locks = collidingLocks();
        var held = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
        asserter.execute(() -> seed(fixture));
        asserter.execute(() -> {
            Uni<Void> winner = transaction(session -> reader.findOwnedParty(session, scope.tenantId(), scope.partyId())
                    .call(() -> locks.serializeScope(session, scope)).invoke(() -> held.complete(null)).chain(() -> stage(release))
                    .chain(() -> session.createNativeQuery("""
                            UPDATE party_identifiers SET issuer_code = 'NEW_AUTHORITY', version = 1, updated_at = :instant
                            WHERE tenant_id = :tenant AND party_id = :party AND id = :identifier AND version = 0
                            """).setParameter(TENANT, scope.tenantId()).setParameter("party", scope.partyId())
                            .setParameter(IDENTIFIER, fixture.identifier().id()).setParameter("instant", CREATED.plusSeconds(1)).executeUpdate())
                    .invoke(count -> assertEquals(1, count)).replaceWithVoid());
            Uni<Void> contender = stage(held).chain(() -> transaction(session -> reader.findOwnedParty(session, scope.tenantId(), scope.partyId())
                    .chain(() -> reader.findIdentifierSchemeId(session, scope.tenantId(), scope.partyId(), fixture.identifier().id()))
                    .invoke(scheme -> assertEquals(scope.schemeId(), scheme.orElseThrow().value()))
                    .call(() -> locks.serializeScope(session, scope)).chain(() -> reader.findForUpdate(session, scope, fixture.identifier().id()))
                    .invoke(result -> { assertEquals(1, result.orElseThrow().version().value()); assertEquals("NEW_AUTHORITY", result.orElseThrow().issuerCode()); })
                    .replaceWithVoid()));
            Uni<Void> monitor = stage(held).chain(() -> awaitContender(PartyIdentifierMutationLocks.SCOPE_NAMESPACE))
                    .chain(() -> exclusiveOwnerProbe(fixture).onItemOrFailure().transform((value, failure) -> { assertLockUnavailable(failure); return value; }))
                    .chain(() -> transaction(session -> session.createNativeQuery(
                            "SELECT id FROM party_identifiers WHERE tenant_id = :tenant AND id = :identifier FOR UPDATE NOWAIT", UUID.class)
                            .setParameter(TENANT, scope.tenantId()).setParameter(IDENTIFIER, fixture.identifier().id()).getSingleResult()))
                    .invoke(identifier -> assertEquals(fixture.identifier().id(), identifier)).invoke(() -> release.complete(null)).replaceWithVoid();
            return joined(winner, contender, monitor);
        });
        asserter.assertThat(() -> transaction(session -> session.createNativeQuery("SELECT version FROM parties WHERE id = :party", Long.class)
                .setParameter("party", scope.partyId()).getSingleResult()), version -> assertEquals(0L, version));
    }

    @Test
    @RunOnVertxContext
    void sharedOwnerLockPermitsOtherReadersAndBlocksExclusiveRootWriters(UniAsserter asserter) {
        Fixture fixture = fixture();
        var scope = fixture.scope();
        var held = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
        asserter.execute(() -> seed(fixture));
        asserter.execute(() -> {
            Uni<Void> holder = transaction(session -> reader.findOwnedParty(session, scope.tenantId(), scope.partyId())
                    .invoke(owner -> assertEquals(PartyType.NATURAL_PERSON, owner.orElseThrow()))
                    .invoke(() -> held.complete(null)).chain(() -> stage(release)));
            Uni<Void> probe = stage(held).chain(() -> transaction(session -> reader.findOwnedParty(session, scope.tenantId(), scope.partyId()))
                    .invoke(owner -> assertEquals(PartyType.NATURAL_PERSON, owner.orElseThrow())))
                    .chain(() -> exclusiveOwnerProbe(fixture))
                    .onItemOrFailure().transform((value, failure) -> { assertLockUnavailable(failure); return value; })
                    .invoke(() -> release.complete(null)).replaceWithVoid();
            return Uni.combine().all().unis(holder, probe).discardItems().ifNoItem().after(WAIT).fail();
        });
    }

    private Uni<UUID> exclusiveOwnerProbe(Fixture fixture) {
        return transaction(session -> session.createNativeQuery(
                "SELECT id FROM parties WHERE tenant_id = :tenant AND id = :party FOR UPDATE NOWAIT", UUID.class)
                .setParameter(TENANT, fixture.party().tenantId()).setParameter("party", fixture.party().id()).getSingleResult());
    }

    private static void assertLockUnavailable(Throwable failure) {
        assertNotNull(failure);
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof PgException postgres) { assertEquals("55P03", postgres.getSqlState()); return; }
            if (cause instanceof SQLException database) { assertEquals("55P03", database.getSQLState()); return; }
            cause = cause.getCause();
        }
        fail("Expected PostgreSQL lock-unavailable failure was not reported");
    }

    private Uni<Void> awaitContender(int namespace) {
        return sessions.openSession().flatMap(session -> Multi.createBy().repeating().uni(() -> session.createNativeQuery("""
                        SELECT count(*) FILTER (WHERE granted) = 1 AND count(*) FILTER (WHERE NOT granted) = 1
                        FROM pg_locks WHERE locktype = 'advisory' AND classid::bigint = :namespace AND objid::bigint = :identity AND objsubid = 2
                        """, Boolean.class).setParameter("namespace", namespace).setParameter("identity", COLLISION).getSingleResult()
                        .onItem().delayIt().by(Duration.ofMillis(5)))
                .until(Boolean.TRUE::equals).collect().last().ifNoItem().after(WAIT).fail().eventually(session::close)).replaceWithVoid();
    }

    private <T> Uni<T> transaction(Function<Mutiny.Session, Uni<T>> work) {
        return sessions.openSession().flatMap(session -> session.withTransaction(transaction ->
                        session.createNativeQuery("SET TRANSACTION ISOLATION LEVEL READ COMMITTED").executeUpdate()
                                .chain(() -> work.apply(session)).ifNoItem().after(WAIT).fail()).eventually(session::close));
    }

    private Uni<Void> seed(Fixture fixture) {
        return transaction(session -> session.persist(fixture.party()).call(() -> session.persist(fixture.identifier())).call(session::flush));
    }

    private static Uni<Void> stage(CompletableFuture<Void> signal) { return Uni.createFrom().completionStage(signal); }

    private static Uni<Void> joined(Uni<Void> winner, Uni<Void> contender, Uni<Void> monitor) {
        return Uni.combine().all().unis(winner, contender, monitor).discardItems().ifNoItem().after(WAIT).fail();
    }

    private static PartyIdentifierMutationLocks collidingLocks() { return new PartyIdentifierMutationLocks(ignored -> COLLISION, ignored -> COLLISION); }

    private static PartyIdentifierIdempotencyRecordId key(Fixture fixture, String key) {
        return new PartyIdentifierIdempotencyRecordId(fixture.party().tenantId(), PartyIdentifierOperation.CREATE.namespace(), key);
    }

    private static CompletedPartyIdentifierMutation completed(Fixture fixture) {
        var identifier = fixture.identifier();
        var result = new PartyIdentifierResult(new PartyIdentifierId(identifier.id()), new PartyId(identifier.partyId()),
                new IdentifierSchemeId(identifier.identifierSchemeId()), IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE, identifier.maskedValue(),
                identifier.status(), false, null, null, null, null, null, new PartyIdentifierVersion(0), CREATED, CREATED);
        return new CompletedPartyIdentifierMutation(new TenantId(identifier.tenantId()), PartyIdentifierOperation.CREATE, "a".repeat(64), result, null, "original-owner", CREATED);
    }

    private static Fixture fixture() {
        var audit = AuditInfo.initial(CREATED, "original-owner");
        var party = new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.NATURAL_PERSON, "Coordinated owner", PartyRecordStatus.DRAFT, audit, 0);
        party.attachNaturalPersonDetails(new NaturalPersonDetailsEntity(party.id(), new NaturalPersonDetails("Coordinated", "Owner", null, null, null, null), audit));
        var identifier = PartyIdentifierEntity.builder().id(UUID.randomUUID()).tenantId(party.tenantId()).partyId(party.id())
                .identifierSchemeId(IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID).issuerCode(null).encryptedValue("synthetic-coordination-ciphertext")
                .encryptionKeyVersion((short) 1).normalizedValueHash(UUID.randomUUID().toString().replace("-", "").repeat(2)).maskedValue("***1234")
                .normalizationVersion((short) 1).status(PartyIdentifierStatus.PENDING_VERIFICATION).primary(false).issuedOn(null).expiresOn(null)
                .verifiedAt(null).verifiedBy(null).createdAt(CREATED).createdBy("original-owner").updatedAt(CREATED).updatedBy("original-owner").version(0).build();
        return new Fixture(party, identifier);
    }

    /** Retains one qualified persisted owner/child pair for independent-transaction coordination. */
    private record Fixture(PartyEntity party, PartyIdentifierEntity identifier) {
        PartyIdentifierMutationLocks.Scope scope() { return new PartyIdentifierMutationLocks.Scope(party.tenantId(), party.id(), identifier.identifierSchemeId()); }
    }
}
