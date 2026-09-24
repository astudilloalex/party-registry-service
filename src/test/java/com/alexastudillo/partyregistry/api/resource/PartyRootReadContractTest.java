package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies root read wire contracts, literal canonical filters, exact scoped navigation, and safe retained details. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class PartyRootReadContractTest {

    private static final String ROOT = "/v1/parties";
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final Set<String> SUMMARY_KEYS = Set.of("partyId", "type", "displayName", "recordStatus", "createdAt", "version");

    @Inject
    RootPartyFixtures fixtures;

    @Test
    void allTypesAndStatesHaveSafeCurrentDetailsAndStableBidirectionalSummaryPages() {
        UUID tenant = UUID.randomUUID();
        List<String> expected = new ArrayList<>();
        for (PartyType type : PartyType.values()) {
            for (PartyRecordStatus status : PartyRecordStatus.values()) {
                UUID id = seed(tenant, type, status, " Historical Áda ", CLOCK.instant());
                expected.add(id.toString());
                Response response = request(tenant).get(ROOT + "/" + id);
                success(response);
                assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
                String detail = type == PartyType.NATURAL_PERSON ? "naturalPersonDetails" : "legalEntityDetails";
                Map<String, Object> data = response.jsonPath().getMap("data");
                assertEquals(Set.of("partyId", "type", "displayName", "recordStatus", "version", "createdAt", "updatedAt", "createdBy", "updatedBy", detail), data.keySet());
                assertEquals(id.toString(), data.get("partyId"));
                assertEquals(type.name(), data.get("type"));
                assertEquals(status.name(), data.get("recordStatus"));
                assertEquals(" Historical Áda ", data.get("displayName"));
                assertEquals(0, data.get("version"));
                assertEquals(CLOCK.instant(), Instant.parse((String) data.get("createdAt")));
                assertEquals(data.get("createdAt"), data.get("updatedAt"));
                assertEquals("fixture-creator", data.get("createdBy"));
                assertEquals(data.get("createdBy"), data.get("updatedBy"));
                assertFalse(response.asString().contains("identifier"));
            }
        }
        seed(UUID.randomUUID(), PartyType.NATURAL_PERSON, PartyRecordStatus.ACTIVE, "Hidden", CLOCK.instant().plusSeconds(1));
        expected.sort(Comparator.reverseOrder());
        Response first = request(tenant).queryParam("limit", 3).get(ROOT);
        List<String> actual = new ArrayList<>(page(first, 8, 3, 3));
        assertNull(first.jsonPath().get("prevCursor"));
        Response second = request(tenant).queryParam("limit", 3).queryParam("cursor", first.jsonPath().getString("nextCursor")).get(ROOT);
        actual.addAll(page(second, 8, 3, 3));
        Response previous = request(tenant).queryParam("limit", 3).queryParam("cursor", second.jsonPath().getString("prevCursor")).get(ROOT);
        assertEquals(first.jsonPath().getMap("$"), previous.jsonPath().getMap("$"));
        Response last = request(tenant).queryParam("limit", 3).queryParam("cursor", second.jsonPath().getString("nextCursor")).get(ROOT);
        actual.addAll(page(last, 8, 3, 2));
        assertNull(last.jsonPath().get("nextCursor"));
        assertNotNull(last.jsonPath().get("prevCursor"));
        assertEquals(expected, actual);
    }

    @Test
    void intersectsEveryFilterWithInclusiveInstantsAndLiteralHistoricalUnicode() {
        UUID tenant = UUID.randomUUID();
        Instant lower = CLOCK.instant().minusSeconds(1);
        UUID first = seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED, " Straße  Águila_% ", CLOCK.instant());
        UUID second = seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED, "straße  águila_% retained", lower);
        seed(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED, "Straße  Águila_%", lower);
        seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ACTIVE, "Straße  Águila_%", lower);
        seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED, "Straße  Águila_%", lower.minusNanos(1000));
        seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED, "Straße  Águila_%", CLOCK.instant().plusNanos(1000));
        seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED, "Straße  ÁguilaXX", lower);
        seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED, "Straße Águila_%", lower);
        seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED, "Straße  Aguila_%", lower);
        var filters = new LinkedHashMap<String, Object>(Map.of("type", "LEGAL_ENTITY", "recordStatus", "ARCHIVED",
                "displayNameStartsWith", " straße  á ", "displayNameContains", " águila_% ",
                "createdFrom", lower.atOffset(ZoneOffset.ofHours(-5)).toString(), "createdTo", CLOCK.instant().toString()));
        Response response = request(tenant).queryParams(filters).get(ROOT);
        assertEquals(List.of(first.toString(), second.toString()), page(response, 2, 1, 2));
        assertEquals(" Straße  Águila_% ", response.jsonPath().getString("data[0].displayName"));
        filters.put("createdFrom", CLOCK.instant().toString());
        assertEquals(List.of(first.toString()), page(request(tenant).queryParams(filters).get(ROOT), 1, 1, 1));
        filters.put("displayNameContains", "does-not-match");
        Response empty = request(tenant).queryParams(filters).get(ROOT);
        assertEquals(List.of(), page(empty, 0, 0, 0));
        assertNull(empty.jsonPath().get("nextCursor"));
        assertNull(empty.jsonPath().get("prevCursor"));
    }

    @Test
    void cursorAuthenticatesTenantEveryEffectiveFilterAndLimitButAcceptsEquivalentValues() {
        UUID tenant = UUID.randomUUID();
        seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED, "Straße  Águila_%", CLOCK.instant());
        UUID second = seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED, "Straße  Águila_%", CLOCK.instant().minusNanos(1000));
        var filters = new LinkedHashMap<String, Object>(Map.of("type", "LEGAL_ENTITY", "recordStatus", "ARCHIVED", "limit", 1,
                "displayNameStartsWith", " straße ", "displayNameContains", " águila_% ",
                "createdFrom", CLOCK.instant().minusSeconds(1).toString(), "createdTo", CLOCK.instant().toString()));
        Response first = request(tenant).queryParams(filters).get(ROOT);
        page(first, 2, 2, 1);
        String cursor = first.jsonPath().getString("nextCursor");
        assertNotNull(cursor);
        var equivalent = new LinkedHashMap<>(filters);
        equivalent.put("displayNameStartsWith", "STRASSE");
        equivalent.put("displayNameContains", "ÁGUILA_%");
        equivalent.put("createdTo", CLOCK.instant().atOffset(ZoneOffset.ofHours(3)).toString());
        assertEquals(List.of(second.toString()), page(request(tenant).queryParams(equivalent).queryParam("cursor", cursor).get(ROOT), 2, 2, 1));
        error(request(UUID.randomUUID()).queryParams(filters).queryParam("cursor", cursor).get(ROOT), 400, "bad-request");
        Map<String, Object> changes = Map.of("type", "NATURAL_PERSON", "recordStatus", "DRAFT", "limit", 2,
                "displayNameStartsWith", "other", "displayNameContains", "other",
                "createdFrom", CLOCK.instant().minusSeconds(2).toString(), "createdTo", CLOCK.instant().plusSeconds(1).toString());
        changes.forEach((key, value) -> {
            var changed = new LinkedHashMap<>(filters);
            changed.put(key, value);
            error(request(tenant).queryParams(changed).queryParam("cursor", cursor).get(ROOT), 400, "bad-request");
        });
        String altered = cursor.substring(0, cursor.length() - 1) + (cursor.endsWith("x") ? "y" : "x");
        error(request(tenant).queryParams(filters).queryParam("cursor", altered).get(ROOT), 400, "bad-request");
    }

    @Test
    void defaultAndBoundaryPageSizesAndBlankNameFiltersDoNotBroadenInvalidInput() {
        UUID tenant = UUID.randomUUID();
        RootPartyFixtures.await(() -> Multi.createFrom().range(0, 51).onItem().transformToUniAndConcatenate(index ->
                fixtures.create(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT, "Label", CLOCK.instant().minusSeconds(index)))
                .collect().asList());
        page(request(tenant).get(ROOT), 51, 2, 50);
        page(request(tenant).queryParam("limit", 1).get(ROOT), 51, 51, 1);
        Response maximum = request(tenant).queryParam("limit", 200).get(ROOT);
        page(maximum, 51, 1, 51);
        assertNull(maximum.jsonPath().get("nextCursor"));
        Response blank = request(tenant).queryParam("displayNameStartsWith", " ").queryParam("displayNameContains", "").get(ROOT);
        page(blank, 51, 2, 50);
        for (String name : List.of("displayNameStartsWith", "displayNameContains")) {
            page(request(tenant).queryParam(name, "🙂".repeat(300)).get(ROOT), 0, 0, 0);
            error(request(tenant).queryParam(name, "🙂".repeat(301)).get(ROOT), 400, "bad-request");
        }
    }

    @Test
    void rejectsUnknownDuplicateMalformedAndOutOfRangeQueryParameters() {
        UUID tenant = UUID.randomUUID();
        Map<String, String> valid = Map.of("type", "NATURAL_PERSON", "recordStatus", "DRAFT", "displayNameStartsWith", "name",
                "displayNameContains", "name", "createdFrom", CLOCK.instant().toString(), "createdTo", CLOCK.instant().toString(),
                "limit", "1", "cursor", "duplicate-cursor");
        valid.forEach((name, value) -> error(request(tenant).queryParam(name, value, value).get(ROOT), 400, "bad-request"));
        Map<String, List<String>> invalid = Map.of("unknown", List.of("value"), "type", List.of("natural_person", "OTHER", ""),
                "recordStatus", List.of("active", "DELETED", ""), "limit", List.of("", " ", "0", "201", "-1", "+1", "1.5", "999999999999999"),
                "cursor", List.of("", " ", "malformed"), "createdFrom", List.of("invalid-date", CLOCK.instant().atOffset(ZoneOffset.UTC).toLocalDateTime().toString()),
                "createdTo", List.of("invalid-date", ""));
        invalid.forEach((name, values) -> values.forEach(value -> error(request(tenant).queryParam(name, value).get(ROOT), 400, "bad-request")));
        error(request(tenant).queryParam("createdFrom", CLOCK.instant().plusSeconds(1).toString())
                .queryParam("createdTo", CLOCK.instant().toString()).get(ROOT), 400, "bad-request");
    }

    @Test
    void concealsCrossTenantDetailsAndRejectsNoncanonicalIdsWithoutChangingStoredData() {
        UUID tenant = UUID.randomUUID();
        UUID id = seed(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED, " Historical ", CLOCK.instant());
        Map<String, Object> original = request(tenant).get(ROOT + "/" + id).jsonPath().getMap("data");
        error(request(UUID.randomUUID()).get(ROOT + "/" + id), 404, "party-not-found");
        error(request(tenant).get(ROOT + "/" + UUID.randomUUID()), 404, "party-not-found");
        for (String invalid : List.of("invalid", "1-1-1-1-1", "0198CE2B-D6A3-7D6E-80BA-D97B21D793E5")) {
            error(request(tenant).get(ROOT + "/" + invalid), 400, "party-id-invalid");
        }
        assertEquals(original, request(tenant).get(ROOT + "/" + id).jsonPath().getMap("data"));
    }

    private UUID seed(UUID tenant, PartyType type, PartyRecordStatus status, String label, Instant created) {
        return RootPartyFixtures.await(() -> fixtures.create(tenant, type, status, label, created));
    }

    private static RequestSpecification request(UUID tenant) {
        return given().header("Tenant-Id", tenant.toString()).header("Process-Id", PROCESS).header("User-Id", "root-contract-reader");
    }

    private static void success(Response response) {
        assertEquals(200, response.statusCode(), response.asString());
        assertEquals(200, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(PROCESS, response.header("Process-Id"));
    }

    private static List<String> page(Response response, int total, int pages, int size) {
        success(response);
        assertEquals(Set.of("status", "code", "data", "nextCursor", "prevCursor", "totalElements", "totalPages", "numberOfElements"), response.jsonPath().getMap("$").keySet());
        assertEquals(total, response.jsonPath().getInt("totalElements"));
        assertEquals(pages, response.jsonPath().getInt("totalPages"));
        assertEquals(size, response.jsonPath().getInt("numberOfElements"));
        List<Map<String, Object>> summaries = response.jsonPath().getList("data");
        assertEquals(size, summaries.size());
        summaries.forEach(summary -> assertEquals(SUMMARY_KEYS, summary.keySet()));
        assertTrue(size <= 200);
        return summaries.stream().map(summary -> (String) summary.get("partyId")).toList();
    }

    private static void error(Response response, int status, String code) {
        assertEquals(status, response.statusCode());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(PROCESS, response.header("Process-Id"));
    }
}
