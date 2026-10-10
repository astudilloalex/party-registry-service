package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierLifecycleProducer;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierReplayPatchProducer;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierRegistrationProducer;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies real durable historical lifecycle replay, exact request identity and nonduplicated audit/event outcomes. */
@QuarkusTest
@TestProfile(PartyIdentifierLifecycleReplayContractTest.ReplayProfile.class)
@Timeout(120)
class PartyIdentifierLifecycleReplayContractTest {
    private static final String BASE = "/v1/parties/{party}/identifiers";
    private static final String ACTION = BASE + "/{identifier}/{action}";
    private static final String USER = "original-lifecycle-actor";
    private static final String PROCESS = UUID.randomUUID().toString();
    @Inject AgroalDataSource database;
    @Inject ObjectMapper json;

    @Test
    void originalVerificationReplaysAfterCorrectionRevocationAndPartyArchivalWithFreshCorrelation() throws Exception {
        for (PartyType type : PartyType.values()) {
            var fixture = create(type); String key = fixture.key();
            JsonNode verified = success(action(fixture, "verify", "0", key, ""), 200, PROCESS);
            success(request(fixture).header("If-Match", "1").body("{\"issuerCode\":\"corrected_authority\"}")
                    .patch(BASE + "/{identifier}", fixture.party(), fixture.identifier()), 200, PROCESS);
            JsonNode revoked = success(action(fixture, "revoke", "2", key, "{\"reasonCode\":\"withdrawn\"}"), 200, PROCESS);
            archive(fixture); JsonNode before = stored(fixture); JsonNode audit = audit(fixture);
            String retryProcess = UUID.randomUUID().toString();
            var retry = request(fixture, "retry-actor", retryProcess).header("If-Match", "0").header("Idempotency-Key", key)
                    .post(ACTION, fixture.party(), fixture.identifier(), "verify");
            assertEquals(verified, success(retry, 200, retryProcess)); assertEquals(USER, verified.path("verifiedBy").textValue());
            assertEquals("REVOKED", revoked.path("status").textValue()); assertEquals(before, stored(fixture)); assertEquals(audit, audit(fixture));
            assertEquals(List.of(3L, 1L, 4L), effects(fixture));
            assertEquals(fixture.original(), success(request(fixture).header("Idempotency-Key", key).body(fixture.body()).post(BASE, fixture.party()), 201, PROCESS));
            assertEquals(List.of(3L, 1L, 4L), effects(fixture));
        }
    }

    @Test
    void withdrawalDefaultBodiesReplayWithoutReevaluationOrDuplicateAuditAndEvents() throws Exception {
        for (String action : List.of("reject", "revoke")) {
            var fixture = create(PartyType.NATURAL_PERSON); String version = "0";
            if (action.equals("revoke")) { success(action(fixture, "verify", "0", null, ""), 200, PROCESS); version = "1"; }
            JsonNode original = success(action(fixture, action, version, fixture.key(), ""), 200, PROCESS);
            archive(fixture); JsonNode before = stored(fixture); JsonNode reasons = audit(fixture); List<Long> counts = effects(fixture);
            assertEquals(original, success(action(fixture, action, version, fixture.key(), "{}"), 200, PROCESS));
            String retryProcess = UUID.randomUUID().toString();
            var retry = request(fixture, "retry-withdrawer", retryProcess).header("If-Match", version).header("Idempotency-Key", fixture.key())
                    .body("{}").post(ACTION, fixture.party(), fixture.identifier(), action);
            assertEquals(original, success(retry, 200, retryProcess));
            assertEquals(before, stored(fixture)); assertEquals(reasons, audit(fixture)); assertEquals(counts, effects(fixture));
            error(action(fixture, action, version, fixture.key(), "{\"reasonCode\":\"\"}"), 409, "idempotency-key-conflict");
            assertEquals(counts, effects(fixture));
        }
    }

    @Test
    void exactReasonsIncludingControlsAndReorderedPropertiesReplayButChangedReasonsConflict() throws Exception {
        var fixture = create(PartyType.LEGAL_ENTITY);
        String detail = " \u0000\n😀 ";
        String body = json.writeValueAsString(Map.of("reasonCode", "MiXeD", "reasonDetail", detail));
        JsonNode original = success(action(fixture, "reject", "0", fixture.key(), body), 200, PROCESS);
        JsonNode stored = stored(fixture); JsonNode reasons = audit(fixture);
        String reordered = "{\"reasonDetail\":" + json.writeValueAsString(detail) + ",\"reasonCode\":\"MiXeD\"}";
        assertEquals(original, success(action(fixture, "reject", "0", fixture.key(), reordered), 200, PROCESS));
        for (String changed : List.of("{\"reasonCode\":\"mixed\"}", "{\"reasonCode\":\"MiXeD\"}",
                json.writeValueAsString(Map.of("reasonCode", "MiXeD", "reasonDetail", detail + " ")))) {
            error(action(fixture, "reject", "0", fixture.key(), changed), 409, "idempotency-key-conflict");
        }
        assertEquals(stored, stored(fixture)); assertEquals(reasons, audit(fixture)); assertEquals(List.of(2L, 1L, 2L), effects(fixture));
    }

    @Test
    void changedPartyIdentifierAndVersionConflictBeforeCurrentAbsenceOrState() throws Exception {
        var fixture = create(PartyType.NATURAL_PERSON);
        success(action(fixture, "verify", "0", fixture.key(), ""), 200, PROCESS);
        JsonNode before = stored(fixture);
        error(action(fixture, "verify", "1", fixture.key(), ""), 409, "idempotency-key-conflict");
        error(request(fixture).header("If-Match", "0").header("Idempotency-Key", fixture.key())
                .post(ACTION, UUID.randomUUID(), fixture.identifier(), "verify"), 409, "idempotency-key-conflict");
        error(request(fixture).header("If-Match", "0").header("Idempotency-Key", fixture.key())
                .post(ACTION, fixture.party(), UUID.randomUUID(), "verify"), 409, "idempotency-key-conflict");
        assertEquals(before, stored(fixture)); assertEquals(List.of(2L, 0L, 2L), effects(fixture));
    }

    @Test
    void sameKeyHasIndependentCreationActionAndTenantScopes() throws Exception {
        var first = create(PartyType.NATURAL_PERSON);
        JsonNode verified = success(action(first, "verify", "0", first.key(), ""), 200, PROCESS);
        success(action(first, "revoke", "1", first.key(), "{}"), 200, PROCESS);
        var second = create(PartyType.LEGAL_ENTITY);
        JsonNode independent = success(action(second, "verify", "0", first.key(), ""), 200, PROCESS);
        assertNotEquals(verified.path("identifierId"), independent.path("identifierId"));
        assertEquals(verified, success(action(first, "verify", "0", first.key(), ""), 200, PROCESS));
        assertEquals(List.of(3L, 1L, 3L), effects(first)); assertEquals(List.of(2L, 0L, 2L), effects(second));
    }

    @Test
    void syntaxStillPrecedesCompletedReplayAndFailedKeyRemainsReusable() throws Exception {
        var fixture = create(PartyType.LEGAL_ENTITY); String key = fixture.key();
        error(action(fixture, "revoke", "0", key, "{}"), 409, "invalid-identifier-lifecycle");
        assertEquals(List.of(1L, 0L, 1L), effects(fixture));
        success(action(fixture, "verify", "0", null, ""), 200, PROCESS);
        JsonNode original = success(action(fixture, "revoke", "1", key, "{}"), 200, PROCESS);
        JsonNode before = stored(fixture); JsonNode reasons = audit(fixture);
        error(action(fixture, "revoke", "1", key, "null"), 400, "bad-request");
        error(request(fixture).header("If-Match", "1").header("Idempotency-Key", key).body("{}")
                .post(ACTION, "bad-party", fixture.identifier(), "revoke"), 400, "party-id-invalid");
        error(action(fixture, "revoke", "01", key, "{}"), 400, "if-match-invalid");
        error(action(fixture, "revoke", "1", "", "{}"), 400, "idempotency-key-blank");
        assertEquals(original, success(action(fixture, "revoke", "1", key, ""), 200, PROCESS));
        assertEquals(before, stored(fixture)); assertEquals(reasons, audit(fixture)); assertEquals(List.of(2L, 1L, 3L), effects(fixture));
    }

    private JsonNode success(Response response, int status, String process) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(process, response.header("Process-Id"));
        JsonNode body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(status, body); return body.path("data");
    }
    private void error(Response response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        PartyIdentifierContractAssertions.assertErrorResponse(status, code, json.readTree(response.asString()));
    }
    private static Response action(Fixture fixture, String action, String version, String key, String body) {
        var request = request(fixture).header("If-Match", version).body(body);
        if (key != null) { request.header("Idempotency-Key", key); }
        return request.post(ACTION, fixture.party(), fixture.identifier(), action);
    }
    private static RequestSpecification request(Fixture fixture) { return request(fixture, USER, PROCESS); }
    private static RequestSpecification request(Fixture fixture, String user, String process) {
        return given().contentType("application/json").header("Tenant-Id", fixture.tenant().toString()).header("User-Id", user).header("Process-Id", process);
    }
    private Fixture create(PartyType type) throws Exception {
        UUID party = UUID.randomUUID(); UUID tenant = UUID.randomUUID();
        var instant = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().minusSeconds(60));
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO parties(id, tenant_id, type, display_name, record_status, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, cast(? AS party_type), 'Replay owner', 'ACTIVE', 7, ?, 'fixture-creator', ?, 'fixture-creator')
                """)) {
            statement.setObject(1, party); statement.setObject(2, tenant); statement.setString(3, type.name());
            statement.setTimestamp(4, instant); statement.setTimestamp(5, instant); assertEquals(1, statement.executeUpdate());
        }
        String scheme = type == PartyType.NATURAL_PERSON ? IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE : IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE;
        String body = json.writeValueAsString(Map.of("identifierSchemeCode", scheme, "value", "AB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "issuerCode", "ORIGINAL_AUTHORITY", "isPrimary", true));
        String key = "shared-" + UUID.randomUUID();
        var seed = new Fixture(party, tenant, UUID.randomUUID(), key, body, json.nullNode());
        JsonNode original = success(request(seed).header("Idempotency-Key", key).body(body).post(BASE, party), 201, PROCESS);
        return new Fixture(party, tenant, UUID.fromString(original.path("identifierId").textValue()), key, body, original);
    }
    private void archive(Fixture fixture) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("UPDATE parties SET record_status = 'ARCHIVED' WHERE id = ?")) {
            statement.setObject(1, fixture.party()); assertEquals(1, statement.executeUpdate());
        }
    }
    private JsonNode stored(Fixture fixture) throws Exception {
        return snapshot("SELECT cast(to_jsonb(i) AS text) FROM party_identifiers i WHERE id = ?", fixture.identifier());
    }
    private JsonNode audit(Fixture fixture) throws Exception {
        return snapshot("SELECT cast(coalesce(jsonb_agg(to_jsonb(a) ORDER BY resulting_version), '[]'::jsonb) AS text) FROM party_identifier_action_audit a WHERE identifier_id = ?", fixture.identifier());
    }
    private JsonNode snapshot(String sql, UUID id) throws Exception {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id); try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return json.readTree(rows.getString(1)); }
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
    /** Retains isolated scope and original creation data needed to prove cross-operation historical equivalence. */
    private record Fixture(UUID party, UUID tenant, UUID identifier, String key, String body, JsonNode original) { }
    /** Selects actual fixed-clock workflows and durable stored-only event intent without test fixtures in packaged builds. */
    public static class ReplayProfile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() { return Map.of("quarkus.rabbitmq.devservices.enabled", "false", "party-registry.outbox.mode", "stored-only"); }
        @Override public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FixedPartyIdentifierRegistrationProducer.class, FixedPartyIdentifierLifecycleProducer.class, FixedPartyIdentifierReplayPatchProducer.class);
        }
    }
}
