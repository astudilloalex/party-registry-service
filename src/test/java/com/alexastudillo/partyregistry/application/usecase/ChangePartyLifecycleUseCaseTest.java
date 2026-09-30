package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ChangePartyLifecycleCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedPartyLifecycle;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleRequest;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.PartyActivationEvidence;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Application-owned lifecycle precedence, Domain delegation,
 * historical replay, exact request identity, and safe event intent.
 */
class ChangePartyLifecycleUseCaseTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final LocalDate DAY = LocalDate.of(2026, Month.SEPTEMBER, 19);
    private static final Instant NOW = DAY.atTime(0, 0, 0, 123456789).toInstant(ZoneOffset.UTC);
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final PartyId PARTY_ID = new PartyId(UUID.randomUUID());
    private static final PartyVersion VERSION = new PartyVersion(4);
    private static final RequestMetadata METADATA = new RequestMetadata(TENANT, "original-actor", UUID.randomUUID());
    private static final String KEY = " Exact Replay Key ";

    @Test
    void orchestratesTheCompleteDomainTransitionMatrixForBothTypes() {
        for (PartyType type : PartyType.values()) {
            for (PartyRecordStatus status : PartyRecordStatus.values()) {
                for (PartyLifecycleAction action : PartyLifecycleAction.values()) {
                    Party original = fixture(type, status, VERSION);
                    var port = port(original);
                    var clock = new PostLockClock(port);
                    var command = command(action, VERSION, Optional.of(KEY));
                    Uni<PartyMutationOutcome> operation = useCase(port, clock).execute(command);
                    if (!eligible(status, action)) {
                        assertFailure(operation, ApplicationFailure.InvalidPartyLifecycle.class);
                        assertEquals(List.of("begin", "serialize", "completion", "root", "rollback"), port.calls);
                        assertNull(port.candidate);
                        assertTrue(port.events.isEmpty());
                        continue;
                    }
                    PartyMutationOutcome result = operation.await().atMost(TIMEOUT);
                    assertEquals(PartyMutationOutcome.Disposition.APPLIED, result.disposition());
                    assertEquals(target(action), result.party().recordStatus());
                    assertEquals(5, result.party().version().value());
                    assertEquals(original.displayName(), result.party().displayName());
                    assertEquals(original.auditInfo().createdAt(), result.party().createdAt());
                    assertEquals("creator", result.party().createdBy());
                    assertEquals(NOW.truncatedTo(ChronoUnit.MICROS), result.party().updatedAt());
                    assertEquals(METADATA.userId(), result.party().updatedBy());
                    assertEquals(1, clock.reads);
                    var expected = new ArrayList<>(List.of("begin", "serialize", "completion", "root"));
                    if (action == PartyLifecycleAction.ACTIVATE) {
                        expected.add("evidence");
                    }
                    expected.addAll(List.of("write", "record", "event", "commit"));
                    assertEquals(expected, port.calls);
                    assertEquals(new CompletedPartyLifecycle(command.effectiveRequest(), result.party()),
                            port.completion.orElseThrow());
                    assertEquals(KEY, port.key);
                    assertEquals(action, port.action);
                    assertEquals(1, port.events.size());
                    assertEquals(eventType(action), port.events.getFirst().eventType());
                    assertEquals(METADATA.processId(), port.events.getFirst().correlationId());
                }
            }
        }
    }

    @Test
    void replaysEveryOriginalOutcomeBeforeCurrentStateEvidenceOrClockWithFreshAttribution() {
        for (PartyLifecycleAction action : PartyLifecycleAction.values()) {
            Party original = fixture(PartyType.LEGAL_ENTITY, initial(action), VERSION);
            var port = port(original);
            var request = command(action, VERSION, Optional.of(KEY));
            PartyMutationOutcome first = useCase(port, new PostLockClock(port)).execute(request).await()
                    .atMost(TIMEOUT);
            Party accepted = port.current.orElseThrow();
            Party later = action == PartyLifecycleAction.ARCHIVE
                    ? accepted.correctDisplayName("Later label", NOW.plusSeconds(1), "later-actor")
                    : accepted.archive(NOW.plusSeconds(1), "later-actor");
            port.current = Optional.of(later);
            port.evidence = List.of();
            port.calls.clear();
            port.events.clear();
            var retryMetadata = new RequestMetadata(TENANT, "retry-actor", UUID.randomUUID());
            var retry = new ChangePartyLifecycleCommand(retryMetadata, PARTY_ID, VERSION, action, Optional.of(KEY));
            var clock = new PostLockClock(port);
            PartyMutationOutcome replayed = useCase(port, clock).execute(retry).await().atMost(TIMEOUT);
            assertEquals(first.party(), replayed.party());
            assertEquals(PartyMutationOutcome.Disposition.REPLAYED, replayed.disposition());
            assertEquals(METADATA.userId(), replayed.party().updatedBy());
            assertSame(retryMetadata, port.metadata);
            assertSame(later, port.current.orElseThrow());
            assertEquals(0, clock.reads);
            assertEquals(List.of("begin", "serialize", "completion", "commit"), port.calls);
            assertTrue(port.events.isEmpty());
        }
    }

    @Test
    void conflictingPartyOrExpectedVersionFailsBeforeRootLookupWithoutDisclosingTheResult() {
        Party original = fixture(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT, VERSION);
        var saved = new CompletedPartyLifecycle(
                new PartyLifecycleRequest(PartyLifecycleAction.ARCHIVE, PARTY_ID, VERSION),
                PartyDetailsResult.fromAggregate(original.archive(NOW, METADATA.userId())));
        List<ChangePartyLifecycleCommand> conflicts = List.of(
                new ChangePartyLifecycleCommand(METADATA, new PartyId(UUID.randomUUID()), VERSION,
                        PartyLifecycleAction.ARCHIVE, Optional.of(KEY)),
                command(PartyLifecycleAction.ARCHIVE, new PartyVersion(5), Optional.of(KEY)));
        for (ChangePartyLifecycleCommand request : conflicts) {
            var port = new PartyMutationPortStub();
            port.completion = Optional.of(saved);
            var clock = new PostLockClock(port);
            assertFailure(useCase(port, clock).execute(request), ApplicationFailure.IdempotencyKeyConflict.class);
            assertEquals(List.of("begin", "serialize", "completion", "rollback"), port.calls);
            assertEquals(0, clock.reads);
            assertNull(port.candidate);
            assertTrue(port.events.isEmpty());
        }
    }

    @Test
    void absenceAndStaleVersionPrecedeAllLifecycleAndEvidenceChecks() {
        for (PartyLifecycleAction action : PartyLifecycleAction.values()) {
            var port = port(fixture(PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED, VERSION));
            var clock = new PostLockClock(port);
            var useCase = useCase(port, clock);
            var other = new RequestMetadata(new TenantId(UUID.randomUUID()), "other", UUID.randomUUID());
            var absent = new ChangePartyLifecycleCommand(other, PARTY_ID, PartyVersion.initial(), action,
                    Optional.empty());
            assertFailure(useCase.execute(absent), ApplicationFailure.PartyNotFound.class);
            assertEquals(0, clock.reads);
            assertFalse(port.calls.contains("evidence"));
            port.calls.clear();
            assertFailure(useCase.execute(command(action, PartyVersion.initial(), Optional.empty())),
                    ApplicationFailure.StalePartyVersion.class);
            assertEquals(List.of("begin", "root", "rollback"), port.calls);
            assertEquals(0, clock.reads);
        }
    }

    @Test
    void aMissingEvidenceFailureDoesNotConsumeTheKeyAndUtcDateDoesNotFollowClockZone() {
        Party original = fixture(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT, VERSION);
        var port = port(original);
        port.evidence = List.of(evidence(original, DAY.minusDays(1)));
        var clock = new PostLockClock(port);
        var useCase = useCase(port, clock);
        var command = command(PartyLifecycleAction.ACTIVATE, VERSION, Optional.of(KEY));
        assertFailure(useCase.execute(command), ApplicationFailure.MissingQualifyingIdentifier.class);
        assertTrue(port.completion.isEmpty());
        assertTrue(port.events.isEmpty());
        assertNull(port.candidate);
        assertSame(original, port.current.orElseThrow());
        port.evidence = List.of(evidence(original, DAY));
        port.calls.clear();
        PartyMutationOutcome result = useCase.execute(command).await().atMost(TIMEOUT);
        assertEquals(PartyRecordStatus.ACTIVE, result.party().recordStatus());
        assertEquals(2, clock.reads);
        assertEquals(command.effectiveRequest(), port.completion.orElseThrow().request());
    }

    @Test
    void unkeyedRequestsDoNotReplayOrCreateCompletionRecords() {
        Party original = fixture(PartyType.LEGAL_ENTITY, PartyRecordStatus.ACTIVE, VERSION);
        var port = port(original);
        var useCase = useCase(port, new PostLockClock(port));
        var command = command(PartyLifecycleAction.DEACTIVATE, VERSION, Optional.empty());
        PartyMutationOutcome result = useCase.execute(command).await().atMost(TIMEOUT);
        assertEquals(PartyMutationOutcome.Disposition.APPLIED, result.disposition());
        assertEquals(List.of("begin", "root", "write", "event", "commit"), port.calls);
        assertTrue(port.completion.isEmpty());
        port.calls.clear();
        assertFailure(useCase.execute(command), ApplicationFailure.StalePartyVersion.class);
        assertEquals(List.of("begin", "root", "rollback"), port.calls);
    }

    @Test
    void translatesOnlyWriteVersionConflictsAndPreservesOtherFailures() {
        Party original = fixture(PartyType.LEGAL_ENTITY, PartyRecordStatus.ACTIVE, VERSION);
        var command = command(PartyLifecycleAction.DEACTIVATE, VERSION, Optional.of(KEY));
        var port = port(original);
        port.writeFailure = new ApplicationException(
                new ApplicationFailure.ExpectedVersionMismatch(VERSION, new PartyVersion(5)));
        var failure = assertInstanceOf(ApplicationException.class,
                failure(useCase(port, new PostLockClock(port)).execute(command)));
        assertEquals(new ApplicationFailure.StalePartyVersion(VERSION, new PartyVersion(5)), failure.failure());
        assertTrue(port.completion.isEmpty());
        assertTrue(port.events.isEmpty());
        for (Throwable expected : List.of(new ApplicationException(new ApplicationFailure.PersistenceFailure()),
                new CancellationException("Controlled cancellation"))) {
            port.writeFailure = expected;
            assertSame(expected, failure(useCase(port, new PostLockClock(port)).execute(command)));
        }
    }

    @Test
    void acceptedVersionOverflowIsAnInternalInvariantFailure() {
        var maximum = new PartyVersion(Long.MAX_VALUE);
        var port = port(fixture(PartyType.LEGAL_ENTITY, PartyRecordStatus.DRAFT, maximum));
        var command = command(PartyLifecycleAction.ARCHIVE, maximum, Optional.of(KEY));
        assertInstanceOf(IllegalStateException.class, failure(useCase(port, new PostLockClock(port)).execute(command)));
        assertNull(port.candidate);
        assertTrue(port.completion.isEmpty());
        assertTrue(port.events.isEmpty());
    }

    private static void assertFailure(Uni<?> operation, Class<? extends ApplicationFailure> type) {
        var application = assertInstanceOf(ApplicationException.class, failure(operation));
        assertInstanceOf(type, application.failure());
    }

    private static ChangePartyLifecycleUseCase useCase(PartyMutationPortStub port, Clock clock) {
        return new ChangePartyLifecycleUseCase(port, clock, new RecordingOperationObserver());
    }

    private static Throwable failure(Uni<?> operation) {
        var received = new AtomicReference<Throwable>();
        operation.subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(received::set, TIMEOUT)
                .assertFailed();
        return received.get();
    }

    private static ChangePartyLifecycleCommand command(PartyLifecycleAction action, PartyVersion version,
            Optional<String> key) {
        return new ChangePartyLifecycleCommand(METADATA, PARTY_ID, version, action, key);
    }

    private static PartyMutationPortStub port(Party original) {
        var port = new PartyMutationPortStub();
        port.current = Optional.of(original);
        port.evidence = List.of(evidence(original, DAY));
        return port;
    }

    private static PartyActivationEvidence evidence(Party party, LocalDate expiration) {
        var scheme = new IdentifierSchemeId(UUID.randomUUID());
        return new PartyActivationEvidence(party.tenantId(), party.partyId(), scheme, scheme,
                PartyIdentifierStatus.VERIFIED, expiration, IdentifierSubjectType.BOTH);
    }

    private static Party fixture(PartyType type, PartyRecordStatus status, PartyVersion version) {
        var audit = AuditInfo.initial(NOW.minusSeconds(1).truncatedTo(ChronoUnit.MICROS), "creator");
        return switch (type) {
            case NATURAL_PERSON -> NaturalPerson.restore(PARTY_ID, TENANT, " Historical Label ", status, version, audit,
                    new NaturalPersonDetails("Mixed", "Case", null, null, null, "GB"));
            case LEGAL_ENTITY -> LegalEntity.restore(PARTY_ID, TENANT, " Historical Company ", status, version, audit,
                    new LegalEntityDetails("Mixed Company", null, null, "GB", null, null));
        };
    }

    private static boolean eligible(PartyRecordStatus status, PartyLifecycleAction action) {
        return switch (action) {
            case ACTIVATE -> status == PartyRecordStatus.DRAFT;
            case DEACTIVATE -> status == PartyRecordStatus.ACTIVE;
            case ARCHIVE -> status != PartyRecordStatus.ARCHIVED;
        };
    }

    private static PartyRecordStatus initial(PartyLifecycleAction action) {
        return action == PartyLifecycleAction.DEACTIVATE ? PartyRecordStatus.ACTIVE : PartyRecordStatus.DRAFT;
    }

    private static PartyRecordStatus target(PartyLifecycleAction action) {
        return switch (action) {
            case ACTIVATE -> PartyRecordStatus.ACTIVE;
            case DEACTIVATE -> PartyRecordStatus.INACTIVE;
            case ARCHIVE -> PartyRecordStatus.ARCHIVED;
        };
    }

    private static String eventType(PartyLifecycleAction action) {
        return switch (action) {
            case ACTIVATE -> "party.activated.v1";
            case DEACTIVATE -> "party.deactivated.v1";
            case ARCHIVE -> "party.archived.v1";
        };
    }

    /**
     * Verifies one timestamp read after replay/root resolution while presenting a
     * deliberately non-UTC clock zone.
     */
    private static final class PostLockClock extends Clock {
        private final Clock fixed = Clock.fixed(NOW, ZoneOffset.ofHours(-7));
        private final PartyMutationPortStub port;
        private int reads;

        private PostLockClock(PartyMutationPortStub port) {
            this.port = port;
        }

        @Override
        public ZoneId getZone() {
            return fixed.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return fixed.withZone(zone);
        }

        @Override
        public Instant instant() {
            assertEquals("root", port.calls.getLast());
            reads++;
            return fixed.instant();
        }
    }
}
