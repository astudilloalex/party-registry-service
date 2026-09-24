package com.alexastudillo.partyregistry.api.model.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a natural person and the safe identifier created with it.
 */
public record NaturalPersonCreateResponse(
        UUID partyId,
        String type,
        String displayName,
        String recordStatus,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        NaturalPersonDetailsResponse naturalPersonDetails,
        InitialPartyIdentifierResponse initialIdentifier) {
}
