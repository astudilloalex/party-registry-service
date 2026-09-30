package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/** Identifies one tenant-owned nationality without including any transport-specific data. */
public record GetNationalityQuery(TenantId tenantId, PartyId partyId, NationalityId nationalityId) {

    public GetNationalityQuery {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(nationalityId, "nationalityId");
    }
}
