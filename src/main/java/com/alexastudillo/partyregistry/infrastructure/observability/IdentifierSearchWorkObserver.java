package com.alexastudillo.partyregistry.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Records numeric search work, including partial failed scans, with closed operation and measurement dimensions. */
@ApplicationScoped
public class IdentifierSearchWorkObserver {
    private final MeterRegistry registry;

    @Inject
    public IdentifierSearchWorkObserver(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Starts independent counters for a single read subscription; no request or protected data is retained. */
    public Sample start() {
        return new Sample();
    }

    /** Enumerates fixed measurements rather than accepting arbitrary metric labels. */
    public enum Measurement {
        SQL_STATEMENTS, CANDIDATE_BATCHES, CANDIDATES, MATCHES, MAX_BATCH, MAX_PAGE
    }

    /** Enumerates operation labels, including failures before a search method has been selected. */
    public enum Operation {
        READ, EXACT, PREFIX
    }

    /** Enumerates work termination independently of public error codes or provider diagnostic text. */
    public enum Outcome {
        SUCCESS, FAILURE, CANCELLED, TIMEOUT
    }

    private static Outcome outcome(Throwable failure, boolean cancelled) {
        if (cancelled) return Outcome.CANCELLED;
        var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable current = failure; current != null && visited.add(current); current = current.getCause()) {
            if (current instanceof java.util.concurrent.CancellationException) return Outcome.CANCELLED;
            if (current instanceof io.smallrye.mutiny.TimeoutException
                    || current instanceof io.vertx.pgclient.PgException postgres && "57014".equals(postgres.getSqlState())) return Outcome.TIMEOUT;
        }
        return failure == null ? Outcome.SUCCESS : Outcome.FAILURE;
    }

    /** Owns thread-safe numeric counters and publishes them once, even when detached work finishes after cancellation. */
    public final class Sample {
        private final AtomicLong[] values = java.util.Arrays.stream(Measurement.values())
                .map(ignored -> new AtomicLong()).toArray(AtomicLong[]::new);
        private final AtomicBoolean completed = new AtomicBoolean();
        private volatile Operation operation = Operation.READ;

        public void operation(Operation selected) {
            operation = selected;
        }

        public void add(Measurement measurement, long count) {
            if (!completed.get()) values[measurement.ordinal()].addAndGet(count);
        }

        public void maximum(Measurement measurement, long count) {
            if (!completed.get()) values[measurement.ordinal()].accumulateAndGet(count, Math::max);
        }

        /** Publishes partial work on all terminal signals without representing it as response metadata. */
        public void complete(Throwable failure, boolean cancelled) {
            if (!completed.compareAndSet(false, true)) return;
            String terminal = outcome(failure, cancelled).name().toLowerCase(java.util.Locale.ROOT);
            for (Measurement measurement : Measurement.values()) {
                registry.summary("party.registry.identifier.search.work", "operation", operation.name().toLowerCase(java.util.Locale.ROOT),
                        "measurement", measurement.name().toLowerCase(java.util.Locale.ROOT), "outcome", terminal)
                        .record(values[measurement.ordinal()].get());
            }
        }
    }
}
