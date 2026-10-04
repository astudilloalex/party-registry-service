package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;

import java.util.Objects;

/** Defines effective action identity with an independent identifier version and exact action-specific defaults. */
public record PartyIdentifierActionRequest(PartyIdentifierOperation operation, PartyId partyId,
        PartyIdentifierId identifierId, PartyIdentifierVersion expectedVersion,
        PartyIdentifierReason reason, boolean replaceCurrentPrimary) {

    public PartyIdentifierActionRequest {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(identifierId, "identifierId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(reason, "reason");
        if (operation == PartyIdentifierOperation.CREATE
                || (operation != PartyIdentifierOperation.SET_PRIMARY && replaceCurrentPrimary)
                || ((operation == PartyIdentifierOperation.VERIFY || operation == PartyIdentifierOperation.SET_PRIMARY)
                    && !reason.isAbsent())) {
            throw new IllegalArgumentException("Identifier effective action input is inconsistent");
        }
    }

    @Override
    public String toString() {
        return "PartyIdentifierActionRequest[operation=" + operation + ", input=<redacted>]";
    }
}
