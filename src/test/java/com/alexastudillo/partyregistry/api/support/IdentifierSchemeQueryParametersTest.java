package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the full decoded query multimap rather than selected or silently defaulted parameters. */
class IdentifierSchemeQueryParametersTest {
    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "actor", UUID.randomUUID());

    @Test
    void absentValuesDefaultAndEveryFilterRemainsExact() {
        var empty = IdentifierSchemeQueryParameters.parse(METADATA, Map.of());
        assertSame(METADATA, empty.requestMetadata());
        assertEquals(50, empty.criteria().limit());
        assertNull(empty.criteria().status());
        assertNull(empty.criteria().issuingCountryCode());
        assertTrue(empty.cursor().isEmpty());
        for (var category : IdentifierCategory.values()) {
            for (var subject : IdentifierSubjectType.values()) {
                for (var status : IdentifierSchemeStatus.values()) {
                    var query = IdentifierSchemeQueryParameters.parse(METADATA, Map.of(
                            "issuingCountryCode", List.of("EC"), "category", List.of(category.name()),
                            "applicableSubjectType", List.of(subject.name()), "status", List.of(status.name()),
                            "limit", List.of("200"), "cursor", List.of("opaque-token")));
                    assertEquals("EC", query.criteria().issuingCountryCode());
                    assertEquals(category, query.criteria().category());
                    assertEquals(subject, query.criteria().applicableSubjectType());
                    assertEquals(status, query.criteria().status());
                    assertEquals(200, query.criteria().limit());
                    assertEquals("opaque-token", query.cursor().orElseThrow());
                }
            }
        }
        assertEquals(1, IdentifierSchemeQueryParameters.parse(METADATA, Map.of("limit", List.of("1"))).criteria().limit());
    }

    @Test
    void rejectsUnknownRepeatedBlankNullAndEmptyValueLists() {
        bad(Map.of("unknown", List.of("x")));
        for (String name : new String[]{"issuingCountryCode", "category", "applicableSubjectType", "status", "limit", "cursor"}) {
            bad(Map.of(name, List.of("x", "x")));
            bad(Map.of(name, List.of()));
            bad(Map.of(name, List.of(" ")));
            bad(Map.of(name, List.of("")));
            Map<String, List<String>> values = new HashMap<>();
            values.put(name, null);
            bad(values);
            values.put(name, java.util.Arrays.asList((String) null));
            bad(values);
        }
    }

    @Test
    void rejectsFormatRangeAndOversizedCursorWithoutDecodingOrCoercing() {
        for (String limit : new String[]{"0", "201", "-1", "+1", "1.0", "1e2", " 1", "1 ", "１", "9".repeat(300)}) {
            bad(Map.of("limit", List.of(limit)));
        }
        for (String country : new String[]{"ec", " EC", "E1", "ÉC", "ＥＣ", "ECC"}) {
            bad(Map.of("issuingCountryCode", List.of(country)));
        }
        bad(Map.of("category", List.of("other")));
        bad(Map.of("applicableSubjectType", List.of("UNKNOWN")));
        bad(Map.of("status", List.of("active")));
        bad(Map.of("cursor", List.of("x".repeat(257))));
        assertEquals("x".repeat(256), IdentifierSchemeQueryParameters.parse(METADATA,
                Map.of("cursor", List.of("x".repeat(256)))).cursor().orElseThrow());
    }

    private static void bad(Map<String, List<String>> parameters) {
        var failure = assertThrows(ApiResponseException.class, () -> IdentifierSchemeQueryParameters.parse(METADATA, parameters));
        assertEquals("bad-request", failure.getResponseCode().getCode());
        assertEquals(400, failure.getResponseCode().getStatus());
    }
}
