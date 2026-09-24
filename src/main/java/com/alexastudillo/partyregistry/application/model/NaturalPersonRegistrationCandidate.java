package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;

import java.util.List;
import java.util.Objects;

/**
 * Carries a natural person and independent initial identifier for atomic
 * registration.
 */
public record NaturalPersonRegistrationCandidate(
        RequestMetadata requestMetadata,
        String idempotencyKey,
        String registrationFingerprint,
        NaturalPerson party,
        IdentifierScheme identifierScheme,
        PartyIdentifier initialIdentifier,
        List<OutboxEventCandidate> outboxCandidates) implements PartyRegistrationCandidate {

    public NaturalPersonRegistrationCandidate {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        validateKeyAndFingerprint(idempotencyKey, registrationFingerprint);
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(identifierScheme, "identifierScheme");
        Objects.requireNonNull(initialIdentifier, "initialIdentifier");
        outboxCandidates = List.copyOf(outboxCandidates);
        validateAggregatePair(party, identifierScheme, initialIdentifier);
    }

    @Override
    public String operation() {
        return RegisterNaturalPersonCommand.OPERATION_NAME;
    }

    @Override
    public String toString() {
        return "NaturalPersonRegistrationCandidate[idempotencyKey=<redacted>, registrationFingerprint=<redacted>"
                + ", party=" + party + ", identifierScheme=" + identifierScheme
                + ", initialIdentifier=<redacted>, outboxCandidates=" + outboxCandidates + "]";
    }

    private static void validateKeyAndFingerprint(String key, String fingerprint) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        if (fingerprint == null || !fingerprint.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("Registration fingerprint must be lowercase hexadecimal HMAC-SHA-256");
        }
    }

    private static void validateAggregatePair(
            NaturalPerson party,
            IdentifierScheme scheme,
            PartyIdentifier identifier) {
        if (!party.tenantId().equals(identifier.tenantId())
                || !party.partyId().equals(identifier.partyId())
                || !scheme.id().equals(identifier.identifierSchemeId())) {
            throw new IllegalArgumentException("Registration aggregate identities do not match");
        }
    }
}
