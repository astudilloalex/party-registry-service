package com.alexastudillo.partyregistry.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record NationalityMutationIntent(
        UUID tenantId,
        UUID partyId,
        UUID nationalityId,
        String countryCode,
        LocalDate validFrom,
        LocalDate validUntil,
        CountryEvidence countryEvidence,
        long expectedVersion,
        String actorId,
        OutboxIntent outbox) {
    public NationalityMutationIntent {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(outbox, "outbox");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
    }
}
