package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.ChangeIdentifierSchemeLifecycleCommand;
import com.alexastudillo.partyregistry.application.command.CreateIdentifierSchemeCommand;
import com.alexastudillo.partyregistry.application.command.PatchIdentifierSchemeCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeCreateInput;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationContext;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationPort;
import com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCase;
import com.alexastudillo.partyregistry.application.usecase.CreateIdentifierSchemeUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchIdentifierSchemeUseCase;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeChanges;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises owned reactive scopes, global locks, exact writes, and atomic immutable replay against PostgreSQL. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(90)
class HibernateReactiveIdentifierSchemeMutationAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00.123456Z"), ZoneOffset.UTC);
    private static final String KEY = " scheme-request ";
    @Inject Mutiny.SessionFactory sessions;
    @Inject IdentifierSchemePersistenceMapper mapper;
    @Inject IdentifierSchemeCompletionCodec codec;
    @Inject PartyMutationTimeouts timeouts;
    @Inject HibernateReactiveIdentifierSchemeReadAdapter reads;

    @Test
    @RunOnVertxContext
    void ownsOneLazySessionAndEmitsAfterCommitWithClosedContext(UniAsserter asserter) {
        var metadata = metadata("creator");
        var command = createCommand(metadata, code());
        var opened = new AtomicReference<Mutiny.Session>();
        var scope = new AtomicReference<IdentifierSchemeMutationContext>();
        var count = new AtomicInteger();
        var delegate = adapter(capturingFactory(opened, count), timeouts, locks());
        IdentifierSchemeMutationPort port = (request, work) -> delegate.execute(request, context -> {
            scope.set(context);
            assertEquals(metadata.tenantId(), context.tenantId());
            return work.apply(context);
        });
        var operation = creation(port).execute(command);
        assertEquals(0, count.get());
        assertNull(scope.get());
        asserter.assertThat(() -> operation.call(result -> reads.findById(result.scheme().id())
                .invoke(found -> assertEquals(result.scheme(), found.orElseThrow()))), result -> {
            assertEquals(IdentifierSchemeMutationOutcome.Disposition.APPLIED, result.disposition());
            assertEquals(0, result.scheme().version().value());
            assertEquals(IdentifierSchemeStatus.DRAFT, result.scheme().status());
            assertEquals(result.scheme().createdAt(), result.scheme().updatedAt());
            assertEquals(1, count.get());
            assertFalse(opened.get().isOpen());
            assertThrows(IllegalStateException.class, scope.get()::tenantId);
        });
        asserter.assertFailedWith(() -> scope.get().codeExists(command.effectiveRequest().code()), IllegalStateException.class);
        asserter.assertThat(() -> counts(command), row -> assertEquals(List.of(1L, 1L, 0L), row));
    }

    @Test
    @RunOnVertxContext
    void rejectsEscapedOrderedConcurrentRecoveredAndInventedWork(UniAsserter asserter) {
        var metadata = metadata("actor");
        var draft = draft(code(), metadata.userId());
        var port = adapter(sessions, timeouts, locks());
        asserter.assertFailedWith(() -> port.execute(metadata, context -> context.findCompleted("identifier-scheme.create.v1", KEY)
                .replaceWith(applied(draft))), IllegalStateException.class);
        asserter.assertFailedWith(() -> port.execute(metadata, context -> context.findForUpdate(draft.id())
                .chain(() -> context.serializeReplayKey("identifier-scheme.create.v1", KEY)).replaceWith(applied(draft))), IllegalStateException.class);
        asserter.assertFailedWith(() -> port.execute(metadata, context -> context.serializeReplayKey("identifier-scheme.create.v1", KEY)
                .chain(() -> context.serializeCode(draft.code())).replaceWith(applied(draft))), IllegalStateException.class);
        asserter.assertFailedWith(() -> port.execute(metadata, context -> Uni.combine().all().unis(
                context.serializeReplayKey("identifier-scheme.create.v1", KEY),
                context.serializeReplayKey("identifier-scheme.create.v1", "other")).asTuple().replaceWith(applied(draft))), IllegalStateException.class);
        asserter.assertFailedWith(() -> port.execute(metadata, context -> context.codeExists(draft.code())
                .onFailure().recoverWithUni(ignored -> context.serializeCode(draft.code()).replaceWith(false))
                .replaceWith(applied(draft))), IllegalStateException.class);
        asserter.assertFailedWith(() -> port.execute(metadata, context -> Uni.createFrom().item(applied(draft))), IllegalStateException.class);
        asserter.assertFailedWith(() -> port.execute(metadata, context -> context.serializeCode(draft.code())
                .chain(() -> context.codeExists("another-code")).replaceWith(applied(draft))), IllegalStateException.class);
    }

    @Test
    @RunOnVertxContext
    void failuresAfterFlushedCreationOrCompletionRollBackAndKeepTheKeyReusable(UniAsserter asserter) {
        for (String checkpoint : List.of("insert", "recordCompletion")) {
            var command = createCommand(metadata("original"), code());
            var opened = new AtomicReference<Mutiny.Session>();
            Throwable failure = new IllegalStateException("Controlled acceptance failure");
            var delegate = adapter(capturingFactory(opened, new AtomicInteger()), timeouts, locks());
            var failing = failingPort(delegate, opened, checkpoint, failure);
            asserter.assertFailedWith(() -> creation(failing).execute(command), actual -> assertSame(failure, actual));
            asserter.execute(() -> awaitClosed(opened));
            asserter.assertThat(() -> counts(command), row -> assertEquals(List.of(0L, 0L, 0L), row));
            asserter.execute(() -> creation(adapter(sessions, timeouts, locks())).execute(command));
            asserter.assertThat(() -> counts(command), row -> assertEquals(List.of(1L, 1L, 0L), row));
        }
    }

    @Test
    @RunOnVertxContext
    void cancellationAndTypedFailuresReleasePendingWritesAndOwnedSessions(UniAsserter asserter) {
        for (Throwable failure : List.of(new CancellationException("cancelled"),
                new ApplicationException(new ApplicationFailure.InvalidIdentifierSchemeConfiguration()))) {
            var command = createCommand(metadata("actor"), code());
            var opened = new AtomicReference<Mutiny.Session>();
            var delegate = adapter(capturingFactory(opened, new AtomicInteger()), timeouts, locks());
            asserter.assertFailedWith(() -> creation((request, work) -> delegate.execute(request,
                    context -> work.apply(context).chain(() -> Uni.createFrom().failure(failure)))).execute(command),
                    actual -> assertSame(failure, actual));
            asserter.assertThat(() -> counts(command), row -> assertEquals(List.of(0L, 0L, 0L), row));
            asserter.execute(() -> awaitClosed(opened));
        }
        var command = createCommand(metadata("actor"), code());
        var opened = new AtomicReference<Mutiny.Session>();
        var reached = new CompletableFuture<Void>();
        var dropped = new CopyOnWriteArrayList<Throwable>();
        var delegate = adapter(capturingFactory(opened, new AtomicInteger()), timeouts, locks());
        asserter.execute(() -> {
            Infrastructure.setDroppedExceptionHandler(dropped::add);
            var subscriber = creation((request, work) -> delegate.execute(request, context -> work.apply(context)
                    .call(() -> Uni.createFrom().<Void>emitter(emitter -> reached.complete(null)))))
                    .execute(command).subscribe().withSubscriber(UniAssertSubscriber.create());
            return Uni.createFrom().completionStage(reached).ifNoItem().after(Duration.ofSeconds(10)).fail()
                    .invoke(subscriber::cancel).chain(() -> awaitClosed(opened))
                    .invoke(() -> {
                        assertNull(subscriber.getItem());
                        assertEquals(List.of(), dropped, "Cancellation must finish rollback before closing its connection");
                    }).eventually(subscriber::cancel).eventually(Infrastructure::resetDroppedExceptionHandler);
        });
        asserter.assertThat(() -> counts(command), row -> assertEquals(List.of(0L, 0L, 0L), row));
        asserter.execute(() -> creation(adapter(sessions, timeouts, locks())).execute(command));
    }

    @Test
    @RunOnVertxContext
    void equivalentCreationWaitsForTheFullKeyAndReplaysCommittedOriginal(UniAsserter asserter) {
        var command = createCommand(metadata("original"), code());
        var retry = createCommand(new RequestMetadata(command.requestMetadata().tenantId(), "retry", UUID.randomUUID()), command.effectiveRequest().code());
        var reached = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
        var port = adapter(sessions, timeouts, locks());
        asserter.assertThat(() -> {
            Uni<IdentifierSchemeMutationOutcome> winner = creation((request, work) -> port.execute(request, context -> work.apply(context)
                    .invoke(() -> reached.complete(null)).call(() -> Uni.createFrom().completionStage(release)))).execute(command);
            var contender = Uni.createFrom().completionStage(reached).chain(() -> creation(port).execute(retry));
            var coordinator = Uni.createFrom().completionStage(reached).chain(() -> awaitLockWait(
                    IdentifierSchemeMutationLocks.replayLockId(identity(command)))).invoke(() -> release.complete(null));
            return Uni.combine().all().unis(winner, contender, coordinator).asTuple().eventually(() -> release.complete(null));
        }, result -> {
            assertEquals(IdentifierSchemeMutationOutcome.Disposition.APPLIED, result.getItem1().disposition());
            assertEquals(IdentifierSchemeMutationOutcome.Disposition.REPLAYED, result.getItem2().disposition());
            assertEquals(result.getItem1().scheme(), result.getItem2().scheme());
            assertEquals("original", result.getItem2().scheme().updatedBy());
        });
        asserter.assertThat(() -> counts(command), row -> assertEquals(List.of(1L, 1L, 0L), row));
    }

    @Test
    @RunOnVertxContext
    void differentTenantsSerializeTheSameExactGlobalCode(UniAsserter asserter) {
        var first = createCommand(metadata("first"), code());
        var other = createCommand(metadata("other"), first.effectiveRequest().code());
        var reached = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
        var port = adapter(sessions, timeouts, locks());
        asserter.assertThat(() -> {
            var winner = creation((request, work) -> port.execute(request, context -> work.apply(context)
                    .invoke(() -> reached.complete(null)).call(() -> Uni.createFrom().completionStage(release)))).execute(first);
            var contender = Uni.createFrom().completionStage(reached).chain(() -> creation(port).execute(other))
                    .onItemOrFailure().transform((item, failure) -> failure);
            var coordinator = Uni.createFrom().completionStage(reached)
                    .chain(() -> awaitLockWait(IdentifierSchemeMutationLocks.codeLockId(first.effectiveRequest().code())))
                    .invoke(() -> release.complete(null));
            return Uni.combine().all().unis(winner, contender, coordinator).asTuple().eventually(() -> release.complete(null));
        }, result -> assertFailure(result.getItem2(), ApplicationFailure.IdentifierSchemeCodeConflict.class));
        asserter.assertThat(() -> counts(other), row -> assertEquals(List.of(1L, 0L, 0L), row));
    }

    @Test
    @RunOnVertxContext
    void collisionsOnlySerializeAndNeverConflateFullKeyOrCodeIdentity(UniAsserter asserter) {
        var collisionLocks = new IdentifierSchemeMutationLocks(ignored -> 101L, ignored -> 102L);
        var first = createCommand(metadata("first"), code());
        var second = createCommand(metadata("second"), code());
        var sameTenantNewKey = new CreateIdentifierSchemeCommand(first.requestMetadata(), "different", second.effectiveRequest());
        var create = creation(adapter(sessions, timeouts, collisionLocks));
        asserter.execute(() -> create.execute(first));
        asserter.assertThat(() -> create.execute(second), result -> assertEquals(IdentifierSchemeMutationOutcome.Disposition.APPLIED, result.disposition()));
        asserter.assertFailedWith(() -> create.execute(sameTenantNewKey), failure -> assertFailure(failure, ApplicationFailure.IdentifierSchemeCodeConflict.class));
        asserter.assertFailedWith(() -> create.execute(new CreateIdentifierSchemeCommand(first.requestMetadata(), KEY, second.effectiveRequest())),
                failure -> assertFailure(failure, ApplicationFailure.IdempotencyKeyConflict.class));
        asserter.assertThat(() -> create.execute(first), result -> assertEquals(IdentifierSchemeMutationOutcome.Disposition.REPLAYED, result.disposition()));
    }

    @Test
    @RunOnVertxContext
    void globalRowWritesAdvanceOnceAndReplayRemainsHistoricalAcrossLaterChanges(UniAsserter asserter) {
        var command = createCommand(metadata("creator"), code());
        var created = new AtomicReference<IdentifierSchemeResult>();
        var activated = new AtomicReference<IdentifierSchemeResult>();
        var port = adapter(sessions, timeouts, locks());
        var lifecycle = lifecycle(port);
        asserter.execute(() -> creation(port).execute(command).invoke(outcome -> created.set(outcome.scheme())));
        asserter.assertThat(() -> lifecycle.execute(action(command.requestMetadata(), created.get(), IdentifierSchemeLifecycleAction.ACTIVATE, Optional.of(KEY))), result -> {
            activated.set(result.scheme());
            assertEquals(1, result.scheme().version().value());
            assertEquals(created.get().createdAt(), result.scheme().createdAt());
        });
        var another = metadata("another-tenant");
        asserter.assertThat(() -> lifecycle.execute(action(another, activated.get(), IdentifierSchemeLifecycleAction.RETIRE, Optional.empty())), result -> {
            assertEquals(2, result.scheme().version().value());
            assertEquals(IdentifierSchemeStatus.RETIRED, result.scheme().status());
            assertEquals("another-tenant", result.scheme().updatedBy());
            assertEquals(created.get().createdBy(), result.scheme().createdBy());
        });
        asserter.assertThat(() -> lifecycle.execute(action(command.requestMetadata(), created.get(), IdentifierSchemeLifecycleAction.ACTIVATE, Optional.of(KEY))), result -> {
            assertEquals(IdentifierSchemeMutationOutcome.Disposition.REPLAYED, result.disposition());
            assertEquals(activated.get(), result.scheme());
        });
        asserter.assertThat(() -> creation(port).execute(command), result -> assertEquals(created.get(), result.scheme()));
        asserter.assertThat(() -> reads.findById(created.get().id()), result -> assertEquals(2, result.orElseThrow().version().value()));
        asserter.assertThat(() -> counts(command), row -> assertEquals(List.of(1L, 2L, 0L), row));
        asserter.assertFailedWith(() -> lifecycle.execute(action(another, created.get(), IdentifierSchemeLifecycleAction.ACTIVATE, Optional.of(KEY))),
                failure -> assertFailure(failure, ApplicationFailure.IdentifierSchemeVersionMismatch.class));
    }

    @Test
    @RunOnVertxContext
    void completionFailureRollsBackLifecycleAndKeepsItsKeyReusable(UniAsserter asserter) {
        var command = createCommand(metadata("creator"), code());
        var stored = new AtomicReference<IdentifierSchemeResult>();
        var port = adapter(sessions, timeouts, locks());
        var opened = new AtomicReference<Mutiny.Session>();
        Throwable failure = new IllegalStateException("Completion failed after flush");
        var failing = failingPort(adapter(capturingFactory(opened, new AtomicInteger()), timeouts, locks()), opened, "recordCompletion", failure);
        asserter.execute(() -> creation(port).execute(command).invoke(outcome -> stored.set(outcome.scheme())));
        asserter.assertFailedWith(() -> lifecycle(failing).execute(action(command.requestMetadata(), stored.get(), IdentifierSchemeLifecycleAction.ACTIVATE, Optional.of(KEY))),
                actual -> assertSame(failure, actual));
        asserter.assertThat(() -> reads.findById(stored.get().id()), found -> assertEquals(stored.get(), found.orElseThrow()));
        asserter.assertThat(() -> counts(command), row -> assertEquals(List.of(1L, 1L, 0L), row));
        asserter.execute(() -> lifecycle(port).execute(action(command.requestMetadata(), stored.get(), IdentifierSchemeLifecycleAction.ACTIVATE, Optional.of(KEY))));
    }

    @Test
    @RunOnVertxContext
    void identicalPatchAdvancesExactlyOncePreservesIdentityAndCreatesNoCompletion(UniAsserter asserter) {
        var command = createCommand(metadata("creator"), code());
        var stored = new AtomicReference<IdentifierSchemeResult>();
        var port = adapter(sessions, timeouts, locks());
        var patch = new PatchIdentifierSchemeUseCase(port, new IdentifierRuleCatalog(), Clock.offset(CLOCK, Duration.ofSeconds(2)));
        asserter.execute(() -> creation(port).execute(command).invoke(outcome -> stored.set(outcome.scheme())));
        asserter.assertThat(() -> patch.execute(new PatchIdentifierSchemeCommand(command.requestMetadata(), stored.get().id(),
                stored.get().version(), sameName())), result -> {
            assertEquals(1, result.scheme().version().value());
            assertEquals(stored.get().name(), result.scheme().name());
            assertEquals(stored.get().id(), result.scheme().id());
            assertEquals(stored.get().code(), result.scheme().code());
            assertEquals(stored.get().createdBy(), result.scheme().createdBy());
            assertEquals(stored.get().createdAt(), result.scheme().createdAt());
            assertEquals(CLOCK.instant().plusSeconds(2), result.scheme().updatedAt());
        });
        asserter.assertThat(() -> counts(command), row -> assertEquals(List.of(1L, 1L, 0L), row));
        asserter.assertFailedWith(() -> patch.execute(new PatchIdentifierSchemeCommand(command.requestMetadata(), stored.get().id(),
                stored.get().version(), sameName())), failure -> assertFailure(failure, ApplicationFailure.IdentifierSchemeVersionMismatch.class));
    }

    @Test
    @RunOnVertxContext
    void zeroRowCasIsTypedAndInvalidIdentityCannotBeWritten(UniAsserter asserter) {
        var source = draft(code(), "actor");
        var storage = new IdentifierSchemeMutationPersistence(mapper);
        asserter.execute(() -> sessions.withTransaction((session, transaction) -> storage.insert(session, source)));
        asserter.assertFailedWith(() -> sessions.withTransaction((session, transaction) -> storage.findForUpdate(session, source.id())
                .chain(found -> session.createMutationQuery("update IdentifierSchemeEntity scheme set scheme.version = 2 where scheme.id = :id")
                        .setParameter("id", source.id().value()).executeUpdate()
                        .chain(() -> storage.persistScheme(session, found.orElseThrow(), source.retire(CLOCK.instant().plusSeconds(1), "actor"), source.version())))),
                failure -> assertFailure(failure, ApplicationFailure.IdentifierSchemeVersionMismatch.class));
        asserter.assertThat(() -> reads.findById(source.id()), found -> assertEquals(IdentifierSchemeResult.fromAggregate(source), found.orElseThrow()));
        var next = source.retire(CLOCK.instant().plusSeconds(1), "actor");
        var changedIdentity = new IdentifierScheme(next.id(), "different-code", next.issuingCountryCode(), next.category(), next.applicableSubjectType(),
                next.name(), next.description(), next.normalizerKey(), next.validatorKey(), next.minimumLength(), next.maximumLength(),
                next.requiresExpiration(), next.status(), next.version(), next.auditInfo());
        asserter.assertFailedWith(() -> sessions.withTransaction((session, transaction) -> storage.persistScheme(session, source, changedIdentity, source.version())),
                IllegalArgumentException.class);
    }

    @Test
    @RunOnVertxContext
    void finiteLockAndOperationBudgetsFailAfterRollbackAndSessionCleanup(UniAsserter asserter) {
        var command = createCommand(metadata("actor"), code());
        var opened = new AtomicReference<Mutiny.Session>();
        var budgets = new PartyMutationTimeouts(new Budgets(Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofSeconds(2)));
        var port = adapter(capturingFactory(opened, new AtomicInteger()), budgets, locks());
        asserter.assertFailedWith(() -> sessions.openSession().flatMap(holder -> holder.withTransaction(transaction ->
                locks().serializeReplayKey(holder, identity(command)).chain(() -> creation(port).execute(command)))
                .eventually(holder::close)), failure -> {
            assertFailure(failure, ApplicationFailure.DependencyUnavailable.class);
            assertFalse(opened.get().isOpen());
        });
        asserter.assertThat(() -> counts(command), row -> assertEquals(List.of(0L, 0L, 0L), row));
        asserter.assertFailedWith(() -> creation((metadata, work) -> port.execute(metadata, context -> work.apply(context)
                .chain(() -> Uni.createFrom().nothing()))).execute(command), failure -> {
            assertFailure(failure, ApplicationFailure.DependencyUnavailable.class);
            assertFalse(opened.get().isOpen());
        });
        asserter.assertThat(() -> counts(command), row -> assertEquals(List.of(0L, 0L, 0L), row));
        asserter.execute(() -> creation(adapter(sessions, timeouts, locks())).execute(command));
    }

    @Test
    @RunOnVertxContext
    void statementDeadlineAndLocalSettingsApplyInsideTheActualTransaction(UniAsserter asserter) {
        var metadata = metadata("actor");
        var opened = new AtomicReference<Mutiny.Session>();
        var budgets = new PartyMutationTimeouts(new Budgets(Duration.ofMillis(100), Duration.ofMillis(150), Duration.ofSeconds(2)));
        var port = adapter(capturingFactory(opened, new AtomicInteger()), budgets, locks());
        asserter.assertFailedWith(() -> port.execute(metadata, context -> opened.get().createNativeQuery("""
                select current_setting('transaction_isolation'), current_setting('transaction_read_only'),
                       current_setting('lock_timeout'), current_setting('statement_timeout')
                """, Object[].class).getSingleResult().invoke(row -> {
            assertEquals("read committed", row[0]);
            assertEquals("off", row[1]);
            assertEquals("100ms", row[2]);
            assertEquals("150ms", row[3]);
        }).chain(() -> opened.get().createNativeQuery("select 1 from pg_sleep(1)", Integer.class).getSingleResult())
                .replaceWith(applied(draft(code(), "actor")))), failure -> {
            assertFailure(failure, ApplicationFailure.DependencyUnavailable.class);
            assertFalse(opened.get().isOpen());
        });
        asserter.assertThat(() -> sessions.withSession(session -> session.createNativeQuery(
                "select current_setting('lock_timeout'), current_setting('statement_timeout')", Object[].class).getSingleResult()),
                row -> assertArrayEquals(new Object[]{"0", "0"}, row));
    }

    @Test
    @RunOnVertxContext
    void rowLockRetainsGlobalWinningVersionWhileIndependentLifecycleWaits(UniAsserter asserter) {
        var create = createCommand(metadata("creator"), code());
        var original = new AtomicReference<IdentifierSchemeResult>();
        var reached = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
        var port = adapter(sessions, timeouts, locks());
        asserter.execute(() -> creation(port).execute(create).invoke(outcome -> original.set(outcome.scheme())));
        asserter.assertThat(() -> {
            var winner = lifecycle((request, work) -> port.execute(request, context -> work.apply(context)
                    .invoke(() -> reached.complete(null)).call(() -> Uni.createFrom().completionStage(release))))
                    .execute(action(create.requestMetadata(), original.get(), IdentifierSchemeLifecycleAction.ACTIVATE, Optional.empty()));
            var contender = Uni.createFrom().completionStage(reached).chain(() -> lifecycle(port)
                    .execute(action(metadata("other-tenant"), original.get(), IdentifierSchemeLifecycleAction.RETIRE, Optional.empty())))
                    .onItemOrFailure().transform((item, failure) -> failure);
            var coordinator = Uni.createFrom().completionStage(reached).chain(this::awaitRowWait).invoke(() -> release.complete(null));
            return Uni.combine().all().unis(winner, contender, coordinator).asTuple().eventually(() -> release.complete(null));
        }, result -> {
            assertEquals(1, result.getItem1().scheme().version().value());
            assertEquals(IdentifierSchemeStatus.ACTIVE, result.getItem1().scheme().status());
            assertFailure(result.getItem2(), ApplicationFailure.IdentifierSchemeVersionMismatch.class);
        });
        asserter.assertThat(() -> counts(create), row -> assertEquals(List.of(1L, 1L, 0L), row));
    }

    @Test
    @RunOnVertxContext
    void contextRejectsUseOutsideItsOwningVertxContext(UniAsserter asserter) {
        var metadata = metadata("actor");
        var port = adapter(sessions, timeouts, locks());
        Throwable failure = new IllegalStateException("End controlled foreign-context probe");
        asserter.assertFailedWith(() -> port.execute(metadata, context -> {
            var owner = io.vertx.core.Vertx.currentContext();
            var checked = new CompletableFuture<Void>();
            CompletableFuture.runAsync(() -> assertThrows(IllegalStateException.class, context::tenantId))
                    .whenComplete((ignored, problem) -> owner.runOnContext(event -> {
                        if (problem == null) { checked.complete(null); }
                        else { checked.completeExceptionally(problem); }
                    }));
            return Uni.createFrom().completionStage(checked).chain(() -> Uni.createFrom().failure(failure));
        }), actual -> assertSame(failure, actual));
    }

    private Uni<Void> awaitRowWait() {
        return sessions.openSession().flatMap(session -> Multi.createBy().repeating().uni(() -> session.createNativeQuery("""
                select exists (select 1 from pg_locks waiting join pg_locks holding
                               on waiting.transactionid = holding.transactionid
                               where waiting.locktype = 'transactionid' and not waiting.granted and holding.granted)
                """, Boolean.class).getSingleResult()).until(Boolean.TRUE::equals).collect().last()
                .ifNoItem().after(Duration.ofSeconds(5)).fail().eventually(session::close)).replaceWithVoid();
    }

    private static IdentifierSchemeChanges sameName() {
        return new IdentifierSchemeChanges(FieldUpdate.present("Exact Name"), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
    }

    /** Supplies short finite budgets through the existing production timeout capability. */
    private record Budgets(Duration lockTimeout, Duration statementTimeout, Duration operationTimeout) implements PartyMutationConfiguration { }

    private HibernateReactiveIdentifierSchemeMutationAdapter adapter(Mutiny.SessionFactory factory, PartyMutationTimeouts limits,
            IdentifierSchemeMutationLocks locking) {
        return new HibernateReactiveIdentifierSchemeMutationAdapter(factory,
                new IdentifierSchemeMutationPersistence(mapper), new IdentifierSchemeReplayPersistence(codec), locking, limits);
    }

    private static IdentifierSchemeMutationLocks locks() { return new IdentifierSchemeMutationLocks(); }

    private static CreateIdentifierSchemeUseCase creation(IdentifierSchemeMutationPort port) {
        return new CreateIdentifierSchemeUseCase(port, new IdentifierRuleCatalog(), CLOCK);
    }

    private static ChangeIdentifierSchemeLifecycleUseCase lifecycle(IdentifierSchemeMutationPort port) {
        return new ChangeIdentifierSchemeLifecycleUseCase(port, new IdentifierRuleCatalog(), Clock.offset(CLOCK, Duration.ofSeconds(1)));
    }

    private static ChangeIdentifierSchemeLifecycleCommand action(RequestMetadata metadata, IdentifierSchemeResult source,
            IdentifierSchemeLifecycleAction action, Optional<String> key) {
        return new ChangeIdentifierSchemeLifecycleCommand(metadata, source.id(), source.version(), action, key);
    }

    private static CreateIdentifierSchemeCommand createCommand(RequestMetadata metadata, String code) {
        return new CreateIdentifierSchemeCommand(metadata, KEY, new IdentifierSchemeCreateInput(code, "GB", IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH, "Exact Name", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", null, null, false));
    }

    private static IdentifierScheme draft(String code, String actor) {
        return IdentifierScheme.create(new IdentifierSchemeId(UUID.randomUUID()), code, "GB", IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH, "Exact Name", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", null, null, false, CLOCK.instant(), actor);
    }

    private static String code() { return "Exact-" + UUID.randomUUID(); }
    private static RequestMetadata metadata(String actor) { return new RequestMetadata(new TenantId(UUID.randomUUID()), actor, UUID.randomUUID()); }
    private static IdentifierSchemeMutationOutcome applied(IdentifierScheme scheme) {
        return new IdentifierSchemeMutationOutcome(IdentifierSchemeResult.fromAggregate(scheme), IdentifierSchemeMutationOutcome.Disposition.APPLIED);
    }
    private static IdentifierSchemeIdempotencyRecordId identity(CreateIdentifierSchemeCommand command) {
        return new IdentifierSchemeIdempotencyRecordId(command.requestMetadata().tenantId().value(), command.effectiveRequest().operation(), command.idempotencyKey());
    }

    private Uni<List<Long>> counts(CreateIdentifierSchemeCommand command) {
        return sessions.withSession(session -> session.createNativeQuery("""
                select (select count(*) from identifier_schemes where code = :code),
                       (select count(*) from identifier_scheme_idempotency_records where tenant_id = :tenant),
                       (select count(*) from party_outbox_events where tenant_id = :tenant)
                """, Object[].class).setParameter("code", command.effectiveRequest().code())
                .setParameter("tenant", command.requestMetadata().tenantId().value()).getSingleResult()
                .map(row -> List.of(((Number) row[0]).longValue(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue())));
    }

    private static IdentifierSchemeMutationPort failingPort(IdentifierSchemeMutationPort port, AtomicReference<Mutiny.Session> opened,
            String checkpoint, Throwable failure) {
        return (metadata, work) -> port.execute(metadata, context -> work.apply(IdentifierSchemeMutationContext.class.cast(
                Proxy.newProxyInstance(IdentifierSchemeMutationContext.class.getClassLoader(), new Class<?>[]{IdentifierSchemeMutationContext.class},
                        (ignored, method, arguments) -> {
                            Object result;
                            try { result = method.invoke(context, arguments); }
                            catch (InvocationTargetException exception) { throw exception.getCause(); }
                            if (checkpoint.equals(method.getName()) && result instanceof Uni<?> operation) {
                                return operation.call(() -> opened.get().flush()).chain(() -> Uni.createFrom().failure(failure));
                            }
                            return result;
                        }))));
    }

    private Mutiny.SessionFactory capturingFactory(AtomicReference<Mutiny.Session> opened, AtomicInteger count) {
        return Mutiny.SessionFactory.class.cast(Proxy.newProxyInstance(Mutiny.SessionFactory.class.getClassLoader(),
                new Class<?>[]{Mutiny.SessionFactory.class}, (ignored, method, arguments) -> {
                    if (method.getName().equals("openSession") && (arguments == null || arguments.length == 0)) {
                        count.incrementAndGet();
                        return sessions.openSession().invoke(opened::set);
                    }
                    throw new AssertionError("Unexpected factory operation: " + method.getName());
                }));
    }

    private Uni<Void> awaitLockWait(long lockId) {
        return sessions.openSession().flatMap(session -> Multi.createBy().repeating().uni(() -> session.createNativeQuery("""
                select exists (select 1 from pg_locks where locktype = 'advisory' and not granted
                               and classid::bigint = :upper and objid::bigint = :lower and objsubid = 1)
                """, Boolean.class).setParameter("upper", lockId >>> 32).setParameter("lower", lockId & 0xffffffffL)
                .getSingleResult()).until(Boolean.TRUE::equals).collect().last()
                .ifNoItem().after(Duration.ofSeconds(5)).fail().eventually(session::close)).replaceWithVoid();
    }

    private static Uni<Void> awaitClosed(AtomicReference<Mutiny.Session> opened) {
        return Multi.createBy().repeating().uni(() -> Uni.createFrom().item(() -> !opened.get().isOpen())
                .onItem().delayIt().by(Duration.ofMillis(5))).until(Boolean.TRUE::equals).collect().last()
                .ifNoItem().after(Duration.ofSeconds(5)).fail().replaceWithVoid();
    }

    private static void assertFailure(Throwable failure, Class<? extends ApplicationFailure> type) {
        assertInstanceOf(type, assertInstanceOf(ApplicationException.class, failure).failure());
    }
}
