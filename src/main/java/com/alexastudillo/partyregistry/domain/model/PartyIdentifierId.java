package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

import java.util.UUID;

/**
 * Identifies one independently versioned PartyIdentifier aggregate.
 */
public record PartyIdentifierId(UUID value) {

    public PartyIdentifierId {
        if (value == null) {
            throw new DomainValidationException(
                    DomainViolation.PARTY_IDENTIFIER_ID_REQUIRED,
                    "Party identifier aggregate ID is required");
        }
    }
}
