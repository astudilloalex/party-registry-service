package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.IdentifierSearchContractAssertions;
import com.alexastudillo.partyregistry.contract.IdentifierSchemeContractValidator;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies both actual HTTP search routes use strict input, trusted process echo and safe shared success/error contracts. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSearchResourceContractTest {
    private final IdentifierSearchContractAssertions assertions = new IdentifierSearchContractAssertions(
            IdentifierSchemeContractValidator.readContract(Path.of("docs/contracts/party-registry.openapi.yaml")));

    @Test
    void bothRoutesReturnExplicitEmptySharedEnvelopesAndAcceptedProcessEcho() {
        for (String path : paths()) {
            String process = UUID.randomUUID().toString();
            Response response = send(path, body(path), process);
            assertEquals(process, response.header("Process-Id"));
            if (path.equals(IdentifierSearchContractAssertions.EXACT_PATH)) assertions.assertExactResponse(response.statusCode(), response.asString());
            else assertions.assertPrefixResponse(response.statusCode(), response.asString(), 50);
        }
    }

    @Test
    void nullBodiesStrictInputAndUnknownSchemesHaveStableFailures() {
        for (String path : paths()) {
            String process = UUID.randomUUID().toString();
            var absent = send(path, "null", process);
            IdentifierSearchContractAssertions.assertErrorResponse(400, "request-body-required", absent.asString());
            assertEquals(process, absent.header("Process-Id"));
            var invalid = send(path, "{\"identifierSchemeCode\":\"EC_NATIONAL_ID\",\"unknown\":true}", process);
            IdentifierSearchContractAssertions.assertErrorResponse(400, "bad-request", invalid.asString());
            var unknown = send(path, body(path).replace("EC_NATIONAL_ID", "ABSENT_SEARCH_SCHEME"), process);
            IdentifierSearchContractAssertions.assertErrorResponse(422, "unknown-identifier-scheme", unknown.asString());
            var query = send(path + "?limit=1", body(path), process);
            IdentifierSearchContractAssertions.assertErrorResponse(400, "bad-request", query.asString());
        }
    }

    private static Response send(String path, String body, String process) {
        return given().contentType("application/json").header("Process-Id", process).header("Tenant-Id", UUID.randomUUID().toString())
                .header("User-Id", "search-contract-test").body(body).post(path);
    }

    private static List<String> paths() {
        return List.of(IdentifierSearchContractAssertions.EXACT_PATH, IdentifierSearchContractAssertions.PREFIX_PATH);
    }

    private static String body(String path) {
        String field = path.equals(IdentifierSearchContractAssertions.EXACT_PATH) ? "value" : "prefix";
        return "{\"identifierSchemeCode\":\"EC_NATIONAL_ID\",\"" + field + "\":\"17\"}";
    }
}
