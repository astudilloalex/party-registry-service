package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;
import java.util.Objects;

/** Retains independent omission/clearing markers for the three permitted identifier metadata properties. */
@JsonDeserialize(using = PartyIdentifierPatchRequestDeserializer.class)
@RegisterForReflection
public record PartyIdentifierPatchRequest(@ValidIssuerCode FieldUpdate<String> issuerCode, FieldUpdate<LocalDate> issuedOn, FieldUpdate<LocalDate> expiresOn) {
    public PartyIdentifierPatchRequest {
        Objects.requireNonNull(issuerCode, "issuerCode"); Objects.requireNonNull(issuedOn, "issuedOn"); Objects.requireNonNull(expiresOn, "expiresOn");
    }

    /** Reports whether no metadata property was supplied, independently of nullable values. */
    public boolean empty() { return !issuerCode.isPresent() && !issuedOn.isPresent() && !expiresOn.isPresent(); }
}
