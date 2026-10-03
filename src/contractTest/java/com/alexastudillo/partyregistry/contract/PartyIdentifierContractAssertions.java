package com.alexastudillo.partyregistry.contract;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shares exact identifier envelope and safe-field checks across unit, packaged JVM, and native tests. */
public final class PartyIdentifierContractAssertions {

    private static final Set<String> REQUIRED_ITEM = Set.of("identifierId", "partyId", "identifierSchemeId", "schemeCode",
            "maskedValue", "status", "isPrimary", "version", "createdAt", "updatedAt");
    private static final Set<String> OPTIONAL_ITEM = Set.of("issuerCode", "issuedOn", "expiresOn", "verifiedAt", "verifiedBy");
    private static final Set<String> PAGE_KEYS = Set.of("status", "code", "data", "nextCursor", "prevCursor",
            "totalElements", "totalPages", "numberOfElements");

    private PartyIdentifierContractAssertions() {
    }

    /** Requires a single safe identifier and exact successful envelope with matching actual HTTP status. */
    public static void assertItemResponse(int status, JsonNode body) {
        assertEquals(Set.of("status", "code", "data"), keys(body));
        assertEquals(status, body.path("status").intValue());
        assertEquals("successful", body.path("code").textValue());
        assertItem(body.path("data"));
    }

    /** Rejects missing required data and undeclared properties, including reasons and protected storage material. */
    public static void assertItem(JsonNode item) {
        assertTrue(item.isObject());
        Set<String> actual = keys(item);
        var permitted = new HashSet<>(REQUIRED_ITEM);
        permitted.addAll(OPTIONAL_ITEM);
        assertTrue(actual.containsAll(REQUIRED_ITEM), "Missing required identifier fields");
        assertTrue(permitted.containsAll(actual), "Undeclared identifier fields");
        REQUIRED_ITEM.forEach(field -> assertTrue(!item.path(field).isNull(), "Null required identifier field: " + field));
    }

    /** Requires all five page fields; unavailable cursors may be explicit nulls, never omitted. */
    public static void assertPageResponse(int status, JsonNode body, int limit) {
        assertEquals(PAGE_KEYS, keys(body));
        assertEquals(status, body.path("status").intValue());
        assertEquals("successful", body.path("code").textValue());
        assertTrue(body.path("data").isArray());
        assertTrue(limit >= 1 && limit <= 200);
        assertTrue(body.path("data").size() <= limit);
        assertCount(body.path("totalElements"));
        assertCount(body.path("totalPages"));
        assertCount(body.path("numberOfElements"));
        assertEquals(body.path("data").size(), body.path("numberOfElements").longValue());
        assertEquals(Math.ceilDiv(body.path("totalElements").longValue(), limit), body.path("totalPages").longValue());
        body.path("data").forEach(PartyIdentifierContractAssertions::assertItem);
        for (String field : Set.of("nextCursor", "prevCursor")) {
            JsonNode cursor = body.path(field);
            assertTrue(cursor.isNull() || cursor.isTextual() && !cursor.textValue().isBlank());
        }
    }

    /** Checks stable failure bodies without accepting data, messages, field errors, reasons, or exception details. */
    public static void assertErrorResponse(int status, String code, JsonNode body) {
        assertEquals(Set.of("status", "code"), keys(body));
        assertEquals(status, body.path("status").intValue());
        assertEquals(code, body.path("code").textValue());
    }

    private static void assertCount(JsonNode value) {
        assertTrue(value.isIntegralNumber());
        assertTrue(value.longValue() >= 0 && value.longValue() <= Integer.MAX_VALUE);
    }

    private static Set<String> keys(JsonNode value) {
        return value.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
    }
}
