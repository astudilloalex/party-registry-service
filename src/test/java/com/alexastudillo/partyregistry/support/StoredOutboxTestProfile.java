package com.alexastudillo.partyregistry.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Enables deterministic outbox storage without broker publication.
 */
public final class StoredOutboxTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "party-registry.outbox.mode", "stored-only",
                "quarkus.rabbitmq.devservices.enabled", "false");
    }
}
