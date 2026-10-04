package com.alexastudillo.partyregistry.application.model;

import java.util.Objects;

/** Carries a safe accepted identifier result and committed application-versus-replay disposition. */
public record PartyIdentifierMutationOutcome(PartyIdentifierResult identifier, Disposition disposition) {

    public PartyIdentifierMutationOutcome {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(disposition, "disposition");
    }

    /** Distinguishes one new accepted mutation from retrieval of its original immutable completion. */
    public enum Disposition {
        APPLIED,
        REPLAYED
    }
}
