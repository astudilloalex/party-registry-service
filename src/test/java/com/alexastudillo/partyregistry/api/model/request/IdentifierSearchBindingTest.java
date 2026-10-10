package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises strict search decoders against ambiguous tokens, raw UTF-16 boundaries and omitted/null inputs. */
class IdentifierSearchBindingTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void preservesDefaultsEmptyFiltersAndNullBodyOrCursor() throws JsonProcessingException {
        var prefix = json.readValue("{\"identifierSchemeCode\":\"SEARCH\",\"prefix\":\" a \",\"identifierStatuses\":[],\"cursor\":null}", IdentifierSearchPrefixRequest.class);
        assertEquals(50, prefix.limit());
        assertEquals(" a ", prefix.prefix());
        assertTrue(prefix.identifierStatuses().isEmpty());
        assertNull(prefix.partyRecordStatuses());
        assertNull(prefix.cursor());
        assertEquals("IdentifierSearchPrefixRequest[redacted]", prefix.toString());
        assertNull(json.readValue("null", IdentifierSearchExactRequest.class));
        assertNull(json.readValue("null", IdentifierSearchPrefixRequest.class));
    }

    @Test
    void rejectsMalformedDuplicateUnknownNullAndWrongTypeFields() {
        for (String body : List.of("[]", "true", "{}", "{", "{\"identifierSchemeCode\":\"X\",\"prefix\":\"A\"} {}",
                "{\"identifierSchemeCode\":\"X\",\"prefix\":\"A\",\"prefix\":\"B\"}",
                "{\"identifierSchemeCode\":null,\"prefix\":\"A\"}",
                "{\"identifierSchemeCode\":\"X\",\"prefix\":true}",
                "{\"identifierSchemeCode\":\"X\",\"prefix\":\" \"}",
                "{\"identifierSchemeCode\":\"X\",\"prefix\":\"A\",\"unknown\":1}",
                "{\"identifierSchemeCode\":\"X\",\"prefix\":\"A\",\"cursor\":\" \"}")) {
            assertThrows(JsonProcessingException.class, () -> json.readValue(body, IdentifierSearchPrefixRequest.class));
        }
        for (String array : List.of("null", "[null]", "[true]", "[\"verified\"]", "[\"UNKNOWN\"]", "\"VERIFIED\"")) {
            String body = "{\"identifierSchemeCode\":\"X\",\"prefix\":\"A\",\"identifierStatuses\":" + array + "}";
            assertThrows(JsonProcessingException.class, () -> json.readValue(body, IdentifierSearchPrefixRequest.class));
        }
        String exactWithLimit = "{\"identifierSchemeCode\":\"X\",\"value\":\"A\",\"limit\":1}";
        assertThrows(JsonProcessingException.class, () -> json.readValue(exactWithLimit, IdentifierSearchExactRequest.class));
    }

    @Test
    void rejectsEveryInvalidLimitTokenWithoutNarrowingOverflow() {
        for (String limit : List.of("null", "\"50\"", "true", "1.0", "1e0", "0", "-1", "201", "999999999999999999999999999999")) {
            String body = "{\"identifierSchemeCode\":\"X\",\"prefix\":\"A\",\"limit\":" + limit + "}";
            assertThrows(JsonProcessingException.class, () -> json.readValue(body, IdentifierSearchPrefixRequest.class));
        }
    }

    @Test
    void rawUtf16BoundariesCountSupplementaryUnitsBeforeNormalization() throws JsonProcessingException {
        String permitted = "{\"identifierSchemeCode\":\"X\",\"prefix\":\"" + "😀".repeat(32) + "\"}";
        assertEquals(64, json.readValue(permitted, IdentifierSearchPrefixRequest.class).prefix().length());
        String excessive = "{\"identifierSchemeCode\":\"X\",\"prefix\":\"" + "😀".repeat(33) + "\"}";
        assertThrows(JsonProcessingException.class, () -> json.readValue(excessive, IdentifierSearchPrefixRequest.class));
        String exact = "{\"identifierSchemeCode\":\"" + "S".repeat(64) + "\",\"value\":\"" + "V".repeat(256) + "\"}";
        assertEquals(256, json.readValue(exact, IdentifierSearchExactRequest.class).value().length());
        String tooLong = exact.replace("V".repeat(256), "V".repeat(257));
        assertThrows(JsonProcessingException.class, () -> json.readValue(tooLong, IdentifierSearchExactRequest.class));
    }
}
