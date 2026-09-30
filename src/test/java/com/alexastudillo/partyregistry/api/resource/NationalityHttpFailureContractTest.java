package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.api.filter.RequestContextFilter;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.GeographicReferenceStubResource;
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Pins nationality validation precedence, tenant concealment, and exact framework/error envelopes. */
@QuarkusTest
@TestProfile(NationalityContractTestProfile.class)
@QuarkusTestResource(GeographicReferenceStubResource.class)
@Timeout(90)
class NationalityHttpFailureContractTest {

    private static final String PROCESS = UUID.randomUUID().toString();
    private static final String USER = "nationality-error-contract";
    private static final String KEY = "Idempotency-Key";

    @Inject
    RootPartyFixtures fixtures;

    @Test
    void requiresTrustedContextInOrderOnAllFiveRoutes() {
        UUID party = UUID.randomUUID();
        UUID nationality = UUID.randomUUID();
        String root = path(party);
        List<Endpoint> endpoints = List.of(new Endpoint("POST", root), new Endpoint("GET", root),
                new Endpoint("GET", root + "/" + nationality), new Endpoint("PATCH", root + "/" + nationality),
                new Endpoint("POST", root + "/" + nationality + "/set-primary"));
        UUID tenant = UUID.randomUUID();
        for (Endpoint endpoint : endpoints) {
            assertError(send(given(), endpoint), 400, "process-id-required", null);
            assertError(send(given().header("Process-Id", PROCESS), endpoint), 400, "tenant-id-required", PROCESS);
            assertError(send(request(tenant).header("Process-Id", PROCESS), endpoint),
                    400, "process-id-duplicated", null);
            assertError(send(given().header("Process-Id", PROCESS).header("Tenant-Id", tenant.toString())
                    .header("User-Id", " "), endpoint), 400, "user-id-blank", PROCESS);
        }
    }

    @Test
    void rejectsBodyKeysPathsAndQueryValuesBeforeAnyNationalityWrite() {
        UUID tenant = UUID.randomUUID();
        UUID party = seed(tenant);
        String root = path(party);
        for (String malformed : List.of("{", "[]", "{\"countryCode\":\"EC\",\"extra\":true}",
                "{\"countryCode\":\"EC\",\"countryCode\":\"CO\"}",
                "{\"countryCode\":\"EC\",\"isPrimary\":null}",
                "{\"countryCode\":\"EC\",\"validFrom\":\"2026-02-30\"}")) {
            assertError(request(tenant).contentType(ContentType.JSON).header(KEY, "unique")
                    .body(malformed).post(root), 400, "bad-request", PROCESS);
        }
        assertError(request(tenant).contentType(ContentType.JSON).header(KEY, "unique")
                .body("null").post(root), 400, "request-body-required", PROCESS);
        assertError(request(tenant).contentType(ContentType.JSON).header(KEY, "unique")
                .body("{}").post(root), 400, "country-code-required", PROCESS);
        assertError(request(tenant).contentType(ContentType.JSON).header(KEY, "unique")
                .body("{\"countryCode\":\"ÉC\"}").post(root), 400, "country-code-invalid", PROCESS);
        assertError(request(tenant).contentType(ContentType.JSON)
                .body("{\"countryCode\":\"EC\"}").post(root), 400, "idempotency-key-required", PROCESS);
        assertError(request(tenant).contentType(ContentType.JSON).header(KEY, "same", "same")
                .body("{\"countryCode\":\"EC\"}").post(root), 400, "idempotency-key-duplicated", PROCESS);
        assertError(request(tenant).contentType(ContentType.JSON).header(KEY, " ")
                .body("{\"countryCode\":\"EC\"}").post(root), 400, "idempotency-key-blank", PROCESS);
        assertError(request(tenant).contentType(ContentType.JSON).header(KEY, "x".repeat(129))
                .body("{\"countryCode\":\"EC\"}").post(root), 400, "idempotency-key-too-long", PROCESS);
        assertError(request(tenant).contentType(ContentType.JSON).body("{}").patch(root + "/" + UUID.randomUUID()),
                400, "patch-property-required", PROCESS);
        for (String body : List.of("{\"isPrimary\":true}", "{\"validFrom\":12}",
                "{\"validFrom\":null,\"validFrom\":null}")) {
            assertError(request(tenant).contentType(ContentType.JSON).body(body).patch(root + "/" + UUID.randomUUID()),
                    400, "bad-request", PROCESS);
        }
        assertError(request(tenant).get("/v1/parties/INVALID/nationalities"), 400, "party-id-invalid", PROCESS);
        assertError(request(tenant).get(root + "/invalid-nationality"), 400, "bad-request", PROCESS);
        for (RequestSpecification invalid : List.of(
                request(tenant).queryParam("unknown", "value"), request(tenant).queryParam("isPrimary", "true", "true"),
                request(tenant).queryParam("countryCode", "ec"), request(tenant).queryParam("limit", "201"),
                request(tenant).queryParam("cursor", "tampered"), request(tenant).queryParam("asOfDate", "2026-02-30"))) {
            assertError(invalid.get(root), 400, "bad-request", PROCESS);
        }
        Response empty = request(tenant).get(root);
        assertEquals(200, empty.statusCode());
        assertEquals(0, empty.jsonPath().getInt("totalElements"));
    }

    @Test
    void concealsForeignPartiesAndCrossPartyNationalitiesWithDistinct404Codes() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID own = seed(tenant);
        UUID other = seed(tenant);
        UUID foreignTenant = UUID.randomUUID();
        UUID foreign = seed(foreignTenant);
        String ownId;
        String foreignId;
        try (var _ = GeographicReferenceStubResource.allowContext(tenant.toString(), USER, PROCESS)) {
            ownId = create(tenant, other);
        }
        try (var _ = GeographicReferenceStubResource.allowContext(foreignTenant.toString(), USER, PROCESS)) {
            foreignId = create(foreignTenant, foreign);
        }
        assertError(request(tenant).get(path(foreign)), 404, "party-not-found", PROCESS);
        assertError(request(tenant).get(path(own) + "/" + ownId), 404, "nationality-not-found", PROCESS);
        assertError(request(tenant).get(path(own) + "/" + foreignId), 404, "nationality-not-found", PROCESS);
        assertError(request(tenant).get(path(own) + "/" + UUID.randomUUID()), 404, "nationality-not-found", PROCESS);
        assertError(request(tenant).get(path(UUID.randomUUID())), 404, "party-not-found", PROCESS);
    }

    @Test
    void mapsMethodMediaRouteAndUnexpectedFailuresWithoutLeakingDetails() {
        UUID tenant = UUID.randomUUID();
        UUID party = seed(tenant);
        String root = path(party);
        assertError(request(tenant).delete(root + "/" + UUID.randomUUID()), 405, "method-not-allowed", PROCESS);
        assertError(request(tenant).delete(root), 405, "method-not-allowed", PROCESS);
        assertError(request(tenant).contentType(ContentType.TEXT).body("not-json").post(root),
                415, "unsupported-media-type", PROCESS);
        assertError(request(tenant).get(root + "/" + UUID.randomUUID() + "/unexpected"),
                404, "not-found", PROCESS);
        Response unexpected = request(tenant).get("/v1/error-verification/unexpected");
        assertError(unexpected, 500, "server-error", PROCESS);
        assertFalse(unexpected.asString().contains("sensitive-database-detail"));
    }

    private UUID seed(UUID tenant) {
        return RootPartyFixtures.await(() -> fixtures.create(tenant, PartyType.NATURAL_PERSON,
                PartyRecordStatus.ARCHIVED, "Nationality failure fixture", Instant.parse("2026-09-20T10:00:00Z")));
    }

    private static String create(UUID tenant, UUID party) {
        Response response = request(tenant).header(KEY, UUID.randomUUID().toString()).contentType(ContentType.JSON)
                .body("{\"countryCode\":\"EC\"}").post(path(party));
        assertEquals(201, response.statusCode(), response.asString());
        return response.jsonPath().getString("data.nationalityId");
    }

    private static String path(UUID party) {
        return "/v1/parties/" + party + "/nationalities";
    }

    private static RequestSpecification request(UUID tenant) {
        return given().header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS)
                .header(RequestContextFilter.TENANT_ID_HEADER, tenant.toString())
                .header(RequestContextFilter.USER_ID_HEADER, USER);
    }

    private static Response send(RequestSpecification request, Endpoint endpoint) {
        return switch (endpoint.method()) {
            case "GET" -> request.get(endpoint.path());
            case "POST" -> request.post(endpoint.path());
            case "PATCH" -> request.patch(endpoint.path());
            default -> throw new IllegalArgumentException("Unsupported test method");
        };
    }

    private static void assertError(Response response, int status, String code, String processEcho) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(processEcho, response.header(RequestContextFilter.PROCESS_ID_HEADER));
    }

    /** Describes a declared nationality route for shared context-validation assertions. */
    private record Endpoint(String method, String path) {
    }
}
