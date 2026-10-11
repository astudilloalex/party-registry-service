package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;

import java.util.Objects;

/** Carries only stored safe summary/mask fields and an internal precise identifier navigation position. */
public record IdentifierSearchHit(PartyId partyId, PartyType type, String displayName, PartyRecordStatus recordStatus,
        String schemeCode, String maskedValue, PartyIdentifierStatus status, boolean primary,
        PartyIdentifierPagePosition position) {
    public IdentifierSearchHit {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(recordStatus, "recordStatus");
        Objects.requireNonNull(schemeCode, "schemeCode");
        Objects.requireNonNull(maskedValue, "maskedValue");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(position, "position");
    }
}
