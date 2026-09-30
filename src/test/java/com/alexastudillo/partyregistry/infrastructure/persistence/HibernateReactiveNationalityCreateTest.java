package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.CreateNationalityCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.NationalityMutationPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NationalityPeriod;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Exercises keyed nationality insertion, replay order, tenant concealment, and failure rollback. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(60)
class HibernateReactiveNationalityCreateTest {

    private static final Instant CREATED = Instant.parse("2026-09-23T10:00:00Z");

    @Inject
    NationalityMutationPort mutations;

    @Inject
    Mutiny.SessionFactory sessions;

    @Test
    void acceptsBothPartyTypesAndReplaysHistoricalResultWithoutAnotherCountryCheck() {
        for (PartyType type : PartyType.values()) {
            PartyEntity party = seed(type, PartyRecordStatus.ARCHIVED);
            var first = command(party, "EC", "key", "first");
            AtomicInteger lookups = new AtomicInteger();
            NationalityMutationOutcome created = RootPartyFixtures.await(() -> mutations.create(first,
                    () -> { lookups.incrementAndGet(); return Uni.createFrom().voidItem(); }));
            assertEquals(NationalityMutationOutcome.Disposition.APPLIED, created.disposition());
            assertEquals("EC", created.nationality().countryCode());
            assertEquals(7, created.nationality().nationalityId().value().version());
            assertEquals(1L, version(party));
            NationalityMutationOutcome replayed = RootPartyFixtures.await(() -> mutations.create(
                    command(party, "EC", "key", "another"), () -> {
                        lookups.incrementAndGet();
                        return Uni.createFrom().failure(new IllegalStateException("Replay must not validate country"));
                    }));
            assertEquals(NationalityMutationOutcome.Disposition.REPLAYED, replayed.disposition());
            assertEquals(created.nationality(), replayed.nationality());
            assertEquals(1, lookups.get());
            assertEquals(1L, count(party));
            assertEquals(1L, version(party));
        }
    }

    @Test
    void rejectsChangedKeyBeforeLookupAndDoesNotConsumeFailedKeys() {
        PartyEntity party = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        PartyEntity other = seed(PartyType.LEGAL_ENTITY, PartyRecordStatus.DRAFT);
        RootPartyFixtures.await(() -> mutations.create(command(party, "EC", "reused", "first"),
                Uni.createFrom()::voidItem));
        AtomicInteger lookups = new AtomicInteger();
        for (CreateNationalityCommand changed : new CreateNationalityCommand[] {
                command(party, "CO", "reused", "other"), command(other, "EC", "reused", "other")}) {
            // A key is tenant-scoped: use the first Party's tenant for the different-Party attempt.
            CreateNationalityCommand attempt = changed.partyId().equals(new PartyId(other.id()))
                    ? new CreateNationalityCommand(new RequestMetadata(new TenantId(party.tenantId()), "other", UUID.randomUUID()),
                            changed.partyId(), "EC", false, new NationalityPeriod(null, null), "reused") : changed;
            ApplicationException conflict = org.junit.jupiter.api.Assertions.assertThrows(ApplicationException.class,
                    () -> RootPartyFixtures.await(() -> mutations.create(attempt, () -> {
                        lookups.incrementAndGet();
                        return Uni.createFrom().voidItem();
                    })));
            assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, conflict.failure());
        }
        assertEquals(0, lookups.get());
        assertEquals(0L, count(other));

        PartyEntity hidden = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.ACTIVE);
        CreateNationalityCommand concealed = new CreateNationalityCommand(new RequestMetadata(
                new TenantId(party.tenantId()), "actor", UUID.randomUUID()), new PartyId(hidden.id()),
                "EC", false, new NationalityPeriod(null, null), "hidden-key");
        ApplicationException notFound = org.junit.jupiter.api.Assertions.assertThrows(ApplicationException.class,
                () -> RootPartyFixtures.await(() -> mutations.create(concealed, () -> {
                    lookups.incrementAndGet(); return Uni.createFrom().voidItem();
                })));
        assertInstanceOf(ApplicationFailure.PartyNotFound.class, notFound.failure());
        assertEquals(0, lookups.get());

        ApplicationException dependency = org.junit.jupiter.api.Assertions.assertThrows(ApplicationException.class,
                () -> RootPartyFixtures.await(() -> mutations.create(command(party, "CO", "failed-key", "actor"),
                        () -> Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.DependencyUnavailable("geographic-reference"))))));
        assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, dependency.failure());
        assertEquals(1L, count(party));
        assertEquals(0L, records(party, "failed-key"));
        ApplicationException unrecognized = org.junit.jupiter.api.Assertions.assertThrows(ApplicationException.class,
                () -> RootPartyFixtures.await(() -> mutations.create(command(party, "ZZ", "unknown-key", "actor"),
                        () -> Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.UnrecognizedNationalityCountry())))));
        assertInstanceOf(ApplicationFailure.UnrecognizedNationalityCountry.class, unrecognized.failure());
        assertEquals(0L, records(party, "unknown-key"));
        NationalityMutationOutcome retry = RootPartyFixtures.await(() -> mutations.create(
                command(party, "CO", "failed-key", "actor"), Uni.createFrom()::voidItem));
        assertEquals(NationalityMutationOutcome.Disposition.APPLIED, retry.disposition());
        assertEquals(2L, count(party));
        assertNull(retry.nationality().validFrom());
    }

    private PartyEntity seed(PartyType type, PartyRecordStatus status) {
        PartyEntity party = new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), type,
                "Nationality create fixture", status, AuditInfo.initial(CREATED, "fixture"), 0);
        RootPartyFixtures.await(() -> sessions.withTransaction((session, transaction) -> session.persist(party)));
        return party;
    }

    private static CreateNationalityCommand command(PartyEntity party, String country, String key, String actor) {
        return new CreateNationalityCommand(new RequestMetadata(new TenantId(party.tenantId()), actor, UUID.randomUUID()),
                new PartyId(party.id()), country, false, new NationalityPeriod(null, null), key);
    }

    private long version(PartyEntity party) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.createQuery(
                "select party.version from PartyEntity party where party.id = :id", Long.class)
                .setParameter("id", party.id()).getSingleResult()));
    }

    private long count(PartyEntity party) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.createQuery(
                "select count(nationality) from PartyNationalityEntity nationality where nationality.partyId = :id",
                Long.class).setParameter("id", party.id()).getSingleResult()));
    }

    private long records(PartyEntity party, String key) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.createQuery(
                "select count(entry) from ApiIdempotencyRecordEntity entry where entry.id.tenantId = :tenant and entry.id.idempotencyKey = :key",
                Long.class).setParameter("tenant", party.tenantId()).setParameter("key", key).getSingleResult()));
    }
}
