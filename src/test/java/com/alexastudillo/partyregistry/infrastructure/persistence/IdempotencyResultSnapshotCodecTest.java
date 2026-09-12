package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies explicit legacy and registration idempotency snapshot schemas.
 */
class IdempotencyResultSnapshotCodecTest {

    private static final PartyId NATURAL_PARTY_ID = new PartyId(
            UUID.fromString("01991c8d-4800-7000-8000-000000000001"));
    private static final PartyId LEGAL_PARTY_ID = new PartyId(
            UUID.fromString("01991c8d-4800-7000-8000-000000000002"));
    private static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("01991c8d-4800-7000-8000-000000000003"));
    private static final IdentifierSchemeId SCHEME_ID = new IdentifierSchemeId(
            UUID.fromString("01991c8d-4800-7000-8000-000000000004"));
    private static final PartyIdentifierId IDENTIFIER_ID = new PartyIdentifierId(
            UUID.fromString("01991c8d-4800-7000-8000-000000000005"));
    private static final Instant CREATED_AT = Instant.parse("2026-09-04T10:15:30.123456Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-09-04T11:16:31.654321Z");
    private static final String MASKED_VALUE = "****************9421";

    private final IdempotencyResultSnapshotCodec codec = new IdempotencyResultSnapshotCodec();

    @Test
    void roundTripsExactNaturalPersonRegistrationResult() {
        PartyRegistrationResult original = registration(
                naturalPerson(
                        "Ada Byron",
                        LocalDate.parse("1815-12-10"),
                        LocalDate.parse("1852-11-27"),
                        "GB"),
                completeIdentifier(NATURAL_PARTY_ID),
                PartyRegistrationOutcome.CREATED);

        IdempotencyResultSnapshot snapshot = codec.encodeRegistration(original);

        assertEquals(IdempotencyResultSnapshotCodec.REGISTRATION_SCHEMA_VERSION,
                snapshot.schemaVersion());
        assertEquals(original, codec.decodeRegistration(snapshot));
    }

    @Test
    void roundTripsExactLegalEntityRegistrationResultAndReplayOutcome() {
        PartyRegistrationResult original = registration(
                legalEntity(
                        "Analytical Engines",
                        "LTD",
                        LocalDate.parse("1843-01-01"),
                        LocalDate.parse("2026-01-31")),
                completeIdentifier(LEGAL_PARTY_ID),
                PartyRegistrationOutcome.REPLAYED);

        PartyRegistrationResult restored = codec.decodeRegistration(
                codec.encodeRegistration(original));

        assertEquals(original, restored);
        assertEquals(PartyRegistrationOutcome.REPLAYED, restored.outcome());
    }

    @Test
    void preservesEveryNullableNaturalAndLegalField() {
        PartyRegistrationResult natural = registration(
                naturalPerson(null, null, null, null),
                nullableIdentifier(NATURAL_PARTY_ID),
                PartyRegistrationOutcome.CREATED);
        PartyRegistrationResult legal = registration(
                legalEntity(null, null, null, null),
                nullableIdentifier(LEGAL_PARTY_ID),
                PartyRegistrationOutcome.CREATED);

        assertEquals(natural, codec.decodeRegistration(codec.encodeRegistration(natural)));
        assertEquals(legal, codec.decodeRegistration(codec.encodeRegistration(legal)));
    }

    @Test
    void retainsVersionOneNaturalPersonRoundTrips() {
        NaturalPersonResult original = naturalPerson(
                "Ada Byron",
                LocalDate.parse("1815-12-10"),
                LocalDate.parse("1852-11-27"),
                "GB");

        IdempotencyResultSnapshot snapshot = codec.encodeLegacyNaturalPerson(original);

        assertEquals(NaturalPersonResultSnapshot.CURRENT_SCHEMA_VERSION, snapshot.schemaVersion());
        assertEquals(original, codec.decodeLegacyNaturalPerson(snapshot));
    }

    @Test
    void rejectsColumnPayloadMismatchesUnsupportedVersionsAndCrossVersionDecoding() {
        IdempotencyResultSnapshot versionTwo = codec.encodeRegistration(registration(
                naturalPerson(null, null, null, null),
                nullableIdentifier(NATURAL_PARTY_ID),
                PartyRegistrationOutcome.CREATED));
        ObjectNode mismatchedPayload = (ObjectNode) versionTwo.payload().deepCopy();
        mismatchedPayload.put("schemaVersion", 1);
        ObjectNode unsupportedPayload = (ObjectNode) versionTwo.payload().deepCopy();
        unsupportedPayload.put("schemaVersion", 3);
        IdempotencyResultSnapshot versionOne = codec.encodeLegacyNaturalPerson(
                naturalPerson(null, null, null, null));

        assertSanitizedPersistenceFailure(() -> codec.decodeRegistration(
                new IdempotencyResultSnapshot((short) 1, versionTwo.payload())));
        assertSanitizedPersistenceFailure(() -> codec.decodeRegistration(
                new IdempotencyResultSnapshot((short) 2, mismatchedPayload)));
        assertSanitizedPersistenceFailure(() -> codec.decodeRegistration(
                new IdempotencyResultSnapshot((short) 3, unsupportedPayload)));
        assertSanitizedPersistenceFailure(() -> codec.decodeRegistration(versionOne));
        assertSanitizedPersistenceFailure(() -> codec.decodeLegacyNaturalPerson(versionTwo));
    }

    @Test
    void serializesOnlySafeVersionTwoValues() {
        PartyRegistrationResult original = registration(
                naturalPerson(
                        "Ada Byron",
                        LocalDate.parse("1815-12-10"),
                        LocalDate.parse("1852-11-27"),
                        "GB"),
                completeIdentifier(NATURAL_PARTY_ID),
                PartyRegistrationOutcome.CREATED);

        String json = codec.encodeRegistration(original).payload().toString();
        List<String> forbiddenPropertyNames = List.of(
                "value",
                "completeValue",
                "normalizedValue",
                "encryptedValue",
                "hmac",
                "fingerprint",
                "normalizedValueHash",
                "encryptionKeyVersion",
                "normalizationVersion",
                "keyMaterial",
                "processId",
                "apiResponse",
                "code",
                "data",
                "nonce",
                "aad");
        List<String> forbiddenHighEntropyValues = List.of(
                "complete-6a5537c30e854e9984675b19272fcd87",
                "normalized-307241558f31422190f9ba02a1f54f63",
                "encrypted-26b512bfb17a484886fab0cf93fd48f5",
                "hmac-fingerprint-a751982c68e64d198a292d2fb24cac4d",
                "normalized-hash-779995c77c6f427ba86a4fbaf91d380c",
                "key-version-material-cc982391c661477ba8b10f458787a410",
                "protection-internals-3b16ed79ad3c4471b903809716b4a588",
                "process-id-15ceec2db2664832a81ce8e23cdb7b8f",
                "api-envelope-86c8f14bbad54f3b9406911a78473bc0");

        forbiddenPropertyNames.forEach(name -> assertFalse(json.contains('"' + name + '"'), name));
        forbiddenHighEntropyValues.forEach(value -> assertFalse(json.contains(value), value));
        assertTrue(json.contains(MASKED_VALUE));
        assertTrue(json.contains("initialIdentifier"));
    }

    private static PartyRegistrationResult registration(
            PartyDetailsResult party,
            PartyIdentifierResult identifier,
            PartyRegistrationOutcome outcome) {
        return new PartyRegistrationResult(party, identifier, outcome);
    }

    private static NaturalPersonResult naturalPerson(
            String preferredName,
            LocalDate birthDate,
            LocalDate dateOfDeath,
            String birthCountryCode) {
        return new NaturalPersonResult(
                NATURAL_PARTY_ID,
                TENANT_ID,
                PartyType.NATURAL_PERSON,
                "Ada Lovelace",
                PartyRecordStatus.DRAFT,
                new PartyVersion(41),
                "Ada",
                "Lovelace",
                preferredName,
                birthDate,
                dateOfDeath,
                birthCountryCode,
                CREATED_AT,
                "natural-creator",
                UPDATED_AT,
                "natural-updater");
    }

    private static LegalEntityResult legalEntity(
            String tradeName,
            String legalFormCode,
            LocalDate incorporatedOn,
            LocalDate dissolvedOn) {
        return new LegalEntityResult(
                LEGAL_PARTY_ID,
                TENANT_ID,
                PartyType.LEGAL_ENTITY,
                "Analytical Engines Ltd",
                PartyRecordStatus.DRAFT,
                new PartyVersion(43),
                "Analytical Engines Limited",
                tradeName,
                legalFormCode,
                "GB",
                incorporatedOn,
                dissolvedOn,
                CREATED_AT,
                "legal-creator",
                UPDATED_AT,
                "legal-updater");
    }

    private static PartyIdentifierResult completeIdentifier(PartyId partyId) {
        return new PartyIdentifierResult(
                IDENTIFIER_ID,
                partyId,
                SCHEME_ID,
                "GB-NATIONAL-ID",
                MASKED_VALUE,
                PartyIdentifierStatus.PENDING_VERIFICATION,
                true,
                "ISSUER-42",
                LocalDate.parse("2020-01-02"),
                LocalDate.parse("2030-03-04"),
                Instant.parse("2026-09-04T10:45:30.987654Z"),
                "verifier-42",
                new PartyIdentifierVersion(47),
                CREATED_AT.plusSeconds(1),
                UPDATED_AT.plusSeconds(1));
    }

    private static PartyIdentifierResult nullableIdentifier(PartyId partyId) {
        return new PartyIdentifierResult(
                IDENTIFIER_ID,
                partyId,
                SCHEME_ID,
                "GB-NATIONAL-ID",
                MASKED_VALUE,
                PartyIdentifierStatus.PENDING_VERIFICATION,
                false,
                null,
                null,
                null,
                null,
                null,
                PartyIdentifierVersion.initial(),
                CREATED_AT,
                CREATED_AT);
    }

    private static void assertSanitizedPersistenceFailure(Runnable action) {
        ApplicationException exception = assertThrows(ApplicationException.class, action::run);
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, exception.failure());
        assertEquals("Persistence operation failed", exception.getMessage());
    }
}
