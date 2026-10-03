-- Historical evidence cannot be provisioned by the registration API, which admits only active supported configuration.
-- Separate slots isolate stored-only and published-mode regression tests without editing earlier fixture migrations.
INSERT INTO identifier_schemes (id, code, issuing_country_code, category, applicable_subject_type, name,
    normalizer_key, validator_key, requires_expiration, status, created_at, updated_at, created_by, updated_by)
SELECT CAST('0198d113-08f1-7e48-b291-399bbb9cd' || lpad(slot::text, 3, '0') AS uuid),
    'HISTORY_SCHEME_' || slot, 'QH', 'OTHER', 'BOTH', 'Historical evidence scheme',
    'OBSOLETE_NORMALIZER_V1', 'OBSOLETE_VALIDATOR_V1', true, 'ACTIVE',
    TIMESTAMPTZ '2020-01-01 00:00:00+00', TIMESTAMPTZ '2020-01-02 00:00:00+00', 'historical-creator', 'historical-editor'
FROM generate_series(1, 8) AS slot;

INSERT INTO parties (id, tenant_id, type, display_name, record_status, created_at, updated_at, created_by, updated_by)
SELECT CAST('0198d114-08f1-7e48-b291-399bbb9cd' || lpad(slot::text, 3, '0') AS uuid),
    CAST('0198d116-08f1-7e48-b291-399bbb9cd' || lpad(slot::text, 3, '0') AS uuid),
    CAST(CASE WHEN slot % 2 = 1 THEN 'NATURAL_PERSON' ELSE 'LEGAL_ENTITY' END AS party_type),
    'Historical evidence party ' || slot, 'DRAFT', TIMESTAMPTZ '2020-01-01 00:00:00+00',
    TIMESTAMPTZ '2020-01-02 00:00:00+00', 'historical-creator', 'historical-editor'
FROM generate_series(1, 8) AS slot;

INSERT INTO natural_person_details (party_id, given_names, family_names, created_by, updated_by)
SELECT id, 'Historical', 'Evidence', 'historical-creator', 'historical-editor'
FROM parties WHERE id::text LIKE '0198d114-%' AND type = 'NATURAL_PERSON';

INSERT INTO legal_entity_details (party_id, legal_name, incorporation_country_code, created_by, updated_by)
SELECT id, 'Historical Evidence Ltd', 'EC', 'historical-creator', 'historical-editor'
FROM parties WHERE id::text LIKE '0198d114-%' AND type = 'LEGAL_ENTITY';

INSERT INTO party_identifiers (id, tenant_id, party_id, identifier_scheme_id, encrypted_value,
    encryption_key_version, normalized_value_hash, masked_value, normalization_version,
    is_primary, status, verified_at, verified_by, version, created_at, updated_at, created_by, updated_by)
SELECT CAST('0198d115-08f1-7e48-b291-399bbb9cd' || lpad(slot::text, 3, '0') AS uuid),
    CAST('0198d116-08f1-7e48-b291-399bbb9cd' || lpad(slot::text, 3, '0') AS uuid),
    CAST('0198d114-08f1-7e48-b291-399bbb9cd' || lpad(slot::text, 3, '0') AS uuid),
    CAST('0198d113-08f1-7e48-b291-399bbb9cd' || lpad(slot::text, 3, '0') AS uuid),
    'historical-unread-ciphertext-' || slot, 2, repeat(md5('historical-evidence-' || slot), 2),
    '***1234', 3, false, 'VERIFIED', TIMESTAMPTZ '2020-01-02 00:00:00+00', 'historical-verifier', 7,
    TIMESTAMPTZ '2020-01-01 00:00:00+00', TIMESTAMPTZ '2020-01-02 00:00:00+00', 'historical-creator', 'historical-editor'
FROM generate_series(1, 8) AS slot;
