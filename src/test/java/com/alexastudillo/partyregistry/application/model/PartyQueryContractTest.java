package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies canonical filtering, scope identity, precise ordering, and immutable safe query results.
 */
class PartyQueryContractTest {

    private static final Instant CREATED = LocalDateTime.of(2026, Month.SEPTEMBER, 19, 12, 0, 0, 123456000)
            .toInstant(ZoneOffset.UTC);
    private static final TenantId TENANT = new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
    private static final PartyId PARTY = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));

    @Test
    void literalCanonicalFiltersIntersectAndPreserveAccentsPunctuationAndInteriorSpaces() {
        var filter = new PartyNamePredicate("  straße  ", "  ÁGUILA_%  ");
        String stored = "  Straße  Águila_%  ";

        assertTrue(filter.matches(stored));
        assertFalse(filter.matches("Strasse Aguila_%"));
        assertFalse(filter.matches("Strasse ÁguilaXYZ"));
        assertFalse(filter.matches("Another Águila_%"));
        assertEquals("  Straße  Águila_%  ", stored);
        assertFalse(new PartyNamePredicate(null, "two  spaces").matches("Two Spaces"));
        assertTrue(new PartyNamePredicate(null, "two  spaces").matches("Two  Spaces"));
    }

    @Test
    void blankFiltersAreAbsentAndExpansionDoesNotBecomeANewWriteLengthConstraint() {
        assertEquals(new PartyNamePredicate(null, null), new PartyNamePredicate("\u2003", " \t"));
        assertFalse(new PartyNamePredicate(null, " ").isEffective());
        assertTrue(new PartyNamePredicate("", null).matches("Any Label"));
        var expanded = new PartyNamePredicate("ß".repeat(300), null);
        assertTrue(expanded.isEffective());
        assertTrue(expanded.matches("ß".repeat(300)));
        assertEquals("SS".repeat(300), expanded.startsWith());
    }

    @Test
    void effectiveScopeUsesCanonicalFiltersEquivalentInstantsTenantAndPageSize() {
        var criteria = new PartySearchCriteria(PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED,
                new PartyNamePredicate(" straße ", "label"), CREATED, CREATED, 50);
        Instant sameInstant = CREATED.atOffset(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(5)).toInstant();
        var equivalent = new PartySearchCriteria(PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED,
                new PartyNamePredicate("STRASSE", " LABEL "), sameInstant, sameInstant, 50);
        var scope = new PartySearchScope(TENANT, criteria);

        assertEquals(scope, new PartySearchScope(TENANT, equivalent));
        var otherTenant = new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e6"));
        assertNotEquals(scope, new PartySearchScope(otherTenant, criteria));
        assertNotEquals(scope, new PartySearchScope(TENANT, new PartySearchCriteria(
                criteria.type(), criteria.recordStatus(), criteria.name(), CREATED, CREATED, 200)));
        assertNotEquals(scope, new PartySearchScope(TENANT, new PartySearchCriteria(
                PartyType.LEGAL_ENTITY, criteria.recordStatus(), criteria.name(), CREATED, CREATED, 50)));
        assertNotEquals(scope, new PartySearchScope(TENANT, new PartySearchCriteria(
                criteria.type(), PartyRecordStatus.ACTIVE, criteria.name(), CREATED, CREATED, 50)));
    }

    @Test
    void creationIntervalsAreInclusiveAndRejectInversion() {
        var name = new PartyNamePredicate(null, null);
        Instant later = CREATED.plusNanos(1000);
        var criteria = new PartySearchCriteria(null, null, name, CREATED, later, 50);

        assertTrue(criteria.containsCreationInstant(CREATED));
        assertTrue(criteria.containsCreationInstant(later));
        assertFalse(criteria.containsCreationInstant(CREATED.minusNanos(1)));
        assertFalse(criteria.containsCreationInstant(later.plusNanos(1)));
        assertTrue(new PartySearchCriteria(null, null, name, CREATED, CREATED, 1).containsCreationInstant(CREATED));
        assertThrows(IllegalArgumentException.class,
                () -> new PartySearchCriteria(null, null, name, later, CREATED, 50));
        assertThrows(IllegalArgumentException.class,
                () -> new PartySearchCriteria(null, null, name, null, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PartySearchCriteria(null, null, name, null, null, 201));
    }

    @Test
    void positionsPreserveSubmillisecondTiesAndUnsignedOrderingInBothUuidHalves() {
        var lower = position("7fffffff-ffff-ffff-ffff-ffffffffffff", CREATED);
        var higher = position("80000000-0000-0000-0000-000000000000", CREATED);
        var lowTail = position("80000000-0000-0000-7fff-ffffffffffff", CREATED);
        var highTail = position("80000000-0000-0000-8000-000000000000", CREATED);

        assertTrue(lower.compareTo(higher) < 0);
        assertTrue(lowTail.compareTo(highTail) < 0);
        assertTrue(highTail.compareTo(lowTail) > 0);
        assertEquals(0, lowTail.compareTo(position(lowTail.partyId().value().toString(), CREATED)));
        var later = new PartyPagePosition(CREATED.plusNanos(1000), lower.partyId());
        assertTrue(later.compareTo(higher) > 0);
        var boundary = new PartyPageBoundary(PartyPageBoundary.Direction.PREVIOUS, later);
        assertEquals(CREATED.plusNanos(1000), boundary.position().createdAt());
        assertEquals(PartyPageBoundary.Direction.PREVIOUS, boundary.direction());
    }

    @Test
    void resultsAreImmutablePreserveStoredTextAndRetainLongCounts() {
        var summary = new PartySummaryResult(PARTY, PartyType.NATURAL_PERSON, "Historical Label",
                PartyRecordStatus.ARCHIVED, CREATED, new PartyVersion(3));
        var supplied = new ArrayList<>(List.of(summary));
        long total = (long) Integer.MAX_VALUE + 1;
        var slice = new PartyPageSlice(supplied, total, Optional.of(summary.position()), Optional.empty());
        var result = new PartyPageResult(slice.items(), "opaque", null, total, Math.ceilDiv(total, 50));
        supplied.clear();

        assertEquals(List.of(summary), slice.items());
        assertEquals("Historical Label", result.items().getFirst().displayName());
        assertEquals(total, result.totalElements());
        assertEquals(1, result.numberOfElements());
        List<PartySummaryResult> immutable = result.items();
        assertThrows(UnsupportedOperationException.class, immutable::clear);
        var empty = new PartyPageSlice(List.of(), 0, Optional.empty(), Optional.empty());
        assertTrue(empty.items().isEmpty());
        assertEquals(0, empty.totalElements());
    }

    @Test
    void invalidCursorFailuresContainNoClientTokenOrScopePayload() {
        var failure = new ApplicationFailure.InvalidPartyCursor();
        assertEquals(0, failure.getClass().getRecordComponents().length);
        assertEquals("Invalid Party cursor", new ApplicationException(failure).getMessage());
    }

    private static PartyPagePosition position(String id, Instant createdAt) {
        return new PartyPagePosition(createdAt, new PartyId(UUID.fromString(id)));
    }
}
