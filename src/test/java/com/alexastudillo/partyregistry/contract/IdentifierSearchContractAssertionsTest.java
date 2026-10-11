package com.alexastudillo.partyregistry.contract;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Proves shared search assertions reject token, schema, metadata and confidentiality drift. */
class IdentifierSearchContractAssertionsTest {
    private static final String EMPTY = """
            {"status":200,"code":"successful","data":[],"nextCursor":null,"prevCursor":null,
            "totalElements":0,"totalPages":0,"numberOfElements":0}
            """;
    private final OpenAPI api = IdentifierSchemeContractValidator.readContract(Path.of("docs/contracts/party-registry.openapi.yaml"));
    private final IdentifierSearchContractAssertions assertions = new IdentifierSearchContractAssertions(api);

    @Test
    void acceptsPublishedSearchExamplesAndExplicitEmptyMetadata() {
        for (String path : List.of(IdentifierSearchContractAssertions.EXACT_PATH, IdentifierSearchContractAssertions.PREFIX_PATH)) {
            boolean prefix = path.equals(IdentifierSearchContractAssertions.PREFIX_PATH);
            var operation = api.getPaths().get(path).getPost();
            IdentifierSchemeContractValidator.examples(operation.getRequestBody().getContent().get("application/json"))
                    .forEach(example -> assertions.assertRequest(prefix, example.toString()));
            IdentifierSchemeContractValidator.examples(operation.getResponses().get("200").getContent().get("application/json"))
                    .forEach(example -> {
                        if (prefix) assertions.assertPrefixResponse(200, example.toString(), 1);
                        else assertions.assertExactResponse(200, example.toString());
                    });
        }
        assertions.assertExactResponse(200, EMPTY);
        assertions.assertPrefixResponse(200, EMPTY, 50);
        IdentifierSearchContractAssertions.assertErrorResponse(500, "server-error", "{\"status\":500,\"code\":\"server-error\"}");
    }

    @Test
    void rejectsStrictTokenAndRawUtf16Violations() {
        for (String json : List.of(
                "null", "[]", "{\"identifierSchemeCode\":\"X\",\"prefix\":\"A\",\"unknown\":1}",
                "{\"identifierSchemeCode\":\"X\",\"prefix\":\"A\",\"prefix\":\"B\"}",
                "{\"identifierSchemeCode\":\"X\",\"prefix\":\"A\"} {}",
                "{\"identifierSchemeCode\":\"X\",\"prefix\":\" \"}",
                "{\"identifierSchemeCode\":\"X\",\"prefix\":true}")) {
            assertThrows(AssertionError.class, () -> assertions.assertRequest(true, json));
        }
        for (String limit : List.of("null", "\"50\"", "true", "1.0", "1e0", "0", "201", "999999999999999999999999999")) {
            String json = "{\"identifierSchemeCode\":\"X\",\"prefix\":\"A\",\"limit\":" + limit + "}";
            assertThrows(AssertionError.class, () -> assertions.assertRequest(true, json));
        }
        String atBoundary = "{\"identifierSchemeCode\":\"X\",\"prefix\":\"" + "😀".repeat(32) + "\"}";
        assertions.assertRequest(true, atBoundary);
        String overBoundary = "{\"identifierSchemeCode\":\"X\",\"prefix\":\"" + "😀".repeat(33) + "\"}";
        assertThrows(AssertionError.class, () -> assertions.assertRequest(true, overBoundary));
    }

    @Test
    void rejectsMissingMetadataPrivateFieldsWrongStatusesAndOverflow() {
        for (String invalid : List.of(
                EMPTY.replace("\"nextCursor\":null,", ""),
                EMPTY.replace("\"totalElements\":0", "\"totalElements\":null"),
                EMPTY.replace("\"status\":200", "\"status\":201"),
                EMPTY.replace("\"totalElements\":0", "\"totalElements\":2147483648"),
                EMPTY.replace("\"totalPages\":0", "\"totalPages\":1"),
                EMPTY.replace("\"status\":200", "\"value\":\"PRIVATE\",\"status\":200"))) {
            assertThrows(AssertionError.class, () -> assertions.assertExactResponse(200, invalid));
        }
        var example = IdentifierSchemeContractValidator.examples(api.getPaths().get(IdentifierSearchContractAssertions.EXACT_PATH)
                .getPost().getResponses().get("200").getContent().get("application/json")).getFirst();
        ((ObjectNode) example.path("data").get(0).path("identifier")).put("encryptedValue", "PRIVATE");
        String payload = example.toString();
        assertThrows(AssertionError.class, () -> assertions.assertExactResponse(200, payload));
        assertThrows(AssertionError.class, () -> IdentifierSearchContractAssertions.assertErrorResponse(500, "server-error",
                "{\"status\":500,\"code\":\"server-error\",\"message\":\"PRIVATE\"}"));
        assertThrows(AssertionError.class, () -> IdentifierSearchContractAssertions.assertErrorResponse(503, "server-error",
                "{\"status\":500,\"code\":\"server-error\"}"));
    }

    @Test
    void rejectsUnresolvedReferencesAndMissingLengthUnitDeclarations() {
        api.getComponents().getSchemas().put("BrokenFixture", new Schema<>().$ref("#/components/schemas/MissingFixture"));
        assertThrows(AssertionError.class, () -> new IdentifierSearchContractAssertions(api));
        api.getComponents().getSchemas().remove("BrokenFixture");
        Schema<?> requestSchema = api.getComponents().getSchemas().get("IdentifierSearchPrefixRequest");
        requestSchema.getProperties().get("prefix").getExtensions().remove("x-string-length-unit");
        assertThrows(AssertionError.class, () -> assertions.assertRequest(true, "{\"identifierSchemeCode\":\"X\",\"prefix\":\"A\"}"));
    }
}
