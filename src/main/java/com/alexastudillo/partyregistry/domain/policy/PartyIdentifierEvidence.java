package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;

/**
 * Associates an independent PartyIdentifier with the scheme used to assess activation eligibility.
 */
public record PartyIdentifierEvidence(
        PartyIdentifier identifier,
        IdentifierScheme scheme) {

    public PartyIdentifierEvidence {
        if (identifier == null) {
            throw new DomainValidationException(
                    DomainViolation.PARTY_IDENTIFIER_REQUIRED,
                    "PartyIdentifier evidence is required");
        }
        if (scheme == null) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_SCHEME_UNKNOWN,
                    "Identifier scheme evidence is required");
        }
    }
}
