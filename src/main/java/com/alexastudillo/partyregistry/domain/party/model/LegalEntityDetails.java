package com.alexastudillo.partyregistry.domain.party.model;

import java.time.LocalDate;

public record LegalEntityDetails(
        String legalName,
        String tradeName,
        String legalFormCode,
        CountryCode incorporationCountryCode,
        LocalDate incorporatedOn,
        LocalDate dissolvedOn) {
}
