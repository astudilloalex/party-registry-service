package com.alexastudillo.partyregistry.api.model.response;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

/** Exposes the approved stored Party summary and one safe matching identifier per search hit. */
@RegisterForReflection
public record IdentifierSearchResponse(UUID partyId, String type, String displayName, String recordStatus,
        IdentifierSearchIdentifierResponse identifier) {
}
