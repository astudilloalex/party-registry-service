package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Adds trusted evaluation times to a Party activation request.
 */
public record PartyActivationCandidate(
        RequestMetadata requestMetadata,
        PartyId partyId,
        PartyVersion expectedVersion,
        LocalDate evaluatedOn,
        Instant occurredAt) {

    public PartyActivationCandidate {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(evaluatedOn, "evaluatedOn");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
