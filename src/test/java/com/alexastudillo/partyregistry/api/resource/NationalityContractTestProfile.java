package com.alexastudillo.partyregistry.api.resource;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/** Keeps Flyway persistence active while enabling the existing gated 500 verification endpoint. */
public final class NationalityContractTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.rabbitmq.devservices.enabled", "false",
                "party-registry.error-verification.enabled", "true");
    }
}
