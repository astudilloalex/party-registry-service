package com.alexastudillo.partyregistry.api.error;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies exact shared identifier error envelopes and process echoes through the actual reactive HTTP handler. */
@QuarkusTest
@TestProfile(GlobalErrorContractTestProfile.class)
class PartyIdentifierErrorContractTest {

    @Test
    void knownIdentifierFailuresAndUnknownInvariantsUseOnlyStatusAndCode() {
        Map<String, Integer> statuses = Map.of("absent", 404, "version", 412, "lifecycle", 409, "primary", 409,
                "exhausted", 409, "cursor", 400, "validity", 422, "unknown", 500);
        Map<String, String> codes = Map.of("absent", "identifier-not-found", "version", "expected-version-mismatch",
                "lifecycle", "invalid-identifier-lifecycle", "primary", "primary-identifier-conflict",
                "exhausted", "identifier-version-exhausted", "cursor", "bad-request",
                "validity", "identifier-validation-failure", "unknown", "server-error");
        String process = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
        statuses.forEach((kind, status) -> {
            var response = given().header("Tenant-Id", "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1")
                    .header("User-Id", "identifier-error-test").header("Process-Id", process)
                    .get("/v1/identifier-error-verification/" + kind);
            assertEquals(status.intValue(), response.statusCode());
            assertEquals(Map.of("status", status, "code", codes.get(kind)), response.jsonPath().getMap("$"));
            assertEquals(process, response.header("Process-Id"));
        });
    }
}
