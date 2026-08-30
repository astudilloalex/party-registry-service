package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonPatch;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/**
 * Requests a presence-aware partial update of one natural person's details.
 */
public record PatchNaturalPersonCommand(
        RequestMetadata requestMetadata,
        PartyId partyId,
        PartyVersion expectedVersion,
        NaturalPersonPatch patch) {

    public PatchNaturalPersonCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(patch, "patch");
    }

    public TenantId tenantId() {
        return requestMetadata.tenantId();
    }
}
