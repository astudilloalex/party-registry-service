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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Verifies filter intersection, scoped bidirectional cursors and historical keyed HTTP outcomes. */
@QuarkusTest
@TestProfile(NationalityContractTestProfile.class)
@QuarkusTestResource(GeographicReferenceStubResource.class)
@Timeout(90)
class NationalityListReplayHttpContractTest {

    private static final String USER = "nationality-replay";
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final String AS_OF = "2026-09-23";

    @Inject
    RootPartyFixtures fixtures;

    @Test
    void paginatesIntersectionAndRejectsAnyCrossScopeOrAlteredCursor() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID party = seed(tenant);
        UUID another = seed(tenant);
        UUID foreignTenant = UUID.randomUUID();
        String path = path(party);
        List<String> ids = new ArrayList<>();
        try (var _ = GeographicReferenceStubResource.allowContext(tenant.toString(), USER, PROCESS)) {
            ids.add(create(tenant, party, """
                    {"countryCode":"EC","validFrom":"2026-01-01","validUntil":"2026-01-31"}
                    """).jsonPath().getString("data.nationalityId"));
            ids.add(create(tenant, party, """
                    {"countryCode":"EC","validFrom":"2026-02-01","validUntil":"2026-02-28"}
                    """).jsonPath().getString("data.nationalityId"));
            ids.add(create(tenant, party, "{\"countryCode\":\"GB\"}").jsonPath().getString("data.nationalityId"));
            ids.add(create(tenant, party, "{\"countryCode\":\"IN\"}").jsonPath().getString("data.nationalityId"));
            create(tenant, party, "{\"countryCode\":\"EC\",\"validFrom\":\"2030-01-01\"}");
            create(tenant, another, "{\"countryCode\":\"GB\"}");
        }
        var first = listing(tenant, path, 2, null);
        page(first, 4, 2, 2);
        assertNull(first.jsonPath().get("prevCursor"));
        String next = first.jsonPath().getString("nextCursor");
        assertFalse(next.isBlank());
        var second = listing(tenant, path, 2, next);
        page(second, 4, 2, 2);
        assertNull(second.jsonPath().get("nextCursor"));
        Response restored = request(tenant).queryParam("asOfDate", AS_OF).queryParam("includeExpired", "true")
                .queryParam("limit", "2").queryParam("cursor", second.jsonPath().getString("prevCursor")).get(path);
        assertEquals(first.jsonPath().getList("data"), restored.jsonPath().getList("data"));
        List<String> observed = new ArrayList<>();
        for (Response response : List.of(first, second)) {
            observed.addAll(response.jsonPath().getList("data.nationalityId", String.class));
        }
        assertEquals(List.of(ids.get(3), ids.get(2), ids.get(1), ids.get(0)), observed);

        Response intersection = request(tenant).queryParam("asOfDate", AS_OF).queryParam("includeExpired", "true")
                .queryParam("countryCode", "EC").queryParam("isPrimary", "false").get(path);
        page(intersection, 2, 1, 2);
        Response empty = request(tenant).queryParam("asOfDate", AS_OF).queryParam("isPrimary", "true").get(path);
        page(empty, 0, 0, 0);
        assertEquals(List.of(), empty.jsonPath().getList("data"));
        assertNull(empty.jsonPath().get("nextCursor"));
        assertNull(empty.jsonPath().get("prevCursor"));

        for (Response invalid : List.of(
                listing(tenant, path(another), 2, next), listing(foreignTenant, path, 2, next),
                request(tenant).queryParam("asOfDate", "2026-09-24").queryParam("includeExpired", "true")
                        .queryParam("limit", "2").queryParam("cursor", next).get(path),
                listing(tenant, path, 3, next),
                request(tenant).queryParam("asOfDate", AS_OF).queryParam("includeExpired", "false")
                        .queryParam("limit", "2").queryParam("cursor", next).get(path),
                request(tenant).queryParam("asOfDate", AS_OF).queryParam("includeExpired", "true")
                        .queryParam("countryCode", "EC").queryParam("limit", "2").queryParam("cursor", next).get(path),
                listing(tenant, path, 2, next.substring(0, next.length() - 1) + "Z"))) {
            error(invalid, 400, "bad-request");
        }
    }

    @Test
    void replaysEquivalentCreateAndKeyedPrimaryBeforeCountryOrCurrentStateChecks() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID party = seed(tenant);
        UUID another = seed(tenant);
        String root = path(party);
        String firstKey = "created-key";
        try (var _ = GeographicReferenceStubResource.allowContext(tenant.toString(), USER, PROCESS)) {
            int lookupsBefore = GeographicReferenceStubResource.requestCount("EC");
            Response created = request(tenant).header("Idempotency-Key", firstKey).contentType(ContentType.JSON)
                    .body("{\"countryCode\":\" ec \"}").post(root);
            success(created, 201);
            assertEquals(lookupsBefore + 1, GeographicReferenceStubResource.requestCount("EC"));
            String otherProcess = UUID.randomUUID().toString();
            try (var _ = GeographicReferenceStubResource.allowContext(tenant.toString(), "second-actor", otherProcess)) {
                Response replay = request(tenant, "second-actor", otherProcess).header("Idempotency-Key", firstKey)
                        .contentType(ContentType.JSON)
                        .body("{\"countryCode\":\"EC\",\"isPrimary\":false,\"validFrom\":null,\"validUntil\":null}")
                        .post(root);
                assertEquals(201, replay.statusCode(), replay.asString());
                assertEquals(created.jsonPath().getMap("data"), replay.jsonPath().getMap("data"));
                assertEquals(otherProcess, replay.header("Process-Id"));
            }
            assertEquals(lookupsBefore + 1, GeographicReferenceStubResource.requestCount("EC"));
            error(request(tenant).header("Idempotency-Key", firstKey).contentType(ContentType.JSON)
                    .body("{\"countryCode\":\"GB\"}").post(path(another)), 409, "idempotency-key-conflict");
            assertEquals(0, request(tenant).get(path(another)).jsonPath().getInt("totalElements"));

            Response second = create(tenant, party, "{\"countryCode\":\"GB\"}");
            String originalPath = root + "/" + created.jsonPath().getString("data.nationalityId");
            String secondPath = root + "/" + second.jsonPath().getString("data.nationalityId");
            Response primary = request(tenant).header("Idempotency-Key", "primary-key")
                    .post(originalPath + "/set-primary");
            success(primary, 200);
            request(tenant).post(secondPath + "/set-primary");
            assertFalse(request(tenant).get(originalPath).jsonPath().getBoolean("data.isPrimary"));
            int versionBeforeReplay = request(tenant).get("/v1/parties/" + party).jsonPath().getInt("data.version");
            Response replayPrimary = request(tenant).header("Idempotency-Key", "primary-key")
                    .post(originalPath + "/set-primary");
            success(replayPrimary, 200);
            assertEquals(primary.jsonPath().getMap("data"), replayPrimary.jsonPath().getMap("data"));
            assertEquals(versionBeforeReplay, request(tenant).get("/v1/parties/" + party).jsonPath().getInt("data.version"));
            error(request(tenant).header("Idempotency-Key", "primary-key")
                    .post(secondPath + "/set-primary"), 409, "idempotency-key-conflict");
            Response unkeyed = request(tenant).post(secondPath + "/set-primary");
            success(unkeyed, 200);
            assertEquals(request(tenant).get(secondPath).jsonPath().getString("data.updatedAt"),
                    unkeyed.jsonPath().getString("data.updatedAt"));
        }
    }

    private UUID seed(UUID tenant) {
        return RootPartyFixtures.await(() -> fixtures.create(tenant, PartyType.NATURAL_PERSON,
                PartyRecordStatus.ARCHIVED, "List/replay fixture", Instant.parse("2026-09-20T10:00:00Z")));
    }

    private static Response create(UUID tenant, UUID party, String body) {
        Response response = request(tenant).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON).body(body).post(path(party));
        success(response, 201);
        return response;
    }

    private static Response listing(UUID tenant, String path, int limit, String cursor) {
        RequestSpecification request = request(tenant).queryParam("asOfDate", AS_OF)
                .queryParam("includeExpired", "true").queryParam("limit", Integer.toString(limit));
        if (cursor != null) {
            request.queryParam("cursor", cursor);
        }
        return request.get(path);
    }

    private static void page(Response response, int total, int pages, int count) {
        success(response, 200);
        assertEquals(Set.of("status", "code", "data", "nextCursor", "prevCursor", "totalElements", "totalPages",
                "numberOfElements"), response.jsonPath().getMap("$").keySet());
        assertEquals(total, response.jsonPath().getInt("totalElements"));
        assertEquals(pages, response.jsonPath().getInt("totalPages"));
        assertEquals(count, response.jsonPath().getInt("numberOfElements"));
    }

    private static String path(UUID party) {
        return "/v1/parties/" + party + "/nationalities";
    }

    private static RequestSpecification request(UUID tenant) {
        return request(tenant, USER, PROCESS);
    }

    private static RequestSpecification request(UUID tenant, String actor, String process) {
        return given().header(RequestContextFilter.PROCESS_ID_HEADER, process)
                .header(RequestContextFilter.TENANT_ID_HEADER, tenant.toString())
                .header(RequestContextFilter.USER_ID_HEADER, actor);
    }

    private static void success(Response response, int status) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(status, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(PROCESS, response.header("Process-Id"));
    }

    private static void error(Response response, int status, String code) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(PROCESS, response.header("Process-Id"));
    }
}
