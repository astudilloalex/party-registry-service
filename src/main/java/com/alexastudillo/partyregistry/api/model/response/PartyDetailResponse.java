package com.alexastudillo.partyregistry.api.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents exhaustive type-specific Party details after lifecycle operations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartyDetailResponse(
        UUID partyId,
        String type,
        String displayName,
        String recordStatus,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        @Nullable NaturalPersonDetailsResponse naturalPersonDetails,
        @Nullable LegalEntityDetailsResponse legalEntityDetails) {
}
