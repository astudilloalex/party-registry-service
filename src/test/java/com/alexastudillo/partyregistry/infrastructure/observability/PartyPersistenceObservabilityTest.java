package com.alexastudillo.partyregistry.infrastructure.observability;

import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies subscription-local scan accounting and lock-wait outcomes preserve reactive terminal signals. */
class PartyPersistenceObservabilityTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Test
    void countsOnlyReceivedRowsAndBatchesSeparatelyForEachSubscription() {
        var registry = new SimpleMeterRegistry();
        try {
            var observer = new PartyPersistenceObservability(registry);
            Uni<String> scan = observer.observeScan(statistics -> {
                statistics.receivedBatch(256);
                statistics.receivedBatch(12);
                statistics.receivedBatch(0);
                return Uni.createFrom().item("result");
            });
            assertTrue(registry.getMeters().isEmpty());
            for (int attempt = 0; attempt < 2; attempt++) {
                scan.subscribe().withSubscriber(UniAssertSubscriber.create()).awaitItem(TIMEOUT).assertItem("result");
            }
            assertEquals(536, registry.get(PartyPersistenceObservability.SCAN_ROWS).summary().totalAmount());
            assertEquals(4, registry.get(PartyPersistenceObservability.SCAN_BATCHES).summary().totalAmount());
            assertEquals(2, registry.get(PartyPersistenceObservability.SCAN_DURATION).timer().count());
            registry.getMeters().forEach(meter -> assertEquals(Set.of("outcome"), meter.getId().getTags().stream()
                    .map(Tag::getKey).collect(Collectors.toSet())));
        } finally {
            registry.close();
        }
    }

    @Test
    void recordsPartialScanFailureAndCancellationWithoutInventingAResult() {
        var registry = new SimpleMeterRegistry();
        try {
            var observer = new PartyPersistenceObservability(registry);
            var failure = new IllegalStateException("private SQL detail");
            var failed = observer.observeScan(statistics -> {
                statistics.receivedBatch(7);
                return Uni.createFrom().failure(failure);
            }).subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(TIMEOUT);
            assertSame(failure, failed.getFailure());
            var cancelled = observer.observeScan(statistics -> {
                statistics.receivedBatch(9);
                return Uni.createFrom().nothing();
            }).subscribe().withSubscriber(UniAssertSubscriber.create());
            cancelled.cancel();
            cancelled.assertNotTerminated();
            assertEquals(7, registry.get(PartyPersistenceObservability.SCAN_ROWS).tag("outcome", "failure").summary().totalAmount());
            assertEquals(9, registry.get(PartyPersistenceObservability.SCAN_ROWS).tag("outcome", "cancelled").summary().totalAmount());
        } finally {
            registry.close();
        }
    }

    @Test
    void observesEveryActionWaitSuccessFailureAndCancellationWithoutChangingSignals() {
        var registry = new SimpleMeterRegistry();
        try {
            var observer = new PartyPersistenceObservability(registry);
            var failure = new IllegalStateException("private lock key");
            for (PartyLifecycleAction action : PartyLifecycleAction.values()) {
                observer.observeKeyWait(action, () -> Uni.createFrom().voidItem()).subscribe()
                        .withSubscriber(UniAssertSubscriber.create()).awaitItem(TIMEOUT).assertCompleted();
                var received = new AtomicReference<Throwable>();
                observer.observeKeyWait(action, () -> Uni.createFrom().failure(failure)).subscribe()
                        .withSubscriber(UniAssertSubscriber.create()).awaitFailure(received::set, TIMEOUT);
                assertSame(failure, received.get());
                var pending = observer.observeKeyWait(action, () -> Uni.createFrom().nothing()).subscribe()
                        .withSubscriber(UniAssertSubscriber.create());
                pending.cancel();
                pending.assertNotTerminated();
            }
            assertEquals(9, registry.find(PartyPersistenceObservability.KEY_WAIT).timers().size());
            registry.getMeters().forEach(meter -> assertEquals(Set.of("operation", "outcome"), meter.getId().getTags().stream()
                    .map(Tag::getKey).collect(Collectors.toSet())));
        } finally {
            registry.close();
        }
    }
}
