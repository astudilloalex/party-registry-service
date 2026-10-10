package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises real identifier GET routes, shared envelopes, ownership, exact filters and authenticated navigation. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(120)
class PartyIdentifierReadResourceContractTest {

    private static final String PROCESS = UUID.randomUUID().toString();
    private static final LocalDate DATE = LocalDate.of(2025, Month.FEBRUARY, 28);
    private static final Timestamp INSTANT = Timestamp.from(DATE.atStartOfDay(ZoneOffset.UTC).toInstant().plusNanos(123456000));
    private static final String BASE = "/v1/parties/{party}/identifiers";

    @Inject
    AgroalDataSource database;

    @Inject
    ObjectMapper json;

    @ParameterizedTest
    @MethodSource("owners")
    void listsCompleteHistoryForBothTypesAndEveryPartyState(PartyType type, PartyRecordStatus state) throws Exception {
        Fixture fixture = seed(type, state);
        JsonNode body = page(request(fixture).get(BASE, fixture.party()), 50);
        assertEquals(5, body.path("totalElements").intValue());
        assertEquals(fixture.ids(), ids(body));
    }

    @Test
    void listingPreservesTheCompleteStoredPartyAndIdentifierRows() throws Exception {
        Fixture fixture = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.ACTIVE);
        String before = snapshot(fixture);
        page(request(fixture).get(BASE, fixture.party()), 50);
        assertEquals(before, snapshot(fixture));
    }

    @ParameterizedTest
    @MethodSource("owners")
    void retrievesEveryHistoricalStateWithoutReadmissionOrMutation(PartyType type, PartyRecordStatus state) throws Exception {
        Fixture fixture = seed(type, state);
        String before = snapshot(fixture);
        for (UUID id : fixture.ids()) {
            JsonNode body = item(request(fixture).get(BASE + "/{identifier}", fixture.party(), id));
            assertEquals(fixture.statuses().get(id), body.path("data").path("status").textValue());
            assertEquals("legacy-authority", body.path("data").path("issuerCode").textValue());
            assertEquals(8, body.path("data").path("version").longValue());
            assertFalse(body.toString().contains("synthetic-http-ciphertext"));
        }
        assertEquals(before, snapshot(fixture));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void forwardPagesHaveExactCountsAndUnsignedTieOrdering(int index) throws Exception {
        Fixture fixture = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED);
        Response response = request(fixture).queryParam("limit", "2").get(BASE, fixture.party());
        for (int step = 0; step < index; step++) {
            assertEquals(200, response.statusCode());
            String cursor = response.path("nextCursor");
            response = request(fixture).queryParam("limit", "2").queryParam("cursor", cursor).get(BASE, fixture.party());
        }
        JsonNode body = page(response, 2);
        assertEquals(5, body.path("totalElements").intValue());
        assertEquals(fixture.ids().subList(index * 2, Math.min(5, index * 2 + 2)), ids(body));
    }

    @Test
    void backwardCursorRecoversThePreviousPage() throws Exception {
        Fixture fixture = seed(PartyType.LEGAL_ENTITY, PartyRecordStatus.INACTIVE);
        Response first = request(fixture).queryParam("limit", "2").get(BASE, fixture.party());
        assertEquals(200, first.statusCode());
        String next = first.path("nextCursor");
        Response middle = request(fixture).queryParam("limit", "2").queryParam("cursor", next).get(BASE, fixture.party());
        assertEquals(200, middle.statusCode());
        String previous = middle.path("prevCursor");
        JsonNode body = page(request(fixture).queryParam("limit", "2").queryParam("cursor", previous).get(BASE, fixture.party()), 2);
        assertEquals(fixture.ids().subList(0, 2), ids(body));
    }

    @Test
    void exactFilterIntersectionReturnsOnlyVerifiedPrimaryEvidence() throws Exception {
        Fixture fixture = seed(PartyType.LEGAL_ENTITY, PartyRecordStatus.ACTIVE);
        JsonNode body = page(request(fixture).queryParam("identifierSchemeId", IdentifierSchemeTestFixtures.BOTH_EXPIRING_ID)
                .queryParam("schemeCode", IdentifierSchemeTestFixtures.BOTH_EXPIRING_CODE)
                .queryParam("status", "VERIFIED").queryParam("isPrimary", "true").get(BASE, fixture.party()), 50);
        assertEquals(1, body.path("totalElements").intValue());
        assertEquals("VERIFIED", body.path("data").get(0).path("status").textValue());
        assertTrue(body.path("data").get(0).path("isPrimary").booleanValue());
    }

    @Test
    void validUnknownAndMismatchedSchemesProduceEmptyOwnedPages() throws Exception {
        Fixture fixture = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        for (String code : List.of("unknown-scheme", IdentifierSchemeTestFixtures.BOTH_RETIRED_CODE)) {
            JsonNode body = page(request(fixture).queryParam("identifierSchemeId", IdentifierSchemeTestFixtures.BOTH_EXPIRING_ID)
                    .queryParam("schemeCode", code).get(BASE, fixture.party()), 50);
            assertEquals(0, body.path("totalElements").intValue());
            assertTrue(body.path("data").isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "200"})
    void cursorCannotCrossEffectiveLimitScope(String limit) throws Exception {
        Fixture fixture = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        Response first = request(fixture).queryParam("limit", "2").get(BASE, fixture.party());
        assertEquals(200, first.statusCode());
        String cursor = first.path("nextCursor");
        error(request(fixture).queryParam("limit", limit).queryParam("cursor", cursor).get(BASE, fixture.party()), 400, "bad-request");
    }

    @Test
    void concealsCrossTenantAndWrongPartyTargets() throws Exception {
        Fixture owned = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        Fixture foreign = seed(PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED);
        error(request(owned).get(BASE, foreign.party()), 404, "party-not-found");
        error(request(owned).get(BASE + "/{identifier}", owned.party(), foreign.ids().getFirst()), 404, "identifier-not-found");
        error(request(owned).get(BASE + "/{identifier}", owned.party(), UUID.randomUUID()), 404, "identifier-not-found");
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void invalidQueriesNeverReturnData(String name, List<String> values) throws Exception {
        Fixture fixture = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        error(request(fixture).queryParam(name, values).get(BASE, fixture.party()), 400, "bad-request");
    }

    @Test
    void canonicalParentAndChildSyntaxPrecedeLookups() throws Exception {
        Fixture fixture = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        error(request(fixture).get(BASE + "/{identifier}", "bad-party", "bad-identifier"), 400, "party-id-invalid");
        error(request(fixture).get(BASE + "/{identifier}", fixture.party(), fixture.ids().getFirst().toString().toUpperCase(Locale.ROOT)),
                400, "bad-request");
    }

    @Test
    void headerFailuresRetainAcceptedProcessEchoAndRejectInvalidProcess() throws Exception {
        Fixture fixture = seed(PartyType.LEGAL_ENTITY, PartyRecordStatus.ACTIVE);
        Response missingUser = given().header("Process-Id", PROCESS).header("Tenant-Id", fixture.tenant()).get(BASE, fixture.party());
        error(missingUser, 400, "user-id-required");
        Response invalidProcess = given().header("Process-Id", "invalid").header("Tenant-Id", fixture.tenant()).header("User-Id", "reader")
                .get(BASE, fixture.party());
        assertEquals(400, invalidProcess.statusCode());
        assertNull(invalidProcess.header("Process-Id"));
        PartyIdentifierContractAssertions.assertErrorResponse(400, "process-id-invalid", json.readTree(invalidProcess.asString()));
    }

    private JsonNode page(Response response, int limit) throws Exception {
        assertEquals(200, response.statusCode());
        assertEquals(PROCESS, response.header("Process-Id"));
        JsonNode body = json.readTree(response.asString());
        PartyIdentifierContractAssertions.assertPageResponse(200, body, limit);
        return body;
    }

    private JsonNode item(Response response) throws Exception {
        assertEquals(200, response.statusCode());
        assertEquals(PROCESS, response.header("Process-Id"));
        JsonNode body = json.readTree(response.asString());
        PartyIdentifierContractAssertions.assertItemResponse(200, body);
        return body;
    }

    private void error(Response response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode());
        assertEquals(PROCESS, response.header("Process-Id"));
        PartyIdentifierContractAssertions.assertErrorResponse(status, code, json.readTree(response.asString()));
    }

    private static RequestSpecification request(Fixture fixture) {
        return given().header("Process-Id", PROCESS).header("Tenant-Id", fixture.tenant()).header("User-Id", "http-reader");
    }

    private static List<UUID> ids(JsonNode body) {
        List<UUID> ids = new ArrayList<>();
        body.path("data").forEach(row -> ids.add(UUID.fromString(row.path("identifierId").textValue())));
        return ids;
    }

    private static Stream<Arguments> owners() {
        return Stream.of(PartyType.values()).flatMap(type -> Stream.of(PartyRecordStatus.values()).map(state -> Arguments.of(type, state)));
    }

    private static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("unknown", List.of("value")), Arguments.of("status", List.of("VERIFIED", "VERIFIED")),
                Arguments.of("status", List.of("verified")), Arguments.of("isPrimary", List.of("1")),
                Arguments.of("schemeCode", List.of(" ")), Arguments.of("identifierSchemeId", List.of("1-1-1-1-1")),
                Arguments.of("limit", List.of("0")), Arguments.of("limit", List.of("201")), Arguments.of("limit", List.of("1.5")));
    }

    private Fixture seed(PartyType type, PartyRecordStatus state) throws SQLException {
        UUID tenant = UUID.randomUUID();
        UUID party = UUID.randomUUID();
        Map<UUID, String> statuses = new java.util.HashMap<>();
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("INSERT INTO parties (id, tenant_id, type, display_name, record_status, created_by, updated_by, version) VALUES (?, ?, ?::party_type, 'HTTP read fixture', ?::party_record_status, 'creator', 'creator', 12)")) {
                insert.setObject(1, party);
                insert.setObject(2, tenant);
                insert.setString(3, type.name());
                insert.setString(4, state.name());
                insert.executeUpdate();
            }
            String detail = type == PartyType.NATURAL_PERSON
                    ? "INSERT INTO natural_person_details (party_id, given_names, family_names, created_by, updated_by) VALUES (?, 'HTTP', 'Reader', 'creator', 'creator')"
                    : "INSERT INTO legal_entity_details (party_id, legal_name, incorporation_country_code, created_by, updated_by) VALUES (?, 'HTTP company', 'EC', 'creator', 'creator')";
            try (var insert = connection.prepareStatement(detail)) {
                insert.setObject(1, party);
                insert.executeUpdate();
            }
            for (PartyIdentifierStatus status : PartyIdentifierStatus.values()) {
                UUID id = UUID.randomUUID();
                statuses.put(id, status.name());
                insertIdentifier(connection, tenant, party, id, status);
            }
            connection.commit();
        }
        List<UUID> ordered = statuses.keySet().stream().sorted(Comparator.comparing(UUID::toString).reversed()).toList();
        return new Fixture(tenant, party, ordered, Map.copyOf(statuses));
    }

    private static void insertIdentifier(Connection connection, UUID tenant, UUID party, UUID id, PartyIdentifierStatus status) throws SQLException {
        boolean verified = status == PartyIdentifierStatus.VERIFIED || status == PartyIdentifierStatus.REVOKED;
        try (var insert = connection.prepareStatement("""
                INSERT INTO party_identifiers (id, tenant_id, party_id, identifier_scheme_id, issuer_code, encrypted_value,
                    encryption_key_version, normalized_value_hash, masked_value, normalization_version, is_primary, status,
                    issued_on, expires_on, verified_at, verified_by, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, 'legacy-authority', 'synthetic-http-ciphertext', 7, ?, '***1234', 3, ?,
                    ?::party_identifier_status, ?, ?, ?, ?, ?, 'creator', ?, 'maintainer', 8)
                """)) {
            insert.setObject(1, id);
            insert.setObject(2, tenant);
            insert.setObject(3, party);
            insert.setObject(4, IdentifierSchemeTestFixtures.BOTH_EXPIRING_ID);
            insert.setString(5, id.toString().replace("-", "").repeat(2));
            insert.setBoolean(6, status == PartyIdentifierStatus.VERIFIED || status == PartyIdentifierStatus.PENDING_VERIFICATION);
            insert.setString(7, status.name());
            insert.setObject(8, DATE.minusYears(1));
            insert.setObject(9, DATE.minusDays(1));
            insert.setTimestamp(10, verified ? INSTANT : null);
            insert.setString(11, verified ? "original-verifier" : null);
            insert.setTimestamp(12, INSTANT);
            insert.setTimestamp(13, INSTANT);
            insert.executeUpdate();
        }
    }

    private String snapshot(Fixture fixture) throws SQLException {
        try (Connection connection = database.getConnection(); var query = connection.prepareStatement("""
                SELECT jsonb_build_object('party', (SELECT to_jsonb(p) FROM parties p WHERE p.id = ?),
                    'identifiers', (SELECT jsonb_agg(to_jsonb(i) ORDER BY i.id) FROM party_identifiers i WHERE i.party_id = ?))::text
                """)) {
            query.setObject(1, fixture.party());
            query.setObject(2, fixture.party());
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    /** Retains synthetic database ownership, exact order and stored statuses for real-resource assertions. */
    private record Fixture(UUID tenant, UUID party, List<UUID> ids, Map<UUID, String> statuses) { }
}
