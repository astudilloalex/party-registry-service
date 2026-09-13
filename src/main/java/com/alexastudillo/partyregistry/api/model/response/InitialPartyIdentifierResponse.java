package com.alexastudillo.partyregistry.api.model.response;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents the safe pending identifier created atomically with a Party.
 */
public record InitialPartyIdentifierResponse(
        UUID identifierId,
        UUID partyId,
        UUID identifierSchemeId,
        String schemeCode,
        String maskedValue,
        String status,
        boolean isPrimary,
        @Nullable String issuerCode,
        @Nullable LocalDate issuedOn,
        @Nullable LocalDate expiresOn,
        @Nullable Instant verifiedAt,
        @Nullable String verifiedBy,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
