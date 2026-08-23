package com.alexastudillo.partyregistry.domain.party.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Party(
        UUID tenantId,
        PartyType type,
        String displayName,
        PartyRecordStatus recordStatus,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        long version,
        NaturalPersonDetails naturalPersonDetails,
        LegalEntityDetails legalEntityDetails,
        List<PartyNationality> nationalities) {
}
