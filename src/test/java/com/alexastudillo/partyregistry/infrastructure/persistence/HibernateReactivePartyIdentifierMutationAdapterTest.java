package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierReason;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.PartyIdentifierPrimaryPolicy;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises committed identifier outcomes, sequential capability guards and owned rollback against PostgreSQL. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(120)
class HibernateReactivePartyIdentifierMutationAdapterTest {

    private static final Instant CREATED = LocalDate.of(2026, Month.OCTOBER, 9).atStartOfDay(ZoneOffset.UTC).toInstant();
    private static final Instant CHANGED = CREATED.plusSeconds(1);
    private static final String KEY = " identifier-context-key ";
    private static final String DIGEST = "a".repeat(64);
    private static final String ACTOR = "identifier-actor";

    @Inject Mutiny.SessionFactory sessions;
    @Inject PartyIdentifierMutationReader reader;
    @Inject PartyIdentifierMutationPersistence writes;
    @Inject PartyIdentifierMutationEffects effects;
    @Inject PartyMutationTimeouts timeouts;
    @Inject PartyIdentifierPersistenceMapper mapper;

    @Test
    @RunOnVertxContext
    void lazilyOwnsOneSessionAndEmitsOnlyCommittedCreationWithClosedContext(UniAsserter asserter) {
        var fixture = fixture();
        var opened = new AtomicReference<Mutiny.Session>();
        var count = new AtomicInteger();
        var escaped = new AtomicReference<PartyIdentifierMutationContext>();
        var port = adapter(capturingFactory(opened, count), timeouts);
        var operation = port.execute(fixture.metadata(), context -> {
            escaped.set(context);
            assertEquals(fixture.metadata().tenantId(), context.tenantId());
            return create(context, fixture);
        });
        assertEquals(0, count.get());
        asserter.execute(() -> seed(fixture, false));
        asserter.assertThat(() -> operation.call(result -> read(fixture).invoke(stored ->
                assertEquals(result.identifier().identifierId(), stored.identifierId()))), result -> {
            assertEquals(PartyIdentifierMutationOutcome.Disposition.APPLIED, result.disposition());
            assertEquals(0, result.identifier().version().value());
            assertEquals(1, count.get());
            assertFalse(opened.get().isOpen());
            assertThrows(IllegalStateException.class, escaped.get()::tenantId);
        });
        asserter.assertFailedWith(() -> escaped.get().findOwnedParty(fixture.identifier().partyId()), IllegalStateException.class);
        asserter.assertThat(() -> counts(fixture), row -> assertEquals(List.of(1L, 1L, 0L, 0L), row));
    }

    @Test
    @RunOnVertxContext
    void replayPrecedesOwnerAccessAndProhibitsNewWrites(UniAsserter asserter) {
        var fixture = fixture();
        var port = adapter(sessions, timeouts);
        asserter.execute(() -> seed(fixture, false));
        asserter.execute(() -> port.execute(fixture.metadata(), context -> create(context, fixture)));
        asserter.assertThat(() -> port.execute(fixture.metadata(), context -> create(context, fixture)), result -> {
            assertEquals(PartyIdentifierMutationOutcome.Disposition.REPLAYED, result.disposition());
            assertEquals(fixture.identifier().identifierId(), result.identifier().identifierId());
        });
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> context.serializeReplayKey(PartyIdentifierOperation.CREATE, KEY)
                .chain(() -> context.findCompleted(PartyIdentifierOperation.CREATE, KEY))
                .chain(() -> context.findOwnedParty(fixture.identifier().partyId())).replaceWith(applied(safe(fixture.identifier())))), IllegalStateException.class);
        asserter.assertThat(() -> counts(fixture), row -> assertEquals(List.of(1L, 1L, 0L, 0L), row));
    }

    @Test
    @RunOnVertxContext
    void withdrawalCommitsExactAuditCompletionAndEventTogether(UniAsserter asserter) {
        var fixture = fixture();
        var port = adapter(sessions, timeouts);
        asserter.execute(() -> seed(fixture, true));
        asserter.assertThat(() -> port.execute(fixture.metadata(), context -> reject(context, fixture)), result -> {
            assertEquals(PartyIdentifierStatus.REJECTED, result.identifier().status());
            assertEquals(1, result.identifier().version().value());
        });
        asserter.assertThat(() -> counts(fixture), row -> assertEquals(List.of(1L, 1L, 1L, 0L), row));
        asserter.assertThat(() -> sessions.withSession(session -> session.createNativeQuery(
                "SELECT reason_code, reason_detail, actor FROM party_identifier_action_audit WHERE tenant_id = :tenant", Object[].class)
                .setParameter("tenant", fixture.party().tenantId()).getSingleResult()), row -> {
            assertEquals("\u0000", PartyIdentifierReasonCodec.decode((byte[]) row[0], 64));
            assertEquals("", PartyIdentifierReasonCodec.decode((byte[]) row[1], 1000));
            assertEquals(ACTOR, row[2]);
        });
        asserter.assertThat(() -> sessions.withSession(session -> session.find(PartyEntity.class, fixture.party().id())), party -> assertEquals(0, party.version()));
    }

    @Test
    @RunOnVertxContext
    void rejectsOrderingScopeReuseConcurrentAndRecoveredCapabilities(UniAsserter asserter) {
        var fixture = fixture();
        var port = adapter(sessions, timeouts);
        asserter.execute(() -> seed(fixture, true));
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> context.findCompleted(PartyIdentifierOperation.CREATE, KEY)
                .replaceWith(applied(safe(fixture.identifier())))), IllegalStateException.class);
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> context.findOwnedParty(fixture.identifier().partyId())
                .chain(() -> context.serializeReplayKey(PartyIdentifierOperation.CREATE, KEY)).replaceWith(applied(safe(fixture.identifier())))), IllegalStateException.class);
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> lock(context, fixture)
                .chain(() -> context.serializeScope(fixture.identifier().partyId(), new IdentifierSchemeId(UUID.randomUUID())))
                .replaceWith(applied(safe(fixture.identifier())))), IllegalStateException.class);
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> Uni.combine().all().unis(
                context.serializeReplayKey(PartyIdentifierOperation.CREATE, KEY), context.serializeReplayKey(PartyIdentifierOperation.CREATE, "other"))
                .asTuple().replaceWith(applied(safe(fixture.identifier())))), IllegalStateException.class);
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> context.findCompleted(PartyIdentifierOperation.CREATE, KEY)
                .onFailure().recoverWithUni(_ -> context.findCompleted(PartyIdentifierOperation.CREATE, KEY))
                .replaceWith(applied(safe(fixture.identifier())))), IllegalStateException.class);
        asserter.assertThat(() -> counts(fixture), row -> assertEquals(List.of(1L, 0L, 0L, 0L), row));
    }

    @Test
    @RunOnVertxContext
    void rejectsInventedOutcomesMissingAuditAndMismatchedCompletion(UniAsserter asserter) {
        var fixture = fixture();
        var port = adapter(sessions, timeouts);
        asserter.execute(() -> seed(fixture, true));
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), _ -> Uni.createFrom().item(applied(safe(fixture.identifier())))), IllegalStateException.class);
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> lock(context, fixture)
                .chain(() -> context.findForUpdate(fixture.identifier().partyId(), fixture.identifier().identifierId()))
                .chain(found -> context.updateIdentifier(found.orElseThrow().reject(CHANGED, ACTOR), fixture.identifier().version()))
                .chain(result -> context.appendEnabledEvent(changed(result, fixture.metadata())).replaceWith(applied(result)))), IllegalStateException.class);
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> context.serializeReplayKey(PartyIdentifierOperation.REJECT, KEY)
                .chain(() -> context.findCompleted(PartyIdentifierOperation.REJECT, KEY)).chain(() -> lock(context, fixture))
                .chain(() -> context.findForUpdate(fixture.identifier().partyId(), fixture.identifier().identifierId()))
                .chain(found -> context.updateIdentifier(found.orElseThrow().reject(CHANGED, ACTOR), fixture.identifier().version()))
                .chain(result -> context.recordCompletion(PartyIdentifierOperation.REJECT, KEY, new CompletedPartyIdentifierMutation(
                        fixture.metadata().tenantId(), PartyIdentifierOperation.REJECT, DIGEST, result, fixture.identifier().version(), "wrong-actor", CHANGED))
                        .replaceWith(applied(result)))), IllegalStateException.class);
        asserter.assertThat(() -> read(fixture), original -> assertEquals(fixture.identifier().auditInfo(), original.auditInfo()));
        asserter.assertThat(() -> counts(fixture), row -> assertEquals(List.of(1L, 0L, 0L, 0L), row));
    }

    @Test
    @RunOnVertxContext
    void lateFailureRollsBackEveryFlushedEffectAndPreservesFailureIdentity(UniAsserter asserter) {
        var fixture = fixture();
        var opened = new AtomicReference<Mutiny.Session>();
        var port = adapter(capturingFactory(opened, new AtomicInteger()), timeouts);
        Throwable failure = new IllegalStateException("Controlled late acceptance failure");
        asserter.execute(() -> seed(fixture, true));
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> reject(context, fixture)
                .call(() -> opened.get().flush()).chain(() -> Uni.createFrom().failure(failure))), actual -> assertSame(failure, actual));
        asserter.assertThat(() -> read(fixture), original -> assertEquals(0, original.version().value()));
        asserter.assertThat(() -> counts(fixture), row -> assertEquals(List.of(1L, 0L, 0L, 0L), row));
        asserter.execute(() -> adapter(sessions, timeouts).execute(fixture.metadata(), context -> reject(context, fixture)));
    }

    @Test
    @RunOnVertxContext
    void configuresReadCommittedLocalLimitsAndClosesAfterWorkTimeout(UniAsserter asserter) {
        var fixture = fixture();
        var opened = new AtomicReference<Mutiny.Session>();
        var limits = new PartyMutationTimeouts(new Budgets(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofSeconds(3)));
        var port = adapter(capturingFactory(opened, new AtomicInteger()), limits);
        asserter.execute(() -> seed(fixture, false));
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> opened.get().createNativeQuery("""
                SELECT current_setting('transaction_isolation'), current_setting('transaction_read_only'),
                       current_setting('lock_timeout'), current_setting('statement_timeout')
                """, Object[].class).getSingleResult().invoke(row -> {
            assertEquals("read committed", row[0]); assertEquals("off", row[1]);
            assertEquals("300ms", row[2]); assertEquals("2s", row[3]);
        }).chain(() -> create(context, fixture)).chain(() -> Uni.createFrom().nothing())), failure -> {
            assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, assertInstanceOf(ApplicationException.class, failure).failure());
            assertFalse(opened.get().isOpen());
        });
        asserter.assertThat(() -> counts(fixture), row -> assertEquals(List.of(0L, 0L, 0L, 0L), row));
        asserter.execute(() -> adapter(sessions, timeouts).execute(fixture.metadata(), context -> create(context, fixture)));
    }

    @Test
    @RunOnVertxContext
    void cancellationClosesTheScopeBeforePoolReuse(UniAsserter asserter) {
        var fixture = fixture();
        var opened = new AtomicReference<Mutiny.Session>();
        var reached = new CompletableFuture<Void>();
        var escaped = new AtomicReference<PartyIdentifierMutationContext>();
        var observer = new RecordingOperationObserver();
        var port = new HibernateReactivePartyIdentifierMutationAdapter(capturingFactory(opened, new AtomicInteger()), reader, writes,
                new PartyIdentifierMutationLocks(), effects, timeouts, observer);
        asserter.execute(() -> seed(fixture, false));
        asserter.execute(() -> {
            UniAssertSubscriber<PartyIdentifierMutationOutcome> subscriber = port.execute(fixture.metadata(), context -> {
                escaped.set(context);
                return create(context, fixture).call(() -> Uni.createFrom().<Void>emitter(_ -> reached.complete(null)));
            }).subscribe().withSubscriber(UniAssertSubscriber.create());
            return Uni.createFrom().completionStage(reached).ifNoItem().after(Duration.ofSeconds(10)).fail()
                    .invoke(subscriber::cancel).chain(() -> awaitClosed(opened)).invoke(() -> {
                        assertNull(subscriber.getItem()); assertThrows(IllegalStateException.class, escaped.get()::tenantId);
                        assertEquals(List.of(new RecordingOperationObserver.CompletedCall(fixture.metadata(),
                                ObservedOperation.TRANSACTION_PARTY_IDENTIFIER_MUTATION, OperationOutcome.CANCELLED)), observer.completedCalls());
                    }).eventually(subscriber::cancel);
        });
        asserter.assertThat(() -> counts(fixture), row -> assertEquals(List.of(0L, 0L, 0L, 0L), row));
        asserter.execute(() -> adapter(sessions, timeouts).execute(fixture.metadata(), context -> create(context, fixture)));
    }

    @Test
    @RunOnVertxContext
    void rejectsForeignContextAndWrongEventAttributionWithoutCommit(UniAsserter asserter) {
        var fixture = fixture();
        var port = adapter(sessions, timeouts);
        Throwable controlled = new IllegalStateException("End foreign-context probe");
        asserter.execute(() -> seed(fixture, true));
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> {
            var owner = io.vertx.core.Vertx.currentContext();
            var checked = new CompletableFuture<Void>();
            CompletableFuture.runAsync(() -> assertThrows(IllegalStateException.class, context::tenantId))
                    .whenComplete((_, problem) -> owner.runOnContext(_ -> {
                        if (problem == null) { checked.complete(null); }
                        else { checked.completeExceptionally(problem); }
                    }));
            return Uni.createFrom().completionStage(checked).chain(() -> Uni.createFrom().failure(controlled));
        }), actual -> assertSame(controlled, actual));
        var wrongAttribution = new RequestMetadata(fixture.metadata().tenantId(), "other-actor", UUID.randomUUID());
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> lock(context, fixture)
                .chain(() -> context.findForUpdate(fixture.identifier().partyId(), fixture.identifier().identifierId()))
                .chain(found -> context.updateIdentifier(found.orElseThrow().reject(CHANGED, ACTOR), fixture.identifier().version()))
                .chain(result -> context.appendEnabledEvent(changed(result, wrongAttribution)).replaceWith(applied(result)))), IllegalStateException.class);
        asserter.assertThat(() -> counts(fixture), row -> assertEquals(List.of(1L, 0L, 0L, 0L), row));
    }

    @Test
    @RunOnVertxContext
    void primaryReplacementRequiresOneMatchingEventForEachWrittenRevision(UniAsserter asserter) {
        var fixture = fixture();
        var target = fixture.identifier().verify(CREATED, ACTOR);
        var former = otherIdentifier(fixture).verify(CREATED, ACTOR).selectPrimary(CREATED, ACTOR);
        var port = adapter(sessions, timeouts);
        asserter.execute(() -> seed(fixture, false));
        asserter.execute(() -> sessions.withTransaction((session, _) -> session.persist(mapper.toEntity(target))
                .call(() -> session.persist(mapper.toEntity(former))).call(session::flush)));
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), context -> replace(context, fixture, target, false)), IllegalStateException.class);
        asserter.assertThat(() -> read(fixture), stored -> { assertEquals(1, stored.version().value()); assertFalse(stored.isPrimary()); });
        asserter.assertThat(() -> port.execute(fixture.metadata(), context -> replace(context, fixture, target, true)), result -> {
            assertEquals(2, result.identifier().version().value()); assertEquals(true, result.identifier().isPrimary());
        });
        asserter.assertThat(() -> sessions.withSession(session -> session.find(PartyIdentifierEntity.class, former.identifierId().value())), stored -> {
            assertEquals(3, stored.version()); assertFalse(stored.isPrimary());
        });
    }

    @Test
    @RunOnVertxContext
    void actualLockAndStatementTimeoutsRollBackBeforeSessionReuse(UniAsserter asserter) {
        var fixture = fixture();
        var opened = new AtomicReference<Mutiny.Session>();
        var limits = new PartyMutationTimeouts(new Budgets(Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofSeconds(3)));
        var port = adapter(capturingFactory(opened, new AtomicInteger()), limits);
        var identity = new PartyIdentifierIdempotencyRecordId(fixture.party().tenantId(), PartyIdentifierOperation.CREATE.namespace(), KEY);
        asserter.execute(() -> seed(fixture, false));
        asserter.assertFailedWith(() -> sessions.openSession().flatMap(holder -> holder.withTransaction(_ ->
                new PartyIdentifierMutationLocks().serializeReplayKey(holder, identity)
                        .chain(() -> port.execute(fixture.metadata(), context -> create(context, fixture)))).eventually(holder::close)), failure -> {
            assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, assertInstanceOf(ApplicationException.class, failure).failure());
            assertFalse(opened.get().isOpen());
        });
        asserter.assertFailedWith(() -> port.execute(fixture.metadata(), _ -> opened.get().createNativeQuery(
                "SELECT 1 FROM pg_sleep(1)", Integer.class).getSingleResult().replaceWith(applied(safe(fixture.identifier())))), failure -> {
            assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, assertInstanceOf(ApplicationException.class, failure).failure());
            assertFalse(opened.get().isOpen());
        });
        asserter.assertThat(() -> counts(fixture), row -> assertEquals(List.of(0L, 0L, 0L, 0L), row));
        asserter.assertThat(() -> sessions.withSession(session -> session.createNativeQuery(
                "SELECT current_setting('lock_timeout'), current_setting('statement_timeout')", Object[].class).getSingleResult()), row -> {
            assertEquals("0", row[0]); assertEquals("0", row[1]);
        });
        asserter.execute(() -> adapter(sessions, timeouts).execute(fixture.metadata(), context -> create(context, fixture)));
    }

    private static PartyIdentifier otherIdentifier(Fixture fixture) {
        return PartyIdentifier.builder().identifierId(new PartyIdentifierId(UUID.randomUUID())).tenantId(fixture.metadata().tenantId())
                .partyId(fixture.identifier().partyId()).identifierSchemeId(fixture.identifier().identifierSchemeId())
                .protectedValue(new ProtectedIdentifierValue("synthetic-former-ciphertext", 1, UUID.randomUUID().toString().replace("-", "").repeat(2), "***5678", new IdentifierRuleVersion(1)))
                .primary(false).status(PartyIdentifierStatus.PENDING_VERIFICATION).version(PartyIdentifierVersion.initial()).auditInfo(AuditInfo.initial(CREATED, ACTOR)).build();
    }

    private static Uni<PartyIdentifierMutationOutcome> replace(PartyIdentifierMutationContext context, Fixture fixture, PartyIdentifier target, boolean completeEvents) {
        return lock(context, fixture).chain(() -> context.findForUpdate(target.partyId(), target.identifierId()))
                .chain(found -> context.findCurrentPrimary(target.partyId(), target.identifierSchemeId()).map(primary ->
                        new PartyIdentifierPrimaryPolicy().planSelection(found.orElseThrow(), primary.orElse(null), true, CHANGED, ACTOR)))
                .chain(plan -> {
                    var former = plan.formerPrimary();
                    if (former == null) { return Uni.createFrom().failure(new AssertionError("Replacement fixture must contain a former primary")); }
                    return context.updateIdentifier(former, new PartyIdentifierVersion(former.version().value() - 1))
                            .call(result -> completeEvents ? context.appendEnabledEvent(primaryEvent(result, fixture.metadata())) : Uni.createFrom().voidItem())
                            .chain(() -> context.updateIdentifier(plan.candidate(), target.version()))
                            .call(result -> context.appendEnabledEvent(primaryEvent(result, fixture.metadata()))).map(HibernateReactivePartyIdentifierMutationAdapterTest::applied);
                });
    }

    private static PartyIdentifierChangedOutboxCandidate primaryEvent(PartyIdentifierResult result, RequestMetadata metadata) {
        return new PartyIdentifierChangedOutboxCandidate(UUID.randomUUID(), metadata.tenantId(), result.identifierId(), result.version(), result.partyId(), result.schemeCode(),
                result.status(), result.isPrimary(), PartyIdentifierChangedOutboxCandidate.Kind.PRIMARY_CHANGED, CHANGED, metadata.processId(), metadata.userId());
    }

    private HibernateReactivePartyIdentifierMutationAdapter adapter(Mutiny.SessionFactory factory, PartyMutationTimeouts limits) {
        return new HibernateReactivePartyIdentifierMutationAdapter(factory, reader, writes, new PartyIdentifierMutationLocks(), effects, limits);
    }

    private static Uni<PartyIdentifierMutationOutcome> create(PartyIdentifierMutationContext context, Fixture fixture) {
        return context.serializeReplayKey(PartyIdentifierOperation.CREATE, KEY).chain(() -> context.findCompleted(PartyIdentifierOperation.CREATE, KEY))
                .chain(found -> found.isPresent() ? Uni.createFrom().item(new PartyIdentifierMutationOutcome(found.orElseThrow().result(), PartyIdentifierMutationOutcome.Disposition.REPLAYED))
                        : context.findOwnedParty(fixture.identifier().partyId()).chain(() -> context.findSchemeIdByCode(IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE))
                                .chain(() -> context.serializeScope(fixture.identifier().partyId(), fixture.identifier().identifierSchemeId()))
                                .chain(() -> context.findSchemeForRegistration(fixture.identifier().identifierSchemeId()))
                                .chain(() -> context.insertIdentifier(fixture.identifier())).chain(result -> context.recordCompletion(PartyIdentifierOperation.CREATE, KEY,
                                        new CompletedPartyIdentifierMutation(fixture.metadata().tenantId(), PartyIdentifierOperation.CREATE, DIGEST, result, null, ACTOR, CREATED))
                                        .chain(() -> context.appendEnabledEvent(new PartyIdentifierCreatedOutboxCandidate(UUID.randomUUID(), fixture.metadata().tenantId(),
                                                result.identifierId(), result.version(), result.partyId(), result.schemeCode(), result.status(), CREATED, fixture.metadata().processId(), ACTOR)))
                                        .replaceWith(applied(result))));
    }

    private static Uni<PartyIdentifierMutationOutcome> reject(PartyIdentifierMutationContext context, Fixture fixture) {
        return context.serializeReplayKey(PartyIdentifierOperation.REJECT, KEY).chain(() -> context.findCompleted(PartyIdentifierOperation.REJECT, KEY))
                .chain(() -> lock(context, fixture)).chain(() -> context.findForUpdate(fixture.identifier().partyId(), fixture.identifier().identifierId()))
                .chain(found -> context.updateIdentifier(found.orElseThrow().reject(CHANGED, ACTOR), fixture.identifier().version()))
                .chain(result -> context.appendActionAudit(new PartyIdentifierActionAudit(fixture.metadata(), result.partyId(), result.identifierId(), result.version(),
                        PartyIdentifierOperation.REJECT, CHANGED, new PartyIdentifierReason("\u0000", "")))
                        .chain(() -> context.recordCompletion(PartyIdentifierOperation.REJECT, KEY, new CompletedPartyIdentifierMutation(fixture.metadata().tenantId(),
                                PartyIdentifierOperation.REJECT, DIGEST, result, fixture.identifier().version(), ACTOR, CHANGED)))
                        .chain(() -> context.appendEnabledEvent(changed(result, fixture.metadata()))).replaceWith(applied(result)));
    }

    private static Uni<Void> lock(PartyIdentifierMutationContext context, Fixture fixture) {
        return context.findOwnedParty(fixture.identifier().partyId()).chain(() -> context.findIdentifierSchemeId(fixture.identifier().partyId(), fixture.identifier().identifierId()))
                .chain(() -> context.serializeScope(fixture.identifier().partyId(), fixture.identifier().identifierSchemeId()));
    }

    private static PartyIdentifierChangedOutboxCandidate changed(PartyIdentifierResult result, RequestMetadata metadata) {
        return new PartyIdentifierChangedOutboxCandidate(UUID.randomUUID(), metadata.tenantId(), result.identifierId(), result.version(), result.partyId(), result.schemeCode(),
                result.status(), result.isPrimary(), PartyIdentifierChangedOutboxCandidate.Kind.REJECTED, CHANGED, metadata.processId(), metadata.userId());
    }

    private Uni<Void> seed(Fixture fixture, boolean withIdentifier) {
        return sessions.withTransaction((session, _) -> session.persist(fixture.party())
                .call(() -> withIdentifier ? session.persist(mapper.toEntity(fixture.identifier())) : Uni.createFrom().voidItem()).call(session::flush));
    }

    private Uni<PartyIdentifier> read(Fixture fixture) {
        return sessions.withSession(session -> session.find(PartyIdentifierEntity.class, fixture.identifier().identifierId().value()).map(mapper::toDomain));
    }

    private Uni<List<Long>> counts(Fixture fixture) {
        return sessions.withSession(session -> session.createNativeQuery("""
                SELECT (SELECT count(*) FROM party_identifiers WHERE tenant_id = :tenant),
                       (SELECT count(*) FROM party_identifier_idempotency_records WHERE tenant_id = :tenant),
                       (SELECT count(*) FROM party_identifier_action_audit WHERE tenant_id = :tenant),
                       (SELECT count(*) FROM party_outbox_events WHERE tenant_id = :tenant)
                """, Object[].class).setParameter("tenant", fixture.party().tenantId()).getSingleResult()
                .map(row -> List.of(((Number) row[0]).longValue(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue(), ((Number) row[3]).longValue())));
    }

    private Mutiny.SessionFactory capturingFactory(AtomicReference<Mutiny.Session> opened, AtomicInteger count) {
        return Mutiny.SessionFactory.class.cast(Proxy.newProxyInstance(Mutiny.SessionFactory.class.getClassLoader(), new Class<?>[]{Mutiny.SessionFactory.class},
                (_, method, arguments) -> {
                    if (method.getName().equals("openSession") && (arguments == null || arguments.length == 0)) {
                        count.incrementAndGet(); return sessions.openSession().invoke(opened::set);
                    }
                    throw new AssertionError("Unexpected session factory operation");
                }));
    }

    private static Uni<Void> awaitClosed(AtomicReference<Mutiny.Session> opened) {
        return Multi.createBy().repeating().uni(() -> Uni.createFrom().item(() -> !opened.get().isOpen()).onItem().delayIt().by(Duration.ofMillis(5)))
                .until(Boolean.TRUE::equals).collect().last().ifNoItem().after(Duration.ofSeconds(5)).fail().replaceWithVoid();
    }

    private static PartyIdentifierMutationOutcome applied(PartyIdentifierResult result) { return new PartyIdentifierMutationOutcome(result, PartyIdentifierMutationOutcome.Disposition.APPLIED); }

    private static PartyIdentifierResult safe(PartyIdentifier source) {
        return new PartyIdentifierResult(source.identifierId(), source.partyId(), source.identifierSchemeId(), IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE,
                source.protectedValue().maskedValue(), source.status(), source.isPrimary(), source.issuerCode(), source.issuedOn(), source.expiresOn(), source.verifiedAt(),
                source.verifiedBy(), source.version(), source.auditInfo().createdAt(), source.auditInfo().updatedAt());
    }

    private static Fixture fixture() {
        var audit = AuditInfo.initial(CREATED, ACTOR);
        var party = new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.NATURAL_PERSON, "Context owner", PartyRecordStatus.DRAFT, audit, 0);
        party.attachNaturalPersonDetails(new NaturalPersonDetailsEntity(party.id(), new NaturalPersonDetails("Context", "Owner", null, null, null, null), audit));
        var tenant = new TenantId(party.tenantId());
        var identifier = PartyIdentifier.builder().identifierId(new PartyIdentifierId(UUID.randomUUID())).tenantId(tenant).partyId(new PartyId(party.id()))
                .identifierSchemeId(new IdentifierSchemeId(IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID))
                .protectedValue(new ProtectedIdentifierValue("synthetic-context-ciphertext", 1, UUID.randomUUID().toString().replace("-", "").repeat(2), "***1234", new IdentifierRuleVersion(1)))
                .primary(false).status(PartyIdentifierStatus.PENDING_VERIFICATION).version(PartyIdentifierVersion.initial()).auditInfo(audit).build();
        return new Fixture(party, identifier, new RequestMetadata(tenant, ACTOR, UUID.randomUUID()));
    }

    /** Binds independent storage fixtures to the accepted tenant and actor. */
    private record Fixture(PartyEntity party, PartyIdentifier identifier, RequestMetadata metadata) { }

    /** Uses finite short test deadlines without changing the production timeout configuration. */
    private record Budgets(Duration lockTimeout, Duration statementTimeout, Duration operationTimeout) implements PartyMutationConfiguration { }
}
