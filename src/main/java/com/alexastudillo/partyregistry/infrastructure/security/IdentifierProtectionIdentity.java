package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/** Supplies actual stored owner/version AAD identity without fabricating a plaintext registration request. */
public record IdentifierProtectionIdentity(TenantId tenantId, PartyId partyId, PartyIdentifierId identifierId,
        IdentifierSchemeId schemeId, IdentifierRuleVersion normalizationVersion, int encryptionKeyVersion) {
    public IdentifierProtectionIdentity {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(identifierId, "identifierId");
        Objects.requireNonNull(schemeId, "schemeId");
        Objects.requireNonNull(normalizationVersion, "normalizationVersion");
        if (encryptionKeyVersion < 1) {
            throw new IllegalArgumentException("Stored identifier key version is invalid");
        }
    }

    @Override
    public String toString() {
        return "IdentifierProtectionIdentity[redacted]";
    }
}
