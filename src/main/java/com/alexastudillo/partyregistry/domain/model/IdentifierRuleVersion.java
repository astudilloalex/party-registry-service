package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

/**
 * Identifies a positive, persisted version of an identifier-processing rule.
 */
public record IdentifierRuleVersion(int value) {

    private static final int MAX_PERSISTED_VERSION = Short.MAX_VALUE;

    public IdentifierRuleVersion {
        if (value <= 0 || value > MAX_PERSISTED_VERSION) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_RULE_VERSION_INVALID,
                    "Identifier rule version must fit a positive small integer");
        }
    }
}
