package com.alexastudillo.partyregistry;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the listing index on clean installation and V4 upgrade using exclusively Flyway-managed DDL.
 */
@Timeout(120)
class PartyListingMigrationTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void installsTheOrderedTenantIndexWithoutChangingEarlierMigrationsOrStoredData(boolean upgradeFromV4)
            throws SQLException {
        try (var database = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))) {
            database.withStartupTimeout(Duration.ofSeconds(60));
            database.start();
            Map<String, Integer> previousChecksums = Map.of();
            UUID partyId = UUID.randomUUID();
            if (upgradeFromV4) {
                Flyway previous = migration(database, "4");
                assertEquals(4, previous.migrate().migrationsExecuted);
                previousChecksums = checksums(previous);
                try (Connection connection = connection(database)) {
                    assertIndexAbsent(connection);
                    insertHistoricalParty(connection, partyId);
                }
            }

            Flyway current = migration(database, "5");
            assertEquals(upgradeFromV4 ? 1 : 5, current.migrate().migrationsExecuted);
            current.validate();
            Map<String, Integer> currentChecksums = checksums(current);
            previousChecksums.forEach((version, checksum) -> assertEquals(checksum, currentChecksums.get(version)));
            assertEquals(5, currentChecksums.size());
            assertEquals(0, current.migrate().migrationsExecuted);

            try (Connection connection = connection(database)) {
                assertIndexDefinition(connection);
                if (upgradeFromV4) {
                    assertHistoricalParty(connection, partyId);
                }
            }
        }
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

    private static void assertIndexAbsent(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement("SELECT to_regclass('public.ix_parties_tenant_created_id')");
                var result = query.executeQuery()) {
            assertTrue(result.next());
            assertNull(result.getObject(1));
        }
    }

    private static void assertIndexDefinition(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT indexdef FROM pg_catalog.pg_indexes
                WHERE schemaname = 'public' AND tablename = 'parties'
                  AND indexname = 'ix_parties_tenant_created_id'
                """); var result = query.executeQuery()) {
            assertTrue(result.next());
            assertEquals("CREATE INDEX ix_parties_tenant_created_id ON public.parties USING btree "
                    + "(tenant_id, created_at DESC, id DESC)", result.getString(1));
            assertFalse(result.next());
        }
    }

    private static void insertHistoricalParty(Connection connection, UUID partyId) throws SQLException {
        connection.setAutoCommit(false);
        try (var party = connection.prepareStatement("""
                INSERT INTO parties (id, tenant_id, type, display_name, record_status, version,
                                     created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, 'NATURAL_PERSON', 'Historical Label', 'ARCHIVED', 7, ?, 'creator', ?, 'archivist')
                """); var details = connection.prepareStatement("""
                INSERT INTO natural_person_details (party_id, given_names, family_names, created_by, updated_by)
                VALUES (?, 'Historical', 'Name', 'creator', 'creator')
                """)) {
            var timestamp = LocalDateTime.of(2026, Month.SEPTEMBER, 19, 12, 0, 0, 123456000).atOffset(ZoneOffset.UTC);
            party.setObject(1, partyId);
            party.setObject(2, UUID.randomUUID());
            party.setObject(3, timestamp);
            party.setObject(4, timestamp);
            party.executeUpdate();
            details.setObject(1, partyId);
            details.executeUpdate();
            connection.commit();
        }
    }

    private static void assertHistoricalParty(Connection connection, UUID partyId) throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT p.display_name, p.record_status, p.version, p.created_by, p.updated_by, d.given_names
                FROM parties p JOIN natural_person_details d ON d.party_id = p.id WHERE p.id = ?
                """)) {
            query.setObject(1, partyId);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals("Historical Label", result.getString(1));
                assertEquals("ARCHIVED", result.getString(2));
                assertEquals(7, result.getLong(3));
                assertEquals("creator", result.getString(4));
                assertEquals("archivist", result.getString(5));
                assertEquals("Historical", result.getString(6));
            }
        }
    }
}
