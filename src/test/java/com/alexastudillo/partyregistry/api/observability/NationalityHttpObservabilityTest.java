package com.alexastudillo.partyregistry.api.observability;

import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Confirms bounded route, replay, and conflict labels without raw identifiers or client keys. */
class NationalityHttpObservabilityTest {

    @Test
    void classifiesExactlyFiveShapesBeforeRootAndUnmatchedRoutes() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new PartyHttpObservability(registry);
        String root = "/v1/parties/" + java.util.UUID.randomUUID() + "/nationalities";
        String item = root + "/" + java.util.UUID.randomUUID();
        Map<String, String> routes = Map.of(
                "POST " + root, "create-nationality", "GET " + root, "list-nationalities",
                "GET " + item, "retrieve-nationality", "PATCH " + item, "patch-nationality",
                "POST " + item + "/set-primary", "set-primary-nationality");
        routes.forEach((request, expected) -> {
            String[] parts = request.split(" ", 2);
            assertEquals(expected, telemetry.operationName(parts[0], parts[1]));
            assertFalse(expected.contains(parts[1]));
        });
        assertEquals("unsupported", telemetry.operationName("DELETE", item));
        assertEquals("unmatched", telemetry.operationName("POST", item + "/unexpected"));
        assertEquals("unmatched", telemetry.operationName("GET", root + "/"));
        assertEquals("retrieve-party", telemetry.operationName("GET", "/v1/parties/party-id"));
    }

    @Test
    void recordsOnlyKeyedAppliedReplayedAndConflictOutcomesWithStableLabels() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new PartyHttpObservability(registry);
        telemetry.recordNationalityCompletion("create-nationality", 201, "successful",
                NationalityMutationOutcome.Disposition.APPLIED, true);
        telemetry.recordNationalityCompletion("create-nationality", 201, "successful",
                NationalityMutationOutcome.Disposition.REPLAYED, true);
        telemetry.recordNationalityCompletion("set-primary-nationality", 200, "successful",
                NationalityMutationOutcome.Disposition.REPLAYED, true);
        telemetry.recordNationalityCompletion("set-primary-nationality", 409, "idempotency-key-conflict", null, true);
        telemetry.recordNationalityCompletion("set-primary-nationality", 200, "successful",
                NationalityMutationOutcome.Disposition.APPLIED, false);
        telemetry.recordNationalityCompletion("patch-nationality", 200, "successful",
                NationalityMutationOutcome.Disposition.APPLIED, true);
        telemetry.recordCompletion("list-nationalities", 200, "successful", 100, null);
        assertEquals(1, counter(registry, "create-nationality", "applied", "successful"));
        assertEquals(1, counter(registry, "create-nationality", "replayed", "successful"));
        assertEquals(1, counter(registry, "set-primary-nationality", "replayed", "successful"));
        assertEquals(1, counter(registry, "set-primary-nationality", "conflict", "idempotency-key-conflict"));
        assertEquals(4, registry.find(PartyHttpObservability.NATIONALITY_MUTATION_METRIC).counters().size());
        assertEquals(1, registry.find(PartyHttpObservability.OPERATION_METRIC)
                .tag("operation", "list-nationalities").timer().count());
    }

    private static double counter(SimpleMeterRegistry registry, String operation, String outcome, String code) {
        return registry.get(PartyHttpObservability.NATIONALITY_MUTATION_METRIC)
                .tags("operation", operation, "outcome", outcome, "code", code).counter().count();
    }
}
