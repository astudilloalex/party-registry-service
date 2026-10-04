package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/** Binds authenticated identifier continuation to immutable tenant/Party ownership and every effective filter. */
public record PartyIdentifierSearchScope(TenantId tenantId, PartyId partyId, PartyIdentifierSearchCriteria criteria) {

    public PartyIdentifierSearchScope {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(criteria, "criteria");
    }
}
