package com.alexastudillo.partyregistry;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that an additive V7-to-V8 upgrade retains accepted identifier and replay history exactly. */
@Timeout(120)
class PartyIdentifierContractUpgradeTest {

    private static final LocalDate ISSUED = LocalDate.of(2024, Month.FEBRUARY, 29);
    private static final LocalDate EXPIRES = LocalDate.of(2025, Month.MARCH, 1);
    private static final Timestamp ACCEPTED = Timestamp.from(ISSUED.atStartOfDay(ZoneOffset.UTC).toInstant().plusNanos(123456000));

    @Test
    void upgradesRetainedBothTypeHistoryWithoutRewritingStatesProtectionOrSnapshots() throws SQLException {
        try (var database = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))) {
            database.start();
            Flyway previous = flyway(database, "7");
            assertEquals(7, previous.migrate().migrationsExecuted);
            try (Connection connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword())) {
                assertNull(scalar(connection, "SELECT to_regclass('party_identifier_idempotency_records')::text"));
                assertNull(scalar(connection, "SELECT to_regclass('party_identifier_action_audit')::text"));
                seedHistory(connection, "NATURAL_PERSON");
                seedHistory(connection, "LEGAL_ENTITY");
                Map<String, String> previousRows = history(connection);

                Flyway current = flyway(database, "8");
                assertEquals(1, current.migrate().migrationsExecuted);
                current.validate();
                assertEquals(previousRows, history(connection));
                assertEquals("0", scalar(connection, "SELECT count(*)::text FROM party_identifier_idempotency_records"));
                assertEquals("0", scalar(connection, "SELECT count(*)::text FROM party_identifier_action_audit"));
                assertEquals("10", scalar(connection, "SELECT count(*)::text FROM party_identifiers"));
                assertEquals(0, current.migrate().migrationsExecuted);
            }
        }
    }

    @Test
    void upgradesWithoutChangingPriorChecksumsIndexesOrConstraints() throws SQLException {
        try (var database = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))) {
            database.start();
            Flyway previous = flyway(database, "7");
            assertEquals(7, previous.migrate().migrationsExecuted);
            Map<String, Integer> previousChecksums = checksums(previous);
            try (Connection connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword())) {
                Map<String, String> previousIndexes = indexes(connection);
                Map<String, String> previousConstraints = constraints(connection);
                Flyway current = flyway(database, "8");
                assertEquals(1, current.migrate().migrationsExecuted);
                current.validate();
                Map<String, Integer> currentChecksums = checksums(current);
                assertEquals(8, currentChecksums.size());
                previousChecksums.forEach((version, checksum) -> assertEquals(checksum, currentChecksums.get(version)));
                Map<String, String> currentIndexes = indexes(connection);
                previousIndexes.forEach((name, definition) -> assertEquals(definition, currentIndexes.get(name), name));
                Map<String, String> currentConstraints = constraints(connection);
                previousConstraints.forEach((name, definition) -> assertEquals(definition, currentConstraints.get(name), name));
                assertEquals(0, current.migrate().migrationsExecuted);
            }
        }
    }

    private static void seedHistory(Connection connection, String type) throws SQLException {
        UUID tenant = UUID.randomUUID();
        UUID party = UUID.randomUUID();
        connection.setAutoCommit(false);
        try {
            try (var insert = connection.prepareStatement("INSERT INTO parties (id, tenant_id, type, display_name, record_status, created_by, updated_by, version) VALUES (?, ?, ?::party_type, 'Retained owner', 'ARCHIVED', 'creator', 'archiver', 12)")) {
                insert.setObject(1, party);
                insert.setObject(2, tenant);
                insert.setString(3, type);
                insert.executeUpdate();
            }
            String detailSql = type.equals("NATURAL_PERSON")
                    ? "INSERT INTO natural_person_details (party_id, given_names, family_names, created_by, updated_by) VALUES (?, 'Retained', 'Owner', 'creator', 'creator')"
                    : "INSERT INTO legal_entity_details (party_id, legal_name, incorporation_country_code, created_by, updated_by) VALUES (?, 'Retained company', 'EC', 'creator', 'creator')";
            try (var insert = connection.prepareStatement(detailSql)) {
                insert.setObject(1, party);
                insert.executeUpdate();
            }
            for (String status : new String[]{"PENDING_VERIFICATION", "VERIFIED", "REJECTED", "EXPIRED", "REVOKED"}) {
                seedIdentifier(connection, tenant, party, status);
            }
            try (var insert = connection.prepareStatement("INSERT INTO api_idempotency_records (tenant_id, operation, idempotency_key, request_hash, party_id, result_snapshot, created_by) VALUES (?, 'party.create.v1', 'retained-key', repeat('a', 64), ?, '{\"historical\":true,\"issuerCode\":\"legacy-authority\"}', 'creator')")) {
                insert.setObject(1, tenant);
                insert.setObject(2, party);
                insert.executeUpdate();
            }
            connection.commit();
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }

    private static void seedIdentifier(Connection connection, UUID tenant, UUID party, String status) throws SQLException {
        UUID identifier = UUID.randomUUID();
        try (var insert = connection.prepareStatement("""
                INSERT INTO party_identifiers
                (id, tenant_id, party_id, identifier_scheme_id, issuer_code, encrypted_value,
                 encryption_key_version, normalized_value_hash, masked_value, normalization_version,
                 is_primary, status, issued_on, expires_on, verified_at, verified_by,
                 created_at, created_by, updated_at, updated_by, version)
                SELECT ?, ?, ?, id, 'legacy-authority', ?, 7, ?, '***retained', 3,
                       ?, ?::party_identifier_status, ?, ?, ?, ?, ?, 'creator', ?, 'maintainer', 9
                FROM identifier_schemes WHERE code = 'EC_TAX_ID'
                """)) {
            insert.setObject(1, identifier);
            insert.setObject(2, tenant);
            insert.setObject(3, party);
            insert.setString(4, "synthetic-retained-ciphertext-" + identifier);
            insert.setString(5, identifier.toString().replace("-", "").repeat(2));
            insert.setBoolean(6, !status.equals("REVOKED"));
            insert.setString(7, status);
            insert.setObject(8, ISSUED);
            insert.setObject(9, EXPIRES);
            boolean verifiedHistory = status.equals("VERIFIED") || status.equals("REVOKED");
            insert.setTimestamp(10, verifiedHistory ? ACCEPTED : null);
            insert.setString(11, verifiedHistory ? "verifier" : null);
            insert.setTimestamp(12, ACCEPTED);
            insert.setTimestamp(13, ACCEPTED);
            assertEquals(1, insert.executeUpdate());
        }
    }

    private static Map<String, String> history(Connection connection) throws SQLException {
        Map<String, String> snapshots = new HashMap<>();
        for (String table : new String[]{"parties", "natural_person_details", "legal_entity_details", "party_identifiers",
                "identifier_schemes", "api_idempotency_records", "identifier_scheme_idempotency_records", "party_outbox_events"}) {
            snapshots.put(table, scalar(connection, "SELECT COALESCE(jsonb_agg(row_data ORDER BY row_data::text), '[]'::jsonb)::text FROM (SELECT to_jsonb(t) AS row_data FROM " + table + " t) retained"));
        }
        return snapshots;
    }

    private static Map<String, String> indexes(Connection connection) throws SQLException {
        return definitions(connection, "SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'party_identifiers'");
    }

    private static Map<String, String> constraints(Connection connection) throws SQLException {
        return definitions(connection, "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'party_identifiers'::regclass");
    }

    private static Map<String, String> definitions(Connection connection, String sql) throws SQLException {
        Map<String, String> definitions = new HashMap<>();
        try (var query = connection.prepareStatement(sql); var result = query.executeQuery()) {
            while (result.next()) {
                definitions.put(result.getString(1), result.getString(2));
            }
        }
        assertTrue(!definitions.isEmpty());
        return definitions;
    }

    private static Map<String, Integer> checksums(Flyway flyway) {
        return Arrays.stream(flyway.info().applied()).collect(Collectors.toMap(
                migration -> migration.getVersion().getVersion(), MigrationInfo::getChecksum));
    }

    private static Flyway flyway(PostgreSQLContainer database, String target) {
        return Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .locations("classpath:db/migration").target(target).cleanDisabled(true).load();
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (var query = connection.prepareStatement(sql); var result = query.executeQuery()) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
