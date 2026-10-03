package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.CreateNationalityCommand;
import com.alexastudillo.partyregistry.application.command.PatchNationalityCommand;
import com.alexastudillo.partyregistry.application.command.SetPrimaryNationalityCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.NationalityMutationPort;
import com.alexastudillo.partyregistry.application.port.NationalityReadPort;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
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
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Races independent reactive sessions on the same Party without losing keys or
 * exposing overlapping history.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(120)
class NationalityConcurrencyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, Month.SEPTEMBER, 23);

    @Inject
    RootPartyFixtures fixtures;

    @Inject
    NationalityMutationPort mutations;

    @Inject
    NationalityReadPort reads;

    @Inject
    Mutiny.SessionFactory sessions;

    @Test
    void differentKeysCompeteForTheSameCountryAndOnlyOneSnapshotCommits() throws Exception {
        UUID tenant = UUID.randomUUID();
        PartyId party = seed(tenant);
        List<Attempt> attempts = race(() -> mutations.create(create(tenant, party, "EC", "one", null, null),
                Uni.createFrom()::voidItem),
                () -> mutations.create(create(tenant, party, "EC", "two", null, null),
                        Uni.createFrom()::voidItem));
        assertEquals(1, successes(attempts).size());
        assertInstanceOf(ApplicationFailure.NationalityValidityConflict.class,
                onlyFailure(attempts).failure().failure());
        assertEquals(1L, nationalityCount(party));
        assertEquals(1L, keyCount(tenant));
        assertEquals(1L, rootVersion(party));
    }

    @Test
    void equivalentConcurrentKeysConvergeWithoutRepeatingCountryLookup() throws Exception {
        UUID tenant = UUID.randomUUID();
        PartyId party = seed(tenant);
        AtomicInteger lookups = new AtomicInteger();
        Supplier<Uni<NationalityMutationOutcome>> first = () -> mutations.create(
                create(tenant, party, "EC", "shared", null, null), () -> {
                    lookups.incrementAndGet();
                    return Uni.createFrom().voidItem();
                });
        List<Attempt> attempts = race(first, first);
        assertEquals(2, successes(attempts).size());
        assertEquals(1,
                attempts.stream().filter(
                        result -> result.outcome().disposition() == NationalityMutationOutcome.Disposition.APPLIED)
                        .count());
        assertEquals(1,
                attempts.stream().filter(
                        result -> result.outcome().disposition() == NationalityMutationOutcome.Disposition.REPLAYED)
                        .count());
        assertEquals(attempts.getFirst().outcome().nationality(), attempts.getLast().outcome().nationality());
        assertEquals(1, lookups.get());
        assertEquals(1L, nationalityCount(party));
        assertEquals(1L, keyCount(tenant));
        assertEquals(1L, rootVersion(party));
    }

    @Test
    void sameKeyWithDifferentPartyInputsHasOnlyOneAcceptedSnapshot() throws Exception {
        UUID tenant = UUID.randomUUID();
        PartyId first = seed(tenant);
        PartyId second = seed(tenant);
        List<Attempt> attempts = race(
                () -> mutations.create(create(tenant, first, "EC", "shared-different", null, null),
                        Uni.createFrom()::voidItem),
                () -> mutations.create(create(tenant, second, "GB", "shared-different", null, null),
                        Uni.createFrom()::voidItem));
        assertEquals(1, successes(attempts).size());
        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, onlyFailure(attempts).failure().failure());
        assertEquals(1L, nationalityCount(first) + nationalityCount(second));
        assertEquals(1L, keyCount(tenant));
        assertEquals(1L, rootVersion(first) + rootVersion(second));
    }

    @Test
    void competingPatchesCannotCreateOverlappingSameCountryHistory() throws Exception {
        UUID tenant = UUID.randomUUID();
        PartyId party = seed(tenant);
        NationalityId january = RootPartyFixtures.await(() -> mutations.create(create(tenant, party, "EC", "jan",
                LocalDate.of(2026, Month.JANUARY, 1), LocalDate.of(2026, Month.JANUARY, 10)),
                Uni.createFrom()::voidItem))
                .nationality().nationalityId();
        NationalityId february = RootPartyFixtures.await(() -> mutations.create(create(tenant, party, "EC", "feb",
                LocalDate.of(2026, Month.FEBRUARY, 1), LocalDate.of(2026, Month.FEBRUARY, 10)),
                Uni.createFrom()::voidItem))
                .nationality().nationalityId();
        List<Attempt> attempts = race(
                () -> mutations.patch(new PatchNationalityCommand(metadata(tenant), party, january,
                        FieldUpdate.absent(), FieldUpdate.present(LocalDate.of(2026, Month.JANUARY, 25)))),
                () -> mutations.patch(new PatchNationalityCommand(metadata(tenant), party, february,
                        FieldUpdate.present(LocalDate.of(2026, Month.JANUARY, 20)), FieldUpdate.absent())));
        assertEquals(1, successes(attempts).size());
        assertInstanceOf(ApplicationFailure.NationalityValidityConflict.class,
                onlyFailure(attempts).failure().failure());
        var first = RootPartyFixtures.await(() -> reads.findById(new TenantId(tenant), party, january)).orElseThrow();
        var second = RootPartyFixtures.await(() -> reads.findById(new TenantId(tenant), party, february)).orElseThrow();
        assertFalse(new NationalityPeriod(first.validFrom(), first.validUntil())
                .overlaps(new NationalityPeriod(second.validFrom(), second.validUntil())));
        assertEquals(3L, rootVersion(party));
    }

    @Test
    void competingPrimaryTransfersKeepOnlyOneCurrentDesignationWithoutPartialDemotions() throws Exception {
        UUID tenant = UUID.randomUUID();
        PartyId party = seed(tenant);
        NationalityId ec = RootPartyFixtures.await(() -> mutations.create(create(tenant, party, "EC", "ec",
                null, null), Uni.createFrom()::voidItem)).nationality().nationalityId();
        NationalityId gb = RootPartyFixtures.await(() -> mutations.create(create(tenant, party, "GB", "gb",
                null, null), Uni.createFrom()::voidItem)).nationality().nationalityId();
        List<Attempt> attempts = race(() -> mutations.setPrimary(new SetPrimaryNationalityCommand(
                metadata(tenant), party, ec, TODAY, Optional.empty())),
                () -> mutations.setPrimary(new SetPrimaryNationalityCommand(
                        metadata(tenant), party, gb, TODAY, Optional.empty())));
        assertEquals(2, successes(attempts).size());
        assertNull(attempts.getFirst().failure());
        assertNull(attempts.getLast().failure());
        long designated = List.of(ec, gb).stream().map(id -> RootPartyFixtures.await(() -> reads.findById(
                new TenantId(tenant), party, id)).orElseThrow()).filter(NationalityResult::isPrimary).count();
        assertEquals(1, designated);
        assertEquals(4L, rootVersion(party));
    }

    private PartyId seed(UUID tenant) {
        return new PartyId(RootPartyFixtures.await(() -> fixtures.create(tenant, PartyType.NATURAL_PERSON,
                PartyRecordStatus.ARCHIVED, "Concurrent nationality fixture", Instant.parse("2026-09-20T00:00:00Z"))));
    }

    private static RequestMetadata metadata(UUID tenant) {
        return new RequestMetadata(new TenantId(tenant), "concurrency-actor", UUID.randomUUID());
    }

    private static CreateNationalityCommand create(UUID tenant, PartyId party, String country, String key,
            LocalDate from, LocalDate until) {
        return new CreateNationalityCommand(metadata(tenant), party, country, false,
                new NationalityPeriod(from, until), key);
    }

    private static List<Attempt> race(Supplier<Uni<NationalityMutationOutcome>> first,
            Supplier<Uni<NationalityMutationOutcome>> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> attempt(first, ready, start));
            var two = executor.submit(() -> attempt(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(one.get(25, TimeUnit.SECONDS), two.get(25, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }

    private static Attempt attempt(Supplier<Uni<NationalityMutationOutcome>> work,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return RootPartyFixtures.await(() -> work.get().map(result -> new Attempt(result, null))
                .onFailure(ApplicationException.class).recoverWithItem(failure -> new Attempt(null,
                        (ApplicationException) failure)));
    }

    private static List<Attempt> successes(List<Attempt> attempts) {
        return attempts.stream().filter(attempt -> attempt.failure() == null).toList();
    }

    private static Attempt onlyFailure(List<Attempt> attempts) {
        var failed = attempts.stream().filter(attempt -> attempt.failure() != null).toList();
        assertEquals(1, failed.size());
        assertNotNull(failed.getFirst().failure());
        return failed.getFirst();
    }

    private long nationalityCount(PartyId party) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.createQuery(
                "select count(nationality) from PartyNationalityEntity nationality where nationality.partyId = :partyId",
                Long.class).setParameter("partyId", party.value()).getSingleResult()));
    }

    private long keyCount(UUID tenant) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.createQuery(
                "select count(record) from ApiIdempotencyRecordEntity record where record.id.tenantId = :tenantId and record.id.operation = :operation",
                Long.class).setParameter("tenantId", tenant)
                .setParameter("operation", NationalityOperation.CREATE.label()).getSingleResult()));
    }

    private long rootVersion(PartyId party) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.createQuery(
                "select root.version from PartyEntity root where root.id = :partyId", Long.class)
                .setParameter("partyId", party.value()).getSingleResult()));
    }

    /** Carries the classified result of one independent transactional attempt. */
    private record Attempt(NationalityMutationOutcome outcome, ApplicationException failure) {
    }
}
