package com.alexastudillo.partyregistry.application.party.result;

import java.util.UUID;

public record CreatePartyResult(UUID partyId, long version) {
}
