package com.alexastudillo.partyregistry.api.filter;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies trusted request-context validation and lifecycle behavior over HTTP.
 */
@QuarkusTest
@TestProfile(RequestContextFilterProfile.class)
class RequestContextFilterContractTest {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
    private static final String USER_ID = "request-context-test";

    @AfterEach
    void clearTestMdc() {
        MDC.clear();
    }

    @Test
    void acceptsOneValidContextAndEchoesTheProcessIdentifier() {
        validRequest()
                .when().get("/v1/not-implemented")
                .then()
                .statusCode(404)
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS_ID)
                .body("status", equalTo(404))
                .body("code", equalTo("not-found"));
    }

    @Test
    void rejectsMissingAndDuplicateHeaders() {
        given()
                .header(RequestContextFilter.USER_ID_HEADER, USER_ID)
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS_ID)
                .when().get("/v1/not-implemented")
                .then()
                .statusCode(400)
                .body("code", equalTo("bad-request"));

        given()
                .header(RequestContextFilter.TENANT_ID_HEADER, TENANT_ID, TENANT_ID)
                .header(RequestContextFilter.USER_ID_HEADER, USER_ID)
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS_ID)
                .when().get("/v1/not-implemented")
                .then()
                .statusCode(400)
                .body("code", equalTo("bad-request"));
    }

    @Test
    void rejectsMalformedCanonicalIdentifiersAndControlsProcessEcho() {
        given()
                .header(RequestContextFilter.TENANT_ID_HEADER, TENANT_ID.toUpperCase())
                .header(RequestContextFilter.USER_ID_HEADER, USER_ID)
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS_ID)
                .when().get("/v1/not-implemented")
                .then()
                .statusCode(400)
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS_ID)
                .body("code", equalTo("bad-request"));

        given()
                .header(RequestContextFilter.TENANT_ID_HEADER, TENANT_ID)
                .header(RequestContextFilter.USER_ID_HEADER, USER_ID)
                .header(RequestContextFilter.PROCESS_ID_HEADER, "not-a-uuid")
                .when().get("/v1/not-implemented")
                .then()
                .statusCode(400)
                .header(RequestContextFilter.PROCESS_ID_HEADER, nullValue())
                .body("code", equalTo("bad-request"));
    }

    @Test
    void rejectsBlankAndOversizedUsersOverHttp() {
        assertInvalidUser("   ");
        assertInvalidUser("a".repeat(129));
    }

    @Test
    void rejectsUnsafeUserControlCharactersBeforeMetadataCanBeCreated() {
        TenantId tenantId = new TenantId(UUID.fromString(TENANT_ID));
        UUID processId = UUID.fromString(PROCESS_ID);

        assertThrows(
                IllegalArgumentException.class,
                () -> new RequestMetadata(tenantId, "unsafe\u0001user", processId));
    }

    @Test
    void excludesManagementPaths() {
        given().when().get("/q/health/live").then().statusCode(200);
    }

    @Test
    void clearsOnlyOwnedMdcKeys() {
        MDC.put("processId", PROCESS_ID);
        MDC.put("userId", USER_ID);
        MDC.put("tenantId", TENANT_ID);
        MDC.put("traceId", "retained");

        RequestContextFilter.clearOwnedMdc();

        assertNull(MDC.get("processId"));
        assertNull(MDC.get("userId"));
        assertNull(MDC.get("tenantId"));
        assertEquals("retained", MDC.get("traceId"));
    }

    private static RequestSpecification validRequest() {
        return given()
                .header(RequestContextFilter.TENANT_ID_HEADER, TENANT_ID)
                .header(RequestContextFilter.USER_ID_HEADER, USER_ID)
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS_ID);
    }

    private static void assertInvalidUser(String userId) {
        given()
                .header(RequestContextFilter.TENANT_ID_HEADER, TENANT_ID)
                .header(RequestContextFilter.USER_ID_HEADER, userId)
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS_ID)
                .when().get("/v1/not-implemented")
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("code", equalTo("bad-request"));
    }

}
