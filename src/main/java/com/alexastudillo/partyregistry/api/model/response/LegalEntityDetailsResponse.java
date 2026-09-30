package com.alexastudillo.partyregistry.api.model.response;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Represents legal-entity detail fields in the public API contract.
 */
public record LegalEntityDetailsResponse(
        String legalName,
        @Nullable String tradeName,
        @Nullable String legalFormCode,
        String incorporationCountryCode,
        @Nullable LocalDate incorporatedOn,
        @Nullable LocalDate dissolvedOn) {
}
