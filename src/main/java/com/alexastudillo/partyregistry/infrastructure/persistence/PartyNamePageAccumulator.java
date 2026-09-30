package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyNamePredicate;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPagePosition;
import com.alexastudillo.partyregistry.application.model.PartyPageSlice;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartySummaryResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Counts canonical name matches during descending traversal while retaining only one bounded page and edge positions.
 */
final class PartyNamePageAccumulator {

    private final PartyNamePredicate predicate;
    private final int limit;
    private final PartyPageBoundary.Direction direction;
    private final @Nullable PartyPagePosition requestedPosition;
    private final Deque<PartySummaryResult> page = new ArrayDeque<>();
    private @Nullable PartyPagePosition scanPosition;
    private @Nullable PartyPagePosition firstMatch;
    private @Nullable PartyPagePosition lastMatch;
    private long total;
    private long scannedRows;
    private long batches;
    private int maximumRetained;

    PartyNamePageAccumulator(PartySearchCriteria criteria, Optional<PartyPageBoundary> boundary) {
        predicate = criteria.name();
        limit = criteria.limit();
        direction = boundary.map(PartyPageBoundary::direction).orElse(PartyPageBoundary.Direction.NEXT);
        requestedPosition = boundary.map(PartyPageBoundary::position).orElse(null);
    }

    Optional<PartyPageBoundary> scanBoundary() {
        return Optional.ofNullable(scanPosition)
                .map(position -> new PartyPageBoundary(PartyPageBoundary.Direction.NEXT, position));
    }

    void acceptBatch(List<PartySummaryResult> batch) {
        if (batch.isEmpty()) {
            return;
        }
        batches = Math.incrementExact(batches);
        scannedRows = Math.addExact(scannedRows, batch.size());
        scanPosition = batch.getLast().position();
        for (PartySummaryResult item : batch) {
            if (predicate.matches(item.displayName())) {
                acceptMatch(item);
            }
        }
    }

    private void acceptMatch(PartySummaryResult item) {
        total = Math.incrementExact(total);
        PartyPagePosition position = item.position();
        if (firstMatch == null) {
            firstMatch = position;
        }
        lastMatch = position;
        if (requestedPosition == null || (direction == PartyPageBoundary.Direction.NEXT
                && position.compareTo(requestedPosition) < 0)) {
            if (page.size() < limit) {
                page.addLast(item);
            }
        } else if (direction == PartyPageBoundary.Direction.PREVIOUS && position.compareTo(requestedPosition) > 0) {
            if (page.size() == limit) {
                page.removeFirst();
            }
            page.addLast(item);
        }
        maximumRetained = Math.max(maximumRetained, page.size());
    }

    PartyPageSlice result() {
        if (total == 0) {
            return new PartyPageSlice(List.of(), 0, Optional.empty(), Optional.empty());
        }
        PartyPagePosition maximum = Objects.requireNonNull(firstMatch, "first match");
        PartyPagePosition minimum = Objects.requireNonNull(lastMatch, "last match");
        PartyPagePosition next = page.isEmpty()
                ? Objects.requireNonNull(requestedPosition, "empty continuation") : page.getLast().position();
        PartyPagePosition previous = page.isEmpty()
                ? Objects.requireNonNull(requestedPosition, "empty continuation") : page.getFirst().position();
        return new PartyPageSlice(List.copyOf(page), total,
                minimum.compareTo(next) < 0 ? Optional.of(next) : Optional.empty(),
                maximum.compareTo(previous) > 0 ? Optional.of(previous) : Optional.empty());
    }

    long scannedRows() {
        return scannedRows;
    }

    long batches() {
        return batches;
    }

    int maximumRetained() {
        return maximumRetained;
    }
}
