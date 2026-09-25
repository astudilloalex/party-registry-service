package com.alexastudillo.partyregistry.api.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Exposes only the approved nationality identity, stored designation, validity and audit fields. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NationalityResponse(
        UUID nationalityId, UUID partyId, String countryCode, boolean isPrimary,
        @Nullable LocalDate validFrom, @Nullable LocalDate validUntil,
        Instant createdAt, Instant updatedAt) {
}
