package com.alexastudillo.partyregistry.contractvalidation;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractValidationHarnessTest {
    private static final Path FIXTURES = Path.of("src/test/resources/contract-validation");

    @Test
    void acceptsValidOpenApiWithResolvedLocalReference() {
        assertDoesNotThrow(() -> ContractValidationHarness.validateOpenApi(FIXTURES.resolve("openapi-valid.yaml")));
    }

    @Test
    void rejectsMalformedOpenApi() {
        assertThrows(ContractValidationException.class,
                () -> ContractValidationHarness.validateOpenApi(FIXTURES.resolve("openapi-malformed.yaml")));
    }

    @Test
    void rejectsUnresolvedOpenApiReference() {
        assertThrows(ContractValidationException.class,
                () -> ContractValidationHarness.validateOpenApi(FIXTURES.resolve("openapi-unresolved-ref.yaml")));
    }

    @Test
    void acceptsDraft202012SchemaAndEveryDiscoveredValidExample() {
        int validated = ContractValidationHarness.validateEventContracts(
                FIXTURES.resolve("event-schema-valid.json"), FIXTURES.resolve("examples-valid"));

        assertEquals(2, validated);
    }

    @Test
    void rejectsInvalidJsonSchema() {
        assertThrows(ContractValidationException.class,
                () -> ContractValidationHarness.validateEventContracts(
                        FIXTURES.resolve("event-schema-invalid.json"), FIXTURES.resolve("examples-valid")));
    }

    @Test
    void rejectsExampleThatViolatesCatalogSchema() {
        assertThrows(ContractValidationException.class,
                () -> ContractValidationHarness.validateEventContracts(
                        FIXTURES.resolve("event-schema-valid.json"), FIXTURES.resolve("examples-invalid")));
    }
}
