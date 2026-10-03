package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeChanges;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;

import java.util.Objects;

/** Requests one version-guarded presence-aware catalog correction. */
public record PatchIdentifierSchemeCommand(
        RequestMetadata requestMetadata, IdentifierSchemeId schemeId, IdentifierSchemeVersion expectedVersion,
        IdentifierSchemeChanges changes) {
    public PatchIdentifierSchemeCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(schemeId, "schemeId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(changes, "changes");
    }
}
