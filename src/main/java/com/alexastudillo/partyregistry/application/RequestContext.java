package com.alexastudillo.partyregistry.application;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record RequestContext(UUID tenantId, String userId, UUID processId) {
    public RequestContext(String tenantId, String userId, String processId, Supplier<UUID> processIds) {
        this(canonicalUuid(tenantId, "tenant-id"), validUser(userId),
                processId == null ? generated(processIds) : canonicalUuid(processId, "process-id"));
    }

    public RequestContext {
        Objects.requireNonNull(tenantId, "tenantId");
        validUser(userId);
        Objects.requireNonNull(processId, "processId");
    }

    private static UUID canonicalUuid(String raw, String field) {
        if (raw == null) {
            throw validation(field + " is required");
        }
        try {
            UUID parsed = UUID.fromString(raw);
            if (!parsed.toString().equals(raw)) {
                throw validation(field + " must be a canonical lowercase UUID");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw validation(field + " must be a canonical lowercase UUID");
        }
    }

    private static String validUser(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw validation("user-id must contain between 1 and 128 characters");
        }
        return value;
    }

    private static UUID generated(Supplier<UUID> processIds) {
        UUID generated = Objects.requireNonNull(processIds, "processIds").get();
        if (generated == null) {
            throw validation("process-id generation failed");
        }
        return generated;
    }

    private static ApplicationFailure validation(String message) {
        return new ApplicationFailure("VALIDATION_ERROR", message);
    }
}
