package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.PartyRegistrationCommand;
import com.alexastudillo.partyregistry.application.command.RegisterLegalEntityCommand;
import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies exact canonical registration input and keyed fingerprint comparison.
 */
class HmacRegistrationFingerprintAdapterTest {

    private static final TenantId TENANT_ID = tenant("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1");
    private static final UUID PROCESS_ID = UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5");
    private static final LocalDate ISSUED_ON = LocalDate.of(2020, 1, 2);
    private static final LocalDate EXPIRES_ON = LocalDate.of(2030, 1, 2);

    private final HmacRegistrationFingerprintAdapter adapter = adapterWithKey(
            "fedcba9876543210fedcba9876543210");

    @Test
    void returnsDeterministicLowercaseHmacSha256AndMatchesDecodedHex() {
        PartyRegistrationCommand command = naturalCommand(
                metadata(TENANT_ID, "operator", PROCESS_ID),
                "key-1",
                "Ada Lovelace",
                "Ada",
                "Lovelace",
                "Ada",
                LocalDate.of(1815, 12, 10),
                LocalDate.of(1852, 11, 27),
                "GB",
                identifier("GB-NATIONAL-ID", "AB-1234", "issuer", ISSUED_ON, EXPIRES_ON, true));

        String first = adapter.fingerprint(command);
        String second = adapter.fingerprint(command);

        assertEquals(first, second);
        assertTrue(first.matches("^[0-9a-f]{64}$"));
        assertTrue(adapter.matches(first, second.toUpperCase(java.util.Locale.ROOT)));
    }

    @Test
    void excludesIdempotencyUserAndProcessCorrelationValues() {
        RegisterNaturalPersonCommand first = baselineNatural();
        RegisterNaturalPersonCommand second = naturalCommand(
                metadata(
                        TENANT_ID,
                        "different-operator",
                        UUID.fromString("0198ce2c-609c-7c04-a977-425e7d60c58d")),
                "different-idempotency-key",
                first.displayName(),
                first.givenNames(),
                first.familyNames(),
                first.preferredName(),
                first.birthDate(),
                first.dateOfDeath(),
                first.birthCountryCode(),
                first.initialIdentifier());

        assertEquals(adapter.fingerprint(first), adapter.fingerprint(second));
    }

    @Test
    void distinguishesEveryEffectiveNaturalPersonAndIdentifierField() {
        RegisterNaturalPersonCommand baseline = baselineNatural();
        String baselineFingerprint = adapter.fingerprint(baseline);
        List<RegisterNaturalPersonCommand> variants = List.of(
                naturalCommand(metadata(tenant("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab2"), "operator", PROCESS_ID),
                        baseline.idempotencyKey(), baseline.displayName(), baseline.givenNames(), baseline.familyNames(),
                        baseline.preferredName(), baseline.birthDate(), baseline.dateOfDeath(), baseline.birthCountryCode(),
                        baseline.initialIdentifier()),
                naturalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), "Augusta Ada King",
                        baseline.givenNames(), baseline.familyNames(), baseline.preferredName(), baseline.birthDate(),
                        baseline.dateOfDeath(), baseline.birthCountryCode(), baseline.initialIdentifier()),
                naturalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), baseline.displayName(),
                        "Augusta Ada", baseline.familyNames(), baseline.preferredName(), baseline.birthDate(),
                        baseline.dateOfDeath(), baseline.birthCountryCode(), baseline.initialIdentifier()),
                naturalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), baseline.displayName(),
                        baseline.givenNames(), "King", baseline.preferredName(), baseline.birthDate(),
                        baseline.dateOfDeath(), baseline.birthCountryCode(), baseline.initialIdentifier()),
                naturalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), baseline.displayName(),
                        baseline.givenNames(), baseline.familyNames(), "Augusta", baseline.birthDate(),
                        baseline.dateOfDeath(), baseline.birthCountryCode(), baseline.initialIdentifier()),
                naturalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), baseline.displayName(),
                        baseline.givenNames(), baseline.familyNames(), baseline.preferredName(),
                        baseline.birthDate().plusDays(1), baseline.dateOfDeath(), baseline.birthCountryCode(),
                        baseline.initialIdentifier()),
                naturalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), baseline.displayName(),
                        baseline.givenNames(), baseline.familyNames(), baseline.preferredName(), baseline.birthDate(),
                        baseline.dateOfDeath().plusDays(1), baseline.birthCountryCode(), baseline.initialIdentifier()),
                naturalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), baseline.displayName(),
                        baseline.givenNames(), baseline.familyNames(), baseline.preferredName(), baseline.birthDate(),
                        baseline.dateOfDeath(), "EC", baseline.initialIdentifier()),
                naturalWithIdentifier(baseline, identifier("GB-PASSPORT", "AB-1234", "issuer", ISSUED_ON,
                        EXPIRES_ON, true)),
                naturalWithIdentifier(baseline, identifier("GB-NATIONAL-ID", "AB1234", "issuer", ISSUED_ON,
                        EXPIRES_ON, true)),
                naturalWithIdentifier(baseline, identifier("GB-NATIONAL-ID", "AB-1234", "other-issuer", ISSUED_ON,
                        EXPIRES_ON, true)),
                naturalWithIdentifier(baseline, identifier("GB-NATIONAL-ID", "AB-1234", "issuer",
                        ISSUED_ON.plusDays(1), EXPIRES_ON, true)),
                naturalWithIdentifier(baseline, identifier("GB-NATIONAL-ID", "AB-1234", "issuer", ISSUED_ON,
                        EXPIRES_ON.plusDays(1), true)),
                naturalWithIdentifier(baseline, identifier("GB-NATIONAL-ID", "AB-1234", "issuer", ISSUED_ON,
                        EXPIRES_ON, false)));

        variants.forEach(variant -> assertNotEquals(baselineFingerprint, adapter.fingerprint(variant)));
    }

    @Test
    void distinguishesEveryEffectiveLegalEntityFieldAndOperation() {
        RegisterLegalEntityCommand baseline = baselineLegal();
        String baselineFingerprint = adapter.fingerprint(baseline);
        List<RegisterLegalEntityCommand> variants = List.of(
                legalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), "Other display",
                        baseline.legalName(), baseline.tradeName(), baseline.legalFormCode(),
                        baseline.incorporationCountryCode(), baseline.incorporatedOn(), baseline.dissolvedOn(),
                        baseline.initialIdentifier()),
                legalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), baseline.displayName(),
                        "Other legal name", baseline.tradeName(), baseline.legalFormCode(),
                        baseline.incorporationCountryCode(), baseline.incorporatedOn(), baseline.dissolvedOn(),
                        baseline.initialIdentifier()),
                legalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), baseline.displayName(),
                        baseline.legalName(), "Other trade", baseline.legalFormCode(),
                        baseline.incorporationCountryCode(), baseline.incorporatedOn(), baseline.dissolvedOn(),
                        baseline.initialIdentifier()),
                legalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), baseline.displayName(),
                        baseline.legalName(), baseline.tradeName(), "PLC", baseline.incorporationCountryCode(),
                        baseline.incorporatedOn(), baseline.dissolvedOn(), baseline.initialIdentifier()),
                legalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), baseline.displayName(),
                        baseline.legalName(), baseline.tradeName(), baseline.legalFormCode(), "EC",
                        baseline.incorporatedOn(), baseline.dissolvedOn(), baseline.initialIdentifier()),
                legalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), baseline.displayName(),
                        baseline.legalName(), baseline.tradeName(), baseline.legalFormCode(),
                        baseline.incorporationCountryCode(), baseline.incorporatedOn().plusDays(1),
                        baseline.dissolvedOn(), baseline.initialIdentifier()),
                legalCommand(baseline.requestMetadata(), baseline.idempotencyKey(), baseline.displayName(),
                        baseline.legalName(), baseline.tradeName(), baseline.legalFormCode(),
                        baseline.incorporationCountryCode(), baseline.incorporatedOn(),
                        baseline.dissolvedOn().plusDays(1), baseline.initialIdentifier()));

        variants.forEach(variant -> assertNotEquals(baselineFingerprint, adapter.fingerprint(variant)));
        assertNotEquals(adapter.fingerprint(baselineNatural()), baselineFingerprint);
        assertTrue(HmacRegistrationFingerprintAdapter.canonicalForm(baseline)
                .startsWith("{\"operation\":\"REGISTER_LEGAL_ENTITY\""));
    }

    @Test
    void escapesCanonicalValuesWithoutDelimiterOrUtf8ReplacementCollisions() {
        RegisterNaturalPersonCommand escaped = naturalCommand(
                metadata(TENANT_ID, "operator", PROCESS_ID),
                "key",
                "Display\n\"",
                "\u0001Ada",
                "Love\\lace",
                "\uD800",
                null,
                null,
                null,
                identifier("SCHEME\"A", "ID\n\\\uDC00", null, null, null, false));

        String canonical = HmacRegistrationFingerprintAdapter.canonicalForm(escaped);

        assertTrue(canonical.contains("\"displayName\":\"Display\\n\\\"\""));
        assertTrue(canonical.contains("\"givenNames\":\"\\u0001Ada\""));
        assertTrue(canonical.contains("\"familyNames\":\"Love\\\\lace\""));
        assertTrue(canonical.contains("\"preferredName\":\"\\ud800\""));
        assertTrue(canonical.contains("\"identifierSchemeCode\":\"SCHEME\\\"A\""));
        assertTrue(canonical.contains("\"identifierValue\":\"ID\\n\\\\\\udc00\""));
        assertFalse(canonical.contains("Display\n"));

        RegisterNaturalPersonCommand replacement = naturalCommand(
                escaped.requestMetadata(),
                escaped.idempotencyKey(),
                escaped.displayName(),
                escaped.givenNames(),
                escaped.familyNames(),
                "?",
                escaped.birthDate(),
                escaped.dateOfDeath(),
                escaped.birthCountryCode(),
                escaped.initialIdentifier());
        assertNotEquals(adapter.fingerprint(escaped), adapter.fingerprint(replacement));
    }

    @Test
    void separatesFingerprintsByDedicatedKey() {
        HmacRegistrationFingerprintAdapter otherAdapter = adapterWithKey(
                "0123456789abcdef0123456789abcdef");

        assertNotEquals(
                adapter.fingerprint(baselineNatural()),
                otherAdapter.fingerprint(baselineNatural()));
    }

    @Test
    void rejectsMalformedEncodedFingerprintsWithoutThrowing() {
        String valid = adapter.fingerprint(baselineNatural());

        assertFalse(adapter.matches(null, valid));
        assertFalse(adapter.matches(valid, null));
        assertFalse(adapter.matches("", valid));
        assertFalse(adapter.matches("0".repeat(63), valid));
        assertFalse(adapter.matches("z".repeat(64), valid));
        assertFalse(adapter.matches("z".repeat(64), "z".repeat(64)));
        assertFalse(adapter.matches(valid, "0".repeat(64)));
    }

    @Test
    void sanitizesFingerprintFailuresWithoutExposingCanonicalInput() {
        HmacRegistrationFingerprintAdapter invalidAdapter =
                new HmacRegistrationFingerprintAdapter(new InvalidSecretKey());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> invalidAdapter.fingerprint(baselineNatural()));

        assertEquals("Registration fingerprinting failed", failure.getMessage());
        assertFalse(failure.getMessage().contains("AB-1234"));
        assertNull(failure.getCause());
    }

    private static RegisterNaturalPersonCommand baselineNatural() {
        return naturalCommand(
                metadata(TENANT_ID, "operator", PROCESS_ID),
                "key-1",
                "Ada Lovelace",
                "Ada",
                "Lovelace",
                "Ada",
                LocalDate.of(1815, 12, 10),
                LocalDate.of(1852, 11, 27),
                "GB",
                identifier("GB-NATIONAL-ID", "AB-1234", "issuer", ISSUED_ON, EXPIRES_ON, true));
    }

    private static RegisterLegalEntityCommand baselineLegal() {
        return legalCommand(
                metadata(TENANT_ID, "operator", PROCESS_ID),
                "key-2",
                "Analytical Engines Ltd",
                "Analytical Engines Limited",
                "Analytical Engines",
                "LTD",
                "GB",
                LocalDate.of(1843, 1, 1),
                LocalDate.of(1850, 1, 1),
                identifier("GB-REGISTRATION", "LE-1234", "registry", ISSUED_ON, EXPIRES_ON, false));
    }

    private static RegisterNaturalPersonCommand naturalWithIdentifier(
            RegisterNaturalPersonCommand command,
            InitialPartyIdentifierInput identifier) {
        return naturalCommand(
                command.requestMetadata(), command.idempotencyKey(), command.displayName(), command.givenNames(),
                command.familyNames(), command.preferredName(), command.birthDate(), command.dateOfDeath(),
                command.birthCountryCode(), identifier);
    }

    private static RegisterNaturalPersonCommand naturalCommand(
            RequestMetadata metadata,
            String idempotencyKey,
            String displayName,
            String givenNames,
            String familyNames,
            String preferredName,
            LocalDate birthDate,
            LocalDate dateOfDeath,
            String birthCountryCode,
            InitialPartyIdentifierInput identifier) {
        return new RegisterNaturalPersonCommand(
                metadata, idempotencyKey, displayName, givenNames, familyNames, preferredName,
                birthDate, dateOfDeath, birthCountryCode, identifier);
    }

    private static RegisterLegalEntityCommand legalCommand(
            RequestMetadata metadata,
            String idempotencyKey,
            String displayName,
            String legalName,
            String tradeName,
            String legalFormCode,
            String incorporationCountryCode,
            LocalDate incorporatedOn,
            LocalDate dissolvedOn,
            InitialPartyIdentifierInput identifier) {
        return new RegisterLegalEntityCommand(
                metadata, idempotencyKey, displayName, legalName, tradeName, legalFormCode,
                incorporationCountryCode, incorporatedOn, dissolvedOn, identifier);
    }

    private static InitialPartyIdentifierInput identifier(
            String schemeCode,
            String value,
            String issuerCode,
            LocalDate issuedOn,
            LocalDate expiresOn,
            boolean primary) {
        return new InitialPartyIdentifierInput(
                schemeCode, value, issuerCode, issuedOn, expiresOn, primary);
    }

    private static RequestMetadata metadata(TenantId tenantId, String userId, UUID processId) {
        return new RequestMetadata(tenantId, userId, processId);
    }

    private static TenantId tenant(String value) {
        return new TenantId(UUID.fromString(value));
    }

    private static HmacRegistrationFingerprintAdapter adapterWithKey(String key) {
        return new HmacRegistrationFingerprintAdapter(
                new SecretKeySpec(key.getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
    }

    /** Supplies deliberately invalid key bytes to exercise sanitized JCA failure handling. */
    private static final class InvalidSecretKey implements SecretKey {

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public String getAlgorithm() {
            return "HmacSHA256";
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return null;
        }
    }
}
