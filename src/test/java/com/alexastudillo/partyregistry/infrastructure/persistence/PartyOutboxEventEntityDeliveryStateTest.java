package com.alexastudillo.partyregistry.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies attempt-stable lease, retry, publication, and terminal entity
 * transitions.
 */
class PartyOutboxEventEntityDeliveryStateTest {

    private static final Instant NOW = Instant.parse("2026-09-04T15:00:00Z");

    @Test
    void claimIncrementsAttemptsExactlyOnceAndInstallsAFiniteLease() {
        PartyOutboxEventEntity entity = pendingEntity(null, 0, null);
        Duration leaseDuration = Duration.ofSeconds(30);

        entity.claimForPublication(NOW, leaseDuration, "outbox-publisher");

        assertEquals(PartyOutboxStatus.PENDING, entity.status());
        assertEquals(1, entity.publishAttempts());
        assertEquals(NOW, entity.lastAttemptAt());
        assertEquals(NOW.plusSeconds(30), entity.nextAttemptAt());
        assertEquals(NOW, entity.updatedAt());
        assertEquals("outbox-publisher", entity.updatedBy());
        assertNull(entity.lastErrorDetail());
        assertThrows(
                IllegalStateException.class,
                () -> entity.claimForPublication(NOW, leaseDuration, "outbox-publisher"));
    }

    @Test
    void retryKeepsPendingAndDoesNotIncrementTheClaimAttemptAgain() {
        PartyOutboxEventEntity entity = pendingEntity(null, 2, "old-code");
        entity.claimForPublication(NOW, Duration.ofSeconds(30), "outbox-publisher");

        entity.scheduleRetry(
                3,
                NOW.plusSeconds(1),
                NOW.plusSeconds(9),
                "broker-unavailable",
                "outbox-publisher");

        assertEquals(PartyOutboxStatus.PENDING, entity.status());
        assertEquals(3, entity.publishAttempts());
        assertEquals(NOW.plusSeconds(9), entity.nextAttemptAt());
        assertEquals("broker-unavailable", entity.lastErrorCode());
        assertNull(entity.lastErrorDetail());
        assertNull(entity.publishedAt());
    }

    @Test
    void acknowledgedClaimBecomesPublishedAndCannotTransitionAgain() {
        PartyOutboxEventEntity entity = pendingEntity(null, 0, null);
        entity.claimForPublication(NOW, Duration.ofSeconds(30), "outbox-publisher");

        Instant publishedAt = NOW.plusSeconds(1);
        entity.markPublished(1, publishedAt, "outbox-publisher");

        assertEquals(PartyOutboxStatus.PUBLISHED, entity.status());
        assertEquals(publishedAt, entity.publishedAt());
        assertNull(entity.nextAttemptAt());
        assertNull(entity.lastErrorCode());
        Instant failedAt = NOW.plusSeconds(2);
        assertThrows(
                IllegalStateException.class,
                () -> entity.markFailed(
                        1,
                        failedAt,
                        "broker-unavailable",
                        "outbox-publisher"));
    }

    @Test
    void terminalFailureStoresOnlyABoundedStableCode() {
        PartyOutboxEventEntity entity = pendingEntity(null, 4, "broker-unavailable");
        entity.claimForPublication(NOW, Duration.ofSeconds(30), "outbox-publisher");

        entity.markFailed(5, NOW.plusSeconds(1), "broker-timeout", "outbox-publisher");

        assertEquals(PartyOutboxStatus.FAILED, entity.status());
        assertEquals(5, entity.publishAttempts());
        assertEquals("broker-timeout", entity.lastErrorCode());
        assertNull(entity.lastErrorDetail());
        assertNull(entity.nextAttemptAt());
        assertNull(entity.publishedAt());
    }

    @Test
    void rejectsFutureClaimsStaleAttemptsAndUnboundedErrorCodes() {
        PartyOutboxEventEntity leased = pendingEntity(NOW.plusSeconds(1), 1, null);
        Duration leaseDuration = Duration.ofSeconds(30);
        assertThrows(
                IllegalStateException.class,
                () -> leased.claimForPublication(NOW, leaseDuration, "outbox-publisher"));

        PartyOutboxEventEntity claimed = pendingEntity(null, 0, null);
        claimed.claimForPublication(NOW, leaseDuration, "outbox-publisher");
        Instant publishedAt = NOW.plusSeconds(1);
        assertThrows(
                IllegalStateException.class,
                () -> claimed.markPublished(2, publishedAt, "outbox-publisher"));
        Instant failedAt = NOW.plusSeconds(1);
        String unboundedErrorCode = "x".repeat(65);
        assertThrows(
                IllegalArgumentException.class,
                () -> claimed.markFailed(1, failedAt, unboundedErrorCode, "outbox-publisher"));
    }

    private static PartyOutboxEventEntity pendingEntity(
            Instant nextAttemptAt,
            int publishAttempts,
            String lastErrorCode) {
        return PartyOutboxEventEntity.builder()
                .id(UUID.fromString("01991bd0-8000-7000-8000-000000000001"))
                .tenantId(UUID.fromString("01991bd0-8000-7000-8000-000000000002"))
                .aggregateType(PartyOutboxAggregateType.PARTY)
                .aggregateId(UUID.fromString("01991bd0-8000-7000-8000-000000000003"))
                .aggregateVersion(0)
                .eventType("party.created.v1")
                .eventSchemaVersion((short) 1)
                .payload(Map.of("partyType", "NATURAL_PERSON"))
                .occurredAt(NOW.minusSeconds(10))
                .correlationId("01991bd0-8000-7000-8000-000000000004")
                .status(PartyOutboxStatus.PENDING)
                .publishAttempts(publishAttempts)
                .nextAttemptAt(nextAttemptAt)
                .lastErrorCode(lastErrorCode)
                .lastErrorDetail("detail-that-must-be-cleared")
                .createdAt(NOW.minusSeconds(10))
                .createdBy("creator")
                .updatedAt(NOW.minusSeconds(10))
                .updatedBy("creator")
                .version(0)
                .build();
    }
}
