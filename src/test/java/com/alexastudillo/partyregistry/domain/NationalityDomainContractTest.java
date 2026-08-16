package com.alexastudillo.partyregistry.domain;

import static com.alexastudillo.partyregistry.domain.DomainContractSupport.assertDomainViolation;
import static com.alexastudillo.partyregistry.domain.DomainContractSupport.enumValue;
import static com.alexastudillo.partyregistry.domain.DomainContractSupport.invoke;
import static com.alexastudillo.partyregistry.domain.DomainContractSupport.invokeStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NationalityDomainContractTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);
    private static final UUID NATIONALITY_ID = UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890ad");

    @Test
    void nullEndDateIsActiveAndAValidEndDateImmediatelyCreatesRetainedHistory() {
        Object active = nationality(NATIONALITY_ID, "EC", true, TODAY.minusYears(1), null);
        Object ended = invoke(active, "end", TODAY, TODAY);

        assertTrue((Boolean) invoke(active, "isActive"));
        assertFalse((Boolean) invoke(ended, "isActive"));
        assertNotSame(active, ended);
        assertEquals(NATIONALITY_ID, invoke(ended, "id"));
        assertEquals(TODAY, invoke(ended, "validUntil"));
    }

    @ParameterizedTest
    @CsvSource({"2026-08-16,2026-08-16", "2026-08-15,2026-08-14", "2026-08-16,"})
    void futureOrReversedValidityDatesAreRejected(String fromText, String untilText) {
        LocalDate from = LocalDate.parse(fromText);
        LocalDate until = untilText == null ? null : LocalDate.parse(untilText);
        assertDomainViolation(() -> nationality(NATIONALITY_ID, "EC", false, from, until));
    }

    @Test
    void nationalityIsRejectedForLegalEntityParty() {
        Object nationality = nationality(NATIONALITY_ID, "EC", false, TODAY, null);
        assertDomainViolation(() -> invokeStatic(
                "NationalityPolicy", "validate", enumValue("PartyType", "LEGAL_ENTITY"), List.of(nationality)));
    }

    @Test
    void duplicateActiveCountryIsRejectedWhileEndedHistoryIsRetained() {
        Object historical = nationality(NATIONALITY_ID, "EC", false, TODAY.minusYears(2), TODAY.minusYears(1));
        Object active = nationality(UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890ae"), "EC", false, TODAY, null);
        invokeStatic("NationalityPolicy", "validate", enumValue("PartyType", "NATURAL_PERSON"), List.of(historical, active));

        Object duplicate = nationality(UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890af"), "EC", false, TODAY, null);
        assertDomainViolation(() -> invokeStatic(
                "NationalityPolicy", "validate", enumValue("PartyType", "NATURAL_PERSON"), List.of(active, duplicate)));
    }

    @Test
    void moreThanOneActivePrimaryNationalityIsRejected() {
        Object ecuadorian = nationality(NATIONALITY_ID, "EC", true, TODAY, null);
        Object american = nationality(
                UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890b0"), "US", true, TODAY, null);
        assertDomainViolation(() -> invokeStatic(
                "NationalityPolicy", "validate", enumValue("PartyType", "NATURAL_PERSON"), List.of(ecuadorian, american)));
    }

    private static Object nationality(UUID id, String country, boolean primary, LocalDate from, LocalDate until) {
        return invokeStatic("Nationality", "create", id, country, primary, from, until, TODAY);
    }
}
