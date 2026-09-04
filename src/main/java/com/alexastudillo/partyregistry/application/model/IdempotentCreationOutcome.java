package com.alexastudillo.partyregistry.application.model;

/**
 * Distinguishes an originally created natural person from a replayed creation.
 */
public enum IdempotentCreationOutcome {
    CREATED,
    REPLAYED
}
