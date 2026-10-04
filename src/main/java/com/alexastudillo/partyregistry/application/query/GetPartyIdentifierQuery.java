package com.alexastudillo.partyregistry.application.query;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;

import java.util.Objects;

/** Requests a masked identifier detail qualified by trusted tenant and the owning Party. */
public record GetPartyIdentifierQuery(RequestMetadata requestMetadata, PartyId partyId, PartyIdentifierId identifierId) {

    public GetPartyIdentifierQuery {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(identifierId, "identifierId");
    }
}
