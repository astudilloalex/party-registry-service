package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.quarkus.runtime.annotations.RegisterForReflection;

/** Represents the effective optional primary-replacement choice at the HTTP boundary. */
@JsonDeserialize(using = PartyIdentifierPrimaryRequestDeserializer.class)
@RegisterForReflection
public record PartyIdentifierPrimaryRequest(boolean replaceCurrentPrimary) { }
