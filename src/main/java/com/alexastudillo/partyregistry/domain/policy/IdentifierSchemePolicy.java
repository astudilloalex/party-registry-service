package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;

import java.time.LocalDate;

/**
 * Enforces identifier-scheme eligibility, normalized length, and expiration rules.
 */
public final class IdentifierSchemePolicy {

    /**
     * Requires a known active scheme compatible with the Party type for new registration.
     *
     * @param scheme resolved scheme, or null when the code is unknown
     * @param partyType immutable Party type
     */
    public void requireRegistrationEligibility(IdentifierScheme scheme, PartyType partyType) {
        if (scheme == null) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_SCHEME_UNKNOWN,
                    "Identifier scheme is unknown");
        }
        if (partyType == null) {
            throw new DomainValidationException(
                    DomainViolation.PARTY_TYPE_REQUIRED,
                    "Party type is required");
        }
        if (scheme.status() != IdentifierSchemeStatus.ACTIVE) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_SCHEME_INACTIVE,
                    "Identifier scheme is not active");
        }
        if (!scheme.supports(partyType)) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_SCHEME_INCOMPATIBLE,
                    "Identifier scheme is incompatible with the Party type");
        }
    }

    /**
     * Applies inclusive scheme limits to the normalized identifier value.
     *
     * @param scheme selected scheme
     * @param normalizedValue transient normalized identifier value
     */
    public void validateNormalizedLength(IdentifierScheme scheme, String normalizedValue) {
        if (scheme == null) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_SCHEME_UNKNOWN,
                    "Identifier scheme is unknown");
        }
        if (normalizedValue == null || normalizedValue.isBlank()) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_VALUE_REQUIRED,
                    "Normalized identifier value is required");
        }
        int length = normalizedValue.codePointCount(0, normalizedValue.length());
        if (scheme.minimumLength() != null && length < scheme.minimumLength()) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_VALUE_TOO_SHORT,
                    "Normalized identifier value is shorter than the scheme minimum");
        }
        if (scheme.maximumLength() != null && length > scheme.maximumLength()) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_VALUE_TOO_LONG,
                    "Normalized identifier value exceeds the scheme maximum");
        }
    }

    /**
     * Enforces required expiration and rejects identifiers already expired on evaluation.
     *
     * @param scheme selected scheme
     * @param expiresOn optional expiration date
     * @param evaluatedOn operation evaluation date
     */
    public void validateExpiration(
            IdentifierScheme scheme,
            LocalDate expiresOn,
            LocalDate evaluatedOn) {
        if (scheme == null) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_SCHEME_UNKNOWN,
                    "Identifier scheme is unknown");
        }
        if (evaluatedOn == null) {
            throw new DomainValidationException(
                    DomainViolation.EVALUATION_DATE_REQUIRED,
                    "Evaluation date is required");
        }
        if (scheme.requiresExpiration() && expiresOn == null) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_EXPIRATION_REQUIRED,
                    "Identifier expiration is required by the scheme");
        }
        if (expiresOn != null && expiresOn.isBefore(evaluatedOn)) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_EXPIRED,
                    "Identifier is already expired");
        }
    }
}
