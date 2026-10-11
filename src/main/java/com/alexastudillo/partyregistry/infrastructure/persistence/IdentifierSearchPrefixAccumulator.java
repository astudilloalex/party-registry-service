package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.*;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

/** Counts the entire descending match stream while retaining only the requested nearest page and global extrema. */
final class IdentifierSearchPrefixAccumulator {
    private final int limit;
    private final Optional<PartyIdentifierPageBoundary> boundary;
    private final ArrayDeque<IdentifierSearchHit> page = new ArrayDeque<>();
    private long total;
    private Optional<PartyIdentifierPagePosition> first = Optional.empty();
    private Optional<PartyIdentifierPagePosition> last = Optional.empty();

    IdentifierSearchPrefixAccumulator(int limit, Optional<PartyIdentifierPageBoundary> boundary) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("Identifier search page capacity is invalid");
        this.limit = limit;
        this.boundary = boundary;
    }

    void add(IdentifierSearchHit hit) {
        last.ifPresent(previous -> {
            if (IdentifierSearchPositions.compare(previous, hit.position()) <= 0) {
                throw new IllegalStateException("Identifier search matches must be strictly descending");
            }
        });
        total = Math.incrementExact(total);
        if (first.isEmpty()) first = Optional.of(hit.position());
        last = Optional.of(hit.position());
        if (boundary.isEmpty()) {
            if (page.size() < limit) page.addLast(hit);
            return;
        }
        var requested = boundary.get();
        int relative = IdentifierSearchPositions.compare(hit.position(), requested.position());
        if (requested.direction() == PartyIdentifierPageBoundary.Direction.NEXT) {
            if (relative < 0 && page.size() < limit) page.addLast(hit);
        } else if (relative > 0) {
            if (page.size() == limit) page.removeFirst();
            page.addLast(hit);
        }
    }

    PrefixIdentifierSearchSlice finish() {
        if (total == 0) return new PrefixIdentifierSearchSlice(List.of(), 0, Optional.empty(), Optional.empty());
        var firstPage = page.isEmpty() ? boundary.orElseThrow().position() : page.getFirst().position();
        var lastPage = page.isEmpty() ? boundary.orElseThrow().position() : page.getLast().position();
        boolean next = IdentifierSearchPositions.compare(last.orElseThrow(), lastPage) < 0;
        boolean previous = IdentifierSearchPositions.compare(first.orElseThrow(), firstPage) > 0;
        return new PrefixIdentifierSearchSlice(List.copyOf(page), total, next ? Optional.of(lastPage) : Optional.empty(),
                previous ? Optional.of(firstPage) : Optional.empty());
    }

    int retainedSize() { return page.size(); }
}
