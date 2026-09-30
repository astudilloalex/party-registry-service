package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.GeographicReferenceStubResource;
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import com.alexastudillo.partyregistry.support.StoredOutboxTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Verifies root correction validation, Unicode boundaries, all-state audit/version behavior, and retained registration outcomes. */
@QuarkusTest
@TestProfile(StoredOutboxTestProfile.class)
class PartyRootPatchContractTest {

    private static final String ROOT = "/v1/parties/";
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final String USER = "root-patch-contract";
    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Inject
    RootPartyFixtures fixtures;
    @Inject
    Mutiny.SessionFactory sessions;

    @Test
    void correctsBothTypesInEveryStateAndIdenticalWritesAdvanceExactlyOnce() {
        UUID tenant = UUID.randomUUID();
        for (PartyType type : PartyType.values()) {
            for (PartyRecordStatus state : PartyRecordStatus.values()) {
                UUID id = seed(tenant, type, state);
                Map<String, Object> before = current(tenant, id);
                Map<String, Object> first = accepted(patch(tenant, id, "0", "  Áda,  Straße!  "), 1);
                assertEquals("ÁDA,  STRASSE!", first.get("displayName"));
                preserved(before, first);
                assertEquals(USER, first.get("updatedBy"));
                assertEquals(first.get("updatedAt"), current(tenant, id).get("updatedAt"));
                Map<String, Object> identical = accepted(patch(tenant, id, "1", "ÁDA,  STRASSE!"), 2);
                preserved(first, identical);
                assertEquals(first.get("displayName"), identical.get("displayName"));
                assertEquals(2, eventCount(id));
                error(patch(tenant, id, "0", "stale"), 412, "expected-version-mismatch");
                assertEquals(identical, current(tenant, id));
                assertEquals(2, eventCount(id));
            }
        }
    }

    @Test
    void acceptsNormalizedUtf16BoundariesAndRejectsExpansionWithoutChangingTheRecord() {
        UUID tenant = UUID.randomUUID();
        for (PartyType type : PartyType.values()) {
            UUID id = seed(tenant, type, PartyRecordStatus.ARCHIVED);
            int version = 0;
            for (String label : List.of("a".repeat(300), "ß".repeat(150), "🙂".repeat(150), " ".repeat(50) + "a".repeat(300) + " ")) {
                Response response = patch(tenant, id, Integer.toString(version), label);
                version++;
                Map<String, Object> result = accepted(response, version);
                assertEquals(300, ((String) result.get("displayName")).length());
            }
            Map<String, Object> before = current(tenant, id);
            for (String label : List.of("a".repeat(301), "ß".repeat(151), "🙂".repeat(150) + "a")) {
                error(patch(tenant, id, Integer.toString(version), label), 400, "display-name-too-long");
            }
            for (String label : List.of("", " ", "\u2003")) {
                error(patch(tenant, id, Integer.toString(version), label), 422, "blank-display-name");
            }
            assertEquals(before, current(tenant, id));
            assertEquals(version, eventCount(id));
        }
    }

    @Test
    void rejectsClosedJsonViolationsAndDistinguishesRequiredBodyFromRequiredValue() {
        UUID tenant = UUID.randomUUID();
        UUID id = seed(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        Map<String, Object> before = current(tenant, id);
        error(request(tenant).contentType(ContentType.JSON).patch(ROOT + id), 400, "request-body-required");
        error(raw(tenant, id.toString(), "null"), 400, "request-body-required");
        for (String body : List.of("{}", "{\"displayName\":null}")) {
            error(raw(tenant, id.toString(), body), 400, "display-name-required");
        }
        for (String body : List.of("{", "[]", "42", "\"label\"", "{\"displayName\":true}", "{\"displayName\":42}",
                "{\"displayName\":[]}", "{\"displayName\":{}}", "{\"displayName\":\"A\",\"displayName\":\"B\"}",
                "{\"displayName\":\"Valid\",\"recordStatus\":\"ACTIVE\"}", "{\"displayName\":\"Valid\",\"naturalPersonDetails\":{}}",
                "{\"displayName\":\"Valid\"} {}")) {
            error(raw(tenant, id.toString(), body), 400, "bad-request");
        }
        assertEquals(before, current(tenant, id));
        assertEquals(0, eventCount(id));
    }

    @Test
    void enforcesContextBodyPathAndVersionSyntaxBeforeAbsenceVersionAndBlankness() {
        UUID tenant = UUID.randomUUID();
        UUID id = seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.DRAFT);
        Map<String, Object> before = current(tenant, id);
        Response missingTenant = given().header("Process-Id", PROCESS).header("User-Id", USER).contentType(ContentType.JSON)
                .body("{}").patch(ROOT + "invalid");
        error(missingTenant, 400, "tenant-id-required");
        error(raw(tenant, "invalid", "{}"), 400, "display-name-required");
        error(raw(tenant, "invalid", "{\"displayName\":\"" + "ß".repeat(151) + "\"}"), 400, "display-name-too-long");
        error(raw(tenant, "invalid", "{\"displayName\":\"Valid\"}"), 400, "party-id-invalid");
        error(raw(tenant, id.toString(), "{\"displayName\":\"Valid\"}"), 400, "if-match-required");
        error(request(tenant).header("If-Match", "0", "0").contentType(ContentType.JSON).body(Map.of("displayName", "Valid")).patch(ROOT + id),
                400, "if-match-duplicated");
        for (String version : List.of("\"0\"", "-1", "00", "*", "+1", "1.0")) {
            error(patch(tenant, id, version, "Valid"), 400, "if-match-invalid");
        }
        error(patch(tenant, id, "9223372036854775808", "Valid"), 400, "if-match-out-of-range");
        error(patch(UUID.randomUUID(), id, "1", " "), 404, "party-not-found");
        error(patch(tenant, UUID.randomUUID(), "1", " "), 404, "party-not-found");
        error(patch(tenant, id, "1", " "), 412, "expected-version-mismatch");
        error(patch(tenant, id, "9223372036854775807", "Valid"), 412, "expected-version-mismatch");
        assertEquals(before, current(tenant, id));
        assertEquals(0, eventCount(id));
    }

    @Test
    void suppliedIdempotencyKeysNeverTurnCorrectionsIntoLifecycleReplays() {
        UUID tenant = UUID.randomUUID();
        UUID id = seed(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        RequestSpecification first = request(tenant).header("If-Match", "0").header("Idempotency-Key", "ignored");
        accepted(first.contentType(ContentType.JSON).body(Map.of("displayName", "Corrected")).patch(ROOT + id), 1);
        error(request(tenant).header("If-Match", "0").header("Idempotency-Key", "ignored").contentType(ContentType.JSON)
                .body(Map.of("displayName", "Corrected")).patch(ROOT + id), 412, "expected-version-mismatch");
        accepted(request(tenant).header("If-Match", "1").header("Idempotency-Key", " ", "duplicate").contentType(ContentType.JSON)
                .body(Map.of("displayName", "Corrected")).patch(ROOT + id), 2);
        long completedResults = RootPartyFixtures.await(() -> sessions.withSession(session -> session.createNativeQuery(
                "select count(*) from api_idempotency_records where tenant_id = :tenant", Long.class)
                .setParameter("tenant", tenant).getSingleResult()));
        assertEquals(0, completedResults);
    }

    @Test
    void registrationReplaysAndIndependentIdentifierRowsRemainOriginalForBothTypes() throws Exception {
        UUID tenant = UUID.randomUUID();
        try (var _ = GeographicReferenceStubResource.allowContext(tenant.toString(), USER, PROCESS)) {
            for (PartyType type : PartyType.values()) {
                String route = type == PartyType.NATURAL_PERSON ? "/v1/natural-person" : "/v1/legal-entity";
                String scheme = type == PartyType.NATURAL_PERSON ? "TEST_NATURAL_ACTIVE" : "TEST_LEGAL_ACTIVE";
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("displayName", "Original");
                if (type == PartyType.NATURAL_PERSON) {
                    body.put("givenNames", "Original");
                    body.put("familyNames", "Person");
                } else {
                    body.put("legalName", "Original Company");
                    body.put("incorporationCountryCode", "EC");
                }
                body.put("initialIdentifier", Map.of("identifierSchemeCode", scheme, "value", "ID" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), "isPrimary", true));
                String key = "registration-" + UUID.randomUUID();
                Response created = request(tenant).header("Idempotency-Key", key).contentType(ContentType.JSON).body(body).post(route);
                assertEquals(201, created.statusCode());
                UUID id = UUID.fromString(created.jsonPath().getString("data.partyId"));
                String identifiers = identifiers(id);
                accepted(patch(tenant, id, "0", "Corrected"), 1);
                Response replay = request(tenant).header("Idempotency-Key", key).contentType(ContentType.JSON).body(body).post(route);
                assertEquals(201, replay.statusCode());
                assertEquals(created.jsonPath().getMap("$"), replay.jsonPath().getMap("$"));
                assertEquals(identifiers, identifiers(id));
                assertEquals("CORRECTED", current(tenant, id).get("displayName"));
                assertEquals(1, current(tenant, id).get("version"));
            }
        }
    }

    private UUID seed(UUID tenant, PartyType type, PartyRecordStatus state) {
        return RootPartyFixtures.await(() -> fixtures.create(tenant, type, state, " Historical Label ", CLOCK.instant()));
    }

    private long eventCount(UUID id) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.createNativeQuery(
                "select count(*) from party_outbox_events where aggregate_id = :id and event_type = 'party.updated.v1'", Long.class)
                .setParameter("id", id).getSingleResult()));
    }

    private String identifiers(UUID id) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.createNativeQuery(
                "select jsonb_agg(to_jsonb(i) order by i.id)::text from party_identifiers i where party_id = :id", String.class)
                .setParameter("id", id).getSingleResult()));
    }

    private static RequestSpecification request(UUID tenant) {
        return given().header("Tenant-Id", tenant.toString()).header("Process-Id", PROCESS).header("User-Id", USER);
    }

    private static Response patch(UUID tenant, UUID id, String version, String label) {
        return request(tenant).header("If-Match", version).contentType(ContentType.JSON).body(Map.of("displayName", label)).patch(ROOT + id);
    }

    private static Response raw(UUID tenant, String id, String body) {
        return request(tenant).contentType(ContentType.JSON).body(body).patch(ROOT + id);
    }

    private static Map<String, Object> current(UUID tenant, UUID id) {
        Response response = request(tenant).get(ROOT + id);
        assertEquals(200, response.statusCode());
        return response.jsonPath().getMap("data");
    }

    private static Map<String, Object> accepted(Response response, int version) {
        assertEquals(200, response.statusCode(), response.asString());
        assertEquals(200, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
        assertEquals(PROCESS, response.header("Process-Id"));
        assertEquals(version, response.jsonPath().getInt("data.version"));
        assertFalse(Instant.parse(response.jsonPath().getString("data.updatedAt")).isBefore(CLOCK.instant()));
        return response.jsonPath().getMap("data");
    }

    private static void preserved(Map<String, Object> before, Map<String, Object> after) {
        var original = new LinkedHashMap<>(before);
        var changed = new LinkedHashMap<>(after);
        for (String field : List.of("displayName", "version", "updatedAt", "updatedBy")) {
            original.remove(field);
            changed.remove(field);
        }
        assertEquals(original, changed);
    }

    private static void error(Response response, int status, String code) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(PROCESS, response.header("Process-Id"));
    }
}
