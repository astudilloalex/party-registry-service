package com.alexastudillo.partyregistry.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record IdentifierUniquenessKey(UUID tenantId, UUID schemeId, String normalizedValueHash) {
    private static final Pattern HASH_FORMAT = Pattern.compile("[0-9A-F]{64}");

    public IdentifierUniquenessKey {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(schemeId, "schemeId");
        if (normalizedValueHash == null || !HASH_FORMAT.matcher(normalizedValueHash).matches()) {
            throw new DomainViolation("Normalized identifier hash must be exactly 64 uppercase hexadecimal characters");
        }
    }
}
