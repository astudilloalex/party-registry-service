package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierPatchProducer;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierRegistrationProducer;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.NullNode;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises actual metadata PATCH transport, independent preconditions and complete stored/history/event preservation. */
@QuarkusTest
@TestProfile(PartyIdentifierPatchResourceContractTest.PatchProfile.class)
@Timeout(120)
class PartyIdentifierPatchResourceContractTest {
    private static final String BASE = "/v1/parties/{party}/identifiers";
    private static final String USER = "metadata-http-operator";
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final LocalDate DATE = FixedPartyIdentifierRegistrationProducer.DATE;
    @Inject AgroalDataSource database;
    @Inject ObjectMapper json;

    @Test
    void bothTypesAndEligibleStatesCorrectOnlyMetadataAuditAndOneIdentifierVersion() throws Exception {
        for (PartyType type : PartyType.values()) {
            for (var state : List.of(PartyIdentifierStatus.PENDING_VERIFICATION, PartyIdentifierStatus.VERIFIED)) {
                var fixture = create(type); state(fixture, state, 3, DATE.plusDays(10));
                JsonNode before = stored(fixture); String parent = parent(fixture);
                JsonNode result = success(patch(fixture, "3", "{\"issuerCode\":\"civil_registry\"}"), 200);
                assertEquals("CIVIL_REGISTRY", result.path("issuerCode").textValue()); assertEquals(4, result.path("version").longValue());
                assertEquals(state.name(), result.path("status").textValue()); assertEquals(before.path("is_primary").booleanValue(), result.path("isPrimary").booleanValue());
                assertEquals(FixedPartyIdentifierPatchProducer.CLOCK.instant().truncatedTo(ChronoUnit.MICROS).toString(), result.path("updatedAt").textValue());
                assertProtectedHistory(before, stored(fixture)); assertEquals(parent, parent(fixture)); assertEquals(List.of(1L, 1L, 2L), counts(fixture));
                assertEquals(fixture.original(), success(post(fixture), 201)); assertEquals(List.of(1L, 1L, 2L), counts(fixture));
            }
        }
    }

    @Test
    void suppliedNullClearsAndOmittedMetadataAndVerificationStayIntact() throws Exception {
        var fixture = create(PartyType.NATURAL_PERSON); state(fixture, PartyIdentifierStatus.VERIFIED, 3, DATE.plusDays(10));
        JsonNode before = stored(fixture);
        JsonNode result = success(patch(fixture, "3", "{\"issuerCode\":null}"), 200);
        assertFalse(result.has("issuerCode")); assertEquals(DATE.minusYears(1).toString(), result.path("issuedOn").textValue());
        assertEquals(DATE.plusDays(10).toString(), result.path("expiresOn").textValue()); assertProtectedHistory(before, stored(fixture));
        result = success(patch(fixture, "4", "{\"issuedOn\":null,\"expiresOn\":null}"), 200);
        assertFalse(result.has("issuedOn")); assertFalse(result.has("expiresOn")); assertEquals(5, result.path("version").longValue());
        assertProtectedHistory(before, stored(fixture)); assertEquals(List.of(1L, 1L, 3L), counts(fixture));
    }

    @Test
    void terminalAndExhaustedTargetsRejectWithoutAnyStoredOrLogicalEffect() throws Exception {
        for (var state : List.of(PartyIdentifierStatus.REJECTED, PartyIdentifierStatus.REVOKED, PartyIdentifierStatus.EXPIRED)) {
            var fixture = create(PartyType.LEGAL_ENTITY); state(fixture, state, Long.MAX_VALUE, DATE.minusDays(1));
            JsonNode before = stored(fixture); String parent = parent(fixture);
            error(patch(fixture, Long.toString(Long.MAX_VALUE), "{\"expiresOn\":null}"), 409, "invalid-identifier-lifecycle");
            assertEquals(before, stored(fixture)); assertEquals(parent, parent(fixture)); assertEquals(List.of(1L, 1L, 1L), counts(fixture));
        }
        var exhausted = create(PartyType.NATURAL_PERSON); state(exhausted, PartyIdentifierStatus.VERIFIED, Long.MAX_VALUE, DATE.minusDays(1));
        JsonNode before = stored(exhausted);
        error(patch(exhausted, Long.toString(Long.MAX_VALUE), "{\"issuerCode\":\"CIVIL_REGISTRY\"}"), 409, "identifier-version-exhausted");
        assertEquals(before, stored(exhausted)); assertEquals(List.of(1L, 1L, 1L), counts(exhausted));
    }

    @Test
    void staleVersionPrecedesStateAndPartyVersionNeverSubstitutesForIdentifierVersion() throws Exception {
        var fixture = create(PartyType.NATURAL_PERSON); state(fixture, PartyIdentifierStatus.REVOKED, 3, DATE.minusDays(1));
        JsonNode before = stored(fixture);
        error(patch(fixture, "7", "{\"expiresOn\":null}"), 412, "expected-version-mismatch");
        assertEquals(before, stored(fixture)); assertEquals(List.of(1L, 1L, 1L), counts(fixture));
        var other = create(PartyType.LEGAL_ENTITY); JsonNode unchanged = stored(other);
        error(patch(other, "7", "{\"issuerCode\":null}"), 412, "expected-version-mismatch");
        assertEquals(unchanged, stored(other));
    }

    @Test
    void expiredByDateEvidenceNeedsRepairAndResultingIntervalsCannotBeInverted() throws Exception {
        for (var state : List.of(PartyIdentifierStatus.PENDING_VERIFICATION, PartyIdentifierStatus.VERIFIED)) {
            var fixture = create(PartyType.NATURAL_PERSON); state(fixture, state, 3, DATE.minusDays(1));
            JsonNode before = stored(fixture);
            error(patch(fixture, "3", "{\"issuerCode\":\"CIVIL_REGISTRY\"}"), 422, "identifier-validation-failure");
            assertEquals(before, stored(fixture));
            JsonNode corrected = success(patch(fixture, "3", "{\"expiresOn\":\"" + DATE + "\"}"), 200);
            assertEquals(DATE.toString(), corrected.path("expiresOn").textValue());
            success(patch(fixture, "4", "{\"expiresOn\":null}"), 200);
            JsonNode repaired = stored(fixture);
            error(patch(fixture, "5", "{\"issuedOn\":\"" + DATE.plusDays(2) + "\",\"expiresOn\":\"" + DATE.plusDays(1) + "\"}"), 422, "identifier-validation-failure");
            assertEquals(repaired, stored(fixture)); assertEquals(List.of(1L, 1L, 3L), counts(fixture));
        }
    }

    @Test
    void identicalCorrectionAdvancesOnceAndEveryReplayHeaderIsIgnored() throws Exception {
        var fixture = create(PartyType.LEGAL_ENTITY);
        var response = request(fixture).header("If-Match", "0").header("Idempotency-Key", "", "unused-key")
                .body("{\"issuerCode\":\"FIXTURE_AUTHORITY\"}").patch(BASE + "/{identifier}", fixture.party(), fixture.identifier());
        assertEquals(1, success(response, 200).path("version").longValue());
        error(patch(fixture, "0", "{\"issuerCode\":\"FIXTURE_AUTHORITY\"}"), 412, "expected-version-mismatch");
        assertEquals(List.of(1L, 1L, 2L), counts(fixture)); assertEquals(fixture.original(), success(post(fixture), 201));
    }

    @Test
    void bodyThenSelectorsThenVersionUseStableCodesBeforeAnyMutation() throws Exception {
        var fixture = create(PartyType.NATURAL_PERSON);
        for (String body : List.of("{\"status\":null}", "{\"issuerCode\":42}", "{\"issuerCode\":null,\"issuerCode\":null}",
                "{\"issuedOn\":\"2026-02-30\"}", "{\"expiresOn\":null} {}", "null {}")) {
            error(request(fixture).body(body).patch(BASE + "/{identifier}", "bad-party", "bad-identifier"), 400, "bad-request");
        }
        for (String missing : List.of("", "null")) { error(request(fixture).body(missing).patch(BASE + "/{identifier}", "bad-party", "bad-identifier"), 400, "request-body-required"); }
        error(request(fixture).body("{}").patch(BASE + "/{identifier}", "bad-party", "bad-identifier"), 400, "patch-property-required");
        error(request(fixture).body("{\"issuerCode\":\"" + "😀".repeat(65) + "\"}").patch(BASE + "/{identifier}", "bad-party", "bad-identifier"), 400, "issuer-code-too-long");
        error(request(fixture).body("{\"issuerCode\":null}").patch(BASE + "/{identifier}", "bad-party", "bad-identifier"), 400, "party-id-invalid");
        error(request(fixture).body("{\"issuerCode\":null}").patch(BASE + "/{identifier}", fixture.party(), "bad-identifier"), 400, "bad-request");
        error(request(fixture).body("{\"issuerCode\":null}").patch(BASE + "/{identifier}", fixture.party(), fixture.identifier()), 400, "if-match-required");
        for (String version : List.of("\"0\"", "01", "*", "-1")) { error(patch(fixture, version, "{\"issuerCode\":null}"), 400, "if-match-invalid"); }
        error(patch(fixture, "9223372036854775808", "{\"issuerCode\":null}"), 400, "if-match-out-of-range");
        error(request(fixture).header("If-Match", "0", "0").body("{\"issuerCode\":null}").patch(BASE + "/{identifier}", fixture.party(), fixture.identifier()), 400, "if-match-duplicated");
        assertEquals(List.of(1L, 1L, 1L), counts(fixture));
    }

    @Test
    void absentWrongOwnerAndCrossTenantTargetsAreConcealedBeforeBusinessEvaluation() throws Exception {
        var fixture = create(PartyType.NATURAL_PERSON);
        String body = "{\"issuerCode\":null}";
        error(request(fixture).header("If-Match", "0").body(body).patch(BASE + "/{identifier}", UUID.randomUUID(), fixture.identifier()), 404, "party-not-found");
        error(request(fixture).header("If-Match", "0").body(body).patch(BASE + "/{identifier}", fixture.party(), UUID.randomUUID()), 404, "identifier-not-found");
        var other = create(PartyType.LEGAL_ENTITY);
        error(request(other).header("If-Match", "0").body(body).patch(BASE + "/{identifier}", other.party(), fixture.identifier()), 404, "identifier-not-found");
        assertEquals(List.of(1L, 1L, 1L), counts(fixture)); assertEquals(List.of(1L, 1L, 1L), counts(other));
    }

    private JsonNode success(Response response, int status) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        var body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(status, body); return body.path("data");
    }
    private void error(Response response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        PartyIdentifierContractAssertions.assertErrorResponse(status, code, json.readTree(response.asString()));
    }
    private static Response patch(Fixture fixture, String version, String body) { return request(fixture).header("If-Match", version).body(body).patch(BASE + "/{identifier}", fixture.party(), fixture.identifier()); }
    private static Response post(Fixture fixture) { return request(fixture).header("Idempotency-Key", fixture.key()).body(fixture.body()).post(BASE, fixture.party()); }
    private static RequestSpecification request(Fixture fixture) { return given().contentType("application/json").header("Tenant-Id", fixture.tenant().toString()).header("User-Id", USER).header("Process-Id", PROCESS); }

    private Fixture create(PartyType type) throws Exception {
        UUID party = UUID.randomUUID(); UUID tenant = UUID.randomUUID();
        var instant = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().minusSeconds(60));
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO parties(id, tenant_id, type, display_name, record_status, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, cast(? AS party_type), 'Metadata owner', 'DRAFT', 7, ?, 'fixture-creator', ?, 'fixture-creator')
                """)) {
            statement.setObject(1, party); statement.setObject(2, tenant); statement.setString(3, type.name()); statement.setTimestamp(4, instant); statement.setTimestamp(5, instant); assertEquals(1, statement.executeUpdate());
        }
        seedDetails(party, type, instant);
        String code = type == PartyType.NATURAL_PERSON ? IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE : IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE;
        String body = json.writeValueAsString(Map.of("identifierSchemeCode", code, "value", "AB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "issuerCode", "FIXTURE_AUTHORITY", "issuedOn", DATE.minusYears(1).toString(), "expiresOn", DATE.plusDays(10).toString(), "isPrimary", true));
        String key = "create-" + UUID.randomUUID();
        var seed = new Fixture(party, tenant, UUID.randomUUID(), key, body, NullNode.getInstance());
        JsonNode original = success(post(seed), 201);
        return new Fixture(party, tenant, UUID.fromString(original.path("identifierId").textValue()), key, body, original);
    }

    private void seedDetails(UUID party, PartyType type, Timestamp instant) throws SQLException {
        String sql = type == PartyType.NATURAL_PERSON ? """
                INSERT INTO natural_person_details(party_id, given_names, family_names, birth_date, created_at, created_by, updated_at, updated_by)
                VALUES (?, 'Given', 'Family', ?, ?, 'fixture-creator', ?, 'fixture-creator')
                """ : """
                INSERT INTO legal_entity_details(party_id, legal_name, incorporation_country_code, incorporated_on, created_at, created_by, updated_at, updated_by)
                VALUES (?, 'Registry Company', 'EC', ?, ?, 'fixture-creator', ?, 'fixture-creator')
                """;
        try (var connection = database.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, party); statement.setObject(2, LocalDate.of(1990, Month.JANUARY, 1)); statement.setTimestamp(3, instant); statement.setTimestamp(4, instant);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void state(Fixture fixture, PartyIdentifierStatus state, long version, LocalDate expiration) throws SQLException {
        var instant = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant());
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                UPDATE party_identifiers SET status = cast(? AS party_identifier_status), version = ?, expires_on = ?,
                    verified_at = ?, verified_by = ?, is_primary = ? WHERE id = ?
                """)) {
            boolean verified = state == PartyIdentifierStatus.VERIFIED || state == PartyIdentifierStatus.REVOKED;
            statement.setString(1, state.name()); statement.setLong(2, version); statement.setObject(3, expiration);
            statement.setTimestamp(4, verified ? instant : null); statement.setString(5, verified ? "fixture-verifier" : null);
            statement.setBoolean(6, state == PartyIdentifierStatus.PENDING_VERIFICATION || state == PartyIdentifierStatus.VERIFIED); statement.setObject(7, fixture.identifier());
            assertEquals(1, statement.executeUpdate());
        }
    }
    private JsonNode stored(Fixture fixture) throws Exception { return json.readTree(snapshot("SELECT cast(to_jsonb(identifier) AS text) FROM party_identifiers identifier WHERE id = ?", fixture.identifier())); }
    private String parent(Fixture fixture) throws SQLException {
        return snapshot("""
                SELECT cast(jsonb_build_object('party', to_jsonb(party), 'natural', to_jsonb(natural_details), 'legal', to_jsonb(legal_details)) AS text)
                FROM parties party LEFT JOIN natural_person_details natural_details ON natural_details.party_id = party.id
                LEFT JOIN legal_entity_details legal_details ON legal_details.party_id = party.id WHERE party.id = ?
                """, fixture.party());
    }
    private String snapshot(String sql, UUID id) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id); try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getString(1); }
        }
    }
    private List<Long> counts(Fixture fixture) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                SELECT (SELECT count(*) FROM party_identifiers WHERE tenant_id = ?),
                       (SELECT count(*) FROM party_identifier_idempotency_records WHERE tenant_id = ?),
                       (SELECT count(*) FROM party_outbox_events WHERE tenant_id = ?)
                """)) {
            statement.setObject(1, fixture.tenant()); statement.setObject(2, fixture.tenant()); statement.setObject(3, fixture.tenant());
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return List.of(rows.getLong(1), rows.getLong(2), rows.getLong(3)); }
        }
    }
    private static void assertProtectedHistory(JsonNode before, JsonNode after) {
        var retainedBefore = (ObjectNode) before.deepCopy(); var retainedAfter = (ObjectNode) after.deepCopy();
        List<String> mutable = List.of("issuer_code", "issued_on", "expires_on", "version", "updated_at", "updated_by");
        retainedBefore.remove(mutable); retainedAfter.remove(mutable); assertEquals(retainedBefore, retainedAfter);
    }

    /** Retains a real original creation request/result for current-row, event and historical-replay assertions. */
    private record Fixture(UUID party, UUID tenant, UUID identifier, String key, String body, JsonNode original) { }
    /** Selects only real fixed-clock creation/correction workflows and stored-only event intent for this HTTP suite. */
    public static class PatchProfile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() { return Map.of("quarkus.rabbitmq.devservices.enabled", "false", "party-registry.outbox.mode", "stored-only"); }
        @Override public Set<Class<?>> getEnabledAlternatives() { return Set.of(FixedPartyIdentifierRegistrationProducer.class, FixedPartyIdentifierPatchProducer.class); }
    }
}
