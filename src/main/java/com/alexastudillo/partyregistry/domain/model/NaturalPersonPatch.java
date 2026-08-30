package com.alexastudillo.partyregistry.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Describes presence-aware changes to natural-person detail fields.
 */
public record NaturalPersonPatch(
        FieldUpdate<String> givenNames,
        FieldUpdate<String> familyNames,
        FieldUpdate<String> preferredName,
        FieldUpdate<LocalDate> birthDate,
        FieldUpdate<LocalDate> dateOfDeath,
        FieldUpdate<String> birthCountryCode) {

    public NaturalPersonPatch {
        Objects.requireNonNull(givenNames, "givenNames");
        Objects.requireNonNull(familyNames, "familyNames");
        Objects.requireNonNull(preferredName, "preferredName");
        Objects.requireNonNull(birthDate, "birthDate");
        Objects.requireNonNull(dateOfDeath, "dateOfDeath");
        Objects.requireNonNull(birthCountryCode, "birthCountryCode");
    }

    public static NaturalPersonPatch empty() {
        return new NaturalPersonPatch(
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent());
    }

    public boolean isEmpty() {
        return !givenNames.isPresent()
                && !familyNames.isPresent()
                && !preferredName.isPresent()
                && !birthDate.isPresent()
                && !dateOfDeath.isPresent()
                && !birthCountryCode.isPresent();
    }
}
