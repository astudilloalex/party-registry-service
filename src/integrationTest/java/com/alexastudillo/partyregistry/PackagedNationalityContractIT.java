package com.alexastudillo.partyregistry;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises all five nationality routes and the packaged OpenAPI on JVM and native executables. */
@QuarkusIntegrationTest
@QuarkusTestResource(value = PackagedGeographicReferenceResource.class, restrictToAnnotatedClass = true)
@Timeout(90)
class PackagedNationalityContractIT {

    private static final String TENANT = "0198d5f0-0000-7000-8000-000000000200";
    private static final String PERSON = "0198d5f0-0000-7000-8000-000000000201";
    private static final String COMPANY = "0198d5f0-0000-7000-8000-000000000202";
    private static final String HISTORICAL = "0198d5f0-0000-7000-8000-000000000203";
    private static final String PAGE_COMPANY = "0198d5f0-0000-7000-8000-000000000204";
    private static final String PAGE_HISTORICAL = "0198d5f0-0000-7000-8000-000000000205";
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final Set<String> DATA_FIELDS = Set.of("nationalityId", "partyId", "countryCode",
            "isPrimary", "validFrom", "createdAt", "updatedAt");

    @Test
    void createsReplaysAndCorrectsBothRetainedPartyTypesWithRealCountryResponses() {
        for (String party : List.of(PERSON, COMPANY)) {
            String path = "/v1/parties/" + party + "/nationalities";
            String key = "packaged-nationality-" + party;
            Response created = request().header("Idempotency-Key", key).contentType("application/json")
                    .body("{\"countryCode\":\" ec \",\"validFrom\":\"2026-01-01\"}").post(path);
            success(created, 201);
            assertEquals(DATA_FIELDS, created.jsonPath().getMap("data").keySet());
            assertEquals("EC", created.jsonPath().getString("data.countryCode"));
            assertEquals(party, created.jsonPath().getString("data.partyId"));
            String id = created.jsonPath().getString("data.nationalityId");
            String detail = path + "/" + id;
            Response replay = request().header("Idempotency-Key", key).contentType("application/json")
                    .body("{\"countryCode\":\"EC\",\"isPrimary\":false,\"validFrom\":\"2026-01-01\",\"validUntil\":null}")
                    .post(path);
            success(replay, 201);
            assertEquals(created.jsonPath().getMap("data"), replay.jsonPath().getMap("data"));
            assertEquals(created.jsonPath().getMap("data"), request().get(detail).jsonPath().getMap("data"));
            error(request().header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                    .body("{\"countryCode\":\"EC\",\"validFrom\":\"2026-01-01\"}").post(path),
                    409, "nationality-validity-conflict");
            Response patched = request().contentType("application/json")
                    .body("{\"validUntil\":\"2030-01-01\"}").patch(detail);
            success(patched, 200);
            assertEquals("2030-01-01", patched.jsonPath().getString("data.validUntil"));
            Response primary = request().header("Idempotency-Key", "packaged-primary-" + party)
                    .post(detail + "/set-primary");
            success(primary, 200);
            assertTrue(primary.jsonPath().getBoolean("data.isPrimary"));
            assertEquals(primary.jsonPath().getMap("data"), request().header("Idempotency-Key", "packaged-primary-" + party)
                    .post(detail + "/set-primary").jsonPath().getMap("data"));
            if (party.equals(COMPANY)) {
                Response historical = request().get(path + "/" + HISTORICAL);
                success(historical, 200);
                assertEquals("2025-12-31", historical.jsonPath().getString("data.validUntil"));
            }
        }
    }

    @Test
    void traversesScopedPagesWithFlywayManagedHistory() {
        String path = "/v1/parties/" + PAGE_COMPANY + "/nationalities";
        // This test has its own Flyway-seeded history; it does not rely on another test's HTTP mutations.
        Response emptyCurrent = request().queryParam("asOfDate", "2026-09-23").get(path);
        page(emptyCurrent, 0, 0, 0);
        Response history = request().queryParam("asOfDate", "2026-09-23")
                .queryParam("includeExpired", "true").get(path);
        page(history, 1, 1, 1);
        assertEquals(PAGE_HISTORICAL, history.jsonPath().getString("data[0].nationalityId"));
        assertNull(history.jsonPath().get("nextCursor"));
        String firstKey = "packaged-page-one-" + UUID.randomUUID();
        String secondKey = "packaged-page-two-" + UUID.randomUUID();
        success(request().header("Idempotency-Key", firstKey).contentType("application/json")
                .body("{\"countryCode\":\"EC\",\"validFrom\":\"2026-01-01\"}").post(path), 201);
        success(request().header("Idempotency-Key", secondKey).contentType("application/json")
                .body("{\"countryCode\":\"GB\"}").post(path), 201);
        Response first = request().queryParam("asOfDate", "2026-09-23")
                .queryParam("includeExpired", "true").queryParam("limit", "1").get(path);
        page(first, 3, 3, 1);
        String cursor = first.jsonPath().getString("nextCursor");
        assertNotNull(cursor);
        Response second = request().queryParam("asOfDate", "2026-09-23")
                .queryParam("includeExpired", "true").queryParam("limit", "1")
                .queryParam("cursor", cursor).get(path);
        page(second, 3, 3, 1);
        assertNotEquals(first.jsonPath().getString("data[0].nationalityId"),
                second.jsonPath().getString("data[0].nationalityId"));
        Response previous = request().queryParam("asOfDate", "2026-09-23")
                .queryParam("includeExpired", "true").queryParam("limit", "1")
                .queryParam("cursor", second.jsonPath().getString("prevCursor")).get(path);
        assertEquals(first.jsonPath().getList("data"), previous.jsonPath().getList("data"));
        error(request().queryParam("asOfDate", "2026-09-23")
                .queryParam("includeExpired", "true").queryParam("limit", "2")
                .queryParam("cursor", cursor).get(path), 400, "bad-request");
        error(request().queryParam("asOfDate", "2026-09-24")
                .queryParam("includeExpired", "true").queryParam("limit", "1")
                .queryParam("cursor", cursor).get(path), 400, "bad-request");
    }

    @Test
    void servesThePackagedNationalityContractVerbatim() {
        var parser = new OpenAPIV3Parser();
        var source = parser.readLocation(Path.of("docs/contracts/party-registry.openapi.yaml").toString(), null, null);
        var published = parser.readContents(given().get("/q/openapi").asString(), null, null);
        assertNotNull(source.getOpenAPI());
        assertNotNull(published.getOpenAPI());
        assertTrue(source.getMessages().isEmpty(), source.getMessages().toString());
        assertTrue(published.getMessages().isEmpty(), published.getMessages().toString());
        for (String path : List.of("/v1/parties/{partyId}/nationalities",
                "/v1/parties/{partyId}/nationalities/{nationalityId}",
                "/v1/parties/{partyId}/nationalities/{nationalityId}/set-primary")) {
            assertEquals(source.getOpenAPI().getPaths().get(path), published.getOpenAPI().getPaths().get(path));
        }
        for (String schema : List.of("NationalityCreateRequest", "NationalityUpdateRequest", "NationalityResponse",
                "NationalityApiResponse", "NationalityCollectionApiResponse")) {
            assertEquals(source.getOpenAPI().getComponents().getSchemas().get(schema),
                    published.getOpenAPI().getComponents().getSchemas().get(schema));
        }
    }

    private static RequestSpecification request() {
        return given().header("Tenant-Id", TENANT).header("Process-Id", PROCESS)
                .header("User-Id", "packaged-nationality-operator");
    }

    private static void success(Response response, int status) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(status, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(PROCESS, response.header("Process-Id"));
        assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
    }

    private static void page(Response response, int total, int pages, int size) {
        assertEquals(200, response.statusCode(), response.asString());
        assertEquals(200, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(PROCESS, response.header("Process-Id"));
        assertEquals(Set.of("status", "code", "data", "nextCursor", "prevCursor", "totalElements", "totalPages",
                "numberOfElements"), response.jsonPath().getMap("$").keySet());
        assertEquals(total, response.jsonPath().getInt("totalElements"));
        assertEquals(pages, response.jsonPath().getInt("totalPages"));
        assertEquals(size, response.jsonPath().getInt("numberOfElements"));
    }

    private static void error(Response response, int status, String code) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(PROCESS, response.header("Process-Id"));
    }
}
