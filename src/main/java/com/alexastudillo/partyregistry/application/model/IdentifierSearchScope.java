package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;

import java.util.Objects;

/** Defines effective tenant/scheme/normalization/filter/limit cursor scope without user, process or key material. */
public record IdentifierSearchScope(TenantId tenantId, IdentifierSchemeId schemeId, String schemeCode,
        IdentifierSearchSelector selector, IdentifierSearchFilters filters, int limit) {
    public IdentifierSearchScope {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(schemeId, "schemeId");
        Objects.requireNonNull(schemeCode, "schemeCode");
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(filters, "filters");
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Identifier search page limit is out of range");
        }
    }

    @Override
    public String toString() {
        return "IdentifierSearchScope[redacted]";
    }
}
