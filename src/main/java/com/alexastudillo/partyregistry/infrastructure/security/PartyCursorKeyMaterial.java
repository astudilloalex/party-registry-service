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
import java.util.Optional;

/**
 * Validates cursor-specific signing material once at startup and retains immutable rotation keys.
 */
@Startup
@Singleton
public final class PartyCursorKeyMaterial {

    private final String currentKeyId;
    private final Map<String, SecretKey> keys;

    /** Rejects missing or unusable configuration with a sanitized failure that contains no supplied key material. */
    @Inject
    public PartyCursorKeyMaterial(PartyPaginationConfiguration configuration) {
        try {
            currentKeyId = validKeyId(configuration.currentSigningKeyId()
                    .orElseThrow(PartyCursorKeyMaterial::invalidConfiguration));
            Map<String, String> configuredKeys = configuration.signingKeys();
            if (configuredKeys == null || configuredKeys.isEmpty()) {
                throw invalidConfiguration();
            }
            Map<String, SecretKey> decoded = new HashMap<>();
            configuredKeys.forEach((id, key) -> decoded.put(validKeyId(id), decodeKey(key)));
            keys = Map.copyOf(decoded);
            if (!keys.containsKey(currentKeyId)) {
                throw invalidConfiguration();
            }
        } catch (CryptographicConfigurationException exception) {
            throw exception;
        } catch (RuntimeException _) {
            throw invalidConfiguration();
        }
    }

    String currentKeyId() {
        return currentKeyId;
    }

    SecretKey currentKey() {
        return keys.get(currentKeyId);
    }

    Optional<SecretKey> verificationKey(String id) {
        return Optional.ofNullable(keys.get(id));
    }

    private static String validKeyId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) {
            throw invalidConfiguration();
        }
        return id;
    }

    private static SecretKey decodeKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw invalidConfiguration();
        }
        byte[] decoded = null;
        try {
            decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length != 32) {
                throw invalidConfiguration();
            }
            return new SecretKeySpec(decoded, "HmacSHA256");
        } finally {
            if (decoded != null) {
                Arrays.fill(decoded, (byte) 0);
            }
        }
    }

    private static CryptographicConfigurationException invalidConfiguration() {
        return new CryptographicConfigurationException("Cursor signing configuration is invalid");
    }
}
