package com.alexastudillo.partyregistry.api.model.response;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.UUID;

/** Exposes exactly the six approved root summary fields without Domain wrappers, subtype details, or identifiers. */
@RegisterForReflection
public record PartySummaryResponse(UUID partyId, String type, String displayName, String recordStatus,
        Instant createdAt, long version) {
}
