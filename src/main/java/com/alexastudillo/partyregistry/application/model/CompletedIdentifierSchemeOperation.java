package com.alexastudillo.partyregistry.application.model;

import java.util.Objects;

/** Retains exact effective intent and its immutable originally accepted catalog result. */
public record CompletedIdentifierSchemeOperation(
        IdentifierSchemeEffectiveRequest request, IdentifierSchemeResult result) {
    public CompletedIdentifierSchemeOperation {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(result, "result");
    }
}
