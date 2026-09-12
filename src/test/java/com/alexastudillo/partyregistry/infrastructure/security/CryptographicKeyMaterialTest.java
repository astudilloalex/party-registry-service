package com.alexastudillo.partyregistry.infrastructure.security;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies startup key decoding, version selection, and sanitized rejection.
 */
class CryptographicKeyMaterialTest {

    private static final String SENSITIVE_INVALID_VALUE = "not-a-valid-secret-value";

    @Test
    void acceptsCurrentAndRetainedAes256KeysWithSeparateHmacKeys() {
        CryptographicKeyMaterial material = SecurityTestKeys.keyMaterial(2, 1, 2);

        assertEquals(2, material.currentEncryptionKeyVersion());
        assertEquals("AES", material.currentEncryptionKey().getAlgorithm());
        assertEquals("AES", material.encryptionKey(1).getAlgorithm());
        assertEquals("HmacSHA256", material.identifierIndexHmacKey().getAlgorithm());
        assertEquals("HmacSHA256", material.registrationHmacKey().getAlgorithm());
        assertFalse(java.security.MessageDigest.isEqual(
                material.identifierIndexHmacKey().getEncoded(),
                material.registrationHmacKey().getEncoded()));
    }

    @Test
    void mapsTheApprovedPropertyNamesWithoutDefaults() throws NoSuchMethodException {
        assertEquals(
                "party-registry.identifier-protection",
                IdentifierProtectionConfiguration.class.getAnnotation(ConfigMapping.class).prefix());
        assertEquals(
                "party-registry.idempotency",
                RegistrationFingerprintConfiguration.class.getAnnotation(ConfigMapping.class).prefix());
        assertWithName(IdentifierProtectionConfiguration.class, "encryptionKeys", "encryption-keys");
        assertWithName(
                IdentifierProtectionConfiguration.class,
                "currentEncryptionKeyVersion",
                "current-encryption-key-version");
        assertWithName(IdentifierProtectionConfiguration.class, "indexHmacKey", "index-hmac-key");
        assertWithName(
                RegistrationFingerprintConfiguration.class,
                "registrationHmacKey",
                "registration-hmac-key");
    }

    @Test
    void rejectsMissingMalformedAndIncorrectlySizedConfiguration() {
        assertInvalid(Map.of(), "1", SecurityTestKeys.INDEX_KEY, SecurityTestKeys.REGISTRATION_KEY);
        assertInvalid(
                Map.of("1", SecurityTestKeys.ENCRYPTION_KEY_ONE),
                null,
                SecurityTestKeys.INDEX_KEY,
                SecurityTestKeys.REGISTRATION_KEY);
        assertInvalid(
                Map.of("1", SecurityTestKeys.ENCRYPTION_KEY_ONE),
                "2",
                SecurityTestKeys.INDEX_KEY,
                SecurityTestKeys.REGISTRATION_KEY);
        assertInvalid(
                Map.of("01", SecurityTestKeys.ENCRYPTION_KEY_ONE),
                "1",
                SecurityTestKeys.INDEX_KEY,
                SecurityTestKeys.REGISTRATION_KEY);
        assertInvalid(
                Map.of("1", SENSITIVE_INVALID_VALUE),
                "1",
                SecurityTestKeys.INDEX_KEY,
                SecurityTestKeys.REGISTRATION_KEY);
        assertInvalid(
                Map.of("1", SecurityTestKeys.encoded("a".repeat(31))),
                "1",
                SecurityTestKeys.INDEX_KEY,
                SecurityTestKeys.REGISTRATION_KEY);
        assertInvalid(
                Map.of("1", SecurityTestKeys.ENCRYPTION_KEY_ONE),
                "1",
                null,
                SecurityTestKeys.REGISTRATION_KEY);
        assertInvalid(
                Map.of("1", SecurityTestKeys.ENCRYPTION_KEY_ONE),
                "1",
                SecurityTestKeys.encoded("b".repeat(33)),
                SecurityTestKeys.REGISTRATION_KEY);
        assertInvalid(
                Map.of("1", SecurityTestKeys.ENCRYPTION_KEY_ONE),
                "1",
                SecurityTestKeys.INDEX_KEY,
                SENSITIVE_INVALID_VALUE);
    }

    private static void assertWithName(Class<?> configurationType, String methodName, String expectedName)
            throws NoSuchMethodException {
        Method method = configurationType.getMethod(methodName);
        assertEquals(expectedName, method.getAnnotation(WithName.class).value());
    }

    private static void assertInvalid(
            Map<String, String> encryptionKeys,
            String currentVersion,
            String indexKey,
            String registrationKey) {
        CryptographicConfigurationException exception = assertThrows(
                CryptographicConfigurationException.class,
                () -> SecurityTestKeys.keyMaterial(
                        encryptionKeys,
                        currentVersion,
                        indexKey,
                        registrationKey));

        assertEquals("Cryptographic configuration is invalid", exception.getMessage());
        assertFalse(exception.getMessage().contains(SENSITIVE_INVALID_VALUE));
        assertNull(exception.getCause());
        assertNotNull(exception.getMessage());
    }
}
