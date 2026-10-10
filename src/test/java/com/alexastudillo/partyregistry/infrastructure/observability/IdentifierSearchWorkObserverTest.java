package com.alexastudillo.partyregistry.infrastructure.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.alexastudillo.partyregistry.infrastructure.observability.IdentifierSearchWorkObserver.Measurement.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies partial numeric work is published once without identity or selector dimensions and late work is ignored. */
class IdentifierSearchWorkObserverTest {
    @Test
    void retainsPartialWorkAndBoundsDimensionsAfterTermination() {
        var registry = new SimpleMeterRegistry();
        try {
            var sample = new IdentifierSearchWorkObserver(registry).start();
            sample.operation(IdentifierSearchWorkObserver.Operation.PREFIX);
            sample.add(SQL_STATEMENTS, 3);
            sample.add(CANDIDATE_BATCHES, 2);
            sample.add(CANDIDATES, 257);
            sample.add(MATCHES, 12);
            sample.maximum(MAX_BATCH, 256);
            sample.maximum(MAX_BATCH, 1);
            sample.maximum(MAX_PAGE, 10);
            sample.complete(new IllegalStateException("sensitive-selector-not-a-label"), false);
            sample.add(CANDIDATES, 100);
            sample.complete(null, false);
            assertEquals(257, registry.get("party.registry.identifier.search.work")
                    .tags("operation", "prefix", "measurement", "candidates").summary().totalAmount());
            assertEquals(256, registry.get("party.registry.identifier.search.work")
                    .tags("operation", "prefix", "measurement", "max_batch").summary().totalAmount());
            assertEquals(6, registry.getMeters().size());
            registry.getMeters().forEach(meter -> {
                assertEquals(Set.of("operation", "measurement", "outcome"), meter.getId().getTags().stream()
                        .map(io.micrometer.core.instrument.Tag::getKey).collect(Collectors.toSet()));
                assertEquals(1, registry.get(meter.getId().getName()).tags(meter.getId().getTags()).summary().count());
            });
        } finally {
            registry.close();
        }
    }

    @Test
    void distinguishesTimeoutAndCancellationFromOtherFailures() {
        var registry = new SimpleMeterRegistry();
        try {
            var observer = new IdentifierSearchWorkObserver(registry);
            observer.start().complete(new RuntimeException(new io.smallrye.mutiny.TimeoutException()), false);
            observer.start().complete(null, true);
            observer.start().complete(null, false);
            for (String outcome : java.util.List.of("timeout", "cancelled", "success")) {
                assertEquals(1, registry.get("party.registry.identifier.search.work")
                        .tags("operation", "read", "measurement", "candidates", "outcome", outcome).summary().count());
            }
        } finally {
            registry.close();
        }
    }
}
