package com.alexastudillo.partyregistry.infrastructure.persistence.party;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

import com.alexastudillo.partyregistry.infrastructure.persistence.PostgreSqlTestResource;

import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

@QuarkusTest
@QuarkusTestResource(value = PostgreSqlTestResource.class, restrictToAnnotatedClass = true)
@TestProfile(PartyRootMigrationTest.MigrationOnlyProfile.class)
class PartyRootMigrationTest {

    @Test
    void createsDbmlRootEnumsTableChecksAndIndexes() throws SQLException {
        try (Connection connection = dataSource().getConnection()) {
            assertEquals(List.of("NATURAL_PERSON", "LEGAL_ENTITY"), enumValues(connection, "party_type"));
            assertEquals(List.of("DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED"),
                    enumValues(connection, "party_record_status"));
            assertTrue(exists(connection, "SELECT to_regclass('public.parties') IS NOT NULL"));
            assertRootColumns(columns(connection));

            assertEquals(
                    Set.of(
                            "ck_parties_nonblank_display_name",
                            "ck_parties_nonblank_created_by",
                            "ck_parties_nonblank_updated_by",
                            "ck_parties_nonnegative_version"),
                    names(connection, """
                            SELECT conname
                            FROM pg_constraint
                            WHERE conrelid = 'public.parties'::regclass
                              AND contype = 'c'
                            """));
            Map<String, String> indexes = keyedValues(connection, """
                    SELECT indexname
                         , indexdef
                    FROM pg_indexes
                    WHERE schemaname = 'public' AND tablename = 'parties'
                    """);
            assertEquals(Set.of(
                            "parties_pkey",
                            "uq_parties_tenant_id",
                            "ix_parties_tenant_type_status",
                            "ix_parties_tenant_display_name"),
                    indexes.keySet());
            assertTrue(indexes.get("uq_parties_tenant_id").contains("UNIQUE"));
            assertTrue(indexes.get("uq_parties_tenant_id").contains("(tenant_id, id)"));
            assertTrue(indexes.get("ix_parties_tenant_type_status").contains("(tenant_id, type, record_status)"));
            assertTrue(indexes.get("ix_parties_tenant_display_name").contains("(tenant_id, display_name)"));
            assertTrue(exists(connection, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_trigger
                        WHERE tgrelid = 'public.parties'::regclass
                          AND tgname = 'trg_parties_type_immutable'
                          AND NOT tgisinternal
                    )
                    """));
            assertTrue(exists(connection, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_proc
                        WHERE proname = 'reject_party_type_change'
                    )
                    """));
        }

        assertConstraintViolation(
                "INSERT INTO parties (tenant_id, type, display_name, created_by, updated_by) "
                        + "VALUES (uuidv7(), 'NATURAL_PERSON', ' ', 'subject', 'subject')",
                "ck_parties_nonblank_display_name");
        assertConstraintViolation(
                "INSERT INTO parties (tenant_id, type, display_name, created_by, updated_by) "
                        + "VALUES (uuidv7(), 'NATURAL_PERSON', 'Name', ' ', 'subject')",
                "ck_parties_nonblank_created_by");
        assertConstraintViolation(
                "INSERT INTO parties (tenant_id, type, display_name, created_by, updated_by) "
                        + "VALUES (uuidv7(), 'NATURAL_PERSON', 'Name', 'subject', ' ')",
                "ck_parties_nonblank_updated_by");
        assertConstraintViolation(
                "INSERT INTO parties (tenant_id, type, display_name, created_by, updated_by, version) "
                        + "VALUES (uuidv7(), 'NATURAL_PERSON', 'Name', 'subject', 'subject', -1)",
                "ck_parties_nonnegative_version");
    }

    @Test
    void rejectsTypeChangesButAllowsNoOpAssignments() throws SQLException {
        try (Connection connection = dataSource().getConnection()) {
            connection.setAutoCommit(false);
            InsertedParty inserted = insertParty(connection);

            assertEquals(7, inserted.id().version());
            assertEquals("DRAFT", inserted.recordStatus());
            assertEquals(0L, inserted.version());

            assertDoesNotThrow(() -> updateType(connection, inserted.id(), "NATURAL_PERSON"));
            SQLException failure = assertThrows(SQLException.class,
                    () -> updateType(connection, inserted.id(), "LEGAL_ENTITY"));
            assertEquals("23514", failure.getSQLState());
            connection.rollback();
        }
    }

    private static DataSource dataSource() {
        Instance<DataSource> selected = CDI.current().select(DataSource.class, new DataSourceLiteral("flyway"));
        if (!selected.isResolvable()) {
            throw new IllegalStateException("The isolated Flyway datasource is not available");
        }
        return selected.get();
    }

    private static List<String> enumValues(Connection connection, String typeName) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT enumlabel
                FROM pg_enum
                JOIN pg_type ON pg_type.oid = pg_enum.enumtypid
                WHERE pg_type.typname = ?
                ORDER BY pg_enum.enumsortorder
                """)) {
            statement.setString(1, typeName);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (rows.next()) {
                    values.add(rows.getString(1));
                }
                return values;
            }
        }
    }

    private static Set<String> names(Connection connection, String query) throws SQLException {
        try (var statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(query)) {
            var names = new java.util.HashSet<String>();
            while (rows.next()) {
                names.add(rows.getString(1));
            }
            return Set.copyOf(names);
        }
    }

    private static Map<String, String> keyedValues(Connection connection, String query) throws SQLException {
        try (var statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(query)) {
            var values = new java.util.HashMap<String, String>();
            while (rows.next()) {
                values.put(rows.getString(1), rows.getString(2));
            }
            return Map.copyOf(values);
        }
    }

    private static Map<String, ColumnMetadata> columns(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT column_name, data_type, udt_name, character_maximum_length,
                               is_nullable, column_default
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'parties'
                        """)) {
            var columns = new java.util.HashMap<String, ColumnMetadata>();
            while (rows.next()) {
                columns.put(rows.getString("column_name"), new ColumnMetadata(
                        rows.getString("data_type"),
                        rows.getString("udt_name"),
                        (Integer) rows.getObject("character_maximum_length"),
                        rows.getString("is_nullable").equals("YES"),
                        rows.getString("column_default")));
            }
            return Map.copyOf(columns);
        }
    }

    private static void assertRootColumns(Map<String, ColumnMetadata> columns) {
        assertEquals(Set.of(
                "id", "tenant_id", "type", "display_name", "record_status",
                "created_at", "created_by", "updated_at", "updated_by", "version"),
                columns.keySet());
        assertColumn(columns, "id", "uuid", "uuid", null, false, "uuidv7()");
        assertColumn(columns, "tenant_id", "uuid", "uuid", null, false, null);
        assertColumn(columns, "type", "USER-DEFINED", "party_type", null, false, null);
        assertColumn(columns, "display_name", "character varying", "varchar", 300, false, null);
        assertColumn(columns, "record_status", "USER-DEFINED", "party_record_status", null, false, "'DRAFT'");
        assertColumn(columns, "created_at", "timestamp with time zone", "timestamptz", null, false, "now()");
        assertColumn(columns, "created_by", "character varying", "varchar", 128, false, null);
        assertColumn(columns, "updated_at", "timestamp with time zone", "timestamptz", null, false, "now()");
        assertColumn(columns, "updated_by", "character varying", "varchar", 128, false, null);
        assertColumn(columns, "version", "bigint", "int8", null, false, "0");
    }

    private static void assertColumn(
            Map<String, ColumnMetadata> columns,
            String name,
            String dataType,
            String udtName,
            Integer maximumLength,
            boolean nullable,
            String defaultFragment) {
        ColumnMetadata column = columns.get(name);
        assertNotNull(column);
        assertEquals(dataType, column.dataType());
        assertEquals(udtName, column.udtName());
        assertEquals(maximumLength, column.maximumLength());
        assertEquals(nullable, column.nullable());
        if (defaultFragment == null) {
            assertNull(column.defaultExpression());
        } else {
            assertTrue(column.defaultExpression().contains(defaultFragment));
        }
    }

    private static void assertConstraintViolation(String sql, String expectedConstraint) throws SQLException {
        try (Connection connection = dataSource().getConnection();
                var statement = connection.createStatement()) {
            PSQLException failure = assertThrows(PSQLException.class, () -> statement.executeUpdate(sql));
            assertNotNull(failure.getServerErrorMessage());
            assertEquals(expectedConstraint, failure.getServerErrorMessage().getConstraint());
        }
    }

    private static boolean exists(Connection connection, String query) throws SQLException {
        try (var statement = connection.createStatement();
                ResultSet row = statement.executeQuery(query)) {
            return row.next() && row.getBoolean(1);
        }
    }

    private static InsertedParty insertParty(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO parties (tenant_id, type, display_name, created_by, updated_by)
                VALUES (?, 'NATURAL_PERSON', 'Ana Example', 'test-subject', 'test-subject')
                RETURNING id, record_status::text, version
                """)) {
            statement.setObject(1, UUID.randomUUID());
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                return new InsertedParty(row.getObject(1, UUID.class), row.getString(2), row.getLong(3));
            }
        }
    }

    private static void updateType(Connection connection, UUID partyId, String type) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE parties SET type = ?::party_type WHERE id = ?")) {
            statement.setString(1, type);
            statement.setObject(2, partyId);
            statement.executeUpdate();
        }
    }

    public static final class MigrationOnlyProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.hibernate-orm.enabled", "false",
                    "quarkus.otel.sdk.disabled", "true");
        }
    }

    private record ColumnMetadata(
            String dataType,
            String udtName,
            Integer maximumLength,
            boolean nullable,
            String defaultExpression) {
    }

    private record InsertedParty(UUID id, String recordStatus, long version) {
    }
}
