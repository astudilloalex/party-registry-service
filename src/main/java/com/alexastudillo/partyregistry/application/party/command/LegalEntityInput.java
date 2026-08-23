package com.alexastudillo.partyregistry.application.party.command;

import java.time.LocalDate;

public record LegalEntityInput(
        String legalName,
        String tradeName,
        String legalFormCode,
        String incorporationCountryCode,
        LocalDate incorporatedOn,
        LocalDate dissolvedOn) {
}
