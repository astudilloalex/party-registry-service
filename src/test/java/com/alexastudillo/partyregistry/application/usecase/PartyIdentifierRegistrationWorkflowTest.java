package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.RegisterPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreationRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSchemePolicy;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies replay-first registration admission with one locked scheme, clock capture and complete safe outcome. */
class PartyIdentifierRegistrationWorkflowTest {
    private static final LocalDate TODAY = LocalDate.of(2026, Month.OCTOBER, 9);
    private static final Instant NOW = TODAY.atTime(23, 59, 59, 123456789).toInstant(ZoneOffset.UTC);
    private static final Instant ACCEPTED = NOW.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    private static final String DIGEST = "a".repeat(64);
    private static final String CODE = "FIXTURE-SCHEME";

    @Test
    void registersBothTypesUsingOnlyTheLockedSchemeAndOneMicrosecondInstant() {
        for (PartyType type : PartyType.values()) {
            var fixture = new Harness();
            fixture.partyType = Optional.of(type);
            var input = new InitialPartyIdentifierInput(CODE, "  ab1234  ", "civil_registry", null, TODAY, true);
            var outcome = item(fixture.useCase().executeOutcome(fixture.command(input)));
            assertEquals(List.of("fingerprint", "transaction", "serializeReplayKey", "findCompleted", "findOwnedParty", "findSchemeIdByCode",
                    "serializeScope", "findSchemeForRegistration", "protect", "insertIdentifier", "recordCompletion", "appendEnabledEvent"), fixture.order);
            assertEquals(PartyIdentifierMutationOutcome.Disposition.APPLIED, outcome.disposition());
            assertEquals(PartyIdentifierStatus.PENDING_VERIFICATION, outcome.identifier().status());
            assertEquals(0, outcome.identifier().version().value());
            assertEquals(ACCEPTED, outcome.identifier().createdAt());
            assertEquals(ACCEPTED, outcome.identifier().updatedAt());
            assertEquals("CIVIL_REGISTRY", outcome.identifier().issuerCode());
            assertEquals(TODAY, outcome.identifier().expiresOn());
            assertTrue(outcome.identifier().isPrimary());
            assertEquals(1, fixture.clockReads.get());
            assertEquals("AB1234", fixture.protection.getFirst().normalizedValue());
            assertEquals(input.value(), fixture.fingerprintRequest.value());
            assertEquals(input.issuerCode(), fixture.fingerprintRequest.issuerCode());
            assertEquals(fixture.scheme, fixture.lockedScheme);
            assertEquals(outcome.identifier(), fixture.accepted.orElseThrow().result());
            assertEquals(fixture.metadata.userId(), fixture.accepted.orElseThrow().acceptedBy());
            assertNull(fixture.accepted.orElseThrow().expectedVersion());
            var event = assertInstanceOf(PartyIdentifierCreatedOutboxCandidate.class, fixture.events.getFirst());
            assertEquals(outcome.identifier().identifierId(), event.identifierId());
            assertEquals(ACCEPTED, event.occurredAt());
            assertEquals(fixture.metadata.processId(), event.correlationId());
        }
    }

    @Test
    void replaysBeforeAbsentOwnerRetiredSchemeProtectionOrClock() {
        var fixture = new Harness();
        var command = fixture.command(input(null, null, false));
        var original = item(fixture.useCase().executeOutcome(command));
        fixture.order.clear();
        fixture.partyType = Optional.empty();
        fixture.scheme = scheme(IdentifierSchemeStatus.RETIRED, IdentifierSubjectType.BOTH);
        fixture.failure = new IllegalStateException("New-operation dependencies unavailable");
        var retry = new RegisterPartyIdentifierCommand(new RequestMetadata(fixture.metadata.tenantId(), "retry-actor", UUID.randomUUID()), command.idempotencyKey(), command.partyId(), command.identifier());
        var outcome = item(fixture.useCase().executeOutcome(retry));
        assertEquals(PartyIdentifierMutationOutcome.Disposition.REPLAYED, outcome.disposition());
        assertEquals(original.identifier(), outcome.identifier());
        assertEquals(List.of("fingerprint", "transaction", "serializeReplayKey", "findCompleted"), fixture.order);
        assertEquals(1, fixture.clockReads.get());
        assertEquals(1, fixture.protection.size());
        assertEquals(1, fixture.events.size());
    }

    @Test
    void changedRawInputConflictsBeforeOwnerAndDoesNotExposeSavedResult() {
        var fixture = new Harness();
        item(fixture.useCase().executeOutcome(fixture.command(input(null, null, false))));
        fixture.order.clear();
        fixture.nextDigest = "b".repeat(64);
        fixture.partyType = Optional.empty();
        var changed = new InitialPartyIdentifierInput(CODE, "AB1234", null, null, null, false);
        var failure = failure(fixture.useCase().executeOutcome(fixture.command(changed)));
        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, failure.failure());
        assertEquals(List.of("fingerprint", "transaction", "serializeReplayKey", "findCompleted"), fixture.order);
        assertEquals(1, fixture.clockReads.get());
        assertEquals(1, fixture.protection.size());
    }

    @Test
    void ownerAbsencePrecedesUnknownInactiveAndIncompatibleSchemeAdmission() {
        var absent = new Harness();
        absent.partyType = Optional.empty();
        assertInstanceOf(ApplicationFailure.PartyNotFound.class, failure(absent.useCase().executeOutcome(absent.command(input(null, null, false)))).failure());
        assertEquals(List.of("fingerprint", "transaction", "serializeReplayKey", "findCompleted", "findOwnedParty"), absent.order);
        var unknown = new Harness();
        unknown.schemeIdentity = Optional.empty();
        assertInstanceOf(ApplicationFailure.UnknownIdentifierScheme.class, failure(unknown.useCase().executeOutcome(unknown.command(input(null, null, false)))).failure());
        var inactive = new Harness();
        inactive.scheme = scheme(IdentifierSchemeStatus.DEPRECATED, IdentifierSubjectType.BOTH);
        assertInstanceOf(ApplicationFailure.InactiveIdentifierScheme.class, failure(inactive.useCase().executeOutcome(inactive.command(input(null, null, false)))).failure());
        var incompatible = new Harness();
        incompatible.scheme = scheme(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.LEGAL_ENTITY);
        assertInstanceOf(ApplicationFailure.IncompatibleIdentifierScheme.class, failure(incompatible.useCase().executeOutcome(incompatible.command(input(null, null, false)))).failure());
        for (var fixture : List.of(absent, unknown, inactive, incompatible)) {
            assertTrue(fixture.protection.isEmpty()); assertTrue(fixture.events.isEmpty()); assertTrue(fixture.accepted.isEmpty());
        }
    }

    @Test
    void missingOrChangedLockedSchemeFailsBeforeProtection() {
        var missing = new Harness();
        missing.missingLockedScheme = true;
        assertInstanceOf(ApplicationFailure.UnknownIdentifierScheme.class, failure(missing.useCase().executeOutcome(missing.command(input(null, null, false)))).failure());
        var changed = new Harness();
        changed.scheme = new IdentifierScheme(new IdentifierSchemeId(UUID.randomUUID()), CODE, "GB", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                "Fixture", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", 1, 256, true, IdentifierSchemeStatus.ACTIVE, IdentifierSchemeVersion.initial(), AuditInfo.initial(ACCEPTED, "catalog"));
        var unexpected = changed.useCase().executeOutcome(changed.command(input(null, null, false))).subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
                .awaitFailure(Duration.ofSeconds(2)).getFailure();
        assertInstanceOf(IllegalStateException.class, unexpected);
        assertTrue(changed.protection.isEmpty());
    }

    @Test
    void nullableExpirationAndFutureIssueDatesRemainAcceptedWithLegacySchemeMetadata() {
        for (InitialPartyIdentifierInput input : List.of(input(null, null, false), input(TODAY.plusDays(1), null, false), input(null, TODAY, false))) {
            var fixture = new Harness();
            var result = item(fixture.useCase().executeOutcome(fixture.command(input))).identifier();
            assertEquals(input.issuedOn(), result.issuedOn()); assertEquals(input.expiresOn(), result.expiresOn());
            assertNull(result.verifiedAt()); assertEquals(1, fixture.clockReads.get());
        }
    }

    @Test
    void ruleAndValidityFailuresLeaveProtectionCompletionAndEventsUntouched() {
        var inputs = List.of(new InitialPartyIdentifierInput(CODE, "AB-1234", null, null, null, false),
                input(null, TODAY.minusDays(1), false), input(TODAY.plusDays(2), TODAY.plusDays(1), false));
        var violations = List.of(DomainViolation.IDENTIFIER_VALUE_INVALID, DomainViolation.IDENTIFIER_EXPIRED, DomainViolation.IDENTIFIER_VALIDITY_DATE_ORDER);
        for (int i = 0; i < inputs.size(); i++) {
            var fixture = new Harness();
            var rejected = assertInstanceOf(ApplicationFailure.IdentifierValidationFailure.class, failure(fixture.useCase().executeOutcome(fixture.command(inputs.get(i)))).failure());
            assertEquals(violations.get(i), rejected.violation());
            assertTrue(fixture.protection.isEmpty()); assertTrue(fixture.accepted.isEmpty()); assertTrue(fixture.events.isEmpty());
        }
    }

    @Test
    void failedAttemptDoesNotCompleteAKeyAndCanBeRetried() {
        var fixture = new Harness();
        var command = fixture.command(input(null, null, false));
        var duplicate = new ApplicationException(new ApplicationFailure.IdentifierUniquenessConflict(fixture.scheme.id()));
        fixture.failure = duplicate;
        assertSame(duplicate, failure(fixture.useCase().executeOutcome(command)));
        assertTrue(fixture.accepted.isEmpty()); assertTrue(fixture.events.isEmpty());
        fixture.failure = null;
        assertEquals(PartyIdentifierMutationOutcome.Disposition.APPLIED, item(fixture.useCase().executeOutcome(command)).disposition());
    }

    @Test
    void preparationUsesTheLockedSchemesNormalizedLengthBounds() {
        var fixture = new Harness();
        var source = fixture.scheme;
        fixture.scheme = new IdentifierScheme(source.id(), source.code(), source.issuingCountryCode(), source.category(), source.applicableSubjectType(),
                source.name(), source.description(), source.normalizerKey(), source.validatorKey(), 8, 16, source.requiresExpiration(), source.status(), source.version(), source.auditInfo());
        var rejected = assertInstanceOf(ApplicationFailure.IdentifierValidationFailure.class,
                failure(fixture.useCase().executeOutcome(fixture.command(input(null, null, false)))).failure());
        assertEquals(DomainViolation.IDENTIFIER_VALUE_TOO_SHORT, rejected.violation());
        assertTrue(fixture.protection.isEmpty()); assertTrue(fixture.accepted.isEmpty()); assertTrue(fixture.events.isEmpty());
    }

    @Test
    void fingerprintAndClockAreLazyAndCapturedOnlyOncePerSubscription() {
        var fixture = new Harness();
        var operation = fixture.useCase().executeOutcome(fixture.command(input(null, null, false)));
        assertTrue(fixture.order.isEmpty()); assertEquals(0, fixture.clockReads.get());
        assertEquals(PartyIdentifierMutationOutcome.Disposition.APPLIED, item(operation).disposition());
        assertEquals(PartyIdentifierMutationOutcome.Disposition.REPLAYED, item(operation).disposition());
        assertEquals(1, fixture.clockReads.get());
        assertEquals(2, fixture.order.stream().filter("fingerprint"::equals).count());
        assertEquals(1, fixture.protection.size());
    }

    private static InitialPartyIdentifierInput input(LocalDate issued, LocalDate expires, boolean primary) {
        return new InitialPartyIdentifierInput(CODE, "  ab1234  ", null, issued, expires, primary);
    }

    private static IdentifierScheme scheme(IdentifierSchemeStatus status, IdentifierSubjectType subjects) {
        return new IdentifierScheme(new IdentifierSchemeId(UUID.fromString("019a1a56-f000-7000-8000-000000000003")), CODE, "GB", IdentifierCategory.OTHER, subjects,
                "Fixture scheme", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", 1, 256, true, status, IdentifierSchemeVersion.initial(), AuditInfo.initial(ACCEPTED, "catalog"));
    }

    private static <T> T item(Uni<T> operation) {
        return operation.subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create()).awaitItem(Duration.ofSeconds(2)).assertCompleted().getItem();
    }

    private static ApplicationException failure(Uni<?> operation) {
        return assertInstanceOf(ApplicationException.class, operation.subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())
                .awaitFailure(Duration.ofSeconds(2)).getFailure());
    }

    /** Records only the transaction capabilities used by registration and exposes controllable dependency failures. */
    private static final class Harness implements PartyIdentifierMutationContext, PartyIdentifierFingerprintPort {
        final List<String> order = new ArrayList<>();
        final List<IdentifierProtectionRequest> protection = new ArrayList<>();
        final List<OutboxEventCandidate> events = new ArrayList<>();
        final AtomicInteger clockReads = new AtomicInteger();
        final RequestMetadata metadata = new RequestMetadata(new TenantId(UUID.randomUUID()), "original-actor", UUID.randomUUID());
        final PartyId party = new PartyId(UUID.randomUUID());
        Optional<PartyType> partyType = Optional.of(PartyType.NATURAL_PERSON);
        IdentifierScheme scheme = scheme(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.BOTH);
        Optional<IdentifierSchemeId> schemeIdentity = Optional.of(scheme.id());
        Optional<CompletedPartyIdentifierMutation> accepted = Optional.empty();
        PartyIdentifierCreationRequest fingerprintRequest;
        IdentifierScheme lockedScheme;
        Throwable failure;
        boolean missingLockedScheme;
        String nextDigest = DIGEST;

        RegisterPartyIdentifierCommand command(InitialPartyIdentifierInput input) { return new RegisterPartyIdentifierCommand(metadata, " exact-key ", party, input); }

        RegisterPartyIdentifierUseCase useCase() {
            PartyIdentifierMutationPort mutations = (request, work) -> Uni.createFrom().deferred(() -> { order.add("transaction"); return work.apply(this); });
            var preparation = new PartyIdentifierPreparation(_ -> { throw new AssertionError("Registration must use its same-context locked scheme"); },
                    new IdentifierRuleCatalog(), new IdentifierSchemePolicy(), request -> {
                        order.add("protect"); protection.add(request);
                        return new ProtectedIdentifierValue("synthetic-ciphertext", 1, "b".repeat(64), "***1234", new IdentifierRuleVersion(1));
                    });
            return new RegisterPartyIdentifierUseCase(mutations, this, preparation, new CountingClock(clockReads), new RecordingOperationObserver());
        }

        @Override public String fingerprint(TenantId tenant, PartyIdentifierCreationRequest request) { order.add("fingerprint"); fingerprintRequest = request; return nextDigest; }
        @Override public String fingerprint(TenantId tenant, PartyIdentifierActionRequest request) { throw new AssertionError("Unexpected action fingerprint"); }
        @Override public boolean matches(String stored, String candidate) { return stored.equals(candidate); }
        @Override public TenantId tenantId() { return metadata.tenantId(); }
        @Override public Uni<Void> serializeReplayKey(PartyIdentifierOperation operation, String key) { order.add("serializeReplayKey"); assertEquals(PartyIdentifierOperation.CREATE, operation); return Uni.createFrom().voidItem(); }
        @Override public Uni<Optional<CompletedPartyIdentifierMutation>> findCompleted(PartyIdentifierOperation operation, String key) { order.add("findCompleted"); return Uni.createFrom().item(accepted); }
        @Override public Uni<Optional<PartyType>> findOwnedParty(PartyId id) { order.add("findOwnedParty"); assertEquals(party, id); return Uni.createFrom().item(partyType); }
        @Override public Uni<Optional<IdentifierSchemeId>> findSchemeIdByCode(String code) { order.add("findSchemeIdByCode"); assertEquals(CODE, code); return Uni.createFrom().item(schemeIdentity); }
        @Override public Uni<Void> serializeScope(PartyId id, IdentifierSchemeId schemeId) { order.add("serializeScope"); assertEquals(party, id); assertEquals(schemeIdentity.orElseThrow(), schemeId); return Uni.createFrom().voidItem(); }
        @Override public Uni<Optional<IdentifierScheme>> findSchemeForRegistration(IdentifierSchemeId id) { order.add("findSchemeForRegistration"); lockedScheme = scheme; return Uni.createFrom().item(missingLockedScheme ? Optional.empty() : Optional.of(scheme)); }
        @Override public Uni<PartyIdentifierResult> insertIdentifier(PartyIdentifier candidate) { order.add("insertIdentifier"); return failure == null ? Uni.createFrom().item(PartyIdentifierResult.fromAggregate(candidate, scheme)) : Uni.createFrom().failure(failure); }
        @Override public Uni<Void> recordCompletion(PartyIdentifierOperation operation, String key, CompletedPartyIdentifierMutation completion) { order.add("recordCompletion"); accepted = Optional.of(completion); return Uni.createFrom().voidItem(); }
        @Override public Uni<Void> appendEnabledEvent(OutboxEventCandidate event) { order.add("appendEnabledEvent"); events.add(event); return Uni.createFrom().voidItem(); }
        @Override public Uni<Optional<IdentifierSchemeId>> findIdentifierSchemeId(PartyId id, PartyIdentifierId identifier) { throw unexpected(); }
        @Override public Uni<Optional<PartyIdentifier>> findForUpdate(PartyId id, PartyIdentifierId identifier) { throw unexpected(); }
        @Override public Uni<Optional<PartyIdentifier>> findCurrentPrimary(PartyId id, IdentifierSchemeId schemeId) { throw unexpected(); }
        @Override public Uni<PartyIdentifierResult> updateIdentifier(PartyIdentifier candidate, PartyIdentifierVersion expected) { throw unexpected(); }
        @Override public Uni<Void> appendActionAudit(PartyIdentifierActionAudit audit) { throw unexpected(); }
        private static AssertionError unexpected() { return new AssertionError("Creation must not mutate existing evidence"); }
    }

    /** Advances across UTC midnight on a second read so repeated evaluation would be observable. */
    private static final class CountingClock extends Clock {
        private final AtomicInteger reads;
        CountingClock(AtomicInteger reads) { this.reads = reads; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return NOW.plusSeconds(reads.getAndIncrement() * 2L); }
    }
}
