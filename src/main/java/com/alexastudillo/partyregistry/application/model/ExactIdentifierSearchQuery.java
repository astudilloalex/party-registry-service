package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/** Carries an operation-local complete-value selector without HTTP types or sensitive diagnostic output. */
public record ExactIdentifierSearchQuery(TenantId tenantId, String schemeCode, String value, IdentifierSearchFilters filters) {
    public ExactIdentifierSearchQuery {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(schemeCode, "schemeCode");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(filters, "filters");
    }

    @Override
    public String toString() {
        return "ExactIdentifierSearchQuery[redacted]";
    }
}
