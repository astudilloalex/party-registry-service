package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies natural-person lookup and update persistence against PostgreSQL.
 */
@QuarkusTest
class ReactivePersistenceAdapterTest {

    private static final LocalDate EVALUATED_ON = LocalDate.parse("2026-08-30");
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T10:15:30.123456Z");

    @Inject
    NaturalPersonRepository repository;

    @Inject
    NaturalPersonPersistenceMapper mapper;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    @RunOnVertxContext
    void loadsNaturalPeopleAndConcealsNonMatchingRows(UniAsserter asserter) {
        TenantId tenantId = tenantId();
        TenantId otherTenant = tenantId();
        NaturalPerson naturalPerson = naturalPerson(tenantId, "original");
        PartyId missingDetailsId = partyId();
        PartyId legalEntityId = partyId();

        asserter.execute(() -> persistNaturalPerson(naturalPerson));
        asserter.assertThat(
                () -> repository.findByTenantAndId(tenantId, naturalPerson.partyId()),
                found -> assertNaturalPersonEquals(naturalPerson, found.orElseThrow()));
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
    }

    @Test
    @RunOnVertxContext
    void incrementsVersionForDetailOnlyUpdateWithUnchangedRootValues(UniAsserter asserter) {
        TenantId tenantId = tenantId();
        NaturalPerson original = naturalPerson(tenantId, "detail-only");
        NaturalPerson updated = detailOnlyReplacement(original, "Countess Lovelace");

        asserter.execute(() -> persistNaturalPerson(original));
        asserter.assertThat(
                () -> repository.update(updated, PartyVersion.initial()),
                persisted -> {
                    assertEquals(new PartyVersion(1), persisted.version());
                    assertEquals(original.displayName(), persisted.displayName());
                    assertEquals(original.auditInfo(), persisted.auditInfo());
                    assertEquals("COUNTESS LOVELACE", persisted.details().preferredName());
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

        asserter.execute(() -> persistNaturalPerson(original));
        asserter.assertThat(
                () -> repository.update(updated, PartyVersion.initial()),
                persisted -> {
                    assertEquals(new PartyVersion(1), persisted.version());
                    assertEquals("GRACE HOPPER", persisted.displayName());
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

        asserter.execute(() -> persistNaturalPerson(original));
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
                            persisted.details().preferredName().equals("FIRST PREFERENCE")
                                    || persisted.details().preferredName()
                                            .equals("SECOND PREFERENCE"));
                });
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

    private Uni<Void> persistNaturalPerson(NaturalPerson naturalPerson) {
        PartyEntity party = mapper.toEntity(naturalPerson);
        return sessionFactory.withTransaction((session, transaction) -> session.persist(party)
                .call(session::flush));
    }

    private Uni<UpdateAttempt> attemptUpdate(NaturalPerson candidate) {
        return repository.update(candidate, PartyVersion.initial())
                .map(ignored -> new UpdateAttempt(true, null))
                .onFailure(ApplicationException.class)
                .recoverWithItem(failure -> new UpdateAttempt(
                        false,
                        failure.failure()));
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

    /**
     * Captures one concurrent update outcome without failing the combined pipeline.
     */
    private record UpdateAttempt(boolean succeeded, ApplicationFailure failure) {
    }
}
