package com.alexastudillo.partyregistry.infrastructure.persistence;

/**
 * Identifies the persistence aggregate category recorded by an outbox event.
 */
public enum PartyOutboxAggregateType {
    PARTY,
    PARTY_IDENTIFIER
}
