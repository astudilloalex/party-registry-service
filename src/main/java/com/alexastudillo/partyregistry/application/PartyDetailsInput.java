package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.DetailKind;
import java.time.LocalDate;
import java.util.UUID;

public record PartyDetailsInput(
        UUID partyId,
        DetailKind kind,
        String primaryName,
        String secondaryName,
        String optionalName,
        String countryCode,
        LocalDate startDate,
        LocalDate endDate) {}
