package com.alexastudillo.partyregistry.adapter.out.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.postgresql.PostgreSQLContainer;

abstract class PostgreSql18TestSupport {
    static final String ACTOR = "synthetic-test-principal";
    static final String HASH_A = "A".repeat(64);
    static final String HASH_B = "B".repeat(64);
    static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:18.0")
            .withDatabaseName("party_registry")
            .withUsername("migration_owner")
            .withPassword("synthetic-local-test-password");

    @BeforeAll
    static void startPostgreSqlAndMigrate() throws Exception {
        CONTAINER.start();
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            assertEquals(18, queryInt(connection, "select current_setting('server_version_num')::int / 10000"));
            statement.execute("create role party_registry_runtime nologin");
        }
        flyway().migrate();
    }

    @AfterAll
    static void stopPostgreSql() {
        CONTAINER.stop();
    }

    static Flyway flyway() {
        return Flyway.configure()
                .dataSource(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword())
                .locations("classpath:db/migration")
                .load();
    }

    static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
    }

    static Connection transaction() throws SQLException {
        Connection connection = ownerConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    static void rollbackAndClose(Connection connection) throws SQLException {
        connection.rollback();
        connection.close();
    }

    static int queryInt(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    static String queryString(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    static void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    static UUID insertNaturalParty(Connection connection, UUID tenantId) throws SQLException {
        UUID partyId = UUID.randomUUID();
        execute(connection, """
                insert into parties(id, tenant_id, type, display_name, created_by, updated_by)
                values (?, ?, 'NATURAL_PERSON', 'Synthetic Person', ?, ?)
                """, partyId, tenantId, ACTOR, ACTOR);
        insertNaturalDetails(connection, partyId);
        return partyId;
    }

    static void insertNaturalDetails(Connection connection, UUID partyId) throws SQLException {
        execute(connection, """
                insert into natural_person_details(party_id, given_names, family_names, created_by, updated_by)
                values (?, 'Synthetic', 'Person', ?, ?)
                """, partyId, ACTOR, ACTOR);
    }

    static UUID insertScheme(Connection connection, String code) throws SQLException {
        UUID schemeId = UUID.randomUUID();
        execute(connection, """
                insert into identifier_schemes(
                    id, code, issuing_country_code, category, applicable_subject_type, name,
                    normalizer_key, validator_key, status, created_by, updated_by)
                values (?, ?, 'EC', 'NATIONAL_ID', 'BOTH', 'Synthetic scheme',
                    'DIGITS_ONLY_V1', 'SYNTHETIC_VALIDATOR_V1', 'ACTIVE', ?, ?)
                """, schemeId, code, ACTOR, ACTOR);
        return schemeId;
    }

    static UUID insertIdentifier(
            Connection connection, UUID tenantId, UUID partyId, UUID schemeId, String hash,
            String status, boolean primary) throws SQLException {
        UUID identifierId = UUID.randomUUID();
        String verifiedAt = status.equals("VERIFIED") ? "now()" : "null";
        String verifiedBy = status.equals("VERIFIED") ? "'synthetic-verifier'" : "null";
        String expiresOn = status.equals("EXPIRED") ? "current_date" : "null";
        execute(connection, """
                insert into party_identifiers(
                    id, tenant_id, party_id, identifier_scheme_id, encrypted_value,
                    encryption_key_version, normalized_value_hash, masked_value,
                    is_primary, status, expires_on, verified_at, verified_by, created_by, updated_by)
                values (?, ?, ?, ?, 'synthetic-ciphertext', 1, ?, '****0000', ?, ?::party_identifier_status,
                    %s, %s, %s, ?, ?)
                """.formatted(expiresOn, verifiedAt, verifiedBy),
                identifierId, tenantId, partyId, schemeId, hash, primary, status, ACTOR, ACTOR);
        return identifierId;
    }

    static SQLException assertConstraint(String expectedConstraint, org.junit.jupiter.api.function.Executable action) {
        SQLException failure = assertThrows(SQLException.class, action);
        org.junit.jupiter.api.Assertions.assertEquals(expectedConstraint, postgresConstraint(failure),
                () -> "Expected named constraint " + expectedConstraint + " but received: " + failure.getMessage());
        return failure;
    }

    static String postgresConstraint(SQLException failure) {
        try {
            Object serverError = failure.getClass().getMethod("getServerErrorMessage").invoke(failure);
            return (String) serverError.getClass().getMethod("getConstraint").invoke(serverError);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException reflectionFailure) {
            throw new AssertionError("PostgreSQL JDBC error metadata is unavailable", reflectionFailure);
        }
    }
}
