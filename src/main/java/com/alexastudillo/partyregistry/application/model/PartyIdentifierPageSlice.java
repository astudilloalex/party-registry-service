package com.alexastudillo.partyregistry.application.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Returns safe historical rows, exact pre-continuation count and navigation positions from one consistent read. */
public record PartyIdentifierPageSlice(List<PartyIdentifierResult> items, long totalElements,
        Optional<PartyIdentifierPagePosition> next, Optional<PartyIdentifierPagePosition> previous) {

    public PartyIdentifierPageSlice {
        items = List.copyOf(items);
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(previous, "previous");
        if (totalElements < items.size() || (totalElements == 0 && (next.isPresent() || previous.isPresent()))
                || items.size() > PartyIdentifierSearchCriteria.MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("Identifier page data, count or capacity is inconsistent");
        }
    }
}
