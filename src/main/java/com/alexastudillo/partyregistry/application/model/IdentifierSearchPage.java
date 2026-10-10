package com.alexastudillo.partyregistry.application.model;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Returns immutable safe search results and exact long metadata while keeping issued tokens out of diagnostics. */
public record IdentifierSearchPage(List<IdentifierSearchHit> items, long totalElements, long totalPages,
        @Nullable String nextCursor, @Nullable String previousCursor) {
    public IdentifierSearchPage {
        items = List.copyOf(items);
        if (totalElements < items.size() || totalPages < 0
                || totalElements == 0 && (totalPages != 0 || nextCursor != null || previousCursor != null)
                || totalElements > 0 && totalPages == 0) {
            throw new IllegalArgumentException("Identifier search collection metadata is inconsistent");
        }
    }

    public static IdentifierSearchPage exact(ExactIdentifierSearchSlice slice) {
        return new IdentifierSearchPage(slice.items(), slice.totalElements(), slice.totalElements() == 0 ? 0 : 1, null, null);
    }

    /** Computes complete page totals without adding limit-1 to a potentially maximal long count. */
    public static IdentifierSearchPage prefix(PrefixIdentifierSearchSlice slice, int limit,
            @Nullable String nextCursor, @Nullable String previousCursor) {
        if (limit < 1 || limit > 200 || slice.items().size() > limit) {
            throw new IllegalArgumentException("Identifier search page capacity is invalid");
        }
        return new IdentifierSearchPage(slice.items(), slice.totalElements(), Math.ceilDiv(slice.totalElements(), limit),
                nextCursor, previousCursor);
    }

    public int numberOfElements() {
        return items.size();
    }

    @Override
    public String toString() {
        return "IdentifierSearchPage[redacted]";
    }
}
