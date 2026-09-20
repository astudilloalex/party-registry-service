package com.alexastudillo.partyregistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the approved static contract for Party registration, root maintenance, and detail operations.
 */
class OpenApiContractTest {

    private static final Path CONTRACT = Path.of("docs/contracts/party-registry.openapi.yaml");
    private static final String CANONICAL_UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    private static OpenAPI openApi;

    @BeforeAll
    static void parseContract() {
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(CONTRACT.toString(), null, null);

        assertNotNull(result.getOpenAPI(), () -> "OpenAPI parsing failed: " + result.getMessages());
        assertTrue(result.getMessages().isEmpty(), () -> "OpenAPI parser messages: " + result.getMessages());
        openApi = result.getOpenAPI();
    }

    @Test
    void declaresRootReadParametersAndTenantSafeResponseContracts() {
        Operation list = openApi.getPaths().get("/v1/parties").getGet();
        PathItem item = openApi.getPaths().get("/v1/parties/{partyId}");
        Operation get = item.getGet();
        List<Parameter> parameters = list.getParameters().stream()
                .map(OpenApiContractTest::resolveParameter).toList();

        assertEquals("listParties", list.getOperationId());
        assertEquals("getParty", get.getOperationId());
        assertEquals(Set.of("type", "recordStatus", "displayNameStartsWith", "displayNameContains",
                "createdFrom", "createdTo", "cursor", "limit"), parameters.stream()
                .filter(value -> "query".equals(value.getIn())).map(Parameter::getName)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("Tenant-Id", "Process-Id", "User-Id"), parameters.stream()
                .filter(value -> "header".equals(value.getIn())).map(Parameter::getName)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of("#/components/parameters/PartyIdPath", "#/components/parameters/TenantId",
                "#/components/parameters/ProcessId", "#/components/parameters/UserId"),
                item.getParameters().stream().map(Parameter::get$ref).toList());
        assertTrue(get.getParameters() == null || get.getParameters().isEmpty());
        assertNull(list.getRequestBody());
        assertNull(get.getRequestBody());
        assertResponseSchemaReference(list, "200", "PartyCollectionApiResponse");
        assertResponseSchemaReference(get, "200", "PartyDetailApiResponse");
        assertResponseReference(get, "404", "PartyNotFound");
        for (Operation operation : List.of(list, get)) {
            assertHasProcessIdEcho(operation, "200");
            assertResponseReference(operation, "400", "BadRequest");
            assertResponseReference(operation, "default", "DefaultError");
        }
    }

    @Test
    void declaresExactlyTheSixRootOperationsAndMatchingSuccessfulStatusExamples() {
        Map<String, Set<PathItem.HttpMethod>> expected = Map.of(
                "/v1/parties", Set.of(PathItem.HttpMethod.GET),
                "/v1/parties/{partyId}", Set.of(PathItem.HttpMethod.GET, PathItem.HttpMethod.PATCH),
                "/v1/parties/{partyId}/activate", Set.of(PathItem.HttpMethod.POST),
                "/v1/parties/{partyId}/deactivate", Set.of(PathItem.HttpMethod.POST),
                "/v1/parties/{partyId}/archive", Set.of(PathItem.HttpMethod.POST));
        expected.forEach((path, methods) -> {
            PathItem item = openApi.getPaths().get(path);
            assertEquals(methods, item.readOperationsMap().keySet());
            item.readOperations().forEach(operation -> {
                assertTrue(operation.getTags().contains("parties"));
                assertNotNull(operation.getResponses().get("200"));
                assertNull(operation.getResponses().get("201"));
                assertHasProcessIdEcho(operation, "200");
            });
        });
        assertEquals(Set.of("listParties", "getParty", "updateParty", "activateParty", "deactivateParty", "archiveParty"),
                expected.keySet().stream().flatMap(path -> openApi.getPaths().get(path).readOperations().stream())
                        .map(Operation::getOperationId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void constrainsRootFiltersAndScopesContinuationWithoutBroadeningInvalidInput() {
        Operation list = openApi.getPaths().get("/v1/parties").getGet();
        Map<String, Parameter> queries = list.getParameters().stream()
                .map(OpenApiContractTest::resolveParameter).filter(value -> "query".equals(value.getIn()))
                .collect(java.util.stream.Collectors.toMap(Parameter::getName, value -> value));

        assertEquals("#/components/schemas/PartyType", queries.get("type").getSchema().get$ref());
        assertEquals("#/components/schemas/PartyRecordStatus", queries.get("recordStatus").getSchema().get$ref());
        for (String name : List.of("displayNameStartsWith", "displayNameContains")) {
            assertEquals(300, queries.get(name).getSchema().getMaxLength());
            assertEquals(Boolean.TRUE, queries.get(name).getAllowEmptyValue());
        }
        for (String name : List.of("createdFrom", "createdTo")) {
            assertEquals("date-time", queries.get(name).getSchema().getFormat());
            assertTrue(queries.get(name).getDescription().contains("explicit UTC offset"));
        }
        Schema<?> limit = queries.get("limit").getSchema();
        assertEquals(1, limit.getMinimum().intValueExact());
        assertEquals(200, limit.getMaximum().intValueExact());
        assertEquals(50, limit.getDefault());
        assertEquals(1, queries.get("cursor").getSchema().getMinLength());
        assertNotEquals(Boolean.TRUE, queries.get("cursor").getSchema().getNullable());
        for (String semantics : List.of("each at most once", "AND semantics", "Unicode code points",
                "createdAt DESC", "partyId DESC", "tenant, all effective filters, and page size",
                "400 bad-request", "one consistent view")) {
            assertTrue(list.getDescription().contains(semantics), semantics);
        }
    }

    @Test
    void requiresExactRootPageMetadataAndExplicitNullDirections() {
        Schema<?> collection = schema("PartyCollectionApiResponse");
        Set<String> fields = Set.of("status", "code", "data", "nextCursor", "prevCursor",
                "totalElements", "totalPages", "numberOfElements");
        assertEquals(fields, Set.copyOf(collection.getRequired()));
        assertEquals(fields, collection.getProperties().keySet());
        assertEquals(Boolean.FALSE, collection.getAdditionalProperties());
        assertEquals(List.of(200), property(collection, "status").getEnum());
        assertEquals("#/components/schemas/PartySummary", property(collection, "data").getItems().get$ref());
        assertEquals(200, property(collection, "data").getMaxItems());
        for (String cursor : List.of("nextCursor", "prevCursor")) {
            assertEquals(Boolean.TRUE, property(collection, cursor).getNullable());
        }
        for (String count : List.of("totalElements", "totalPages", "numberOfElements")) {
            assertNotEquals(Boolean.TRUE, property(collection, count).getNullable());
            assertEquals(0, property(collection, count).getMinimum().intValueExact());
        }
        var response = openApi.getPaths().get("/v1/parties").getGet().getResponses().get("200");
        JsonNode empty = assertInstanceOf(JsonNode.class,
                response.getContent().get("application/json").getExamples().get("empty").getValue());
        assertEquals(fields, empty.properties().stream().map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(200, empty.path("status").intValue());
        assertTrue(empty.path("data").isArray());
        assertTrue(empty.path("data").isEmpty());
        assertTrue(empty.path("nextCursor").isNull());
        assertTrue(empty.path("prevCursor").isNull());
        for (String count : List.of("totalElements", "totalPages", "numberOfElements")) {
            assertEquals(0, empty.path(count).intValue());
        }
    }

    @Test
    void closesRootRepresentationsAndRequiresOnlyTheMatchingSubtype() {
        Schema<?> summary = schema("PartySummary");
        Set<String> summaryFields = Set.of("partyId", "type", "displayName", "recordStatus", "createdAt", "version");
        assertEquals(summaryFields, summary.getProperties().keySet());
        assertEquals(summaryFields, Set.copyOf(summary.getRequired()));
        assertEquals(Boolean.FALSE, summary.getAdditionalProperties());

        Schema<?> detail = schema("PartyDetailResponse");
        assertEquals(Set.of("partyId", "type", "displayName", "recordStatus", "createdAt", "version",
                "updatedAt", "createdBy", "updatedBy", "naturalPersonDetails", "legalEntityDetails"),
                detail.getProperties().keySet());
        assertEquals(Boolean.FALSE, detail.getAdditionalProperties());
        assertEquals(2, detail.getOneOf().size());
        Schema<?> natural = detail.getOneOf().getFirst();
        Schema<?> legal = detail.getOneOf().get(1);
        assertEquals(List.of("NATURAL_PERSON"), property(natural, "type").getEnum());
        assertEquals(List.of("naturalPersonDetails"), natural.getRequired());
        assertEquals(List.of("legalEntityDetails"), natural.getNot().getRequired());
        assertEquals(List.of("LEGAL_ENTITY"), property(legal, "type").getEnum());
        assertEquals(List.of("legalEntityDetails"), legal.getRequired());
        assertEquals(List.of("naturalPersonDetails"), legal.getNot().getRequired());
        assertEquals(Set.of("status", "code", "data"), schema("PartyDetailApiResponse").getProperties().keySet());
    }

    @Test
    void definesStrictRootPatchAndItsDistinctValidationAndBusinessFailures() {
        Operation patch = openApi.getPaths().get("/v1/parties/{partyId}").getPatch();
        Schema<?> request = schema("PartyUpdateRequest");

        assertEquals(Boolean.TRUE, patch.getRequestBody().getRequired());
        assertEquals("#/components/schemas/PartyUpdateRequest", patch.getRequestBody()
                .getContent().get("application/json").getSchema().get$ref());
        assertEquals(List.of("displayName"), request.getRequired());
        assertEquals(Set.of("displayName"), request.getProperties().keySet());
        assertEquals(Boolean.FALSE, request.getAdditionalProperties());
        assertEquals("string", property(request, "displayName").getType());
        assertNotEquals(Boolean.TRUE, property(request, "displayName").getNullable());
        assertNull(property(request, "displayName").getMinLength());
        assertNull(property(request, "displayName").getMaxLength());
        assertEquals(300, property(request, "displayName").getExtensions().get("x-normalized-max-length"));
        assertEquals(List.of("#/components/parameters/PartyIfMatch"), patch.getParameters().stream()
                .map(Parameter::get$ref).toList());
        assertResponseSchemaReference(patch, "200", "PartyDetailApiResponse");
        assertHasProcessIdEcho(patch, "200");
        assertResponseReference(patch, "404", "PartyNotFound");
        assertResponseReference(patch, "412", "PartyExpectedVersionMismatch");
        assertResponseReference(patch, "422", "PartyBlankDisplayName");
        assertNull(patch.getResponses().get("409"));
        for (String rule : List.of("display-name-required", "display-name-too-long",
                "blank-display-name", "duplicate/unknown properties", "ARCHIVED",
                "expected-version mismatch", "does not enable lifecycle replay")) {
            assertTrue(patch.getDescription().contains(rule), rule);
        }
        var examples = openApi.getComponents().getResponses().get("BadRequest")
                .getContent().get("application/json").getExamples();
        JsonNode required = assertInstanceOf(JsonNode.class, examples.get("requiredDisplayName").getValue());
        assertEquals(400, required.path("status").intValue());
        assertEquals("display-name-required", required.path("code").textValue());
    }

    @Test
    void definesTheSharedLifecycleInputAndHistoricalReplayContract() {
        Parameter key = parameter("PartyLifecycleIdempotencyKey");
        assertEquals(Boolean.FALSE, key.getRequired());
        assertEquals("Idempotency-Key", key.getName());
        assertEquals(128, key.getSchema().getMaxLength());
        assertEquals(".*\\S.*", key.getSchema().getPattern());
        for (String rule : List.of("Unicode code points", "without trimming or case conversion",
                "tenant and lifecycle action", "Party ID and expected version",
                "excluding User-Id and Process-Id", "409 idempotency-key-conflict", "failed attempts consume no key")) {
            assertTrue(key.getDescription().contains(rule), rule);
        }
        for (String header : List.of("PartyIfMatch", "PartyLifecycleIfMatch")) {
            assertTrue(parameter(header).getRequired());
            assertEquals("If-Match", parameter(header).getName());
            assertEquals("^(0|[1-9][0-9]*)$", parameter(header).getSchema().getPattern());
            assertTrue(parameter(header).getDescription().contains("9223372036854775807"));
        }
        assertEquals(CANONICAL_UUID_PATTERN, parameter("PartyIdPath").getSchema().getPattern());
        for (String action : List.of("activate", "deactivate", "archive")) {
            PathItem path = openApi.getPaths().get("/v1/parties/{partyId}/" + action);
            Operation operation = path.getPost();
            assertEquals(List.of("#/components/parameters/PartyIdPath", "#/components/parameters/TenantId",
                    "#/components/parameters/ProcessId", "#/components/parameters/UserId",
                    "#/components/parameters/PartyLifecycleIdempotencyKey",
                    "#/components/parameters/PartyLifecycleIfMatch"),
                    path.getParameters().stream().map(Parameter::get$ref).toList());
            assertNull(operation.getRequestBody());
            assertResponseSchemaReference(operation, "200", "PartyDetailApiResponse");
            assertHasProcessIdEcho(operation, "200");
            assertResponseReference(operation, "404", "PartyNotFound");
            assertResponseReference(operation, "409", "PartyLifecycleConflict");
            assertResponseReference(operation, "412", "PartyStaleVersion");
            assertTrue(operation.getDescription().contains("restart"));
            assertTrue(operation.getDescription().contains("current accepted Process-Id"));
        }
    }

    @Test
    void declaresPermittedTransitionsAndSafeSuccessfulReplayExamples() {
        Map<String, String> statuses = Map.of("activate", "ACTIVE", "deactivate", "INACTIVE", "archive", "ARCHIVED");
        statuses.forEach((action, status) -> {
            Operation operation = openApi.getPaths().get("/v1/parties/{partyId}/" + action).getPost();
            JsonNode example = assertInstanceOf(JsonNode.class,
                    operation.getResponses().get("200").getContent().get("application/json").getExample());
            assertEquals(3, example.size());
            assertEquals(200, example.path("status").intValue());
            assertEquals("successful", example.path("code").textValue());
            assertEquals(status, example.path("data").path("recordStatus").textValue());
            assertTrue(example.path("data").path("version").longValue() > 0);
            assertFalse(example.path("data").has("identifiers"));
            assertFalse(example.path("data").has("initialIdentifier"));
        });
        Operation activate = openApi.getPaths().get("/v1/parties/{partyId}/activate").getPost();
        Operation deactivate = openApi.getPaths().get("/v1/parties/{partyId}/deactivate").getPost();
        Operation archive = openApi.getPaths().get("/v1/parties/{partyId}/archive").getPost();
        assertTrue(activate.getDescription().contains("Only DRAFT may become ACTIVE"));
        assertTrue(activate.getDescription().contains("reactivation is not supported"));
        assertTrue(activate.getDescription().contains("single UTC evaluation date"));
        assertTrue(activate.getDescription().contains("DEPRECATED/RETIRED"));
        assertTrue(deactivate.getDescription().contains("Only ACTIVE may become INACTIVE"));
        assertTrue(archive.getDescription().contains("DRAFT, ACTIVE, and INACTIVE may become ARCHIVED"));
        assertResponseReference(activate, "422", "PartyMissingActivationEvidence");
        assertNull(deactivate.getResponses().get("422"));
        assertNull(archive.getResponses().get("422"));
    }

    @Test
    void rootBusinessErrorExamplesUseOnlyTheirStableStatusAndCode() {
        Map<String, PartyResponseCode> codes = Map.of(
                "PartyNotFound", PartyResponseCode.PARTY_NOT_FOUND,
                "PartyExpectedVersionMismatch", PartyResponseCode.EXPECTED_VERSION_MISMATCH,
                "PartyBlankDisplayName", PartyResponseCode.BLANK_DISPLAY_NAME,
                "PartyStaleVersion", PartyResponseCode.STALE_PARTY_VERSION,
                "PartyMissingActivationEvidence", PartyResponseCode.MISSING_QUALIFYING_IDENTIFIER);
        codes.forEach((name, code) -> {
            ApiResponse response = openApi.getComponents().getResponses().get(name);
            assertEquals("#/components/headers/ProcessIdEcho", response.getHeaders().get("Process-Id").get$ref());
            var content = response.getContent().get("application/json");
            assertEquals("#/components/schemas/ApiErrorResponse", content.getSchema().get$ref());
            JsonNode example = assertInstanceOf(JsonNode.class, content.getExample());
            assertEquals(2, example.size());
            assertEquals(code.getStatus(), example.path("status").intValue());
            assertEquals(code.getCode(), example.path("code").textValue());
        });
        var conflict = openApi.getComponents().getResponses().get("PartyLifecycleConflict")
                .getContent().get("application/json").getExamples();
        assertEquals(Set.of("keyConflict", "transitionConflict"), conflict.keySet());
        Map.of("keyConflict", PartyResponseCode.IDEMPOTENCY_KEY_CONFLICT,
                "transitionConflict", PartyResponseCode.INVALID_PARTY_LIFECYCLE).forEach((name, code) -> {
                    JsonNode example = assertInstanceOf(JsonNode.class, conflict.get(name).getValue());
                    assertEquals(2, example.size());
                    assertEquals(code.getStatus(), example.path("status").intValue());
                    assertEquals(code.getCode(), example.path("code").textValue());
                });
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
    void declaresLegalEntityDetailOperationsWithTrustedContextAndSpecificFailures() {
        PathItem itemPath = openApi.getPaths().get("/v1/legal-entity/{partyId}");
        assertNotNull(itemPath);
        assertEquals("getLegalEntity", itemPath.getGet().getOperationId());
        assertEquals("replaceLegalEntity", itemPath.getPut().getOperationId());
        assertEquals("patchLegalEntity", itemPath.getPatch().getOperationId());
        assertEquals(List.of("#/components/parameters/PartyIdPath", "#/components/parameters/TenantId",
                "#/components/parameters/ProcessId", "#/components/parameters/UserId"),
                itemPath.getParameters().stream().map(Parameter::get$ref).toList());
        assertNull(itemPath.getGet().getRequestBody());
        assertTrue(itemPath.getGet().getParameters() == null || itemPath.getGet().getParameters().isEmpty());

        for (Operation operation : List.of(itemPath.getGet(), itemPath.getPut(), itemPath.getPatch())) {
            assertResponseSchemaReference(operation, "200", "LegalEntityApiResponse");
            assertHasProcessIdEcho(operation, "200");
            assertResponseReference(operation, "400", "BadRequest");
            assertResponseReference(operation, "404", "LegalEntityNotFound");
            assertResponseReference(operation, "default", "DefaultError");
        }
        for (Operation update : List.of(itemPath.getPut(), itemPath.getPatch())) {
            assertTrue(update.getRequestBody().getRequired());
            assertEquals(List.of("#/components/parameters/LegalEntityIfMatch"),
                    update.getParameters().stream().map(Parameter::get$ref).toList());
            assertResponseReference(update, "412", "LegalEntityExpectedVersionMismatch");
            assertResponseReference(update, "422", "LegalEntityBusinessValidationFailure");
            assertResponseReference(update, "503", "DependencyUnavailable");
        }
        Parameter legalIfMatch = parameter("LegalEntityIfMatch");
        assertEquals("If-Match", legalIfMatch.getName());
        assertEquals("header", legalIfMatch.getIn());
        assertTrue(legalIfMatch.getRequired());
        assertEquals(parameter("IfMatch").getSchema().getPattern(), legalIfMatch.getSchema().getPattern());
        assertTrue(legalIfMatch.getDescription().contains("expected-version-mismatch"));
        assertEquals("#/components/schemas/LegalEntityPutRequest", itemPath.getPut()
                .getRequestBody().getContent().get("application/json").getSchema().get$ref());
        assertEquals("#/components/schemas/LegalEntityPatchRequest", itemPath.getPatch()
                .getRequestBody().getContent().get("application/json").getSchema().get$ref());
    }

    @Test
    void keepsLegalDetailSchemasClosedAndSeparateFromCreation() {
        Set<String> fields = Set.of("legalName", "tradeName", "legalFormCode",
                "incorporationCountryCode", "incorporatedOn", "dissolvedOn");
        Schema<?> put = schema("LegalEntityPutRequest");
        Schema<?> patch = schema("LegalEntityPatchRequest");
        assertEquals(Set.of("legalName", "incorporationCountryCode"), Set.copyOf(put.getRequired()));
        assertTrue(patch.getRequired() == null || patch.getRequired().isEmpty());
        assertEquals(1, patch.getMinProperties());
        for (Schema<?> request : List.of(put, patch)) {
            assertEquals(fields, request.getProperties().keySet());
            assertEquals(Boolean.FALSE, request.getAdditionalProperties());
            for (String required : List.of("legalName", "incorporationCountryCode")) {
                assertNotEquals(Boolean.TRUE, property(request, required).getNullable());
            }
            for (String nullable : List.of("tradeName", "legalFormCode", "incorporatedOn", "dissolvedOn")) {
                assertEquals(Boolean.TRUE, property(request, nullable).getNullable());
            }
            for (String date : List.of("incorporatedOn", "dissolvedOn")) {
                assertEquals("string", property(request, date).getType());
                assertEquals("date", property(request, date).getFormat());
            }
        }
        assertTrue(put.getDescription().contains("Omitted optional properties are cleared"));
        assertTrue(patch.getDescription().contains("explicit null clears a nullable property"));
        Schema<?> envelope = schema("LegalEntityApiResponse");
        assertEquals(Set.of("status", "code", "data"), envelope.getProperties().keySet());
        assertEquals("#/components/schemas/LegalEntityResponse", property(envelope, "data").get$ref());
        Schema<?> detail = schema("LegalEntityResponse");
        assertEquals("#/components/schemas/PartyBase", detail.getAllOf().getFirst().get$ref());
        assertEquals(Set.of("legalEntityDetails"), detail.getAllOf().get(1).getProperties().keySet());
        assertEquals("#/components/schemas/LegalEntityDetails",
                property(detail.getAllOf().get(1), "legalEntityDetails").get$ref());
        assertResponseSchemaReference(openApi.getPaths().get("/v1/legal-entity").getPost(),
                "201", "LegalEntityCreateApiResponse");
    }

    @Test
    void documentsCauseSpecificLegalBusinessErrorsWithoutExposingMessages() {
        Map<String, String> expected = Map.of(
                "LegalEntityNotFound", "legal-entity-not-found",
                "LegalEntityExpectedVersionMismatch", "expected-version-mismatch");
        for (var entry : expected.entrySet()) {
            ApiResponse response = openApi.getComponents().getResponses().get(entry.getKey());
            assertEquals("#/components/headers/ProcessIdEcho", response.getHeaders().get("Process-Id").get$ref());
            var json = response.getContent().get("application/json");
            assertEquals("#/components/schemas/ApiErrorResponse", json.getSchema().get$ref());
            JsonNode value = assertInstanceOf(JsonNode.class, json.getExample());
            assertEquals(2, value.size());
            assertEquals(entry.getValue(), value.get("code").textValue());
            assertEquals(entry.getKey().equals("LegalEntityNotFound") ? 404 : 412, value.get("status").intValue());
        }
        ApiResponse business = openApi.getComponents().getResponses().get("LegalEntityBusinessValidationFailure");
        var examples = business.getContent().get("application/json").getExamples();
        Set<String> codes = Set.of("unrecognized-incorporation-country", "dissolution-before-incorporation",
                "incorporation-date-in-future", "dissolution-date-in-future");
        assertEquals(codes.size(), examples.size());
        Set<String> actual = examples.values().stream().map(example -> {
            JsonNode value = assertInstanceOf(JsonNode.class, example.getValue());
            assertEquals(2, value.size());
            assertEquals(422, value.get("status").intValue());
            return value.get("code").textValue();
        }).collect(java.util.stream.Collectors.toSet());
        assertEquals(codes, actual);
        String validation = openApi.getComponents().getResponses().get("BadRequest").getDescription();
        assertTrue(validation.contains("legal-entity creation, PUT, or PATCH"));
        assertTrue(validation.contains("natural-person or legal-entity PATCH"));
        assertTrue(validation.contains("normalized body before partyId and If-Match"));
        assertTrue(business.getDescription().contains("Date ordering is checked before incorporation in the future"));
    }

    @Test
    void sharedBusinessFailureExamplesAgreeWithTheSpecificServiceCatalog() {
        Map<String, Integer> responses = Map.of(
                "NotFound", 404, "Conflict", 409, "PreconditionFailed", 412, "UnprocessableEntity", 422,
                "LegalEntityNotFound", 404, "LegalEntityExpectedVersionMismatch", 412,
                "LegalEntityBusinessValidationFailure", 422);
        responses.forEach((name, status) -> {
            var json = openApi.getComponents().getResponses().get(name).getContent().get("application/json");
            List<Object> values = json.getExamples() == null
                    ? List.of(json.getExample())
                    : json.getExamples().values().stream().map(Example::getValue).toList();
            for (Object value : values) {
                JsonNode body = assertInstanceOf(JsonNode.class, value);
                assertEquals(2, body.size());
                assertEquals(status.intValue(), body.get("status").intValue());
                String code = body.get("code").textValue();
                PartyResponseCode catalog = Arrays.stream(PartyResponseCode.values())
                        .filter(candidate -> candidate.getCode().equals(code))
                        .findFirst().orElseThrow(() -> new AssertionError("Undeclared response code: " + code));
                assertEquals(status.intValue(), catalog.getStatus(), code);
            }
        });
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
    void documentsSpecificTrustedHeaderCodesWithoutExpandingTheErrorEnvelope() {
        Set<String> expectedCodes = Set.of(
                "process-id-required", "process-id-duplicated", "process-id-invalid",
                "tenant-id-required", "tenant-id-duplicated", "tenant-id-invalid",
                "user-id-required", "user-id-duplicated", "user-id-blank", "user-id-too-long", "user-id-unsafe");
        ApiResponse badRequest = openApi.getComponents().getResponses().get("BadRequest");
        for (String code : expectedCodes) {
            assertTrue(badRequest.getDescription().contains("`" + code + "`"), code);
            PartyResponseCode declared = PartyResponseCode.valueOf(code.toUpperCase(Locale.ROOT)
                    .replace('-', '_'));
            assertEquals(code, declared.getCode());
            assertEquals(400, declared.getStatus());
        }
        assertTrue(badRequest.getDescription().contains("Process-Id, Tenant-Id,"));
        Schema<?> error = schema("ApiErrorResponse");
        assertEquals(Set.of("status", "code"), error.getProperties().keySet());
        assertEquals(Boolean.FALSE, error.getAdditionalProperties());
        var examples = badRequest.getContent().get("application/json").getExamples();
        for (String name : List.of("requiredTenant", "invalidProcess", "duplicatedUser")) {
            JsonNode value = assertInstanceOf(JsonNode.class, examples.get(name).getValue());
            assertEquals(2, value.size());
            assertEquals(400, value.get("status").intValue());
            assertTrue(expectedCodes.contains(value.get("code").textValue()));
        }
        assertEquals("^(?=.*\\S)[^\\x00-\\x1F\\x7F-\\x9F]+$", parameter("UserId").getSchema().getPattern());
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
    void documentsNormalizedTextLimitsWithoutImposingRawLengthLimits() {
        Map<String, Map<String, Integer>> limits = Map.of(
                "NaturalPersonCreateRequest", Map.of(
                        "displayName", 300, "givenNames", 200, "familyNames", 200, "preferredName", 200),
                "NaturalPersonPutRequest", Map.of("givenNames", 200, "familyNames", 200, "preferredName", 200),
                "NaturalPersonPatchRequest", Map.of("givenNames", 200, "familyNames", 200, "preferredName", 200),
                "LegalEntityCreateRequest", Map.of(
                        "displayName", 300, "legalName", 300, "tradeName", 300, "legalFormCode", 64),
                "LegalEntityPutRequest", Map.of("legalName", 300, "tradeName", 300, "legalFormCode", 64),
                "LegalEntityPatchRequest", Map.of("legalName", 300, "tradeName", 300, "legalFormCode", 64),
                "PartyUpdateRequest", Map.of("displayName", 300));
        for (var request : limits.entrySet()) {
            Schema<?> requestSchema = schema(request.getKey());
            assertTrue(requestSchema.getDescription().contains("strip().toUpperCase(Locale.ROOT)"));
            assertTrue(requestSchema.getDescription().contains("UTF-16 code units, not raw input"));
            for (var field : request.getValue().entrySet()) {
                Schema<?> text = property(requestSchema, field.getKey());
                assertNull(text.getMaxLength(), request.getKey() + "." + field.getKey());
                assertEquals(field.getValue(), text.getExtensions().get("x-normalized-max-length"));
            }
        }
        for (String path : List.of("/v1/natural-person", "/v1/legal-entity")) {
            assertTrue(openApi.getPaths().get(path).getPost().getDescription()
                    .contains("original submitted text"));
        }
        for (String name : List.of("LegalEntityPutRequest", "LegalEntityPatchRequest")) {
            assertFalse(schema(name).getDescription().contains("not implemented"));
        }
        assertFalse(schema("PartyUpdateRequest").getDescription().contains("not implemented"));
        assertTrue(schema("NationalityCreateRequest").getDescription().contains("not implemented"));

        Schema<?> identifier = schema("PartyIdentifierCreateRequest");
        assertEquals(256, property(identifier, "value").getMaxLength());
        assertEquals(64, property(identifier, "identifierSchemeCode").getMaxLength());
        assertEquals(64, property(identifier, "issuerCode").getMaxLength());
        assertTrue(property(identifier, "value").getDescription().contains("raw submitted identifier value"));
    }

    @Test
    void acceptsOnlyAsciiCountryInputWithJavaStripWhitespaceAndKeepsResponseShape() {
        Map<String, String> countryInputs = Map.of(
                "NaturalPersonCreateRequest", "birthCountryCode",
                "NaturalPersonPutRequest", "birthCountryCode",
                "NaturalPersonPatchRequest", "birthCountryCode",
                "LegalEntityCreateRequest", "incorporationCountryCode",
                "LegalEntityPutRequest", "incorporationCountryCode",
                "LegalEntityPatchRequest", "incorporationCountryCode",
                "NationalityCreateRequest", "countryCode");
        for (var entry : countryInputs.entrySet()) {
            Schema<?> country = property(schema(entry.getKey()), entry.getValue());
            Pattern inputPattern = Pattern.compile(country.getPattern());
            for (String accepted : List.of("GB", "gb", "gB", " \tgb\r\n", "\u2003gb\u3000")) {
                assertTrue(inputPattern.matcher(accepted).matches(), entry.getKey());
            }
            for (String rejected : List.of("", " ", "G", "GBR", "G B", "\u00df", " \u00df ",
                    "\u0131s", "\uff47\uff42", "\u00a0gb\u00a0", "\u2007gb", "gb\u202f")) {
                assertFalse(inputPattern.matcher(rejected).matches(), entry.getKey());
            }
            assertEquals(2, country.getMinLength());
            assertNull(country.getMaxLength());
            assertEquals(2, country.getExtensions().get("x-normalized-max-length"));
            assertTrue(country.getDescription().contains("two ASCII letters in either case"));
            assertTrue(country.getDescription().contains("String.strip()"));
        }
        for (var entry : Map.of("NaturalPersonDetails", "birthCountryCode",
                "LegalEntityDetails", "incorporationCountryCode", "NationalityResponse", "countryCode").entrySet()) {
            Schema<?> country = property(schema(entry.getKey()), entry.getValue());
            assertEquals("^[A-Z]{2}$", country.getPattern());
            assertEquals(2, country.getMinLength());
            assertEquals(2, country.getMaxLength());
        }
    }

    @Test
    void countryInputPatternMatchesTheExactJavaStripWhitespaceSet() {
        String pattern = property(schema("NaturalPersonCreateRequest"), "birthCountryCode").getPattern();
        Pattern inputPattern = Pattern.compile(pattern);
        for (int codePoint = 0; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
            String surrounding = new String(Character.toChars(codePoint));
            assertEquals(Character.isWhitespace(codePoint),
                    inputPattern.matcher(surrounding + "gB" + surrounding).matches(),
                    "Country input whitespace differs at code point " + codePoint);
        }
    }

    @Test
    void requiresOneStrictInitialIdentifierForPartyRegistration() {
        Schema<?> naturalPerson = schema("NaturalPersonCreateRequest");
        Schema<?> legalEntity = schema("LegalEntityCreateRequest");
        Schema<?> initialIdentifier = schema("InitialPartyIdentifierCreateRequest");
        Schema<?> identifier = schema("PartyIdentifierCreateRequest");

        assertTrue(naturalPerson.getRequired().contains("initialIdentifier"));
        assertTrue(legalEntity.getRequired().contains("initialIdentifier"));
        assertEquals(Boolean.FALSE, naturalPerson.getAdditionalProperties());
        assertEquals(Boolean.FALSE, legalEntity.getAdditionalProperties());
        assertEquals(
                "#/components/schemas/InitialPartyIdentifierCreateRequest",
                property(naturalPerson, "initialIdentifier").get$ref());
        assertEquals(
                "#/components/schemas/InitialPartyIdentifierCreateRequest",
                property(legalEntity, "initialIdentifier").get$ref());
        assertEquals("#/components/schemas/PartyIdentifierCreateRequest",
                initialIdentifier.getAllOf().getFirst().get$ref());
        assertEquals(Boolean.FALSE, identifier.getAdditionalProperties());
        assertTrue(identifier.getRequired().containsAll(List.of("identifierSchemeCode", "value")));
        assertEquals(".*\\S.*", property(identifier, "identifierSchemeCode").getPattern());
        assertEquals(".*\\S.*", property(identifier, "value").getPattern());
        assertTrue(property(identifier, "value").getWriteOnly());
    }

    @Test
    void documentsExpirationAsOptionalForEveryIdentifier() {
        Schema<?> identifier = schema("PartyIdentifierCreateRequest");
        assertFalse(identifier.getRequired().contains("expiresOn"));
        assertEquals(Boolean.TRUE, property(identifier, "expiresOn").getNullable());
        assertTrue(identifier.getDescription().contains("optional for all document types"));
        for (String name : List.of("IdentifierSchemeCreateRequest", "IdentifierSchemeUpdateRequest",
                "IdentifierSchemeResponse")) {
            assertEquals(Boolean.TRUE, property(schema(name), "requiresExpiration").getDeprecated());
        }
    }

    @Test
    void usesCreateOnlyResponsesWithProtectedPendingIdentifier() {
        Operation naturalCreate = openApi.getPaths().get("/v1/natural-person").getPost();
        Operation legalCreate = openApi.getPaths().get("/v1/legal-entity").getPost();
        Schema<?> naturalResponse = schema("NaturalPersonCreateResponse");
        Schema<?> legalResponse = schema("LegalEntityCreateResponse");
        Schema<?> identifier = schema("PartyIdentifierResponse");
        Schema<?> initialIdentifier = schema("InitialPartyIdentifierResponse");

        assertResponseSchemaReference(naturalCreate, "201", "NaturalPersonCreateApiResponse");
        assertResponseSchemaReference(legalCreate, "201", "LegalEntityCreateApiResponse");
        assertTrue(naturalResponse.getAllOf().get(1).getRequired().contains("initialIdentifier"));
        assertTrue(legalResponse.getAllOf().get(1).getRequired().contains("initialIdentifier"));
        assertTrue(identifier.getRequired().containsAll(List.of(
                "identifierId", "partyId", "identifierSchemeId", "schemeCode", "maskedValue",
                "status", "isPrimary", "version", "createdAt", "updatedAt")));
        assertNull(identifier.getProperties().get("value"));
        assertNull(identifier.getProperties().get("normalizedValue"));
        assertNull(identifier.getProperties().get("encryptedValue"));
        assertNull(identifier.getProperties().get("fingerprint"));
        assertEquals(List.of("PENDING_VERIFICATION"),
                property(initialIdentifier.getAllOf().get(1), "status").getEnum());
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
    void addsCurrentIdentifiersOnlyToTheNaturalPersonGetResponse() {
        PathItem itemPath = openApi.getPaths().get("/v1/natural-person/{partyId}");
        assertResponseSchemaReference(itemPath.getGet(), "200", "NaturalPersonDetailApiResponse");
        assertResponseSchemaReference(itemPath.getPut(), "200", "NaturalPersonApiResponse");
        assertResponseSchemaReference(itemPath.getPatch(), "200", "NaturalPersonApiResponse");
        assertResponseSchemaReference(openApi.getPaths().get("/v1/natural-person").getPost(),
                "201", "NaturalPersonCreateApiResponse");
        assertEquals("#/components/schemas/NaturalPersonPutRequest", itemPath.getPut()
                .getRequestBody().getContent().get("application/json").getSchema().get$ref());
        assertEquals("#/components/schemas/NaturalPersonPatchRequest", itemPath.getPatch()
                .getRequestBody().getContent().get("application/json").getSchema().get$ref());

        Schema<?> detailEnvelope = schema("NaturalPersonDetailApiResponse");
        assertEquals(Set.of("status", "code", "data"), detailEnvelope.getProperties().keySet());
        assertEquals(Set.of("status", "code", "data"), Set.copyOf(detailEnvelope.getRequired()));
        assertEquals(List.of(200), property(detailEnvelope, "status").getEnum());
        assertEquals("#/components/schemas/ApiSuccessCode", property(detailEnvelope, "code").get$ref());
        assertEquals("#/components/schemas/NaturalPersonDetailResponse",
                property(detailEnvelope, "data").get$ref());

        Schema<?> detail = schema("NaturalPersonDetailResponse");
        assertEquals(2, detail.getAllOf().size());
        assertEquals("#/components/schemas/NaturalPersonResponse", detail.getAllOf().getFirst().get$ref());
        Schema<?> addition = detail.getAllOf().get(1);
        assertEquals(List.of("identifiers"), addition.getRequired());
        assertEquals(Set.of("identifiers"), addition.getProperties().keySet());
        Schema<?> identifiers = property(addition, "identifiers");
        assertEquals("array", identifiers.getType());
        assertNotEquals(Boolean.TRUE, identifiers.getNullable());
        assertNull(identifiers.getMaxItems());
        assertTrue(identifiers.getMinItems() == null || identifiers.getMinItems() == 0);
        assertEquals("#/components/schemas/PartyIdentifierResponse", identifiers.getItems().get$ref());
    }

    @Test
    void excludesIdentifiersFromWriteRequestsAndNonDetailResponses() {
        assertEquals("#/components/schemas/NaturalPersonResponse",
                property(schema("NaturalPersonApiResponse"), "data").get$ref());
        Schema<?> create = schema("NaturalPersonCreateResponse");
        assertEquals("#/components/schemas/NaturalPersonResponse", create.getAllOf().getFirst().get$ref());
        assertEquals(Set.of("initialIdentifier"), create.getAllOf().get(1).getProperties().keySet());
        assertEquals("#/components/schemas/PartyBase",
                schema("NaturalPersonResponse").getAllOf().getFirst().get$ref());
        assertEquals(Set.of("type", "naturalPersonDetails"),
                schema("NaturalPersonResponse").getAllOf().get(1).getProperties().keySet());
        assertFalse(schema("PartyBase").getProperties().containsKey("identifiers"));
        for (String request : List.of("NaturalPersonCreateRequest", "NaturalPersonPutRequest",
                "NaturalPersonPatchRequest")) {
            assertFalse(schema(request).getProperties().containsKey("identifiers"));
        }
        assertEquals(Set.of("identifierId", "partyId", "identifierSchemeId", "schemeCode", "maskedValue",
                "status", "isPrimary", "issuerCode", "issuedOn", "expiresOn", "verifiedAt", "verifiedBy",
                "version", "createdAt", "updatedAt"), schema("PartyIdentifierResponse").getProperties().keySet());
    }

    @Test
    void documentsCurrentIdentifierFilteringOrderingAndUnpaginatedSafeRetrieval() {
        PathItem itemPath = openApi.getPaths().get("/v1/natural-person/{partyId}");
        String description = itemPath.getGet().getDescription();
        for (String required : List.of("PENDING_VERIFICATION", "VERIFIED", "EXPIRED", "REJECTED", "REVOKED",
                "expiresOn", "null or greater than or equal to one UTC evaluation date", "entire request",
                "DEPRECATED", "RETIRED", "without filtering by scheme or `isPrimary`",
                "createdAt ASC", "identifierId ASC", "not truncated to 50", "no pagination parameters",
                "identifiers: []", "PartyIdentifierResponse", "maskedValue", "not decrypt",
                "plaintext", "ciphertext", "hashes", "encryption keys")) {
            assertTrue(description.contains(required), required);
        }
        assertTrue(itemPath.getGet().getParameters() == null || itemPath.getGet().getParameters().isEmpty());
        assertEquals(List.of("#/components/parameters/PartyIdPath", "#/components/parameters/TenantId",
                "#/components/parameters/ProcessId", "#/components/parameters/UserId"),
                itemPath.getParameters().stream().map(Parameter::get$ref).toList());
    }

    @Test
    void declaresRegistrationAndActivationFailures() {
        Operation naturalCreate = openApi.getPaths().get("/v1/natural-person").getPost();
        Operation legalCreate = openApi.getPaths().get("/v1/legal-entity").getPost();
        Operation identifierCreate = openApi.getPaths().get("/v1/parties/{partyId}/identifiers").getPost();
        Operation activate = openApi.getPaths().get("/v1/parties/{partyId}/activate").getPost();
        PathItem itemPath = openApi.getPaths().get("/v1/natural-person/{partyId}");
        Operation replace = itemPath.getPut();
        Operation patch = itemPath.getPatch();

        for (Operation create : List.of(naturalCreate, legalCreate)) {
            assertResponseReference(create, "400", "BadRequest");
            assertResponseReference(create, "409", "Conflict");
            assertResponseReference(create, "422", "UnprocessableEntity");
            assertResponseReference(create, "503", "DependencyUnavailable");
        }

        assertResponseReference(identifierCreate, "404", "NotFound");
        assertResponseReference(identifierCreate, "409", "Conflict");
        assertResponseReference(identifierCreate, "422", "UnprocessableEntity");
        assertResponseReference(identifierCreate, "503", "DependencyUnavailable");

        assertResponseReference(activate, "400", "BadRequest");
        assertResponseReference(activate, "404", "PartyNotFound");
        assertResponseReference(activate, "409", "PartyLifecycleConflict");
        assertResponseReference(activate, "412", "PartyStaleVersion");
        assertResponseReference(activate, "422", "PartyMissingActivationEvidence");
        assertTrue(activate.getDescription().contains("VERIFIED"));
        assertTrue(activate.getDescription().contains("422 missing-qualifying-identifier"));

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
        assertHasProcessIdEcho(openApi.getPaths().get("/v1/legal-entity").getPost(), "201");
        assertHasProcessIdEcho(openApi.getPaths().get("/v1/parties/{partyId}/identifiers").getPost(), "201");
        assertHasProcessIdEcho(openApi.getPaths().get("/v1/parties/{partyId}/activate").getPost(), "200");
        assertHasProcessIdEcho(itemPath.getGet(), "200");
        assertHasProcessIdEcho(itemPath.getPut(), "200");
        assertHasProcessIdEcho(itemPath.getPatch(), "200");
    }

    private static Parameter parameter(String name) {
        return openApi.getComponents().getParameters().get(name);
    }

    private static Parameter resolveParameter(Parameter value) {
        String reference = value.get$ref();
        return reference == null ? value : parameter(reference.substring(reference.lastIndexOf('/') + 1));
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

    private static void assertResponseSchemaReference(Operation operation, String status, String componentName) {
        ApiResponse response = operation.getResponses().get(status);

        assertNotNull(response);
        assertEquals(
                "#/components/schemas/" + componentName,
                response.getContent().get("application/json").getSchema().get$ref());
    }

    private static void assertHasProcessIdEcho(Operation operation, String status) {
        ApiResponse response = operation.getResponses().get(status);

        assertNotNull(response.getHeaders().get("Process-Id"));
        assertEquals(
                "#/components/headers/ProcessIdEcho",
                response.getHeaders().get("Process-Id").get$ref());
    }
}
