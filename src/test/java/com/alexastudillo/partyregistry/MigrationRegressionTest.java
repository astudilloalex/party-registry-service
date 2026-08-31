package com.alexastudillo.partyregistry;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.postgresql.util.PSQLException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Flyway migration integrity and PostgreSQL party-shape constraints.
 */
@QuarkusTest
class MigrationRegressionTest {

    private static final String V1_SHA_256 = "7da1a66b7cddb2389da0032c5f3ccef8049e75eaa77783eda37e89758235e4d7";
    private static final String V2_SHA_256 = "9dd1164d81535fcebd9b76851e910264a116c8fbf37acdd05a57960f40b3e723";
    private static final String CREATED_BY = "migration-regression-test";

    @Inject
    Flyway flyway;

    @Inject
    AgroalDataSource dataSource;

    @Test
    void appliesAndValidatesBothMigrations() throws SQLException {
        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion())
                .toList();

        assertEquals(List.of("1", "2"), appliedVersions);
        assertDoesNotThrow(flyway::validate);

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT to_regclass('public.api_idempotency_records')::text")) {
            result.next();
            assertEquals("api_idempotency_records", result.getString(1));
        }
    }

    @Test
    void preservesTheFirstMigrationChecksum() throws IOException, NoSuchAlgorithmException {
        assertMigrationChecksum(
                "/db/migration/V1__create_party_registry_schema.sql",
                V1_SHA_256);
    }

    @Test
    void preservesTheSecondMigrationChecksum() throws IOException, NoSuchAlgorithmException {
        assertMigrationChecksum(
                "/db/migration/V2__create_api_idempotency_records.sql",
                V2_SHA_256);
    }

    private static void assertMigrationChecksum(String resource, String expectedHash)
            throws IOException, NoSuchAlgorithmException {
        try (InputStream migration = MigrationRegressionTest.class.getResourceAsStream(resource)) {
            assertNotNull(migration);
            String actualHash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(migration.readAllBytes()));
            assertEquals(expectedHash, actualHash);
        }
    }

    @Test
    void keepsHibernateSchemaGenerationDisabled() throws IOException {
        Properties properties = new Properties();
        try (InputStream configuration = MigrationRegressionTest.class.getResourceAsStream(
                "/application.properties")) {
            assertNotNull(configuration);
            properties.load(configuration);
        }

        assertEquals(
                "validate",
                properties.getProperty("quarkus.hibernate-orm.schema-management.strategy"));
        assertNull(properties.getProperty("quarkus.hibernate-orm.database.generation"));
    }

    @Test
    void rejectsDuplicateIdempotencyKeysAndMalformedHashes() throws SQLException {
        UUID tenantId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();

        try (Connection connection = dataSource.getConnection()) {
            insertNaturalPerson(connection, tenantId, partyId);
            insertIdempotencyRecord(connection, tenantId, partyId, "create-key", "a".repeat(64));
            assertStoredSnapshot(connection, tenantId, partyId, "create-key");

            SQLException duplicate = assertThrows(
                    SQLException.class,
                    () -> insertIdempotencyRecord(
                            connection,
                            tenantId,
                            partyId,
                            "create-key",
                            "a".repeat(64)));
            assertPostgresConstraint(duplicate, "23505", "pk_api_idempotency_records");

            SQLException malformedHash = assertThrows(
                    SQLException.class,
                    () -> insertIdempotencyRecord(
                            connection,
                            tenantId,
                            partyId,
                            "malformed-hash-key",
                            "A".repeat(64)));
            assertPostgresConstraint(malformedHash, "23514", "ck_api_idempotency_request_hash");
        }
    }

    @Test
    void keepsPartyTypeImmutableAfterV2() throws SQLException {
        UUID tenantId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();

        try (Connection connection = dataSource.getConnection()) {
            insertNaturalPerson(connection, tenantId, partyId);

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE parties SET type = 'LEGAL_ENTITY' WHERE id = ?")) {
                update.setObject(1, partyId);
                SQLException failure = assertThrows(SQLException.class, update::executeUpdate);
                assertPostgresConstraint(failure, "23514", "ck_parties_type_immutable");
            }
        }
    }

    @Test
    void rejectsNaturalPersonDetailsForLegalEntitiesAfterV2() throws SQLException {
        UUID tenantId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            insertParty(connection, tenantId, partyId, "LEGAL_ENTITY");
            insertNaturalPersonDetails(connection, partyId);

            SQLException failure = assertThrows(SQLException.class, () -> forceDeferredConstraints(connection));
            assertPostgresConstraint(failure, "23514", "ck_parties_detail_shape");
            connection.rollback();
        }
    }

    @Test
    void rejectsDualPartyDetailsAfterV2() throws SQLException {
        UUID tenantId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            insertParty(connection, tenantId, partyId, "NATURAL_PERSON");
            insertNaturalPersonDetails(connection, partyId);
            insertLegalEntityDetails(connection, partyId);

            SQLException failure = assertThrows(SQLException.class, () -> forceDeferredConstraints(connection));
            assertPostgresConstraint(failure, "23514", "ck_parties_detail_shape");
            connection.rollback();
        }
    }

    private static void insertNaturalPerson(Connection connection, UUID tenantId, UUID partyId)
            throws SQLException {
        insertParty(connection, tenantId, partyId, "NATURAL_PERSON");
        insertNaturalPersonDetails(connection, partyId);
    }

    private static void insertParty(
            Connection connection,
            UUID tenantId,
            UUID partyId,
            String partyType) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO parties (id, tenant_id, type, display_name, created_by, updated_by)
                VALUES (?, ?, ?::party_type, 'Migration Test Party', ?, ?)
                """)) {
            insert.setObject(1, partyId);
            insert.setObject(2, tenantId);
            insert.setString(3, partyType);
            insert.setString(4, CREATED_BY);
            insert.setString(5, CREATED_BY);
            insert.executeUpdate();
        }
    }

    private static void insertNaturalPersonDetails(Connection connection, UUID partyId)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO natural_person_details (
                    party_id, given_names, family_names, created_by, updated_by
                ) VALUES (?, 'Migration', 'Test', ?, ?)
                """)) {
            insert.setObject(1, partyId);
            insert.setString(2, CREATED_BY);
            insert.setString(3, CREATED_BY);
            insert.executeUpdate();
        }
    }

    private static void insertLegalEntityDetails(Connection connection, UUID partyId)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO legal_entity_details (
                    party_id, legal_name, incorporation_country_code, created_by, updated_by
                ) VALUES (?, 'Migration Test Company', 'EC', ?, ?)
                """)) {
            insert.setObject(1, partyId);
            insert.setString(2, CREATED_BY);
            insert.setString(3, CREATED_BY);
            insert.executeUpdate();
        }
    }

    private static void insertIdempotencyRecord(
            Connection connection,
            UUID tenantId,
            UUID partyId,
            String idempotencyKey,
            String requestHash) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO api_idempotency_records (
                    tenant_id,
                    operation,
                    idempotency_key,
                    request_hash,
                    party_id,
                    result_snapshot_schema_version,
                    result_snapshot,
                    created_by
                ) VALUES (?, 'CREATE_NATURAL_PERSON', ?, ?, ?, 1, ?::jsonb, ?)
                """)) {
            insert.setObject(1, tenantId);
            insert.setString(2, idempotencyKey);
            insert.setString(3, requestHash);
            insert.setObject(4, partyId);
            insert.setString(5, "{\"schemaVersion\":1,\"partyId\":\"" + partyId + "\"}");
            insert.setString(6, CREATED_BY);
            insert.executeUpdate();
        }
    }

    private static void assertStoredSnapshot(
            Connection connection,
            UUID tenantId,
            UUID partyId,
            String idempotencyKey) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT
                    party_id,
                    result_snapshot_schema_version,
                    result_snapshot ->> 'schemaVersion',
                    created_at,
                    created_by
                FROM api_idempotency_records
                WHERE tenant_id = ?
                    AND operation = 'CREATE_NATURAL_PERSON'
                    AND idempotency_key = ?
                """)) {
            query.setObject(1, tenantId);
            query.setString(2, idempotencyKey);

            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals(partyId, result.getObject("party_id", UUID.class));
                assertEquals(1, result.getInt("result_snapshot_schema_version"));
                assertEquals("1", result.getString(3));
                assertNotNull(result.getTimestamp("created_at"));
                assertEquals(CREATED_BY, result.getString("created_by"));
            }
        }
    }

    private static void forceDeferredConstraints(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET CONSTRAINTS ALL IMMEDIATE");
        }
    }

    private static void assertPostgresConstraint(
            SQLException failure,
            String sqlState,
            String constraintName) {
        PSQLException postgresFailure = assertInstanceOf(PSQLException.class, failure);

        assertEquals(sqlState, postgresFailure.getSQLState());
        assertNotNull(postgresFailure.getServerErrorMessage());
        assertEquals(constraintName, postgresFailure.getServerErrorMessage().getConstraint());
    }
}
