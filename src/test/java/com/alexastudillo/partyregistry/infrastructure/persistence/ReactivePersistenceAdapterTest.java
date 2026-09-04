package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.CreateNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdempotentCreationOutcome;
import com.alexastudillo.partyregistry.application.model.IdempotentCreationResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.port.IdempotentNaturalPersonCreationPort;
import com.alexastudillo.partyregistry.application.port.NaturalPersonRepository;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Hibernate Reactive adapters against the Flyway-managed
 * PostgreSQL schema.
 */
@QuarkusTest
class ReactivePersistenceAdapterTest {

    private static final LocalDate EVALUATED_ON = LocalDate.parse("2026-08-30");
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T10:15:30.123456Z");
    private static final String REQUEST_HASH = "a".repeat(64);

    @Inject
    NaturalPersonRepository repository;

    @Inject
    IdempotentNaturalPersonCreationPort creationPort;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    @RunOnVertxContext
    void persistsSnapshotsAndConcealsNonMatchingRows(UniAsserter asserter) {
        TenantId tenantId = tenantId();
        TenantId otherTenant = tenantId();
        NaturalPerson naturalPerson = naturalPerson(tenantId, "original");
        String key = "create-" + UUID.randomUUID();
        PartyId missingDetailsId = partyId();
        PartyId legalEntityId = partyId();

        asserter.assertThat(
                () -> creationPort.createIdempotently(
                        tenantId,
                        key,
                        REQUEST_HASH,
                        naturalPerson),
                result -> {
                    assertEquals(IdempotentCreationOutcome.CREATED, result.outcome());
                    assertEquals(naturalPerson.partyId(), result.result().partyId());
                    assertEquals(PartyVersion.initial(), result.result().version());
                });
        asserter.assertThat(
                () -> repository.findByTenantAndId(tenantId, naturalPerson.partyId()),
                found -> assertNaturalPersonEquals(naturalPerson, found.orElseThrow()));
        asserter.assertThat(
                () -> creationPort.findCompleted(tenantId, key, REQUEST_HASH),
                replay -> {
                    IdempotentCreationResult result = replay.orElseThrow();
                    assertEquals(IdempotentCreationOutcome.REPLAYED, result.outcome());
                    assertEquals(NaturalPersonResult.fromAggregate(naturalPerson), result.result());
                });
        asserter.assertThat(
                () -> repository.findByTenantAndId(otherTenant, naturalPerson.partyId()),
                found -> assertTrue(found.isEmpty()));
        asserter.execute(() -> persistPartyWithoutDetails(
                tenantId,
                missingDetailsId,
                PartyType.NATURAL_PERSON));
        asserter.execute(() -> persistPartyWithoutDetails(
                tenantId,
                legalEntityId,
                PartyType.LEGAL_ENTITY));
        asserter.assertThat(
                () -> repository.findByTenantAndId(tenantId, missingDetailsId),
                found -> assertTrue(found.isEmpty()));
        asserter.assertThat(
                () -> repository.findByTenantAndId(tenantId, legalEntityId),
                found -> assertTrue(found.isEmpty()));
        asserter.assertFailedWith(
                () -> creationPort.findCompleted(tenantId, key, "b".repeat(64)),
                failure -> assertFailure(failure, ApplicationFailure.IdempotencyKeyConflict.class));
    }

    @Test
    @RunOnVertxContext
    void incrementsVersionForDetailOnlyUpdateWithUnchangedRootValues(UniAsserter asserter) {
        TenantId tenantId = tenantId();
        NaturalPerson original = naturalPerson(tenantId, "detail-only");
        NaturalPerson updated = detailOnlyReplacement(original, "Countess Lovelace");

        asserter.execute(() -> creationPort.createIdempotently(
                tenantId,
                "detail-only-" + UUID.randomUUID(),
                REQUEST_HASH,
                original));
        asserter.assertThat(
                () -> repository.update(updated, PartyVersion.initial()),
                persisted -> {
                    assertEquals(new PartyVersion(1), persisted.version());
                    assertEquals(original.displayName(), persisted.displayName());
                    assertEquals(original.auditInfo(), persisted.auditInfo());
                    assertEquals("Countess Lovelace", persisted.details().preferredName());
                });
    }

    @Test
    @RunOnVertxContext
    void updatesAuditAndVersionAndRejectsStaleWrites(UniAsserter asserter) {
        TenantId tenantId = tenantId();
        NaturalPerson original = naturalPerson(tenantId, "update");
        NaturalPerson updated = original.replaceDetails(
                new NaturalPersonDetails(
                        "Grace",
                        "Hopper",
                        null,
                        LocalDate.parse("1906-12-09"),
                        LocalDate.parse("1992-01-01"),
                        "US"),
                EVALUATED_ON,
                CREATED_AT.plusSeconds(60),
                "updater");

        asserter.execute(() -> creationPort.createIdempotently(
                tenantId,
                "update-" + UUID.randomUUID(),
                REQUEST_HASH,
                original));
        asserter.assertThat(
                () -> repository.update(updated, PartyVersion.initial()),
                persisted -> {
                    assertEquals(new PartyVersion(1), persisted.version());
                    assertEquals("Grace Hopper", persisted.displayName());
                    assertEquals("updater", persisted.auditInfo().updatedBy());
                    assertEquals(CREATED_AT.plusSeconds(60), persisted.auditInfo().updatedAt());
                });
        asserter.assertThat(
                () -> findDetails(original.partyId()),
                details -> {
                    assertEquals("updater", details.updatedBy());
                    assertEquals(CREATED_AT.plusSeconds(60), details.updatedAt());
                    assertEquals(CREATED_AT, details.createdAt());
                    assertEquals("creator", details.createdBy());
                });
        asserter.assertFailedWith(
                () -> repository.update(updated, PartyVersion.initial()),
                failure -> {
                    ApplicationException exception = assertInstanceOf(ApplicationException.class,
                            failure);
                    ApplicationFailure.ExpectedVersionMismatch mismatch = assertInstanceOf(
                            ApplicationFailure.ExpectedVersionMismatch.class,
                            exception.failure());
                    assertEquals(PartyVersion.initial(), mismatch.expectedVersion());
                    assertEquals(new PartyVersion(1), mismatch.currentVersion());
                });
    }

    @Test
    @RunOnVertxContext
    void permitsOnlyOneConcurrentUpdateForOneVersion(UniAsserter asserter) {
        TenantId tenantId = tenantId();
        NaturalPerson original = naturalPerson(tenantId, "concurrent-update");
        NaturalPerson firstUpdate = detailOnlyReplacement(original, "First Preference");
        NaturalPerson secondUpdate = detailOnlyReplacement(original, "Second Preference");

        asserter.execute(() -> creationPort.createIdempotently(
                tenantId,
                "concurrent-update-" + UUID.randomUUID(),
                REQUEST_HASH,
                original));
        asserter.assertThat(
                () -> Uni.combine().all().unis(
                        attemptUpdate(firstUpdate),
                        attemptUpdate(secondUpdate))
                        .asTuple(),
                attempts -> {
                    List<UpdateAttempt> results = List.of(attempts.getItem1(), attempts.getItem2());
                    assertEquals(1, results.stream().filter(UpdateAttempt::succeeded).count());
                    assertEquals(1, results.stream().filter(attempt -> attempt
                            .failure() instanceof ApplicationFailure.ExpectedVersionMismatch)
                            .count());
                });
        asserter.assertThat(
                () -> repository.findByTenantAndId(tenantId, original.partyId()),
                found -> {
                    NaturalPerson persisted = found.orElseThrow();
                    assertEquals(new PartyVersion(1), persisted.version());
                    assertTrue(
                            persisted.details().preferredName().equals("First Preference")
                                    || persisted.details().preferredName()
                                            .equals("Second Preference"));
                });
    }

    @Test
    @RunOnVertxContext
    void recoversEquivalentConcurrentCreationAndRejectsDifferentPayloads(UniAsserter asserter) {
        TenantId tenantId = tenantId();
        String equalKey = "equal-race-" + UUID.randomUUID();
        NaturalPerson equalFirst = naturalPerson(tenantId, "equal-first");
        NaturalPerson equalSecond = naturalPerson(tenantId, "equal-second");

        asserter.assertThat(
                () -> Uni.combine().all().unis(
                        creationPort.createIdempotently(tenantId, equalKey, REQUEST_HASH,
                                equalFirst),
                        creationPort.createIdempotently(tenantId, equalKey, REQUEST_HASH,
                                equalSecond))
                        .asTuple(),
                results -> {
                    assertEquals(results.getItem1().result(), results.getItem2().result());
                    assertEquals(
                            1,
                            List.of(results.getItem1(), results.getItem2()).stream()
                                    .filter(result -> result
                                            .outcome() == IdempotentCreationOutcome.CREATED)
                                    .count());
                    assertEquals(
                            1,
                            List.of(results.getItem1(), results.getItem2()).stream()
                                    .filter(result -> result
                                            .outcome() == IdempotentCreationOutcome.REPLAYED)
                                    .count());
                });
        asserter.assertThat(
                () -> persistenceCounts(
                        tenantId,
                        equalKey,
                        equalFirst.partyId(),
                        equalSecond.partyId()),
                counts -> {
                    assertEquals(1, counts.parties());
                    assertEquals(1, counts.details());
                    assertEquals(1, counts.idempotencyRecords());
                });

        String differentKey = "different-race-" + UUID.randomUUID();
        NaturalPerson differentFirst = naturalPerson(tenantId, "first");
        NaturalPerson differentSecond = naturalPerson(tenantId, "second");
        asserter.assertThat(
                () -> Uni.combine().all().unis(
                        attemptCreation(tenantId, differentKey, "c".repeat(64), differentFirst),
                        attemptCreation(tenantId, differentKey, "d".repeat(64),
                                differentSecond))
                        .asTuple(),
                attempts -> {
                    List<CreationAttempt> results = List.of(attempts.getItem1(),
                            attempts.getItem2());
                    assertEquals(1, results.stream().filter(CreationAttempt::succeeded).count());
                    assertEquals(1, results.stream().filter(attempt -> attempt
                            .failure() instanceof ApplicationFailure.IdempotencyKeyConflict)
                            .count());
                });
        asserter.assertThat(
                () -> persistenceCounts(
                        tenantId,
                        differentKey,
                        differentFirst.partyId(),
                        differentSecond.partyId()),
                counts -> {
                    assertEquals(1, counts.parties());
                    assertEquals(1, counts.details());
                    assertEquals(1, counts.idempotencyRecords());
                });
    }

    @Test
    @RunOnVertxContext
    void rollsBackAggregateAndSanitizesConstraintFailure(UniAsserter asserter) {
        TenantId tenantId = tenantId();
        NaturalPerson naturalPerson = naturalPerson(tenantId, "rollback");
        String key = "rollback-" + UUID.randomUUID();

        asserter.assertFailedWith(
                () -> creationPort.createIdempotently(
                        tenantId,
                        key,
                        "A".repeat(64),
                        naturalPerson),
                failure -> {
                    ApplicationException exception = assertInstanceOf(ApplicationException.class,
                            failure);
                    assertInstanceOf(ApplicationFailure.PersistenceFailure.class,
                            exception.failure());
                    assertEquals("Persistence operation failed", exception.getMessage());
                    assertFalse(exception.getMessage().contains("ck_api_idempotency_request_hash"));
                });
        asserter.assertThat(
                () -> repository.findByTenantAndId(tenantId, naturalPerson.partyId()),
                found -> assertTrue(found.isEmpty()));
        asserter.assertThat(
                () -> persistenceCounts(
                        tenantId,
                        key,
                        naturalPerson.partyId(),
                        naturalPerson.partyId()),
                counts -> {
                    assertEquals(0, counts.parties());
                    assertEquals(0, counts.details());
                    assertEquals(0, counts.idempotencyRecords());
                });
        asserter.assertThat(
                () -> creationPort.findCompleted(tenantId, key, "A".repeat(64)),
                completed -> assertTrue(completed.isEmpty()));
    }

    private Uni<NaturalPersonDetailsEntity> findDetails(PartyId partyId) {
        return sessionFactory.withSession(
                session -> session.find(NaturalPersonDetailsEntity.class, partyId.value()));
    }

    private Uni<Void> persistPartyWithoutDetails(
            TenantId tenantId,
            PartyId partyId,
            PartyType type) {
        PartyEntity party = new PartyEntity(
                partyId.value(),
                tenantId.value(),
                type,
                "Concealed Party",
                PartyRecordStatus.DRAFT,
                AuditInfo.initial(CREATED_AT, "creator"),
                0);
        return sessionFactory
                .withTransaction((session, transaction) -> session.persist(party).call(session::flush));
    }

    private Uni<PersistenceCounts> persistenceCounts(
            TenantId tenantId,
            String idempotencyKey,
            PartyId firstPartyId,
            PartyId secondPartyId) {
        return sessionFactory.withSession(session -> session.createQuery("""
                select count(party)
                from PartyEntity party
                where party.id = :firstPartyId or party.id = :secondPartyId
                """, Long.class)
                .setParameter("firstPartyId", firstPartyId.value())
                .setParameter("secondPartyId", secondPartyId.value())
                .getSingleResult()
                .flatMap(parties -> session
                        .createQuery("""
                                select count(details)
                                from NaturalPersonDetailsEntity details
                                where details.partyId = :firstPartyId or details.partyId = :secondPartyId
                                """,
                                Long.class)
                        .setParameter("firstPartyId", firstPartyId.value())
                        .setParameter("secondPartyId", secondPartyId.value())
                        .getSingleResult()
                        .flatMap(details -> session.find(
                                ApiIdempotencyRecordEntity.class,
                                new ApiIdempotencyRecordId(
                                        tenantId.value(),
                                        CreateNaturalPersonCommand.OPERATION,
                                        idempotencyKey))
                                .map(idempotencyRecord -> new PersistenceCounts(
                                        parties,
                                        details,
                                        idempotencyRecord == null ? 0 : 1)))));
    }

    private Uni<UpdateAttempt> attemptUpdate(NaturalPerson candidate) {
        return repository.update(candidate, PartyVersion.initial())
                .map(ignored -> new UpdateAttempt(true, null))
                .onFailure(ApplicationException.class)
                .recoverWithItem(failure -> new UpdateAttempt(
                        false,
                        ((ApplicationException) failure).failure()));
    }

    private Uni<CreationAttempt> attemptCreation(
            TenantId tenantId,
            String key,
            String hash,
            NaturalPerson naturalPerson) {
        return creationPort.createIdempotently(tenantId, key, hash, naturalPerson)
                .map(ignored -> new CreationAttempt(true, null))
                .onFailure(ApplicationException.class)
                .recoverWithItem(failure -> new CreationAttempt(
                        false,
                        ((ApplicationException) failure).failure()));
    }

    private static NaturalPerson detailOnlyReplacement(
            NaturalPerson original,
            String preferredName) {
        return original.replaceDetails(
                new NaturalPersonDetails(
                        original.details().givenNames(),
                        original.details().familyNames(),
                        preferredName,
                        original.details().birthDate(),
                        original.details().dateOfDeath(),
                        original.details().birthCountryCode()),
                EVALUATED_ON,
                original.auditInfo().updatedAt(),
                original.auditInfo().updatedBy());
    }

    private static NaturalPerson naturalPerson(TenantId tenantId, String suffix) {
        return NaturalPerson.create(
                partyId(),
                tenantId,
                null,
                new NaturalPersonDetails(
                        "Ada " + suffix,
                        "Lovelace",
                        "Ada",
                        LocalDate.parse("1815-12-10"),
                        LocalDate.parse("1852-11-27"),
                        "GB"),
                EVALUATED_ON,
                CREATED_AT,
                "creator");
    }

    private static TenantId tenantId() {
        return new TenantId(UUID.randomUUID());
    }

    private static PartyId partyId() {
        return new PartyId(UUID.randomUUID());
    }

    private static void assertNaturalPersonEquals(NaturalPerson expected, NaturalPerson actual) {
        assertEquals(expected.partyId(), actual.partyId());
        assertEquals(expected.tenantId(), actual.tenantId());
        assertEquals(expected.type(), actual.type());
        assertEquals(expected.displayName(), actual.displayName());
        assertEquals(expected.recordStatus(), actual.recordStatus());
        assertEquals(expected.version(), actual.version());
        assertEquals(expected.auditInfo(), actual.auditInfo());
        assertEquals(expected.details(), actual.details());
    }

    private static void assertFailure(
            Throwable failure,
            Class<? extends ApplicationFailure> expectedFailureType) {
        ApplicationException exception = assertInstanceOf(ApplicationException.class, failure);
        assertInstanceOf(expectedFailureType, exception.failure());
    }

    /**
     * Captures one concurrent update outcome without failing the combined pipeline.
     */
    private record UpdateAttempt(boolean succeeded, ApplicationFailure failure) {
    }

    /**
     * Captures one concurrent creation outcome without failing the combined
     * pipeline.
     */
    private record CreationAttempt(boolean succeeded, ApplicationFailure failure) {
    }

    /**
     * Counts rows participating in one idempotent creation attempt set.
     */
    private record PersistenceCounts(long parties, long details, long idempotencyRecords) {
    }
}
