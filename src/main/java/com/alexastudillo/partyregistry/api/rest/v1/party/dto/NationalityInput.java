package com.alexastudillo.partyregistry.api.rest.v1.party.dto;

import java.time.LocalDate;

public record NationalityInput(
        String countryCode,
        Boolean isPrimary,
        LocalDate validFrom,
        LocalDate validUntil) {
}
