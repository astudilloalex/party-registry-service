package com.alexastudillo.partyregistry.application.error;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;

import java.util.Objects;

/** Classifies known catalog administration violations while preserving all unrelated failures. */
public final class IdentifierSchemeFailures {
    private IdentifierSchemeFailures() { }

    /** Translates only deliberate administration violations, retaining their cause and unknown terminal signals. */
    public static Throwable translate(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        if (!(failure instanceof DomainValidationException domain)) {
            return failure;
        }
        ApplicationFailure classification = switch (domain.violation()) {
            case IDENTIFIER_MINIMUM_LENGTH_INVALID, IDENTIFIER_MAXIMUM_LENGTH_INVALID, IDENTIFIER_LENGTH_RANGE_INVALID ->
                    new ApplicationFailure.IdentifierSchemeLengthRangeInvalid();
            case IDENTIFIER_RULE_CATALOG_INVALID -> new ApplicationFailure.InvalidIdentifierSchemeConfiguration();
            case IDENTIFIER_SCHEME_RULES_LOCKED -> new ApplicationFailure.IdentifierSchemeRulesLocked();
            case IDENTIFIER_SCHEME_RETIRED -> new ApplicationFailure.IdentifierSchemeRetired();
            case IDENTIFIER_SCHEME_VERSION_OVERFLOW -> new ApplicationFailure.IdentifierSchemeVersionExhausted();
            case IDENTIFIER_SCHEME_ACTIVATION_INVALID_STATE, IDENTIFIER_SCHEME_DEPRECATION_INVALID_STATE,
                    IDENTIFIER_SCHEME_RETIREMENT_INVALID_STATE -> new ApplicationFailure.InvalidIdentifierSchemeLifecycle();
            default -> null;
        };
        return classification == null ? failure : new ApplicationException(classification, domain);
    }
}
