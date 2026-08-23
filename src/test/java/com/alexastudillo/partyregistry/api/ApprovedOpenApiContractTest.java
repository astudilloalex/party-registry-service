package com.alexastudillo.partyregistry.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the approved OpenAPI document is valid and packaged without divergence.
 */
class ApprovedOpenApiContractTest {

    private static final Path APPROVED_CONTRACT = Path.of(
            "docs/contracts/party-registry.openapi.yaml");

    @Test
    void approvedContractParsesWithoutErrors() {
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(
                APPROVED_CONTRACT.toString(),
                null,
                null);

        assertTrue(result.getMessages().isEmpty(), () -> String.join(System.lineSeparator(), result.getMessages()));
        OpenAPI contract = result.getOpenAPI();
        assertNotNull(contract);
        assertEquals("3.1.1", contract.getOpenapi());
        assertEquals("Party Registry Service API", contract.getInfo().getTitle());
        assertEquals(9, contract.getPaths().size());
        assertNotNull(contract.getComponents().getSchemas().get("BadRequestResponse"));
        assertNotNull(contract.getComponents().getSchemas().get("DependencyUnavailableResponse"));
    }

    @Test
    void packagedContractIsByteForByteEquivalent() throws IOException {
        String approved = Files.readString(APPROVED_CONTRACT, StandardCharsets.UTF_8);
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream("META-INF/openapi.yaml");
        assertNotNull(resourceStream);
        try (InputStream packagedStream = resourceStream) {
            String packaged = new String(packagedStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(approved, packaged);
        }
    }
}
