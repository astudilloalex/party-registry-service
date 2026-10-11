package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.IdentifierSearchContractAssertions;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.infrastructure.security.IdentifierSearchCpuDispatcher;
import com.alexastudillo.partyregistry.infrastructure.security.JcaIdentifierProtectionAdapter;
import com.alexastudillo.partyregistry.support.IdentifierSearchDatabaseFixture;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual HTTP deadline and CPU admission failures with deterministic resource ownership and fresh request reuse. */
@QuarkusTest
@TestProfile(IdentifierSearchRuntimeContractTest.RuntimeProfile.class)
@Timeout(45)
class IdentifierSearchRuntimeContractTest {
    @Inject Mutiny.SessionFactory sessions;
    @Inject IdentifierSearchCpuDispatcher cpu;
    @Inject Vertx vertx;
    @Inject DataSource dataSource;
    @Inject JcaIdentifierProtectionAdapter protection;
    private UUID tenant;
    private String code;

    @BeforeEach
    void seedProtectedEvidenceOnTestThread() throws Exception {
        tenant = UUID.randomUUID();
        code = "RUNTIME_" + UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            var fixture = new IdentifierSearchDatabaseFixture(connection, protection);
            UUID scheme = fixture.scheme(code, IdentifierSchemeStatus.RETIRED, "TRIM_UPPERCASE_V1");
            UUID party = fixture.party(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED);
            fixture.identifier(tenant, party, scheme, UUID.randomUUID(), "MATCH", PartyIdentifierStatus.REVOKED, false, Instant.EPOCH);
        }
    }

    @Test
    void exhaustedPoolTimesOutBothRoutesAndLateAcquisitionsReturnCapacity() throws Exception {
        assertEquals(200, send(false).statusCode());
        Context origin = vertx.getOrCreateContext();
        var held = new CompletableFuture<Mutiny.Session>();
        origin.runOnContext(ignored -> sessions.openSession().subscribe().with(held::complete, held::completeExceptionally));
        var session = held.get(5, TimeUnit.SECONDS);
        try {
            for (boolean prefix : List.of(false, true)) {
                long started = System.nanoTime();
                var response = send(prefix);
                assertEquals(503, response.statusCode());
                IdentifierSearchContractAssertions.assertErrorResponse(response.statusCode(), "dependency-unavailable", response.asString());
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5000, "Pool exhaustion must terminate within the configured finite budget");
            }
        } finally {
            var closed = new CompletableFuture<Void>();
            origin.runOnContext(ignored -> session.close().subscribe().with(closed::complete, closed::completeExceptionally));
            closed.get(5, TimeUnit.SECONDS);
        }
        for (boolean prefix : List.of(false, true)) {
            var healthy = send(prefix);
            assertEquals(200, healthy.statusCode());
            assertEquals(1, healthy.jsonPath().getInt("totalElements"));
        }
    }

    @Test
    void saturatedCpuRejectsPrefixWithoutCallerFallbackAndRecoversAfterCancellation() throws Exception {
        assertEquals(200, send(true).statusCode());
        Context origin = vertx.getOrCreateContext();
        var entered = new CompletableFuture<Void>();
        var busy = new CompletableFuture<UniAssertSubscriber<String>>();
        var queued = new CompletableFuture<UniAssertSubscriber<String>>();
        var release = new CountDownLatch(1);
        origin.runOnContext(ignored -> busy.complete(cpu.dispatch(cancelled -> {
            assertNull(Vertx.currentContext());
            assertTrue(Thread.currentThread().getName().startsWith("identifier-search-cpu-"));
            entered.complete(null);
            try {
                if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Test CPU barrier timed out");
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test CPU barrier interrupted");
            }
            return "late";
        }).subscribe().withSubscriber(UniAssertSubscriber.create())));
        entered.get(5, TimeUnit.SECONDS);
        origin.runOnContext(ignored -> queued.complete(cpu.dispatch(cancelled -> "queued").subscribe().withSubscriber(UniAssertSubscriber.create())));
        var queuedSubscriber = queued.get(5, TimeUnit.SECONDS);
        var busySubscriber = busy.get(5, TimeUnit.SECONDS);
        try {
            var response = send(true);
            assertEquals(503, response.statusCode());
            IdentifierSearchContractAssertions.assertErrorResponse(response.statusCode(), "dependency-unavailable", response.asString());
            assertEquals(200, send(false).statusCode());
        } finally {
            var cancelled = new CompletableFuture<Void>();
            origin.runOnContext(ignored -> {
                queuedSubscriber.cancel();
                busySubscriber.cancel();
                release.countDown();
                cancelled.complete(null);
            });
            cancelled.get(5, TimeUnit.SECONDS);
        }
        var healthy = send(true);
        assertEquals(200, healthy.statusCode());
        assertEquals(1, healthy.jsonPath().getInt("totalElements"));
        assertNull(queuedSubscriber.getItem());
        assertNull(busySubscriber.getItem());
    }

    private Response send(boolean prefix) {
        String process = UUID.randomUUID().toString();
        var response = given().contentType("application/json").header("Tenant-Id", tenant).header("Process-Id", process)
                .header("User-Id", "runtime-search-test").body(Map.of("identifierSchemeCode", code, prefix ? "prefix" : "value", "MATCH"))
                .post(prefix ? IdentifierSearchContractAssertions.PREFIX_PATH : IdentifierSearchContractAssertions.EXACT_PATH);
        assertEquals(process, response.header("Process-Id"));
        return response;
    }

    /** Makes real pool and CPU exhaustion reproducible while retaining production behavior and enabled blocked-thread diagnostics. */
    public static final class RuntimeProfile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.rabbitmq.devservices.enabled", "false", "quarkus.datasource.reactive.max-size", "1",
                    "party-registry.queries.statement-timeout", "1s", "party-registry.queries.traversal-timeout", "2s",
                    "party-registry.search.cpu-workers", "1", "party-registry.search.cpu-queue-capacity", "1");
        }
    }
}
