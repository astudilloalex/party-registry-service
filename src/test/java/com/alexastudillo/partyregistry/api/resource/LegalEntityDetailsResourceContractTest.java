package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.api.filter.RequestContextFilter;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.GeographicReferenceStubResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jboss.logmanager.ExtLogRecord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies legal-detail workflows and cause-specific failures through the real
 * reactive HTTP boundary.
 */
@QuarkusTest
@Timeout(60)
class LegalEntityDetailsResourceContractTest {

    private static final String PATH = "/v1/legal-entity";
    private static final String TENANT = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String PROCESS = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
    private static final String USER = "geographic-reference-adapter-test";
    private static final Context DEFAULT_CONTEXT = new Context(TENANT, USER, PROCESS);

    @Test
    void createsRetrievesReplacesPatchesAndReplaysWithoutRewritingTheCreationSnapshot() {
        Created created = create();
        Map<String, Object> creation = data(created.response(), 201);
        String id = created.partyId();
        Map<String, Object> current = data(request().get(PATH + "/" + id), 200);
        assertCreationSnapshot(creation, current);

        Response put = request().contentType(ContentType.JSON).header("If-Match", "0")
                .body("{\"legalName\":\"New Legal\",\"incorporationCountryCode\":\" ec \"}")
                .put(PATH + "/" + id);
        Map<String, Object> replaced = data(put, 200);
        assertReplacedState(current, put, replaced);
        assertEquals(replaced, data(request().get(PATH + "/" + id), 200));

        Response patch = patch(id, "1", "{\"tradeName\":\"  New Brand  \"}");
        Map<String, Object> patched = data(patch, 200);
        assertPatchedState(patch, patched);
        assertEquals(patched, data(request().get(PATH + "/" + id), 200));
        error(patch(id, "1", "{\"tradeName\":\"Stale\"}"), 412, "expected-version-mismatch");
        assertEquals(creation, data(post(created.key(), created.body()), 201));
        assertEquals(patched, data(request().get(PATH + "/" + id), 200));
    }

    private static void assertCreationSnapshot(Map<String, Object> creation, Map<String, Object> current) {
        assertEquals("LEGAL_ENTITY", current.get("type"));
        assertEquals("CUSTOM LABEL", current.get("displayName"));
        assertFalse(current.containsKey("initialIdentifier"));
        assertFalse(current.containsKey("identifiers"));
        assertEquals(creation.get("legalEntityDetails"), current.get("legalEntityDetails"));
    }

    private static void assertReplacedState(Map<String, Object> current, Response put, Map<String, Object> replaced) {
        assertEquals("NEW LEGAL", replaced.get("displayName"));
        assertEquals(1, put.jsonPath().getInt("data.version"));
        assertNull(put.jsonPath().get("data.legalEntityDetails.tradeName"));
        assertNull(put.jsonPath().get("data.legalEntityDetails.legalFormCode"));
        assertNull(put.jsonPath().get("data.legalEntityDetails.incorporatedOn"));
        assertNull(put.jsonPath().get("data.legalEntityDetails.dissolvedOn"));
        assertEquals(current.get("createdAt"), replaced.get("createdAt"));
        assertEquals(current.get("createdBy"), replaced.get("createdBy"));
        assertEquals(current.get("recordStatus"), replaced.get("recordStatus"));
    }

    private static void assertPatchedState(Response patch, Map<String, Object> patched) {
        assertEquals(2, patch.jsonPath().getInt("data.version"));
        assertEquals("NEW BRAND", patch.jsonPath().getString("data.legalEntityDetails.tradeName"));
        assertEquals("NEW LEGAL", patched.get("displayName"));
    }

    @Test
    void knownDateCountryAndVersionFailuresHaveSpecificCodesAndLeaveStateUnchanged() {
        Created created = create();
        String id = created.partyId();
        Map<String, Object> before = data(request().get(PATH + "/" + id), 200);
        error(patch(id, "0", "{\"dissolvedOn\":\"2019-12-31\"}"), 422, "dissolution-before-incorporation");
        error(patch(id, "0", "{\"incorporatedOn\":\"2999-01-01\"}"), 422, "incorporation-date-in-future");
        error(patch(id, "0", "{\"dissolvedOn\":\"2999-01-01\"}"), 422, "dissolution-date-in-future");
        error(patch(id, "0", "{\"incorporationCountryCode\":\"ZZ\"}"), 422, "unrecognized-incorporation-country");
        error(patch(id, "0", "{\"tradeName\":\"Rejected\",\"incorporationCountryCode\":\"SE\"}"),
                503, "dependency-unavailable");
        error(patch(id, "1", "{\"incorporationCountryCode\":\"SE\"}"), 412, "expected-version-mismatch");
        assertEquals(before, data(request().get(PATH + "/" + id), 200));
    }

    @Test
    void typedBindingsAndValidationPrecedenceUseTheSharedErrorEnvelope() {
        Created created = create();
        String id = created.partyId();
        error(request().contentType(ContentType.JSON).body("{}").put(PATH + "/" + id),
                400, "incorporation-country-code-required");
        error(patch(id, "0", "{}"), 400, "patch-property-required");
        error(patch(id, "0", "{\"legalName\":null}"), 400, "legal-name-required");
        error(patch(id, "0", "{\"incorporationCountryCode\":null}"), 400, "incorporation-country-code-required");
        error(patch(id, "0", "{\"legalName\":42}"), 400, "bad-request");
        error(patch(id, "0", "{\"incorporatedOn\":[2020,1,1]}"), 400, "bad-request");
        error(patch(id, "0", "{\"legalNamePresent\":true}"), 400, "bad-request");
        error(patch(id, "0", "{"), 400, "bad-request");
        error(patch(id, "0", "null"), 400, "request-body-required");
        Response clear = patch(id, "0", "{\"tradeName\":null}");
        data(clear, 200);
        assertNull(clear.jsonPath().get("data.legalEntityDetails.tradeName"));
        assertEquals("EXAMPLE TRADING", clear.jsonPath().getString("data.legalEntityDetails.legalName"));
        assertEquals("CUSTOM LABEL", clear.jsonPath().getString("data.displayName"));
    }

    @Test
    void concealsAbsentCrossTenantAndWrongTypePartiesForAllThreeOperations() {
        Created legal = create();
        Response natural = request().contentType(ContentType.JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body("""
                        {"givenNames":"Other","familyNames":"Type",
                         "initialIdentifier":{"identifierSchemeCode":"TEST_NATURAL_ACTIVE","value":"%s"}}
                        """.formatted("NP" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)))
                .post("/v1/natural-person");
        data(natural, 201);
        String naturalId = natural.jsonPath().getString("data.partyId");
        Context otherTenant = new Context(UUID.randomUUID().toString(), USER, PROCESS);
        for (String method : List.of("GET", "PUT", "PATCH")) {
            for (String id : List.of(UUID.randomUUID().toString(), naturalId)) {
                error(operation(DEFAULT_CONTEXT, method, id, "0", validBody(method)),
                        404, "legal-entity-not-found");
            }
            error(operation(otherTenant, method, legal.partyId(), "0", validBody(method)),
                    404, "legal-entity-not-found");
            error(operation(DEFAULT_CONTEXT, method, "not-a-uuid", "0", validBody(method)),
                    400, "party-id-invalid");
        }
        assertEquals(0, request().get(PATH + "/" + legal.partyId()).jsonPath().getInt("data.version"));
    }

    @Test
    void validatesEveryVersionHeaderFormAndKeepsTheRecordUnchanged() {
        Created created = create();
        for (String method : List.of("PUT", "PATCH")) {
            error(operation(DEFAULT_CONTEXT, method, created.partyId(), null, validBody(method)), 400,
                    "if-match-required");
            for (String version : List.of(" ", "-1", "01", "1.0", "*", "\"0\"")) {
                error(operation(DEFAULT_CONTEXT, method, created.partyId(), version, validBody(method)),
                        400, "if-match-invalid");
            }
            error(operation(DEFAULT_CONTEXT, method, created.partyId(), "9223372036854775808", validBody(method)),
                    400, "if-match-out-of-range");
            error(request().contentType(ContentType.JSON).header("If-Match", "0", "0").body(validBody(method))
                    .request(method, PATH + "/" + created.partyId()), 400, "if-match-duplicated");
        }
        assertEquals(0, request().get(PATH + "/" + created.partyId()).jsonPath().getInt("data.version"));
    }

    @Test
    void trustedHeaderFailuresRemainSpecificOnEveryLegalDetailOperation() {
        Created created = create();
        List<InvalidHeader> cases = List.of(
                new InvalidHeader("Process-Id", List.of(), "process-id-required"),
                new InvalidHeader("Process-Id", List.of(PROCESS, PROCESS), "process-id-duplicated"),
                new InvalidHeader("Process-Id", List.of(PROCESS.toUpperCase(java.util.Locale.ROOT)),
                        "process-id-invalid"),
                new InvalidHeader("Tenant-Id", List.of(), "tenant-id-required"),
                new InvalidHeader("Tenant-Id", List.of(TENANT, TENANT), "tenant-id-duplicated"),
                new InvalidHeader("Tenant-Id", List.of("invalid"), "tenant-id-invalid"),
                new InvalidHeader("User-Id", List.of(), "user-id-required"),
                new InvalidHeader("User-Id", List.of(USER, USER), "user-id-duplicated"),
                new InvalidHeader("User-Id", List.of(" "), "user-id-blank"),
                new InvalidHeader("User-Id", List.of("u".repeat(129)), "user-id-too-long"));
        for (String method : List.of("GET", "PUT", "PATCH")) {
            for (InvalidHeader invalid : cases) {
                RequestSpecification request = given().accept(ContentType.JSON);
                for (var header : Map.of("Process-Id", PROCESS, "Tenant-Id", TENANT, "User-Id", USER).entrySet()) {
                    if (!header.getKey().equals(invalid.name())) {
                        request.header(header.getKey(), header.getValue());
                    } else if (invalid.values().size() == 1) {
                        request.header(header.getKey(), invalid.values().getFirst());
                    } else if (invalid.values().size() == 2) {
                        request.header(header.getKey(), invalid.values().getFirst(), invalid.values().getLast());
                    }
                }
                if (!method.equals("GET")) {
                    request.contentType(ContentType.JSON).header("If-Match", "0").body(validBody(method));
                }
                error(request.request(method, PATH + "/" + created.partyId()), 400, invalid.code(),
                        invalid.name().equals("Process-Id") ? null : PROCESS);
            }
            error(operation(DEFAULT_CONTEXT, method, "0198CE2A-7B7D-7AB4-A5CF-4D4D7DB89AB1", "0", validBody(method)),
                    400, "party-id-invalid");
        }
        data(given().accept(ContentType.JSON).header("tenant-id", TENANT).header("user-id", USER)
                .header("process-id", PROCESS).get(PATH + "/" + created.partyId()), 200);
    }

    @Test
    void rejectionLogsDoNotContainUnknownPropertyNamesOrRejectedValues() {
        Created created = create();
        String secretName = "private-field-" + UUID.randomUUID();
        String secretValue = "private-value-" + UUID.randomUUID();
        List<String> logs = new CopyOnWriteArrayList<>();
        Logger root = Logger.getLogger("");
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                if (logRecord instanceof ExtLogRecord extended && PROCESS.equals(extended.getMdc("processId"))) {
                    logs.add(new SimpleFormatter().formatMessage(logRecord)
                            + (logRecord.getThrown() == null ? "" : logRecord.getThrown().toString()));
                }
            }

            @Override
            public void flush() {
                // Captured messages are immediately available to the test.
            }

            @Override
            public void close() {
                // The test owns handler registration.
            }
        };
        root.addHandler(handler);
        try {
            error(patch(created.partyId(), "0", "{\"" + secretName + "\":\"" + secretValue + "\"}"),
                    400, "bad-request");
            error(patch(created.partyId(), "0", "{\"tradeName\":\"" + secretValue + "x".repeat(301) + "\"}"),
                    400, "trade-name-too-long");
        } finally {
            root.removeHandler(handler);
            handler.close();
        }
        assertTrue(logs.stream().anyMatch(message -> message.contains("source=json-binding")));
        assertTrue(logs.stream().anyMatch(message -> message.contains("code=trade-name-too-long")));
        assertFalse(String.join("\n", logs).contains(secretName));
        assertFalse(String.join("\n", logs).contains(secretValue));
    }

    @Test
    void validatesFieldBoundariesAndStrictBodiesThroughHttp() {
        Created created = create();
        for (String method : List.of("PUT", "PATCH")) {
            for (InvalidBody invalid : List.of(
                    new InvalidBody("{\"legalName\":\" \",\"incorporationCountryCode\":\"EC\"}", "legal-name-required"),
                    new InvalidBody("{\"legalName\":\"" + "ß".repeat(151) + "\",\"incorporationCountryCode\":\"EC\"}",
                            "legal-name-invalid"),
                    new InvalidBody("{\"legalName\":\"Legal\",\"tradeName\":\"" + "😀".repeat(151)
                            + "\",\"incorporationCountryCode\":\"EC\"}", "trade-name-too-long"),
                    new InvalidBody("{\"legalName\":\"Legal\",\"legalFormCode\":\"" + "ß".repeat(33)
                            + "\",\"incorporationCountryCode\":\"EC\"}", "legal-form-code-too-long"),
                    new InvalidBody("{\"legalName\":\"Legal\",\"incorporationCountryCode\":\"ß\"}",
                            "incorporation-country-code-invalid"),
                    new InvalidBody("{\"legalName\":\"Legal\",\"incorporationCountryCode\":\"E C\"}",
                            "incorporation-country-code-invalid"),
                    new InvalidBody("{\"legalName\":true}", "bad-request"),
                    new InvalidBody("{\"incorporatedOn\":\"2020-02-30\"}", "bad-request"),
                    new InvalidBody("[]", "bad-request"),
                    new InvalidBody("null", "request-body-required"))) {
                error(operation(DEFAULT_CONTEXT, method, created.partyId(), "0", invalid.body()), 400, invalid.code());
            }
            error(request().contentType(ContentType.JSON).header("If-Match", "0")
                    .request(method, PATH + "/" + created.partyId()), 400, "request-body-required");
        }
        Response boundary = request().contentType(ContentType.JSON).header("If-Match", "0").body("""
                {"legalName":"%s","tradeName":"%s","legalFormCode":"%s","incorporationCountryCode":" ec "}
                """.formatted("ß".repeat(150), "😀".repeat(150), "ß".repeat(32))).put(PATH + "/" + created.partyId());
        data(boundary, 200);
        assertEquals(300, boundary.jsonPath().getString("data.legalEntityDetails.legalName").length());
        assertEquals(300, boundary.jsonPath().getString("data.legalEntityDetails.tradeName").length());
        assertEquals(64, boundary.jsonPath().getString("data.legalEntityDetails.legalFormCode").length());
    }

    @Test
    void handlesUnusableDependenciesAndUnexpectedPersistenceFailuresWithoutPartialWrites() {
        Created created = create();
        Map<String, Object> before = data(request().get(PATH + "/" + created.partyId()), 200);
        for (String country : List.of("SE", "MJ", "MD", "CT", "TO", "CF")) {
            error(patch(created.partyId(), "0",
                    "{\"tradeName\":\"Rejected\",\"incorporationCountryCode\":\"" + country + "\"}"),
                    503, "dependency-unavailable");
            assertEquals(before, data(request().get(PATH + "/" + created.partyId()), 200));
        }
        error(patch(created.partyId(), "0", "{\"legalFormCode\":\"BAD\\u0000CODE\"}"), 500, "server-error");
        assertEquals(before, data(request().get(PATH + "/" + created.partyId()), 200));
        Response changed = patch(created.partyId(), "0", "{\"incorporationCountryCode\":\" gb \"}");
        data(changed, 200);
        assertEquals("GB", changed.jsonPath().getString("data.legalEntityDetails.incorporationCountryCode"));
    }

    @Test
    void frameworkErrorsRetainTheirStatusAndSharedEnvelope() {
        Created created = create();
        error(request().delete(PATH + "/" + created.partyId()), 405, "method-not-allowed");
        for (String method : List.of("PUT", "PATCH")) {
            Response response = request().contentType(ContentType.TEXT).header("If-Match", "0").body("text")
                    .request(method, PATH + "/" + created.partyId());
            assertEquals(415, response.statusCode());
            assertEquals(415, response.jsonPath().getInt("status"));
            assertEquals(Set.of("status", "code"), response.jsonPath().getMap("$").keySet());
            assertEquals(PROCESS, response.header("Process-Id"));
            assertEquals("unsupported-media-type", response.jsonPath().getString("code"));
        }
    }

    @Test
    void concurrentHttpCorrectionsHaveExactlyOneWinner() throws Exception {
        Created created = create();
        List<Response> responses = race(
                () -> patch(created.partyId(), "0", "{\"tradeName\":\"First Brand\"}"),
                () -> request().contentType(ContentType.JSON).header("If-Match", "0")
                        .body("{\"legalName\":\"Second Legal\",\"tradeName\":\"Second Brand\",\"incorporationCountryCode\":\"EC\"}")
                        .put(PATH + "/" + created.partyId()));
        List<Response> winners = responses.stream().filter(response -> response.statusCode() == 200).toList();
        List<Response> losers = responses.stream().filter(response -> response.statusCode() == 412).toList();
        assertEquals(1, winners.size());
        assertEquals(1, losers.size());
        error(losers.getFirst(), 412, "expected-version-mismatch");
        assertEquals(1, winners.getFirst().jsonPath().getInt("data.version"));
        assertEquals(data(winners.getFirst(), 200), data(request().get(PATH + "/" + created.partyId()), 200));
    }

    @Test
    void propagatesDistinctContextsAcrossConcurrentCountryValidationAndClearsRejectedContext() throws Exception {
        Context one = new Context(UUID.randomUUID().toString(), "legal-editor-one", UUID.randomUUID().toString());
        Context two = new Context(UUID.randomUUID().toString(), "legal-editor-two", UUID.randomUUID().toString());
        List<Completion> completions = new CopyOnWriteArrayList<>();
        Logger logger = Logger.getLogger(RequestContextFilter.class.getName());
        Handler handler = completionHandler(completions);
        logger.addHandler(handler);
        try (var _ = GeographicReferenceStubResource.allowContext(one.tenant(), one.user(), one.process());
                var _ = GeographicReferenceStubResource.allowContext(two.tenant(), two.user(), two.process())) {
            Created firstParty = create(one);
            Created secondParty = create(two);
            List<Response> responses = race(
                    () -> patch(one, firstParty.partyId(), "0", "{\"incorporationCountryCode\":\"gb\"}"),
                    () -> patch(two, secondParty.partyId(), "0", "{\"incorporationCountryCode\":\"gb\"}"));
            for (int index = 0; index < responses.size(); index++) {
                Context context = List.of(one, two).get(index);
                Response response = responses.get(index);
                data(response, 200, context.process());
                assertEquals(context.user(), response.jsonPath().getString("data.updatedBy"));
                assertEquals(List.of(context.tenant(), context.user(), context.process()),
                        GeographicReferenceStubResource.observedContext(context.process(), "GB"));
                assertTrue(completions.stream().anyMatch(log -> context.process().equals(log.process())
                        && context.tenant().equals(log.tenant()) && context.user().equals(log.user())
                        && log.message().contains("operation=patch-legal-entity")));
            }
            error(request(two).get(PATH + "/" + firstParty.partyId()), 404, "legal-entity-not-found", two.process());
            Context invalid = new Context(one.tenant(), one.user(), "invalid-process");
            Response rejected = request(invalid).get(PATH + "/" + firstParty.partyId());
            error(rejected, 400, "process-id-invalid", null);
            assertTrue(completions.stream().anyMatch(log -> log.message().contains("code=process-id-invalid")
                    && log.process() == null && log.tenant() == null && log.user() == null));
        } finally {
            logger.removeHandler(handler);
            handler.close();
        }
    }

    private static Created create() {
        return create(DEFAULT_CONTEXT);
    }

    private static Created create(Context context) {
        String key = "legal-detail-" + UUID.randomUUID();
        String identifier = "LE" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        String body = """
                {"displayName":"Custom Label","legalName":"Example Trading","tradeName":"Old Brand",
                 "legalFormCode":"SA","incorporationCountryCode":"EC","incorporatedOn":"2020-01-15",
                 "initialIdentifier":{"identifierSchemeCode":"TEST_LEGAL_ACTIVE","value":"%s","isPrimary":true}}
                """.formatted(identifier);
        Response response = request(context).contentType(ContentType.JSON).header("Idempotency-Key", key).body(body)
                .post(PATH);
        data(response, 201, context.process());
        return new Created(response, key, body);
    }

    private static Response post(String key, String body) {
        return request().contentType(ContentType.JSON).header("Idempotency-Key", key).body(body).post(PATH);
    }

    private static Response patch(String id, String version, String body) {
        return patch(DEFAULT_CONTEXT, id, version, body);
    }

    private static Response patch(Context context, String id, String version, String body) {
        return request(context).contentType(ContentType.JSON).header("If-Match", version).body(body)
                .patch(PATH + "/" + id);
    }

    private static RequestSpecification request() {
        return request(DEFAULT_CONTEXT);
    }

    private static RequestSpecification request(Context context) {
        return given().accept(ContentType.JSON).header("Tenant-Id", context.tenant())
                .header("User-Id", context.user()).header("Process-Id", context.process());
    }

    private static Map<String, Object> data(Response response, int status) {
        return data(response, status, PROCESS);
    }

    private static Map<String, Object> data(Response response, int status, String process) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(status, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(process, response.header("Process-Id"));
        assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
        return new LinkedHashMap<>(response.jsonPath().getMap("data"));
    }

    private static void error(Response response, int status, String code) {
        error(response, status, code, PROCESS);
    }

    private static void error(Response response, int status, String code, String process) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(process, response.header("Process-Id"));
    }

    private static Response operation(Context context, String method, String id, String version, String body) {
        RequestSpecification request = request(context);
        if (!method.equals("GET")) {
            request.contentType(ContentType.JSON).body(body);
            if (version != null)
                request.header("If-Match", version);
        }
        return request.request(method, PATH + "/" + id);
    }

    private static String validBody(String method) {
        return method.equals("PUT") ? "{\"legalName\":\"Legal\",\"incorporationCountryCode\":\"EC\"}"
                : "{\"tradeName\":\"Brand\"}";
    }

    private static List<Response> race(Supplier<Response> first, Supplier<Response> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> afterStart(ready, start, first));
            var two = executor.submit(() -> afterStart(ready, start, second));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        }
    }

    private static Response afterStart(CountDownLatch ready, CountDownLatch start, Supplier<Response> operation)
            throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return operation.get();
    }

    private static Handler completionHandler(List<Completion> target) {
        return new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                if (logRecord instanceof ExtLogRecord extended) {
                    String message = new SimpleFormatter().formatMessage(logRecord);
                    if (message.startsWith("Request completed")) {
                        target.add(new Completion(extended.getMdc("processId"), extended.getMdc("tenantId"),
                                extended.getMdc("userId"), message));
                    }
                }
            }

            @Override
            public void flush() {
                // Observations are retained directly in memory.
            }

            @Override
            public void close() {
                // The owning test removes this handler.
            }
        };
    }

    /**
     * Associates an invalid representation with its exact public validation code.
     */
    private record InvalidBody(String body, String code) {
    }

    /**
     * Defines a malformed context-header scenario and its specific response code.
     */
    private record InvalidHeader(String name, List<String> values, String code) {
    }

    /** Supplies one independently correlated trusted request context. */
    private record Context(String tenant, String user, String process) {
    }

    /** Captures final request MDC values before the response filter clears them. */
    private record Completion(String process, String tenant, String user, String message) {
    }

    /**
     * Captures an original registration so its snapshot can be replayed after
     * detail updates.
     */
    private record Created(Response response, String key, String body) {
        private String partyId() {
            return response.jsonPath().getString("data.partyId");
        }
    }
}
