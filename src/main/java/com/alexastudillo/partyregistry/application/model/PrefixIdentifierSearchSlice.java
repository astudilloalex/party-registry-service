package com.alexastudillo.partyregistry.application.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Describes a bounded safe prefix page, complete count and actual neighbor positions in one snapshot. */
public record PrefixIdentifierSearchSlice(List<IdentifierSearchHit> items, long totalElements,
        Optional<PartyIdentifierPagePosition> next, Optional<PartyIdentifierPagePosition> previous) {
    public PrefixIdentifierSearchSlice {
        items = List.copyOf(items);
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(previous, "previous");
        if (totalElements < items.size() || items.size() > 200
                || totalElements == 0 && (next.isPresent() || previous.isPresent())) {
            throw new IllegalArgumentException("Prefix identifier search count, page or navigation is inconsistent");
        }
    }
}
