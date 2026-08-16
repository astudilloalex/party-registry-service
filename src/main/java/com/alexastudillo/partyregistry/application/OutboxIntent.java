package com.alexastudillo.partyregistry.application;

import java.util.Objects;
import java.util.UUID;

public record OutboxIntent(String eventType, UUID correlationId) {
    public OutboxIntent {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        Objects.requireNonNull(correlationId, "correlationId");
    }
}
