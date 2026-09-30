package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies encoding, restoration, and validation of detached nationality
 * snapshot fields.
 */
class NationalitySnapshotFieldsTest {

    private static final NationalityId NATIONALITY_ID = new NationalityId(
            UUID.fromString("01991c8d-4800-7000-8000-000000000001"));
    private static final PartyId PARTY_ID = new PartyId(
            UUID.fromString("01991c8d-4800-7000-8000-000000000002"));
    private static final String COUNTRY_CODE = "EC";
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void roundTripsValidNationalityResult() {
        NationalityResult original = new NationalityResult(
                NATIONALITY_ID,
                PARTY_ID,
                COUNTRY_CODE,
                true,
                LocalDate.of(2020, Month.JANUARY, 1),
                LocalDate.of(2030, Month.DECEMBER, 31),
                CREATED_AT,
                UPDATED_AT);

        ObjectNode encoded = NationalitySnapshotFields.result(original);
        NationalityResult restored = NationalitySnapshotFields.readResult(encoded);

        assertEquals(original, restored);
    }

    @Test
    void roundTripsNationalityResultWithNullDates() {
        NationalityResult original = new NationalityResult(
                NATIONALITY_ID,
                PARTY_ID,
                COUNTRY_CODE,
                false,
                null,
                null,
                CREATED_AT,
                UPDATED_AT);

        ObjectNode encoded = NationalitySnapshotFields.result(original);
        NationalityResult restored = NationalitySnapshotFields.readResult(encoded);

        assertEquals(original, restored);
        assertNull(restored.validFrom());
        assertNull(restored.validUntil());
    }

    @Test
    void rejectsMissingField() {
        ObjectNode encoded = NationalitySnapshotFields.result(new NationalityResult(
                NATIONALITY_ID,
                PARTY_ID,
                COUNTRY_CODE,
                true,
                null,
                null,
                CREATED_AT,
                UPDATED_AT));
        encoded.remove("isPrimary");

        assertThrows(ApplicationException.class, () -> NationalitySnapshotFields.readResult(encoded));
    }

    @Test
    void rejectsExtraField() {
        ObjectNode encoded = NationalitySnapshotFields.result(new NationalityResult(
                NATIONALITY_ID,
                PARTY_ID,
                COUNTRY_CODE,
                true,
                null,
                null,
                CREATED_AT,
                UPDATED_AT));
        encoded.put("extraField", "unexpected");

        assertThrows(ApplicationException.class, () -> NationalitySnapshotFields.readResult(encoded));
    }

    @Test
    void rejectsInvalidBooleanType() {
        ObjectNode encoded = NationalitySnapshotFields.result(new NationalityResult(
                NATIONALITY_ID,
                PARTY_ID,
                COUNTRY_CODE,
                true,
                null,
                null,
                CREATED_AT,
                UPDATED_AT));
        encoded.put("isPrimary", "not-a-boolean");

        assertThrows(ApplicationException.class, () -> NationalitySnapshotFields.readResult(encoded));
    }

    @Test
    void rejectsInvalidUuidFormat() {
        ObjectNode encoded = NationalitySnapshotFields.result(new NationalityResult(
                NATIONALITY_ID,
                PARTY_ID,
                COUNTRY_CODE,
                true,
                null,
                null,
                CREATED_AT,
                UPDATED_AT));
        encoded.put("nationalityId", "not-a-uuid");

        assertThrows(IllegalArgumentException.class, () -> NationalitySnapshotFields.readResult(encoded));
    }

    @Test
    void rejectsInvalidDateFormat() {
        ObjectNode encoded = NationalitySnapshotFields.result(new NationalityResult(
                NATIONALITY_ID,
                PARTY_ID,
                COUNTRY_CODE,
                true,
                LocalDate.of(2020, Month.JANUARY, 1),
                null,
                CREATED_AT,
                UPDATED_AT));
        encoded.put("validFrom", "2020/01/01");

        assertThrows(RuntimeException.class, () -> NationalitySnapshotFields.readResult(encoded));
    }
}
