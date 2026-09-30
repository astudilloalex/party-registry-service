package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.NationalityPeriod;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyNationality;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises root serialization, one version advance, rollback and exact named
 * exclusion classification.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(90)
class NationalityMutationTransactionTest {

    private static final Instant AT = Instant.parse("2026-09-23T10:00:00Z");
    private static final NationalityPeriod OPEN = new NationalityPeriod(null, null);

    @Inject
    Mutiny.SessionFactory sessions;

    @Inject
    NationalityMutationTransaction transactions;

    @Test
    void serializesWithAnotherPartyRootWriterAndAdvancesVersionOnlyOnce() throws Exception {
        PartyEntity party = party();
        seed(party, List.of());
        RequestMetadata metadata = metadata(party);
        PartyId id = new PartyId(party.id());
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> RootPartyFixtures
                    .await(() -> transactions.execute(metadata, scope -> scope.lockRoot(id).invoke(locked::countDown)
                            .call(() -> Uni.createFrom().completionStage(() -> {
                                io.vertx.core.Context context = io.vertx.core.Vertx.currentContext();
                                var completion = new java.util.concurrent.CompletableFuture<Void>();
                                executor.submit(() -> {
                                    try {
                                        if (release.await(5, TimeUnit.SECONDS)) {
                                            context.runOnContext(ignored -> completion.complete(null));
                                        } else {
                                            context.runOnContext(ignored -> completion.completeExceptionally(
                                                    new IllegalStateException("Lock gate timed out")));
                                        }
                                    } catch (InterruptedException exception) {
                                        context.runOnContext(ignored -> completion.completeExceptionally(exception));
                                    }
                                });
                                return completion;
                            }))
                            .chain(() -> scope.advanceRoot(id)).replaceWith("first"))));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> RootPartyFixtures.await(
                    () -> transactions.execute(metadata, scope -> scope.lockRoot(id).invoke(secondLocked::countDown)
                            .chain(() -> scope.advanceRoot(id)).replaceWith("second"))));
            assertFalse(secondLocked.await(200, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals("first", first.get(10, TimeUnit.SECONDS));
            assertEquals("second", second.get(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
        assertEquals(2L, version(party));
        assertEquals(AT, RootPartyFixtures.await(() -> sessions.withSession(session -> session.find(
                PartyEntity.class, party.id()).map(PartyEntity::updatedAt))));
    }

    @Test
    void translatesOnlyNamedExclusionConstraintsAndRollsBackFailedWrites() {
        PartyEntity party = party();
        PartyNationalityEntity original = nationality(party, "EC", true);
        seed(party, List.of(original));
        PartyId id = new PartyId(party.id());
        RequestMetadata metadata = metadata(party);

        assertFailure(party, "EC", false, ApplicationFailure.NationalityValidityConflict.class);
        assertFailure(party, "CO", true, ApplicationFailure.PrimaryNationalityConflict.class);
        assertEquals(0L, version(party));
        assertEquals(1L, count(party));

        ApplicationException unrelated = org.junit.jupiter.api.Assertions.assertThrows(ApplicationException.class,
                () -> RootPartyFixtures.await(() -> transactions.execute(metadata, scope -> scope.lockRoot(id)
                        .chain(() -> Uni.createFrom().failure(new IllegalStateException("internal"))))));
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, unrelated.failure());
        assertEquals(0L, version(party));
    }

    private void assertFailure(PartyEntity party, String country, boolean primary, Class<?> expected) {
        PartyNationalityEntity conflicting = nationality(party, country, primary);
        ApplicationException failure = org.junit.jupiter.api.Assertions.assertThrows(ApplicationException.class,
                () -> RootPartyFixtures.await(() -> transactions.execute(metadata(party),
                        scope -> scope.lockRoot(new PartyId(party.id()))
                                .call(() -> scope.session().persist(conflicting))
                                .call(scope.session()::flush)
                                .chain(() -> scope.advanceRoot(new PartyId(party.id())))
                                .replaceWith(conflicting.id()))));
        assertInstanceOf(expected, failure.failure());
    }

    private static PartyEntity party() {
        return new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.NATURAL_PERSON,
                "Mutation fixture", PartyRecordStatus.ARCHIVED, AuditInfo.initial(AT, "tester"), 0);
    }

    private static PartyNationalityEntity nationality(PartyEntity party, String country, boolean primary) {
        return new PartyNationalityEntity(new PartyNationality(new NationalityId(UUID.randomUUID()),
                new PartyId(party.id()), country, primary, OPEN, AuditInfo.initial(AT, "tester")));
    }

    private void seed(PartyEntity party, List<PartyNationalityEntity> history) {
        RootPartyFixtures.await(() -> sessions.withTransaction((session, transaction) -> {
            Uni<Void> sequence = session.persist(party);
            for (PartyNationalityEntity nationalityEntity : history) {
                sequence = sequence.call(() -> session.persist(nationalityEntity));
            }
            return sequence.call(session::flush);
        }));
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

    private static RequestMetadata metadata(PartyEntity party) {
        return new RequestMetadata(new TenantId(party.tenantId()), "tester", UUID.randomUUID());
    }
}
