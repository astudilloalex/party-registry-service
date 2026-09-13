package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;

/**
 * Carries a transient normalized identifier and the exact rule versions that produced it.
 */
public record IdentifierRuleResult(
        String normalizedValue,
        IdentifierRuleVersion normalizationVersion,
        IdentifierRuleVersion validationVersion) {

    public IdentifierRuleResult {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_VALUE_REQUIRED,
                    "Normalized identifier value is required");
        }
        if (normalizationVersion == null || validationVersion == null) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_RULE_VERSION_INVALID,
                    "Identifier rule versions are required");
        }
    }
}
