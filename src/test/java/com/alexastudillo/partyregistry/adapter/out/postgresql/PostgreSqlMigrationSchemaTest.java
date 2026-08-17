package com.alexastudillo.partyregistry.adapter.out.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Arrays;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class PostgreSqlMigrationSchemaTest extends PostgreSql18TestSupport {
    @Test
    void completeFlywayChainIsAppliedOnceAndSecondRunIsNoOp() throws Exception {
        assertEquals(List.of("1", "2"), Arrays.stream(flyway().info().applied())
                .filter(item -> item.getVersion() != null)
                .map(item -> item.getVersion().getVersion())
                .toList());
        MigrateResult secondRun = flyway().migrate();
        assertEquals(0, secondRun.migrationsExecuted);
        assertTrue(flyway().validateWithResult().validationSuccessful);
    }

    @Test
    void missingRuntimeRoleRollsBackV1AndCanRecoverByCorrectingThePrecondition() throws Exception {
        try (PostgreSQLContainer isolated = new PostgreSQLContainer("postgres:18.0")
                .withDatabaseName("precondition_failure")
                .withUsername("isolated_migration_owner")
                .withPassword("synthetic-local-test-password")) {
            isolated.start();
            Flyway isolatedFlyway = Flyway.configure()
                    .dataSource(isolated.getJdbcUrl(), isolated.getUsername(), isolated.getPassword())
                    .locations("classpath:db/migration")
                    .load();
            assertThrows(FlywayException.class, isolatedFlyway::migrate);
            try (Connection connection = DriverManager.getConnection(isolated.getJdbcUrl(), isolated.getUsername(), isolated.getPassword())) {
                assertEquals(0, queryInt(connection, "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname='parties'"));
                assertEquals(0, queryInt(connection, "select count(*) from flyway_schema_history where success"));
                execute(connection, "create role party_registry_runtime nologin");
            }
            assertEquals(2, isolatedFlyway.migrate().migrationsExecuted);
            assertEquals(0, isolatedFlyway.migrate().migrationsExecuted);
        }
    }

    @Test
    void upgradeFromCommittedV1PreservesRowsAndAppliesV2ExactlyOnce() throws Exception {
        String database = createDisposableDatabase("upgrade");
        String url = databaseUrl(database);
        Flyway v1 = flywayFor(url, MigrationVersion.fromVersion("1"));
        assertEquals(1, v1.migrate().migrationsExecuted);
        UUID tenant = UUID.randomUUID();
        UUID party = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, CONTAINER.getUsername(), CONTAINER.getPassword())) {
            connection.setAutoCommit(false);
            execute(connection, "insert into parties(id,tenant_id,type,display_name,created_by,updated_by) values (?,?, 'NATURAL_PERSON','Preserved Person',?,?)", party, tenant, ACTOR, ACTOR);
            insertNaturalDetails(connection, party);
            connection.commit();
        }
        Flyway complete = flywayFor(url, null);
        assertEquals(1, complete.migrate().migrationsExecuted);
        assertEquals(0, complete.migrate().migrationsExecuted);
        try (Connection connection = DriverManager.getConnection(url, CONTAINER.getUsername(), CONTAINER.getPassword())) {
            assertEquals("Preserved Person", queryString(connection, "select display_name from parties where id=?", party));
            assertEquals(2, queryInt(connection, "select count(*) from pg_constraint where conname in ('fk_natural_person_details_party','fk_legal_entity_details_party') and condeferrable and condeferred"));
        }
    }

    @Test
    void failedV2RollsBackBothConstraintAlterationsAtomically() throws Exception {
        String database = createDisposableDatabase("v2_failure");
        String url = databaseUrl(database);
        flywayFor(url, MigrationVersion.fromVersion("1")).migrate();
        try (Connection connection = DriverManager.getConnection(url, CONTAINER.getUsername(), CONTAINER.getPassword())) {
            execute(connection, "alter table legal_entity_details rename constraint fk_legal_entity_details_party to forced_v2_failure");
        }
        Flyway complete = flywayFor(url, null);
        assertThrows(FlywayException.class, complete::migrate);
        try (Connection connection = DriverManager.getConnection(url, CONTAINER.getUsername(), CONTAINER.getPassword())) {
            assertEquals(0, queryInt(connection, "select count(*) from pg_constraint where conname='fk_natural_person_details_party' and condeferrable"));
            assertEquals(1, queryInt(connection, "select count(*) from pg_constraint where conname='forced_v2_failure' and not condeferrable"));
            assertEquals(0, queryInt(connection, "select count(*) from flyway_schema_history where version='2' and success"));
        }
    }

    @Test
    void enumInventoryMatchesDbmlExactly() throws Exception {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("identifier_category", List.of("NATIONAL_ID", "TAX_ID", "PASSPORT", "RESIDENCE_PERMIT", "LEGAL_REGISTRATION_NUMBER", "OTHER"));
        expected.put("identifier_scheme_status", List.of("DRAFT", "ACTIVE", "DEPRECATED", "RETIRED"));
        expected.put("identifier_subject_type", List.of("NATURAL_PERSON", "LEGAL_ENTITY", "BOTH"));
        expected.put("outbox_status", List.of("PENDING", "PUBLISHED", "FAILED"));
        expected.put("party_identifier_status", List.of("PENDING_VERIFICATION", "VERIFIED", "REJECTED", "EXPIRED", "REVOKED"));
        expected.put("party_outbox_aggregate_type", List.of("PARTY", "PARTY_IDENTIFIER"));
        expected.put("party_record_status", List.of("DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED"));
        expected.put("party_type", List.of("NATURAL_PERSON", "LEGAL_ENTITY"));

        try (Connection connection = ownerConnection()) {
            for (Map.Entry<String, List<String>> item : expected.entrySet()) {
                String labels = queryString(connection, """
                        select string_agg(e.enumlabel, ',' order by e.enumsortorder)
                        from pg_enum e join pg_type t on t.oid=e.enumtypid
                        join pg_namespace n on n.oid=t.typnamespace
                        where n.nspname='public' and t.typname=?
                        """, item.getKey());
                assertEquals(String.join(",", item.getValue()), labels, item.getKey());
            }
            assertEquals(expected.keySet(), enumNames(connection));
        }
    }

    @Test
    void tableAndColumnInventoryMatchesDbmlExactly() throws Exception {
        Map<String, List<String>> expected = Map.of(
                "parties", List.of("id", "tenant_id", "type", "display_name", "record_status", "created_at", "created_by", "updated_at", "updated_by", "version"),
                "natural_person_details", List.of("party_id", "given_names", "family_names", "preferred_name", "birth_date", "date_of_death", "birth_country_code", "created_at", "created_by", "updated_at", "updated_by"),
                "legal_entity_details", List.of("party_id", "legal_name", "trade_name", "legal_form_code", "incorporation_country_code", "incorporated_on", "dissolved_on", "created_at", "created_by", "updated_at", "updated_by"),
                "party_nationalities", List.of("id", "party_id", "country_code", "is_primary", "valid_from", "valid_until", "created_at", "created_by", "updated_at", "updated_by"),
                "identifier_schemes", List.of("id", "code", "issuing_country_code", "category", "applicable_subject_type", "name", "description", "normalizer_key", "validator_key", "minimum_length", "maximum_length", "requires_expiration", "status", "created_at", "created_by", "updated_at", "updated_by", "version"),
                "party_identifiers", List.of("id", "tenant_id", "party_id", "identifier_scheme_id", "issuer_code", "encrypted_value", "encryption_key_version", "normalized_value_hash", "masked_value", "normalization_version", "is_primary", "status", "issued_on", "expires_on", "verified_at", "verified_by", "created_at", "created_by", "updated_at", "updated_by", "version"),
                "party_outbox_events", List.of("id", "tenant_id", "aggregate_type", "aggregate_id", "aggregate_version", "event_type", "event_schema_version", "payload", "occurred_at", "correlation_id", "causation_id", "status", "publish_attempts", "next_attempt_at", "last_attempt_at", "published_at", "last_error_code", "last_error_detail", "created_at", "created_by", "updated_at", "updated_by", "version"));
        try (Connection connection = ownerConnection()) {
            assertEquals(expected.keySet(), tableNames(connection));
            for (Map.Entry<String, List<String>> item : expected.entrySet()) {
                assertEquals(String.join(",", item.getValue()), queryString(connection, """
                        select string_agg(column_name, ',' order by ordinal_position)
                        from information_schema.columns where table_schema='public' and table_name=?
                        """, item.getKey()), item.getKey());
            }
            assertEquals(104, expected.values().stream().mapToInt(List::size).sum());
            assertEquals(104, queryInt(connection, """
                    select count(*) from information_schema.columns
                    where table_schema='public' and table_name = any(?::text[])
                    """, connection.createArrayOf("text", expected.keySet().toArray())));
        }
    }

    @Test
    void namedConstraintIndexFunctionAndTriggerInventoryMatchesDbml() throws Exception {
        Set<String> expectedConstraints = Set.of(
                "pk_parties", "uq_parties_tenant_id", "ck_parties_nonnegative_version",
                "pk_natural_person_details", "ck_natural_person_life_dates", "ck_natural_person_birth_country_code", "fk_natural_person_details_party",
                "pk_legal_entity_details", "ck_legal_entity_lifecycle_dates", "ck_legal_entity_incorporation_country_code", "fk_legal_entity_details_party",
                "pk_party_nationalities", "ck_party_nationality_country_code", "ck_party_nationality_validity", "fk_party_nationalities_party",
                "pk_identifier_schemes", "uq_identifier_schemes_code", "ck_identifier_scheme_country_code", "ck_identifier_scheme_minimum_length", "ck_identifier_scheme_maximum_length", "ck_identifier_scheme_length_range", "ck_identifier_schemes_nonnegative_version",
                "pk_party_identifiers", "uq_party_identifier_tenant_scheme_hash", "ck_party_identifier_validity_dates", "ck_party_identifier_verification", "ck_party_identifier_expired_date", "ck_party_identifier_hash_upper_hex", "ck_party_identifier_normalization_version", "ck_party_identifier_encryption_key_version", "ck_party_identifier_nonnegative_version", "fk_party_identifiers_party", "fk_party_identifiers_scheme",
                "pk_party_outbox_events", "ck_party_outbox_nonnegative_aggregate_version", "ck_party_outbox_positive_schema_version", "ck_party_outbox_nonnegative_attempts", "ck_party_outbox_nonnegative_version", "ck_party_outbox_published_at", "ck_party_outbox_failed_error");
        Set<String> expectedIndexes = Set.of(
                "pk_parties", "uq_parties_tenant_id", "ix_parties_tenant_type_status", "ix_parties_tenant_display_name",
                "pk_natural_person_details", "pk_legal_entity_details", "pk_party_nationalities", "ix_party_nationalities_country", "ix_party_nationalities_primary", "uq_party_nationalities_active_country", "uq_party_nationalities_active_primary",
                "pk_identifier_schemes", "uq_identifier_schemes_code", "ix_identifier_schemes_country_category",
                "pk_party_identifiers", "uq_party_identifier_tenant_scheme_hash", "ix_party_identifiers_party_scheme_status", "ix_party_identifiers_primary", "uq_party_identifiers_verified_primary_scheme",
                "pk_party_outbox_events", "ix_party_outbox_delivery", "ix_party_outbox_aggregate", "ix_party_outbox_correlation");
        try (Connection connection = ownerConnection()) {
            Set<String> actualConstraints = names(connection, "select conname from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname='public' and c.contype in ('p','u','c','f') and c.conrelid <> 'flyway_schema_history'::regclass");
            assertEquals(expectedConstraints, actualConstraints,
                    () -> "Missing: " + difference(expectedConstraints, actualConstraints)
                            + "; unexpected: " + difference(actualConstraints, expectedConstraints));
            assertEquals(expectedIndexes, names(connection, "select indexname from pg_indexes where schemaname='public' and tablename <> 'flyway_schema_history'"));
            assertEquals(Set.of("ct_parties_detail_type", "ct_natural_person_details_party_type", "ct_legal_entity_details_party_type"),
                    names(connection, "select tgname from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and not t.tgisinternal"));
            assertEquals(1, queryInt(connection, "select count(*) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='fn_enforce_party_detail_type'"));
            assertEquals(3, queryInt(connection, "select count(*) from pg_trigger where tgname like 'ct_%detail%type' and tgdeferrable and tginitdeferred"));
            assertEquals(2, queryInt(connection, "select count(*) from pg_constraint where conname in ('fk_natural_person_details_party','fk_legal_entity_details_party') and condeferrable and condeferred and confdeltype='r'"));
            assertTrue(queryString(connection, "select pg_get_indexdef(indexrelid) from pg_index join pg_class on oid=indexrelid where relname='uq_party_nationalities_active_country'").endsWith("WHERE (valid_until IS NULL)"));
            assertTrue(queryString(connection, "select pg_get_indexdef(indexrelid) from pg_index join pg_class on oid=indexrelid where relname='uq_party_identifiers_verified_primary_scheme'").contains("WHERE ((is_primary = true) AND (status = 'VERIFIED'"));
        }
    }

    @Test
    void uuidDefaultsAuditDefaultsAndPostgresqlSpecificTypesArePresent() throws Exception {
        try (Connection connection = ownerConnection()) {
            assertEquals(5, queryInt(connection, """
                    select count(*) from information_schema.columns
                    where table_schema='public' and column_name='id' and column_default='uuidv7()'
                    """));
            assertEquals(14, queryInt(connection, """
                    select count(*) from information_schema.columns
                    where table_schema='public' and column_name in ('created_at','updated_at')
                      and is_nullable='NO' and column_default='now()'
                    """));
            assertEquals("jsonb", queryString(connection, "select data_type from information_schema.columns where table_schema='public' and table_name='party_outbox_events' and column_name='payload'"));
            assertEquals("timestamp with time zone", queryString(connection, "select data_type from information_schema.columns where table_schema='public' and table_name='parties' and column_name='created_at'"));
            assertFalse(queryString(connection, "select exists(select 1 from pg_extension where extname <> 'plpgsql')").equals("t"));
        }
    }

    private static Set<String> enumNames(Connection connection) throws Exception {
        return names(connection, "select distinct t.typname from pg_type t join pg_enum e on e.enumtypid=t.oid join pg_namespace n on n.oid=t.typnamespace where n.nspname='public'");
    }

    private static Set<String> tableNames(Connection connection) throws Exception {
        return names(connection, "select table_name from information_schema.tables where table_schema='public' and table_type='BASE TABLE' and table_name <> 'flyway_schema_history'");
    }

    private static Set<String> names(Connection connection, String sql) throws Exception {
        Set<String> names = new TreeSet<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) names.add(result.getString(1));
        }
        return names;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> difference = new TreeSet<>(left);
        difference.removeAll(right);
        return difference;
    }

    private static String createDisposableDatabase(String prefix) throws Exception {
        String database = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create database " + database);
        }
        return database;
    }

    private static String databaseUrl(String database) {
        return "jdbc:postgresql://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(5432) + "/" + database;
    }

    private static Flyway flywayFor(String url, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(url, CONTAINER.getUsername(), CONTAINER.getPassword())
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
