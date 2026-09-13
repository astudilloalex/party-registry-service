package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.RegisterPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.usecase.PartyIdentifierPreparation;
import com.alexastudillo.partyregistry.application.usecase.RegisterPartyIdentifierUseCase;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSchemePolicy;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies tenant-isolated indexing, authenticated encryption, rotation, and masks.
 */
class JcaIdentifierProtectionAdapterTest {

    private static final TenantId TENANT_ID = tenant("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1");
    private static final PartyId PARTY_ID = party("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5");
    private static final PartyIdentifierId IDENTIFIER_ID = identifier(
            "0198ce2c-609c-7c04-a977-425e7d60c58d");
    private static final IdentifierSchemeId SCHEME_ID = scheme(
            "0198ce2d-7e5a-7d9a-bfe7-42f15172338b");
    private static final IdentifierRuleVersion NORMALIZATION_VERSION = new IdentifierRuleVersion(1);
    private static final String COMPLETE_VALUE = "AB-12 34";
    private static final String NORMALIZED_VALUE = "AB1234";

    private final CryptographicKeyMaterial keyMaterial = SecurityTestKeys.keyMaterial(2, 1, 2);
    private final JcaIdentifierProtectionAdapter adapter = new JcaIdentifierProtectionAdapter(
            keyMaterial,
            new SecureRandom());

    @Test
    void createsDeterministicTenantHashAndRandomizedVersionedCiphertext() {
        IdentifierProtectionRequest request = request(
                TENANT_ID, PARTY_ID, IDENTIFIER_ID, SCHEME_ID, COMPLETE_VALUE, NORMALIZED_VALUE,
                NORMALIZATION_VERSION);

        ProtectedIdentifierValue first = adapter.protect(request);
        ProtectedIdentifierValue second = adapter.protect(request);

        assertEquals(first.normalizedValueHash(), second.normalizedValueHash());
        assertTrue(first.normalizedValueHash().matches("^[0-9a-f]{64}$"));
        assertNotEquals(first.encryptedValue(), second.encryptedValue());
        assertTrue(first.encryptedValue().matches("^v1\\.[A-Za-z0-9_-]{16}\\.[A-Za-z0-9_-]+$"));
        assertEquals(2, first.encryptionKeyVersion());
        assertEquals("**1234", first.maskedValue());
        assertEquals(NORMALIZATION_VERSION, first.normalizationVersion());
        assertFalse(first.encryptedValue().contains(COMPLETE_VALUE));
    }

    @Test
    void indexesNormalizedValuesDeterministicallyButSeparatesTenants() {
        IdentifierProtectionRequest first = request(
                TENANT_ID, PARTY_ID, IDENTIFIER_ID, SCHEME_ID, "AB-1234", NORMALIZED_VALUE,
                NORMALIZATION_VERSION);
        IdentifierProtectionRequest formattedEquivalent = request(
                TENANT_ID, PARTY_ID, IDENTIFIER_ID, SCHEME_ID, "AB 1234", NORMALIZED_VALUE,
                NORMALIZATION_VERSION);
        IdentifierProtectionRequest otherTenant = request(
                tenant("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab2"),
                PARTY_ID, IDENTIFIER_ID, SCHEME_ID, "AB 1234", NORMALIZED_VALUE,
                NORMALIZATION_VERSION);

        String firstHash = adapter.protect(first).normalizedValueHash();
        assertEquals(firstHash, adapter.protect(formattedEquivalent).normalizedValueHash());
        assertNotEquals(firstHash, adapter.protect(otherTenant).normalizedValueHash());
    }

    @Test
    void decryptsTheExactCompleteValueWithCurrentKey() {
        IdentifierProtectionRequest request = request(
                TENANT_ID, PARTY_ID, IDENTIFIER_ID, SCHEME_ID, COMPLETE_VALUE, NORMALIZED_VALUE,
                NORMALIZATION_VERSION);
        ProtectedIdentifierValue protectedValue = adapter.protect(request);

        assertEquals(COMPLETE_VALUE, adapter.decrypt(protectedValue, request));
    }

    @Test
    void preservesSuppliedPlaintextWithoutApplyingNormalization() {
        String plaintext = "\u2003  aB-12 34\u00df  \u2003";
        IdentifierProtectionRequest request = request(
                TENANT_ID, PARTY_ID, IDENTIFIER_ID, SCHEME_ID, plaintext, NORMALIZED_VALUE,
                NORMALIZATION_VERSION);

        ProtectedIdentifierValue protectedValue = adapter.protect(request);

        assertEquals(plaintext, adapter.decrypt(protectedValue, request));
    }

    @Test
    void encryptsSchemeNormalizedPlaintextThroughSharedRegistrationPreparation() {
        Instant now = Instant.parse("2026-09-04T00:30:00Z");
        IdentifierScheme identifierScheme = new IdentifierScheme(
                SCHEME_ID,
                "TEST-SCHEME",
                "GB",
                IdentifierCategory.PASSPORT,
                IdentifierSubjectType.NATURAL_PERSON,
                "Test passport",
                null,
                "TRIM_UPPERCASE_V1",
                "ALPHANUMERIC_V1",
                1,
                256,
                false,
                IdentifierSchemeStatus.ACTIVE,
                new IdentifierSchemeVersion(1),
                AuditInfo.initial(now, "catalog"));
        List<PartyIdentifierRegistrationCandidate> candidates = new ArrayList<>();
        RegisterPartyIdentifierUseCase useCase = new RegisterPartyIdentifierUseCase(
                (tenantId, partyId) -> Uni.createFrom().item(Optional.of(PartyType.NATURAL_PERSON)),
                candidate -> {
                    candidates.add(candidate);
                    return Uni.createFrom().item(PartyIdentifierResult.fromAggregate(
                            candidate.identifier(), candidate.identifierScheme()));
                },
                new PartyIdentifierPreparation(
                        code -> Uni.createFrom().item(Optional.of(identifierScheme)),
                        new IdentifierRuleCatalog(),
                        new IdentifierSchemePolicy(),
                        adapter),
                Clock.fixed(now, ZoneOffset.UTC),
                new RecordingOperationObserver());
        String submittedValue = "\u2003  ab1234  \u2003";
        RegisterPartyIdentifierCommand command = new RegisterPartyIdentifierCommand(
                new RequestMetadata(TENANT_ID, "registration-test-user", UUID.randomUUID()),
                "normalized-plaintext-key",
                PARTY_ID,
                new InitialPartyIdentifierInput(
                        identifierScheme.code(), submittedValue, null, null, null, false));

        useCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2))
                .assertCompleted();

        assertEquals(submittedValue, command.identifier().value());
        assertEquals(1, candidates.size());
        var identifier = candidates.getFirst().identifier();
        ProtectedIdentifierValue protectedValue = identifier.protectedValue();
        IdentifierProtectionRequest context = request(
                TENANT_ID, PARTY_ID, identifier.identifierId(), SCHEME_ID, submittedValue, NORMALIZED_VALUE,
                NORMALIZATION_VERSION);
        ProtectedIdentifierValue originalProtection = adapter.protect(context);

        assertEquals(NORMALIZED_VALUE, adapter.decrypt(protectedValue, context));
        assertEquals(submittedValue, adapter.decrypt(originalProtection, context));
        assertEquals(originalProtection.normalizedValueHash(), protectedValue.normalizedValueHash());
        assertEquals(originalProtection.maskedValue(), protectedValue.maskedValue());
        assertEquals("**1234", protectedValue.maskedValue());
        assertEquals(originalProtection.encryptionKeyVersion(), protectedValue.encryptionKeyVersion());
        assertEquals(NORMALIZATION_VERSION, protectedValue.normalizationVersion());
    }

    @Test
    void rejectsEveryChangedAadIdentityAndKeyVersion() {
        IdentifierProtectionRequest request = request(
                TENANT_ID, PARTY_ID, IDENTIFIER_ID, SCHEME_ID, COMPLETE_VALUE, NORMALIZED_VALUE,
                NORMALIZATION_VERSION);
        ProtectedIdentifierValue protectedValue = adapter.protect(request);
        List<IdentifierProtectionRequest> changedContexts = List.of(
                request(tenant("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab2"), PARTY_ID, IDENTIFIER_ID,
                        SCHEME_ID, COMPLETE_VALUE, NORMALIZED_VALUE, NORMALIZATION_VERSION),
                request(TENANT_ID, party("0198ce2b-d6a3-7d6e-80ba-d97b21d793e6"), IDENTIFIER_ID,
                        SCHEME_ID, COMPLETE_VALUE, NORMALIZED_VALUE, NORMALIZATION_VERSION),
                request(TENANT_ID, PARTY_ID, identifier("0198ce2c-609c-7c04-a977-425e7d60c58e"),
                        SCHEME_ID, COMPLETE_VALUE, NORMALIZED_VALUE, NORMALIZATION_VERSION),
                request(TENANT_ID, PARTY_ID, IDENTIFIER_ID,
                        scheme("0198ce2d-7e5a-7d9a-bfe7-42f15172338c"), COMPLETE_VALUE,
                        NORMALIZED_VALUE, NORMALIZATION_VERSION));

        changedContexts.forEach(context -> assertProtectionFailure(
                () -> adapter.decrypt(protectedValue, context)));

        ProtectedIdentifierValue changedNormalizationVersion = new ProtectedIdentifierValue(
                protectedValue.encryptedValue(),
                protectedValue.encryptionKeyVersion(),
                protectedValue.normalizedValueHash(),
                protectedValue.maskedValue(),
                new IdentifierRuleVersion(2));
        assertProtectionFailure(() -> adapter.decrypt(changedNormalizationVersion, request));

        ProtectedIdentifierValue changedKeyVersion = new ProtectedIdentifierValue(
                protectedValue.encryptedValue(),
                1,
                protectedValue.normalizedValueHash(),
                protectedValue.maskedValue(),
                protectedValue.normalizationVersion());
        assertProtectionFailure(() -> adapter.decrypt(changedKeyVersion, request));
    }

    @Test
    void rejectsCiphertextTamperingAndMalformedEnvelopes() {
        IdentifierProtectionRequest request = request(
                TENANT_ID, PARTY_ID, IDENTIFIER_ID, SCHEME_ID, COMPLETE_VALUE, NORMALIZED_VALUE,
                NORMALIZATION_VERSION);
        ProtectedIdentifierValue protectedValue = adapter.protect(request);
        String[] envelope = protectedValue.encryptedValue().split("\\.");
        byte[] ciphertext = Base64.getUrlDecoder().decode(envelope[2]);
        ciphertext[ciphertext.length - 1] ^= 1;
        String tamperedEnvelope = envelope[0] + "." + envelope[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        ProtectedIdentifierValue tampered = new ProtectedIdentifierValue(
                tamperedEnvelope,
                protectedValue.encryptionKeyVersion(),
                protectedValue.normalizedValueHash(),
                protectedValue.maskedValue(),
                protectedValue.normalizationVersion());
        ProtectedIdentifierValue malformed = new ProtectedIdentifierValue(
                "v1.invalid",
                protectedValue.encryptionKeyVersion(),
                protectedValue.normalizedValueHash(),
                protectedValue.maskedValue(),
                protectedValue.normalizationVersion());

        assertProtectionFailure(() -> adapter.decrypt(tampered, request));
        assertProtectionFailure(() -> adapter.decrypt(malformed, request));
    }

    @Test
    void decryptsRetainedCiphertextAfterSelectingANewCurrentKey() {
        CryptographicKeyMaterial originalKeys = SecurityTestKeys.keyMaterial(1, 1);
        JcaIdentifierProtectionAdapter originalAdapter = new JcaIdentifierProtectionAdapter(
                originalKeys,
                new SecureRandom());
        IdentifierProtectionRequest request = request(
                TENANT_ID, PARTY_ID, IDENTIFIER_ID, SCHEME_ID, COMPLETE_VALUE, NORMALIZED_VALUE,
                NORMALIZATION_VERSION);
        ProtectedIdentifierValue retained = originalAdapter.protect(request);
        ProtectedIdentifierValue current = adapter.protect(request);

        assertEquals(1, retained.encryptionKeyVersion());
        assertEquals(2, current.encryptionKeyVersion());
        assertEquals(COMPLETE_VALUE, adapter.decrypt(retained, request));
        assertEquals(COMPLETE_VALUE, adapter.decrypt(current, request));
    }

    @Test
    void appliesConservativeCodePointAwareMaskBoundaries() {
        assertEquals("*", JcaIdentifierProtectionAdapter.mask("1"));
        assertEquals("****", JcaIdentifierProtectionAdapter.mask("1234"));
        assertEquals("*2345", JcaIdentifierProtectionAdapter.mask("12345"));
        assertEquals("********1234", JcaIdentifierProtectionAdapter.mask("DOCUMENT1234"));
        assertEquals("*😀BCD", JcaIdentifierProtectionAdapter.mask("A😀BCD"));
        assertEquals(64, JcaIdentifierProtectionAdapter.mask("X".repeat(70)).length());
        assertTrue(JcaIdentifierProtectionAdapter.mask("X".repeat(66) + "1234").endsWith("1234"));
    }

    @Test
    void translatesProtectionFailuresWithoutSensitiveMessagesOrCauses() {
        String providerMessage = "provider failed for secret-value";
        SecureRandom failingRandom = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                throw new IllegalStateException(providerMessage);
            }
        };
        JcaIdentifierProtectionAdapter failingAdapter = new JcaIdentifierProtectionAdapter(
                keyMaterial,
                failingRandom);
        IdentifierProtectionRequest request = request(
                TENANT_ID, PARTY_ID, IDENTIFIER_ID, SCHEME_ID, COMPLETE_VALUE, NORMALIZED_VALUE,
                NORMALIZATION_VERSION);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> failingAdapter.protect(request));

        ApplicationFailure.DependencyUnavailable failure = assertInstanceOf(
                ApplicationFailure.DependencyUnavailable.class,
                exception.failure());
        assertEquals("identifier-protection", failure.dependencyName());
        assertEquals("Dependency unavailable", exception.getMessage());
        assertFalse(exception.getMessage().contains(providerMessage));
        assertFalse(exception.getMessage().contains(COMPLETE_VALUE));
        assertNull(exception.getCause());
    }

    private static IdentifierProtectionRequest request(
            TenantId tenantId,
            PartyId partyId,
            PartyIdentifierId identifierId,
            IdentifierSchemeId schemeId,
            String completeValue,
            String normalizedValue,
            IdentifierRuleVersion normalizationVersion) {
        return new IdentifierProtectionRequest(
                tenantId,
                partyId,
                identifierId,
                schemeId,
                completeValue,
                normalizedValue,
                normalizationVersion);
    }

    private static void assertProtectionFailure(org.junit.jupiter.api.function.Executable executable) {
        IllegalStateException exception = assertThrows(IllegalStateException.class, executable);
        assertEquals("Identifier protection failed", exception.getMessage());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains(COMPLETE_VALUE));
    }

    private static TenantId tenant(String value) {
        return new TenantId(UUID.fromString(value));
    }

    private static PartyId party(String value) {
        return new PartyId(UUID.fromString(value));
    }

    private static PartyIdentifierId identifier(String value) {
        return new PartyIdentifierId(UUID.fromString(value));
    }

    private static IdentifierSchemeId scheme(String value) {
        return new IdentifierSchemeId(UUID.fromString(value));
    }
}
