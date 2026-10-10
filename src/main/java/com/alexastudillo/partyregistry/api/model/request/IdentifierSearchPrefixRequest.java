package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Carries strict literal-prefix input with an effective integer limit and opaque nullable continuation. */
@RegisterForReflection
@JsonDeserialize(using = IdentifierSearchPrefixRequestDeserializer.class)
public record IdentifierSearchPrefixRequest(String identifierSchemeCode, String prefix,
        @Nullable List<PartyIdentifierStatus> identifierStatuses, @Nullable List<PartyRecordStatus> partyRecordStatuses,
        int limit, @Nullable String cursor) {
    public IdentifierSearchPrefixRequest {
        identifierStatuses = identifierStatuses == null ? null : List.copyOf(identifierStatuses);
        partyRecordStatuses = partyRecordStatuses == null ? null : List.copyOf(partyRecordStatuses);
    }
    @Override public String toString() { return "IdentifierSearchPrefixRequest[redacted]"; }
}
