package com.alexastudillo.partyregistry.support;

import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.domain.policy.StandardIdentifierNormalizer;
import com.alexastudillo.partyregistry.infrastructure.security.JcaIdentifierProtectionAdapter;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Inserts synthetic protected historical evidence using test-thread DML only into the Flyway-managed schema. */
public final class IdentifierSearchDatabaseFixture {
    private final Connection connection;
    private final JcaIdentifierProtectionAdapter protection;

    public IdentifierSearchDatabaseFixture(Connection connection, JcaIdentifierProtectionAdapter protection) {
        this.connection = connection;
        this.protection = protection;
    }

    /** Creates intentionally withdrawn or admission-incompatible schemes for normalizer-only read verification. */
    public UUID scheme(String code, IdentifierSchemeStatus status, String normalizer) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var statement = connection.prepareStatement("""
                insert into identifier_schemes (id, code, issuing_country_code, category, applicable_subject_type,
                    name, normalizer_key, validator_key, minimum_length, maximum_length, requires_expiration,
                    status, created_by, updated_by)
                values (?, ?, 'EC', 'OTHER', 'NATURAL_PERSON', 'Synthetic Search Scheme', ?, 'UNKNOWN_VALIDATOR',
                    100, 256, true, ?::identifier_scheme_status, 'search-fixture', 'search-fixture')
                """)) {
            statement.setObject(1, id);
            statement.setString(2, code);
            statement.setString(3, normalizer);
            statement.setString(4, status.name());
            statement.executeUpdate();
        }
        return id;
    }

    /** Creates a retained Party together with its real subtype row and nationality history. */
    public UUID party(UUID tenant, PartyType type, PartyRecordStatus status) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var statement = connection.prepareStatement("""
                insert into parties (id, tenant_id, type, display_name, record_status, created_by, updated_by)
                values (?, ?, ?::party_type, 'Stored Synthetic Label', ?::party_record_status, 'search-fixture', 'search-fixture')
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, tenant);
            statement.setString(3, type.name());
            statement.setString(4, status.name());
            statement.executeUpdate();
        }
        String subtype = type == PartyType.NATURAL_PERSON
                ? "insert into natural_person_details (party_id, given_names, family_names, created_by, updated_by) values (?, 'Synthetic', 'History', 'search-fixture', 'search-fixture')"
                : "insert into legal_entity_details (party_id, legal_name, incorporation_country_code, created_by, updated_by) values (?, 'Synthetic History', 'EC', 'search-fixture', 'search-fixture')";
        try (var statement = connection.prepareStatement(subtype)) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("""
                insert into party_nationalities (party_id, country_code, is_primary, created_by, updated_by)
                values (?, 'EC', true, 'search-fixture', 'search-fixture')
                """)) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
        return id;
    }

    /** Uses the production AES/HMAC/AAD implementation and retains independent stored masks, states and past-date evidence. */
    public UUID identifier(UUID tenant, UUID party, UUID scheme, UUID id, String value, PartyIdentifierStatus status,
            boolean primary, Instant created) throws SQLException {
        String normalized = StandardIdentifierNormalizer.TRIM_UPPERCASE_V1.normalize(value);
        var stored = protection.protect(new IdentifierProtectionRequest(new TenantId(tenant), new PartyId(party),
                new PartyIdentifierId(id), new IdentifierSchemeId(scheme), value, normalized, new IdentifierRuleVersion(1)));
        try (var statement = connection.prepareStatement("""
                insert into party_identifiers (id, tenant_id, party_id, identifier_scheme_id, encrypted_value,
                    encryption_key_version, normalized_value_hash, masked_value, normalization_version, status,
                    is_primary, created_at, expires_on, verified_at, verified_by, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, 'Stored-Safe-Mask', 1, ?::party_identifier_status, ?, ?,
                    '2000-01-01', '1999-01-01', 'historical-verifier', 'search-fixture', 'search-fixture')
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, tenant);
            statement.setObject(3, party);
            statement.setObject(4, scheme);
            statement.setString(5, stored.encryptedValue());
            statement.setInt(6, stored.encryptionKeyVersion());
            statement.setString(7, stored.normalizedValueHash());
            statement.setString(8, status.name());
            statement.setBoolean(9, primary);
            statement.setTimestamp(10, Timestamp.from(created));
            statement.executeUpdate();
        }
        return id;
    }
}
