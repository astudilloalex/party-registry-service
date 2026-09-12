package com.alexastudillo.partyregistry.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Creates controlled cryptographic configuration for Infrastructure unit tests.
 */
final class SecurityTestKeys {

    static final String INDEX_KEY = encoded("abcdef0123456789abcdef0123456789");
    static final String REGISTRATION_KEY = encoded("fedcba9876543210fedcba9876543210");
    static final String ENCRYPTION_KEY_ONE = encoded("0123456789abcdef0123456789abcdef");
    static final String ENCRYPTION_KEY_TWO = encoded("9876543210abcdef9876543210abcdef");

    private SecurityTestKeys() {
    }

    static CryptographicKeyMaterial keyMaterial(int currentVersion, int... retainedVersions) {
        Map<String, String> keys = new LinkedHashMap<>();
        for (int version : retainedVersions) {
            keys.put(Integer.toString(version), switch (version) {
                case 1 -> ENCRYPTION_KEY_ONE;
                case 2 -> ENCRYPTION_KEY_TWO;
                default -> encoded(Character.toString('a' + version).repeat(32));
            });
        }
        return keyMaterial(keys, Integer.toString(currentVersion), INDEX_KEY, REGISTRATION_KEY);
    }

    static CryptographicKeyMaterial keyMaterial(
            Map<String, String> encryptionKeys,
            String currentVersion,
            String indexKey,
            String registrationKey) {
        IdentifierProtectionConfiguration identifierConfiguration = new IdentifierProtectionConfiguration() {
            @Override
            public Map<String, String> encryptionKeys() {
                return encryptionKeys;
            }

            @Override
            public Optional<String> currentEncryptionKeyVersion() {
                return Optional.ofNullable(currentVersion);
            }

            @Override
            public Optional<String> indexHmacKey() {
                return Optional.ofNullable(indexKey);
            }
        };
        RegistrationFingerprintConfiguration fingerprintConfiguration =
                () -> Optional.ofNullable(registrationKey);
        return new CryptographicKeyMaterial(identifierConfiguration, fingerprintConfiguration);
    }

    static String encoded(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }
}
