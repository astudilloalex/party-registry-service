package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierReason;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Requests verification or withdrawal with exact optional reasons, independent version and optional replay key. */
public record ChangePartyIdentifierLifecycleCommand(RequestMetadata requestMetadata, PartyId partyId,
        PartyIdentifierId identifierId, PartyIdentifierVersion expectedVersion, PartyIdentifierOperation operation,
        @Nullable String idempotencyKey, PartyIdentifierReason reason) {

    public ChangePartyIdentifierLifecycleCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        new PartyIdentifierActionRequest(operation, partyId, identifierId, expectedVersion, reason, false);
        if (operation == PartyIdentifierOperation.SET_PRIMARY) {
            throw new IllegalArgumentException("Primary selection requires its own identifier command");
        }
        PartyIdentifierCommandKeys.validateOptional(idempotencyKey);
    }

    public PartyIdentifierActionRequest effectiveRequest() {
        return new PartyIdentifierActionRequest(operation, partyId, identifierId, expectedVersion, reason, false);
    }

    @Override
    public String toString() {
        return "ChangePartyIdentifierLifecycleCommand[operation=" + operation + ", input=<redacted>]";
    }
}
