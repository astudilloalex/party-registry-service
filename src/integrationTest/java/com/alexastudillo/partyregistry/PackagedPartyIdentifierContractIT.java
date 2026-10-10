package com.alexastudillo.partyregistry;

import com.alexastudillo.partyregistry.contract.IdentifierSchemeContractValidator;
import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises all eight identifier routes, strict native-capable binding, signed pages and exact packaged schemas on actual JVM/native artifacts. */
@QuarkusIntegrationTest
@QuarkusTestResource(value = PackagedGeographicReferenceResource.class, restrictToAnnotatedClass = true)
@Timeout(180)
class PackagedPartyIdentifierContractIT {
    private static final String ROOT = "/v1/parties/{partyId}/identifiers";
    private static final String ITEM = ROOT + "/{identifierId}";
    private final ObjectMapper json = new ObjectMapper();
    private final OpenAPI contract = IdentifierSchemeContractValidator.readContract(Path.of("docs/contracts/party-registry.openapi.yaml"));
    private final IdentifierSchemeContractValidator validator = new IdentifierSchemeContractValidator(contract);
    @TestHTTPResource URI server;

    @Test
    void allEightOperationsForBothPartyTypesPreserveSafeHistoricalReplayAfterLaterReplacementAndWithdrawal() throws Exception {
        for (boolean legal : List.of(false, true)) {
            verifyPartyIdentifierOperationsAndHistoricalReplay(legal);
        }
    }

    private void verifyPartyIdentifierOperationsAndHistoricalReplay(boolean legal) throws Exception {
        var context = Context.fresh();
        String party = owner(context, legal);
        String base = "/v1/parties/" + party + "/identifiers";
        String scheme = legal ? "TEST_LEGAL_ACTIVE" : "TEST_NATURAL_ACTIVE";
        String input = body(scheme);
        String createKey = UUID.randomUUID().toString();
        JsonNode initial = created(context, base, input, createKey);
        String target = base + "/" + initial.path("identifierId").textValue();

        JsonNode corrected = success(context.request().header("If-Match", "0").body("{\"issuerCode\":\"civil_registry\"}").patch(target), ITEM, HttpMethod.PATCH, context);
        assertEquals("CIVIL_REGISTRY", corrected.path("issuerCode").textValue());

        String verifyKey = UUID.randomUUID().toString();
        JsonNode verified = success(action(context, target, "verify", "1", verifyKey, ""), ITEM + "/verify", HttpMethod.POST, context);

        String primaryKey = UUID.randomUUID().toString();
        JsonNode selected = success(action(context, target, "set-primary", "2", primaryKey, ""), ITEM + "/set-primary", HttpMethod.POST, context);
        assertTrue(selected.path("isPrimary").booleanValue());

        JsonNode revoked = executeReplacementAndWithdrawal(context, base, scheme, target, primaryKey, selected);
        verifyHistoricalReplay(context, base, target, input, createKey, initial, verifyKey, verified, revoked);
        verifyCursorPagination(context, base);
    }

    private JsonNode executeReplacementAndWithdrawal(Context context, String base, String scheme, String target, String primaryKey, JsonNode selected) throws Exception {
        JsonNode second = created(context, base, body(scheme), UUID.randomUUID().toString());
        String other = base + "/" + second.path("identifierId").textValue();
        success(action(context, other, "verify", "0", null, ""), ITEM + "/verify", HttpMethod.POST, context);
        success(action(context, other, "set-primary", "1", UUID.randomUUID().toString(), "{\"replaceCurrentPrimary\":true}"), ITEM + "/set-primary", HttpMethod.POST, context);

        JsonNode demoted = success(context.request().get(target), ITEM, HttpMethod.GET, context);
        assertFalse(demoted.path("isPrimary").booleanValue());
        assertEquals(4, demoted.path("version").longValue());
        assertEquals(selected, success(action(context, target, "set-primary", "2", primaryKey, "{}"), ITEM + "/set-primary", HttpMethod.POST, context));

        String reason = json.writeValueAsString(Map.of("reasonCode", "", "reasonDetail", " \u0000\n😀 "));
        JsonNode revoked = success(action(context, target, "revoke", "4", UUID.randomUUID().toString(), reason), ITEM + "/revoke", HttpMethod.POST, context);

        JsonNode third = created(context, base, body(scheme), UUID.randomUUID().toString());
        String pending = base + "/" + third.path("identifierId").textValue();
        success(action(context, pending, "reject", "0", UUID.randomUUID().toString(), "{}"), ITEM + "/reject", HttpMethod.POST, context);

        return revoked;
    }

    private void verifyHistoricalReplay(Context context, String base, String target, String input, String createKey, JsonNode initial, String verifyKey, JsonNode verified, JsonNode revoked) throws Exception {
        assertEquals(verified, success(action(context, target, "verify", "1", verifyKey, ""), ITEM + "/verify", HttpMethod.POST, context));
        assertEquals(initial, created(context, base, input, createKey));
        assertEquals(revoked, success(context.request().get(target), ITEM, HttpMethod.GET, context));
    }

    private void verifyCursorPagination(Context context, String base) throws Exception {
        Response first = context.request().queryParam("limit", "1").get(base);
        JsonNode firstPage = page(first, context, 1);
        String cursor = firstPage.path("nextCursor").textValue();
        assertNotNull(cursor);

        JsonNode next = page(context.request().queryParam("limit", "1").queryParam("cursor", cursor).get(base), context, 1);
        assertNotEquals(firstPage.path("data").get(0).path("identifierId"), next.path("data").get(0).path("identifierId"));

        JsonNode previous = page(context.request().queryParam("limit", "1").queryParam("cursor", next.path("prevCursor").textValue()).get(base), context, 1);
        assertEquals(firstPage.path("data"), previous.path("data"));

        error(context.request().queryParam("limit", "2").queryParam("cursor", cursor).get(base), ROOT, HttpMethod.GET, 400, "bad-request", context);
    }

    @Test
    void packagedDecodersAndFrameworkErrorsRetainStrictDefaultsAndSafeEnvelopes() throws Exception {
        var context = Context.fresh(); String party = owner(context, false); String base = "/v1/parties/" + party + "/identifiers";
        JsonNode initial = created(context, base, body("TEST_NATURAL_ACTIVE"), UUID.randomUUID().toString()); String target = base + "/" + initial.path("identifierId").textValue();
        for (String invalid : List.of("null", "[]", "{", "{\"status\":\"VERIFIED\"}", "{\"issuedOn\":\"2026-02-30\"}", "{} {}")) {
            error(context.request().body(invalid).patch(target), ITEM, HttpMethod.PATCH, 400,
                    invalid.equals("null") ? "request-body-required" : "bad-request", context);
        }
        for (String action : List.of("reject", "revoke", "set-primary")) {
            for (String invalid : List.of("null", " ", "[]", "{\"unknown\":true}", "{} {}")) {
                error(action(context, target, action, "0", null, invalid), ITEM + "/" + action, HttpMethod.POST, 400, "bad-request", context);
            }
        }
        for (String invalid : List.of("{\"replaceCurrentPrimary\":null}", "{\"replaceCurrentPrimary\":\"true\"}", "{\"replaceCurrentPrimary\":false,\"replaceCurrentPrimary\":true}")) {
            error(action(context, target, "set-primary", "0", null, invalid), ITEM + "/set-primary", HttpMethod.POST, 400, "bad-request", context);
        }
        for (String invalid : List.of("{}", "null", " ")) { error(action(context, target, "verify", "0", null, invalid), ITEM + "/verify", HttpMethod.POST, 400, "bad-request", context); }
        error(action(context, target, "verify", "01", null, ""), ITEM + "/verify", HttpMethod.POST, 400, "if-match-invalid", context);
        error(context.request().put(base), ROOT, HttpMethod.GET, 405, "method-not-allowed", context);
        error(context.request().contentType("application/xml").body("<private-reason/>").post(target + "/reject"), ITEM + "/reject", HttpMethod.POST, 415, "unsupported-media-type", context);
        Context concealed = Context.fresh(); error(concealed.request().get(target), ITEM, HttpMethod.GET, 404, "party-not-found", concealed);
    }

    @Test
    void bodylessVerificationUsesActualChunkedBytesOnPackagedExecution() throws Exception {
        var context = Context.fresh(); String party = owner(context, false); String base = "/v1/parties/" + party + "/identifiers";
        JsonNode initial = created(context, base, body("TEST_NATURAL_ACTIVE"), UUID.randomUUID().toString()); String target = base + "/" + initial.path("identifierId").textValue() + "/verify";
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            for (String body : List.of("{}", " \r\n\t", "")) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                var publisher = HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes)); assertEquals(-1, publisher.contentLength());
                var request = HttpRequest.newBuilder(server.resolve(target)).timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                        .header("Tenant-Id", context.tenant()).header("User-Id", context.user()).header("Process-Id", context.process()).header("If-Match", "0")
                        .POST(publisher).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = body.isEmpty() ? 200 : 400; assertEquals(status, response.statusCode());
                assertEquals(context.process(), response.headers().firstValue("Process-Id").orElseThrow());
                JsonNode result = json.readTree(response.body());
                if (status == 200) { PartyIdentifierContractAssertions.assertItemResponse(200, result); }
                else { PartyIdentifierContractAssertions.assertErrorResponse(400, "bad-request", result); }
                validator.assertResponse(ITEM + "/verify", HttpMethod.POST, status, response.body());
            }
        }
    }

    @Test
    void servedIdentifierPathsParametersAndSchemasEqualTheApprovedStaticContract() throws Exception {
        var options = new ParseOptions(); options.setResolve(true);
        var parsed = new OpenAPIV3Parser().readContents(given().get("/q/openapi").asString(), null, options);
        assertNotNull(parsed.getOpenAPI()); assertTrue(parsed.getMessages().isEmpty(), parsed.getMessages().toString());
        OpenAPI published = parsed.getOpenAPI();
        for (String path : List.of(ROOT, ITEM, ITEM + "/verify", ITEM + "/reject", ITEM + "/revoke", ITEM + "/set-primary")) {
            assertEquals(contract.getPaths().get(path), published.getPaths().get(path));
        }
        assertEquals(contract.getComponents().getParameters(), published.getComponents().getParameters());
        for (String schema : List.of("PartyIdentifierCreateRequest", "PartyIdentifierUpdateRequest", "PartyIdentifierResponse", "PartyIdentifierApiResponse",
                "PartyIdentifierCreatedApiResponse", "PartyIdentifierCollectionApiResponse", "PartyIdentifierReasonRequest", "SetPrimaryIdentifierRequest")) {
            assertNotNull(published.getComponents().getSchemas().get(schema));
            // allOf is commutative; the served serializer may reorder its complete, unchanged constraint blocks.
            var mapper = io.swagger.v3.core.util.Json.mapper();
            assertEquals(canonicalSchema(json.readTree(mapper.writeValueAsString(contract.getComponents().getSchemas().get(schema)))),
                    canonicalSchema(json.readTree(mapper.writeValueAsString(published.getComponents().getSchemas().get(schema)))), schema);
        }
    }

    @Test
    void packagedJvmAndNativeHarnessClasspathsExcludeUnitOnlyAlternativePortsAndClockFixtures() {
        for (String fixture : List.of("com.alexastudillo.partyregistry.infrastructure.persistence.PartyIdentifierFaultPort",
                "com.alexastudillo.partyregistry.support.FixedPartyIdentifierPrimaryProducer",
                "com.alexastudillo.partyregistry.support.FixedPartyIdentifierLifecycleProducer")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(fixture));
        }
    }

    private JsonNode canonicalSchema(JsonNode value) {
        if (value.isObject()) {
            var object = json.createObjectNode();
            value.properties().stream().sorted(Map.Entry.comparingByKey()).forEach(field -> {
                JsonNode child = canonicalSchema(field.getValue());
                if (field.getKey().equals("allOf") && child.isArray()) {
                    var constraints = new ArrayList<JsonNode>(); child.forEach(constraints::add);
                    constraints.sort(Comparator.comparing(JsonNode::toString));
                    var sorted = json.createArrayNode(); constraints.forEach(sorted::add); child = sorted;
                }
                object.set(field.getKey(), child);
            });
            return object;
        }
        if (value.isArray()) {
            var array = json.createArrayNode(); value.forEach(child -> array.add(canonicalSchema(child))); return array;
        }
        return value;
    }

    private JsonNode created(Context context, String base, String input, String key) throws Exception {
        validator.assertRequest(ROOT, HttpMethod.POST, input);
        Response response = context.request().header("Idempotency-Key", key).body(input).post(base);
        assertEquals(201, response.statusCode(), response.asString()); assertEquals(context.process(), response.header("Process-Id"));
        validator.assertResponse(ROOT, HttpMethod.POST, 201, response.asString());
        JsonNode body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(201, body); return body.path("data");
    }
    private JsonNode success(Response response, String path, HttpMethod method, Context context) throws Exception {
        assertEquals(200, response.statusCode(), response.asString()); assertEquals(context.process(), response.header("Process-Id"));
        validator.assertResponse(path, method, 200, response.asString());
        JsonNode body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(200, body); return body.path("data");
    }
    private JsonNode page(Response response, Context context, int limit) throws Exception {
        assertEquals(200, response.statusCode(), response.asString()); assertEquals(context.process(), response.header("Process-Id"));
        validator.assertResponse(ROOT, HttpMethod.GET, 200, response.asString());
        JsonNode body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertPageResponse(200, body, limit); return body;
    }
    private void error(Response response, String path, HttpMethod method, int status, String code, Context context) throws Exception {
        assertEquals(status, response.statusCode(), response.asString()); assertEquals(context.process(), response.header("Process-Id"));
        validator.assertResponse(path, method, status, response.asString());
        PartyIdentifierContractAssertions.assertErrorResponse(status, code, json.readTree(response.asString()));
    }
    private static Response action(Context context, String target, String action, String version, String key, String body) {
        var request = context.request().header("If-Match", version).body(body);
        if (key != null) { request.header("Idempotency-Key", key); }
        return request.post(target + "/" + action);
    }
    private String owner(Context context, boolean legal) throws Exception {
        String input = legal ? """
                {"legalName":"Packaged Identifier Company","incorporationCountryCode":"EC","initialIdentifier":{"identifierSchemeCode":"TEST_LEGAL_ACTIVE","value":"%s","isPrimary":false}}
                """.formatted(value()) : """
                {"displayName":"Packaged Identifier Person","givenNames":"Packaged","familyNames":"Identifier","birthCountryCode":"EC","initialIdentifier":{"identifierSchemeCode":"TEST_NATURAL_ACTIVE","value":"%s","isPrimary":false}}
                """.formatted(value());
        String path = legal ? "/v1/legal-entity" : "/v1/natural-person";
        Response response = context.request().header("Idempotency-Key", UUID.randomUUID().toString()).body(input).post(path);
        assertEquals(201, response.statusCode(), response.asString()); return json.readTree(response.asString()).path("data").path("partyId").textValue();
    }
    private String body(String scheme) throws Exception { return json.writeValueAsString(Map.of("identifierSchemeCode", scheme, "value", value())); }
    private static String value() { return "AB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10); }

    /** Retains only accepted test correlation metadata for the actual packaged service. */
    private record Context(String tenant, String user, String process) {
        static Context fresh() { return new Context(UUID.randomUUID().toString(), "packaged-identifier-operator", UUID.randomUUID().toString()); }
        RequestSpecification request() { return given().contentType("application/json").header("Tenant-Id", tenant).header("User-Id", user).header("Process-Id", process); }
    }
}
