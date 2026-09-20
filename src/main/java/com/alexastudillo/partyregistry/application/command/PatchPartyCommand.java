package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;

import java.util.Objects;

/**
 * Requests a root label correction, retaining submitted text for post-version Domain validation.
 */
public record PatchPartyCommand(
        RequestMetadata requestMetadata, PartyId partyId, PartyVersion expectedVersion, String displayName) {

    public PatchPartyCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(displayName, "displayName");
    }
}
