package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
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
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Races root corrections and lifecycle commands against type-specific HTTP updates sharing the same Party version. */
@QuarkusTest
@TestProfile(StoredOutboxTestProfile.class)
@Timeout(120)
class PartyRootDetailRaceContractTest {

    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final String ROOT = "/v1/parties/";
    @Inject
    RootPartyFixtures fixtures;
    @Inject
    Mutiny.SessionFactory sessions;

    @Test
    void everyRootMutationArbitratesWithBothTypeSpecificUpdatesWithoutRestoringStaleFields() throws Exception {
        for (PartyType type : PartyType.values()) {
            for (String action : List.of("patch", "activate", "deactivate", "archive")) {
                raceOne(type, action);
            }
        }
    }

    private void raceOne(PartyType type, String action) throws Exception {
        UUID tenant = UUID.randomUUID();
        PartyRecordStatus initial = action.equals("deactivate") ? PartyRecordStatus.ACTIVE : PartyRecordStatus.DRAFT;
        UUID id = RootPartyFixtures.await(() -> fixtures.create(tenant, type, initial, "Original", CLOCK.instant()));
        seedEvidence(tenant, id);
        String identifiers = identifiers(id);
        String rootProcess = UUID.randomUUID().toString();
        String detailProcess = UUID.randomUUID().toString();
        Map<String, Object> before = request(tenant, rootProcess, "root-racer").get(ROOT + id).jsonPath().getMap("data");
        Supplier<Response> rootCall = () -> root(tenant, id, action, rootProcess);
        Supplier<Response> detailCall = () -> detail(tenant, id, type, detailProcess);
        List<Response> responses = race(rootCall, detailCall);
        boolean rootWon = responses.getFirst().statusCode() == 200;
        assertEquals(List.of(200, 412), responses.stream().map(Response::statusCode).sorted().toList());
        assertEquals(rootProcess, responses.getFirst().header("Process-Id"));
        assertEquals(detailProcess, responses.getLast().header("Process-Id"));
        if (rootWon) {
            error(responses.getLast(), "expected-version-mismatch");
        } else {
            error(responses.getFirst(), action.equals("patch") ? "expected-version-mismatch" : "stale-party-version");
        }
        Map<String, Object> after = request(tenant, rootProcess, "root-racer").get(ROOT + id).jsonPath().getMap("data");
        assertEquals(1, after.get("version"));
        assertEquals(rootWon ? "root-racer" : "detail-racer", after.get("updatedBy"));
        assertEquals(before.get("createdAt"), after.get("createdAt"));
        assertEquals(before.get("createdBy"), after.get("createdBy"));
        String detailProperty = type == PartyType.NATURAL_PERSON ? "naturalPersonDetails" : "legalEntityDetails";
        if (rootWon) {
            assertEquals(before.get(detailProperty), after.get(detailProperty));
            assertEquals(rootState(action, initial), after.get("recordStatus"));
            assertEquals(action.equals("patch") ? "ROOT LABEL" : before.get("displayName"), after.get("displayName"));
        } else {
            assertEquals(initial.name(), after.get("recordStatus"));
            assertNotEquals("ROOT LABEL", after.get("displayName"));
            String field = type == PartyType.NATURAL_PERSON ? "preferredName" : "tradeName";
            assertEquals("DETAIL CHANGE", request(tenant, detailProcess, "detail-racer").get(ROOT + id).jsonPath().getString("data." + detailProperty + "." + field));
        }
        assertEquals(identifiers, identifiers(id));
        error(rootCall.get(), action.equals("patch") ? "expected-version-mismatch" : "stale-party-version");
        error(detailCall.get(), "expected-version-mismatch");
        assertEquals(after, request(tenant, rootProcess, "root-racer").get(ROOT + id).jsonPath().getMap("data"));
        long rootEvents = RootPartyFixtures.await(() -> sessions.withSession(session -> session.createNativeQuery(
                "select count(*) from party_outbox_events where aggregate_id = :id and correlation_id = :process", Long.class)
                .setParameter("id", id).setParameter("process", rootProcess).getSingleResult()));
        assertEquals(rootWon ? 1 : 0, rootEvents);
    }

    private void seedEvidence(UUID tenant, UUID id) {
        RootPartyFixtures.await(() -> sessions.withTransaction((session, transaction) -> session.createNativeQuery("""
                insert into party_identifiers (tenant_id, party_id, identifier_scheme_id, encrypted_value, encryption_key_version,
                    normalized_value_hash, masked_value, status, verified_at, verified_by, created_by, updated_by)
                values (:tenant, :party, :scheme, 'test-only-unread-ciphertext', 1, :hash, '***1234',
                    cast('VERIFIED' as party_identifier_status), :verifiedAt, 'fixture', 'fixture', 'fixture')
                """).setParameter("tenant", tenant).setParameter("party", id).setParameter("scheme", IdentifierSchemeTestFixtures.BOTH_RETIRED_ID)
                .setParameter("hash", UUID.randomUUID().toString().replace("-", "").repeat(2)).setParameter("verifiedAt", CLOCK.instant()).executeUpdate()));
    }

    private String identifiers(UUID id) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.createNativeQuery(
                "select jsonb_agg(to_jsonb(i) order by i.id)::text from party_identifiers i where party_id = :id", String.class)
                .setParameter("id", id).getSingleResult()));
    }

    private static Response root(UUID tenant, UUID id, String action, String process) {
        var request = request(tenant, process, "root-racer").header("If-Match", "0");
        return action.equals("patch") ? request.contentType(ContentType.JSON).body(Map.of("displayName", "Root Label")).patch(ROOT + id)
                : request.post(ROOT + id + "/" + action);
    }

    private static Response detail(UUID tenant, UUID id, PartyType type, String process) {
        String path = type == PartyType.NATURAL_PERSON ? "/v1/natural-person/" : "/v1/legal-entity/";
        String field = type == PartyType.NATURAL_PERSON ? "preferredName" : "tradeName";
        return request(tenant, process, "detail-racer").header("If-Match", "0").contentType(ContentType.JSON)
                .body(Map.of(field, "Detail Change")).patch(path + id);
    }

    private static RequestSpecification request(UUID tenant, String process, String user) {
        return given().header("Tenant-Id", tenant.toString()).header("Process-Id", process).header("User-Id", user);
    }

    private static String rootState(String action, PartyRecordStatus initial) {
        return switch (action) {
            case "patch" -> initial.name();
            case "activate" -> "ACTIVE";
            case "deactivate" -> "INACTIVE";
            case "archive" -> "ARCHIVED";
            default -> throw new IllegalArgumentException("Unknown test action");
        };
    }

    private static List<Response> race(Supplier<Response> first, Supplier<Response> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> afterStart(first, ready, start));
            var two = executor.submit(() -> afterStart(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }

    private static Response afterStart(Supplier<Response> operation, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return operation.get();
    }

    private static void error(Response response, String code) {
        assertEquals(412, response.statusCode(), response.asString());
        assertEquals(Map.of("status", 412, "code", code), response.jsonPath().getMap("$"));
    }
}
