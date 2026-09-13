package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/**
 * Supplies transient identifier material and authenticated identities for protection.
 * Registration workflows supply the scheme-normalized value for both encryption
 * plaintext and indexing; the original command value remains separate for idempotency.
 *
 * @param tenantId tenant identity for indexing and authenticated encryption
 * @param partyId owning Party identity for authenticated encryption
 * @param identifierId identifier identity for authenticated encryption
 * @param identifierSchemeId scheme identity for authenticated encryption
 * @param completeValue encryption plaintext to preserve verbatim, not necessarily the submitted value
 * @param normalizedValue scheme-normalized value used for the lookup hash and mask
 * @param normalizationVersion applied normalization rule version
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
