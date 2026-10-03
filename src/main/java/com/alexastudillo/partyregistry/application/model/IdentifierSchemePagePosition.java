package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;

import java.time.Instant;
import java.util.Objects;

/** Identifies a unique position in ascending creation-time and scheme-UUID order. */
public record IdentifierSchemePagePosition(Instant createdAt, IdentifierSchemeId schemeId) {
    public IdentifierSchemePagePosition {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(schemeId, "schemeId");
    }
}
