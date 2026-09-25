package com.alexastudillo.partyregistry.application.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Carries a consistent detached count, bounded rows, and navigation positions. */
public record NationalityPageSlice(
        List<NationalityResult> items, long totalElements,
        Optional<NationalityPagePosition> next, Optional<NationalityPagePosition> previous) {

    public NationalityPageSlice {
        items = List.copyOf(items);
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(previous, "previous");
        if (totalElements < items.size() || items.size() > NationalitySearchCriteria.MAXIMUM_LIMIT
                || (totalElements == 0 && (next.isPresent() || previous.isPresent()))) {
            throw new IllegalArgumentException("Nationality page and count are inconsistent");
        }
    }
}
