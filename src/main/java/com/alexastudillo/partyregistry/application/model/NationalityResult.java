package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyNationality;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Detaches the public nationality fields from a persistence session and its audit implementation. */
public record NationalityResult(
        NationalityId nationalityId, PartyId partyId, String countryCode, boolean isPrimary,
        @Nullable LocalDate validFrom, @Nullable LocalDate validUntil,
        Instant createdAt, Instant updatedAt) {

    public NationalityResult {
        Objects.requireNonNull(nationalityId, "nationalityId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Projects a validated domain record into detached response data. */
    public static NationalityResult fromAggregate(PartyNationality nationality) {
        Objects.requireNonNull(nationality, "nationality");
        return new NationalityResult(nationality.nationalityId(), nationality.partyId(),
                nationality.countryCode(), nationality.primary(), nationality.period().validFrom(),
                nationality.period().validUntil(), nationality.auditInfo().createdAt(),
                nationality.auditInfo().updatedAt());
    }
}
