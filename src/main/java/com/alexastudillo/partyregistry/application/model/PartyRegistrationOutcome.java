package com.alexastudillo.partyregistry.application.model;

/**
 * Distinguishes a newly committed Party registration from an idempotent replay.
 */
public enum PartyRegistrationOutcome {
    CREATED,
    REPLAYED
}
