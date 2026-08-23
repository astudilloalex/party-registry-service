package com.alexastudillo.partyregistry.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * Verifies trusted request context and operational endpoints over the running JVM application.
 */
@QuarkusTest
class ApiFoundationHttpTest {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String USER_ID = "service-account@example.internal";
    private static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";

    @Test
    void missingTrustedContextReturnsProtectedBadRequest() {
        given()
                .when().get("/api/v1/not-implemented")
                .then()
                .statusCode(400)
                .header("Process-Id", not(containsString(PROCESS_ID)))
                .body("status", equalTo(400))
                .body("code", equalTo("bad-request"))
                .body("data", equalTo(null));
    }

    @Test
    void acceptedProcessIdIsEchoedEvenWhenAnotherHeaderIsInvalid() {
        given()
                .header("Tenant-Id", TENANT_ID)
                .header("User-Id", " ")
                .header("Process-Id", PROCESS_ID)
                .when().get("/api/v1/not-implemented")
                .then()
                .statusCode(400)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(400))
                .body("code", equalTo("bad-request"));
    }

    @Test
    void validTrustedContextReachesStandardNotFoundMapping() {
        givenTrustedContext()
                .when().get("/api/v1/not-implemented")
                .then()
                .statusCode(404)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(404))
                .body("code", equalTo("not-found"))
                .body("data", equalTo(null));
    }

    @Test
    void repeatedTrustedHeaderIsRejected() {
        Headers headers = new Headers(
                new Header("Tenant-Id", TENANT_ID),
                new Header("Tenant-Id", UUID.randomUUID().toString()),
                new Header("User-Id", USER_ID),
                new Header("Process-Id", PROCESS_ID));

        given()
                .headers(headers)
                .when().get("/api/v1/not-implemented")
                .then()
                .statusCode(400)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("code", equalTo("bad-request"));
    }

    @Test
    void nonCanonicalUuidAndUnsafeUserIdAreRejected() {
        given()
                .header("Tenant-Id", "1-1-1-1-1")
                .header("User-Id", "unsafe\u202Eidentifier")
                .header("Process-Id", PROCESS_ID)
                .when().get("/api/v1/not-implemented")
                .then()
                .statusCode(400)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("code", equalTo("bad-request"));
    }

    @Test
    void managementEndpointsDoNotRequireTrustedHeaders() {
        given()
                .when().get("/q/health/live")
                .then()
                .statusCode(200);

        given()
                .when().get("/q/metrics")
                .then()
                .statusCode(200)
                .body(containsString("http_server_requests"));
    }

    @Test
    void approvedOpenApiIsExposedAsAnOperationalEndpoint() {
        given()
                .accept("application/yaml")
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .body(containsString("title: Party Registry Service API"))
                .body(containsString("openapi: 3.1.1"));
    }

    private static io.restassured.specification.RequestSpecification givenTrustedContext() {
        return given()
                .header("Tenant-Id", TENANT_ID)
                .header("User-Id", USER_ID)
                .header("Process-Id", PROCESS_ID);
    }
}
