package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.api.filter.RequestContextFilter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.vertx.VertxContextSupport;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies legal registration, additional identifiers, and activation at the HTTP boundary.
 */
@QuarkusTest
class PartyRegistrationResourceContractTest {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
    private static final String USER_ID = "geographic-reference-adapter-test";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IF_MATCH_HEADER = "If-Match";
    private static final Duration MAXIMUM_WAIT = Duration.ofSeconds(10);

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    void registersAndReplaysALegalEntityWithASafeInitialIdentifier() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        String identifierValue = identifierValue();
        String key = key("legal");
        String body = legalBody("Analytical Engines Ltd", identifierValue);

        Map<String, Object> created = assertSuccess(post(
                tenantId,
                "/v1/legal-entity",
                key,
                body), 201);
        Map<String, Object> replayed = assertSuccess(post(
                tenantId,
                "/v1/legal-entity",
                key,
                body), 201);

        assertEquals(created, replayed);
        assertEquals("LEGAL_ENTITY", created.get("type"));
        assertEquals("DRAFT", created.get("recordStatus"));
        assertEquals("Analytical Engines Ltd", nested(created, "legalEntityDetails").get("legalName"));
        Map<String, Object> identifier = nested(created, "initialIdentifier");
        assertEquals(created.get("partyId"), identifier.get("partyId"));
        assertEquals("TEST_LEGAL_ACTIVE", identifier.get("schemeCode"));
        assertEquals("PENDING_VERIFICATION", identifier.get("status"));
        assertFalse(identifier.containsKey("value"));
        assertFalse(post(tenantId, "/v1/legal-entity", key, legalBody("Changed Ltd", identifierValue))
                .asString().contains(identifierValue));
        assertError(
                post(tenantId, "/v1/legal-entity", key, legalBody("Changed Ltd", identifierValue)),
                409,
                "conflict");
    }

    @Test
    void registersAnAdditionalIdentifierWithoutChangingPartyIdentityOrType() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        Map<String, Object> party = createNaturalPerson(tenantId, identifierValue());
        String partyId = string(party, "partyId");
        String additionalValue = identifierValue();
        String body = identifierBody("TEST_NATURAL_ACTIVE", additionalValue);

        Map<String, Object> identifier = assertSuccess(post(
                tenantId,
                "/v1/parties/" + partyId + "/identifiers",
                key("additional"),
                body), 201);

        assertEquals(partyId, identifier.get("partyId"));
        assertEquals("PENDING_VERIFICATION", identifier.get("status"));
        assertFalse(identifier.containsKey("value"));
        assertFalse(identifier.toString().contains(additionalValue));
        assertError(
                post(
                        tenantId,
                        "/v1/parties/" + partyId + "/identifiers",
                        key("duplicate-additional"),
                        body),
                409,
                "conflict");
        Map<String, Object> retrieved = assertSuccess(
                request(tenantId).get("/v1/natural-person/" + partyId), 200);
        assertEquals(partyId, retrieved.get("partyId"));
        assertEquals("NATURAL_PERSON", retrieved.get("type"));

        assertError(
                request(tenantId)
                        .body(body)
                        .post("/v1/parties/" + partyId + "/identifiers"),
                400,
                "bad-request");
        assertError(
                post(
                        UUID.randomUUID(),
                        "/v1/parties/" + partyId + "/identifiers",
                        key("cross-tenant"),
                        identifierBody("TEST_NATURAL_ACTIVE", identifierValue())),
                404,
                "not-found");
        assertError(
                post(
                        tenantId,
                        "/v1/parties/" + partyId + "/identifiers",
                        key("unknown-scheme"),
                        identifierBody("UNKNOWN_SCHEME", identifierValue())),
                422,
                "unprocessable-entity");
    }

    @Test
    void activatesOnlyWithExactVersionAndQualifyingVerifiedEvidence() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        Map<String, Object> party = createNaturalPerson(tenantId, identifierValue());
        String partyId = string(party, "partyId");
        String path = "/v1/parties/" + partyId + "/activate";

        assertError(request(tenantId).post(path), 400, "bad-request");
        assertError(request(tenantId).header(IF_MATCH_HEADER, "01").post(path), 400, "bad-request");
        assertError(
                request(tenantId).header(IF_MATCH_HEADER, "0", "0").post(path),
                400,
                "bad-request");
        assertError(request(tenantId).header(IF_MATCH_HEADER, "0").post(path), 422, "unprocessable-entity");

        markIdentifierVerified(UUID.fromString(partyId));

        assertError(
                request(UUID.randomUUID()).header(IF_MATCH_HEADER, "0").post(path),
                404,
                "not-found");
        Map<String, Object> activated = assertSuccess(
                request(tenantId)
                        .header(IF_MATCH_HEADER, "0")
                        .header(IDEMPOTENCY_KEY_HEADER, key("activation"))
                        .post(path),
                200);
        assertEquals(partyId, activated.get("partyId"));
        assertEquals("NATURAL_PERSON", activated.get("type"));
        assertEquals("ACTIVE", activated.get("recordStatus"));
        assertEquals(1, ((Number) activated.get("version")).intValue());
        assertNotNull(activated.get("naturalPersonDetails"));
        assertFalse(activated.containsKey("legalEntityDetails"));
        assertError(request(tenantId).header(IF_MATCH_HEADER, "0").post(path), 412, "precondition-failed");
        assertError(request(tenantId).header(IF_MATCH_HEADER, "1").post(path), 409, "conflict");
    }

    private Map<String, Object> createNaturalPerson(UUID tenantId, String identifierValue) {
        return assertSuccess(post(
                tenantId,
                "/v1/natural-person",
                key("natural"),
                """
                        {
                          "givenNames": "Ada",
                          "familyNames": "Lovelace",
                          "birthCountryCode": "EC",
                          "initialIdentifier": {
                            "identifierSchemeCode": "TEST_NATURAL_ACTIVE",
                            "value": "%s",
                            "isPrimary": true
                          }
                        }
                        """.formatted(identifierValue)), 201);
    }

    private void markIdentifierVerified(UUID partyId) {
        awaitReactive(() -> sessionFactory.withTransaction((session, transaction) -> session
                .createNativeQuery("""
                        update party_identifiers
                        set status = 'VERIFIED',
                            verified_at = current_timestamp,
                            verified_by = :verifiedBy,
                            version = version + 1
                        where party_id = :partyId
                        """)
                .setParameter("verifiedBy", USER_ID)
                .setParameter("partyId", partyId)
                .executeUpdate()
                .invoke(updated -> assertEquals(1, updated))
                .replaceWithVoid()));
    }

    private <T> T awaitReactive(Supplier<Uni<T>> operation) {
        try {
            return VertxContextSupport.subscribeAndAwait(
                    () -> operation.get().ifNoItem().after(MAXIMUM_WAIT).fail());
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new AssertionError("Reactive test operation failed", failure);
        }
    }

    private static Response post(UUID tenantId, String path, String idempotencyKey, String body) {
        return request(tenantId)
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .body(body)
                .post(path);
    }

    private static RequestSpecification request(UUID tenantId) {
        return given()
                .contentType(ContentType.JSON)
                .header(RequestContextFilter.TENANT_ID_HEADER, tenantId.toString())
                .header(RequestContextFilter.USER_ID_HEADER, USER_ID)
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS_ID);
    }

    private static Map<String, Object> assertSuccess(Response response, int expectedStatus) {
        assertEquals(expectedStatus, response.statusCode());
        assertEquals(PROCESS_ID, response.header(RequestContextFilter.PROCESS_ID_HEADER));
        assertEquals(expectedStatus, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        Map<String, Object> data = response.jsonPath().getMap("data");
        assertNotNull(data);
        return data;
    }

    private static void assertError(Response response, int expectedStatus, String expectedCode) {
        assertEquals(expectedStatus, response.statusCode());
        assertEquals(PROCESS_ID, response.header(RequestContextFilter.PROCESS_ID_HEADER));
        assertEquals(expectedStatus, response.jsonPath().getInt("status"));
        assertEquals(expectedCode, response.jsonPath().getString("code"));
        assertNull(response.jsonPath().get("data"));
        assertFalse(response.asString().toLowerCase().contains("exception"));
    }

    private static String legalBody(String legalName, String identifierValue) {
        return """
                {
                  "legalName": "%s",
                  "incorporationCountryCode": "EC",
                  "initialIdentifier": {
                    "identifierSchemeCode": "TEST_LEGAL_ACTIVE",
                    "value": "%s",
                    "isPrimary": true
                  }
                }
                """.formatted(legalName, identifierValue);
    }

    private static String identifierBody(String schemeCode, String identifierValue) {
        return """
                {
                  "identifierSchemeCode": "%s",
                  "value": "%s"
                }
                """.formatted(schemeCode, identifierValue);
    }

    private static String identifierValue() {
        return "ID" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String key(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static String string(Map<String, Object> values, String key) {
        return (String) values.get(key);
    }

    private static Map<String, Object> nested(Map<String, Object> values, String key) {
        Object nested = values.get(key);
        assertTrue(nested instanceof Map<?, ?>);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) nested).entrySet()) {
            assertTrue(entry.getKey() instanceof String);
            result.put((String) entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }
}
