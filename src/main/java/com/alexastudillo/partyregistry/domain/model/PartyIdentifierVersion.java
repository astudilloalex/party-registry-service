package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

/**
 * Represents the nonnegative optimistic-concurrency version of a PartyIdentifier aggregate.
 */
public record PartyIdentifierVersion(long value) {

    public PartyIdentifierVersion {
        if (value < 0) {
            throw new DomainValidationException(
                    DomainViolation.PARTY_IDENTIFIER_VERSION_NEGATIVE,
                    "PartyIdentifier version cannot be negative");
        }
    }

    public static PartyIdentifierVersion initial() {
        return new PartyIdentifierVersion(0);
    }

    /**
     * Returns the next independent immutable identifier version without modifying this value.
     *
     * @return exactly the next representable version
     * @throws DomainValidationException when the current version is already {@link Long#MAX_VALUE}
     */
    public PartyIdentifierVersion next() {
        if (value == Long.MAX_VALUE) {
            throw new DomainValidationException(DomainViolation.PARTY_IDENTIFIER_VERSION_OVERFLOW,
                    "Identifier version cannot advance beyond its maximum value");
        }
        return new PartyIdentifierVersion(value + 1);
    }
}
