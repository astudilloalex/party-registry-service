package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.api.filter.RequestContextFilter;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.GeographicReferenceStubResource;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
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
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises all five HTTP routes with the real reactive geographic client and Flyway-managed rows. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@QuarkusTestResource(GeographicReferenceStubResource.class)
@Timeout(90)
class NationalityResourceSmokeTest {

    private static final String USER = "nationality-http";
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final Set<String> RESPONSE_FIELDS = Set.of(
            "nationalityId", "partyId", "countryCode", "isPrimary", "createdAt", "updatedAt");

    @Inject
    RootPartyFixtures fixtures;

    @Test
    void exposesTypedSuccessEnvelopesWithOnlyListPaginationAndEchoesProcessId() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID party = RootPartyFixtures.await(() -> fixtures.create(tenant, PartyType.LEGAL_ENTITY,
                PartyRecordStatus.ARCHIVED, "Nationality HTTP", Instant.parse("2026-09-20T10:00:00Z")));
        String path = "/v1/parties/" + party + "/nationalities";
        try (var _ = GeographicReferenceStubResource.allowContext(tenant.toString(), USER, PROCESS)) {
            Response created = request(tenant).header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(ContentType.JSON).body("{\"countryCode\":\" ec \"}").post(path);
            assertSuccess(created, 201, false);
            assertEquals(RESPONSE_FIELDS, created.jsonPath().getMap("data").keySet());
            assertEquals(party.toString(), created.jsonPath().getString("data.partyId"));
            assertEquals("EC", created.jsonPath().getString("data.countryCode"));
            assertEquals(false, created.jsonPath().getBoolean("data.isPrimary"));
            String id = created.jsonPath().getString("data.nationalityId");

            Response detail = request(tenant).get(path + "/" + id);
            assertSuccess(detail, 200, false);
            assertEquals(created.jsonPath().getMap("data"), detail.jsonPath().getMap("data"));
            Response page = request(tenant).queryParam("asOfDate", "2026-09-23").get(path);
            assertSuccess(page, 200, true);
            assertEquals(1, page.jsonPath().getInt("totalElements"));
            assertEquals(1, page.jsonPath().getInt("numberOfElements"));
            assertEquals(1, page.jsonPath().getInt("totalPages"));
            assertNull(page.jsonPath().get("nextCursor"));
            assertNull(page.jsonPath().get("prevCursor"));
            assertEquals(List.of(created.jsonPath().getMap("data")), page.jsonPath().getList("data"));

            Response patched = request(tenant).contentType(ContentType.JSON)
                    .body("{\"validUntil\":\"2030-01-01\"}").patch(path + "/" + id);
            assertSuccess(patched, 200, false);
            assertEquals("2030-01-01", patched.jsonPath().getString("data.validUntil"));
            Response primary = request(tenant).post(path + "/" + id + "/set-primary");
            assertSuccess(primary, 200, false);
            assertTrue(primary.jsonPath().getBoolean("data.isPrimary"));
            assertEquals("2030-01-01", primary.jsonPath().getString("data.validUntil"));
        }
    }

    private static RequestSpecification request(UUID tenant) {
        return given().header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS)
                .header(RequestContextFilter.TENANT_ID_HEADER, tenant.toString())
                .header(RequestContextFilter.USER_ID_HEADER, USER);
    }

    private static void assertSuccess(Response response, int status, boolean paged) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(status, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(PROCESS, response.header(RequestContextFilter.PROCESS_ID_HEADER));
        assertEquals(paged ? Set.of("status", "code", "data", "nextCursor", "prevCursor", "totalElements", "totalPages",
                "numberOfElements") : Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
    }
}
