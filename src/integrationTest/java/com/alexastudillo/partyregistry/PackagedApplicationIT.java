package com.alexastudillo.partyregistry;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/**
 * Verifies packaged JVM and native artifacts and their cross-cutting HTTP
 * behavior.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(value = PackagedGeographicReferenceResource.class, restrictToAnnotatedClass = true)
class PackagedApplicationIT {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String USER_ID = "packaged-integration-test";
    private static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
    private static final String JSON = "application/json";

    @Test
    void packagedApplicationStartsWithHealthMetricsAndOpenApi() {
        given().when().get("/q/health/live").then().statusCode(200);
        given().when().get("/q/metrics").then().statusCode(200);
        given().when().get("/q/openapi").then()
                .statusCode(200)
                .body(containsString("Party Management API"));
    }

    @Test
    void packagedApplicationExecutesNaturalPersonSmokeFlow() {
        Response created = validRequest()
                .header("Idempotency-Key", "packaged-smoke-create")
                .contentType(JSON)
                .body("""
                        {
                          "givenNames": "Packaged",
                          "familyNames": "Person",
                          "birthCountryCode": "EC"
                        }
                        """)
                .when().post("/v1/natural-person")
                .then()
                .statusCode(201)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(201))
                .body("code", equalTo("successful"))
                .body("data.type", equalTo("NATURAL_PERSON"))
                .body("data.version", equalTo(0))
                .extract().response();

        String partyId = created.path("data.partyId");
        validRequest()
                .when().get("/v1/natural-person/{partyId}", partyId)
                .then()
                .statusCode(200)
                .body("status", equalTo(200))
                .body("code", equalTo("successful"))
                .body("data.partyId", equalTo(partyId));

        validRequest()
                .header("If-Match", "0")
                .contentType(JSON)
                .body("""
                        {
                          "givenNames": "Updated",
                          "familyNames": "Person",
                          "birthCountryCode": "EC"
                        }
                        """)
                .when().put("/v1/natural-person/{partyId}", partyId)
                .then()
                .statusCode(200)
                .body("status", equalTo(200))
                .body("code", equalTo("successful"))
                .body("data.displayName", equalTo("Updated Person"))
                .body("data.version", equalTo(1));

        validRequest()
                .header("If-Match", "1")
                .contentType(JSON)
                .body("{\"preferredName\":\"Smoke\"}")
                .when().patch("/v1/natural-person/{partyId}", partyId)
                .then()
                .statusCode(200)
                .body("status", equalTo(200))
                .body("code", equalTo("successful"))
                .body("data.naturalPersonDetails.preferredName", equalTo("Smoke"))
                .body("data.version", equalTo(2));

        assertError(
                validRequest()
                        .header("Idempotency-Key", "packaged-invalid")
                        .contentType(JSON)
                        .body("{}")
                        .when().post("/v1/natural-person"),
                400,
                "bad-request");
        assertError(
                validRequest().when().get(
                        "/v1/natural-person/00000000-0000-7000-8000-000000000999"),
                404,
                "not-found");
        assertError(
                validRequest().when().delete("/v1/natural-person/{partyId}", partyId),
                405,
                "method-not-allowed");
        assertError(
                validRequest()
                        .header("Idempotency-Key", "packaged-dependency-failure")
                        .contentType(JSON)
                        .body("""
                                {
                                  "givenNames": "Dependency",
                                  "familyNames": "Failure",
                                  "birthCountryCode": "SE"
                                }
                                """)
                        .when().post("/v1/natural-person"),
                503,
                "dependency-unavailable");
    }

    private static RequestSpecification validRequest() {
        return given()
                .header("Tenant-Id", TENANT_ID)
                .header("User-Id", USER_ID)
                .header("Process-Id", PROCESS_ID);
    }

    private static void assertError(Response response, int status, String code) {
        response.then()
                .statusCode(status)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(status))
                .body("code", equalTo(code))
                .body("$", not(hasKey("data")));
    }
}
