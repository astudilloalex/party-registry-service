package com.alexastudillo.partyregistry.api.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** Exposes approved catalog configuration, lifecycle/version, and public timestamps without audit actors. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentifierSchemeResponse(
        UUID id, String code, String issuingCountryCode, String category, String applicableSubjectType,
        String name, @Nullable String description, String normalizerKey, String validatorKey,
        @Nullable Integer minimumLength, @Nullable Integer maximumLength, boolean requiresExpiration,
        String status, long version, Instant createdAt, Instant updatedAt) {
}
