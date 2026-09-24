package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleRequest;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;

import java.util.Objects;
import java.util.Optional;

/**
 * Requests one tenant-scoped lifecycle action with a mandatory expected version and optional exact replay key.
 */
public record ChangePartyLifecycleCommand(
        RequestMetadata requestMetadata,
        PartyId partyId,
        PartyVersion expectedVersion,
        PartyLifecycleAction action,
        Optional<String> idempotencyKey) {

    public ChangePartyLifecycleCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        idempotencyKey.ifPresent(key -> {
            if (key.isBlank() || key.codePointCount(0, key.length()) > 128) {
                throw new IllegalArgumentException("Lifecycle replay key is invalid");
            }
        });
    }

    /** Excludes the current actor, correlation, and key from effective command equivalence. */
    public PartyLifecycleRequest effectiveRequest() {
        return new PartyLifecycleRequest(action, partyId, expectedVersion);
    }
}
