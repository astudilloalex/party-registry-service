package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;

import java.time.Instant;
import java.util.Objects;

/** Describes independently durable private withdrawal audit using accepted tenant/actor and the resulting version. */
public record PartyIdentifierActionAudit(RequestMetadata requestMetadata, PartyId partyId, PartyIdentifierId identifierId,
        PartyIdentifierVersion resultingVersion, PartyIdentifierOperation operation, Instant occurredAt,
        PartyIdentifierReason reason) {

    public PartyIdentifierActionAudit {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(identifierId, "identifierId");
        Objects.requireNonNull(resultingVersion, "resultingVersion");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(reason, "reason");
        if ((operation != PartyIdentifierOperation.REJECT && operation != PartyIdentifierOperation.REVOKE)
                || resultingVersion.value() == 0) {
            throw new IllegalArgumentException("Identifier withdrawal audit action or version is invalid");
        }
    }

    @Override
    public String toString() {
        return "PartyIdentifierActionAudit[operation=" + operation + ", reason=<redacted>]";
    }
}
