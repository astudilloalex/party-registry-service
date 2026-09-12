package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/**
 * Supplies transient identifier material and authenticated identities for protection.
 */
public record IdentifierProtectionRequest(
        TenantId tenantId,
        PartyId partyId,
        PartyIdentifierId identifierId,
        IdentifierSchemeId identifierSchemeId,
        String completeValue,
        String normalizedValue,
        IdentifierRuleVersion normalizationVersion) {

    public IdentifierProtectionRequest {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(identifierId, "identifierId");
        Objects.requireNonNull(identifierSchemeId, "identifierSchemeId");
        Objects.requireNonNull(normalizationVersion, "normalizationVersion");
        if (completeValue == null || completeValue.isBlank()) {
            throw new IllegalArgumentException("Complete identifier value is required");
        }
        if (normalizedValue == null || normalizedValue.isBlank()) {
            throw new IllegalArgumentException("Normalized identifier value is required");
        }
    }

    @Override
    public String toString() {
        return "IdentifierProtectionRequest[tenantId=" + tenantId
                + ", partyId=" + partyId
                + ", identifierId=" + identifierId
                + ", identifierSchemeId=" + identifierSchemeId
                + ", completeValue=<redacted>, normalizedValue=<redacted>"
                + ", normalizationVersion=" + normalizationVersion + "]";
    }
}
