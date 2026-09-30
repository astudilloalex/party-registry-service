package com.alexastudillo.partyregistry.api.model.response;

import java.time.Instant;
import java.util.UUID;

/** Represents the legal-detail read/update contract without creation-only identifiers. */
public record LegalEntityResponse(
        UUID partyId,
        String type,
        String displayName,
        String recordStatus,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        LegalEntityDetailsResponse legalEntityDetails) {
}
