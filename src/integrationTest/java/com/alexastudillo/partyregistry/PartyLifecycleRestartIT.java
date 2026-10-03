package com.alexastudillo.partyregistry;

import com.alexastudillo.partyregistry.contract.IdentifierSchemeContractValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Relaunches the packaged executable against retained PostgreSQL data to verify
 * durable historical lifecycle replay.
 */
@Timeout(240)
class PartyLifecycleRestartIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @Test
    void lostSchemeCreateAndAllLifecycleResponsesReplayOriginalAuditAfterRetirementAndRestart() throws Exception {
        var validator = new IdentifierSchemeContractValidator(IdentifierSchemeContractValidator.readContract(
                Path.of("docs/contracts/party-registry.openapi.yaml")));
        String root = "/v1/identifier-schemes";
        UUID tenant = UUID.randomUUID();
        String code = "Restart-Exact-" + UUID.randomUUID();
        String createKey = "create-" + UUID.randomUUID();
        String input = """
                {"code":"%s","issuingCountryCode":"QS","category":"OTHER","applicableSubjectType":"BOTH",
                 "name":"Retained Exact Name","normalizerKey":"TRIM_UPPERCASE_V1","validatorKey":"ALPHANUMERIC_V1"}
                """.formatted(code).strip();
        validator.assertRequest(root, HttpMethod.POST, input);
        List<String> actions = List.of("activate", "deprecate", "retire");
        List<String> states = List.of("ACTIVE", "DEPRECATED", "RETIRED");
        List<JsonNode> accepted;
        List<String> keys = new ArrayList<>();
        String target;
        JsonNode original;
        JsonNode baselineCounts;
        JsonNode persisted;
        long firstPid;
        try (var database = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
                var client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build()) {
            database.withStartupTimeout(Duration.ofSeconds(60));
            database.start();
            try (var first = launch(database, client, "scheme-first")) {
                firstPid = first.process().pid();
                baselineCounts = schemeCounts(database);
                String process = UUID.randomUUID().toString();
                var discarded = client.send(request(first, tenant, process, "scheme-creator", root)
                        .header("Idempotency-Key", createKey).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(input)).build(),
                        HttpResponse.BodyHandlers.discarding());
                // Recover identity only through the public exact-code route; no accepted
                // payload was retained.
                original = get(client, first, tenant, process, root + "/by-code/" + code).path("data");
                verifyCreatedScheme(discarded, process, original, validator);
                target = root + "/" + original.path("id").asText();
                accepted = executeInitialSchemeTransitions(client, first, tenant, target, actions, states, keys,
                        original, validator);
                persisted = schemeStorage(database, original.path("id").asText());
                assertSchemeStorage(persisted, tenant, original, accepted, createKey, keys);
                assertSchemeCounts(baselineCounts, schemeCounts(database));
            }
            try (var relaunched = launch(database, client, "scheme-relaunched")) {
                assertNotEquals(firstPid, relaunched.process().pid());
                assertEquals(persisted, schemeStorage(database, original.path("id").asText()));
                String retryProcess = UUID.randomUUID().toString();
                // Reorder JSON and materialize the nullable/default fields without changing
                // effective intent.
                String equivalent = """
                        {"requiresExpiration":false,"maximumLength":null,"minimumLength":null,"description":null,
                         "validatorKey":"ALPHANUMERIC_V1","normalizerKey":"TRIM_UPPERCASE_V1","name":"Retained Exact Name",
                         "applicableSubjectType":"BOTH","category":"OTHER","issuingCountryCode":"QS","code":"%s"}
                        """
                        .formatted(code);
                validator.assertRequest(root, HttpMethod.POST, equivalent);
                var creationReplay = client.send(request(relaunched, tenant, retryProcess, "fresh-scheme-retry", root)
                        .header("Idempotency-Key", createKey).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(equivalent)).build(),
                        HttpResponse.BodyHandlers.ofString());
                verifyCreationReplay(creationReplay, retryProcess, root, original, validator);
                verifySchemeActionReplays(client, relaunched, database, tenant, root, target, actions, keys, accepted,
                        persisted, original, baselineCounts, validator);
                assertEquals(accepted.get(2), get(client, relaunched, UUID.randomUUID(), UUID.randomUUID().toString(),
                        root + "/by-code/" + code).path("data"));
            }
        }
    }

    /**
     * Verifies that the initial creation response and recovered draft scheme
     * conform to the API specification.
     */
    private static void verifyCreatedScheme(HttpResponse<?> discarded, String expectedProcess, JsonNode original,
            IdentifierSchemeContractValidator validator) {
        assertEquals(201, discarded.statusCode());
        assertEquals(expectedProcess, discarded.headers().firstValue("Process-Id").orElseThrow());
        assertEquals("DRAFT", original.path("status").asText());
        assertEquals(0, original.path("version").longValue());
        assertEquals(original.path("createdAt"), original.path("updatedAt"));
        validator.assertSchema("IdentifierSchemeResponse", original);
    }

    /**
     * Drives the scheme through its activation, deprecation, and retirement
     * transitions before restart.
     */
    private static List<JsonNode> executeInitialSchemeTransitions(HttpClient client, RunningApplication app,
            UUID tenant,
            String target, List<String> actions, List<String> states, List<String> keys, JsonNode original,
            IdentifierSchemeContractValidator validator) throws Exception {
        List<JsonNode> accepted = new ArrayList<>();
        for (int index = 0; index < actions.size(); index++) {
            String action = actions.get(index);
            String key = action + "-" + UUID.randomUUID();
            keys.add(key);
            String process = UUID.randomUUID().toString();
            var lost = client.send(request(app, tenant, process, "scheme-" + action + "-actor", target + "/" + action)
                    .header("Idempotency-Key", key).header("If-Match", Integer.toString(index))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(200, lost.statusCode());
            assertEquals(process, lost.headers().firstValue("Process-Id").orElseThrow());
            JsonNode state = get(client, app, tenant, process, target).path("data");
            assertEquals(states.get(index), state.path("status").asText());
            assertEquals(index + 1, state.path("version").longValue());
            assertEquals(original.path("createdAt"), state.path("createdAt"));
            validator.assertSchema("IdentifierSchemeResponse", state);
            accepted.add(state);
        }
        return accepted;
    }

    /**
     * Verifies that replaying the creation request against the restarted node
     * returns the cached original result.
     */
    private static void verifyCreationReplay(HttpResponse<String> response, String expectedProcess, String root,
            JsonNode expectedOriginal, IdentifierSchemeContractValidator validator) throws Exception {
        assertEquals(201, response.statusCode(), response.body());
        assertEquals(expectedProcess, response.headers().firstValue("Process-Id").orElseThrow());
        validator.assertResponse(root, HttpMethod.POST, 201, response.body());
        assertEquals(expectedOriginal, JSON.readTree(response.body()).path("data"));
    }

    /**
     * Verifies that replaying every historical lifecycle transition against the
     * restarted node yields cached snapshots.
     */
    private static void verifySchemeActionReplays(HttpClient client, RunningApplication relaunched,
            PostgreSQLContainer database, UUID tenant, String root, String target, List<String> actions,
            List<String> keys, List<JsonNode> accepted, JsonNode persisted, JsonNode original,
            JsonNode baselineCounts, IdentifierSchemeContractValidator validator) throws Exception {
        for (int index = 0; index < actions.size(); index++) {
            String retryProcess = UUID.randomUUID().toString();
            var actionReplay = client.send(request(relaunched, tenant, retryProcess, "fresh-scheme-retry-" + index,
                    target + "/" + actions.get(index))
                    .header("Idempotency-Key", keys.get(index)).header("If-Match", Integer.toString(index))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, actionReplay.statusCode(), actionReplay.body());
            assertEquals(retryProcess, actionReplay.headers().firstValue("Process-Id").orElseThrow());
            validator.assertResponse(root + "/{schemeId}/" + actions.get(index), HttpMethod.POST, 200,
                    actionReplay.body());
            assertEquals(accepted.get(index), JSON.readTree(actionReplay.body()).path("data"));
            assertEquals(accepted.get(2), get(client, relaunched, tenant, retryProcess, target).path("data"));
            assertEquals(persisted, schemeStorage(database, original.path("id").asText()));
            assertSchemeCounts(baselineCounts, schemeCounts(database));
        }
    }

    private static JsonNode schemeCounts(PostgreSQLContainer database) throws Exception {
        return JSON.readTree(schemeEvidence(database, """
                select jsonb_build_object('schemes', (select count(*) from identifier_schemes),
                    'replays', (select count(*) from identifier_scheme_idempotency_records),
                    'parties', (select count(*) from parties), 'events', (select count(*) from party_outbox_events),
                    'partyReplays', (select count(*) from api_idempotency_records))::text
                """));
    }

    private static void assertSchemeCounts(JsonNode baseline, JsonNode current) {
        assertEquals(baseline.path("schemes").longValue() + 1, current.path("schemes").longValue());
        assertEquals(baseline.path("replays").longValue() + 4, current.path("replays").longValue());
        for (String unchanged : List.of("parties", "events", "partyReplays"))
            assertEquals(baseline.path(unchanged), current.path(unchanged));
    }

    private static JsonNode schemeStorage(PostgreSQLContainer database, String id) throws Exception {
        // Read-only evidence on the JUnit thread includes every persisted field, digest
        // and immutable JSON snapshot.
        return JSON.readTree(schemeEvidence(database,
                """
                        select jsonb_build_object('scheme', (select to_jsonb(s) from identifier_schemes s where id = '%1$s'),
                            'replays', (select jsonb_agg(to_jsonb(r) order by operation) from identifier_scheme_idempotency_records r
                                where identifier_scheme_id = '%1$s'))::text
                        """
                        .formatted(UUID.fromString(id))));
    }

    private static void assertSchemeStorage(JsonNode storage, UUID tenant, JsonNode original, List<JsonNode> accepted,
            String createKey, List<String> keys) {
        JsonNode scheme = storage.path("scheme");
        assertEquals("scheme-creator", scheme.path("created_by").asText());
        assertEquals("scheme-retire-actor", scheme.path("updated_by").asText());
        assertEquals(3, scheme.path("version").longValue());
        assertEquals("RETIRED", scheme.path("status").asText());
        assertEquals(java.time.Instant.parse(original.path("createdAt").asText()),
                java.time.OffsetDateTime.parse(scheme.path("created_at").asText()).toInstant());
        assertEquals(java.time.Instant.parse(accepted.get(2).path("updatedAt").asText()),
                java.time.OffsetDateTime.parse(scheme.path("updated_at").asText()).toInstant());
        assertEquals(4, storage.path("replays").size());
        var operations = new java.util.HashSet<String>();
        for (JsonNode replay : storage.path("replays")) {
            operations.add(replay.path("operation").asText());
            String action = replay.path("operation").asText().replace("identifier-scheme.", "").replace(".v1", "");
            int index = List.of("activate", "deprecate", "retire").indexOf(action);
            String actor = index < 0 ? "scheme-creator" : "scheme-" + action + "-actor";
            assertEquals(tenant.toString(), replay.path("tenant_id").asText());
            assertEquals(original.path("id"), replay.path("identifier_scheme_id"));
            assertEquals(index < 0 ? createKey : keys.get(index), replay.path("idempotency_key").asText());
            assertEquals(actor, replay.path("created_by").asText());
            assertEquals(1, replay.path("result_snapshot_schema_version").intValue());
            assertTrue(replay.path("request_hash").asText().matches("[0-9a-f]{64}"));
            JsonNode result = replay.path("result_snapshot").path("scheme");
            assertEquals("scheme-creator", result.path("createdBy").asText());
            assertEquals(actor, result.path("updatedBy").asText());
            JsonNode expected = index < 0 ? original : accepted.get(index);
            assertEquals(java.time.Instant.parse(expected.path("updatedAt").asText()),
                    java.time.OffsetDateTime.parse(replay.path("created_at").asText()).toInstant());
            for (var field : expected.properties()) {
                if (field.getKey().endsWith("At")) {
                    assertEquals(java.time.Instant.parse(field.getValue().asText()),
                            java.time.Instant.parse(result.path(field.getKey()).asText()), field.getKey());
                } else {
                    assertEquals(field.getValue(), result.path(field.getKey()), field.getKey());
                }
            }
        }
        assertEquals(java.util.Set.of("identifier-scheme.create.v1", "identifier-scheme.activate.v1",
                "identifier-scheme.deprecate.v1", "identifier-scheme.retire.v1"), operations);
    }

    private static String schemeEvidence(PostgreSQLContainer database, String statement) throws SQLException {
        try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                database.getPassword())) {
            connection.setReadOnly(true);
            try (var query = connection.prepareStatement(statement)) {
                query.setQueryTimeout(10);
                try (var result = query.executeQuery()) {
                    assertTrue(result.next());
                    return result.getString(1);
                }
            }
        }
    }

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
                    assertEquals("ACTIVE",
                            get(client, first, tenant, process, path).path("data").path("recordStatus").asText());
                    String key = "deactivate-" + id;
                    HttpResponse<Void> discarded = client.send(
                            request(first, tenant, process, "original-actor", path + "/deactivate")
                                    .header("If-Match", "0").header("Idempotency-Key", key)
                                    .POST(HttpRequest.BodyPublishers.noBody()).build(),
                            HttpResponse.BodyHandlers.discarding());
                    assertEquals(200, discarded.statusCode());
                    JsonNode deactivated = get(client, first, tenant, process, path);
                    assertEquals("INACTIVE", deactivated.path("data").path("recordStatus").asText());
                    String archiveKey = "archive-" + id;
                    HttpResponse<Void> lostArchive = client.send(
                            request(first, tenant, process, "original-actor", path + "/archive")
                                    .header("If-Match", "1").header("Idempotency-Key", archiveKey)
                                    .POST(HttpRequest.BodyPublishers.noBody()).build(),
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
                    assertEquals(original.deactivated(), replay(client, relaunched, tenant, retryProcess,
                            path + "/deactivate", "0", original.deactivateKey()));
                    assertEquals(original.archived(), replay(client, relaunched, tenant, retryProcess,
                            path + "/archive", "1", original.archiveKey()));
                    assertEquals(original.archived(), get(client, relaunched, tenant, retryProcess, path));
                }
                assertEquals("4,4", counts(database, tenant));
            }
        }
    }

    @Test
    void completedNationalityCreateAndPrimarySnapshotsSurviveARealProcessRestart() throws Exception {
        AtomicInteger countryCalls = new AtomicInteger();
        HttpServer countries = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
                var database = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
                var client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build()) {
            countries.setExecutor(executor);
            countries.createContext("/api/v1/countries/by-alpha2/", exchange -> replyCountry(exchange, countryCalls));
            countries.start();
            String countryUrl = "http://127.0.0.1:" + countries.getAddress().getPort();
            database.withStartupTimeout(Duration.ofSeconds(60));
            database.start();
            UUID tenant = UUID.randomUUID();
            String root;
            JsonNode created;
            JsonNode designated;
            String target;
            long firstPid;
            try (var first = launch(database, client, "nationality-first", countryUrl)) {
                firstPid = first.process().pid();
                UUID party = seed(database, tenant, "LEGAL_ENTITY");
                root = "/v1/parties/" + party + "/nationalities";
                String process = UUID.randomUUID().toString();
                var creation = client.send(request(first, tenant, process, "creator", root)
                        .header("Idempotency-Key", "nationality-created-restart")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"countryCode\":\"EC\"}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(201, creation.statusCode(), creation.body());
                created = JSON.readTree(creation.body());
                target = root + "/" + created.path("data").path("nationalityId").asText();
                var primary = client.send(request(first, tenant, process, "creator", target + "/set-primary")
                        .header("Idempotency-Key", "nationality-primary-restart")
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, primary.statusCode(), primary.body());
                designated = JSON.readTree(primary.body());
                assertTrue(designated.path("data").path("isPrimary").asBoolean());
                var alternative = client.send(request(first, tenant, process, "creator", root)
                        .header("Idempotency-Key", "nationality-alternative-restart")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"countryCode\":\"GB\"}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(201, alternative.statusCode(), alternative.body());
                String otherTarget = root + "/"
                        + JSON.readTree(alternative.body()).path("data").path("nationalityId").asText();
                var switched = client.send(request(first, tenant, process, "creator", otherTarget + "/set-primary")
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, switched.statusCode(), switched.body());
                assertFalse(get(client, first, tenant, process, target).path("data").path("isPrimary").asBoolean());
                assertEquals(2, countryCalls.get());
                assertEquals("3", nationalityKeyCount(database, tenant));
            }
            try (var relaunched = launch(database, client, "nationality-relaunched", countryUrl)) {
                assertNotEquals(firstPid, relaunched.process().pid());
                String process = UUID.randomUUID().toString();
                var creation = client.send(request(relaunched, tenant, process, "retry-actor", root)
                        .header("Idempotency-Key", "nationality-created-restart")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"countryCode\":\" ec \",\"isPrimary\":false,\"validFrom\":null,\"validUntil\":null}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(201, creation.statusCode(), creation.body());
                assertEquals(process, creation.headers().firstValue("Process-Id").orElseThrow());
                assertEquals(created, JSON.readTree(creation.body()));
                var primary = client.send(request(relaunched, tenant, process, "retry-actor", target + "/set-primary")
                        .header("Idempotency-Key", "nationality-primary-restart")
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, primary.statusCode(), primary.body());
                assertEquals(designated, JSON.readTree(primary.body()));
                assertFalse(
                        get(client, relaunched, tenant, process, target).path("data").path("isPrimary").asBoolean());
                assertEquals(2, countryCalls.get());
                assertEquals("3", nationalityKeyCount(database, tenant));
            }
        } finally {
            countries.stop(0);
        }
    }

    private static String nationalityKeyCount(PostgreSQLContainer database, UUID tenant) throws SQLException {
        return sql(database,
                "select count(*) from api_idempotency_records where tenant_id = '%s' and operation like 'nationality.%%'"
                        .formatted(tenant))
                .strip();
    }

    private static void replyCountry(HttpExchange exchange, AtomicInteger calls) throws IOException {
        String code = exchange.getRequestURI().getPath().substring("/api/v1/countries/by-alpha2/".length());
        calls.incrementAndGet();
        String body = "GB".equals(code)
                ? PackagedGeographicReferenceResource.SUCCESS_RESPONSE
                        .replace("\"alpha2Code\": \"EC\"", "\"alpha2Code\": \"GB\"")
                : PackagedGeographicReferenceResource.SUCCESS_RESPONSE;
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Process-Id", exchange.getRequestHeaders().getFirst("Process-Id"));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    private RunningApplication launch(PostgreSQLContainer database, HttpClient client, String name) throws Exception {
        return launch(database, client, name, null);
    }

    private RunningApplication launch(PostgreSQLContainer database, HttpClient client, String name, String countryUrl)
            throws Exception {
        Path descriptor = Path.of("build/quarkus-artifact.properties").toAbsolutePath();
        var artifact = new Properties();
        try (var input = Files.newInputStream(descriptor)) {
            artifact.load(input);
        }
        Path executable = descriptor.getParent()
                .resolve(Objects.requireNonNull(artifact.getProperty("path"), "Packaged artifact path"));
        List<String> command;
        if ("native".equals(artifact.getProperty("type"))) {
            assertTrue(Files.isExecutable(executable), "Configured native executable must exist");
            command = List.of(executable.toString());
        } else {
            assertEquals("jar", artifact.getProperty("type"),
                    "Restart verification requires a JVM or native executable");
            assertTrue(Files.isRegularFile(executable), "Packaged JVM artifact must exist before restart verification");
            command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar",
                    executable.toString());
        }
        int port;
        try (var reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        var builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(temporary.resolve(name + ".log").toFile());
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
        if (countryUrl != null) {
            builder.environment().put("TEST_GEOGRAPHIC_REFERENCE_BASE_URL", countryUrl);
        }
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
        sql(database,
                """
                        insert into parties (id, tenant_id, type, display_name, record_status, created_at, updated_at, created_by, updated_by)
                        values ('%s', '%s', '%s', 'Retained Historical Label', 'ACTIVE', '%s', '%s', 'fixture', 'fixture');
                        """
                        .formatted(id, tenant, type, created, created));
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
        // This isolated fixture/control connection runs on the JUnit thread, never in
        // application request processing.
        try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                database.getPassword());
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

    private static HttpRequest.Builder request(RunningApplication app, UUID tenant, String process, String actor,
            String path) {
        return HttpRequest.newBuilder(app.base().resolve(path)).timeout(REQUEST_TIMEOUT)
                .header("Tenant-Id", tenant.toString())
                .header("Process-Id", process).header("User-Id", actor);
    }

    private static JsonNode get(HttpClient client, RunningApplication app, UUID tenant, String process, String path)
            throws Exception {
        var response = client.send(request(app, tenant, process, "reader", path).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(process, response.headers().firstValue("Process-Id").orElseThrow());
        return JSON.readTree(response.body());
    }

    private static JsonNode replay(HttpClient client, RunningApplication app, UUID tenant, String process, String path,
            String version, String key) throws Exception {
        var response = client.send(request(app, tenant, process, "retry-actor", path).header("If-Match", version)
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(process, response.headers().firstValue("Process-Id").orElseThrow());
        JsonNode body = JSON.readTree(response.body());
        assertEquals("original-actor", body.path("data").path("updatedBy").asText());
        return body;
    }

    /**
     * Retains externally observed accepted results for assertions after the
     * original process is terminated.
     */
    private record SavedResult(UUID id, String deactivateKey, String archiveKey, JsonNode deactivated,
            JsonNode archived) {
    }

    /**
     * Owns one child application process and waits for termination before the next
     * process starts.
     */
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
