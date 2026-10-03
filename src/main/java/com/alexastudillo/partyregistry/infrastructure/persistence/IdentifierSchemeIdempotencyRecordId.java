package com.alexastudillo.partyregistry.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identifies a completed scheme operation by its tenant, operation namespace, and exact client key. */
@Embeddable
public class IdentifierSchemeIdempotencyRecordId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "operation", nullable = false, length = 64)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    protected IdentifierSchemeIdempotencyRecordId() {
    }

    IdentifierSchemeIdempotencyRecordId(UUID tenantId, String operation, String idempotencyKey) {
        this.tenantId = tenantId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
    }

    UUID tenantId() {
        return tenantId;
    }

    String operation() {
        return operation;
    }

    String idempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof IdentifierSchemeIdempotencyRecordId that
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(operation, that.operation)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, operation, idempotencyKey);
    }
}
