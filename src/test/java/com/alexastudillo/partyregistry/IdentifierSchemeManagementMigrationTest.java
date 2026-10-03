package com.alexastudillo.partyregistry;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies additive scheme replay storage on fresh installations and immutable V6 upgrades. */
@Timeout(120)
class IdentifierSchemeManagementMigrationTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void installsDedicatedReplayStorageAndGlobalOrderingWithoutChangingHistory(boolean upgrade) throws SQLException {
        try (var database = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))) {
            database.withStartupTimeout(Duration.ofSeconds(60));
            database.start();
            Map<String, Integer> previousChecksums = Map.of();
            String previousCatalog = null;
            String previousPartyReplay = null;
            if (upgrade) {
                Flyway previous = migration(database, "6");
                assertEquals(6, previous.migrate().migrationsExecuted);
                previousChecksums = checksums(previous);
                try (Connection connection = connection(database)) {
                    assertNull(scalar(connection, "SELECT to_regclass('identifier_scheme_idempotency_records')::text"));
                    assertNull(scalar(connection, "SELECT to_regclass('ix_identifier_schemes_created_id')::text"));
                    seedPartyReplay(connection);
                    previousCatalog = catalog(connection);
                    previousPartyReplay = partyReplay(connection);
                }
            }
            Flyway current = migration(database, "7");
            assertEquals(upgrade ? 1 : 7, current.migrate().migrationsExecuted);
            current.validate();
            Map<String, Integer> currentChecksums = checksums(current);
            assertEquals(7, currentChecksums.size());
            previousChecksums.forEach((version, checksum) -> assertEquals(checksum, currentChecksums.get(version)));
            assertEquals(0, current.migrate().migrationsExecuted);
            try (Connection connection = connection(database)) {
                if (upgrade) {
                    assertEquals(previousCatalog, catalog(connection));
                    assertEquals(previousPartyReplay, partyReplay(connection));
                }
                assertStructure(connection);
                assertReplayConstraints(connection);
            }
        }
    }

    private static void assertStructure(Connection connection) throws SQLException {
        Map<String, String> columns = new HashMap<>();
        try (var query = connection.prepareStatement("""
                SELECT attname, format_type(atttypid, atttypmod), attnotnull
                FROM pg_attribute WHERE attrelid = 'identifier_scheme_idempotency_records'::regclass
                  AND attnum > 0 AND NOT attisdropped
                """); var result = query.executeQuery()) {
            while (result.next()) {
                assertTrue(result.getBoolean(3));
                columns.put(result.getString(1), result.getString(2));
            }
        }
        assertEquals(Map.of("tenant_id", "uuid", "operation", "character varying(64)",
                "idempotency_key", "character varying(128)", "request_hash", "character(64)",
                "identifier_scheme_id", "uuid", "result_snapshot_schema_version", "smallint",
                "result_snapshot", "jsonb", "created_at", "timestamp with time zone",
                "created_by", "character varying(128)"), columns);
        assertEquals("PRIMARY KEY (tenant_id, operation, idempotency_key)", scalar(connection, """
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conrelid = 'identifier_scheme_idempotency_records'::regclass
                  AND conname = 'pk_identifier_scheme_idempotency_records'
                """));
        assertEquals("FOREIGN KEY (identifier_scheme_id) REFERENCES identifier_schemes(id) ON DELETE RESTRICT",
                scalar(connection, """
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conrelid = 'identifier_scheme_idempotency_records'::regclass
                  AND conname = 'fk_identifier_scheme_idempotency_scheme'
                """));
        assertEquals("1", scalar(connection, """
                SELECT count(*)::text FROM pg_constraint
                WHERE conrelid = 'identifier_scheme_idempotency_records'::regclass AND contype = 'f'
                """));
        assertEquals("CREATE INDEX ix_identifier_schemes_created_id ON public.identifier_schemes USING btree (created_at, id)",
                scalar(connection, "SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_identifier_schemes_created_id'"));
        assertEquals("CREATE INDEX ix_identifier_scheme_idempotency_scheme ON public.identifier_scheme_idempotency_records USING btree (identifier_scheme_id)",
                scalar(connection, "SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_identifier_scheme_idempotency_scheme'"));
    }

    private static void assertReplayConstraints(Connection connection) throws SQLException {
        UUID tenant = UUID.randomUUID();
        UUID scheme = UUID.fromString(scalar(connection, "SELECT id::text FROM identifier_schemes WHERE code = 'EC_TAX_ID'"));
        insert(connection, tenant, scheme, "identifier-scheme.create.v1", " exact-key ", "a".repeat(64), 1, "{}", "actor");
        assertEquals("actor", scalar(connection, "SELECT created_by FROM identifier_scheme_idempotency_records"));
        assertNotNull(scalar(connection, "SELECT created_at::text FROM identifier_scheme_idempotency_records"));
        assertConstraint("23505", "pk_identifier_scheme_idempotency_records", () ->
                insert(connection, tenant, scheme, "identifier-scheme.create.v1", " exact-key ", "a".repeat(64), 1, "{}", "actor"));
        // Replay scope is tenant/action/key; schemes have no tenant ownership.
        insert(connection, UUID.randomUUID(), scheme, "identifier-scheme.create.v1", " exact-key ", "b".repeat(64), 1, "{}", "actor");
        for (String action : new String[]{"activate", "deprecate", "retire"}) {
            insert(connection, tenant, scheme, "identifier-scheme." + action + ".v1", " exact-key ", "c".repeat(64), 1, "{}", "actor");
        }
        insert(connection, tenant, scheme, "identifier-scheme.create.v1", "exact-key", "d".repeat(64), 32767, "{}", "actor");
        assertEquals("6", scalar(connection, "SELECT count(*)::text FROM identifier_scheme_idempotency_records"));
        for (String hash : new String[]{"A".repeat(64), "a".repeat(63), "a".repeat(63) + "g"}) {
            assertConstraint("23514", "ck_identifier_scheme_idempotency_request_hash", () ->
                    insert(connection, tenant, scheme, "op", "hash", hash, 1, "{}", "actor"));
        }
        for (int version : new int[]{0, -1}) {
            assertConstraint("23514", "ck_identifier_scheme_idempotency_positive_snapshot_version", () ->
                    insert(connection, tenant, scheme, "op", "version", "a".repeat(64), version, "{}", "actor"));
        }
        for (String json : new String[]{"[]", "null", "1", "true", "\"text\""}) {
            assertConstraint("23514", "ck_identifier_scheme_idempotency_snapshot_object", () ->
                    insert(connection, tenant, scheme, "op", "json", "a".repeat(64), 1, json, "actor"));
        }
        assertConstraint("23514", "ck_identifier_scheme_idempotency_nonblank_operation", () ->
                insert(connection, tenant, scheme, " ", "key", "a".repeat(64), 1, "{}", "actor"));
        assertConstraint("23514", "ck_identifier_scheme_idempotency_nonblank_key", () ->
                insert(connection, tenant, scheme, "op", " ", "a".repeat(64), 1, "{}", "actor"));
        assertConstraint("23514", "ck_identifier_scheme_idempotency_nonblank_created_by", () ->
                insert(connection, tenant, scheme, "op", "key", "a".repeat(64), 1, "{}", " "));
        assertConstraint("23503", "fk_identifier_scheme_idempotency_scheme", () ->
                insert(connection, tenant, UUID.randomUUID(), "op", "missing", "a".repeat(64), 1, "{}", "actor"));
        assertConstraint("23001", "fk_identifier_scheme_idempotency_scheme", () -> {
            try (var delete = connection.prepareStatement("DELETE FROM identifier_schemes WHERE id = ?")) {
                delete.setObject(1, scheme);
                delete.executeUpdate();
            }
        });
    }

    private static void insert(Connection connection, UUID tenant, UUID scheme, String operation, String key,
            String hash, int version, String json, String actor) throws SQLException {
        try (var insert = connection.prepareStatement("""
                INSERT INTO identifier_scheme_idempotency_records
                (tenant_id, operation, idempotency_key, request_hash, identifier_scheme_id,
                 result_snapshot_schema_version, result_snapshot, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """)) {
            insert.setObject(1, tenant);
            insert.setString(2, operation);
            insert.setString(3, key);
            insert.setString(4, hash);
            insert.setObject(5, scheme);
            insert.setInt(6, version);
            insert.setString(7, json);
            insert.setString(8, actor);
            insert.executeUpdate();
        }
    }

    private static void seedPartyReplay(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO parties (id, tenant_id, type, display_name, created_by, updated_by)
                    VALUES ('01991a56-f000-7000-8000-000000000301', '01991a56-f000-7000-8000-000000000302',
                            'NATURAL_PERSON', 'Historical', 'creator', 'creator')
                    """);
            statement.executeUpdate("""
                    INSERT INTO natural_person_details (party_id, given_names, family_names, created_by, updated_by)
                    VALUES ('01991a56-f000-7000-8000-000000000301', 'Historical', 'Name', 'creator', 'creator')
                    """);
            statement.executeUpdate("""
                    INSERT INTO api_idempotency_records
                    (tenant_id, operation, idempotency_key, request_hash, party_id, result_snapshot, created_by)
                    VALUES ('01991a56-f000-7000-8000-000000000302', 'historical.party.v1', 'key', repeat('a', 64),
                            '01991a56-f000-7000-8000-000000000301', '{"historical":true}', 'creator')
                    """);
            connection.commit();
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static String catalog(Connection connection) throws SQLException {
        return scalar(connection, "SELECT jsonb_agg(to_jsonb(s) ORDER BY code)::text FROM identifier_schemes s");
    }

    private static String partyReplay(Connection connection) throws SQLException {
        return scalar(connection, "SELECT jsonb_agg(to_jsonb(r))::text FROM api_idempotency_records r");
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (var query = connection.prepareStatement(sql); var result = query.executeQuery()) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static void assertConstraint(String state, String name, org.junit.jupiter.api.function.Executable operation) {
        PSQLException failure = assertThrows(PSQLException.class, operation);
        assertEquals(state, failure.getSQLState());
        assertNotNull(failure.getServerErrorMessage());
        assertEquals(name, failure.getServerErrorMessage().getConstraint());
        assertTrue(name.length() <= 63);
    }

    private static Flyway migration(PostgreSQLContainer database, String target) {
        return Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .locations("classpath:db/migration").target(target).cleanDisabled(true).load();
    }

    private static Connection connection(PostgreSQLContainer database) throws SQLException {
        return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

    private static Map<String, Integer> checksums(Flyway flyway) {
        return Arrays.stream(flyway.info().applied()).collect(Collectors.toMap(
                migration -> migration.getVersion().getVersion(), MigrationInfo::getChecksum));
    }
}
