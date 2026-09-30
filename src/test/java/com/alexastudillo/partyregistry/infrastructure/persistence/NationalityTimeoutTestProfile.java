package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/** Uses a short test-owned mutation budget to exercise cancellation-safe transaction cleanup. */
public final class NationalityTimeoutTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.rabbitmq.devservices.enabled", "false",
                "party-registry.mutations.operation-timeout", "1s");
    }
}
