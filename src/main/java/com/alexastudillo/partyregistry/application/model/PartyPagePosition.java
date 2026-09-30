package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Defines an exact creation-time/unsigned-UUID position with ascending natural tuple ordering.
 */
public record PartyPagePosition(Instant createdAt, PartyId partyId) implements Comparable<PartyPagePosition> {

    public PartyPagePosition {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(partyId, "partyId");
    }

    /** Compares timestamps first, then canonical unsigned UUID bytes; reverse this order for public pages. */
    @Override
    public int compareTo(PartyPagePosition other) {
        int timeOrder = createdAt.compareTo(other.createdAt);
        if (timeOrder != 0) {
            return timeOrder;
        }
        UUID left = partyId.value();
        UUID right = other.partyId.value();
        int highOrder = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return highOrder != 0 ? highOrder
                : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }
}
