package com.alexastudillo.partyregistry.api.rest.v1.party.dto;

import java.time.LocalDate;

public record LegalEntityDetailsInput(
        String legalName,
        String tradeName,
        String legalFormCode,
        String incorporationCountryCode,
        LocalDate incorporatedOn,
        LocalDate dissolvedOn) {
}
