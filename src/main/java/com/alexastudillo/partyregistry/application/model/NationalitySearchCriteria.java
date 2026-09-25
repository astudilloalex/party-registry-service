package com.alexastudillo.partyregistry.application.model;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/** Carries effective AND-combined nationality filters and a bounded page size. */
public record NationalitySearchCriteria(
        @Nullable String countryCode, @Nullable Boolean isPrimary, LocalDate asOfDate,
        boolean includeExpired, int limit) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAXIMUM_LIMIT = 200;

    public NationalitySearchCriteria {
        Objects.requireNonNull(asOfDate, "asOfDate");
        if (countryCode != null && !countryCode.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("Nationality country filter must be uppercase ASCII alpha-2");
        }
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("Nationality page size is outside the supported range");
        }
    }
}
