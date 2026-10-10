package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierLifecycleProducer;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierPrimaryProducer;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierRegistrationProducer;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierReplayPatchProducer;
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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coordinates actual HTTP mutation transactions behind observed PostgreSQL key/scope locks and checks exact accepted effects. */
@QuarkusTest
@TestProfile(PartyIdentifierHttpConcurrencyTest.ConcurrencyProfile.class)
@Timeout(120)
class PartyIdentifierHttpConcurrencyTest {
    private static final String BASE = "/v1/parties/{party}/identifiers";
    private static final String ACTION = BASE + "/{identifier}/{action}";
    private static final String PROCESS = UUID.randomUUID().toString();
    @Inject AgroalDataSource database;
    @Inject ObjectMapper json;

    @Test
    void equivalentAndChangedCreationKeysHaveExactlyOneAcceptedOriginalForBothTypes() throws Exception {
        for (PartyType type : PartyType.values()) {
            for (boolean equivalent : List.of(true, false)) {
                var owner = owner(type); String key = UUID.randomUUID().toString(); String body = body(owner);
                String changed = equivalent ? body : body.replace("ORIGINAL_AUTHORITY", "CHANGED_AUTHORITY");
                var gate = replayGate(owner, PartyIdentifierOperation.CREATE, key);
                List<Response> responses = race(gate, () -> create(owner, key, body), () -> create(owner, key, changed));
                if (equivalent) { assertEquals(item(responses.getFirst(), 201), item(responses.getLast(), 201)); }
                else { assertAcceptedAndFailure(responses, 201, 409, "idempotency-key-conflict"); }
                assertEquals(List.of(1L, 1L, 0L, 1L), effects(owner));
            }
        }
    }

    @Test
    void distinctKeyNormalizedDuplicateCreationRaceLeavesOneIdentifierAndNoLosingCompletion() throws Exception {
        for (PartyType type : PartyType.values()) {
            var owner = owner(type); String body = body(owner);
            var responses = race(scopeGate(owner), () -> create(owner, UUID.randomUUID().toString(), body),
                    () -> create(owner, UUID.randomUUID().toString(), body));
            assertAcceptedAndFailure(responses, 201, 409, "identifier-uniqueness-conflict");
            assertEquals(List.of(1L, 1L, 0L, 1L), effects(owner));
        }
    }

    @Test
    void allKeyedActionsConvergeOrConflictOnChangedEffectiveInputsWithoutDuplicateEffects() throws Exception {
        for (PartyType type : PartyType.values()) {
            for (String action : List.of("verify", "reject", "revoke", "set-primary")) {
                for (boolean equivalent : List.of(true, false)) {
                    var owner = owner(type); UUID identifier = registered(owner);
                    if (action.equals("revoke") || action.equals("set-primary")) { verified(identifier); }
                    String key = UUID.randomUUID().toString(); String input = action.equals("verify") ? "" : "{}";
                    UUID changedTarget = !equivalent && action.equals("verify") ? registered(owner) : identifier;
                    String changed = equivalent ? input : switch (action) {
                        case "reject", "revoke" -> "{\"reasonCode\":\"changed\"}";
                        case "set-primary" -> "{\"replaceCurrentPrimary\":true}";
                        default -> "";
                    };
                    var responses = race(replayGate(owner, operation(action), key),
                            () -> action(owner, identifier, action, "0", key, input),
                            () -> action(owner, changedTarget, action, "0", key, changed));
                    if (equivalent) { assertEquals(item(responses.getFirst(), 200), item(responses.getLast(), 200)); }
                    else { assertAcceptedAndFailure(responses, 200, 409, "idempotency-key-conflict"); }
                    long audits = action.equals("reject") || action.equals("revoke") ? 1 : 0;
                    long creations = changedTarget.equals(identifier) ? 1 : 2;
                    assertEquals(List.of(creations, creations + 1, audits, creations + 1), effects(owner));
                    assertEquals(1, changedTarget.equals(identifier) ? storedVersion(identifier) : storedVersion(identifier) + storedVersion(changedTarget));
                }
            }
        }
    }

    @Test
    void independentPatchAndTransitionAtOneVersionHaveOneWinnerAndOnlyMatchingEffects() throws Exception {
        for (PartyType type : PartyType.values()) {
            for (String action : List.of("verify", "reject")) {
                var owner = owner(type); UUID identifier = registered(owner);
                List<Response> responses = race(scopeGate(owner),
                        () -> request(owner).header("If-Match", "0").body("{\"issuerCode\":\"CORRECTED_AUTHORITY\"}")
                                .patch(BASE + "/{identifier}", owner.party(), identifier),
                        () -> action(owner, identifier, action, "0", UUID.randomUUID().toString(), ""));
                assertAcceptedAndFailure(responses, 200, 412, "expected-version-mismatch");
                boolean patchWon = responses.getFirst().statusCode() == 200;
                assertEquals(List.of(1L, patchWon ? 1L : 2L, !patchWon && action.equals("reject") ? 1L : 0L, 2L), effects(owner));
                assertEquals(1, storedVersion(identifier));
                JsonNode winner = item(patchWon ? responses.getFirst() : responses.getLast(), 200);
                assertEquals(patchWon ? "PENDING_VERIFICATION" : action.equals("verify") ? "VERIFIED" : "REJECTED", winner.path("status").textValue());
            }
        }
    }

    @Test
    void differentFirstPrimaryCandidatesAndProvisionalVerifiesHaveOneScopedWinner() throws Exception {
        for (PartyType type : PartyType.values()) {
            for (String action : List.of("verify", "set-primary")) {
                var owner = owner(type); UUID first = registered(owner); UUID second = registered(owner);
                if (action.equals("set-primary")) { verified(first); verified(second); }
                else { provisional(first); provisional(second); }
                var responses = race(scopeGate(owner), () -> action(owner, first, action, "0", UUID.randomUUID().toString(), ""),
                        () -> action(owner, second, action, "0", UUID.randomUUID().toString(), ""));
                assertAcceptedAndFailure(responses, 200, 409, "primary-identifier-conflict");
                assertEquals(1, storedVersion(first) + storedVersion(second)); assertEquals(1, verifiedPrimaries(owner));
                assertEquals(List.of(2L, 3L, 0L, 3L), effects(owner));
            }
        }
    }

    @Test
    void distinctExplicitReplacementsSerializeWithOneFinalPrimaryAndHistoricalAcceptedResults() throws Exception {
        for (PartyType type : PartyType.values()) {
            var owner = owner(type); UUID former = registered(owner); UUID first = registered(owner); UUID second = registered(owner);
            verified(former); verified(first); verified(second); provisional(former);
            String firstKey = UUID.randomUUID().toString(); String secondKey = UUID.randomUUID().toString(); String input = "{\"replaceCurrentPrimary\":true}";
            var responses = race(scopeGate(owner), () -> action(owner, first, "set-primary", "0", firstKey, input),
                    () -> action(owner, second, "set-primary", "0", secondKey, input));
            JsonNode firstResult = item(responses.getFirst(), 200); JsonNode secondResult = item(responses.getLast(), 200);
            assertEquals(1, verifiedPrimaries(owner)); assertEquals(4, storedVersion(former) + storedVersion(first) + storedVersion(second));
            assertEquals(firstResult, item(action(owner, first, "set-primary", "0", firstKey, input), 200));
            assertEquals(secondResult, item(action(owner, second, "set-primary", "0", secondKey, input), 200));
            assertEquals(List.of(3L, 5L, 0L, 7L), effects(owner));
        }
    }

    @Test
    void replacementVersusFormerCorrectionOrWithdrawalMatchesAnAllowedSerialOutcome() throws Exception {
        for (PartyType type : PartyType.values()) {
            for (boolean withdrawal : List.of(false, true)) {
                var owner = owner(type); UUID former = registered(owner); UUID target = registered(owner);
                verified(former); verified(target); provisional(former);
                Supplier<Response> other = withdrawal
                        ? () -> action(owner, former, "revoke", "0", UUID.randomUUID().toString(), "{\"reasonCode\":\"withdrawn\"}")
                        : () -> request(owner).header("If-Match", "0").body("{\"issuerCode\":\"CORRECTED_AUTHORITY\"}")
                                .patch(BASE + "/{identifier}", owner.party(), former);
                var responses = race(scopeGate(owner),
                        () -> action(owner, target, "set-primary", "0", UUID.randomUUID().toString(), "{\"replaceCurrentPrimary\":true}"), other);
                item(responses.getFirst(), 200);
                boolean otherWonFirst = responses.getLast().statusCode() == 200;
                if (otherWonFirst) { item(responses.getLast(), 200); }
                else {
                    assertEquals(412, responses.getLast().statusCode());
                    PartyIdentifierContractAssertions.assertErrorResponse(412, "expected-version-mismatch", json.readTree(responses.getLast().asString()));
                }
                assertEquals(1, verifiedPrimaries(owner)); assertEquals(1, storedVersion(target));
                assertEquals(!withdrawal && otherWonFirst ? 2 : 1, storedVersion(former));
                long completions = withdrawal && otherWonFirst ? 4 : 3;
                long audits = withdrawal && otherWonFirst ? 1 : 0;
                long events = !withdrawal && otherWonFirst ? 5 : 4;
                assertEquals(List.of(2L, completions, audits, events), effects(owner));
            }
        }
    }

    @Test
    void unrelatedSchemeScopeProgressesWhileAnotherPrimaryRequestIsActuallyWaiting() throws Exception {
        var owner = owner(PartyType.NATURAL_PERSON); UUID blocked = registered(owner); verified(blocked);
        var unrelated = new Owner(owner.party(), owner.tenant(), IdentifierSchemeTestFixtures.BOTH_EXPIRING_ID, IdentifierSchemeTestFixtures.BOTH_EXPIRING_CODE);
        UUID independent = registered(unrelated); verified(independent); Gate gate = scopeGate(owner);
        try (var held = database.getConnection(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            held.setAutoCommit(false); lock(held, gate);
            var waiting = workers.submit(() -> action(owner, blocked, "set-primary", "0", UUID.randomUUID().toString(), ""));
            try {
                await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(10)).until(() -> waiters(gate) == 1);
                item(action(unrelated, independent, "set-primary", "0", UUID.randomUUID().toString(), ""), 200);
                assertEquals(0, storedVersion(blocked)); assertEquals(1, storedVersion(independent));
            } finally { held.rollback(); }
            item(waiting.get(30, TimeUnit.SECONDS), 200);
        }
        assertEquals(2, verifiedPrimaries(owner)); assertEquals(List.of(2L, 4L, 0L, 4L), effects(owner));
    }

    private List<Response> race(Gate gate, Supplier<Response> first, Supplier<Response> second) throws Exception {
        try (var held = database.getConnection(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            held.setAutoCommit(false); lock(held, gate);
            var start = new CyclicBarrier(3);
            var left = workers.submit(() -> { start.await(10, TimeUnit.SECONDS); return first.get(); });
            var right = workers.submit(() -> { start.await(10, TimeUnit.SECONDS); return second.get(); });
            try {
                start.await(10, TimeUnit.SECONDS);
                // Both independent request transactions must actually reach the database gate before release.
                await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(10)).until(() -> waiters(gate) == 2);
            } finally { held.rollback(); }
            return List.of(left.get(30, TimeUnit.SECONDS), right.get(30, TimeUnit.SECONDS));
        }
    }
    private static void lock(Connection connection, Gate gate) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?, ?)")) {
            statement.setInt(1, gate.namespace()); statement.setInt(2, gate.identity()); statement.execute();
        }
    }
    private int waiters(Gate gate) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND classid::bigint = ? AND objid::bigint = ? AND objsubid = 2 AND NOT granted
                """)) {
            statement.setLong(1, Integer.toUnsignedLong(gate.namespace())); statement.setLong(2, Integer.toUnsignedLong(gate.identity()));
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getInt(1); }
        }
    }
    private static Gate replayGate(Owner owner, PartyIdentifierOperation operation, String key) {
        return new Gate(PartyIdentifierMutationLocks.REPLAY_NAMESPACE,
                PartyIdentifierMutationLocks.replayLockId(new PartyIdentifierIdempotencyRecordId(owner.tenant(), operation.namespace(), key)));
    }
    private static Gate scopeGate(Owner owner) {
        return new Gate(PartyIdentifierMutationLocks.SCOPE_NAMESPACE,
                PartyIdentifierMutationLocks.scopeLockId(new PartyIdentifierMutationLocks.Scope(owner.tenant(), owner.party(), owner.schemeId())));
    }
    private void assertAcceptedAndFailure(List<Response> responses, int success, int failure, String code) throws Exception {
        assertEquals(List.of(success, failure).stream().sorted().toList(), responses.stream().map(Response::statusCode).sorted().toList());
        for (Response response : responses) {
            assertEquals(PROCESS, response.header("Process-Id"));
            if (response.statusCode() == success) { item(response, success); }
            else { PartyIdentifierContractAssertions.assertErrorResponse(failure, code, json.readTree(response.asString())); }
        }
    }
    private JsonNode item(Response response, int status) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        JsonNode body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(status, body); return body.path("data");
    }
    private static PartyIdentifierOperation operation(String action) { return switch (action) {
        case "verify" -> PartyIdentifierOperation.VERIFY; case "reject" -> PartyIdentifierOperation.REJECT; case "revoke" -> PartyIdentifierOperation.REVOKE;
        default -> PartyIdentifierOperation.SET_PRIMARY;
    }; }
    private static Response action(Owner owner, UUID identifier, String action, String version, String key, String body) {
        return request(owner).header("If-Match", version).header("Idempotency-Key", key).body(body).post(ACTION, owner.party(), identifier, action);
    }
    private static Response create(Owner owner, String key, String body) { return request(owner).header("Idempotency-Key", key).body(body).post(BASE, owner.party()); }
    private static RequestSpecification request(Owner owner) {
        return given().contentType("application/json").header("Tenant-Id", owner.tenant().toString()).header("User-Id", "coordinated-operator").header("Process-Id", PROCESS);
    }
    private String body(Owner owner) throws Exception {
        return json.writeValueAsString(Map.of("identifierSchemeCode", owner.scheme(), "value", "AB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10), "issuerCode", "ORIGINAL_AUTHORITY"));
    }
    private UUID registered(Owner owner) throws Exception { return UUID.fromString(item(create(owner, UUID.randomUUID().toString(), body(owner)), 201).path("identifierId").textValue()); }
    private Owner owner(PartyType type) throws SQLException {
        UUID party = UUID.randomUUID(); UUID tenant = UUID.randomUUID();
        var instant = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().minusSeconds(60));
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO parties(id, tenant_id, type, display_name, record_status, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, cast(? AS party_type), 'Race owner', 'ACTIVE', 7, ?, 'fixture-creator', ?, 'fixture-creator')
                """)) {
            statement.setObject(1, party); statement.setObject(2, tenant); statement.setString(3, type.name());
            statement.setTimestamp(4, instant); statement.setTimestamp(5, instant); assertEquals(1, statement.executeUpdate());
        }
        return type == PartyType.NATURAL_PERSON
                ? new Owner(party, tenant, IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID, IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE)
                : new Owner(party, tenant, IdentifierSchemeTestFixtures.LEGAL_ACTIVE_ID, IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE);
    }
    private void verified(UUID identifier) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                UPDATE party_identifiers SET status = 'VERIFIED', verified_at = created_at, verified_by = 'original-verifier' WHERE id = ?
                """)) {
            statement.setObject(1, identifier); assertEquals(1, statement.executeUpdate());
        }
    }
    private void provisional(UUID identifier) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("UPDATE party_identifiers SET is_primary = true WHERE id = ?")) {
            statement.setObject(1, identifier); assertEquals(1, statement.executeUpdate());
        }
    }
    private int verifiedPrimaries(Owner owner) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("SELECT count(*) FROM party_identifiers WHERE tenant_id = ? AND status = 'VERIFIED' AND is_primary")) {
            statement.setObject(1, owner.tenant()); try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getInt(1); }
        }
    }
    private long storedVersion(UUID identifier) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("SELECT version FROM party_identifiers WHERE id = ?")) {
            statement.setObject(1, identifier); try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getLong(1); }
        }
    }
    private List<Long> effects(Owner owner) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                SELECT (SELECT count(*) FROM party_identifiers WHERE tenant_id = ?),
                       (SELECT count(*) FROM party_identifier_idempotency_records WHERE tenant_id = ?),
                       (SELECT count(*) FROM party_identifier_action_audit WHERE tenant_id = ?),
                       (SELECT count(*) FROM party_outbox_events WHERE tenant_id = ?)
                """)) {
            for (int index = 1; index <= 4; index++) { statement.setObject(index, owner.tenant()); }
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return List.of(rows.getLong(1), rows.getLong(2), rows.getLong(3), rows.getLong(4)); }
        }
    }
    /** Retains a complete isolated owner/scheme scope for actual competing requests. */
    private record Owner(UUID party, UUID tenant, UUID schemeId, String scheme) { }
    /** Identifies exactly the two-integer transaction gate whose real waiters must be observed. */
    private record Gate(int namespace, int identity) { }
    /** Enables real fixed-clock workflows and stored-only events without changing production timeout budgets. */
    public static class ConcurrencyProfile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() { return Map.of("quarkus.rabbitmq.devservices.enabled", "false", "party-registry.outbox.mode", "stored-only"); }
        @Override public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FixedPartyIdentifierRegistrationProducer.class, FixedPartyIdentifierLifecycleProducer.class,
                    FixedPartyIdentifierReplayPatchProducer.class, FixedPartyIdentifierPrimaryProducer.class);
        }
    }
}
