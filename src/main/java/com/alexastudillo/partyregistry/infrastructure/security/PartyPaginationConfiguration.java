package com.alexastudillo.partyregistry.infrastructure.security;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Map;
import java.util.Optional;

/**
 * Maps independently provisioned cursor signing keys without development or production secret defaults.
 */
@ConfigMapping(prefix = "party-registry.pagination")
public interface PartyPaginationConfiguration {

    /** Returns the exact key identifier selected for newly issued cursors. */
    @WithName("current-signing-key-id")
    Optional<String> currentSigningKeyId();

    /** Returns the external Base64-encoded 256-bit key ring, including retained verification keys. */
    @WithName("signing-keys")
    Map<String, String> signingKeys();
}
