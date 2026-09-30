package com.alexastudillo.partyregistry.domain.model;

/**
 * Describes the independent verification lifecycle of an official Party identifier.
 */
public enum PartyIdentifierStatus {
    PENDING_VERIFICATION,
    VERIFIED,
    REJECTED,
    EXPIRED,
    REVOKED
}
