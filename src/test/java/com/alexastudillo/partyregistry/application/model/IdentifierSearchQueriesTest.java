package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;
import com.alexastudillo.partyregistry.domain.policy.StandardIdentifierNormalizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies immutable effective filters, canonical scope equality, defaults and redaction independently of delivery. */
class IdentifierSearchQueriesTest {
    private static final TenantId TENANT = new TenantId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    private static final IdentifierSchemeId SCHEME = new IdentifierSchemeId(UUID.fromString("22222222-2222-4222-8222-222222222222"));

    @Test
    void omittedFullAndDuplicateReorderedFiltersAreEquivalentButEmptyIsDistinct() {
        var omitted = IdentifierSearchFilters.of(null, null);
        var identifiers = new ArrayList<>(Arrays.asList(PartyIdentifierStatus.values()));
        identifiers.add(PartyIdentifierStatus.VERIFIED);
        Collections.reverse(identifiers);
        var explicit = IdentifierSearchFilters.of(identifiers, Arrays.asList(PartyRecordStatus.values()));
        assertEquals(omitted, explicit);
        identifiers.clear();
        assertEquals(5, explicit.identifierStatuses().size());
        var empty = IdentifierSearchFilters.of(List.of(), null);
        assertNotEquals(omitted, empty);
        assertTrue(empty.matchesNothing());
        assertFalse(omitted.matchesNothing());
        var explicitStatuses = explicit.identifierStatuses();
        assertThrows(UnsupportedOperationException.class, explicitStatuses::clear);
    }

    @Test
    void defaultLimitAndCanonicalSelectorProduceEquivalentScopes() {
        var filters = IdentifierSearchFilters.of(null, null);
        var query = new PrefixIdentifierSearchQuery(TENANT, "Exact-Code", " private ", filters, null);
        assertEquals(50, query.limit());
        var normalizer = StandardIdentifierNormalizer.TRIM_UPPERCASE_V1;
        var first = new IdentifierSearchScope(TENANT, SCHEME, query.schemeCode(),
                new IdentifierSearchSelector(normalizer, normalizer.normalize(query.prefix())), filters, query.limit());
        var equivalent = new IdentifierSearchScope(TENANT, SCHEME, "Exact-Code",
                new IdentifierSearchSelector(normalizer, "PRIVATE"), filters, 50);
        assertEquals(first, equivalent);
        assertNotEquals(first, new IdentifierSearchScope(TENANT, SCHEME, "Exact-Code",
                equivalent.selector(), IdentifierSearchFilters.of(List.of(), null), 50));
        assertEquals("IdentifierSearchScope[redacted]", first.toString());
        assertEquals("PrefixIdentifierSearchQuery[redacted]", query.toString());
        var exact = new ExactIdentifierSearchQuery(TENANT, "Exact-Code", "PRIVATE", filters);
        assertEquals("ExactIdentifierSearchQuery[redacted]", exact.toString());
        var continuation = new PrefixIdentifierSearchQuery(TENANT, "Exact-Code", "PRIVATE", filters, 50, "SECRET_TOKEN");
        assertFalse(continuation.toString().contains("SECRET_TOKEN"));
    }

    @Test
    void rejectsInvalidLimitsAndBlankOrOversizedTokensWithoutDiagnosticValues() {
        var filters = IdentifierSearchFilters.of(null, null);
        for (int limit : new int[] {0, -1, 201, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new PrefixIdentifierSearchQuery(TENANT, "SEARCH", "PRIVATE", filters, limit, null));
        }
        for (String cursor : List.of("", " ", "T".repeat(257))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new PrefixIdentifierSearchQuery(TENANT, "SEARCH", "PRIVATE", filters, 50, cursor));
        }
    }
}
