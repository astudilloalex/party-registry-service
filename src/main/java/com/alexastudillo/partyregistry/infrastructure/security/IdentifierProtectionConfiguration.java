package com.alexastudillo.partyregistry.infrastructure.security;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Map;
import java.util.Optional;

/**
 * Maps secret-backed identifier-protection key settings without defining defaults.
 */
@ConfigMapping(prefix = "party-registry.identifier-protection")
public interface IdentifierProtectionConfiguration {

    /**
     * Returns Base64-encoded AES keys indexed by their persisted version.
     *
     * @return configured encryption key ring
     */
    @WithName("encryption-keys")
    Map<String, String> encryptionKeys();

    /**
     * Returns the version selected for new ciphertext.
     *
     * @return configured current key version, when supplied
     */
    @WithName("current-encryption-key-version")
    Optional<String> currentEncryptionKeyVersion();

    /**
     * Returns the Base64-encoded master key for tenant-isolated lookup indexes.
     *
     * @return configured index HMAC key, when supplied
     */
    @WithName("index-hmac-key")
    Optional<String> indexHmacKey();
}
