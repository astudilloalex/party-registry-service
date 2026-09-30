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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers both Party types, recognized retired countries, inclusive conflicts, audit, and effective primary transfers. */
@QuarkusTest
@TestProfile(NationalityContractTestProfile.class)
@QuarkusTestResource(GeographicReferenceStubResource.class)
@Timeout(90)
class NationalityTemporalHttpContractTest {

    private static final String PROCESS = UUID.randomUUID().toString();
    private static final String USER = "nationality-temporal";
    private static final Set<String> DATA_FIELDS = Set.of("nationalityId", "partyId", "countryCode",
            "isPrimary", "createdAt", "updatedAt");

    @Inject
    RootPartyFixtures fixtures;

    @Test
    void acceptsBothPartyTypesAcrossRetainedStatusesAndRecognizedRetiredCountries() throws Exception {
        UUID tenant = UUID.randomUUID();
        try (var _ = GeographicReferenceStubResource.allowContext(tenant.toString(), USER, PROCESS)) {
            for (PartyType type : PartyType.values()) {
                for (PartyRecordStatus status : PartyRecordStatus.values()) {
                    UUID party = seed(tenant, type, status);
                    Response created = create(tenant, party, "{\"countryCode\":\" in \"}");
                    success(created, 201);
                    assertEquals("IN", created.jsonPath().getString("data.countryCode"));
                    assertEquals(DATA_FIELDS, created.jsonPath().getMap("data").keySet());
                    String detail = path(party) + "/" + created.jsonPath().getString("data.nationalityId");
                    assertEquals(created.jsonPath().getMap("data"), request(tenant).get(detail).jsonPath().getMap("data"));
                    Response root = request(tenant).get("/v1/parties/" + party);
                    success(root, 200);
                    assertEquals(type.name(), root.jsonPath().getString("data.type"));
                    assertEquals(status.name(), root.jsonPath().getString("data.recordStatus"));
                    assertEquals(1, root.jsonPath().getInt("data.version"));
                    assertFalse(root.asString().contains("nationalities"));
                }
            }
        }
    }

    @Test
    void distinguishesUnrecognizedCountryOutageAndInclusiveCountryPrimaryConflicts() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID party = seed(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED);
        try (var _ = GeographicReferenceStubResource.allowContext(tenant.toString(), USER, PROCESS)) {
            error(create(tenant, party, "{\"countryCode\":\"ZZ\"}"), 422, "unrecognized-nationality-country");
            error(create(tenant, party, "{\"countryCode\":\"SE\"}"), 503, "dependency-unavailable");
            Response january = create(tenant, party, """
                    {"countryCode":"EC","isPrimary":true,"validFrom":"2026-01-01","validUntil":"2026-01-31"}
                    """);
            success(january, 201);
            error(create(tenant, party, """
                    {"countryCode":"EC","validFrom":"2026-01-31","validUntil":"2026-02-28"}
                    """), 409, "nationality-validity-conflict");
            error(create(tenant, party, """
                    {"countryCode":"GB","isPrimary":true,"validFrom":"2026-01-31","validUntil":"2026-02-28"}
                    """), 409, "primary-nationality-conflict");
            Response february = create(tenant, party, """
                    {"countryCode":"GB","isPrimary":true,"validFrom":"2026-02-01","validUntil":"2026-02-28"}
                    """);
            success(february, 201);
            String januaryPath = path(party) + "/" + january.jsonPath().getString("data.nationalityId");
            String original = request(tenant).get(januaryPath).jsonPath().getString("data.updatedAt");
            error(patch(tenant, januaryPath, "{\"validUntil\":\"2025-12-31\"}"),
                    422, "nationality-validity-invalid");
            error(patch(tenant, januaryPath, "{\"validUntil\":\"2026-02-01\"}"),
                    409, "primary-nationality-conflict");
            assertEquals(original, request(tenant).get(januaryPath).jsonPath().getString("data.updatedAt"));
            Response changed = patch(tenant, januaryPath, "{\"validUntil\":\"2026-01-30\"}");
            success(changed, 200);
            assertEquals(january.jsonPath().getString("data.createdAt"), changed.jsonPath().getString("data.createdAt"));
            assertNotEquals(original, changed.jsonPath().getString("data.updatedAt"));
            assertEquals("EC", changed.jsonPath().getString("data.countryCode"));
            assertEquals(true, changed.jsonPath().getBoolean("data.isPrimary"));
            assertEquals(3, request(tenant).get("/v1/parties/" + party).jsonPath().getInt("data.version"));
        }
    }

    @Test
    void demotesOnlyIntersectingPrimariesAndLeavesUnkeyedNoopAuditIntact() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID party = seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.INACTIVE);
        try (var _ = GeographicReferenceStubResource.allowContext(tenant.toString(), USER, PROCESS)) {
            Response historical = create(tenant, party, """
                    {"countryCode":"EC","isPrimary":true,"validFrom":"2025-01-01","validUntil":"2025-12-31"}
                    """);
            Response former = create(tenant, party, """
                    {"countryCode":"GB","isPrimary":true,"validFrom":"2026-01-01","validUntil":null}
                    """);
            Response target = create(tenant, party, """
                    {"countryCode":"IN","validFrom":"2026-09-01","validUntil":null}
                    """);
            String historicalPath = detail(party, historical);
            String formerPath = detail(party, former);
            String targetPath = detail(party, target);
            String historicalAudit = historical.jsonPath().getString("data.updatedAt");
            error(request(tenant).post(historicalPath + "/set-primary"), 422, "nationality-not-effective");
            int versionBefore = request(tenant).get("/v1/parties/" + party).jsonPath().getInt("data.version");
            Response designated = request(tenant).post(targetPath + "/set-primary");
            success(designated, 200);
            assertTrue(designated.jsonPath().getBoolean("data.isPrimary"));
            assertFalse(request(tenant).get(formerPath).jsonPath().getBoolean("data.isPrimary"));
            assertTrue(request(tenant).get(historicalPath).jsonPath().getBoolean("data.isPrimary"));
            assertEquals(historicalAudit, request(tenant).get(historicalPath).jsonPath().getString("data.updatedAt"));
            assertEquals(versionBefore + 1, request(tenant).get("/v1/parties/" + party).jsonPath().getInt("data.version"));
            Response noop = request(tenant).post(targetPath + "/set-primary");
            success(noop, 200);
            assertEquals(designated.jsonPath().getMap("data"), noop.jsonPath().getMap("data"));
            assertEquals(versionBefore + 1, request(tenant).get("/v1/parties/" + party).jsonPath().getInt("data.version"));
        }
    }

    private UUID seed(UUID tenant, PartyType type, PartyRecordStatus status) {
        return RootPartyFixtures.await(() -> fixtures.create(tenant, type, status, "Temporal fixture",
                Instant.parse("2026-09-20T10:00:00Z")));
    }

    private static Response create(UUID tenant, UUID party, String body) {
        return request(tenant).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON).body(body).post(path(party));
    }

    private static Response patch(UUID tenant, String detail, String body) {
        return request(tenant).contentType(ContentType.JSON).body(body).patch(detail);
    }

    private static String detail(UUID party, Response created) {
        return path(party) + "/" + created.jsonPath().getString("data.nationalityId");
    }

    private static String path(UUID party) {
        return "/v1/parties/" + party + "/nationalities";
    }

    private static RequestSpecification request(UUID tenant) {
        return given().header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS)
                .header(RequestContextFilter.TENANT_ID_HEADER, tenant.toString())
                .header(RequestContextFilter.USER_ID_HEADER, USER);
    }

    private static void success(Response response, int status) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(status, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(PROCESS, response.header("Process-Id"));
        assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
    }

    private static void error(Response response, int status, String code) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(PROCESS, response.header("Process-Id"));
    }
}
