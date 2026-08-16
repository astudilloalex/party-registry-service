package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.PartyStatus;
import com.alexastudillo.partyregistry.domain.PartyType;
import java.util.Objects;
import java.util.UUID;

public record PartyView(UUID id, UUID tenantId, PartyType type, String displayName, PartyStatus status, long version) {
    public PartyView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        if (displayName == null || displayName.isBlank() || version < 0) {
            throw new IllegalArgumentException("Invalid Party view");
        }
    }
}
