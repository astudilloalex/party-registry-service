package com.alexastudillo.partyregistry.adapter.out.postgresql;

import java.time.Duration;

/** Bounded, non-secret operational settings supplied by runtime composition. */
public record PostgreSqlAdapterSettings(
        Duration outboxClaimLease,
        int maximumOutboxBatchSize,
        int maximumAssociationResults,
        String publisherActorId) {

    public PostgreSqlAdapterSettings {
        if (outboxClaimLease == null || outboxClaimLease.isZero() || outboxClaimLease.isNegative()) {
            throw new IllegalArgumentException("outboxClaimLease must be positive");
        }
        if (maximumOutboxBatchSize < 1 || maximumAssociationResults < 1) {
            throw new IllegalArgumentException("PostgreSQL adapter bounds must be positive");
        }
        if (publisherActorId == null || publisherActorId.isBlank() || publisherActorId.length() > 128) {
            throw new IllegalArgumentException("publisherActorId must be a non-blank value of at most 128 characters");
        }
    }
}
