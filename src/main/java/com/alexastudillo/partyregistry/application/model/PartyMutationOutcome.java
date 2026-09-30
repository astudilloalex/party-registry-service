package com.alexastudillo.partyregistry.application.model;

import java.util.Objects;

/**
 * Carries safe accepted Party data and distinguishes a new mutation from historical replay.
 */
public record PartyMutationOutcome(PartyDetailsResult party, Disposition disposition) {

    public PartyMutationOutcome {
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(disposition, "disposition");
    }

    /** Classifies the workflow result without adding transport or current-request data to saved Party data. */
    public enum Disposition {
        APPLIED,
        REPLAYED
    }
}
