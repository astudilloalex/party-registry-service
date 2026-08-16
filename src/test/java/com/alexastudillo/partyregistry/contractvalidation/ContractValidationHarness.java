package com.alexastudillo.partyregistry.contractvalidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class ContractValidationHarness {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonSchemaFactory JSON_SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private ContractValidationHarness() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new ContractValidationException(
                    "Expected arguments: <openapi-file> <event-schema-file> <event-examples-directory>");
        }

        Path openApi = Path.of(args[0]);
        Path eventSchema = Path.of(args[1]);
        Path eventExamples = Path.of(args[2]);

        validateOpenApi(openApi);
        int exampleCount = validateEventContracts(eventSchema, eventExamples);
        System.out.printf("Validated OpenAPI document, JSON Schema Draft 2020-12 catalog, and %d event example(s).%n",
                exampleCount);
    }

    static void validateOpenApi(Path openApi) {
        requireRegularFile(openApi, "OpenAPI document");

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(false);
        options.setValidateExternalRefs(true);

        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(openApi.toAbsolutePath().normalize().toUri().toString(), null, options);
        List<String> messages = result.getMessages() == null ? List.of() : result.getMessages();
        if (result.getOpenAPI() == null || !messages.isEmpty()) {
            throw new ContractValidationException(
                    "OpenAPI validation failed for " + openApi + ": " + String.join("; ", messages));
        }
        if (result.getOpenAPI().getOpenapi() == null || !result.getOpenAPI().getOpenapi().startsWith("3.1.")) {
            throw new ContractValidationException("OpenAPI document must declare OpenAPI 3.1.x: " + openApi);
        }
    }

    static int validateEventContracts(Path eventSchema, Path eventExamples) {
        requireRegularFile(eventSchema, "event schema");
        if (!Files.isDirectory(eventExamples)) {
            throw new ContractValidationException("Event examples directory does not exist: " + eventExamples);
        }

        JsonNode schemaNode = readJson(eventSchema, "event schema");
        assertDraft202012(schemaNode, eventSchema);

        JsonSchema schema;
        try {
            schema = JSON_SCHEMA_FACTORY.getSchema(schemaNode);
        } catch (RuntimeException exception) {
            throw new ContractValidationException("Invalid JSON Schema at " + eventSchema, exception);
        }

        List<Path> examples;
        try (Stream<Path> paths = Files.list(eventExamples)) {
            examples = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new ContractValidationException("Cannot discover event examples under " + eventExamples, exception);
        }

        if (examples.isEmpty()) {
            throw new ContractValidationException("No JSON event examples found under " + eventExamples);
        }
        for (Path example : examples) {
            JsonNode exampleNode = readJson(example, "event example");
            Set<ValidationMessage> errors = schema.validate(exampleNode);
            if (!errors.isEmpty()) {
                String details = errors.stream().map(ValidationMessage::getMessage).sorted().reduce((a, b) -> a + "; " + b)
                        .orElse("unknown schema violation");
                throw new ContractValidationException("Event example validation failed for " + example + ": " + details);
            }
        }
        return examples.size();
    }

    private static void assertDraft202012(JsonNode schemaNode, Path eventSchema) {
        JsonNode dialect = schemaNode.get("$schema");
        if (dialect == null || !"https://json-schema.org/draft/2020-12/schema".equals(dialect.textValue())) {
            throw new ContractValidationException(
                    "Event schema must declare https://json-schema.org/draft/2020-12/schema: " + eventSchema);
        }
        try {
            JSON_SCHEMA_FACTORY.getSchema(schemaNode);
        } catch (RuntimeException exception) {
            throw new ContractValidationException("Invalid JSON Schema at " + eventSchema, exception);
        }
    }

    private static JsonNode readJson(Path path, String description) {
        try {
            return JSON.readTree(path.toFile());
        } catch (IOException exception) {
            throw new ContractValidationException("Cannot parse " + description + " at " + path, exception);
        }
    }

    private static void requireRegularFile(Path path, String description) {
        if (!Files.isRegularFile(path)) {
            throw new ContractValidationException(description + " does not exist: " + path);
        }
    }
}
