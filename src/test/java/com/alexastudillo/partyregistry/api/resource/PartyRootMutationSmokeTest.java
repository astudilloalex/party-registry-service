package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.api.filter.RequestContextFilter;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
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
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies live root mutation delegation, strict validation precedence, shared envelopes, and historical keyed replay. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class PartyRootMutationSmokeTest {

    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final String ROOT = "/v1/parties/";
    private static final String VERSION = ApiRequestSupport.IF_MATCH_HEADER;
    private static final String KEY = ApiRequestSupport.IDEMPOTENCY_KEY_HEADER;

    @Inject
    RootPartyFixtures fixtures;

    @Test
    void archivesAndCorrectsBothTypesWhileReplayingTheOriginalHistoricalResult() {
        UUID tenant = UUID.randomUUID();
        for (PartyType type : PartyType.values()) {
            UUID id = RootPartyFixtures.await(() -> fixtures.create(tenant, type, PartyRecordStatus.DRAFT, " Historical Label ", CLOCK.instant()));
            String path = ROOT + id;
            String key = "archive-" + id;
            assertError(request(tenant).header(VERSION, "0").post(path + "/activate"), 422, "missing-qualifying-identifier");
            Response archived = request(tenant).header(VERSION, "0").header(KEY, key).post(path + "/archive");
            Map<String, Object> original = assertSuccess(archived, "ARCHIVED", 1);
            assertEquals(type.name(), original.get("type"));
            Response patched = request(tenant).header(VERSION, "1").header(KEY, "ignored-patch-key")
                    .contentType(ContentType.JSON).body("{\"displayName\":\"  Áda  Corrected  \"}").patch(path);
            Map<String, Object> current = assertSuccess(patched, "ARCHIVED", 2);
            assertEquals("ÁDA  CORRECTED", current.get("displayName"));
            assertEquals(original.get("createdAt"), current.get("createdAt"));
            assertEquals(original.get("createdBy"), current.get("createdBy"));
            assertEquals(original.get("naturalPersonDetails"), current.get("naturalPersonDetails"));
            assertEquals(original.get("legalEntityDetails"), current.get("legalEntityDetails"));
            Response replay = request(tenant).header(VERSION, "0").header(KEY, key).post(path + "/archive");
            assertEquals(original, assertSuccess(replay, "ARCHIVED", 1));
            assertEquals(current, request(tenant).get(path).jsonPath().getMap("data"));
            assertError(request(tenant).header(VERSION, "1").header(KEY, "ignored-patch-key")
                    .contentType(ContentType.JSON).body("{\"displayName\":\"another\"}").patch(path), 412, "expected-version-mismatch");
            assertError(request(tenant).header(VERSION, "2").post(path + "/archive"), 409, "invalid-party-lifecycle");
        }
    }

    @Test
    void deactivatesAndArchivesWithoutIdentifierEvidenceForBothTypes() {
        UUID tenant = UUID.randomUUID();
        for (PartyType type : PartyType.values()) {
            UUID id = RootPartyFixtures.await(() -> fixtures.create(tenant, type, PartyRecordStatus.ACTIVE, "Retained Label", CLOCK.instant()));
            String path = ROOT + id;
            assertSuccess(request(tenant).header(VERSION, "0").post(path + "/deactivate"), "INACTIVE", 1);
            assertError(request(tenant).header(VERSION, "0").post(path + "/deactivate"), 412, "stale-party-version");
            assertError(request(tenant).header(VERSION, "1").post(path + "/activate"), 409, "invalid-party-lifecycle");
            assertSuccess(request(tenant).header(VERSION, "1").post(path + "/archive"), "ARCHIVED", 2);
        }
    }

    @Test
    void distinguishesStructuralAndBusinessRejectionsWithTheRequiredPrecedence() {
        UUID tenant = UUID.randomUUID();
        UUID id = RootPartyFixtures.await(() -> fixtures.create(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT, "Original", CLOCK.instant()));
        String path = ROOT + id;
        assertError(request(tenant).contentType(ContentType.JSON).patch(path), 400, "request-body-required");
        assertError(request(tenant).contentType(ContentType.JSON).body("null").patch(path), 400, "request-body-required");
        assertError(request(tenant).contentType(ContentType.JSON).body("{}").patch(ROOT + "invalid"), 400, "display-name-required");
        assertError(request(tenant).contentType(ContentType.JSON).body("{\"displayName\":null}").patch(path), 400, "display-name-required");
        assertError(request(tenant).contentType(ContentType.JSON).body("{\"displayName\":\"Valid\"}").patch(ROOT + "invalid"), 400, "party-id-invalid");
        assertError(request(tenant).contentType(ContentType.JSON).body("{\"displayName\":\"Valid\"}").patch(path), 400, "if-match-required");
        assertError(request(tenant).header(VERSION, "1").contentType(ContentType.JSON).body("{\"displayName\":\" \"}").patch(path), 412, "expected-version-mismatch");
        assertError(request(tenant).header(VERSION, "0").contentType(ContentType.JSON).body("{\"displayName\":\" \"}").patch(path), 422, "blank-display-name");
        for (String body : List.of("{", "{\"displayName\":42}", "{\"displayName\":\"Valid\",\"version\":2}",
                "{\"displayName\":\"First\",\"displayName\":\"Second\"}")) {
            assertError(request(tenant).header(VERSION, "0").contentType(ContentType.JSON).body(body).patch(path), 400, "bad-request");
        }
        assertError(request(tenant).contentType(ContentType.JSON).body("{\"displayName\":\"" + "ß".repeat(151) + "\"}")
                .patch(ROOT + "invalid"), 400, "display-name-too-long");
        assertError(request(tenant).header(VERSION, "0").contentType(ContentType.TEXT).body("text").patch(path), 415, "unsupported-media-type");
        assertError(request(tenant).header(KEY, " ").post(ROOT + "invalid/deactivate"), 400, "idempotency-key-blank");
        assertEquals("Original", request(tenant).get(path).jsonPath().getString("data.displayName"));
        assertEquals(0, request(tenant).get(path).jsonPath().getInt("data.version"));
    }

    private static RequestSpecification request(UUID tenant) {
        return given().header(RequestContextFilter.TENANT_ID_HEADER, tenant.toString())
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS).header(RequestContextFilter.USER_ID_HEADER, "root-operator");
    }

    private static Map<String, Object> assertSuccess(Response response, String status, int version) {
        assertEquals(200, response.statusCode());
        assertEquals(200, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(PROCESS, response.header(RequestContextFilter.PROCESS_ID_HEADER));
        assertEquals(status, response.jsonPath().getString("data.recordStatus"));
        assertEquals(version, response.jsonPath().getInt("data.version"));
        assertEquals("root-operator", response.jsonPath().getString("data.updatedBy"));
        return response.jsonPath().getMap("data");
    }

    private static void assertError(Response response, int status, String code) {
        assertEquals(status, response.statusCode());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(PROCESS, response.header(RequestContextFilter.PROCESS_ID_HEADER));
    }
}
