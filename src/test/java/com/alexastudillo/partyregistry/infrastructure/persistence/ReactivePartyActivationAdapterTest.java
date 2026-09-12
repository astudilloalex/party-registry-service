package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.ActivatePartyCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.PartyActivatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyActivationPort;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.usecase.ActivatePartyUseCase;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
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
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies tenant-scoped Party activation, rollback, and concurrency against PostgreSQL.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(90)
class ReactivePartyActivationAdapterTest {

    private static final Duration REACTIVE_TIMEOUT = Duration.ofSeconds(15);
    private static final Instant CREATED_AT = Instant.parse("2026-09-04T09:00:00.123456Z");
    private static final Instant ACTIVATED_AT = Instant.parse("2026-09-04T10:00:00.654321Z");
    private static final LocalDate EVALUATED_ON = LocalDate.of(2026, 9, 4);
    private static final LocalDate FUTURE_EXPIRATION = EVALUATED_ON.plusYears(5);
    private static final String CREATOR = "activation-fixture";
    private static final String ACTIVATOR = "activation-operator";

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    NaturalPersonPersistenceMapper naturalPersonMapper;

    @Inject
    LegalEntityPersistenceMapper legalEntityMapper;

    @Inject
    PartyIdentifierPersistenceMapper identifierMapper;

    @Inject
    IdentifierSchemePersistenceMapper schemeMapper;

    @Inject
    PartyOutboxEventPersistenceMapper outboxMapper;

    @Inject
    OperationObservationPort observationPort;

    @Test
    @RunOnVertxContext
    void activatesNaturalAndLegalPartiesWithIncrementedAuditAndSafeEvents(UniAsserter asserter) {
        ActivationFixture natural = fixture(
                naturalParty(tenantId(), PartyRecordStatus.DRAFT, "Ada Lovelace"),
                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                PartyIdentifierStatus.VERIFIED,
                FUTURE_EXPIRATION,
                false);
        ActivationFixture legal = fixture(
                legalParty(tenantId(), PartyRecordStatus.DRAFT, "Analytical Engines Ltd"),
                IdentifierSchemeTestFixtures.LEGAL_ACTIVE_ID,
                PartyIdentifierStatus.VERIFIED,
                FUTURE_EXPIRATION,
                false);
        PartyActivationPort port = activationPort("stored-only");

        asserter.execute(() -> timed(persistFixtures(List.of(natural, legal))));
        asserter.assertThat(
                () -> timed(activate(port, natural.party(), PartyVersion.initial())),
                result -> assertSuccessfulActivation(natural.party(), result));
        asserter.assertThat(
                () -> timed(activate(port, legal.party(), PartyVersion.initial())),
                result -> assertSuccessfulActivation(legal.party(), result));
        asserter.assertThat(
                () -> timed(loadParty(natural.party().tenantId(), natural.party().partyId())),
                actual -> assertPersistedActivation(natural.party(), actual));
        asserter.assertThat(
                () -> timed(loadParty(legal.party().tenantId(), legal.party().partyId())),
                actual -> assertPersistedActivation(legal.party(), actual));
        asserter.assertThat(
                () -> timed(loadActivationEvents(natural.party().partyId())),
                events -> assertOneSafeEvent(events, natural.party().type()));
        asserter.assertThat(
                () -> timed(loadActivationEvents(legal.party().partyId())),
                events -> assertOneSafeEvent(events, legal.party().type()));
    }

    @Test
    @RunOnVertxContext
    void rejectsPendingExpiredIncompatibleAndAbsentEvidenceWithoutPartialWrites(
            UniAsserter asserter) {
        TenantId tenantId = tenantId();
        List<ActivationFixture> fixtures = List.of(
                fixture(
                        naturalParty(tenantId, PartyRecordStatus.DRAFT, "Pending Evidence"),
                        IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                        PartyIdentifierStatus.PENDING_VERIFICATION,
                        FUTURE_EXPIRATION,
                        true),
                fixture(
                        naturalParty(tenantId, PartyRecordStatus.DRAFT, "Expired Evidence"),
                        IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                        PartyIdentifierStatus.VERIFIED,
                        EVALUATED_ON.minusDays(1),
                        true),
                fixture(
                        naturalParty(tenantId, PartyRecordStatus.DRAFT, "Incompatible Evidence"),
                        IdentifierSchemeTestFixtures.LEGAL_ACTIVE_ID,
                        PartyIdentifierStatus.VERIFIED,
                        FUTURE_EXPIRATION,
                        true),
                new ActivationFixture(
                        naturalParty(tenantId, PartyRecordStatus.DRAFT, "Absent Evidence"),
                        List.of()));
        PartyActivationPort port = activationPort("stored-only");

        asserter.execute(() -> timed(persistFixtures(fixtures)));
        fixtures.forEach(fixture -> asserter.assertThat(
                () -> timed(attempt(port, fixture.party().tenantId(), fixture.party().partyId(),
                        PartyVersion.initial())),
                attempt -> assertInstanceOf(
                        ApplicationFailure.MissingQualifyingIdentifier.class,
                        attempt.failure())));
        fixtures.forEach(fixture -> asserter.assertThat(
                () -> timed(loadParty(fixture.party().tenantId(), fixture.party().partyId())),
                actual -> assertPartyUnchanged(fixture.party(), actual)));
        asserter.assertThat(
                () -> timed(countActivationEvents(fixtures.stream()
                        .map(ActivationFixture::party)
                        .map(Party::partyId)
                        .toList())),
                count -> assertEquals(0L, count));
    }

    @Test
    @RunOnVertxContext
    void acceptsCompatibleVerifiedEvidenceUnderEverySchemeLifecycle(UniAsserter asserter) {
        TenantId tenantId = tenantId();
        List<UUID> schemeIds = List.of(
                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                IdentifierSchemeTestFixtures.BOTH_DRAFT_ID,
                IdentifierSchemeTestFixtures.BOTH_DEPRECATED_ID,
                IdentifierSchemeTestFixtures.BOTH_RETIRED_ID);
        List<ActivationFixture> fixtures = new ArrayList<>();
        for (UUID schemeId : schemeIds) {
            fixtures.add(fixture(
                    naturalParty(
                            tenantId,
                            PartyRecordStatus.DRAFT,
                            "Scheme Lifecycle " + schemeId),
                    schemeId,
                    PartyIdentifierStatus.VERIFIED,
                    FUTURE_EXPIRATION,
                    false));
        }
        PartyActivationPort port = activationPort("disabled");

        asserter.execute(() -> timed(persistFixtures(fixtures)));
        fixtures.forEach(fixture -> asserter.assertThat(
                () -> timed(activate(port, fixture.party(), PartyVersion.initial())),
                result -> assertSuccessfulActivation(fixture.party(), result)));
        asserter.assertThat(
                () -> timed(countActivationEvents(fixtures.stream()
                        .map(ActivationFixture::party)
                        .map(Party::partyId)
                        .toList())),
                count -> assertEquals(0L, count));
    }

    @Test
    @RunOnVertxContext
    void concealsAbsentAndCrossTenantPartiesBeforeVersionLifecycleAndEvidenceChecks(
            UniAsserter asserter) {
        TenantId owningTenant = tenantId();
        TenantId requestingTenant = tenantId();
        Party existing = naturalParty(owningTenant, PartyRecordStatus.ACTIVE, "Concealed Party");
        PartyId absentPartyId = partyId();
        PartyActivationPort port = activationPort("stored-only");

        asserter.execute(() -> timed(persistFixtures(List.of(
                new ActivationFixture(existing, List.of())))));
        asserter.assertThat(
                () -> timed(attempt(port, requestingTenant, existing.partyId(), new PartyVersion(1))),
                attempt -> assertPartyNotFound(attempt, requestingTenant, existing.partyId()));
        asserter.assertThat(
                () -> timed(attempt(port, requestingTenant, absentPartyId, new PartyVersion(1))),
                attempt -> assertPartyNotFound(attempt, requestingTenant, absentPartyId));
        asserter.assertThat(
                () -> timed(loadParty(owningTenant, existing.partyId())),
                actual -> assertPartyUnchanged(existing, actual));
        asserter.assertThat(
                () -> timed(loadActivationEvents(existing.partyId())),
                events -> assertTrue(events.isEmpty()));
    }

    @Test
    @RunOnVertxContext
    void enforcesStaleThenLifecycleThenEligibilityForCombinedFailures(UniAsserter asserter) {
        TenantId tenantId = tenantId();
        Party staleAndInvalidAndIneligible = naturalParty(
                tenantId,
                PartyRecordStatus.ACTIVE,
                "Stale Invalid Ineligible");
        Party invalidAndIneligible = naturalParty(
                tenantId,
                PartyRecordStatus.ACTIVE,
                "Invalid Ineligible");
        Party ineligible = naturalParty(
                tenantId,
                PartyRecordStatus.DRAFT,
                "Ineligible");
        List<ActivationFixture> fixtures = List.of(
                new ActivationFixture(staleAndInvalidAndIneligible, List.of()),
                new ActivationFixture(invalidAndIneligible, List.of()),
                new ActivationFixture(ineligible, List.of()));
        PartyActivationPort port = activationPort("stored-only");

        asserter.execute(() -> timed(persistFixtures(fixtures)));
        asserter.assertThat(
                () -> timed(attempt(
                        port,
                        tenantId,
                        staleAndInvalidAndIneligible.partyId(),
                        new PartyVersion(1))),
                attempt -> {
                    ApplicationFailure.StalePartyVersion failure = assertInstanceOf(
                            ApplicationFailure.StalePartyVersion.class,
                            attempt.failure());
                    assertEquals(new PartyVersion(1), failure.expectedVersion());
                    assertEquals(PartyVersion.initial(), failure.currentVersion());
                });
        asserter.assertThat(
                () -> timed(attempt(
                        port,
                        tenantId,
                        invalidAndIneligible.partyId(),
                        PartyVersion.initial())),
                attempt -> assertInstanceOf(
                        ApplicationFailure.InvalidPartyLifecycle.class,
                        attempt.failure()));
        asserter.assertThat(
                () -> timed(attempt(port, tenantId, ineligible.partyId(), PartyVersion.initial())),
                attempt -> assertInstanceOf(
                        ApplicationFailure.MissingQualifyingIdentifier.class,
                        attempt.failure()));
        fixtures.forEach(fixture -> asserter.assertThat(
                () -> timed(loadParty(tenantId, fixture.party().partyId())),
                actual -> assertPartyUnchanged(fixture.party(), actual)));
        asserter.assertThat(
                () -> timed(countActivationEvents(fixtures.stream()
                        .map(ActivationFixture::party)
                        .map(Party::partyId)
                        .toList())),
                count -> assertEquals(0L, count));
    }

    @Test
    @RunOnVertxContext
    void storesNoneForDisabledAndOnePendingEventForStoredAndPublishedModes(
            UniAsserter asserter) {
        TenantId tenantId = tenantId();
        List<ActivationFixture> fixtures = List.of(
                fixture(
                        naturalParty(tenantId, PartyRecordStatus.DRAFT, "Disabled Event"),
                        IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                        PartyIdentifierStatus.VERIFIED,
                        FUTURE_EXPIRATION,
                        false),
                fixture(
                        naturalParty(tenantId, PartyRecordStatus.DRAFT, "Stored Event"),
                        IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                        PartyIdentifierStatus.VERIFIED,
                        FUTURE_EXPIRATION,
                        false),
                fixture(
                        naturalParty(tenantId, PartyRecordStatus.DRAFT, "Published Mode Event"),
                        IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                        PartyIdentifierStatus.VERIFIED,
                        FUTURE_EXPIRATION,
                        false));

        asserter.execute(() -> timed(persistFixtures(fixtures)));
        asserter.execute(() -> timed(activate(
                activationPort("disabled"),
                fixtures.get(0).party(),
                PartyVersion.initial()).replaceWithVoid()));
        asserter.execute(() -> timed(activate(
                activationPort("stored-only"),
                fixtures.get(1).party(),
                PartyVersion.initial()).replaceWithVoid()));
        asserter.execute(() -> timed(activate(
                activationPort("published"),
                fixtures.get(2).party(),
                PartyVersion.initial()).replaceWithVoid()));
        asserter.assertThat(
                () -> timed(loadActivationEvents(fixtures.get(0).party().partyId())),
                events -> assertTrue(events.isEmpty()));
        asserter.assertThat(
                () -> timed(loadActivationEvents(fixtures.get(1).party().partyId())),
                events -> assertOneSafeEvent(events, PartyType.NATURAL_PERSON));
        asserter.assertThat(
                () -> timed(loadActivationEvents(fixtures.get(2).party().partyId())),
                events -> assertOneSafeEvent(events, PartyType.NATURAL_PERSON));
    }

    @Test
    @RunOnVertxContext
    void rollsBackPartyAndNewEventWhenTheEnabledOutboxWriteFails(UniAsserter asserter) {
        ActivationFixture fixture = fixture(
                naturalParty(tenantId(), PartyRecordStatus.DRAFT, "Rollback Party"),
                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                PartyIdentifierStatus.VERIFIED,
                FUTURE_EXPIRATION,
                false);
        PartyActivatedOutboxCandidate existingEvent = new PartyActivatedOutboxCandidate(
                UUID.randomUUID(),
                fixture.party().tenantId(),
                fixture.party().partyId(),
                PartyVersion.initial().next(),
                fixture.party().type(),
                PartyRecordStatus.ACTIVE,
                ACTIVATED_AT.minusSeconds(1),
                UUID.randomUUID(),
                CREATOR);
        PartyActivationPort port = activationPort("stored-only");

        asserter.execute(() -> timed(persistFixturesAndEvent(
                List.of(fixture),
                outboxMapper.toEntity(existingEvent))));
        asserter.assertThat(
                () -> timed(attempt(
                        port,
                        fixture.party().tenantId(),
                        fixture.party().partyId(),
                        PartyVersion.initial())),
                attempt -> assertInstanceOf(
                        ApplicationFailure.PersistenceFailure.class,
                        attempt.failure()));
        asserter.assertThat(
                () -> timed(loadParty(fixture.party().tenantId(), fixture.party().partyId())),
                actual -> assertPartyUnchanged(fixture.party(), actual));
        asserter.assertThat(
                () -> timed(loadActivationEvents(fixture.party().partyId())),
                events -> {
                    assertEquals(1, events.size());
                    assertEquals(existingEvent.eventId(), events.getFirst().id());
                });
    }

    @Test
    void concurrentCurrentVersionRequestsHaveOneWinnerAndOnlyStaleLosers() throws Exception {
        ActivationFixture fixture = fixture(
                naturalParty(tenantId(), PartyRecordStatus.DRAFT, "Concurrent Party"),
                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                PartyIdentifierStatus.VERIFIED,
                FUTURE_EXPIRATION,
                false);
        PartyActivationPort port = activationPort("stored-only");

        awaitReactive(() -> persistFixtures(List.of(fixture)));
        Supplier<Uni<ActivationAttempt>> attempt = () -> attempt(
                port,
                fixture.party().tenantId(),
                fixture.party().partyId(),
                PartyVersion.initial());
        List<ActivationAttempt> results = race(List.of(attempt, attempt, attempt));
        assertEquals(1L, results.stream().filter(ActivationAttempt::succeeded).count());
        assertEquals(2L, results.stream()
                .map(ActivationAttempt::failure)
                .filter(ApplicationFailure.StalePartyVersion.class::isInstance)
                .count());
        assertEquals(0L, results.stream()
                .map(ActivationAttempt::failure)
                .filter(ApplicationFailure.InvalidPartyLifecycle.class::isInstance)
                .count());
        results.stream()
                .map(ActivationAttempt::failure)
                .filter(ApplicationFailure.StalePartyVersion.class::isInstance)
                .map(ApplicationFailure.StalePartyVersion.class::cast)
                .forEach(failure -> {
                    assertEquals(PartyVersion.initial(), failure.expectedVersion());
                    assertEquals(PartyVersion.initial().next(), failure.currentVersion());
                });

        Party actual = awaitReactive(() -> loadParty(
                fixture.party().tenantId(),
                fixture.party().partyId()));
        assertEquals(PartyRecordStatus.ACTIVE, actual.recordStatus());
        assertEquals(PartyVersion.initial().next(), actual.version());

        List<PartyOutboxEventEntity> events = awaitReactive(
                () -> loadActivationEvents(fixture.party().partyId()));
        assertOneSafeEvent(events, PartyType.NATURAL_PERSON);
        assertEquals(
                PartyVersion.initial().next().value(),
                events.getFirst().aggregateVersion());
    }

    private PartyActivationPort activationPort(String mode) {
        return new HibernateReactivePartyActivationAdapter(
                sessionFactory,
                naturalPersonMapper,
                legalEntityMapper,
                identifierMapper,
                schemeMapper,
                outboxMapper,
                observationPort,
                mode);
    }

    private Uni<PartyDetailsResult> activate(
            PartyActivationPort port,
            Party party,
            PartyVersion expectedVersion) {
        ActivatePartyUseCase useCase = new ActivatePartyUseCase(
                port,
                Clock.fixed(ACTIVATED_AT, ZoneOffset.UTC),
                observationPort);
        return useCase.execute(new ActivatePartyCommand(
                new RequestMetadata(party.tenantId(), ACTIVATOR, UUID.randomUUID()),
                party.partyId(),
                expectedVersion));
    }

    private Uni<ActivationAttempt> attempt(
            PartyActivationPort port,
            TenantId tenantId,
            PartyId partyId,
            PartyVersion expectedVersion) {
        ActivatePartyUseCase useCase = new ActivatePartyUseCase(
                port,
                Clock.fixed(ACTIVATED_AT, ZoneOffset.UTC),
                observationPort);
        return useCase.execute(new ActivatePartyCommand(
                new RequestMetadata(tenantId, ACTIVATOR, UUID.randomUUID()),
                partyId,
                expectedVersion))
                .map(result -> new ActivationAttempt(result, null))
                .onFailure(ApplicationException.class)
                .recoverWithItem(failure -> new ActivationAttempt(
                        null,
                        failure.failure()));
    }

    private Uni<Void> persistFixtures(List<ActivationFixture> fixtures) {
        return persistFixturesAndEvent(fixtures, null);
    }

    private Uni<Void> persistFixturesAndEvent(
            List<ActivationFixture> fixtures,
            PartyOutboxEventEntity event) {
        return sessionFactory.withTransaction((session, transaction) -> {
            Uni<Void> sequence = Uni.createFrom().voidItem();
            for (ActivationFixture fixture : fixtures) {
                sequence = sequence.call(() -> session.persist(toEntity(fixture.party())));
                for (PartyIdentifier identifier : fixture.identifiers()) {
                    sequence = sequence.call(() -> session.persist(identifierMapper.toEntity(identifier)));
                }
            }
            if (event != null) {
                sequence = sequence.call(() -> session.persist(event));
            }
            return sequence.call(session::flush);
        });
    }

    private PartyEntity toEntity(Party party) {
        return switch (party) {
            case NaturalPerson naturalPerson -> naturalPersonMapper.toEntity(naturalPerson);
            case LegalEntity legalEntity -> legalEntityMapper.toEntity(legalEntity);
        };
    }

    private Uni<Party> loadParty(TenantId tenantId, PartyId partyId) {
        return sessionFactory.withSession(session -> session.createQuery("""
                select distinct party
                from PartyEntity party
                left join fetch party.naturalPersonDetails
                left join fetch party.legalEntityDetails
                where party.tenantId = :tenantId
                  and party.id = :partyId
                """, PartyEntity.class)
                .setParameter("tenantId", tenantId.value())
                .setParameter("partyId", partyId.value())
                .getSingleResult()
                .map(this::toDomain));
    }

    private Party toDomain(PartyEntity entity) {
        return switch (entity.type()) {
            case NATURAL_PERSON -> naturalPersonMapper.toDomain(entity);
            case LEGAL_ENTITY -> legalEntityMapper.toDomain(entity);
        };
    }

    private Uni<List<PartyOutboxEventEntity>> loadActivationEvents(PartyId partyId) {
        return sessionFactory.withSession(session -> session.createQuery("""
                from PartyOutboxEventEntity event
                where event.aggregateId = :partyId
                  and event.eventType = :eventType
                order by event.createdAt
                """, PartyOutboxEventEntity.class)
                .setParameter("partyId", partyId.value())
                .setParameter("eventType", PartyActivatedOutboxCandidate.EVENT_TYPE)
                .getResultList());
    }

    private Uni<Long> countActivationEvents(List<PartyId> partyIds) {
        return sessionFactory.withSession(session -> session.createQuery("""
                select count(event)
                from PartyOutboxEventEntity event
                where event.aggregateId in :partyIds
                  and event.eventType = :eventType
                """, Long.class)
                .setParameter("partyIds", partyIds.stream().map(PartyId::value).toList())
                .setParameter("eventType", PartyActivatedOutboxCandidate.EVENT_TYPE)
                .getSingleResult());
    }

    private static ActivationFixture fixture(
            Party party,
            UUID schemeId,
            PartyIdentifierStatus status,
            LocalDate expiresOn,
            boolean primary) {
        return new ActivationFixture(
                party,
                List.of(identifier(party, schemeId, status, expiresOn, primary)));
    }

    private static PartyIdentifier identifier(
            Party party,
            UUID schemeId,
            PartyIdentifierStatus status,
            LocalDate expiresOn,
            boolean primary) {
        PartyIdentifierId identifierId = new PartyIdentifierId(UUID.randomUUID());
        boolean verified = status == PartyIdentifierStatus.VERIFIED;
        return PartyIdentifier.restore(
                identifierId,
                party.tenantId(),
                party.partyId(),
                new com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId(schemeId),
                new ProtectedIdentifierValue(
                        "v1.test-ciphertext." + identifierId.value(),
                        1,
                        UUID.randomUUID().toString().replace("-", "").repeat(2),
                        "********1234",
                        new IdentifierRuleVersion(1)),
                "TEST-ISSUER",
                EVALUATED_ON.minusYears(1),
                expiresOn,
                primary,
                status,
                verified ? CREATED_AT.plusSeconds(1) : null,
                verified ? "fixture-verifier" : null,
                PartyIdentifierVersion.initial(),
                AuditInfo.initial(CREATED_AT, CREATOR));
    }

    private static NaturalPerson naturalParty(
            TenantId tenantId,
            PartyRecordStatus status,
            String displayName) {
        return NaturalPerson.restore(
                partyId(),
                tenantId,
                displayName,
                status,
                PartyVersion.initial(),
                AuditInfo.initial(CREATED_AT, CREATOR),
                new NaturalPersonDetails(
                        displayName,
                        "Family",
                        "Preferred",
                        LocalDate.of(1990, 1, 1),
                        null,
                        "EC"));
    }

    private static LegalEntity legalParty(
            TenantId tenantId,
            PartyRecordStatus status,
            String displayName) {
        return LegalEntity.restore(
                partyId(),
                tenantId,
                displayName,
                status,
                PartyVersion.initial(),
                AuditInfo.initial(CREATED_AT, CREATOR),
                new LegalEntityDetails(
                        displayName,
                        "Trading Name",
                        "LTD",
                        "EC",
                        LocalDate.of(2000, 1, 1),
                        null));
    }

    private static void assertSuccessfulActivation(Party original, PartyDetailsResult result) {
        assertEquals(original.partyId(), result.partyId());
        assertEquals(original.tenantId(), result.tenantId());
        assertEquals(original.type(), result.type());
        assertEquals(original.displayName(), result.displayName());
        assertEquals(PartyRecordStatus.ACTIVE, result.recordStatus());
        assertEquals(original.version().next(), result.version());
        assertEquals(original.auditInfo().createdAt(), result.createdAt());
        assertEquals(original.auditInfo().createdBy(), result.createdBy());
        assertEquals(ACTIVATED_AT, result.updatedAt());
        assertEquals(ACTIVATOR, result.updatedBy());
        switch (original) {
            case NaturalPerson natural -> {
                NaturalPersonResult naturalResult = assertInstanceOf(NaturalPersonResult.class, result);
                assertEquals(natural.details().givenNames(), naturalResult.givenNames());
                assertEquals(natural.details().familyNames(), naturalResult.familyNames());
            }
            case LegalEntity legal -> {
                LegalEntityResult legalResult = assertInstanceOf(LegalEntityResult.class, result);
                assertEquals(legal.details().legalName(), legalResult.legalName());
                assertEquals(legal.details().incorporationCountryCode(),
                        legalResult.incorporationCountryCode());
            }
        }
    }

    private static void assertPersistedActivation(Party original, Party actual) {
        assertEquals(original.partyId(), actual.partyId());
        assertEquals(original.tenantId(), actual.tenantId());
        assertEquals(original.type(), actual.type());
        assertEquals(original.displayName(), actual.displayName());
        assertEquals(PartyRecordStatus.ACTIVE, actual.recordStatus());
        assertEquals(original.version().next(), actual.version());
        assertEquals(original.auditInfo().createdAt(), actual.auditInfo().createdAt());
        assertEquals(original.auditInfo().createdBy(), actual.auditInfo().createdBy());
        assertEquals(ACTIVATED_AT, actual.auditInfo().updatedAt());
        assertEquals(ACTIVATOR, actual.auditInfo().updatedBy());
        assertMatchingDetails(original, actual);
    }

    private static void assertPartyUnchanged(Party expected, Party actual) {
        assertEquals(expected.partyId(), actual.partyId());
        assertEquals(expected.tenantId(), actual.tenantId());
        assertEquals(expected.type(), actual.type());
        assertEquals(expected.displayName(), actual.displayName());
        assertEquals(expected.recordStatus(), actual.recordStatus());
        assertEquals(expected.version(), actual.version());
        assertEquals(expected.auditInfo(), actual.auditInfo());
        assertMatchingDetails(expected, actual);
    }

    private static void assertMatchingDetails(Party expected, Party actual) {
        switch (expected) {
            case NaturalPerson natural -> assertEquals(
                    natural.details(),
                    assertInstanceOf(NaturalPerson.class, actual).details());
            case LegalEntity legal -> assertEquals(
                    legal.details(),
                    assertInstanceOf(LegalEntity.class, actual).details());
        }
    }

    private static void assertOneSafeEvent(
            List<PartyOutboxEventEntity> events,
            PartyType partyType) {
        assertEquals(1, events.size());
        PartyOutboxEventEntity event = events.getFirst();
        assertEquals(PartyOutboxAggregateType.PARTY, event.aggregateType());
        assertEquals(PartyActivatedOutboxCandidate.EVENT_TYPE, event.eventType());
        assertEquals((short) 1, event.eventSchemaVersion());
        assertEquals(Map.of("partyType", partyType.name(), "status", "ACTIVE"), event.payload());
        assertEquals(PartyOutboxStatus.PENDING, event.status());
        assertEquals(0, event.publishAttempts());
        assertNull(event.publishedAt());
        String payload = event.payload().toString().toLowerCase();
        assertFalse(payload.contains("identifier"));
        assertFalse(payload.contains("value"));
        assertFalse(payload.contains("cipher"));
        assertFalse(payload.contains("hash"));
        assertFalse(payload.contains("key"));
    }

    private static void assertPartyNotFound(
            ActivationAttempt attempt,
            TenantId tenantId,
            PartyId partyId) {
        ApplicationFailure.PartyNotFound failure = assertInstanceOf(
                ApplicationFailure.PartyNotFound.class,
                attempt.failure());
        assertEquals(tenantId, failure.tenantId());
        assertEquals(partyId, failure.partyId());
    }

    private static TenantId tenantId() {
        return new TenantId(UUID.randomUUID());
    }

    private static PartyId partyId() {
        return new PartyId(UUID.randomUUID());
    }

    private static <T> Uni<T> timed(Uni<T> operation) {
        return operation.ifNoItem().after(REACTIVE_TIMEOUT).fail();
    }

    private static <T> List<T> race(List<Supplier<Uni<T>>> operations) throws Exception {
        CountDownLatch ready = new CountDownLatch(operations.size());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = operations.stream()
                    .map(operation -> executor.submit(
                            () -> awaitRaceParticipant(operation, ready, start)))
                    .toList();
            assertTrue(ready.await(REACTIVE_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            start.countDown();
            List<T> results = new ArrayList<>(futures.size());
            for (Future<T> future : futures) {
                results.add(future.get(REACTIVE_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            }
            return List.copyOf(results);
        }
    }

    private static <T> T awaitRaceParticipant(
            Supplier<Uni<T>> operation,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(REACTIVE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent activation did not start in time");
        }
        return awaitReactive(operation);
    }

    private static <T> T awaitReactive(Supplier<Uni<T>> operation) throws Exception {
        try {
            return VertxContextSupport.subscribeAndAwait(() -> timed(operation.get()));
        } catch (Exception exception) {
            throw exception;
        } catch (Throwable failure) {
            throw new AssertionError("Reactive test operation failed", failure);
        }
    }

    /**
     * Groups one immutable Party fixture with its independently persisted evidence.
     */
    private record ActivationFixture(Party party, List<PartyIdentifier> identifiers) {

        private ActivationFixture {
            identifiers = List.copyOf(identifiers);
        }
    }

    /**
     * Captures either one activation result or its typed application failure.
     */
    private record ActivationAttempt(
            PartyDetailsResult result,
            ApplicationFailure failure) {

        boolean succeeded() {
            return result != null;
        }
    }
}
