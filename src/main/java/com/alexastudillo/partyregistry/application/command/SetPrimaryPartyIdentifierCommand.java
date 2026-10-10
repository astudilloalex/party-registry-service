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

/** Requests explicit scoped primary selection with one target version and an effective replacement choice. */
public record SetPrimaryPartyIdentifierCommand(RequestMetadata requestMetadata, PartyId partyId,
        PartyIdentifierId identifierId, PartyIdentifierVersion expectedVersion,
        @Nullable String idempotencyKey, boolean replaceCurrentPrimary) {

    public SetPrimaryPartyIdentifierCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(identifierId, "identifierId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        PartyIdentifierCommandKeys.validateOptional(idempotencyKey);
    }

    public PartyIdentifierActionRequest effectiveRequest() {
        return new PartyIdentifierActionRequest(PartyIdentifierOperation.SET_PRIMARY, partyId, identifierId,
                expectedVersion, PartyIdentifierReason.none(), replaceCurrentPrimary);
    }

    @Override
    public String toString() {
        return "SetPrimaryPartyIdentifierCommand[input=<redacted>]";
    }
}
