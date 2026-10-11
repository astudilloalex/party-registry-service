package com.alexastudillo.partyregistry;

import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.domain.policy.StandardIdentifierNormalizer;
import com.alexastudillo.partyregistry.infrastructure.security.*;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Seeds actual version-one protected history before launching a packaged service that writes with version-two keys. */
public final class PackagedIdentifierSearchResource implements QuarkusTestResourceLifecycleManager {
    static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000911");
    static final UUID SCHEME = UUID.fromString("00000000-0000-4000-8000-000000000912");
    static final UUID CORRUPT_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000921");
    static final String CODE = "PACKAGED_SEARCH_HISTORY";
    static final Instant CREATED = Instant.parse("2020-01-01T00:00:00.123456Z");
    static final String CURSOR_V1 = "Y3Vyc29yLWtleS12MS0wMTIzNDU2Nzg5YWJjZGVmMDE=";
    private final PackagedGeographicReferenceResource resources = new PackagedGeographicReferenceResource();

    @Override
    public Map<String, String> start() {
        var properties = new HashMap<>(resources.start());
        try (var connection = DriverManager.getConnection(properties.get("quarkus.datasource.jdbc.url"),
                properties.get("quarkus.datasource.username"), properties.get("quarkus.datasource.password"))) {
            seed(connection);
            var historical = new HistoricalKeys();
            properties.put("party-registry.identifier-protection.encryption-keys.1", historical.encryptionKeys().get("1"));
            properties.put("party-registry.identifier-protection.index-hmac-key", historical.indexHmacKey().orElseThrow());
            properties.put("party-registry.idempotency.registration-hmac-key", historical.registrationHmacKey().orElseThrow());
            properties.put("party-registry.pagination.signing-keys.test-v1", CURSOR_V1);
            properties.put("party-registry.identifier-protection.encryption-keys.2", "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=");
            properties.put("party-registry.identifier-protection.current-encryption-key-version", "2");
            properties.put("%test.party-registry.identifier-protection.current-encryption-key-version", "2");
            properties.put("party-registry.pagination.signing-keys.test-v2", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
            properties.put("party-registry.pagination.current-signing-key-id", "test-v2");
            properties.put("%test.party-registry.pagination.current-signing-key-id", "test-v2");
            return Map.copyOf(properties);
        } catch (SQLException | RuntimeException failure) {
            resources.stop();
            throw new IllegalStateException("Cannot initialize packaged identifier search fixtures", failure);
        }
    }

    @Override public void stop() { resources.stop(); }

    private static void seed(Connection connection) throws SQLException {
        var configuration = new HistoricalKeys();
        var protection = new JcaIdentifierProtectionAdapter(new CryptographicKeyMaterial(configuration, configuration));
        try (var statement = connection.prepareStatement("""
                insert into identifier_schemes (id, code, issuing_country_code, category, applicable_subject_type, name,
                    normalizer_key, validator_key, minimum_length, maximum_length, requires_expiration, status, created_by, updated_by)
                values (?, ?, 'EC', 'OTHER', 'NATURAL_PERSON', 'Packaged Historical Search', 'TRIM_UPPERCASE_V1',
                    'UNKNOWN_VALIDATOR', 100, 256, true, 'RETIRED', 'packaged-fixture', 'packaged-fixture')
                """)) {
            statement.setObject(1, SCHEME);
            statement.setString(2, CODE);
            statement.executeUpdate();
        }
        UUID natural = UUID.fromString("00000000-0000-4000-8000-000000000913");
        UUID legal = UUID.fromString("00000000-0000-4000-8000-000000000914");
        party(connection, TENANT, natural, false);
        party(connection, TENANT, legal, true);
        for (int index = 1; index <= 5; index++) {
            identifier(connection, protection, TENANT, index % 2 == 0 ? legal : natural, new UUID(Long.MIN_VALUE, index), false);
        }
        UUID corrupt = UUID.fromString("00000000-0000-4000-8000-000000000922");
        party(connection, CORRUPT_TENANT, corrupt, false);
        identifier(connection, protection, CORRUPT_TENANT, corrupt, new UUID(Long.MIN_VALUE, 99), true);
    }

    private static void party(Connection connection, UUID tenant, UUID id, boolean legal) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into parties (id, tenant_id, type, display_name, record_status, created_by, updated_by)
                values (?, ?, ?::party_type, 'Packaged Stored Label', 'ARCHIVED', 'packaged-fixture', 'packaged-fixture')
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, tenant);
            statement.setString(3, legal ? "LEGAL_ENTITY" : "NATURAL_PERSON");
            statement.executeUpdate();
        }
        String sql = legal
                ? "insert into legal_entity_details (party_id, legal_name, incorporation_country_code, created_by, updated_by) values (?, 'Packaged History', 'EC', 'packaged-fixture', 'packaged-fixture')"
                : "insert into natural_person_details (party_id, given_names, family_names, created_by, updated_by) values (?, 'Packaged', 'History', 'packaged-fixture', 'packaged-fixture')";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    private static void identifier(Connection connection, JcaIdentifierProtectionAdapter protection, UUID tenant, UUID party,
            UUID id, boolean corrupt) throws SQLException {
        String value = "  historical%_é9  ";
        var stored = protection.protect(new IdentifierProtectionRequest(new TenantId(tenant), new PartyId(party), new PartyIdentifierId(id),
                new IdentifierSchemeId(SCHEME), value, StandardIdentifierNormalizer.TRIM_UPPERCASE_V1.normalize(value), new IdentifierRuleVersion(1)));
        try (var statement = connection.prepareStatement("""
                insert into party_identifiers (id, tenant_id, party_id, identifier_scheme_id, encrypted_value, encryption_key_version,
                    normalized_value_hash, masked_value, normalization_version, status, is_primary, created_at, expires_on, created_by, updated_by)
                values (?, ?, ?, ?, ?, 1, ?, 'Stored-Safe-Mask', 1, 'REVOKED', false, ?, '2000-01-01', 'packaged-fixture', 'packaged-fixture')
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, tenant);
            statement.setObject(3, party);
            statement.setObject(4, SCHEME);
            statement.setString(5, corrupt ? "v1.CORRUPT" : stored.encryptedValue());
            statement.setString(6, stored.normalizedValueHash());
            statement.setTimestamp(7, Timestamp.from(CREATED));
            statement.executeUpdate();
        }
    }

    /** Supplies deterministic historical fixture keys only to the prelaunch test process, never production defaults. */
    private record HistoricalKeys() implements IdentifierProtectionConfiguration, RegistrationFingerprintConfiguration {
        @Override public Map<String, String> encryptionKeys() { return Map.of("1", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="); }
        @Override public Optional<String> currentEncryptionKeyVersion() { return Optional.of("1"); }
        @Override public Optional<String> indexHmacKey() { return Optional.of("YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="); }
        @Override public Optional<String> registrationHmacKey() { return Optional.of("ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA="); }
    }
}
