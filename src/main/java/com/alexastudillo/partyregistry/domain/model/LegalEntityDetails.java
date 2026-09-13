package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.normalization.PartyTextNormalization;

import java.time.LocalDate;

/**
 * Holds the validated organization details associated with a legal-entity Party.
 */
public record LegalEntityDetails(
        String legalName,
        String tradeName,
        String legalFormCode,
        String incorporationCountryCode,
        LocalDate incorporatedOn,
        LocalDate dissolvedOn) {

    private static final int MAX_NAME_LENGTH = 300;
    private static final int MAX_LEGAL_FORM_CODE_LENGTH = 64;

    public LegalEntityDetails {
        validateRequiredLegalName(legalName);
        validateOptionalLength(tradeName, MAX_NAME_LENGTH, DomainViolation.TRADE_NAME_TOO_LONG,
                "Trade name exceeds the maximum length");
        validateOptionalLength(legalFormCode, MAX_LEGAL_FORM_CODE_LENGTH,
                DomainViolation.LEGAL_FORM_CODE_TOO_LONG,
                "Legal form code exceeds the maximum length");
        validateCountryCode(incorporationCountryCode);
        if (incorporatedOn != null && dissolvedOn != null && dissolvedOn.isBefore(incorporatedOn)) {
            throw new DomainValidationException(
                    DomainViolation.DISSOLUTION_BEFORE_INCORPORATION,
                    "Dissolution date cannot precede incorporation date");
        }
    }

    /**
     * Normalizes newly supplied details before validation while preserving the constructor for restoration.
     */
    public static LegalEntityDetails forWrite(
            String legalName, String tradeName, String legalFormCode, String incorporationCountryCode,
            LocalDate incorporatedOn, LocalDate dissolvedOn) {
        return new LegalEntityDetails(
                PartyTextNormalization.uppercase(legalName),
                PartyTextNormalization.uppercase(tradeName),
                PartyTextNormalization.uppercase(legalFormCode),
                PartyTextNormalization.countryCode(incorporationCountryCode), incorporatedOn, dissolvedOn);
    }

    /** Returns canonical details for a new legal entity. */
    public LegalEntityDetails normalizedForWrite() {
        return forWrite(legalName, tradeName, legalFormCode, incorporationCountryCode, incorporatedOn, dissolvedOn);
    }

    /**
     * Validates lifecycle dates relative to the operation evaluation date.
     *
     * @param evaluatedOn operation evaluation date
     */
    public void validateAt(LocalDate evaluatedOn) {
        if (evaluatedOn == null) {
            throw new DomainValidationException(
                    DomainViolation.EVALUATION_DATE_REQUIRED,
                    "Evaluation date is required");
        }
        if (incorporatedOn != null && incorporatedOn.isAfter(evaluatedOn)) {
            throw new DomainValidationException(
                    DomainViolation.INCORPORATION_DATE_IN_FUTURE,
                    "Incorporation date cannot be in the future");
        }
        if (dissolvedOn != null && dissolvedOn.isAfter(evaluatedOn)) {
            throw new DomainValidationException(
                    DomainViolation.DISSOLUTION_DATE_IN_FUTURE,
                    "Dissolution date cannot be in the future");
        }
    }

    public String derivedDisplayName() {
        return PartyTextNormalization.uppercase(legalName);
    }

    private static void validateRequiredLegalName(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(
                    DomainViolation.LEGAL_NAME_REQUIRED,
                    "Legal name is required");
        }
        if (value.codePointCount(0, value.length()) > MAX_NAME_LENGTH) {
            throw new DomainValidationException(
                    DomainViolation.LEGAL_NAME_TOO_LONG,
                    "Legal name exceeds the maximum length");
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

    private static void validateCountryCode(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(
                    DomainViolation.INCORPORATION_COUNTRY_CODE_REQUIRED,
                    "Incorporation country code is required");
        }
        if (!value.matches("^[A-Z]{2}$")) {
            throw new DomainValidationException(
                    DomainViolation.INCORPORATION_COUNTRY_CODE_INVALID,
                    "Incorporation country code must contain two uppercase letters");
        }
    }
}
