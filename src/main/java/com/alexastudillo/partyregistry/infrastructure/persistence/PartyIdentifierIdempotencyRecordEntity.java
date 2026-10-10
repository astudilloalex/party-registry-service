package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** Maps immutable safe identifier completions and their authoritative qualified owner/acceptance metadata. */
@Entity
@Immutable
@Table(name = "party_identifier_idempotency_records")
public class PartyIdentifierIdempotencyRecordEntity {

    @EmbeddedId
    private PartyIdentifierIdempotencyRecordId id;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, columnDefinition = "char(64)")
    private String requestHash;
    @Column(name = "party_id", nullable = false)
    private UUID partyId;
    @Column(name = "identifier_id", nullable = false)
    private UUID identifierId;
    @Column(name = "result_snapshot_schema_version", nullable = false)
    private short resultSnapshotSchemaVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode resultSnapshot;
    @Column(name = "expected_identifier_version")
    private @Nullable Long expectedVersion;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    protected PartyIdentifierIdempotencyRecordEntity() { }

    PartyIdentifierIdempotencyRecordEntity(PartyIdentifierIdempotencyRecordId id,
            CompletedPartyIdentifierMutation completed, IdempotencyResultSnapshot snapshot) {
        if (!id.tenantId().equals(completed.tenantId().value()) || !id.operation().equals(completed.operation().namespace())) {
            throw new IllegalArgumentException("Identifier completion does not match its persistence scope");
        }
        this.id = id;
        requestHash = completed.requestFingerprint();
        partyId = completed.result().partyId().value();
        identifierId = completed.result().identifierId().value();
        resultSnapshotSchemaVersion = snapshot.schemaVersion();
        resultSnapshot = snapshot.payload().deepCopy();
        var expected = completed.expectedVersion();
        expectedVersion = expected == null ? null : expected.value();
        createdAt = completed.acceptedAt();
        createdBy = completed.acceptedBy();
    }

    PartyIdentifierIdempotencyRecordId id() { return id; }
    String requestHash() { return requestHash; }
    UUID partyId() { return partyId; }
    UUID identifierId() { return identifierId; }
    short resultSnapshotSchemaVersion() { return resultSnapshotSchemaVersion; }
    @Nullable Long expectedVersion() { return expectedVersion; }
    Instant createdAt() { return createdAt; }
    String createdBy() { return createdBy; }

    /** Returns detached JSON so validation cannot mutate accepted completion history. */
    JsonNode resultSnapshot() { return resultSnapshot.deepCopy(); }
}
