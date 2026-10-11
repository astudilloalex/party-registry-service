package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.IdentifierSearchContractAssertions;
import com.alexastudillo.partyregistry.contract.IdentifierSchemeContractValidator;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.infrastructure.security.JcaIdentifierProtectionAdapter;
import com.alexastudillo.partyregistry.support.IdentifierSearchDatabaseFixture;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies historical exact-search HTTP cardinality, ownership, full ordering and all persisted eligibility dimensions. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSearchHistoricalContractTest {
    @Inject DataSource dataSource;
    @Inject JcaIdentifierProtectionAdapter protection;
    private final IdentifierSearchContractAssertions assertions = new IdentifierSearchContractAssertions(
            IdentifierSchemeContractValidator.readContract(Path.of("docs/contracts/party-registry.openapi.yaml")));

    @Test
    void returnsAll201DuplicatesWithUnsignedUuidTiesAndConcealsForeignMatches() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        String code = "EXACT_" + UUID.randomUUID();
        var expected = new ArrayList<UUID>();
        try (var connection = dataSource.getConnection()) {
            var fixture = new IdentifierSearchDatabaseFixture(connection, protection);
            UUID scheme = fixture.scheme(code, IdentifierSchemeStatus.RETIRED, "TRIM_UPPERCASE_V1");
            UUID party = fixture.party(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED);
            Instant tied = Instant.parse("2020-01-01T00:00:00.123456Z");
            for (int index = 0; index < 201; index++) {
                UUID id = new UUID(index % 2 == 0 ? Long.MIN_VALUE : 0, index + 1);
                expected.add(fixture.identifier(tenant, party, scheme, id, "  synthetic%_é value ", PartyIdentifierStatus.REVOKED, false, tied));
            }
            UUID foreignParty = fixture.party(foreign, PartyType.NATURAL_PERSON, PartyRecordStatus.ACTIVE);
            fixture.identifier(foreign, foreignParty, scheme, UUID.randomUUID(), "SYNTHETIC%_É VALUE", PartyIdentifierStatus.REVOKED, true, tied);
        }
        expected.sort((left, right) -> {
            int most = Long.compareUnsigned(right.getMostSignificantBits(), left.getMostSignificantBits());
            return most == 0 ? Long.compareUnsigned(right.getLeastSignificantBits(), left.getLeastSignificantBits()) : most;
        });
        var response = exact(tenant, code, " synthetic%_é value ", "");
        assertEquals(201, response.jsonPath().getInt("totalElements"));
        assertEquals(201, response.jsonPath().getInt("numberOfElements"));
        assertEquals(expected.stream().map(UUID::toString).toList(), response.jsonPath().getList("data.identifier.identifierId"));
        assertEquals(1, response.jsonPath().getList("data.partyId").stream().distinct().count());
        assertTrue(response.jsonPath().getList("data.identifier.maskedValue", String.class).stream().allMatch("Stored-Safe-Mask"::equals));
        assertTrue(response.jsonPath().getList("data.displayName", String.class).stream().allMatch("Stored Synthetic Label"::equals));
        assertEquals(0, exact(UUID.randomUUID(), code, "SYNTHETIC%_É VALUE", "").jsonPath().getInt("totalElements"));
        for (String nonmatch : List.of("SYNTHETIC", "%_É VALUE", "Stored-Safe-Mask", "SYNTHETIC%_E VALUE", "SYNTHETIC%_ÉVALUE")) {
            assertEquals(0, exact(tenant, code, nonmatch, "").jsonPath().getInt("totalElements"));
        }
    }

    @Test
    void everyStoredStatePartyTypeAndWithdrawnSchemeRemainsSearchableWithoutReadmission() throws Exception {
        UUID tenant = UUID.randomUUID();
        for (IdentifierSchemeStatus schemeStatus : IdentifierSchemeStatus.values()) {
            String code = "HISTORY_" + UUID.randomUUID();
            var ids = new ArrayList<UUID>();
            try (var connection = dataSource.getConnection()) {
                var fixture = new IdentifierSearchDatabaseFixture(connection, protection);
                UUID scheme = fixture.scheme(code, schemeStatus, "TRIM_UPPERCASE_V1");
                for (PartyType type : PartyType.values()) {
                    for (PartyRecordStatus partyStatus : PartyRecordStatus.values()) {
                        UUID party = fixture.party(tenant, type, partyStatus);
                        for (PartyIdentifierStatus identifierStatus : PartyIdentifierStatus.values()) {
                            String value = "short-" + type + "-" + partyStatus + "-" + identifierStatus;
                            ids.add(fixture.identifier(tenant, party, scheme, UUID.randomUUID(), value, identifierStatus,
                                    identifierStatus == PartyIdentifierStatus.VERIFIED, Instant.EPOCH));
                        }
                    }
                }
            }
            int index = 0;
            for (PartyType type : PartyType.values()) {
                for (PartyRecordStatus partyStatus : PartyRecordStatus.values()) {
                    for (PartyIdentifierStatus identifierStatus : PartyIdentifierStatus.values()) {
                        String value = "short-" + type + "-" + partyStatus + "-" + identifierStatus;
                        var found = exact(tenant, code, " " + value.toLowerCase(java.util.Locale.ROOT) + " ", "");
                        assertEquals(List.of(ids.get(index++).toString()), found.jsonPath().getList("data.identifier.identifierId"));
                        assertEquals(type.name(), found.jsonPath().getString("data[0].type"));
                        assertEquals(partyStatus.name(), found.jsonPath().getString("data[0].recordStatus"));
                        assertEquals(identifierStatus.name(), found.jsonPath().getString("data[0].identifier.status"));
                        var included = exact(tenant, code, value, ",\"identifierStatuses\":[\"" + identifierStatus + "\",\"" + identifierStatus
                                + "\"],\"partyRecordStatuses\":[\"" + partyStatus + "\"]");
                        assertEquals(1, included.jsonPath().getInt("totalElements"));
                        assertEquals(0, exact(tenant, code, value, ",\"identifierStatuses\":[]").jsonPath().getInt("totalElements"));
                        PartyRecordStatus different = PartyRecordStatus.values()[(partyStatus.ordinal() + 1) % PartyRecordStatus.values().length];
                        assertEquals(0, exact(tenant, code, value, ",\"partyRecordStatuses\":[\"" + different + "\"]").jsonPath().getInt("totalElements"));
                    }
                }
            }
        }
    }

    private Response exact(UUID tenant, String code, String value, String filters) {
        String process = UUID.randomUUID().toString();
        var response = given().contentType("application/json").header("Tenant-Id", tenant).header("Process-Id", process)
                .header("User-Id", "historical-search-test")
                .body("{\"identifierSchemeCode\":\"" + code + "\",\"value\":\"" + value + "\"" + filters + "}")
                .post(IdentifierSearchContractAssertions.EXACT_PATH);
        assertEquals(200, response.statusCode());
        assertEquals(process, response.header("Process-Id"));
        assertions.assertExactResponse(response.statusCode(), response.asString());
        return response;
    }
}
