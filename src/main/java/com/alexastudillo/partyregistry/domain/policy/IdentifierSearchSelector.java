package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;

import java.util.Objects;

/** Holds a transient canonical search selector and pure literal comparison without exposing it in diagnostics. */
public record IdentifierSearchSelector(IdentifierNormalizer normalizer, String normalizedValue) {
    public IdentifierSearchSelector {
        Objects.requireNonNull(normalizer, "normalizer");
        Objects.requireNonNull(normalizedValue, "normalizedValue");
        if (normalizedValue.isBlank()) {
            throw new IllegalArgumentException("Identifier search selector must be nonblank");
        }
    }

    public String normalizerKey() {
        return normalizer.key();
    }

    public IdentifierRuleVersion normalizationVersion() {
        return normalizer.version();
    }

    /** Normalizes complete historical text using the selected supported rule without registration checks. */
    public boolean matchesExact(String completeValue) {
        return normalizedValue.equals(normalizer.normalize(completeValue));
    }

    /** Treats punctuation, accents and interior whitespace literally, including complete-value selectors. */
    public boolean matchesPrefix(String completeValue) {
        return normalizer.normalize(completeValue).startsWith(normalizedValue);
    }

    @Override
    public String toString() {
        return "IdentifierSearchSelector[redacted]";
    }
}
