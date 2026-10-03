package com.alexastudillo.partyregistry.application.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Returns bounded detached catalog rows and only genuinely available continuation positions. */
public record IdentifierSchemePageSlice(
        List<IdentifierSchemeResult> items, Optional<IdentifierSchemePagePosition> next,
        Optional<IdentifierSchemePagePosition> previous) {
    public IdentifierSchemePageSlice {
        items = List.copyOf(items);
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(previous, "previous");
        if (items.size() > IdentifierSchemeSearchCriteria.MAXIMUM_LIMIT
                || (items.isEmpty() && (next.isPresent() || previous.isPresent()))) {
            throw new IllegalArgumentException("Identifier scheme page slice is inconsistent");
        }
    }
}
