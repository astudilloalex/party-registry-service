package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Carries the validated trusted request context associated with one application
 * operation.
 */
public record RequestMetadata(TenantId tenantId, String userId, UUID processId) {

    private static final int MAX_USER_LENGTH = 128;

    public RequestMetadata {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(processId, "processId");
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User identifier is required");
        }
        if (userId.codePointCount(0, userId.length()) > MAX_USER_LENGTH) {
            throw new IllegalArgumentException("User identifier exceeds the maximum length");
        }
        if (userId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("User identifier contains control characters");
        }
    }
}
