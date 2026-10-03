package com.alexastudillo.partyregistry;

import com.alexastudillo.partyregistry.contract.IdentifierSchemeContractValidator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Validates all eight scheme routes, strict native-capable decoders, signed pages and published schemas on the real artifact. */
@QuarkusIntegrationTest
@QuarkusTestResource(value = PackagedGeographicReferenceResource.class, restrictToAnnotatedClass = true)
@Timeout(240)
class PackagedIdentifierSchemeContractIT {
    private static final String ROOT = "/v1/identifier-schemes";
    private static final String ITEM = ROOT + "/{schemeId}";
    private static final String CODE = ROOT + "/by-code/{code}";
    private static final List<String> ACTIONS = List.of("activate", "deprecate", "retire");
    private final OpenAPI contract = IdentifierSchemeContractValidator.readContract(Path.of("docs/contracts/party-registry.openapi.yaml"));
    private final IdentifierSchemeContractValidator validator = new IdentifierSchemeContractValidator(contract);

    @Test
    void exercisesEightRoutesPresenceAwareUpdatesAndHistoricalReplayAcrossTenants() {
        Context context = Context.fresh();
        String code = "Exact-Mixed-" + UUID.randomUUID();
        String input = body(code, "QA");
        String key = UUID.randomUUID().toString();
        Response created = create(context, input, key);
        Map<String, Object> initial = data(created, ROOT, HttpMethod.POST, 201, context);
        verifyInitialScheme(initial);
        String target = ROOT + "/" + initial.get("id");
        Context other = Context.fresh();
        verifySchemeState(other, target, code, initial);
        error(other.request().pathParam("code", code.toLowerCase(java.util.Locale.ROOT)).get(CODE), CODE, HttpMethod.GET,
                404, "identifier-scheme-not-found", other);

        String patch = "{\"description\":\"Preserved\",\"minimumLength\":1,\"maximumLength\":32767}";
        validator.assertRequest(ITEM, HttpMethod.PATCH, patch);
        Map<String, Object> bounded = data(patch(context, target, "0", patch), ITEM, HttpMethod.PATCH, 200, context);
        patch = "{\"name\":\"Exact Mixed Name\",\"description\":null}";
        validator.assertRequest(ITEM, HttpMethod.PATCH, patch);
        Map<String, Object> equalEdit = data(patch(context, target, "1", patch), ITEM, HttpMethod.PATCH, 200, context);
        verifyPatchUpdates(initial, bounded, equalEdit);

        Map<String, Object> current = executeLifecycleTransitions(context, other, target, code, equalEdit);

        Context retry = new Context(context.tenant(), "fresh-retry-actor", UUID.randomUUID().toString());
        assertEquals(initial, data(create(retry, input, key), ROOT, HttpMethod.POST, 201, retry));
        assertEquals(current, data(context.request().get(target), ITEM, HttpMethod.GET, 200, context));
        Response visible = other.request().queryParam("issuingCountryCode", "QA").queryParam("status", "RETIRED").get(ROOT);
        page(visible, 1, false, false, other);
        assertEquals(current, visible.jsonPath().getMap("data[0]"));
        error(create(other, input, UUID.randomUUID().toString()), ROOT, HttpMethod.POST, 409, "identifier-scheme-code-conflict", other);
    }

    @Test
    void strictCreateAndPatchDecodersPreserveSyntaxSemanticAndStatePrecedence() {
        Context context = Context.fresh();
        String input = body("Decoder-" + UUID.randomUUID(), "QB");
        String target = ROOT + "/" + data(create(context, input, UUID.randomUUID().toString()), ROOT, HttpMethod.POST, 201, context).get("id");
        Map<String, String> invalidCreate = Map.ofEntries(
                Map.entry("null", "request-body-required"), Map.entry("[]", "bad-request"),
                Map.entry("true", "bad-request"), Map.entry("{}", "identifier-scheme-code-required"),
                Map.entry("{", "bad-request"), Map.entry(input.replace("\"code\":", "\"code\":\"duplicate\",\"code\":"), "bad-request"),
                Map.entry(append(input, "\"status\":\"DRAFT\""), "bad-request"),
                Map.entry(append(input, "\"requiresExpiration\":null"), "bad-request"),
                Map.entry(append(input, "\"minimumLength\":\"1\""), "bad-request"),
                Map.entry(input.replace("\"OTHER\"", "\"UNKNOWN\""), "bad-request"));
        error(context.request().contentType("application/json").post(ROOT), ROOT, HttpMethod.POST, 400, "request-body-required", context);
        for (var invalid : invalidCreate.entrySet()) {
            error(create(context, invalid.getKey(), UUID.randomUUID().toString()), ROOT, HttpMethod.POST, 400, invalid.getValue(), context);
        }
        Map<String, String> invalidPatch = Map.ofEntries(
                Map.entry("null", "request-body-required"), Map.entry("{}", "patch-property-required"),
                Map.entry("[]", "bad-request"), Map.entry("false", "bad-request"), Map.entry("{", "bad-request"),
                Map.entry("{\"name\":\"A\",\"name\":\"B\"}", "bad-request"),
                Map.entry("{\"name\":\"A\",\"code\":\"immutable\"}", "bad-request"),
                Map.entry("{\"name\":null}", "bad-request"), Map.entry("{\"requiresExpiration\":null}", "bad-request"),
                Map.entry("{\"maximumLength\":true}", "bad-request"), Map.entry("{\"maximumLength\":1.5}", "bad-request"));
        error(context.request().contentType("application/json").patch(target), ITEM, HttpMethod.PATCH, 400, "request-body-required", context);
        for (var invalid : invalidPatch.entrySet()) {
            // Structural decoding precedes both invalid path and missing precondition.
            error(context.request().contentType("application/json").body(invalid.getKey()).patch(ROOT + "/INVALID"),
                    ITEM, HttpMethod.PATCH, 400, invalid.getValue(), context);
        }
        String huge = "9".repeat(300);
        for (String bound : List.of("0", "-1", "32768", huge)) {
            String badCreate = append(body("Range-" + UUID.randomUUID(), "QB"), "\"maximumLength\":" + bound);
            error(create(context, badCreate, UUID.randomUUID().toString()), ROOT, HttpMethod.POST,
                    422, "identifier-scheme-length-range-invalid", context);
            error(patch(context, target, "0", "{\"maximumLength\":" + bound + "}"), ITEM, HttpMethod.PATCH,
                    422, "identifier-scheme-length-range-invalid", context);
        }
        error(create(context, body("Rules-" + UUID.randomUUID(), "QB").replace("TRIM_UPPERCASE_V1", "UNSUPPORTED_V1"),
                UUID.randomUUID().toString()), ROOT, HttpMethod.POST, 422, "invalid-identifier-scheme-configuration", context);
        String hugePatch = "{\"maximumLength\":" + huge + "}";
        error(patch(context, ROOT + "/" + UUID.randomUUID(), "9", hugePatch), ITEM, HttpMethod.PATCH, 404, "identifier-scheme-not-found", context);
        error(patch(context, target, "9", hugePatch), ITEM, HttpMethod.PATCH, 412, "expected-version-mismatch", context);
        data(context.request().header("If-Match", "0").post(target + "/activate"), ITEM + "/activate", HttpMethod.POST, 200, context);
        error(patch(context, target, "1", hugePatch), ITEM, HttpMethod.PATCH, 409, "identifier-scheme-rules-locked", context);
        for (String action : ACTIONS) {
            error(context.request().header("If-Match", huge).post(target + "/" + action),
                    ITEM + "/" + action, HttpMethod.POST, 400, "if-match-out-of-range", context);
            for (byte[] body : List.of("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    " ".getBytes(java.nio.charset.StandardCharsets.UTF_8), "null".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    new byte[]{0}, new byte[]{(byte) 0xff})) {
                error(context.request().header("If-Match", "1").body(body).post(target + "/" + action),
                        ITEM + "/" + action, HttpMethod.POST, 400, "bad-request", context);
            }
        }
        data(context.request().header("If-Match", "1").post(target + "/retire"), ITEM + "/retire", HttpMethod.POST, 200, context);
        error(patch(context, target, "2", hugePatch), ITEM, HttpMethod.PATCH, 409, "identifier-scheme-retired", context);
        error(context.request().header("If-Match", "2").post(target + "/activate"), ITEM + "/activate", HttpMethod.POST,
                409, "invalid-identifier-scheme-lifecycle", context);
        assertEquals(2, context.request().get(target).jsonPath().getInt("data.version"));
    }

    @Test
    void authenticatesAllNavigationScopeAndTraversesTimestampTiesWithoutDuplicates() {
        Context context = Context.fresh();
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            ids.add((String) data(create(context, body("Page-" + UUID.randomUUID(), "QP"), UUID.randomUUID().toString()),
                    ROOT, HttpMethod.POST, 201, context).get("id"));
        }
        Map<String, String> filters = Map.of("issuingCountryCode", "QP", "category", "OTHER",
                "applicableSubjectType", "BOTH", "status", "DRAFT", "limit", "1");
        Response first = context.request().queryParams(filters).get(ROOT);
        page(first, 1, true, false, context);
        String cursor = first.jsonPath().getString("nextCursor");
        Response second = context.request().queryParams(filters).queryParam("cursor", cursor).get(ROOT);
        page(second, 1, true, true, context);
        Response last = context.request().queryParams(filters).queryParam("cursor", second.jsonPath().getString("nextCursor")).get(ROOT);
        page(last, 1, false, true, context);
        List<String> observed = List.of(first.jsonPath().getString("data[0].id"), second.jsonPath().getString("data[0].id"), last.jsonPath().getString("data[0].id"));
        assertEquals(ids, observed);
        assertEquals(3, new HashSet<>(observed).size());
        Response previous = context.request().queryParams(filters).queryParam("cursor", last.jsonPath().getString("prevCursor")).get(ROOT);
        page(previous, 1, true, true, context);
        assertEquals(second.jsonPath().getList("data"), previous.jsonPath().getList("data"));
        Response beginning = context.request().queryParams(filters).queryParam("cursor", previous.jsonPath().getString("prevCursor")).get(ROOT);
        page(beginning, 1, true, false, context);
        assertEquals(first.jsonPath().getList("data"), beginning.jsonPath().getList("data"));
        for (var changed : Map.of("issuingCountryCode", "QR", "category", "PASSPORT", "applicableSubjectType", "NATURAL_PERSON",
                "status", "ACTIVE", "limit", "2").entrySet()) {
            var scope = new java.util.HashMap<>(filters);
            scope.put(changed.getKey(), changed.getValue());
            error(context.request().queryParams(scope).queryParam("cursor", cursor).get(ROOT), ROOT, HttpMethod.GET, 400, "bad-request", context);
        }
        Context other = Context.fresh();
        error(other.request().queryParams(filters).queryParam("cursor", cursor).get(ROOT), ROOT, HttpMethod.GET, 400, "bad-request", other);
        error(context.request().queryParams(filters).queryParam("cursor", (cursor.startsWith("A") ? "B" : "A") + cursor.substring(1)).get(ROOT),
                ROOT, HttpMethod.GET, 400, "bad-request", context);
        page(other.request().queryParam("issuingCountryCode", "QP").get(ROOT), 3, false, false, other);
        page(context.request().queryParam("issuingCountryCode", "QP").queryParam("applicableSubjectType", "NATURAL_PERSON").get(ROOT),
                0, false, false, context);
        for (String query : List.of("unknown=1", "status=DRAFT&status=ACTIVE", "issuingCountryCode=qp", "limit=", "limit=1.5", "limit=0", "limit=201")) {
            error(context.request().get(ROOT + "?" + query), ROOT, HttpMethod.GET, 400, "bad-request", context);
        }

        // V1000 inserts these three rows in one statement, giving an immutable timestamp-tie dataset.
        List<String> tied = List.of("0198d111-08f1-7e48-b291-399bbb9cd604", "0198d111-08f1-7e48-b291-399bbb9cd605", "0198d111-08f1-7e48-b291-399bbb9cd606");
        String next = null;
        String timestamp = null;
        Response tiedLast = null;
        for (int index = 0; index < tied.size(); index++) {
            var request = context.request().queryParam("issuingCountryCode", "EC").queryParam("category", "OTHER")
                    .queryParam("applicableSubjectType", "BOTH").queryParam("limit", "1");
            if (next != null) request.queryParam("cursor", next);
            Response result = request.get(ROOT);
            page(result, 1, index < 2, index > 0, context);
            assertEquals(tied.get(index), result.jsonPath().getString("data[0].id"));
            if (timestamp == null) timestamp = result.jsonPath().getString("data[0].createdAt");
            assertEquals(timestamp, result.jsonPath().getString("data[0].createdAt"));
            next = result.jsonPath().getString("nextCursor");
            tiedLast = result;
        }
        assertNotNull(tiedLast);
        Response tiedPrevious = context.request().queryParam("issuingCountryCode", "EC").queryParam("category", "OTHER")
                .queryParam("applicableSubjectType", "BOTH").queryParam("limit", "1")
                .queryParam("cursor", tiedLast.jsonPath().getString("prevCursor")).get(ROOT);
        page(tiedPrevious, 1, true, true, context);
        assertEquals(tied.get(1), tiedPrevious.jsonPath().getString("data[0].id"));
        String partyCursor = givenContext("0198d5f0-0000-7000-8000-000000000100", context.process(), "reader")
                .queryParam("limit", "1").get("/v1/parties").jsonPath().getString("nextCursor");
        assertNotNull(partyCursor);
        error(context.request().queryParam("cursor", partyCursor).get(ROOT), ROOT, HttpMethod.GET, 400, "bad-request", context);
    }

    @Test
    void frameworkAndTrustedContextFailuresUseOnlyDeclaredStableEnvelopes() {
        Context context = Context.fresh();
        error(context.request().delete(ROOT), ROOT, HttpMethod.GET, 405, "method-not-allowed", context);
        error(context.request().contentType("text/plain").body("bad").post(ROOT), ROOT, HttpMethod.POST, 415, "unsupported-media-type", context);
        error(context.request().get(ROOT + "/" + UUID.randomUUID() + "/unknown"), ITEM, HttpMethod.GET, 404, "not-found", context);
        error(context.request().get(ROOT + "/" + UUID.randomUUID()), ITEM, HttpMethod.GET, 404, "identifier-scheme-not-found", context);
        Response missing = transport().get(ROOT);
        error(missing, ROOT, HttpMethod.GET, 400, "process-id-required", new Context("", "", null));
        error(givenContext(context.tenant(), "invalid", "reader").get(ROOT), ROOT, HttpMethod.GET, 400, "process-id-invalid", new Context("", "", null));
        error(context.request().header("Process-Id", UUID.randomUUID().toString()).get(ROOT), ROOT, HttpMethod.GET,
                400, "process-id-duplicated", new Context("", "", null));
        error(transport().header("Process-Id", context.process()).get(ROOT), ROOT, HttpMethod.GET, 400, "tenant-id-required", context);
        error(transport().header("Process-Id", context.process()).header("Tenant-Id", context.tenant()).get(ROOT),
                ROOT, HttpMethod.GET, 400, "user-id-required", context);
        data(create(context, body("After-Context-" + UUID.randomUUID(), "QC"), UUID.randomUUID().toString()), ROOT, HttpMethod.POST, 201, context);
    }

    @Test
    void approvedProcessedAndServedOpenApiAreEqualWithResolvedOperationsAndExamples() {
        OpenAPI packaged = IdentifierSchemeContractValidator.readContract(Path.of("build/resources/main/META-INF/openapi.yaml"));
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var parsed = new OpenAPIV3Parser().readContents(transport().get("/q/openapi").then().statusCode(200).extract().asString(), null, options);
        assertNotNull(parsed.getOpenAPI());
        assertTrue(parsed.getMessages().isEmpty(), parsed.getMessages().toString());
        OpenAPI served = parsed.getOpenAPI();
        assertEquals(contract, packaged);
        var sourceTree = io.swagger.v3.core.util.Json.mapper().valueToTree(contract);
        var servedTree = io.swagger.v3.core.util.Json.mapper().valueToTree(served);
        // SmallRye inlines references; compare all eight scheme operations and their complete resolved definitions.
        for (String path : List.of(ROOT, ITEM, CODE, ITEM + "/activate", ITEM + "/deprecate", ITEM + "/retire")) {
            assertEquivalentDocument(resolveReferences(sourceTree, sourceTree.path("paths").path(path), 0),
                    resolveReferences(servedTree, servedTree.path("paths").path(path), 0), path);
        }
        for (String schema : List.of("IdentifierSchemeCreateRequest", "IdentifierSchemeUpdateRequest", "IdentifierSchemeResponse",
                "IdentifierSchemeCreatedApiResponse", "IdentifierSchemeApiResponse", "IdentifierSchemeCollectionApiResponse", "ApiErrorResponse")) {
            assertEquivalentDocument(resolveReferences(sourceTree, sourceTree.path("components").path("schemas").path(schema), 0),
                    resolveReferences(servedTree, servedTree.path("components").path("schemas").path(schema), 0), schema);
        }
        for (OpenAPI api : List.of(contract, packaged, served)) {
            var schemas = new IdentifierSchemeContractValidator(api);
            int operations = 0;
            for (var path : api.getPaths().entrySet()) {
                if (!path.getKey().startsWith(ROOT)) continue;
                for (var operation : path.getValue().readOperationsMap().entrySet()) {
                    operations++;
                    if (operation.getValue().getRequestBody() != null) {
                        for (var example : IdentifierSchemeContractValidator.examples(operation.getValue().getRequestBody().getContent().get("application/json"))) {
                            schemas.assertRequest(path.getKey(), operation.getKey(), example.toString());
                        }
                    }
                    for (var response : operation.getValue().getResponses().entrySet()) {
                        if (response.getKey().equals("default")) continue;
                        for (var example : IdentifierSchemeContractValidator.examples(schemas.response(response.getValue()).getContent().get("application/json"))) {
                            schemas.assertResponse(path.getKey(), operation.getKey(), Integer.parseInt(response.getKey()), example.toString());
                        }
                    }
                }
            }
            assertEquals(8, operations);
        }
    }

    private Response create(Context context, String input, String key) {
        return context.request().header("Idempotency-Key", key).contentType("application/json").body(input).post(ROOT);
    }

    private static com.fasterxml.jackson.databind.JsonNode resolveReferences(com.fasterxml.jackson.databind.JsonNode root,
            com.fasterxml.jackson.databind.JsonNode node, int depth) {
        assertTrue(depth < 50, "Contract references must be bounded");
        if (node.has("$ref")) {
            String reference = node.path("$ref").asText();
            assertTrue(reference.startsWith("#/"));
            var target = root.at(reference.substring(1));
            assertFalse(target.isMissingNode(), reference);
            return resolveReferences(root, target, depth + 1);
        }
        if (node.isObject()) {
            var resolved = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            node.properties().forEach(field -> resolved.set(field.getKey(), resolveReferences(root, field.getValue(), depth + 1)));
            return resolved;
        }
        if (node.isArray()) {
            var resolved = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> resolved.add(resolveReferences(root, value, depth + 1)));
            return resolved;
        }
        return node;
    }

    private static void assertEquivalentDocument(com.fasterxml.jackson.databind.JsonNode expected,
            com.fasterxml.jackson.databind.JsonNode actual, String path) {
        if (expected.isNumber() && actual.isNumber()) {
            assertEquals(0, expected.decimalValue().compareTo(actual.decimalValue()), path);
        } else if (expected.isObject()) {
            assertTrue(actual.isObject(), path);
            assertEquals(expected.properties().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()),
                    actual.properties().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()), path);
            expected.properties().forEach(field -> assertEquivalentDocument(field.getValue(), actual.path(field.getKey()), path + "/" + field.getKey()));
        } else if (expected.isArray()) {
            assertTrue(actual.isArray(), path);
            assertEquals(expected.size(), actual.size(), path);
            if (path.endsWith("/allOf")) {
                // Conjunction order is immaterial and SmallRye emits the local restriction before the referenced base.
                List<com.fasterxml.jackson.databind.JsonNode> unmatched = new ArrayList<>();
                actual.forEach(unmatched::add);
                for (var part : expected) {
                    boolean matched = false;
                    for (int index = 0; index < unmatched.size(); index++) {
                        try {
                            assertEquivalentDocument(part, unmatched.get(index), path);
                            unmatched.remove(index);
                            matched = true;
                            break;
                        } catch (AssertionError _) {
                            // Try the next conjunct while retaining every required constraint.
                        }
                    }
                    assertTrue(matched, () -> path + " missing conjunct " + part);
                }
            } else {
                for (int index = 0; index < expected.size(); index++) assertEquivalentDocument(expected.get(index), actual.get(index), path + "/" + index);
            }
        } else {
            assertEquals(expected, actual, path);
        }
    }

    private static Response patch(Context context, String target, String version, String input) {
        return context.request().header("If-Match", version).contentType("application/json").body(input).patch(target);
    }

    private Map<String, Object> data(Response response, String path, HttpMethod method, int status, Context context) {
        check(response, path, method, status, context);
        assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
        return response.jsonPath().getMap("data");
    }

    private void page(Response response, int size, boolean next, boolean previous, Context context) {
        check(response, ROOT, HttpMethod.GET, 200, context);
        var fields = new HashSet<>(Set.of("status", "code", "data", "numberOfElements"));
        if (next) fields.add("nextCursor");
        if (previous) fields.add("prevCursor");
        assertEquals(fields, response.jsonPath().getMap("$").keySet());
        assertEquals(size, response.jsonPath().getInt("numberOfElements"));
        assertEquals(size, response.jsonPath().getList("data").size());
    }

    private void error(Response response, String path, HttpMethod method, int status, String code, Context context) {
        check(response, path, method, status, context);
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
    }

    private void check(Response response, String path, HttpMethod method, int status, Context context) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(context.process(), response.header("Process-Id"));
        validator.assertResponse(path, method, status, response.asString());
    }

    private static String body(String code, String country) {
        return """
                {"code":"%s","issuingCountryCode":"%s","category":"OTHER","applicableSubjectType":"BOTH",
                 "name":"Exact Mixed Name","normalizerKey":"TRIM_UPPERCASE_V1","validatorKey":"ALPHANUMERIC_V1"}
                """.formatted(code, country).strip();
    }

    private static String append(String input, String property) {
        return input.substring(0, input.length() - 1) + "," + property + "}";
    }

    private static RequestSpecification transport() {
        return given().config(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", 10000).setParam("http.socket.timeout", 20000)
                .setParam("http.conn-manager.timeout", 10000L)));
    }

    private static RequestSpecification givenContext(String tenant, String process, String user) {
        return transport().header("Process-Id", process).header("Tenant-Id", tenant).header("User-Id", user);
    }

    private static void verifyInitialScheme(Map<String, Object> initial) {
        assertEquals("DRAFT", initial.get("status"));
        assertEquals(0, ((Number) initial.get("version")).longValue());
        assertEquals(initial.get("createdAt"), initial.get("updatedAt"));
        assertFalse((Boolean) initial.get("requiresExpiration"));
        assertFalse(initial.containsKey("description"));
    }

    private void verifySchemeState(Context context, String target, String code, Map<String, Object> expected) {
        assertEquals(expected, data(context.request().get(target), ITEM, HttpMethod.GET, 200, context));
        assertEquals(expected, data(context.request().pathParam("code", code).get(CODE), CODE, HttpMethod.GET, 200, context));
    }

    private static void verifyPatchUpdates(Map<String, Object> initial, Map<String, Object> bounded, Map<String, Object> equalEdit) {
        assertEquals(1, ((Number) bounded.get("version")).longValue());
        assertEquals(initial.get("name"), bounded.get("name"));
        assertEquals(2, ((Number) equalEdit.get("version")).longValue());
        assertFalse(equalEdit.containsKey("description"));
        assertEquals(bounded.get("minimumLength"), equalEdit.get("minimumLength"));
        assertEquals(bounded.get("maximumLength"), equalEdit.get("maximumLength"));
        assertEquals(initial.get("createdAt"), equalEdit.get("createdAt"));
    }

    private Map<String, Object> executeLifecycleTransitions(Context context, Context other, String target, String code, Map<String, Object> state) {
        Map<String, Object> current = state;
        for (int index = 0; index < ACTIONS.size(); index++) {
            String action = ACTIONS.get(index);
            current = data(context.request().header("If-Match", index + 2)
                    .body(new byte[0]).post(target + "/" + action), ITEM + "/" + action, HttpMethod.POST, 200, context);
            assertEquals(List.of("ACTIVE", "DEPRECATED", "RETIRED").get(index), current.get("status"));
            assertEquals(index + 3, ((Number) current.get("version")).longValue());
            verifySchemeState(other, target, code, current);
        }
        return current;
    }

    /** Supplies independent trusted request attribution without any server-side injection. */
    private record Context(String tenant, String user, String process) {
        static Context fresh() {
            return new Context(UUID.randomUUID().toString(), "packaged-scheme-operator", UUID.randomUUID().toString());
        }

        RequestSpecification request() {
            return givenContext(tenant, process, user);
        }
    }
}
