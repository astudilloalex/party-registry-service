package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyNamePredicate;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.alexastudillo.partyregistry.infrastructure.observability.PartyPersistenceObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Records an instrumented reference-volume name scan and verifies bounded batches with exact selective totals.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(90)
class PartyQueryVolumeTest {

    private static final Logger LOG = Logger.getLogger(PartyQueryVolumeTest.class);
    private static final int ROWS = 2000;
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Inject
    Mutiny.SessionFactory sessionFactory;
    @Inject
    PartyRootReader rootReader;
    @Inject
    PartyQueryTimeouts timeouts;

    @Test
    @RunOnVertxContext
    void measuresASelectiveMultiBatchScanWithExactTotals(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        var probe = new PartyQuerySessionProbe(sessionFactory, true, ignored -> Uni.createFrom().voidItem());
        var adapter = new HibernateReactivePartyQueryAdapter(probe.factory(), rootReader, timeouts, new PartyPersistenceObservability(registry));
        var criteria = new PartySearchCriteria(null, null, new PartyNamePredicate(" straße ", " Águila_% "), null, null, 50);
        asserter.execute(() -> seed(tenant));
        asserter.assertThat(() -> Uni.createFrom().deferred(() -> {
            long started = System.nanoTime();
            return adapter.findPage(tenant, criteria, Optional.empty()).invoke(page -> LOG.infof(
                    "Party query reference scan rows=%d matches=%d batches=%d maximumBatch=%d pageSize=%d durationMillis=%d",
                    probe.fetchedRows(), page.totalElements(), probe.batches(), probe.maximumBatch(), page.items().size(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
        }), page -> {
            assertEquals(ROWS / 4, page.totalElements());
            assertEquals(50, page.items().size());
            assertEquals(ROWS, probe.fetchedRows());
            assertEquals(Math.ceilDiv(ROWS, HibernateReactivePartyQueryAdapter.NAME_SCAN_BATCH_SIZE), probe.batches());
            assertTrue(probe.maximumBatch() <= HibernateReactivePartyQueryAdapter.NAME_SCAN_BATCH_SIZE);
            assertTrue(page.previous().isEmpty());
            assertTrue(page.next().isPresent());
            assertEquals(ROWS, registry.get("party.registry.collection.scan.rows").tag("outcome", "success").summary().totalAmount());
            assertEquals(8, registry.get("party.registry.collection.scan.batches").tag("outcome", "success").summary().totalAmount());
            assertEquals(1, registry.get("party.registry.collection.scan").tag("outcome", "success").timer().count());
        });
        asserter.execute(probe::awaitClosed);
    }

    private Uni<Void> seed(TenantId tenant) {
        var created = LocalDate.of(2026, Month.SEPTEMBER, 19).atStartOfDay().toInstant(ZoneOffset.UTC);
        return sessionFactory.withTransaction((session, transaction) -> session.createNativeQuery("""
                with seeded as (
                    insert into parties (tenant_id, type, display_name, record_status, created_at, created_by, updated_at, updated_by)
                    select :tenant, cast('NATURAL_PERSON' as party_type),
                           case when n % 4 = 0 then 'Straße  Águila_%' else 'Unrelated' end,
                           cast('DRAFT' as party_record_status),
                           cast(:created as timestamptz) - n * interval '1 microsecond', 'volume-fixture',
                           cast(:created as timestamptz), 'volume-fixture'
                    from generate_series(1, :size) as generated(n)
                    returning id
                )
                insert into natural_person_details (party_id, given_names, family_names, created_by, updated_by)
                select id, 'Volume', 'Fixture', 'volume-fixture', 'volume-fixture' from seeded
                """).setParameter("tenant", tenant.value()).setParameter("created", created).setParameter("size", ROWS)
                .executeUpdate().replaceWithVoid()).ifNoItem().after(Duration.ofSeconds(30)).fail();
    }
}
