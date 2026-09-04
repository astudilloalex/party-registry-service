package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

/**
 * Represents the nonnegative optimistic-concurrency version of a party aggregate.
 */
public record PartyVersion(long value) {

    public PartyVersion {
        if (value < 0) {
            throw new DomainValidationException(
                    DomainViolation.PARTY_VERSION_NEGATIVE,
                    "Party version cannot be negative");
        }
    }

    public static PartyVersion initial() {
        return new PartyVersion(0);
    }

    /**
     * Returns the next version, rejecting numeric overflow.
     *
     * @return the incremented version
     */
    public PartyVersion next() {
        if (value == Long.MAX_VALUE) {
            throw new DomainValidationException(
                    DomainViolation.PARTY_VERSION_OVERFLOW,
                    "Party version cannot be incremented");
        }
        return new PartyVersion(value + 1);
    }
}
