package com.alexastudillo.partyregistry.application.model;

import java.util.Objects;

/** Describes authenticated forward/backward continuation without requiring the original boundary row to exist. */
public record PartyIdentifierPageBoundary(Direction direction, PartyIdentifierPagePosition position) {

    public PartyIdentifierPageBoundary {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(position, "position");
    }

    /** Chooses smaller tuples forward or nearest larger tuples backward while retaining descending response order. */
    public enum Direction {
        NEXT,
        PREVIOUS
    }
}
