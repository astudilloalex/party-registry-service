package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.IdentifierSchemeContractValidator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.alexastudillo.partyregistry.api.resource.IdentifierSchemeHttpAssertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies every real route uses the published shared mapper for framework, classified dependency, and unexpected errors. */
@QuarkusTest
@TestProfile(IdentifierSchemeFrameworkContractTest.FaultProfile.class)
class IdentifierSchemeFrameworkContractTest {
    @Inject IdentifierSchemeBoundaryFaultPort faults;
    private final IdentifierSchemeContractValidator validator = new IdentifierSchemeContractValidator(
            IdentifierSchemeContractValidator.readContract(java.nio.file.Path.of("docs/contracts/party-registry.openapi.yaml")));

    @Test
    void everyResourcePipelinePreservesClassified503AndSanitizesUnexpected500WithoutFieldErrors() {
        for (var kind : IdentifierSchemeBoundaryFaultPort.Kind.values()) {
            faults.select(kind);
            for (Route route : Route.values()) {
                var context = Context.fresh();
                int status = kind == IdentifierSchemeBoundaryFaultPort.Kind.DEPENDENCY ? 503 : 500;
                var response = route.valid(context.request());
                error(response, status, status == 503 ? "dependency-unavailable" : "server-error", context);
                validator.assertResponse(route.schemaPath(), route.schemaMethod(), status, response.asString());
            }
        }
    }

    @Test
    void methodMediaAndUnknownRoutingFailuresPrecedeOperationInputAndRetainAcceptedCorrelation() {
        var context = Context.fresh();
        String id = UUID.randomUUID().toString();
        for (String suffix : List.of("", "/" + id, "/by-code/Exact", "/" + id + "/activate", "/" + id + "/deprecate", "/" + id + "/retire")) {
            error(context.request().delete(ROOT + suffix), 405, "method-not-allowed", context);
        }
        error(context.request().put(ROOT), 405, "method-not-allowed", context);
        error(context.request().contentType("text/plain").body("private malformed body").post(ROOT), 415, "unsupported-media-type", context);
        error(context.request().contentType("text/plain").body("private malformed body").patch(ROOT + "/invalid"), 415, "unsupported-media-type", context);
        for (String unknown : List.of(ROOT + "/" + id + "/unknown", ROOT + "/unknown/deep/path", "/v1/unknown-scheme-route")) {
            error(context.request().get(unknown), 404, "not-found", context);
        }
        error(context.without("Tenant-Id").contentType("text/plain").body("malformed").post(ROOT), 400, "tenant-id-required", context);
        assertEquals(200, io.restassured.RestAssured.given().get("/q/health/live").statusCode());
    }

    /** Provides valid transport input to each route while test ports fail at the Application I/O boundary. */
    private enum Route {
        CREATE, LIST, GET, CODE, PATCH, ACTIVATE, DEPRECATE, RETIRE;

        private String schemaPath() {
            return switch (this) {
                case CREATE, LIST -> ROOT;
                case GET, PATCH -> ROOT + "/{schemeId}";
                case CODE -> ROOT + "/by-code/{code}";
                case ACTIVATE, DEPRECATE, RETIRE -> ROOT + "/{schemeId}/" + name().toLowerCase(java.util.Locale.ROOT);
            };
        }

        private io.swagger.v3.oas.models.PathItem.HttpMethod schemaMethod() {
            return switch (this) {
                case LIST, GET, CODE -> io.swagger.v3.oas.models.PathItem.HttpMethod.GET;
                case PATCH -> io.swagger.v3.oas.models.PathItem.HttpMethod.PATCH;
                case CREATE, ACTIVATE, DEPRECATE, RETIRE -> io.swagger.v3.oas.models.PathItem.HttpMethod.POST;
            };
        }

        private io.restassured.response.Response valid(RequestSpecification request) {
            String id = UUID.randomUUID().toString();
            return switch (this) {
                case CREATE -> request.contentType("application/json").header("Idempotency-Key", UUID.randomUUID().toString()).body(body(uniqueCode())).post(ROOT);
                case LIST -> request.get(ROOT);
                case GET -> request.get(ROOT + "/" + id);
                case CODE -> request.get(ROOT + "/by-code/ExactCode");
                case PATCH -> request.contentType("application/json").header("If-Match", "0").body("{\"name\":\"Valid\"}").patch(ROOT + "/" + id);
                case ACTIVATE, DEPRECATE, RETIRE -> request.header("If-Match", "0").post(ROOT + "/" + id + "/" + name().toLowerCase(java.util.Locale.ROOT));
            };
        }
    }

    /** Enables failure injection only for this profile; no authentication policy or production configuration is introduced. */
    public static final class FaultProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(IdentifierSchemeBoundaryFaultPort.class);
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.rabbitmq.devservices.enabled", "false");
        }
    }
}
