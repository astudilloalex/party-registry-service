package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the initial Flyway schema, invariants, interval rules, and concurrency behavior.
 */
@QuarkusTest
class InitialMigrationTest {

    private static final String ACTOR = "migration-test";

    @Inject
    AgroalDataSource dataSource;

    @Test
    void migrationCreatesTheApprovedTablesAndUuidV7Defaults() throws SQLException {
        Set<String> expectedTables = Set.of(
                "parties",
                "natural_person_details",
                "legal_entity_details",
                "party_nationalities",
                "identifier_schemes",
                "party_identifiers",
                "party_outbox_events");

        try (Connection connection = dataSource.getConnection();
                PreparedStatement tables = connection.prepareStatement("""
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = ANY (?)
                        """)) {
            tables.setArray(1, connection.createArrayOf("text", expectedTables.toArray()));
            try (ResultSet result = tables.executeQuery()) {
                int count = 0;
                while (result.next()) {
                    assertTrue(expectedTables.contains(result.getString(1)));
                    count++;
                }
                assertEquals(expectedTables.size(), count);
            }

            try (Statement statement = connection.createStatement();
                    ResultSet migration = statement.executeQuery("""
                            SELECT success
                            FROM flyway_schema_history
                            WHERE version = '1'
                            """)) {
                assertTrue(migration.next());
                assertTrue(migration.getBoolean(1));
            }

            UUID partyId = insertParty(connection, "NATURAL_PERSON");
            assertEquals(7, partyId.version());
        }
    }

    @Test
    void partyTypeAllowsNoOpAssignmentAndRejectsAChange() throws SQLException {
        UUID partyId;
        try (Connection connection = dataSource.getConnection()) {
            partyId = insertParty(connection, "NATURAL_PERSON");
            try (PreparedStatement noOp = connection.prepareStatement("""
                    UPDATE parties SET type = 'NATURAL_PERSON' WHERE id = ?
                    """)) {
                noOp.setObject(1, partyId);
                assertEquals(1, noOp.executeUpdate());
            }

            try (PreparedStatement change = connection.prepareStatement("""
                    UPDATE parties SET type = 'LEGAL_ENTITY' WHERE id = ?
                    """)) {
                change.setObject(1, partyId);
                SQLException failure = assertThrows(SQLException.class, change::executeUpdate);
                assertEquals("23514", failure.getSQLState());
            }
        }
    }

    @Test
    void partyInsertConstraintTriggerRejectsWrongDetailAtDeferredCheck() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            UUID partyId = insertParty(connection, "NATURAL_PERSON");
            insertLegalDetails(connection, partyId);

            try (Statement statement = connection.createStatement()) {
                SQLException failure = assertThrows(
                        SQLException.class,
                        () -> statement.execute("SET CONSTRAINTS ct_parties_detail_shape IMMEDIATE"));
                assertEquals("23514", failure.getSQLState());
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void eachDetailConstraintTriggerRejectsMismatchedPartyTypes() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            UUID naturalPartyId = insertParty(connection, "NATURAL_PERSON");
            SQLException legalFailure = assertThrows(
                    SQLException.class,
                    () -> insertLegalDetails(connection, naturalPartyId));
            assertEquals("23514", legalFailure.getSQLState());
        }

        try (Connection connection = dataSource.getConnection()) {
            UUID legalPartyId = insertParty(connection, "LEGAL_ENTITY");
            SQLException naturalFailure = assertThrows(
                    SQLException.class,
                    () -> insertNaturalDetails(connection, legalPartyId));
            assertEquals("23514", naturalFailure.getSQLState());
        }
    }

    @Test
    void detailPartyIdUpdateUsesTheDeferredShapeTrigger() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            UUID legalPartyId = insertParty(connection, "LEGAL_ENTITY");
            insertLegalDetails(connection, legalPartyId);
            UUID naturalPartyId = insertParty(connection, "NATURAL_PERSON");

            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE legal_entity_details SET party_id = ? WHERE party_id = ?
                    """)) {
                update.setObject(1, naturalPartyId);
                update.setObject(2, legalPartyId);
                SQLException failure = assertThrows(SQLException.class, update::executeUpdate);
                assertEquals("23514", failure.getSQLState());
            }
        }
    }

    @Test
    void concurrentOpposingDetailsAllowAtMostOneCommitWithoutDeadlock() throws Exception {
        UUID partyId;
        try (Connection connection = dataSource.getConnection()) {
            partyId = insertParty(connection, "NATURAL_PERSON");
        }

        CyclicBarrier insertsReady = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> natural = executor.submit(
                    () -> attemptDetailCommit(partyId, true, insertsReady));
            Future<Boolean> legal = executor.submit(
                    () -> attemptDetailCommit(partyId, false, insertsReady));

            int committed = (natural.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (legal.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, committed);
        }
    }

    @Test
    void nationalityCountryIntervalsCoverFiniteOpenAndEqualBoundaries() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            UUID partyId = insertParty(connection, "NATURAL_PERSON");
            insertNationality(
                    connection,
                    partyId,
                    "EC",
                    false,
                    LocalDate.of(2020, 1, 1),
                    LocalDate.of(2020, 6, 1));
            insertNationality(
                    connection,
                    partyId,
                    "EC",
                    false,
                    LocalDate.of(2020, 6, 2),
                    LocalDate.of(2020, 12, 31));

            SQLException equalBoundary = assertThrows(SQLException.class, () -> insertNationality(
                    connection,
                    partyId,
                    "EC",
                    false,
                    LocalDate.of(2020, 6, 1),
                    LocalDate.of(2020, 6, 1)));
            assertEquals("23P01", equalBoundary.getSQLState());

            SQLException openInterval = assertThrows(SQLException.class, () -> insertNationality(
                    connection,
                    partyId,
                    "EC",
                    false,
                    null,
                    null));
            assertEquals("23P01", openInterval.getSQLState());
        }
    }

    @Test
    void primaryIntervalsCannotOverlapAcrossCountries() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            UUID partyId = insertParty(connection, "NATURAL_PERSON");
            insertNationality(
                    connection,
                    partyId,
                    "EC",
                    true,
                    LocalDate.of(2021, 1, 1),
                    LocalDate.of(2021, 12, 31));
            insertNationality(
                    connection,
                    partyId,
                    "US",
                    false,
                    LocalDate.of(2021, 1, 1),
                    LocalDate.of(2021, 12, 31));

            SQLException failure = assertThrows(SQLException.class, () -> insertNationality(
                    connection,
                    partyId,
                    "CO",
                    true,
                    LocalDate.of(2021, 6, 1),
                    null));
            assertEquals("23P01", failure.getSQLState());
        }
    }

    @Test
    void exclusionConstraintsAreImmediateAndRejectConcurrentConflicts() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement constraints = connection.prepareStatement("""
                        SELECT conname, condeferrable
                        FROM pg_constraint
                        WHERE conname IN (
                            'ex_party_nationalities_country_validity',
                            'ex_party_nationalities_primary_validity'
                        )
                        """)) {
            try (ResultSet result = constraints.executeQuery()) {
                int count = 0;
                while (result.next()) {
                    assertFalse(result.getBoolean("condeferrable"));
                    count++;
                }
                assertEquals(2, count);
            }
        }

        assertConcurrentNationalityConflict("EC", false, "EC", false);
        assertConcurrentNationalityConflict("EC", true, "US", true);
    }

    private void assertConcurrentNationalityConflict(
            String firstCountry,
            boolean firstPrimary,
            String secondCountry,
            boolean secondPrimary) throws Exception {
        UUID partyId;
        try (Connection connection = dataSource.getConnection()) {
            partyId = insertParty(connection, "NATURAL_PERSON");
        }

        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = executor.submit(() -> attemptNationalityCommit(
                    partyId, firstCountry, firstPrimary, start));
            Future<Boolean> second = executor.submit(() -> attemptNationalityCommit(
                    partyId, secondCountry, secondPrimary, start));

            int committed = (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, committed);
        }
    }

    private boolean attemptDetailCommit(UUID partyId, boolean natural, CyclicBarrier insertsReady) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (natural) {
                    insertNaturalDetails(connection, partyId);
                } else {
                    insertLegalDetails(connection, partyId);
                }
                insertsReady.await(10, TimeUnit.SECONDS);
                connection.commit();
                return true;
            } catch (SQLException failure) {
                connection.rollback();
                assertEquals("23514", failure.getSQLState());
                return false;
            } catch (Exception failure) {
                connection.rollback();
                throw new IllegalStateException("Concurrent detail verification failed", failure);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not obtain a migration test connection", failure);
        }
    }

    private boolean attemptNationalityCommit(
            UUID partyId,
            String countryCode,
            boolean primary,
            CyclicBarrier start) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                start.await(10, TimeUnit.SECONDS);
                insertNationality(
                        connection,
                        partyId,
                        countryCode,
                        primary,
                        LocalDate.of(2022, 1, 1),
                        LocalDate.of(2022, 12, 31));
                connection.commit();
                return true;
            } catch (SQLException failure) {
                connection.rollback();
                assertTrue(
                        Set.of("23P01", "40P01").contains(failure.getSQLState()),
                        () -> "Expected exclusion conflict resolution, got SQLSTATE " + failure.getSQLState());
                return false;
            } catch (Exception failure) {
                connection.rollback();
                throw new IllegalStateException("Concurrent nationality verification failed", failure);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not obtain a migration test connection", failure);
        }
    }

    private static UUID insertParty(Connection connection, String partyType) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO parties (
                    tenant_id,
                    type,
                    display_name,
                    created_by,
                    updated_by
                ) VALUES (?, ?::party_type, ?, ?, ?)
                RETURNING id
                """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setString(2, partyType);
            insert.setString(3, "Migration Test Party");
            insert.setString(4, ACTOR);
            insert.setString(5, ACTOR);
            try (ResultSet result = insert.executeQuery()) {
                assertTrue(result.next());
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static void insertNaturalDetails(Connection connection, UUID partyId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO natural_person_details (
                    party_id,
                    given_names,
                    family_names,
                    created_by,
                    updated_by
                ) VALUES (?, 'Migration', 'Test', ?, ?)
                """)) {
            insert.setObject(1, partyId);
            insert.setString(2, ACTOR);
            insert.setString(3, ACTOR);
            insert.executeUpdate();
        }
    }

    private static void insertLegalDetails(Connection connection, UUID partyId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO legal_entity_details (
                    party_id,
                    legal_name,
                    incorporation_country_code,
                    created_by,
                    updated_by
                ) VALUES (?, 'Migration Test Company', 'EC', ?, ?)
                """)) {
            insert.setObject(1, partyId);
            insert.setString(2, ACTOR);
            insert.setString(3, ACTOR);
            insert.executeUpdate();
        }
    }

    private static void insertNationality(
            Connection connection,
            UUID partyId,
            String countryCode,
            boolean primary,
            LocalDate validFrom,
            LocalDate validUntil) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO party_nationalities (
                    party_id,
                    country_code,
                    is_primary,
                    valid_from,
                    valid_until,
                    created_by,
                    updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setObject(1, partyId);
            insert.setString(2, countryCode);
            insert.setBoolean(3, primary);
            insert.setDate(4, validFrom == null ? null : Date.valueOf(validFrom));
            insert.setDate(5, validUntil == null ? null : Date.valueOf(validUntil));
            insert.setString(6, ACTOR);
            insert.setString(7, ACTOR);
            insert.executeUpdate();
        }
    }
}
