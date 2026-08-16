package com.alexastudillo.partyregistry.contract;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiBehaviorContractTest {
    private static final Set<String> EXPECTED_OPERATIONS = Set.of(
            "createParty", "searchParties", "getParty", "updateParty", "transitionPartyStatus",
            "getPartyDetails", "updatePartyDetails", "addNationality", "searchNationalities",
            "getNationality", "updateNationality", "endNationality", "createPartyIdentifier",
            "searchPartyIdentifiers", "exactSearchPartyIdentifiers", "findPartyIdentifiersByPartyAndScheme",
            "getPartyIdentifier", "updatePartyIdentifier", "transitionPartyIdentifierStatus",
            "decryptPartyIdentifier");
    private static final Set<String> CONDITIONAL_MUTATIONS = Set.of(
            "updateParty", "transitionPartyStatus", "updatePartyDetails", "addNationality",
            "updateNationality", "endNationality", "updatePartyIdentifier", "transitionPartyIdentifierStatus");
    private static final Set<String> PAGINATED_SEARCHES =
            Set.of("searchParties", "searchNationalities", "searchPartyIdentifiers");

    @Test
    void exposesOnlyApprovedV1OperationsAndNoSchemeOutboxDeleteOrAuthenticationSurface() {
        OpenAPI api = ContractSources.openApi();
        assertEquals("/api/v1", api.getServers().getFirst().getUrl());
        assertTrue(api.getSecurity() == null || api.getSecurity().isEmpty(), "V1 must define no security requirement");
        assertTrue(api.getComponents().getSecuritySchemes() == null || api.getComponents().getSecuritySchemes().isEmpty());

        Set<String> operationIds = operations(api).stream().map(Operation::getOperationId).collect(Collectors.toSet());
        assertEquals(EXPECTED_OPERATIONS, operationIds);
        api.getPaths().forEach((path, item) -> {
            String normalized = path.toLowerCase();
            assertFalse(normalized.contains("scheme") && !normalized.contains("by-scheme"), "Scheme resource exposed: " + path);
            assertFalse(normalized.contains("outbox"), "Outbox resource exposed: " + path);
            assertNull(item.getDelete(), "Business DELETE exposed at " + path);
        });
        operations(api).forEach(operation -> {
            assertFalse(operation.getResponses().containsKey("401"), operation.getOperationId() + " exposes 401");
            assertFalse(operation.getResponses().containsKey("403"), operation.getOperationId() + " exposes 403");
            assertTrue(operation.getSecurity() == null || operation.getSecurity().isEmpty());
            assertTrue(allParameters(operation, api).stream().noneMatch(parameter ->
                    parameter.getName().equalsIgnoreCase("Idempotency-Key")));
        });
        assertFalse(api.getComponents().getSchemas().keySet().stream().anyMatch(name ->
                name.toLowerCase().contains("scheme") || name.toLowerCase().contains("outbox")
                        || name.toLowerCase().contains("idempotency")));
    }

    @Test
    void requiresTrustedContextAndReturnsEffectiveProcessIdWithoutTenantBodyDisclosure() {
        OpenAPI api = ContractSources.openApi();
        operations(api).forEach(operation -> {
            Map<String, Parameter> parameters = allParameters(operation, api).stream()
                    .collect(Collectors.toMap(Parameter::getName, parameter -> parameter, (left, right) -> left));
            assertEquals(Boolean.TRUE, parameters.get("tenant-id").getRequired(), operation.getOperationId());
            assertEquals(Boolean.TRUE, parameters.get("user-id").getRequired(), operation.getOperationId());
            assertEquals(Boolean.FALSE, parameters.get("process-id").getRequired(), operation.getOperationId());
            operation.getResponses().forEach((status, response) ->
                    assertNotNull(resolveResponse(api, response).getHeaders().get("process-id"),
                            operation.getOperationId() + " " + status + " lacks process-id"));
        });
        api.getComponents().getSchemas().forEach((name, schema) -> {
            Set<String> fields = schema.getProperties() == null ? Set.of() : schema.getProperties().keySet();
            assertFalse(fields.stream().anyMatch(field -> field.equalsIgnoreCase("tenantId")
                    || field.equalsIgnoreCase("tenant-id")), name + " discloses tenant ownership");
        });
    }

    @Test
    void definesStableEnvelopePaginationPreconditionsAndNoStoreResponses() {
        OpenAPI api = ContractSources.openApi();
        Schema<?> error = api.getComponents().getSchemas().get("ErrorResponse");
        assertEquals(Set.of("status", "code", "data"), new HashSet<>(error.getRequired()));
        @SuppressWarnings("unchecked")
        List<String> errorCodes = (List<String>) ((Schema<?>) error.getProperties().get("code")).getEnum();
        assertEquals(Set.of("VALIDATION_ERROR", "NOT_FOUND", "CONFLICT", "PRECONDITION_REQUIRED",
                "VERSION_CONFLICT", "DEPENDENCY_UNAVAILABLE", "INTERNAL_ERROR"), new HashSet<>(errorCodes));

        CONDITIONAL_MUTATIONS.forEach(operationId -> {
            Operation operation = operation(api, operationId);
            assertTrue(allParameters(operation, api).stream().anyMatch(parameter -> parameter.getName().equals("If-Match")), operationId);
            assertTrue(operation.getResponses().containsKey("412"), operationId);
            assertTrue(operation.getResponses().containsKey("428"), operationId);
            ApiResponse success = operation.getResponses().entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("2"))
                    .map(Map.Entry::getValue)
                    .map(response -> resolveResponse(api, response))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(success.getHeaders().get("ETag"), operationId + " success lacks ETag");
        });

        PAGINATED_SEARCHES.forEach(operationId -> {
            Operation operation = operation(api, operationId);
            Map<String, Parameter> parameters = allParameters(operation, api).stream()
                    .collect(Collectors.toMap(Parameter::getName, parameter -> parameter));
            assertEquals(0, parameters.get("page").getSchema().getDefault());
            assertEquals(20, parameters.get("size").getSchema().getDefault());
            assertEquals(1, parameters.get("size").getSchema().getMinimum().intValue());
            assertEquals(100, parameters.get("size").getSchema().getMaximum().intValue());
            Schema<?> responseSchema = responseSchema(api, resolveResponse(api, operation.getResponses().get("200")));
            assertTrue(responseSchema.getRequired().contains("pagination"), operationId);
        });

        Operation decrypt = operation(api, "decryptPartyIdentifier");
        decrypt.getResponses().forEach((status, response) ->
                assertEquals("#/components/headers/CacheControlNoStore",
                        resolveResponse(api, response).getHeaders().get("Cache-Control").get$ref(),
                        "decryption " + status + " must be no-store"));
    }

    @Test
    void limitsIdentifierPlaintextToWriteInputsAndSeparateDecryptionWhileOrdinaryResultsAreMasked() {
        OpenAPI api = ContractSources.openApi();
        Schema<?> identifierData = api.getComponents().getSchemas().get("IdentifierData");
        assertTrue(identifierData.getProperties().containsKey("maskedValue"));
        assertFalse(identifierData.getProperties().containsKey("value"));
        assertEquals("^\\*\\*\\*\\*(?:.{4})?$", ((Schema<?>) identifierData.getProperties().get("maskedValue")).getPattern());
        assertEquals(Boolean.TRUE, ((Schema<?>) api.getComponents().getSchemas().get("PartyIdentifierCreateRequest")
                .getProperties().get("value")).getWriteOnly());
        assertEquals(Boolean.TRUE, ((Schema<?>) api.getComponents().getSchemas().get("ExactIdentifierSearchRequest")
                .getProperties().get("value")).getWriteOnly());
        assertTrue(api.getComponents().getSchemas().get("DecryptedIdentifierData").getProperties().containsKey("value"));

        String contractText;
        try {
            contractText = java.nio.file.Files.readString(ContractSources.OPEN_API).toLowerCase();
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
        for (String prohibited : List.of("stacktrace", "stack_trace", "sqlstate", "ciphertext", "normalizedvaluehash", "keymaterial")) {
            assertFalse(contractText.contains(prohibited), "Prohibited API surface token: " + prohibited);
        }
    }

    @Test
    void mapsEveryAssignedAcceptanceCriterionToAnExecutableContractTest() {
        JsonNode traceability = ContractSources.json(ContractSources.FIXTURE_CATALOG).path("traceability");
        Set<String> mapped = new TreeSet<>();
        traceability.forEach(mapping -> {
            assertTrue(mapping.path("testId").asText().matches("[A-Za-z]+ContractTest#[A-Za-z0-9]+"));
            assertTrue(mapping.path("observableOutcome").asText().length() > 10);
            mapping.path("acceptanceCriteria").forEach(criterion -> mapped.add(criterion.asText()));
        });
        assertEquals(assignedAcceptanceCriteria(), mapped);
    }

    private static Set<String> assignedAcceptanceCriteria() {
        Set<String> criteria = new TreeSet<>(Set.of("AC-001", "AC-004", "AC-014"));
        addRange(criteria, 7, 10);
        addRange(criteria, 19, 23);
        addRange(criteria, 27, 30);
        addRange(criteria, 32, 62);
        addRange(criteria, 67, 81);
        return criteria;
    }

    private static void addRange(Set<String> criteria, int first, int last) {
        for (int number = first; number <= last; number++) {
            criteria.add("AC-%03d".formatted(number));
        }
    }

    private static List<Operation> operations(OpenAPI api) {
        return api.getPaths().values().stream().flatMap(item -> item.readOperations().stream()).toList();
    }

    private static Operation operation(OpenAPI api, String operationId) {
        return operations(api).stream().filter(candidate -> operationId.equals(candidate.getOperationId())).findFirst().orElseThrow();
    }

    private static List<Parameter> allParameters(Operation operation, OpenAPI api) {
        List<Parameter> parameters = new ArrayList<>();
        api.getPaths().values().stream().filter(item -> item.readOperations().contains(operation)).findFirst()
                .map(PathItem::getParameters).filter(list -> list != null).ifPresent(parameters::addAll);
        if (operation.getParameters() != null) {
            parameters.addAll(operation.getParameters());
        }
        return parameters.stream().map(parameter -> parameter.get$ref() == null ? parameter
                : api.getComponents().getParameters().get(parameter.get$ref().substring(parameter.get$ref().lastIndexOf('/') + 1))).toList();
    }

    private static ApiResponse resolveResponse(OpenAPI api, ApiResponse response) {
        ApiResponse resolved = response;
        Set<String> visited = new HashSet<>();
        while (resolved != null && resolved.get$ref() != null) {
            assertTrue(visited.add(resolved.get$ref()), "Cyclic response reference " + resolved.get$ref());
            resolved = api.getComponents().getResponses()
                    .get(resolved.get$ref().substring(resolved.get$ref().lastIndexOf('/') + 1));
        }
        assertNotNull(resolved, "Unresolved response reference");
        return resolved;
    }

    private static Schema<?> responseSchema(OpenAPI api, ApiResponse response) {
        Schema<?> schema = response.getContent().get("application/json").getSchema();
        return schema.get$ref() == null ? schema
                : api.getComponents().getSchemas().get(schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1));
    }
}
