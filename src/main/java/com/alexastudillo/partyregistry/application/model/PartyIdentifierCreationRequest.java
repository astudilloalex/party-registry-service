package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/** Defines original parsed creation identity before any write normalization, excluding retry attribution and key. */
public record PartyIdentifierCreationRequest(PartyId partyId, String identifierSchemeCode, String value,
        @Nullable String issuerCode, @Nullable LocalDate issuedOn, @Nullable LocalDate expiresOn, boolean isPrimary) {

    public PartyIdentifierCreationRequest {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(identifierSchemeCode, "identifierSchemeCode");
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return "PartyIdentifierCreationRequest[redacted]";
    }
}
