package com.alexastudillo.partyregistry.application.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Carries detached list results and opaque signed navigation for the API boundary. */
public record NationalityPage(
        List<NationalityResult> items, long totalElements, int limit,
        Optional<String> nextCursor, Optional<String> previousCursor) {

    public NationalityPage {
        items = List.copyOf(items);
        Objects.requireNonNull(nextCursor, "nextCursor");
        Objects.requireNonNull(previousCursor, "previousCursor");
        if (limit < 1 || limit > NationalitySearchCriteria.MAXIMUM_LIMIT || totalElements < items.size()) {
            throw new IllegalArgumentException("Nationality page metadata is inconsistent");
        }
    }
}
