package com.alexastudillo.partyregistry.application.model;

/** Compares precise identifier keysets using PostgreSQL-compatible unsigned UUID order. */
public final class IdentifierSearchPositions {
    private IdentifierSearchPositions() {
    }

    /** Returns ascending tuple comparison; consumers reverse it for descending response traversal. */
    public static int compare(PartyIdentifierPagePosition first, PartyIdentifierPagePosition second) {
        int time = first.createdAt().compareTo(second.createdAt());
        if (time != 0) {
            return time;
        }
        var firstId = first.identifierId().value();
        var secondId = second.identifierId().value();
        int high = Long.compareUnsigned(firstId.getMostSignificantBits(), secondId.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(firstId.getLeastSignificantBits(), secondId.getLeastSignificantBits());
    }
}
