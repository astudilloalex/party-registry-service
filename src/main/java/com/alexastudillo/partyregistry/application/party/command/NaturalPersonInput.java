package com.alexastudillo.partyregistry.application.party.command;

import java.time.LocalDate;

public record NaturalPersonInput(
        String givenNames,
        String familyNames,
        String preferredName,
        LocalDate birthDate,
        LocalDate dateOfDeath,
        String birthCountryCode) {
}
