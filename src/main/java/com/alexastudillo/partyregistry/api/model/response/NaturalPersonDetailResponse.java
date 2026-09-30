package com.alexastudillo.partyregistry.api.model.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents the GET-only natural-person detail with current masked identifiers.
 */
public record NaturalPersonDetailResponse(
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
        List<PartyIdentifierResponse> identifiers) {

    public NaturalPersonDetailResponse {
        identifiers = List.copyOf(identifiers);
    }
}
