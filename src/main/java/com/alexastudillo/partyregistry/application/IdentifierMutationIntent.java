package com.alexastudillo.partyregistry.application;

import java.util.Objects;
import java.util.UUID;

public record IdentifierMutationIntent(
        UUID tenantId,
        UUID identifierId,
        UUID partyId,
        UUID schemeId,
        ProtectedIdentifierData protectedIdentifier,
        long expectedVersion,
        Object mutation,
        String actorId,
        OutboxIntent outbox) {
    public IdentifierMutationIntent {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(schemeId, "schemeId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(outbox, "outbox");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
    }
}
