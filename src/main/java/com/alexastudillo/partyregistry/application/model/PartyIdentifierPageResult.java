package com.alexastudillo.partyregistry.application.model;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Carries an immutable safe identifier page, opaque navigation and exact long-valued totals before HTTP mapping. */
public record PartyIdentifierPageResult(List<PartyIdentifierResult> items, @Nullable String nextCursor,
        @Nullable String prevCursor, long totalElements, long totalPages) {

    public PartyIdentifierPageResult {
        items = List.copyOf(items);
        if (totalElements < items.size() || totalPages < 0 || items.size() > PartyIdentifierSearchCriteria.MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("Identifier page totals or capacity are inconsistent");
        }
    }

    public int numberOfElements() {
        return items.size();
    }

    @Override
    public String toString() {
        return "PartyIdentifierPageResult[numberOfElements=" + numberOfElements() + ", cursors=<redacted>]";
    }
}
