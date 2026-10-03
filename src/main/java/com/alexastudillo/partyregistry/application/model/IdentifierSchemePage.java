package com.alexastudillo.partyregistry.application.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Supplies immutable catalog rows and available authenticated cursors without full-catalog counts. */
public record IdentifierSchemePage(
        List<IdentifierSchemeResult> items, Optional<String> nextCursor, Optional<String> previousCursor) {
    public IdentifierSchemePage {
        items = List.copyOf(items);
        Objects.requireNonNull(nextCursor, "nextCursor");
        Objects.requireNonNull(previousCursor, "previousCursor");
        if (items.size() > IdentifierSchemeSearchCriteria.MAXIMUM_LIMIT
                || (items.isEmpty() && (nextCursor.isPresent() || previousCursor.isPresent()))) {
            throw new IllegalArgumentException("Identifier scheme page is inconsistent");
        }
    }
}
