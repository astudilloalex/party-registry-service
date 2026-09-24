package com.alexastudillo.partyregistry.application.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Returns summary rows, exact match count, and available navigation positions from one consistent read.
 */
public record PartyPageSlice(
        List<PartySummaryResult> items,
        long totalElements,
        Optional<PartyPagePosition> next,
        Optional<PartyPagePosition> previous) {

    public PartyPageSlice {
        items = List.copyOf(items);
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(previous, "previous");
        if (totalElements < items.size() || (totalElements == 0 && (next.isPresent() || previous.isPresent()))) {
            throw new IllegalArgumentException("Party page data and count are inconsistent");
        }
        if (items.size() > PartySearchCriteria.MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("Party page exceeds its bounded capacity");
        }
    }
}
