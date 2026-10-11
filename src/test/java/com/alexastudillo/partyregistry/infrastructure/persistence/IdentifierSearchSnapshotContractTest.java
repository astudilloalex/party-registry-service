package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.*;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchReadPort;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;
import com.alexastudillo.partyregistry.domain.policy.StandardIdentifierNormalizer;
import com.alexastudillo.partyregistry.infrastructure.security.IdentifierSearchCpuDispatcher;
import com.alexastudillo.partyregistry.infrastructure.security.JcaIdentifierProtectionAdapter;
import com.alexastudillo.partyregistry.support.IdentifierSearchDatabaseFixture;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Coordinates genuine writes between count/projection and detached candidate batches to prove one immutable read view. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSearchSnapshotContractTest {
    @Inject Mutiny.SessionFactory sessions;
    @Inject IdentifierSchemePersistenceMapper schemes;
    @Inject PartyQueryConfiguration configuration;
    @Inject IdentifierSearchCpuDispatcher cpu;
    @Inject JcaIdentifierProtectionAdapter protection;
    @Inject DataSource dataSource;
    @Inject HibernateReactiveIdentifierSearchReadAdapter actualReads;
    @Inject io.vertx.core.Vertx vertx;
    private TenantId tenant;
    private IdentifierSchemeId scheme;
    private String code;

    @Test
    void successfulInvalidCancelledAndCorruptSearchesPreserveEveryStoredBusinessColumn() throws Exception {
        String baseline = fingerprint();
        assertEquals(200, http("by-identifier", java.util.Map.of("identifierSchemeCode", code, "value", "MATCH-SAME")).statusCode());
        assertEquals(200, http("by-identifier-prefix", java.util.Map.of("identifierSchemeCode", code, "prefix", "MATCH", "limit", 2)).statusCode());
        assertEquals(400, http("by-identifier-prefix", java.util.Map.of("identifierSchemeCode", code, "prefix", true)).statusCode());
        assertEquals(baseline, fingerprint());

        var entered = new java.util.concurrent.CompletableFuture<Void>();
        var subscriber = new java.util.concurrent.CompletableFuture<io.smallrye.mutiny.helpers.test.UniAssertSubscriber<PrefixIdentifierSearchSlice>>();
        var origin = vertx.getOrCreateContext();
        var scope = new IdentifierSearchScope(tenant, scheme, code, selector("MATCH"), filters(), 2);
        origin.runOnContext(ignored -> subscriber.complete(actualReads.withRead(tenant, view -> view.findPrefix(scope, Optional.empty())
                .invoke(slice -> entered.complete(null)).chain(() -> Uni.createFrom().<PrefixIdentifierSearchSlice>nothing()))
                .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.UniAssertSubscriber.create())));
        entered.get(15, java.util.concurrent.TimeUnit.SECONDS);
        var active = subscriber.get(5, java.util.concurrent.TimeUnit.SECONDS);
        var cancelled = new java.util.concurrent.CompletableFuture<Void>();
        origin.runOnContext(ignored -> {
            active.cancel();
            cancelled.complete(null);
        });
        cancelled.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(200, http("by-identifier-prefix", java.util.Map.of("identifierSchemeCode", code, "prefix", "MATCH")).statusCode());
        assertEquals(baseline, fingerprint());

        try (var connection = dataSource.getConnection(); var corrupt = connection.prepareStatement(
                "update party_identifiers set encrypted_value = 'v1.CORRUPT' where tenant_id = ?")) {
            corrupt.setObject(1, tenant.value());
            corrupt.executeUpdate();
        }
        String corruptBaseline = fingerprint();
        assertEquals(500, http("by-identifier-prefix", java.util.Map.of("identifierSchemeCode", code, "prefix", "MATCH")).statusCode());
        assertEquals(corruptBaseline, fingerprint());
    }

    @BeforeEach
    void seedProtectedSnapshotEvidenceOnTheTestThread() throws Exception {
        tenant = new TenantId(UUID.randomUUID());
        code = "SNAPSHOT_" + UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            var fixture = new IdentifierSearchDatabaseFixture(connection, protection);
            scheme = new IdentifierSchemeId(fixture.scheme(code, IdentifierSchemeStatus.RETIRED, "TRIM_UPPERCASE_V1"));
            UUID party = fixture.party(tenant.value(), PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED);
            for (int index = 0; index < 520; index++) {
                fixture.identifier(tenant.value(), party, scheme.value(), UUID.randomUUID(), index < 201 ? "MATCH-SAME" : "MATCH-" + index,
                        PartyIdentifierStatus.REVOKED, false, Instant.EPOCH.plusSeconds(index));
            }
        }
    }

    @Test
    @RunOnVertxContext
    void interveningWriterCannotSplitExactCountAndFullProjection(UniAsserter asserter) {
        var written = new AtomicBoolean();
        IdentifierSearchReadPort reads = observedReads(Phase.COUNT, written);
        asserter.assertThat(() -> reads.withRead(tenant, view -> view.findSchemeByCode(code).chain(() ->
                view.findExact(scheme, selector("MATCH-SAME"), filters()).call(() -> view.findSchemeByCode(code).invoke(found ->
                        assertEquals("Synthetic Search Scheme", found.orElseThrow().name()))))), slice -> {
            assertTrue(written.get());
            assertEquals(201, slice.totalElements());
            assertEquals(201, slice.items().size());
            assertTrue(slice.items().stream().allMatch(hit -> hit.recordStatus() == PartyRecordStatus.ARCHIVED
                    && hit.status() == PartyIdentifierStatus.REVOKED && hit.displayName().equals("Stored Synthetic Label")));
        });
        asserter.assertEquals(() -> reads.withRead(tenant, view -> view.findExact(scheme, selector("MATCH-SAME"), filters()))
                .map(ExactIdentifierSearchSlice::totalElements), 0L);
        asserter.assertEquals(() -> reads.withRead(tenant, view -> view.findSchemeByCode(code)).map(found -> found.orElseThrow().name()), "Changed Scheme");
    }

    @Test
    @RunOnVertxContext
    void interveningWriterCannotSplitPrefixBatchesCountsOrNavigation(UniAsserter asserter) {
        var written = new AtomicBoolean();
        IdentifierSearchReadPort reads = observedReads(Phase.BATCH, written);
        var scope = new IdentifierSearchScope(tenant, scheme, code, selector("MATCH"), filters(), 50);
        asserter.assertThat(() -> reads.withRead(tenant, view -> view.findSchemeByCode(code).chain(() ->
                view.findPrefix(scope, Optional.empty()).call(() -> view.findSchemeByCode(code).invoke(found ->
                        assertEquals("Synthetic Search Scheme", found.orElseThrow().name()))))), slice -> {
            assertTrue(written.get());
            assertEquals(520, slice.totalElements());
            assertEquals(50, slice.items().size());
            assertTrue(slice.next().isPresent());
            assertTrue(slice.previous().isEmpty());
            assertTrue(slice.items().stream().allMatch(hit -> hit.recordStatus() == PartyRecordStatus.ARCHIVED && hit.status() == PartyIdentifierStatus.REVOKED));
        });
        asserter.assertEquals(() -> reads.withRead(tenant, view -> view.findPrefix(scope, Optional.empty()))
                .map(PrefixIdentifierSearchSlice::totalElements), 0L);
    }

    private IdentifierSearchReadPort observedReads(Phase phase, AtomicBoolean written) {
        var intercepted = Mutiny.SessionFactory.class.cast(Proxy.newProxyInstance(Mutiny.SessionFactory.class.getClassLoader(),
                new Class<?>[]{Mutiny.SessionFactory.class}, new ReadInterceptor(sessions, phase, written, this::write)));
        var owner = new IdentifierSearchReadSession(intercepted, schemes, configuration);
        return new HibernateReactiveIdentifierSearchReadAdapter(owner, protection, cpu);
    }

    private io.restassured.response.Response http(String operation, java.util.Map<String, Object> body) {
        String process = UUID.randomUUID().toString();
        var response = io.restassured.RestAssured.given().contentType("application/json").header("Tenant-Id", tenant.value())
                .header("Process-Id", process).header("User-Id", "read-only-search-test").body(body).post("/v1/parties/search/" + operation);
        assertEquals(process, response.header("Process-Id"));
        return response;
    }

    private String fingerprint() throws Exception {
        try (var connection = dataSource.getConnection()) {
            return new IdentifierSearchDatabaseFixture(connection, protection).fingerprint();
        }
    }

    private Uni<Void> write() {
        return sessions.withTransaction(writer -> writer.createNativeMutationQuery(
                "update party_identifiers set status = 'EXPIRED', version = version + 1 where tenant_id = :tenant")
                .setParameter("tenant", tenant.value()).executeUpdate()
                .chain(() -> writer.createNativeMutationQuery("update parties set record_status = 'INACTIVE', display_name = 'Changed Party', version = version + 1 where tenant_id = :tenant")
                        .setParameter("tenant", tenant.value()).executeUpdate())
                .chain(() -> writer.createNativeMutationQuery("update identifier_schemes set name = 'Changed Scheme', version = version + 1 where id = :id")
                        .setParameter("id", scheme.value()).executeUpdate())).replaceWithVoid();
    }

    private static IdentifierSearchFilters filters() {
        return IdentifierSearchFilters.of(List.of(PartyIdentifierStatus.REVOKED), List.of(PartyRecordStatus.ARCHIVED));
    }

    private static IdentifierSearchSelector selector(String text) {
        return new IdentifierSearchSelector(StandardIdentifierNormalizer.TRIM_UPPERCASE_V1, text);
    }

    /** Selects only a real count completion or first protected candidate batch completion as the writer barrier. */
    private enum Phase { COUNT, BATCH }

    /** Delegates actual Hibernate operations while inserting one asynchronous writer between selected query results and consumption. */
    private static final class ReadInterceptor implements java.lang.reflect.InvocationHandler {
        private final Object target;
        private final Phase phase;
        private final AtomicBoolean written;
        private final Supplier<Uni<Void>> writer;
        private final boolean selectedQuery;

        private ReadInterceptor(Object target, Phase phase, AtomicBoolean written, Supplier<Uni<Void>> writer) {
            this(target, phase, written, writer, false);
        }

        private ReadInterceptor(Object target, Phase phase, AtomicBoolean written, Supplier<Uni<Void>> writer, boolean selectedQuery) {
            this.target = target;
            this.phase = phase;
            this.written = written;
            this.writer = writer;
            this.selectedQuery = selectedQuery;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            Object result;
            try {
                result = method.invoke(target, arguments);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
            if (result == target) return proxy;
            if (method.getName().equals("openSession") && result instanceof Uni<?> acquisition) {
                return acquisition.map(session -> Proxy.newProxyInstance(Mutiny.Session.class.getClassLoader(), new Class<?>[]{Mutiny.Session.class},
                        new ReadInterceptor(session, phase, written, writer)));
            }
            if (method.getName().equals("createSelectionQuery") && arguments != null && arguments[0] instanceof String hql) {
                boolean selected = phase == Phase.COUNT ? hql.startsWith("select count(i.id)") : hql.contains("i.encryptedValue");
                return Proxy.newProxyInstance(Mutiny.SelectionQuery.class.getClassLoader(), new Class<?>[]{Mutiny.SelectionQuery.class},
                        new ReadInterceptor(result, phase, written, writer, selected));
            }
            if (selectedQuery && result instanceof Uni<?> query && (method.getName().equals("getSingleResult") || method.getName().equals("getResultList"))) {
                return query.call(ignored -> written.compareAndSet(false, true) ? writer.get() : Uni.createFrom().voidItem());
            }
            return result;
        }
    }
}
