package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jspecify.annotations.Nullable;

/** Retains exact supplied optional withdrawal reasons while redacting their diagnostic representation. */
@JsonDeserialize(using = PartyIdentifierReasonRequestDeserializer.class)
@RegisterForReflection
public record PartyIdentifierReasonRequest(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Nullable String reasonCode,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Nullable String reasonDetail) {
    @Override public String toString() { return "PartyIdentifierReasonRequest[reasons=<redacted>]"; }
}
