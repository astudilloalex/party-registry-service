package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Carries effective AND-combined Party filters and a bounded requested page size.
 */
public record PartySearchCriteria(
        @Nullable PartyType type,
        @Nullable PartyRecordStatus recordStatus,
        PartyNamePredicate name,
        @Nullable Instant createdFrom,
        @Nullable Instant createdTo,
        int limit) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAXIMUM_LIMIT = 200;

    public PartySearchCriteria {
        Objects.requireNonNull(name, "name");
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("Party page size is outside the supported range");
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new IllegalArgumentException("Party creation interval is inverted");
        }
    }

    /** Tests the inclusive creation interval, preserving the exact instant precision. */
    public boolean containsCreationInstant(Instant createdAt) {
        Objects.requireNonNull(createdAt, "createdAt");
        return (createdFrom == null || !createdAt.isBefore(createdFrom))
                && (createdTo == null || !createdAt.isAfter(createdTo));
    }
}
