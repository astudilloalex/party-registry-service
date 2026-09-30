package com.alexastudillo.partyregistry.api.observability;

import com.alexastudillo.partyregistry.api.model.request.PartyUpdateRequest;
import com.alexastudillo.partyregistry.api.resource.PartyResource;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Verifies six bounded root route identities, explicit spans, and mutation dispositions without request-value labels. */
class RootPartyHttpObservabilityTest {

    @Test
    void recognizesRootRoutesAndRejectsNestedLookalikesWithoutRetainingIdentifiers() {
        var registry = new SimpleMeterRegistry();
        try {
            var observer = new PartyHttpObservability(registry);
            for (String id : List.of("secret-party", "01991e8b-e000-7000-8000-000000000001")) {
                assertEquals("list-parties", observer.operationName("GET", "/v1/parties"));
                assertEquals("retrieve-party", observer.operationName("GET", "/v1/parties/" + id));
                assertEquals("patch-party", observer.operationName("PATCH", "/v1/parties/" + id));
                for (String action : List.of("activate", "deactivate", "archive")) {
                    assertEquals(action, observer.operationName("POST", "/v1/parties/" + id + "/" + action));
                    assertEquals("unsupported", observer.operationName("GET", "/v1/parties/" + id + "/" + action));
                    assertEquals("unmatched", observer.operationName("POST", "/v1/parties/" + id + "/extra/" + action));
                }
            }
            assertEquals("unmatched", observer.operationName("GET", "/v1/parties/"));
            assertEquals("unmatched", observer.operationName("GET", "/q/health"));
        } finally {
            registry.close();
        }
    }

    @Test
    void countsAppliedReplayedAndConflictOutcomesWithClosedDimensions() {
        var registry = new SimpleMeterRegistry();
        try {
            var observer = new PartyHttpObservability(registry);
            for (String operation : List.of("patch-party", "activate", "deactivate", "archive")) {
                observer.recordMutationCompletion(operation, 200, "successful", PartyMutationOutcome.Disposition.APPLIED);
                observer.recordMutationCompletion(operation, 200, "successful", PartyMutationOutcome.Disposition.REPLAYED);
                observer.recordMutationCompletion(operation, 409, "idempotency-key-conflict", null);
                observer.recordCompletion(operation, 412, "stale-party-version", 12, null);
                observer.recordMutationCompletion(operation, 412, "stale-party-version", null);
                observer.recordMutationCompletion(operation, 500, "server-error", PartyMutationOutcome.Disposition.APPLIED);
                assertEquals(1, registry.get(PartyHttpObservability.OPTIMISTIC_CONFLICT_METRIC).tag("operation", operation).counter().count());
                for (String disposition : List.of("applied", "replayed", "conflict")) {
                    double count = registry.get(PartyHttpObservability.MUTATION_METRIC)
                            .tags("operation", operation, "outcome", disposition).counters().stream().mapToDouble(Counter::count).sum();
                    assertEquals("conflict".equals(disposition) ? 2 : 1, count);
                }
            }
            observer.recordMutationCompletion("retrieve-party", 200, "successful", PartyMutationOutcome.Disposition.APPLIED);
            assertEquals(16, registry.find(PartyHttpObservability.MUTATION_METRIC).counters().size());
            registry.find(PartyHttpObservability.MUTATION_METRIC).counters().forEach(counter -> {
                assertEquals(Set.of("operation", "outcome", "code"), counter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet()));
                assertFalse(counter.getId().getTags().toString().contains("secret"));
            });
        } finally {
            registry.close();
        }
    }

    @Test
    void declaresAllSixExplicitRootSpans() throws ReflectiveOperationException {
        assertEquals("party.list", PartyResource.class.getMethod("listParties", UriInfo.class).getAnnotation(WithSpan.class).value());
        assertEquals("party.retrieve", PartyResource.class.getMethod("getParty", String.class).getAnnotation(WithSpan.class).value());
        assertEquals("party.patch", PartyResource.class.getMethod("patchParty", String.class, PartyUpdateRequest.class, HttpHeaders.class)
                .getAnnotation(WithSpan.class).value());
        for (String action : List.of("activate", "deactivate", "archive")) {
            assertEquals("party." + action, PartyResource.class.getMethod(action + "Party", String.class, HttpHeaders.class)
                    .getAnnotation(WithSpan.class).value());
        }
    }
}
