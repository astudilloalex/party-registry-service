package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Supplies only ownership, scheme identity, verification, expiration, and compatibility for activation.
 */
public record PartyActivationEvidence(
        TenantId tenantId,
        PartyId partyId,
        IdentifierSchemeId identifierSchemeId,
        IdentifierSchemeId schemeId,
        PartyIdentifierStatus status,
        @Nullable LocalDate expiresOn,
        IdentifierSubjectType applicableSubjectType) {

    public PartyActivationEvidence {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(identifierSchemeId, "identifierSchemeId");
        Objects.requireNonNull(schemeId, "schemeId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(applicableSubjectType, "applicableSubjectType");
    }
}
