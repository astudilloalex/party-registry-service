package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.LegalEntityRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.NaturalPersonRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdempotentPartyRegistrationPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
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
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.alexastudillo.partyregistry.support.StoredOutboxTestProfile;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
 * Verifies atomic reactive Party registration and its two uniqueness race
 * arbiters.
 */
@QuarkusTest
@TestProfile(StoredOutboxTestProfile.class)
@Timeout(45)
class ReactivePartyRegistrationAdapterTest {

        private static final Duration REACTIVE_TIMEOUT = Duration.ofSeconds(15);
        private static final Instant OCCURRED_AT = Instant.parse("2026-09-04T14:30:00.123456Z");
        private static final LocalDate EVALUATED_ON = LocalDate.parse("2026-09-04");
        private static final String USER_ID = "registration-persistence-test";
        private static final String MASKED_VALUE = "********1234";

        @Inject
        IdempotentPartyRegistrationPort registrationPort;

        @Inject
        Mutiny.SessionFactory sessionFactory;

        @Test
        @RunOnVertxContext
        void commitsCompleteNaturalRegistrationWithSafeSnapshotAndTwoOutboxRows(
                        UniAsserter asserter) {
                TenantId tenantId = tenantId();
                NaturalPersonRegistrationCandidate candidate = naturalCandidate(
                                tenantId,
                                "natural-" + UUID.randomUUID(),
                                fingerprint('a'),
                                hash('1'),
                                naturalScheme(),
                                "Ada");

                asserter.assertThat(
                                () -> timed(registrationPort.registerNaturalPerson(candidate)),
                                result -> assertCreatedResult(candidate, result));
                asserter.assertThat(
                                () -> timed(rowCounts(List.of(candidate))),
                                counts -> assertEquals(new RowCounts(1, 1, 0, 1, 1, 2), counts));
                asserter.assertThat(
                                () -> timed(loadNaturalDetails(candidate.party().partyId())),
                                details -> {
                                        assertEquals("Ada", details.givenNames());
                                        assertEquals("Lovelace", details.familyNames());
                                        assertEquals("Countess", details.preferredName());
                                        assertEquals("GB", details.birthCountryCode());
                                });
                asserter.assertThat(
                                () -> timed(loadRegistrationRecord(candidate)),
                                recordEntity -> assertSafeVersionTwoSnapshot(recordEntity, candidate));
                asserter.assertThat(
                                () -> timed(loadIdentifier(candidate.initialIdentifier().identifierId())),
                                identifier -> assertIndependentIdentifier(candidate, identifier));
                asserter.assertThat(
                                () -> timed(loadOutbox(candidate)),
                                events -> assertRegistrationOutbox(events, PartyType.NATURAL_PERSON));
                asserter.assertThat(
                                () -> timed(registrationPort.findCompleted(
                                                tenantId,
                                                candidate.operation(),
                                                candidate.idempotencyKey(),
                                                candidate.registrationFingerprint())),
                                completed -> assertCreatedResult(candidate, completed.orElseThrow()));
        }

        @Test
        @RunOnVertxContext
        void commitsCompleteLegalRegistrationWithOnlyLegalDetails(UniAsserter asserter) {
                TenantId tenantId = tenantId();
                LegalEntityRegistrationCandidate candidate = legalCandidate(
                                tenantId,
                                "legal-" + UUID.randomUUID(),
                                fingerprint('b'),
                                hash('2'),
                                legalScheme(),
                                "Analytical Engines");

                asserter.assertThat(
                                () -> timed(registrationPort.registerLegalEntity(candidate)),
                                result -> assertCreatedResult(candidate, result));
                asserter.assertThat(
                                () -> timed(rowCounts(List.of(candidate))),
                                counts -> assertEquals(new RowCounts(1, 0, 1, 1, 1, 2), counts));
                asserter.assertThat(
                                () -> timed(loadLegalDetails(candidate.party().partyId())),
                                details -> {
                                        assertEquals("Analytical Engines Ltd", details.legalName());
                                        assertEquals("Analytical Engines", details.tradeName());
                                        assertEquals("LTD", details.legalFormCode());
                                        assertEquals("GB", details.incorporationCountryCode());
                                });
                asserter.assertThat(
                                () -> timed(loadRegistrationRecord(candidate)),
                                recordEntity -> assertSafeVersionTwoSnapshot(recordEntity, candidate));
                asserter.assertThat(
                                () -> timed(loadOutbox(candidate)),
                                events -> assertRegistrationOutbox(events, PartyType.LEGAL_ENTITY));
        }

        @Test
        @RunOnVertxContext
        void rejectsAChangedSchemeVersionBeforeAnyWrite(UniAsserter asserter) {
                TenantId tenantId = tenantId();
                IdentifierScheme changedCandidateScheme = withVersion(
                                naturalScheme(),
                                new IdentifierSchemeVersion(1));
                NaturalPersonRegistrationCandidate candidate = naturalCandidate(
                                tenantId,
                                "changed-scheme-" + UUID.randomUUID(),
                                fingerprint('c'),
                                hash('3'),
                                changedCandidateScheme,
                                "Changed");

                asserter.assertThat(
                                () -> timed(attempt(candidate)),
                                attempt -> assertInstanceOf(
                                                ApplicationFailure.InactiveIdentifierScheme.class,
                                                attempt.failure()));
                asserter.assertThat(
                                () -> timed(rowCounts(List.of(candidate))),
                                counts -> assertEquals(RowCounts.NONE, counts));
        }

        @Test
        @RunOnVertxContext
        void rejectsMissingMutatedInactiveAndIncompatibleSchemesBeforeWrites(
                        UniAsserter asserter) {
                TenantId tenantId = tenantId();
                IdentifierScheme missingScheme = fixtureScheme(
                                UUID.randomUUID(),
                                "TEST_MISSING_SCHEME",
                                IdentifierCategory.NATIONAL_ID,
                                IdentifierSubjectType.NATURAL_PERSON,
                                "Missing test scheme",
                                "Test-only scheme absent from persistence.",
                                6,
                                20,
                                IdentifierSchemeStatus.ACTIVE);
                IdentifierScheme mutatedIdentity = fixtureScheme(
                                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                                "TEST_NATURAL_ACTIVE_MUTATED",
                                IdentifierCategory.NATIONAL_ID,
                                IdentifierSubjectType.NATURAL_PERSON,
                                "Test natural-person identifier",
                                "Active test-only scheme for natural persons.",
                                6,
                                20,
                                IdentifierSchemeStatus.ACTIVE);
                IdentifierScheme inactiveScheme = fixtureScheme(
                                IdentifierSchemeTestFixtures.BOTH_DRAFT_ID,
                                IdentifierSchemeTestFixtures.BOTH_DRAFT_CODE,
                                IdentifierCategory.OTHER,
                                IdentifierSubjectType.BOTH,
                                "Test draft identifier",
                                "Inactive test-only draft scheme.",
                                1,
                                32,
                                IdentifierSchemeStatus.DRAFT);
                List<NaturalPersonRegistrationCandidate> candidates = List.of(
                                naturalCandidate(
                                                tenantId,
                                                "missing-scheme-" + UUID.randomUUID(),
                                                fingerprint('6'),
                                                hash('a'),
                                                missingScheme,
                                                "Missing"),
                                naturalCandidate(
                                                tenantId,
                                                "mutated-scheme-" + UUID.randomUUID(),
                                                fingerprint('7'),
                                                hash('b'),
                                                mutatedIdentity,
                                                "Mutated"),
                                naturalCandidate(
                                                tenantId,
                                                "inactive-scheme-" + UUID.randomUUID(),
                                                fingerprint('8'),
                                                hash('c'),
                                                inactiveScheme,
                                                "Inactive"),
                                naturalCandidate(
                                                tenantId,
                                                "incompatible-scheme-" + UUID.randomUUID(),
                                                fingerprint('9'),
                                                hash('d'),
                                                legalScheme(),
                                                "Incompatible"));

                asserter.assertThat(
                                () -> timed(attempt(candidates.get(0))),
                                attempt -> assertInstanceOf(
                                                ApplicationFailure.InactiveIdentifierScheme.class,
                                                attempt.failure()));
                asserter.assertThat(
                                () -> timed(attempt(candidates.get(1))),
                                attempt -> assertInstanceOf(
                                                ApplicationFailure.InactiveIdentifierScheme.class,
                                                attempt.failure()));
                asserter.assertThat(
                                () -> timed(attempt(candidates.get(2))),
                                attempt -> assertInstanceOf(
                                                ApplicationFailure.InactiveIdentifierScheme.class,
                                                attempt.failure()));
                asserter.assertThat(
                                () -> timed(attempt(candidates.get(3))),
                                attempt -> assertInstanceOf(
                                                ApplicationFailure.IncompatibleIdentifierScheme.class,
                                                attempt.failure()));
                asserter.assertThat(
                                () -> timed(rowCounts(candidates)),
                                counts -> assertEquals(RowCounts.NONE, counts));
        }

        @Test
        void equivalentSameKeyRaceReturnsOneCreatedAndOneSafeReplay() throws Exception {
                TenantId tenantId = tenantId();
                String key = "same-key-equal-" + UUID.randomUUID();
                IdentifierScheme scheme = naturalScheme();
                NaturalPersonRegistrationCandidate first = naturalCandidate(
                                tenantId, key, fingerprint('d'), hash('4'), scheme, "Equal First");
                NaturalPersonRegistrationCandidate second = naturalCandidate(
                                tenantId, key, fingerprint('d'), hash('4'), scheme, "Equal Second");

                List<PartyRegistrationResult> registrations = race(
                                () -> registrationPort.registerNaturalPerson(first),
                                () -> registrationPort.registerNaturalPerson(second));
                assertEquals(1, registrations.stream()
                                .filter(result -> result.outcome() == PartyRegistrationOutcome.CREATED)
                                .count());
                assertEquals(1, registrations.stream()
                                .filter(result -> result.outcome() == PartyRegistrationOutcome.REPLAYED)
                                .count());
                assertEquals(registrations.get(0).party(), registrations.get(1).party());
                assertEquals(
                                registrations.get(0).initialIdentifier(),
                                registrations.get(1).initialIdentifier());
                assertEquals(
                                new RowCounts(1, 1, 0, 1, 1, 2),
                                awaitReactive(() -> rowCounts(List.of(first, second))));
        }

        @Test
        void changedInputSameKeyRaceReturnsOneConflictWithoutOrphans() throws Exception {
                TenantId tenantId = tenantId();
                String key = "same-key-changed-" + UUID.randomUUID();
                IdentifierScheme scheme = naturalScheme();
                NaturalPersonRegistrationCandidate first = naturalCandidate(
                                tenantId, key, fingerprint('e'), hash('5'), scheme, "Changed First");
                NaturalPersonRegistrationCandidate second = naturalCandidate(
                                tenantId, key, fingerprint('f'), hash('6'), scheme, "Changed Second");

                List<RegistrationAttempt> attempts = race(
                                () -> attempt(first),
                                () -> attempt(second));
                assertOneWinnerAndFailure(attempts, ApplicationFailure.IdempotencyKeyConflict.class);
                assertEquals(
                                new RowCounts(1, 1, 0, 1, 1, 2),
                                awaitReactive(() -> rowCounts(List.of(first, second))));
        }

        @Test
        void differentKeysSameIdentifierRaceReturnsOneTypedIdentifierConflict() throws Exception {
                TenantId tenantId = tenantId();
                IdentifierScheme scheme = naturalScheme();
                NaturalPersonRegistrationCandidate first = naturalCandidate(
                                tenantId,
                                "identifier-first-" + UUID.randomUUID(),
                                fingerprint('1'),
                                hash('7'),
                                scheme,
                                "Identifier First");
                NaturalPersonRegistrationCandidate second = naturalCandidate(
                                tenantId,
                                "identifier-second-" + UUID.randomUUID(),
                                fingerprint('2'),
                                hash('7'),
                                scheme,
                                "Identifier Second");

                List<RegistrationAttempt> attempts = race(
                                () -> attempt(first),
                                () -> attempt(second));
                assertOneWinnerAndFailure(
                                attempts,
                                ApplicationFailure.IdentifierUniquenessConflict.class);
                ApplicationFailure.IdentifierUniquenessConflict conflict = attempts.stream()
                                .map(RegistrationAttempt::failure)
                                .filter(ApplicationFailure.IdentifierUniquenessConflict.class::isInstance)
                                .map(ApplicationFailure.IdentifierUniquenessConflict.class::cast)
                                .findFirst()
                                .orElseThrow();
                assertEquals(scheme.id(), conflict.schemeId());
                assertEquals(
                                new RowCounts(1, 1, 0, 1, 1, 2),
                                awaitReactive(() -> rowCounts(List.of(first, second))));
        }

        @Test
        void sameIdentifierHashIsAllowedInDifferentTenants() throws Exception {
                IdentifierScheme scheme = naturalScheme();
                NaturalPersonRegistrationCandidate first = naturalCandidate(
                                tenantId(),
                                "tenant-first-" + UUID.randomUUID(),
                                fingerprint('3'),
                                hash('8'),
                                scheme,
                                "Tenant First");
                NaturalPersonRegistrationCandidate second = naturalCandidate(
                                tenantId(),
                                "tenant-second-" + UUID.randomUUID(),
                                fingerprint('4'),
                                hash('8'),
                                scheme,
                                "Tenant Second");

                List<PartyRegistrationResult> results = race(
                                () -> registrationPort.registerNaturalPerson(first),
                                () -> registrationPort.registerNaturalPerson(second));
                assertEquals(PartyRegistrationOutcome.CREATED, results.get(0).outcome());
                assertEquals(PartyRegistrationOutcome.CREATED, results.get(1).outcome());
                assertEquals(
                                new RowCounts(2, 2, 0, 2, 2, 4),
                                awaitReactive(() -> rowCounts(List.of(first, second))));
        }

        @Test
        @RunOnVertxContext
        void lateOutboxConstraintFailureRollsBackEveryRegistrationRow(UniAsserter asserter) {
                TenantId tenantId = tenantId();
                NaturalPersonRegistrationCandidate original = naturalCandidate(
                                tenantId,
                                "late-failure-" + UUID.randomUUID(),
                                fingerprint('5'),
                                hash('9'),
                                naturalScheme(),
                                "Late Failure");
                PartyCreatedOutboxCandidate partyEvent = assertInstanceOf(
                                PartyCreatedOutboxCandidate.class,
                                original.outboxCandidates().getFirst());
                PartyCreatedOutboxCandidate duplicateIdentity = new PartyCreatedOutboxCandidate(
                                UUID.randomUUID(),
                                partyEvent.tenantId(),
                                partyEvent.partyId(),
                                partyEvent.partyVersion(),
                                partyEvent.partyType(),
                                partyEvent.occurredAt(),
                                partyEvent.correlationId(),
                                partyEvent.createdBy());
                NaturalPersonRegistrationCandidate candidate = new NaturalPersonRegistrationCandidate(
                                original.requestMetadata(),
                                original.idempotencyKey(),
                                original.registrationFingerprint(),
                                original.party(),
                                original.identifierScheme(),
                                original.initialIdentifier(),
                                List.of(partyEvent, duplicateIdentity));

                asserter.assertThat(
                                () -> timed(attempt(candidate)),
                                attempt -> assertInstanceOf(
                                                ApplicationFailure.PersistenceFailure.class,
                                                attempt.failure()));
                asserter.assertThat(
                                () -> timed(rowCounts(List.of(candidate))),
                                counts -> assertEquals(RowCounts.NONE, counts));
        }

        private Uni<RegistrationAttempt> attempt(PartyRegistrationCandidate candidate) {
                return register(candidate)
                                .map(result -> new RegistrationAttempt(result, null))
                                .onFailure(ApplicationException.class)
                                .recoverWithItem(failure -> new RegistrationAttempt(
                                                null,
                                                failure.failure()));
        }

        private Uni<PartyRegistrationResult> register(PartyRegistrationCandidate candidate) {
                return switch (candidate) {
                        case NaturalPersonRegistrationCandidate natural ->
                                registrationPort.registerNaturalPerson(natural);
                        case LegalEntityRegistrationCandidate legal ->
                                registrationPort.registerLegalEntity(legal);
                };
        }

        private Uni<RowCounts> rowCounts(List<? extends PartyRegistrationCandidate> candidates) {
                List<UUID> partyIds = candidates.stream()
                                .map(candidate -> candidate.party().partyId().value())
                                .toList();
                List<UUID> aggregateIds = new ArrayList<>(partyIds);
                aggregateIds.addAll(candidates.stream()
                                .map(candidate -> candidate.initialIdentifier().identifierId().value())
                                .toList());
                return sessionFactory.withSession(session -> count(session, """
                                select count(party) from PartyEntity party where party.id in :ids
                                """, partyIds)
                                .flatMap(parties -> count(session, """
                                                select count(details) from NaturalPersonDetailsEntity details
                                                where details.partyId in :ids
                                                """, partyIds)
                                                .flatMap(naturalDetails -> count(session,
                                                                """
                                                                                select count(details) from LegalEntityDetailsEntity details
                                                                                where details.partyId in :ids
                                                                                """,
                                                                partyIds)
                                                                .flatMap(legalDetails -> count(session,
                                                                                """
                                                                                                select count(record) from ApiIdempotencyRecordEntity record
                                                                                                where record.partyId in :ids
                                                                                                """,
                                                                                partyIds)
                                                                                .flatMap(idempotencyRecords -> count(
                                                                                                session,
                                                                                                """
                                                                                                                select count(identifier) from PartyIdentifierEntity identifier
                                                                                                                where identifier.partyId in :ids
                                                                                                                """,
                                                                                                partyIds)
                                                                                                .flatMap(identifiers -> count(
                                                                                                                session,
                                                                                                                """
                                                                                                                                select count(event) from PartyOutboxEventEntity event
                                                                                                                                where event.aggregateId in :ids
                                                                                                                                """,
                                                                                                                aggregateIds)
                                                                                                                .map(outboxEvents -> new RowCounts(
                                                                                                                                parties,
                                                                                                                                naturalDetails,
                                                                                                                                legalDetails,
                                                                                                                                idempotencyRecords,
                                                                                                                                identifiers,
                                                                                                                                outboxEvents))))))));
        }

        private static Uni<Long> count(
                        Mutiny.Session session,
                        String query,
                        List<UUID> ids) {
                return session.createQuery(query, Long.class)
                                .setParameter("ids", ids)
                                .getSingleResult();
        }

        private Uni<NaturalPersonDetailsEntity> loadNaturalDetails(PartyId partyId) {
                return sessionFactory.withSession(session -> session.find(
                                NaturalPersonDetailsEntity.class,
                                partyId.value()));
        }

        private Uni<LegalEntityDetailsEntity> loadLegalDetails(PartyId partyId) {
                return sessionFactory.withSession(session -> session.find(
                                LegalEntityDetailsEntity.class,
                                partyId.value()));
        }

        private Uni<ApiIdempotencyRecordEntity> loadRegistrationRecord(
                        PartyRegistrationCandidate candidate) {
                return sessionFactory.withSession(session -> session.find(
                                ApiIdempotencyRecordEntity.class,
                                new ApiIdempotencyRecordId(
                                                candidate.party().tenantId().value(),
                                                candidate.operation(),
                                                candidate.idempotencyKey())));
        }

        private Uni<PartyIdentifierEntity> loadIdentifier(PartyIdentifierId identifierId) {
                return sessionFactory.withSession(session -> session.find(
                                PartyIdentifierEntity.class,
                                identifierId.value()));
        }

        private Uni<List<PartyOutboxEventEntity>> loadOutbox(PartyRegistrationCandidate candidate) {
                return sessionFactory.withSession(session -> session.createQuery("""
                                from PartyOutboxEventEntity event
                                where event.aggregateId = :partyId or event.aggregateId = :identifierId
                                order by event.eventType
                                """, PartyOutboxEventEntity.class)
                                .setParameter("partyId", candidate.party().partyId().value())
                                .setParameter("identifierId", candidate.initialIdentifier().identifierId().value())
                                .getResultList());
        }

        private static NaturalPersonRegistrationCandidate naturalCandidate(
                        TenantId tenantId,
                        String idempotencyKey,
                        String registrationFingerprint,
                        String normalizedValueHash,
                        IdentifierScheme scheme,
                        String givenNames) {
                NaturalPerson party = NaturalPerson.create(
                                partyId(),
                                tenantId,
                                null,
                                new NaturalPersonDetails(
                                                givenNames,
                                                "Lovelace",
                                                "Countess",
                                                LocalDate.parse("1815-12-10"),
                                                LocalDate.parse("1852-11-27"),
                                                "GB"),
                                EVALUATED_ON,
                                OCCURRED_AT,
                                USER_ID);
                PartyIdentifier identifier = identifier(
                                tenantId,
                                party,
                                scheme,
                                normalizedValueHash);
                return new NaturalPersonRegistrationCandidate(
                                new RequestMetadata(tenantId, USER_ID, UUID.randomUUID()),
                                idempotencyKey,
                                registrationFingerprint,
                                party,
                                scheme,
                                identifier,
                                registrationEvents(party, identifier, scheme));
        }

        private static LegalEntityRegistrationCandidate legalCandidate(
                        TenantId tenantId,
                        String idempotencyKey,
                        String registrationFingerprint,
                        String normalizedValueHash,
                        IdentifierScheme scheme,
                        String tradeName) {
                LegalEntity party = LegalEntity.create(
                                partyId(),
                                tenantId,
                                null,
                                new LegalEntityDetails(
                                                "Analytical Engines Ltd",
                                                tradeName,
                                                "LTD",
                                                "GB",
                                                LocalDate.parse("1843-01-01"),
                                                null),
                                EVALUATED_ON,
                                OCCURRED_AT,
                                USER_ID);
                PartyIdentifier identifier = identifier(
                                tenantId,
                                party,
                                scheme,
                                normalizedValueHash);
                return new LegalEntityRegistrationCandidate(
                                new RequestMetadata(tenantId, USER_ID, UUID.randomUUID()),
                                idempotencyKey,
                                registrationFingerprint,
                                party,
                                scheme,
                                identifier,
                                registrationEvents(party, identifier, scheme));
        }

        private static PartyIdentifier identifier(
                        TenantId tenantId,
                        Party party,
                        IdentifierScheme scheme,
                        String normalizedValueHash) {
                PartyIdentifierId identifierId = new PartyIdentifierId(UUID.randomUUID());
                return PartyIdentifier.builder()
                                .identifierId(identifierId)
                                .tenantId(tenantId)
                                .partyId(party.partyId())
                                .identifierSchemeId(scheme.id())
                                .protectedValue(new ProtectedIdentifierValue(
                                                "v1.protected." + identifierId.value(),
                                                1,
                                                normalizedValueHash,
                                                MASKED_VALUE,
                                                new IdentifierRuleVersion(1)))
                                .issuerCode("ISSUER")
                                .issuedOn(LocalDate.parse("2020-01-01"))
                                .expiresOn(LocalDate.parse("2030-01-01"))
                                .primary(false)
                                .created(OCCURRED_AT, USER_ID)
                                .build();
        }

        private static List<OutboxEventCandidate> registrationEvents(
                        Party party,
                        PartyIdentifier identifier,
                        IdentifierScheme scheme) {
                UUID correlationId = UUID.randomUUID();
                return List.of(
                                new PartyCreatedOutboxCandidate(
                                                UUID.randomUUID(),
                                                party.tenantId(),
                                                party.partyId(),
                                                party.version(),
                                                party.type(),
                                                OCCURRED_AT,
                                                correlationId,
                                                USER_ID),
                                new PartyIdentifierCreatedOutboxCandidate(
                                                UUID.randomUUID(),
                                                identifier.tenantId(),
                                                identifier.identifierId(),
                                                identifier.version(),
                                                identifier.partyId(),
                                                scheme.code(),
                                                identifier.status(),
                                                OCCURRED_AT,
                                                correlationId,
                                                USER_ID));
        }

        private static IdentifierScheme naturalScheme() {
                return fixtureScheme(
                                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE,
                                IdentifierCategory.NATIONAL_ID,
                                IdentifierSubjectType.NATURAL_PERSON,
                                "Test natural-person identifier",
                                "Active test-only scheme for natural persons.",
                                6,
                                20,
                                IdentifierSchemeStatus.ACTIVE);
        }

        private static IdentifierScheme legalScheme() {
                return fixtureScheme(
                                IdentifierSchemeTestFixtures.LEGAL_ACTIVE_ID,
                                IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE,
                                IdentifierCategory.LEGAL_REGISTRATION_NUMBER,
                                IdentifierSubjectType.LEGAL_ENTITY,
                                "Test legal-entity identifier",
                                "Active test-only scheme for legal entities.",
                                6,
                                24,
                                IdentifierSchemeStatus.ACTIVE);
        }

        private static IdentifierScheme fixtureScheme(
                        UUID id,
                        String code,
                        IdentifierCategory category,
                        IdentifierSubjectType subjectType,
                        String name,
                        String description,
                        int minimumLength,
                        int maximumLength,
                        IdentifierSchemeStatus status) {
                return new IdentifierScheme(
                                new IdentifierSchemeId(id),
                                code,
                                "EC",
                                category,
                                subjectType,
                                name,
                                description,
                                "TRIM_UPPERCASE_V1",
                                "ALPHANUMERIC_V1",
                                minimumLength,
                                maximumLength,
                                false,
                                status,
                                IdentifierSchemeVersion.initial(),
                                AuditInfo.initial(OCCURRED_AT, "test-fixture"));
        }

        private static IdentifierScheme withVersion(
                        IdentifierScheme scheme,
                        IdentifierSchemeVersion version) {
                return new IdentifierScheme(
                                scheme.id(),
                                scheme.code(),
                                scheme.issuingCountryCode(),
                                scheme.category(),
                                scheme.applicableSubjectType(),
                                scheme.name(),
                                scheme.description(),
                                scheme.normalizerKey(),
                                scheme.validatorKey(),
                                scheme.minimumLength(),
                                scheme.maximumLength(),
                                scheme.requiresExpiration(),
                                scheme.status(),
                                version,
                                scheme.auditInfo());
        }

        private static void assertCreatedResult(
                        PartyRegistrationCandidate candidate,
                        PartyRegistrationResult result) {
                assertEquals(PartyRegistrationOutcome.CREATED, result.outcome());
                assertEquals(candidate.party().partyId(), result.party().partyId());
                assertEquals(
                                candidate.initialIdentifier().identifierId(),
                                result.initialIdentifier().identifierId());
                assertEquals(MASKED_VALUE, result.initialIdentifier().maskedValue());
                assertEquals(
                                PartyIdentifierStatus.PENDING_VERIFICATION,
                                result.initialIdentifier().status());
        }

        private static void assertSafeVersionTwoSnapshot(
                        ApiIdempotencyRecordEntity recordEntity,
                        PartyRegistrationCandidate candidate) {
                assertEquals(
                                IdempotencyResultSnapshotCodec.REGISTRATION_SCHEMA_VERSION,
                                recordEntity.resultSnapshotSchemaVersion());
                String snapshot = recordEntity.resultSnapshot().payload().toString();
                assertTrue(snapshot.contains(candidate.party().partyId().value().toString()));
                assertTrue(snapshot.contains(candidate.initialIdentifier().identifierId().value().toString()));
                assertTrue(snapshot.contains(MASKED_VALUE));
                assertFalse(snapshot.contains(candidate.initialIdentifier().protectedValue().encryptedValue()));
                assertFalse(snapshot.contains(candidate.initialIdentifier().protectedValue().normalizedValueHash()));
                assertFalse(snapshot.contains("encryptionKeyVersion"));
                assertFalse(snapshot.contains("normalizationVersion"));
        }

        private static void assertIndependentIdentifier(
                        PartyRegistrationCandidate candidate,
                        PartyIdentifierEntity identifier) {
                assertEquals(candidate.initialIdentifier().identifierId().value(), identifier.id());
                assertEquals(candidate.party().partyId().value(), identifier.partyId());
                assertEquals(candidate.identifierScheme().id().value(), identifier.identifierSchemeId());
                assertEquals(
                                candidate.initialIdentifier().protectedValue().normalizedValueHash(),
                                identifier.normalizedValueHash());
                assertEquals(PartyIdentifierStatus.PENDING_VERIFICATION, identifier.status());
        }

        private static void assertRegistrationOutbox(
                        List<PartyOutboxEventEntity> events,
                        PartyType partyType) {
                assertEquals(2, events.size());
                PartyOutboxEventEntity partyEvent = events.stream()
                                .filter(event -> event.eventType().equals(PartyCreatedOutboxCandidate.EVENT_TYPE))
                                .findFirst()
                                .orElseThrow();
                PartyOutboxEventEntity identifierEvent = events.stream()
                                .filter(event -> event.eventType().equals(
                                                PartyIdentifierCreatedOutboxCandidate.EVENT_TYPE))
                                .findFirst()
                                .orElseThrow();
                assertEquals(Map.of("partyType", partyType.name()), partyEvent.payload());
                assertEquals(
                                PartyIdentifierStatus.PENDING_VERIFICATION.name(),
                                identifierEvent.payload().get("status"));
                assertFalse(identifierEvent.payload().keySet().stream()
                                .anyMatch(key -> key.contains("value") || key.contains("hash") || key.contains("key")));
                assertNull(identifierEvent.lastErrorDetail());
        }

        private static void assertOneWinnerAndFailure(
                        List<RegistrationAttempt> attempts,
                        Class<? extends ApplicationFailure> expectedFailure) {
                assertEquals(1, attempts.stream().filter(RegistrationAttempt::succeeded).count());
                assertEquals(1, attempts.stream()
                                .map(RegistrationAttempt::failure)
                                .filter(expectedFailure::isInstance)
                                .count());
        }

        private static TenantId tenantId() {
                return new TenantId(UUID.randomUUID());
        }

        private static PartyId partyId() {
                return new PartyId(UUID.randomUUID());
        }

        private static String fingerprint(char value) {
                return Character.toString(value).repeat(64);
        }

        private static String hash(char value) {
                return Character.toString(value).repeat(64);
        }

        private static <T> Uni<T> timed(Uni<T> operation) {
                return operation.ifNoItem().after(REACTIVE_TIMEOUT).fail();
        }

        private static <T> List<T> race(
                        Supplier<Uni<T>> first,
                        Supplier<Uni<T>> second) throws Exception {
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        Future<T> firstResult = executor.submit(() -> awaitRaceParticipant(first, ready, start));
                        Future<T> secondResult = executor.submit(() -> awaitRaceParticipant(second, ready, start));
                        assertTrue(ready.await(REACTIVE_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
                        start.countDown();
                        return List.of(
                                        firstResult.get(REACTIVE_TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                                        secondResult.get(REACTIVE_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
                }
        }

        private static <T> T awaitRaceParticipant(
                        Supplier<Uni<T>> operation,
                        CountDownLatch ready,
                        CountDownLatch start) throws Exception {
                ready.countDown();
                if (!start.await(REACTIVE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent registration did not start in time");
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
         * Captures a registration result or its typed application failure.
         */
        private record RegistrationAttempt(
                        PartyRegistrationResult result,
                        ApplicationFailure failure) {

                boolean succeeded() {
                        return result != null;
                }
        }

        /**
         * Counts every row owned by one isolated registration scenario.
         */
        private record RowCounts(
                        long parties,
                        long naturalDetails,
                        long legalDetails,
                        long idempotencyRecords,
                        long identifiers,
                        long outboxEvents) {

                private static final RowCounts NONE = new RowCounts(0, 0, 0, 0, 0, 0);
        }
}
