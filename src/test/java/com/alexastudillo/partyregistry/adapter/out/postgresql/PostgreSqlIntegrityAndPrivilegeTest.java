package com.alexastudillo.partyregistry.adapter.out.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PostgreSqlIntegrityAndPrivilegeTest extends PostgreSql18TestSupport {
    @Test
    void validPartyAndDetailCommitInBothInsertionOrders() throws Exception {
        UUID tenant = UUID.randomUUID();
        try (Connection connection = transaction()) {
            UUID party = UUID.randomUUID();
            insertNaturalDetails(connection, party);
            execute(connection, "insert into parties(id,tenant_id,type,display_name,created_by,updated_by) values (?,?,'NATURAL_PERSON','Synthetic Person',?,?)", party, tenant, ACTOR, ACTOR);
            connection.commit();
        }
        try (Connection connection = transaction()) {
            insertNaturalParty(connection, tenant);
            connection.commit();
        }
    }

    @Test
    void deferredDetailInvariantRejectsEveryInvalidCommittedState() throws Exception {
        assertCommitConstraint("ct_parties_detail_type", connection ->
                execute(connection, "insert into parties(id,tenant_id,type,display_name,created_by,updated_by) values (uuidv7(),uuidv7(),'NATURAL_PERSON','Missing detail',?,?)", ACTOR, ACTOR));
        assertCommitConstraint("ct_parties_detail_type", connection -> {
            UUID party = UUID.randomUUID();
            execute(connection, "insert into parties(id,tenant_id,type,display_name,created_by,updated_by) values (?,?, 'NATURAL_PERSON','Wrong detail',?,?)", party, UUID.randomUUID(), ACTOR, ACTOR);
            execute(connection, "insert into legal_entity_details(party_id,legal_name,incorporation_country_code,created_by,updated_by) values (?,'Synthetic Legal','EC',?,?)", party, ACTOR, ACTOR);
        });
        assertCommitConstraint("ct_parties_detail_type", connection -> {
            UUID party = insertNaturalParty(connection, UUID.randomUUID());
            execute(connection, "insert into legal_entity_details(party_id,legal_name,incorporation_country_code,created_by,updated_by) values (?,'Synthetic Legal','EC',?,?)", party, ACTOR, ACTOR);
        });

        UUID tenant = UUID.randomUUID();
        UUID party;
        try (Connection setup = transaction()) {
            party = insertNaturalParty(setup, tenant);
            setup.commit();
        }
        UUID existingParty = party;
        assertCommitConstraint("ct_parties_detail_type", connection -> execute(connection, "delete from natural_person_details where party_id=?", existingParty));
        assertCommitConstraint("ct_parties_detail_type", connection -> execute(connection, "update parties set type='LEGAL_ENTITY' where id=?", existingParty));

        assertCommitConstraint("ct_parties_detail_type", connection -> {
            UUID movedTo = UUID.randomUUID();
            execute(connection, "insert into parties(id,tenant_id,type,display_name,created_by,updated_by) values (?,?, 'NATURAL_PERSON','Move target',?,?)", movedTo, tenant, ACTOR, ACTOR);
            execute(connection, "update natural_person_details set party_id=? where party_id=?", movedTo, existingParty);
        });
    }

    @Test
    void lifecycleDatesCountryCodesAndNumericBoundariesAreEnforced() throws Exception {
        assertStatementConstraint("ck_natural_person_life_dates", connection -> {
            UUID party = UUID.randomUUID();
            execute(connection, "insert into natural_person_details(party_id,given_names,family_names,birth_date,date_of_death,created_by,updated_by) values (?,'Synthetic','Person','2020-01-02','2020-01-01',?,?)", party, ACTOR, ACTOR);
        });
        assertStatementConstraint("ck_natural_person_birth_country_code", connection -> {
            UUID party = UUID.randomUUID();
            execute(connection, "insert into natural_person_details(party_id,given_names,family_names,birth_country_code,created_by,updated_by) values (?,'Synthetic','Person','ec',?,?)", party, ACTOR, ACTOR);
        });
        assertStatementConstraint("ck_legal_entity_lifecycle_dates", connection -> execute(connection, "insert into legal_entity_details(party_id,legal_name,incorporation_country_code,incorporated_on,dissolved_on,created_by,updated_by) values (uuidv7(),'Synthetic Legal','EC','2020-01-02','2020-01-01',?,?)", ACTOR, ACTOR));
        assertStatementConstraint("ck_legal_entity_incorporation_country_code", connection -> execute(connection, "insert into legal_entity_details(party_id,legal_name,incorporation_country_code,created_by,updated_by) values (uuidv7(),'Synthetic Legal','e1',?,?)", ACTOR, ACTOR));

        try (Connection connection = transaction()) {
            UUID party = insertNaturalParty(connection, UUID.randomUUID());
            execute(connection, "insert into party_nationalities(party_id,country_code,valid_from,valid_until,created_by,updated_by) values (?,'EC','2020-01-01','2020-01-01',?,?)", party, ACTOR, ACTOR);
            connection.commit();
        }
        assertStatementConstraint("ck_party_nationality_country_code", connection -> execute(connection, "insert into party_nationalities(party_id,country_code,created_by,updated_by) values (uuidv7(),'ec',?,?)", ACTOR, ACTOR));
        assertStatementConstraint("ck_party_nationality_validity", connection -> execute(connection, "insert into party_nationalities(party_id,country_code,valid_from,valid_until,created_by,updated_by) values (uuidv7(),'EC','2020-01-02','2020-01-01',?,?)", ACTOR, ACTOR));
        assertStatementConstraint("ck_parties_nonnegative_version", connection -> execute(connection, "update parties set version=-1 where id=(select id from parties limit 1)"));
        assertStatementConstraint("ck_identifier_scheme_minimum_length", connection -> insertInvalidScheme(connection, "minimum_length", "0"));
        assertStatementConstraint("ck_identifier_scheme_maximum_length", connection -> insertInvalidScheme(connection, "maximum_length", "0"));
        assertStatementConstraint("ck_identifier_scheme_length_range", connection -> insertInvalidScheme(connection, "minimum_length,maximum_length", "10,5"));
        assertStatementConstraint("ck_identifier_schemes_nonnegative_version", connection -> insertInvalidScheme(connection, "version", "-1"));
    }

    @Test
    void nationalityPartialUniquenessRetainsHistoryAndAllowsReplacement() throws Exception {
        UUID party;
        try (Connection setup = transaction()) {
            party = insertNaturalParty(setup, UUID.randomUUID());
            setup.commit();
        }
        try (Connection connection = transaction()) {
            execute(connection, "insert into party_nationalities(party_id,country_code,is_primary,valid_from,created_by,updated_by) values (?,'EC',true,current_date,?,?)", party, ACTOR, ACTOR);
            Savepoint duplicateCountry = connection.setSavepoint();
            assertConstraint("uq_party_nationalities_active_country", () -> execute(connection, "insert into party_nationalities(party_id,country_code,created_by,updated_by) values (?,'EC',?,?)", party, ACTOR, ACTOR));
            connection.rollback(duplicateCountry);
            Savepoint primary = connection.setSavepoint();
            assertConstraint("uq_party_nationalities_active_primary", () -> execute(connection, "insert into party_nationalities(party_id,country_code,is_primary,created_by,updated_by) values (?,'US',true,?,?)", party, ACTOR, ACTOR));
            connection.rollback(primary);
            connection.commit();
        }
        try (Connection connection = transaction()) {
            execute(connection, "update party_nationalities set valid_until=current_date where party_id=? and country_code='EC'", party);
            execute(connection, "insert into party_nationalities(party_id,country_code,is_primary,valid_from,created_by,updated_by) values (?,'EC',true,current_date,?,?)", party, ACTOR, ACTOR);
            connection.commit();
        }
        try (Connection connection = ownerConnection()) {
            assertEquals(2, queryInt(connection, "select count(*) from party_nationalities where party_id=? and country_code='EC'", party));
            assertEquals(1, queryInt(connection, "select count(*) from party_nationalities where party_id=? and country_code='EC' and valid_until is null", party));
        }
    }

    @Test
    void identifierHashTenantForeignKeyPermanentScopeAndVerifiedPrimaryAreEnforced() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID partyA;
        UUID partyB;
        UUID schemeA;
        UUID schemeB;
        try (Connection setup = transaction()) {
            partyA = insertNaturalParty(setup, tenantA);
            partyB = insertNaturalParty(setup, tenantB);
            schemeA = insertScheme(setup, "SYNTH_A_" + UUID.randomUUID());
            schemeB = insertScheme(setup, "SYNTH_B_" + UUID.randomUUID());
            setup.commit();
        }
        UUID finalPartyA = partyA;
        UUID finalSchemeA = schemeA;
        for (String invalidHash : List.of(
                "a".repeat(64),
                "A".repeat(32) + "b" + "A".repeat(31),
                "A".repeat(63) + "G",
                "0x" + "A".repeat(64),
                "A".repeat(63))) {
            assertStatementConstraint("ck_party_identifier_hash_upper_hex", connection -> insertIdentifier(connection, tenantA, finalPartyA, finalSchemeA, invalidHash, "PENDING_VERIFICATION", false));
        }
        assertStatementConstraint("fk_party_identifiers_party", connection -> insertIdentifier(connection, tenantB, finalPartyA, finalSchemeA, HASH_A, "PENDING_VERIFICATION", false));

        try (Connection connection = transaction()) {
            insertIdentifier(connection, tenantA, partyA, schemeA, HASH_A, "REVOKED", false);
            connection.commit();
        }
        for (String status : List.of("PENDING_VERIFICATION", "VERIFIED", "REJECTED", "EXPIRED", "REVOKED")) {
            String duplicateStatus = status;
            assertStatementConstraint("uq_party_identifier_tenant_scheme_hash", connection -> insertIdentifier(connection, tenantA, finalPartyA, finalSchemeA, HASH_A, duplicateStatus, false));
        }
        try (Connection connection = transaction()) {
            insertIdentifier(connection, tenantB, partyB, schemeA, HASH_A, "PENDING_VERIFICATION", false);
            insertIdentifier(connection, tenantA, partyA, schemeB, HASH_A, "PENDING_VERIFICATION", false);
            insertIdentifier(connection, tenantA, partyA, schemeA, HASH_B, "VERIFIED", true);
            insertIdentifier(connection, tenantA, partyA, schemeA, "C".repeat(64), "REJECTED", true);
            insertIdentifier(connection, tenantA, partyA, schemeA, "D".repeat(64), "VERIFIED", false);
            Savepoint primary = connection.setSavepoint();
            assertConstraint("uq_party_identifiers_verified_primary_scheme", () -> insertIdentifier(connection, tenantA, partyA, schemeA, "E".repeat(64), "VERIFIED", true));
            connection.rollback(primary);
            connection.commit();
        }
    }

    @Test
    void concurrentPermanentIdentifierDuplicatesPermitExactlyOneCommit() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID party;
        UUID scheme;
        try (Connection setup = transaction()) {
            party = insertNaturalParty(setup, tenant);
            scheme = insertScheme(setup, "CONCURRENT_" + UUID.randomUUID());
            setup.commit();
        }
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        UUID finalParty = party;
        UUID finalScheme = scheme;
        List<CompletableFuture<Boolean>> attempts = List.of(1, 2).stream().map(ignored -> CompletableFuture.supplyAsync(() -> {
            try (Connection connection = transaction()) {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                insertIdentifier(connection, tenant, finalParty, finalScheme, HASH_A, "PENDING_VERIFICATION", false);
                connection.commit();
                return true;
            } catch (SQLException expectedUniqueConflict) {
                return false;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        })).toList();
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        assertEquals(1, attempts.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count());
        try (Connection connection = ownerConnection()) {
            assertEquals(1, queryInt(connection, "select count(*) from party_identifiers where tenant_id=? and identifier_scheme_id=? and normalized_value_hash=?", tenant, scheme, HASH_A));
        }
    }

    @Test
    void identifierAndOutboxStateChecksRejectInvalidAndAcceptBoundaryValues() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID party;
        UUID scheme;
        try (Connection setup = transaction()) {
            party = insertNaturalParty(setup, tenant);
            scheme = insertScheme(setup, "CHECKS_" + UUID.randomUUID());
            setup.commit();
        }
        UUID p = party;
        UUID s = scheme;
        assertStatementConstraint("ck_party_identifier_verification", c -> execute(c, identifierSql("VERIFIED", "null", "1", "1", "0"), UUID.randomUUID(), tenant, p, s, HASH_A, ACTOR, ACTOR));
        assertStatementConstraint("ck_party_identifier_expired_date", c -> execute(c, identifierSql("EXPIRED", "null", "1", "1", "0"), UUID.randomUUID(), tenant, p, s, HASH_A, ACTOR, ACTOR));
        assertStatementConstraint("ck_party_identifier_validity_dates", c -> execute(c, """
                insert into party_identifiers(id,tenant_id,party_id,identifier_scheme_id,encrypted_value,encryption_key_version,normalized_value_hash,masked_value,issued_on,expires_on,created_by,updated_by)
                values (?,?,?,?,'synthetic-ciphertext',1,?,'****0000','2020-01-02','2020-01-01',?,?)
                """, UUID.randomUUID(), tenant, p, s, HASH_A, ACTOR, ACTOR));
        assertStatementConstraint("ck_party_identifier_normalization_version", c -> execute(c, identifierSql("PENDING_VERIFICATION", "null", "0", "1", "0"), UUID.randomUUID(), tenant, p, s, HASH_A, ACTOR, ACTOR));
        assertStatementConstraint("ck_party_identifier_encryption_key_version", c -> execute(c, identifierSql("PENDING_VERIFICATION", "null", "1", "0", "0"), UUID.randomUUID(), tenant, p, s, HASH_A, ACTOR, ACTOR));
        assertStatementConstraint("ck_party_identifier_nonnegative_version", c -> execute(c, identifierSql("PENDING_VERIFICATION", "null", "1", "1", "-1"), UUID.randomUUID(), tenant, p, s, HASH_A, ACTOR, ACTOR));

        assertOutboxConstraint("ck_party_outbox_nonnegative_aggregate_version", "PENDING", "-1", "1", "0", "null", "null");
        assertOutboxConstraint("ck_party_outbox_positive_schema_version", "PENDING", "0", "0", "0", "null", "null");
        assertOutboxConstraint("ck_party_outbox_nonnegative_attempts", "PENDING", "0", "1", "-1", "null", "null");
        assertOutboxConstraint("ck_party_outbox_nonnegative_version", "PENDING", "0", "1", "0", "null", "null", "-1");
        assertOutboxConstraint("ck_party_outbox_published_at", "PUBLISHED", "0", "1", "0", "null", "null");
        assertOutboxConstraint("ck_party_outbox_published_at", "PENDING", "0", "1", "0", "now()", "null");
        assertOutboxConstraint("ck_party_outbox_failed_error", "FAILED", "0", "1", "0", "null", "null");
        try (Connection connection = transaction()) {
            execute(connection, outboxSql("PUBLISHED", "0", "1", "0", "now()", "null", "0"), UUID.randomUUID(), tenant, party, ACTOR, ACTOR);
            execute(connection, outboxSql("FAILED", "0", "1", "0", "null", "'SAFE_CODE'", "0"), UUID.randomUUID(), tenant, party, ACTOR, ACTOR);
            connection.commit();
        }
    }

    @Test
    void businessMutationAndOutboxAreAtomicAndOptimisticVersionRejectsStaleWrite() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID party = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        try (Connection connection = transaction()) {
            execute(connection, "insert into parties(id,tenant_id,type,display_name,created_by,updated_by) values (?,?, 'NATURAL_PERSON','Rolled back',?,?)", party, tenant, ACTOR, ACTOR);
            insertNaturalDetails(connection, party);
            execute(connection, outboxSql("PENDING", "0", "1", "0", "null", "null", "0"), event, tenant, party, ACTOR, ACTOR);
            connection.rollback();
        }
        try (Connection connection = ownerConnection()) {
            assertEquals(0, queryInt(connection, "select count(*) from parties where id=?", party));
            assertEquals(0, queryInt(connection, "select count(*) from party_outbox_events where id=?", event));
        }
        try (Connection connection = transaction()) {
            party = insertNaturalParty(connection, tenant);
            event = UUID.randomUUID();
            execute(connection, outboxSql("PENDING", "0", "1", "0", "null", "null", "0"), event, tenant, party, ACTOR, ACTOR);
            connection.commit();
        }
        try (Connection first = transaction(); Connection stale = transaction()) {
            assertEquals(1, executeCount(first, "update parties set display_name='Winner',version=version+1 where id=? and version=0", party));
            first.commit();
            assertEquals(0, executeCount(stale, "update parties set display_name='Stale',version=version+1 where id=? and version=0", party));
            stale.commit();
        }
        try (Connection connection = ownerConnection()) {
            assertEquals("Winner", queryString(connection, "select display_name from parties where id=?", party));
            assertEquals(1, queryInt(connection, "select version from parties where id=?", party));
            assertEquals(1, queryInt(connection, "select count(*) from party_outbox_events where id=?", event));
        }
    }

    @Test
    void concurrentOutboxClaimsSkipRowsAlreadyLockedByAnotherPublisher() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID party;
        try (Connection setup = transaction()) {
            party = insertNaturalParty(setup, tenant);
            execute(setup, outboxSql("PENDING", "0", "1", "0", "null", "null", "0"), UUID.randomUUID(), tenant, party, ACTOR, ACTOR);
            execute(setup, outboxSql("PENDING", "0", "1", "0", "null", "null", "0"), UUID.randomUUID(), tenant, party, ACTOR, ACTOR);
            setup.commit();
        }
        try (Connection first = transaction(); Connection second = transaction()) {
            UUID firstClaim = UUID.fromString(queryString(first, "select id::text from party_outbox_events where tenant_id=? and status='PENDING' order by created_at,id for update skip locked limit 1", tenant));
            UUID secondClaim = UUID.fromString(queryString(second, "select id::text from party_outbox_events where tenant_id=? and status='PENDING' order by created_at,id for update skip locked limit 1", tenant));
            org.junit.jupiter.api.Assertions.assertNotEquals(firstClaim, secondClaim);
        }
    }

    @Test
    void runtimeRoleCanReadSchemesButCannotMutateSchemesDeleteOrExecuteGuardFunction() throws Exception {
        try (Connection owner = transaction()) {
            insertScheme(owner, "PRIVILEGE_" + UUID.randomUUID());
            owner.commit();
        }
        try (Connection runtime = ownerConnection()) {
            execute(runtime, "set role party_registry_runtime");
            assertTrue(queryInt(runtime, "select count(*) from identifier_schemes") > 0);
            for (String statement : List.of(
                    "insert into identifier_schemes(code,issuing_country_code,category,applicable_subject_type,name,normalizer_key,validator_key,created_by,updated_by) values ('DENIED','EC','OTHER','BOTH','Denied','N','V','x','x')",
                    "update identifier_schemes set name='Denied'",
                    "delete from identifier_schemes",
                    "delete from parties",
                    "select fn_enforce_party_detail_type()")) {
                SQLException denied = assertThrows(SQLException.class, () -> execute(runtime, statement));
                assertEquals("42501", denied.getSQLState());
            }
        }
        try (Connection owner = ownerConnection()) {
            int before = queryInt(owner, "select count(*) from party_outbox_events");
            execute(owner, "update identifier_schemes set description='governed synthetic change' where code like 'PRIVILEGE_%'");
            assertEquals(before, queryInt(owner, "select count(*) from party_outbox_events"));
        }
    }

    @Test
    void constraintErrorMappingFixtureCoversEveryExpectedDatabaseFailureWithoutSensitiveData() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/database/constraint-error-mapping.csv"), StandardCharsets.UTF_8))) {
            Map<String, String> mappings = reader.lines().filter(line -> !line.startsWith("#") && !line.isBlank())
                    .map(line -> line.split(",", 2))
                    .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
            assertEquals(31, mappings.size());
            assertEquals("CONFLICT", mappings.get("uq_party_identifier_tenant_scheme_hash"));
            assertEquals("NOT_FOUND", mappings.get("fk_party_identifiers_party"));
            assertEquals("VALIDATION_ERROR", mappings.get("ct_parties_detail_type"));
            assertTrue(mappings.values().stream().allMatch(Set.of("CONFLICT", "NOT_FOUND", "VALIDATION_ERROR")::contains));
            assertTrue(mappings.keySet().stream().noneMatch(name -> name.toLowerCase().contains("value")));
        }
    }

    private static void assertCommitConstraint(String constraint, SqlAction action) throws Exception {
        try (Connection connection = transaction()) {
            action.run(connection);
            SQLException failure = assertConstraint(constraint, connection::commit);
            assertEquals("23000", failure.getSQLState());
        }
    }

    private static void assertStatementConstraint(String constraint, SqlAction action) throws Exception {
        try (Connection connection = transaction()) {
            SQLException failure = assertConstraint(constraint, () -> action.run(connection));
            assertTrue(failure.getSQLState().startsWith("23"));
        }
    }

    private static void insertInvalidScheme(Connection connection, String columns, String values) throws SQLException {
        execute(connection, """
                insert into identifier_schemes(code,issuing_country_code,category,applicable_subject_type,name,normalizer_key,validator_key,created_by,updated_by,%s)
                values (?,'EC','OTHER','BOTH','Synthetic invalid','N','V',?,?,%s)
                """.formatted(columns, values), "INVALID_" + UUID.randomUUID(), ACTOR, ACTOR);
    }

    private static String identifierSql(String status, String expiresOn, String normalizationVersion, String keyVersion, String version) {
        return """
                insert into party_identifiers(id,tenant_id,party_id,identifier_scheme_id,encrypted_value,encryption_key_version,normalized_value_hash,masked_value,normalization_version,status,expires_on,created_by,updated_by,version)
                values (?,?,?,?,'synthetic-ciphertext',%s,?,'****0000',%s,'%s',%s,?,?,%s)
                """.formatted(keyVersion, normalizationVersion, status, expiresOn, version);
    }

    private static void assertOutboxConstraint(String constraint, String status, String aggregateVersion, String schemaVersion, String attempts, String publishedAt, String errorCode) throws Exception {
        assertOutboxConstraint(constraint, status, aggregateVersion, schemaVersion, attempts, publishedAt, errorCode, "0");
    }

    private static void assertOutboxConstraint(String constraint, String status, String aggregateVersion, String schemaVersion, String attempts, String publishedAt, String errorCode, String version) throws Exception {
        assertStatementConstraint(constraint, connection -> execute(connection, outboxSql(status, aggregateVersion, schemaVersion, attempts, publishedAt, errorCode, version), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ACTOR, ACTOR));
    }

    private static String outboxSql(String status, String aggregateVersion, String schemaVersion, String attempts, String publishedAt, String errorCode, String version) {
        return """
                insert into party_outbox_events(id,tenant_id,aggregate_type,aggregate_id,aggregate_version,event_type,event_schema_version,payload,status,publish_attempts,published_at,last_error_code,created_by,updated_by,version)
                values (?,?, 'PARTY',?,%s,'party.synthetic.v1',%s,'{}','%s',%s,%s,%s,?,?,%s)
                """.formatted(aggregateVersion, schemaVersion, status, attempts, publishedAt, errorCode, version);
    }

    private static int executeCount(Connection connection, String sql, Object... parameters) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            return statement.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run(Connection connection) throws Exception;
    }
}
