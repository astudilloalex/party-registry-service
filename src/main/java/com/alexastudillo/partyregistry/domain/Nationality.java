package com.alexastudillo.partyregistry.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Nationality(
        UUID id, CountryCode countryCode, boolean primary, LocalDate validFrom, LocalDate validUntil) {

    public Nationality {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(countryCode, "countryCode");
        LifeDatePolicy.validate(validFrom, validUntil);
    }

    public static Nationality create(
            UUID id, String countryCode, boolean primary, LocalDate validFrom, LocalDate validUntil, LocalDate today) {
        Objects.requireNonNull(today, "today");
        requireNotFuture(validFrom, today, "validFrom");
        requireNotFuture(validUntil, today, "validUntil");
        return new Nationality(id, CountryCode.of(countryCode), primary, validFrom, validUntil);
    }

    public boolean isActive() {
        return validUntil == null;
    }

    public Nationality end(LocalDate endDate, LocalDate today) {
        if (!isActive()) {
            throw new DomainViolation("An ended nationality cannot be ended again");
        }
        return create(id, countryCode.value(), primary, validFrom, endDate, today);
    }

    private static void requireNotFuture(LocalDate value, LocalDate today, String field) {
        if (value != null && value.isAfter(today)) {
            throw new DomainViolation(field + " must not be in the future");
        }
    }
}
