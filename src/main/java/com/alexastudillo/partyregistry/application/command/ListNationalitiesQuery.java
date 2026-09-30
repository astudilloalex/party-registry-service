package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.NationalitySearchCriteria;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;
import java.util.Optional;

/** Carries the effective nationality search and an optional opaque continuation. */
public record ListNationalitiesQuery(
        TenantId tenantId, PartyId partyId, NationalitySearchCriteria criteria, Optional<String> cursor) {

    public ListNationalitiesQuery {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(cursor, "cursor");
    }
}
