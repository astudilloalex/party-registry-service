package com.alexastudillo.partyregistry.api.model.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a legal entity and the safe identifier created with it.
 */
public record LegalEntityCreateResponse(
        UUID partyId,
        String type,
        String displayName,
        String recordStatus,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        LegalEntityDetailsResponse legalEntityDetails,
        InitialPartyIdentifierResponse initialIdentifier) {
}
