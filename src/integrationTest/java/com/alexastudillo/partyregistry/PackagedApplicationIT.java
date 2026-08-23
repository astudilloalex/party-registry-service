package com.alexastudillo.partyregistry;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Verifies packaged JVM and native artifacts and their cross-cutting HTTP behavior.
 */
@QuarkusIntegrationTest
class PackagedApplicationIT {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String USER_ID = "packaged-integration-test";
    private static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";

    @Test
    void packagedApplicationStartsWithHealthMetricsAndOpenApi() {
        given().when().get("/q/health/live").then().statusCode(200);
        given().when().get("/q/metrics").then().statusCode(200);
        given().when().get("/q/openapi").then()
                .statusCode(200)
                .body(containsString("Party Registry Service API"));
    }

    @Test
    void packagedApplicationEnforcesTheTrustedContextContract() {
        given()
                .header("Tenant-Id", TENANT_ID)
                .header("User-Id", USER_ID)
                .header("Process-Id", PROCESS_ID)
                .when().get("/api/v1/not-implemented")
                .then()
                .statusCode(404)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(404))
                .body("code", equalTo("not-found"));
    }
}
