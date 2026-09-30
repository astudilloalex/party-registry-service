package com.alexastudillo.partyregistry.application.model;

import java.util.Objects;

/**
 * Describes authenticated continuation relative to an exact position without requiring that row to exist.
 */
public record PartyPageBoundary(Direction direction, PartyPagePosition position) {

    public PartyPageBoundary {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(position, "position");
    }

    /** Selects smaller tuples for forward traversal or nearest larger tuples for backward traversal. */
    public enum Direction {
        NEXT,
        PREVIOUS
    }
}
