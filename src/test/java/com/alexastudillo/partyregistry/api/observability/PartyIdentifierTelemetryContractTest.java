package com.alexastudillo.partyregistry.api.observability;

import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierLifecycleProducer;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierPrimaryProducer;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierRegistrationProducer;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies real postcommit disposition metrics, numeric persistence costs and concurrent accepted-context isolation. */
@QuarkusTest
@TestProfile(PartyIdentifierTelemetryContractTest.TelemetryProfile.class)
@Timeout(120)
class PartyIdentifierTelemetryContractTest {
    private static final String BASE = "/v1/parties/{party}/identifiers";
    @Inject AgroalDataSource database;
    @Inject ObjectMapper json;
    @Inject MeterRegistry meters;

    @Test
    void actualCreatedReplayedAppliedConflictAndFailureOutcomesNeverCountUnacceptedSuccess() throws Exception {
        double created = count("register-identifier", "created", "successful");
        double replayed = count("register-identifier", "replayed", "successful");
        double applied = count("verify-identifier", "applied", "successful");
        double conflicts = count("verify-identifier", "conflict", "idempotency-key-conflict");
        double failures = count("verify-identifier", "failure", "bad-request");
        var fixture = fixture();
        JsonNode initial = create(fixture); UUID identifier = UUID.fromString(initial.path("identifierId").textValue());
        assertEquals(initial, create(fixture));
        item(request(fixture, "verifier", fixture.process()).header("If-Match", "0").header("Idempotency-Key", fixture.key())
                .post(BASE + "/{identifier}/verify", fixture.party(), identifier), 200, fixture.process());
        Response conflict = request(fixture, "verifier", fixture.process()).header("If-Match", "1").header("Idempotency-Key", fixture.key())
                .post(BASE + "/{identifier}/verify", fixture.party(), identifier);
        assertEquals(409, conflict.statusCode()); PartyIdentifierContractAssertions.assertErrorResponse(409, "idempotency-key-conflict", json.readTree(conflict.asString()));
        Response invalid = request(fixture, "verifier", fixture.process()).header("If-Match", "1").body("{}")
                .post(BASE + "/{identifier}/verify", fixture.party(), identifier);
        assertEquals(400, invalid.statusCode()); PartyIdentifierContractAssertions.assertErrorResponse(400, "bad-request", json.readTree(invalid.asString()));
        assertEquals(created + 1, count("register-identifier", "created", "successful"));
        assertEquals(replayed + 1, count("register-identifier", "replayed", "successful"));
        assertEquals(applied + 1, count("verify-identifier", "applied", "successful"));
        assertEquals(conflicts + 1, count("verify-identifier", "conflict", "idempotency-key-conflict"));
        assertEquals(failures + 1, count("verify-identifier", "failure", "bad-request"));
        Response list = request(fixture, "reader", fixture.process()).get(BASE, fixture.party());
        assertEquals(200, list.statusCode()); PartyIdentifierContractAssertions.assertPageResponse(200, json.readTree(list.asString()), 50);
        assertTrue(meters.get("party.registry.identifier.lock.wait").tag("kind", "replay-key").timers().stream().mapToLong(Timer::count).sum() > 0);
        assertTrue(meters.get("party.registry.identifier.lock.wait").tag("kind", "primary-scope").timers().stream().mapToLong(Timer::count).sum() > 0);
        assertTrue(meters.get("party.registry.identifier.page.rows").summary().totalAmount() >= 1);
        assertSafeDimensions(fixture);
    }

    @Test
    void concurrentTenantsActorsAndFreshReplayProcessesRemainAttributedOnlyToTheirOwnRequests() throws Exception {
        var first = fixture(); var second = fixture();
        UUID firstId = UUID.fromString(create(first).path("identifierId").textValue()); UUID secondId = UUID.fromString(create(second).path("identifierId").textValue());
        JsonNode accepted;
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var left = workers.submit(() -> request(first, "first-actor", first.process()).header("If-Match", "0").header("Idempotency-Key", "shared-key")
                    .post(BASE + "/{identifier}/verify", first.party(), firstId));
            var right = workers.submit(() -> request(second, "second-actor", second.process()).header("If-Match", "0").header("Idempotency-Key", "shared-key")
                    .post(BASE + "/{identifier}/verify", second.party(), secondId));
            accepted = item(left.get(30, TimeUnit.SECONDS), 200, first.process());
            assertEquals("first-actor", accepted.path("verifiedBy").textValue());
            assertEquals("second-actor", item(right.get(30, TimeUnit.SECONDS), 200, second.process()).path("verifiedBy").textValue());
        }
        String freshProcess = UUID.randomUUID().toString();
        JsonNode retry = item(request(first, "retry-actor", freshProcess).header("If-Match", "0").header("Idempotency-Key", "shared-key")
                .post(BASE + "/{identifier}/verify", first.party(), firstId), 200, freshProcess);
        assertEquals(accepted, retry);
        Response later = request(second, "later-reader", second.process()).get(BASE + "/{identifier}", second.party(), secondId);
        assertEquals("second-actor", item(later, 200, second.process()).path("verifiedBy").textValue());
        assertSafeDimensions(first); assertSafeDimensions(second);
    }

    private double count(String operation, String outcome, String code) {
        return meters.find("party.registry.http.identifier.mutation").tags("operation", operation, "outcome", outcome, "code", code)
                .counters().stream().mapToDouble(Counter::count).sum();
    }
    private void assertSafeDimensions(Fixture fixture) {
        meters.getMeters().stream().filter(meter -> meter.getId().getName().startsWith("party.registry")).forEach(meter -> {
            String tags = meter.getId().getTags().toString();
            assertFalse(tags.contains(fixture.party().toString())); assertFalse(tags.contains(fixture.tenant().toString()));
            assertFalse(tags.contains(fixture.process())); assertFalse(tags.contains(fixture.key())); assertFalse(tags.contains("private"));
        });
    }
    private JsonNode create(Fixture fixture) throws Exception {
        return item(request(fixture, "creator", fixture.process()).header("Idempotency-Key", fixture.key()).body(fixture.body()).post(BASE, fixture.party()), 201, fixture.process());
    }
    private JsonNode item(Response response, int status, String process) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(process, response.header("Process-Id"));
        JsonNode body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(status, body); return body.path("data");
    }
    private static RequestSpecification request(Fixture fixture, String actor, String process) {
        return given().contentType("application/json").header("Tenant-Id", fixture.tenant().toString()).header("User-Id", actor).header("Process-Id", process);
    }
    private Fixture fixture() throws Exception {
        UUID party = UUID.randomUUID(); UUID tenant = UUID.randomUUID();
        var instant = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().minusSeconds(60));
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO parties(id, tenant_id, type, display_name, record_status, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, 'NATURAL_PERSON', 'Telemetry owner', 'ACTIVE', 7, ?, 'fixture-creator', ?, 'fixture-creator')
                """)) {
            statement.setObject(1, party); statement.setObject(2, tenant); statement.setTimestamp(3, instant); statement.setTimestamp(4, instant); assertEquals(1, statement.executeUpdate());
        }
        String body = json.writeValueAsString(Map.of("identifierSchemeCode", IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE,
                "value", "AB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)));
        return new Fixture(party, tenant, UUID.randomUUID().toString(), "private-key-" + UUID.randomUUID(), body);
    }
    /** Retains isolated accepted context and exact creation identity only for correlation/replay assertions. */
    private record Fixture(UUID party, UUID tenant, String process, String key, String body) {
        @Override public String toString() { return "Fixture[input=<redacted>]"; }
    }
    /** Selects actual managed persistence/telemetry and fixed-clock workflows without a synthetic mutation port. */
    public static class TelemetryProfile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() { return Map.of("quarkus.rabbitmq.devservices.enabled", "false", "party-registry.outbox.mode", "stored-only"); }
        @Override public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FixedPartyIdentifierRegistrationProducer.class, FixedPartyIdentifierLifecycleProducer.class, FixedPartyIdentifierPrimaryProducer.class);
        }
    }
}
