package com.alexastudillo.partyregistry.api.rest.v1.party.dto;

import java.util.List;

public record CreateNaturalPersonRequest(
        String type,
        NaturalPersonDetailsInput naturalPersonDetails,
        List<NationalityInput> nationalities) {
}
