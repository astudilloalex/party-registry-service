package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

import java.util.UUID;

/**
 * Identifies the tenant that owns a party aggregate.
 */
public record TenantId(UUID value) {

    public TenantId {
        if (value == null) {
            throw new DomainValidationException(
                    DomainViolation.TENANT_ID_REQUIRED,
                    "Tenant identifier is required");
        }
    }
}
