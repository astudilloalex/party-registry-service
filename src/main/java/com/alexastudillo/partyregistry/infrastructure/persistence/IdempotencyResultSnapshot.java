package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * Couples an idempotency snapshot payload with its separately persisted schema version.
 */
record IdempotencyResultSnapshot(short schemaVersion, JsonNode payload) {

    IdempotencyResultSnapshot {
        Objects.requireNonNull(payload, "payload");
        if (!payload.isObject()) {
            throw new IllegalArgumentException("Idempotency snapshot payload must be a JSON object");
        }
    }
}
