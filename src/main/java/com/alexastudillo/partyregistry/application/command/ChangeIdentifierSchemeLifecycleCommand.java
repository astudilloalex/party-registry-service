package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleRequest;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;

import java.util.Objects;
import java.util.Optional;

/** Requests one expected-version transition, optionally protected by a scoped completion key. */
public record ChangeIdentifierSchemeLifecycleCommand(
        RequestMetadata requestMetadata, IdentifierSchemeId schemeId, IdentifierSchemeVersion expectedVersion,
        IdentifierSchemeLifecycleAction action, Optional<String> idempotencyKey) {
    public ChangeIdentifierSchemeLifecycleCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(schemeId, "schemeId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        idempotencyKey.ifPresent(key -> {
            if (key.isBlank() || key.codePointCount(0, key.length()) > 128) {
                throw new IllegalArgumentException("Identifier scheme lifecycle key is outside the supported range");
            }
        });
    }

    public IdentifierSchemeLifecycleRequest effectiveRequest() {
        return new IdentifierSchemeLifecycleRequest(action, schemeId, expectedVersion);
    }
}
