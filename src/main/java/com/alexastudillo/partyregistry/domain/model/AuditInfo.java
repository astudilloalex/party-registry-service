package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

import java.time.Instant;

/**
 * Captures immutable creation audit values and the latest aggregate modification.
 */
public record AuditInfo(
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    private static final int MAX_USER_LENGTH = 128;

    public AuditInfo {
        requireTimestamp(createdAt);
        requireTimestamp(updatedAt);
        validateUser(createdBy);
        validateUser(updatedBy);
        if (updatedAt.isBefore(createdAt)) {
            throw new DomainValidationException(
                    DomainViolation.AUDIT_TIMESTAMP_ORDER,
                    "Audit update time cannot precede creation time");
        }
    }

    public static AuditInfo initial(Instant occurredAt, String userId) {
        return new AuditInfo(occurredAt, userId, occurredAt, userId);
    }

    /**
     * Preserves creation values and records a later modification.
     *
     * @param occurredAt modification timestamp
     * @param userId modifying user
     * @return updated audit information
     */
    public AuditInfo updated(Instant occurredAt, String userId) {
        requireTimestamp(occurredAt);
        if (occurredAt.isBefore(updatedAt)) {
            throw new DomainValidationException(
                    DomainViolation.AUDIT_TIMESTAMP_ORDER,
                    "Audit update time cannot move backwards");
        }
        return new AuditInfo(createdAt, createdBy, occurredAt, userId);
    }

    private static void requireTimestamp(Instant timestamp) {
        if (timestamp == null) {
            throw new DomainValidationException(
                    DomainViolation.AUDIT_TIMESTAMP_REQUIRED,
                    "Audit timestamp is required");
        }
    }

    private static void validateUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new DomainValidationException(
                    DomainViolation.AUDIT_USER_REQUIRED,
                    "Audit user is required");
        }
        if (userId.codePointCount(0, userId.length()) > MAX_USER_LENGTH) {
            throw new DomainValidationException(
                    DomainViolation.AUDIT_USER_TOO_LONG,
                    "Audit user exceeds the maximum length");
        }
        if (userId.chars().anyMatch(Character::isISOControl)) {
            throw new DomainValidationException(
                    DomainViolation.AUDIT_USER_UNSAFE,
                    "Audit user contains control characters");
        }
    }
}
