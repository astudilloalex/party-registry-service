package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

import java.util.UUID;

/**
 * Identifies one party aggregate.
 */
public record PartyId(UUID value) {

    public PartyId {
        if (value == null) {
            throw new DomainValidationException(
                    DomainViolation.PARTY_ID_REQUIRED,
                    "Party identifier is required");
        }
    }
}
