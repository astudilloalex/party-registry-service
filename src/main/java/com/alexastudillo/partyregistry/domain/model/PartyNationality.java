package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

import java.util.Objects;

/** Holds a Party's normalized country designation, interval, and modification audit. */
public record PartyNationality(
        NationalityId nationalityId,
        PartyId partyId,
        String countryCode,
        boolean primary,
        NationalityPeriod period,
        AuditInfo auditInfo) {

    public PartyNationality {
        Objects.requireNonNull(nationalityId, "nationalityId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(auditInfo, "auditInfo");
        if (countryCode == null || countryCode.length() != 2
                || countryCode.charAt(0) < 'A' || countryCode.charAt(0) > 'Z'
                || countryCode.charAt(1) < 'A' || countryCode.charAt(1) > 'Z') {
            throw new DomainValidationException(DomainViolation.NATIONALITY_COUNTRY_CODE_INVALID,
                    "Nationality country code must be two uppercase ASCII letters");
        }
    }

    /** Returns a new record with updated bounds and audit, without changing its identity or designation. */
    public PartyNationality withPeriod(NationalityPeriod next, AuditInfo updatedAudit) {
        return new PartyNationality(nationalityId, partyId, countryCode, primary, next, updatedAudit);
    }

    /** Returns a new record with an updated designation and audit. */
    public PartyNationality withPrimary(boolean next, AuditInfo updatedAudit) {
        return new PartyNationality(nationalityId, partyId, countryCode, next, period, updatedAudit);
    }
}
