package com.alexastudillo.partyregistry.infrastructure.messaging;

import java.util.Objects;

/**
 * Couples one safe broker message with the database attempt that owns its finite lease.
 */
public record ClaimedOutboxEvent(OutboxMessage message, int publishAttempt) {

    public ClaimedOutboxEvent {
        Objects.requireNonNull(message, "message");
        if (publishAttempt <= 0) {
            throw new IllegalArgumentException("Outbox publish attempt must be positive");
        }
    }
}
