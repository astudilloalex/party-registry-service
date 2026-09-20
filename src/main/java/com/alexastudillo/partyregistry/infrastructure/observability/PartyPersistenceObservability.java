package com.alexastudillo.partyregistry.infrastructure.observability;

import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/** Measures reactive collection scans and replay-lock waits using bounded labels and per-subscription counters. */
@ApplicationScoped
public class PartyPersistenceObservability {

    static final String SCAN_DURATION = "party.registry.collection.scan";
    static final String SCAN_ROWS = "party.registry.collection.scan.rows";
    static final String SCAN_BATCHES = "party.registry.collection.scan.batches";
    static final String KEY_WAIT = "party.registry.lifecycle.key.wait";
    private static final String OUTCOME_TAG = "outcome";
    private final MeterRegistry registry;

    public PartyPersistenceObservability(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /** Observes received name-scan batches, including partial work on failure/cancellation, without retaining projections. */
    public <T> Uni<T> observeScan(Function<ScanStatistics, Uni<T>> work) {
        return Uni.createFrom().deferred(() -> {
            var statistics = new ScanStatistics();
            long started = System.nanoTime();
            return Uni.createFrom().deferred(() -> work.apply(statistics))
                    .onTermination().invoke((item, failure, cancelled) -> recordScan(statistics, started, outcome(failure, cancelled)));
        });
    }

    /** Measures only asynchronous lock acquisition, preserving the original item, error, and cancellation. */
    public Uni<Void> observeKeyWait(PartyLifecycleAction action, Supplier<Uni<Void>> work) {
        String operation = switch (action) {
            case ACTIVATE -> "activate";
            case DEACTIVATE -> "deactivate";
            case ARCHIVE -> "archive";
        };
        return Uni.createFrom().deferred(() -> {
            long started = System.nanoTime();
            return Uni.createFrom().deferred(work::get)
                    .onTermination().invoke((item, failure, cancelled) -> {
                        try {
                            registry.timer(KEY_WAIT, "operation", operation, OUTCOME_TAG, outcome(failure, cancelled))
                                    .record(Math.max(0, System.nanoTime() - started), TimeUnit.NANOSECONDS);
                        } catch (RuntimeException _) {
                            // A telemetry backend must not replace a lock's terminal signal.
                        }
                    });
        });
    }

    private void recordScan(ScanStatistics statistics, long started, String outcome) {
        try {
            registry.timer(SCAN_DURATION, OUTCOME_TAG, outcome)
                    .record(Math.max(0, System.nanoTime() - started), TimeUnit.NANOSECONDS);
            registry.summary(SCAN_ROWS, OUTCOME_TAG, outcome).record(statistics.rows.get());
            registry.summary(SCAN_BATCHES, OUTCOME_TAG, outcome).record(statistics.batches.get());
        } catch (RuntimeException _) {
            // Counts are operational evidence, never a condition for returning a complete page.
        }
    }

    private static String outcome(Throwable failure, boolean cancelled) {
        if (cancelled || failure instanceof CancellationException) {
            return "cancelled";
        }
        return failure == null ? "success" : "failure";
    }

    /** Retains only numeric work counts for one scan, safely readable when cancellation races a received batch. */
    public static final class ScanStatistics {
        private final AtomicLong rows = new AtomicLong();
        private final AtomicLong batches = new AtomicLong();

        /** Counts nonempty received batches; the terminating empty fetch does not represent scanned rows. */
        public void receivedBatch(int size) {
            if (size > 0) {
                rows.addAndGet(size);
                batches.incrementAndGet();
            }
        }
    }
}
