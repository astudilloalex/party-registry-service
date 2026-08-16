package com.alexastudillo.partyregistry.domain;

import java.util.Objects;
import java.util.UUID;

public record PartyIdentity(UUID id, UUID tenantId, PartyType type, String displayName) {
    public PartyIdentity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(type, "type");
        requireText(displayName, "displayName");
    }

    public PartyIdentity withDisplayName(String newDisplayName) {
        return new PartyIdentity(id, tenantId, type, newDisplayName);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainViolation(field + " must not be blank");
        }
    }
}
