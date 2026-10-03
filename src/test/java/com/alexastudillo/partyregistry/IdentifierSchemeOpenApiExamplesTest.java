package com.alexastudillo.partyregistry;

import com.alexastudillo.partyregistry.contract.IdentifierSchemeContractValidator;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Proves explicit scheme status/code documentation and validates JSON examples beyond Swagger parsing. */
class IdentifierSchemeOpenApiExamplesTest {
    private static final String ROOT = "/v1/identifier-schemes";
    private static final String UPDATE_REQUEST = "IdentifierSchemeUpdateRequest";
    private static final String CREATE_REQUEST = "IdentifierSchemeCreateRequest";
    private static final String SCHEME_RESPONSE = "IdentifierSchemeResponse";
    private static final String APPLICATION_JSON = "application/json";
    private final OpenAPI api = IdentifierSchemeContractValidator.readContract(Path.of("docs/contracts/party-registry.openapi.yaml"));
    private final IdentifierSchemeContractValidator validator = new IdentifierSchemeContractValidator(api);

    @Test
    void everyOperationDocumentsItsStatusesCodesEchoAndSchemaValidExamples() {
        Set<String> codes = new HashSet<>();
        api.getPaths().forEach((path, item) -> {
            if (!path.startsWith(ROOT)) return;
            item.readOperationsMap().forEach((method, operation) -> {
                Set<String> expected = new HashSet<>(Set.of("200", "400", "401", "404", "405", "500", "503"));
                if (method == PathItem.HttpMethod.POST && path.equals(ROOT)) { expected.remove("200"); expected.addAll(Set.of("201", "409", "415", "422")); }
                if (method == PathItem.HttpMethod.PATCH) expected.addAll(Set.of("409", "412", "415", "422"));
                if (method == PathItem.HttpMethod.POST && !path.equals(ROOT)) expected.addAll(Set.of("409", "412"));
                if (path.endsWith("activate")) expected.add("422");
                assertEquals(expected, operation.getResponses().keySet(), operation.getOperationId());
                assertNotNull(operation.getExtensions().get("x-request-example"), operation.getOperationId());
                var requestExample = new ObjectMapper().valueToTree(operation.getExtensions().get("x-request-example"));
                assertEquals(method.name(), requestExample.path("method").textValue());
                assertTrue(requestExample.path("path").textValue().startsWith(ROOT));
                var parameters = new java.util.ArrayList<io.swagger.v3.oas.models.parameters.Parameter>();
                if (item.getParameters() != null) parameters.addAll(item.getParameters());
                if (operation.getParameters() != null) parameters.addAll(operation.getParameters());
                for (var declared : parameters) {
                    var parameter = declared.get$ref() == null ? declared : api.getComponents().getParameters()
                            .get(declared.get$ref().substring("#/components/parameters/".length()));
                    if ("header".equals(parameter.getIn())) {
                        var header = requestExample.path("headers").path(parameter.getName());
                        if (Boolean.TRUE.equals(parameter.getRequired())) assertFalse(header.isMissingNode(), parameter.getName());
                        if (!header.isMissingNode()) validator.assertSchema(parameter.getSchema(), header);
                    }
                }
                if (operation.getRequestBody() == null) assertFalse(requestExample.has("body"));
                else validator.assertRequest(path, method, requestExample.path("body").toString());
                operation.getResponses().forEach((status, declared) -> {
                    var response = validator.response(declared);
                    assertEquals("#/components/headers/ProcessIdEcho", response.getHeaders().get("Process-Id").get$ref());
                    var media = response.getContent().get(APPLICATION_JSON);
                    if (Integer.parseInt(status) >= 400) assertEquals("#/components/schemas/ApiErrorResponse", media.getSchema().get$ref());
                    var examples = IdentifierSchemeContractValidator.examples(media);
                    assertFalse(examples.isEmpty(), operation.getOperationId() + " " + status);
                    for (var value : examples) {
                        validator.assertResponse(path, method, Integer.parseInt(status), value.toString());
                        String code = value.path("code").textValue();
                        codes.add(code);
                        if (Integer.parseInt(status) >= 400 && !Set.of("unauthorized", "not-found", "method-not-allowed", "unsupported-media-type", "server-error").contains(code)) {
                            var actual = Arrays.stream(PartyResponseCode.values()).filter(c -> c.getCode().equals(code)).findFirst().orElseThrow();
                            assertEquals(Integer.parseInt(status), actual.getStatus(), code);
                        }
                    }
                });
                if (operation.getRequestBody() != null) {
                    var media = operation.getRequestBody().getContent().get(APPLICATION_JSON);
                    var examples = IdentifierSchemeContractValidator.examples(media);
                    assertFalse(examples.isEmpty());
                    examples.forEach(example -> validator.assertRequest(path, method, example.toString()));
                }
            });
        });
        assertTrue(codes.containsAll(Set.of("identifier-scheme-not-found", "identifier-scheme-code-conflict",
                "idempotency-key-conflict", "identifier-scheme-rules-locked", "identifier-scheme-retired",
                "invalid-identifier-scheme-lifecycle", "identifier-scheme-version-exhausted", "expected-version-mismatch",
                "identifier-scheme-length-range-invalid", "invalid-identifier-scheme-configuration",
                "identifier-scheme-id-invalid", "identifier-scheme-code-required", "identifier-scheme-code-too-long",
                "request-body-required", "patch-property-required", "bad-request", "dependency-unavailable")));
        for (String code : List.of("process-id-required", "process-id-duplicated", "process-id-invalid", "tenant-id-required", "tenant-id-duplicated", "tenant-id-invalid",
                "user-id-required", "user-id-duplicated", "user-id-blank", "user-id-too-long", "user-id-unsafe",
                "idempotency-key-required", "idempotency-key-duplicated", "idempotency-key-blank", "idempotency-key-too-long",
                "if-match-required", "if-match-duplicated", "if-match-invalid", "if-match-out-of-range")) assertTrue(codes.contains(code), code);
    }

    @Test
    void validatorRejectsSchemaDriftAndChecksReferencesCompositionNullableCodePointsAndExactNumbers() throws Exception {
        ObjectMapper json = new ObjectMapper();
        verifyCreateRequests(json);
        verifyUpdateRequests(json);
        verifyResponseSchemas(json);
        verifyCollectionResponses();
    }

    private void verifyCreateRequests(ObjectMapper json) throws Exception {
        var valid = json.readTree("""
                {"code":"Example","issuingCountryCode":"ZZ","category":"OTHER","applicableSubjectType":"BOTH",
                 "name":"Name","normalizerKey":"TRIM_UPPERCASE_V1","validatorKey":"ALPHANUMERIC_V1"}
                """);
        validator.assertSchema(CREATE_REQUEST, valid);
        for (var mutation : Map.of("requiresExpiration", json.nullNode(), "minimumLength", json.getNodeFactory().numberNode(32768),
                "code", json.getNodeFactory().textNode("\u2003\n"), "category", json.getNodeFactory().textNode("INVALID"),
                "issuingCountryCode", json.getNodeFactory().textNode("zz"), "auditActor", json.getNodeFactory().textNode("leak")).entrySet()) {
            var invalid = valid.deepCopy();
            ((ObjectNode) invalid).set(mutation.getKey(), mutation.getValue());
            assertThrows(AssertionError.class, () -> validator.assertSchema(CREATE_REQUEST, invalid), mutation.getKey());
        }
        var unicode = (ObjectNode) valid.deepCopy();
        unicode.put("code", "\ud83d\ude00".repeat(64));
        unicode.putNull("description");
        validator.assertSchema(CREATE_REQUEST, unicode);
        unicode.put("code", "\ud83d\ude00".repeat(65));
        assertThrows(AssertionError.class, () -> validator.assertSchema(CREATE_REQUEST, unicode));
    }

    private void verifyUpdateRequests(ObjectMapper json) throws Exception {
        var emptyUpdateRequest = json.readTree("{}");
        assertThrows(AssertionError.class, () -> validator.assertSchema(UPDATE_REQUEST, emptyUpdateRequest));
        var invalidFloatLength = json.readTree("{\"maximumLength\":1.0}");
        assertThrows(AssertionError.class, () -> validator.assertSchema(UPDATE_REQUEST, invalidFloatLength));
        validator.assertSchema(UPDATE_REQUEST, json.readTree("{\"maximumLength\":null}"));
    }

    private void verifyResponseSchemas(ObjectMapper json) {
        var response = api.getPaths().get(ROOT).getPost().getResponses().get("201");
        var created = (ObjectNode) IdentifierSchemeContractValidator.examples(response.getContent().get(APPLICATION_JSON)).getFirst().deepCopy();
        validator.assertResponse(ROOT, PathItem.HttpMethod.POST, 201, created.toString());
        var dataNode = (ObjectNode) created.get("data");
        assertNotNull(dataNode);
        dataNode.put("status", "ACTIVE");
        String modifiedResponse = created.toString();
        assertThrows(AssertionError.class, () -> validator.assertResponse(ROOT, PathItem.HttpMethod.POST, 201, modifiedResponse));
        var validData = IdentifierSchemeContractValidator.examples(response.getContent().get(APPLICATION_JSON)).getFirst().get("data");
        for (var mutation : Map.of("id", json.getNodeFactory().textNode("INVALID"),
                "version", json.getNodeFactory().numberNode(new java.math.BigInteger("9223372036854775808")),
                "createdAt", json.getNodeFactory().textNode("not-an-instant"), "createdBy", json.getNodeFactory().textNode("actor-leak")).entrySet()) {
            var invalid = (ObjectNode) validData.deepCopy();
            invalid.set(mutation.getKey(), mutation.getValue());
            assertThrows(AssertionError.class, () -> validator.assertSchema(SCHEME_RESPONSE, invalid), mutation.getKey());
        }
        var maximal = (ObjectNode) validData.deepCopy();
        maximal.put("version", Long.MAX_VALUE);
        validator.assertSchema(SCHEME_RESPONSE, maximal);
        maximal.remove("requiresExpiration");
        assertThrows(AssertionError.class, () -> validator.assertSchema(SCHEME_RESPONSE, maximal));
    }

    private void verifyCollectionResponses() {
        validator.assertResponse(ROOT, PathItem.HttpMethod.GET, 200, "{\"status\":200,\"code\":\"successful\",\"data\":[],\"numberOfElements\":0}");
        for (String invalid : List.of(
                "{\"status\":200,\"code\":\"successful\",\"data\":{},\"numberOfElements\":0}",
                "{\"status\":200,\"code\":\"successful\",\"data\":[],\"numberOfElements\":0,\"nextCursor\":null}",
                "{\"status\":200,\"code\":\"successful\",\"data\":[],\"numberOfElements\":1}",
                "{\"status\":200,\"code\":\"successful\",\"data\":[]}")) {
            assertThrows(AssertionError.class, () -> validator.assertResponse(ROOT, PathItem.HttpMethod.GET, 200, invalid));
        }
        assertThrows(AssertionError.class, () -> validator.assertResponse(ROOT, PathItem.HttpMethod.GET, 400,
                "{\"status\":400,\"code\":\"bad-request\",\"message\":\"SQL leak\"}"));
        var broken = new OpenAPI().components(new io.swagger.v3.oas.models.Components().addSchemas("Broken", new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/Absent")));
        assertThrows(AssertionError.class, () -> new IdentifierSchemeContractValidator(broken));
    }
}
