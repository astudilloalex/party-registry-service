package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Exercises actual PostgreSQL snapshot ownership, finite pool acquisition and reuse after late allocation. */
@QuarkusTest
@TestProfile(IdentifierSearchReadSessionTest.SingleConnectionProfile.class)
class IdentifierSearchReadSessionTest {
    @Inject Mutiny.SessionFactory sessions;
    @Inject IdentifierSearchReadSession reads;
    @Inject IdentifierSchemePersistenceMapper schemes;

    @Test
    @RunOnVertxContext
    void ownsAReadOnlySnapshotAndRejectsEscapedContext(UniAsserter asserter) {
        var escaped = new AtomicReference<IdentifierSearchReadSession.ReadView>();
        asserter.assertEquals(() -> reads.read(view -> {
            escaped.set(view);
            return view.findSchemeByCode("EC_NATIONAL_ID").chain(scheme -> view.query(session ->
                    session.createNativeQuery("select current_setting('transaction_isolation') || '/' || current_setting('transaction_read_only')", String.class)
                            .getSingleResult()));
        }), "repeatable read/on");
        asserter.assertFailedWith(() -> escaped.get().findSchemeByCode("EC_NATIONAL_ID"), IllegalStateException.class);
        asserter.assertEquals(() -> reads.read(view -> view.findSchemeByCode("ec_national_id").map(java.util.Optional::isEmpty)), true);
    }

    @Test
    @RunOnVertxContext
    void exhaustedAcquisitionTimesOutAndLateAllocationDoesNotConsumeTheOnlyConnection(UniAsserter asserter) {
        var boundedReads = shortReads();
        var held = new AtomicReference<Mutiny.Session>();
        var invoked = new AtomicInteger();
        asserter.execute(() -> sessions.openSession().invoke(held::set));
        asserter.assertFailedWith(() -> boundedReads.read(view -> {
            invoked.incrementAndGet();
            return view.findSchemeByCode("EC_NATIONAL_ID");
        }), failure -> {
            var unavailable = assertInstanceOf(ApplicationException.class, failure);
            assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, unavailable.failure());
        });
        asserter.execute(() -> held.get().close());
        asserter.assertEquals(() -> reads.read(view -> view.findSchemeByCode("EC_NATIONAL_ID").map(java.util.Optional::isPresent))
                .ifNoItem().after(Duration.ofSeconds(3)).fail(), true);
        asserter.execute(() -> assertEquals(0, invoked.get()));
    }

    @Test
    @RunOnVertxContext
    void statementOrTraversalFailureClosesTheOwnedTransactionBeforeConnectionReuse(UniAsserter asserter) {
        var boundedReads = shortReads();
        asserter.assertFailedWith(() -> boundedReads.read(view -> view.query(session ->
                session.createNativeQuery("select 'done' from pg_sleep(1)", String.class).getSingleResult())),
                failure -> {
                    var unavailable = assertInstanceOf(ApplicationException.class, failure);
                    assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, unavailable.failure());
                });
        asserter.assertEquals(() -> reads.read(view -> view.findSchemeByCode("EC_NATIONAL_ID").map(java.util.Optional::isPresent)), true);
    }

    private IdentifierSearchReadSession shortReads() {
        return new IdentifierSearchReadSession(sessions, schemes,
                new TestDeadlines(Duration.ofMillis(200), Duration.ofMillis(300)));
    }

    /** Applies short finite budgets only to cases that deliberately provoke unavailability. */
    private record TestDeadlines(Duration statementTimeout, Duration traversalTimeout) implements PartyQueryConfiguration {
    }

    /** Limits the reactive pool to expose leaked late acquisitions deterministically. */
    public static final class SingleConnectionProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.rabbitmq.devservices.enabled", "false", "quarkus.datasource.reactive.max-size", "1");
        }
    }
}
