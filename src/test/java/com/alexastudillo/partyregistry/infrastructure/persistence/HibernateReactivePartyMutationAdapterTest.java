package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedPartyLifecycle;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleRequest;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyMutationPort;
import com.alexastudillo.partyregistry.application.command.ChangePartyLifecycleCommand;
import com.alexastudillo.partyregistry.application.command.PatchPartyCommand;
import com.alexastudillo.partyregistry.application.usecase.ChangePartyLifecycleUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchPartyUseCase;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies complete mutation commit/rollback, scope lifetime/order, replay visibility, and timeout/cancellation cleanup. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(90)
class HibernateReactivePartyMutationAdapterTest {

    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final String KEY = "archive-request";
    private static final PartyLifecycleAction ARCHIVE = PartyLifecycleAction.ARCHIVE;

    @Inject
    Mutiny.SessionFactory sessions;
    @Inject
    PartyRootMutationPersistence roots;
    @Inject
    PartyLifecycleIdempotencyPersistence replay;
    @Inject
    PartyOutboxEventPersistenceMapper outboxMapper;
    @Inject
    PartyMutationTimeouts timeouts;
    @Inject
    PartyRootReader reader;
    @Inject
    NaturalPersonPersistenceMapper mapper;

    @Test
    @RunOnVertxContext
    void emitsOnlyAfterRootSnapshotAndEventCommitAndClosesTheScope(UniAsserter asserter) {
        NaturalPerson original = fixture();
        var metadata = metadata(original, "original-actor");
        var scope = new AtomicReference<PartyMutationContext>();
        var port = adapter(sessions, timeouts);
        asserter.execute(() -> seed(original));
        asserter.assertThat(() -> port.execute(metadata, context -> {
            scope.set(context);
            assertEquals(original.tenantId(), context.tenantId());
            return archive(context, original, metadata);
        }).call(result -> reader.findDetails(original.tenantId(), original.partyId())
                .invoke(stored -> assertEquals(result.party(), stored.orElseThrow()))), result -> {
            assertEquals(PartyMutationOutcome.Disposition.APPLIED, result.disposition());
            assertEquals(1, result.party().version().value());
            assertThrows(IllegalStateException.class, scope.get()::tenantId);
        });
        asserter.assertThat(() -> counts(original), counts -> assertEquals(List.of(1L, 1L), counts));
        asserter.assertFailedWith(() -> scope.get().findForUpdate(original.partyId()), IllegalStateException.class);
        var retry = metadata(original, "retry-actor");
        asserter.assertThat(() -> port.execute(retry, context -> archive(context, original, retry)), result -> {
            assertEquals(PartyMutationOutcome.Disposition.REPLAYED, result.disposition());
            assertEquals("original-actor", result.party().updatedBy());
            assertEquals(1, result.party().version().value());
        });
        asserter.assertThat(() -> counts(original), counts -> assertEquals(List.of(1L, 1L), counts));
    }

    @Test
    @RunOnVertxContext
    void observesTheCommittedMutationOnlyAfterSubscription(UniAsserter asserter) {
        NaturalPerson original = fixture();
        var metadata = metadata(original, "actor");
        var observer = new RecordingOperationObserver();
        var port = new HibernateReactivePartyMutationAdapter(sessions, roots, replay,
                new PartyRootOutboxPersistence(outboxMapper, "stored-only"), timeouts, observer);
        Uni<PartyMutationOutcome> operation = port.execute(metadata, context -> archive(context, original, metadata));
        assertEquals(List.of(), observer.startedCalls());
        asserter.execute(() -> seed(original));
        asserter.assertThat(() -> operation, result -> {
            assertEquals(PartyMutationOutcome.Disposition.APPLIED, result.disposition());
            assertEquals(List.of(new RecordingOperationObserver.StartedCall(metadata, ObservedOperation.TRANSACTION_PARTY_MUTATION)), observer.startedCalls());
            assertEquals(List.of(new RecordingOperationObserver.CompletedCall(metadata, ObservedOperation.TRANSACTION_PARTY_MUTATION,
                    OperationOutcome.APPLIED)), observer.completedCalls());
        });
    }

    @Test
    @RunOnVertxContext
    void setsReadCommittedReadWriteAndFiniteLocalLimitsBeforeApplicationWork(UniAsserter asserter) {
        NaturalPerson original = fixture();
        var metadata = metadata(original, "actor");
        var opened = new AtomicReference<Mutiny.Session>();
        var limits = new PartyMutationTimeouts(new Budgets(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofSeconds(10)));
        var port = adapter(capturingFactory(opened), limits);
        asserter.execute(() -> seed(original));
        asserter.execute(() -> port.execute(metadata, context -> opened.get().createNativeQuery("""
                select current_setting('transaction_isolation'), current_setting('transaction_read_only'),
                       (select setting from pg_settings where name = 'lock_timeout'),
                       (select setting from pg_settings where name = 'statement_timeout')
                """, Object[].class).getSingleResult().invoke(row -> {
            assertEquals("read committed", row[0]);
            assertEquals("off", row[1]);
            assertEquals("300", row[2]);
            assertEquals("2000", row[3]);
        }).chain(() -> archive(context, original, metadata))));
        asserter.assertThat(() -> sessions.withSession(session -> session.createNativeQuery("""
                select current_setting('lock_timeout'), current_setting('statement_timeout')
                """, Object[].class).getSingleResult()), row -> {
            assertEquals("0", row[0]);
            assertEquals("0", row[1]);
            assertFalse(opened.get().isOpen());
        });
    }

    @Test
    @RunOnVertxContext
    void rejectsKeyAfterRootAndUnserializedReplayAccessWithoutAnyAcceptedOutcome(UniAsserter asserter) {
        NaturalPerson original = fixture();
        var metadata = metadata(original, "actor");
        var port = adapter(sessions, timeouts);
        asserter.execute(() -> seed(original));
        asserter.assertFailedWith(() -> port.execute(metadata, context -> context.findForUpdate(original.partyId())
                .chain(() -> context.serializeReplayKey(ARCHIVE, KEY)).replaceWith(applied(original))),
                HibernateReactivePartyMutationAdapterTest::assertInternal);
        asserter.assertFailedWith(() -> port.execute(metadata, context -> context.findCompleted(ARCHIVE, KEY)
                .replaceWith(applied(original))), HibernateReactivePartyMutationAdapterTest::assertInternal);
        asserter.assertFailedWith(() -> port.execute(metadata, context -> context.serializeReplayKey(ARCHIVE, KEY)
                .chain(() -> context.findForUpdate(original.partyId())).replaceWith(applied(original))),
                HibernateReactivePartyMutationAdapterTest::assertInternal);
        assertUnchanged(asserter, original);
    }

    @Test
    @RunOnVertxContext
    void preservesKnownFailuresAndRollsBackAllPendingWrites(UniAsserter asserter) {
        var port = adapter(sessions, timeouts);
        List<Throwable> failures = List.of(new ApplicationException(new ApplicationFailure.InvalidPartyCursor()),
                new DomainValidationException(DomainViolation.DISPLAY_NAME_REQUIRED, "Controlled Domain failure"),
                new CancellationException("Controlled cancellation"));
        for (Throwable failure : failures) {
            NaturalPerson original = fixture();
            var metadata = metadata(original, "actor");
            asserter.execute(() -> seed(original));
            asserter.assertFailedWith(() -> port.execute(metadata, context -> archive(context, original, metadata)
                    .chain(() -> Uni.createFrom().failure(failure))), actual -> assertSame(failure, actual));
            assertUnchanged(asserter, original);
            asserter.assertThat(() -> port.execute(metadata, context -> archive(context, original, metadata)),
                    result -> assertEquals(PartyMutationOutcome.Disposition.APPLIED, result.disposition()));
        }
    }

    @Test
    @RunOnVertxContext
    void doesNotResumeAStorageScopeAfterItsFirstFailure(UniAsserter asserter) {
        NaturalPerson original = fixture();
        var metadata = metadata(original, "actor");
        var port = adapter(sessions, timeouts);
        asserter.execute(() -> seed(original));
        asserter.assertFailedWith(() -> port.execute(metadata, context -> context.findCompleted(ARCHIVE, KEY)
                .replaceWith(applied(original))
                .onFailure().recoverWithUni(ignored -> context.findForUpdate(original.partyId())
                        .replaceWith(applied(original)))), HibernateReactivePartyMutationAdapterTest::assertInternal);
        assertUnchanged(asserter, original);
    }

    @Test
    @RunOnVertxContext
    void rejectsConcurrentUseOfTheSameScopedSession(UniAsserter asserter) {
        NaturalPerson original = fixture();
        var metadata = metadata(original, "actor");
        var port = adapter(sessions, timeouts);
        asserter.execute(() -> seed(original));
        asserter.assertFailedWith(() -> port.execute(metadata, context -> Uni.combine().all().unis(
                context.serializeReplayKey(ARCHIVE, KEY), context.serializeReplayKey(ARCHIVE, "another-key"))
                .asTuple().replaceWith(applied(original))), HibernateReactivePartyMutationAdapterTest::assertInternal);
        assertUnchanged(asserter, original);
    }

    @Test
    @RunOnVertxContext
    void serverLockTimeoutEndsAContenderBeforeAnyRootWrite(UniAsserter asserter) {
        NaturalPerson original = fixture();
        var metadata = metadata(original, "actor");
        var limits = new PartyMutationTimeouts(new Budgets(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofSeconds(5)));
        var port = adapter(sessions, limits);
        asserter.execute(() -> seed(original));
        asserter.assertFailedWith(() -> sessions.openSession().flatMap(holder -> holder.withTransaction(transaction ->
                replay.serializeReplayKey(holder, original.tenantId(), ARCHIVE, KEY)
                        .chain(() -> port.execute(metadata, context -> archive(context, original, metadata))))
                .eventually(holder::close)), failure -> {
            assertInternal(failure);
            assertSqlState(failure, "55P03");
        });
        assertUnchanged(asserter, original);
        asserter.execute(() -> port.execute(metadata, context -> archive(context, original, metadata)));
    }

    @Test
    @RunOnVertxContext
    void equivalentContendersReadTheWinnerBeforeTryingToAcquireTheRoot(UniAsserter asserter) {
        NaturalPerson original = fixture();
        var first = metadata(original, "first-actor");
        var second = metadata(original, "retry-actor");
        var port = adapter(sessions, timeouts);
        var reached = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
        asserter.execute(() -> seed(original));
        asserter.assertThat(() -> {
            Uni<PartyMutationOutcome> winner = port.execute(first, context -> archive(context, original, first)
                    .invoke(() -> reached.complete(null)).call(() -> Uni.createFrom().completionStage(release)));
            Uni<PartyMutationOutcome> contender = Uni.createFrom().completionStage(reached)
                    .chain(() -> port.execute(second, context -> archive(context, original, second)));
            Uni<Void> coordinator = Uni.createFrom().completionStage(reached).chain(() -> awaitKeyWait(original))
                    .invoke(() -> release.complete(null));
            return Uni.combine().all().unis(winner, contender, coordinator).asTuple().eventually(() -> release.complete(null));
        }, results -> {
            assertEquals(PartyMutationOutcome.Disposition.APPLIED, results.getItem1().disposition());
            assertEquals(PartyMutationOutcome.Disposition.REPLAYED, results.getItem2().disposition());
            assertEquals(results.getItem1().party(), results.getItem2().party());
        });
        asserter.assertThat(() -> counts(original), counts -> assertEquals(List.of(1L, 1L), counts));
    }

    @Test
    @RunOnVertxContext
    void workTimeoutRollsBackAndReleasesTheKeyAndRoot(UniAsserter asserter) {
        NaturalPerson original = fixture();
        var metadata = metadata(original, "actor");
        var opened = new AtomicReference<Mutiny.Session>();
        var limits = new PartyMutationTimeouts(new Budgets(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)));
        var port = adapter(capturingFactory(opened), limits);
        asserter.execute(() -> seed(original));
        asserter.assertFailedWith(() -> port.execute(metadata, context -> archive(context, original, metadata)
                .chain(() -> Uni.createFrom().nothing())), HibernateReactivePartyMutationAdapterTest::assertInternal);
        asserter.execute(() -> awaitClosed(opened));
        assertUnchanged(asserter, original);
        asserter.execute(() -> adapter(sessions, timeouts).execute(metadata, context -> archive(context, original, metadata)));
    }

    @Test
    @RunOnVertxContext
    void downstreamCancellationClosesTheSessionAndLeavesNoPartialMutation(UniAsserter asserter) {
        NaturalPerson original = fixture();
        var metadata = metadata(original, "actor");
        var opened = new AtomicReference<Mutiny.Session>();
        var reached = new CompletableFuture<Void>();
        var port = adapter(capturingFactory(opened), timeouts);
        asserter.execute(() -> seed(original));
        asserter.execute(() -> {
            UniAssertSubscriber<PartyMutationOutcome> subscriber = port.execute(metadata, context -> archive(context, original, metadata)
                    .call(() -> Uni.createFrom().<Void>emitter(emitter -> reached.complete(null))))
                    .subscribe().withSubscriber(UniAssertSubscriber.create());
            return Uni.createFrom().completionStage(reached).ifNoItem().after(Duration.ofSeconds(10)).fail()
                    .invoke(subscriber::cancel).chain(() -> awaitClosed(opened))
                    .invoke(() -> assertNull(subscriber.getItem())).eventually(subscriber::cancel);
        });
        assertUnchanged(asserter, original);
        asserter.execute(() -> adapter(sessions, timeouts).execute(metadata, context -> archive(context, original, metadata)));
    }

    private HibernateReactivePartyMutationAdapter adapter(Mutiny.SessionFactory factory, PartyMutationTimeouts limits) {
        return new HibernateReactivePartyMutationAdapter(factory, roots, replay, new PartyRootOutboxPersistence(outboxMapper, "stored-only"),
                limits, new RecordingOperationObserver());
    }

    @Test
    @RunOnVertxContext
    void lifecycleCheckpointFailuresRollBackFlushedRootSnapshotAndEventAndLeaveTheKeyReusable(UniAsserter asserter) {
        for (Checkpoint checkpoint : Checkpoint.values()) {
            NaturalPerson original = fixture();
            var metadata = metadata(original, "checkpoint-actor");
            var opened = new AtomicReference<Mutiny.Session>();
            var failing = new ChangePartyLifecycleUseCase(failingPort(opened, checkpoint, true),
                    Clock.offset(CLOCK, Duration.ofSeconds(1)), new RecordingOperationObserver());
            var command = new ChangePartyLifecycleCommand(metadata, original.partyId(), original.version(), ARCHIVE, Optional.of(KEY));
            asserter.execute(() -> seed(original));
            asserter.assertFailedWith(() -> failing.execute(command), HibernateReactivePartyMutationAdapterTest::assertInternal);
            asserter.execute(() -> awaitClosed(opened));
            assertUnchanged(asserter, original);
            var retry = new ChangePartyLifecycleUseCase(adapter(sessions, timeouts), Clock.offset(CLOCK, Duration.ofSeconds(1)), new RecordingOperationObserver());
            asserter.assertThat(() -> retry.execute(command), result -> {
                assertEquals(PartyMutationOutcome.Disposition.APPLIED, result.disposition());
                assertEquals(1, result.party().version().value());
            });
            asserter.assertThat(() -> counts(original), counts -> assertEquals(List.of(1L, 1L), counts));
        }
    }

    @Test
    @RunOnVertxContext
    void correctionCheckpointFailuresRollBackFlushedLabelAndEventBeforeAcceptance(UniAsserter asserter) {
        for (Checkpoint checkpoint : List.of(Checkpoint.ROOT, Checkpoint.EVENT)) {
            NaturalPerson original = fixture();
            var metadata = metadata(original, "checkpoint-actor");
            var opened = new AtomicReference<Mutiny.Session>();
            var failing = new PatchPartyUseCase(failingPort(opened, checkpoint, false),
                    Clock.offset(CLOCK, Duration.ofSeconds(1)), new RecordingOperationObserver());
            var command = new PatchPartyCommand(metadata, original.partyId(), original.version(), "Corrected");
            asserter.execute(() -> seed(original));
            asserter.assertFailedWith(() -> failing.execute(command), HibernateReactivePartyMutationAdapterTest::assertInternal);
            asserter.execute(() -> awaitClosed(opened));
            assertUnchanged(asserter, original);
            var retry = new PatchPartyUseCase(adapter(sessions, timeouts), Clock.offset(CLOCK, Duration.ofSeconds(1)), new RecordingOperationObserver());
            asserter.assertThat(() -> retry.execute(command), result -> assertEquals("CORRECTED", result.party().displayName()));
            asserter.assertThat(() -> counts(original), counts -> assertEquals(List.of(0L, 1L), counts));
        }
    }

    private PartyMutationPort failingPort(AtomicReference<Mutiny.Session> opened, Checkpoint checkpoint, boolean keyed) {
        var delegate = adapter(capturingFactory(opened), timeouts);
        return (metadata, work) -> delegate.execute(metadata, context -> work.apply(PartyMutationContext.class.cast(
                Proxy.newProxyInstance(PartyMutationContext.class.getClassLoader(), new Class<?>[]{PartyMutationContext.class},
                        (ignored, method, arguments) -> {
                            Object result;
                            try {
                                result = method.invoke(context, arguments);
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                            if (method.getName().equals(checkpoint.method) && result instanceof Uni<?> operation) {
                                return operation.call(() -> opened.get().flush())
                                        .call(() -> assertFlushedCheckpoint(opened.get(), metadata.tenantId(), checkpoint, keyed))
                                        .chain(() -> Uni.createFrom().failure(new IllegalStateException("Controlled checkpoint failure")));
                            }
                            return result;
                        }))));
    }

    private static Uni<Void> assertFlushedCheckpoint(Mutiny.Session session, TenantId tenant, Checkpoint checkpoint, boolean keyed) {
        return session.createNativeQuery("""
                select (select version from parties where tenant_id = :tenant),
                       (select count(*) from api_idempotency_records where tenant_id = :tenant),
                       (select count(*) from party_outbox_events where tenant_id = :tenant)
                """, Object[].class).setParameter("tenant", tenant.value()).getSingleResult().invoke(row -> {
            assertEquals(1, ((Number) row[0]).longValue());
            assertEquals(keyed && checkpoint != Checkpoint.ROOT ? 1 : 0, ((Number) row[1]).longValue());
            assertEquals(checkpoint == Checkpoint.EVENT ? 1 : 0, ((Number) row[2]).longValue());
        }).replaceWithVoid();
    }

    /** Names the real scoped write after which a test flushes, proves database visibility, and injects failure. */
    private enum Checkpoint {
        ROOT("persistRoot"), SNAPSHOT("recordCompletion"), EVENT("appendEnabledEvent");

        private final String method;

        Checkpoint(String method) {
            this.method = method;
        }
    }

    private static Uni<PartyMutationOutcome> archive(PartyMutationContext context, Party original, RequestMetadata metadata) {
        return context.serializeReplayKey(ARCHIVE, KEY).chain(() -> context.findCompleted(ARCHIVE, KEY))
                .chain(found -> found.isPresent()
                        ? Uni.createFrom().item(new PartyMutationOutcome(found.orElseThrow().result(), PartyMutationOutcome.Disposition.REPLAYED))
                        : context.findForUpdate(original.partyId()).map(Optional::orElseThrow)
                                .chain(locked -> context.persistRoot(locked.archive(CLOCK.instant().plusSeconds(1), metadata.userId()), locked.version()))
                                .chain(stored -> complete(context, original, stored, metadata)));
    }

    private static Uni<PartyMutationOutcome> complete(PartyMutationContext context, Party original, Party stored, RequestMetadata metadata) {
        var snapshot = new CompletedPartyLifecycle(new PartyLifecycleRequest(ARCHIVE, original.partyId(), original.version()),
                PartyDetailsResult.fromAggregate(stored));
        var event = new PartyChangedOutboxCandidate(UUID.randomUUID(), stored.tenantId(), stored.partyId(), stored.version(), stored.type(),
                stored.recordStatus(), PartyChangedOutboxCandidate.Kind.ARCHIVED, stored.auditInfo().updatedAt(), metadata.processId(), metadata.userId());
        return context.recordCompletion(ARCHIVE, KEY, snapshot).chain(() -> context.appendEnabledEvent(event)).replaceWith(applied(stored));
    }

    private Uni<Void> seed(NaturalPerson original) {
        return sessions.withTransaction((session, transaction) -> session.persist(mapper.toEntity(original)));
    }

    private Uni<List<Long>> counts(Party original) {
        return sessions.withSession(session -> session.createNativeQuery("""
                select (select count(*) from api_idempotency_records where tenant_id = :tenant),
                       (select count(*) from party_outbox_events where tenant_id = :tenant)
                """, Object[].class).setParameter("tenant", original.tenantId().value()).getSingleResult()
                .map(row -> List.of(((Number) row[0]).longValue(), ((Number) row[1]).longValue())));
    }

    private void assertUnchanged(UniAsserter asserter, Party original) {
        asserter.assertThat(() -> reader.findDetails(original.tenantId(), original.partyId()),
                result -> assertEquals(PartyDetailsResult.fromAggregate(original), result.orElseThrow()));
        asserter.assertThat(() -> counts(original), counts -> assertEquals(List.of(0L, 0L), counts));
    }

    private Uni<Void> awaitKeyWait(Party original) {
        long lockId = PartyLifecycleIdempotencyPersistence.lockId(PartyLifecycleIdempotencyPersistence.id(original.tenantId(), ARCHIVE, KEY));
        return sessions.openSession().flatMap(session -> Multi.createBy().repeating().uni(() -> session.createNativeQuery("""
                select exists (select 1 from pg_locks where locktype = 'advisory' and not granted
                               and classid::bigint = :upper and objid::bigint = :lower and objsubid = 1)
                """, Boolean.class).setParameter("upper", lockId >>> 32).setParameter("lower", lockId & 0xffffffffL)
                .getSingleResult().onItem().delayIt().by(Duration.ofMillis(5))).until(Boolean.TRUE::equals).collect().last()
                .ifNoItem().after(Duration.ofSeconds(5)).fail().eventually(session::close)).replaceWithVoid();
    }

    private Mutiny.SessionFactory capturingFactory(AtomicReference<Mutiny.Session> opened) {
        Object proxy = Proxy.newProxyInstance(Mutiny.SessionFactory.class.getClassLoader(), new Class<?>[]{Mutiny.SessionFactory.class},
                (ignored, method, arguments) -> {
                    if (method.getName().equals("openSession") && (arguments == null || arguments.length == 0)) {
                        return sessions.openSession().invoke(opened::set);
                    }
                    throw new AssertionError("Unexpected factory operation: " + method.getName());
                });
        return Mutiny.SessionFactory.class.cast(proxy);
    }

    private static Uni<Void> awaitClosed(AtomicReference<Mutiny.Session> opened) {
        return Multi.createBy().repeating().uni(() -> Uni.createFrom().item(() -> !opened.get().isOpen())
                        .onItem().delayIt().by(Duration.ofMillis(5)))
                .until(Boolean.TRUE::equals).collect().last().ifNoItem().after(Duration.ofSeconds(5)).fail().replaceWithVoid();
    }

    private static NaturalPerson fixture() {
        return NaturalPerson.restore(new PartyId(UUID.randomUUID()), new TenantId(UUID.randomUUID()), "Original Label", PartyRecordStatus.DRAFT,
                PartyVersion.initial(), AuditInfo.initial(CLOCK.instant(), "creator"), new NaturalPersonDetails("Original", "Person", null, null, null, "GB"));
    }

    private static RequestMetadata metadata(Party original, String user) {
        return new RequestMetadata(original.tenantId(), user, UUID.randomUUID());
    }

    private static PartyMutationOutcome applied(Party party) {
        return new PartyMutationOutcome(PartyDetailsResult.fromAggregate(party), PartyMutationOutcome.Disposition.APPLIED);
    }

    private static void assertInternal(Throwable failure) {
        var application = assertInstanceOf(ApplicationException.class, failure);
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, application.failure());
    }

    private static void assertSqlState(Throwable failure, String state) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof SQLException sql && state.equals(sql.getSQLState())) {
                return;
            }
            cause = cause.getCause();
        }
        throw new AssertionError("Expected PostgreSQL lock timeout", failure);
    }

    /** Supplies test-only finite limits while retaining the adapter's production timeout implementation. */
    private record Budgets(Duration lockTimeout, Duration statementTimeout, Duration operationTimeout) implements PartyMutationConfiguration {
    }
}
