package com.alexastudillo.partyregistry.application.observability;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies terminal operation observation without mutation of reactive signals.
 */
class OperationObservationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final RequestMetadata METADATA = new RequestMetadata(
            new TenantId(UUID.fromString("01991e84-6000-7000-8000-000000000001")),
            "observation-test-user",
            UUID.fromString("01991e84-6000-7000-8000-000000000002"));
    private static final ObservedOperation OPERATION =
            ObservedOperation.APPLICATION_NATURAL_PERSON_REGISTRATION;

    @Test
    void recordsCreatedAndReplayedOncePerSubscriptionAndPreservesItemIdentity() {
        Object created = new Object();
        RecordingOperationObserver createdObserver = new RecordingOperationObserver();
        Uni<Object> observedCreated = observed(
                Uni.createFrom().item(created),
                createdObserver,
                OperationOutcome.CREATED);

        assertTrue(createdObserver.startedCalls().isEmpty());
        assertSame(created, awaitItem(observedCreated));
        assertObservation(createdObserver, OperationOutcome.CREATED);

        Object replayed = new Object();
        RecordingOperationObserver replayedObserver = new RecordingOperationObserver();
        assertSame(replayed, awaitItem(observed(
                Uni.createFrom().item(replayed),
                replayedObserver,
                OperationOutcome.REPLAYED)));
        assertObservation(replayedObserver, OperationOutcome.REPLAYED);
    }

    @Test
    void classifiesValidationConflictAndDependencyFailuresWithoutReplacingThem() {
        assertFailureOutcome(
                new ApplicationException(new ApplicationFailure.InvalidPartyCursor()),
                OperationOutcome.VALIDATION_FAILED);
        assertFailureOutcome(
                new ApplicationException(new ApplicationFailure.InvalidBusinessState(
                        DomainViolation.DISPLAY_NAME_REQUIRED)),
                OperationOutcome.VALIDATION_FAILED);
        assertFailureOutcome(
                new ApplicationException(new ApplicationFailure.IdempotencyKeyConflict("safe-key")),
                OperationOutcome.CONFLICT);
        assertFailureOutcome(
                new ApplicationException(new ApplicationFailure.DependencyUnavailable("safe-dependency")),
                OperationOutcome.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void classifiesUnexpectedFailureAsInternalWithoutExposingOrReplacingIt() {
        IllegalStateException unexpected = new IllegalStateException("sensitive failure text");

        assertFailureOutcome(unexpected, OperationOutcome.INTERNAL_FAILURE);
    }

    @Test
    void recordsCancellationOnceAndPreservesUpstreamCancellation() {
        AtomicBoolean upstreamCancelled = new AtomicBoolean();
        RecordingOperationObserver observer = new RecordingOperationObserver();
        Uni<Object> observed = observed(
                Uni.createFrom().nothing().onCancellation().invoke(() -> upstreamCancelled.set(true)),
                observer,
                OperationOutcome.CREATED);

        UniAssertSubscriber<Object> subscriber = observed.subscribe()
                .withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitSubscription(TIMEOUT).cancel();

        assertTrue(upstreamCancelled.get());
        assertObservation(observer, OperationOutcome.CANCELLED);
    }

    @Test
    void observerFailuresNeverReplaceItemsFailuresOrCancellation() {
        OperationObservationPort failingObserver = (metadata, operation) -> {
            throw new IllegalStateException("observer start failed");
        };
        Object item = new Object();
        assertSame(item, awaitItem(OperationObservation.observe(
                METADATA,
                OPERATION,
                failingObserver,
                () -> Uni.createFrom().item(item),
                ignored -> OperationOutcome.CREATED)));

        IllegalStateException original = new IllegalStateException("original");
        OperationObservationPort failingCompletion = (metadata, operation) -> outcome -> {
            throw new IllegalStateException("observer completion failed");
        };
        assertSame(original, awaitFailure(OperationObservation.observe(
                METADATA,
                OPERATION,
                failingCompletion,
                () -> Uni.createFrom().failure(original),
                ignored -> OperationOutcome.CREATED)));

        AtomicBoolean cancelled = new AtomicBoolean();
        UniAssertSubscriber<Object> subscriber = OperationObservation.observe(
                        METADATA,
                        OPERATION,
                        failingCompletion,
                        () -> Uni.createFrom().<Object>nothing()
                                .onCancellation().invoke(() -> cancelled.set(true)),
                        ignored -> OperationOutcome.CREATED)
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitSubscription(TIMEOUT).cancel();
        assertTrue(cancelled.get());
    }

    private static <T> Uni<T> observed(
            Uni<T> source,
            RecordingOperationObserver observer,
            OperationOutcome successfulOutcome) {
        return OperationObservation.observe(
                METADATA,
                OPERATION,
                observer,
                () -> source,
                ignored -> successfulOutcome);
    }

    private static void assertFailureOutcome(Throwable failure, OperationOutcome expectedOutcome) {
        RecordingOperationObserver observer = new RecordingOperationObserver();

        Throwable observedFailure = awaitFailure(observed(
                Uni.createFrom().failure(failure),
                observer,
                OperationOutcome.CREATED));

        assertSame(failure, observedFailure);
        assertObservation(observer, expectedOutcome);
    }

    private static void assertObservation(
            RecordingOperationObserver observer,
            OperationOutcome expectedOutcome) {
        assertEquals(
                List.of(new RecordingOperationObserver.StartedCall(METADATA, OPERATION)),
                observer.startedCalls());
        assertEquals(
                List.of(new RecordingOperationObserver.CompletedCall(
                        METADATA,
                        OPERATION,
                        expectedOutcome)),
                observer.completedCalls());
    }

    private static <T> T awaitItem(Uni<T> result) {
        UniAssertSubscriber<T> subscriber = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitItem(TIMEOUT).assertCompleted();
        return subscriber.getItem();
    }

    private static Throwable awaitFailure(Uni<?> result) {
        UniAssertSubscriber<?> subscriber = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitFailure(TIMEOUT).assertFailed();
        return subscriber.getFailure();
    }
}
