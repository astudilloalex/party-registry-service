package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.RegisterPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierProtectionPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.application.usecase.PartyIdentifierPreparation;
import com.alexastudillo.partyregistry.application.usecase.RegisterPartyIdentifierUseCase;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSchemePolicy;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Replaces retired adapter regression checks with durable registration, real protection and single-session effects. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(120)
class ReactivePartyIdentifierRegistrationAdapterTest {
    private static final Instant NOW = LocalDate.of(2026, Month.OCTOBER, 9).atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC);
    private static final LocalDate ISSUED = LocalDate.of(2024, Month.JANUARY, 1);
    private static final LocalDate EXPIRES = LocalDate.of(2034, Month.JANUARY, 1);
    private static final String ACTOR = "additional-identifier-test";

    @Inject Mutiny.SessionFactory sessions;
    @Inject RegisterPartyIdentifierUseCase composed;
    @Inject PartyIdentifierMutationReader reader;
    @Inject PartyIdentifierMutationPersistence writes;
    @Inject PartyIdentifierReplayPersistence replay;
    @Inject PartyIdentifierAuditPersistence audit;
    @Inject PartyOutboxEventPersistenceMapper outbox;
    @Inject PartyMutationTimeouts timeouts;
    @Inject PartyIdentifierFingerprintPort fingerprints;
    @Inject IdentifierProtectionPort protection;

    @Test
    @RunOnVertxContext
    void cdiSelectsDurableCreationAndHistoricalReplayForBothPartyTypes(UniAsserter asserter) {
        for (PartyType type : PartyType.values()) {
            var owner = owner(type, new TenantId(UUID.randomUUID()));
            var command = command(owner, "cdi-creation", applicableCode(type), value(), true);
            var accepted = new AtomicReference<PartyIdentifierResult>();
            asserter.execute(() -> seed(owner));
            asserter.execute(() -> composed.executeOutcome(command).invoke(result -> accepted.set(result.identifier())));
            var retry = new RegisterPartyIdentifierCommand(new RequestMetadata(owner.tenant(), "retry-actor", UUID.randomUUID()), command.idempotencyKey(), command.partyId(), command.identifier());
            asserter.assertThat(() -> composed.executeOutcome(retry), result -> {
                assertEquals(PartyIdentifierMutationOutcome.Disposition.REPLAYED, result.disposition());
                assertEquals(accepted.get(), result.identifier()); assertEquals(0, result.identifier().version().value());
            });
            asserter.assertThat(() -> counts(owner), counts -> assertEquals(List.of(1L, 1L, 0L), counts));
        }
    }

    @Test
    @RunOnVertxContext
    void registersDifferentSchemesWithoutChangingAnyPartyOrSubtypeField(UniAsserter asserter) {
        for (PartyType type : PartyType.values()) {
            var owner = owner(type, new TenantId(UUID.randomUUID()));
            var before = new AtomicReference<List<String>>();
            var useCase = workflow("stored-only", false);
            asserter.execute(() -> seed(owner));
            asserter.execute(() -> snapshot(owner).invoke(before::set));
            for (String code : List.of(applicableCode(type), IdentifierSchemeTestFixtures.BOTH_EXPIRING_CODE)) {
                var command = command(owner, code, code, value(), true);
                asserter.assertThat(() -> useCase.executeOutcome(command), result -> {
                    assertEquals(PartyIdentifierMutationOutcome.Disposition.APPLIED, result.disposition());
                    assertEquals(PartyIdentifierStatus.PENDING_VERIFICATION, result.identifier().status());
                    assertEquals(0, result.identifier().version().value()); assertTrue(result.identifier().isPrimary());
                    assertEquals(NOW, result.identifier().createdAt()); assertEquals(NOW, result.identifier().updatedAt());
                });
            }
            asserter.assertThat(() -> counts(owner), counts -> assertEquals(List.of(2L, 2L, 2L), counts));
            asserter.assertThat(() -> snapshot(owner), current -> assertEquals(before.get(), current));
        }
    }

    @Test
    @RunOnVertxContext
    void pendingVerifiedAndPastDateEvidenceKeepTheirActiveValueReservation(UniAsserter asserter) {
        for (PartyIdentifierStatus state : List.of(PartyIdentifierStatus.PENDING_VERIFICATION, PartyIdentifierStatus.VERIFIED)) {
            var tenant = new TenantId(UUID.randomUUID());
            var original = owner(PartyType.NATURAL_PERSON, tenant);
            var other = owner(PartyType.NATURAL_PERSON, tenant);
            var useCase = workflow("stored-only", false);
            String value = value();
            var created = new AtomicReference<PartyIdentifierResult>();
            asserter.execute(() -> seed(original)); asserter.execute(() -> seed(other));
            asserter.execute(() -> useCase.execute(command(original, "original", applicableCode(original.type()), value, false)).invoke(created::set));
            asserter.execute(() -> historicalState(created.get(), state, true));
            for (Owner owner : List.of(original, other)) {
                asserter.assertFailedWith(() -> useCase.execute(command(owner, "duplicate", applicableCode(owner.type()), value, false)), failure ->
                        assertInstanceOf(ApplicationFailure.IdentifierUniquenessConflict.class, assertInstanceOf(ApplicationException.class, failure).failure()));
            }
            asserter.assertThat(() -> counts(original), counts -> assertEquals(List.of(1L, 1L, 1L), counts));
        }
    }

    @Test
    @RunOnVertxContext
    void terminalHistoryReleasesUniquenessWithoutDeletingOriginalRecords(UniAsserter asserter) {
        for (PartyIdentifierStatus state : List.of(PartyIdentifierStatus.REJECTED, PartyIdentifierStatus.EXPIRED, PartyIdentifierStatus.REVOKED)) {
            var owner = owner(PartyType.NATURAL_PERSON, new TenantId(UUID.randomUUID()));
            var useCase = workflow("stored-only", false);
            String value = value();
            var original = new AtomicReference<PartyIdentifierResult>();
            asserter.execute(() -> seed(owner));
            asserter.execute(() -> useCase.execute(command(owner, "original", applicableCode(owner.type()), value, false)).invoke(original::set));
            asserter.execute(() -> historicalState(original.get(), state, false));
            asserter.assertThat(() -> useCase.execute(command(owner, "replacement", applicableCode(owner.type()), value, false)), result -> {
                assertNotEquals(original.get().identifierId(), result.identifierId()); assertEquals(0, result.version().value());
            });
            asserter.assertThat(() -> sessions.withSession(session -> session.find(PartyIdentifierEntity.class, original.get().identifierId().value())), stored -> assertEquals(state, stored.status()));
            asserter.assertThat(() -> counts(owner), counts -> assertEquals(List.of(2L, 2L, 2L), counts));
        }
    }

    @Test
    @RunOnVertxContext
    void identicalValuesAndKeysRemainIndependentAcrossTenants(UniAsserter asserter) {
        String value = value();
        var useCase = workflow("stored-only", false);
        for (int i = 0; i < 2; i++) {
            var owner = owner(PartyType.NATURAL_PERSON, new TenantId(UUID.randomUUID()));
            asserter.execute(() -> seed(owner));
            asserter.execute(() -> useCase.execute(command(owner, "same-key", applicableCode(owner.type()), value, false)));
            asserter.assertThat(() -> counts(owner), counts -> assertEquals(List.of(1L, 1L, 1L), counts));
        }
    }

    @Test
    @RunOnVertxContext
    void absentCrossTenantAndIneligibleSchemeRequestsHaveNoDurableEffects(UniAsserter asserter) {
        var owner = owner(PartyType.NATURAL_PERSON, new TenantId(UUID.randomUUID()));
        var useCase = workflow("stored-only", false);
        asserter.execute(() -> seed(owner));
        var missing = new RegisterPartyIdentifierCommand(metadata(owner), "missing", new PartyId(UUID.randomUUID()), input(applicableCode(owner.type()), value(), false));
        var crossTenant = new RegisterPartyIdentifierCommand(new RequestMetadata(new TenantId(UUID.randomUUID()), ACTOR, UUID.randomUUID()), "cross-tenant", owner.id(), missing.identifier());
        for (var command : List.of(missing, crossTenant)) {
            asserter.assertFailedWith(() -> useCase.execute(command), failure -> assertInstanceOf(ApplicationFailure.PartyNotFound.class, assertInstanceOf(ApplicationException.class, failure).failure()));
        }
        var codes = List.of("UNKNOWN-FIXTURE", IdentifierSchemeTestFixtures.BOTH_DRAFT_CODE, IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE);
        List<Class<? extends ApplicationFailure>> failures = List.of(ApplicationFailure.UnknownIdentifierScheme.class,
                ApplicationFailure.InactiveIdentifierScheme.class, ApplicationFailure.IncompatibleIdentifierScheme.class);
        for (int index = 0; index < codes.size(); index++) {
            String code = codes.get(index);
            var expected = failures.get(index);
            asserter.assertFailedWith(() -> useCase.execute(command(owner, code, code, value(), false)), failure -> {
                var actual = assertInstanceOf(ApplicationException.class, failure).failure();
                assertInstanceOf(expected, actual);
            });
        }
        asserter.assertThat(() -> counts(owner), counts -> assertEquals(List.of(0L, 0L, 0L), counts));
    }

    @Test
    @RunOnVertxContext
    void allOutboxModesRetainCompletionsAndOnlyEnabledModesStoreSafePendingIntent(UniAsserter asserter) {
        for (String mode : List.of("disabled", "stored-only", "published")) {
            var owner = owner(PartyType.NATURAL_PERSON, new TenantId(UUID.randomUUID()));
            var useCase = workflow(mode, false);
            asserter.execute(() -> seed(owner));
            asserter.execute(() -> useCase.execute(command(owner, "mode", applicableCode(owner.type()), value(), false)));
            asserter.assertThat(() -> counts(owner), counts -> assertEquals(List.of(1L, 1L, mode.equals("disabled") ? 0L : 1L), counts));
            if (!mode.equals("disabled")) {
                asserter.assertThat(() -> sessions.withSession(session -> session.createSelectionQuery("FROM PartyOutboxEventEntity event WHERE event.tenantId = :tenant", PartyOutboxEventEntity.class)
                        .setParameter("tenant", owner.tenant().value()).getSingleResult()), event -> {
                    assertEquals(PartyIdentifierCreatedOutboxCandidate.EVENT_TYPE, event.eventType()); assertEquals(PartyOutboxStatus.PENDING, event.status());
                    assertEquals(0, event.publishAttempts()); assertNull(event.publishedAt());
                    assertEquals(Set.of("partyId", "schemeCode", "status"), event.payload().keySet());
                });
            }
        }
    }

    @Test
    @RunOnVertxContext
    void lateEventFailureRollsBackFlushedIdentifierCompletionAndIntentThenAllowsKeyReuse(UniAsserter asserter) {
        var owner = owner(PartyType.NATURAL_PERSON, new TenantId(UUID.randomUUID()));
        var command = command(owner, "retryable", applicableCode(owner.type()), value(), false);
        var failure = workflow("stored-only", true);
        asserter.execute(() -> seed(owner));
        asserter.assertFailedWith(() -> failure.execute(command), IllegalStateException.class);
        asserter.assertThat(() -> counts(owner), counts -> assertEquals(List.of(0L, 0L, 0L), counts));
        asserter.execute(() -> workflow("stored-only", false).execute(command));
        asserter.assertThat(() -> counts(owner), counts -> assertEquals(List.of(1L, 1L, 1L), counts));
    }

    private RegisterPartyIdentifierUseCase workflow(String mode, boolean failAfterEvent) {
        PartyIdentifierMutationEffects effects = new PartyIdentifierMutationEffects(replay, audit, new PartyIdentifierOutboxPersistence(outbox, mode));
        if (failAfterEvent) {
            // A storage checkpoint proves real flush visibility before a controlled acceptance failure.
            effects = new PartyIdentifierMutationEffects(replay, audit, new PartyIdentifierOutboxPersistence(outbox, mode)) {
                @Override Uni<Void> appendEvent(Mutiny.Session session, OutboxEventCandidate event) {
                    return super.appendEvent(session, event).call(session::flush).chain(() -> Uni.createFrom().failure(new IllegalStateException("Controlled post-event failure")));
                }
            };
        }
        var mutations = new HibernateReactivePartyIdentifierMutationAdapter(sessions, reader, writes, new PartyIdentifierMutationLocks(), effects, timeouts);
        return new RegisterPartyIdentifierUseCase(mutations, fingerprints, new PartyIdentifierPreparation(new IdentifierRuleCatalog(), new IdentifierSchemePolicy(), protection),
                Clock.fixed(NOW, ZoneOffset.UTC), new RecordingOperationObserver());
    }

    private Uni<Void> seed(Owner owner) { return sessions.withTransaction((session, _) -> session.persist(owner.entity()).call(session::flush)); }

    private Uni<List<String>> snapshot(Owner owner) {
        return sessions.withSession(session -> session.createNativeQuery("""
                SELECT cast(to_jsonb(party) AS text), cast(to_jsonb(natural_details) AS text), cast(to_jsonb(legal_details) AS text)
                FROM parties party LEFT JOIN natural_person_details natural_details ON natural_details.party_id = party.id
                LEFT JOIN legal_entity_details legal_details ON legal_details.party_id = party.id WHERE party.id = :id
                """, Object[].class).setParameter("id", owner.id().value()).getSingleResult()
                .map(row -> Arrays.asList((String) row[0], (String) row[1], (String) row[2])));
    }

    private Uni<List<Long>> counts(Owner owner) {
        return sessions.withSession(session -> session.createNativeQuery("""
                SELECT (SELECT count(*) FROM party_identifiers WHERE tenant_id = :tenant),
                       (SELECT count(*) FROM party_identifier_idempotency_records WHERE tenant_id = :tenant),
                       (SELECT count(*) FROM party_outbox_events WHERE tenant_id = :tenant)
                """, Object[].class).setParameter("tenant", owner.tenant().value()).getSingleResult()
                .map(row -> List.of(((Number) row[0]).longValue(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue())));
    }

    private Uni<Void> historicalState(PartyIdentifierResult identifier, PartyIdentifierStatus state, boolean pastDate) {
        return sessions.withTransaction((session, _) -> session.createMutationQuery("""
                UPDATE PartyIdentifierEntity identifier SET status = :state, verifiedAt = :verifiedAt,
                       verifiedBy = :verifiedBy, expiresOn = :expires WHERE id = :id
                """).setParameter("state", state).setParameter("verifiedAt", state == PartyIdentifierStatus.VERIFIED ? NOW : null)
                .setParameter("verifiedBy", state == PartyIdentifierStatus.VERIFIED ? "fixture-verifier" : null)
                .setParameter("expires", pastDate ? LocalDate.of(2026, Month.OCTOBER, 8) : EXPIRES)
                .setParameter("id", identifier.identifierId().value()).executeUpdate().invoke(count -> assertEquals(1, count)).replaceWithVoid());
    }

    private static Owner owner(PartyType type, TenantId tenant) {
        var id = new PartyId(UUID.randomUUID());
        var audit = AuditInfo.initial(NOW.minusSeconds(60), ACTOR);
        var entity = new PartyEntity(id.value(), tenant.value(), type, "Registration owner", PartyRecordStatus.DRAFT, audit, 7);
        if (type == PartyType.NATURAL_PERSON) {
            entity.attachNaturalPersonDetails(new NaturalPersonDetailsEntity(id.value(), new NaturalPersonDetails("Given", "Family", "Preferred",
                    LocalDate.of(1990, Month.JANUARY, 1), null, "EC"), audit));
        } else {
            entity.attachLegalEntityDetails(new LegalEntityDetailsEntity(id.value(), new LegalEntityDetails("Registry Company", "Trading", "LTD", "EC",
                    LocalDate.of(2000, Month.JANUARY, 1), null), audit));
        }
        return new Owner(entity, id, tenant, type);
    }

    private static String applicableCode(PartyType type) { return type == PartyType.NATURAL_PERSON ? IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE : IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE; }
    private static String value() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private static RequestMetadata metadata(Owner owner) { return new RequestMetadata(owner.tenant(), ACTOR, UUID.randomUUID()); }
    private static RegisterPartyIdentifierCommand command(Owner owner, String key, String code, String value, boolean primary) {
        return new RegisterPartyIdentifierCommand(metadata(owner), key, owner.id(), input(code, value, primary));
    }
    private static InitialPartyIdentifierInput input(String code, String value, boolean primary) { return new InitialPartyIdentifierInput(code, value, "AUTHORITY", ISSUED, EXPIRES, primary); }

    /** Retains a fully populated owner with independently versioned subtype/audit data for preservation checks. */
    private record Owner(PartyEntity entity, PartyId id, TenantId tenant, PartyType type) { }
}
