package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierLifecycleProducer;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises actual lifecycle routes, closed transitions, ordered syntax and durable private withdrawal audit. */
@QuarkusTest
@TestProfile(PartyIdentifierLifecycleResourceContractTest.LifecycleProfile.class)
@Timeout(120)
class PartyIdentifierLifecycleResourceContractTest {
    private static final String BASE = "/v1/parties/{party}/identifiers";
    private static final String ACTION = BASE + "/{identifier}/{action}";
    private static final String USER = "lifecycle-http-operator";
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final LocalDate DATE = FixedPartyIdentifierRegistrationProducer.DATE;
    private static final List<String> ACTIONS = List.of("verify", "reject", "revoke");
    @Inject AgroalDataSource database;
    @Inject ObjectMapper json;

    @Test
    void fullMatrixForBothTypesPreservesProtectedHistoryAndParentWithExactEffects() throws Exception {
        for (PartyType type : PartyType.values()) {
            for (PartyIdentifierStatus state : PartyIdentifierStatus.values()) {
                for (String action : ACTIONS) {
                    var fixture = create(type); state(fixture, state, 3, DATE);
                    JsonNode before = stored(fixture); String parent = snapshot("SELECT cast(to_jsonb(p) AS text) FROM parties p WHERE id = ?", fixture.party());
                    boolean allowed = state == PartyIdentifierStatus.PENDING_VERIFICATION && !action.equals("revoke")
                            || state == PartyIdentifierStatus.VERIFIED && action.equals("revoke");
                    Response response = action(fixture, action, "3", "");
                    if (allowed) {
                        JsonNode result = success(response, 200);
                        assertEquals(resulting(action), result.path("status").textValue()); assertEquals(4, result.path("version").longValue());
                        assertEquals(action.equals("verify"), result.path("isPrimary").booleanValue());
                        assertRetained(before, stored(fixture)); assertEquals(List.of(1L, action.equals("verify") ? 0L : 1L, 0L), effects(fixture));
                    } else {
                        error(response, 409, "invalid-identifier-lifecycle"); assertEquals(before, stored(fixture)); assertEquals(List.of(1L, 0L, 0L), effects(fixture));
                    }
                    assertEquals(parent, snapshot("SELECT cast(to_jsonb(p) AS text) FROM parties p WHERE id = ?", fixture.party()));
                }
            }
        }
    }

    @Test
    void verificationUsesAcceptedActorAndOneMicrosecondInstantAndRepeatConflicts() throws Exception {
        var fixture = create(PartyType.NATURAL_PERSON);
        JsonNode result = success(action(fixture, "verify", "0", ""), 200);
        assertEquals(USER, result.path("verifiedBy").textValue());
        assertEquals(FixedPartyIdentifierLifecycleProducer.CLOCK.instant().truncatedTo(ChronoUnit.MICROS).toString(), result.path("verifiedAt").textValue());
        assertEquals(result.path("verifiedAt"), result.path("updatedAt"));
        JsonNode before = stored(fixture);
        error(action(fixture, "verify", "1", ""), 409, "invalid-identifier-lifecycle"); assertEquals(before, stored(fixture));
    }

    @Test
    void versionStateExhaustionAndExpirationHaveTheirDeclaredPrecedence() throws Exception {
        var fixture = create(PartyType.LEGAL_ENTITY); state(fixture, PartyIdentifierStatus.REVOKED, Long.MAX_VALUE, DATE.minusDays(1));
        error(action(fixture, "verify", "7", ""), 412, "expected-version-mismatch");
        error(action(fixture, "verify", Long.toString(Long.MAX_VALUE), ""), 409, "invalid-identifier-lifecycle");
        state(fixture, PartyIdentifierStatus.PENDING_VERIFICATION, Long.MAX_VALUE, DATE.minusDays(1));
        error(action(fixture, "verify", Long.toString(Long.MAX_VALUE), ""), 409, "identifier-version-exhausted");
        state(fixture, PartyIdentifierStatus.PENDING_VERIFICATION, 3, DATE.minusDays(1)); JsonNode before = stored(fixture);
        error(action(fixture, "verify", "3", ""), 422, "identifier-validation-failure"); assertEquals(before, stored(fixture));
        state(fixture, PartyIdentifierStatus.PENDING_VERIFICATION, 3, null);
        success(action(fixture, "verify", "3", ""), 200);
        assertEquals(List.of(1L, 0L, 0L), effects(fixture));
    }

    @Test
    void withdrawalsRetainVerificationAndExactBinaryReasonsWithEventsDisabled() throws Exception {
        for (String action : List.of("reject", "revoke")) {
            var fixture = create(PartyType.NATURAL_PERSON);
            state(fixture, action.equals("reject") ? PartyIdentifierStatus.PENDING_VERIFICATION : PartyIdentifierStatus.VERIFIED, 3, DATE.minusDays(1));
            JsonNode before = stored(fixture);
            String detail = " \u0000\n😀 ";
            JsonNode result = success(action(fixture, action, "3", json.writeValueAsString(Map.of("reasonCode", "", "reasonDetail", detail))), 200);
            assertFalse(result.has("reasonCode")); assertFalse(result.has("reasonDetail")); assertFalse(result.path("isPrimary").booleanValue());
            assertEquals(before.path("verified_at"), stored(fixture).path("verified_at")); assertEquals(before.path("verified_by"), stored(fixture).path("verified_by"));
            assertAudit(fixture, action, detail); assertEquals(List.of(1L, 1L, 0L), effects(fixture));
        }
    }

    @Test
    void strictBodySyntaxPrecedesSelectorsVersionsAndOptionalKeys() throws Exception {
        var fixture = create(PartyType.NATURAL_PERSON);
        for (String body : List.of("{}", "null", " ", "\n")) {
            error(request(fixture).body(body).post(ACTION, "bad-party", "bad-identifier", "verify"), 400, "bad-request");
        }
        for (String body : List.of("null", " ", "[]", "{\"reasonCode\":null}", "{\"reasonCode\":42}", "{\"unknown\":true}",
                "{\"reasonCode\":\"a\",\"reasonCode\":\"b\"}", "{} {}", "{\"reasonDetail\":\"" + "😀".repeat(1001) + "\"}")) {
            error(request(fixture).body(body).post(ACTION, "bad-party", "bad-identifier", "reject"), 400, "bad-request");
        }
        error(request(fixture).body("{}").post(ACTION, "bad-party", "bad-identifier", "reject"), 400, "party-id-invalid");
        error(request(fixture).body("{}").post(ACTION, fixture.party(), "bad-identifier", "reject"), 400, "bad-request");
        error(request(fixture).body("{}").post(ACTION, fixture.party(), fixture.identifier(), "reject"), 400, "if-match-required");
        error(request(fixture).header("If-Match", "0", "0").post(ACTION, fixture.party(), fixture.identifier(), "reject"), 400, "if-match-duplicated");
        for (String version : List.of("01", "\"0\"", "*", "-1")) { error(action(fixture, "reject", version, ""), 400, "if-match-invalid"); }
        error(action(fixture, "reject", "9223372036854775808", ""), 400, "if-match-out-of-range");
        error(request(fixture).header("If-Match", "0").header("Idempotency-Key", "").post(ACTION, fixture.party(), fixture.identifier(), "reject"), 400, "idempotency-key-blank");
        error(request(fixture).header("If-Match", "0").header("Idempotency-Key", "a", "b").post(ACTION, fixture.party(), fixture.identifier(), "reject"), 400, "idempotency-key-duplicated");
        error(request(fixture).header("If-Match", "0").header("Idempotency-Key", "a".repeat(129)).post(ACTION, fixture.party(), fixture.identifier(), "reject"), 400, "idempotency-key-too-long");
        assertEquals(List.of(1L, 0L, 0L), effects(fixture));
    }

    @Test
    void ownedAbsenceAndWrongOwnerAreConcealedBeforeVersionOrLifecycle() throws Exception {
        var fixture = create(PartyType.NATURAL_PERSON); var other = create(PartyType.LEGAL_ENTITY);
        error(request(fixture).header("If-Match", "7").post(ACTION, UUID.randomUUID(), fixture.identifier(), "verify"), 404, "party-not-found");
        error(request(fixture).header("If-Match", "7").post(ACTION, fixture.party(), UUID.randomUUID(), "verify"), 404, "identifier-not-found");
        error(request(other).header("If-Match", "7").post(ACTION, other.party(), fixture.identifier(), "verify"), 404, "identifier-not-found");
        assertEquals(List.of(1L, 0L, 0L), effects(fixture)); assertEquals(List.of(1L, 0L, 0L), effects(other));
    }

    @Test
    void provisionalConflictPreservesBothRowsAndNonprimaryVerificationStillWorks() throws Exception {
        var fixture = create(PartyType.NATURAL_PERSON); var primary = additional(fixture, true);
        state(primary, PartyIdentifierStatus.VERIFIED, 3, DATE.minusDays(1));
        JsonNode before = stored(fixture); JsonNode former = stored(primary);
        error(action(fixture, "verify", "0", ""), 409, "primary-identifier-conflict");
        assertEquals(before, stored(fixture)); assertEquals(former, stored(primary));
        var nonprimary = additional(fixture, false); success(action(nonprimary, "verify", "0", ""), 200);
        assertEquals(former, stored(primary)); assertEquals(List.of(3L, 0L, 0L), effects(fixture));
    }

    private JsonNode success(Response response, int status) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        JsonNode body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(status, body); return body.path("data");
    }
    private void error(Response response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        PartyIdentifierContractAssertions.assertErrorResponse(status, code, json.readTree(response.asString()));
    }
    private static Response action(Fixture fixture, String action, String version, String body) {
        return request(fixture).header("If-Match", version).body(body).post(ACTION, fixture.party(), fixture.identifier(), action);
    }
    private static RequestSpecification request(Fixture fixture) {
        return given().contentType("application/json").header("Tenant-Id", fixture.tenant().toString()).header("User-Id", USER).header("Process-Id", PROCESS);
    }
    private Fixture create(PartyType type) throws Exception {
        UUID party = UUID.randomUUID(); UUID tenant = UUID.randomUUID();
        var instant = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().minusSeconds(60));
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO parties(id, tenant_id, type, display_name, record_status, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, cast(? AS party_type), 'Lifecycle owner', 'ACTIVE', 7, ?, 'fixture-creator', ?, 'fixture-creator')
                """)) {
            statement.setObject(1, party); statement.setObject(2, tenant); statement.setString(3, type.name()); statement.setTimestamp(4, instant); statement.setTimestamp(5, instant);
            assertEquals(1, statement.executeUpdate());
        }
        return additional(new Fixture(party, tenant, UUID.randomUUID(), type), true);
    }
    private Fixture additional(Fixture owner, boolean primary) throws Exception {
        String scheme = owner.type() == PartyType.NATURAL_PERSON ? IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE : IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE;
        String body = json.writeValueAsString(Map.of("identifierSchemeCode", scheme, "value", "AB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "issuerCode", "FIXTURE_AUTHORITY", "issuedOn", DATE.minusYears(1).toString(), "expiresOn", DATE.toString(), "isPrimary", primary));
        JsonNode result = success(request(owner).header("Idempotency-Key", UUID.randomUUID().toString()).body(body).post(BASE, owner.party()), 201);
        return new Fixture(owner.party(), owner.tenant(), UUID.fromString(result.path("identifierId").textValue()), owner.type());
    }
    private void state(Fixture fixture, PartyIdentifierStatus state, long version, LocalDate expiration) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                UPDATE party_identifiers SET status = cast(? AS party_identifier_status), version = ?, expires_on = ?,
                    verified_at = ?, verified_by = ?, is_primary = ? WHERE id = ?
                """)) {
            boolean verified = state == PartyIdentifierStatus.VERIFIED || state == PartyIdentifierStatus.REVOKED;
            statement.setString(1, state.name()); statement.setLong(2, version); statement.setObject(3, expiration);
            statement.setTimestamp(4, verified ? Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant()) : null);
            statement.setString(5, verified ? "original-verifier" : null);
            statement.setBoolean(6, state == PartyIdentifierStatus.PENDING_VERIFICATION || state == PartyIdentifierStatus.VERIFIED);
            statement.setObject(7, fixture.identifier()); assertEquals(1, statement.executeUpdate());
        }
    }
    private JsonNode stored(Fixture fixture) throws Exception { return json.readTree(snapshot("SELECT cast(to_jsonb(i) AS text) FROM party_identifiers i WHERE id = ?", fixture.identifier())); }
    private String snapshot(String sql, UUID id) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id); try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getString(1); }
        }
    }
    private List<Long> effects(Fixture fixture) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                SELECT (SELECT count(*) FROM party_identifier_idempotency_records WHERE tenant_id = ?),
                       (SELECT count(*) FROM party_identifier_action_audit WHERE tenant_id = ?),
                       (SELECT count(*) FROM party_outbox_events WHERE tenant_id = ?)
                """)) {
            statement.setObject(1, fixture.tenant()); statement.setObject(2, fixture.tenant()); statement.setObject(3, fixture.tenant());
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return List.of(rows.getLong(1), rows.getLong(2), rows.getLong(3)); }
        }
    }
    private void assertAudit(Fixture fixture, String action, String detail) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                SELECT action, resulting_version, actor, occurred_at, reason_code, reason_detail
                FROM party_identifier_action_audit WHERE identifier_id = ?
                """)) {
            statement.setObject(1, fixture.identifier());
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next()); assertEquals(action.toUpperCase(java.util.Locale.ROOT), rows.getString(1)); assertEquals(4, rows.getLong(2));
                assertEquals(USER, rows.getString(3)); assertEquals(FixedPartyIdentifierLifecycleProducer.CLOCK.instant().truncatedTo(ChronoUnit.MICROS), rows.getTimestamp(4).toInstant());
                assertEquals(0, rows.getBytes(5).length); assertEquals(detail, decode(rows.getBytes(6))); assertFalse(rows.next());
            }
        }
    }
    private static String decode(byte[] bytes) {
        var value = new StringBuilder(bytes.length / 2);
        for (int index = 0; index < bytes.length; index += 2) { value.append((char) (((bytes[index] & 255) << 8) | (bytes[index + 1] & 255))); }
        return value.toString();
    }
    private static String resulting(String action) { return switch (action) { case "verify" -> "VERIFIED"; case "reject" -> "REJECTED"; default -> "REVOKED"; }; }
    private static void assertRetained(JsonNode before, JsonNode after) {
        ObjectNode left = before.deepCopy(); ObjectNode right = after.deepCopy();
        var mutable = List.of("status", "is_primary", "version", "updated_at", "updated_by", "verified_at", "verified_by");
        left.remove(mutable); right.remove(mutable); assertEquals(left, right);
    }
    /** Identifies an isolated real owned identifier without retaining submitted values or reason data. */
    private record Fixture(UUID party, UUID tenant, UUID identifier, PartyType type) { }
    /** Uses actual fixed-clock workflows with events disabled to prove reason audit is independent of messaging. */
    public static class LifecycleProfile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() { return Map.of("quarkus.rabbitmq.devservices.enabled", "false", "party-registry.outbox.mode", "disabled"); }
        @Override public Set<Class<?>> getEnabledAlternatives() { return Set.of(FixedPartyIdentifierRegistrationProducer.class, FixedPartyIdentifierLifecycleProducer.class); }
    }
}
