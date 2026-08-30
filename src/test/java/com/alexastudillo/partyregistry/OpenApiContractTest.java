package com.alexastudillo.partyregistry;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the approved static OpenAPI contract and its natural-person clarifications.
 */
class OpenApiContractTest {

    private static final Path CONTRACT = Path.of("docs/contracts/party-registry.openapi.yaml");
    private static final String CANONICAL_UUID_PATTERN =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    private static OpenAPI openApi;

    @BeforeAll
    static void parseContract() {
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(CONTRACT.toString(), null, null);

        assertNotNull(result.getOpenAPI(), () -> "OpenAPI parsing failed: " + result.getMessages());
        assertTrue(result.getMessages().isEmpty(), () -> "OpenAPI parser messages: " + result.getMessages());
        openApi = result.getOpenAPI();
    }

    @Test
    void declaresAllNaturalPersonOperations() {
        PathItem collectionPath = openApi.getPaths().get("/v1/natural-person");
        PathItem itemPath = openApi.getPaths().get("/v1/natural-person/{partyId}");

        assertNotNull(collectionPath);
        assertNotNull(itemPath);
        assertEquals("createNaturalPerson", collectionPath.getPost().getOperationId());
        assertEquals("getNaturalPerson", itemPath.getGet().getOperationId());
        assertEquals("replaceNaturalPerson", itemPath.getPut().getOperationId());
        assertEquals("patchNaturalPerson", itemPath.getPatch().getOperationId());
    }

    @Test
    void constrainsTrustedAndConcurrencyHeaders() {
        Parameter processId = parameter("ProcessId");
        Parameter idempotencyKey = parameter("IdempotencyKeyRequired");
        Parameter ifMatch = parameter("IfMatch");

        assertTrue(processId.getRequired());
        assertEquals("uuid", processId.getSchema().getFormat());
        assertEquals(CANONICAL_UUID_PATTERN, processId.getSchema().getPattern());
        assertTrue(processId.getDescription().contains("Exactly one canonical UUID"));

        assertTrue(idempotencyKey.getRequired());
        assertEquals(1, idempotencyKey.getSchema().getMinLength());
        assertEquals(128, idempotencyKey.getSchema().getMaxLength());
        assertEquals(".*\\S.*", idempotencyKey.getSchema().getPattern());

        assertTrue(ifMatch.getRequired());
        assertEquals("^(0|[1-9][0-9]*)$", ifMatch.getSchema().getPattern());
        assertTrue(ifMatch.getDescription().contains("nonnegative decimal aggregate version"));
    }

    @Test
    void keepsNaturalPersonRequestSchemasStrictAndDocumentsUpdateSemantics() {
        Schema<?> create = schema("NaturalPersonCreateRequest");
        Schema<?> put = schema("NaturalPersonPutRequest");
        Schema<?> patch = schema("NaturalPersonPatchRequest");

        assertEquals(Boolean.FALSE, create.getAdditionalProperties());
        assertEquals(Boolean.FALSE, put.getAdditionalProperties());
        assertEquals(Boolean.FALSE, patch.getAdditionalProperties());
        assertEquals(".*\\S.*", property(create, "givenNames").getPattern());
        assertEquals(".*\\S.*", property(create, "familyNames").getPattern());
        assertEquals(1, patch.getMinProperties());
        assertNotEquals(Boolean.TRUE, property(patch, "givenNames").getNullable());
        assertEquals(Boolean.TRUE, property(patch, "preferredName").getNullable());
        assertTrue(put.getDescription().contains("Omitted optional properties are cleared"));
        assertTrue(patch.getDescription().contains("explicit null clears a nullable property"));
    }

    @Test
    void fixesNaturalPersonResponseType() {
        Schema<?> response = schema("NaturalPersonResponse");
        Schema<?> naturalPersonShape = response.getAllOf().get(1);
        Schema<?> type = property(naturalPersonShape, "type");

        assertEquals(List.of("NATURAL_PERSON"), type.getEnum());
        assertTrue(naturalPersonShape.getRequired().contains("type"));
        assertTrue(naturalPersonShape.getRequired().contains("naturalPersonDetails"));
    }

    @Test
    void declaresNaturalPersonBusinessAndDependencyFailures() {
        Operation create = openApi.getPaths().get("/v1/natural-person").getPost();
        PathItem itemPath = openApi.getPaths().get("/v1/natural-person/{partyId}");
        Operation replace = itemPath.getPut();
        Operation patch = itemPath.getPatch();

        assertResponseReference(create, "422", "UnprocessableEntity");
        assertResponseReference(create, "503", "DependencyUnavailable");

        for (Operation update : List.of(replace, patch)) {
            assertResponseReference(update, "412", "PreconditionFailed");
            assertResponseReference(update, "422", "UnprocessableEntity");
            assertResponseReference(update, "503", "DependencyUnavailable");
            assertNull(update.getResponses().get("409"));
        }

        ApiResponse dependencyUnavailable = openApi.getComponents()
                .getResponses()
                .get("DependencyUnavailable");
        JsonNode example = assertInstanceOf(JsonNode.class, dependencyUnavailable
                .getContent()
                .get("application/json")
                .getExample());
        assertEquals(503, example.get("status").intValue());
        assertEquals("dependency-unavailable", example.get("code").textValue());
    }

    @Test
    void documentsAcceptedProcessIdEcho() {
        Schema<?> echoSchema = openApi.getComponents().getHeaders().get("ProcessIdEcho").getSchema();
        PathItem itemPath = openApi.getPaths().get("/v1/natural-person/{partyId}");

        assertEquals("uuid", echoSchema.getFormat());
        assertEquals(CANONICAL_UUID_PATTERN, echoSchema.getPattern());
        assertHasProcessIdEcho(openApi.getPaths().get("/v1/natural-person").getPost(), "201");
        assertHasProcessIdEcho(itemPath.getGet(), "200");
        assertHasProcessIdEcho(itemPath.getPut(), "200");
        assertHasProcessIdEcho(itemPath.getPatch(), "200");
    }

    private static Parameter parameter(String name) {
        return openApi.getComponents().getParameters().get(name);
    }

    private static Schema<?> schema(String name) {
        return openApi.getComponents().getSchemas().get(name);
    }

    private static Schema<?> property(Schema<?> owner, String name) {
        return owner.getProperties().get(name);
    }

    private static void assertResponseReference(Operation operation, String status, String componentName) {
        ApiResponse response = operation.getResponses().get(status);

        assertNotNull(response);
        assertEquals("#/components/responses/" + componentName, response.get$ref());
    }

    private static void assertHasProcessIdEcho(Operation operation, String status) {
        ApiResponse response = operation.getResponses().get(status);

        assertNotNull(response.getHeaders().get("Process-Id"));
        assertEquals(
                "#/components/headers/ProcessIdEcho",
                response.getHeaders().get("Process-Id").get$ref());
    }
}
