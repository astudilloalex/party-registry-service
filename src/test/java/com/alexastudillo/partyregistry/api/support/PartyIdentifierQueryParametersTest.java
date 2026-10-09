package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies closed identifier query syntax, exact filters and absence-only defaults without persistence access. */
class PartyIdentifierQueryParametersTest {

    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "reader", UUID.randomUUID());
    private static final PartyId PARTY = new PartyId(UUID.randomUUID());
    private static final String SCHEME = "0198d111-08f1-7e48-b291-399bbb9cd605";

    @Test
    void absentFiltersRetainTriStateAndDefaultOnlyTheLimit() {
        var query = PartyIdentifierQueryParameters.parse(METADATA, PARTY, Map.of());
        assertSame(METADATA, query.requestMetadata());
        assertEquals(PARTY, query.partyId());
        assertEquals(50, query.criteria().limit());
        assertNull(query.criteria().identifierSchemeId());
        assertNull(query.criteria().schemeCode());
        assertNull(query.criteria().status());
        assertNull(query.criteria().isPrimary());
        assertNull(query.cursor());
    }

    @Test
    void keepsConjunctiveFiltersAndEveryExactStatusOrBoolean() {
        for (PartyIdentifierStatus status : PartyIdentifierStatus.values()) {
            for (String primary : List.of("true", "false")) {
                var query = PartyIdentifierQueryParameters.parse(METADATA, PARTY, Map.of(
                        "identifierSchemeId", List.of(SCHEME), "schemeCode", List.of(" Mixed_Code "),
                        "status", List.of(status.name()), "isPrimary", List.of(primary),
                        "limit", List.of("200"), "cursor", List.of("opaque-token")));
                assertEquals(new IdentifierSchemeId(UUID.fromString(SCHEME)), query.criteria().identifierSchemeId());
                assertEquals(" Mixed_Code ", query.criteria().schemeCode());
                assertEquals(status, query.criteria().status());
                assertEquals(Boolean.valueOf(primary), query.criteria().isPrimary());
                assertEquals(200, query.criteria().limit());
                assertEquals("opaque-token", query.cursor());
            }
        }
    }

    @Test
    void acceptsCodePointAndCursorBoundsWithoutNormalizingCaseOrWhitespace() {
        String code = "😀".repeat(64);
        var query = PartyIdentifierQueryParameters.parse(METADATA, PARTY, Map.of(
                "schemeCode", List.of(code), "cursor", List.of("x".repeat(256))));
        assertEquals(code, query.criteria().schemeCode());
        assertEquals("x".repeat(256), query.cursor());
        bad(Map.of("schemeCode", List.of("😀".repeat(65))));
        bad(Map.of("cursor", List.of("x".repeat(257))));
        assertEquals("lowercase", PartyIdentifierQueryParameters.parse(METADATA, PARTY,
                Map.of("schemeCode", List.of("lowercase"))).criteria().schemeCode());
    }

    @Test
    void rejectsUnknownRepeatedBlankAndEmptyParameters() {
        bad(Map.of("unknown", List.of("sensitive-query-value")));
        for (String name : List.of("identifierSchemeId", "schemeCode", "status", "isPrimary", "cursor", "limit")) {
            bad(Map.of(name, List.of("value", "value")));
            bad(Map.of(name, List.of()));
            bad(Map.of(name, List.of("")));
            bad(Map.of(name, List.of(" ")));
        }
    }

    @Test
    void rejectsNullParameterNamesListsAndValues() {
        for (String name : List.of("identifierSchemeId", "schemeCode", "status", "isPrimary", "cursor", "limit")) {
            Map<String, List<String>> nulls = new HashMap<>();
            nulls.put(name, null);
            bad(nulls);
            nulls.put(name, Arrays.asList((String) null));
            bad(nulls);
        }
        Map<String, List<String>> unknown = new HashMap<>();
        unknown.put(null, List.of("value"));
        bad(unknown);
    }

    @Test
    void acceptsDecimalRangeAndRejectsInvalidSuppliedLimits() {
        for (String limit : List.of("1", "50", "200", "0002")) {
            assertEquals(Integer.parseInt(limit), PartyIdentifierQueryParameters.parse(METADATA, PARTY,
                    Map.of("limit", List.of(limit))).criteria().limit());
        }
        for (String limit : List.of("0", "201", "-1", "+1", "1.0", "1e2", " 1", "1 ", "１", "9".repeat(300))) {
            bad(Map.of("limit", List.of(limit)));
        }
    }

    @Test
    void rejectsNoncanonicalSchemeIdsAndUnsupportedExactStateOrPrimaryTokens() {
        for (String id : List.of("1-1-1-1-1", SCHEME.toUpperCase(java.util.Locale.ROOT), " " + SCHEME, SCHEME + " ", "not-uuid")) {
            bad(Map.of("identifierSchemeId", List.of(id)));
        }
        for (String status : List.of("verified", "Verified", " VERIFIED", "UNKNOWN")) {
            bad(Map.of("status", List.of(status)));
        }
        for (String primary : List.of("True", "FALSE", "1", "0", " true ")) {
            bad(Map.of("isPrimary", List.of(primary)));
        }
    }

    private static void bad(Map<String, List<String>> parameters) {
        ApiResponseException failure = assertThrows(ApiResponseException.class,
                () -> PartyIdentifierQueryParameters.parse(METADATA, PARTY, parameters));
        assertEquals("bad-request", failure.getResponseCode().getCode());
        assertEquals(400, failure.getResponseCode().getStatus());
        assertFalse(failure.toString().contains("sensitive-query-value"));
    }
}
