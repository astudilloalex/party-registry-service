package com.alexastudillo.partyregistry.infrastructure.security;

import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Validates and retains decoded cryptographic keys in memory from application
 * startup.
 */
@Startup
@Singleton
public class CryptographicKeyMaterial {

    private static final int KEY_LENGTH_BYTES = 32;
    private static final int MAXIMUM_KEY_VERSION = Short.MAX_VALUE;
    private static final String AES = "AES";
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String INVALID_CONFIGURATION = "Cryptographic configuration is invalid";

    private final Map<Integer, SecretKey> encryptionKeys;
    private final int currentEncryptionKeyVersion;
    private final SecretKey identifierIndexHmacKey;
    private final SecretKey registrationHmacKey;

    /**
     * Validates all required key material while the Quarkus application starts.
     *
     * @param identifierConfiguration  identifier encryption and lookup-key
     *                                 configuration
     * @param fingerprintConfiguration registration fingerprint-key configuration
     */
    @Inject
    public CryptographicKeyMaterial(
            IdentifierProtectionConfiguration identifierConfiguration,
            RegistrationFingerprintConfiguration fingerprintConfiguration) {
        try {
            encryptionKeys = decodeEncryptionKeys(identifierConfiguration.encryptionKeys());
            currentEncryptionKeyVersion = parseVersion(
                    identifierConfiguration.currentEncryptionKeyVersion().orElseThrow(
                            CryptographicKeyMaterial::invalidConfiguration));
            if (!encryptionKeys.containsKey(currentEncryptionKeyVersion)) {
                throw invalidConfiguration();
            }
            identifierIndexHmacKey = decodeKey(
                    identifierConfiguration.indexHmacKey().orElseThrow(
                            CryptographicKeyMaterial::invalidConfiguration),
                    HMAC_SHA_256);
            registrationHmacKey = decodeKey(
                    fingerprintConfiguration.registrationHmacKey().orElseThrow(
                            CryptographicKeyMaterial::invalidConfiguration),
                    HMAC_SHA_256);
        } catch (CryptographicConfigurationException exception) {
            throw exception;
        } catch (RuntimeException _) {
            throw invalidConfiguration();
        }
    }

    SecretKey currentEncryptionKey() {
        return encryptionKeys.get(currentEncryptionKeyVersion);
    }

    SecretKey encryptionKey(int version) {
        SecretKey key = encryptionKeys.get(version);
        if (key == null) {
            throw new IllegalStateException("Identifier protection failed");
        }
        return key;
    }

    int currentEncryptionKeyVersion() {
        return currentEncryptionKeyVersion;
    }

    SecretKey identifierIndexHmacKey() {
        return identifierIndexHmacKey;
    }

    SecretKey registrationHmacKey() {
        return registrationHmacKey;
    }

    private static Map<Integer, SecretKey> decodeEncryptionKeys(Map<String, String> encodedKeys) {
        if (encodedKeys == null || encodedKeys.isEmpty()) {
            throw invalidConfiguration();
        }

        Map<Integer, SecretKey> decodedKeys = new HashMap<>();
        encodedKeys.forEach((encodedVersion, encodedKey) -> {
            int version = parseVersion(encodedVersion);
            if (decodedKeys.put(version, decodeKey(encodedKey, AES)) != null) {
                throw invalidConfiguration();
            }
        });
        return Map.copyOf(decodedKeys);
    }

    private static int parseVersion(String encodedVersion) {
        if (encodedVersion == null || encodedVersion.isEmpty()) {
            throw invalidConfiguration();
        }
        try {
            int version = Integer.parseInt(encodedVersion);
            if (version <= 0
                    || version > MAXIMUM_KEY_VERSION
                    || !Integer.toString(version).equals(encodedVersion)) {
                throw invalidConfiguration();
            }
            return version;
        } catch (NumberFormatException _) {
            throw invalidConfiguration();
        }
    }

    private static SecretKey decodeKey(String encodedKey, String algorithm) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw invalidConfiguration();
        }

        byte[] decodedKey = null;
        try {
            decodedKey = Base64.getDecoder().decode(encodedKey);
            if (decodedKey.length != KEY_LENGTH_BYTES) {
                throw invalidConfiguration();
            }
            return new SecretKeySpec(decodedKey, algorithm);
        } catch (IllegalArgumentException _) {
            throw invalidConfiguration();
        } finally {
            if (decodedKey != null) {
                Arrays.fill(decodedKey, (byte) 0);
            }
        }
    }

    private static CryptographicConfigurationException invalidConfiguration() {
        return new CryptographicConfigurationException(INVALID_CONFIGURATION);
    }
}
