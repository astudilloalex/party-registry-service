package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.PatchPartyCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies post-lock clock capture, failure precedence, all-state label corrections, safe event intent, and no replay work. */
class PatchPartyUseCaseTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Instant CREATED = LocalDate.of(2026, Month.SEPTEMBER, 19).atStartOfDay().toInstant(ZoneOffset.UTC);
    private static final Instant NOW = CREATED.plusSeconds(1).plusNanos(123456789);
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final PartyId PARTY_ID = new PartyId(UUID.randomUUID());
    private static final RequestMetadata METADATA = new RequestMetadata(TENANT, "corrector", UUID.randomUUID());
    private final RecordingOperationObserver observations = new RecordingOperationObserver();

    @Test
    void correctsBothTypesInAllStatesWithOnePostLockInstantAndSafeEvent() {
        for (PartyType type : PartyType.values()) {
            for (PartyRecordStatus status : PartyRecordStatus.values()) {
                var port = new PartyMutationPortStub();
                Party original = fixture(type, status, PartyVersion.initial());
                port.current = Optional.of(original);
                var clock = new PostLockClock(port);
                var command = new PatchPartyCommand(METADATA, PARTY_ID, original.version(), "  Áda  Changed  ");
                var outcome = new PatchPartyUseCase(port, clock, observations).execute(command).await().atMost(TIMEOUT);
                assertEquals(new RecordingOperationObserver.CompletedCall(METADATA, ObservedOperation.APPLICATION_PARTY_PATCH,
                        OperationOutcome.APPLIED), observations.completedCalls().getLast());
                Party expected = original.correctDisplayName(command.displayName(), NOW.truncatedTo(ChronoUnit.MICROS), METADATA.userId());
                assertEquals(PartyDetailsResult.fromAggregate(expected), outcome.party());
                assertEquals(PartyMutationOutcome.Disposition.APPLIED, outcome.disposition());
                assertEquals(List.of("begin", "root", "write", "event", "commit"), port.calls);
                assertEquals(1, clock.reads);
                assertSame(METADATA, port.metadata);
                assertEquals(original.version(), port.expectedVersion);
                var event = assertInstanceOf(PartyChangedOutboxCandidate.class, port.events.getFirst());
                assertEquals(PartyChangedOutboxCandidate.Kind.UPDATED, event.kind());
                assertEquals(expected.version(), event.partyVersion());
                assertEquals(NOW.truncatedTo(ChronoUnit.MICROS), event.occurredAt());
                assertEquals(METADATA.processId(), event.correlationId());
                assertEquals(METADATA.userId(), event.createdBy());
                assertTrue(port.completion.isEmpty());
            }
        }
    }

    @Test
    void absencePrecedesStaleVersionAndBlankNameWithoutConsultingTheClock() {
        var port = new PartyMutationPortStub();
        port.current = Optional.of(fixture(PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED, new PartyVersion(5)));
        var clock = new PostLockClock(port);
        var other = new RequestMetadata(new TenantId(UUID.randomUUID()), "reader", UUID.randomUUID());
        var command = new PatchPartyCommand(other, PARTY_ID, PartyVersion.initial(), " ");
        var application = assertInstanceOf(ApplicationException.class, failure(new PatchPartyUseCase(port, clock, observations).execute(command)));
        assertEquals(new ApplicationFailure.PartyNotFound(PARTY_ID, other.tenantId()), application.failure());
        assertEquals(List.of("begin", "root", "rollback"), port.calls);
        assertEquals(0, clock.reads);
        assertNull(port.candidate);
    }

    @Test
    void staleVersionPrecedesBlankNameAndDoesNotCreateEventIntent() {
        var port = new PartyMutationPortStub();
        port.current = Optional.of(fixture(PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED, new PartyVersion(5)));
        var clock = new PostLockClock(port);
        var command = new PatchPartyCommand(METADATA, PARTY_ID, PartyVersion.initial(), "");
        var application = assertInstanceOf(ApplicationException.class, failure(new PatchPartyUseCase(port, clock, observations).execute(command)));
        assertEquals(OperationOutcome.PRECONDITION_FAILED, observations.completedCalls().getFirst().outcome());
        assertEquals(new ApplicationFailure.ExpectedVersionMismatch(PartyVersion.initial(), new PartyVersion(5)), application.failure());
        assertEquals(List.of("begin", "root", "rollback"), port.calls);
        assertEquals(0, clock.reads);
        assertTrue(port.events.isEmpty());
    }

    @Test
    void mapsOnlyRecognizedLabelViolationsAndKeepsVersionOverflowInternal() {
        for (String name : List.of("", " ", "a".repeat(301))) {
            var port = new PartyMutationPortStub();
            port.current = Optional.of(fixture(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT, PartyVersion.initial()));
            var command = new PatchPartyCommand(METADATA, PARTY_ID, PartyVersion.initial(), name);
            var application = assertInstanceOf(ApplicationException.class,
                    failure(new PatchPartyUseCase(port, new PostLockClock(port), observations).execute(command)));
            var violation = assertInstanceOf(ApplicationFailure.InvalidBusinessState.class, application.failure());
            assertEquals(name.isBlank() ? DomainViolation.DISPLAY_NAME_REQUIRED : DomainViolation.DISPLAY_NAME_TOO_LONG, violation.violation());
            assertEquals(List.of("begin", "root", "rollback"), port.calls);
        }
        var port = new PartyMutationPortStub();
        var maximum = new PartyVersion(Long.MAX_VALUE);
        port.current = Optional.of(fixture(PartyType.LEGAL_ENTITY, PartyRecordStatus.ACTIVE, maximum));
        var command = new PatchPartyCommand(METADATA, PARTY_ID, maximum, "Valid Label");
        assertInstanceOf(IllegalStateException.class, failure(new PatchPartyUseCase(port, new PostLockClock(port), observations).execute(command)));
        assertNull(port.candidate);
        assertTrue(port.events.isEmpty());
    }

    @Test
    void identicalCanonicalCorrectionIsAppliedAgainAtTheNextVersion() {
        var port = new PartyMutationPortStub();
        port.current = Optional.of(fixture(PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED, PartyVersion.initial()));
        var useCase = new PatchPartyUseCase(port, new PostLockClock(port), observations);
        var first = useCase.execute(new PatchPartyCommand(METADATA, PARTY_ID, PartyVersion.initial(), "same")).await().atMost(TIMEOUT);
        var second = useCase.execute(new PatchPartyCommand(METADATA, PARTY_ID, first.party().version(), "  SAME  ")).await().atMost(TIMEOUT);
        assertEquals(first.party().displayName(), second.party().displayName());
        assertEquals(2, second.party().version().value());
        assertEquals(2, port.events.size());
        assertEquals(PartyRecordStatus.ARCHIVED, second.party().recordStatus());
        assertTrue(port.completion.isEmpty());
    }

    @Test
    void preservesStorageFailuresAndCancellationWithoutCreatingAnEvent() {
        for (Throwable expected : List.of(new ApplicationException(new ApplicationFailure.ExpectedVersionMismatch(
                PartyVersion.initial(), new PartyVersion(1))), new CancellationException("Controlled cancellation"))) {
            var port = new PartyMutationPortStub();
            port.current = Optional.of(fixture(PartyType.LEGAL_ENTITY, PartyRecordStatus.DRAFT, PartyVersion.initial()));
            port.writeFailure = expected;
            var command = new PatchPartyCommand(METADATA, PARTY_ID, PartyVersion.initial(), "Valid Label");
            assertSame(expected, failure(new PatchPartyUseCase(port, new PostLockClock(port), observations).execute(command)));
            assertEquals(expected instanceof CancellationException ? OperationOutcome.CANCELLED : OperationOutcome.PRECONDITION_FAILED,
                    observations.completedCalls().getLast().outcome());
            assertEquals(List.of("begin", "root", "write", "rollback"), port.calls);
            assertTrue(port.events.isEmpty());
        }
    }

    private static Throwable failure(Uni<?> operation) {
        var received = new AtomicReference<Throwable>();
        operation.subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(received::set, TIMEOUT).assertFailed();
        return received.get();
    }

    private static Party fixture(PartyType type, PartyRecordStatus status, PartyVersion version) {
        var audit = AuditInfo.initial(CREATED, "creator");
        return switch (type) {
            case NATURAL_PERSON -> NaturalPerson.restore(PARTY_ID, TENANT, " Historical Label ", status, version, audit,
                    new NaturalPersonDetails("Mixed", "Case", " Old ", null, null, "GB"));
            case LEGAL_ENTITY -> LegalEntity.restore(PARTY_ID, TENANT, " Historical Company ", status, version, audit,
                    new LegalEntityDetails("Mixed Company", " Trade ", null, "GB", null, null));
        };
    }

    /** Counts clock reads and rejects any timestamp capture that occurs before the root lookup. */
    private static final class PostLockClock extends Clock {
        private final PartyMutationPortStub port;
        private final Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
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
