package com.alexastudillo.partyregistry.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shares strict search input, safe-field and exact metadata checks across JVM and native contract tests. */
public final class IdentifierSearchContractAssertions {
    public static final String EXACT_PATH = "/v1/parties/search/by-identifier";
    public static final String PREFIX_PATH = "/v1/parties/search/by-identifier-prefix";
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final Set<String> PAGE_FIELDS = Set.of("status", "code", "data", "nextCursor", "prevCursor",
            "totalElements", "totalPages", "numberOfElements");
    private static final Set<String> HIT_FIELDS = Set.of("partyId", "type", "displayName", "recordStatus", "identifier");
    private static final Set<String> IDENTIFIER_FIELDS = Set.of("identifierId", "schemeCode", "maskedValue", "status", "isPrimary");
    private final OpenAPI api;
    private final IdentifierSchemeContractValidator validator;

    /** Rejects unresolved references before validating any source, packaged or served search contract. */
    public IdentifierSearchContractAssertions(OpenAPI api) {
        this.api = api;
        validator = new IdentifierSchemeContractValidator(api);
    }

    /** Checks closed JSON tokens plus the declared stricter raw UTF-16 and nonblank input rules. */
    public void assertRequest(boolean prefix, String json) {
        JsonNode request = parse(json);
        String name = prefix ? "IdentifierSearchPrefixRequest" : "IdentifierSearchExactRequest";
        validator.assertSchema(name, request);
        assertRawText(name, "identifierSchemeCode", request);
        assertRawText(name, prefix ? "prefix" : "value", request);
        if (prefix && request.has("cursor") && !request.path("cursor").isNull()) {
            assertTrue(request.path("cursor").isTextual());
            assertTrue(!request.path("cursor").textValue().isBlank(), "Blank cursor");
        }
    }

    /** Checks response schema, HTTP status, safe field closure and complete exact-result metadata. */
    public void assertExactResponse(int status, String json) {
        JsonNode body = assertSuccess(EXACT_PATH, status, json);
        long count = body.path("data").size();
        assertEquals(count, body.path("totalElements").longValue());
        assertEquals(count == 0 ? 0L : 1L, body.path("totalPages").longValue());
        assertTrue(body.path("nextCursor").isNull());
        assertTrue(body.path("prevCursor").isNull());
    }

    /** Checks page capacity and overflow-safe exact totals without assuming a retained cross-request snapshot. */
    public void assertPrefixResponse(int status, String json, int limit) {
        JsonNode body = assertSuccess(PREFIX_PATH, status, json);
        assertTrue(limit >= 1 && limit <= 200);
        assertTrue(body.path("data").size() <= limit);
        long total = body.path("totalElements").longValue();
        assertEquals(Math.ceilDiv(total, limit), body.path("totalPages").longValue());
        assertTrue(body.path("data").size() <= total);
        if (total == 0) {
            assertTrue(body.path("nextCursor").isNull());
            assertTrue(body.path("prevCursor").isNull());
        }
    }

    /** Rejects error payload leakage and requires exact matching transport/body status and stable code. */
    public static void assertErrorResponse(int status, String code, String json) {
        JsonNode body = parse(json);
        assertEquals(Set.of("status", "code"), keys(body));
        assertTrue(body.path("status").isIntegralNumber() && body.path("status").canConvertToInt());
        assertEquals(status, body.path("status").intValue());
        assertEquals(code, body.path("code").textValue());
    }

    private JsonNode assertSuccess(String path, int status, String json) {
        assertEquals(200, status);
        JsonNode body = parse(json);
        validator.assertResponse(path, PathItem.HttpMethod.POST, status, json);
        assertEquals(PAGE_FIELDS, keys(body));
        assertEquals("successful", body.path("code").textValue());
        for (String field : Set.of("totalElements", "totalPages", "numberOfElements")) {
            JsonNode count = body.path(field);
            assertTrue(count.isIntegralNumber() && count.canConvertToInt() && count.longValue() >= 0);
        }
        assertEquals(body.path("data").size(), body.path("numberOfElements").intValue());
        for (String field : Set.of("nextCursor", "prevCursor")) {
            JsonNode cursor = body.path(field);
            assertTrue(cursor.isNull() || cursor.isTextual() && !cursor.textValue().isBlank());
        }
        body.path("data").forEach(IdentifierSearchContractAssertions::assertHit);
        return body;
    }

    private void assertRawText(String schemaName, String field, JsonNode request) {
        Schema<?> requestSchema = api.getComponents().getSchemas().get(schemaName);
        Schema<?> schema = requestSchema.getProperties().get(field);
        assertNotNull(schema);
        assertNotNull(schema.getExtensions());
        assertEquals("utf16-code-units", schema.getExtensions().get("x-string-length-unit"));
        JsonNode value = request.path(field);
        assertTrue(value.isTextual() && !value.textValue().isBlank());
        assertTrue(value.textValue().length() <= schema.getMaxLength(), "Raw UTF-16 limit exceeded");
    }

    private static void assertHit(JsonNode hit) {
        assertEquals(HIT_FIELDS, keys(hit));
        assertCanonicalUuid(hit.path("partyId"));
        JsonNode identifier = hit.path("identifier");
        assertEquals(IDENTIFIER_FIELDS, keys(identifier));
        assertCanonicalUuid(identifier.path("identifierId"));
    }

    private static void assertCanonicalUuid(JsonNode value) {
        assertTrue(value.isTextual());
        UUID id = assertDoesNotThrow(() -> UUID.fromString(value.textValue()));
        assertEquals(id.toString(), value.textValue());
    }

    private static Set<String> keys(JsonNode value) {
        assertTrue(value.isObject());
        return value.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static JsonNode parse(String json) {
        try {
            JsonNode value = JSON.readTree(json);
            assertNotNull(value, "Missing JSON");
            return value;
        } catch (JsonProcessingException failure) {
            throw new AssertionError("Invalid contract JSON", failure);
        }
    }
}
