package com.alexastudillo.partyregistry.application;

import java.util.Objects;
import java.util.UUID;

public record PartyMutationIntent(
        UUID tenantId, UUID partyId, long expectedVersion, Object mutation, String actorId, OutboxIntent outbox) {
    public PartyMutationIntent {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(outbox, "outbox");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
    }
}
