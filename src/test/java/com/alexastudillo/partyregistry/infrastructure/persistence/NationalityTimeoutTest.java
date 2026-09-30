package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.CreateNationalityCommand;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.NationalityMutationPort;
import com.alexastudillo.partyregistry.domain.model.NationalityPeriod;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves a stalled new validation times out without consuming its key or
 * retaining the root lock.
 */
@QuarkusTest
@TestProfile(NationalityTimeoutTestProfile.class)
@Timeout(45)
class NationalityTimeoutTest {

    @Inject
    RootPartyFixtures fixtures;

    @Inject
    NationalityMutationPort mutations;

    @Inject
    Mutiny.SessionFactory sessions;

    @Test
    void timeoutRollsBackTheKeyAndAllowsAnEquivalentRetry() {
        UUID tenant = UUID.randomUUID();
        PartyId party = new PartyId(RootPartyFixtures.await(() -> fixtures.create(tenant, PartyType.NATURAL_PERSON,
                PartyRecordStatus.ARCHIVED, "Timeout fixture", Instant.parse("2026-09-20T10:00:00Z"))));
        CreateNationalityCommand command = new CreateNationalityCommand(
                new RequestMetadata(new TenantId(tenant), "actor", UUID.randomUUID()), party,
                "EC", false, new NationalityPeriod(null, null), "timed-out-key");
        assertThrows(RuntimeException.class, () -> RootPartyFixtures.await(() -> mutations.create(command,
                () -> Uni.createFrom().nothing())));
        assertEquals(0L, countKeys(tenant));
        assertEquals(0L, countNationalities(party));
        RootPartyFixtures.await(() -> mutations.create(command, Uni.createFrom()::voidItem));
        assertEquals(1L, countNationalities(party));
    }

    @Test
    void cancellationBeforeCountryValidationFinishesReleasesTheKeyForRetry() throws Throwable {
        UUID tenant = UUID.randomUUID();
        PartyId party = new PartyId(RootPartyFixtures.await(() -> fixtures.create(tenant, PartyType.LEGAL_ENTITY,
                PartyRecordStatus.ARCHIVED, "Cancellation fixture", Instant.parse("2026-09-20T10:00:00Z"))));
        CreateNationalityCommand command = new CreateNationalityCommand(new RequestMetadata(new TenantId(tenant),
                "actor", UUID.randomUUID()), party, "EC", false, new NationalityPeriod(null, null), "cancelled-key");
        CountDownLatch validating = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicReference<Context> context = new AtomicReference<>();
        AtomicReference<UniAssertSubscriber<com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome>> subscriber = new AtomicReference<>();
        VertxContextSupport.subscribeAndAwait(() -> {
            context.set(Vertx.currentContext());
            subscriber.set(mutations.create(command,
                    () -> Uni.createFrom().<Void>nothing()
                            .onSubscription().invoke(validating::countDown)
                            .onCancellation().invoke(cancelled::countDown))
                    .subscribe().withSubscriber(UniAssertSubscriber.create()));
            return Uni.createFrom().voidItem();
        });
        assertTrue(validating.await(5, TimeUnit.SECONDS));
        context.get().runOnContext(ignored -> subscriber.get().cancel());
        assertTrue(cancelled.await(5, TimeUnit.SECONDS));
        RootPartyFixtures.await(() -> mutations.create(command, Uni.createFrom()::voidItem));
        assertEquals(1L, countNationalities(party));
        assertEquals(1L, countKeys(tenant));
    }

    private long countKeys(UUID tenant) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.createQuery(
                "select count(record) from ApiIdempotencyRecordEntity record where record.id.tenantId = :tenant",
                Long.class).setParameter("tenant", tenant).getSingleResult()));
    }

    private long countNationalities(PartyId party) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.createQuery(
                "select count(nationality) from PartyNationalityEntity nationality where nationality.partyId = :party",
                Long.class).setParameter("party", party.value()).getSingleResult()));
    }
}
