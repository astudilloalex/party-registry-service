package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies read failures are classified after actual owned reactive query/transaction cleanup. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(90)
class IdentifierSchemeReadFailureBoundaryTest {
    @Inject Mutiny.SessionFactory sessions;
    @Inject IdentifierSchemePersistenceMapper mapper;
    @Inject PartyQueryTimeouts timeouts;

    @Test
    @RunOnVertxContext
    void connectionAcquisitionIsClassifiedButUnknownAndCancellationArePreserved(UniAsserter asserter) {
        for (Throwable failure : new Throwable[]{new ConnectException("private"), new IllegalStateException("private"), new CancellationException()}) {
            var factory = Mutiny.SessionFactory.class.cast(Proxy.newProxyInstance(Mutiny.SessionFactory.class.getClassLoader(),
                    new Class<?>[]{Mutiny.SessionFactory.class}, (ignored, method, arguments) -> {
                        if (method.getName().equals("openSession")) { return Uni.createFrom().failure(failure); }
                        throw new AssertionError(method.getName());
                    }));
            var reader = new HibernateReactiveIdentifierSchemeReadAdapter(factory, mapper, timeouts);
            asserter.assertFailedWith(() -> reader.findById(new IdentifierSchemeId(UUID.randomUUID())), actual -> {
                if (failure instanceof ConnectException) {
                    assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, assertInstanceOf(ApplicationException.class, actual).failure());
                } else { assertSame(failure, actual); }
            });
        }
    }

    @Test
    @RunOnVertxContext
    void genuineServerAndTraversalTimeoutsCloseReadOnlySessionBefore503(UniAsserter asserter) {
        var scheme = IdentifierScheme.create(new IdentifierSchemeId(UUID.randomUUID()), "Read-" + UUID.randomUUID(), "GB", IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH, "Name", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", null, null, false,
                Instant.parse("2026-10-01T11:00:00Z"), "creator");
        asserter.execute(() -> sessions.withTransaction((session, transaction) -> session.persist(mapper.toEntity(scheme))));
        for (var budget : new QueryBudgets[]{new QueryBudgets(Duration.ofMillis(100), Duration.ofSeconds(3)),
                new QueryBudgets(Duration.ofMillis(400), Duration.ofMillis(100))}) {
            var opened = new AtomicReference<Mutiny.Session>();
            var reader = new HibernateReactiveIdentifierSchemeReadAdapter(slowFactory(opened), mapper, new PartyQueryTimeouts(budget));
            asserter.assertFailedWith(() -> reader.findById(scheme.id()), failure -> {
                assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, assertInstanceOf(ApplicationException.class, failure).failure());
                assertFalse(opened.get().isOpen());
            });
        }
        asserter.assertThat(() -> new HibernateReactiveIdentifierSchemeReadAdapter(sessions, mapper, timeouts).findById(scheme.id()),
                found -> assertEquals(scheme.id(), found.orElseThrow().id()));
    }

    private Mutiny.SessionFactory slowFactory(AtomicReference<Mutiny.Session> opened) {
        return Mutiny.SessionFactory.class.cast(Proxy.newProxyInstance(Mutiny.SessionFactory.class.getClassLoader(),
                new Class<?>[]{Mutiny.SessionFactory.class}, (ignored, method, arguments) -> {
                    if (!method.getName().equals("openSession")) { throw new AssertionError(method.getName()); }
                    return sessions.openSession().invoke(opened::set).map(session -> Mutiny.Session.class.cast(
                            Proxy.newProxyInstance(Mutiny.Session.class.getClassLoader(), new Class<?>[]{Mutiny.Session.class},
                                    (proxy, operation, parameters) -> {
                                        if (operation.getName().equals("createSelectionQuery")) {
                                            return session.createNativeQuery("""
                                                    select scheme.* from identifier_schemes scheme cross join pg_sleep(1)
                                                    where scheme.id = :id
                                                    """, IdentifierSchemeEntity.class);
                                        }
                                        try { return operation.invoke(session, parameters); }
                                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                                    })));
                }));
    }

    /** Supplies independent query statement/traversal limits using the existing settings contract. */
    private record QueryBudgets(Duration statementTimeout, Duration traversalTimeout) implements PartyQueryConfiguration { }
}
