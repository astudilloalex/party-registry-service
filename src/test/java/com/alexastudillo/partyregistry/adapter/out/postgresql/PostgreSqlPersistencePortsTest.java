package com.alexastudillo.partyregistry.adapter.out.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alexastudillo.partyregistry.application.ApplicationFailure;
import com.alexastudillo.partyregistry.application.CountryEvidence;
import com.alexastudillo.partyregistry.application.IdentifierMutation;
import com.alexastudillo.partyregistry.application.IdentifierMutationIntent;
import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.NationalityMutationIntent;
import com.alexastudillo.partyregistry.application.OutboxClaim;
import com.alexastudillo.partyregistry.application.OutboxIntent;
import com.alexastudillo.partyregistry.application.PageRequest;
import com.alexastudillo.partyregistry.application.PartyCreationMutation;
import com.alexastudillo.partyregistry.application.PartyDetailsInput;
import com.alexastudillo.partyregistry.application.PartyDetailsMutation;
import com.alexastudillo.partyregistry.application.PartyMutationIntent;
import com.alexastudillo.partyregistry.application.ProtectedIdentifierData;
import com.alexastudillo.partyregistry.application.RecordedOutboxOutcome;
import com.alexastudillo.partyregistry.application.UpdatePartyCommand;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCatalogPort;
import com.alexastudillo.partyregistry.domain.DetailKind;
import com.alexastudillo.partyregistry.domain.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.PartyStatus;
import com.alexastudillo.partyregistry.domain.PartyType;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T037 requirement/port traceability (all calls use {@link PostgreSqlAdapters#create}):
 * <ul>
 *   <li>FR-001/006-009/012/013, AC-001/020/045/046: {@code partyPortsCoverQueriesMutationsAuditAndTenantNonDisclosure}.</li>
 *   <li>FR-021-039, AC-008/011/012/074-080: {@code identifierPortsCoverProtectedQueriesMutationsPermanentUniquenessAndCatalog}.</li>
 *   <li>FR-047-050, ADR-002, AC-016/031/032/040: {@code outboxPortCommitsClaimsRejectsStaleOutcomesAndRecoversSameEvent}.</li>
 *   <li>DR-001-006, NFR-001/002: all tests run against the migrated PostgreSQL 18 container using the runtime role.</li>
 * </ul>
 */
final class PostgreSqlPersistencePortsTest extends PostgreSql18TestSupport {
    private static final String RUNTIME_PASSWORD = "synthetic-runtime-test-password";
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-20T10:15:30Z");
    private static final AtomicInteger ACTIVE_TRANSACTIONS = new AtomicInteger();
    private static PgPool pool;
    private static PostgreSqlAdapterBundle ports;

    @BeforeAll
    static void createRuntimeAdapter() throws Exception {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("alter role party_registry_runtime login password '" + RUNTIME_PASSWORD + "'");
        }
        PgConnectOptions connectionOptions = new PgConnectOptions()
                .setHost(CONTAINER.getHost())
                .setPort(CONTAINER.getMappedPort(5432))
                .setDatabase(CONTAINER.getDatabaseName())
                .setUser("party_registry_runtime")
                .setPassword(RUNTIME_PASSWORD);
        pool = PgPool.pool(connectionOptions, new PoolOptions().setMaxSize(6));
        ports = PostgreSqlAdapters.create(
                pool,
                new PostgreSqlAdapterSettings(Duration.ofMinutes(2), 3, 2, "test-outbox-publisher"),
                new PostgreSqlTransactionObserver() {
                    @Override
                    public void opened(String operation) {
                        ACTIVE_TRANSACTIONS.incrementAndGet();
                    }

                    @Override
                    public void completed(String operation) {
                        ACTIVE_TRANSACTIONS.decrementAndGet();
                    }
                });
    }

    @AfterAll
    static void closeRuntimePool() {
        if (pool != null) {
            pool.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    @Test
    void partyPortsCoverQueriesMutationsAuditAndTenantNonDisclosure() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        PartyDetailsInput initialDetails = naturalDetails(partyId, "Synthetic", "Alpha", null);

        MutationResult created = await(ports.partyUnitOfWork().createPartyAndAppendOutbox(new PartyMutationIntent(
                tenant, partyId, 0, PartyCreationMutation.from(PartyType.NATURAL_PERSON, initialDetails), ACTOR,
                outbox("party-created"))));
        assertEquals(partyId, created.resourceId());
        assertEquals(0, created.version());
        assertNull(await(ports.partyQueries().findByTenantAndId(otherTenant, partyId)));
        assertThrows(UnsupportedOperationException.class, () -> ports.partyQueries().findById(partyId));

        var party = await(ports.partyQueries().findByTenantAndId(tenant, partyId));
        assertEquals("Synthetic Alpha", party.displayName());
        assertEquals(ACTOR, party.audit().createdBy());
        assertNotNull(party.audit().createdAt());
        assertEquals(1, await(ports.partyQueries().search(
                tenant, PartyType.NATURAL_PERSON, PartyStatus.DRAFT, new PageRequest(0, 1))).total());
        assertTrue(await(ports.partyQueries().search(
                otherTenant, null, null, PageRequest.defaults())).items().isEmpty());

        var details = await(ports.partyQueries().findDetails(tenant, partyId));
        assertEquals("Synthetic", details.primaryName());
        assertEquals(ACTOR, details.audit().updatedBy());
        assertNull(await(ports.partyQueries().findDetails(otherTenant, partyId)));

        assertEquals(1, await(ports.partyUnitOfWork().updatePartyAndAppendOutbox(partyIntent(
                tenant, partyId, 0, new UpdatePartyCommand("Synthetic Alpha Updated"), "party-updated"))).version());
        assertFailure("VERSION_CONFLICT", ports.partyUnitOfWork().updatePartyAndAppendOutbox(partyIntent(
                tenant, partyId, 0, new UpdatePartyCommand("stale"), "party-stale")));
        assertEquals(2, await(ports.partyUnitOfWork().transitionPartyAndAppendOutbox(partyIntent(
                tenant, partyId, 1, PartyStatus.ACTIVE, "party-activated"))).version());

        PartyDetailsInput revised = naturalDetails(partyId, "Synthetic", "Beta", "Preferred");
        assertEquals(3, await(ports.partyUnitOfWork().updateDetailsAndAppendOutbox(partyIntent(
                tenant, partyId, 2, PartyDetailsMutation.from(revised), "party-details-updated"))).version());
        assertEquals("Synthetic Beta", await(ports.partyQueries().findByTenantAndId(tenant, partyId)).displayName());

        UUID nationalityId = UUID.randomUUID();
        MutationResult nationalityAdded = await(ports.partyUnitOfWork().addNationalityAndAppendOutbox(nationality(
                tenant, partyId, nationalityId, "EC", true, null, 3, "nationality-added")));
        assertEquals(nationalityId, nationalityAdded.resourceId());
        assertNotEquals(partyId, nationalityAdded.resourceId());
        var active = await(ports.partyQueries().findNationality(tenant, partyId, nationalityId));
        assertTrue(active.active());
        assertEquals(ACTOR, active.audit().createdBy());
        assertNull(await(ports.partyQueries().findNationality(otherTenant, partyId, nationalityId)));
        assertEquals(1, await(ports.partyQueries().searchNationalities(
                tenant, partyId, "EC", true, true, new PageRequest(0, 1))).total());

        assertEquals(5, await(ports.partyUnitOfWork().updateNationalityAndAppendOutbox(nationality(
                tenant, partyId, nationalityId, "CO", false, null, 4, "nationality-updated"))).version());
        LocalDate endedOn = LocalDate.of(2026, 8, 20);
        assertEquals(6, await(ports.partyUnitOfWork().endNationalityAndAppendOutbox(nationality(
                tenant, partyId, nationalityId, "CO", false, endedOn, 5, "nationality-ended"))).version());
        var ended = await(ports.partyQueries().findNationality(tenant, partyId, nationalityId));
        assertFalse(ended.active());
        assertEquals(endedOn, ended.validUntil());
        assertEquals(1, await(ports.partyQueries().searchNationalities(
                tenant, partyId, "CO", false, false, PageRequest.defaults())).total());

        try (Connection connection = ownerConnection()) {
            assertEquals(partyId.toString(), queryString(connection,
                    "select aggregate_id::text from party_outbox_events where id = ?", nationalityAdded.outboxEventId()));
            assertEquals(6, queryInt(connection, "select version from parties where id = ?", partyId));
            assertEquals(7, queryInt(connection,
                    "select count(*) from party_outbox_events where tenant_id = ?", tenant));
        }
    }

    @Test
    void identifierPortsCoverProtectedQueriesMutationsPermanentUniquenessAndCatalog() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID partyId;
        UUID schemeId;
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            partyId = insertNaturalParty(connection, tenant);
            schemeId = insertScheme(connection, "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            connection.commit();
        }

        assertEquals(schemeId, await(ports.identifierSchemes().findUsableById(schemeId)).id());
        assertNull(await(ports.identifierSchemes().findUsableById(UUID.randomUUID())));
        assertEquals(List.of("findUsableById"), List.of(IdentifierSchemeCatalogPort.class.getDeclaredMethods()).stream()
                .map(java.lang.reflect.Method::getName).sorted().toList());

        UUID identifierId = UUID.randomUUID();
        IdentifierMutationIntent creation = identifierIntent(tenant, identifierId, partyId, schemeId, 0,
                new IdentifierMutation.Creation("SYNTHETIC", false, LocalDate.of(2020, 1, 1), null, 1), HASH_A,
                "identifier-created");
        assertEquals(0, await(ports.identifierUnitOfWork().createIdentifierAndAppendOutbox(creation)).version());
        var visible = await(ports.identifierQueries().findByTenantAndId(tenant, identifierId));
        var protectedView = await(ports.identifierQueries().findProtectedByTenantAndId(tenant, identifierId));
        assertEquals("synthetic-ciphertext", protectedView.ciphertext());
        assertEquals(HASH_A, visible.normalizedValueHash());
        assertNull(await(ports.identifierQueries().findByTenantAndId(otherTenant, identifierId)));
        assertNull(await(ports.identifierQueries().findProtectedByTenantAndId(otherTenant, identifierId)));
        assertEquals(identifierId, await(ports.identifierQueries().findExact(tenant, schemeId, HASH_A)).getFirst().id());
        assertTrue(await(ports.identifierQueries().findExact(tenant, schemeId, HASH_B)).isEmpty());
        assertEquals(1, await(ports.identifierQueries().search(
                tenant, partyId, schemeId, PartyIdentifierStatus.PENDING_VERIFICATION, false,
                new PageRequest(0, 1))).total());
        assertEquals(1, await(ports.identifierQueries().findByPartyAndScheme(
                tenant, partyId, schemeId, false)).size());
        assertTrue(await(ports.identifierQueries().findByPartyAndScheme(
                tenant, partyId, schemeId, true)).isEmpty());

        var update = new IdentifierMutation.Update("UPDATED", true, LocalDate.of(2021, 1, 1), null);
        assertEquals(1, await(ports.identifierUnitOfWork().updateIdentifierAndAppendOutbox(identifierIntent(
                tenant, identifierId, partyId, schemeId, 0, update, HASH_A, "identifier-updated"))).version());
        assertFailure("VERSION_CONFLICT", ports.identifierUnitOfWork().updateIdentifierAndAppendOutbox(identifierIntent(
                tenant, identifierId, partyId, schemeId, 0, update, HASH_A, "identifier-stale")));
        assertFailure("CONFLICT", ports.identifierUnitOfWork().createIdentifierAndAppendOutbox(identifierIntent(
                tenant, UUID.randomUUID(), partyId, schemeId, 0,
                new IdentifierMutation.Creation(null, false, null, null, 1), HASH_A, "identifier-duplicate")));
        try (Connection connection = ownerConnection()) {
            assertEquals(1, queryInt(connection,
                    "select count(*) from party_identifiers where tenant_id = ? and identifier_scheme_id = ? and normalized_value_hash = ?",
                    tenant, schemeId, HASH_A));
            assertEquals(2, queryInt(connection,
                    "select count(*) from party_outbox_events where tenant_id = ?", tenant));
        }

        var transition = new IdentifierMutation.Transition(
                PartyIdentifierStatus.VERIFIED, OBSERVED_AT, "synthetic-verifier", null);
        assertEquals(2, await(ports.identifierUnitOfWork().transitionIdentifierAndAppendOutbox(identifierIntent(
                tenant, identifierId, partyId, schemeId, 1, transition, HASH_A, "identifier-verified"))).version());
        assertEquals(1, await(ports.identifierQueries().findByPartyAndScheme(
                tenant, partyId, schemeId, true)).size());
    }

    @Test
    void outboxPortCommitsClaimsRejectsStaleOutcomesAndRecoversSameEvent() throws Exception {
        // Isolate the globally claimed queue from events intentionally left by the other port tests.
        try (Connection connection = ownerConnection()) {
            execute(connection, "update party_outbox_events set status = 'PUBLISHED', published_at = now() where status = 'PENDING'");
        }
        UUID tenant = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        MutationResult mutation = await(ports.partyUnitOfWork().createPartyAndAppendOutbox(new PartyMutationIntent(
                tenant, partyId, 0, PartyCreationMutation.from(PartyType.NATURAL_PERSON,
                        naturalDetails(partyId, "Outbox", "Fixture", null)), ACTOR, outbox("outbox-fixture"))));

        assertFalse(await(ports.outboxStore().recordOutcome(
                mutation.outboxEventId(), 99, RecordedOutboxOutcome.PUBLISHED)));
        assertTrue(await(ports.outboxStore().recordOutcome(
                mutation.outboxEventId(), 0, RecordedOutboxOutcome.FAILED)));
        assertNull(await(ports.outboxStore().recoverFailed(mutation.outboxEventId(), 0)));
        var initiallyRecovered = await(ports.outboxStore().recoverFailed(mutation.outboxEventId(), 1));
        assertEquals(mutation.outboxEventId(), initiallyRecovered.eventId());
        assertEquals(2, initiallyRecovered.version());

        assertFailure("VALIDATION_ERROR", ports.outboxStore().claimEligible(0, OBSERVED_AT));
        assertFailure("VALIDATION_ERROR", ports.outboxStore().claimEligible(4, OBSERVED_AT));
        List<OutboxClaim> claims = await(ports.outboxStore().claimEligible(1, OBSERVED_AT));
        OutboxClaim claim = claims.stream().filter(item -> item.eventId().equals(mutation.outboxEventId())).findFirst().orElseThrow();
        assertEquals(0, ACTIVE_TRANSACTIONS.get(), "claim must commit before simulated broker I/O");
        assertFalse(await(ports.outboxStore().recordOutcome(
                claim.eventId(), claim.claimedVersion() - 1, RecordedOutboxOutcome.PUBLISHED)));
        assertTrue(await(ports.outboxStore().recordOutcome(
                claim.eventId(), claim.claimedVersion(), RecordedOutboxOutcome.FAILED)));
        long failedVersion = claim.claimedVersion() + 1;
        assertNull(await(ports.outboxStore().recoverFailed(claim.eventId(), failedVersion - 1)));
        var recovered = await(ports.outboxStore().recoverFailed(claim.eventId(), failedVersion));
        assertEquals(claim.eventId(), recovered.eventId());
        assertEquals(failedVersion + 1, recovered.version());

        OutboxClaim reclaimed = await(ports.outboxStore().claimEligible(1, OBSERVED_AT.plusSeconds(121))).stream()
                .filter(item -> item.eventId().equals(claim.eventId())).findFirst().orElseThrow();
        assertTrue(await(ports.outboxStore().recordOutcome(
                reclaimed.eventId(), reclaimed.claimedVersion(), RecordedOutboxOutcome.PUBLISHED)));
        assertFalse(await(ports.outboxStore().recordOutcome(
                reclaimed.eventId(), reclaimed.claimedVersion(), RecordedOutboxOutcome.PENDING)));
    }

    private static PartyDetailsInput naturalDetails(UUID partyId, String given, String family, String preferred) {
        return new PartyDetailsInput(partyId, DetailKind.NATURAL_PERSON, given, family, preferred, "EC",
                LocalDate.of(1990, 1, 1), null);
    }

    private static PartyMutationIntent partyIntent(
            UUID tenant, UUID partyId, long version, Object mutation, String eventType) {
        return new PartyMutationIntent(tenant, partyId, version, mutation, ACTOR, outbox(eventType));
    }

    private static NationalityMutationIntent nationality(
            UUID tenant, UUID partyId, UUID nationalityId, String countryCode, boolean primary,
            LocalDate validUntil, long version, String eventType) {
        return new NationalityMutationIntent(tenant, partyId, nationalityId, countryCode, primary,
                LocalDate.of(2020, 1, 1), validUntil, new CountryEvidence(countryCode, OBSERVED_AT),
                version, ACTOR, outbox(eventType));
    }

    private static IdentifierMutationIntent identifierIntent(
            UUID tenant, UUID identifierId, UUID partyId, UUID schemeId, long version,
            IdentifierMutation mutation, String hash, String eventType) {
        return new IdentifierMutationIntent(tenant, identifierId, partyId, schemeId,
                new ProtectedIdentifierData("synthetic-ciphertext", hash, "****0000", 1), version,
                mutation, ACTOR, outbox(eventType));
    }

    private static OutboxIntent outbox(String eventType) {
        return new OutboxIntent(eventType, UUID.randomUUID());
    }

    private static <T> T await(java.util.concurrent.CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static void assertFailure(String code, java.util.concurrent.CompletionStage<?> stage) {
        CompletionException failure = assertThrows(CompletionException.class, () -> await(stage));
        assertTrue(failure.getCause() instanceof ApplicationFailure);
        ApplicationFailure applicationFailure = (ApplicationFailure) failure.getCause();
        assertEquals(code, applicationFailure.code());
        assertFalse(applicationFailure.getMessage().contains("uq_"));
        assertFalse(applicationFailure.getMessage().contains(HASH_A));
    }
}
