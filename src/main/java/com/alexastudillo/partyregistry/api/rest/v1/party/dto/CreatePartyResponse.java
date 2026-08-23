package com.alexastudillo.partyregistry.api.rest.v1.party.dto;

import java.util.UUID;

public record CreatePartyResponse(UUID partyId, long version) {
}
