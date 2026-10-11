package com.alexastudillo.partyregistry;

import com.alexastudillo.partyregistry.application.model.*;
import com.alexastudillo.partyregistry.contract.IdentifierSearchContractAssertions;
import com.alexastudillo.partyregistry.contract.IdentifierSchemeContractValidator;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;
import com.alexastudillo.partyregistry.domain.policy.StandardIdentifierNormalizer;
import com.alexastudillo.partyregistry.infrastructure.security.HmacIdentifierSearchCursorAdapter;
import com.alexastudillo.partyregistry.infrastructure.security.PartyCursorKeyMaterial;
import com.alexastudillo.partyregistry.infrastructure.security.PartyPaginationConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies real packaged JVM/native search codecs, retained protected history, rotated cursors and exact served contracts. */
@QuarkusIntegrationTest
@QuarkusTestResource(value = PackagedIdentifierSearchResource.class, restrictToAnnotatedClass = true)
@Timeout(120)
class PackagedIdentifierSearchContractIT {
    private final OpenAPI contract = IdentifierSchemeContractValidator.readContract(Path.of("docs/contracts/party-registry.openapi.yaml"));
    private final IdentifierSearchContractAssertions assertions = new IdentifierSearchContractAssertions(contract);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void preExistingVersionOneHistorySupportsExactAndBidirectionalPrefixWithRetainedSigningKeys() {
        var exact = send(false, PackagedIdentifierSearchResource.TENANT, Map.of("identifierSchemeCode", PackagedIdentifierSearchResource.CODE,
                "value", " historical%_é9 "));
        assertions.assertExactResponse(exact.statusCode(), exact.asString());
        assertEquals(5, exact.jsonPath().getInt("totalElements"));
        assertEquals(2, exact.jsonPath().getList("data.type").stream().distinct().count());
        var first = prefix(Map.of("limit", 2));
        assertEquals(5, first.jsonPath().getInt("totalElements"));
        String next = first.jsonPath().getString("nextCursor");
        assertTrue(next.startsWith("1.test-v2."));
        var second = prefix(Map.of("limit", 2, "cursor", next));
        var last = prefix(Map.of("limit", 2, "cursor", second.jsonPath().getString("nextCursor")));
        assertEquals(List.of(2, 2, 1), List.of(first.jsonPath().getInt("numberOfElements"), second.jsonPath().getInt("numberOfElements"), last.jsonPath().getInt("numberOfElements")));
        assertNull(last.jsonPath().getString("nextCursor"));
        assertEquals(second.jsonPath().getList("data"), prefix(Map.of("limit", 2, "cursor", last.jsonPath().getString("prevCursor"))).jsonPath().getList("data"));

        var scope = new IdentifierSearchScope(new TenantId(PackagedIdentifierSearchResource.TENANT), new IdentifierSchemeId(PackagedIdentifierSearchResource.SCHEME),
                PackagedIdentifierSearchResource.CODE, new IdentifierSearchSelector(StandardIdentifierNormalizer.TRIM_UPPERCASE_V1, "HISTORICAL%_É"),
                IdentifierSearchFilters.of(null, null), 2);
        String old = new HmacIdentifierSearchCursorAdapter(new PartyCursorKeyMaterial(new RetainedSigningKey())).encode(scope,
                new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.NEXT,
                        new PartyIdentifierPagePosition(PackagedIdentifierSearchResource.CREATED, new PartyIdentifierId(new UUID(Long.MIN_VALUE, 4)))));
        assertTrue(old.startsWith("1.test-v1."));
        assertEquals(second.jsonPath().getList("data"), prefix(Map.of("limit", 2, "cursor", old)).jsonPath().getList("data"));
    }

    @Test
    void actualNativeCapableJcaCreatesAndFindsNewCurrentKeyEvidence() {
        UUID tenant = UUID.randomUUID();
        String process = UUID.randomUUID().toString();
        String value = "NEW" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var created = given().contentType("application/json").header("Tenant-Id", tenant).header("Process-Id", process)
                .header("User-Id", "packaged-search-test").header("Idempotency-Key", UUID.randomUUID())
                .body(Map.of("givenNames", "Packaged", "familyNames", "Search", "initialIdentifier",
                        Map.of("identifierSchemeCode", "TEST_NATURAL_ACTIVE", "value", value, "isPrimary", false)))
                .post("/v1/natural-person");
        assertEquals(201, created.statusCode());
        for (boolean prefix : List.of(false, true)) {
            var response = send(prefix, tenant, Map.of("identifierSchemeCode", "TEST_NATURAL_ACTIVE", prefix ? "prefix" : "value", value));
            if (prefix) assertions.assertPrefixResponse(response.statusCode(), response.asString(), 50);
            else assertions.assertExactResponse(response.statusCode(), response.asString());
            assertEquals(1, response.jsonPath().getInt("totalElements"));
            assertEquals(created.jsonPath().getString("data.partyId"), response.jsonPath().getString("data[0].partyId"));
        }
    }

    @Test
    void packagedStrictBindingEmptyResultsAndCorruptionUseOnlySharedSafeContracts() {
        for (boolean prefix : List.of(false, true)) {
            String path = prefix ? IdentifierSearchContractAssertions.PREFIX_PATH : IdentifierSearchContractAssertions.EXACT_PATH;
            String field = prefix ? "prefix" : "value";
            for (String body : List.of("null", "{", "{}", "{\"identifierSchemeCode\":\"EC_NATIONAL_ID\",\"" + field + "\":\"17\",\"unknown\":true}")) {
                String process = UUID.randomUUID().toString();
                var response = given().contentType("application/json").header("Tenant-Id", UUID.randomUUID()).header("Process-Id", process)
                        .header("User-Id", "packaged-search-test").body(body).post(path);
                assertEquals(400, response.statusCode());
                assertEquals(process, response.header("Process-Id"));
                IdentifierSearchContractAssertions.assertErrorResponse(response.statusCode(), body.equals("null") ? "request-body-required" : "bad-request", response.asString());
            }
            var empty = send(prefix, UUID.randomUUID(), Map.of("identifierSchemeCode", PackagedIdentifierSearchResource.CODE, field, "historical"));
            if (prefix) assertions.assertPrefixResponse(empty.statusCode(), empty.asString(), 50);
            else assertions.assertExactResponse(empty.statusCode(), empty.asString());
            assertEquals(0, empty.jsonPath().getInt("totalElements"));
            var unknown = send(prefix, UUID.randomUUID(), Map.of("identifierSchemeCode", "ABSENT_SEARCH", field, "historical"));
            assertEquals(422, unknown.statusCode());
            IdentifierSearchContractAssertions.assertErrorResponse(unknown.statusCode(), "unknown-identifier-scheme", unknown.asString());
        }
        var corrupt = send(true, PackagedIdentifierSearchResource.CORRUPT_TENANT, Map.of("identifierSchemeCode", PackagedIdentifierSearchResource.CODE, "prefix", "historical"));
        assertEquals(500, corrupt.statusCode());
        IdentifierSearchContractAssertions.assertErrorResponse(corrupt.statusCode(), "server-error", corrupt.asString());
        var badLimit = send(true, PackagedIdentifierSearchResource.TENANT, Map.of("identifierSchemeCode", PackagedIdentifierSearchResource.CODE, "prefix", "historical", "limit", 1.0));
        assertEquals(400, badLimit.statusCode());
        IdentifierSearchContractAssertions.assertErrorResponse(badLimit.statusCode(), "bad-request", badLimit.asString());
    }

    @Test
    void sourcePackagedAndServedSearchSchemasAgreeAndPrivateDriftIsRejected() throws Exception {
        assertEquals(Files.readString(Path.of("docs/contracts/party-registry.openapi.yaml")), Files.readString(Path.of("build/resources/main/META-INF/openapi.yaml")));
        var options = new ParseOptions();
        options.setResolve(true);
        var parsed = new OpenAPIV3Parser().readContents(given().get("/q/openapi").asString(), null, options);
        assertNotNull(parsed.getOpenAPI());
        assertTrue(parsed.getMessages().isEmpty(), parsed.getMessages().toString());
        var served = parsed.getOpenAPI();
        for (String path : List.of(IdentifierSearchContractAssertions.EXACT_PATH, IdentifierSearchContractAssertions.PREFIX_PATH)) {
            assertEquals(contract.getPaths().get(path), served.getPaths().get(path));
        }
        for (String schema : List.of("IdentifierSearchExactRequest", "IdentifierSearchPrefixRequest", "IdentifierSearchApiResponse", "PartyIdentifierSearchItem", "PartyIdentifierSearchIdentifier")) {
            assertNotNull(served.getComponents().getSchemas().get(schema));
            assertEquals(contract.getComponents().getSchemas().get(schema), served.getComponents().getSchemas().get(schema));
        }
        var first = prefix(Map.of("limit", 2));
        new IdentifierSearchContractAssertions(served).assertPrefixResponse(first.statusCode(), first.asString(), 2);
        JsonNode leaked = json.readTree(first.asString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) leaked.path("data").get(0).path("identifier")).put("normalizedValue", "PrivateSelector");
        String leakedBody = json.writeValueAsString(leaked);
        assertThrows(AssertionError.class, () -> assertions.assertPrefixResponse(200, leakedBody, 2));
        for (String fixture : List.of("com.alexastudillo.partyregistry.api.resource.IdentifierSearchFaultReadPort",
                "com.alexastudillo.partyregistry.support.IdentifierSearchDatabaseFixture")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(fixture));
        }
    }

    private Response prefix(Map<String, Object> extra) {
        var body = new java.util.HashMap<String, Object>(extra);
        body.put("identifierSchemeCode", PackagedIdentifierSearchResource.CODE);
        body.put("prefix", " historical%_é ");
        var response = send(true, PackagedIdentifierSearchResource.TENANT, body);
        int limit = extra.containsKey("limit") ? Integer.class.cast(extra.get("limit")) : 50;
        assertions.assertPrefixResponse(response.statusCode(), response.asString(), limit);
        return response;
    }

    private static Response send(boolean prefix, UUID tenant, Map<String, Object> body) {
        String process = UUID.randomUUID().toString();
        var response = given().contentType("application/json").header("Tenant-Id", tenant).header("Process-Id", process)
                .header("User-Id", "packaged-search-test").body(body)
                .post(prefix ? IdentifierSearchContractAssertions.PREFIX_PATH : IdentifierSearchContractAssertions.EXACT_PATH);
        assertEquals(process, response.header("Process-Id"));
        return response;
    }

    /** Signs a historical token under the retained old test key, independently of the running packaged service's current key. */
    private record RetainedSigningKey() implements PartyPaginationConfiguration {
        @Override public Optional<String> currentSigningKeyId() { return Optional.of("test-v1"); }
        @Override public Map<String, String> signingKeys() { return Map.of("test-v1", PackagedIdentifierSearchResource.CURSOR_V1); }
    }
}
