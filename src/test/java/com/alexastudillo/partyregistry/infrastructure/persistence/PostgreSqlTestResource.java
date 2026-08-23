package com.alexastudillo.partyregistry.infrastructure.persistence;

import java.util.Map;

import org.testcontainers.postgresql.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public final class PostgreSqlTestResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer postgres;

    @Override
    public synchronized Map<String, String> start() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer("postgres:18")
                    .withDatabaseName("party_registry_service_test")
                    .withUsername("party_registry_test")
                    .withPassword("party_registry_test");
        }

        if (!postgres.isRunning()) {
            try {
                postgres.start();
            } catch (RuntimeException | Error startupFailure) {
                stop();
                throw startupFailure;
            }
        }

        String reactiveUrl = "vertx-reactive:postgresql://%s:%d/%s".formatted(
                postgres.getHost(),
                postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                postgres.getDatabaseName());

        return Map.of(
                "quarkus.datasource.username", postgres.getUsername(),
                "quarkus.datasource.password", postgres.getPassword(),
                "quarkus.datasource.reactive.url", reactiveUrl,
                "quarkus.datasource.flyway.jdbc.url", postgres.getJdbcUrl(),
                "quarkus.datasource.flyway.username", postgres.getUsername(),
                "quarkus.datasource.flyway.password", postgres.getPassword());
    }

    @Override
    public synchronized void stop() {
        PostgreSQLContainer runningContainer = postgres;
        postgres = null;
        if (runningContainer != null) {
            runningContainer.stop();
        }
    }
}
