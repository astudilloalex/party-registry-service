package com.alexastudillo.partyregistry.domain.normalization;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Defines canonical text for new Party writes without changing historical representations.
 */
public final class PartyTextNormalization {

    private PartyTextNormalization() {
    }

    /** Removes exterior whitespace and uppercases text independently of the default locale, preserving null. */
    public static @Nullable String uppercase(@Nullable String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    /** Canonicalizes ASCII alpha-2 input, preserving invalid text for the existing field-specific validators. */
    public static @Nullable String countryCode(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.matches("[A-Za-z]{2}") ? uppercase(stripped) : stripped;
    }
}
