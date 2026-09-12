package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

import java.time.Instant;

/**
 * Defines the stable identity, lifecycle, and processing rules of an official identifier scheme.
 * The legacy requiresExpiration property is retained for persisted catalogs but never makes expiration mandatory.
 */
public record IdentifierScheme(
        IdentifierSchemeId id,
        String code,
        String issuingCountryCode,
        IdentifierCategory category,
        IdentifierSubjectType applicableSubjectType,
        String name,
        String description,
        String normalizerKey,
        String validatorKey,
        Integer minimumLength,
        Integer maximumLength,
        boolean requiresExpiration,
        IdentifierSchemeStatus status,
        IdentifierSchemeVersion version,
        AuditInfo auditInfo) {

    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_RULE_KEY_LENGTH = 64;

    public IdentifierScheme {
        require(id, DomainViolation.IDENTIFIER_SCHEME_ID_REQUIRED, "Identifier scheme ID is required");
        validateRequiredText(code, MAX_CODE_LENGTH,
                DomainViolation.IDENTIFIER_SCHEME_CODE_REQUIRED,
                DomainViolation.IDENTIFIER_SCHEME_CODE_TOO_LONG,
                "Identifier scheme code");
        validateCountryCode(issuingCountryCode);
        require(category, DomainViolation.IDENTIFIER_CATEGORY_REQUIRED, "Identifier category is required");
        require(applicableSubjectType, DomainViolation.IDENTIFIER_SUBJECT_TYPE_REQUIRED,
                "Identifier subject type is required");
        validateRequiredText(name, MAX_NAME_LENGTH,
                DomainViolation.IDENTIFIER_SCHEME_NAME_REQUIRED,
                DomainViolation.IDENTIFIER_SCHEME_NAME_TOO_LONG,
                "Identifier scheme name");
        validateOptionalLength(description, MAX_DESCRIPTION_LENGTH,
                DomainViolation.IDENTIFIER_SCHEME_DESCRIPTION_TOO_LONG,
                "Identifier scheme description exceeds the maximum length");
        validateRequiredText(normalizerKey, MAX_RULE_KEY_LENGTH,
                DomainViolation.IDENTIFIER_NORMALIZER_KEY_REQUIRED,
                DomainViolation.IDENTIFIER_NORMALIZER_KEY_TOO_LONG,
                "Identifier normalizer key");
        validateRequiredText(validatorKey, MAX_RULE_KEY_LENGTH,
                DomainViolation.IDENTIFIER_VALIDATOR_KEY_REQUIRED,
                DomainViolation.IDENTIFIER_VALIDATOR_KEY_TOO_LONG,
                "Identifier validator key");
        validateLengthRange(minimumLength, maximumLength);
        require(status, DomainViolation.IDENTIFIER_SCHEME_STATUS_REQUIRED,
                "Identifier scheme status is required");
        require(version, DomainViolation.IDENTIFIER_SCHEME_VERSION_REQUIRED,
                "Identifier scheme version is required");
        require(auditInfo, DomainViolation.AUDIT_REQUIRED, "Audit information is required");
    }

    /**
     * Creates a draft identifier scheme at version zero.
     *
     * @return a new identifier scheme with its initial lifecycle and audit state
     */
    public static IdentifierScheme create(
            IdentifierSchemeId id,
            String code,
            String issuingCountryCode,
            IdentifierCategory category,
            IdentifierSubjectType applicableSubjectType,
            String name,
            String description,
            String normalizerKey,
            String validatorKey,
            Integer minimumLength,
            Integer maximumLength,
            boolean requiresExpiration,
            Instant occurredAt,
            String createdBy) {
        return new IdentifierScheme(
                id,
                code,
                issuingCountryCode,
                category,
                applicableSubjectType,
                name,
                description,
                normalizerKey,
                validatorKey,
                minimumLength,
                maximumLength,
                requiresExpiration,
                IdentifierSchemeStatus.DRAFT,
                IdentifierSchemeVersion.initial(),
                AuditInfo.initial(occurredAt, createdBy));
    }

    public boolean supports(PartyType partyType) {
        return applicableSubjectType.supports(partyType);
    }

    private static void validateCountryCode(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_ISSUING_COUNTRY_CODE_REQUIRED,
                    "Identifier issuing country code is required");
        }
        if (!value.matches("^[A-Z]{2}$")) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_ISSUING_COUNTRY_CODE_INVALID,
                    "Identifier issuing country code must contain two uppercase letters");
        }
    }

    private static void validateLengthRange(Integer minimumLength, Integer maximumLength) {
        if (minimumLength != null && minimumLength <= 0) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_MINIMUM_LENGTH_INVALID,
                    "Identifier minimum length must be positive");
        }
        if (maximumLength != null && maximumLength <= 0) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_MAXIMUM_LENGTH_INVALID,
                    "Identifier maximum length must be positive");
        }
        if (minimumLength != null && maximumLength != null && maximumLength < minimumLength) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_LENGTH_RANGE_INVALID,
                    "Identifier maximum length cannot be less than its minimum length");
        }
    }

    private static void validateRequiredText(
            String value,
            int maximumLength,
            DomainViolation requiredViolation,
            DomainViolation lengthViolation,
            String fieldName) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(requiredViolation, fieldName + " is required");
        }
        if (value.codePointCount(0, value.length()) > maximumLength) {
            throw new DomainValidationException(lengthViolation, fieldName + " exceeds the maximum length");
        }
    }

    private static void validateOptionalLength(
            String value,
            int maximumLength,
            DomainViolation violation,
            String message) {
        if (value != null && value.codePointCount(0, value.length()) > maximumLength) {
            throw new DomainValidationException(violation, message);
        }
    }

    private static <T> T require(T value, DomainViolation violation, String message) {
        if (value == null) {
            throw new DomainValidationException(violation, message);
        }
        return value;
    }
}
