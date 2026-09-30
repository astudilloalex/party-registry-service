package com.alexastudillo.partyregistry.api.filter;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Disables persistence while exercising only the HTTP request boundary.
 */
public final class RequestContextFilterProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.devservices.enabled", "false",
                "quarkus.datasource.health.enabled", "false",
                "quarkus.flyway.migrate-at-start", "false",
                "quarkus.hibernate-orm.enabled", "false");
    }
}
