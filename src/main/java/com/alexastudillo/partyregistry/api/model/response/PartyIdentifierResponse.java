package com.alexastudillo.partyregistry.api.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents the safe public projection of an official Party identifier.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
public record PartyIdentifierResponse(
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
