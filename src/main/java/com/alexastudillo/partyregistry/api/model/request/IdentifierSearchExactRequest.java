package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Carries a strictly decoded exact selector with omitted-filter distinction and redacted diagnostics. */
@RegisterForReflection
@JsonDeserialize(using = IdentifierSearchExactRequestDeserializer.class)
public record IdentifierSearchExactRequest(String identifierSchemeCode, String value,
        @Nullable List<PartyIdentifierStatus> identifierStatuses, @Nullable List<PartyRecordStatus> partyRecordStatuses) {
    public IdentifierSearchExactRequest {
        identifierStatuses = identifierStatuses == null ? null : List.copyOf(identifierStatuses);
        partyRecordStatuses = partyRecordStatuses == null ? null : List.copyOf(partyRecordStatuses);
    }
    @Override public String toString() { return "IdentifierSearchExactRequest[redacted]"; }
}
