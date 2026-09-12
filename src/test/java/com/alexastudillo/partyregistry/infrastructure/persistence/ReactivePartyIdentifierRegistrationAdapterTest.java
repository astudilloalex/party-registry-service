package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierRegistrationPort;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies independent additional-identifier transactions against PostgreSQL.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(60)
class ReactivePartyIdentifierRegistrationAdapterTest {

    private static final Duration REACTIVE_TIMEOUT = Duration.ofSeconds(15);
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-04T16:45:00.123456Z");
    private static final LocalDate ISSUED_ON = LocalDate.parse("2024-01-01");
    private static final LocalDate EXPIRES_ON = LocalDate.parse("2034-01-01");
    private static final String USER_ID = "additional-identifier-test";

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    IdentifierSchemePersistenceMapper identifierSchemeMapper;

    @Inject
    PartyIdentifierPersistenceMapper identifierMapper;

    @Inject
    PartyOutboxEventPersistenceMapper outboxMapper;

    @Inject
    OperationObservationPort observationPort;

    @Test
    @RunOnVertxContext
    void registersMultipleEligibleIdentifiersForNaturalAndLegalPartiesWithoutChangingThem(
            UniAsserter asserter) {
        PartyIdentifierRegistrationPort port = registrationPort("stored-only");
        TenantId tenantId = tenantId();
        PartyFixture naturalParty = naturalParty(tenantId, "Ada Lovelace");
        PartyFixture legalParty = legalParty(tenantId, "Analytical Engines Ltd");
        List<PartyIdentifierRegistrationCandidate> candidates = List.of(
                candidate(naturalParty, naturalScheme(), hash(), true),
                candidate(naturalParty, bothExpiringScheme(), hash(), false),
                candidate(legalParty, legalScheme(), hash(), true),
                candidate(legalParty, bothExpiringScheme(), hash(), false));

        asserter.execute(() -> timed(persistParties(List.of(naturalParty, legalParty))));
        candidates.forEach(candidate -> asserter.assertThat(
                () -> timed(port.register(candidate)),
                result -> assertRegistered(candidate, result)));
        asserter.assertThat(
                () -> timed(countIdentifiers(List.of(naturalParty.partyId(), legalParty.partyId()))),
                count -> assertEquals(4L, count));
        asserter.assertThat(
                () -> timed(countOutbox(candidates)),
                count -> assertEquals(4L, count));
        assertPartyUnchanged(asserter, naturalParty);
        assertPartyUnchanged(asserter, legalParty);
    }

    @Test
    @RunOnVertxContext
    void rejectsPendingAndVerifiedActiveDuplicatesWithTheTypedConflict(UniAsserter asserter) {
        PartyIdentifierRegistrationPort port = registrationPort("stored-only");
        TenantId tenantId = tenantId();
        List<PartyFixture> parties = List.of(
                naturalParty(tenantId, "Pending Owner"),
                naturalParty(tenantId, "Pending Duplicate"),
                naturalParty(tenantId, "Verified Owner"),
                naturalParty(tenantId, "Verified Duplicate"));
        String pendingHash = hash();
        String verifiedHash = hash();
        PartyIdentifierRegistrationCandidate pendingOwner = candidate(
                parties.get(0), naturalScheme(), pendingHash, false);
        PartyIdentifierRegistrationCandidate pendingDuplicate = candidate(
                parties.get(1), naturalScheme(), pendingHash, false);
        PartyIdentifierRegistrationCandidate verifiedOwner = candidate(
                parties.get(2), naturalScheme(), verifiedHash, false);
        PartyIdentifierRegistrationCandidate verifiedDuplicate = candidate(
                parties.get(3), naturalScheme(), verifiedHash, false);

        asserter.execute(() -> timed(persistParties(parties)));
        asserter.execute(() -> timed(port.register(pendingOwner).replaceWithVoid()));
        asserter.assertThat(
                () -> timed(attempt(port, pendingDuplicate)),
                this::assertIdentifierConflict);
        asserter.execute(() -> timed(port.register(verifiedOwner).replaceWithVoid()));
        asserter.execute(() -> timed(transitionIdentifier(
                verifiedOwner.identifier().identifierId(),
                PartyIdentifierStatus.VERIFIED)));
        asserter.assertThat(
                () -> timed(attempt(port, verifiedDuplicate)),
                this::assertIdentifierConflict);
        asserter.assertThat(
                () -> timed(countIdentifiers(parties.stream().map(PartyFixture::partyId).toList())),
                count -> assertEquals(2L, count));
        asserter.assertThat(
                () -> timed(countOutbox(List.of(
                        pendingOwner,
                        pendingDuplicate,
                        verifiedOwner,
                        verifiedDuplicate))),
                count -> assertEquals(2L, count));
        parties.forEach(party -> assertPartyUnchanged(asserter, party));
    }

    @Test
    @RunOnVertxContext
    void allowsTheSameStoredHashInDifferentTenants(UniAsserter asserter) {
        PartyIdentifierRegistrationPort port = registrationPort("stored-only");
        PartyFixture firstParty = naturalParty(tenantId(), "First Tenant");
        PartyFixture secondParty = naturalParty(tenantId(), "Second Tenant");
        String sharedHash = hash();
        PartyIdentifierRegistrationCandidate first = candidate(
                firstParty, naturalScheme(), sharedHash, false);
        PartyIdentifierRegistrationCandidate second = candidate(
                secondParty, naturalScheme(), sharedHash, false);

        asserter.execute(() -> timed(persistParties(List.of(firstParty, secondParty))));
        asserter.assertThat(() -> timed(port.register(first)), result -> assertRegistered(first, result));
        asserter.assertThat(() -> timed(port.register(second)), result -> assertRegistered(second, result));
        asserter.assertThat(
                () -> timed(countIdentifiers(List.of(firstParty.partyId(), secondParty.partyId()))),
                count -> assertEquals(2L, count));
        assertPartyUnchanged(asserter, firstParty);
        assertPartyUnchanged(asserter, secondParty);
    }

    @Test
    @RunOnVertxContext
    void allowsReuseAfterEverySchemaTerminalStatus(UniAsserter asserter) {
        PartyIdentifierRegistrationPort port = registrationPort("stored-only");
        TenantId tenantId = tenantId();
        List<PartyIdentifierStatus> terminalStatuses = List.of(
                PartyIdentifierStatus.REJECTED,
                PartyIdentifierStatus.EXPIRED,
                PartyIdentifierStatus.REVOKED);
        List<PartyFixture> parties = new ArrayList<>();
        List<PartyIdentifierRegistrationCandidate> originals = new ArrayList<>();
        List<PartyIdentifierRegistrationCandidate> replacements = new ArrayList<>();
        for (PartyIdentifierStatus status : terminalStatuses) {
            PartyFixture originalParty = naturalParty(tenantId, status + " Owner");
            PartyFixture replacementParty = naturalParty(tenantId, status + " Replacement");
            String sharedHash = hash();
            parties.add(originalParty);
            parties.add(replacementParty);
            originals.add(candidate(originalParty, naturalScheme(), sharedHash, false));
            replacements.add(candidate(replacementParty, naturalScheme(), sharedHash, false));
        }

        asserter.execute(() -> timed(persistParties(parties)));
        for (int index = 0; index < terminalStatuses.size(); index++) {
            PartyIdentifierRegistrationCandidate original = originals.get(index);
            PartyIdentifierRegistrationCandidate replacement = replacements.get(index);
            PartyIdentifierStatus status = terminalStatuses.get(index);
            asserter.execute(() -> timed(port.register(original).replaceWithVoid()));
            asserter.execute(() -> timed(transitionIdentifier(
                    original.identifier().identifierId(),
                    status)));
            asserter.assertThat(
                    () -> timed(port.register(replacement)),
                    result -> assertRegistered(replacement, result));
        }
        asserter.assertThat(
                () -> timed(countIdentifiers(parties.stream().map(PartyFixture::partyId).toList())),
                count -> assertEquals(6L, count));
        asserter.assertThat(
                () -> timed(countOutbox(concat(originals, replacements))),
                count -> assertEquals(6L, count));
        parties.forEach(party -> assertPartyUnchanged(asserter, party));
    }

    @Test
    @RunOnVertxContext
    void concealsCrossTenantAbsentAndChangedTypeParties(UniAsserter asserter) {
        PartyIdentifierRegistrationPort port = registrationPort("stored-only");
        TenantId owningTenant = tenantId();
        PartyFixture existing = naturalParty(owningTenant, "Concealed Party");
        PartyFixture crossTenantView = new PartyFixture(
                existing.entity(),
                existing.partyId(),
                tenantId(),
                existing.type(),
                existing.expected());
        PartyFixture absent = naturalParty(owningTenant, "Absent Party");
        PartyIdentifierRegistrationCandidate crossTenant = candidate(
                crossTenantView, naturalScheme(), hash(), false);
        PartyIdentifierRegistrationCandidate missing = candidate(
                absent, naturalScheme(), hash(), false);
        PartyIdentifierRegistrationCandidate changedType = candidate(
                existing,
                PartyType.LEGAL_ENTITY,
                legalScheme(),
                hash(),
                false);

        asserter.execute(() -> timed(persistParties(List.of(existing))));
        asserter.assertThat(
                () -> timed(attempt(port, crossTenant)),
                attempt -> assertPartyNotFound(attempt, crossTenant));
        asserter.assertThat(
                () -> timed(attempt(port, missing)),
                attempt -> assertPartyNotFound(attempt, missing));
        asserter.assertThat(
                () -> timed(attempt(port, changedType)),
                attempt -> assertPartyNotFound(attempt, changedType));
        asserter.assertThat(
                () -> timed(countIdentifiers(List.of(existing.partyId(), absent.partyId()))),
                count -> assertEquals(0L, count));
        asserter.assertThat(
                () -> timed(countOutbox(List.of(crossTenant, missing, changedType))),
                count -> assertEquals(0L, count));
        assertPartyUnchanged(asserter, existing);
    }

    @Test
    @RunOnVertxContext
    void rejectsInactiveIncompatibleMissingAndMutatedSchemesBeforeWrites(UniAsserter asserter) {
        PartyIdentifierRegistrationPort port = registrationPort("stored-only");
        TenantId tenantId = tenantId();
        List<PartyFixture> parties = List.of(
                naturalParty(tenantId, "Inactive Scheme"),
                naturalParty(tenantId, "Incompatible Scheme"),
                naturalParty(tenantId, "Missing Scheme"),
                naturalParty(tenantId, "Mutated Identity"),
                naturalParty(tenantId, "Mutated Version"));
        IdentifierScheme active = naturalScheme();
        IdentifierScheme missing = copyScheme(
                active,
                new IdentifierSchemeId(UUID.randomUUID()),
                "TEST_MISSING_ADDITIONAL",
                active.version());
        IdentifierScheme mutatedIdentity = copyScheme(
                active,
                active.id(),
                active.code() + "_MUTATED",
                active.version());
        IdentifierScheme mutatedVersion = copyScheme(
                active,
                active.id(),
                active.code(),
                new IdentifierSchemeVersion(1));
        List<PartyIdentifierRegistrationCandidate> candidates = List.of(
                candidate(parties.get(0), draftScheme(), hash(), false),
                candidate(parties.get(1), legalScheme(), hash(), false),
                candidate(parties.get(2), missing, hash(), false),
                candidate(parties.get(3), mutatedIdentity, hash(), false),
                candidate(parties.get(4), mutatedVersion, hash(), false));

        asserter.execute(() -> timed(persistParties(parties)));
        asserter.assertThat(
                () -> timed(attempt(port, candidates.get(0))),
                attempt -> assertInstanceOf(
                        ApplicationFailure.InactiveIdentifierScheme.class,
                        attempt.failure()));
        asserter.assertThat(
                () -> timed(attempt(port, candidates.get(1))),
                attempt -> {
                    ApplicationFailure.IncompatibleIdentifierScheme failure = assertInstanceOf(
                            ApplicationFailure.IncompatibleIdentifierScheme.class,
                            attempt.failure());
                    assertEquals(PartyType.NATURAL_PERSON, failure.partyType());
                });
        for (int index = 2; index < candidates.size(); index++) {
            int candidateIndex = index;
            asserter.assertThat(
                    () -> timed(attempt(port, candidates.get(candidateIndex))),
                    attempt -> assertInstanceOf(
                            ApplicationFailure.InactiveIdentifierScheme.class,
                            attempt.failure()));
        }
        asserter.assertThat(
                () -> timed(countIdentifiers(parties.stream().map(PartyFixture::partyId).toList())),
                count -> assertEquals(0L, count));
        asserter.assertThat(
                () -> timed(countOutbox(candidates)),
                count -> assertEquals(0L, count));
        parties.forEach(party -> assertPartyUnchanged(asserter, party));
    }

    @Test
    @RunOnVertxContext
    void bridgesDisabledStoredOnlyAndPublishedModesWithoutPublishing(UniAsserter asserter) {
        TenantId tenantId = tenantId();
        List<PartyFixture> parties = List.of(
                naturalParty(tenantId, "Disabled Event"),
                naturalParty(tenantId, "Stored Event"),
                naturalParty(tenantId, "Published Mode Event"));
        List<PartyIdentifierRegistrationCandidate> candidates = parties.stream()
                .map(party -> candidate(party, naturalScheme(), hash(), false))
                .toList();

        asserter.execute(() -> timed(persistParties(parties)));
        asserter.execute(() -> timed(registrationPort("disabled")
                .register(candidates.get(0))
                .replaceWithVoid()));
        asserter.execute(() -> timed(registrationPort("stored-only")
                .register(candidates.get(1))
                .replaceWithVoid()));
        asserter.execute(() -> timed(registrationPort("published")
                .register(candidates.get(2))
                .replaceWithVoid()));
        asserter.assertThat(
                () -> timed(loadOutbox(candidates.get(0))),
                events -> assertTrue(events.isEmpty()));
        asserter.assertThat(
                () -> timed(loadOutbox(candidates.get(1))),
                this::assertOneStoredSafeIdentifierEvent);
        asserter.assertThat(
                () -> timed(loadOutbox(candidates.get(2))),
                this::assertOneStoredSafeIdentifierEvent);
        asserter.assertThat(
                () -> timed(countIdentifiers(parties.stream().map(PartyFixture::partyId).toList())),
                count -> assertEquals(3L, count));
        parties.forEach(party -> assertPartyUnchanged(asserter, party));
    }

    @Test
    @RunOnVertxContext
    void rollsBackIdentifierAndEventsWhenALateOutboxWriteFails(UniAsserter asserter) {
        PartyIdentifierRegistrationPort port = registrationPort("stored-only");
        PartyFixture party = naturalParty(tenantId(), "Rollback Party");
        PartyIdentifierRegistrationCandidate original = candidate(
                party, naturalScheme(), hash(), false);
        PartyIdentifierCreatedOutboxCandidate event = assertInstanceOf(
                PartyIdentifierCreatedOutboxCandidate.class,
                original.outboxCandidates().getFirst());
        PartyIdentifierCreatedOutboxCandidate duplicateIdentity =
                new PartyIdentifierCreatedOutboxCandidate(
                        UUID.randomUUID(),
                        event.tenantId(),
                        event.identifierId(),
                        event.identifierVersion(),
                        event.partyId(),
                        event.schemeCode(),
                        event.status(),
                        event.occurredAt(),
                        event.correlationId(),
                        event.createdBy());
        PartyIdentifierRegistrationCandidate candidate =
                new PartyIdentifierRegistrationCandidate(
                        original.requestMetadata(),
                        original.idempotencyKey(),
                        original.partyType(),
                        original.identifier(),
                        original.identifierScheme(),
                        List.of(event, duplicateIdentity));

        asserter.execute(() -> timed(persistParties(List.of(party))));
        asserter.assertThat(
                () -> timed(attempt(port, candidate)),
                attempt -> assertInstanceOf(
                        ApplicationFailure.PersistenceFailure.class,
                        attempt.failure()));
        asserter.assertThat(
                () -> timed(countIdentifiers(List.of(party.partyId()))),
                count -> assertEquals(0L, count));
        asserter.assertThat(
                () -> timed(loadOutbox(candidate)),
                events -> assertTrue(events.isEmpty()));
        assertPartyUnchanged(asserter, party);
    }

    private PartyIdentifierRegistrationPort registrationPort(String mode) {
        return new HibernateReactivePartyIdentifierRegistrationAdapter(
                sessionFactory,
                identifierSchemeMapper,
                identifierMapper,
                outboxMapper,
                observationPort,
                mode);
    }

    private Uni<Void> persistParties(List<PartyFixture> parties) {
        return sessionFactory.withTransaction((session, transaction) -> {
            Uni<Void> sequence = Uni.createFrom().voidItem();
            for (PartyFixture party : parties) {
                sequence = sequence.call(() -> session.persist(party.entity()));
            }
            return sequence.call(session::flush);
        });
    }

    private Uni<Void> transitionIdentifier(
            PartyIdentifierId identifierId,
            PartyIdentifierStatus status) {
        return sessionFactory.withTransaction((session, transaction) -> {
            if (status == PartyIdentifierStatus.VERIFIED) {
                return session.createMutationQuery("""
                        update PartyIdentifierEntity identifier
                        set identifier.status = :status,
                            identifier.verifiedAt = :verifiedAt,
                            identifier.verifiedBy = :verifiedBy
                        where identifier.id = :identifierId
                        """)
                        .setParameter("status", status)
                        .setParameter("verifiedAt", OCCURRED_AT.plusSeconds(1))
                        .setParameter("verifiedBy", "fixture-verifier")
                        .setParameter("identifierId", identifierId.value())
                        .executeUpdate()
                        .invoke(updated -> assertEquals(1, updated))
                        .replaceWithVoid();
            }
            return session.createMutationQuery("""
                    update PartyIdentifierEntity identifier
                    set identifier.status = :status
                    where identifier.id = :identifierId
                    """)
                    .setParameter("status", status)
                    .setParameter("identifierId", identifierId.value())
                    .executeUpdate()
                    .invoke(updated -> assertEquals(1, updated))
                    .replaceWithVoid();
        });
    }

    private Uni<RegistrationAttempt> attempt(
            PartyIdentifierRegistrationPort port,
            PartyIdentifierRegistrationCandidate candidate) {
        return port.register(candidate)
                .map(result -> new RegistrationAttempt(result, null))
                .onFailure(ApplicationException.class)
                .recoverWithItem(failure -> new RegistrationAttempt(
                        null,
                        failure.failure()));
    }

    private Uni<Long> countIdentifiers(List<PartyId> partyIds) {
        return sessionFactory.withSession(session -> session.createQuery("""
                select count(identifier)
                from PartyIdentifierEntity identifier
                where identifier.partyId in :partyIds
                """, Long.class)
                .setParameter("partyIds", partyIds.stream().map(PartyId::value).toList())
                .getSingleResult());
    }

    private Uni<Long> countOutbox(List<PartyIdentifierRegistrationCandidate> candidates) {
        return sessionFactory.withSession(session -> session.createQuery("""
                select count(event)
                from PartyOutboxEventEntity event
                where event.aggregateId in :aggregateIds
                """, Long.class)
                .setParameter("aggregateIds", candidates.stream()
                        .map(PartyIdentifierRegistrationCandidate::identifier)
                        .map(PartyIdentifier::identifierId)
                        .map(PartyIdentifierId::value)
                        .toList())
                .getSingleResult());
    }

    private Uni<List<PartyOutboxEventEntity>> loadOutbox(
            PartyIdentifierRegistrationCandidate candidate) {
        return sessionFactory.withSession(session -> session.createQuery("""
                from PartyOutboxEventEntity event
                where event.aggregateId = :aggregateId
                """, PartyOutboxEventEntity.class)
                .setParameter("aggregateId", candidate.identifier().identifierId().value())
                .getResultList());
    }

    private Uni<PartySnapshot> loadPartySnapshot(PartyId partyId) {
        return sessionFactory.withSession(session -> session.find(PartyEntity.class, partyId.value())
                .flatMap(entity -> {
                    if (entity.type() == PartyType.NATURAL_PERSON) {
                        return session.find(NaturalPersonDetailsEntity.class, partyId.value())
                                .map(details -> snapshot(entity, naturalDetailsState(details)));
                    }
                    return session.find(LegalEntityDetailsEntity.class, partyId.value())
                            .map(details -> snapshot(entity, legalDetailsState(details)));
                }));
    }

    private void assertPartyUnchanged(UniAsserter asserter, PartyFixture party) {
        asserter.assertThat(
                () -> timed(loadPartySnapshot(party.partyId())),
                actual -> assertEquals(party.expected(), actual));
    }

    private void assertIdentifierConflict(RegistrationAttempt attempt) {
        ApplicationFailure.IdentifierUniquenessConflict failure = assertInstanceOf(
                ApplicationFailure.IdentifierUniquenessConflict.class,
                attempt.failure());
        assertEquals(naturalScheme().id(), failure.schemeId());
        assertNull(attempt.result());
    }

    private static void assertPartyNotFound(
            RegistrationAttempt attempt,
            PartyIdentifierRegistrationCandidate candidate) {
        ApplicationFailure.PartyNotFound failure = assertInstanceOf(
                ApplicationFailure.PartyNotFound.class,
                attempt.failure());
        assertEquals(candidate.identifier().partyId(), failure.partyId());
        assertEquals(candidate.identifier().tenantId(), failure.tenantId());
        assertNull(attempt.result());
    }

    private void assertOneStoredSafeIdentifierEvent(List<PartyOutboxEventEntity> events) {
        assertEquals(1, events.size());
        PartyOutboxEventEntity event = events.getFirst();
        assertEquals(PartyIdentifierCreatedOutboxCandidate.EVENT_TYPE, event.eventType());
        assertEquals(PartyOutboxStatus.PENDING, event.status());
        assertEquals(0, event.publishAttempts());
        assertNull(event.publishedAt());
        assertEquals(
                PartyIdentifierStatus.PENDING_VERIFICATION.name(),
                event.payload().get("status"));
        assertFalse(event.payload().keySet().stream().anyMatch(key -> key.contains("value")
                || key.contains("hash")
                || key.contains("key")));
    }

    private static void assertRegistered(
            PartyIdentifierRegistrationCandidate candidate,
            PartyIdentifierResult result) {
        assertEquals(candidate.identifier().identifierId(), result.identifierId());
        assertEquals(candidate.identifier().partyId(), result.partyId());
        assertEquals(candidate.identifierScheme().id(), result.identifierSchemeId());
        assertEquals(candidate.identifierScheme().code(), result.schemeCode());
        assertEquals(PartyIdentifierStatus.PENDING_VERIFICATION, result.status());
        assertEquals(0L, result.version().value());
    }

    private static PartyIdentifierRegistrationCandidate candidate(
            PartyFixture party,
            IdentifierScheme scheme,
            String normalizedValueHash,
            boolean primary) {
        return candidate(
                party,
                party.type(),
                scheme,
                normalizedValueHash,
                primary);
    }

    private static PartyIdentifierRegistrationCandidate candidate(
            PartyFixture party,
            PartyType expectedPartyType,
            IdentifierScheme scheme,
            String normalizedValueHash,
            boolean primary) {
        PartyIdentifierId identifierId = new PartyIdentifierId(UUID.randomUUID());
        PartyIdentifier identifier = PartyIdentifier.builder()
                .identifierId(identifierId)
                .tenantId(party.tenantId())
                .partyId(party.partyId())
                .identifierSchemeId(scheme.id())
                .protectedValue(new ProtectedIdentifierValue(
                        "v1.protected." + identifierId.value(),
                        1,
                        normalizedValueHash,
                        "********" + normalizedValueHash.substring(60),
                        new IdentifierRuleVersion(1)))
                .issuerCode("ISSUER")
                .issuedOn(ISSUED_ON)
                .expiresOn(EXPIRES_ON)
                .primary(primary)
                .created(OCCURRED_AT, USER_ID)
                .build();
        OutboxEventCandidate event = new PartyIdentifierCreatedOutboxCandidate(
                UUID.randomUUID(),
                identifier.tenantId(),
                identifier.identifierId(),
                identifier.version(),
                identifier.partyId(),
                scheme.code(),
                identifier.status(),
                OCCURRED_AT,
                UUID.randomUUID(),
                USER_ID);
        return new PartyIdentifierRegistrationCandidate(
                new RequestMetadata(party.tenantId(), USER_ID, UUID.randomUUID()),
                "additional-" + UUID.randomUUID(),
                expectedPartyType,
                identifier,
                scheme,
                List.of(event));
    }

    private static PartyFixture naturalParty(TenantId tenantId, String displayName) {
        PartyId partyId = partyId();
        AuditInfo audit = AuditInfo.initial(OCCURRED_AT.minusSeconds(60), USER_ID);
        PartyEntity entity = new PartyEntity(
                partyId.value(),
                tenantId.value(),
                PartyType.NATURAL_PERSON,
                displayName,
                PartyRecordStatus.DRAFT,
                audit,
                0);
        NaturalPersonDetailsEntity details = new NaturalPersonDetailsEntity(
                partyId.value(),
                new NaturalPersonDetails(
                        displayName,
                        "Family",
                        "Preferred",
                        LocalDate.parse("1990-01-01"),
                        null,
                        "EC"),
                audit);
        entity.attachNaturalPersonDetails(details);
        return new PartyFixture(
                entity,
                partyId,
                tenantId,
                PartyType.NATURAL_PERSON,
                snapshot(entity, naturalDetailsState(details)));
    }

    private static PartyFixture legalParty(TenantId tenantId, String displayName) {
        PartyId partyId = partyId();
        AuditInfo audit = AuditInfo.initial(OCCURRED_AT.minusSeconds(60), USER_ID);
        PartyEntity entity = new PartyEntity(
                partyId.value(),
                tenantId.value(),
                PartyType.LEGAL_ENTITY,
                displayName,
                PartyRecordStatus.DRAFT,
                audit,
                0);
        LegalEntityDetailsEntity details = new LegalEntityDetailsEntity(
                partyId.value(),
                new LegalEntityDetails(
                        displayName,
                        "Trading Name",
                        "LTD",
                        "EC",
                        LocalDate.parse("2000-01-01"),
                        null),
                audit);
        entity.attachLegalEntityDetails(details);
        return new PartyFixture(
                entity,
                partyId,
                tenantId,
                PartyType.LEGAL_ENTITY,
                snapshot(entity, legalDetailsState(details)));
    }

    private static PartySnapshot snapshot(PartyEntity party, String detailsState) {
        return new PartySnapshot(
                party.id(),
                party.tenantId(),
                party.type(),
                party.displayName(),
                party.recordStatus(),
                party.createdAt(),
                party.createdBy(),
                party.updatedAt(),
                party.updatedBy(),
                party.version(),
                detailsState);
    }

    private static String naturalDetailsState(NaturalPersonDetailsEntity details) {
        return String.join("|",
                details.givenNames(),
                details.familyNames(),
                details.preferredName(),
                String.valueOf(details.birthDate()),
                String.valueOf(details.dateOfDeath()),
                details.birthCountryCode(),
                String.valueOf(details.createdAt()),
                details.createdBy(),
                String.valueOf(details.updatedAt()),
                details.updatedBy());
    }

    private static String legalDetailsState(LegalEntityDetailsEntity details) {
        return String.join("|",
                details.legalName(),
                details.tradeName(),
                details.legalFormCode(),
                details.incorporationCountryCode(),
                String.valueOf(details.incorporatedOn()),
                String.valueOf(details.dissolvedOn()),
                String.valueOf(details.createdAt()),
                details.createdBy(),
                String.valueOf(details.updatedAt()),
                details.updatedBy());
    }

    private static IdentifierScheme naturalScheme() {
        return fixtureScheme(
                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE,
                IdentifierCategory.NATIONAL_ID,
                IdentifierSubjectType.NATURAL_PERSON,
                6,
                20,
                false,
                IdentifierSchemeStatus.ACTIVE);
    }

    private static IdentifierScheme legalScheme() {
        return fixtureScheme(
                IdentifierSchemeTestFixtures.LEGAL_ACTIVE_ID,
                IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE,
                IdentifierCategory.LEGAL_REGISTRATION_NUMBER,
                IdentifierSubjectType.LEGAL_ENTITY,
                6,
                24,
                false,
                IdentifierSchemeStatus.ACTIVE);
    }

    private static IdentifierScheme bothExpiringScheme() {
        return fixtureScheme(
                IdentifierSchemeTestFixtures.BOTH_EXPIRING_ID,
                IdentifierSchemeTestFixtures.BOTH_EXPIRING_CODE,
                IdentifierCategory.PASSPORT,
                IdentifierSubjectType.BOTH,
                6,
                16,
                true,
                IdentifierSchemeStatus.ACTIVE);
    }

    private static IdentifierScheme draftScheme() {
        return fixtureScheme(
                IdentifierSchemeTestFixtures.BOTH_DRAFT_ID,
                IdentifierSchemeTestFixtures.BOTH_DRAFT_CODE,
                IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH,
                1,
                32,
                false,
                IdentifierSchemeStatus.DRAFT);
    }

    private static IdentifierScheme fixtureScheme(
            UUID id,
            String code,
            IdentifierCategory category,
            IdentifierSubjectType subjectType,
            int minimumLength,
            int maximumLength,
            boolean requiresExpiration,
            IdentifierSchemeStatus status) {
        return new IdentifierScheme(
                new IdentifierSchemeId(id),
                code,
                "EC",
                category,
                subjectType,
                "Additional identifier test scheme",
                null,
                "TRIM_UPPERCASE_V1",
                "ALPHANUMERIC_V1",
                minimumLength,
                maximumLength,
                requiresExpiration,
                status,
                IdentifierSchemeVersion.initial(),
                AuditInfo.initial(OCCURRED_AT.minusSeconds(120), "test-fixture"));
    }

    private static IdentifierScheme copyScheme(
            IdentifierScheme original,
            IdentifierSchemeId id,
            String code,
            IdentifierSchemeVersion version) {
        return new IdentifierScheme(
                id,
                code,
                original.issuingCountryCode(),
                original.category(),
                original.applicableSubjectType(),
                original.name(),
                original.description(),
                original.normalizerKey(),
                original.validatorKey(),
                original.minimumLength(),
                original.maximumLength(),
                original.requiresExpiration(),
                original.status(),
                version,
                original.auditInfo());
    }

    private static List<PartyIdentifierRegistrationCandidate> concat(
            List<PartyIdentifierRegistrationCandidate> first,
            List<PartyIdentifierRegistrationCandidate> second) {
        List<PartyIdentifierRegistrationCandidate> combined = new ArrayList<>(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }

    private static TenantId tenantId() {
        return new TenantId(UUID.randomUUID());
    }

    private static PartyId partyId() {
        return new PartyId(UUID.randomUUID());
    }

    private static String hash() {
        return UUID.randomUUID().toString().replace("-", "").repeat(2);
    }

    private static <T> Uni<T> timed(Uni<T> operation) {
        return operation.ifNoItem().after(REACTIVE_TIMEOUT).fail();
    }

    /**
     * Holds a persisted Party fixture and the complete state expected to remain unchanged.
     */
    private record PartyFixture(
            PartyEntity entity,
            PartyId partyId,
            TenantId tenantId,
            PartyType type,
            PartySnapshot expected) {
    }

    /**
     * Captures Party, audit, version, and type-specific detail state for mutation checks.
     */
    private record PartySnapshot(
            UUID id,
            UUID tenantId,
            PartyType type,
            String displayName,
            PartyRecordStatus status,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            long version,
            String detailsState) {
    }

    /**
     * Captures either a successful additional identifier or its typed failure.
     */
    private record RegistrationAttempt(
            PartyIdentifierResult result,
            ApplicationFailure failure) {
    }
}
