package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemeCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemePatchRequest;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.ws.rs.core.HttpHeaders;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies canonical selectors, reused operation-header rules, and ordered command construction. */
class IdentifierSchemeRequestSupportTest {
    private static final String ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "actor", UUID.randomUUID());
    private static final IdentifierSchemeCreateRequest CREATE = new IdentifierSchemeCreateRequest(
            "Exact", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, "name", null,
            "historical", "unknown", null, null, false);

    @Test
    void parsesCanonicalIdsAndExactUnicodeCodes() {
        withSupport(support -> {
            assertEquals(UUID.fromString(ID), support.schemeId(ID).value());
            for (String invalid : new String[]{null, "", "1-1-1-1-1", ID.toUpperCase(java.util.Locale.ROOT),
                    " " + ID, ID + " ", "not-uuid"}) {
                code("identifier-scheme-id-invalid", () -> support.schemeId(invalid));
            }
            var parameters = new IdentifierSchemeRequestParameters();
            assertEquals(" Mixed ", parameters.code(" Mixed "));
            assertEquals("😀".repeat(64), parameters.code("😀".repeat(64)));
            code("identifier-scheme-code-too-long", () -> parameters.code("😀".repeat(65)));
            code("identifier-scheme-code-required", () -> parameters.code(" "));
        });
    }

    @Test
    void createRequiresAnExactBoundedKeyAfterBodyValidation() {
        withSupport(support -> {
            code("request-body-required", () -> support.createCommand(METADATA, null, headers(Map.of())));
            code("idempotency-key-required", () -> support.createCommand(METADATA, CREATE, headers(Map.of())));
            for (var entry : Map.of("idempotency-key-duplicated", List.of("a", "b"),
                    "idempotency-key-blank", List.of(" "), "idempotency-key-too-long", List.of("😀".repeat(129))).entrySet()) {
                code(entry.getKey(), () -> support.createCommand(METADATA, CREATE,
                        headers(Map.of("Idempotency-Key", entry.getValue()))));
            }
            var command = support.createCommand(METADATA, CREATE, headers(Map.of("Idempotency-Key", List.of(" Exact Key "))));
            assertSame(METADATA, command.requestMetadata());
            assertEquals(" Exact Key ", command.idempotencyKey());
            assertEquals("Exact", command.effectiveRequest().code());
        });
    }

    @Test
    void patchValidatesBodyBeforeIdAndVersion() throws Exception {
        var mapper = new ObjectMapper();
        IdentifierSchemePatchRequest empty = mapper.readValue("{}", IdentifierSchemePatchRequest.class);
        IdentifierSchemePatchRequest patch = mapper.readValue("{\"name\":\" Exact \"}", IdentifierSchemePatchRequest.class);
        withSupport(support -> {
            code("request-body-required", () -> support.patchCommand(METADATA, null, "bad", headers(Map.of())));
            code("patch-property-required", () -> support.patchCommand(METADATA, empty, "bad", headers(Map.of())));
            code("identifier-scheme-id-invalid", () -> support.patchCommand(METADATA, patch, "bad", headers(Map.of())));
            code("if-match-required", () -> support.patchCommand(METADATA, patch, ID, headers(Map.of())));
            var command = support.patchCommand(METADATA, patch, ID, headers(Map.of("If-Match", List.of("0"))));
            assertEquals(" Exact ", command.changes().name().value());
            assertEquals(0, command.expectedVersion().value());
        });
    }

    @Test
    void lifecycleValidatesOptionalKeyBeforeIdBeforeVersion() {
        withSupport(support -> {
            for (var entry : Map.of("idempotency-key-duplicated", List.of("a", "b"),
                    "idempotency-key-blank", List.of(""), "idempotency-key-too-long", List.of("😀".repeat(129))).entrySet()) {
                code(entry.getKey(), () -> support.lifecycleCommand(METADATA, "bad", IdentifierSchemeLifecycleAction.ACTIVATE,
                        headers(Map.of("Idempotency-Key", entry.getValue()))));
            }
            code("identifier-scheme-id-invalid", () -> support.lifecycleCommand(METADATA, "bad",
                    IdentifierSchemeLifecycleAction.RETIRE, headers(Map.of())));
            code("if-match-required", () -> support.lifecycleCommand(METADATA, ID,
                    IdentifierSchemeLifecycleAction.RETIRE, headers(Map.of())));
            for (var action : IdentifierSchemeLifecycleAction.values()) {
                var unkeyed = support.lifecycleCommand(METADATA, ID, action, headers(Map.of("If-Match", List.of("42"))));
                assertTrue(unkeyed.idempotencyKey().isEmpty());
                assertEquals(action, unkeyed.action());
                var keyed = support.lifecycleCommand(METADATA, ID, action, headers(Map.of(
                        "If-Match", List.of("42"), "Idempotency-Key", List.of("😀".repeat(128)))));
                assertEquals("😀".repeat(128), keyed.idempotencyKey().orElseThrow());
            }
        });
    }

    @Test
    void reusesEveryExpectedVersionSyntaxAndCardinalityCode() {
        withSupport(support -> {
            code("if-match-required", () -> support.expectedVersion(headers(Map.of())));
            code("if-match-duplicated", () -> support.expectedVersion(headers(Map.of("If-Match", List.of("0", "0")))));
            for (String invalid : new String[]{"", " ", "01", "+1", "-1", "1.0", "1e2", "*", "\"1\"", " 1", "1 ", "１"}) {
                code("if-match-invalid", () -> support.expectedVersion(headers(Map.of("If-Match", List.of(invalid)))));
            }
            for (String overflow : new String[]{"9223372036854775808", "9".repeat(300)}) {
                code("if-match-out-of-range", () -> support.expectedVersion(headers(Map.of("If-Match", List.of(overflow)))));
            }
            assertEquals(Long.MAX_VALUE, support.expectedVersion(headers(Map.of("If-Match", List.of("9223372036854775807")))).value());
        });
    }

    private static void withSupport(java.util.function.Consumer<IdentifierSchemeRequestSupport> test) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            test.accept(new IdentifierSchemeRequestSupport(new ApiRequestSupport(factory.getValidator()),
                    new IdentifierSchemeRequestParameters()));
        }
    }

    private static HttpHeaders headers(Map<String, List<String>> values) {
        return HttpHeaders.class.cast(java.lang.reflect.Proxy.newProxyInstance(HttpHeaders.class.getClassLoader(),
                new Class<?>[]{HttpHeaders.class}, (_, method, arguments) -> {
                    if (method.getName().equals("getRequestHeader")) {
                        return values.get(arguments[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                }));
    }

    private static void code(String expected, Runnable action) {
        var failure = assertThrows(ApiResponseException.class, action::run);
        assertEquals(expected, failure.getResponseCode().getCode());
        assertEquals(400, failure.getResponseCode().getStatus());
    }
}
