package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;

import java.util.Objects;

/**
 * Defines effective lifecycle request identity independently of retry correlation and audit actor.
 */
public record PartyLifecycleRequest(PartyLifecycleAction action, PartyId partyId, PartyVersion expectedVersion) {

    public PartyLifecycleRequest {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
    }
}
