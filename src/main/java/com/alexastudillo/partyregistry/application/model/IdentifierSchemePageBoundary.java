package com.alexastudillo.partyregistry.application.model;

import java.util.Objects;

/** Carries a decoded exclusive continuation position and traversal direction. */
public record IdentifierSchemePageBoundary(Direction direction, IdentifierSchemePagePosition position) {
    public IdentifierSchemePageBoundary {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(position, "position");
    }

    /** Selects forward or backward traversal while results remain in ascending order. */
    public enum Direction { NEXT, PREVIOUS }
}
