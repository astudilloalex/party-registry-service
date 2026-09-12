package com.alexastudillo.partyregistry;

import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Flyway integrity and the PostgreSQL structures required by persistence adapters.
 */
@QuarkusTest
class MigrationRegressionTest {

    private static final String V1_SHA_256 = "7da1a66b7cddb2389da0032c5f3ccef8049e75eaa77783eda37e89758235e4d7";
    private static final String V2_SHA_256 = "9dd1164d81535fcebd9b76851e910264a116c8fbf37acdd05a57960f40b3e723";
    private static final String CREATED_BY = "migration-regression-test";
    private static final Pattern QUOTED_ENUM_LITERAL = Pattern.compile("'([A-Z_]+)'");

    @Inject
    Flyway flyway;

    @Inject
    AgroalDataSource dataSource;

    @Test
    void appliesAndValidatesProductionMigrationsAndTheTestFixture() throws SQLException {
        Map<String, String> appliedMigrations = new HashMap<>();
        for (var migration : flyway.info().applied()) {
            appliedMigrations.put(migration.getVersion().getVersion(), migration.getScript());
        }

        assertEquals(Map.of(
                "1", "db/migration/V1__create_party_registry_schema.sql",
                "2", "db/migration/V2__create_api_idempotency_records.sql",
                "3", "db/migration/V3__seed_ecuador_identifier_schemes.sql",
                "1000", "db/test-migration/V1000__seed_identifier_scheme_test_fixtures.sql"),
                appliedMigrations);
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
    void seedsEcuadorSchemesAsDraftsWithDatabaseGeneratedIds() throws SQLException {
        Set<String> codes = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT * FROM identifier_schemes
                        WHERE code IN ('EC_NATIONAL_ID', 'EC_TAX_ID', 'EC_PASSPORT')
                        """)) {
            while (result.next()) {
                String code = result.getString("code");
                assertTrue(codes.add(code));
                UUID id = result.getObject("id", UUID.class);
                assertNotNull(id);
                assertEquals(7, id.version());
                assertTrue(ids.add(id));
                assertEquals("EC", result.getString("issuing_country_code"));
                assertEquals(code.substring(3), result.getString("category"));
                assertEquals(code.equals("EC_TAX_ID") ? "BOTH" : "NATURAL_PERSON",
                        result.getString("applicable_subject_type"));
                assertEquals("TRIM_UPPERCASE_V1", result.getString("normalizer_key"));
                assertEquals("ALPHANUMERIC_V1", result.getString("validator_key"));
                if (code.equals("EC_PASSPORT")) {
                    assertNull(result.getObject("minimum_length"));
                    assertNull(result.getObject("maximum_length"));
                } else {
                    int length = code.equals("EC_NATIONAL_ID") ? 10 : 13;
                    assertEquals(length, result.getInt("minimum_length"));
                    assertEquals(length, result.getInt("maximum_length"));
                }
                assertEquals(!code.equals("EC_TAX_ID"), result.getBoolean("requires_expiration"));
                assertEquals("DRAFT", result.getString("status"));
                assertNotNull(result.getTimestamp("created_at"));
                assertEquals(result.getTimestamp("created_at"), result.getTimestamp("updated_at"));
                assertEquals("system", result.getString("created_by"));
                assertEquals("system", result.getString("updated_by"));
                assertEquals(0L, result.getLong("version"));
            }
        }
        assertEquals(Set.of("EC_NATIONAL_ID", "EC_TAX_ID", "EC_PASSPORT"), codes);
    }

    @Test
    void preservesPersistenceStructureAndCriticalColumnTypes() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertColumns(connection, "identifier_schemes", Map.ofEntries(
                    column("id", "uuid", true),
                    column("code", "character varying(64)", true),
                    column("issuing_country_code", "character(2)", true),
                    column("category", "identifier_category", true),
                    column("applicable_subject_type", "identifier_subject_type", true),
                    column("name", "character varying(150)", true),
                    column("description", "character varying(500)", false),
                    column("normalizer_key", "character varying(64)", true),
                    column("validator_key", "character varying(64)", true),
                    column("minimum_length", "smallint", false),
                    column("maximum_length", "smallint", false),
                    column("requires_expiration", "boolean", true),
                    column("status", "identifier_scheme_status", true),
                    column("created_at", "timestamp with time zone", true),
                    column("created_by", "character varying(128)", true),
                    column("updated_at", "timestamp with time zone", true),
                    column("updated_by", "character varying(128)", true),
                    column("version", "bigint", true)));
            assertColumns(connection, "party_identifiers", Map.ofEntries(
                    column("id", "uuid", true),
                    column("tenant_id", "uuid", true),
                    column("party_id", "uuid", true),
                    column("identifier_scheme_id", "uuid", true),
                    column("issuer_code", "character varying(64)", false),
                    column("encrypted_value", "text", true),
                    column("encryption_key_version", "smallint", true),
                    column("normalized_value_hash", "character(64)", true),
                    column("masked_value", "character varying(64)", true),
                    column("normalization_version", "smallint", true),
                    column("is_primary", "boolean", true),
                    column("status", "party_identifier_status", true),
                    column("issued_on", "date", false),
                    column("expires_on", "date", false),
                    column("verified_at", "timestamp with time zone", false),
                    column("verified_by", "character varying(128)", false),
                    column("created_at", "timestamp with time zone", true),
                    column("created_by", "character varying(128)", true),
                    column("updated_at", "timestamp with time zone", true),
                    column("updated_by", "character varying(128)", true),
                    column("version", "bigint", true)));
            assertColumns(connection, "party_outbox_events", Map.ofEntries(
                    column("id", "uuid", true),
                    column("tenant_id", "uuid", true),
                    column("aggregate_type", "party_outbox_aggregate_type", true),
                    column("aggregate_id", "uuid", true),
                    column("aggregate_version", "bigint", true),
                    column("event_type", "character varying(128)", true),
                    column("event_schema_version", "smallint", true),
                    column("payload", "jsonb", true),
                    column("occurred_at", "timestamp with time zone", true),
                    column("correlation_id", "character varying(128)", false),
                    column("causation_id", "character varying(128)", false),
                    column("status", "outbox_status", true),
                    column("publish_attempts", "integer", true),
                    column("next_attempt_at", "timestamp with time zone", false),
                    column("last_attempt_at", "timestamp with time zone", false),
                    column("published_at", "timestamp with time zone", false),
                    column("last_error_code", "character varying(64)", false),
                    column("last_error_detail", "character varying(1000)", false),
                    column("created_at", "timestamp with time zone", true),
                    column("created_by", "character varying(128)", true),
                    column("updated_at", "timestamp with time zone", true),
                    column("updated_by", "character varying(128)", true),
                    column("version", "bigint", true)));
            assertColumns(connection, "api_idempotency_records", Map.ofEntries(
                    column("tenant_id", "uuid", true),
                    column("operation", "character varying(64)", true),
                    column("idempotency_key", "character varying(128)", true),
                    column("request_hash", "character(64)", true),
                    column("party_id", "uuid", true),
                    column("result_snapshot_schema_version", "smallint", true),
                    column("result_snapshot", "jsonb", true),
                    column("created_at", "timestamp with time zone", true),
                    column("created_by", "character varying(128)", true)));
        }
    }

    @Test
    void preservesNamedPrimaryUniqueAndForeignKeyConstraints() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertKeyConstraint(
                    connection,
                    "identifier_schemes",
                    "pk_identifier_schemes",
                    "p",
                    List.of("id"));
            assertKeyConstraint(
                    connection,
                    "identifier_schemes",
                    "uq_identifier_schemes_code",
                    "u",
                    List.of("code"));
            assertKeyConstraint(
                    connection,
                    "parties",
                    "uq_parties_tenant_id",
                    "u",
                    List.of("tenant_id", "id"));
            assertKeyConstraint(
                    connection,
                    "party_identifiers",
                    "pk_party_identifiers",
                    "p",
                    List.of("id"));
            assertForeignKeyConstraint(
                    connection,
                    "party_identifiers",
                    "fk_party_identifiers_party",
                    List.of("tenant_id", "party_id"),
                    "parties",
                    List.of("tenant_id", "id"));
            assertForeignKeyConstraint(
                    connection,
                    "party_identifiers",
                    "fk_party_identifiers_scheme",
                    List.of("identifier_scheme_id"),
                    "identifier_schemes",
                    List.of("id"));
            assertKeyConstraint(
                    connection,
                    "party_outbox_events",
                    "pk_party_outbox_events",
                    "p",
                    List.of("id"));
            assertKeyConstraint(
                    connection,
                    "party_outbox_events",
                    "uq_party_outbox_event_identity",
                    "u",
                    List.of(
                            "tenant_id",
                            "aggregate_type",
                            "aggregate_id",
                            "aggregate_version",
                            "event_type"));
            assertKeyConstraint(
                    connection,
                    "api_idempotency_records",
                    "pk_api_idempotency_records",
                    "p",
                    List.of("tenant_id", "operation", "idempotency_key"));
            assertForeignKeyConstraint(
                    connection,
                    "api_idempotency_records",
                    "fk_api_idempotency_records_party",
                    List.of("tenant_id", "party_id"),
                    "parties",
                    List.of("tenant_id", "id"));
        }
    }

    @Test
    void preservesTheActiveIdentifierPartialUniqueIndex() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement("""
                        SELECT
                            index_metadata.indisunique,
                            pg_get_expr(index_metadata.indpred, index_metadata.indrelid) AS predicate,
                            ARRAY(
                                SELECT pg_get_indexdef(
                                    index_metadata.indexrelid,
                                    key_position,
                                    true
                                )
                                FROM generate_series(
                                    1,
                                    index_metadata.indnkeyatts
                                ) AS key_positions(key_position)
                                ORDER BY key_position
                            ) AS key_columns
                        FROM pg_catalog.pg_index index_metadata
                        JOIN pg_catalog.pg_class index_relation
                            ON index_relation.oid = index_metadata.indexrelid
                        JOIN pg_catalog.pg_class table_relation
                            ON table_relation.oid = index_metadata.indrelid
                        JOIN pg_catalog.pg_namespace namespace
                            ON namespace.oid = table_relation.relnamespace
                        WHERE namespace.nspname = 'public'
                            AND table_relation.relname = 'party_identifiers'
                            AND index_relation.relname = 'uq_party_identifiers_active_value'
                        """)) {
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertTrue(result.getBoolean("indisunique"));
                assertEquals(
                        List.of("tenant_id", "identifier_scheme_id", "normalized_value_hash"),
                        List.of((String[]) result.getArray("key_columns").getArray()));

                String predicate = result.getString("predicate");
                assertNotNull(predicate);
                assertTrue(predicate.toLowerCase().contains("status"));
                assertEquals(
                        Set.of("PENDING_VERIFICATION", "VERIFIED"),
                        quotedEnumLiterals(predicate));
                assertFalse(result.next());
            }
        }
    }

    @Test
    void preservesChecksReliedOnByPersistenceAdapters() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertCheck(connection, "parties", "ck_parties_nonnegative_version", "version >= 0");
            assertCheck(
                    connection,
                    "identifier_schemes",
                    "ck_identifier_scheme_minimum_length",
                    "minimum_length",
                    "> 0");
            assertCheck(
                    connection,
                    "identifier_schemes",
                    "ck_identifier_scheme_maximum_length",
                    "maximum_length",
                    "> 0");
            assertCheck(
                    connection,
                    "identifier_schemes",
                    "ck_identifier_scheme_length_range",
                    "maximum_length >= minimum_length");
            assertCheck(
                    connection,
                    "identifier_schemes",
                    "ck_identifier_schemes_nonnegative_version",
                    "version >= 0");
            assertCheck(
                    connection,
                    "party_identifiers",
                    "ck_party_identifier_validity_dates",
                    "expires_on >= issued_on");
            assertCheck(
                    connection,
                    "party_identifiers",
                    "ck_party_identifier_verification",
                    "verified_at IS NOT NULL",
                    "verified_by IS NOT NULL");
            assertCheck(
                    connection,
                    "party_identifiers",
                    "ck_party_identifier_expired_date",
                    "expires_on IS NOT NULL");
            assertCheck(
                    connection,
                    "party_identifiers",
                    "ck_party_identifier_normalization_version",
                    "normalization_version > 0");
            assertCheck(
                    connection,
                    "party_identifiers",
                    "ck_party_identifier_encryption_key_version",
                    "encryption_key_version > 0");
            assertCheck(
                    connection,
                    "party_identifiers",
                    "ck_party_identifier_nonnegative_version",
                    "version >= 0");
            assertCheck(
                    connection,
                    "party_outbox_events",
                    "ck_party_outbox_nonnegative_aggregate_version",
                    "aggregate_version >= 0");
            assertCheck(
                    connection,
                    "party_outbox_events",
                    "ck_party_outbox_positive_schema_version",
                    "event_schema_version > 0");
            assertCheck(
                    connection,
                    "party_outbox_events",
                    "ck_party_outbox_nonnegative_attempts",
                    "publish_attempts >= 0");
            assertCheck(
                    connection,
                    "party_outbox_events",
                    "ck_party_outbox_nonnegative_version",
                    "version >= 0");
            assertCheck(
                    connection,
                    "party_outbox_events",
                    "ck_party_outbox_published_at",
                    "status = 'PUBLISHED'",
                    "published_at IS NOT NULL");
            assertCheck(
                    connection,
                    "party_outbox_events",
                    "ck_party_outbox_failed_error",
                    "status = 'FAILED'",
                    "last_error_code IS NOT NULL");
            assertCheck(
                    connection,
                    "party_outbox_events",
                    "ck_party_outbox_created_event_shape",
                    "party.created.v1",
                    "aggregate_version = 0",
                    "event_schema_version = 1");
            assertCheck(
                    connection,
                    "api_idempotency_records",
                    "ck_api_idempotency_request_hash",
                    "[0-9a-f]{64}");
            assertCheck(
                    connection,
                    "api_idempotency_records",
                    "ck_api_idempotency_positive_snapshot_schema_version",
                    "result_snapshot_schema_version > 0");
            assertCheck(
                    connection,
                    "api_idempotency_records",
                    "ck_api_idempotency_snapshot_object",
                    "jsonb_typeof(result_snapshot)",
                    "= 'object'");
        }
    }

    @Test
    void preservesPartyAndSchemeImmutabilityAndDetailShapeTriggers() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertTrigger(
                    connection,
                    "parties",
                    "trg_parties_type_immutable",
                    "reject_party_type_change",
                    false,
                    false,
                    "BEFORE",
                    "UPDATE OF type");
            assertTrigger(
                    connection,
                    "parties",
                    "ct_parties_detail_shape",
                    "enforce_party_detail_shape",
                    true,
                    true,
                    "AFTER",
                    "INSERT");
            assertTrigger(
                    connection,
                    "natural_person_details",
                    "ct_natural_person_details_party_shape",
                    "enforce_party_detail_shape",
                    true,
                    true,
                    "AFTER",
                    "INSERT",
                    "UPDATE OF party_id");
            assertTrigger(
                    connection,
                    "legal_entity_details",
                    "ct_legal_entity_details_party_shape",
                    "enforce_party_detail_shape",
                    true,
                    true,
                    "AFTER",
                    "INSERT",
                    "UPDATE OF party_id");
            assertTrigger(
                    connection,
                    "identifier_schemes",
                    "trg_identifier_scheme_activated_identity_immutable",
                    "reject_active_identifier_scheme_identity_change",
                    false,
                    false,
                    "BEFORE",
                    "UPDATE OF",
                    "code",
                    "issuing_country_code",
                    "category",
                    "applicable_subject_type");
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
        assertNull(properties.getProperty("jakarta.persistence.schema-generation.database.action"));
        assertEquals("db/migration", properties.getProperty("quarkus.flyway.locations"));
        assertEquals("true", properties.getProperty("quarkus.flyway.migrate-at-start"));
        assertEquals("true", properties.getProperty("quarkus.flyway.validate-on-migrate"));
        assertEquals("true", properties.getProperty("quarkus.flyway.clean-disabled"));
        assertEquals(
                "db/migration,db/test-migration",
                properties.getProperty("%test.quarkus.flyway.locations"));
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
                ) VALUES (?, ?, ?, ?, ?, 1, ?::jsonb, ?)
                """)) {
            insert.setObject(1, tenantId);
            insert.setString(2, RegisterNaturalPersonCommand.LEGACY_OPERATION);
            insert.setString(3, idempotencyKey);
            insert.setString(4, requestHash);
            insert.setObject(5, partyId);
            insert.setString(6, "{\"schemaVersion\":1,\"partyId\":\"" + partyId + "\"}");
            insert.setString(7, CREATED_BY);
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
                    AND operation = ?
                    AND idempotency_key = ?
                """)) {
            query.setObject(1, tenantId);
            query.setString(2, RegisterNaturalPersonCommand.LEGACY_OPERATION);
            query.setString(3, idempotencyKey);

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

    private static Map.Entry<String, ColumnDefinition> column(
            String name,
            String sqlType,
            boolean notNull) {
        return Map.entry(name, new ColumnDefinition(sqlType, notNull));
    }

    private static void assertColumns(
            Connection connection,
            String tableName,
            Map<String, ColumnDefinition> expected) throws SQLException {
        Map<String, ColumnDefinition> actual = new HashMap<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT
                    attribute.attname,
                    pg_catalog.format_type(attribute.atttypid, attribute.atttypmod) AS sql_type,
                    attribute.attnotnull
                FROM pg_catalog.pg_attribute attribute
                JOIN pg_catalog.pg_class relation
                    ON relation.oid = attribute.attrelid
                JOIN pg_catalog.pg_namespace namespace
                    ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                    AND relation.relname = ?
                    AND attribute.attnum > 0
                    AND NOT attribute.attisdropped
                """)) {
            query.setString(1, tableName);
            try (var result = query.executeQuery()) {
                while (result.next()) {
                    actual.put(
                            result.getString("attname"),
                            new ColumnDefinition(
                                    result.getString("sql_type"),
                                    result.getBoolean("attnotnull")));
                }
            }
        }

        assertEquals(expected, actual, tableName);
    }

    private static void assertKeyConstraint(
            Connection connection,
            String tableName,
            String constraintName,
            String constraintType,
            List<String> expectedColumns) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT
                    constraint_metadata.contype::text AS constraint_type,
                    ARRAY(
                        SELECT key_attribute.attname
                        FROM unnest(constraint_metadata.conkey)
                            WITH ORDINALITY AS key_columns(attribute_number, position)
                        JOIN pg_catalog.pg_attribute key_attribute
                            ON key_attribute.attrelid = constraint_metadata.conrelid
                            AND key_attribute.attnum = key_columns.attribute_number
                        ORDER BY key_columns.position
                    ) AS key_columns
                FROM pg_catalog.pg_constraint constraint_metadata
                JOIN pg_catalog.pg_class relation
                    ON relation.oid = constraint_metadata.conrelid
                JOIN pg_catalog.pg_namespace namespace
                    ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                    AND relation.relname = ?
                    AND constraint_metadata.conname = ?
                """)) {
            query.setString(1, tableName);
            query.setString(2, constraintName);
            try (var result = query.executeQuery()) {
                assertTrue(result.next(), constraintName);
                assertEquals(constraintType, result.getString("constraint_type"), constraintName);
                assertEquals(
                        expectedColumns,
                        List.of((String[]) result.getArray("key_columns").getArray()),
                        constraintName);
                assertFalse(result.next(), constraintName);
            }
        }
    }

    private static void assertForeignKeyConstraint(
            Connection connection,
            String tableName,
            String constraintName,
            List<String> expectedColumns,
            String expectedReferencedTable,
            List<String> expectedReferencedColumns) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT
                    referenced_relation.relname AS referenced_table,
                    constraint_metadata.confdeltype::text AS delete_action,
                    ARRAY(
                        SELECT key_attribute.attname
                        FROM unnest(constraint_metadata.conkey)
                            WITH ORDINALITY AS key_columns(attribute_number, position)
                        JOIN pg_catalog.pg_attribute key_attribute
                            ON key_attribute.attrelid = constraint_metadata.conrelid
                            AND key_attribute.attnum = key_columns.attribute_number
                        ORDER BY key_columns.position
                    ) AS key_columns,
                    ARRAY(
                        SELECT referenced_attribute.attname
                        FROM unnest(constraint_metadata.confkey)
                            WITH ORDINALITY AS referenced_columns(attribute_number, position)
                        JOIN pg_catalog.pg_attribute referenced_attribute
                            ON referenced_attribute.attrelid = constraint_metadata.confrelid
                            AND referenced_attribute.attnum = referenced_columns.attribute_number
                        ORDER BY referenced_columns.position
                    ) AS referenced_columns
                FROM pg_catalog.pg_constraint constraint_metadata
                JOIN pg_catalog.pg_class relation
                    ON relation.oid = constraint_metadata.conrelid
                JOIN pg_catalog.pg_namespace namespace
                    ON namespace.oid = relation.relnamespace
                JOIN pg_catalog.pg_class referenced_relation
                    ON referenced_relation.oid = constraint_metadata.confrelid
                WHERE namespace.nspname = 'public'
                    AND relation.relname = ?
                    AND constraint_metadata.conname = ?
                    AND constraint_metadata.contype = 'f'
                """)) {
            query.setString(1, tableName);
            query.setString(2, constraintName);
            try (var result = query.executeQuery()) {
                assertTrue(result.next(), constraintName);
                assertEquals(
                        expectedColumns,
                        List.of((String[]) result.getArray("key_columns").getArray()),
                        constraintName);
                assertEquals(expectedReferencedTable, result.getString("referenced_table"), constraintName);
                assertEquals(
                        expectedReferencedColumns,
                        List.of((String[]) result.getArray("referenced_columns").getArray()),
                        constraintName);
                assertEquals("r", result.getString("delete_action"), constraintName);
                assertFalse(result.next(), constraintName);
            }
        }
    }

    private static void assertCheck(
            Connection connection,
            String tableName,
            String constraintName,
            String... expectedFragments) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT pg_get_constraintdef(constraint_metadata.oid, true) AS definition
                FROM pg_catalog.pg_constraint constraint_metadata
                JOIN pg_catalog.pg_class relation
                    ON relation.oid = constraint_metadata.conrelid
                JOIN pg_catalog.pg_namespace namespace
                    ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                    AND relation.relname = ?
                    AND constraint_metadata.conname = ?
                    AND constraint_metadata.contype = 'c'
                """)) {
            query.setString(1, tableName);
            query.setString(2, constraintName);
            try (var result = query.executeQuery()) {
                assertTrue(result.next(), constraintName);
                String definition = canonicalSql(result.getString("definition"));
                for (String fragment : expectedFragments) {
                    assertTrue(
                            definition.contains(canonicalSql(fragment)),
                            () -> constraintName + " does not contain " + fragment + ": " + definition);
                }
                assertFalse(result.next(), constraintName);
            }
        }
    }

    private static void assertTrigger(
            Connection connection,
            String tableName,
            String triggerName,
            String functionName,
            boolean deferrable,
            boolean initiallyDeferred,
            String... expectedDefinitionFragments) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT
                    trigger_metadata.tgenabled::text AS enabled,
                    trigger_metadata.tgdeferrable,
                    trigger_metadata.tginitdeferred,
                    function_metadata.proname,
                    pg_get_triggerdef(trigger_metadata.oid, true) AS definition
                FROM pg_catalog.pg_trigger trigger_metadata
                JOIN pg_catalog.pg_class relation
                    ON relation.oid = trigger_metadata.tgrelid
                JOIN pg_catalog.pg_namespace namespace
                    ON namespace.oid = relation.relnamespace
                JOIN pg_catalog.pg_proc function_metadata
                    ON function_metadata.oid = trigger_metadata.tgfoid
                WHERE namespace.nspname = 'public'
                    AND relation.relname = ?
                    AND trigger_metadata.tgname = ?
                    AND NOT trigger_metadata.tgisinternal
                """)) {
            query.setString(1, tableName);
            query.setString(2, triggerName);
            try (var result = query.executeQuery()) {
                assertTrue(result.next(), triggerName);
                assertEquals("O", result.getString("enabled"), triggerName);
                assertEquals(deferrable, result.getBoolean("tgdeferrable"), triggerName);
                assertEquals(initiallyDeferred, result.getBoolean("tginitdeferred"), triggerName);
                assertEquals(functionName, result.getString("proname"), triggerName);
                String definition = canonicalSql(result.getString("definition"));
                for (String fragment : expectedDefinitionFragments) {
                    assertTrue(
                            definition.contains(canonicalSql(fragment)),
                            () -> triggerName + " does not contain " + fragment + ": " + definition);
                }
                assertFalse(result.next(), triggerName);
            }
        }
    }

    private static Set<String> quotedEnumLiterals(String expression) {
        Set<String> literals = new HashSet<>();
        Matcher matcher = QUOTED_ENUM_LITERAL.matcher(expression);
        while (matcher.find()) {
            literals.add(matcher.group(1));
        }
        return Set.copyOf(literals);
    }

    private static String canonicalSql(String sql) {
        return sql.replaceAll("\\s+", "").toLowerCase();
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

    /**
     * Describes one exact PostgreSQL column type and nullability contract.
     */
    private record ColumnDefinition(String sqlType, boolean notNull) {
    }
}
