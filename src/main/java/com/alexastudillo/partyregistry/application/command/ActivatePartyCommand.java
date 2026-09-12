package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/**
 * Requests tenant-scoped activation at an expected Party version.
 */
public record ActivatePartyCommand(
        RequestMetadata requestMetadata,
        PartyId partyId,
        PartyVersion expectedVersion) {

    public ActivatePartyCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
    }

    public TenantId tenantId() {
        return requestMetadata.tenantId();
    }
}
