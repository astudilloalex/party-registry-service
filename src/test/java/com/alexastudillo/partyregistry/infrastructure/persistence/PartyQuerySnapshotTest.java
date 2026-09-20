package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyNamePredicate;
import com.alexastudillo.partyregistry.application.model.PartyPageSlice;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartySummaryResult;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.infrastructure.observability.PartyPersistenceObservability;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.pgclient.PgException;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coordinates separate database sessions to verify snapshot consistency and cleanup after query timeout or cancellation.
 */
@QuarkusTest
@TestProfile(PartyQuerySnapshotTest.SmallPoolProfile.class)
@Timeout(90)
class PartyQuerySnapshotTest {

    private static final Instant CREATED = LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atStartOfDay().toInstant(ZoneOffset.UTC);

    @Inject
    Mutiny.SessionFactory sessionFactory;
    @Inject
    NaturalPersonPersistenceMapper mapper;
    @Inject
    PartyRootReader rootReader;
    @Inject
    PartyQueryPort queries;
    @Inject
    PartyQueryTimeouts timeouts;
    @Inject
    PartyPersistenceObservability observations;

    @Test
    @RunOnVertxContext
    void countPageAndNeighborsIgnoreAWriteCommittedBetweenTheirStatements(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        List<NaturalPerson> initial = List.of(fixture(tenant, 0), fixture(tenant, 1));
        NaturalPerson added = fixture(tenant, -1);
        var probe = new PartyQuerySessionProbe(sessionFactory, false, ignored -> persistSeparately(List.of(added)));
        var adapter = new HibernateReactivePartyQueryAdapter(probe.factory(), rootReader, timeouts, observations);
        var criteria = criteria(false);
        asserter.execute(() -> persistSeparately(initial));
        asserter.assertThat(() -> adapter.findPage(tenant, criteria, Optional.empty()), page -> {
            assertEquals(2, page.totalElements());
            assertEquals(initial.stream().map(NaturalPerson::partyId).toList(),
                    page.items().stream().map(PartySummaryResult::partyId).toList());
            assertTrue(page.next().isEmpty());
            assertTrue(page.previous().isEmpty());
            assertSnapshot(probe.snapshot());
        });
        asserter.assertThat(() -> queries.findPage(tenant, criteria, Optional.empty()), page -> assertEquals(3, page.totalElements()));
        asserter.execute(probe::awaitClosed);
    }

    @Test
    @RunOnVertxContext
    void canonicalTraversalRetainsOneSnapshotAcrossMultipleBatches(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        List<NaturalPerson> initial = IntStream.range(0, 300).mapToObj(index -> fixture(tenant, index)).toList();
        NaturalPerson added = fixture(tenant, 301);
        var probe = new PartyQuerySessionProbe(sessionFactory, true, ignored -> persistSeparately(List.of(added)));
        var adapter = new HibernateReactivePartyQueryAdapter(probe.factory(), rootReader, timeouts, observations);
        var criteria = criteria(true);
        asserter.execute(() -> persistSeparately(initial));
        asserter.assertThat(() -> adapter.findPage(tenant, criteria, Optional.empty()), page -> {
            assertEquals(300, page.totalElements());
            assertEquals(300, probe.fetchedRows());
            assertEquals(2, probe.batches());
            assertTrue(probe.maximumBatch() <= HibernateReactivePartyQueryAdapter.NAME_SCAN_BATCH_SIZE);
            assertSnapshot(probe.snapshot());
        });
        asserter.assertThat(() -> queries.findPage(tenant, criteria, Optional.empty()), page -> assertEquals(301, page.totalElements()));
        asserter.execute(probe::awaitClosed);
    }

    @Test
    @RunOnVertxContext
    void traversalTimeoutReturnsNoPageAndClosesTheActualReadTransaction(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        var probe = new PartyQuerySessionProbe(sessionFactory, false, ignored -> Uni.createFrom().nothing());
        var limits = new PartyQueryTimeouts(new TestConfiguration(Duration.ofSeconds(1), Duration.ofSeconds(2)));
        var adapter = new HibernateReactivePartyQueryAdapter(probe.factory(), rootReader, limits, observations);
        asserter.assertFailedWith(() -> adapter.findPage(tenant, criteria(false), Optional.empty()), failure -> {
            var application = assertInstanceOf(ApplicationException.class, failure);
            assertInstanceOf(ApplicationFailure.PersistenceFailure.class, application.failure());
            assertSnapshot(probe.snapshot());
        });
        asserter.execute(probe::awaitClosed);
        asserter.execute(() -> assertReleased(probe.snapshot().backendPid()));
        asserter.assertThat(() -> queries.findPage(tenant, criteria(false), Optional.empty()), page -> assertEquals(0, page.totalElements()));
    }

    @Test
    @RunOnVertxContext
    void serverStatementDeadlineInterruptsSlowSqlAndReleasesItsTransaction(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        var probe = new PartyQuerySessionProbe(sessionFactory, false,
                session -> session.createNativeQuery("select 1 from pg_sleep(10)", Integer.class)
                        .getSingleResult().replaceWithVoid());
        var limits = new PartyQueryTimeouts(new TestConfiguration(Duration.ofMillis(100), Duration.ofSeconds(2)));
        var adapter = new HibernateReactivePartyQueryAdapter(probe.factory(), rootReader, limits, observations);
        asserter.assertFailedWith(() -> adapter.findPage(tenant, criteria(false), Optional.empty()), failure -> {
            var application = assertInstanceOf(ApplicationException.class, failure);
            assertInstanceOf(ApplicationFailure.PersistenceFailure.class, application.failure());
            assertEquals(100, probe.snapshot().statementTimeoutMillis());
            assertTrue(hasSqlState(failure, "57014"));
        });
        asserter.execute(probe::awaitClosed);
        asserter.execute(() -> assertReleased(probe.snapshot().backendPid()));
    }

    @Test
    @RunOnVertxContext
    void cancellationAfterARealReadReleasesItsSnapshotAndDoesNotEmitAPartialPage(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        for (int attempt = 0; attempt < 3; attempt++) {
            asserter.execute(() -> cancelOneRead(tenant));
        }
        asserter.assertThat(() -> queries.findPage(tenant, criteria(false), Optional.empty()), page -> assertEquals(0, page.totalElements()));
    }

    private Uni<Void> cancelOneRead(TenantId tenant) {
        var reached = new CompletableFuture<Void>();
        var probe = new PartyQuerySessionProbe(sessionFactory, false,
                ignored -> Uni.createFrom().<Void>emitter(emitter -> reached.complete(null)));
        var adapter = new HibernateReactivePartyQueryAdapter(probe.factory(), rootReader, timeouts, observations);
        UniAssertSubscriber<PartyPageSlice> subscriber = adapter.findPage(tenant, criteria(false), Optional.empty())
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        return Uni.createFrom().completionStage(reached).ifNoItem().after(Duration.ofSeconds(5)).fail()
                .invoke(subscriber::cancel).invoke(() -> assertNull(subscriber.getItem()))
                .chain(probe::awaitClosed).chain(() -> assertReleased(probe.snapshot().backendPid()))
                .eventually(subscriber::cancel);
    }

    private Uni<Void> persistSeparately(List<NaturalPerson> parties) {
        return sessionFactory.openSession().flatMap(session -> session.withTransaction(transaction ->
                session.persistAll(parties.stream().map(mapper::toEntity).toArray()))
                .eventually(session::close)).ifNoItem().after(Duration.ofSeconds(30)).fail();
    }

    private Uni<Void> assertReleased(int backendPid) {
        return sessionFactory.withSession(session -> session.createNativeQuery("""
                select current_setting('transaction_isolation'), current_setting('transaction_read_only'),
                       (select count(*) from pg_stat_activity where pid = :pid and pid <> pg_backend_pid()
                        and xact_start is not null)
                """, Object[].class).setParameter("pid", backendPid).getSingleResult().invoke(row -> {
                    assertEquals("read committed", row[0]);
                    assertEquals("off", row[1]);
                    assertEquals(0, ((Number) row[2]).longValue());
                })).replaceWithVoid();
    }

    private static void assertSnapshot(PartyQuerySessionProbe.Snapshot snapshot) {
        assertEquals("repeatable read", snapshot.isolation());
        assertEquals("on", snapshot.readOnly());
        assertTrue(snapshot.statementTimeoutMillis() > 0);
    }

    private static boolean hasSqlState(Throwable failure, String sqlState) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof PgException postgres && sqlState.equals(postgres.getSqlState())) {
                return true;
            }
            if (current instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static PartySearchCriteria criteria(boolean names) {
        return new PartySearchCriteria(null, null, new PartyNamePredicate(names ? "match" : null, null), null, null, 50);
    }

    private static NaturalPerson fixture(TenantId tenant, int index) {
        return NaturalPerson.restore(new PartyId(UUID.randomUUID()), tenant, "Match " + index, PartyRecordStatus.DRAFT,
                PartyVersion.initial(), AuditInfo.initial(CREATED.minusSeconds(index), "snapshot-fixture"),
                new NaturalPersonDetails("Snapshot", "Fixture", null, null, null, "GB"));
    }

    /** Supplies finite test budgets without weakening the deployed configuration. */
    private record TestConfiguration(Duration statementTimeout, Duration traversalTimeout) implements PartyQueryConfiguration {
    }

    /** Uses two connections so repeated cancellation would expose unreleased pooled connections immediately. */
    public static final class SmallPoolProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.datasource.reactive.max-size", "2", "quarkus.rabbitmq.devservices.enabled", "false");
        }
    }
}
