package com.alexastudillo.partyregistry.application.query;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;

import java.util.Objects;
import java.util.Optional;

/** Requests a global filtered page with continuation bound to the current request tenant. */
public record ListIdentifierSchemesQuery(
        RequestMetadata requestMetadata, IdentifierSchemeSearchCriteria criteria, Optional<String> cursor) {
    public ListIdentifierSchemesQuery {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(cursor, "cursor");
    }
}
