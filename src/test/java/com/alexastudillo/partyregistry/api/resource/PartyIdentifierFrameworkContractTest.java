package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.alexastudillo.partyregistry.infrastructure.persistence.PartyIdentifierFaultPort;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierLifecycleProducer;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierPrimaryProducer;
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
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins published global HTTP failure handling on actual identifier routes, including malformed input and corrupt historical completions. */
@QuarkusTest
@TestProfile(PartyIdentifierFrameworkContractTest.FrameworkProfile.class)
@Timeout(120)
class PartyIdentifierFrameworkContractTest {
    private static final String BASE = "/v1/parties/{party}/identifiers";
    private static final String PROCESS = UUID.randomUUID().toString();
    @Inject AgroalDataSource database;
    @Inject ObjectMapper json;
    @Inject PartyIdentifierFaultPort faults;
    @AfterEach void clearFaults() { faults.clear(); }

    @Test
    void malformedBindingAndFieldValidationUseStableCodeOnlyEnvelopes() throws Exception {
        var fixture = fixture();
        error(request(fixture).body("{").post(BASE, fixture.party()), 400, "bad-request");
        error(request(fixture).header("If-Match", "0").body("{").patch(BASE + "/{identifier}", fixture.party(), fixture.identifier()), 400, "bad-request");
        for (String action : List.of("verify", "reject", "revoke", "set-primary")) {
            error(request(fixture).header("If-Match", "0").body("{").post(BASE + "/{identifier}/{action}", fixture.party(), fixture.identifier(), action), 400, "bad-request");
        }
        error(request(fixture).body("{\"identifierSchemeCode\":\"TEST_NATURAL_ACTIVE\",\"value\":\" \"}").post(BASE, fixture.party()), 400, "identifier-value-required");
        error(request(fixture).body("{\"identifierSchemeCode\":\"TEST_NATURAL_ACTIVE\",\"value\":\"AB123456\",\"issuerCode\":\"bad__issuer\"}")
                .post(BASE, fixture.party()), 400, "issuer-code-invalid");
        assertEquals(List.of(0L, 0L, 0L), effects(fixture));
    }

    @Test
    void missingRoutesUnsupportedMethodsAndMediaRetainSharedEnvelopesAndAcceptedEcho() throws Exception {
        var fixture = fixture();
        error(request(fixture).get(BASE + "/{identifier}/unknown-action", fixture.party(), fixture.identifier()), 404, "not-found");
        error(request(fixture).put(BASE, fixture.party()), 405, "method-not-allowed");
        error(request(fixture).get(BASE + "/{identifier}/verify", fixture.party(), fixture.identifier()), 405, "method-not-allowed");
        error(request(fixture).contentType("application/xml").body("<private-value/>").post(BASE, fixture.party()), 415, "unsupported-media-type");
        error(request(fixture).header("If-Match", "0").contentType("application/xml").body("<private-reason/>")
                .post(BASE + "/{identifier}/reject", fixture.party(), fixture.identifier()), 415, "unsupported-media-type");
        assertEquals(List.of(0L, 0L, 0L), effects(fixture));
    }

    @Test
    void invalidPathsQueriesAndContextCannotEscapeTheSafeBoundary() throws Exception {
        var fixture = fixture();
        error(request(fixture).get(BASE, "BAD-PARTY"), 400, "party-id-invalid");
        error(request(fixture).get(BASE + "/{identifier}", fixture.party(), "BAD-IDENTIFIER"), 400, "bad-request");
        for (String query : List.of("unknown=private", "status=verified", "limit=0", "limit=1&limit=2", "cursor=private-invalid")) {
            error(request(fixture).get(BASE + "?" + query, fixture.party()), 400, "bad-request");
        }
        Response invalidProcess = given().contentType("application/json").header("Tenant-Id", fixture.tenant().toString()).header("User-Id", "operator")
                .header("Process-Id", "INVALID-PROCESS").get(BASE, fixture.party());
        assertEquals(400, invalidProcess.statusCode()); assertNull(invalidProcess.header("Process-Id"));
        PartyIdentifierContractAssertions.assertErrorResponse(400, "process-id-invalid", json.readTree(invalidProcess.asString()));
    }

    @Test
    void unexpectedActualWorkflowFailureIsSanitizedAndHasNoAcceptedEffects() throws Exception {
        var fixture = fixture(); UUID identifier = register(fixture);
        faults.arm(PartyIdentifierFaultPort.Phase.UPDATE, PartyIdentifierFaultPort.Mode.FAIL);
        error(request(fixture).header("If-Match", "0").post(BASE + "/{identifier}/verify", fixture.party(), identifier), 500, "server-error");
        assertEquals(List.of(1L, 0L, 1L), effects(fixture));
        faults.clear(); JsonNode current = json.readTree(request(fixture).get(BASE + "/{identifier}", fixture.party(), identifier).asString()).path("data");
        assertEquals("PENDING_VERIFICATION", current.path("status").textValue()); assertEquals(0, current.path("version").longValue());
    }

    @Test
    void corruptCompletionFailsClosedWithoutRemutatingOrDisclosingStoredPrivateContent() throws Exception {
        var fixture = fixture(); UUID identifier = register(fixture);
        Response accepted = request(fixture).header("If-Match", "0").header("Idempotency-Key", "corrupt-key")
                .post(BASE + "/{identifier}/verify", fixture.party(), identifier);
        assertEquals(200, accepted.statusCode()); JsonNode original = json.readTree(accepted.asString()).path("data");
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                UPDATE party_identifier_idempotency_records SET result_snapshot = cast(? AS jsonb)
                WHERE tenant_id = ? AND operation = 'party.identifier.verify.v1' AND idempotency_key = 'corrupt-key'
                """)) {
            statement.setString(1, "{\"private-value\":\"sensitive-identity-material\"}"); statement.setObject(2, fixture.tenant()); assertEquals(1, statement.executeUpdate());
        }
        error(request(fixture).header("If-Match", "0").header("Idempotency-Key", "corrupt-key")
                .post(BASE + "/{identifier}/verify", fixture.party(), identifier), 500, "server-error");
        Response current = request(fixture).get(BASE + "/{identifier}", fixture.party(), identifier);
        assertEquals(200, current.statusCode()); assertEquals(original, json.readTree(current.asString()).path("data"));
        assertEquals(List.of(2L, 0L, 2L), effects(fixture));
    }

    @Test
    void regroupedRoutingKeepsAllEightPublicOperationsAndTheirSafeSuccessContracts() throws Exception {
        var fixture = fixture(); UUID metadata = register(fixture);
        success(request(fixture).get(BASE + "/{identifier}", fixture.party(), metadata));
        Response list = request(fixture).get(BASE, fixture.party());
        assertEquals(200, list.statusCode()); assertEquals(PROCESS, list.header("Process-Id"));
        PartyIdentifierContractAssertions.assertPageResponse(200, json.readTree(list.asString()), 50);
        success(request(fixture).header("If-Match", "0").body("{\"issuerCode\":null}").patch(BASE + "/{identifier}", fixture.party(), metadata));
        UUID verified = register(fixture);
        success(request(fixture).header("If-Match", "0").post(BASE + "/{identifier}/verify", fixture.party(), verified));
        success(request(fixture).header("If-Match", "1").post(BASE + "/{identifier}/revoke", fixture.party(), verified));
        UUID rejected = register(fixture);
        success(request(fixture).header("If-Match", "0").post(BASE + "/{identifier}/reject", fixture.party(), rejected));
        UUID primary = register(fixture);
        success(request(fixture).header("If-Match", "0").post(BASE + "/{identifier}/verify", fixture.party(), primary));
        JsonNode selected = success(request(fixture).header("If-Match", "1").post(BASE + "/{identifier}/set-primary", fixture.party(), primary));
        assertTrue(selected.path("isPrimary").booleanValue());
    }

    private JsonNode success(Response response) throws Exception {
        assertEquals(200, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        JsonNode body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(200, body); return body.path("data");
    }

    private void error(Response response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        PartyIdentifierContractAssertions.assertErrorResponse(status, code, json.readTree(response.asString()));
    }
    private static RequestSpecification request(Fixture fixture) {
        return given().contentType("application/json").header("Tenant-Id", fixture.tenant().toString()).header("User-Id", "framework-operator").header("Process-Id", PROCESS);
    }
    private Fixture fixture() throws SQLException {
        faults.clear(); UUID party = UUID.randomUUID(); UUID tenant = UUID.randomUUID();
        var instant = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().minusSeconds(60));
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO parties(id, tenant_id, type, display_name, record_status, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, 'NATURAL_PERSON', 'Framework owner', 'ACTIVE', 7, ?, 'fixture-creator', ?, 'fixture-creator')
                """)) {
            statement.setObject(1, party); statement.setObject(2, tenant); statement.setTimestamp(3, instant); statement.setTimestamp(4, instant); assertEquals(1, statement.executeUpdate());
        }
        return new Fixture(party, tenant, UUID.randomUUID());
    }
    private UUID register(Fixture fixture) throws Exception {
        String body = json.writeValueAsString(Map.of("identifierSchemeCode", IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE,
                "value", "AB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)));
        Response response = request(fixture).header("Idempotency-Key", UUID.randomUUID().toString()).body(body).post(BASE, fixture.party());
        assertEquals(201, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        JsonNode result = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(201, result);
        return UUID.fromString(result.path("data").path("identifierId").textValue());
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
    /** Retains isolated owner scope plus an absent nested selector for framework-only failure checks. */
    private record Fixture(UUID party, UUID tenant, UUID identifier) { }

    /** Selects actual workflows and controlled technical failures while keeping the published global mapper authoritative. */
    public static class FrameworkProfile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() { return Map.of("quarkus.rabbitmq.devservices.enabled", "false", "party-registry.outbox.mode", "stored-only"); }
        @Override public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(PartyIdentifierFaultPort.class, FixedPartyIdentifierRegistrationProducer.class,
                    FixedPartyIdentifierLifecycleProducer.class, FixedPartyIdentifierPrimaryProducer.class);
        }
    }
}
