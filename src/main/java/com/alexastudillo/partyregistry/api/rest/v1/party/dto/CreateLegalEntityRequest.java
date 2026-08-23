package com.alexastudillo.partyregistry.api.rest.v1.party.dto;

public record CreateLegalEntityRequest(
        String type,
        LegalEntityDetailsInput legalEntityDetails) {
}
