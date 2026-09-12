package com.alexastudillo.partyregistry.api.filter;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        for (String header : List.of("Process-Id", "Tenant-Id", "User-Id")) {
            String prefix = header.toLowerCase(Locale.ROOT);
            String echo = header.equals("Process-Id") ? null : PROCESS_ID;
            assertRejection(requestWithout(header), prefix + "-required", echo);
            assertRejection(requestWithout(header).header(header, "SensitiveOne", "SensitiveTwo"),
                    prefix + "-duplicated", echo);
        }
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
                .body("status", equalTo(400))
                .body("code", equalTo("tenant-id-invalid"));

        given()
                .header(RequestContextFilter.TENANT_ID_HEADER, TENANT_ID)
                .header(RequestContextFilter.USER_ID_HEADER, USER_ID)
                .header(RequestContextFilter.PROCESS_ID_HEADER, "not-a-uuid")
                .when().get("/v1/not-implemented")
                .then()
                .statusCode(400)
                .header(RequestContextFilter.PROCESS_ID_HEADER, nullValue())
                .body("status", equalTo(400))
                .body("code", equalTo("process-id-invalid"));
    }

    @Test
    void rejectsBlankAndOversizedUsersOverHttp() {
        assertRejection(requestWithout("User-Id").header("User-Id", "   "),
                "user-id-blank", PROCESS_ID);
        assertRejection(requestWithout("User-Id").header("User-Id", "a".repeat(129)),
                "user-id-too-long", PROCESS_ID);
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

    @Test
    void acceptsCaseInsensitiveHeaderNamesAndPreservesRejectionPrecedence() {
        given().header("tenant-id", TENANT_ID).header("process-id", PROCESS_ID).header("user-id", USER_ID)
                .get("/v1/not-implemented").then().statusCode(404).header("Process-Id", PROCESS_ID);
        assertRejection(given(), "process-id-required", null);
        assertRejection(given().header("Process-Id", PROCESS_ID), "tenant-id-required", PROCESS_ID);
        assertRejection(requestWithout("Process-Id").header("Process-Id", "{{SensitiveProcess}}"),
                "process-id-invalid", null);
        validRequest().get("/v1/not-implemented").then().statusCode(404).header("Process-Id", PROCESS_ID);
    }

    private static void assertRejection(RequestSpecification request, String expectedCode, String expectedEcho) {
        var response = request.get("/v1/not-implemented");
        assertEquals(400, response.statusCode());
        assertEquals(expectedEcho, response.header("Process-Id"));
        assertEquals(Map.of("status", 400, "code", expectedCode), response.jsonPath().getMap("$"));
    }

    private static RequestSpecification requestWithout(String excludedHeader) {
        RequestSpecification request = given();
        Map.of("Tenant-Id", TENANT_ID, "Process-Id", PROCESS_ID, "User-Id", USER_ID).forEach((header, value) -> {
            if (!header.equals(excludedHeader)) {
                request.header(header, value);
            }
        });
        return request;
    }

}
