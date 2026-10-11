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
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies literal protected-prefix HTTP matching and live bidirectional scopes over multiple detached candidate batches. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSearchPrefixContractTest {
    @Inject DataSource dataSource;
    @Inject JcaIdentifierProtectionAdapter protection;
    private final IdentifierSearchContractAssertions assertions = new IdentifierSearchContractAssertions(
            IdentifierSchemeContractValidator.readContract(Path.of("docs/contracts/party-registry.openapi.yaml")));

    @Test
    void partialNationalIdAndDuplicateTimestampTiesTraverseTwoTwoOneWithoutCollapse() throws Exception {
        UUID tenant = UUID.randomUUID();
        String code = "EC_NATIONAL_ID";
        var expected = new ArrayList<UUID>();
        try (var connection = dataSource.getConnection()) {
            UUID scheme;
            try (var statement = connection.prepareStatement("select id from identifier_schemes where code = ?")) {
                statement.setString(1, code);
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    scheme = rows.getObject(1, UUID.class);
                }
            }
            var fixture = new IdentifierSearchDatabaseFixture(connection, protection);
            UUID party = fixture.party(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.INACTIVE);
            for (int index = 0; index < 5; index++) {
                UUID id = UUID.randomUUID();
                expected.add(fixture.identifier(tenant, party, scheme, id, "1710034065", PartyIdentifierStatus.REVOKED,
                        false, Instant.parse("2020-01-01T00:00:00.123456Z")));
            }
        }
        expected.sort((left, right) -> {
            int most = Long.compareUnsigned(right.getMostSignificantBits(), left.getMostSignificantBits());
            return most == 0 ? Long.compareUnsigned(right.getLeastSignificantBits(), left.getLeastSignificantBits()) : most;
        });
        var first = prefix(tenant, code, "17", Map.of("limit", 2), 2);
        var second = prefix(tenant, code, "17", Map.of("limit", 2, "cursor", first.jsonPath().getString("nextCursor")), 2);
        var last = prefix(tenant, code, "17", Map.of("limit", 2, "cursor", second.jsonPath().getString("nextCursor")), 2);
        assertEquals(List.of(2, 2, 1), List.of(identifiers(first).size(), identifiers(second).size(), identifiers(last).size()));
        var actual = new ArrayList<>(identifiers(first));
        actual.addAll(identifiers(second));
        actual.addAll(identifiers(last));
        assertEquals(expected.stream().map(UUID::toString).toList(), actual);
        for (var response : List.of(first, second, last)) {
            assertEquals(5, response.jsonPath().getInt("totalElements"));
            assertEquals(3, response.jsonPath().getInt("totalPages"));
        }
        assertNull(last.jsonPath().getString("nextCursor"));
        assertEquals(identifiers(second), identifiers(prefix(tenant, code, "17",
                Map.of("limit", 2, "cursor", last.jsonPath().getString("prevCursor")), 2)));
        assertEquals(5, prefix(tenant, code, "1710034065", Map.of(), 50).jsonPath().getInt("totalElements"));
    }

    @Test
    void multiBatchNavigationKeepsCompleteCountsEquivalentScopesAndDeletedBoundarySemantics() throws Exception {
        UUID tenant = UUID.randomUUID();
        String code = "PREFIX_" + UUID.randomUUID();
        List<String> ids = seedMultiBatchDataset(tenant, code);
        String cursor = assertNavigationAndScopes(tenant, code, ids);
        assertAlteredQueryRejections(tenant, code, cursor);
        assertBatchSizeBoundaries(tenant, code, ids);
        assertDeletedBoundarySemantics(tenant, code, ids, cursor);
    }

    private List<String> seedMultiBatchDataset(UUID tenant, String code) throws Exception {
        var ids = new ArrayList<String>();
        try (var connection = dataSource.getConnection()) {
            var fixture = new IdentifierSearchDatabaseFixture(connection, protection);
            UUID scheme = fixture.scheme(code, IdentifierSchemeStatus.DEPRECATED, "TRIM_UPPERCASE_V1");
            UUID party = fixture.party(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED);
            for (int index = 0; index < 520; index++) {
                String value = (index < 260 ? "literal%_" : "OTHER-") + index;
                UUID id = fixture.identifier(tenant, party, scheme, UUID.randomUUID(), value, PartyIdentifierStatus.REVOKED,
                        false, Instant.parse("2020-01-01T00:00:00.123456Z").plusSeconds(index));
                if (index < 260) ids.addFirst(id.toString());
            }
            UUID foreign = UUID.randomUUID();
            UUID foreignParty = fixture.party(foreign, PartyType.NATURAL_PERSON, PartyRecordStatus.ACTIVE);
            fixture.identifier(foreign, foreignParty, scheme, UUID.randomUUID(), "LITERAL%_foreign", PartyIdentifierStatus.REVOKED,
                    true, Instant.now());
        }
        return ids;
    }

    private String assertNavigationAndScopes(UUID tenant, String code, List<String> ids) {
        var first = prefix(tenant, code, " literal%_ ", Map.of(), 50);
        assertEquals(ids.subList(0, 50), identifiers(first));
        assertEquals(260, first.jsonPath().getInt("totalElements"));
        assertEquals(6, first.jsonPath().getInt("totalPages"));
        String cursor = first.jsonPath().getString("nextCursor");
        assertNotNull(cursor);
        var equivalent = prefix(tenant, code, "LITERAL%_", Map.of("limit", 50, "cursor", cursor,
                "identifierStatuses", List.of("REVOKED", "VERIFIED", "EXPIRED", "REJECTED", "PENDING_VERIFICATION", "REVOKED"),
                "partyRecordStatuses", List.of("ARCHIVED", "ACTIVE", "DRAFT", "INACTIVE", "ACTIVE")), 50);
        assertEquals(ids.subList(50, 100), identifiers(equivalent));
        var backward = prefix(tenant, code, "literal%_", Map.of("limit", 50, "cursor", equivalent.jsonPath().getString("prevCursor")), 50);
        assertEquals(identifiers(first), identifiers(backward));
        return cursor;
    }

    private void assertAlteredQueryRejections(UUID tenant, String code, String cursor) {
        for (Map<String, Object> altered : List.<Map<String, Object>>of(Map.of("cursor", cursor, "limit", 1),
                Map.of("cursor", cursor, "identifierStatuses", List.of()), Map.of("cursor", cursor, "partyRecordStatuses", List.of("ACTIVE")))) {
            reject(tenant, code, "literal%_", altered);
        }
        reject(UUID.randomUUID(), code, "literal%_", Map.of("cursor", cursor));
        reject(tenant, code, "OTHER-", Map.of("cursor", cursor));
    }

    private void assertBatchSizeBoundaries(UUID tenant, String code, List<String> ids) {
        assertEquals(1, identifiers(prefix(tenant, code, "literal%_", Map.of("limit", 1), 1)).size());
        var large = prefix(tenant, code, "literal%_", Map.of("limit", 200), 200);
        var last = prefix(tenant, code, "literal%_", Map.of("limit", 200, "cursor", large.jsonPath().getString("nextCursor")), 200);
        var all = new ArrayList<>(identifiers(large));
        all.addAll(identifiers(last));
        assertEquals(ids, all);
        assertNull(last.jsonPath().getString("nextCursor"));
        assertEquals(identifiers(large), identifiers(prefix(tenant, code, "literal%_",
                Map.of("limit", 200, "cursor", last.jsonPath().getString("prevCursor")), 200)));
    }

    private void assertDeletedBoundarySemantics(UUID tenant, String code, List<String> ids, String cursor) throws Exception {
        try (var connection = dataSource.getConnection(); var remove = connection.prepareStatement("delete from party_identifiers where id = ?")) {
            remove.setObject(1, UUID.fromString(ids.get(49)));
            remove.executeUpdate();
        }
        var live = prefix(tenant, code, "literal%_", Map.of("cursor", cursor), 50);
        assertEquals(259, live.jsonPath().getInt("totalElements"));
        assertEquals(ids.subList(50, 100), identifiers(live));
        try (var connection = dataSource.getConnection(); var remove = connection.prepareStatement(
                "delete from party_identifiers where tenant_id = ? and created_at < ?")) {
            remove.setObject(1, tenant);
            remove.setTimestamp(2, java.sql.Timestamp.from(Instant.parse("2020-01-01T00:00:00.123456Z").plusSeconds(210)));
            remove.executeUpdate();
        }
        var emptyLive = prefix(tenant, code, "literal%_", Map.of("cursor", cursor), 50);
        assertTrue(identifiers(emptyLive).isEmpty());
        assertEquals(49, emptyLive.jsonPath().getInt("totalElements"));
        assertNull(emptyLive.jsonPath().getString("nextCursor"));
        assertNotNull(emptyLive.jsonPath().getString("prevCursor"));
        try (var connection = dataSource.getConnection(); var remove = connection.prepareStatement("delete from party_identifiers where tenant_id = ?")) {
            remove.setObject(1, tenant);
            remove.executeUpdate();
        }
        var vanished = prefix(tenant, code, "literal%_", Map.of("cursor", cursor), 50);
        assertEquals(0, vanished.jsonPath().getInt("totalElements"));
        assertNull(vanished.jsonPath().getString("nextCursor"));
        assertNull(vanished.jsonPath().getString("prevCursor"));
    }

    @Test
    void literalUnicodeAndAllHistoricalFiltersAreAppliedBeforeAdmission() throws Exception {
        UUID tenant = UUID.randomUUID();
        String code = "LITERAL_" + UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            var fixture = new IdentifierSearchDatabaseFixture(connection, protection);
            UUID scheme = fixture.scheme(code, IdentifierSchemeStatus.RETIRED, "TRIM_UPPERCASE_V1");
            for (PartyType type : PartyType.values()) {
                for (PartyRecordStatus partyState : PartyRecordStatus.values()) {
                    UUID party = fixture.party(tenant, type, partyState);
                    for (PartyIdentifierStatus state : PartyIdentifierStatus.values()) {
                        fixture.identifier(tenant, party, scheme, UUID.randomUUID(), "Straße%_é  " + type + partyState + state,
                                state, state == PartyIdentifierStatus.VERIFIED, Instant.EPOCH);
                    }
                }
            }
            UUID party = fixture.party(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
            for (String nonmatch : List.of("OTHER-STRASSE%_É  ", "STRASSEXXÉ  ", "STRASSE%_E  ", "STRASSE%_É X")) {
                fixture.identifier(tenant, party, scheme, UUID.randomUUID(), nonmatch, PartyIdentifierStatus.REVOKED, false, Instant.EPOCH);
            }
            fixture.identifier(tenant, party, scheme, UUID.randomUUID(), "ß".repeat(64) + "X", PartyIdentifierStatus.REVOKED, false, Instant.EPOCH);
        }
        var all = prefix(tenant, code, " straße%_é  ", Map.of("limit", 200), 200);
        // Exterior whitespace is normalized away, while literal punctuation and accented characters remain significant.
        assertEquals(41, all.jsonPath().getInt("totalElements"));
        assertEquals(41, identifiers(all).stream().distinct().count());
        for (PartyIdentifierStatus state : PartyIdentifierStatus.values()) {
            var filtered = prefix(tenant, code, "Straße%_é", Map.of("identifierStatuses", List.of(state.name())), 50);
            assertEquals(state == PartyIdentifierStatus.REVOKED ? 9 : 8, filtered.jsonPath().getInt("totalElements"));
        }
        for (PartyRecordStatus state : PartyRecordStatus.values()) {
            var filtered = prefix(tenant, code, "Straße%_é", Map.of("partyRecordStatuses", List.of(state.name())), 50);
            assertEquals(state == PartyRecordStatus.DRAFT ? 11 : 10, filtered.jsonPath().getInt("totalElements"));
        }
        assertEquals(4, prefix(tenant, code, "Straße%_é", Map.of("identifierStatuses", List.of("VERIFIED", "REVOKED"),
                "partyRecordStatuses", List.of("ACTIVE")), 50).jsonPath().getInt("totalElements"));
        assertEquals(1, prefix(tenant, code, "ß".repeat(64), Map.of(), 50).jsonPath().getInt("totalElements"));
        assertEquals(0, prefix(UUID.randomUUID(), code, "Straße%_é", Map.of(), 50).jsonPath().getInt("totalElements"));
        for (String dimension : List.of("identifierStatuses", "partyRecordStatuses")) {
            assertEquals(0, prefix(tenant, code, "Straße%_é", Map.of(dimension, List.of()), 50).jsonPath().getInt("totalElements"));
        }
    }

    private Response prefix(UUID tenant, String code, String prefix, Map<String, Object> extra, int limit) {
        var response = send(tenant, code, prefix, extra);
        assertEquals(200, response.statusCode());
        assertions.assertPrefixResponse(response.statusCode(), response.asString(), limit);
        return response;
    }

    private static void reject(UUID tenant, String code, String prefix, Map<String, Object> extra) {
        var response = send(tenant, code, prefix, extra);
        assertEquals(400, response.statusCode());
        IdentifierSearchContractAssertions.assertErrorResponse(response.statusCode(), "bad-request", response.asString());
    }

    private static Response send(UUID tenant, String code, String prefix, Map<String, Object> extra) {
        var body = new java.util.HashMap<String, Object>(extra);
        body.put("identifierSchemeCode", code);
        body.put("prefix", prefix);
        String process = UUID.randomUUID().toString();
        var response = given().contentType("application/json").header("Tenant-Id", tenant).header("Process-Id", process)
                .header("User-Id", "prefix-search-contract").body(body).post(IdentifierSearchContractAssertions.PREFIX_PATH);
        assertEquals(process, response.header("Process-Id"));
        return response;
    }

    private static List<String> identifiers(Response response) {
        return response.jsonPath().getList("data.identifier.identifierId");
    }
}
