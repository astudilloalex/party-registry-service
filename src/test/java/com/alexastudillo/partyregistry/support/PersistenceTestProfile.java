package com.alexastudillo.partyregistry.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Disables RabbitMQ Dev Services for database-focused integration tests.
 */
public final class PersistenceTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.rabbitmq.devservices.enabled", "false");
    }
}
