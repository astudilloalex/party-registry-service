package com.alexastudillo.partyregistry;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.postgresql.util.PSQLException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies clean Flyway installation and qualified identifier replay and withdrawal-audit constraints. */
@Testcontainers
@Timeout(120)
class PartyIdentifierContractMigrationTest {

    private static final String CREATE = "party.identifier.create.v1";
    private static final String REVOKE = "party.identifier.revoke.v1";

    @Container
    private static final PostgreSQLContainer DATABASE =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @BeforeAll
    static void install() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .locations("classpath:db/migration").cleanDisabled(true).load();
        assertEquals(8, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (Connection connection = connection()) {
            assertEquals("0", scalar(connection, "SELECT count(*)::text FROM parties"));
            assertEquals("0", scalar(connection, "SELECT count(*)::text FROM party_identifier_idempotency_records"));
            assertEquals("0", scalar(connection, "SELECT count(*)::text FROM party_identifier_action_audit"));
        }
    }

    @Test
    void installsQualifiedKeysAndRestrictiveOwnerReferences() throws SQLException {
        try (Connection connection = connection()) {
            assertEquals("UNIQUE (tenant_id, party_id, id)", constraint(connection,
                    "party_identifiers", "uq_party_identifiers_owner_id"));
            assertEquals("PRIMARY KEY (tenant_id, operation, idempotency_key)", constraint(connection,
                    "party_identifier_idempotency_records", "pk_party_identifier_idempotency_records"));
            for (String table : new String[]{"party_identifier_idempotency_records", "party_identifier_action_audit"}) {
                String name = table.equals("party_identifier_action_audit")
                        ? "fk_party_identifier_audit_owner" : "fk_party_identifier_idempotency_owner";
                assertEquals("FOREIGN KEY (tenant_id, party_id, identifier_id) REFERENCES party_identifiers(tenant_id, party_id, id) ON DELETE RESTRICT",
                        constraint(connection, table, name));
            }
            assertEquals("UNIQUE (tenant_id, identifier_id, resulting_version, action)", constraint(connection,
                    "party_identifier_action_audit", "uq_party_identifier_audit_result"));
        }
    }

    @Test
    void installsHistoricalOrderAndRetentionIndexesWithoutSeededOutcomes() throws SQLException {
        try (Connection connection = connection()) {
            assertEquals("CREATE INDEX ix_party_identifiers_created_id ON public.party_identifiers USING btree (tenant_id, party_id, created_at DESC, id DESC)",
                    scalar(connection, "SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_party_identifiers_created_id'"));
            assertEquals("CREATE INDEX ix_party_identifier_idempotency_owner ON public.party_identifier_idempotency_records USING btree (tenant_id, party_id, identifier_id)",
                    scalar(connection, "SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_party_identifier_idempotency_owner'"));
            assertEquals("CREATE INDEX ix_party_identifier_audit_owner ON public.party_identifier_action_audit USING btree (tenant_id, party_id, identifier_id)",
                    scalar(connection, "SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_party_identifier_audit_owner'"));
        }
    }

    @Test
    void preservesTypedSafeSnapshotsAndBinaryAuditColumnContracts() throws SQLException {
        try (Connection connection = connection()) {
            assertEquals(Map.ofEntries(
                    Map.entry("tenant_id", "uuid required"), Map.entry("operation", "character varying(64) required"),
                    Map.entry("idempotency_key", "character varying(128) required"), Map.entry("request_hash", "character(64) required"),
                    Map.entry("party_id", "uuid required"), Map.entry("identifier_id", "uuid required"),
                    Map.entry("result_snapshot_schema_version", "smallint required"), Map.entry("result_snapshot", "jsonb required"),
                    Map.entry("expected_identifier_version", "bigint optional"), Map.entry("created_at", "timestamp with time zone required"),
                    Map.entry("created_by", "character varying(128) required")), columns(connection, "party_identifier_idempotency_records"));
            assertEquals(Map.ofEntries(
                    Map.entry("id", "uuid required"), Map.entry("tenant_id", "uuid required"),
                    Map.entry("party_id", "uuid required"), Map.entry("identifier_id", "uuid required"),
                    Map.entry("action", "character varying(6) required"), Map.entry("resulting_version", "bigint required"),
                    Map.entry("actor", "character varying(128) required"), Map.entry("occurred_at", "timestamp with time zone required"),
                    Map.entry("reason_code", "bytea optional"), Map.entry("reason_detail", "bytea optional")),
                    columns(connection, "party_identifier_action_audit"));
        }
    }

    @Test
    void completionScopePreservesExactKeysAndActionVersionSemantics() throws SQLException {
        try (Connection connection = connection()) {
            Owner owner = owner(connection);
            Completion create = new Completion(CREATE, " exact-key ", "a".repeat(64), 1, "{}", null, "actor");
            completion(connection, owner, create);
            assertConstraint("23505", "pk_party_identifier_idempotency_records",
                    () -> completion(connection, owner, create));
            completion(connection, owner, new Completion(CREATE, "exact-key", "b".repeat(64), 32767, "{}", null, "actor"));
            for (String action : new String[]{"verify", "reject", "revoke", "set-primary"}) {
                completion(connection, owner, new Completion("party.identifier." + action + ".v1",
                        " exact-key ", "c".repeat(64), 1, "{}", Long.MAX_VALUE, "actor"));
            }
            completion(connection, owner(connection), create);
            assertEquals("6", scalar(connection, "SELECT count(*)::text FROM party_identifier_idempotency_records WHERE tenant_id = '" + owner.tenant() + "'"));
        }
    }

    @Test
    void completionRejectsMalformedDigestsSchemasAndExpectedVersions() throws SQLException {
        try (Connection connection = connection()) {
            Owner owner = owner(connection);
            for (String hash : new String[]{"A".repeat(64), "a".repeat(63), "a".repeat(63) + "g"}) {
                Completion invalid = new Completion(CREATE, "key", hash, 1, "{}", null, "actor");
                assertConstraint("23514", "ck_party_identifier_idempotency_hash", () -> completion(connection, owner, invalid));
            }
            for (int schema : new int[]{0, -1}) {
                Completion invalid = new Completion(CREATE, "key", "a".repeat(64), schema, "{}", null, "actor");
                assertConstraint("23514", "ck_party_identifier_idempotency_schema", () -> completion(connection, owner, invalid));
            }
            for (String snapshot : new String[]{"[]", "null", "1", "true", "\"text\""}) {
                Completion invalid = new Completion(CREATE, "key", "a".repeat(64), 1, snapshot, null, "actor");
                assertConstraint("23514", "ck_party_identifier_idempotency_snapshot", () -> completion(connection, owner, invalid));
            }
            for (Completion invalid : new Completion[]{
                    new Completion(CREATE, "key", "a".repeat(64), 1, "{}", 0L, "actor"),
                    new Completion(REVOKE, "key", "a".repeat(64), 1, "{}", null, "actor"),
                    new Completion(REVOKE, "key", "a".repeat(64), 1, "{}", -1L, "actor")}) {
                assertConstraint("23514", "ck_party_identifier_idempotency_expected_version", () -> completion(connection, owner, invalid));
            }
        }
    }

    @Test
    void completionRejectsUnknownOperationsAndBlankAttribution() throws SQLException {
        try (Connection connection = connection()) {
            Owner owner = owner(connection);
            Completion operation = new Completion("party.identifier.patch.v1", "key", "a".repeat(64), 1, "{}", 0L, "actor");
            Completion key = new Completion(CREATE, " ", "a".repeat(64), 1, "{}", null, "actor");
            Completion actor = new Completion(CREATE, "key", "a".repeat(64), 1, "{}", null, " ");
            assertConstraint("23514", "ck_party_identifier_idempotency_operation", () -> completion(connection, owner, operation));
            assertConstraint("23514", "ck_party_identifier_idempotency_key", () -> completion(connection, owner, key));
            assertConstraint("23514", "ck_party_identifier_idempotency_actor", () -> completion(connection, owner, actor));
        }
    }

    @Test
    void rejectsCrossOwnerReferencesAndIdentifierDeletion() throws SQLException {
        try (Connection connection = connection()) {
            Owner owner = owner(connection);
            Owner other = owner(connection);
            Owner wrongTenant = new Owner(other.tenant(), owner.party(), owner.identifier());
            Owner wrongParty = new Owner(owner.tenant(), other.party(), owner.identifier());
            Completion valid = new Completion(CREATE, "key", "a".repeat(64), 1, "{}", null, "actor");
            for (Owner wrong : new Owner[]{wrongTenant, wrongParty, new Owner(owner.tenant(), owner.party(), UUID.randomUUID())}) {
                assertConstraint("23503", "fk_party_identifier_idempotency_owner", () -> completion(connection, wrong, valid));
                assertConstraint("23503", "fk_party_identifier_audit_owner", () -> audit(connection, wrong, new Audit("REJECT", 1, null, null, "actor")));
            }
            completion(connection, owner, valid);
            assertConstraint("23001", "fk_party_identifier_idempotency_owner", () -> deleteIdentifier(connection, owner));
            audit(connection, other, new Audit("REVOKE", 1, null, null, "actor"));
            assertConstraint("23001", "fk_party_identifier_audit_owner", () -> deleteIdentifier(connection, other));
        }
    }

    @Test
    void auditRetainsNullEmptyAndMaximumBinaryReasonsAndRejectsInvalidShapes() throws SQLException {
        try (Connection connection = connection()) {
            Owner owner = owner(connection);
            Audit absent = new Audit("REJECT", 1, null, null, "actor");
            audit(connection, owner, absent);
            assertConstraint("23505", "uq_party_identifier_audit_result", () -> audit(connection, owner, absent));
            audit(connection, owner, new Audit("REVOKE", 2, new byte[0], new byte[0], "actor"));
            audit(connection, owner, new Audit("REVOKE", Long.MAX_VALUE, new byte[256], new byte[4000], "actor"));
            assertEquals("1", scalar(connection, "SELECT count(*)::text FROM party_identifier_action_audit WHERE identifier_id = '" + owner.identifier() + "' AND reason_code IS NULL AND reason_detail IS NULL"));
            assertEquals("1", scalar(connection, "SELECT count(*)::text FROM party_identifier_action_audit WHERE identifier_id = '" + owner.identifier() + "' AND octet_length(reason_code) = 0 AND octet_length(reason_detail) = 0"));
            for (byte[] code : new byte[][]{new byte[1], new byte[258]}) {
                Audit invalid = new Audit("REJECT", 3, code, null, "actor");
                assertConstraint("23514", "ck_party_identifier_audit_reason_code", () -> audit(connection, owner, invalid));
            }
            for (byte[] detail : new byte[][]{new byte[1], new byte[4002]}) {
                Audit invalid = new Audit("REJECT", 3, null, detail, "actor");
                assertConstraint("23514", "ck_party_identifier_audit_reason_detail", () -> audit(connection, owner, invalid));
            }
            Audit action = new Audit("VERIFY", 3, null, null, "actor");
            Audit version = new Audit("REJECT", 0, null, null, "actor");
            Audit actor = new Audit("REJECT", 3, null, null, " ");
            assertConstraint("23514", "ck_party_identifier_audit_action", () -> audit(connection, owner, action));
            assertConstraint("23514", "ck_party_identifier_audit_version", () -> audit(connection, owner, version));
            assertConstraint("23514", "ck_party_identifier_audit_actor", () -> audit(connection, owner, actor));
        }
    }

    private static Owner owner(Connection connection) throws SQLException {
        Owner owner = new Owner(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        connection.setAutoCommit(false);
        try (var party = connection.prepareStatement("INSERT INTO parties (id, tenant_id, type, display_name, created_by, updated_by) VALUES (?, ?, 'NATURAL_PERSON', 'Migration owner', 'actor', 'actor')");
                var detail = connection.prepareStatement("INSERT INTO natural_person_details (party_id, given_names, family_names, created_by, updated_by) VALUES (?, 'Migration', 'Owner', 'actor', 'actor')");
                var identifier = connection.prepareStatement("INSERT INTO party_identifiers (id, tenant_id, party_id, identifier_scheme_id, encrypted_value, encryption_key_version, normalized_value_hash, masked_value, created_by, updated_by) SELECT ?, ?, ?, id, 'synthetic-ciphertext', 1, ?, '***', 'actor', 'actor' FROM identifier_schemes WHERE code = 'EC_NATIONAL_ID'")) {
            party.setObject(1, owner.party());
            party.setObject(2, owner.tenant());
            party.executeUpdate();
            detail.setObject(1, owner.party());
            detail.executeUpdate();
            identifier.setObject(1, owner.identifier());
            identifier.setObject(2, owner.tenant());
            identifier.setObject(3, owner.party());
            identifier.setString(4, "a".repeat(64));
            assertEquals(1, identifier.executeUpdate());
            connection.commit();
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
        return owner;
    }

    private static void completion(Connection connection, Owner owner, Completion row) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO party_identifier_idempotency_records (tenant_id, party_id, identifier_id, operation, idempotency_key, request_hash, result_snapshot_schema_version, result_snapshot, expected_identifier_version, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)")) {
            insert.setObject(1, owner.tenant());
            insert.setObject(2, owner.party());
            insert.setObject(3, owner.identifier());
            insert.setString(4, row.operation());
            insert.setString(5, row.key());
            insert.setString(6, row.hash());
            insert.setInt(7, row.schema());
            insert.setString(8, row.snapshot());
            insert.setObject(9, row.expectedVersion());
            insert.setString(10, row.actor());
            insert.executeUpdate();
        }
    }

    private static void audit(Connection connection, Owner owner, Audit row) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO party_identifier_action_audit (tenant_id, party_id, identifier_id, action, resulting_version, actor, reason_code, reason_detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            insert.setObject(1, owner.tenant());
            insert.setObject(2, owner.party());
            insert.setObject(3, owner.identifier());
            insert.setString(4, row.action());
            insert.setLong(5, row.version());
            insert.setString(6, row.actor());
            insert.setBytes(7, row.code());
            insert.setBytes(8, row.detail());
            insert.executeUpdate();
        }
    }

    private static void deleteIdentifier(Connection connection, Owner owner) throws SQLException {
        try (var delete = connection.prepareStatement("DELETE FROM party_identifiers WHERE id = ?")) {
            delete.setObject(1, owner.identifier());
            delete.executeUpdate();
        }
    }

    private static String constraint(Connection connection, String table, String name) throws SQLException {
        try (var query = connection.prepareStatement("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = ?::regclass AND conname = ?")) {
            query.setString(1, table);
            query.setString(2, name);
            try (var result = query.executeQuery()) {
                assertTrue(result.next(), name);
                return result.getString(1);
            }
        }
    }

    private static Map<String, String> columns(Connection connection, String table) throws SQLException {
        Map<String, String> columns = new HashMap<>();
        try (var query = connection.prepareStatement("SELECT attname, format_type(atttypid, atttypmod), attnotnull FROM pg_attribute WHERE attrelid = ?::regclass AND attnum > 0 AND NOT attisdropped")) {
            query.setString(1, table);
            try (var result = query.executeQuery()) {
                while (result.next()) {
                    columns.put(result.getString(1), result.getString(2) + (result.getBoolean(3) ? " required" : " optional"));
                }
            }
        }
        return columns;
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
        var details = failure.getServerErrorMessage();
        assertNotNull(details);
        assertEquals(name, details.getConstraint());
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
    }

    /** Identifies one qualified synthetic owner and identifier. */
    private record Owner(UUID tenant, UUID party, UUID identifier) { }

    /** Supplies one completion candidate for database constraint checks. */
    private record Completion(String operation, String key, String hash, int schema, String snapshot,
            Long expectedVersion, String actor) { }

    /** Supplies one binary withdrawal-audit candidate for database constraint checks. */
    private record Audit(String action, long version, byte[] code, byte[] detail, String actor) { }
}
