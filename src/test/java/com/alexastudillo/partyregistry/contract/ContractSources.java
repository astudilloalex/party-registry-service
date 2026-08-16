package com.alexastudillo.partyregistry.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContractSources {
    static final Path OPEN_API = Path.of("api/openapi/v1/party-registry.openapi.yaml");
    static final Path EVENT_SCHEMA = Path.of("api/events/v1/party-registry-events.schema.json");
    static final Path FIXTURE_CATALOG = Path.of("src/test/resources/contracts/fixture-catalog.json");
    static final ObjectMapper JSON = new ObjectMapper();

    private ContractSources() {
    }

    static OpenAPI openApi() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(false);
        options.setValidateExternalRefs(true);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPEN_API.toAbsolutePath().normalize().toUri().toString(), null, options);
        List<String> messages = result.getMessages() == null ? List.of() : result.getMessages();
        assertTrue(messages.isEmpty(), () -> "OpenAPI parse/reference errors: " + String.join("; ", messages));
        assertNotNull(result.getOpenAPI(), "OpenAPI contract did not parse");
        return result.getOpenAPI();
    }

    static JsonNode json(Path path) {
        try {
            return JSON.readTree(Files.readString(path));
        } catch (IOException exception) {
            throw new AssertionError("Cannot read JSON contract fixture " + path, exception);
        }
    }
}
