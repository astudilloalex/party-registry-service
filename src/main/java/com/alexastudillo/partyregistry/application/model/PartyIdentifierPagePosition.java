package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;

import java.time.Instant;
import java.util.Objects;

/** Preserves the exact creation-instant/identifier tuple for historical keyset navigation. */
public record PartyIdentifierPagePosition(Instant createdAt, PartyIdentifierId identifierId) {

    public PartyIdentifierPagePosition {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(identifierId, "identifierId");
    }
}
