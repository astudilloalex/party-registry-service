package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.PartyNationality;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;

/** Protects inclusive country and primary periods within a Party's nationality history. */
public final class NationalityPeriodPolicy {

    private NationalityPeriodPolicy() {
    }

    /** Rejects intersecting same-country or primary intervals of another record for the same Party. */
    public static void validate(PartyNationality candidate, Collection<PartyNationality> existing) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(existing, "existing");
        boolean countryConflict = false;
        boolean primaryConflict = false;
        for (PartyNationality other : existing) {
            Objects.requireNonNull(other, "other");
            if (candidate.nationalityId().equals(other.nationalityId())
                    || !candidate.partyId().equals(other.partyId())
                    || !candidate.period().overlaps(other.period())) {
                continue;
            }
            if (candidate.countryCode().equals(other.countryCode())) {
                countryConflict = true;
            }
            if (candidate.primary() && other.primary()) {
                primaryConflict = true;
            }
        }
        if (countryConflict) {
            throw new DomainValidationException(DomainViolation.NATIONALITY_VALIDITY_CONFLICT,
                    "Nationality country periods overlap");
        }
        if (primaryConflict) {
            throw new DomainValidationException(DomainViolation.PRIMARY_NATIONALITY_CONFLICT,
                    "Primary nationality periods overlap");
        }
    }

    /** Requires that the target is effective on the one date chosen for the operation. */
    public static void requireEffective(PartyNationality target, LocalDate date) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(date, "date");
        if (!target.period().contains(date)) {
            throw new DomainValidationException(DomainViolation.NATIONALITY_NOT_EFFECTIVE,
                    "Nationality is not effective on the evaluation date");
        }
    }
}
