package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.ChangePartyLifecycleCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.application.usecase.ChangePartyLifecycleUseCase;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import com.alexastudillo.partyregistry.support.StoredOutboxTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Races complete lifecycle workflows on independent Vert.x contexts and database transactions behind an explicit start barrier. */
@QuarkusTest
@TestProfile(StoredOutboxTestProfile.class)
@Timeout(120)
class PartyLifecycleConcurrencyTest {

    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Inject
    RootPartyFixtures fixtures;
    @Inject
    ChangePartyLifecycleUseCase lifecycle;
    @Inject
    PartyQueryPort queries;
    @Inject
    Mutiny.SessionFactory sessions;

    @Test
    void equivalentKeysConvergeOnOneResultEventAndVersionForEveryActionAndType() throws Exception {
        for (PartyType type : PartyType.values()) {
            for (PartyLifecycleAction action : PartyLifecycleAction.values()) {
                UUID tenant = UUID.randomUUID();
                UUID id = seed(tenant, type, action);
                var first = command(tenant, id, action, "same-key", "first-actor");
                var second = command(tenant, id, action, "same-key", "second-actor");
                List<Attempt> attempts = race(first, second);
                attempts.forEach(attempt -> assertNull(attempt.failure()));
                assertEquals(attempts.getFirst().outcome().party(), attempts.getLast().outcome().party());
                assertEquals(1, attempts.stream().filter(attempt -> attempt.outcome().disposition() == PartyMutationOutcome.Disposition.APPLIED).count());
                assertEquals(1, attempts.stream().filter(attempt -> attempt.outcome().disposition() == PartyMutationOutcome.Disposition.REPLAYED).count());
                assertEquals(1, attempts.getFirst().outcome().party().version().value());
                assertEquals(attempts.getFirst().outcome().party(), RootPartyFixtures.await(() -> queries.findDetails(new TenantId(tenant), new PartyId(id))).orElseThrow());
                assertCounts(tenant, 1, 1);
            }
        }
    }

    @Test
    void conflictingSameKeyForDifferentPartiesCannotAcceptTwoMutations() throws Exception {
        for (PartyType type : PartyType.values()) {
            UUID tenant = UUID.randomUUID();
            UUID one = seed(tenant, type, PartyLifecycleAction.ARCHIVE);
            UUID two = seed(tenant, type, PartyLifecycleAction.ARCHIVE);
            var originalOne = RootPartyFixtures.await(() -> queries.findDetails(new TenantId(tenant), new PartyId(one))).orElseThrow();
            var originalTwo = RootPartyFixtures.await(() -> queries.findDetails(new TenantId(tenant), new PartyId(two))).orElseThrow();
            List<Attempt> attempts = race(command(tenant, one, PartyLifecycleAction.ARCHIVE, "shared-key", "one"),
                    command(tenant, two, PartyLifecycleAction.ARCHIVE, "shared-key", "two"));
            Attempt winner = onlySuccess(attempts);
            assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, onlyFailure(attempts).failure().failure());
            var originalLoser = winner.outcome().party().partyId().value().equals(one) ? originalTwo : originalOne;
            assertEquals(originalLoser, RootPartyFixtures.await(() -> queries.findDetails(originalLoser.tenantId(), originalLoser.partyId())).orElseThrow());
            assertCounts(tenant, 1, 1);
        }
    }

    @Test
    void distinctAndUnkeyedCommandsHaveOneWinnerAndOneStaleVersionWithoutLosingRecords() throws Exception {
        for (PartyType type : PartyType.values()) {
            for (PartyLifecycleAction action : PartyLifecycleAction.values()) {
                for (boolean keyed : List.of(false, true)) {
                    UUID tenant = UUID.randomUUID();
                    UUID id = seed(tenant, type, action);
                    List<Attempt> attempts = race(command(tenant, id, action, keyed ? "one-key" : null, "one"),
                            command(tenant, id, action, keyed ? "two-key" : null, "two"));
                    Attempt winner = onlySuccess(attempts);
                    assertInstanceOf(ApplicationFailure.StalePartyVersion.class, onlyFailure(attempts).failure().failure());
                    assertEquals(PartyMutationOutcome.Disposition.APPLIED, winner.outcome().disposition());
                    assertEquals(1, winner.outcome().party().version().value());
                    assertCounts(tenant, keyed ? 1 : 0, 1);
                }
            }
        }
    }

    private UUID seed(UUID tenant, PartyType type, PartyLifecycleAction action) {
        PartyRecordStatus state = action == PartyLifecycleAction.DEACTIVATE ? PartyRecordStatus.ACTIVE : PartyRecordStatus.DRAFT;
        UUID id = RootPartyFixtures.await(() -> fixtures.create(tenant, type, state, "Original", CLOCK.instant()));
        if (action == PartyLifecycleAction.ACTIVATE) {
            RootPartyFixtures.await(() -> sessions.withTransaction((session, transaction) -> session.createNativeQuery("""
                    insert into party_identifiers (tenant_id, party_id, identifier_scheme_id, encrypted_value, encryption_key_version,
                        normalized_value_hash, masked_value, status, verified_at, verified_by, created_by, updated_by)
                    values (:tenant, :party, :scheme, 'test-only-unread-ciphertext', 1, :hash, '***1234',
                        cast('VERIFIED' as party_identifier_status), :verifiedAt, 'fixture', 'fixture', 'fixture')
                    """).setParameter("tenant", tenant).setParameter("party", id).setParameter("scheme", IdentifierSchemeTestFixtures.BOTH_RETIRED_ID)
                    .setParameter("hash", UUID.randomUUID().toString().replace("-", "").repeat(2))
                    .setParameter("verifiedAt", CLOCK.instant()).executeUpdate()));
        }
        return id;
    }

    private List<Attempt> race(ChangePartyLifecycleCommand first, ChangePartyLifecycleCommand second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
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

    private Attempt attempt(ChangePartyLifecycleCommand command, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        // Each bridge invocation creates a separate Vert.x context; the real mutation adapter opens a fresh session per subscription.
        return RootPartyFixtures.await(() -> lifecycle.execute(command).map(outcome -> new Attempt(outcome, null))
                .onFailure(ApplicationException.class).recoverWithItem(failure -> new Attempt(null, (ApplicationException) failure)));
    }

    private void assertCounts(UUID tenant, long snapshots, long events) {
        Object[] counts = RootPartyFixtures.await(() -> sessions.withSession(session -> session.createNativeQuery("""
                select (select count(*) from api_idempotency_records where tenant_id = :tenant),
                       (select count(*) from party_outbox_events where tenant_id = :tenant)
                """, Object[].class).setParameter("tenant", tenant).getSingleResult()));
        assertEquals(snapshots, ((Number) counts[0]).longValue());
        assertEquals(events, ((Number) counts[1]).longValue());
    }

    private static ChangePartyLifecycleCommand command(UUID tenant, UUID id, PartyLifecycleAction action, String key, String actor) {
        return new ChangePartyLifecycleCommand(new RequestMetadata(new TenantId(tenant), actor, UUID.randomUUID()),
                new PartyId(id), PartyVersion.initial(), action, Optional.ofNullable(key));
    }

    private static Attempt onlySuccess(List<Attempt> attempts) {
        List<Attempt> successes = attempts.stream().filter(attempt -> attempt.failure() == null).toList();
        assertEquals(1, successes.size());
        return successes.getFirst();
    }

    private static Attempt onlyFailure(List<Attempt> attempts) {
        List<Attempt> failures = attempts.stream().filter(attempt -> attempt.failure() != null).toList();
        assertEquals(1, failures.size());
        return failures.getFirst();
    }

    /** Retains an accepted workflow result or its transport-neutral business failure without recovering unexpected failures. */
    private record Attempt(PartyMutationOutcome outcome, ApplicationException failure) {
    }
}
