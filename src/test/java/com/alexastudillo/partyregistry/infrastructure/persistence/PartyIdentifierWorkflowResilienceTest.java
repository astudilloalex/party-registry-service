package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.ChangePartyIdentifierLifecycleCommand;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierReason;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.usecase.ChangePartyIdentifierLifecycleUseCase;
import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.infrastructure.persistence.PartyIdentifierFaultPort.Mode;
import com.alexastudillo.partyregistry.infrastructure.persistence.PartyIdentifierFaultPort.Phase;
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
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.restassured.response.Response;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies actual-workflow flushed rollback, classified SQL/work waits, cancellation cleanup and postcommit keyed recovery. */
@QuarkusTest
@TestProfile(PartyIdentifierWorkflowResilienceTest.ResilienceProfile.class)
@Timeout(120)
class PartyIdentifierWorkflowResilienceTest {
    private static final String BASE = "/v1/parties/{party}/identifiers";
    private static final String ACTION = BASE + "/{identifier}/{action}";
    private static final String PROCESS = UUID.randomUUID().toString();
    @Inject AgroalDataSource database;
    @Inject ObjectMapper json;
    @Inject PartyIdentifierFaultPort faults;
    @Inject Mutiny.SessionFactory sessions;
    @Inject PartyIdentifierPersistenceMapper mapper;
    @Inject ChangePartyIdentifierLifecycleUseCase lifecycle;

    @AfterEach void clearFaults() { faults.clear(); }

    @Test
    void everyFlushedPrimaryCheckpointRollsBackBothRecordsAndAllowsSameKeyRecovery() throws Exception {
        for (Phase phase : List.of(Phase.DEMOTION, Phase.PROMOTION, Phase.COMPLETION, Phase.EVENT)) {
            var owner = owner(); UUID former = registered(owner); UUID target = registered(owner);
            verified(former, true); verified(target, false);
            JsonNode before = rows(owner); List<Long> counts = effects(owner);
            faults.arm(phase, Mode.FAIL);
            error(action(owner, target, "set-primary", "failure-key", "{\"replaceCurrentPrimary\":true}"), 500, "server-error");
            assertEquals(before, rows(owner)); assertEquals(counts, effects(owner));
            faults.clear(); item(action(owner, target, "set-primary", "failure-key", "{\"replaceCurrentPrimary\":true}"));
            assertEquals(List.of(3L, 0L, 4L), effects(owner));
        }
    }

    @Test
    void everyFlushedWithdrawalCheckpointRollsBackAuditCompletionAndEventAndKeyIsReusable() throws Exception {
        for (String action : List.of("reject", "revoke")) {
            for (Phase phase : List.of(Phase.UPDATE, Phase.AUDIT, Phase.COMPLETION, Phase.EVENT)) {
                var owner = owner(); UUID target = registered(owner);
                if (action.equals("revoke")) { verified(target, true); }
                JsonNode before = rows(owner); faults.arm(phase, Mode.FAIL);
                error(action(owner, target, action, "failure-key", "{\"reasonCode\":\"withdrawn\"}"), 500, "server-error");
                assertEquals(before, rows(owner)); assertEquals(List.of(1L, 0L, 1L), effects(owner));
                faults.clear(); item(action(owner, target, action, "failure-key", "{\"reasonCode\":\"withdrawn\"}"));
                assertEquals(List.of(2L, 1L, 2L), effects(owner));
            }
        }
    }

    @Test
    void realStatementAndOperationWaitsReturn503AfterRollbackAndPermitPoolAndKeyReuse() throws Exception {
        for (Mode mode : List.of(Mode.SQL_WAIT, Mode.HOLD)) {
            var owner = owner(); UUID target = registered(owner); JsonNode before = rows(owner);
            var probe = faults.arm(Phase.EVENT, mode);
            error(action(owner, target, "reject", "timeout-key", "{}"), 503, "dependency-unavailable");
            assertTrue(probe.reached.isDone()); assertFalse(probe.opened.get().isOpen());
            assertEquals(before, rows(owner)); assertEquals(List.of(1L, 0L, 1L), effects(owner));
            faults.clear(); item(action(owner, target, "reject", "timeout-key", "{}"));
            assertEquals(List.of(2L, 1L, 2L), effects(owner));
        }
    }

    @Test
    void actualTargetLockTimeoutIsClassifiedAndTheFailedKeyRemainsUnused() throws Exception {
        var owner = owner(); UUID target = registered(owner); JsonNode before = rows(owner);
        try (var held = database.getConnection(); var statement = held.prepareStatement("SELECT id FROM party_identifiers WHERE id = ? FOR UPDATE")) {
            held.setAutoCommit(false); statement.setObject(1, target);
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); }
            try { error(action(owner, target, "reject", "lock-key", "{}"), 503, "dependency-unavailable"); }
            finally { held.rollback(); }
        }
        assertEquals(before, rows(owner)); assertEquals(List.of(1L, 0L, 1L), effects(owner));
        item(action(owner, target, "reject", "lock-key", "{}")); assertEquals(List.of(2L, 1L, 2L), effects(owner));
    }

    @Test
    void lostPostCommitDeliveryKeepsAcceptedStateAndRetryRecoversWithoutFalseRollback() throws Exception {
        var owner = owner(); UUID target = registered(owner);
        faults.arm(Phase.DELIVERY, Mode.FAIL);
        error(action(owner, target, "verify", "delivery-key", ""), 500, "server-error");
        JsonNode committed = rows(owner); assertEquals(List.of(2L, 0L, 2L), effects(owner));
        faults.clear(); JsonNode retry = item(action(owner, target, "verify", "delivery-key", ""));
        assertEquals("VERIFIED", retry.path("status").textValue()); assertEquals(1, retry.path("version").longValue());
        assertEquals(committed, rows(owner)); assertEquals(List.of(2L, 0L, 2L), effects(owner));
    }

    @Test
    @RunOnVertxContext
    void cancellingActualWithdrawalAfterFlushedAuditClosesSessionBeforePoolAndKeyReuse(UniAsserter asserter) {
        var created = FixedPartyIdentifierRegistrationProducer.CLOCK.instant();
        var tenant = new TenantId(UUID.randomUUID()); var party = new PartyId(UUID.randomUUID());
        var source = PartyIdentifier.builder().identifierId(new PartyIdentifierId(UUID.randomUUID())).tenantId(tenant).partyId(party)
                .identifierSchemeId(new IdentifierSchemeId(IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID))
                .protectedValue(new ProtectedIdentifierValue("synthetic-cancellation-ciphertext", 1, UUID.randomUUID().toString().replace("-", "").repeat(2), "***1234", new IdentifierRuleVersion(1)))
                .status(PartyIdentifierStatus.PENDING_VERIFICATION).primary(false).version(PartyIdentifierVersion.initial()).auditInfo(AuditInfo.initial(created, "original-creator")).build();
        var entity = new PartyEntity(party.value(), tenant.value(), PartyType.NATURAL_PERSON, "Cancellation owner", PartyRecordStatus.ACTIVE, AuditInfo.initial(created, "original-creator"), 7);
        var command = new ChangePartyIdentifierLifecycleCommand(new RequestMetadata(tenant, "cancellation-actor", UUID.randomUUID()), party,
                source.identifierId(), PartyIdentifierVersion.initial(), PartyIdentifierOperation.REJECT, "cancel-key", PartyIdentifierReason.none());
        asserter.execute(() -> sessions.withTransaction((session, _) -> session.persist(entity).call(() -> session.persist(mapper.toEntity(source))).call(session::flush)));
        asserter.execute(() -> {
            var probe = faults.arm(Phase.AUDIT, Mode.HOLD);
            UniAssertSubscriber<PartyIdentifierMutationOutcome> subscriber = lifecycle.execute(command).subscribe().withSubscriber(UniAssertSubscriber.create());
            return Uni.createFrom().completionStage(probe.reached).ifNoItem().after(Duration.ofSeconds(10)).fail()
                    .invoke(subscriber::cancel).chain(() -> awaitClosed(probe)).invoke(() -> assertNull(subscriber.getItem())).eventually(subscriber::cancel);
        });
        asserter.assertThat(() -> sessions.withSession(session -> session.find(PartyIdentifierEntity.class, source.identifierId().value())), restored -> assertEquals(0, restored.version()));
        asserter.assertThat(() -> reactiveEffects(tenant), counts -> assertEquals(List.of(0L, 0L, 0L), counts));
        asserter.execute(faults::clear);
        asserter.assertThat(() -> lifecycle.execute(command), outcome -> assertEquals(1, outcome.identifier().version().value()));
        asserter.assertThat(() -> reactiveEffects(tenant), counts -> assertEquals(List.of(1L, 1L, 1L), counts));
    }

    private static Uni<Void> awaitClosed(PartyIdentifierFaultPort.Probe probe) {
        return Multi.createBy().repeating().uni(() -> Uni.createFrom().item(() -> !probe.opened.get().isOpen()).onItem().delayIt().by(Duration.ofMillis(5)))
                .until(Boolean.TRUE::equals).collect().last().ifNoItem().after(Duration.ofSeconds(5)).fail().replaceWithVoid();
    }
    private Uni<List<Long>> reactiveEffects(TenantId tenant) {
        return sessions.withSession(session -> session.createNativeQuery("""
                SELECT (SELECT count(*) FROM party_identifier_idempotency_records WHERE tenant_id = :tenant),
                       (SELECT count(*) FROM party_identifier_action_audit WHERE tenant_id = :tenant),
                       (SELECT count(*) FROM party_outbox_events WHERE tenant_id = :tenant)
                """, Object[].class).setParameter("tenant", tenant.value()).getSingleResult()
                .map(row -> List.of(((Number) row[0]).longValue(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue())));
    }
    private Response action(Owner owner, UUID target, String action, String key, String body) {
        return request(owner).header("If-Match", "0").header("Idempotency-Key", key).body(body).post(ACTION, owner.party(), target, action);
    }
    private JsonNode item(Response response) throws Exception {
        assertEquals(200, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        JsonNode body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(200, body); return body.path("data");
    }
    private void error(Response response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        PartyIdentifierContractAssertions.assertErrorResponse(status, code, json.readTree(response.asString()));
    }
    private static io.restassured.specification.RequestSpecification request(Owner owner) {
        return given().contentType("application/json").header("Tenant-Id", owner.tenant().toString()).header("User-Id", "resilience-operator").header("Process-Id", PROCESS);
    }
    private Owner owner() throws SQLException {
        faults.clear(); UUID party = UUID.randomUUID(); UUID tenant = UUID.randomUUID();
        var instant = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().minusSeconds(60));
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO parties(id, tenant_id, type, display_name, record_status, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, 'NATURAL_PERSON', 'Resilience owner', 'ACTIVE', 7, ?, 'fixture-creator', ?, 'fixture-creator')
                """)) {
            statement.setObject(1, party); statement.setObject(2, tenant); statement.setTimestamp(3, instant); statement.setTimestamp(4, instant); assertEquals(1, statement.executeUpdate());
        }
        return new Owner(party, tenant);
    }
    private UUID registered(Owner owner) throws Exception {
        String body = json.writeValueAsString(Map.of("identifierSchemeCode", IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE, "value", "AB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)));
        Response response = request(owner).header("Idempotency-Key", UUID.randomUUID().toString()).body(body).post(BASE, owner.party());
        assertEquals(201, response.statusCode()); return UUID.fromString(json.readTree(response.asString()).path("data").path("identifierId").textValue());
    }
    private void verified(UUID target, boolean primary) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("UPDATE party_identifiers SET status = 'VERIFIED', is_primary = ?, verified_at = created_at, verified_by = 'original-verifier' WHERE id = ?")) {
            statement.setBoolean(1, primary); statement.setObject(2, target); assertEquals(1, statement.executeUpdate());
        }
    }
    private JsonNode rows(Owner owner) throws Exception {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("SELECT cast(jsonb_agg(to_jsonb(i) ORDER BY id) AS text) FROM party_identifiers i WHERE tenant_id = ?")) {
            statement.setObject(1, owner.tenant()); try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return json.readTree(rows.getString(1)); }
        }
    }
    private List<Long> effects(Owner owner) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                SELECT (SELECT count(*) FROM party_identifier_idempotency_records WHERE tenant_id = ?),
                       (SELECT count(*) FROM party_identifier_action_audit WHERE tenant_id = ?),
                       (SELECT count(*) FROM party_outbox_events WHERE tenant_id = ?)
                """)) {
            statement.setObject(1, owner.tenant()); statement.setObject(2, owner.tenant()); statement.setObject(3, owner.tenant());
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return List.of(rows.getLong(1), rows.getLong(2), rows.getLong(3)); }
        }
    }
    /** Retains one isolated database owner used by real workflow requests and complete rollback assertions. */
    private record Owner(UUID party, UUID tenant) { }
    /** Enables controlled actual persistence checkpoints and real fixed-clock workflows with unchanged production timeout budgets. */
    public static class ResilienceProfile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() { return Map.of("quarkus.rabbitmq.devservices.enabled", "false", "party-registry.outbox.mode", "stored-only"); }
        @Override public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(PartyIdentifierFaultPort.class, FixedPartyIdentifierRegistrationProducer.class,
                    FixedPartyIdentifierLifecycleProducer.class, FixedPartyIdentifierPrimaryProducer.class);
        }
    }
}
