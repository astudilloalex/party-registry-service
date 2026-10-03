package com.alexastudillo.partyregistry.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Validates scheme examples and observed JSON against referenced OpenAPI 3.0 schemas using existing test dependencies. */
public final class IdentifierSchemeContractValidator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final OpenAPI api;
    private final JsonNode document;

    /** Accepts either a parsed source contract or a served/packaged contract after reference resolution. */
    public IdentifierSchemeContractValidator(OpenAPI api) {
        this.api = api;
        document = io.swagger.v3.core.util.Json.mapper().valueToTree(api);
        assertReferences(document);
    }

    /** Parses and resolves the static document, rejecting Swagger diagnostics and unresolved references. */
    public static OpenAPI readContract(Path path) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(path.toString(), null, options);
        assertNotNull(result.getOpenAPI(), () -> String.valueOf(result.getMessages()));
        assertTrue(result.getMessages().isEmpty(), () -> String.valueOf(result.getMessages()));
        return result.getOpenAPI();
    }

    /** Checks a request's JSON against the schema declared by the selected operation. */
    public void assertRequest(String path, PathItem.HttpMethod method, String json) {
        var body = api.getPaths().get(path).readOperationsMap().get(method).getRequestBody();
        assertNotNull(body, "Operation declares no request body: " + path);
        assertSchema(body.getContent().get("application/json").getSchema(), parse(json));
    }

    /** Checks response schema, explicit status, stable documented code and page/omission invariants. */
    public void assertResponse(String path, PathItem.HttpMethod method, int status, String json) {
        ApiResponse response = response(api.getPaths().get(path).readOperationsMap().get(method).getResponses().get(String.valueOf(status)));
        assertNotNull(response, "Undocumented HTTP status " + status + " for " + path);
        JsonNode value = parse(json);
        assertEquals(status, value.path("status").intValue(), "HTTP/body status mismatch");
        assertSchema(response.getContent().get("application/json").getSchema(), value);
        if (status >= 400) {
            assertTrue(examples(response.getContent().get("application/json")).stream()
                    .anyMatch(example -> example.path("code").equals(value.path("code"))), "Undocumented error code: " + value);
        } else if (value.path("data").isArray()) {
            assertEquals(value.path("data").size(), value.path("numberOfElements").intValue());
            value.path("data").forEach(this::assertOmittedOptionals);
        } else {
            assertOmittedOptionals(value.path("data"));
            if (status == 201) {
                assertEquals(value.path("data").path("createdAt"), value.path("data").path("updatedAt"));
            }
        }
    }

    /** Validates a named schema; useful for checking consumer request and mutation boundary fixtures. */
    public void assertSchema(String name, JsonNode value) {
        assertSchema(api.getComponents().getSchemas().get(name), value);
    }

    /** Validates a possibly referenced/composed schema without flattening away nullability or constraints. */
    public void assertSchema(Schema<?> schema, JsonNode value) {
        assertNotNull(schema, "Missing declared schema");
        List<String> errors = new ArrayList<>();
        validate(io.swagger.v3.core.util.Json.mapper().valueToTree(schema), value, "$", errors, 0);
        assertTrue(errors.isEmpty(), () -> "Schema violations: " + errors + "; JSON=" + value);
    }

    /** Resolves a response reference while retaining its original media schema. */
    public ApiResponse response(ApiResponse value) {
        if (value == null || value.get$ref() == null) return value;
        assertTrue(value.get$ref().startsWith("#/components/responses/"));
        return response(api.getComponents().getResponses().get(value.get$ref().substring("#/components/responses/".length())));
    }

    /** Materializes singular and named examples exactly as the parser represents their JSON values. */
    public static List<JsonNode> examples(MediaType media) {
        var examples = new ArrayList<JsonNode>();
        if (media.getExample() != null) examples.add(JSON.valueToTree(media.getExample()));
        if (media.getExamples() != null) media.getExamples().values().forEach(example -> {
            assertNull(example.get$ref(), "Example references must be resolved before validation");
            assertNotNull(example.getValue(), "Example must contain JSON");
            examples.add(JSON.valueToTree(example.getValue()));
        });
        return List.copyOf(examples);
    }

    private void assertReferences(JsonNode node) {
        if (node.isObject() && node.has("$ref")) {
            String ref = node.path("$ref").textValue();
            assertTrue(ref.startsWith("#/"), "Unexpected external reference: " + ref);
            assertFalse(document.at(ref.substring(1)).isMissingNode(), "Unresolved reference: " + ref);
        }
        node.forEach(this::assertReferences);
    }

    private void validate(JsonNode schema, JsonNode value, String path, List<String> errors, int depth) {
        if (depth > 50) throw new AssertionError("Recursive schema exceeds validation depth at " + path);
        if (schema.has("$ref")) {
            validate(document.at(schema.path("$ref").textValue().substring(1)), value, path, errors, depth + 1);
            return;
        }
        if (value.isNull()) {
            if (!schema.path("nullable").asBoolean(false)) errors.add(path + " must not be null");
            return;
        }
        for (JsonNode part : schema.path("allOf")) validate(part, value, path, errors, depth + 1);
        for (String composition : List.of("oneOf", "anyOf")) {
            if (schema.has(composition)) {
                int matches = 0;
                for (JsonNode part : schema.path(composition)) {
                    var candidate = new ArrayList<String>();
                    validate(part, value, path, candidate, depth + 1);
                    if (candidate.isEmpty()) matches++;
                }
                if (matches == 0 || (composition.equals("oneOf") && matches != 1)) errors.add(path + " fails " + composition);
            }
        }
        if (schema.has("not")) {
            var candidate = new ArrayList<String>();
            validate(schema.path("not"), value, path, candidate, depth + 1);
            if (candidate.isEmpty()) errors.add(path + " matches forbidden schema");
        }
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonNode allowed : schema.path("enum")) {
                if (allowed.equals(value) || (allowed.isNumber() && value.isNumber()
                        && allowed.decimalValue().compareTo(value.decimalValue()) == 0)) found = true;
            }
            if (!found) errors.add(path + " invalid enum");
        }
        String type = schema.path("type").asText("");
        boolean correctType = switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "boolean" -> value.isBoolean();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "" -> true;
            default -> throw new AssertionError("Unsupported schema type: " + type);
        };
        if (!correctType) { errors.add(path + " expected " + type); return; }
        if (value.isObject()) {
            for (JsonNode required : schema.path("required")) {
                if (!value.has(required.textValue())) errors.add(path + " missing " + required.textValue());
            }
            if (schema.has("minProperties") && value.size() < schema.path("minProperties").intValue()) errors.add(path + " too few properties");
            if (schema.has("maxProperties") && value.size() > schema.path("maxProperties").intValue()) errors.add(path + " too many properties");
            for (Map.Entry<String, JsonNode> field : value.properties()) {
                JsonNode property = schema.path("properties").path(field.getKey());
                if (!property.isMissingNode()) validate(property, field.getValue(), path + "." + field.getKey(), errors, depth + 1);
                else if (schema.path("additionalProperties").isBoolean() && !schema.path("additionalProperties").booleanValue()) errors.add(path + " unknown " + field.getKey());
                else if (schema.path("additionalProperties").isObject()) validate(schema.path("additionalProperties"), field.getValue(), path, errors, depth + 1);
            }
        }
        if (value.isArray()) {
            if (schema.has("maxItems") && value.size() > schema.path("maxItems").intValue()) errors.add(path + " too many items");
            if (schema.has("minItems") && value.size() < schema.path("minItems").intValue()) errors.add(path + " too few items");
            if (schema.path("uniqueItems").asBoolean() && java.util.stream.StreamSupport.stream(value.spliterator(), false).distinct().count() != value.size()) errors.add(path + " duplicate items");
            if (schema.has("items")) for (int i = 0; i < value.size(); i++) validate(schema.path("items"), value.get(i), path + "[" + i + "]", errors, depth + 1);
        }
        if (value.isTextual()) {
            String text = value.textValue();
            int length = text.codePointCount(0, text.length());
            if (schema.has("maxLength") && length > schema.path("maxLength").intValue()) errors.add(path + " too long");
            if (schema.has("minLength") && length < schema.path("minLength").intValue()) errors.add(path + " too short");
            if (schema.has("pattern") && !Pattern.compile(schema.path("pattern").textValue()).matcher(text).find()) errors.add(path + " invalid pattern");
            try {
                switch (schema.path("format").asText("")) {
                    case "date-time" -> OffsetDateTime.parse(text);
                    case "uuid" -> UUID.fromString(text);
                    default -> {
                        // Formats other than date-time and uuid do not require parser validation.
                    }
                }
            } catch (IllegalArgumentException | DateTimeParseException _) {
                errors.add(path + " invalid format");
            }
        }
        if (value.isNumber()) {
            if (schema.has("minimum") && value.decimalValue().compareTo(schema.path("minimum").decimalValue()) < 0) errors.add(path + " below minimum");
            if (schema.has("maximum") && value.decimalValue().compareTo(schema.path("maximum").decimalValue()) > 0) errors.add(path + " above maximum");
        }
    }

    private void assertOmittedOptionals(JsonNode data) {
        for (String optional : List.of("description", "minimumLength", "maximumLength")) {
            assertFalse(data.has(optional) && data.path(optional).isNull(), "Absent response property must be omitted: " + optional);
        }
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException failure) {
            throw new AssertionError("Invalid observed JSON", failure);
        }
    }
}
