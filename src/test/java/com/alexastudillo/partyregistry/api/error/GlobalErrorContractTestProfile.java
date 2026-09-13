package com.alexastudillo.partyregistry.api.error;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Enables only the gated error probes and JSON authentication challenge.
 */
public final class GlobalErrorContractTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.devservices.enabled", "false",
                "quarkus.datasource.health.enabled", "false",
                "quarkus.flyway.migrate-at-start", "false",
                "quarkus.hibernate-orm.enabled", "false",
                "party-registry.error-verification.enabled", "true",
                "api-response.errors.authentication-challenge.enabled", "true");
    }
}
