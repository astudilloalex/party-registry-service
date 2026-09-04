package com.alexastudillo.partyregistry.api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Represents the strict JSON body accepted when creating a natural person.
 */
public record NaturalPersonCreateRequest(
        @Nullable @Size(max = 300, message = "display-name-too-long") String displayName,
        @NotBlank(message = "given-names-required") @Size(max = 200, message = "given-names-too-long") String givenNames,
        @NotBlank(message = "family-names-required") @Size(max = 200, message = "family-names-too-long") String familyNames,
        @Nullable @Size(max = 200, message = "preferred-name-too-long") String preferredName,
        @Nullable LocalDate birthDate,
        @Nullable LocalDate dateOfDeath,
        @Nullable @Pattern(regexp = "^[A-Z]{2}$", message = "birth-country-code-invalid") String birthCountryCode) {
}
