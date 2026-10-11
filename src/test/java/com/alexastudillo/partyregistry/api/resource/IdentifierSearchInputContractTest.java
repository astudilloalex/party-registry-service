package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.IdentifierSearchContractAssertions;
import com.alexastudillo.partyregistry.contract.IdentifierSchemeContractValidator;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises real search HTTP header precedence, strict binding, Unicode limits
 * and concurrent completion attribution.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSearchInputContractTest {
    private static final String PROCESS = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
    private static final String TENANT = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private final IdentifierSearchContractAssertions assertions = new IdentifierSearchContractAssertions(
            IdentifierSchemeContractValidator.readContract(Path.of("docs/contracts/party-registry.openapi.yaml")));

    @Test
    void headerCardinalityAndCanonicalValuesPrecedeInvalidBodies() {
        for (String path : paths()) {
            for (String header : List.of("Process-Id", "Tenant-Id", "User-Id")) {
                String echo = header.equals("Process-Id") ? null : PROCESS;
                reject(without(header).body("{"), path, header.toLowerCase(Locale.ROOT) + "-required", echo);
                reject(without(header).header(header, "RejectedSensitive", "RejectedSensitive").body("{"), path,
                        header.toLowerCase(Locale.ROOT) + "-duplicated", echo);
            }
            for (String header : List.of("Process-Id", "Tenant-Id")) {
                String value = header.equals("Process-Id") ? PROCESS : TENANT;
                for (String invalid : List.of(value.toUpperCase(Locale.ROOT), "not-a-uuid", "1-1-1-1-1")) {
                    reject(without(header).header(header, invalid).body("{"), path,
                            header.toLowerCase(Locale.ROOT) + "-invalid", header.equals("Process-Id") ? null : PROCESS);
                }
            }
            reject(given().contentType("application/json").body("{"), path, "process-id-required", null);
            reject(given().contentType("application/json").header("Process-Id", PROCESS).body("{"), path,
                    "tenant-id-required", PROCESS);
        }
    }

    @Test
    void acceptsCaseInsensitiveNamesAndUserBoundaryAndExemptsManagement() {
        for (String path : paths()) {
            var response = given().contentType("application/json").header("process-id", PROCESS)
                    .header("tenant-id", TENANT)
                    .header("user-id", "a".repeat(128)).body(body(path, "17")).post(path);
            success(path, response, 50);
            reject(without("User-Id").header("User-Id", " ").body(body(path, "17")), path, "user-id-blank", PROCESS);
            reject(without("User-Id").header("User-Id", "a".repeat(129)).body(body(path, "17")), path,
                    "user-id-too-long", PROCESS);
            reject(without("User-Id").config(io.restassured.RestAssured.config().httpClient(
                    io.restassured.config.HttpClientConfig.httpClientConfig().setParam("http.protocol.element-charset", "ISO-8859-1")))
                    .header("User-Id", "unsafe\u0085user").body(body(path, "17")), path,
                    "user-id-unsafe", PROCESS);
        }
        assertEquals(200, given().get("/q/health/live").statusCode());
    }

    @Test
    void missingNullMalformedAndAmbiguousBodiesHaveOnlyStableErrors() {
        for (String path : paths()) {
            reject(without(""), path, "request-body-required", PROCESS);
            reject(without("").body("null"), path, "request-body-required", PROCESS);
            String field = field(path);
            String valid = body(path, "SensitiveSelector");
            for (String invalid : List.of("{", "[]", "true", "{}", valid + " {}",
                    valid.replace("}", ",\"unknownSensitive\":true}"),
                    valid.replace("}", ",\"" + field + "\":\"duplicateSensitive\"}"),
                    valid.replace("\"SensitiveSelector\"", "null"),
                    valid.replace("\"SensitiveSelector\"", "true"),
                    valid.replace("SensitiveSelector", " "),
                    valid.replace("\"EC_NATIONAL_ID\"", "null"),
                    valid.replace("\"EC_NATIONAL_ID\"", "1"))) {
                reject(without("").body(invalid), path, "bad-request", PROCESS);
            }
            reject(without("").body(valid), path + "?unknown=SensitiveQuery", "bad-request", PROCESS);
        }
    }

    @Test
    void filterTokensAndEmptySetsAreDistinctOverHttp() {
        for (String path : paths()) {
            for (String dimension : List.of("identifierStatuses", "partyRecordStatuses")) {
                for (String token : List.of("null", "[null]", "[true]", "[1]", "[\"verified\"]", "[\"UNKNOWN\"]",
                        "\"VERIFIED\"")) {
                    reject(without("").body(extend(body(path, "17"), "\"" + dimension + "\":" + token)), path,
                            "bad-request", PROCESS);
                }
                var empty = without("").body(extend(body(path, "17"), "\"" + dimension + "\":[]")).post(path);
                success(path, empty, 50);
                assertEquals(0, empty.jsonPath().getInt("totalElements"));
            }
        }
    }

    @Test
    void rawUtf16BoundariesApplyBeforeSchemeAndNormalization() {
        for (String path : paths()) {
            int bound = path.equals(IdentifierSearchContractAssertions.EXACT_PATH) ? 256 : 64;
            for (String value : List.of("V".repeat(bound), "😀".repeat(bound / 2))) {
                success(path, without("").body(body(path, value)).post(path), 50);
                reject(without("").body(body(path, value + "X")), path, "bad-request", PROCESS);
            }
            var permitted = without("").body(body(path, "17").replace("EC_NATIONAL_ID", "S".repeat(64))).post(path);
            error(permitted, 422, "unknown-identifier-scheme", PROCESS);
            reject(without("").body(body(path, "17").replace("EC_NATIONAL_ID", "S".repeat(65))), path, "bad-request",
                    PROCESS);
        }
    }

    @Test
    void prefixLimitsAndCursorTokensAreStrictAndExactCannotBePaginated() {
        String path = IdentifierSearchContractAssertions.PREFIX_PATH;
        for (String token : List.of("null", "\"50\"", "true", "1.0", "1e0", "0", "-1", "201",
                "999999999999999999999999999999")) {
            reject(without("").body(extend(body(path, "17"), "\"limit\":" + token)), path, "bad-request", PROCESS);
        }
        for (String token : List.of("\"\"", "\" \"", "true", "1", "[]", "\"" + "T".repeat(257) + "\"",
                "\"alteredSensitiveToken\"")) {
            reject(without("").body(extend(body(path, "17"), "\"cursor\":" + token)), path, "bad-request", PROCESS);
        }
        for (int limit : List.of(1, 50, 200)) {
            success(path,
                    without("").body(extend(body(path, "17"), "\"limit\":" + limit + ",\"cursor\":null")).post(path),
                    limit);
        }
        String exact = IdentifierSearchContractAssertions.EXACT_PATH;
        for (String extra : List.of("\"limit\":1", "\"cursor\":null")) {
            reject(without("").body(extend(body(exact, "17"), extra)), exact, "bad-request", PROCESS);
        }
        for (String route : paths()) {
            success(route, without("").header("If-Match", "ignored").header("Idempotency-Key", "ignored")
                    .body(body(route, "17")).post(route), 50);
        }
    }

    @Test
    void concurrentSuccessAndFailureKeepOnlyTheirAcceptedCompletionContext() throws Exception {
        var logs = new CompletionCapture();
        Logger root = Logger.getLogger("");
        root.addHandler(logs);
        var start = new CountDownLatch(1);
        try (var clients = Executors.newFixedThreadPool(4)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Attribution>>();
            for (int index = 0; index < 4; index++) {
                int selected = index;
                futures.add(clients.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    String process = UUID.randomUUID().toString();
                    String tenant = UUID.randomUUID().toString();
                    String user = "concurrent-search-" + selected;
                    String path = paths().get(selected % 2);
                    var response = given().contentType("application/json").header("Process-Id", process)
                            .header("Tenant-Id", tenant).header("User-Id", user)
                            .body(selected < 2 ? body(path, "17") : "{").post(path);
                    assertEquals(process, response.header("Process-Id"));
                    assertEquals(selected < 2 ? 200 : 400, response.statusCode());
                    return new Attribution(process, tenant, user);
                }));
            }
            start.countDown();
            for (var future : futures) {
                var expected = future.get(30, TimeUnit.SECONDS);
                var attributed = logs.records.stream().filter(log -> expected.process().equals(log.context().process()))
                        .toList();
                assertEquals(1, attributed.size());
                assertEquals(expected, attributed.getFirst().context());
                assertFalse(attributed.getFirst().message().contains("Sensitive"));
            }
            reject(given().contentType("application/json").body("{"), paths().getFirst(), "process-id-required", null);
            success(paths().getFirst(), without("").body(body(paths().getFirst(), "17")).post(paths().getFirst()), 50);
        } finally {
            root.removeHandler(logs);
            logs.close();
        }
    }

    private void success(String path, Response response, int limit) {
        assertEquals(200, response.statusCode());
        assertEquals(PROCESS, response.header("Process-Id"));
        if (path.equals(IdentifierSearchContractAssertions.EXACT_PATH))
            assertions.assertExactResponse(response.statusCode(), response.asString());
        else
            assertions.assertPrefixResponse(response.statusCode(), response.asString(), limit);
    }

    private static void reject(RequestSpecification request, String path, String code, String echo) {
        error(request.post(path), 400, code, echo);
    }

    private static void error(Response response, int status, String code, String echo) {
        assertEquals(status, response.statusCode());
        assertEquals(echo, response.header("Process-Id"));
        IdentifierSearchContractAssertions.assertErrorResponse(response.statusCode(), code, response.asString());
    }

    private static RequestSpecification without(String excluded) {
        var request = given().contentType("application/json");
        Map.of("Process-Id", PROCESS, "Tenant-Id", TENANT, "User-Id", "search-input-contract")
                .forEach((name, value) -> {
                    if (!name.equals(excluded))
                        request.header(name, value);
                });
        return request;
    }

    private static List<String> paths() {
        return List.of(IdentifierSearchContractAssertions.EXACT_PATH, IdentifierSearchContractAssertions.PREFIX_PATH);
    }

    private static String field(String path) {
        return path.equals(IdentifierSearchContractAssertions.EXACT_PATH) ? "value" : "prefix";
    }

    private static String body(String path, String value) {
        return "{\"identifierSchemeCode\":\"EC_NATIONAL_ID\",\"" + field(path) + "\":\"" + value + "\"}";
    }

    private static String extend(String body, String extra) {
        return body.substring(0, body.length() - 1) + "," + extra + "}";
    }

    /**
     * Snapshots only the accepted request identifiers for concurrency assertions on
     * test-owned log records.
     */
    private record Attribution(String process, String tenant, String user) {
    }

    /** Holds an immutable completion message and its accepted MDC snapshot. */
    private record Completion(String message, Attribution context) {
    }

    /**
     * Captures completion records before the single request filter clears its owned
     * MDC.
     */
    private static final class CompletionCapture extends Handler {
        private final List<Completion> records = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord logRecord) {
            if (logRecord instanceof ExtLogRecord extended && logRecord.getMessage().startsWith("Request completed")) {
                records.add(new Completion(new SimpleFormatter().formatMessage(logRecord), new Attribution(
                        extended.getMdc("processId"), extended.getMdc("tenantId"), extended.getMdc("userId"))));
            }
        }

        @Override
        public void flush() {
            //
        }

        @Override
        public void close() {
            //
        }
    }
}
