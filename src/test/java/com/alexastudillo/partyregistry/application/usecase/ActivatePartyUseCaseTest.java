package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ActivatePartyCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyActivationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.port.PartyActivationPort;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.METADATA;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.PARTY_ID;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.TENANT_ID;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitItem;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitThrowable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies trusted-time enrichment and unchanged propagation of atomic activation outcomes.
 */
class ActivatePartyUseCaseTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T00:30:00Z");
    private static final PartyVersion EXPECTED_VERSION = new PartyVersion(7);
    private static final ActivatePartyCommand COMMAND = new ActivatePartyCommand(
            METADATA,
            PARTY_ID,
            EXPECTED_VERSION);
    private static final PartyDetailsResult ACTIVATED_RESULT = new NaturalPersonResult(
            PARTY_ID,
            TENANT_ID,
            PartyType.NATURAL_PERSON,
            "Ada Lovelace",
            PartyRecordStatus.ACTIVE,
            EXPECTED_VERSION.next(),
            "Ada",
            "Lovelace",
            null,
            null,
            null,
            null,
            OCCURRED_AT.minusSeconds(60),
            "creator",
            OCCURRED_AT,
            METADATA.userId());

    @Test
    void delegatesExactlyOnceWithTheExactCandidateAndReturnsThePortUni() {
        RecordingActivationPort port = new RecordingActivationPort();
        Uni<PartyDetailsResult> portOutcome = Uni.createFrom().item(ACTIVATED_RESULT);
        port.outcome = portOutcome;
        RecordingOperationObserver observer = new RecordingOperationObserver();
        ActivatePartyUseCase useCase = new ActivatePartyUseCase(
                port,
                Clock.fixed(OCCURRED_AT, ZoneOffset.UTC),
                observer);

        Uni<PartyDetailsResult> returned = useCase.execute(COMMAND);
        PartyDetailsResult result = awaitItem(returned);

        assertSame(ACTIVATED_RESULT, result);
        assertEquals(EXPECTED_VERSION.next(), result.version());
        assertEquals(1, port.candidates.size());
        PartyActivationCandidate candidate = port.candidates.getFirst();
        assertEquals(new PartyActivationCandidate(
                METADATA,
                PARTY_ID,
                EXPECTED_VERSION,
                LocalDate.of(2026, 9, 1),
                OCCURRED_AT), candidate);
        assertSame(METADATA, candidate.requestMetadata());
        assertSame(PARTY_ID, candidate.partyId());
        assertSame(EXPECTED_VERSION, candidate.expectedVersion());
        assertEquals(TENANT_ID, candidate.requestMetadata().tenantId());
        assertEquals(new RecordingOperationObserver.CompletedCall(
                        METADATA,
                        ObservedOperation.APPLICATION_PARTY_ACTIVATION,
                        OperationOutcome.ACTIVATED),
                observer.completedCalls().getFirst());
    }

    @Test
    void derivesTheUtcDateFromOneInstantRegardlessOfTheClockZone() {
        RecordingActivationPort port = new RecordingActivationPort();
        CountingClock clock = new CountingClock(
                OCCURRED_AT,
                ZoneId.of("America/Los_Angeles"));
        ActivatePartyUseCase useCase = new ActivatePartyUseCase(
                port,
                clock,
                new RecordingOperationObserver());

        awaitItem(useCase.execute(COMMAND));

        assertEquals(1, clock.instantCalls);
        assertEquals(1, port.candidates.size());
        PartyActivationCandidate candidate = port.candidates.getFirst();
        assertEquals(OCCURRED_AT, candidate.occurredAt());
        assertEquals(LocalDate.of(2026, 9, 1), candidate.evaluatedOn());
        assertEquals(LocalDate.of(2026, 8, 31),
                LocalDate.ofInstant(OCCURRED_AT, clock.getZone()));
    }

    @Test
    void propagatesEachPortOwnedFailurePrecedenceOutcomeUnchanged() {
        List<ApplicationFailure> precedence = List.of(
                new ApplicationFailure.PartyNotFound(PARTY_ID, TENANT_ID),
                new ApplicationFailure.StalePartyVersion(EXPECTED_VERSION, EXPECTED_VERSION.next()),
                new ApplicationFailure.InvalidPartyLifecycle(PARTY_ID, PartyRecordStatus.ACTIVE),
                new ApplicationFailure.MissingQualifyingIdentifier(PARTY_ID));

        for (ApplicationFailure expectedFailure : precedence) {
            RecordingActivationPort port = new RecordingActivationPort();
            ApplicationException expectedException = new ApplicationException(expectedFailure);
            Uni<PartyDetailsResult> portOutcome = Uni.createFrom().failure(expectedException);
            port.outcome = portOutcome;
            ActivatePartyUseCase useCase = new ActivatePartyUseCase(
                    port,
                    Clock.fixed(OCCURRED_AT, ZoneOffset.UTC),
                    new RecordingOperationObserver());

            Uni<PartyDetailsResult> returned = useCase.execute(COMMAND);
            Throwable actual = awaitThrowable(returned);

            assertSame(expectedException, actual);
            assertSame(expectedFailure, expectedException.failure());
            assertEquals(1, port.candidates.size());
        }
    }

    @Test
    void propagatesUnexpectedFailureUnchangedWithoutASecondPortCall() {
        RecordingActivationPort port = new RecordingActivationPort();
        IllegalStateException expected = new IllegalStateException("unexpected");
        Uni<PartyDetailsResult> portOutcome = Uni.createFrom().failure(expected);
        port.outcome = portOutcome;
        ActivatePartyUseCase useCase = new ActivatePartyUseCase(
                port,
                Clock.fixed(OCCURRED_AT, ZoneOffset.UTC),
                new RecordingOperationObserver());

        Uni<PartyDetailsResult> returned = useCase.execute(COMMAND);

        assertSame(expected, awaitThrowable(returned));
        assertEquals(1, port.candidates.size());
    }

    @Test
    void propagatesCancellationWithoutASecondPortCall() {
        RecordingActivationPort port = new RecordingActivationPort();
        AtomicBoolean cancelled = new AtomicBoolean();
        Uni<PartyDetailsResult> portOutcome = Uni.createFrom().<PartyDetailsResult>nothing()
                .onCancellation().invoke(() -> cancelled.set(true));
        port.outcome = portOutcome;
        ActivatePartyUseCase useCase = new ActivatePartyUseCase(
                port,
                Clock.fixed(OCCURRED_AT, ZoneOffset.UTC),
                new RecordingOperationObserver());

        Uni<PartyDetailsResult> returned = useCase.execute(COMMAND);
        UniAssertSubscriber<PartyDetailsResult> subscriber = returned.subscribe()
                .withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitSubscription(TIMEOUT).cancel();

        assertTrue(cancelled.get());
        assertEquals(1, port.candidates.size());
    }

    @Test
    void rejectsNullCommandAndDependenciesBeforeDelegation() {
        RecordingActivationPort port = new RecordingActivationPort();
        CountingClock clock = new CountingClock(OCCURRED_AT, ZoneOffset.UTC);
        RecordingOperationObserver observer = new RecordingOperationObserver();
        ActivatePartyUseCase useCase = new ActivatePartyUseCase(port, clock, observer);

        assertThrows(NullPointerException.class, () -> useCase.execute(null));
        assertThrows(NullPointerException.class, () -> new ActivatePartyUseCase(null, clock, observer));
        assertThrows(NullPointerException.class, () -> new ActivatePartyUseCase(port, null, observer));
        assertThrows(NullPointerException.class, () -> new ActivatePartyUseCase(port, clock, null));
        assertTrue(port.candidates.isEmpty());
        assertEquals(0, clock.instantCalls);
    }

    /** Records each atomic activation request and returns one configurable outcome. */
    private static final class RecordingActivationPort implements PartyActivationPort {

        private final List<PartyActivationCandidate> candidates = new ArrayList<>();
        private Uni<PartyDetailsResult> outcome = Uni.createFrom().item(ACTIVATED_RESULT);

        @Override
        public Uni<PartyDetailsResult> activate(PartyActivationCandidate candidate) {
            candidates.add(candidate);
            return outcome;
        }
    }

    /** Supplies one deterministic instant while exposing how often it was requested. */
    private static final class CountingClock extends Clock {

        private final Instant value;
        private final ZoneId zone;
        private int instantCalls;

        private CountingClock(Instant value, ZoneId zone) {
            this.value = value;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone) {
            return new CountingClock(value, requestedZone);
        }

        @Override
        public Instant instant() {
            instantCalls++;
            return value;
        }
    }
}
