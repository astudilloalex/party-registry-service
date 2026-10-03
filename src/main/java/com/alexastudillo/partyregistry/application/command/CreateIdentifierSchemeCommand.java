package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemeCreateInput;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;

import java.util.Objects;

/** Requests idempotent global scheme creation with current attribution and exact effective intent. */
public record CreateIdentifierSchemeCommand(
        RequestMetadata requestMetadata, String idempotencyKey, IdentifierSchemeCreateInput effectiveRequest) {
    public CreateIdentifierSchemeCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(effectiveRequest, "effectiveRequest");
        if (idempotencyKey.isBlank() || idempotencyKey.codePointCount(0, idempotencyKey.length()) > 128) {
            throw new IllegalArgumentException("Identifier scheme creation key is outside the supported range");
        }
    }
}
