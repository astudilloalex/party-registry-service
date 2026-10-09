package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.PatchPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies same-context current-version correction, neutral failure precedence and preserved evidence without replay mechanics. */
class PatchPartyIdentifierUseCaseTest {
    private static final LocalDate TODAY = PartyIdentifierFixtures.EVALUATED_ON;
    private static final Instant NOW = TODAY.atTime(23, 59, 59, 123456789).toInstant(ZoneOffset.UTC);
    private static final Instant ACCEPTED = NOW.truncatedTo(ChronoUnit.MICROS);

    @Test
    void correctsBothOwnerTypesAndEligibleStatesSequentiallyWithOneClockAndOneSafeEvent() {
        for (PartyType type : PartyType.values()) {
            for (var status : List.of(PartyIdentifierStatus.PENDING_VERIFICATION, PartyIdentifierStatus.VERIFIED)) {
                var fixture = new Harness(type, status, 3, TODAY);
                var command = fixture.command(FieldUpdate.present("civil_registry"), FieldUpdate.absent(), FieldUpdate.absent(), 3);
                var outcome = item(fixture.useCase().execute(command));
                assertEquals(List.of("transaction", "owner", "schemeIdentity", "scope", "lockedTarget", "update", "event"), fixture.order);
                assertEquals(PartyIdentifierMutationOutcome.Disposition.APPLIED, outcome.disposition());
                assertEquals("CIVIL_REGISTRY", outcome.identifier().issuerCode()); assertEquals(4, outcome.identifier().version().value());
                assertEquals(ACCEPTED, outcome.identifier().updatedAt()); assertEquals(1, fixture.clock.reads);
                assertPreserved(fixture.current, fixture.candidate);
                assertEquals(fixture.metadata.userId(), fixture.candidate.auditInfo().updatedBy());
                var event = assertInstanceOf(PartyIdentifierChangedOutboxCandidate.class, fixture.events.getFirst());
                assertEquals(PartyIdentifierChangedOutboxCandidate.Kind.UPDATED, event.kind());
                assertEquals(outcome.identifier().version(), event.identifierVersion()); assertEquals(ACCEPTED, event.occurredAt());
                assertEquals(fixture.metadata.processId(), event.correlationId()); assertEquals(fixture.metadata.userId(), event.createdBy());
            }
        }
    }

    @Test
    void ownerAndBothIdentifierAbsenceChecksPrecedeStaleVersionStateAndTime() {
        for (int missing = 0; missing < 3; missing++) {
            var fixture = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.REVOKED, Long.MAX_VALUE, TODAY.minusDays(1));
            fixture.missingOwner = missing == 0; fixture.missingIdentity = missing == 1; fixture.missingTarget = missing == 2;
            var failure = failure(fixture.useCase().execute(fixture.clear(0)));
            if (missing == 0) { assertInstanceOf(ApplicationFailure.PartyNotFound.class, failure.failure()); }
            else { assertInstanceOf(ApplicationFailure.IdentifierNotFound.class, failure.failure()); }
            assertTrue(fixture.events.isEmpty()); assertNull(fixture.candidate); assertEquals(0, fixture.clock.reads);
        }
    }

    @Test
    void independentVersionMismatchPrecedesTerminalStateAndExhaustion() {
        var fixture = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.REVOKED, Long.MAX_VALUE, TODAY.minusDays(1));
        var mismatch = assertInstanceOf(ApplicationFailure.IdentifierVersionMismatch.class, failure(fixture.useCase().execute(fixture.clear(7))).failure());
        assertEquals(new PartyIdentifierVersion(7), mismatch.expectedVersion()); assertEquals(fixture.current.version(), mismatch.currentVersion());
        assertNull(fixture.candidate); assertEquals(0, fixture.clock.reads);
    }

    @Test
    void terminalStatePrecedesExhaustionAndExhaustionPrecedesResultingDates() {
        for (var state : List.of(PartyIdentifierStatus.REJECTED, PartyIdentifierStatus.EXPIRED, PartyIdentifierStatus.REVOKED)) {
            var fixture = new Harness(PartyType.LEGAL_ENTITY, state, Long.MAX_VALUE, TODAY.minusDays(1));
            assertInstanceOf(ApplicationFailure.InvalidIdentifierLifecycle.class, failure(fixture.useCase().execute(fixture.clear(Long.MAX_VALUE))).failure());
            assertNull(fixture.candidate); assertTrue(fixture.events.isEmpty());
        }
        var eligible = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.VERIFIED, Long.MAX_VALUE, TODAY.minusDays(1));
        var unchanged = eligible.command(FieldUpdate.present("CIVIL_REGISTRY"), FieldUpdate.absent(), FieldUpdate.absent(), Long.MAX_VALUE);
        assertInstanceOf(ApplicationFailure.IdentifierVersionExhausted.class, failure(eligible.useCase().execute(unchanged)).failure());
        assertNull(eligible.candidate); assertTrue(eligible.events.isEmpty());
    }

    @Test
    void pastDateEvidenceMustBeRepairedOrClearedAndIssuerOnlyCannotBypassValidity() {
        for (var state : List.of(PartyIdentifierStatus.PENDING_VERIFICATION, PartyIdentifierStatus.VERIFIED)) {
            var rejected = new Harness(PartyType.NATURAL_PERSON, state, 3, TODAY.minusDays(1));
            var issuerOnly = rejected.command(FieldUpdate.present("CIVIL_REGISTRY"), FieldUpdate.absent(), FieldUpdate.absent(), 3);
            var violation = assertInstanceOf(ApplicationFailure.IdentifierValidationFailure.class, failure(rejected.useCase().execute(issuerOnly)).failure());
            assertEquals(DomainViolation.IDENTIFIER_EXPIRED, violation.violation()); assertNull(rejected.candidate); assertTrue(rejected.events.isEmpty());
            for (FieldUpdate<LocalDate> expiry : List.of(FieldUpdate.present(TODAY), FieldUpdate.<LocalDate>present(null))) {
                var repaired = new Harness(PartyType.NATURAL_PERSON, state, 3, TODAY.minusDays(1));
                var command = repaired.command(FieldUpdate.absent(), FieldUpdate.absent(), expiry, 3);
                assertEquals(expiry.value(), item(repaired.useCase().execute(command)).identifier().expiresOn());
                assertPreserved(repaired.current, repaired.candidate);
            }
        }
    }

    @Test
    void anInvertedResultingIntervalFailsWithoutWriteOrEvent() {
        var fixture = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, 3, TODAY);
        var command = fixture.command(FieldUpdate.absent(), FieldUpdate.present(TODAY.plusDays(2)), FieldUpdate.present(TODAY.plusDays(1)), 3);
        var violation = assertInstanceOf(ApplicationFailure.IdentifierValidationFailure.class, failure(fixture.useCase().execute(command)).failure());
        assertEquals(DomainViolation.IDENTIFIER_VALIDITY_DATE_ORDER, violation.violation()); assertNull(fixture.candidate); assertTrue(fixture.events.isEmpty());
    }

    @Test
    void anIdenticalCorrectionStillAdvancesExactlyOnceAndKeepsCreationAudit() {
        var fixture = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.VERIFIED, 3, TODAY);
        var command = fixture.command(FieldUpdate.present(fixture.current.issuerCode()), FieldUpdate.absent(), FieldUpdate.absent(), 3);
        var result = item(fixture.useCase().execute(command)).identifier();
        assertEquals(4, result.version().value()); assertEquals(fixture.current.issuerCode(), result.issuerCode());
        assertEquals(fixture.current.auditInfo().createdAt(), result.createdAt()); assertEquals(1, fixture.events.size());
    }

    @Test
    void storageFailurePropagatesBeforeEventAndNeverCreatesReplayOrWithdrawalAudit() {
        var fixture = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.PENDING_VERIFICATION, 3, TODAY);
        fixture.writeFailure = new IllegalStateException("Controlled guarded write failure");
        var command = fixture.clear(3);
        var error = fixture.useCase().execute(command).subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(Duration.ofSeconds(2)).getFailure();
        assertSame(fixture.writeFailure, error); assertTrue(fixture.events.isEmpty());
        assertEquals(List.of("transaction", "owner", "schemeIdentity", "scope", "lockedTarget", "update"), fixture.order);
    }

    @Test
    void correctionIsSubscriptionLazyAndOneClockReadCannotCrossUtcMidnight() {
        var fixture = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.PENDING_VERIFICATION, 3, TODAY);
        var operation = fixture.useCase().execute(fixture.command(FieldUpdate.present("CIVIL_REGISTRY"), FieldUpdate.absent(), FieldUpdate.absent(), 3));
        assertTrue(fixture.order.isEmpty()); assertEquals(0, fixture.clock.reads);
        assertEquals(TODAY, item(operation).identifier().expiresOn()); assertEquals(1, fixture.clock.reads);
    }

    private static void assertPreserved(PartyIdentifier before, PartyIdentifier after) {
        assertEquals(before.identifierId(), after.identifierId()); assertEquals(before.partyId(), after.partyId()); assertEquals(before.tenantId(), after.tenantId());
        assertEquals(before.identifierSchemeId(), after.identifierSchemeId()); assertSame(before.protectedValue(), after.protectedValue());
        assertEquals(before.status(), after.status()); assertEquals(before.isPrimary(), after.isPrimary());
        assertEquals(before.verifiedAt(), after.verifiedAt()); assertEquals(before.verifiedBy(), after.verifiedBy());
        assertEquals(before.auditInfo().createdAt(), after.auditInfo().createdAt()); assertEquals(before.auditInfo().createdBy(), after.auditInfo().createdBy());
    }

    private static <T> T item(Uni<T> operation) { return operation.subscribe().withSubscriber(UniAssertSubscriber.create()).awaitItem(Duration.ofSeconds(2)).assertCompleted().getItem(); }
    private static ApplicationException failure(Uni<?> operation) { return assertInstanceOf(ApplicationException.class, operation.subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(Duration.ofSeconds(2)).getFailure()); }

    /** Records permitted sequential PATCH capabilities and fails immediately on replay, catalog admission or unrelated writes. */
    private static final class Harness implements PartyIdentifierMutationContext {
        final PartyType type;
        final PartyIdentifier current;
        final RequestMetadata metadata = new RequestMetadata(PartyIdentifierFixtures.TENANT, "metadata-operator", UUID.randomUUID());
        final List<String> order = new ArrayList<>();
        final List<OutboxEventCandidate> events = new ArrayList<>();
        final CountingClock clock = new CountingClock();
        PartyIdentifier candidate;
        Throwable writeFailure;
        boolean missingOwner;
        boolean missingIdentity;
        boolean missingTarget;

        Harness(PartyType type, PartyIdentifierStatus status, long version, LocalDate expiration) {
            this.type = type; this.current = PartyIdentifierFixtures.identifier(type, status, true, version, expiration);
        }
        PatchPartyIdentifierUseCase useCase() {
            PartyIdentifierMutationPort mutations = (request, work) -> Uni.createFrom().deferred(() -> { assertSame(metadata, request); order.add("transaction"); return work.apply(this); });
            return new PatchPartyIdentifierUseCase(mutations, clock, new RecordingOperationObserver());
        }
        PatchPartyIdentifierCommand clear(long version) { return command(FieldUpdate.present(null), FieldUpdate.absent(), FieldUpdate.absent(), version); }
        PatchPartyIdentifierCommand command(FieldUpdate<String> issuer, FieldUpdate<LocalDate> issue, FieldUpdate<LocalDate> expiration, long version) {
            return new PatchPartyIdentifierCommand(metadata, current.partyId(), current.identifierId(), new PartyIdentifierVersion(version), issuer, issue, expiration);
        }
        @Override public TenantId tenantId() { return metadata.tenantId(); }
        @Override public Uni<Optional<PartyType>> findOwnedParty(PartyId id) { order.add("owner"); assertEquals(current.partyId(), id); return Uni.createFrom().item(missingOwner ? Optional.empty() : Optional.of(type)); }
        @Override public Uni<Optional<IdentifierSchemeId>> findIdentifierSchemeId(PartyId party, PartyIdentifierId id) { order.add("schemeIdentity"); assertEquals(current.identifierId(), id); return Uni.createFrom().item(missingIdentity ? Optional.empty() : Optional.of(current.identifierSchemeId())); }
        @Override public Uni<Void> serializeScope(PartyId party, IdentifierSchemeId scheme) { order.add("scope"); assertEquals(current.identifierSchemeId(), scheme); return Uni.createFrom().voidItem(); }
        @Override public Uni<Optional<PartyIdentifier>> findForUpdate(PartyId party, PartyIdentifierId id) { order.add("lockedTarget"); return Uni.createFrom().item(missingTarget ? Optional.empty() : Optional.of(current)); }
        @Override public Uni<PartyIdentifierResult> updateIdentifier(PartyIdentifier accepted, PartyIdentifierVersion expected) {
            order.add("update"); assertEquals(current.version(), expected);
            if (writeFailure != null) { return Uni.createFrom().failure(writeFailure); }
            candidate = accepted; return Uni.createFrom().item(PartyIdentifierFixtures.result(accepted));
        }
        @Override public Uni<Void> appendEnabledEvent(OutboxEventCandidate event) { order.add("event"); events.add(event); return Uni.createFrom().voidItem(); }
        @Override public Uni<Void> serializeReplayKey(PartyIdentifierOperation operation, String key) { throw unexpected(); }
        @Override public Uni<Optional<CompletedPartyIdentifierMutation>> findCompleted(PartyIdentifierOperation operation, String key) { throw unexpected(); }
        @Override public Uni<Optional<IdentifierSchemeId>> findSchemeIdByCode(String code) { throw unexpected(); }
        @Override public Uni<Optional<IdentifierScheme>> findSchemeForRegistration(IdentifierSchemeId id) { throw unexpected(); }
        @Override public Uni<Optional<PartyIdentifier>> findCurrentPrimary(PartyId party, IdentifierSchemeId scheme) { throw unexpected(); }
        @Override public Uni<PartyIdentifierResult> insertIdentifier(PartyIdentifier accepted) { throw unexpected(); }
        @Override public Uni<Void> appendActionAudit(PartyIdentifierActionAudit audit) { throw unexpected(); }
        @Override public Uni<Void> recordCompletion(PartyIdentifierOperation operation, String key, CompletedPartyIdentifierMutation completion) { throw unexpected(); }
        private static AssertionError unexpected() { return new AssertionError("PATCH must not request replay, catalog admission, primary selection or withdrawal effects"); }
    }

    /** Makes a second operation-time read visibly cross midnight while exposing a non-UTC zone. */
    private static final class CountingClock extends Clock {
        int reads;
        @Override public ZoneId getZone() { return ZoneOffset.ofHours(14); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(NOW, zone); }
        @Override public Instant instant() { return reads++ == 0 ? NOW : NOW.plusSeconds(2); }
    }
}
