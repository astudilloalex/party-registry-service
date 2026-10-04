package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;

import java.time.LocalDate;
import java.util.Objects;

/** Requests presence-aware metadata correction guarded by the independent target identifier version. */
public record PatchPartyIdentifierCommand(RequestMetadata requestMetadata, PartyId partyId, PartyIdentifierId identifierId,
        PartyIdentifierVersion expectedVersion, FieldUpdate<String> issuerCode,
        FieldUpdate<LocalDate> issuedOn, FieldUpdate<LocalDate> expiresOn) {

    public PatchPartyIdentifierCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(identifierId, "identifierId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(issuerCode, "issuerCode");
        Objects.requireNonNull(issuedOn, "issuedOn");
        Objects.requireNonNull(expiresOn, "expiresOn");
        if (!issuerCode.isPresent() && !issuedOn.isPresent() && !expiresOn.isPresent()) {
            throw new IllegalArgumentException("Identifier metadata correction requires a field");
        }
    }

    @Override
    public String toString() {
        return "PatchPartyIdentifierCommand[input=<redacted>]";
    }
}
