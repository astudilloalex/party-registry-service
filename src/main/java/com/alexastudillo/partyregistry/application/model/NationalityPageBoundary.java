package com.alexastudillo.partyregistry.application.model;

import java.util.Objects;

/** Describes authenticated forward or backward navigation relative to an exact tuple. */
public record NationalityPageBoundary(Direction direction, NationalityPagePosition position) {

    public NationalityPageBoundary {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(position, "position");
    }

    /** Selects lower tuples forward or higher tuples backward in descending order. */
    public enum Direction { NEXT, PREVIOUS }
}
