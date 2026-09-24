package com.alexastudillo.partyregistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Relaunches the packaged executable against retained PostgreSQL data to verify durable historical lifecycle replay. */
@Timeout(240)
class PartyLifecycleRestartIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @Test
    void discardedResponsesReplayAfterARealProcessRestartWithNewCorrelationAndNoNewEvents() throws Exception {
        try (var database = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
                var client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build()) {
            database.withStartupTimeout(Duration.ofSeconds(60));
            database.start();
            UUID tenant = UUID.randomUUID();
            List<SavedResult> saved = new ArrayList<>();
            long firstPid;
            try (var first = launch(database, client, "first")) {
                firstPid = first.process().pid();
                for (String type : List.of("NATURAL_PERSON", "LEGAL_ENTITY")) {
                    UUID id = seed(database, tenant, type);
                    String path = "/v1/parties/" + id;
                    String process = UUID.randomUUID().toString();
                    assertEquals("ACTIVE", get(client, first, tenant, process, path).path("data").path("recordStatus").asText());
                    String key = "deactivate-" + id;
                    HttpResponse<Void> discarded = client.send(request(first, tenant, process, "original-actor", path + "/deactivate")
                            .header("If-Match", "0").header("Idempotency-Key", key).POST(HttpRequest.BodyPublishers.noBody()).build(),
                            HttpResponse.BodyHandlers.discarding());
                    assertEquals(200, discarded.statusCode());
                    JsonNode deactivated = get(client, first, tenant, process, path);
                    assertEquals("INACTIVE", deactivated.path("data").path("recordStatus").asText());
                    String archiveKey = "archive-" + id;
                    HttpResponse<Void> lostArchive = client.send(request(first, tenant, process, "original-actor", path + "/archive")
                            .header("If-Match", "1").header("Idempotency-Key", archiveKey).POST(HttpRequest.BodyPublishers.noBody()).build(),
                            HttpResponse.BodyHandlers.discarding());
                    assertEquals(200, lostArchive.statusCode());
                    JsonNode archived = get(client, first, tenant, process, path);
                    saved.add(new SavedResult(id, key, archiveKey, deactivated, archived));
                }
                assertEquals("4,4", counts(database, tenant));
            }
            try (var relaunched = launch(database, client, "relaunched")) {
                assertNotEquals(firstPid, relaunched.process().pid());
                for (SavedResult original : saved) {
                    String retryProcess = UUID.randomUUID().toString();
                    String path = "/v1/parties/" + original.id();
                    assertEquals(original.deactivated(), replay(client, relaunched, tenant, retryProcess, path + "/deactivate", "0", original.deactivateKey()));
                    assertEquals(original.archived(), replay(client, relaunched, tenant, retryProcess, path + "/archive", "1", original.archiveKey()));
                    assertEquals(original.archived(), get(client, relaunched, tenant, retryProcess, path));
                }
                assertEquals("4,4", counts(database, tenant));
            }
        }
    }

    private RunningApplication launch(PostgreSQLContainer database, HttpClient client, String name) throws Exception {
        Path descriptor = Path.of("build/quarkus-artifact.properties").toAbsolutePath();
        var artifact = new Properties();
        try (var input = Files.newInputStream(descriptor)) {
            artifact.load(input);
        }
        Path executable = descriptor.getParent().resolve(Objects.requireNonNull(artifact.getProperty("path"), "Packaged artifact path"));
        List<String> command;
        if ("native".equals(artifact.getProperty("type"))) {
            assertTrue(Files.isExecutable(executable), "Configured native executable must exist");
            command = List.of(executable.toString());
        } else {
            assertEquals("jar", artifact.getProperty("type"), "Restart verification requires a JVM or native executable");
            assertTrue(Files.isRegularFile(executable), "Packaged JVM artifact must exist before restart verification");
            command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", executable.toString());
        }
        int port;
        try (var reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(temporary.resolve(name + ".log").toFile());
        builder.environment().putAll(Map.ofEntries(
                Map.entry("QUARKUS_PROFILE", "test"),
                Map.entry("QUARKUS_HTTP_PORT", Integer.toString(port)),
                Map.entry("QUARKUS_DATASOURCE_DEVSERVICES_ENABLED", "false"),
                Map.entry("QUARKUS_DATASOURCE_USERNAME", database.getUsername()),
                Map.entry("QUARKUS_DATASOURCE_PASSWORD", database.getPassword()),
                Map.entry("TEST_DB_USERNAME", database.getUsername()),
                Map.entry("TEST_DB_PASSWORD", database.getPassword()),
                Map.entry("QUARKUS_DATASOURCE_JDBC_URL", database.getJdbcUrl()),
                Map.entry("QUARKUS_DATASOURCE_REACTIVE_URL", database.getJdbcUrl().substring("jdbc:".length())),
                Map.entry("_TEST_QUARKUS_FLYWAY_LOCATIONS", "db/migration"),
                Map.entry("_TEST_PARTY_REGISTRY_OUTBOX_MODE", "stored-only")));
        var application = new RunningApplication(builder.start(), URI.create("http://localhost:" + port));
        try {
            awaitReady(application, client);
            return application;
        } catch (Exception | AssertionError failure) {
            application.close();
            throw failure;
        }
    }

    private static void awaitReady(RunningApplication application, HttpClient client) {
        var probe = HttpRequest.newBuilder(application.base().resolve("/q/health/ready"))
                .timeout(Duration.ofSeconds(2)).GET().build();
        await("packaged application readiness")
                .atMost(Duration.ofSeconds(60))
                .pollDelay(Duration.ZERO)
                .pollInterval(Duration.ofMillis(100))
                .failFast("Packaged application exited before readiness", () -> !application.process().isAlive())
                .ignoreExceptionsMatching(IOException.class::isInstance)
                .until(() -> client.send(probe, HttpResponse.BodyHandlers.discarding()).statusCode() == 200);
    }

    private static UUID seed(PostgreSQLContainer database, UUID tenant, String type) throws Exception {
        UUID id = UUID.randomUUID();
        String created = LocalDate.of(2026, Month.SEPTEMBER, 19).atStartOfDay().toInstant(ZoneOffset.UTC).toString();
        sql(database, """
                insert into parties (id, tenant_id, type, display_name, record_status, created_at, updated_at, created_by, updated_by)
                values ('%s', '%s', '%s', 'Retained Historical Label', 'ACTIVE', '%s', '%s', 'fixture', 'fixture');
                """.formatted(id, tenant, type, created, created));
        String details = type.equals("NATURAL_PERSON")
                ? "insert into natural_person_details (party_id,given_names,family_names,created_by,updated_by) values ('%s','Original','Person','fixture','fixture')"
                : "insert into legal_entity_details (party_id,legal_name,incorporation_country_code,created_by,updated_by) values ('%s','Original Company','GB','fixture','fixture')";
        sql(database, details.formatted(id));
        return id;
    }

    private static String counts(PostgreSQLContainer database, UUID tenant) throws Exception {
        return sql(database, """
                select (select count(*) from api_idempotency_records where tenant_id = '%s')::text || ',' ||
                       (select count(*) from party_outbox_events where tenant_id = '%s')::text
                """.formatted(tenant, tenant)).strip();
    }

    private static String sql(PostgreSQLContainer database, String statement) throws SQLException {
        // This isolated fixture/control connection runs on the JUnit thread, never in application request processing.
        try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
                var query = connection.prepareStatement(statement)) {
            query.setQueryTimeout(10);
            if (!query.execute()) {
                return "";
            }
            try (var result = query.getResultSet()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static HttpRequest.Builder request(RunningApplication app, UUID tenant, String process, String actor, String path) {
        return HttpRequest.newBuilder(app.base().resolve(path)).timeout(REQUEST_TIMEOUT).header("Tenant-Id", tenant.toString())
                .header("Process-Id", process).header("User-Id", actor);
    }

    private static JsonNode get(HttpClient client, RunningApplication app, UUID tenant, String process, String path) throws Exception {
        var response = client.send(request(app, tenant, process, "reader", path).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(process, response.headers().firstValue("Process-Id").orElseThrow());
        return JSON.readTree(response.body());
    }

    private static JsonNode replay(HttpClient client, RunningApplication app, UUID tenant, String process, String path, String version, String key) throws Exception {
        var response = client.send(request(app, tenant, process, "retry-actor", path).header("If-Match", version).header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(process, response.headers().firstValue("Process-Id").orElseThrow());
        JsonNode body = JSON.readTree(response.body());
        assertEquals("original-actor", body.path("data").path("updatedBy").asText());
        return body;
    }

    /** Retains externally observed accepted results for assertions after the original process is terminated. */
    private record SavedResult(UUID id, String deactivateKey, String archiveKey, JsonNode deactivated, JsonNode archived) {
    }

    /** Owns one child application process and waits for termination before the next process starts. */
    private record RunningApplication(Process process, URI base) implements AutoCloseable {
        @Override
        public void close() throws InterruptedException {
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            }
            assertFalse(process.isAlive());
        }
    }
}
