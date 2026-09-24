package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyNamePredicate;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPagePosition;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartySummaryResult;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies exact full-scan counts and bounded retained state independently of qualified dataset size.
 */
class PartyNamePageAccumulatorTest {

    private static final Instant CREATED = LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atStartOfDay().toInstant(ZoneOffset.UTC);

    @Test
    void countsTenThousandMatchesWhileRetainingOnlyTheRequestedPage() {
        var accumulator = new PartyNamePageAccumulator(criteria(), Optional.empty());
        for (int batch = 0; batch < 100; batch++) {
            int start = batch * 100;
            accumulator.acceptBatch(IntStream.range(start, start + 100).mapToObj(PartyNamePageAccumulatorTest::summary).toList());
            assertTrue(accumulator.maximumRetained() <= 3);
        }
        var page = accumulator.result();
        assertEquals(10000, page.totalElements());
        assertEquals(List.of(summary(0), summary(1), summary(2)), page.items());
        assertEquals(10000, accumulator.scannedRows());
        assertEquals(100, accumulator.batches());
        assertEquals(3, accumulator.maximumRetained());
        assertEquals(Optional.of(summary(2).position()), page.next());
        assertTrue(page.previous().isEmpty());
    }

    @Test
    void previousTraversalRetainsTheNearestLargerMatchesInDescendingOrder() {
        var boundary = new PartyPageBoundary(PartyPageBoundary.Direction.PREVIOUS, summary(9).position());
        var accumulator = new PartyNamePageAccumulator(criteria(), Optional.of(boundary));
        for (int index = 0; index < 20; index++) {
            accumulator.acceptBatch(List.of(summary(index)));
        }
        var page = accumulator.result();
        assertEquals(List.of(summary(6), summary(7), summary(8)), page.items());
        assertEquals(20, page.totalElements());
        assertEquals(3, accumulator.maximumRetained());
        assertEquals(Optional.of(summary(6).position()), page.previous());
        assertEquals(Optional.of(summary(8).position()), page.next());
    }

    @Test
    void handlesNoMatchesAndEmptyContinuationWithoutInventingRows() {
        var empty = new PartyNamePageAccumulator(criteria(), Optional.empty());
        empty.acceptBatch(List.of());
        assertEquals(0, empty.result().totalElements());
        assertTrue(empty.result().items().isEmpty());
        var position = new PartyPagePosition(CREATED.minusSeconds(10), summary(10).partyId());
        var after = new PartyNamePageAccumulator(criteria(),
                Optional.of(new PartyPageBoundary(PartyPageBoundary.Direction.NEXT, position)));
        after.acceptBatch(List.of(summary(0), summary(1)));
        assertTrue(after.result().items().isEmpty());
        assertEquals(2, after.result().totalElements());
        assertEquals(Optional.of(position), after.result().previous());
        assertTrue(after.result().next().isEmpty());
    }

    private static PartySearchCriteria criteria() {
        return new PartySearchCriteria(null, null, new PartyNamePredicate("straße", "%_"), null, null, 3);
    }

    private static PartySummaryResult summary(int index) {
        return new PartySummaryResult(new PartyId(new UUID(0, index)), PartyType.NATURAL_PERSON,
                "Straße %_ " + index, PartyRecordStatus.DRAFT, CREATED.minusSeconds(index), PartyVersion.initial());
    }
}
