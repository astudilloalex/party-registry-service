package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyType;

import java.util.List;
import java.util.Objects;

/**
 * Carries one protected additional identifier for independent atomic registration.
 */
public record PartyIdentifierRegistrationCandidate(
        RequestMetadata requestMetadata,
        String idempotencyKey,
        PartyType partyType,
        PartyIdentifier identifier,
        IdentifierScheme identifierScheme,
        List<OutboxEventCandidate> outboxCandidates) {

    public PartyIdentifierRegistrationCandidate {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        Objects.requireNonNull(partyType, "partyType");
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(identifierScheme, "identifierScheme");
        outboxCandidates = List.copyOf(outboxCandidates);
        if (!identifier.identifierSchemeId().equals(identifierScheme.id())) {
            throw new IllegalArgumentException("Identifier and scheme identities do not match");
        }
    }

    @Override
    public String toString() {
        return "PartyIdentifierRegistrationCandidate[idempotencyKey=<redacted>, partyType=" + partyType
                + ", identifier=<redacted>, identifierScheme=" + identifierScheme
                + ", outboxCandidates=" + outboxCandidates + "]";
    }
}
