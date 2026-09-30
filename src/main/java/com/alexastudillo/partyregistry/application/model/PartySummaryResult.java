package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;

import java.time.Instant;
import java.util.Objects;

/**
 * Projects the six safe root summary fields without loading subtype or identifier data.
 */
public record PartySummaryResult(
        PartyId partyId,
        PartyType type,
        String displayName,
        PartyRecordStatus recordStatus,
        Instant createdAt,
        PartyVersion version) {

    public PartySummaryResult {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(recordStatus, "recordStatus");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(version, "version");
    }

    /** Returns the exact keyset position represented by this summary. */
    public PartyPagePosition position() {
        return new PartyPagePosition(createdAt, partyId);
    }
}
