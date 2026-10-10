package com.alexastudillo.partyregistry.api.model.response;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

/** Exposes only the five approved safe matching-identifier fields, never complete values or protection metadata. */
@RegisterForReflection
public record IdentifierSearchIdentifierResponse(UUID identifierId, String schemeCode, String maskedValue, String status, boolean isPrimary) {
}
