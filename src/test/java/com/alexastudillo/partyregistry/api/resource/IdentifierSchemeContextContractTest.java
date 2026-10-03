package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.api.filter.RequestContextFilter;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static com.alexastudillo.partyregistry.api.resource.IdentifierSchemeHttpAssertions.*;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies single-filter header ordering, partial/full completion MDC, replay
 * disposition, concurrent cleanup, and management exemption.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSchemeContextContractTest {
    @Inject
    MeterRegistry meters;

    @Test
    void everyRouteRequiresExactlyOneContextBeforeAnyBodyPathOrOperationHeader() {
        for (Route route : Route.values()) {
            var context = Context.fresh();
            for (String header : List.of("Process-Id", "Tenant-Id", "User-Id")) {
                String prefix = header.toLowerCase(Locale.ROOT);
                String echo = header.equals("Process-Id") ? null : context.process();
                error(route.send(context.without(header)), 400, prefix + "-required", echo);
                error(route.send(context.without(header).header(header, "private-one", "private-two")), 400,
                        prefix + "-duplicated", echo);
            }
            error(route.send(given()), 400, "process-id-required", (String) null);
            error(route.send(given().header("Process-Id", context.process())), 400, "tenant-id-required", context);
            for (String value : List.of("private-invalid", "0198D111-08F1-7E48-B291-399BBB9CD604", "1-1-1-1-1")) {
                error(route.send(context.without("Process-Id").header("Process-Id", value)), 400, "process-id-invalid",
                        (String) null);
                error(route.send(context.without("Tenant-Id").header("Tenant-Id", value)), 400, "tenant-id-invalid",
                        context);
            }
            error(route.send(context.without("User-Id").header("User-Id", " ")), 400, "user-id-blank", context);
            error(route.send(context.without("User-Id").header("User-Id", "x".repeat(129))), 400, "user-id-too-long",
                    context);
            error(route.send(context.without("User-Id").header("User-Id", "unsafe\tuser")), 400, "user-id-unsafe",
                    context);
            Response valid = route.send(given().header("process-id", context.process())
                    .header("tenant-id", context.tenant()).header("user-id", "x".repeat(128)));
            assertEquals(route.validStatus(), valid.statusCode(), valid.asString());
            assertEquals(context.process(), valid.header("Process-Id"));
        }
    }

    @Test
    void completionLogsRetainOnlyValidatedContextOnEarlyAndLateFailures() {
        try (var capture = new Capture()) {
            for (Route route : Route.values()) {
                var context = Context.fresh();
                error(route.send(context.without("Tenant-Id").header("Tenant-Id", "private-rejected-tenant")), 400,
                        "tenant-id-invalid", context);
                capture.assertContext(context.process(), null, null, route.operation, "tenant-id-invalid");
                var missing = Context.fresh();
                error(route.send(missing.without("User-Id")), 400, "user-id-required", missing);
                capture.assertContext(missing.process(), missing.tenant(), null, route.operation, "user-id-required");
                route.send(context.without("Process-Id").header("Process-Id", "private-rejected-process"));
                var valid = Context.fresh();
                Response response = route.send(valid.request());
                assertEquals(route.validStatus(), response.statusCode());
                capture.assertContext(valid.process(), valid.tenant(), valid.user(), route.operation,
                        route.validCode());
            }
            var uncorrelated = capture.entries.stream().filter(entry -> entry.process() == null).toList();
            assertEquals(8, uncorrelated.size());
            uncorrelated.forEach(entry -> {
                assertNull(entry.tenant());
                assertNull(entry.user());
            });
            assertFalse(capture.entries.toString().contains("private-rejected"));
        }
    }

    @Test
    void concurrentRequestsAndSubsequentInvalidRequestsCannotInheritAnotherContext() throws Exception {
        var contexts = new ArrayList<Context>();
        var routes = new ArrayList<Route>();
        for (Route route : Route.values()) {
            for (int attempt = 0; attempt < 2; attempt++) {
                contexts.add(Context.fresh());
                routes.add(route);
            }
        }
        var ready = new CountDownLatch(contexts.size());
        var start = new CountDownLatch(1);
        try (var capture = new Capture(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Response>> pending = new ArrayList<>();
            for (int index = 0; index < contexts.size(); index++) {
                Context context = contexts.get(index);
                Route route = routes.get(index);
                boolean missing = index % 2 == 1;
                pending.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return route.send(context.without(missing ? "User-Id" : ""));
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (int index = 0; index < pending.size(); index++) {
                var response = pending.get(index).get(20, TimeUnit.SECONDS);
                var context = contexts.get(index);
                var route = routes.get(index);
                boolean missing = index % 2 == 1;
                assertEquals(missing ? 400 : route.validStatus(), response.statusCode());
                assertEquals(context.process(), response.header("Process-Id"));
                capture.assertContext(context.process(), context.tenant(), missing ? null : context.user(),
                        route.operation,
                        missing ? "user-id-required" : route.validCode());
            }
            for (Route route : Route.values()) {
                error(route.send(given()), 400, "process-id-required", (String) null);
            }
            assertEquals(24, capture.entries.size());
            capture.entries.stream().filter(entry -> entry.process() == null).forEach(entry -> {
                assertNull(entry.tenant());
                assertNull(entry.user());
            });
        } finally {
            start.countDown();
        }
    }

    @Test
    void realSuccessAndHistoricalReplayKeepFreshCompletionContextAndBoundedDisposition() {
        var original = Context.fresh();
        var retry = new Context(original.tenant(), "retry-user", UUID.randomUUID().toString());
        String input = body(uniqueCode());
        String key = "private-replay-" + UUID.randomUUID();
        double appliedBefore = disposition("create-identifier-scheme", "applied");
        double replayBefore = disposition("create-identifier-scheme", "replayed");
        try (var capture = new Capture()) {
            var first = success(original.request().contentType("application/json").header("Idempotency-Key", key)
                    .body(input).post(ROOT), 201, original);
            capture.assertContext(original.process(), original.tenant(), original.user(), "create-identifier-scheme",
                    "successful");
            assertEquals(appliedBefore + 1, disposition("create-identifier-scheme", "applied"));
            for (String action : List.of("activate", "deprecate", "retire")) {
                var context = new Context(original.tenant(), "action-user", UUID.randomUUID().toString());
                int version = List.of("activate", "deprecate", "retire").indexOf(action);
                String operation = action + "-identifier-scheme";
                double before = disposition(operation, "applied");
                double replayedBefore = disposition(operation, "replayed");
                var accepted = success(
                        context.request().header("If-Match", Integer.toString(version)).header("Idempotency-Key", key)
                                .post(ROOT + "/" + first.get("id") + "/" + action),
                        200, context);
                capture.assertContext(context.process(), context.tenant(), context.user(), operation, "successful");
                assertEquals(before + 1, disposition(operation, "applied"));
                assertEquals(accepted,
                        success(retry.request().header("If-Match", Integer.toString(version))
                                .header("Idempotency-Key", key)
                                .post(ROOT + "/" + first.get("id") + "/" + action), 200, retry));
                capture.assertContext(retry.process(), retry.tenant(), retry.user(), operation, "successful");
                assertEquals(replayedBefore + 1, disposition(operation, "replayed"));
            }
            assertEquals(first, success(retry.request().contentType("application/json").header("Idempotency-Key", key)
                    .body(input).post(ROOT), 201, retry));
            assertEquals(replayBefore + 1, disposition("create-identifier-scheme", "replayed"));
            capture.assertContext(retry.process(), retry.tenant(), retry.user(), "create-identifier-scheme",
                    "successful");
            var patchContext = Context.fresh();
            var target = create(patchContext, uniqueCode());
            double patchBefore = disposition("patch-identifier-scheme", "applied");
            var patchAttempt = new Context(patchContext.tenant(), "patch-context-user", UUID.randomUUID().toString());
            success(patchAttempt.request().contentType("application/json").header("If-Match", "0")
                    .body("{\"name\":\"Context edit\"}").patch(ROOT + "/" + target.get("id")), 200, patchAttempt);
            capture.assertContext(patchAttempt.process(), patchAttempt.tenant(), patchAttempt.user(),
                    "patch-identifier-scheme", "successful");
            assertEquals(patchBefore + 1, disposition("patch-identifier-scheme", "applied"));
            assertFalse(capture.entries.toString().contains(key));
        }
    }

    @Test
    void managementIsExemptAndTheExactLoggingFormatRemainsConfigured() {
        try (var capture = new Capture()) {
            Response response = given().header("Process-Id", "invalid-management").get("/q/health/live");
            assertEquals(200, response.statusCode());
            assertNull(response.header("Process-Id"));
            assertTrue(capture.entries.isEmpty());
        }
        assertEquals(
                "%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [userId=%X{userId}] [tenantId=%X{tenantId}] %s%e%n",
                ConfigProvider.getConfig().getValue("quarkus.log.console.format", String.class));
    }

    private double disposition(String operation, String outcome) {
        return meters.find("party.registry.http.identifier.scheme.mutation")
                .tags("operation", operation, "outcome", outcome).counters()
                .stream().mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
    }

    /**
     * Sends valid operation syntax for each real route; item targets are isolated
     * absent selectors.
     */
    private enum Route {
        CREATE("POST", "", "create-identifier-scheme"), LIST("GET", "", "list-identifier-schemes"),
        GET("GET", "/{id}", "retrieve-identifier-scheme"),
        CODE("GET", "/by-code/{id}", "retrieve-identifier-scheme-by-code"),
        PATCH("PATCH", "/{id}", "patch-identifier-scheme"),
        ACTIVATE("POST", "/{id}/activate", "activate-identifier-scheme"),
        DEPRECATE("POST", "/{id}/deprecate", "deprecate-identifier-scheme"),
        RETIRE("POST", "/{id}/retire", "retire-identifier-scheme");

        private final String method;
        private final String suffix;
        private final String operation;

        Route(String method, String suffix, String operation) {
            this.method = method;
            this.suffix = suffix;
            this.operation = operation;
        }

        private Response send(RequestSpecification request) {
            if (this == CREATE)
                request.contentType("application/json").body(body(uniqueCode()));
            if (this == PATCH)
                request.contentType("application/json").body("{\"name\":\"Valid\"}");
            return request.header("Idempotency-Key", UUID.randomUUID().toString()).header("If-Match", "0")
                    .request(method, ROOT + suffix.replace("{id}", UUID.randomUUID().toString()));
        }

        private int validStatus() {
            return this == CREATE ? 201 : this == LIST ? 200 : 404;
        }

        private String validCode() {
            return this == CREATE || this == LIST ? "successful" : "identifier-scheme-not-found";
        }
    }

    /**
     * Snapshots validated MDC at request completion before the production filter
     * clears its owned keys.
     */
    private record Completion(String process, String tenant, String user, String message) {
    }

    /**
     * Collects completion records synchronously in memory and always removes its
     * test-only handler.
     */
    private static final class Capture extends Handler implements AutoCloseable {
        private final Logger logger = Logger.getLogger(RequestContextFilter.class.getName());
        private final List<Completion> entries = new CopyOnWriteArrayList<>();

        private Capture() {
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord logRecord) {
            if (logRecord instanceof ExtLogRecord extended) {
                String message = new SimpleFormatter().formatMessage(logRecord);
                if (message.startsWith("Request completed")) {
                    entries.add(new Completion(extended.getMdc("processId"), extended.getMdc("tenantId"),
                            extended.getMdc("userId"), message));
                }
            }
        }

        private void assertContext(String process, String tenant, String user, String operation, String code) {
            var matches = entries.stream()
                    .filter(entry -> process.equals(entry.process())
                            && entry.message().contains("operation=" + operation + " ")
                            && entry.message().contains("code=" + code + " "))
                    .toList();
            assertEquals(1, matches.size(), entries.toString());
            assertEquals(tenant, matches.getFirst().tenant());
            assertEquals(user, matches.getFirst().user());
        }

        @Override
        public void flush() {
            // Completion records are copied synchronously.
        }

        @Override
        public void close() {
            logger.removeHandler(this);
        }
    }
}
