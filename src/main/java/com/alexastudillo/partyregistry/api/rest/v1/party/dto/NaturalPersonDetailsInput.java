package com.alexastudillo.partyregistry.api.rest.v1.party.dto;

import java.time.LocalDate;

public record NaturalPersonDetailsInput(
        String givenNames,
        String familyNames,
        String preferredName,
        LocalDate birthDate,
        LocalDate dateOfDeath,
        String birthCountryCode) {
}
