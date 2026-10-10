package com.alexastudillo.partyregistry.api.observability;

import com.alexastudillo.partyregistry.api.resource.PartyIdentifierResource;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierPatchRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins all eight low-cardinality identifier route labels and explicit safe operation span names. */
class PartyIdentifierHttpObservabilityTest {
    @Test
    void labelsEveryIdentifierOperationWithoutRetainingTargetSegments() {
        var registry = new SimpleMeterRegistry();
        try {
            var observation = new PartyHttpObservability(registry);
            String root = "/v1/parties/private-party-id/identifiers";
            assertEquals("register-identifier", observation.operationName("POST", root));
            assertEquals("list-identifiers", observation.operationName("GET", root));
            assertEquals("retrieve-identifier", observation.operationName("GET", root + "/private-identifier-id"));
            assertEquals("patch-identifier", observation.operationName("PATCH", root + "/private-identifier-id"));
            for (String action : List.of("verify", "reject", "revoke", "set-primary")) {
                assertEquals(action + "-identifier", observation.operationName("POST", root + "/private-identifier-id/" + action));
                assertEquals("unsupported", observation.operationName("GET", root + "/private-identifier-id/" + action));
            }
            assertEquals("unsupported", observation.operationName("PUT", root));
            assertEquals("unmatched", observation.operationName("GET", root + "/"));
            assertEquals("unmatched", observation.operationName("POST", root + "/id/unknown"));
        } finally { registry.close(); }
    }

    @Test
    void allResourceSpansUseBoundedIdentifierOperationNames() throws ReflectiveOperationException {
        var methods = Map.of(
                "create", PartyIdentifierResource.class.getMethod("createPartyIdentifier", String.class, PartyIdentifierCreateRequest.class, HttpHeaders.class),
                "list", PartyIdentifierResource.class.getMethod("listPartyIdentifiers", String.class, UriInfo.class),
                "retrieve", PartyIdentifierResource.class.getMethod("getPartyIdentifier", String.class, String.class),
                "patch", PartyIdentifierResource.class.getMethod("patchPartyIdentifier", String.class, String.class, PartyIdentifierPatchRequest.class, HttpHeaders.class));
        methods.forEach((operation, method) -> assertEquals("party-identifier." + operation, method.getAnnotation(WithSpan.class).value()));
        for (String action : List.of("verify", "reject", "revoke", "set-primary")) {
            String method = action.equals("set-primary") ? "setPrimaryPartyIdentifier" : action + "PartyIdentifier";
            assertEquals("party-identifier." + action, PartyIdentifierResource.class.getMethod(method, String.class, String.class, byte[].class, HttpHeaders.class)
                    .getAnnotation(WithSpan.class).value());
        }
    }

    @Test
    void recordsCreatedAppliedReplayedConflictAndFailureWithoutInventingUncommittedAcceptance() {
        var registry = new SimpleMeterRegistry();
        try {
            var observation = new PartyHttpObservability(registry);
            observation.recordIdentifierCompletion("register-identifier", 201, "successful", PartyIdentifierMutationOutcome.Disposition.APPLIED, true);
            observation.recordIdentifierCompletion("register-identifier", 201, "successful", PartyIdentifierMutationOutcome.Disposition.REPLAYED, true);
            observation.recordIdentifierCompletion("patch-identifier", 200, "successful", PartyIdentifierMutationOutcome.Disposition.APPLIED, false);
            observation.recordIdentifierCompletion("verify-identifier", 409, "idempotency-key-conflict", null, true);
            observation.recordIdentifierCompletion("verify-identifier", 500, "server-error", null, true);
            observation.recordIdentifierCompletion("verify-identifier", 200, "successful", null, true);
            assertEquals(1, metric(registry, "register-identifier", "created", "successful", true));
            assertEquals(1, metric(registry, "register-identifier", "replayed", "successful", true));
            assertEquals(1, metric(registry, "patch-identifier", "applied", "successful", false));
            assertEquals(1, metric(registry, "verify-identifier", "conflict", "idempotency-key-conflict", true));
            assertEquals(1, metric(registry, "verify-identifier", "failure", "server-error", true));
            assertTrue(registry.find("party.registry.http.identifier.mutation").tags("operation", "verify-identifier", "outcome", "applied").counters().isEmpty());
            observation.recordCompletion("verify-identifier", 412, "expected-version-mismatch", 1, null);
            assertEquals(1, registry.get(PartyHttpObservability.OPTIMISTIC_CONFLICT_METRIC).tag("operation", "verify-identifier").counter().count());
        } finally { registry.close(); }
    }

    @Test
    void requestContextRetainsOnlyDispositionAndKeyPresenceAndFreshContextsInheritNothing() {
        var context = new RequestMetadataContext(); var fresh = new RequestMetadataContext();
        var result = PartyIdentifierFixtures.result(PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, null));
        context.markIdentifierKeyed(); context.recordIdentifierDisposition(new PartyIdentifierMutationOutcome(result, PartyIdentifierMutationOutcome.Disposition.REPLAYED));
        assertTrue(context.identifierKeyed()); assertEquals(PartyIdentifierMutationOutcome.Disposition.REPLAYED, context.identifierDisposition());
        assertFalse(fresh.identifierKeyed()); assertNull(fresh.identifierDisposition());
        for (var field : RequestMetadataContext.class.getDeclaredFields()) {
            assertNotEquals(result.getClass(), field.getType());
            assertNotEquals(PartyIdentifierMutationOutcome.class, field.getType());
        }
    }

    private static double metric(SimpleMeterRegistry registry, String operation, String outcome, String code, boolean keyed) {
        return registry.get("party.registry.http.identifier.mutation").tags("operation", operation, "outcome", outcome, "code", code, "keyed", Boolean.toString(keyed)).counter().count();
    }
}
