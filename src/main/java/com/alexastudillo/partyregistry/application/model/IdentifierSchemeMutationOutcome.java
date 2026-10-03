package com.alexastudillo.partyregistry.application.model;

import java.util.Objects;

/** Describes an accepted scheme result and whether this subscription applied or replayed it. */
public record IdentifierSchemeMutationOutcome(IdentifierSchemeResult scheme, Disposition disposition) {
    public IdentifierSchemeMutationOutcome {
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(disposition, "disposition");
    }

    /** Distinguishes a newly accepted write from historical successful replay. */
    public enum Disposition { APPLIED, REPLAYED }
}
