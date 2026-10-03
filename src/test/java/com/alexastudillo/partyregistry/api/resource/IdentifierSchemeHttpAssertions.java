package com.alexastudillo.partyregistry.api.resource;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shares exact public-envelope assertions and valid isolated HTTP inputs across scheme delivery tests. */
final class IdentifierSchemeHttpAssertions {
    static final String ROOT = "/v1/identifier-schemes";
    static final Set<String> REQUIRED = Set.of("id", "code", "issuingCountryCode", "category", "applicableSubjectType",
            "name", "normalizerKey", "validatorKey", "requiresExpiration", "status", "version", "createdAt", "updatedAt");

    private IdentifierSchemeHttpAssertions() {
    }

    static String body(String code) {
        return """
                {"code":"%s","issuingCountryCode":"ZZ","category":"OTHER","applicableSubjectType":"BOTH",
                 "name":"Exact Mixed Name","normalizerKey":"TRIM_UPPERCASE_V1","validatorKey":"ALPHANUMERIC_V1"}
                """.formatted(code);
    }

    static String uniqueCode() {
        return "Http-" + UUID.randomUUID();
    }

    static Map<String, Object> success(Response response, int status, Context context) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
        assertEquals(status, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(context.process(), response.header("Process-Id"));
        Map<String, Object> data = response.jsonPath().getMap("data");
        safe(data);
        return data;
    }

    static void safe(Map<String, Object> data) {
        var fields = new HashSet<>(REQUIRED);
        for (String optional : Set.of("description", "minimumLength", "maximumLength")) {
            if (data.containsKey(optional)) {
                fields.add(optional);
            }
        }
        assertEquals(fields, data.keySet());
        assertInstanceOf(Boolean.class, data.get("requiresExpiration"));
        UUID.fromString((String) data.get("id"));
        Instant.parse((String) data.get("createdAt"));
        Instant.parse((String) data.get("updatedAt"));
        assertTrue(((Number) data.get("version")).longValue() >= 0);
    }

    static void error(Response response, int status, String code, Context context) {
        error(response, status, code, context.process());
    }

    static void error(Response response, int status, String code, String echo) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(echo, response.header("Process-Id"));
    }

    static void assertError(Response response, int status, String code, Context context) {
        error(response, status, code, context);
    }

    static void assertError(Response response, int status, String code, String echo) {
        error(response, status, code, echo);
    }

    static Map<String, Object> create(Context context, String code) {
        return success(context.request().contentType("application/json").header("Idempotency-Key", UUID.randomUUID().toString())
                .body(body(code)).post(ROOT), 201, context);
    }

    /** Supplies one trusted context and can remove a header to test early rejection and partial initialization. */
    record Context(String tenant, String user, String process) {
        static Context fresh() {
            return new Context(UUID.randomUUID().toString(), "http-operator", UUID.randomUUID().toString());
        }

        RequestSpecification request() {
            return without("");
        }

        RequestSpecification without(String omitted) {
            var request = given();
            Map.of("Tenant-Id", tenant, "User-Id", user, "Process-Id", process).forEach((key, value) -> {
                if (!key.equals(omitted)) {
                    request.header(key, value);
                }
            });
            return request;
        }
    }
}
