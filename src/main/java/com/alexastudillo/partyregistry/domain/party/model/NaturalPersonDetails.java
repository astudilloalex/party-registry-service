package com.alexastudillo.partyregistry.domain.party.model;

import java.time.LocalDate;

public record NaturalPersonDetails(
        String givenNames,
        String familyNames,
        String preferredName,
        LocalDate birthDate,
        LocalDate dateOfDeath,
        CountryCode birthCountryCode) {
}
