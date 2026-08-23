package com.alexastudillo.partyregistry.application.party.command;

import java.time.LocalDate;

public record NationalityInput(
        String countryCode,
        boolean isPrimary,
        LocalDate validFrom,
        LocalDate validUntil) {
}
