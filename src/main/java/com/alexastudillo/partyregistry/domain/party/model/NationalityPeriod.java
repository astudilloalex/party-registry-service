package com.alexastudillo.partyregistry.domain.party.model;

import java.time.LocalDate;

public record NationalityPeriod(LocalDate validFrom, LocalDate validUntil) {
}
