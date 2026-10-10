package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies safe immutable slices, full-precision unsigned ordering and overflow-safe collection metadata. */
class IdentifierSearchResultsTest {
    @Test
    void preservesNanosecondAndUnsignedUuidOrdering() {
        var low = new PartyIdentifierPagePosition(Instant.parse("2026-10-10T00:00:00.123456789Z"),
                new PartyIdentifierId(UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff")));
        var high = new PartyIdentifierPagePosition(low.createdAt(),
                new PartyIdentifierId(UUID.fromString("80000000-0000-0000-0000-000000000000")));
        assertTrue(IdentifierSearchPositions.compare(high, low) > 0);
        assertTrue(IdentifierSearchPositions.compare(new PartyIdentifierPagePosition(low.createdAt().plusNanos(1),
                low.identifierId()), high) > 0);
        assertEquals(0, IdentifierSearchPositions.compare(low, low));
        var bottom = new PartyIdentifierPagePosition(low.createdAt(), new PartyIdentifierId(new UUID(0, Long.MAX_VALUE)));
        var top = new PartyIdentifierPagePosition(low.createdAt(), new PartyIdentifierId(new UUID(0, Long.MIN_VALUE)));
        assertTrue(IdentifierSearchPositions.compare(top, bottom) > 0);
    }

    @Test
    void exactSlicesAreCompleteImmutableAndNeverImplicitlyCapped() {
        var items = new ArrayList<IdentifierSearchHit>();
        for (int index = 1; index <= 201; index++) items.add(hit(index));
        var slice = new ExactIdentifierSearchSlice(items, 201);
        items.clear();
        var page = IdentifierSearchPage.exact(slice);
        assertEquals(201, page.numberOfElements());
        assertEquals(201, page.totalElements());
        assertEquals(1, page.totalPages());
        assertEquals(null, page.nextCursor());
        assertEquals(null, page.previousCursor());
        var pageItems = page.items();
        assertThrows(UnsupportedOperationException.class, pageItems::clear);
        var invalidHits = List.of(hit(1));
        assertThrows(IllegalArgumentException.class, () -> new ExactIdentifierSearchSlice(invalidHits, 2));
        assertEquals(0, IdentifierSearchPage.exact(new ExactIdentifierSearchSlice(List.of(), 0)).totalPages());
    }

    @Test
    void prefixMetadataRetainsEmptyContinuationTotalsAndRejectsInconsistency() {
        var slice = new PrefixIdentifierSearchSlice(List.of(hit(1)), 5, Optional.of(hit(1).position()), Optional.empty());
        var page = IdentifierSearchPage.prefix(slice, 2, "OPAQUE", null);
        assertEquals(3, page.totalPages());
        assertEquals(1, page.numberOfElements());
        assertEquals("IdentifierSearchPage[redacted]", page.toString());
        var empty = new PrefixIdentifierSearchSlice(List.of(), 5, Optional.empty(), Optional.of(hit(1).position()));
        assertEquals(5, IdentifierSearchPage.prefix(empty, 2, null, "OPAQUE").totalElements());
        var maximal = new PrefixIdentifierSearchSlice(List.of(), Long.MAX_VALUE, Optional.empty(), Optional.empty());
        assertEquals(Long.MAX_VALUE / 2 + 1, IdentifierSearchPage.prefix(maximal, 2, null, null).totalPages());
        var emptyHits = List.<IdentifierSearchHit>of();
        var nextPosition = Optional.of(hit(1).position());
        var previousPosition = Optional.<PartyIdentifierPagePosition>empty();
        assertThrows(IllegalArgumentException.class,
                () -> new PrefixIdentifierSearchSlice(emptyHits, 0, nextPosition, previousPosition));
        assertThrows(IllegalArgumentException.class, () -> IdentifierSearchPage.prefix(slice, 0, null, null));
    }

    private static IdentifierSearchHit hit(int index) {
        return new IdentifierSearchHit(new PartyId(new UUID(0, 1)), PartyType.NATURAL_PERSON, "Stored Name",
                PartyRecordStatus.ARCHIVED, "SEARCH", "***001", PartyIdentifierStatus.REVOKED, false,
                new PartyIdentifierPagePosition(Instant.EPOCH.plusNanos(index), new PartyIdentifierId(new UUID(0, index))));
    }
}
