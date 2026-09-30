package com.alexastudillo.partyregistry.application.model;

import java.util.Objects;

/** Distinguishes a committed nationality mutation from a historical completed replay. */
public record NationalityMutationOutcome(NationalityResult nationality, Disposition disposition) {

    public NationalityMutationOutcome {
        Objects.requireNonNull(nationality, "nationality");
        Objects.requireNonNull(disposition, "disposition");
    }

    /** Indicates whether a new attempt was accepted or a stored result was replayed. */
    public enum Disposition { APPLIED, REPLAYED }
}
