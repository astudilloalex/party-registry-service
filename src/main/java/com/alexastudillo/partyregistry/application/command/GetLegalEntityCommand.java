package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/** Requests retrieval of one legal entity using trusted tenant context. */
public record GetLegalEntityCommand(RequestMetadata requestMetadata, PartyId partyId) {

    public GetLegalEntityCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
    }

    public TenantId tenantId() {
        return requestMetadata.tenantId();
    }
}
