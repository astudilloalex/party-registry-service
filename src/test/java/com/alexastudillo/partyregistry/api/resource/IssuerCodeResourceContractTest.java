package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.support.StoredOutboxTestProfile;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.GeographicReferenceStubResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises actual initial/additional registration issuer validation, canonical storage output, and raw retry identity. */
@QuarkusTest
@TestProfile(StoredOutboxTestProfile.class)
@Timeout(90)
class IssuerCodeResourceContractTest {

    private static final String PROCESS = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";

    @Test
    void bothOwnerTypesAndAdditionalRegistrationReturnCanonicalOrAbsentIssuer() throws Exception {
        for (boolean natural : List.of(true, false)) {
            String tenant = UUID.randomUUID().toString();
            try (var _ = GeographicReferenceStubResource.allowContext(tenant, "issuer-contract-operator", PROCESS)) {
                int countryCalls = GeographicReferenceStubResource.requestCount("EC");
                var created = post(path(natural), tenant, UUID.randomUUID().toString(), ownerBody(natural, "civil_registry"));
                assertEquals(201, created.statusCode(), created.asString());
                assertEquals(201, created.jsonPath().getInt("status"));
                assertEquals("successful", created.jsonPath().getString("code"));
                assertEquals("CIVIL_REGISTRY", created.jsonPath().getString("data.initialIdentifier.issuerCode"));
                assertEquals(PROCESS, created.header("Process-Id"));
                assertEquals(countryCalls + (natural ? 0 : 1), GeographicReferenceStubResource.requestCount("EC"));
                assertEquals(0, GeographicReferenceStubResource.requestCount("CIVIL_REGISTRY"));
                String party = created.jsonPath().getString("data.partyId");
                String scheme = natural ? "TEST_NATURAL_ACTIVE" : "TEST_LEGAL_ACTIVE";
                var blank = post("/v1/parties/" + party + "/identifiers", tenant, UUID.randomUUID().toString(), identifier(scheme, "\t "));
                assertEquals(201, blank.statusCode(), blank.asString());
                assertEquals("successful", blank.jsonPath().getString("code"));
                assertNull(blank.jsonPath().get("data.issuerCode"));
                assertEquals(PROCESS, blank.header("Process-Id"));
                assertEquals(countryCalls + (natural ? 0 : 1), GeographicReferenceStubResource.requestCount("EC"));
            }
        }
    }

    @Test
    void newIssuerSyntaxFailuresKeepSpecificSafeEnvelopesBeforeBusinessEvaluation() {
        String tenant = UUID.randomUUID().toString();
        for (String invalid : List.of("_REGISTRY", "CIVIL__REGISTRY", "REGISTRY_", "REGISTRY1", "REGÍSTRY", "REG\0ISTRY", " registry ")) {
            var response = post("/v1/parties/not-a-uuid/identifiers", tenant, UUID.randomUUID().toString(), identifier("TEST_NATURAL_ACTIVE", invalid));
            assertEquals(400, response.statusCode(), response.asString());
            assertEquals("issuer-code-invalid", response.jsonPath().getString("code"));
            assertEquals(Set.of("status", "code"), response.jsonPath().getMap("$").keySet());
            assertEquals(PROCESS, response.header("Process-Id"));
        }
    }

    @Test
    void initialIssuerSyntaxFailsBeforeCountryValidationForBothPartyTypes() {
        String tenant = UUID.randomUUID().toString();
        int countryCalls = GeographicReferenceStubResource.requestCount("EC");
        for (boolean natural : List.of(true, false)) {
            var invalid = post(path(natural), tenant, UUID.randomUUID().toString(), ownerBody(natural, "CIVIL__REGISTRY"));
            assertEquals(400, invalid.statusCode(), invalid.asString());
            assertEquals(Map.of("status", 400, "code", "issuer-code-invalid"), invalid.jsonPath().getMap("$"));
            var oversized = post(path(natural), tenant, UUID.randomUUID().toString(), ownerBody(natural, "😀".repeat(65)));
            assertEquals(400, oversized.statusCode(), oversized.asString());
            assertEquals(Map.of("status", 400, "code", "issuer-code-too-long"), oversized.jsonPath().getMap("$"));
        }
        assertEquals(countryCalls, GeographicReferenceStubResource.requestCount("EC"));
    }

    @Test
    void creationReplayRetainsOriginalParsedIssuerIdentityBeforeWriteCanonicalization() {
        String tenant = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();
        var body = ownerBody(true, "civil_registry");
        var first = post(path(true), tenant, key, body);
        assertEquals(201, first.statusCode(), first.asString());
        var replay = post(path(true), tenant, key, body);
        assertEquals(201, replay.statusCode(), replay.asString());
        assertEquals(first.jsonPath().getMap("data"), replay.jsonPath().getMap("data"));
        var changed = new LinkedHashMap<>(body);
        var original = body.get("initialIdentifier");
        assertTrue(original instanceof Map<?, ?>);
        Map<?, ?> fields = (Map<?, ?>) original;
        var changedIdentifier = new LinkedHashMap<String, Object>();
        fields.forEach((name, value) -> changedIdentifier.put(name.toString(), value));
        changedIdentifier.put("issuerCode", "CIVIL_REGISTRY");
        changed.put("initialIdentifier", changedIdentifier);
        var conflict = post(path(true), tenant, key, changed);
        assertEquals(409, conflict.statusCode(), conflict.asString());
        assertEquals("idempotency-key-conflict", conflict.jsonPath().getString("code"));
    }

    private static Map<String, Object> ownerBody(boolean natural, String issuer) {
        var body = new LinkedHashMap<String, Object>();
        if (natural) {
            body.put("givenNames", "Issuer");
            body.put("familyNames", "Operator");
        } else {
            body.put("legalName", "Issuer Example Organization");
            body.put("incorporationCountryCode", "EC");
        }
        body.put("initialIdentifier", identifier(natural ? "TEST_NATURAL_ACTIVE" : "TEST_LEGAL_ACTIVE", issuer));
        return body;
    }

    private static Map<String, Object> identifier(String scheme, String issuer) {
        var body = new LinkedHashMap<String, Object>();
        body.put("identifierSchemeCode", scheme);
        body.put("value", "IC" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT));
        body.put("issuerCode", issuer);
        return body;
    }

    private static String path(boolean natural) {
        return natural ? "/v1/natural-person" : "/v1/legal-entity";
    }

    private static Response post(String path, String tenant, String key, Map<String, Object> body) {
        return given().contentType(ContentType.JSON).header("Process-Id", PROCESS).header("Tenant-Id", tenant)
                .header("User-Id", "issuer-contract-operator").header("Idempotency-Key", key)
                .body(body).post(path).then().extract().response();
    }
}
