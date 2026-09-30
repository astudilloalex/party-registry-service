package com.alexastudillo.partyregistry;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs root pagination, strict correction, lifecycle and historical snapshot contracts against packaged JVM/native executables. */
@QuarkusIntegrationTest
@QuarkusTestResource(value = PackagedGeographicReferenceResource.class, restrictToAnnotatedClass = true)
@Timeout(90)
class PackagedRootContractIT {

    private static final String ROOT = "/v1/parties";
    private static final String TENANT = "0198d5f0-0000-7000-8000-000000000100";
    private static final String PERSON = "0198d5f0-0000-7000-8000-000000000101";
    private static final String COMPANY = "0198d5f0-0000-7000-8000-000000000102";
    private static final String PROCESS = UUID.randomUUID().toString();

    @Test
    void navigatesScopedSummaryPagesWithExplicitTerminalAndEmptyDirections() {
        Response first = request().queryParam("limit", 1).get(ROOT);
        page(first, 2, 2, 1);
        assertEquals(COMPANY, first.jsonPath().getString("data[0].partyId"));
        assertNull(first.jsonPath().get("prevCursor"));
        String cursor = first.jsonPath().getString("nextCursor");
        Response last = request().queryParam("limit", 1).queryParam("cursor", cursor).get(ROOT);
        page(last, 2, 2, 1);
        assertEquals(PERSON, last.jsonPath().getString("data[0].partyId"));
        assertNull(last.jsonPath().get("nextCursor"));
        Response previous = request().queryParam("limit", 1).queryParam("cursor", last.jsonPath().getString("prevCursor")).get(ROOT);
        assertEquals(first.jsonPath().getMap("$"), previous.jsonPath().getMap("$"));
        error(request().queryParam("limit", 2).queryParam("cursor", cursor).get(ROOT), 400, "bad-request");

        Response empty = request().queryParam("displayNameContains", "absent-filter").get(ROOT);
        page(empty, 0, 0, 0);
        assertNull(empty.jsonPath().get("nextCursor"));
        assertNull(empty.jsonPath().get("prevCursor"));
    }

    @Test
    void replaysOriginalLifecycleResultsAfterArchivedCorrectionForBothSubtypes() {
        for (String id : List.of(PERSON, COMPANY)) {
            String path = ROOT + "/" + id;
            String detail = id.equals(PERSON) ? "naturalPersonDetails" : "legalEntityDetails";
            Map<String, Object> original = detail(request().get(path), detail, "DRAFT", 0);
            Map<String, Object> activated = detail(action(path, "activate", "0"), detail, "ACTIVE", 1);
            Map<String, Object> deactivated = detail(action(path, "deactivate", "1"), detail, "INACTIVE", 2);
            Map<String, Object> archived = detail(action(path, "archive", "2"), detail, "ARCHIVED", 3);
            Map<String, Object> corrected = detail(request().header("If-Match", "3").contentType("application/json")
                    .body(Map.of("displayName", "  Packaged Root Straße  ")).patch(path), detail, "ARCHIVED", 4);
            assertEquals("PACKAGED ROOT STRASSE", corrected.get("displayName"));
            assertEquals(original.get(detail), corrected.get(detail));
            assertEquals(original.get("createdAt"), corrected.get("createdAt"));
            assertEquals(activated, detail(action(path, "activate", "0"), detail, "ACTIVE", 1));
            assertEquals(deactivated, detail(action(path, "deactivate", "1"), detail, "INACTIVE", 2));
            assertEquals(archived, detail(action(path, "archive", "2"), detail, "ARCHIVED", 3));
            assertEquals(corrected, detail(request().get(path), detail, "ARCHIVED", 4));
        }
        page(request().queryParam("displayNameStartsWith", " packaged root ").queryParam("recordStatus", "ARCHIVED").get(ROOT), 2, 1, 2);
    }

    @Test
    void strictDeserializerAndFrameworkFailuresKeepTheExactSharedEnvelope() {
        String path = ROOT + "/" + UUID.randomUUID();
        error(request().contentType("application/json").patch(path), 400, "request-body-required");
        for (var invalid : Map.of("null", "request-body-required", "{}", "display-name-required",
                "{\"displayName\":null}", "display-name-required", "{\"displayName\":42}", "bad-request",
                "{\"displayName\":\"A\",\"displayName\":\"B\"}", "bad-request",
                "{\"displayName\":\"Valid\",\"recordStatus\":\"ACTIVE\"}", "bad-request", "{", "bad-request").entrySet()) {
            error(request().contentType("application/json").body(invalid.getKey()).patch(path), 400, invalid.getValue());
        }
        error(request().contentType("application/json").body(Map.of("displayName", "ß".repeat(151))).patch(path), 400, "display-name-too-long");
        error(request().contentType("text/plain").body("text").patch(path), 415, "unsupported-media-type");
        error(request().delete(path), 405, "method-not-allowed");
        error(request().get(path + "/unknown"), 404, "not-found");
        error(request().get(path), 404, "party-not-found");
    }

    private static Response action(String path, String action, String version) {
        return request().header("If-Match", version).header("Idempotency-Key", path + ":" + action).post(path + "/" + action);
    }

    @Test
    void publishedRootOpenApiMatchesApprovedPathsSchemasHeadersAndErrorExamples() {
        var parser = new OpenAPIV3Parser();
        var source = parser.readLocation(Path.of("docs/contracts/party-registry.openapi.yaml").toString(), null, null);
        Response response = given().get("/q/openapi");
        assertEquals(200, response.statusCode());
        var published = parser.readContents(response.asString(), null, null);
        assertNotNull(source.getOpenAPI());
        assertNotNull(published.getOpenAPI());
        assertTrue(source.getMessages().isEmpty(), source.getMessages().toString());
        assertTrue(published.getMessages().isEmpty(), published.getMessages().toString());
        for (String path : List.of(ROOT, ROOT + "/{partyId}", ROOT + "/{partyId}/activate", ROOT + "/{partyId}/deactivate", ROOT + "/{partyId}/archive")) {
            assertEquals(source.getOpenAPI().getPaths().get(path), published.getOpenAPI().getPaths().get(path), path);
        }
        var expected = source.getOpenAPI().getComponents();
        var actual = published.getOpenAPI().getComponents();
        for (String schema : List.of("PartySummary", "PartyCollectionApiResponse", "PartyDetailResponse", "PartyDetailApiResponse", "PartyUpdateRequest", "ApiErrorResponse")) {
            assertEquals(expected.getSchemas().get(schema), actual.getSchemas().get(schema), schema);
        }
        for (String parameter : List.of("TenantId", "UserId", "ProcessId", "PartyIdPath", "PartyIfMatch", "PartyLifecycleIfMatch", "PartyLifecycleIdempotencyKey")) {
            assertEquals(expected.getParameters().get(parameter), actual.getParameters().get(parameter), parameter);
        }
        for (String error : List.of("BadRequest", "PartyNotFound", "PartyExpectedVersionMismatch", "PartyStaleVersion", "PartyBlankDisplayName", "PartyMissingActivationEvidence", "PartyLifecycleConflict", "DefaultError")) {
            assertEquals(expected.getResponses().get(error), actual.getResponses().get(error), error);
        }
    }

    private static RequestSpecification request() {
        return given().header("Tenant-Id", TENANT).header("Process-Id", PROCESS).header("User-Id", "packaged-root-operator");
    }

    private static Map<String, Object> detail(Response response, String property, String state, int version) {
        success(response);
        assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
        Map<String, Object> data = response.jsonPath().getMap("data");
        assertEquals(Set.of("partyId", "type", "displayName", "recordStatus", "version", "createdAt", "updatedAt", "createdBy", "updatedBy", property), data.keySet());
        assertEquals(state, data.get("recordStatus"));
        assertEquals(version, data.get("version"));
        assertFalse(response.asString().contains("ciphertext"));
        return data;
    }

    private static void page(Response response, int total, int pages, int size) {
        success(response);
        assertEquals(Set.of("status", "code", "data", "totalElements", "totalPages", "numberOfElements", "nextCursor", "prevCursor"), response.jsonPath().getMap("$").keySet());
        assertEquals(total, response.jsonPath().getInt("totalElements"));
        assertEquals(pages, response.jsonPath().getInt("totalPages"));
        assertEquals(size, response.jsonPath().getInt("numberOfElements"));
        List<Map<String, Object>> items = response.jsonPath().getList("data");
        assertEquals(size, items.size());
        items.forEach(item -> assertEquals(Set.of("partyId", "type", "displayName", "recordStatus", "createdAt", "version"), item.keySet()));
    }

    private static void success(Response response) {
        assertEquals(200, response.statusCode(), response.asString());
        assertEquals(200, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(PROCESS, response.header("Process-Id"));
    }

    private static void error(Response response, int status, String code) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(PROCESS, response.header("Process-Id"));
    }
}
