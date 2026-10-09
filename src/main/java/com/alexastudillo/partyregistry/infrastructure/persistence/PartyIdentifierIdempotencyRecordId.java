package com.alexastudillo.partyregistry.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identifies an identifier-only completion by its exact tenant/operation/key tuple. */
@Embeddable
public class PartyIdentifierIdempotencyRecordId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "operation", nullable = false, length = 64)
    private String operation;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    protected PartyIdentifierIdempotencyRecordId() { }

    PartyIdentifierIdempotencyRecordId(UUID tenantId, String operation, String idempotencyKey) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.operation = Objects.requireNonNull(operation, "operation");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.codePointCount(0, idempotencyKey.length()) > 128) {
            throw new IllegalArgumentException("Identifier replay key is invalid");
        }
        this.idempotencyKey = idempotencyKey;
    }

    UUID tenantId() { return tenantId; }
    String operation() { return operation; }
    String idempotencyKey() { return idempotencyKey; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PartyIdentifierIdempotencyRecordId that
                && Objects.equals(tenantId, that.tenantId) && Objects.equals(operation, that.operation)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() { return Objects.hash(tenantId, operation, idempotencyKey); }

    @Override
    public String toString() { return "PartyIdentifierIdempotencyRecordId[key=<redacted>]"; }
}
