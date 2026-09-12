package com.alexastudillo.partyregistry.infrastructure.security;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Maps the dedicated secret-backed registration fingerprint key without a default.
 */
@ConfigMapping(prefix = "party-registry.idempotency")
public interface RegistrationFingerprintConfiguration {

    /**
     * Returns the Base64-encoded registration HMAC key.
     *
     * @return configured registration key, when supplied
     */
    @WithName("registration-hmac-key")
    Optional<String> registrationHmacKey();
}
