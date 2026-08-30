package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

import java.time.LocalDate;

/**
 * Holds the validated personal details associated with a natural-person party.
 */
public record NaturalPersonDetails(
        String givenNames,
        String familyNames,
        String preferredName,
        LocalDate birthDate,
        LocalDate dateOfDeath,
        String birthCountryCode) {

    private static final int MAX_NAME_LENGTH = 200;

    public NaturalPersonDetails {
        validateRequiredName(givenNames, DomainViolation.GIVEN_NAMES_REQUIRED,
                DomainViolation.GIVEN_NAMES_TOO_LONG, "Given names");
        validateRequiredName(familyNames, DomainViolation.FAMILY_NAMES_REQUIRED,
                DomainViolation.FAMILY_NAMES_TOO_LONG, "Family names");
        if (preferredName != null
                && preferredName.codePointCount(0, preferredName.length()) > MAX_NAME_LENGTH) {
            throw new DomainValidationException(
                    DomainViolation.PREFERRED_NAME_TOO_LONG,
                    "Preferred name exceeds the maximum length");
        }
        if (birthCountryCode != null && !birthCountryCode.matches("^[A-Z]{2}$")) {
            throw new DomainValidationException(
                    DomainViolation.BIRTH_COUNTRY_CODE_INVALID,
                    "Birth country code must contain two uppercase letters");
        }
        if (birthDate != null && dateOfDeath != null && dateOfDeath.isBefore(birthDate)) {
            throw new DomainValidationException(
                    DomainViolation.DEATH_BEFORE_BIRTH,
                    "Date of death cannot precede birth date");
        }
    }

    /**
     * Validates lifecycle dates relative to the date on which an operation is evaluated.
     *
     * @param evaluatedOn operation evaluation date
     * @throws DomainValidationException when a date is in the future or the evaluation date is absent
     */
    public void validateAt(LocalDate evaluatedOn) {
        if (evaluatedOn == null) {
            throw new DomainValidationException(
                    DomainViolation.EVALUATION_DATE_REQUIRED,
                    "Evaluation date is required");
        }
        if (birthDate != null && birthDate.isAfter(evaluatedOn)) {
            throw new DomainValidationException(
                    DomainViolation.BIRTH_DATE_IN_FUTURE,
                    "Birth date cannot be in the future");
        }
        if (dateOfDeath != null && dateOfDeath.isAfter(evaluatedOn)) {
            throw new DomainValidationException(
                    DomainViolation.DATE_OF_DEATH_IN_FUTURE,
                    "Date of death cannot be in the future");
        }
    }

    public String derivedDisplayName() {
        return givenNames.strip() + " " + familyNames.strip();
    }

    private static void validateRequiredName(
            String value,
            DomainViolation requiredViolation,
            DomainViolation lengthViolation,
            String fieldName) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(requiredViolation, fieldName + " are required");
        }
        if (value.codePointCount(0, value.length()) > MAX_NAME_LENGTH) {
            throw new DomainValidationException(
                    lengthViolation,
                    fieldName + " exceed the maximum length");
        }
    }
}
