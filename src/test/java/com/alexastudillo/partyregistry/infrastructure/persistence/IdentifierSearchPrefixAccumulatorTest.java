package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.*;
import com.alexastudillo.partyregistry.domain.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies complete counting and nearest bidirectional pages retain only bounded state, including disappeared boundaries. */
class IdentifierSearchPrefixAccumulatorTest {
    @Test
    void retainsOnlyRequestedCapacityAcrossTenThousandMatches() {
        var accumulator = new IdentifierSearchPrefixAccumulator(50, Optional.empty());
        for (int id = 10_000; id > 0; id--) {
            accumulator.add(hit(id));
            assertTrue(accumulator.retainedSize() <= 50);
        }
        var slice = accumulator.finish();
        assertEquals(10_000, slice.totalElements());
        assertEquals(50, slice.items().size());
        assertEquals(hit(10_000), slice.items().getFirst());
        assertEquals(hit(9_951), slice.items().getLast());
        assertTrue(slice.previous().isEmpty());
        assertEquals(Optional.of(hit(9_951).position()), slice.next());
    }

    @Test
    void traversesForwardAndRestoresNearestPreviousPageInDescendingOrder() {
        var first = scan(Optional.empty());
        assertEquals(List.of(hit(5), hit(4)), first.items());
        var next = scan(Optional.of(new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.NEXT, first.next().orElseThrow())));
        assertEquals(List.of(hit(3), hit(2)), next.items());
        var last = scan(Optional.of(new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.NEXT, next.next().orElseThrow())));
        assertEquals(List.of(hit(1)), last.items());
        assertTrue(last.next().isEmpty());
        var backward = scan(Optional.of(new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.PREVIOUS, last.previous().orElseThrow())));
        assertEquals(next.items(), backward.items());
        var restored = scan(Optional.of(new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.PREVIOUS, backward.previous().orElseThrow())));
        assertEquals(first.items(), restored.items());
    }

    @Test
    void retainsLiveTotalsAndActualNavigationForDisappearedOrEmptyBoundaries() {
        var vanished = new PartyIdentifierPagePosition(Instant.EPOCH, new PartyIdentifierId(new UUID(0, 6)));
        var previous = scan(Optional.of(new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.PREVIOUS, vanished)));
        assertTrue(previous.items().isEmpty());
        assertEquals(5, previous.totalElements());
        assertEquals(Optional.of(vanished), previous.next());
        assertTrue(previous.previous().isEmpty());
        var afterAll = scan(Optional.of(new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.NEXT, hit(0).position())));
        assertTrue(afterAll.items().isEmpty());
        assertEquals(Optional.of(hit(0).position()), afterAll.previous());
        assertTrue(afterAll.next().isEmpty());
        var empty = new IdentifierSearchPrefixAccumulator(2, Optional.of(new PartyIdentifierPageBoundary(
                PartyIdentifierPageBoundary.Direction.NEXT, vanished))).finish();
        assertEquals(0, empty.totalElements());
        assertTrue(empty.next().isEmpty() && empty.previous().isEmpty());
    }

    private static PrefixIdentifierSearchSlice scan(Optional<PartyIdentifierPageBoundary> boundary) {
        var accumulator = new IdentifierSearchPrefixAccumulator(2, boundary);
        for (int id = 5; id > 0; id--) accumulator.add(hit(id));
        return accumulator.finish();
    }

    private static IdentifierSearchHit hit(int id) {
        return new IdentifierSearchHit(new PartyId(new UUID(0, 9)), PartyType.NATURAL_PERSON, "Stored Name", PartyRecordStatus.ARCHIVED,
                "SEARCH", "***001", PartyIdentifierStatus.REVOKED, false,
                new PartyIdentifierPagePosition(Instant.EPOCH, new PartyIdentifierId(new UUID(0, id))));
    }
}
