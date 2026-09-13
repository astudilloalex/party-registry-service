package com.alexastudillo.partyregistry;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies packaged JVM and native artifacts and their cross-cutting HTTP
 * behavior.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(value = PackagedGeographicReferenceResource.class, restrictToAnnotatedClass = true)
@Timeout(90)
class PackagedApplicationIT {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String USER_ID = "packaged-integration-test";
    private static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
    private static final String JSON = "application/json";
    private static final String ACTIVATION_FIXTURE_PARTY_ID = "0198d5f0-0000-7000-8000-000000000001";

    @Test
    void packagedApplicationStartsWithHealthMetricsAndOpenApi() {
        given().when().get("/q/health/live").then().statusCode(200);
        given().when().get("/q/metrics").then().statusCode(200);
        given().when().get("/q/openapi").then()
                .statusCode(200)
                .body(containsString("Party Management API"));
    }

    @Test
    void packagedApplicationExplainsContextHeaderFailuresBeforeBodyValidation() {
        Response missingTenant = given().header("Process-Id", PROCESS_ID).header("User-Id", USER_ID)
                .contentType(JSON).body("{}").post("/v1/natural-person");
        assertError(missingTenant, 400, "tenant-id-required");
        assertEquals(Set.of("status", "code"), missingTenant.jsonPath().getMap("$").keySet());

        Response invalidProcess = given().header("Tenant-Id", TENANT_ID).header("User-Id", USER_ID)
                .header("Process-Id", "{{SensitiveProcess}}")
                .contentType(JSON).body("{}").post("/v1/natural-person");
        assertEquals(400, invalidProcess.statusCode());
        assertNull(invalidProcess.header("Process-Id"));
        assertEquals(Map.of("status", 400, "code", "process-id-invalid"),
                invalidProcess.jsonPath().getMap("$"));
        invalidProcess.then().body(not(containsString("Sensitive")));
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
                          "birthCountryCode": "EC",
                          "initialIdentifier": {
                            "identifierSchemeCode": "TEST_NATURAL_ACTIVE",
                            "value": "PACKAGED123456",
                            "isPrimary": true
                          }
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
                .body("data.initialIdentifier.schemeCode", equalTo("TEST_NATURAL_ACTIVE"))
                .body("data.initialIdentifier.status", equalTo("PENDING_VERIFICATION"))
                .body("data.initialIdentifier", not(hasKey("value")))
                .body("data", not(hasKey("identifiers")))
                .extract().response();

        String partyId = created.path("data.partyId");
        Response detail = validRequest()
                .when().get("/v1/natural-person/{partyId}", partyId)
                .then()
                .statusCode(200)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(200))
                .body("code", equalTo("successful"))
                .body("data.partyId", equalTo(partyId))
                .body("data.type", equalTo("NATURAL_PERSON"))
                .body("data", not(hasKey("initialIdentifier")))
                .body("data.identifiers.size()", equalTo(1))
                .body("data.identifiers[0].identifierId", equalTo(created.path("data.initialIdentifier.identifierId")))
                .body("data.identifiers[0].partyId", equalTo(partyId))
                .body("data.identifiers[0].identifierSchemeId",
                        equalTo(created.path("data.initialIdentifier.identifierSchemeId")))
                .body("data.identifiers[0].schemeCode", equalTo("TEST_NATURAL_ACTIVE"))
                .body("data.identifiers[0].maskedValue", equalTo("*********3456"))
                .body("data.identifiers[0].status", equalTo("PENDING_VERIFICATION"))
                .body("data.identifiers[0].isPrimary", equalTo(true))
                .body("data.identifiers[0].version", equalTo(0))
                .body(not(containsString("PACKAGED123456")))
                .extract().response();
        assertEquals(Set.of("status", "code", "data"), detail.jsonPath().getMap("$").keySet());
        assertEquals(Set.of("partyId", "type", "displayName", "recordStatus", "version", "createdAt",
                "updatedAt", "createdBy", "updatedBy", "naturalPersonDetails", "identifiers"),
                detail.jsonPath().getMap("data").keySet());
        assertEquals(Set.of("identifierId", "partyId", "identifierSchemeId", "schemeCode", "maskedValue",
                "status", "isPrimary", "issuerCode", "issuedOn", "expiresOn", "verifiedAt", "verifiedBy",
                "version", "createdAt", "updatedAt"), detail.jsonPath().getMap("data.identifiers[0]").keySet());
        for (String forbidden : List.of("value", "normalizedValue", "encryptedValue", "normalizedValueHash",
                "encryptionKeyVersion", "normalizationVersion", "plaintext", "ciphertext", "fingerprint", "keys")) {
            detail.then().body(not(containsString("\"" + forbidden + "\"")));
        }

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
                .body("data.displayName", equalTo("UPDATED PERSON"))
                .body("data", not(hasKey("identifiers")))
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
                .body("data.naturalPersonDetails.preferredName", equalTo("SMOKE"))
                .body("data", not(hasKey("identifiers")))
                .body("data.version", equalTo(2));

        assertError(
                validRequest()
                        .header("Idempotency-Key", "packaged-invalid")
                        .contentType(JSON)
                        .body("{}")
                        .when().post("/v1/natural-person"),
                400,
                "family-names-required");
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
                                  "birthCountryCode": "SE",
                                  "initialIdentifier": {
                                    "identifierSchemeCode": "TEST_NATURAL_ACTIVE",
                                    "value": "DEPENDENCY123456"
                                  }
                                }
                                """)
                        .when().post("/v1/natural-person"),
                503,
                "dependency-unavailable");
    }

    @Test
    void packagedApplicationNormalizesAllNaturalAndLegalTextFieldsOnWrites() {
        Response created = validRequest()
                .header("Idempotency-Key", "packaged-normalized-natural-" + UUID.randomUUID())
                .contentType(JSON)
                .body("""
                        {
                          "displayName": "  native person  ",
                          "givenNames": "  native  ",
                          "familyNames": "  person  ",
                          "preferredName": "  smoke  ",
                          "birthCountryCode": "  ec  ",
                          "birthDate": "1900-01-02",
                          "dateOfDeath": "2000-03-04",
                          "initialIdentifier": {
                            "identifierSchemeCode": "TEST_NATURAL_ACTIVE",
                            "value": "%s"
                          }
                        }
                        """.formatted(identifierValue("NN")))
                .post("/v1/natural-person")
                .then().statusCode(201)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(201))
                .body("code", equalTo("successful"))
                .body("data.displayName", equalTo("NATIVE PERSON"))
                .body("data.naturalPersonDetails", equalTo(Map.of(
                        "givenNames", "NATIVE", "familyNames", "PERSON", "preferredName", "SMOKE",
                        "birthCountryCode", "EC", "birthDate", "1900-01-02", "dateOfDeath", "2000-03-04")))
                .extract().response();
        assertEquals(Set.of("status", "code", "data"), created.jsonPath().getMap("$").keySet());
        String partyId = created.path("data.partyId");

        validRequest().header("If-Match", "0").contentType(JSON)
                .body("""
                        {
                          "givenNames": "  replaced  ",
                          "familyNames": "  person  ",
                          "preferredName": "  replacement  ",
                          "birthCountryCode": "  eC  ",
                          "birthDate": "1901-02-03",
                          "dateOfDeath": "2001-04-05"
                        }
                        """)
                .put("/v1/natural-person/{partyId}", partyId)
                .then().statusCode(200)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(200))
                .body("code", equalTo("successful"))
                .body("data.version", equalTo(1))
                .body("data.displayName", equalTo("REPLACED PERSON"))
                .body("data.naturalPersonDetails", equalTo(Map.of(
                        "givenNames", "REPLACED", "familyNames", "PERSON", "preferredName", "REPLACEMENT",
                        "birthCountryCode", "EC", "birthDate", "1901-02-03", "dateOfDeath", "2001-04-05")));

        validRequest().header("If-Match", "1").contentType(JSON)
                .body("""
                        {
                          "givenNames": "  patched  ",
                          "familyNames": "  name  ",
                          "preferredName": "  updated  ",
                          "birthCountryCode": "  Ec  ",
                          "birthDate": "1902-03-04",
                          "dateOfDeath": "2002-05-06"
                        }
                        """)
                .patch("/v1/natural-person/{partyId}", partyId)
                .then().statusCode(200)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(200))
                .body("code", equalTo("successful"))
                .body("data.version", equalTo(2))
                .body("data.displayName", equalTo("PATCHED NAME"))
                .body("data.naturalPersonDetails", equalTo(Map.of(
                        "givenNames", "PATCHED", "familyNames", "NAME", "preferredName", "UPDATED",
                        "birthCountryCode", "EC", "birthDate", "1902-03-04", "dateOfDeath", "2002-05-06")));

        Response legal = validRequest()
                .header("Idempotency-Key", "packaged-normalized-legal-" + UUID.randomUUID())
                .contentType(JSON)
                .body("""
                        {
                          "displayName": "  native company  ",
                          "legalName": "  native company ltd  ",
                          "tradeName": "  native trading  ",
                          "legalFormCode": "  ltd  ",
                          "incorporationCountryCode": "  ec  ",
                          "incorporatedOn": "2000-01-02",
                          "dissolvedOn": "2020-03-04",
                          "initialIdentifier": {
                            "identifierSchemeCode": "TEST_LEGAL_ACTIVE",
                            "value": "%s"
                          }
                        }
                        """.formatted(identifierValue("NL")))
                .post("/v1/legal-entity")
                .then().statusCode(201)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(201))
                .body("code", equalTo("successful"))
                .body("data.displayName", equalTo("NATIVE COMPANY"))
                .body("data.legalEntityDetails", equalTo(Map.of(
                        "legalName", "NATIVE COMPANY LTD", "tradeName", "NATIVE TRADING", "legalFormCode", "LTD",
                        "incorporationCountryCode", "EC", "incorporatedOn", "2000-01-02", "dissolvedOn", "2020-03-04")))
                .extract().response();
        assertEquals(Set.of("status", "code", "data"), legal.jsonPath().getMap("$").keySet());
    }

    @Test
    void packagedApplicationRegistersANormalizedEcuadorNationalIdWithoutExpiration() {
        Response response = validRequest()
                .header("Idempotency-Key", "packaged-ecuador-national-id-" + UUID.randomUUID())
                .contentType(JSON)
                .body("""
                        {
                          "givenNames": "Packaged",
                          "familyNames": "Ecuador National ID",
                          "initialIdentifier": {
                            "identifierSchemeCode": "EC_NATIONAL_ID",
                            "value": "  0190000000  ",
                            "expiresOn": null,
                            "isPrimary": true
                          }
                        }
                        """)
                .when().post("/v1/natural-person")
                .then()
                .statusCode(201)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(201))
                .body("code", equalTo("successful"))
                .body("data.type", equalTo("NATURAL_PERSON"))
                .body("data.recordStatus", equalTo("DRAFT"))
                .body("data.initialIdentifier.schemeCode", equalTo("EC_NATIONAL_ID"))
                .body("data.initialIdentifier.status", equalTo("PENDING_VERIFICATION"))
                .body("data.initialIdentifier.maskedValue", equalTo("******0000"))
                .body("data.initialIdentifier", not(hasKey("value")))
                .body("data.initialIdentifier", not(hasKey("normalizedValue")))
                .body("data.initialIdentifier", not(hasKey("encryptedValue")))
                .body("data.initialIdentifier", not(hasKey("normalizedValueHash")))
                .body(not(containsString("0190000000")))
                .extract().response();

        assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
        assertNull(response.path("data.initialIdentifier.expiresOn"));
    }

    @Test
    void packagedApplicationRejectsAnEcuadorNationalIdChecksumWithoutLeakingTheValue() {
        String expiresOn = LocalDate.now(ZoneOffset.UTC).plusYears(1).toString();
        Response response = validRequest()
                .header("Idempotency-Key", "packaged-ecuador-invalid-checksum-" + UUID.randomUUID())
                .contentType(JSON)
                .body("""
                        {
                          "givenNames": "Packaged",
                          "familyNames": "Invalid Ecuador National ID",
                          "initialIdentifier": {
                            "identifierSchemeCode": "EC_NATIONAL_ID",
                            "value": "1710034064",
                            "expiresOn": "%s"
                          }
                        }
                        """.formatted(expiresOn))
                .when().post("/v1/natural-person");

        assertError(response, 422, "identifier-validation-failure");
        assertEquals(Set.of("status", "code"), response.jsonPath().getMap("$").keySet());
        response.then().body(not(containsString("1710034064")));
    }

    @Test
    void packagedApplicationRegistersALegalEntityWithANormalizedEcuadorTaxId() {
        Response response = validRequest()
                .header("Idempotency-Key", "packaged-ecuador-tax-id-" + UUID.randomUUID())
                .contentType(JSON)
                .body("""
                        {
                          "legalName": "Packaged Ecuador Tax ID Ltd",
                          "incorporationCountryCode": "EC",
                          "initialIdentifier": {
                            "identifierSchemeCode": "EC_TAX_ID",
                            "value": "  1790000000001  ",
                            "isPrimary": true
                          }
                        }
                        """)
                .when().post("/v1/legal-entity")
                .then()
                .statusCode(201)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(201))
                .body("code", equalTo("successful"))
                .body("data.type", equalTo("LEGAL_ENTITY"))
                .body("data.recordStatus", equalTo("DRAFT"))
                .body("data.initialIdentifier.schemeCode", equalTo("EC_TAX_ID"))
                .body("data.initialIdentifier.status", equalTo("PENDING_VERIFICATION"))
                .body("data.initialIdentifier.maskedValue", equalTo("*********0001"))
                .body("data.initialIdentifier", not(hasKey("value")))
                .body("data.initialIdentifier", not(hasKey("normalizedValue")))
                .body("data.initialIdentifier", not(hasKey("encryptedValue")))
                .body("data.initialIdentifier", not(hasKey("normalizedValueHash")))
                .body("data.initialIdentifier", not(hasKey("encryptionKeyVersion")))
                .body(not(containsString("1790000000001")))
                .extract().response();

        assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
        assertNull(response.path("data.initialIdentifier.expiresOn"));
    }

    @Test
    void packagedApplicationRejectsAnEcuadorTaxIdSuffixWithoutLeakingTheValue() {
        Response response = validRequest()
                .header("Idempotency-Key", "packaged-ecuador-invalid-tax-suffix-" + UUID.randomUUID())
                .contentType(JSON)
                .body("""
                        {
                          "legalName": "Packaged Invalid Ecuador Tax ID Ltd",
                          "incorporationCountryCode": "EC",
                          "initialIdentifier": {
                            "identifierSchemeCode": "EC_TAX_ID",
                            "value": "1790000000002"
                          }
                        }
                        """)
                .when().post("/v1/legal-entity");

        assertError(response, 422, "identifier-validation-failure");
        assertEquals(Set.of("status", "code"), response.jsonPath().getMap("$").keySet());
        response.then().body(not(containsString("1790000000002")));
    }

    @Test
    void packagedApplicationRegistersANormalizedPassportWithPendingVerification() {
        String expiresOn = LocalDate.now(ZoneOffset.UTC).plusYears(1).toString();
        Response response = validRequest()
                .header("Idempotency-Key", "packaged-ecuador-passport-" + UUID.randomUUID())
                .contentType(JSON)
                .body("""
                        {
                          "givenNames": "Packaged",
                          "familyNames": "Passport Holder",
                          "initialIdentifier": {
                            "identifierSchemeCode": "EC_PASSPORT",
                            "value": "  ab0123456  ",
                            "expiresOn": "%s"
                          }
                        }
                        """.formatted(expiresOn))
                .when().post("/v1/natural-person")
                .then()
                .statusCode(201)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(201))
                .body("code", equalTo("successful"))
                .body("data.type", equalTo("NATURAL_PERSON"))
                .body("data.recordStatus", equalTo("DRAFT"))
                .body("data.initialIdentifier.schemeCode", equalTo("EC_PASSPORT"))
                .body("data.initialIdentifier.status", equalTo("PENDING_VERIFICATION"))
                .body("data.initialIdentifier.maskedValue", equalTo("*****3456"))
                .body("data.initialIdentifier.expiresOn", equalTo(expiresOn))
                .body("data.initialIdentifier", not(hasKey("value")))
                .body("data.initialIdentifier", not(hasKey("normalizedValue")))
                .body("data.initialIdentifier", not(hasKey("encryptedValue")))
                .body("data.initialIdentifier", not(hasKey("normalizedValueHash")))
                .body(not(containsString("ab0123456")))
                .body(not(containsString("AB0123456")))
                .extract().response();

        assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
        assertNull(response.path("data.initialIdentifier.verifiedAt"));
    }

    @Test
    void packagedApplicationAcceptsAbsentPassportExpirationButRejectsSuppliedPastDates() {
        for (String expiration : new String[] { "null", "\"2000-01-01\"" }) {
            Response response = validRequest()
                    .header("Idempotency-Key", "packaged-passport-expiration-" + UUID.randomUUID())
                    .contentType(JSON)
                    .body("""
                            {
                              "givenNames": "Packaged",
                              "familyNames": "Optional Passport Expiration",
                              "initialIdentifier": {
                                "identifierSchemeCode": "EC_PASSPORT",
                                "value": "CD0123456",
                                "expiresOn": %s
                              }
                            }
                            """.formatted(expiration))
                    .when().post("/v1/natural-person");
            if (expiration.equals("null")) {
                response.then().statusCode(201).header("Process-Id", equalTo(PROCESS_ID))
                        .body("status", equalTo(201)).body("code", equalTo("successful"))
                        .body("data.initialIdentifier.status", equalTo("PENDING_VERIFICATION"));
                assertNull(response.path("data.initialIdentifier.expiresOn"));
                assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
            } else {
                assertError(response, 422, "identifier-validation-failure");
                assertEquals(Set.of("status", "code"), response.jsonPath().getMap("$").keySet());
            }
            response.then().body(not(containsString("CD0123456")));
        }
    }

    @Test
    void packagedApplicationExecutesRegistrationAndActivationFlows() {
        String naturalValue = identifierValue("PN");
        String naturalKey = "packaged-natural-" + UUID.randomUUID();
        String naturalBody = naturalBody("Packaged Native", naturalValue);
        Response natural = validRequest()
                .header("Idempotency-Key", naturalKey)
                .contentType(JSON)
                .body(naturalBody)
                .when().post("/v1/natural-person")
                .then()
                .statusCode(201)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(201))
                .body("code", equalTo("successful"))
                .body("data.type", equalTo("NATURAL_PERSON"))
                .body("data.recordStatus", equalTo("DRAFT"))
                .body("data.initialIdentifier.status", equalTo("PENDING_VERIFICATION"))
                .body("data.initialIdentifier", not(hasKey("value")))
                .body(not(containsString(naturalValue)))
                .extract().response();
        String partyId = natural.path("data.partyId");
        String identifierId = natural.path("data.initialIdentifier.identifierId");
        String maskedValue = natural.path("data.initialIdentifier.maskedValue");

        validRequest()
                .header("Idempotency-Key", naturalKey)
                .contentType(JSON)
                .body(naturalBody)
                .when().post("/v1/natural-person")
                .then()
                .statusCode(201)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(201))
                .body("code", equalTo("successful"))
                .body("data.partyId", equalTo(partyId))
                .body("data.initialIdentifier.identifierId", equalTo(identifierId))
                .body("data.initialIdentifier.maskedValue", equalTo(maskedValue))
                .body(not(containsString(naturalValue)));

        assertError(
                validRequest()
                        .header("Idempotency-Key", "packaged-duplicate-" + UUID.randomUUID())
                        .contentType(JSON)
                        .body(naturalBody(
                                "Packaged Duplicate",
                                naturalValue.toLowerCase(Locale.ROOT)))
                        .when().post("/v1/natural-person"),
                409,
                "conflict");

        String legalValue = identifierValue("PL");
        validRequest()
                .header("Idempotency-Key", "packaged-legal-" + UUID.randomUUID())
                .contentType(JSON)
                .body(legalBody(legalValue))
                .when().post("/v1/legal-entity")
                .then()
                .statusCode(201)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(201))
                .body("code", equalTo("successful"))
                .body("data.type", equalTo("LEGAL_ENTITY"))
                .body("data.recordStatus", equalTo("DRAFT"))
                .body("data.initialIdentifier.schemeCode", equalTo("TEST_LEGAL_ACTIVE"))
                .body("data.initialIdentifier", not(hasKey("value")))
                .body(not(containsString(legalValue)));

        String additionalValue = identifierValue("PA");
        String additionalKey = "packaged-additional-" + UUID.randomUUID();
        validRequest()
                .header("Idempotency-Key", additionalKey)
                .contentType(JSON)
                .body(identifierBody(additionalValue))
                .when().post("/v1/parties/{partyId}/identifiers", partyId)
                .then()
                .statusCode(201)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(201))
                .body("code", equalTo("successful"))
                .body("data.partyId", equalTo(partyId))
                .body("data.status", equalTo("PENDING_VERIFICATION"))
                .body("data", not(hasKey("value")))
                .body(not(containsString(additionalValue)));
        assertError(
                validRequest()
                        .header("Idempotency-Key", additionalKey)
                        .contentType(JSON)
                        .body(identifierBody(additionalValue))
                        .when().post("/v1/parties/{partyId}/identifiers", partyId),
                409,
                "conflict");
        assertError(
                validRequest()
                        .contentType(JSON)
                        .body(identifierBody(identifierValue("PM")))
                        .when().post("/v1/parties/{partyId}/identifiers", partyId),
                400,
                "idempotency-key-required");

        validRequest()
                .header("If-Match", "0")
                .when().post("/v1/parties/{partyId}/activate", ACTIVATION_FIXTURE_PARTY_ID)
                .then()
                .statusCode(200)
                .header("Process-Id", equalTo(PROCESS_ID))
                .body("status", equalTo(200))
                .body("code", equalTo("successful"))
                .body("data.partyId", equalTo(ACTIVATION_FIXTURE_PARTY_ID))
                .body("data.type", equalTo("NATURAL_PERSON"))
                .body("data.recordStatus", equalTo("ACTIVE"))
                .body("data.version", equalTo(1))
                .body("data", not(hasKey("initialIdentifier")));
        assertError(
                validRequest()
                        .header("If-Match", "0")
                        .when().post("/v1/parties/{partyId}/activate", ACTIVATION_FIXTURE_PARTY_ID),
                412,
                "precondition-failed");
    }

    private static RequestSpecification validRequest() {
        return given()
                .header("Tenant-Id", TENANT_ID)
                .header("User-Id", USER_ID)
                .header("Process-Id", PROCESS_ID);
    }

    private static String naturalBody(String displayName, String identifierValue) {
        return """
                {
                  "displayName": "%s",
                  "givenNames": "Packaged",
                  "familyNames": "Registration",
                  "birthCountryCode": "EC",
                  "initialIdentifier": {
                    "identifierSchemeCode": "TEST_NATURAL_ACTIVE",
                    "value": "%s",
                    "isPrimary": true
                  }
                }
                """.formatted(displayName, identifierValue);
    }

    private static String legalBody(String identifierValue) {
        return """
                {
                  "legalName": "Packaged Analytical Engines Ltd",
                  "incorporationCountryCode": "EC",
                  "initialIdentifier": {
                    "identifierSchemeCode": "TEST_LEGAL_ACTIVE",
                    "value": "%s",
                    "isPrimary": true
                  }
                }
                """.formatted(identifierValue);
    }

    private static String identifierBody(String identifierValue) {
        return """
                {
                  "identifierSchemeCode": "TEST_NATURAL_ACTIVE",
                  "value": "%s"
                }
                """.formatted(identifierValue);
    }

    private static String identifierValue(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
