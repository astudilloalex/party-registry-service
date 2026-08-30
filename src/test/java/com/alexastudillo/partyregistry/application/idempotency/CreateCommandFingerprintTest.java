package com.alexastudillo.partyregistry.application.idempotency;

import com.alexastudillo.partyregistry.application.command.CreateNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies canonical effective-command serialization and fingerprinting.
 */
class CreateCommandFingerprintTest {

    private static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
    private static final UUID PROCESS_ID = UUID.fromString(
            "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5");

    @Test
    void producesDeterministicLowercaseSha256Hashes() {
        CreateNaturalPersonCommand command = command(
                metadata(TENANT_ID, "operator", PROCESS_ID),
                "key-1",
                "Ada Lovelace",
                "Ada",
                "Lovelace",
                "Ada",
                LocalDate.of(1815, Month.DECEMBER, 10),
                LocalDate.of(1852, Month.NOVEMBER, 27),
                "GB");

        String first = CreateCommandFingerprint.hashOf(CreateNaturalPersonCommand.OPERATION, command);
        String second = CreateCommandFingerprint.hashOf(CreateNaturalPersonCommand.OPERATION, command);

        assertEquals(first, second);
        assertTrue(first.matches("^[0-9a-f]{64}$"));
    }

    @Test
    void representsOmittedAndNullOptionalValuesUniformly() {
        CreateNaturalPersonCommand command = command(
                metadata(TENANT_ID, "operator", PROCESS_ID),
                "key-1",
                null,
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                null);

        assertEquals(
                "{\"operation\":\"CREATE_NATURAL_PERSON\","
                        + "\"tenantId\":\"0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1\","
                        + "\"displayName\":null,\"givenNames\":\"Ada\","
                        + "\"familyNames\":\"Lovelace\",\"preferredName\":null,"
                        + "\"birthDate\":null,\"dateOfDeath\":null,"
                        + "\"birthCountryCode\":null}",
                CreateCommandFingerprint.canonicalize(CreateNaturalPersonCommand.OPERATION, command));
    }

    @Test
    void excludesIdempotencyAndCorrelationValues() {
        CreateNaturalPersonCommand first = command(
                metadata(TENANT_ID, "operator-a", PROCESS_ID),
                "key-a",
                null,
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                null);
        CreateNaturalPersonCommand second = command(
                metadata(
                        TENANT_ID,
                        "operator-b",
                        UUID.fromString("0198ce2c-609c-7c04-a977-425e7d60c58d")),
                "key-b",
                null,
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                null);

        assertEquals(
                CreateCommandFingerprint.hashOf(CreateNaturalPersonCommand.OPERATION, first),
                CreateCommandFingerprint.hashOf(CreateNaturalPersonCommand.OPERATION, second));
    }

    @Test
    void distinguishesEveryMeaningfulField() {
        CreateNaturalPersonCommand baseline = command(
                metadata(TENANT_ID, "operator", PROCESS_ID),
                "key-1",
                "Ada Lovelace",
                "Ada",
                "Lovelace",
                "Ada",
                LocalDate.of(1815, Month.DECEMBER, 10),
                LocalDate.of(1852, Month.NOVEMBER, 27),
                "GB");
        String baselineHash = CreateCommandFingerprint.hashOf(
                CreateNaturalPersonCommand.OPERATION,
                baseline);

        List<CreateNaturalPersonCommand> variants = List.of(
                command(metadata(new TenantId(UUID.randomUUID()), "operator", PROCESS_ID),
                        "key-1", "Ada Lovelace", "Ada", "Lovelace", "Ada",
                        LocalDate.of(1815, Month.DECEMBER, 10), LocalDate.of(1852, Month.NOVEMBER, 27), "GB"),
                command(metadata(TENANT_ID, "operator", PROCESS_ID),
                        "key-1", "Augusta Ada King", "Ada", "Lovelace", "Ada",
                        LocalDate.of(1815, Month.DECEMBER, 10), LocalDate.of(1852, Month.NOVEMBER, 27), "GB"),
                command(metadata(TENANT_ID, "operator", PROCESS_ID),
                        "key-1", "Ada Lovelace", "Augusta Ada", "Lovelace", "Ada",
                        LocalDate.of(1815, Month.DECEMBER, 10), LocalDate.of(1852, Month.NOVEMBER, 27), "GB"),
                command(metadata(TENANT_ID, "operator", PROCESS_ID),
                        "key-1", "Ada Lovelace", "Ada", "King", "Ada",
                        LocalDate.of(1815, Month.DECEMBER, 10), LocalDate.of(1852, Month.NOVEMBER, 27), "GB"),
                command(metadata(TENANT_ID, "operator", PROCESS_ID),
                        "key-1", "Ada Lovelace", "Ada", "Lovelace", "Augusta",
                        LocalDate.of(1815, Month.DECEMBER, 10), LocalDate.of(1852, Month.NOVEMBER, 27), "GB"),
                command(metadata(TENANT_ID, "operator", PROCESS_ID),
                        "key-1", "Ada Lovelace", "Ada", "Lovelace", "Ada",
                        LocalDate.of(1815, Month.DECEMBER, 11), LocalDate.of(1852, Month.NOVEMBER, 27), "GB"),
                command(metadata(TENANT_ID, "operator", PROCESS_ID),
                        "key-1", "Ada Lovelace", "Ada", "Lovelace", "Ada",
                        LocalDate.of(1815, Month.DECEMBER, 10), LocalDate.of(1852, Month.NOVEMBER, 28), "GB"),
                command(metadata(TENANT_ID, "operator", PROCESS_ID),
                        "key-1", "Ada Lovelace", "Ada", "Lovelace", "Ada",
                        LocalDate.of(1815, Month.DECEMBER, 10), LocalDate.of(1852, Month.NOVEMBER, 27), "EC"));

        variants.forEach(variant -> assertNotEquals(
                baselineHash,
                CreateCommandFingerprint.hashOf(CreateNaturalPersonCommand.OPERATION, variant)));
    }

    @Test
    void scopesTheFingerprintByOperation() {
        CreateNaturalPersonCommand command = command(
                metadata(TENANT_ID, "operator", PROCESS_ID),
                "key-1",
                null,
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                null);

        assertNotEquals(
                CreateCommandFingerprint.hashOf(CreateNaturalPersonCommand.OPERATION, command),
                CreateCommandFingerprint.hashOf("IMPORT_NATURAL_PERSON", command));
    }

    @Test
    void preservesMalformedUtf16CodeUnitsWithoutUtf8ReplacementCollisions() {
        CreateNaturalPersonCommand malformed = command(
                metadata(TENANT_ID, "operator", PROCESS_ID),
                "key-1",
                null,
                "\uD800",
                "Lovelace",
                null,
                null,
                null,
                null);
        CreateNaturalPersonCommand replacementCharacter = command(
                metadata(TENANT_ID, "operator", PROCESS_ID),
                "key-1",
                null,
                "?",
                "Lovelace",
                null,
                null,
                null,
                null);

        assertTrue(CreateCommandFingerprint.canonicalize(
                CreateNaturalPersonCommand.OPERATION,
                malformed).contains("\\ud800"));
        assertNotEquals(
                CreateCommandFingerprint.hashOf(CreateNaturalPersonCommand.OPERATION, malformed),
                CreateCommandFingerprint.hashOf(
                        CreateNaturalPersonCommand.OPERATION,
                        replacementCharacter));
    }

    private static CreateNaturalPersonCommand command(
            RequestMetadata metadata,
            String idempotencyKey,
            String displayName,
            String givenNames,
            String familyNames,
            String preferredName,
            LocalDate birthDate,
            LocalDate dateOfDeath,
            String birthCountryCode) {
        return new CreateNaturalPersonCommand(
                metadata,
                idempotencyKey,
                displayName,
                givenNames,
                familyNames,
                preferredName,
                birthDate,
                dateOfDeath,
                birthCountryCode);
    }

    private static RequestMetadata metadata(TenantId tenantId, String userId, UUID processId) {
        return new RequestMetadata(tenantId, userId, processId);
    }
}
