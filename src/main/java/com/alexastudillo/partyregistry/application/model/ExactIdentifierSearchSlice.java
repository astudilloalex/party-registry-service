package com.alexastudillo.partyregistry.application.model;

import java.util.List;

/** Represents the complete immutable exact-match collection from one consistent read view. */
public record ExactIdentifierSearchSlice(List<IdentifierSearchHit> items, long totalElements) {
    public ExactIdentifierSearchSlice {
        items = List.copyOf(items);
        if (totalElements != items.size()) {
            throw new IllegalArgumentException("Exact identifier search count must describe the complete collection");
        }
    }
}
