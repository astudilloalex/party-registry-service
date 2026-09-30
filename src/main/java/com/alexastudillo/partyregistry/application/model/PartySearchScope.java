package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/**
 * Binds continuation to the trusted tenant and all effective filters, including page size.
 */
public record PartySearchScope(TenantId tenantId, PartySearchCriteria criteria) {

    public PartySearchScope {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(criteria, "criteria");
    }
}
