package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierPrimaryProducer;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierRegistrationProducer;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/** Exercises actual primary selection, atomic two-record preservation, independent scopes and historical recovery. */
@QuarkusTest
@TestProfile(PartyIdentifierPrimaryResourceContractTest.PrimaryProfile.class)
@Timeout(120)
class PartyIdentifierPrimaryResourceContractTest {
    private static final String BASE = "/v1/parties/{party}/identifiers";
    private static final String ACTION = BASE + "/{identifier}/set-primary";
    private static final String USER = "primary-http-operator";
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final LocalDate DATE = FixedPartyIdentifierRegistrationProducer.DATE;
    @Inject AgroalDataSource database;
    @Inject ObjectMapper json;

    @Test
    void replacementWithoutConsentPreservesBothRecordsAndDoesNotConsumeKeyForBothTypes() throws Exception {
        for (PartyType type : PartyType.values()) {
            var target = create(type); var former = additional(target, target.scheme());
            state(target, PartyIdentifierStatus.VERIFIED, false, 3, DATE);
            state(former, PartyIdentifierStatus.VERIFIED, true, 9, DATE.minusDays(1));
            JsonNode beforeTarget = stored(target); JsonNode beforeFormer = stored(former);
            error(select(target, "3", "failed-key", "{}"), 409, "primary-identifier-conflict");
            assertEquals(beforeTarget, stored(target)); assertEquals(beforeFormer, stored(former)); assertEquals(List.of(2L, 0L, 2L), effects(target));
            success(select(target, "3", "failed-key", "{\"replaceCurrentPrimary\":true}"), PROCESS);
            assertEquals(List.of(3L, 0L, 4L), effects(target));
        }
    }

    @Test
    void explicitReplacementOfPastDatePrimaryPreservesBothRecordsAndParentForBothTypes() throws Exception {
        for (PartyType type : PartyType.values()) {
            var target = create(type); var former = additional(target, target.scheme());
            state(target, PartyIdentifierStatus.VERIFIED, false, 3, DATE);
            state(former, PartyIdentifierStatus.VERIFIED, true, 9, DATE.minusDays(1));
            JsonNode beforeTarget = stored(target); JsonNode beforeFormer = stored(former);
            JsonNode parent = parent(target); JsonNode scheme = scheme(target);
            success(select(target, "3", "replacement-key", "{\"replaceCurrentPrimary\":true}"), PROCESS);
            assertRetained(beforeTarget, stored(target)); assertRetained(beforeFormer, stored(former));
            assertEquals(parent, parent(target)); assertEquals(scheme, scheme(target));
        }
    }

    @Test
    void explicitReplacementAdvancesBothVersionsWithOneActorInstantAndCompleteEffectsForBothTypes() throws Exception {
        for (PartyType type : PartyType.values()) {
            var target = create(type); var former = additional(target, target.scheme());
            state(target, PartyIdentifierStatus.VERIFIED, false, 3, DATE);
            state(former, PartyIdentifierStatus.VERIFIED, true, 9, DATE.minusDays(1));
            JsonNode result = success(select(target, "3", "failed-key", "{\"replaceCurrentPrimary\":true}"), PROCESS);
            assertTrue(result.path("isPrimary").booleanValue()); assertEquals(4, result.path("version").longValue());
            JsonNode targetAfter = stored(target); JsonNode formerAfter = stored(former);
            assertFalse(formerAfter.path("is_primary").booleanValue()); assertEquals(10, formerAfter.path("version").longValue());
            assertEquals(targetAfter.path("updated_at"), formerAfter.path("updated_at"));
            assertEquals(USER, formerAfter.path("updated_by").textValue());
            assertEquals(FixedPartyIdentifierPrimaryProducer.CLOCK.instant().truncatedTo(ChronoUnit.MICROS).toString(), result.path("updatedAt").textValue());
            assertEquals(List.of(3L, 0L, 4L), effects(target));
        }
    }

    @Test
    void historicalReplayAfterLaterReplacementTouchesNeitherCurrentRecordAndEchoesFreshProcess() throws Exception {
        var target = create(PartyType.NATURAL_PERSON); var former = additional(target, target.scheme());
        state(target, PartyIdentifierStatus.VERIFIED, false, 3, null); state(former, PartyIdentifierStatus.VERIFIED, true, 9, DATE);
        String body = "{\"replaceCurrentPrimary\":true}";
        JsonNode original = success(select(target, "3", "original-key", body), PROCESS);
        success(select(former, "10", "later-key", body), PROCESS);
        JsonNode targetNow = stored(target); JsonNode formerNow = stored(former); List<Long> effects = effects(target);
        String retryProcess = UUID.randomUUID().toString();
        Response retry = request(target, "retry-actor", retryProcess).header("If-Match", "3").header("Idempotency-Key", "original-key").body(body)
                .post(ACTION, target.party(), target.identifier());
        assertEquals(original, success(retry, retryProcess)); assertFalse(targetNow.path("is_primary").booleanValue()); assertTrue(formerNow.path("is_primary").booleanValue());
        assertEquals(targetNow, stored(target)); assertEquals(formerNow, stored(former)); assertEquals(effects, effects(target));
        error(select(target, "3", "original-key", "{}"), 409, "idempotency-key-conflict");
        error(select(target, "5", "original-key", body), 409, "idempotency-key-conflict");
        assertEquals(List.of(4L, 0L, 6L), effects(target));
    }

    @Test
    void distinctSchemesKeepIndependentPrimariesAndProvisionalFlagsAreNotDisplaced() throws Exception {
        var first = create(PartyType.LEGAL_ENTITY); var otherScheme = additional(first, IdentifierSchemeTestFixtures.BOTH_EXPIRING_CODE);
        var provisional = additional(first, first.scheme());
        state(first, PartyIdentifierStatus.VERIFIED, false, 3, DATE); state(otherScheme, PartyIdentifierStatus.VERIFIED, false, 3, null);
        state(provisional, PartyIdentifierStatus.PENDING_VERIFICATION, true, 7, DATE);
        JsonNode pending = stored(provisional); success(select(first, "3", null, ""), PROCESS); JsonNode selectedFirst = stored(first);
        success(select(otherScheme, "3", null, "{}"), PROCESS);
        assertEquals(selectedFirst, stored(first)); assertEquals(pending, stored(provisional));
        assertTrue(stored(first).path("is_primary").booleanValue()); assertTrue(stored(otherScheme).path("is_primary").booleanValue());
        assertEquals(List.of(3L, 0L, 5L), effects(first));
    }

    @Test
    void alreadyPrimaryAdvancesOnceAndAbsentEmptyOrFalseBodiesReplayOneResult() throws Exception {
        var target = create(PartyType.NATURAL_PERSON); state(target, PartyIdentifierStatus.VERIFIED, true, 3, DATE);
        JsonNode before = stored(target); JsonNode original = success(select(target, "3", "default-key", ""), PROCESS);
        assertEquals(4, original.path("version").longValue()); assertRetained(before, stored(target));
        assertEquals(original, success(select(target, "3", "default-key", "{}"), PROCESS));
        assertEquals(original, success(select(target, "3", "default-key", "{\"replaceCurrentPrimary\":false}"), PROCESS));
        assertEquals(List.of(2L, 0L, 2L), effects(target));
        error(select(target, "3", "default-key", "{\"replaceCurrentPrimary\":true}"), 409, "idempotency-key-conflict");
        assertEquals(List.of(2L, 0L, 2L), effects(target));
    }

    @Test
    void stateVersionExhaustionAndDateFailuresPreserveEveryPriorFieldAndEffect() throws Exception {
        for (var status : List.of(PartyIdentifierStatus.PENDING_VERIFICATION, PartyIdentifierStatus.REJECTED, PartyIdentifierStatus.REVOKED, PartyIdentifierStatus.EXPIRED)) {
            var target = create(PartyType.NATURAL_PERSON); state(target, status, false, Long.MAX_VALUE, DATE.minusDays(1));
            JsonNode before = stored(target);
            error(select(target, "7", null, ""), 412, "expected-version-mismatch");
            error(select(target, Long.toString(Long.MAX_VALUE), null, ""), 409, "invalid-identifier-lifecycle");
            assertEquals(before, stored(target)); assertEquals(List.of(1L, 0L, 1L), effects(target));
        }
        var target = create(PartyType.LEGAL_ENTITY); state(target, PartyIdentifierStatus.VERIFIED, false, Long.MAX_VALUE, DATE.minusDays(1));
        error(select(target, Long.toString(Long.MAX_VALUE), null, ""), 409, "identifier-version-exhausted");
        state(target, PartyIdentifierStatus.VERIFIED, false, 3, DATE.minusDays(1)); JsonNode before = stored(target);
        error(select(target, "3", null, ""), 422, "identifier-validation-failure"); assertEquals(before, stored(target));
        assertEquals(List.of(1L, 0L, 1L), effects(target));
    }

    @Test
    void exhaustedFormerPrimaryPreventsBothWritesAndKeyConsumption() throws Exception {
        var target = create(PartyType.NATURAL_PERSON); var former = additional(target, target.scheme());
        state(target, PartyIdentifierStatus.VERIFIED, false, 3, DATE); state(former, PartyIdentifierStatus.VERIFIED, true, Long.MAX_VALUE, DATE.minusDays(1));
        JsonNode beforeTarget = stored(target); JsonNode beforeFormer = stored(former);
        error(select(target, "3", "unused-key", "{\"replaceCurrentPrimary\":true}"), 409, "identifier-version-exhausted");
        assertEquals(beforeTarget, stored(target)); assertEquals(beforeFormer, stored(former)); assertEquals(List.of(2L, 0L, 2L), effects(target));
    }

    @Test
    void strictOptionalBodyPrecedesSelectorsVersionAndOptionalKey() throws Exception {
        var target = create(PartyType.LEGAL_ENTITY);
        for (String body : List.of("null", " ", "[]", "true", "{\"replaceCurrentPrimary\":null}", "{\"replaceCurrentPrimary\":\"true\"}",
                "{\"replaceCurrentPrimary\":true,\"replaceCurrentPrimary\":false}", "{\"replaceCurrentPrimary\":1}", "{\"extra\":false}", "{} {}")) {
            error(request(target).body(body).post(ACTION, "bad-party", "bad-identifier"), 400, "bad-request");
        }
        error(request(target).body("{}").post(ACTION, "bad-party", "bad-identifier"), 400, "party-id-invalid");
        error(request(target).body("{}").post(ACTION, target.party(), "bad-identifier"), 400, "bad-request");
        error(request(target).body("{}").post(ACTION, target.party(), target.identifier()), 400, "if-match-required");
        error(select(target, "01", null, "{}"), 400, "if-match-invalid");
        error(select(target, "9223372036854775808", null, "{}"), 400, "if-match-out-of-range");
        error(request(target).header("If-Match", "0", "0").body("{}").post(ACTION, target.party(), target.identifier()), 400, "if-match-duplicated");
        error(select(target, "0", "", "{}"), 400, "idempotency-key-blank");
        error(select(target, "0", "a".repeat(129), "{}"), 400, "idempotency-key-too-long");
        assertEquals(List.of(1L, 0L, 1L), effects(target));
    }

    @Test
    void ownershipAndIdentifierAbsencePrecedeStaleVersion() throws Exception {
        var target = create(PartyType.NATURAL_PERSON); var other = create(PartyType.LEGAL_ENTITY);
        error(request(target).header("If-Match", "7").post(ACTION, UUID.randomUUID(), target.identifier()), 404, "party-not-found");
        error(request(target).header("If-Match", "7").post(ACTION, target.party(), UUID.randomUUID()), 404, "identifier-not-found");
        error(request(other).header("If-Match", "7").post(ACTION, other.party(), target.identifier()), 404, "identifier-not-found");
        assertEquals(List.of(1L, 0L, 1L), effects(target)); assertEquals(List.of(1L, 0L, 1L), effects(other));
    }

    private JsonNode success(Response response, String process) throws Exception {
        assertEquals(200, response.statusCode()); assertEquals(process, response.header("Process-Id"));
        JsonNode body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(200, body); return body.path("data");
    }
    private void error(Response response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        PartyIdentifierContractAssertions.assertErrorResponse(status, code, json.readTree(response.asString()));
    }
    private static Response select(Fixture target, String version, String key, String body) {
        var request = request(target).header("If-Match", version).body(body);
        if (key != null) { request.header("Idempotency-Key", key); }
        return request.post(ACTION, target.party(), target.identifier());
    }
    private static RequestSpecification request(Fixture target) { return request(target, USER, PROCESS); }
    private static RequestSpecification request(Fixture target, String user, String process) {
        return given().contentType("application/json").header("Tenant-Id", target.tenant().toString()).header("User-Id", user).header("Process-Id", process);
    }
    private Fixture create(PartyType type) throws Exception {
        UUID party = UUID.randomUUID(); UUID tenant = UUID.randomUUID();
        var instant = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().minusSeconds(60));
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO parties(id, tenant_id, type, display_name, record_status, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, cast(? AS party_type), 'Primary owner', 'ACTIVE', 7, ?, 'fixture-creator', ?, 'fixture-creator')
                """)) {
            statement.setObject(1, party); statement.setObject(2, tenant); statement.setString(3, type.name());
            statement.setTimestamp(4, instant); statement.setTimestamp(5, instant); assertEquals(1, statement.executeUpdate());
        }
        seedDetails(party, type, instant);
        String scheme = type == PartyType.NATURAL_PERSON ? IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE : IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE;
        return additional(new Fixture(party, tenant, UUID.randomUUID(), scheme), scheme);
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
            statement.setObject(1, party); statement.setObject(2, LocalDate.of(1990, Month.JANUARY, 1));
            statement.setTimestamp(3, instant); statement.setTimestamp(4, instant); assertEquals(1, statement.executeUpdate());
        }
    }
    private Fixture additional(Fixture owner, String scheme) throws Exception {
        String body = json.writeValueAsString(Map.of("identifierSchemeCode", scheme, "value", "AB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "issuerCode", "FIXTURE_AUTHORITY", "issuedOn", DATE.minusYears(1).toString(), "expiresOn", DATE.plusDays(10).toString(), "isPrimary", false));
        Response response = request(owner).header("Idempotency-Key", UUID.randomUUID().toString()).body(body).post(BASE, owner.party());
        assertEquals(201, response.statusCode()); JsonNode result = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(201, result);
        return new Fixture(owner.party(), owner.tenant(), UUID.fromString(result.path("data").path("identifierId").textValue()), scheme);
    }
    private void state(Fixture target, PartyIdentifierStatus state, boolean primary, long version, LocalDate expiration) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                UPDATE party_identifiers SET status = cast(? AS party_identifier_status), is_primary = ?, version = ?, expires_on = ?, verified_at = ?, verified_by = ? WHERE id = ?
                """)) {
            boolean verified = state == PartyIdentifierStatus.VERIFIED || state == PartyIdentifierStatus.REVOKED;
            statement.setString(1, state.name()); statement.setBoolean(2, primary); statement.setLong(3, version); statement.setObject(4, expiration);
            statement.setTimestamp(5, verified ? Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant()) : null);
            statement.setString(6, verified ? "original-verifier" : null); statement.setObject(7, target.identifier()); assertEquals(1, statement.executeUpdate());
        }
    }
    private JsonNode stored(Fixture target) throws Exception { return snapshot("SELECT cast(to_jsonb(i) AS text) FROM party_identifiers i WHERE id = ?", target.identifier()); }
    private JsonNode scheme(Fixture target) throws Exception { return snapshot("SELECT cast(to_jsonb(s) AS text) FROM identifier_schemes s JOIN party_identifiers i ON s.id = i.identifier_scheme_id WHERE i.id = ?", target.identifier()); }
    private JsonNode parent(Fixture target) throws Exception {
        return snapshot("""
                SELECT cast(jsonb_build_object('party', to_jsonb(p), 'natural', to_jsonb(n), 'legal', to_jsonb(l)) AS text)
                FROM parties p LEFT JOIN natural_person_details n ON n.party_id = p.id LEFT JOIN legal_entity_details l ON l.party_id = p.id WHERE p.id = ?
                """, target.party());
    }
    private JsonNode snapshot(String sql, UUID id) throws Exception {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id); try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return json.readTree(rows.getString(1)); }
        }
    }
    private List<Long> effects(Fixture target) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                SELECT (SELECT count(*) FROM party_identifier_idempotency_records WHERE tenant_id = ?),
                       (SELECT count(*) FROM party_identifier_action_audit WHERE tenant_id = ?),
                       (SELECT count(*) FROM party_outbox_events WHERE tenant_id = ?)
                """)) {
            statement.setObject(1, target.tenant()); statement.setObject(2, target.tenant()); statement.setObject(3, target.tenant());
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return List.of(rows.getLong(1), rows.getLong(2), rows.getLong(3)); }
        }
    }
    private static void assertRetained(JsonNode before, JsonNode after) {
        ObjectNode left = before.deepCopy(); ObjectNode right = after.deepCopy();
        var mutable = List.of("is_primary", "version", "updated_at", "updated_by"); left.remove(mutable); right.remove(mutable); assertEquals(left, right);
    }
    /** Identifies isolated real owned evidence and its scheme without exposing submitted protected values. */
    private record Fixture(UUID party, UUID tenant, UUID identifier, String scheme) { }
    /** Enables actual fixed-clock creation/selection with stored-only event intent for complete-outcome assertions. */
    public static class PrimaryProfile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() { return Map.of("quarkus.rabbitmq.devservices.enabled", "false", "party-registry.outbox.mode", "stored-only"); }
        @Override public Set<Class<?>> getEnabledAlternatives() { return Set.of(FixedPartyIdentifierRegistrationProducer.class, FixedPartyIdentifierPrimaryProducer.class); }
    }
}
