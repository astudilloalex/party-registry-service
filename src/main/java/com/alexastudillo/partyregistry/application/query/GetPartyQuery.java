package com.alexastudillo.partyregistry.application.query;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;

import java.util.Objects;

/**
 * Requests the safe common detail of one Party in the trusted tenant context.
 */
public record GetPartyQuery(RequestMetadata metadata, PartyId partyId) {

    public GetPartyQuery {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(partyId, "partyId");
    }
}
