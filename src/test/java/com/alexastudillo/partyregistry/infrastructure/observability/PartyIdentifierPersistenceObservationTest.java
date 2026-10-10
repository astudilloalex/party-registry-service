package com.alexastudillo.partyregistry.infrastructure.observability;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageSlice;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies bounded identifier lock/page costs preserve values, failures and cancellation without retaining private dimensions. */
class PartyIdentifierPersistenceObservationTest {
    @Test
    void keyAndScopeWaitsRecordOnlyKindAndTerminalOutcomeWithoutChangingSignals() {
        var registry = new SimpleMeterRegistry();
        try {
            var observation = new PartyPersistenceObservability(registry);
            observation.observeIdentifierWait(true, () -> Uni.createFrom().voidItem()).subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem(Duration.ofSeconds(2)).assertCompleted();
            var failure = new IllegalStateException("private-key-or-scope-content");
            assertSame(failure, observation.observeIdentifierWait(false, () -> Uni.createFrom().failure(failure))
                    .subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(Duration.ofSeconds(2)).getFailure());
            var cancelled = observation.observeIdentifierWait(true, () -> Uni.createFrom().nothing()).subscribe().withSubscriber(UniAssertSubscriber.create());
            cancelled.cancel();
            for (String outcome : List.of("success", "cancelled")) {
                assertEquals(1, registry.get("party.registry.identifier.lock.wait").tags("kind", "replay-key", "outcome", outcome).timer().count());
            }
            assertEquals(1, registry.get("party.registry.identifier.lock.wait").tags("kind", "primary-scope", "outcome", "failure").timer().count());
            registry.getMeters().forEach(meter -> assertTrue(meter.getId().getTags().stream().noneMatch(tag -> tag.getValue().contains("private"))));
        } finally { registry.close(); }
    }

    @Test
    void pageCostsAreNumericAndFailedOrCancelledWorkNeverInventsRows() {
        var registry = new SimpleMeterRegistry();
        try {
            var observation = new PartyPersistenceObservability(registry);
            var page = new PartyIdentifierPageSlice(List.of(), 5, Optional.empty(), Optional.empty());
            var result = observation.observeIdentifierPage(() -> Uni.createFrom().item(page)).subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem(Duration.ofSeconds(2)).assertCompleted().getItem();
            assertSame(page, result);
            assertEquals(0, registry.get("party.registry.identifier.page.rows").tag("outcome", "success").summary().totalAmount());
            assertEquals(5, registry.get("party.registry.identifier.page.matches").tag("outcome", "success").summary().totalAmount());
            observation.observeIdentifierPage(() -> Uni.createFrom().failure(new IllegalStateException("private-filter")))
                    .subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(Duration.ofSeconds(2));
            var cancelled = observation.observeIdentifierPage(() -> Uni.createFrom().nothing()).subscribe().withSubscriber(UniAssertSubscriber.create()); cancelled.cancel();
            for (String outcome : List.of("success", "failure", "cancelled")) { assertEquals(1, registry.get("party.registry.identifier.page").tag("outcome", outcome).timer().count()); }
            assertTrue(registry.find("party.registry.identifier.page.rows").tag("outcome", "failure").summaries().isEmpty());
        } finally { registry.close(); }
    }
}
