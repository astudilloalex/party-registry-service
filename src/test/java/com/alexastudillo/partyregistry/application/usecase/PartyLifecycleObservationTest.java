package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ChangePartyLifecycleCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyMutationPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preserves lazy subscription-scoped activation observations while extending
 * bounded lifecycle and replay outcomes.
 */
class PartyLifecycleObservationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Clock CLOCK = Clock
            .fixed(LocalDate.of(2026, Month.SEPTEMBER, 19).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "actor",
            UUID.randomUUID());
    private static final PartyId PARTY_ID = new PartyId(UUID.randomUUID());

    @ParameterizedTest
    @EnumSource(PartyLifecycleAction.class)
    void observesEachAppliedOrReplayedResultOnlyAfterSubscription(PartyLifecycleAction action) {
        for (PartyMutationOutcome.Disposition disposition : PartyMutationOutcome.Disposition.values()) {
            var expected = result(action, disposition);
            var calls = new AtomicInteger();
            PartyMutationPort port = (metadata, work) -> {
                calls.incrementAndGet();
                assertSame(METADATA, metadata);
                return Uni.createFrom().item(expected);
            };
            var observer = new RecordingOperationObserver();
            var useCase = new ChangePartyLifecycleUseCase(port, CLOCK, observer);
            Uni<PartyMutationOutcome> operation = useCase.execute(command(action));
            assertEquals(List.of(), observer.startedCalls());
            assertEquals(0, calls.get());
            assertSame(expected, operation.await().atMost(TIMEOUT));
            assertEquals(1, calls.get());
            assertEquals(List.of(new RecordingOperationObserver.StartedCall(METADATA, operation(action))),
                    observer.startedCalls());
            OperationOutcome outcome = disposition == PartyMutationOutcome.Disposition.REPLAYED
                    ? OperationOutcome.REPLAYED
                    : switch (action) {
                        case ACTIVATE -> OperationOutcome.ACTIVATED;
                        case DEACTIVATE -> OperationOutcome.DEACTIVATED;
                        case ARCHIVE -> OperationOutcome.ARCHIVED;
                    };
            assertEquals(List.of(new RecordingOperationObserver.CompletedCall(METADATA, operation(action), outcome)),
                    observer.completedCalls());
        }
    }

    @Test
    void preservesKnownAndUnexpectedFailuresAcrossTheObservationBoundary() {
        List<Throwable> failures = List.of(
                new ApplicationException(new ApplicationFailure.PartyNotFound(PARTY_ID, METADATA.tenantId())),
                new ApplicationException(
                        new ApplicationFailure.StalePartyVersion(PartyVersion.initial(), new PartyVersion(1))),
                new ApplicationException(
                        new ApplicationFailure.InvalidPartyLifecycle(PARTY_ID, PartyRecordStatus.ACTIVE)),
                new ApplicationException(new ApplicationFailure.MissingQualifyingIdentifier(PARTY_ID)),
                new IllegalStateException("Controlled failure"));
        for (Throwable expected : failures) {
            PartyMutationPort port = (metadata, work) -> Uni.createFrom().failure(expected);
            var observer = new RecordingOperationObserver();
            var operation = new ChangePartyLifecycleUseCase(port, CLOCK, observer)
                    .execute(command(PartyLifecycleAction.ACTIVATE));
            operation.subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitFailure(actual -> assertSame(expected, actual), TIMEOUT).assertFailed();
            assertEquals(1, observer.startedCalls().size());
            assertEquals(1, observer.completedCalls().size());
        }
    }

    @Test
    void propagatesDownstreamCancellationWithoutAnotherMutationSubscription() {
        var cancelled = new AtomicBoolean();
        var calls = new AtomicInteger();
        PartyMutationPort port = (metadata, work) -> {
            calls.incrementAndGet();
            return Uni.createFrom().<PartyMutationOutcome>nothing().onCancellation().invoke(() -> cancelled.set(true));
        };
        var observer = new RecordingOperationObserver();
        new ChangePartyLifecycleUseCase(port, CLOCK, observer).execute(command(PartyLifecycleAction.ACTIVATE))
                .subscribe().withSubscriber(UniAssertSubscriber.create()).awaitSubscription(TIMEOUT).cancel();
        assertTrue(cancelled.get());
        assertEquals(1, calls.get());
        assertEquals(List.of(
                new RecordingOperationObserver.CompletedCall(METADATA, ObservedOperation.APPLICATION_PARTY_ACTIVATION,
                        OperationOutcome.CANCELLED)),
                observer.completedCalls());
    }

    @Test
    void requiresCommandAndAllExplicitDependencies() {
        PartyMutationPort port = (metadata, work) -> Uni.createFrom().nothing();
        var observer = new RecordingOperationObserver();
        var useCase = new ChangePartyLifecycleUseCase(port, CLOCK, observer);
        assertThrows(NullPointerException.class, () -> useCase.execute(null));
        assertThrows(NullPointerException.class, () -> new ChangePartyLifecycleUseCase(null, CLOCK, observer));
        assertThrows(NullPointerException.class, () -> new ChangePartyLifecycleUseCase(port, null, observer));
        assertThrows(NullPointerException.class, () -> new ChangePartyLifecycleUseCase(port, CLOCK, null));
    }

    private static ObservedOperation operation(PartyLifecycleAction action) {
        return switch (action) {
            case ACTIVATE -> ObservedOperation.APPLICATION_PARTY_ACTIVATION;
            case DEACTIVATE -> ObservedOperation.APPLICATION_PARTY_DEACTIVATION;
            case ARCHIVE -> ObservedOperation.APPLICATION_PARTY_ARCHIVAL;
        };
    }

    private static ChangePartyLifecycleCommand command(PartyLifecycleAction action) {
        return new ChangePartyLifecycleCommand(METADATA, PARTY_ID, PartyVersion.initial(), action, Optional.empty());
    }

    private static PartyMutationOutcome result(PartyLifecycleAction action,
            PartyMutationOutcome.Disposition disposition) {
        PartyRecordStatus status = switch (action) {
            case ACTIVATE -> PartyRecordStatus.ACTIVE;
            case DEACTIVATE -> PartyRecordStatus.INACTIVE;
            case ARCHIVE -> PartyRecordStatus.ARCHIVED;
        };
        var party = NaturalPerson.restore(PARTY_ID, METADATA.tenantId(), "Label", status, new PartyVersion(1),
                AuditInfo.initial(CLOCK.instant(), "actor"),
                new NaturalPersonDetails("Given", "Family", null, null, null, "GB"));
        return new PartyMutationOutcome(PartyDetailsResult.fromAggregate(party), disposition);
    }
}
