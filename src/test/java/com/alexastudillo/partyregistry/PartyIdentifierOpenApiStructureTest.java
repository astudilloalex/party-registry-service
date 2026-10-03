package com.alexastudillo.partyregistry;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the independent identifier wire contract while preserving unrelated Party contracts. */
class PartyIdentifierOpenApiStructureTest {

    private static final String ROOT = "/v1/parties/{partyId}/identifiers";
    private static final String ITEM = ROOT + "/{identifierId}";
    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    private final OpenAPI api = load();

    @Test
    void declaresExactlyEightOperationsWithTrustedContextAndCanonicalPaths() {
        Map<String, Set<PathItem.HttpMethod>> expected = Map.of(
                ROOT, Set.of(PathItem.HttpMethod.POST, PathItem.HttpMethod.GET),
                ITEM, Set.of(PathItem.HttpMethod.GET, PathItem.HttpMethod.PATCH),
                ITEM + "/verify", Set.of(PathItem.HttpMethod.POST),
                ITEM + "/reject", Set.of(PathItem.HttpMethod.POST),
                ITEM + "/revoke", Set.of(PathItem.HttpMethod.POST),
                ITEM + "/set-primary", Set.of(PathItem.HttpMethod.POST));
        assertEquals(expected.keySet(), api.getPaths().keySet().stream()
                .filter(path -> path.startsWith(ROOT)).collect(Collectors.toSet()));
        expected.forEach((path, methods) -> {
            PathItem item = api.getPaths().get(path);
            assertEquals(methods, item.readOperationsMap().keySet());
            item.readOperations().forEach(operation -> {
                for (String header : List.of("Process-Id", "Tenant-Id", "User-Id")) {
                    Parameter parameter = parameter(item, operation, header);
                    assertEquals("header", parameter.getIn());
                    assertTrue(parameter.getRequired());
                }
                assertEquals(UUID_PATTERN, parameter(item, operation, "partyId").getSchema().getPattern());
                if (!path.equals(ROOT)) {
                    assertEquals(UUID_PATTERN, parameter(item, operation, "identifierId").getSchema().getPattern());
                }
                requireRules(operation, "Process-Id", "Tenant-Id", "User-Id", "party-not-found", "canonical");
            });
        });
    }

    @Test
    void usesIdentifierSpecificVersionAndDurableReplayHeaders() {
        Parameter version = api.getComponents().getParameters().get("PartyIdentifierIfMatch");
        assertNotNull(version);
        assertEquals("If-Match", version.getName());
        assertTrue(version.getRequired());
        assertEquals("^(0|[1-9][0-9]*)$", version.getSchema().getPattern());
        for (String rule : List.of("identifier", "9223372036854775807", "412 expected-version-mismatch", "identifier-version-exhausted")) {
            assertTrue(version.getDescription().contains(rule), rule);
        }
        for (String name : List.of("PartyIdentifierCreateKey", "PartyIdentifierActionKey")) {
            Parameter key = api.getComponents().getParameters().get(name);
            assertNotNull(key, name);
            assertEquals(name.endsWith("CreateKey"), key.getRequired());
            assertEquals(128, key.getSchema().getMaxLength());
            for (String rule : List.of("Unicode code points", "without trimming", "tenant", "restart", "User-Id", "Process-Id")) {
                assertTrue(key.getDescription().contains(rule), name + rule);
            }
        }
        PathItem collection = api.getPaths().get(ROOT);
        assertEquals("Idempotency-Key", parameter(collection, collection.getPost(), "Idempotency-Key").getName());
        PathItem detail = api.getPaths().get(ITEM);
        assertEquals(version, parameter(detail, detail.getPatch(), "If-Match"));
        assertFalse(parameters(detail, detail.getPatch()).stream().anyMatch(p -> "Idempotency-Key".equals(p.getName())));
        assertEquals("Exactly one nonnegative decimal aggregate version is required. A syntactically valid value "
                + "that does not equal the current version produces HTTP 412 with the operation's specific code: "
                + "expected-version-mismatch for detail updates and stale-party-version for activation.",
                api.getComponents().getParameters().get("IfMatch").getDescription());
    }

    @Test
    void closesIdentifierBodiesAndPreservesNullableMetadata() {
        Schema<?> create = schema("PartyIdentifierCreateRequest");
        assertEquals(Set.of("identifierSchemeCode", "value"), Set.copyOf(create.getRequired()));
        assertEquals(Set.of("identifierSchemeCode", "value", "issuerCode", "issuedOn", "expiresOn", "isPrimary"), create.getProperties().keySet());
        Schema<?> patch = schema("PartyIdentifierUpdateRequest");
        assertEquals(Set.of("issuerCode", "issuedOn", "expiresOn"), patch.getProperties().keySet());
        assertEquals(1, patch.getMinProperties());
        for (Schema<?> body : List.of(create, patch)) {
            assertEquals(Boolean.FALSE, body.getAdditionalProperties());
            assertTrue(body.getDescription().contains("UTF-16"));
            assertTrue(body.getDescription().contains("duplicate"));
            for (String nullable : List.of("issuerCode", "issuedOn", "expiresOn")) {
                assertEquals(Boolean.TRUE, body.getProperties().get(nullable).getNullable());
            }
        }
        assertEquals(Boolean.FALSE, create.getProperties().get("isPrimary").getDefault());
        assertNotEquals(Boolean.TRUE, create.getProperties().get("isPrimary").getNullable());
        assertEquals(64, create.getProperties().get("identifierSchemeCode").getMaxLength());
        assertEquals(256, create.getProperties().get("value").getMaxLength());
        assertEquals(64, patch.getProperties().get("issuerCode").getMaxLength());
    }

    @Test
    void closesOptionalActionBodiesAndPreservesStrictDefaults() {
        Schema<?> reason = schema("PartyIdentifierReasonRequest");
        assertEquals(Boolean.FALSE, reason.getAdditionalProperties());
        assertEquals(Set.of("reasonCode", "reasonDetail"), reason.getProperties().keySet());
        assertEquals(64, reason.getProperties().get("reasonCode").getMaxLength());
        assertEquals(1000, reason.getProperties().get("reasonDetail").getMaxLength());
        assertTrue(reason.getDescription().contains("Unicode code points"));
        reason.getProperties().values().forEach(value -> assertNotEquals(Boolean.TRUE, value.getNullable()));
        Schema<?> replacement = schema("SetPrimaryIdentifierRequest");
        assertEquals(Boolean.FALSE, replacement.getAdditionalProperties());
        assertEquals(Set.of("replaceCurrentPrimary"), replacement.getProperties().keySet());
        assertEquals(Boolean.FALSE, replacement.getProperties().get("replaceCurrentPrimary").getDefault());
        assertNotEquals(Boolean.TRUE, replacement.getProperties().get("replaceCurrentPrimary").getNullable());
    }

    @Test
    void definesStrictHistoricalQueriesAndSupportedCollectionCapacity() {
        PathItem collection = api.getPaths().get(ROOT);
        Operation list = collection.getGet();
        var queries = parameters(collection, list).stream().filter(p -> "query".equals(p.getIn()))
                .collect(Collectors.toMap(Parameter::getName, p -> p));
        assertEquals(Set.of("identifierSchemeId", "schemeCode", "status", "isPrimary", "cursor", "limit"), queries.keySet());
        assertEquals(UUID_PATTERN, queries.get("identifierSchemeId").getSchema().getPattern());
        assertEquals(64, queries.get("schemeCode").getSchema().getMaxLength());
        assertEquals(256, queries.get("cursor").getSchema().getMaxLength());
        assertEquals(50, queries.get("limit").getSchema().getDefault());
        assertEquals(BigDecimal.ONE, queries.get("limit").getSchema().getMinimum());
        assertEquals(new BigDecimal("200"), queries.get("limit").getSchema().getMaximum());
        requireRules(list, "AND", "unknown", "repeated", "createdAt DESC", "unsigned", "identifierId DESC", "consistent", "all five", "live");
        Schema<?> page = schema("PartyIdentifierCollectionApiResponse");
        assertEquals(8, page.getRequired().size());
        for (String name : List.of("totalElements", "totalPages", "numberOfElements")) {
            Schema<?> count = page.getProperties().get(name);
            assertEquals(BigDecimal.ZERO, count.getMinimum());
            assertEquals(new BigDecimal("2147483647"), count.getMaximum());
            assertNotEquals(Boolean.TRUE, count.getNullable());
        }
        for (String cursor : List.of("nextCursor", "prevCursor")) {
            assertEquals(Boolean.TRUE, page.getProperties().get(cursor).getNullable());
        }
    }

    @Test
    void declaresBodyPresenceLifecycleMatrixAndPrimaryScopeBeforeImplementation() {
        PathItem collection = api.getPaths().get(ROOT);
        assertTrue(collection.getPost().getRequestBody().getRequired());
        requireRules(collection.getPost(), "body", "Idempotency-Key", "partyId", "provisional", "replay");
        Operation patch = api.getPaths().get(ITEM).getPatch();
        assertTrue(patch.getRequestBody().getRequired());
        requireRules(patch, "omitted", "null", "PENDING_VERIFICATION", "VERIFIED", "identical", "Idempotency-Key", "If-Match");
        Operation verify = api.getPaths().get(ITEM + "/verify").getPost();
        assertNull(verify.getRequestBody());
        requireRules(verify, "no body", "whitespace", "PENDING_VERIFICATION", "VERIFIED", "User-Id", "UTC", "primary-identifier-conflict", "no external");
        for (String action : List.of("reject", "revoke", "set-primary")) {
            PathItem item = api.getPaths().get(ITEM + "/" + action);
            Operation operation = item.getPost();
            assertEquals(Boolean.FALSE, operation.getRequestBody().getRequired());
            assertEquals(api.getComponents().getParameters().get("PartyIdentifierIfMatch"), parameter(item, operation, "If-Match"));
            requireRules(operation, "body", "partyId", "identifierId", "If-Match", "Idempotency-Key", "replay", "null");
        }
        requireRules(api.getPaths().get(ITEM + "/reject").getPost(), "PENDING_VERIFICATION", "REJECTED", "past", "reason");
        requireRules(api.getPaths().get(ITEM + "/revoke").getPost(), "VERIFIED", "REVOKED", "verifiedAt", "verifiedBy", "past", "reason");
        requireRules(api.getPaths().get(ITEM + "/set-primary").getPost(), "tenant", "Party", "scheme", "replaceCurrentPrimary", "former", "atomic", "once");
    }

    @Test
    void publishesEveryClosedLifecycleMatrixEntryAndTerminality() {
        Schema<?> status = schema("PartyIdentifierStatus");
        assertEquals(List.of("PENDING_VERIFICATION", "VERIFIED", "REJECTED", "EXPIRED", "REVOKED"), status.getEnum());
        String description = status.getDescription();
        assertNotNull(description);
        for (String row : List.of(
                "| PENDING_VERIFICATION | VERIFIED | REJECTED | Forbidden |",
                "| VERIFIED | Forbidden | Forbidden | REVOKED |",
                "| REJECTED | Forbidden | Forbidden | Forbidden |",
                "| EXPIRED | Forbidden | Forbidden | Forbidden |",
                "| REVOKED | Forbidden | Forbidden | Forbidden |")) {
            assertTrue(description.contains(row), row);
        }
        for (String rule : List.of("409 invalid-identifier-lifecycle", "without mutation",
                "Unkeyed repeats conflict", "replay the original", "No public expire/reactivate")) {
            assertTrue(description.contains(rule), rule);
        }
    }

    @Test
    void pinsActionSyntaxAndBusinessFailurePrecedence() {
        for (String action : List.of("verify", "reject", "revoke", "set-primary")) {
            Operation operation = api.getPaths().get(ITEM + "/" + action).getPost();
            String description = operation.getDescription().replaceAll("\\s+", " ");
            assertTrue(description.contains("canonical partyId, canonical identifierId, identifier If-Match, then optional Idempotency-Key"), action);
            assertTrue(description.contains("Business precedence is completed equivalent replay or changed-input key conflict, Party absence "
                    + "(404 party-not-found), identifier absence (404 identifier-not-found), expected version, lifecycle, "
                    + "target-version exhaustion, validity, then"), action);
            assertEquals(2, parameters(api.getPaths().get(ITEM + "/" + action), operation).stream()
                    .filter(parameter -> "If-Match".equals(parameter.getName()) || "Idempotency-Key".equals(parameter.getName())).count());
        }
        Operation patch = api.getPaths().get(ITEM).getPatch();
        requireRules(patch, "Ignore Idempotency-Key completely", "omitted metadata retains", "explicit null clears",
                "Even an identical", "issuer-only", "scheme lifecycle does not prevent");
    }

    @Test
    void bindsWithdrawalAndSelectionToTheirStrictOptionalSchemas() {
        for (String action : List.of("reject", "revoke")) {
            Operation operation = api.getPaths().get(ITEM + "/" + action).getPost();
            assertEquals("#/components/schemas/PartyIdentifierReasonRequest",
                    operation.getRequestBody().getContent().get("application/json").getSchema().get$ref());
        }
        Operation selection = api.getPaths().get(ITEM + "/set-primary").getPost();
        assertEquals("#/components/schemas/SetPrimaryIdentifierRequest",
                selection.getRequestBody().getContent().get("application/json").getSchema().get$ref());
        for (String forbidden : List.of("PartyIdentifierUpdateRequest", "PartyIdentifierReasonRequest", "SetPrimaryIdentifierRequest")) {
            Schema<?> request = schema(forbidden);
            assertTrue(request.getRequired() == null || request.getRequired().isEmpty(), forbidden);
            assertNotEquals(Boolean.TRUE, request.getNullable(), forbidden);
        }
    }

    private static OpenAPI load() {
        var result = new OpenAPIV3Parser().readLocation("docs/contracts/party-registry.openapi.yaml", null, null);
        assertTrue(result.getMessages().isEmpty(), () -> String.valueOf(result.getMessages()));
        OpenAPI document = result.getOpenAPI();
        assertNotNull(document);
        return document;
    }

    private Schema<?> schema(String name) {
        Schema<?> result = api.getComponents().getSchemas().get(name);
        assertNotNull(result, name);
        return result;
    }

    private List<Parameter> parameters(PathItem item, Operation operation) {
        var result = new ArrayList<Parameter>();
        if (item.getParameters() != null) result.addAll(item.getParameters());
        if (operation.getParameters() != null) result.addAll(operation.getParameters());
        return result.stream().map(parameter -> parameter.get$ref() == null ? parameter
                : api.getComponents().getParameters().get(parameter.get$ref().substring("#/components/parameters/".length()))).toList();
    }

    private Parameter parameter(PathItem item, Operation operation, String name) {
        var matches = parameters(item, operation).stream().filter(value -> name.equals(value.getName())).toList();
        assertEquals(1, matches.size(), operation.getOperationId() + ": " + name);
        return matches.getFirst();
    }

    private static void requireRules(Operation operation, String... rules) {
        String description = operation.getDescription();
        assertNotNull(description, operation.getOperationId());
        for (String rule : rules) assertTrue(description.contains(rule), operation.getOperationId() + ": " + rule);
    }
}
