package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ChangePartyIdentifierLifecycleCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreationRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierReason;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the complete lifecycle matrix, replay-first identity, private reason audit and same-context safe acceptance. */
class ChangePartyIdentifierLifecycleUseCaseTest {
    private static final LocalDate DATE = PartyIdentifierFixtures.EVALUATED_ON;
    private static final Instant NOW = DATE.atTime(23, 59, 59, 123456789).toInstant(ZoneOffset.UTC);
    private static final Instant ACCEPTED = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final List<PartyIdentifierOperation> ACTIONS = List.of(PartyIdentifierOperation.VERIFY, PartyIdentifierOperation.REJECT, PartyIdentifierOperation.REVOKE);

    @Test
    void everyMatrixEntryForBothTypesHasOnlyItsDeclaredTransitionOrNeutralConflict() {
        for (PartyType type : PartyType.values()) {
            for (var state : PartyIdentifierStatus.values()) {
                for (var action : ACTIONS) {
                    var fixture = new Harness(type, state, false, 3, DATE);
                    var command = fixture.command(action, null, PartyIdentifierReason.none(), 3);
                    boolean permitted = state == PartyIdentifierStatus.PENDING_VERIFICATION && action != PartyIdentifierOperation.REVOKE
                            || state == PartyIdentifierStatus.VERIFIED && action == PartyIdentifierOperation.REVOKE;
                    if (permitted) {
                        var result = item(fixture.useCase().execute(command)).identifier();
                        assertEquals(resulting(action), result.status()); assertEquals(4, result.version().value());
                        assertPreserved(fixture.current, fixture.candidate); assertEquals(1, fixture.events.size());
                        assertEquals(action == PartyIdentifierOperation.VERIFY ? 0 : 1, fixture.audits.size());
                    } else {
                        assertInstanceOf(ApplicationFailure.InvalidIdentifierLifecycle.class, failure(fixture.useCase().execute(command)).failure());
                        assertNull(fixture.candidate); assertTrue(fixture.events.isEmpty()); assertTrue(fixture.audits.isEmpty());
                    }
                }
            }
        }
    }

    @Test
    void verificationUsesOneUtcInstantAndAcceptedActorWithoutCatalogOrProtection() {
        var fixture = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.PENDING_VERIFICATION, true, 3, DATE);
        var result = item(fixture.useCase().execute(fixture.command(PartyIdentifierOperation.VERIFY, null, PartyIdentifierReason.none(), 3))).identifier();
        assertEquals(ACCEPTED, result.verifiedAt()); assertEquals(fixture.metadata.userId(), result.verifiedBy());
        assertEquals(result.verifiedAt(), result.updatedAt()); assertTrue(result.isPrimary()); assertEquals(1, fixture.clock.reads);
        assertEquals(List.of("transaction", "owner", "identity", "scope", "target", "primary", "update", "event"), fixture.order);
        assertEquals(fixture.current.auditInfo().createdAt(), result.createdAt());
    }

    @Test
    void provisionalConflictIncludesPastDateVerifiedOwnersAndLeavesBothUnchanged() {
        var fixture = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.PENDING_VERIFICATION, true, 3, DATE);
        fixture.primary = primary(fixture.current, DATE.minusDays(1));
        var failure = failure(fixture.useCase().execute(fixture.command(PartyIdentifierOperation.VERIFY, "key", PartyIdentifierReason.none(), 3)));
        assertInstanceOf(ApplicationFailure.PrimaryIdentifierConflict.class, failure.failure());
        assertNull(fixture.candidate); assertTrue(fixture.completed.isEmpty()); assertTrue(fixture.events.isEmpty());
        var independent = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.PENDING_VERIFICATION, false, 3, DATE);
        independent.primary = fixture.primary;
        assertEquals(PartyIdentifierStatus.VERIFIED, item(independent.useCase().execute(independent.command(PartyIdentifierOperation.VERIFY, null, PartyIdentifierReason.none(), 3))).identifier().status());
    }

    @Test
    void stateCapacityAndValidityPrecedePrimaryLookupAndItsPossibleFailure() {
        var terminal = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.REVOKED, true, Long.MAX_VALUE, DATE.minusDays(1));
        assertInstanceOf(ApplicationFailure.InvalidIdentifierLifecycle.class, failure(terminal.useCase().execute(terminal.command(PartyIdentifierOperation.VERIFY, null, PartyIdentifierReason.none(), Long.MAX_VALUE))).failure());
        var exhausted = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.PENDING_VERIFICATION, true, Long.MAX_VALUE, DATE);
        assertInstanceOf(ApplicationFailure.IdentifierVersionExhausted.class, failure(exhausted.useCase().execute(exhausted.command(PartyIdentifierOperation.VERIFY, null, PartyIdentifierReason.none(), Long.MAX_VALUE))).failure());
        var expired = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.PENDING_VERIFICATION, true, 3, DATE.minusDays(1));
        var violation = assertInstanceOf(ApplicationFailure.IdentifierValidationFailure.class, failure(expired.useCase().execute(expired.command(PartyIdentifierOperation.VERIFY, null, PartyIdentifierReason.none(), 3))).failure());
        assertEquals(DomainViolation.IDENTIFIER_EXPIRED, violation.violation());
        for (var fixture : List.of(terminal, exhausted, expired)) { assertFalse(fixture.order.contains("primary")); assertNull(fixture.candidate); }
    }

    @Test
    void withdrawalsAcceptPastDatesRetainVerificationAndStoreExactReasonsWithoutKeys() {
        var reasons = new PartyIdentifierReason("", " \u0000\n😀 ");
        for (var action : List.of(PartyIdentifierOperation.REJECT, PartyIdentifierOperation.REVOKE)) {
            var state = action == PartyIdentifierOperation.REJECT ? PartyIdentifierStatus.PENDING_VERIFICATION : PartyIdentifierStatus.VERIFIED;
            var fixture = new Harness(PartyType.NATURAL_PERSON, state, true, 3, DATE.minusDays(1));
            var result = item(fixture.useCase().execute(fixture.command(action, null, reasons, 3))).identifier();
            assertEquals(resulting(action), result.status()); assertFalse(result.isPrimary());
            assertEquals(fixture.current.verifiedAt(), result.verifiedAt()); assertEquals(fixture.current.verifiedBy(), result.verifiedBy());
            var audit = fixture.audits.getFirst(); assertEquals(reasons, audit.reason()); assertSame(fixture.metadata, audit.requestMetadata());
            assertEquals(result.version(), audit.resultingVersion()); assertEquals(ACCEPTED, audit.occurredAt());
            assertTrue(fixture.completed.isEmpty()); assertFalse(fixture.order.contains("fingerprint"));
        }
    }

    @Test
    void keyedActionsCompleteAfterAuditAndBeforeEventUsingOriginalVersionAndAttribution() {
        var fixture = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.VERIFIED, true, 3, DATE);
        var command = fixture.command(PartyIdentifierOperation.REVOKE, " exact-key ", new PartyIdentifierReason("code", "detail"), 3);
        var result = item(fixture.useCase().execute(command));
        assertEquals(List.of("fingerprint", "transaction", "key", "completed", "owner", "identity", "scope", "target", "update", "audit", "completion", "event"), fixture.order);
        var completion = fixture.completed.orElseThrow(); assertEquals(command.expectedVersion(), completion.expectedVersion());
        assertEquals(fixture.metadata.userId(), completion.acceptedBy()); assertEquals(ACCEPTED, completion.acceptedAt()); assertEquals(result.identifier(), completion.result());
        var event = assertInstanceOf(PartyIdentifierChangedOutboxCandidate.class, fixture.events.getFirst());
        assertEquals(PartyIdentifierChangedOutboxCandidate.Kind.REVOKED, event.kind()); assertEquals(result.identifier().version(), event.identifierVersion());
        assertEquals(fixture.metadata.processId(), event.correlationId());
    }

    @Test
    void historicalReplayPrecedesAbsentOwnerTargetVersionCurrentStateAndClock() {
        var fixture = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.PENDING_VERIFICATION, false, 3, DATE);
        var command = fixture.command(PartyIdentifierOperation.VERIFY, "key", PartyIdentifierReason.none(), 3);
        var original = item(fixture.useCase().execute(command)); fixture.order.clear(); fixture.missingOwner = true; fixture.missingTarget = true;
        var metadata = new RequestMetadata(fixture.metadata.tenantId(), "retry-actor", UUID.randomUUID());
        var retry = new ChangePartyIdentifierLifecycleCommand(metadata, command.partyId(), command.identifierId(), command.expectedVersion(), command.operation(), command.idempotencyKey(), command.reason());
        var result = item(fixture.useCase().execute(retry));
        assertEquals(original.identifier(), result.identifier()); assertEquals(PartyIdentifierMutationOutcome.Disposition.REPLAYED, result.disposition());
        assertEquals(List.of("fingerprint", "transaction", "key", "completed"), fixture.order); assertEquals(1, fixture.clock.reads);
        assertEquals(1, fixture.events.size()); assertTrue(fixture.audits.isEmpty());
    }

    @Test
    void changedEffectiveDigestConflictsBeforeOwnerAndDoesNotExposeTheAcceptedResult() {
        var fixture = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.PENDING_VERIFICATION, false, 3, DATE);
        var command = fixture.command(PartyIdentifierOperation.REJECT, "key", PartyIdentifierReason.none(), 3);
        item(fixture.useCase().execute(command)); fixture.order.clear(); fixture.digest = "b".repeat(64); fixture.missingOwner = true;
        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, failure(fixture.useCase().execute(command)).failure());
        assertEquals(List.of("fingerprint", "transaction", "key", "completed"), fixture.order); assertEquals(1, fixture.audits.size()); assertEquals(1, fixture.events.size());
    }

    @Test
    void ownerAndTargetAbsencePrecedeStaleVersionWhichPrecedesState() {
        for (int absent = 0; absent < 3; absent++) {
            var fixture = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.REVOKED, false, 3, DATE.minusDays(1));
            fixture.missingOwner = absent == 0; fixture.missingIdentity = absent == 1; fixture.missingTarget = absent == 2;
            var failure = failure(fixture.useCase().execute(fixture.command(PartyIdentifierOperation.VERIFY, null, PartyIdentifierReason.none(), 0)));
            if (absent == 0) { assertInstanceOf(ApplicationFailure.PartyNotFound.class, failure.failure()); }
            else { assertInstanceOf(ApplicationFailure.IdentifierNotFound.class, failure.failure()); }
            assertEquals(0, fixture.clock.reads); assertNull(fixture.candidate);
        }
        var stale = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.REVOKED, false, 3, DATE);
        assertInstanceOf(ApplicationFailure.IdentifierVersionMismatch.class, failure(stale.useCase().execute(stale.command(PartyIdentifierOperation.VERIFY, null, PartyIdentifierReason.none(), 0))).failure());
        assertEquals(0, stale.clock.reads);
    }

    @Test
    void failedGuardedWriteDoesNotConsumeAKeyAndCanBeRetriedWithoutPartialEffects() {
        var fixture = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.PENDING_VERIFICATION, false, 3, null);
        var command = fixture.command(PartyIdentifierOperation.REJECT, "key", PartyIdentifierReason.none(), 3);
        fixture.writeFailure = new IllegalStateException("Controlled guarded lifecycle failure");
        var error = fixture.useCase().execute(command).subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(Duration.ofSeconds(2)).getFailure();
        assertSame(fixture.writeFailure, error); assertTrue(fixture.completed.isEmpty()); assertTrue(fixture.audits.isEmpty()); assertTrue(fixture.events.isEmpty());
        fixture.writeFailure = null; assertEquals(PartyIdentifierStatus.REJECTED, item(fixture.useCase().execute(command)).identifier().status());
    }

    @Test
    void pipelinesAreLazyAndUnkeyedVerifyDoesNotComputeAnyFingerprint() {
        var fixture = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.PENDING_VERIFICATION, false, 3, DATE);
        var operation = fixture.useCase().execute(fixture.command(PartyIdentifierOperation.VERIFY, null, PartyIdentifierReason.none(), 3));
        assertTrue(fixture.order.isEmpty()); assertEquals(0, fixture.clock.reads);
        assertEquals(PartyIdentifierStatus.VERIFIED, item(operation).identifier().status()); assertEquals(1, fixture.clock.reads);
        assertFalse(fixture.order.contains("fingerprint"));
    }

    private static PartyIdentifierStatus resulting(PartyIdentifierOperation operation) { return switch (operation) {
        case VERIFY -> PartyIdentifierStatus.VERIFIED; case REJECT -> PartyIdentifierStatus.REJECTED; case REVOKE -> PartyIdentifierStatus.REVOKED;
        default -> throw new AssertionError("Unexpected lifecycle fixture action"); }; }
    private static void assertPreserved(PartyIdentifier before, PartyIdentifier after) {
        assertEquals(before.identifierId(), after.identifierId()); assertEquals(before.partyId(), after.partyId()); assertEquals(before.tenantId(), after.tenantId());
        assertEquals(before.identifierSchemeId(), after.identifierSchemeId()); assertSame(before.protectedValue(), after.protectedValue());
        assertEquals(before.issuerCode(), after.issuerCode()); assertEquals(before.issuedOn(), after.issuedOn()); assertEquals(before.expiresOn(), after.expiresOn());
        assertEquals(before.auditInfo().createdAt(), after.auditInfo().createdAt()); assertEquals(before.auditInfo().createdBy(), after.auditInfo().createdBy());
    }
    private static PartyIdentifier primary(PartyIdentifier source, LocalDate expiration) {
        return PartyIdentifier.builder().identifierId(new PartyIdentifierId(UUID.randomUUID())).tenantId(source.tenantId()).partyId(source.partyId())
                .identifierSchemeId(source.identifierSchemeId()).protectedValue(source.protectedValue()).issuerCode(source.issuerCode()).issuedOn(source.issuedOn())
                .expiresOn(expiration).primary(true).status(PartyIdentifierStatus.VERIFIED).verifiedAt(PartyIdentifierFixtures.CREATED_AT).verifiedBy("fixture-primary")
                .version(new PartyIdentifierVersion(5)).auditInfo(source.auditInfo()).build();
    }
    private static <T> T item(Uni<T> operation) { return operation.subscribe().withSubscriber(UniAssertSubscriber.create()).awaitItem(Duration.ofSeconds(2)).assertCompleted().getItem(); }
    private static ApplicationException failure(Uni<?> operation) { return assertInstanceOf(ApplicationException.class, operation.subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(Duration.ofSeconds(2)).getFailure()); }

    /** Records owned lifecycle capabilities while forbidding catalog admission, creation and cross-feature effects. */
    private static final class Harness implements PartyIdentifierMutationContext, PartyIdentifierFingerprintPort {
        final PartyType type;
        final PartyIdentifier current;
        final RequestMetadata metadata = new RequestMetadata(PartyIdentifierFixtures.TENANT, "lifecycle-operator", UUID.randomUUID());
        final List<String> order = new ArrayList<>();
        final List<OutboxEventCandidate> events = new ArrayList<>();
        final List<PartyIdentifierActionAudit> audits = new ArrayList<>();
        final CountingClock clock = new CountingClock();
        PartyIdentifier candidate;
        PartyIdentifier primary;
        Optional<CompletedPartyIdentifierMutation> completed = Optional.empty();
        String digest = "a".repeat(64);
        Throwable writeFailure;
        boolean missingOwner;
        boolean missingIdentity;
        boolean missingTarget;
        Harness(PartyType type, PartyIdentifierStatus status, boolean flag, long version, LocalDate expiration) { this.type = type; current = PartyIdentifierFixtures.identifier(type, status, flag, version, expiration); }
        ChangePartyIdentifierLifecycleUseCase useCase() {
            PartyIdentifierMutationPort mutations = (request, work) -> Uni.createFrom().deferred(() -> { order.add("transaction"); return work.apply(this); });
            return new ChangePartyIdentifierLifecycleUseCase(mutations, this, clock, new RecordingOperationObserver());
        }
        ChangePartyIdentifierLifecycleCommand command(PartyIdentifierOperation action, String key, PartyIdentifierReason reason, long version) { return new ChangePartyIdentifierLifecycleCommand(metadata, current.partyId(), current.identifierId(), new PartyIdentifierVersion(version), action, key, reason); }
        @Override public String fingerprint(TenantId tenant, PartyIdentifierActionRequest request) { order.add("fingerprint"); return digest; }
        @Override public String fingerprint(TenantId tenant, PartyIdentifierCreationRequest request) { throw unexpected(); }
        @Override public boolean matches(String stored, String next) { return stored.equals(next); }
        @Override public TenantId tenantId() { return metadata.tenantId(); }
        @Override public Uni<Void> serializeReplayKey(PartyIdentifierOperation operation, String key) { order.add("key"); return Uni.createFrom().voidItem(); }
        @Override public Uni<Optional<CompletedPartyIdentifierMutation>> findCompleted(PartyIdentifierOperation operation, String key) { order.add("completed"); return Uni.createFrom().item(completed); }
        @Override public Uni<Optional<PartyType>> findOwnedParty(PartyId party) { order.add("owner"); return Uni.createFrom().item(missingOwner ? Optional.empty() : Optional.of(type)); }
        @Override public Uni<Optional<IdentifierSchemeId>> findIdentifierSchemeId(PartyId party, PartyIdentifierId id) { order.add("identity"); return Uni.createFrom().item(missingIdentity ? Optional.empty() : Optional.of(current.identifierSchemeId())); }
        @Override public Uni<Void> serializeScope(PartyId party, IdentifierSchemeId scheme) { order.add("scope"); return Uni.createFrom().voidItem(); }
        @Override public Uni<Optional<PartyIdentifier>> findForUpdate(PartyId party, PartyIdentifierId id) { order.add("target"); return Uni.createFrom().item(missingTarget ? Optional.empty() : Optional.of(current)); }
        @Override public Uni<Optional<PartyIdentifier>> findCurrentPrimary(PartyId party, IdentifierSchemeId scheme) { order.add("primary"); return Uni.createFrom().item(Optional.ofNullable(primary)); }
        @Override public Uni<PartyIdentifierResult> updateIdentifier(PartyIdentifier value, PartyIdentifierVersion expected) {
            order.add("update"); assertEquals(current.version(), expected);
            if (writeFailure != null) { return Uni.createFrom().failure(writeFailure); }
            candidate = value; return Uni.createFrom().item(PartyIdentifierFixtures.result(value));
        }
        @Override public Uni<Void> appendActionAudit(PartyIdentifierActionAudit audit) { order.add("audit"); audits.add(audit); return Uni.createFrom().voidItem(); }
        @Override public Uni<Void> recordCompletion(PartyIdentifierOperation operation, String key, CompletedPartyIdentifierMutation result) { order.add("completion"); completed = Optional.of(result); return Uni.createFrom().voidItem(); }
        @Override public Uni<Void> appendEnabledEvent(OutboxEventCandidate event) { order.add("event"); events.add(event); return Uni.createFrom().voidItem(); }
        @Override public Uni<Optional<IdentifierSchemeId>> findSchemeIdByCode(String code) { throw unexpected(); }
        @Override public Uni<Optional<IdentifierScheme>> findSchemeForRegistration(IdentifierSchemeId id) { throw unexpected(); }
        @Override public Uni<PartyIdentifierResult> insertIdentifier(PartyIdentifier value) { throw unexpected(); }
        private static AssertionError unexpected() { return new AssertionError("Lifecycle must not register/readmit/protect evidence or mutate another aggregate"); }
    }

    /** Exposes a non-UTC zone and makes a second read cross UTC midnight. */
    private static final class CountingClock extends Clock {
        int reads;
        @Override public ZoneId getZone() { return ZoneOffset.ofHours(14); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(NOW, zone); }
        @Override public Instant instant() { return reads++ == 0 ? NOW : NOW.plusSeconds(2); }
    }
}
