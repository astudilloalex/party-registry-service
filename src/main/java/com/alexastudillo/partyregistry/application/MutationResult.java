package com.alexastudillo.partyregistry.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MutationResult(UUID resourceId, long version, UUID outboxEventId, Instant occurredAt) {
    public MutationResult {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(outboxEventId, "outboxEventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
    }
}
