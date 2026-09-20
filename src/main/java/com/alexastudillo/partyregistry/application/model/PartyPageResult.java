package com.alexastudillo.partyregistry.application.model;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Carries an immutable summary page with opaque navigation and exact long-valued totals.
 */
public record PartyPageResult(
        List<PartySummaryResult> items,
        @Nullable String nextCursor,
        @Nullable String prevCursor,
        long totalElements,
        long totalPages) {

    public PartyPageResult {
        items = List.copyOf(items);
        if (totalElements < items.size() || totalPages < 0) {
            throw new IllegalArgumentException("Party page totals are inconsistent");
        }
    }

    /** Returns the actual result size rather than an independently supplied count. */
    public int numberOfElements() {
        return items.size();
    }
}
