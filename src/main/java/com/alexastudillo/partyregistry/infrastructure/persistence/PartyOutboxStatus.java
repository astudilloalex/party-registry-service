package com.alexastudillo.partyregistry.infrastructure.persistence;

/**
 * Describes the delivery state of a persisted Party outbox event.
 */
public enum PartyOutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
