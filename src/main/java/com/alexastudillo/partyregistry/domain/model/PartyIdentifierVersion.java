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
}
