package com.alexastudillo.partyregistry.api.rest.v1.party.dto;

import java.util.List;

public record CreatePartyRequest(
        String type,
        NaturalPersonDetailsInput naturalPersonDetails,
        LegalEntityDetailsInput legalEntityDetails,
        List<NationalityInput> nationalities) {
}
