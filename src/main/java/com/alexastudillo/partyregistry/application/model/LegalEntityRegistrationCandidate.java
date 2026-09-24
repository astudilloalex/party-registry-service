package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.application.command.RegisterLegalEntityCommand;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;

import java.util.List;
import java.util.Objects;

/**
 * Carries a legal entity and independent initial identifier for atomic
 * registration.
 */
public record LegalEntityRegistrationCandidate(
        RequestMetadata requestMetadata,
        String idempotencyKey,
        String registrationFingerprint,
        LegalEntity party,
        IdentifierScheme identifierScheme,
        PartyIdentifier initialIdentifier,
        List<OutboxEventCandidate> outboxCandidates) implements PartyRegistrationCandidate {

    public LegalEntityRegistrationCandidate {
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
        return RegisterLegalEntityCommand.OPERATION_NAME;
    }

    @Override
    public String toString() {
        return "LegalEntityRegistrationCandidate[idempotencyKey=<redacted>, registrationFingerprint=<redacted>"
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
            LegalEntity party,
            IdentifierScheme scheme,
            PartyIdentifier identifier) {
        if (!party.tenantId().equals(identifier.tenantId())
                || !party.partyId().equals(identifier.partyId())
                || !scheme.id().equals(identifier.identifierSchemeId())) {
            throw new IllegalArgumentException("Registration aggregate identities do not match");
        }
    }
}
