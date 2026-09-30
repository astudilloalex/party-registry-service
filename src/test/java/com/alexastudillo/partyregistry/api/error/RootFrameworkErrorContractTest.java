package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPageSlice;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyMutationPort;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Exercises real root resource/framework error paths with controlled test-only port failures and confidential input canaries. */
@QuarkusTest
@TestProfile(RootFrameworkErrorContractTest.FailureProfile.class)
class RootFrameworkErrorContractTest {

    private static final String ROOT = "/v1/parties";
    private static final String ID = UUID.randomUUID().toString();
    private static final String TENANT = UUID.randomUUID().toString();
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final String CANARY = "private-input-canary";
    private static final List<String> ACTIONS = List.of("activate", "deactivate", "archive");

    @Inject
    MeterRegistry meters;

    @Test
    void allSixRootMethodsSanitizeUnexpectedFailuresThroughTheSharedGlobalMapper() {
        error(request().queryParam("displayNameContains", CANARY).get(ROOT), 500, "server-error");
        error(request().get(ROOT + "/" + ID), 500, "server-error");
        error(request().header("If-Match", "0").contentType(ContentType.JSON).body(Map.of("displayName", CANARY))
                .patch(ROOT + "/" + ID), 500, "server-error");
        for (String action : ACTIONS) {
            error(request().header("If-Match", "0").header("Idempotency-Key", CANARY).post(ROOT + "/" + ID + "/" + action), 500, "server-error");
        }
    }

    @Test
    void frameworkFailuresKeepExactStatusCodeEnvelopeAndAcceptedProcessEcho() {
        error(request().post(ROOT), 405, "method-not-allowed");
        error(request().delete(ROOT + "/" + ID), 405, "method-not-allowed");
        for (String action : ACTIONS) {
            error(request().put(ROOT + "/" + ID + "/" + action), 405, "method-not-allowed");
        }
        error(request().get(ROOT + "/" + ID + "/unknown"), 404, "not-found");
        error(request().header("If-Match", "0").contentType(ContentType.TEXT).body(CANARY).patch(ROOT + "/" + ID), 415, "unsupported-media-type");
        error(request().header("If-Match", "0").contentType(ContentType.JSON).body("{\"displayName\":\"" + CANARY)
                .patch(ROOT + "/" + ID), 400, "bad-request");
        error(request().queryParam("limit", CANARY).get(ROOT), 400, "bad-request");
        error(request().queryParam("cursor", CANARY).get(ROOT), 400, "bad-request");
    }

    @Test
    void rejectedPayloadsAndFiltersNeverBecomeOperationalMessagesOrMetricDimensions() {
        List<String> captured = new CopyOnWriteArrayList<>();
        Logger logger = Logger.getLogger("");
        Handler handler = new CapturingHandler(captured);
        logger.addHandler(handler);
        try {
            error(request().contentType(ContentType.JSON).body("{\"displayName\":\"Valid\",\"" + CANARY + "\":true}")
                    .patch(ROOT + "/" + ID), 400, "bad-request");
            error(request().queryParam("cursor", CANARY).get(ROOT), 400, "bad-request");
            error(request().queryParam("displayNameStartsWith", CANARY).get(ROOT), 500, "server-error");
            error(request().header("If-Match", "0").header("Idempotency-Key", CANARY).post(ROOT + "/" + ID + "/archive"), 500, "server-error");
            assertFalse(captured.toString().contains(CANARY));
            meters.getMeters().stream().filter(meter -> meter.getId().getName().startsWith("party.registry"))
                    .forEach(meter -> {
                        String dimensions = meter.getId().getTags().toString();
                        assertFalse(dimensions.contains(CANARY));
                        assertFalse(dimensions.contains(ID));
                        assertFalse(dimensions.contains(TENANT));
                        assertFalse(dimensions.contains(PROCESS));
                    });
        } finally {
            logger.removeHandler(handler);
            handler.close();
        }
    }

    private static RequestSpecification request() {
        return given().header("Tenant-Id", TENANT).header("Process-Id", PROCESS).header("User-Id", "root-error-contract");
    }

    private static void error(Response response, int status, String code) {
        assertEquals(status, response.statusCode());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(PROCESS, response.header("Process-Id"));
        assertFalse(response.asString().contains("private-database-detail"));
        assertFalse(response.asString().contains(CANARY));
    }

    /** Fails only this profile's I/O ports while leaving actual resources, workflows, filters, and global errors active. */
    @Alternative
    @ApplicationScoped
    public static class FailingRootPorts implements PartyQueryPort, PartyMutationPort {
        @Override
        public Uni<Optional<PartyDetailsResult>> findDetails(TenantId tenant, PartyId id) {
            return Uni.createFrom().failure(new IllegalStateException("private-database-detail"));
        }

        @Override
        public Uni<PartyPageSlice> findPage(TenantId tenant, PartySearchCriteria criteria, Optional<PartyPageBoundary> boundary) {
            return Uni.createFrom().failure(new IllegalStateException("private-database-detail"));
        }

        @Override
        public Uni<PartyMutationOutcome> execute(RequestMetadata metadata, Function<PartyMutationContext, Uni<PartyMutationOutcome>> work) {
            return Uni.createFrom().failure(new IllegalStateException("private-database-detail"));
        }
    }

    /** Selects controlled port failures without enabling any production failure endpoint. */
    public static final class FailureProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FailingRootPorts.class);
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.rabbitmq.devservices.enabled", "false");
        }
    }

    /** Captures already-formatted operational messages without modifying the request pipeline. */
    private static final class CapturingHandler extends Handler {
        private final List<String> captured;

        private CapturingHandler(List<String> captured) {
            this.captured = captured;
        }

        @Override
        public void publish(LogRecord logRecord) {
            captured.add(new SimpleFormatter().formatMessage(logRecord));
        }

        @Override
        public void flush() {
            // Messages are captured synchronously.
        }

        @Override
        public void close() {
            // The test removes the handler from its logger.
        }
    }
}
