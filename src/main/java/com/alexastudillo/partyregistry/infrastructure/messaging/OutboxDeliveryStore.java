package com.alexastudillo.partyregistry.infrastructure.messaging;

import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Defines reactive lease and status transitions for infrastructure outbox delivery.
 */
public interface OutboxDeliveryStore {

    Uni<List<ClaimedOutboxEvent>> claimDue(int batchSize, Instant claimedAt, Duration lease);

    Uni<Void> markPublished(UUID eventId, int claimedAttempt, Instant acknowledgedAt);

    Uni<Void> scheduleRetry(
            UUID eventId,
            int claimedAttempt,
            Instant failedAt,
            Instant retryAt,
            String errorCode);

    Uni<Void> markFailed(UUID eventId, int claimedAttempt, Instant failedAt, String errorCode);
}
