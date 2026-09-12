package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

/**
 * Represents the nonnegative optimistic-concurrency version of an identifier scheme.
 */
public record IdentifierSchemeVersion(long value) {

    public IdentifierSchemeVersion {
        if (value < 0) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_SCHEME_VERSION_NEGATIVE,
                    "Identifier scheme version cannot be negative");
        }
    }

    public static IdentifierSchemeVersion initial() {
        return new IdentifierSchemeVersion(0);
    }
}
