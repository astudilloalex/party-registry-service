package com.alexastudillo.partyregistry.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps completed API idempotency records and their immutable replay snapshots.
 */
@Entity
@Table(name = "api_idempotency_records")
public class ApiIdempotencyRecordEntity {

    @EmbeddedId
    private ApiIdempotencyRecordId id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, columnDefinition = "char(64)")
    private String requestHash;

    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    @Column(name = "result_snapshot_schema_version", nullable = false)
    private short resultSnapshotSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", nullable = false, columnDefinition = "jsonb")
    private NaturalPersonResultSnapshot resultSnapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    protected ApiIdempotencyRecordEntity() {
    }

    ApiIdempotencyRecordEntity(
            ApiIdempotencyRecordId id,
            String requestHash,
            UUID partyId,
            short resultSnapshotSchemaVersion,
            NaturalPersonResultSnapshot resultSnapshot,
            Instant createdAt,
            String createdBy) {
        this.id = id;
        this.requestHash = requestHash;
        this.partyId = partyId;
        this.resultSnapshotSchemaVersion = resultSnapshotSchemaVersion;
        this.resultSnapshot = resultSnapshot;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    ApiIdempotencyRecordId id() {
        return id;
    }

    String requestHash() {
        return requestHash;
    }

    short resultSnapshotSchemaVersion() {
        return resultSnapshotSchemaVersion;
    }

    NaturalPersonResultSnapshot resultSnapshot() {
        return resultSnapshot;
    }
}
