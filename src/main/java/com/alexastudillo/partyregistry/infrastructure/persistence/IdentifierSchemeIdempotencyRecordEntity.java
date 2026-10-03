package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Maps completed scheme operations, their explicit snapshot versions, and original acceptance attribution. */
@Entity
@Table(name = "identifier_scheme_idempotency_records")
public class IdentifierSchemeIdempotencyRecordEntity {

    @EmbeddedId
    private IdentifierSchemeIdempotencyRecordId id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, columnDefinition = "char(64)")
    private String requestHash;

    @Column(name = "identifier_scheme_id", nullable = false)
    private UUID identifierSchemeId;

    @Column(name = "result_snapshot_schema_version", nullable = false)
    private short resultSnapshotSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode resultSnapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    protected IdentifierSchemeIdempotencyRecordEntity() {
    }

    IdentifierSchemeIdempotencyRecordEntity(
            IdentifierSchemeIdempotencyRecordId id,
            String requestHash,
            UUID identifierSchemeId,
            short resultSnapshotSchemaVersion,
            JsonNode resultSnapshot,
            Instant createdAt,
            String createdBy) {
        this.id = id;
        this.requestHash = requestHash;
        this.identifierSchemeId = identifierSchemeId;
        this.resultSnapshotSchemaVersion = resultSnapshotSchemaVersion;
        this.resultSnapshot = resultSnapshot.deepCopy();
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    IdentifierSchemeIdempotencyRecordId id() {
        return id;
    }

    String requestHash() {
        return requestHash;
    }

    UUID identifierSchemeId() {
        return identifierSchemeId;
    }

    short resultSnapshotSchemaVersion() {
        return resultSnapshotSchemaVersion;
    }

    /** Returns a detached tree so codec inspection cannot dirty the stored completion. */
    JsonNode resultSnapshot() {
        return resultSnapshot.deepCopy();
    }

    Instant createdAt() {
        return createdAt;
    }

    String createdBy() {
        return createdBy;
    }
}
