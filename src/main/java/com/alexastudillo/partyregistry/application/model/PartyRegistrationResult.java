package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;

import java.util.Objects;

/**
 * Combines safe Party data with its initial masked identifier result.
 */
public record PartyRegistrationResult(
        PartyDetailsResult party,
        PartyIdentifierResult initialIdentifier,
        PartyRegistrationOutcome outcome) {

    public PartyRegistrationResult {
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(initialIdentifier, "initialIdentifier");
        Objects.requireNonNull(outcome, "outcome");
        if (!party.partyId().equals(initialIdentifier.partyId())) {
            throw new IllegalArgumentException("Party and initial identifier identities do not match");
        }
        if (initialIdentifier.status() != PartyIdentifierStatus.PENDING_VERIFICATION) {
            throw new IllegalArgumentException("Initial identifier must be pending verification");
        }
    }
}
