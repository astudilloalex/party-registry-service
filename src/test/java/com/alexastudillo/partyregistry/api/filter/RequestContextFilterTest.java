package com.alexastudillo.partyregistry.api.filter;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.observability.PartyHttpObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises header diagnostics and partial trusted-context cleanup without an
 * HTTP server.
 */
class RequestContextFilterTest {

    private static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void distinguishesMissingAndDuplicateValuesForEveryHeader() throws IOException {
        for (String header : List.of("Process-Id", "Tenant-Id", "User-Id")) {
            String prefix = header.toLowerCase(Locale.ROOT);
            var missing = validHeaders();
            missing.remove(header);
            assertRejection(missing, header, prefix + "-required");

            var duplicate = validHeaders();
            duplicate.add(header, duplicate.getFirst(header));
            assertRejection(duplicate, header, prefix + "-duplicated");

            var empty = validHeaders();
            empty.put(header, List.of());
            assertRejection(empty, header, prefix + "-required");

            var nullValue = validHeaders();
            List<String> values = new ArrayList<>();
            values.add(null);
            nullValue.put(header, values);
            assertRejection(nullValue, header, prefix + "-required");
        }
    }

    @Test
    void rejectsMalformedBlankNoncanonicalAndUnresolvedUuidsWithoutParserCauses() throws IOException {
        for (String header : List.of("Process-Id", "Tenant-Id")) {
            for (String value : List.of("", " ", "SensitiveInvalidUuid", "{{SensitiveUuid}}", "1-1-1-1-1",
                    PROCESS_ID.toUpperCase(Locale.ROOT), " " + PROCESS_ID, PROCESS_ID + " ")) {
                var headers = validHeaders();
                headers.putSingle(header, value);
                assertRejection(headers, header, header.toLowerCase(Locale.ROOT) + "-invalid");
            }
        }
    }

    @Test
    void distinguishesBlankLongAndUnsafeUsers() throws IOException {
        for (String value : List.of("", "   ")) {
            var headers = validHeaders();
            headers.putSingle("User-Id", value);
            assertRejection(headers, "User-Id", "user-id-blank");
        }
        for (String value : List.of("Sensitive".repeat(20), "\uD83D\uDE00".repeat(129))) {
            var headers = validHeaders();
            headers.putSingle("User-Id", value);
            assertRejection(headers, "User-Id", "user-id-too-long");
        }
        for (String value : List.of("Sensitive\u0001User", "Sensitive\nUser", "Sensitive\u007fUser",
                "Sensitive\u0085User")) {
            var headers = validHeaders();
            headers.putSingle("User-Id", value);
            assertRejection(headers, "User-Id", "user-id-unsafe");
        }
    }

    @Test
    void selectsProcessBeforeTenantBeforeUserWhenMultipleHeadersFail() throws IOException {
        var headers = new MultivaluedHashMap<String, String>();
        assertRejection(headers, "Process-Id", "process-id-required");
        headers.putSingle("Process-Id", PROCESS_ID);
        assertRejection(headers, "Tenant-Id", "tenant-id-required");
        headers.putSingle("Tenant-Id", TENANT_ID);
        assertRejection(headers, "User-Id", "user-id-required");
    }

    @Test
    void acceptsUnicodeCodePointBoundaryAndClearsOnlyOwnedContext() throws IOException {
        var headers = validHeaders();
        String user = "\uD83D\uDE00".repeat(128);
        headers.putSingle("User-Id", user);
        RequestMetadataContext metadata = new RequestMetadataContext();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            RequestContextFilter filter = new RequestContextFilter(metadata, new PartyHttpObservability(registry));
            MDC.put("traceId", "retained");
            var request = request(headers);
            filter.filter(request);
            assertEquals(user, metadata.metadata().userId());
            assertEquals(PROCESS_ID, MDC.get("processId"));
            assertEquals(TENANT_ID, MDC.get("tenantId"));
            assertEquals(user, MDC.get("userId"));
            var responseHeaders = new MultivaluedHashMap<String, Object>();
            filter.filter(request, response(responseHeaders));
            assertEquals(PROCESS_ID, responseHeaders.getFirst("Process-Id"));
            assertNull(MDC.get("processId"));
            assertNull(MDC.get("tenantId"));
            assertNull(MDC.get("userId"));
            assertEquals("retained", MDC.get("traceId"));
        } finally {
            registry.close();
        }
    }

    private static void assertRejection(
            MultivaluedMap<String, String> headers, String header, String expectedCode) throws IOException {
        RequestMetadataContext metadata = new RequestMetadataContext();
        List<CapturedLog> logs = new ArrayList<>();
        Logger logger = Logger.getLogger(RequestContextFilter.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                logs.add(new CapturedLog(new SimpleFormatter().formatMessage(logRecord),
                        MDC.get("processId"), MDC.get("tenantId"), MDC.get("userId"), logRecord.getThrown()));
            }

            @Override
            public void flush() {
                //
            }

            @Override
            public void close() {
                //
            }
        };
        logger.addHandler(handler);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            RequestContextFilter filter = new RequestContextFilter(metadata, new PartyHttpObservability(registry));
            MDC.put("processId", "SensitiveStaleProcess");
            MDC.put("tenantId", "SensitiveStaleTenant");
            MDC.put("userId", "SensitiveStaleUser");
            MDC.put("traceId", "retained");
            var request = request(headers);
            ApiResponseException failure = assertThrows(ApiResponseException.class, () -> filter.filter(request));
            assertEquals(expectedCode, failure.getResponseCode().getCode());
            assertEquals(400, failure.getResponseCode().getStatus());
            assertNull(failure.getCause());
            assertThrows(IllegalStateException.class, metadata::metadata);

            String expectedProcess = header.equals("Process-Id") ? null : PROCESS_ID;
            String expectedTenant = header.equals("User-Id") ? TENANT_ID : null;
            assertEquals(expectedProcess, metadata.acceptedProcessId());
            assertEquals(expectedProcess, MDC.get("processId"));
            assertEquals(expectedTenant, MDC.get("tenantId"));
            assertNull(MDC.get("userId"));
            var responseHeaders = new MultivaluedHashMap<String, Object>();
            filter.filter(request, response(responseHeaders));
            assertEquals(expectedProcess, responseHeaders.getFirst("Process-Id"));
            assertNull(MDC.get("processId"));
            assertNull(MDC.get("tenantId"));
            assertNull(MDC.get("userId"));
            assertEquals("retained", MDC.get("traceId"));

            assertTrue(logs.stream().anyMatch(log -> log.message().contains(
                    "Request rejected status=400 code=" + expectedCode
                            + " source=request-context header=" + header + " rule=" + expectedCode)),
                    logs::toString);
            for (CapturedLog log : logs) {
                assertFalse(log.message().contains("Sensitive"));
                assertNull(log.failure());
                assertEquals(expectedProcess, log.processId());
                assertEquals(expectedTenant, log.tenantId());
                assertNull(log.userId());
            }
        } finally {
            registry.close();
            logger.removeHandler(handler);
            handler.close();
        }
    }

    private static MultivaluedMap<String, String> validHeaders() {
        var headers = new MultivaluedHashMap<String, String>();
        headers.putSingle("Process-Id", PROCESS_ID);
        headers.putSingle("Tenant-Id", TENANT_ID);
        headers.putSingle("User-Id", "header-test");
        return headers;
    }

    private static ContainerRequestContext request(MultivaluedMap<String, String> headers) {
        UriInfo uri = (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(), new Class<?>[] { UriInfo.class },
                (_, method, _) -> switch (method.getName()) {
                    case "getPath" -> "v1/natural-person";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (ContainerRequestContext) Proxy.newProxyInstance(ContainerRequestContext.class.getClassLoader(),
                new Class<?>[] { ContainerRequestContext.class }, (_, method, _) -> switch (method.getName()) {
                    case "getMethod" -> "POST";
                    case "getHeaders" -> headers;
                    case "getUriInfo" -> uri;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static ContainerResponseContext response(MultivaluedMap<String, Object> headers) {
        return (ContainerResponseContext) Proxy.newProxyInstance(ContainerResponseContext.class.getClassLoader(),
                new Class<?>[] { ContainerResponseContext.class }, (_, method, _) -> switch (method.getName()) {
                    case "getHeaders" -> headers;
                    case "getStatus" -> 400;
                    case "getEntity" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Captures MDC before response completion clears the validated request context.
     */
    private record CapturedLog(String message, Object processId, Object tenantId, Object userId, Throwable failure) {
    }
}
