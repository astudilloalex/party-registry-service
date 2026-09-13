package com.alexastudillo.partyregistry.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps safe event payloads and delivery metadata in the Party outbox.
 */
@Entity
@Table(name = "party_outbox_events")
public class PartyOutboxEventEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "aggregate_type", nullable = false, columnDefinition = "party_outbox_aggregate_type")
    private PartyOutboxAggregateType aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "event_schema_version", nullable = false)
    private short eventSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "causation_id", length = 128)
    private String causationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "outbox_status")
    private PartyOutboxStatus status;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_detail", length = 1000)
    private String lastErrorDetail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PartyOutboxEventEntity() {
    }

    static Builder builder() {
        return new Builder();
    }

    private PartyOutboxEventEntity(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.aggregateType = builder.aggregateType;
        this.aggregateId = builder.aggregateId;
        this.aggregateVersion = builder.aggregateVersion;
        this.eventType = builder.eventType;
        this.eventSchemaVersion = builder.eventSchemaVersion;
        this.payload = builder.payload == null ? Map.of() : Map.copyOf(builder.payload);
        this.occurredAt = builder.occurredAt;
        this.correlationId = builder.correlationId;
        this.causationId = builder.causationId;
        this.status = builder.status;
        this.publishAttempts = builder.publishAttempts;
        this.nextAttemptAt = builder.nextAttemptAt;
        this.lastAttemptAt = builder.lastAttemptAt;
        this.publishedAt = builder.publishedAt;
        this.lastErrorCode = builder.lastErrorCode;
        this.lastErrorDetail = builder.lastErrorDetail;
        this.createdAt = builder.createdAt;
        this.createdBy = builder.createdBy;
        this.updatedAt = builder.updatedAt;
        this.updatedBy = builder.updatedBy;
        this.version = builder.version;
    }

    /**
     * Builds instances of {@link PartyOutboxEventEntity} with explicit or default values.
     */
    static final class Builder {
        private UUID id;
        private UUID tenantId;
        private PartyOutboxAggregateType aggregateType;
        private UUID aggregateId;
        private long aggregateVersion;
        private String eventType;
        private short eventSchemaVersion;
        private Map<String, String> payload = Map.of();
        private Instant occurredAt;
        private String correlationId;
        private String causationId;
        private PartyOutboxStatus status = PartyOutboxStatus.PENDING;
        private int publishAttempts;
        private Instant nextAttemptAt;
        private Instant lastAttemptAt;
        private Instant publishedAt;
        private String lastErrorCode;
        private String lastErrorDetail;
        private Instant createdAt;
        private String createdBy;
        private Instant updatedAt;
        private String updatedBy;
        private long version;

        private Builder() {
        }

        Builder id(UUID id) {
            this.id = id;
            return this;
        }

        Builder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        Builder aggregateType(PartyOutboxAggregateType aggregateType) {
            this.aggregateType = aggregateType;
            return this;
        }

        Builder aggregateId(UUID aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        Builder aggregateVersion(long aggregateVersion) {
            this.aggregateVersion = aggregateVersion;
            return this;
        }

        Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        Builder eventSchemaVersion(short eventSchemaVersion) {
            this.eventSchemaVersion = eventSchemaVersion;
            return this;
        }

        Builder payload(Map<String, String> payload) {
            this.payload = payload;
            return this;
        }

        Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        Builder causationId(String causationId) {
            this.causationId = causationId;
            return this;
        }

        Builder status(PartyOutboxStatus status) {
            this.status = status;
            return this;
        }

        Builder publishAttempts(int publishAttempts) {
            this.publishAttempts = publishAttempts;
            return this;
        }

        Builder nextAttemptAt(Instant nextAttemptAt) {
            this.nextAttemptAt = nextAttemptAt;
            return this;
        }

        Builder lastAttemptAt(Instant lastAttemptAt) {
            this.lastAttemptAt = lastAttemptAt;
            return this;
        }

        Builder publishedAt(Instant publishedAt) {
            this.publishedAt = publishedAt;
            return this;
        }

        Builder lastErrorCode(String lastErrorCode) {
            this.lastErrorCode = lastErrorCode;
            return this;
        }

        Builder lastErrorDetail(String lastErrorDetail) {
            this.lastErrorDetail = lastErrorDetail;
            return this;
        }

        Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        Builder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        Builder version(long version) {
            this.version = version;
            return this;
        }

        PartyOutboxEventEntity build() {
            return new PartyOutboxEventEntity(this);
        }
    }

    UUID id() {
        return id;
    }

    UUID tenantId() {
        return tenantId;
    }

    PartyOutboxAggregateType aggregateType() {
        return aggregateType;
    }

    UUID aggregateId() {
        return aggregateId;
    }

    long aggregateVersion() {
        return aggregateVersion;
    }

    String eventType() {
        return eventType;
    }

    short eventSchemaVersion() {
        return eventSchemaVersion;
    }

    Map<String, String> payload() {
        return Map.copyOf(payload);
    }

    Instant occurredAt() {
        return occurredAt;
    }

    String correlationId() {
        return correlationId;
    }

    String causationId() {
        return causationId;
    }

    PartyOutboxStatus status() {
        return status;
    }

    int publishAttempts() {
        return publishAttempts;
    }

    Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    Instant lastAttemptAt() {
        return lastAttemptAt;
    }

    Instant publishedAt() {
        return publishedAt;
    }

    String lastErrorCode() {
        return lastErrorCode;
    }

    String lastErrorDetail() {
        return lastErrorDetail;
    }

    Instant createdAt() {
        return createdAt;
    }

    String createdBy() {
        return createdBy;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    String updatedBy() {
        return updatedBy;
    }

    long version() {
        return version;
    }

    void claimForPublication(Instant claimedAt, Duration lease, String publisherId) {
        Objects.requireNonNull(claimedAt, "claimedAt");
        Objects.requireNonNull(lease, "lease");
        requirePublisherId(publisherId);
        if (status != PartyOutboxStatus.PENDING
                || nextAttemptAt != null && nextAttemptAt.isAfter(claimedAt)) {
            throw new IllegalStateException("Only due pending outbox events can be claimed");
        }
        if (lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("Outbox claim lease must be positive");
        }
        publishAttempts = Math.incrementExact(publishAttempts);
        lastAttemptAt = claimedAt;
        nextAttemptAt = claimedAt.plus(lease);
        lastErrorDetail = null;
        updatedAt = claimedAt;
        updatedBy = publisherId;
    }

    void markPublished(int claimedAttempt, Instant acknowledgedAt, String publisherId) {
        requireCurrentClaim(claimedAttempt);
        Objects.requireNonNull(acknowledgedAt, "acknowledgedAt");
        requirePublisherId(publisherId);
        status = PartyOutboxStatus.PUBLISHED;
        nextAttemptAt = null;
        publishedAt = acknowledgedAt;
        lastErrorCode = null;
        lastErrorDetail = null;
        updatedAt = acknowledgedAt;
        updatedBy = publisherId;
    }

    void scheduleRetry(
            int claimedAttempt,
            Instant failedAt,
            Instant retryAt,
            String errorCode,
            String publisherId) {
        requireCurrentClaim(claimedAttempt);
        Objects.requireNonNull(failedAt, "failedAt");
        Objects.requireNonNull(retryAt, "retryAt");
        requireErrorCode(errorCode);
        requirePublisherId(publisherId);
        if (!retryAt.isAfter(failedAt)) {
            throw new IllegalArgumentException("Outbox retry time must be after the failure time");
        }
        nextAttemptAt = retryAt;
        publishedAt = null;
        lastErrorCode = errorCode;
        lastErrorDetail = null;
        updatedAt = failedAt;
        updatedBy = publisherId;
    }

    void markFailed(
            int claimedAttempt,
            Instant failedAt,
            String errorCode,
            String publisherId) {
        requireCurrentClaim(claimedAttempt);
        Objects.requireNonNull(failedAt, "failedAt");
        requireErrorCode(errorCode);
        requirePublisherId(publisherId);
        status = PartyOutboxStatus.FAILED;
        nextAttemptAt = null;
        publishedAt = null;
        lastErrorCode = errorCode;
        lastErrorDetail = null;
        updatedAt = failedAt;
        updatedBy = publisherId;
    }

    private void requireCurrentClaim(int claimedAttempt) {
        if (status != PartyOutboxStatus.PENDING
                || claimedAttempt <= 0
                || publishAttempts != claimedAttempt) {
            throw new IllegalStateException("Outbox delivery transition does not match the current claim");
        }
    }

    private static void requireErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank() || errorCode.length() > 64) {
            throw new IllegalArgumentException("A bounded outbox error code is required");
        }
    }

    private static void requirePublisherId(String publisherId) {
        if (publisherId == null || publisherId.isBlank() || publisherId.length() > 128) {
            throw new IllegalArgumentException("A bounded outbox publisher ID is required");
        }
    }
}
