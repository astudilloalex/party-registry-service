package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.normalization.PartyTextNormalization;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Defines canonical literal prefix/substring matching independently of database collations.
 */
public record PartyNamePredicate(@Nullable String startsWith, @Nullable String contains) {

    public PartyNamePredicate {
        startsWith = canonical(startsWith);
        contains = canonical(contains);
    }

    /** Returns whether either supplied filter remains effective after canonicalization. */
    public boolean isEffective() {
        return startsWith != null || contains != null;
    }

    /** Compares a stored label without modifying its original representation or interpreting wildcards. */
    public boolean matches(String storedName) {
        String normalized = Objects.requireNonNull(PartyTextNormalization.uppercase(storedName), "storedName");
        return (startsWith == null || normalized.startsWith(startsWith))
                && (contains == null || normalized.contains(contains));
    }

    private static @Nullable String canonical(@Nullable String value) {
        String normalized = PartyTextNormalization.uppercase(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }
}
