package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.api.filter.RequestContextFilter;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies live root GET envelopes, tenant-safe pagination/detail, validation, and unchanged nested-identifier dispatch. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class PartyRootReadSmokeTest {

    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final String ROOT = "/v1/parties";

    @Inject
    RootPartyFixtures fixtures;

    @Test
    void servesBothSafeDetailTypesAndLeavesNestedIdentifierRoutingIntact() {
        UUID tenant = UUID.randomUUID();
        for (PartyType type : PartyType.values()) {
            UUID id = RootPartyFixtures.await(() -> fixtures.create(tenant, type, PartyRecordStatus.ARCHIVED, " Historical Áda ", CLOCK.instant()));
            Response response = request(tenant).get(ROOT + "/" + id);
            assertSuccess(response);
            Map<String, Object> data = response.jsonPath().getMap("data");
            String details = type == PartyType.NATURAL_PERSON ? "naturalPersonDetails" : "legalEntityDetails";
            assertEquals(Set.of("partyId", "type", "displayName", "recordStatus", "version", "createdAt", "updatedAt", "createdBy", "updatedBy", details), data.keySet());
            assertEquals(id.toString(), data.get("partyId"));
            assertEquals(type.name(), data.get("type"));
            assertEquals(" Historical Áda ", data.get("displayName"));
            assertEquals("ARCHIVED", data.get("recordStatus"));
            assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
            Response nested = request(tenant).contentType(ContentType.JSON)
                    .body("{\"identifierSchemeCode\":\"TEST_NATURAL_ACTIVE\",\"value\":\"ABC1234\"}")
                    .post(ROOT + "/" + id + "/identifiers");
            assertError(nested, 400, "idempotency-key-required");
            assertError(request(UUID.randomUUID()).get(ROOT + "/" + id), 404, "party-not-found");
        }
    }

    @Test
    void servesPagedSummariesWithExplicitNullDirectionsAndTenantSafeTotals() {
        UUID tenant = UUID.randomUUID();
        UUID firstId = RootPartyFixtures.await(() -> fixtures.create(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT, "First", CLOCK.instant()));
        UUID secondId = RootPartyFixtures.await(() -> fixtures.create(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ACTIVE, "Second", CLOCK.instant().minusSeconds(1)));
        RootPartyFixtures.await(() -> fixtures.create(UUID.randomUUID(), PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT, "Other tenant", CLOCK.instant()));
        Response first = request(tenant).queryParam("limit", 1).get(ROOT);
        assertPage(first, 2, 2, 1);
        assertEquals(firstId.toString(), first.jsonPath().getString("data[0].partyId"));
        assertEquals(Set.of("partyId", "type", "displayName", "recordStatus", "createdAt", "version"), first.jsonPath().getMap("data[0]").keySet());
        assertNull(first.jsonPath().get("prevCursor"));
        String cursor = first.jsonPath().getString("nextCursor");
        Response last = request(tenant).queryParam("limit", 1).queryParam("cursor", cursor).get(ROOT);
        assertPage(last, 2, 2, 1);
        assertEquals(secondId.toString(), last.jsonPath().getString("data[0].partyId"));
        assertNull(last.jsonPath().get("nextCursor"));
        assertFalse(last.jsonPath().getString("prevCursor").isBlank());
    }

    @Test
    void emptyListsAndInvalidInputsUseTheDeclaredContracts() {
        UUID tenant = UUID.randomUUID();
        Response empty = request(tenant).get(ROOT);
        assertPage(empty, 0, 0, 0);
        assertEquals(List.of(), empty.jsonPath().getList("data"));
        assertNull(empty.jsonPath().get("nextCursor"));
        assertNull(empty.jsonPath().get("prevCursor"));
        assertError(request(tenant).queryParam("unknown", "value").get(ROOT), 400, "bad-request");
        assertError(request(tenant).queryParam("limit", 201).get(ROOT), 400, "bad-request");
        assertError(request(tenant).queryParam("cursor", "tampered").get(ROOT), 400, "bad-request");
        assertError(request(tenant).get(ROOT + "/invalid-id"), 400, "party-id-invalid");
        assertError(request(tenant).get(ROOT + "/" + UUID.randomUUID()), 404, "party-not-found");
    }

    private static RequestSpecification request(UUID tenant) {
        return given().header(RequestContextFilter.TENANT_ID_HEADER, tenant.toString())
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS).header(RequestContextFilter.USER_ID_HEADER, "root-reader");
    }

    private static void assertSuccess(Response response) {
        assertEquals(200, response.statusCode());
        assertEquals(200, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(PROCESS, response.header(RequestContextFilter.PROCESS_ID_HEADER));
    }

    private static void assertPage(Response response, int total, int pages, int size) {
        assertSuccess(response);
        assertEquals(Set.of("status", "code", "data", "nextCursor", "prevCursor", "totalElements", "totalPages", "numberOfElements"),
                response.jsonPath().getMap("$").keySet());
        assertEquals(total, response.jsonPath().getInt("totalElements"));
        assertEquals(pages, response.jsonPath().getInt("totalPages"));
        assertEquals(size, response.jsonPath().getInt("numberOfElements"));
        assertTrue(response.jsonPath().get("data") instanceof List<?>);
    }

    private static void assertError(Response response, int status, String code) {
        assertEquals(status, response.statusCode());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(PROCESS, response.header(RequestContextFilter.PROCESS_ID_HEADER));
    }
}
