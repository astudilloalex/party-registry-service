package com.alexastudillo.partyregistry.application;

import java.util.Objects;
import java.util.UUID;

public record IdentifierSchemeMetadata(
        UUID id,
        String code,
        String status,
        String applicableSubjectType,
        String normalizerKey,
        String validatorKey,
        Integer minimumLength,
        Integer maximumLength,
        boolean requiresExpiration) {
    public IdentifierSchemeMetadata {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(applicableSubjectType, "applicableSubjectType");
        Objects.requireNonNull(normalizerKey, "normalizerKey");
        Objects.requireNonNull(validatorKey, "validatorKey");
    }
}
