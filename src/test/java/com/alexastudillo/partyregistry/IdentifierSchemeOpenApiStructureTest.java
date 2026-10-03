package com.alexastudillo.partyregistry;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the scheme-only static contract without changing other resources' pagination or headers. */
class IdentifierSchemeOpenApiStructureTest {
    private static final String ROOT = "/v1/identifier-schemes";
    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    private static final Set<String> PATCH = Set.of("name", "description", "normalizerKey", "validatorKey",
            "minimumLength", "maximumLength", "requiresExpiration");
    private static OpenAPI api;

    @BeforeAll
    static void load() {
        var parsed = new OpenAPIV3Parser().readLocation("docs/contracts/party-registry.openapi.yaml", null, null);
        assertNotNull(parsed.getOpenAPI(), () -> String.valueOf(parsed.getMessages()));
        assertTrue(parsed.getMessages().isEmpty(), () -> String.valueOf(parsed.getMessages()));
        api = parsed.getOpenAPI();
    }

    @Test
    void declaresExactlyEightOperationsWithPathOnlySelectorsAndTrustedContext() {
        Map<String, Set<PathItem.HttpMethod>> paths = Map.of(ROOT, Set.of(PathItem.HttpMethod.POST, PathItem.HttpMethod.GET),
                ROOT + "/{schemeId}", Set.of(PathItem.HttpMethod.GET, PathItem.HttpMethod.PATCH),
                ROOT + "/by-code/{code}", Set.of(PathItem.HttpMethod.GET),
                ROOT + "/{schemeId}/activate", Set.of(PathItem.HttpMethod.POST),
                ROOT + "/{schemeId}/deprecate", Set.of(PathItem.HttpMethod.POST),
                ROOT + "/{schemeId}/retire", Set.of(PathItem.HttpMethod.POST));
        assertEquals(paths.keySet(), api.getPaths().keySet().stream().filter(p -> p.startsWith(ROOT)).collect(Collectors.toSet()));
        paths.forEach((path, methods) -> {
            PathItem item = api.getPaths().get(path);
            assertEquals(methods, item.readOperationsMap().keySet());
            item.readOperationsMap().forEach((method, operation) -> {
                var parameters = parameters(item, operation);
                for (String header : List.of("Process-Id", "Tenant-Id", "User-Id")) {
                    var matches = parameters.stream().filter(p -> header.equals(p.getName())).toList();
                    assertEquals(1, matches.size(), operation.getOperationId() + header);
                    assertEquals("header", matches.getFirst().getIn());
                    assertTrue(matches.getFirst().getRequired());
                }
                for (String selector : List.of("schemeId", "code")) {
                    var matches = parameters.stream().filter(p -> selector.equals(p.getName())).toList();
                    assertEquals(path.contains("{" + selector + "}") ? 1 : 0, matches.size());
                    matches.forEach(p -> { assertEquals("path", p.getIn()); assertTrue(p.getRequired()); });
                }
                if (method == PathItem.HttpMethod.GET || path.endsWith("activate") || path.endsWith("deprecate") || path.endsWith("retire")) {
                    assertNull(operation.getRequestBody());
                } else {
                    assertTrue(operation.getRequestBody().getRequired());
                }
                String description = operation.getDescription();
                assertNotNull(description, operation.getOperationId());
                for (String rule : List.of("global", "Process-Id", "Tenant-Id", "User-Id")) {
                    assertTrue(description.contains(rule), operation.getOperationId() + rule);
                }
            });
        });
        assertEquals(UUID_PATTERN, api.getComponents().getParameters().get("IdentifierSchemeIdPath").getSchema().getPattern());
    }

    @Test
    void closesRequestsAndPreservesPresenceExactTextDefaultsAndSemanticBounds() {
        Schema<?> create = schema("IdentifierSchemeCreateRequest");
        Schema<?> patch = schema("IdentifierSchemeUpdateRequest");
        assertEquals(Set.of("code", "issuingCountryCode", "category", "applicableSubjectType", "name", "normalizerKey", "validatorKey"), Set.copyOf(create.getRequired()));
        assertEquals(PATCH, patch.getProperties().keySet());
        assertEquals(1, patch.getMinProperties());
        assertTrue(patch.getRequired() == null || patch.getRequired().isEmpty());
        assertEquals(Boolean.FALSE, property(create, "requiresExpiration").getDefault());
        assertNull(property(patch, "requiresExpiration").getDefault());
        verifyRequestProperties(create, patch);
        verifyTextConstraints(create);
        assertEquals(List.of("NATIONAL_ID", "TAX_ID", "PASSPORT", "RESIDENCE_PERMIT", "LEGAL_REGISTRATION_NUMBER", "OTHER"), schema("IdentifierCategory").getEnum());
        assertEquals(List.of("NATURAL_PERSON", "LEGAL_ENTITY", "BOTH"), schema("IdentifierSubjectType").getEnum());
    }

    private static void verifyRequestProperties(Schema<?> create, Schema<?> patch) {
        for (Schema<?> request : List.of(create, patch)) {
            assertEquals(Boolean.FALSE, request.getAdditionalProperties());
            assertTrue(request.getDescription().contains("Unicode code points"));
            assertTrue(request.getDescription().contains("duplicate"));
            for (String optional : List.of("description", "minimumLength", "maximumLength")) {
                assertEquals(Boolean.TRUE, property(request, optional).getNullable());
            }
            for (String required : List.of("name", "normalizerKey", "validatorKey", "requiresExpiration")) {
                assertNotEquals(Boolean.TRUE, property(request, required).getNullable());
            }
            assertEquals("boolean", property(request, "requiresExpiration").getType());
            for (String bound : List.of("minimumLength", "maximumLength")) {
                Schema<?> numeric = property(request, bound);
                assertEquals("integer", numeric.getType());
                assertEquals(BigDecimal.ONE, numeric.getMinimum());
                assertEquals(new BigDecimal("32767"), numeric.getMaximum());
            }
        }
    }

    private static void verifyTextConstraints(Schema<?> create) {
        for (var entry : Map.of("code", 64, "name", 150, "normalizerKey", 64, "validatorKey", 64, "description", 500).entrySet()) {
            var text = property(create, entry.getKey());
            assertEquals(entry.getValue(), text.getMaxLength());
            if (!entry.getKey().equals("description")) {
                Pattern pattern = Pattern.compile(text.getPattern());
                assertFalse(pattern.matcher(" \n\u2003").find());
                assertTrue(pattern.matcher(" \nA\n ").find());
                assertTrue(pattern.matcher("\u00a0").find());
            }
        }
        assertEquals("^[A-Z]{2}$", property(create, "issuingCountryCode").getPattern());
    }

    @Test
    void requiresSafeOutputAndSchemeOnlyCountMetadata() {
        Schema<?> response = schema("IdentifierSchemeResponse");
        Set<String> required = Set.of("id", "code", "issuingCountryCode", "category", "applicableSubjectType", "name",
                "normalizerKey", "validatorKey", "requiresExpiration", "status", "version", "createdAt", "updatedAt");
        assertEquals(required, Set.copyOf(response.getRequired()));
        assertEquals(16, response.getProperties().size());
        assertEquals(Boolean.FALSE, response.getAdditionalProperties());
        assertEquals(BigDecimal.ZERO, property(response, "version").getMinimum());
        assertEquals(new BigDecimal("9223372036854775807"), property(response, "version").getMaximum());
        for (String optional : List.of("description", "minimumLength", "maximumLength")) {
            assertTrue(property(response, optional).getDescription().contains("omitted"));
        }
        Schema<?> page = schema("IdentifierSchemeCollectionApiResponse");
        assertEquals(Set.of("status", "code", "data", "numberOfElements"), Set.copyOf(page.getRequired()));
        assertEquals(Boolean.FALSE, page.getAdditionalProperties());
        assertEquals(200, property(page, "data").getMaxItems());
        for (String metadata : List.of("nextCursor", "prevCursor", "totalElements", "totalPages", "numberOfElements")) {
            assertNotEquals(Boolean.TRUE, property(page, metadata).getNullable());
        }
        assertEquals(8, schema("PartyCollectionApiResponse").getRequired().size());
        assertEquals(Boolean.TRUE, property(schema("PartyCollectionApiResponse"), "nextCursor").getNullable());
    }

    @Test
    void definesSchemeScopedHeadersNavigationAndMaintenanceRules() {
        var components = api.getComponents().getParameters();
        for (String name : List.of("IdentifierSchemeCreateKey", "IdentifierSchemeLifecycleKey")) {
            Parameter key = components.get(name);
            assertNotNull(key, name);
            assertEquals(128, schema("IdentifierSchemeReplayKey").getMaxLength());
            assertEquals(name.endsWith("CreateKey"), key.getRequired());
            for (String rule : List.of("Unicode code points", "without trimming", "tenant", "restart")) {
                assertTrue(key.getDescription().contains(rule), name + rule);
            }
        }
        Parameter version = components.get("IdentifierSchemeIfMatch");
        assertTrue(version.getRequired());
        assertEquals("^(0|[1-9][0-9]*)$", version.getSchema().getPattern());
        assertTrue(version.getDescription().contains("9223372036854775807"));
        assertTrue(version.getDescription().contains("412 expected-version-mismatch"));
        Operation list = api.getPaths().get(ROOT).getGet();
        for (String rule : List.of("AND", "BOTH", "createdAt ASC", "id ASC", "no frozen snapshot", "unknown", "repeated")) {
            assertTrue(list.getDescription().contains(rule), rule);
        }
        var query = parameters(api.getPaths().get(ROOT), list).stream().filter(p -> "query".equals(p.getIn()))
                .collect(Collectors.toMap(Parameter::getName, p -> p));
        assertEquals(Set.of("issuingCountryCode", "category", "applicableSubjectType", "status", "cursor", "limit"), query.keySet());
        assertEquals(50, query.get("limit").getSchema().getDefault());
        assertEquals(new BigDecimal("200"), query.get("limit").getSchema().getMaximum());
        assertEquals(256, query.get("cursor").getSchema().getMaxLength());
        String patch = api.getPaths().get(ROOT + "/{schemeId}").getPatch().getDescription();
        for (String rule : List.of("DRAFT", "ACTIVE", "DEPRECATED", "RETIRED", "obsolete", "identical", "body", "If-Match")) {
            assertTrue(patch.contains(rule), rule);
        }
        for (String action : List.of("activate", "deprecate", "retire")) {
            String description = api.getPaths().get(ROOT + "/{schemeId}/" + action).getPost().getDescription();
            for (String rule : List.of("no body", "Idempotency-Key", "schemeId", "If-Match", "replay", "version")) {
                assertTrue(description.contains(rule), action + rule);
            }
        }
    }

    private static List<Parameter> parameters(PathItem path, Operation operation) {
        var values = new ArrayList<Parameter>();
        if (path.getParameters() != null) values.addAll(path.getParameters());
        if (operation.getParameters() != null) values.addAll(operation.getParameters());
        return values.stream().map(p -> p.get$ref() == null ? p : api.getComponents().getParameters()
                .get(p.get$ref().substring("#/components/parameters/".length()))).toList();
    }

    private static Schema<?> schema(String name) {
        var value = api.getComponents().getSchemas().get(name);
        assertNotNull(value, name);
        return value;
    }

    private static Schema<?> property(Schema<?> owner, String name) {
        var value = owner.getProperties().get(name);
        assertNotNull(value, name);
        return value.get$ref() == null ? value : schema(value.get$ref().substring("#/components/schemas/".length()));
    }
}
