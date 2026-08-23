package com.alexastudillo.partyregistry.domain.party.model;

public record PartyNationality(
        CountryCode countryCode,
        boolean isPrimary,
        NationalityPeriod period) {
}
