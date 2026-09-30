package com.alexastudillo.partyregistry.application.query;

import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Requests tenant-qualified summaries with effective criteria and an optional opaque continuation.
 */
public record ListPartiesQuery(RequestMetadata metadata, PartySearchCriteria criteria, @Nullable String cursor) {

    public ListPartiesQuery {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(criteria, "criteria");
    }
}
