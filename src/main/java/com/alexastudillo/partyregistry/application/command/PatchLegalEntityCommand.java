package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.LegalEntityPatch;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/** Carries presence-aware legal-detail changes and the required current Party version. */
public record PatchLegalEntityCommand(
        RequestMetadata requestMetadata,
        PartyId partyId,
        PartyVersion expectedVersion,
        LegalEntityPatch patch) {

    public PatchLegalEntityCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(patch, "patch");
    }

    public TenantId tenantId() {
        return requestMetadata.tenantId();
    }
}
