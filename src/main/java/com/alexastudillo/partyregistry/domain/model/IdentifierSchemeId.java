package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

import java.util.UUID;

/**
 * Identifies one stable official-identifier scheme.
 */
public record IdentifierSchemeId(UUID value) {

    public IdentifierSchemeId {
        if (value == null) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_SCHEME_ID_REQUIRED,
                    "Identifier scheme ID is required");
        }
    }
}
