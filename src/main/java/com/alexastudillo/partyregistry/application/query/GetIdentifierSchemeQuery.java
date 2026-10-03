package com.alexastudillo.partyregistry.application.query;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSelector;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;

import java.util.Objects;

/** Requests safe current catalog data through a typed global selector. */
public record GetIdentifierSchemeQuery(RequestMetadata requestMetadata, IdentifierSchemeSelector selector) {
    public GetIdentifierSchemeQuery {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(selector, "selector");
    }
}
