package com.alexastudillo.partyregistry.infrastructure.security;

import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies cursor key selection, retained verification keys, stable relaunch configuration, and sanitized rejection.
 */
class PartyCursorKeyMaterialTest {

    private static final String FIRST_KEY = "Y3Vyc29yLWtleS12MS0wMTIzNDU2Nzg5YWJjZGVmMDE=";
    private static final String SECOND_KEY = encoded("cursor-key-v2-0123456789abcdef01");

    @Test
    void selectsTheCurrentKeyAndRetainsIndependentVerificationMaterial() {
        var configuration = new TestConfiguration(Optional.of("v2"), Map.of("v1", FIRST_KEY, "v2", SECOND_KEY));
        var material = new PartyCursorKeyMaterial(configuration);

        assertEquals("v2", material.currentKeyId());
        assertEquals("HmacSHA256", material.currentKey().getAlgorithm());
        assertArrayEquals(Base64.getDecoder().decode(SECOND_KEY), material.currentKey().getEncoded());
        assertArrayEquals(Base64.getDecoder().decode(FIRST_KEY),
                material.verificationKey("v1").orElseThrow().getEncoded());
        assertTrue(material.verificationKey("unknown").isEmpty());
        assertFalse(java.security.MessageDigest.isEqual(material.currentKey().getEncoded(),
                material.verificationKey("v1").orElseThrow().getEncoded()));
    }

    @Test
    void mapsExternalPropertyNamesAndRestoresTheSameMaterialAcrossInstances() {
        var config = new SmallRyeConfigBuilder().withMapping(PartyPaginationConfiguration.class)
                .withDefaultValue("party-registry.pagination.current-signing-key-id", "test-v1")
                .withDefaultValue("party-registry.pagination.signing-keys.test-v1", FIRST_KEY).build();
        var settings = config.getConfigMapping(PartyPaginationConfiguration.class);
        var first = new PartyCursorKeyMaterial(settings);
        var relaunched = new PartyCursorKeyMaterial(settings);

        assertEquals("test-v1", settings.currentSigningKeyId().orElseThrow());
        assertEquals(Map.of("test-v1", FIRST_KEY), settings.signingKeys());
        assertEquals(first.currentKeyId(), relaunched.currentKeyId());
        assertArrayEquals(first.currentKey().getEncoded(), relaunched.currentKey().getEncoded());
    }

    @Test
    void rejectsMissingCurrentKeyAndMalformedOrUnusableMaterialWithoutDisclosure() {
        assertInvalid(Optional.empty(), Map.of("v1", FIRST_KEY));
        assertInvalid(Optional.of("v1"), Map.of());
        assertInvalid(Optional.of("v2"), Map.of("v1", FIRST_KEY));
        assertInvalid(Optional.of(" v1 "), Map.of("v1", FIRST_KEY));
        assertInvalid(Optional.of("v1"), Map.of("v1", "sensitive-invalid-secret"));
        assertInvalid(Optional.of("v1"), Map.of("v1", " "));
        assertInvalid(Optional.of("v1"), Map.of("v1", encoded("a".repeat(31))));
        assertInvalid(Optional.of("v1"), Map.of("v1", encoded("a".repeat(33))));
        assertInvalid(Optional.of("v1"), Map.of("v1", FIRST_KEY, "invalid.id", SECOND_KEY));
    }

    private static void assertInvalid(Optional<String> current, Map<String, String> keys) {
        var configuration = new TestConfiguration(current, keys);
        var failure = assertThrows(CryptographicConfigurationException.class,
                () -> new PartyCursorKeyMaterial(configuration));
        assertEquals("Cursor signing configuration is invalid", failure.getMessage());
        assertNull(failure.getCause());
        for (String value : keys.values()) {
            if (!value.isBlank()) {
                assertFalse(failure.getMessage().contains(value));
            }
        }
    }

    private static String encoded(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Supplies deterministic test-only key configuration without a running dependency-injection container. */
    private record TestConfiguration(Optional<String> currentSigningKeyId, Map<String, String> signingKeys)
            implements PartyPaginationConfiguration {
    }
}
