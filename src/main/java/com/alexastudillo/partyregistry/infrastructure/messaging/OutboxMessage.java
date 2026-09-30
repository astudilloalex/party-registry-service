package com.alexastudillo.partyregistry.infrastructure.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the safe immutable event envelope sent to the message broker.
 */
public record OutboxMessage(
        UUID eventId,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        short eventSchemaVersion,
        Map<String, String> payload,
        Instant occurredAt,
        String correlationId,
        String causationId) {

    public OutboxMessage {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("Outbox aggregate type is required");
        }
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("Outbox aggregate version cannot be negative");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Outbox event type is required");
        }
        if (eventSchemaVersion <= 0) {
            throw new IllegalArgumentException("Outbox event schema version must be positive");
        }
        payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
    }
}
