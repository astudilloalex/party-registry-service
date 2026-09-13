package com.alexastudillo.partyregistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the approved static OpenAPI contract and Party registration requirements.
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
        for (String name : List.of("LegalEntityPutRequest", "LegalEntityPatchRequest", "PartyUpdateRequest")) {
            assertTrue(schema(name).getDescription().contains("not implemented"));
        }
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
        assertEquals("#/components/schemas/PartyIdentifierCreateRequest", initialIdentifier.getAllOf().getFirst().get$ref());
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
        assertEquals(List.of("PENDING_VERIFICATION"), property(initialIdentifier.getAllOf().get(1), "status").getEnum());
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
        assertResponseReference(activate, "404", "NotFound");
        assertResponseReference(activate, "409", "Conflict");
        assertResponseReference(activate, "412", "PreconditionFailed");
        assertResponseReference(activate, "422", "UnprocessableEntity");
        assertTrue(activate.getDescription().contains("VERIFIED"));
        assertTrue(activate.getDescription().contains("422 unprocessable-entity"));

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
