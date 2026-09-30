package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies closed query cardinality, strict typed values, instant equivalence, literal Unicode filters, and absent-only defaults. */
class PartyQueryParametersTest {

    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "reader", UUID.randomUUID());

    @Test
    void defaultsOnlyAbsentParameters() {
        var query = PartyQueryParameters.parse(METADATA, Map.of());
        assertSame(METADATA, query.metadata());
        assertEquals(50, query.criteria().limit());
        assertNull(query.cursor());
        assertNull(query.criteria().type());
        assertNull(query.criteria().recordStatus());
        assertNull(query.criteria().createdFrom());
        assertNull(query.criteria().createdTo());
        assertFalse(query.criteria().name().isEffective());
    }

    @Test
    void rejectsUnknownRepeatedAndMissingValuesRatherThanTakingTheFirstValue() {
        invalid(Map.of("unknown", List.of("secret-value")));
        for (String key : List.of("type", "recordStatus", "displayNameStartsWith", "displayNameContains", "createdFrom", "createdTo", "cursor", "limit")) {
            invalid(Map.of(key, List.of("same", "same")));
            invalid(Map.of(key, List.of()));
            var values = new ArrayList<String>();
            values.add(null);
            invalid(Map.of(key, values));
        }
        var missing = new HashMap<String, List<String>>();
        missing.put("limit", null);
        invalid(missing);
    }

    @Test
    void acceptsEveryExactEnumAndRejectsCaseWhitespaceAndUnknownValues() {
        for (PartyType type : PartyType.values()) {
            assertEquals(type, PartyQueryParameters.parse(METADATA, Map.of("type", List.of(type.name()))).criteria().type());
        }
        for (PartyRecordStatus status : PartyRecordStatus.values()) {
            assertEquals(status, PartyQueryParameters.parse(METADATA, Map.of("recordStatus", List.of(status.name()))).criteria().recordStatus());
        }
        for (String key : List.of("type", "recordStatus")) {
            for (String value : List.of("", " ", "active", " ACTIVE", "UNKNOWN")) {
                invalid(Map.of(key, List.of(value)));
            }
        }
    }

    @Test
    void validatesDecimalPageSizeWithoutTreatingInvalidValuesAsDefaults() {
        for (int limit : List.of(1, 50, 200)) {
            assertEquals(limit, PartyQueryParameters.parse(METADATA, Map.of("limit", List.of(Integer.toString(limit)))).criteria().limit());
        }
        assertEquals(1, PartyQueryParameters.parse(METADATA, Map.of("limit", List.of("001"))).criteria().limit());
        for (String invalid : List.of("", " ", "0", "201", "-1", "+1", "1.0", "1e2", " 50", "50 ", "١", "99999999999999999999999")) {
            invalid(Map.of("limit", List.of(invalid)));
        }
    }

    @Test
    void boundsRawCodePointsBeforeCanonicalizationAndTreatsWildcardsLiterally() {
        for (String key : List.of("displayNameStartsWith", "displayNameContains")) {
            PartyQueryParameters.parse(METADATA, Map.of(key, List.of("🙂".repeat(300))));
            PartyQueryParameters.parse(METADATA, Map.of(key, List.of("ß".repeat(300))));
            invalid(Map.of(key, List.of("🙂".repeat(301))));
            invalid(Map.of(key, List.of(" ".repeat(301))));
            assertFalse(PartyQueryParameters.parse(METADATA, Map.of(key, List.of(" \t "))).criteria().name().isEffective());
        }
        var query = PartyQueryParameters.parse(METADATA, Map.of("displayNameStartsWith", List.of("  Áda  %_ "),
                "displayNameContains", List.of(" straße  ")));
        assertEquals("ÁDA  %_", query.criteria().name().startsWith());
        assertEquals("STRASSE", query.criteria().name().contains());
    }

    @Test
    void convertsEquivalentOffsetsToTheSameExactInclusiveInstantAndRejectsInvertedBounds() {
        var utc = LocalDate.of(2026, Month.SEPTEMBER, 19).atTime(12, 0, 0, 123456789).atOffset(ZoneOffset.UTC);
        var offset = utc.withOffsetSameInstant(ZoneOffset.ofHours(-5));
        var query = PartyQueryParameters.parse(METADATA, Map.of("createdFrom", List.of(utc.toString()), "createdTo", List.of(offset.toString())));
        assertEquals(utc.toInstant(), query.criteria().createdFrom());
        assertEquals(utc.toInstant(), query.criteria().createdTo());
        invalid(Map.of("createdFrom", List.of(utc.plusNanos(1).toString()), "createdTo", List.of(utc.toString())));
        for (String key : List.of("createdFrom", "createdTo")) {
            for (String value : List.of("", " ", utc.toLocalDateTime().toString(), "2026-02-30T12:00:00Z", "not-a-date")) {
                invalid(Map.of(key, List.of(value)));
            }
        }
    }

    @Test
    void preservesTheOpaqueCursorButRejectsPresentBlankValues() {
        var query = PartyQueryParameters.parse(METADATA, Map.of("cursor", List.of("opaque.cursor-token")));
        assertEquals("opaque.cursor-token", query.cursor());
        invalid(Map.of("cursor", List.of("")));
        invalid(Map.of("cursor", List.of(" \t ")));
    }

    private static void invalid(Map<String, List<String>> parameters) {
        var failure = assertThrows(ApiResponseException.class, () -> PartyQueryParameters.parse(METADATA, parameters));
        assertEquals(PartyResponseCode.BAD_REQUEST, failure.getResponseCode());
        assertNull(failure.getCause());
    }
}
