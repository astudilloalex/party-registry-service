package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/** Binds continuation intent to request tenant and exact filters without implying catalog ownership. */
public record IdentifierSchemeSearchScope(TenantId tenantId, IdentifierSchemeSearchCriteria criteria) {
    public IdentifierSchemeSearchScope {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(criteria, "criteria");
    }
}
