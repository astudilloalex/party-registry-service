package com.alexastudillo.partyregistry.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxClaim(
        UUID eventId,
        UUID tenantId,
        long claimedVersion,
        String eventType,
        int schemaVersion,
        String payload,
        Instant occurredAt) {
    public OutboxClaim {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (claimedVersion < 0 || schemaVersion < 1) {
            throw new IllegalArgumentException("Invalid outbox claim version");
        }
    }
}
