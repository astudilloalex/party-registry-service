package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.SetPrimaryPartyIdentifierCommand;
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
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies replay-first primary selection, whole-plan validation and awaited demotion before promotion in one owned scope. */
class SetPrimaryPartyIdentifierUseCaseTest {
    private static final LocalDate DATE = PartyIdentifierFixtures.EVALUATED_ON;
    private static final Instant NOW = DATE.atTime(23, 59, 59, 123456789).toInstant(ZoneOffset.UTC);
    private static final Instant ACCEPTED = NOW.truncatedTo(ChronoUnit.MICROS);

    @Test
    void bothTypesAndInclusiveOrAbsentExpirationSelectOneTargetWithoutCatalogAdmission() {
        for (PartyType type : PartyType.values()) {
            for (LocalDate expiration : new LocalDate[]{null, DATE}) {
                var fixture = new Harness(type, PartyIdentifierStatus.VERIFIED, false, 3, expiration);
                var result = item(fixture.useCase().execute(fixture.command(false, null, 3))).identifier();
                assertTrue(result.isPrimary()); assertEquals(4, result.version().value()); assertEquals(ACCEPTED, result.updatedAt());
                assertEquals(1, fixture.clock.reads); assertEquals(1, fixture.writes.size()); assertEquals(1, fixture.events.size());
                assertSame(fixture.current.protectedValue(), fixture.writes.getFirst().protectedValue()); assertPreserved(fixture.current, fixture.writes.getFirst());
            }
        }
    }

    @Test
    void explicitReplacementValidatesBothVersionsAndDemotesBeforePromotingWithOneActorAndInstant() {
        var fixture = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, DATE);
        fixture.primary = former(fixture.current, 9, DATE.minusDays(1));
        var outcome = item(fixture.useCase().execute(fixture.command(true, "key", 3)));
        assertEquals(List.of("fingerprint", "transaction", "key", "completed", "owner", "identity", "scope", "target", "primary",
                "demote", "promote", "completion", "event", "event"), fixture.order);
        var demoted = fixture.writes.getFirst(); var promoted = fixture.writes.getLast();
        assertFalse(demoted.isPrimary()); assertTrue(promoted.isPrimary()); assertEquals(10, demoted.version().value()); assertEquals(4, promoted.version().value());
        assertEquals(ACCEPTED, demoted.auditInfo().updatedAt()); assertEquals(demoted.auditInfo().updatedAt(), promoted.auditInfo().updatedAt());
        assertEquals(fixture.metadata.userId(), demoted.auditInfo().updatedBy()); assertPreserved(fixture.primary, demoted); assertPreserved(fixture.current, promoted);
        assertEquals(promoted.identifierId(), outcome.identifier().identifierId()); assertEquals(2, fixture.events.size());
        for (var event : fixture.events) { assertEquals(PartyIdentifierChangedOutboxCandidate.Kind.PRIMARY_CHANGED, assertInstanceOf(PartyIdentifierChangedOutboxCandidate.class, event).kind()); }
    }

    @Test
    void alreadyPrimarySelectionAdvancesOnlyTheTargetAndEmitsOneEvent() {
        var fixture = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.VERIFIED, true, 3, DATE);
        fixture.primary = fixture.current;
        assertEquals(4, item(fixture.useCase().execute(fixture.command(false, null, 3))).identifier().version().value());
        assertEquals(List.of(fixture.current.identifierId()), fixture.writes.stream().map(PartyIdentifier::identifierId).toList()); assertEquals(1, fixture.events.size());
    }

    @Test
    void conflictAndFormerExhaustionValidateBeforeAnyDemotionOrPromotion() {
        var conflict = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, DATE);
        conflict.primary = former(conflict.current, 9, DATE.minusDays(1));
        assertInstanceOf(ApplicationFailure.PrimaryIdentifierConflict.class, failure(conflict.useCase().execute(conflict.command(false, "key", 3))).failure());
        var exhausted = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, DATE);
        exhausted.primary = former(exhausted.current, Long.MAX_VALUE, DATE.minusDays(1));
        assertInstanceOf(ApplicationFailure.IdentifierVersionExhausted.class, failure(exhausted.useCase().execute(exhausted.command(true, "key", 3))).failure());
        for (var fixture : List.of(conflict, exhausted)) { assertTrue(fixture.writes.isEmpty()); assertTrue(fixture.events.isEmpty()); assertTrue(fixture.completed.isEmpty()); }
    }

    @Test
    void targetVersionStateCapacityAndDatePrecedePrimaryLookup() {
        var stale = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.REVOKED, false, 3, DATE.minusDays(1));
        assertInstanceOf(ApplicationFailure.IdentifierVersionMismatch.class, failure(stale.useCase().execute(stale.command(true, null, 0))).failure());
        for (var state : List.of(PartyIdentifierStatus.PENDING_VERIFICATION, PartyIdentifierStatus.REJECTED, PartyIdentifierStatus.EXPIRED, PartyIdentifierStatus.REVOKED)) {
            var terminal = new Harness(PartyType.LEGAL_ENTITY, state, false, Long.MAX_VALUE, DATE.minusDays(1));
            assertInstanceOf(ApplicationFailure.InvalidIdentifierLifecycle.class, failure(terminal.useCase().execute(terminal.command(true, null, Long.MAX_VALUE))).failure());
            assertFalse(terminal.order.contains("primary")); assertTrue(terminal.writes.isEmpty());
        }
        var exhausted = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.VERIFIED, false, Long.MAX_VALUE, DATE.minusDays(1));
        assertInstanceOf(ApplicationFailure.IdentifierVersionExhausted.class, failure(exhausted.useCase().execute(exhausted.command(true, null, Long.MAX_VALUE))).failure());
        var expired = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.VERIFIED, false, 3, DATE.minusDays(1));
        assertInstanceOf(ApplicationFailure.IdentifierValidationFailure.class, failure(expired.useCase().execute(expired.command(true, null, 3))).failure());
        for (var fixture : List.of(stale, exhausted, expired)) { assertFalse(fixture.order.contains("primary")); assertTrue(fixture.writes.isEmpty()); }
    }

    @Test
    void completedReplayAndChangedInputConflictPrecedeAbsentOwnerAndCurrentPrimaries() {
        var fixture = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, DATE);
        fixture.primary = former(fixture.current, 9, DATE);
        var command = fixture.command(true, "key", 3); var original = item(fixture.useCase().execute(command));
        fixture.order.clear(); fixture.missingOwner = true;
        var retryMetadata = new RequestMetadata(fixture.metadata.tenantId(), "retry-actor", UUID.randomUUID());
        var retry = new SetPrimaryPartyIdentifierCommand(retryMetadata, command.partyId(), command.identifierId(), command.expectedVersion(), command.idempotencyKey(), true);
        var replay = item(fixture.useCase().execute(retry)); assertEquals(original.identifier(), replay.identifier());
        assertEquals(PartyIdentifierMutationOutcome.Disposition.REPLAYED, replay.disposition()); assertEquals(1, fixture.clock.reads);
        assertEquals(List.of("fingerprint", "transaction", "key", "completed"), fixture.order); assertEquals(2, fixture.writes.size()); assertEquals(2, fixture.events.size());
        fixture.digest = "b".repeat(64);
        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, failure(fixture.useCase().execute(command)).failure());
        assertEquals(2, fixture.writes.size()); assertEquals(2, fixture.events.size());
    }

    @Test
    void missingOwnerAndTargetAreConcealedBeforeVersionAndNoClockRuns() {
        var owner = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, DATE); owner.missingOwner = true;
        assertInstanceOf(ApplicationFailure.PartyNotFound.class, failure(owner.useCase().execute(owner.command(false, null, 0))).failure());
        var target = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, DATE); target.missingTarget = true;
        assertInstanceOf(ApplicationFailure.IdentifierNotFound.class, failure(target.useCase().execute(target.command(false, null, 0))).failure());
        assertEquals(0, owner.clock.reads); assertEquals(0, target.clock.reads);
    }

    @Test
    void demotionFailurePreventsPromotionCompletionAndEventsAndPipelineIsLazy() {
        var fixture = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, DATE);
        fixture.primary = former(fixture.current, 9, DATE); fixture.writeFailure = new IllegalStateException("Controlled primary demotion failure");
        var operation = fixture.useCase().execute(fixture.command(true, "key", 3)); assertTrue(fixture.order.isEmpty());
        var error = operation.subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(Duration.ofSeconds(2)).getFailure();
        assertSame(fixture.writeFailure, error); assertFalse(fixture.order.contains("promote")); assertTrue(fixture.completed.isEmpty()); assertTrue(fixture.events.isEmpty());
    }

    private static PartyIdentifier former(PartyIdentifier source, long version, LocalDate expiration) {
        return PartyIdentifier.builder().identifierId(new PartyIdentifierId(UUID.randomUUID())).tenantId(source.tenantId()).partyId(source.partyId())
                .identifierSchemeId(source.identifierSchemeId()).protectedValue(source.protectedValue()).issuerCode(source.issuerCode()).issuedOn(source.issuedOn())
                .expiresOn(expiration).primary(true).status(PartyIdentifierStatus.VERIFIED).verifiedAt(source.verifiedAt()).verifiedBy(source.verifiedBy())
                .version(new PartyIdentifierVersion(version)).auditInfo(source.auditInfo()).build();
    }
    private static void assertPreserved(PartyIdentifier before, PartyIdentifier after) {
        assertSame(before.protectedValue(), after.protectedValue()); assertEquals(before.partyId(), after.partyId()); assertEquals(before.identifierSchemeId(), after.identifierSchemeId());
        assertEquals(before.status(), after.status()); assertEquals(before.verifiedAt(), after.verifiedAt()); assertEquals(before.verifiedBy(), after.verifiedBy());
        assertEquals(before.expiresOn(), after.expiresOn()); assertEquals(before.issuedOn(), after.issuedOn()); assertEquals(before.issuerCode(), after.issuerCode());
        assertEquals(before.auditInfo().createdAt(), after.auditInfo().createdAt()); assertEquals(before.auditInfo().createdBy(), after.auditInfo().createdBy());
    }
    private static <T> T item(Uni<T> operation) { return operation.subscribe().withSubscriber(UniAssertSubscriber.create()).awaitItem(Duration.ofSeconds(2)).assertCompleted().getItem(); }
    private static ApplicationException failure(Uni<?> operation) { return assertInstanceOf(ApplicationException.class, operation.subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(Duration.ofSeconds(2)).getFailure()); }

    /** Records primary-only callback capabilities and rejects catalog, protection, creation and withdrawal operations. */
    private static final class Harness implements PartyIdentifierMutationContext, PartyIdentifierFingerprintPort {
        final PartyType type;
        final PartyIdentifier current;
        final RequestMetadata metadata = new RequestMetadata(PartyIdentifierFixtures.TENANT, "primary-operator", UUID.randomUUID());
        final List<String> order = new ArrayList<>();
        final List<PartyIdentifier> writes = new ArrayList<>();
        final List<OutboxEventCandidate> events = new ArrayList<>();
        final CountingClock clock = new CountingClock();
        PartyIdentifier primary;
        Optional<CompletedPartyIdentifierMutation> completed = Optional.empty();
        String digest = "a".repeat(64);
        Throwable writeFailure;
        boolean missingOwner;
        boolean missingTarget;
        Harness(PartyType type, PartyIdentifierStatus status, boolean flag, long version, LocalDate expiration) {
            this.type = type; current = PartyIdentifierFixtures.identifier(type, status, flag, version, expiration);
        }
        SetPrimaryPartyIdentifierUseCase useCase() {
            PartyIdentifierMutationPort mutations = (request, work) -> Uni.createFrom().deferred(() -> { order.add("transaction"); return work.apply(this); });
            return new SetPrimaryPartyIdentifierUseCase(mutations, this, clock, new RecordingOperationObserver());
        }
        SetPrimaryPartyIdentifierCommand command(boolean replace, String key, long version) {
            return new SetPrimaryPartyIdentifierCommand(metadata, current.partyId(), current.identifierId(), new PartyIdentifierVersion(version), key, replace);
        }
        @Override public String fingerprint(TenantId tenant, PartyIdentifierActionRequest request) { order.add("fingerprint"); return digest; }
        @Override public String fingerprint(TenantId tenant, PartyIdentifierCreationRequest request) { throw unexpected(); }
        @Override public boolean matches(String stored, String next) { return stored.equals(next); }
        @Override public TenantId tenantId() { return metadata.tenantId(); }
        @Override public Uni<Void> serializeReplayKey(PartyIdentifierOperation operation, String key) { assertEquals(PartyIdentifierOperation.SET_PRIMARY, operation); order.add("key"); return Uni.createFrom().voidItem(); }
        @Override public Uni<Optional<CompletedPartyIdentifierMutation>> findCompleted(PartyIdentifierOperation operation, String key) { order.add("completed"); return Uni.createFrom().item(completed); }
        @Override public Uni<Optional<PartyType>> findOwnedParty(PartyId party) { order.add("owner"); return Uni.createFrom().item(missingOwner ? Optional.empty() : Optional.of(type)); }
        @Override public Uni<Optional<IdentifierSchemeId>> findIdentifierSchemeId(PartyId party, PartyIdentifierId id) { order.add("identity"); return Uni.createFrom().item(Optional.of(current.identifierSchemeId())); }
        @Override public Uni<Void> serializeScope(PartyId party, IdentifierSchemeId scheme) { order.add("scope"); return Uni.createFrom().voidItem(); }
        @Override public Uni<Optional<PartyIdentifier>> findForUpdate(PartyId party, PartyIdentifierId id) { order.add("target"); return Uni.createFrom().item(missingTarget ? Optional.empty() : Optional.of(current)); }
        @Override public Uni<Optional<PartyIdentifier>> findCurrentPrimary(PartyId party, IdentifierSchemeId scheme) { order.add("primary"); return Uni.createFrom().item(Optional.ofNullable(primary)); }
        @Override public Uni<PartyIdentifierResult> updateIdentifier(PartyIdentifier value, PartyIdentifierVersion expected) {
            boolean target = value.identifierId().equals(current.identifierId()); order.add(target ? "promote" : "demote");
            assertEquals(target ? current.version() : primary.version(), expected);
            if (writeFailure != null) { return Uni.createFrom().failure(writeFailure); }
            writes.add(value); return Uni.createFrom().item(PartyIdentifierFixtures.result(value));
        }
        @Override public Uni<Void> recordCompletion(PartyIdentifierOperation operation, String key, CompletedPartyIdentifierMutation result) { order.add("completion"); completed = Optional.of(result); return Uni.createFrom().voidItem(); }
        @Override public Uni<Void> appendEnabledEvent(OutboxEventCandidate event) { order.add("event"); events.add(event); return Uni.createFrom().voidItem(); }
        @Override public Uni<Optional<IdentifierSchemeId>> findSchemeIdByCode(String code) { throw unexpected(); }
        @Override public Uni<Optional<IdentifierScheme>> findSchemeForRegistration(IdentifierSchemeId id) { throw unexpected(); }
        @Override public Uni<PartyIdentifierResult> insertIdentifier(PartyIdentifier value) { throw unexpected(); }
        @Override public Uni<Void> appendActionAudit(PartyIdentifierActionAudit audit) { throw unexpected(); }
        private static AssertionError unexpected() { return new AssertionError("Primary selection must not readmit/protect evidence or write withdrawal audit"); }
    }
    /** Crosses UTC midnight on a second read to detect accidental multiple operation instants. */
    private static final class CountingClock extends Clock {
        int reads;
        @Override public ZoneId getZone() { return ZoneOffset.ofHours(14); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(NOW, zone); }
        @Override public Instant instant() { return reads++ == 0 ? NOW : NOW.plusSeconds(2); }
    }
}
