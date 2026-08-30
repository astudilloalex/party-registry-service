package com.alexastudillo.partyregistry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the cross-cutting configuration established before feature
 * implementation.
 */
@QuarkusTest
@TestProfile(FoundationConfigurationTest.ConfigurationOnlyProfile.class)
class FoundationConfigurationTest {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String USER_ID = "foundation-configuration-test";
    private static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Config config;

    @Test
    void rejectsUnknownJsonProperties() {
        assertTrue(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertThrows(
                UnrecognizedPropertyException.class,
                () -> objectMapper.readValue("{\"known\":\"value\",\"unsupported\":true}", StrictPayload.class));
    }

    @Test
    void scopesSharedHttpFailureHandlingToBusinessPaths() {
        given()
                .header("Tenant-Id", TENANT_ID)
                .header("User-Id", USER_ID)
                .header("Process-Id", PROCESS_ID)
                .when().get("/v1/not-implemented")
                .then()
                .statusCode(404)
                .body("status", equalTo(404))
                .body("code", equalTo("not-found"));

        given().when().get("/q/health/live").then().statusCode(200);
    }

    @Test
    void configuresFiniteGeographicReferenceTimeouts() {
        String connectTimeoutValue = config.getConfigValue(
                "quarkus.rest-client.geographic-reference.connect-timeout")
                .getValue();
        String readTimeoutValue = config.getConfigValue(
                "quarkus.rest-client.geographic-reference.read-timeout")
                .getValue();

        assertNotNull(connectTimeoutValue);
        assertNotNull(readTimeoutValue);
        assertTrue(Long.parseLong(connectTimeoutValue) > 0);
        assertTrue(Long.parseLong(readTimeoutValue) > 0);
    }

    @Test
    void requiresGeographicReferenceBaseUrlInProduction() throws IOException {
        Properties properties = loadApplicationProperties();

        assertEquals(
                "${GEOGRAPHIC_REFERENCE_BASE_URL}",
                properties.getProperty("%prod.quarkus.rest-client.geographic-reference.url"));
    }

    private static Properties loadApplicationProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = FoundationConfigurationTest.class.getResourceAsStream("/application.properties")) {
            assertNotNull(input);
            properties.load(input);
        }
        return properties;
    }

    /**
     * Provides a closed JSON target for strict deserialization verification.
     */
    private record StrictPayload(String known) {
    }

    /**
     * Disables database services that are unrelated to configuration-boundary
     * verification.
     */
    public static final class ConfigurationOnlyProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.datasource.devservices.enabled", "false",
                    "quarkus.datasource.health.enabled", "false",
                    "quarkus.flyway.migrate-at-start", "false",
                    "quarkus.hibernate-orm.enabled", "false");
        }
    }
}
