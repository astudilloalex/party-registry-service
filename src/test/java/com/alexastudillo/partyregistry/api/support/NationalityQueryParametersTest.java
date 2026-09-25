package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.application.command.ListNationalitiesQuery;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies strict nationality filter parsing and UTC date evaluation. */
class NationalityQueryParametersTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final PartyId PARTY = new PartyId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.ofHours(-5));

    @Test
    void defaultsToOneCapturedUtcDateAndBoundedPageSize() {
        ListNationalitiesQuery query = parse(Map.of());
        assertEquals(LocalDate.of(2026, Month.SEPTEMBER, 24), query.criteria().asOfDate());
        assertEquals(50, query.criteria().limit());
        assertNull(query.criteria().countryCode());
        assertNull(query.criteria().isPrimary());
        assertFalse(query.criteria().includeExpired());
        assertTrue(query.cursor().isEmpty());
    }

    @Test
    void parsesAllSupportedValuesExactly() {
        ListNationalitiesQuery query = parse(Map.of(
                "countryCode", List.of("EC"), "isPrimary", List.of("false"),
                "asOfDate", List.of("2026-09-23"), "includeExpired", List.of("true"),
                "limit", List.of("200"), "cursor", List.of("opaque-token")));
        assertEquals("EC", query.criteria().countryCode());
        assertEquals(false, query.criteria().isPrimary());
        assertEquals(LocalDate.of(2026, Month.SEPTEMBER, 23), query.criteria().asOfDate());
        assertTrue(query.criteria().includeExpired());
        assertEquals(200, query.criteria().limit());
        assertEquals("opaque-token", query.cursor().orElseThrow());
    }

    @Test
    void rejectsUnknownRepeatedBlankAndMalformedParameters() {
        for (Map<String, List<String>> invalid : List.of(
                Map.of("other", List.of("x")), Map.of("isPrimary", List.of("true", "true")),
                Map.of("cursor", List.of(" ")), Map.of("countryCode", List.of("ec")),
                Map.of("countryCode", List.of("ÉC")), Map.of("isPrimary", List.of("TRUE")),
                Map.of("includeExpired", List.of("1")), Map.of("limit", List.of("0")),
                Map.of("limit", List.of("201")), Map.of("limit", List.of("+1")),
                Map.of("limit", List.of("9999999999999999")),
                Map.of("asOfDate", List.of("2026-02-30")),
                Map.of("asOfDate", List.of("2026-9-23")))) {
            ApiResponseException error = assertThrows(ApiResponseException.class, () -> parse(invalid));
            assertEquals("bad-request", error.getResponseCode().getCode());
        }
    }

    private static ListNationalitiesQuery parse(Map<String, List<String>> values) {
        return NationalityQueryParameters.parse(TENANT, PARTY, values, CLOCK);
    }
}
