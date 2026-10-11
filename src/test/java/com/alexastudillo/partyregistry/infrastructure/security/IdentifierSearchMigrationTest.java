package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
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
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies Flyway-only search index installation and V8 upgrade preserve actually protected historical rows and checksums. */
@Timeout(120)
class IdentifierSearchMigrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void installsTheIndexWithoutRewritingProtectedHistoryOrEarlierMigrations(boolean upgrade) throws SQLException {
        try (var database = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))) {
            database.withStartupTimeout(Duration.ofSeconds(60));
            database.start();
            Map<String, Integer> previousChecksums = Map.of();
            String history = "";
            if (upgrade) {
                var prior = migrate(database, "8");
                assertEquals(8, prior.migrate().migrationsExecuted);
                previousChecksums = checksums(prior);
                try (Connection connection = connection(database)) {
                    insertProtectedHistory(connection);
                    history = historicalRows(connection);
                }
            }
            var current = migrate(database, "9");
            assertEquals(upgrade ? 1 : 9, current.migrate().migrationsExecuted);
            current.validate();
            var currentChecksums = checksums(current);
            previousChecksums.forEach((version, checksum) -> assertEquals(checksum, currentChecksums.get(version)));
            assertEquals(9, currentChecksums.size());
            assertEquals(0, current.migrate().migrationsExecuted);
            try (Connection connection = connection(database)) {
                try (var query = connection.prepareStatement("""
                        SELECT indexdef FROM pg_catalog.pg_indexes
                        WHERE schemaname = 'public' AND tablename = 'party_identifiers'
                          AND indexname = 'ix_party_identifiers_scheme_created_id'
                        """); var rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("CREATE INDEX ix_party_identifiers_scheme_created_id ON public.party_identifiers USING btree "
                            + "(tenant_id, identifier_scheme_id, created_at DESC, id DESC)", rows.getString(1));
                }
                if (upgrade) assertEquals(history, historicalRows(connection));
            }
        }
    }

    private static void insertProtectedHistory(Connection connection) throws SQLException {
        UUID tenant = UUID.randomUUID();
        UUID party = UUID.randomUUID();
        UUID identifier = UUID.randomUUID();
        UUID scheme;
        try (var query = connection.prepareStatement("SELECT id FROM identifier_schemes ORDER BY code LIMIT 1");
                var rows = query.executeQuery()) {
            assertTrue(rows.next());
            scheme = rows.getObject(1, UUID.class);
        }
        var protection = new JcaIdentifierProtectionAdapter(SecurityTestKeys.keyMaterial(1, 1));
        var protectedValue = protection.protect(new IdentifierProtectionRequest(new TenantId(tenant), new PartyId(party),
                new PartyIdentifierId(identifier), new IdentifierSchemeId(scheme), "SYNTHETIC001", "SYNTHETIC001", new IdentifierRuleVersion(1)));
        connection.setAutoCommit(false);
        try (var root = connection.prepareStatement("""
                INSERT INTO parties (id, tenant_id, type, display_name, record_status, created_by, updated_by)
                VALUES (?, ?, 'NATURAL_PERSON', 'Synthetic historical label', 'ARCHIVED', 'test', 'test')
                """); var details = connection.prepareStatement("""
                INSERT INTO natural_person_details (party_id, given_names, family_names, created_by, updated_by)
                VALUES (?, 'Synthetic', 'History', 'test', 'test')
                """); var evidence = connection.prepareStatement("""
                INSERT INTO party_identifiers (id, tenant_id, party_id, identifier_scheme_id, encrypted_value,
                    encryption_key_version, normalized_value_hash, masked_value, normalization_version, status,
                    is_primary, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'REVOKED', false, 'test', 'test')
                """)) {
            root.setObject(1, party);
            root.setObject(2, tenant);
            root.executeUpdate();
            details.setObject(1, party);
            details.executeUpdate();
            evidence.setObject(1, identifier);
            evidence.setObject(2, tenant);
            evidence.setObject(3, party);
            evidence.setObject(4, scheme);
            evidence.setString(5, protectedValue.encryptedValue());
            evidence.setInt(6, protectedValue.encryptionKeyVersion());
            evidence.setString(7, protectedValue.normalizedValueHash());
            evidence.setString(8, protectedValue.maskedValue());
            evidence.setInt(9, protectedValue.normalizationVersion().value());
            evidence.executeUpdate();
            connection.commit();
        }
    }

    private static String historicalRows(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT jsonb_build_object('party', to_jsonb(p), 'identifier', to_jsonb(i), 'details', to_jsonb(d))::text
                FROM party_identifiers i JOIN parties p ON p.id = i.party_id AND p.tenant_id = i.tenant_id
                JOIN natural_person_details d ON d.party_id = p.id
                """); var rows = query.executeQuery()) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private static Flyway migrate(PostgreSQLContainer database, String target) {
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
