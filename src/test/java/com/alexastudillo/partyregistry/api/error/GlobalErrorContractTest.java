package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.partyregistry.api.filter.RequestContextFilter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/**
 * Verifies shared global JSON envelopes for framework and unexpected failures.
 */
@QuarkusTest
@TestProfile(GlobalErrorContractTestProfile.class)
class GlobalErrorContractTest {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
    private static final String USER_ID = "global-error-contract-test";

    @Test
    void mapsMalformedJsonAndBeanValidationToBadRequest() {
        validRequest()
                .contentType(ContentType.JSON)
                .body("{")
                .when().post("/v1/error-verification/validation")
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("code", equalTo("bad-request"))
                .body("$", not(hasKey("data")));

        validRequest()
                .contentType(ContentType.JSON)
                .body("{\"givenNames\":\"  \",\"familyNames\":\"Lovelace\"}")
                .when().post("/v1/error-verification/validation")
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("code", equalTo("bad-request"))
                .body("$", not(hasKey("data")));
    }

    @Test
    void mapsUnsupportedMethodsThroughTheSharedHttpFallback() {
        validRequest()
                .when().put("/v1/error-verification/validation")
                .then()
                .statusCode(405)
                .body("status", equalTo(405))
                .body("code", equalTo("method-not-allowed"))
                .body("$", not(hasKey("data")));
    }

    @Test
    void mapsPlatformAuthenticationFailureToUnauthorized() {
        validRequest()
                .when().get("/v1/error-verification/authentication-failure")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("status", equalTo(401))
                .body("code", equalTo("unauthorized"))
                .body("$", not(hasKey("data")));
    }

    @Test
    void sanitizesUnexpectedFailures() {
        validRequest()
                .when().get("/v1/error-verification/unexpected")
                .then()
                .statusCode(500)
                .body("status", equalTo(500))
                .body("code", equalTo("server-error"))
                .body("$", not(hasKey("data")))
                .body(not(containsString("sensitive-database-detail")));
    }

    private static RequestSpecification validRequest() {
        return given()
                .header(RequestContextFilter.TENANT_ID_HEADER, TENANT_ID)
                .header(RequestContextFilter.USER_ID_HEADER, USER_ID)
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS_ID);
    }
}
