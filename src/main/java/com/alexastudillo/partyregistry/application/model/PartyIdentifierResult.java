package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Exposes the safe masked projection of one PartyIdentifier.
 */
public record PartyIdentifierResult(
        PartyIdentifierId identifierId,
        PartyId partyId,
        IdentifierSchemeId identifierSchemeId,
        String schemeCode,
        String maskedValue,
        PartyIdentifierStatus status,
        boolean isPrimary,
        @Nullable String issuerCode,
        @Nullable LocalDate issuedOn,
        @Nullable LocalDate expiresOn,
        @Nullable Instant verifiedAt,
        @Nullable String verifiedBy,
        PartyIdentifierVersion version,
        Instant createdAt,
        Instant updatedAt) {

    public PartyIdentifierResult {
        Objects.requireNonNull(identifierId, "identifierId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(identifierSchemeId, "identifierSchemeId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (schemeCode == null || schemeCode.isBlank()) {
            throw new IllegalArgumentException("Identifier scheme code is required");
        }
        if (maskedValue == null || maskedValue.isBlank()) {
            throw new IllegalArgumentException("Masked identifier value is required");
        }
    }

    /**
     * Projects an identifier aggregate without copying protected persistence material.
     *
     * @param identifier aggregate to project
     * @param scheme resolved scheme associated with the identifier
     * @return the safe identifier result
     */
    public static PartyIdentifierResult fromAggregate(
            PartyIdentifier identifier,
            IdentifierScheme scheme) {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(scheme, "scheme");
        if (!identifier.identifierSchemeId().equals(scheme.id())) {
            throw new IllegalArgumentException("Identifier and scheme identities do not match");
        }
        return new PartyIdentifierResult(
                identifier.identifierId(),
                identifier.partyId(),
                identifier.identifierSchemeId(),
                scheme.code(),
                identifier.protectedValue().maskedValue(),
                identifier.status(),
                identifier.isPrimary(),
                identifier.issuerCode(),
                identifier.issuedOn(),
                identifier.expiresOn(),
                identifier.verifiedAt(),
                identifier.verifiedBy(),
                identifier.version(),
                identifier.auditInfo().createdAt(),
                identifier.auditInfo().updatedAt());
    }
}
