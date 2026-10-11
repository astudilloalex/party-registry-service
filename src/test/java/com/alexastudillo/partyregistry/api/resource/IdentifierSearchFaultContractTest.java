package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.IdentifierSearchContractAssertions;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies controlled real HTTP failures, checked count capacity, sanitized
 * diagnostics and subsequent healthy attribution.
 */
@QuarkusTest
@TestProfile(IdentifierSearchFaultContractTest.FaultProfile.class)
class IdentifierSearchFaultContractTest {
    @Inject
    IdentifierSearchFaultReadPort faults;

    @Test
    void bothRoutesSanitizeUnexpectedCorruptAndSynchronousFailuresAndPreserveDependencyCodes() {
        for (String path : List.of(IdentifierSearchContractAssertions.EXACT_PATH,
                IdentifierSearchContractAssertions.PREFIX_PATH)) {
            for (IdentifierSearchFaultReadPort.Kind kind : IdentifierSearchFaultReadPort.Kind.values()) {
                if (kind == IdentifierSearchFaultReadPort.Kind.HEALTHY)
                    continue;
                String process = UUID.randomUUID().toString();
                var logs = new FailureCapture(process);
                Logger root = Logger.getLogger("");
                root.addHandler(logs);
                try {
                    faults.select(kind);
                    String field = path.equals(IdentifierSearchContractAssertions.EXACT_PATH) ? "value" : "prefix";
                    var response = given().contentType("application/json").header("Tenant-Id", UUID.randomUUID())
                            .header("Process-Id", process)
                            .header("User-Id", "fault-search-test")
                            .body("{\"identifierSchemeCode\":\"EC_NATIONAL_ID\",\"" + field
                                    + "\":\"SensitiveSelector\"}")
                            .post(path);
                    int status = kind == IdentifierSearchFaultReadPort.Kind.DEPENDENCY ? 503 : 500;
                    assertEquals(status, response.statusCode());
                    assertEquals(process, response.header("Process-Id"));
                    IdentifierSearchContractAssertions.assertErrorResponse(response.statusCode(),
                            status == 503 ? "dependency-unavailable" : "server-error", response.asString());
                    assertFalse(logs.records.isEmpty());
                    assertTrue(logs.records.stream().noneMatch(text -> text.contains("Sensitive")),
                            logs.records::toString);
                } finally {
                    faults.select(IdentifierSearchFaultReadPort.Kind.HEALTHY);
                    root.removeHandler(logs);
                    logs.close();
                }
                String healthyProcess = UUID.randomUUID().toString();
                String field = path.equals(IdentifierSearchContractAssertions.EXACT_PATH) ? "value" : "prefix";
                var healthy = given().contentType("application/json").header("Tenant-Id", UUID.randomUUID())
                        .header("Process-Id", healthyProcess)
                        .header("User-Id", "healthy-search-test")
                        .body("{\"identifierSchemeCode\":\"EC_NATIONAL_ID\",\"" + field + "\":\"17\"}").post(path);
                assertEquals(200, healthy.statusCode());
                assertEquals(healthyProcess, healthy.header("Process-Id"));
            }
        }
    }

    /**
     * Selects the test-only failure port without introducing alternatives into
     * packaged/native source sets.
     */
    public static final class FaultProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.rabbitmq.devservices.enabled", "false");
        }

        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(IdentifierSearchFaultReadPort.class);
        }
    }

    /**
     * Captures formatted attributed diagnostics, including nested throwable text,
     * for confidentiality assertions.
     */
    private static final class FailureCapture extends Handler {
        private final String process;
        private final List<String> records = new CopyOnWriteArrayList<>();

        private FailureCapture(String process) {
            this.process = process;
        }

        @Override
        public void publish(LogRecord logRecord) {
            if (logRecord instanceof ExtLogRecord extended && process.equals(extended.getMdc("processId"))) {
                records.add(new SimpleFormatter().format(extended));
            }
        }

        @Override
        public void flush() { //
        }

        @Override
        public void close() {
            //
        }
    }
}
