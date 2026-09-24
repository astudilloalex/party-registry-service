package com.alexastudillo.partyregistry.api.model.response;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Represents natural-person detail fields in the public API contract.
 */
public record NaturalPersonDetailsResponse(
        String givenNames,
        String familyNames,
        @Nullable String preferredName,
        @Nullable LocalDate birthDate,
        @Nullable LocalDate dateOfDeath,
        @Nullable String birthCountryCode) {
}
