package com.alexastudillo.partyregistry.domain;

import java.util.Objects;
import java.util.UUID;

public record PartyIdentifierIdentity(
        UUID id, UUID tenantId, UUID partyId, UUID schemeId, String normalizedValue, String issuerCode) {

    public PartyIdentifierIdentity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(schemeId, "schemeId");
        if (normalizedValue == null || normalizedValue.isEmpty()) {
            throw new DomainViolation("Normalized identifier value must not be empty");
        }
    }

    public PartyIdentifierIdentity withIssuerCode(String newIssuerCode) {
        return new PartyIdentifierIdentity(id, tenantId, partyId, schemeId, normalizedValue, newIssuerCode);
    }
}
