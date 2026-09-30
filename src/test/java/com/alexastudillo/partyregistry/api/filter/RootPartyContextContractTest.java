package com.alexastudillo.partyregistry.api.filter;

import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies root-route header precedence, concurrent completion MDC, historical replay attribution, and management exclusions. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class RootPartyContextContractTest {

    private static final String ROOT = "/v1/parties";
    private static final String PROCESS = "Process-Id";
    private static final String TENANT = "Tenant-Id";
    private static final String USER = "User-Id";
    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Inject
    RootPartyFixtures fixtures;

    @Test
    void requiresSingleContextHeadersOnEveryRootRouteBeforeOperationValidation() {
        for (Route route : Route.values()) {
            var context = Context.fresh();
            for (String header : List.of(PROCESS, TENANT, USER)) {
                String prefix = header.toLowerCase(Locale.ROOT);
                String echo = header.equals(PROCESS) ? null : context.process();
                error(route.send(context.without(header)), prefix + "-required", echo);
                error(route.send(context.without(header).header(header, "rejected-one", "rejected-two")), prefix + "-duplicated", echo);
            }
            error(route.send(given()), "process-id-required", null);
            error(route.send(given().header(PROCESS, context.process())), "tenant-id-required", context.process());
        }
    }

    @Test
    void validatesCanonicalValuesAndUserBoundariesWithoutEchoingRejectedProcessValues() {
        for (Route route : Route.values()) {
            var context = Context.fresh();
            error(route.send(context.without(PROCESS).header(PROCESS, "invalid-private-process")), "process-id-invalid", null);
            error(route.send(context.without(PROCESS).header(PROCESS, "0198CE2B-D6A3-7D6E-80BA-D97B21D793E5")), "process-id-invalid", null);
            error(route.send(context.without(TENANT).header(TENANT, "invalid-private-tenant")), "tenant-id-invalid", context.process());
            error(route.send(context.without(USER).header(USER, " ")), "user-id-blank", context.process());
            error(route.send(context.without(USER).header(USER, "x".repeat(129))), "user-id-too-long", context.process());
            error(route.send(context.without(USER).header(USER, "unsafe\tuser")), "user-id-unsafe", context.process());
            Response accepted = route.send(given().header("process-id", context.process()).header("tenant-id", context.tenant())
                    .header("user-id", "x".repeat(128)));
            assertEquals(route == Route.LIST ? 200 : 404, accepted.statusCode());
            assertEquals(context.process(), accepted.header(PROCESS));
        }
    }

    @Test
    void completionLogsRetainOnlyAcceptedPartsAndExcludeRejectedValues() {
        try (var capture = new CompletionCapture()) {
            for (Route route : Route.values()) {
                var context = Context.fresh();
                error(route.send(context.without(TENANT).header(TENANT, "private-rejected-tenant")), "tenant-id-invalid", context.process());
                capture.assertContext(context.process(), null, null, route, "tenant-id-invalid");
                var missingUser = Context.fresh();
                error(route.send(missingUser.without(USER)), "user-id-required", missingUser.process());
                capture.assertContext(missingUser.process(), missingUser.tenant(), null, route, "user-id-required");
                error(route.send(context.without(PROCESS).header(PROCESS, "private-rejected-process")), "process-id-invalid", null);
            }
            List<Completion> uncorrelated = capture.entries.stream().filter(entry -> entry.process() == null).toList();
            assertEquals(Route.values().length, uncorrelated.size());
            uncorrelated.forEach(entry -> {
                assertNull(entry.tenant());
                assertNull(entry.user());
            });
            assertFalse(capture.entries.toString().contains("private-rejected"));
        }
    }

    @Test
    void concurrentRequestsKeepTheirOwnCompleteOrPartialContext() throws Exception {
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
        try (var capture = new CompletionCapture(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Response>> pending = new ArrayList<>();
            for (int index = 0; index < contexts.size(); index++) {
                Context context = contexts.get(index);
                Route route = routes.get(index);
                boolean missingUser = index % 2 == 1;
                pending.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return route.send(context.without(missingUser ? USER : ""));
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (int index = 0; index < pending.size(); index++) {
                Response response = pending.get(index).get(20, TimeUnit.SECONDS);
                Context context = contexts.get(index);
                Route route = routes.get(index);
                boolean missingUser = index % 2 == 1;
                int validStatus = route == Route.LIST ? 200 : 404;
                String validCode = route == Route.LIST ? "successful" : "party-not-found";
                assertEquals(missingUser ? 400 : validStatus, response.statusCode());
                assertEquals(context.process(), response.header(PROCESS));
                capture.assertContext(context.process(), context.tenant(), missingUser ? null : context.user(), route,
                        missingUser ? "user-id-required" : validCode);
            }
            assertEquals(contexts.size(), capture.entries.size());
        } finally {
            start.countDown();
        }
    }

    @Test
    void replayKeepsOriginalAuditButEchoesAndLogsTheNewRequestContext() {
        try (var capture = new CompletionCapture()) {
            var original = Context.fresh();
            UUID tenant = UUID.fromString(original.tenant());
            UUID id = RootPartyFixtures.await(() -> fixtures.create(tenant, PartyType.LEGAL_ENTITY,
                    PartyRecordStatus.ACTIVE, "Historical", CLOCK.instant()));
            String path = ROOT + "/" + id;
            String key = "private-replay-key-" + UUID.randomUUID();
            Response first = original.without("").header("If-Match", "0").header("Idempotency-Key", key).post(path + "/deactivate");
            assertEquals(200, first.statusCode());
            assertEquals(200, original.without("").header("If-Match", "1").post(path + "/archive").statusCode());
            var retry = new Context(original.tenant(), "retry-user", UUID.randomUUID().toString());
            Response replay = retry.without("").header("If-Match", "0").header("Idempotency-Key", key).post(path + "/deactivate");
            assertEquals(200, replay.statusCode());
            assertEquals(first.jsonPath().getMap("data"), replay.jsonPath().getMap("data"));
            assertEquals(original.user(), replay.jsonPath().getString("data.updatedBy"));
            assertEquals(retry.process(), replay.header(PROCESS));
            capture.assertContext(retry.process(), retry.tenant(), retry.user(), Route.DEACTIVATE, "successful");
            assertEquals("ARCHIVED", retry.without("").get(path).jsonPath().getString("data.recordStatus"));
            assertFalse(capture.entries.toString().contains(key));
        }
    }

    @Test
    void managementIgnoresBusinessContextAndKeepsTheConfiguredLogFormat() {
        try (var capture = new CompletionCapture()) {
            Response response = given().header(PROCESS, "invalid-management-process").get("/q/health/live");
            assertEquals(200, response.statusCode());
            assertNull(response.header(PROCESS));
            assertTrue(capture.entries.isEmpty());
        }
        assertEquals("%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [userId=%X{userId}] [tenantId=%X{tenantId}] %s%e%n",
                ConfigProvider.getConfig().getValue("quarkus.log.console.format", String.class));
    }

    private static void error(Response response, String code, String process) {
        assertEquals(400, response.statusCode());
        assertEquals(Map.of("status", 400, "code", code), response.jsonPath().getMap("$"));
        assertEquals(process, response.header(PROCESS));
    }

    /** Supplies valid non-context input to one of the six real resource methods without creating a target Party. */
    private enum Route {
        LIST("GET", "", "list-parties"), GET("GET", "/{id}", "retrieve-party"),
        PATCH("PATCH", "/{id}", "patch-party"), ACTIVATE("POST", "/{id}/activate", "activate"),
        DEACTIVATE("POST", "/{id}/deactivate", "deactivate"), ARCHIVE("POST", "/{id}/archive", "archive");

        private final String method;
        private final String suffix;
        private final String operation;

        Route(String method, String suffix, String operation) {
            this.method = method;
            this.suffix = suffix;
            this.operation = operation;
        }

        private Response send(RequestSpecification request) {
            if (this == PATCH) {
                request.contentType(ContentType.JSON).body("{\"displayName\":\"Valid\"}");
            }
            return request.header("If-Match", "0").request(method, ROOT + suffix.replace("{id}", UUID.randomUUID().toString()));
        }
    }

    /** Defines one request's complete context and can omit a chosen header for partial-initialization checks. */
    private record Context(String tenant, String user, String process) {
        private static Context fresh() {
            return new Context(UUID.randomUUID().toString(), "user-" + UUID.randomUUID(), UUID.randomUUID().toString());
        }

        private RequestSpecification without(String omitted) {
            var request = given();
            Map.of(TENANT, tenant, USER, user, PROCESS, process).forEach((header, value) -> {
                if (!header.equals(omitted)) {
                    request.header(header, value);
                }
            });
            return request;
        }
    }

    /** Snapshots completion MDC before the sole production filter clears its owned context. */
    private record Completion(String process, String tenant, String user, String message) {
    }

    /** Captures only completion records in memory and removes its handler when the test finishes. */
    private static final class CompletionCapture extends Handler implements AutoCloseable {
        private final Logger logger = Logger.getLogger(RequestContextFilter.class.getName());
        private final List<Completion> entries = new CopyOnWriteArrayList<>();

        private CompletionCapture() {
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord logRecord) {
            if (logRecord instanceof ExtLogRecord extended) {
                String message = new SimpleFormatter().formatMessage(logRecord);
                if (message.startsWith("Request completed")) {
                    entries.add(new Completion(extended.getMdc("processId"), extended.getMdc("tenantId"), extended.getMdc("userId"), message));
                }
            }
        }

        private void assertContext(String process, String tenant, String user, Route route, String code) {
            List<Completion> matches = entries.stream().filter(entry -> process.equals(entry.process())
                    && entry.message().contains("operation=" + route.operation + " ") && entry.message().contains("code=" + code + " ")).toList();
            assertEquals(1, matches.size());
            assertEquals(tenant, matches.getFirst().tenant());
            assertEquals(user, matches.getFirst().user());
        }

        @Override
        public void flush() {
            // Records are copied synchronously into the concurrent list.
        }

        @Override
        public void close() {
            logger.removeHandler(this);
        }
    }
}
