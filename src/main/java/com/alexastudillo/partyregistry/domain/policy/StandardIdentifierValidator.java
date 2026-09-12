package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;

/**
 * Enumerates the explicitly supported versioned identifier validators.
 */
public enum StandardIdentifierValidator implements IdentifierValidator {
    ALPHANUMERIC_V1(new IdentifierRuleVersion(1));

    private final IdentifierRuleVersion version;

    StandardIdentifierValidator(IdentifierRuleVersion version) {
        this.version = version;
    }

    @Override
    public String key() {
        return name();
    }

    @Override
    public IdentifierRuleVersion version() {
        return version;
    }

    @Override
    public boolean isValid(String normalizedValue) {
        return normalizedValue != null && normalizedValue.matches("^[A-Z0-9]+$");
    }
}
