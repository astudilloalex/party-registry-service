package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/** Binds a continuation to its tenant, Party, effective filters, date, and page size. */
public record NationalitySearchScope(TenantId tenantId, PartyId partyId, NationalitySearchCriteria criteria) {

    public NationalitySearchScope {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(criteria, "criteria");
    }
}
