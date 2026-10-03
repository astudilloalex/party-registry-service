package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;

import java.util.Objects;

/** Defines effective lifecycle intent without retry actor or process correlation. */
public record IdentifierSchemeLifecycleRequest(
        IdentifierSchemeLifecycleAction action, IdentifierSchemeId schemeId,
        IdentifierSchemeVersion expectedVersion) implements IdentifierSchemeEffectiveRequest {

    public IdentifierSchemeLifecycleRequest {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(schemeId, "schemeId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
    }

    @Override
    public String operation() {
        return action.operation();
    }
}
