package com.alexastudillo.partyregistry;

import com.alexastudillo.partyregistry.contract.IdentifierSchemeContractValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates identifier examples, status/code declarations, and safe output schema boundaries. */
class PartyIdentifierOpenApiExamplesTest {

    private static final String ROOT = "/v1/parties/{partyId}/identifiers";
    private static final String ITEM = ROOT + "/{identifierId}";
    private static final String JSON_MEDIA = "application/json";
    private static final Set<String> ITEM_REQUIRED = Set.of("identifierId", "partyId", "identifierSchemeId",
            "schemeCode", "maskedValue", "status", "isPrimary", "version", "createdAt", "updatedAt");
    private static final Set<String> PAGE_KEYS = Set.of("status", "code", "data", "nextCursor", "prevCursor",
            "totalElements", "totalPages", "numberOfElements");
    private final OpenAPI api = IdentifierSchemeContractValidator.readContract(Path.of("docs/contracts/party-registry.openapi.yaml"));
    private final IdentifierSchemeContractValidator validator = new IdentifierSchemeContractValidator(api);

    @Test
    void closesSafeIdentifierSchemasAndPinsIndependentVersionCapacity() {
        Schema<?> item = api.getComponents().getSchemas().get("PartyIdentifierResponse");
        assertEquals(Boolean.FALSE, item.getAdditionalProperties());
        assertEquals(ITEM_REQUIRED, Set.copyOf(item.getRequired()));
        var safe = new HashSet<>(ITEM_REQUIRED);
        safe.addAll(Set.of("issuerCode", "issuedOn", "expiresOn", "verifiedAt", "verifiedBy"));
        assertEquals(safe, item.getProperties().keySet());
        Schema<?> version = item.getProperties().get("version");
        assertEquals(BigDecimal.ZERO, version.getMinimum());
        assertEquals(new BigDecimal("9223372036854775807"), version.getMaximum());
        for (String name : List.of("PartyIdentifierApiResponse", "PartyIdentifierCollectionApiResponse")) {
            assertEquals(Boolean.FALSE, api.getComponents().getSchemas().get(name).getAdditionalProperties());
        }
        Schema<?> page = api.getComponents().getSchemas().get("PartyIdentifierCollectionApiResponse");
        assertEquals(List.of(200), page.getProperties().get("status").getEnum());
    }

    @Test
    void everyOperationHasSchemaValidSuccessExamplesAndAcceptedProcessEcho() {
        int operations = 0;
        for (var path : api.getPaths().entrySet()) {
            if (!path.getKey().startsWith(ROOT)) continue;
            for (var operation : path.getValue().readOperationsMap().entrySet()) {
                int status = path.getKey().equals(ROOT) && operation.getKey() == PathItem.HttpMethod.POST ? 201 : 200;
                assertSuccess(path.getKey(), operation.getKey(), operation.getValue(), status);
                operations++;
            }
        }
        assertEquals(8, operations);
    }

    @Test
    void everyOperationDeclaresFrameworkFailuresAndOnlySafeSchemaValidErrorExamples() {
        Set<String> codes = new HashSet<>();
        for (var path : api.getPaths().entrySet()) {
            if (!path.getKey().startsWith(ROOT)) continue;
            for (var operation : path.getValue().readOperationsMap().entrySet()) {
                assertTrue(operation.getValue().getResponses().keySet()
                        .containsAll(Set.of("400", "401", "404", "405", "500", "503")), operation.getValue().getOperationId());
                if (operation.getKey() != PathItem.HttpMethod.GET) {
                    assertTrue(operation.getValue().getResponses().containsKey("415"), operation.getValue().getOperationId());
                }
                collectErrorExamples(path.getKey(), operation.getKey(), operation.getValue(), codes);
            }
        }
        assertTrue(codes.containsAll(Set.of("identifier-not-found", "invalid-identifier-lifecycle", "primary-identifier-conflict",
                "identifier-version-exhausted", "expected-version-mismatch", "identifier-uniqueness-conflict", "idempotency-key-conflict",
                "unknown-identifier-scheme", "inactive-identifier-scheme", "incompatible-identifier-scheme", "identifier-validation-failure",
                "bad-request", "not-found", "method-not-allowed", "unsupported-media-type", "server-error", "dependency-unavailable")));
    }

    @Test
    void registrationAndMaintenancePublishOnlyTheirApplicableConflictAndValidationCodes() {
        Operation create = api.getPaths().get(ROOT).getPost();
        assertEquals(Set.of("idempotency-key-conflict", "identifier-uniqueness-conflict"), codes(create, "409"));
        assertEquals(Set.of("unknown-identifier-scheme", "inactive-identifier-scheme", "incompatible-identifier-scheme",
                "identifier-validation-failure"), codes(create, "422"));
        Operation patch = api.getPaths().get(ITEM).getPatch();
        assertEquals(Set.of("invalid-identifier-lifecycle", "identifier-version-exhausted"), codes(patch, "409"));
        assertEquals(Set.of("identifier-validation-failure"), codes(patch, "422"));
        for (String action : List.of("verify", "reject", "revoke", "set-primary")) {
            Operation operation = api.getPaths().get(ITEM + "/" + action).getPost();
            var expected = new HashSet<>(Set.of("idempotency-key-conflict", "invalid-identifier-lifecycle", "identifier-version-exhausted"));
            if (action.equals("verify") || action.equals("set-primary")) expected.add("primary-identifier-conflict");
            assertEquals(expected, codes(operation, "409"), action);
            assertEquals(Set.of("expected-version-mismatch"), codes(operation, "412"), action);
        }
    }

    @Test
    void issuerSchemasAcceptOptionalBlankAndAsciiCodesButRejectInvalidSubmittedText() {
        var mapper = new ObjectMapper();
        for (String schema : List.of("PartyIdentifierCreateRequest", "PartyIdentifierUpdateRequest")) {
            ObjectNode body = mapper.createObjectNode();
            if (schema.equals("PartyIdentifierCreateRequest")) {
                body.put("identifierSchemeCode", "TEST_NATURAL_ACTIVE");
                body.put("value", "EXAMPLE123");
            }
            body.putNull("issuerCode");
            validator.assertSchema(schema, body);
            for (String accepted : List.of("", "\t\n", "\u2003", "civil_registry", "A".repeat(64))) {
                body.put("issuerCode", accepted);
                validator.assertSchema(schema, body);
            }
            for (String invalid : List.of("_REGISTRY", "REGISTRY_", "CIVIL__REGISTRY", "REGISTRY1", "REGÍSTRY",
                    "REG\0ISTRY", " registry ", "registry\n", "A".repeat(65), " ".repeat(65))) {
                body.put("issuerCode", invalid);
                assertThrows(AssertionError.class, () -> validator.assertSchema(schema, body), invalid);
            }
        }
    }

    @Test
    void creationAndCorrectionDeclareIssuerSyntaxErrorsWithSafeExamples() {
        for (Operation operation : List.of(api.getPaths().get(ROOT).getPost(), api.getPaths().get(ITEM).getPatch(),
                api.getPaths().get("/v1/natural-person").getPost(), api.getPaths().get("/v1/legal-entity").getPost())) {
            assertTrue(codes(operation, "400").containsAll(Set.of("issuer-code-invalid", "issuer-code-too-long")));
        }
    }

    @Test
    void listExamplesIncludeEmptyAndNonemptyPagesWithExplicitUnavailableDirections() {
        var response = validator.response(api.getPaths().get(ROOT).getGet().getResponses().get("200"));
        var examples = IdentifierSchemeContractValidator.examples(response.getContent().get(JSON_MEDIA));
        assertTrue(examples.size() >= 2);
        assertTrue(examples.stream().anyMatch(value -> value.path("data").isEmpty()));
        assertTrue(examples.stream().anyMatch(value -> !value.path("data").isEmpty()));
        for (JsonNode example : examples) {
            assertEquals(PAGE_KEYS, keys(example));
            assertTrue(example.path("nextCursor").isNull());
            assertTrue(example.path("prevCursor").isNull());
            assertEquals(example.path("data").size(), example.path("numberOfElements").intValue());
            assertEquals(example.path("data").size(), example.path("totalElements").intValue());
        }
    }

    @Test
    void schemaValidationRejectsSensitiveFieldsStatusDriftAndVersionOverflow() {
        var response = validator.response(api.getPaths().get(ROOT).getPost().getResponses().get("201"));
        var examples = IdentifierSchemeContractValidator.examples(response.getContent().get(JSON_MEDIA));
        assertFalse(examples.isEmpty());
        ObjectNode accepted = (ObjectNode) examples.getFirst();
        for (String field : List.of("value", "normalizedValue", "ciphertext", "lookupHash", "keyVersion", "reasonDetail")) {
            ObjectNode changed = accepted.deepCopy();
            ((ObjectNode) changed.get("data")).put(field, "must-not-leak");
            String body = changed.toString();
            assertThrows(AssertionError.class, () -> validator.assertResponse(ROOT, PathItem.HttpMethod.POST, 201, body), field);
        }
        ObjectNode overflow = accepted.deepCopy();
        ((ObjectNode) overflow.get("data")).put("version", new java.math.BigInteger("9223372036854775808"));
        JsonNode invalidItem = overflow.get("data");
        assertThrows(AssertionError.class, () -> validator.assertSchema("PartyIdentifierResponse", invalidItem));
        ObjectNode wrongStatus = accepted.deepCopy();
        wrongStatus.put("status", 200);
        String wrongBody = wrongStatus.toString();
        assertThrows(AssertionError.class, () -> validator.assertResponse(ROOT, PathItem.HttpMethod.POST, 201, wrongBody));
    }

    private void assertSuccess(String path, PathItem.HttpMethod method, Operation operation, int status) {
        ApiResponse response = validator.response(operation.getResponses().get(Integer.toString(status)));
        assertNotNull(response, operation.getOperationId());
        assertEcho(response);
        var examples = IdentifierSchemeContractValidator.examples(response.getContent().get(JSON_MEDIA));
        assertFalse(examples.isEmpty(), operation.getOperationId());
        for (JsonNode example : examples) {
            validator.assertResponse(path, method, status, example.toString());
            assertEquals("successful", example.path("code").textValue());
            if (example.path("data").isObject()) assertEquals(Set.of("status", "code", "data"), keys(example));
            if (status == 201) {
                assertEquals("PENDING_VERIFICATION", example.path("data").path("status").textValue());
                assertEquals(0, example.path("data").path("version").longValue());
            }
        }
    }

    private void collectErrorExamples(String path, PathItem.HttpMethod method, Operation operation, Set<String> codes) {
        for (var declared : operation.getResponses().entrySet()) {
            if (declared.getKey().equals("default") || Integer.parseInt(declared.getKey()) < 400) continue;
            int status = Integer.parseInt(declared.getKey());
            ApiResponse response = validator.response(declared.getValue());
            assertEcho(response);
            var media = response.getContent().get(JSON_MEDIA);
            assertEquals("#/components/schemas/ApiErrorResponse", media.getSchema().get$ref());
            var examples = IdentifierSchemeContractValidator.examples(media);
            assertFalse(examples.isEmpty(), operation.getOperationId() + ": " + status);
            for (JsonNode value : examples) {
                assertEquals(Set.of("status", "code"), keys(value));
                validator.assertResponse(path, method, status, value.toString());
                codes.add(value.path("code").textValue());
            }
        }
    }

    private Set<String> codes(Operation operation, String status) {
        var response = validator.response(operation.getResponses().get(status));
        assertNotNull(response, operation.getOperationId() + ": " + status);
        return IdentifierSchemeContractValidator.examples(response.getContent().get(JSON_MEDIA)).stream()
                .map(value -> value.path("code").textValue()).collect(Collectors.toSet());
    }

    private static void assertEcho(ApiResponse response) {
        assertNotNull(response.getHeaders());
        assertNotNull(response.getHeaders().get("Process-Id"));
        assertEquals("#/components/headers/ProcessIdEcho", response.getHeaders().get("Process-Id").get$ref());
    }

    private static Set<String> keys(JsonNode value) {
        return value.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
    }
}
