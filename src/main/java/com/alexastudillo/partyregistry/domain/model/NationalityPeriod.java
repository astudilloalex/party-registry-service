package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/** Represents an inclusive nationality interval with optional unbounded ends. */
public record NationalityPeriod(@Nullable LocalDate validFrom, @Nullable LocalDate validUntil) {

    public NationalityPeriod {
        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            throw new DomainValidationException(DomainViolation.NATIONALITY_VALIDITY_DATE_ORDER,
                    "Nationality end date cannot precede start date");
        }
    }

    /** Returns whether both inclusive intervals share at least one calendar day. */
    public boolean overlaps(NationalityPeriod other) {
        Objects.requireNonNull(other, "other");
        return (validUntil == null || other.validFrom == null || !validUntil.isBefore(other.validFrom))
                && (other.validUntil == null || validFrom == null || !other.validUntil.isBefore(validFrom));
    }

    /** Returns whether the date lies within the interval, including its endpoints. */
    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return (validFrom == null || !date.isBefore(validFrom))
                && (validUntil == null || !date.isAfter(validUntil));
    }

    /** Merges only supplied bounds, preserving omitted values and allowing explicit null. */
    public NationalityPeriod merge(FieldUpdate<LocalDate> from, FieldUpdate<LocalDate> until) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(until, "until");
        return new NationalityPeriod(from.isPresent() ? from.value() : validFrom,
                until.isPresent() ? until.value() : validUntil);
    }
}
