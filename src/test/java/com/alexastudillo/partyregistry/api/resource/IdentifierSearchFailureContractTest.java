package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.IdentifierSearchContractAssertions;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.infrastructure.security.JcaIdentifierProtectionAdapter;
import com.alexastudillo.partyregistry.support.IdentifierSearchDatabaseFixture;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual search selector/framework failures and corrupt protected data without replacing production reads. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSearchFailureContractTest {
    @Inject DataSource dataSource;
    @Inject JcaIdentifierProtectionAdapter protection;

    @Test
    void bothRoutesPreserveKnownAndFrameworkEnvelopesAndAcceptedProcessEcho() throws Exception {
        String unsupported = "UNSUPPORTED_" + UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            new IdentifierSearchDatabaseFixture(connection, protection).scheme(unsupported, IdentifierSchemeStatus.DRAFT, "UNKNOWN_NORMALIZER");
        }
        for (String path : paths()) {
            String process = UUID.randomUUID().toString();
            String field = path.equals(IdentifierSearchContractAssertions.EXACT_PATH) ? "value" : "prefix";
            check(request(process).body(Map.of("identifierSchemeCode", unsupported, field, "SensitiveSelector")).post(path), process,
                    422, "identifier-validation-failure");
            check(request(process).body(Map.of("identifierSchemeCode", "ABSENT_SCHEME", field, "SensitiveSelector")).post(path), process,
                    422, "unknown-identifier-scheme");
            check(request(process).get(path), process, 405, "method-not-allowed");
            check(request(process).contentType("application/xml").body("<SensitiveSelector/>").post(path), process,
                    415, "unsupported-media-type");
            check(request(process).body("{}").post(path + "/absent-route"), process, 404, "not-found");
        }
        String process = UUID.randomUUID().toString();
        check(request(process).body(Map.of("identifierSchemeCode", "ABSENT_SCHEME", "prefix", "17", "cursor", "altered-token"))
                .post(IdentifierSearchContractAssertions.PREFIX_PATH), process, 422, "unknown-identifier-scheme");
        check(request(process).body(Map.of("identifierSchemeCode", "EC_NATIONAL_ID", "prefix", "17", "cursor", "altered-token"))
                .post(IdentifierSearchContractAssertions.PREFIX_PATH), process, 400, "bad-request");
    }

    @Test
    void requiredCorruptCiphertextFailsPrefixWithoutPartialSuccessAndExactDoesNotDecrypt() throws Exception {
        UUID tenant = UUID.randomUUID();
        String code = "CORRUPT_" + UUID.randomUUID();
        UUID id;
        try (var connection = dataSource.getConnection()) {
            var fixture = new IdentifierSearchDatabaseFixture(connection, protection);
            UUID scheme = fixture.scheme(code, IdentifierSchemeStatus.RETIRED, "TRIM_UPPERCASE_V1");
            UUID party = fixture.party(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED);
            id = fixture.identifier(tenant, party, scheme, UUID.randomUUID(), "SensitiveProtectedValue", PartyIdentifierStatus.REVOKED,
                    false, Instant.EPOCH);
            try (var corrupt = connection.prepareStatement("update party_identifiers set encrypted_value = 'v1.CORRUPT' where id = ?")) {
                corrupt.setObject(1, id);
                corrupt.executeUpdate();
            }
        }
        String process = UUID.randomUUID().toString();
        var prefix = given().contentType("application/json").header("Tenant-Id", tenant).header("Process-Id", process)
                .header("User-Id", "corrupt-search-test").body(Map.of("identifierSchemeCode", code, "prefix", "Sensitive"))
                .post(IdentifierSearchContractAssertions.PREFIX_PATH);
        check(prefix, process, 500, "server-error");
        var exact = given().contentType("application/json").header("Tenant-Id", tenant).header("Process-Id", process)
                .header("User-Id", "corrupt-search-test").body(Map.of("identifierSchemeCode", code, "value", "SensitiveProtectedValue"))
                .post(IdentifierSearchContractAssertions.EXACT_PATH);
        assertEquals(200, exact.statusCode());
        assertEquals(List.of(id.toString()), exact.jsonPath().getList("data.identifier.identifierId"));
        assertFalse(exact.asString().contains("Sensitive"));
        var excluded = given().contentType("application/json").header("Tenant-Id", tenant).header("Process-Id", process)
                .header("User-Id", "healthy-search-test").body(Map.of("identifierSchemeCode", code, "prefix", "Sensitive", "identifierStatuses", List.of("VERIFIED")))
                .post(IdentifierSearchContractAssertions.PREFIX_PATH);
        assertEquals(200, excluded.statusCode());
        assertEquals(0, excluded.jsonPath().getInt("totalElements"));
    }

    private static RequestSpecification request(String process) {
        return given().contentType("application/json").header("Tenant-Id", UUID.randomUUID()).header("Process-Id", process)
                .header("User-Id", "failure-search-test");
    }

    private static void check(Response response, String process, int status, String code) {
        assertEquals(status, response.statusCode());
        assertEquals(process, response.header("Process-Id"));
        IdentifierSearchContractAssertions.assertErrorResponse(response.statusCode(), code, response.asString());
    }

    private static List<String> paths() {
        return List.of(IdentifierSearchContractAssertions.EXACT_PATH, IdentifierSearchContractAssertions.PREFIX_PATH);
    }
}
