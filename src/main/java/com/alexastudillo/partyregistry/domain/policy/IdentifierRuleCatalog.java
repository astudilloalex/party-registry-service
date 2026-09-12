package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;

import java.util.Map;
import java.util.Set;

/**
 * Resolves only explicitly registered pure identifier rules by stable versioned keys.
 */
public final class IdentifierRuleCatalog {

    private final Map<String, IdentifierNormalizer> normalizers = Map.of(
            StandardIdentifierNormalizer.TRIM_UPPERCASE_V1.key(),
            StandardIdentifierNormalizer.TRIM_UPPERCASE_V1);
    private final Map<String, IdentifierValidator> validators = Map.of(
            StandardIdentifierValidator.ALPHANUMERIC_V1.key(),
            StandardIdentifierValidator.ALPHANUMERIC_V1);

    /**
     * Normalizes and validates a complete value using the scheme's exact versioned rule keys.
     *
     * @param scheme selected identifier scheme
     * @param completeValue complete value held only for the current operation
     * @return the transient normalized value and applied rule versions
     */
    public IdentifierRuleResult evaluate(IdentifierScheme scheme, String completeValue) {
        if (scheme == null) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_SCHEME_UNKNOWN,
                    "Identifier scheme is unknown");
        }
        IdentifierNormalizer normalizer = normalizers.get(scheme.normalizerKey());
        IdentifierValidator validator = validators.get(scheme.validatorKey());
        if (normalizer == null || validator == null) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_RULE_CATALOG_INVALID,
                    "Identifier scheme references an unsupported processing rule");
        }

        String normalizedValue = normalizer.normalize(completeValue);
        if (!validator.isValid(normalizedValue)) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_VALUE_INVALID,
                    "Identifier value does not satisfy its scheme validator");
        }
        return new IdentifierRuleResult(normalizedValue, normalizer.version(), validator.version());
    }

    public Set<String> supportedNormalizerKeys() {
        return normalizers.keySet();
    }

    public Set<String> supportedValidatorKeys() {
        return validators.keySet();
    }
}
