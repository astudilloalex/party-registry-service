package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Carries a bounded prefix page request with absent-cursor semantics and fully redacted diagnostics. */
public record PrefixIdentifierSearchQuery(TenantId tenantId, String schemeCode, String prefix,
        IdentifierSearchFilters filters, int limit, @Nullable String cursor) {
    public static final int DEFAULT_LIMIT = 50;

    public PrefixIdentifierSearchQuery {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(schemeCode, "schemeCode");
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(filters, "filters");
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Identifier search page limit is out of range");
        }
        if (cursor != null && (cursor.isBlank() || cursor.length() > 256)) {
            throw new IllegalArgumentException("Identifier search cursor is invalid");
        }
    }

    /** Uses the declared default when the delivery boundary has established that limit was omitted. */
    public PrefixIdentifierSearchQuery(TenantId tenantId, String schemeCode, String prefix,
            IdentifierSearchFilters filters, @Nullable String cursor) {
        this(tenantId, schemeCode, prefix, filters, DEFAULT_LIMIT, cursor);
    }

    @Override
    public String toString() {
        return "PrefixIdentifierSearchQuery[redacted]";
    }
}
