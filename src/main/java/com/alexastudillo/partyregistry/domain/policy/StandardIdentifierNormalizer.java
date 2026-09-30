package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;

import java.util.Locale;

/**
 * Enumerates the explicitly supported versioned identifier normalizers.
 */
public enum StandardIdentifierNormalizer implements IdentifierNormalizer {
    TRIM_UPPERCASE_V1(new IdentifierRuleVersion(1));

    private final IdentifierRuleVersion version;

    StandardIdentifierNormalizer(IdentifierRuleVersion version) {
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
    public String normalize(String completeValue) {
        if (completeValue == null || completeValue.isBlank()) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_VALUE_REQUIRED,
                    "Complete identifier value is required");
        }
        return completeValue.strip().toUpperCase(Locale.ROOT);
    }
}
