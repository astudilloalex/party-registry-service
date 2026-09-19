package com.alexastudillo.partyregistry.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Describes legal-detail changes while distinguishing omission from an explicit null.
 */
public record LegalEntityPatch(
        FieldUpdate<String> legalName,
        FieldUpdate<String> tradeName,
        FieldUpdate<String> legalFormCode,
        FieldUpdate<String> incorporationCountryCode,
        FieldUpdate<LocalDate> incorporatedOn,
        FieldUpdate<LocalDate> dissolvedOn) {

    public LegalEntityPatch {
        Objects.requireNonNull(legalName, "legalName");
        Objects.requireNonNull(tradeName, "tradeName");
        Objects.requireNonNull(legalFormCode, "legalFormCode");
        Objects.requireNonNull(incorporationCountryCode, "incorporationCountryCode");
        Objects.requireNonNull(incorporatedOn, "incorporatedOn");
        Objects.requireNonNull(dissolvedOn, "dissolvedOn");
    }

    /** Returns a patch in which every legal-detail property is omitted. */
    public static LegalEntityPatch empty() {
        return new LegalEntityPatch(
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
    }

    public boolean isEmpty() {
        return !legalName.isPresent()
                && !tradeName.isPresent()
                && !legalFormCode.isPresent()
                && !incorporationCountryCode.isPresent()
                && !incorporatedOn.isPresent()
                && !dissolvedOn.isPresent();
    }
}
