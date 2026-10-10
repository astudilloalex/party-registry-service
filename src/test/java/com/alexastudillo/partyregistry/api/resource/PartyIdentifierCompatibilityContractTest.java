package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.FixedIdentifierEvidencePartyProducer;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierLifecycleProducer;
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
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves actual verification feeds existing activation/current projections and withdrawals release values without rewriting retained history. */
@QuarkusTest
@TestProfile(PartyIdentifierCompatibilityContractTest.CompatibilityProfile.class)
@Timeout(120)
class PartyIdentifierCompatibilityContractTest {
    private static final String BASE = "/v1/parties/{party}/identifiers";
    private static final String PROCESS = UUID.randomUUID().toString();
    @Inject AgroalDataSource database;
    @Inject ObjectMapper json;

    @Test
    void nonprimaryVerificationFeedsExistingActivationAndLastEvidenceRevocationNeverChangesParty() throws Exception {
        for (PartyType type : PartyType.values()) {
            var fixture = fixture(type); JsonNode initial = create(fixture, fixture.key());
            UUID identifier = UUID.fromString(initial.path("identifierId").textValue());
            Response premature = request(fixture).header("If-Match", "7").post("/v1/parties/{party}/activate", fixture.party());
            assertEquals(422, premature.statusCode()); PartyIdentifierContractAssertions.assertErrorResponse(422, "missing-qualifying-identifier", json.readTree(premature.asString()));
            JsonNode verified = item(action(fixture, identifier, "verify", "0")); assertFalse(verified.path("isPrimary").booleanValue());
            Response activation = request(fixture).header("If-Match", "7").post("/v1/parties/{party}/activate", fixture.party());
            assertEquals(200, activation.statusCode()); assertEquals(8, json.readTree(activation.asString()).path("data").path("version").longValue());
            JsonNode parent = parent(fixture); JsonNode scheme = scheme(fixture);
            if (type == PartyType.NATURAL_PERSON) { assertEquals(1, currentIdentifiers(fixture).size()); }
            JsonNode revoked = item(action(fixture, identifier, "revoke", "1")); assertEquals("REVOKED", revoked.path("status").textValue());
            assertEquals(parent, parent(fixture)); assertEquals(scheme, scheme(fixture)); assertEquals("ACTIVE", parent.path("party").path("record_status").textValue());
            if (type == PartyType.NATURAL_PERSON) { assertTrue(currentIdentifiers(fixture).isEmpty()); }
            JsonNode detail = item(request(fixture).get(BASE + "/{identifier}", fixture.party(), identifier)); assertEquals(revoked, detail);
            assertEquals(initial, create(fixture, fixture.key()));
        }
    }

    @Test
    void rejectionAndRevocationReleaseUniquenessWhileKeepingCompleteHistoricalRowsAndCreationResults() throws Exception {
        for (PartyType type : PartyType.values()) {
            for (String action : List.of("reject", "revoke")) {
                var fixture = fixture(type); JsonNode initial = create(fixture, fixture.key());
                UUID identifier = UUID.fromString(initial.path("identifierId").textValue()); String version = "0";
                if (action.equals("revoke")) { item(action(fixture, identifier, "verify", "0")); version = "1"; }
                JsonNode withdrawn = item(action(fixture, identifier, action, version)); JsonNode history = stored(identifier); JsonNode parent = parent(fixture);
                JsonNode replacement = create(fixture, UUID.randomUUID().toString());
                assertNotEquals(initial.path("identifierId"), replacement.path("identifierId")); assertEquals("PENDING_VERIFICATION", replacement.path("status").textValue());
                assertEquals(history, stored(identifier)); assertEquals(parent, parent(fixture));
                assertEquals(withdrawn, item(request(fixture).get(BASE + "/{identifier}", fixture.party(), identifier)));
                assertEquals(initial, create(fixture, fixture.key()));
            }
        }
    }

    private Response action(Fixture fixture, UUID identifier, String action, String version) {
        return request(fixture).header("If-Match", version).post(BASE + "/{identifier}/{action}", fixture.party(), identifier, action);
    }
    private JsonNode item(Response response) throws Exception {
        assertEquals(200, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        JsonNode body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(200, body); return body.path("data");
    }
    private JsonNode create(Fixture fixture, String key) throws Exception {
        Response response = request(fixture).header("Idempotency-Key", key).body(fixture.body()).post(BASE, fixture.party());
        assertEquals(201, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        JsonNode body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(201, body); return body.path("data");
    }
    private static RequestSpecification request(Fixture fixture) {
        return given().contentType("application/json").header("Tenant-Id", fixture.tenant().toString()).header("User-Id", "compatibility-operator").header("Process-Id", PROCESS);
    }
    private Fixture fixture(PartyType type) throws Exception {
        UUID party = UUID.randomUUID(); UUID tenant = UUID.randomUUID();
        var instant = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().minusSeconds(60));
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO parties(id, tenant_id, type, display_name, record_status, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, cast(? AS party_type), 'Compatibility owner', 'DRAFT', 7, ?, 'fixture-creator', ?, 'fixture-creator')
                """)) {
            statement.setObject(1, party); statement.setObject(2, tenant); statement.setString(3, type.name());
            statement.setTimestamp(4, instant); statement.setTimestamp(5, instant); assertEquals(1, statement.executeUpdate());
        }
        details(party, type, instant);
        String code = type == PartyType.NATURAL_PERSON ? IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE : IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE;
        UUID scheme = type == PartyType.NATURAL_PERSON ? IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID : IdentifierSchemeTestFixtures.LEGAL_ACTIVE_ID;
        String body = json.writeValueAsString(Map.of("identifierSchemeCode", code, "value", "AB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)));
        return new Fixture(party, tenant, scheme, UUID.randomUUID().toString(), body);
    }
    private void details(UUID party, PartyType type, Timestamp instant) throws SQLException {
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
    private JsonNode currentIdentifiers(Fixture fixture) throws Exception {
        Response response = request(fixture).get("/v1/natural-person/{party}", fixture.party()); assertEquals(200, response.statusCode());
        return json.readTree(response.asString()).path("data").path("identifiers");
    }
    private JsonNode stored(UUID identifier) throws Exception { return snapshot("SELECT cast(to_jsonb(i) AS text) FROM party_identifiers i WHERE id = ?", identifier); }
    private JsonNode scheme(Fixture fixture) throws Exception { return snapshot("SELECT cast(to_jsonb(s) AS text) FROM identifier_schemes s WHERE id = ?", fixture.scheme()); }
    private JsonNode parent(Fixture fixture) throws Exception {
        return snapshot("""
                SELECT cast(jsonb_build_object('party', to_jsonb(p), 'natural', to_jsonb(n), 'legal', to_jsonb(l)) AS text)
                FROM parties p LEFT JOIN natural_person_details n ON n.party_id = p.id LEFT JOIN legal_entity_details l ON l.party_id = p.id WHERE p.id = ?
                """, fixture.party());
    }
    private JsonNode snapshot(String sql, UUID id) throws Exception {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id); try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return json.readTree(rows.getString(1)); }
        }
    }
    /** Retains isolated owner and original exact creation identity for history and uniqueness assertions. */
    private record Fixture(UUID party, UUID tenant, UUID scheme, String key, String body) {
        @Override public String toString() { return "Fixture[input=<redacted>]"; }
    }
    /** Selects real workflows with consistent operation clocks; existing Party activation policy and versions remain independent. */
    public static class CompatibilityProfile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() { return Map.of("quarkus.rabbitmq.devservices.enabled", "false", "party-registry.outbox.mode", "stored-only"); }
        @Override public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FixedPartyIdentifierRegistrationProducer.class, FixedPartyIdentifierLifecycleProducer.class, FixedIdentifierEvidencePartyProducer.class);
        }
    }
}
