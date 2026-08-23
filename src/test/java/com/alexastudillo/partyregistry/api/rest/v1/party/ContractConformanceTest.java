package com.alexastudillo.partyregistry.api.rest.v1.party;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

class ContractConformanceTest {

    private static final Path OPEN_API_CONTRACT =
            Path.of("specs/001-party-registration/contracts/party-registration.openapi.yaml");
    private static final Path EVENT_SCHEMA_CONTRACT =
            Path.of("specs/001-party-registration/contracts/party-created-v1.schema.json");
    private static final Set<String> REQUIRED_HEADERS = Set.of("Tenant-Id", "User-Id", "Process-Id");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void openApiContractParsesAndDocumentsRequiredContextHeaders() {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);

        SwaggerParseResult parseResult = new OpenAPIV3Parser()
                .readLocation(OPEN_API_CONTRACT.toAbsolutePath().toString(), null, parseOptions);

        assertTrue(parseResult.getMessages().isEmpty(),
                () -> "OpenAPI parser messages: " + String.join(System.lineSeparator(), parseResult.getMessages()));

        OpenAPI openApi = parseResult.getOpenAPI();
        assertNotNull(openApi, "The OpenAPI document must be parsed");
        assertEquals("3.1.1", openApi.getOpenapi());

        assertNotNull(openApi.getPaths().get("/internal/v1/parties"),
                "POST /internal/v1/parties must be documented");
        Operation post = openApi.getPaths().get("/internal/v1/parties").getPost();
        assertNotNull(post, "POST /internal/v1/parties must be documented");

        List<Parameter> parameters = post.getParameters().stream()
                .map(parameter -> parameter.get$ref() == null
                        ? parameter
                        : openApi.getComponents().getParameters()
                                .get(parameter.get$ref().substring(parameter.get$ref().lastIndexOf('/') + 1)))
                .toList();
        Map<String, Parameter> parametersByName = parameters.stream()
                .collect(Collectors.toMap(Parameter::getName, Function.identity()));

        assertEquals(REQUIRED_HEADERS, parametersByName.keySet());
        assertEquals(3, parameters.size());
        parameters.forEach(parameter -> assertAll(
                () -> assertEquals("header", parameter.getIn()),
                () -> assertTrue(Boolean.TRUE.equals(parameter.getRequired()),
                        () -> parameter.getName() + " must be required")));
    }

    @Test
    void eventSchemaAcceptsApprovedExamplesAndRejectsUnknownProperties() throws IOException {
        JsonNode schemaDocument = OBJECT_MAPPER.readTree(EVENT_SCHEMA_CONTRACT.toFile());
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(schemaDocument);

        JsonNode examples = schemaDocument.get("examples");
        assertNotNull(examples, "The event schema must contain approved examples");
        assertTrue(examples.isArray());
        assertEquals(2, examples.size());

        examples.forEach(example -> {
            Set<ValidationMessage> messages = schema.validate(example);
            assertTrue(messages.isEmpty(), () -> "Approved example failed validation: " + messages);
        });

        ObjectNode payloadWithUnknownProperty = (ObjectNode) examples.get(0).deepCopy();
        payloadWithUnknownProperty.put("unknown", true);

        assertFalse(schema.validate(payloadWithUnknownProperty).isEmpty(),
                "The event schema must reject unknown properties");
    }
}
