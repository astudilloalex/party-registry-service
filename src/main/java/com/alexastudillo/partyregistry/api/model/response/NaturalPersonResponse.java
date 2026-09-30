package com.alexastudillo.partyregistry.api.model.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents one complete natural person in successful API responses.
 */
public record NaturalPersonResponse(
        UUID partyId,
        String type,
        String displayName,
        String recordStatus,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        NaturalPersonDetailsResponse naturalPersonDetails) {
}
