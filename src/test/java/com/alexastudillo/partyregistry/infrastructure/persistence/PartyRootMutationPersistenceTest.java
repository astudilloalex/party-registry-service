package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.PartyActivationEvidence;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies root-only writes, real row locks, precise versions/audit, minimal evidence, and tenant-safe conflict diagnostics. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(90)
class PartyRootMutationPersistenceTest {

    private static final Instant CREATED = LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC);
    private static final Clock CLOCK = Clock.fixed(CREATED.plusSeconds(1), ZoneOffset.UTC);
    private static final String ACTOR = "root-updater";

    @Inject
    Mutiny.SessionFactory sessionFactory;
    @Inject
    PartyRootMutationPersistence persistence;
    @Inject
    PartyRootReader reader;
    @Inject
    NaturalPersonPersistenceMapper naturalMapper;
    @Inject
    LegalEntityPersistenceMapper legalMapper;
    @Inject
    PartyIdentifierPersistenceMapper identifierMapper;

    @Test
    @RunOnVertxContext
    void correctsBothTypesInEveryStatusAndAdvancesIdenticalWritesExactlyOnce(UniAsserter asserter) {
        for (PartyType type : PartyType.values()) {
            for (PartyRecordStatus status : PartyRecordStatus.values()) {
                Party original = fixture(type, status);
                Party first = original.correctDisplayName("  Mixed Áda  ", CLOCK.instant(), ACTOR);
                Party second = first.correctDisplayName("mixed áda", CLOCK.instant(), ACTOR);
                asserter.execute(() -> seed(original));
                rememberRetainedRows(asserter, original);
                asserter.assertThat(() -> mutate(original, party -> party.correctDisplayName("  Mixed Áda  ", CLOCK.instant(), ACTOR)),
                        actual -> assertParty(first, actual));
                asserter.assertThat(() -> mutate(first, party -> party.correctDisplayName("mixed áda", CLOCK.instant(), ACTOR)),
                        actual -> assertParty(second, actual));
                assertStoredAndRetained(asserter, second);
            }
        }
    }

    @Test
    @RunOnVertxContext
    void storesEverySupportedTransitionWithoutTouchingLabelsDetailsOrIdentifiers(UniAsserter asserter) {
        List<Transition> transitions = List.of(new Transition(PartyRecordStatus.DRAFT, PartyLifecycleAction.ACTIVATE),
                new Transition(PartyRecordStatus.ACTIVE, PartyLifecycleAction.DEACTIVATE),
                new Transition(PartyRecordStatus.DRAFT, PartyLifecycleAction.ARCHIVE),
                new Transition(PartyRecordStatus.ACTIVE, PartyLifecycleAction.ARCHIVE),
                new Transition(PartyRecordStatus.INACTIVE, PartyLifecycleAction.ARCHIVE));
        for (PartyType type : PartyType.values()) {
            for (Transition transition : transitions) {
                Party original = fixture(type, transition.initial());
                Party candidate = transition.apply(original);
                asserter.execute(() -> seed(original));
                rememberRetainedRows(asserter, original);
                asserter.assertThat(() -> mutate(original, transition::apply), actual -> assertParty(candidate, actual));
                assertStoredAndRetained(asserter, candidate);
            }
        }
    }

    @Test
    @RunOnVertxContext
    void rejectsStaleConditionalWritesAndConcealsCrossTenantDiagnosticLookups(UniAsserter asserter) {
        Party original = fixture(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        Party winner = original.archive(CLOCK.instant(), ACTOR);
        Party stale = original.correctDisplayName("Losing name", CLOCK.instant(), ACTOR);
        var otherTenant = new TenantId(UUID.randomUUID());
        Party concealed = NaturalPerson.restore(original.partyId(), otherTenant, original.displayName(), original.recordStatus(),
                original.version(), original.auditInfo(), assertInstanceOf(NaturalPerson.class, original).details());
        Party concealedCandidate = concealed.archive(CLOCK.instant(), ACTOR);
        asserter.execute(() -> seed(original));
        asserter.execute(() -> mutate(original, party -> party.archive(CLOCK.instant(), ACTOR)));
        asserter.assertFailedWith(() -> transaction(session -> persistence.persistRoot(
                session, original.tenantId(), original, stale, original.version())), failure -> {
            var application = assertInstanceOf(ApplicationException.class, failure);
            var conflict = assertInstanceOf(ApplicationFailure.ExpectedVersionMismatch.class, application.failure());
            assertEquals(original.version(), conflict.expectedVersion());
            assertEquals(winner.version(), conflict.currentVersion());
        });
        asserter.assertFailedWith(() -> transaction(session -> persistence.persistRoot(
                session, otherTenant, concealed, concealedCandidate, concealed.version())), failure -> {
            var application = assertInstanceOf(ApplicationException.class, failure);
            var absent = assertInstanceOf(ApplicationFailure.PartyNotFound.class, application.failure());
            assertEquals(otherTenant, absent.tenantId());
        });
        asserter.assertThat(() -> reader.findDetails(original.tenantId(), original.partyId()),
                result -> assertEquals(PartyDetailsResult.fromAggregate(winner), result.orElseThrow()));
    }

    @Test
    @RunOnVertxContext
    void locksOnlyTheQualifiedRootAndDoesNotLockAnUnownedParty(UniAsserter asserter) {
        Party original = fixture(PartyType.LEGAL_ENTITY, PartyRecordStatus.DRAFT);
        var otherTenant = new TenantId(UUID.randomUUID());
        var missing = new PartyId(UUID.randomUUID());
        asserter.execute(() -> seed(original));
        asserter.assertThat(() -> transaction(session -> persistence.findForUpdate(session, original.tenantId(), missing)),
                result -> assertTrue(result.isEmpty()));
        asserter.execute(() -> transaction(session -> persistence.findForUpdate(session, original.tenantId(), original.partyId())
                .invoke(result -> assertTrue(result.isPresent()))
                .chain(() -> transaction(other -> persistence.findForUpdate(other, otherTenant, original.partyId())
                        .invoke(result -> assertTrue(result.isEmpty()))))));
        asserter.assertFailedWith(() -> transaction(session -> persistence.findForUpdate(session, original.tenantId(), original.partyId())
                .chain(() -> transaction(other -> other.createNativeQuery("""
                        select id from parties where tenant_id = :tenant and id = :id for update nowait
                        """, UUID.class).setParameter("tenant", original.tenantId().value())
                        .setParameter("id", original.partyId().value()).getSingleResult()))), failure -> assertSqlState(failure, "55P03"));
        asserter.assertThat(() -> mutate(original, party -> party.archive(CLOCK.instant(), ACTOR)),
                actual -> assertEquals(1, actual.version().value()));
    }

    @Test
    @RunOnVertxContext
    void readsMinimalEvidenceOnlyForTheBoundTenantAndParty(UniAsserter asserter) {
        Party original = fixture(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        Party other = fixture(PartyType.LEGAL_ENTITY, PartyRecordStatus.ACTIVE);
        asserter.execute(() -> seed(original));
        asserter.execute(() -> seed(other));
        asserter.assertThat(() -> transaction(session -> persistence.activationEvidence(session, original.tenantId(), original.partyId())),
                evidence -> assertEquals(List.of(new PartyActivationEvidence(original.tenantId(), original.partyId(),
                        new IdentifierSchemeId(IdentifierSchemeTestFixtures.BOTH_DEPRECATED_ID),
                        new IdentifierSchemeId(IdentifierSchemeTestFixtures.BOTH_DEPRECATED_ID),
                        PartyIdentifierStatus.VERIFIED, null, IdentifierSubjectType.BOTH)), evidence));
        asserter.assertThat(() -> transaction(session -> persistence.activationEvidence(session, other.tenantId(), original.partyId())),
                evidence -> assertTrue(evidence.isEmpty()));
        asserter.assertThat(() -> transaction(session -> persistence.activationEvidence(session, original.tenantId(), other.partyId())),
                evidence -> assertTrue(evidence.isEmpty()));
    }

    @Test
    @RunOnVertxContext
    void rejectsInconsistentCandidatesWithoutPartialWrites(UniAsserter asserter) {
        var original = assertInstanceOf(NaturalPerson.class, fixture(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT));
        Party correct = original.correctDisplayName("Valid name", CLOCK.instant(), ACTOR);
        Party alteredDetails = NaturalPerson.restore(original.partyId(), original.tenantId(), correct.displayName(),
                correct.recordStatus(), correct.version(), correct.auditInfo(),
                new NaturalPersonDetails("Changed", "Details", null, null, null, "GB"));
        Party bothFields = NaturalPerson.restore(original.partyId(), original.tenantId(), correct.displayName(),
                PartyRecordStatus.ARCHIVED, correct.version(), correct.auditInfo(), original.details());
        Party imprecise = original.correctDisplayName("Valid name", CLOCK.instant().plusNanos(1), ACTOR);
        Party wrongOwner = NaturalPerson.restore(original.partyId(), new TenantId(UUID.randomUUID()), correct.displayName(),
                correct.recordStatus(), correct.version(), correct.auditInfo(), original.details());
        asserter.execute(() -> seed(original));
        for (Party candidate : List.of(original, alteredDetails, bothFields, imprecise, wrongOwner)) {
            asserter.assertFailedWith(() -> transaction(session -> persistence.findForUpdate(session, original.tenantId(), original.partyId())
                    .chain(locked -> persistence.persistRoot(session, original.tenantId(), locked.orElseThrow(), candidate, original.version()))),
                    PartyRootMutationPersistenceTest::assertInternal);
        }
        asserter.assertThat(() -> reader.findDetails(original.tenantId(), original.partyId()),
                result -> assertEquals(PartyDetailsResult.fromAggregate(original), result.orElseThrow()));
    }

    @Test
    @RunOnVertxContext
    void corruptDetailStructureFailsInternallyWhileCrossTenantReadsStayEmpty(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        var otherTenant = new TenantId(UUID.randomUUID());
        var id = new PartyId(UUID.randomUUID());
        var root = new PartyEntity(id.value(), tenant.value(), PartyType.NATURAL_PERSON, "Incomplete root",
                PartyRecordStatus.DRAFT, AuditInfo.initial(CREATED, "creator"), 0);
        asserter.execute(() -> transaction(session -> session.persist(root)));
        asserter.assertFailedWith(() -> transaction(session -> persistence.findForUpdate(session, tenant, id)),
                PartyRootMutationPersistenceTest::assertInternal);
        asserter.assertThat(() -> transaction(session -> persistence.findForUpdate(session, otherTenant, id)),
                result -> assertTrue(result.isEmpty()));
    }

    private Uni<Party> mutate(Party expected, Function<Party, Party> change) {
        return transaction(session -> persistence.findForUpdate(session, expected.tenantId(), expected.partyId())
                .chain(found -> {
                    Party locked = found.orElseThrow();
                    return persistence.persistRoot(session, expected.tenantId(), locked, change.apply(locked), expected.version());
                }).call(session::flush));
    }

    private void rememberRetainedRows(UniAsserter asserter, Party party) {
        asserter.execute(() -> retainedRows(party).invoke(rows -> asserter.putData(party.partyId().toString(), rows)));
    }

    private void assertStoredAndRetained(UniAsserter asserter, Party expected) {
        asserter.assertThat(() -> reader.findDetails(expected.tenantId(), expected.partyId()),
                result -> assertEquals(PartyDetailsResult.fromAggregate(expected), result.orElseThrow()));
        asserter.assertThat(() -> retainedRows(expected), rows -> assertEquals(asserter.getData(expected.partyId().toString()), rows));
    }

    private Uni<String> retainedRows(Party party) {
        return transaction(session -> session.createNativeQuery("""
                select jsonb_build_object(
                    'natural', (select to_jsonb(d) from natural_person_details d where d.party_id = p.id),
                    'legal', (select to_jsonb(d) from legal_entity_details d where d.party_id = p.id),
                    'nationalities', (select jsonb_agg(to_jsonb(n) order by n.id) from party_nationalities n where n.party_id = p.id),
                    'identifiers', (select jsonb_agg(to_jsonb(i) order by i.id) from party_identifiers i
                                    where i.tenant_id = p.tenant_id and i.party_id = p.id))::text
                from parties p where p.tenant_id = :tenant and p.id = :id
                """, String.class).setParameter("tenant", party.tenantId().value()).setParameter("id", party.partyId().value())
                .getSingleResult());
    }

    private Uni<Void> seed(Party party) {
        PartyEntity entity = switch (party) {
            case NaturalPerson natural -> naturalMapper.toEntity(natural);
            case LegalEntity legal -> legalMapper.toEntity(legal);
        };
        var identifier = PartyIdentifier.builder().identifierId(new PartyIdentifierId(UUID.randomUUID()))
                .tenantId(party.tenantId()).partyId(party.partyId())
                .identifierSchemeId(new IdentifierSchemeId(IdentifierSchemeTestFixtures.BOTH_DEPRECATED_ID))
                .protectedValue(new ProtectedIdentifierValue("v1.test-ciphertext", 1,
                        UUID.randomUUID().toString().replace("-", "").repeat(2), "***1234", new IdentifierRuleVersion(1)))
                .status(PartyIdentifierStatus.VERIFIED).verifiedAt(CREATED).verifiedBy("verifier")
                .primary(false).version(PartyIdentifierVersion.initial()).auditInfo(party.auditInfo()).build();
        return transaction(session -> session.persist(entity).chain(() -> session.persist(identifierMapper.toEntity(identifier)))
                .call(session::flush)
                .chain(() -> session.createNativeQuery("""
                        insert into party_nationalities (party_id, country_code, is_primary, created_at, updated_at, created_by, updated_by)
                        values (:party, 'GB', true, :created, :created, 'nationality-fixture', 'nationality-fixture')
                        """).setParameter("party", party.partyId().value()).setParameter("created", CREATED).executeUpdate().replaceWithVoid()));
    }

    private <T> Uni<T> transaction(Function<Mutiny.Session, Uni<T>> work) {
        return sessionFactory.openSession().flatMap(session -> session.withTransaction(transaction ->
                work.apply(session).ifNoItem().after(Duration.ofSeconds(10)).fail()).eventually(session::close));
    }

    private static Party fixture(PartyType type, PartyRecordStatus status) {
        var tenant = new TenantId(UUID.randomUUID());
        var id = new PartyId(UUID.randomUUID());
        var audit = AuditInfo.initial(CREATED, "historical-creator");
        return switch (type) {
            case NATURAL_PERSON -> NaturalPerson.restore(id, tenant, " Historical Label ", status, PartyVersion.initial(), audit,
                    new NaturalPersonDetails("Mixed", "Case", " Old ", LocalDate.of(1990, Month.JANUARY, 1), null, "GB"));
            case LEGAL_ENTITY -> LegalEntity.restore(id, tenant, " Historical Company ", status, PartyVersion.initial(), audit,
                    new LegalEntityDetails("Mixed Company", " Old Trade ", "Ltd", "GB", LocalDate.of(2000, Month.JANUARY, 1), null));
        };
    }

    private static void assertParty(Party expected, Party actual) {
        assertEquals(PartyDetailsResult.fromAggregate(expected), PartyDetailsResult.fromAggregate(actual));
        assertEquals(0, actual.auditInfo().updatedAt().getNano() % 1_000);
    }

    private static void assertInternal(Throwable failure) {
        var application = assertInstanceOf(ApplicationException.class, failure);
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, application.failure());
        assertEquals("Persistence operation failed", application.getMessage());
    }

    private static void assertSqlState(Throwable failure, String state) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof SQLException sql && state.equals(sql.getSQLState())) {
                return;
            }
            cause = cause.getCause();
        }
        throw new AssertionError("Expected PostgreSQL lock rejection", failure);
    }

    /** Supplies supported Domain transitions while persistence remains independent of the transition matrix. */
    private record Transition(PartyRecordStatus initial, PartyLifecycleAction action) {

        Party apply(Party party) {
            return switch (action) {
                case ACTIVATE -> party.activate(CLOCK.instant(), ACTOR);
                case DEACTIVATE -> party.deactivate(CLOCK.instant(), ACTOR);
                case ARCHIVE -> party.archive(CLOCK.instant(), ACTOR);
            };
        }
    }
}
