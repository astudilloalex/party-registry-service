-- Isolated retained roots for the packaged JVM/native root contract suite.
INSERT INTO parties (id, tenant_id, type, display_name, record_status, created_at, updated_at, created_by, updated_by)
VALUES
    ('0198d5f0-0000-7000-8000-000000000101', '0198d5f0-0000-7000-8000-000000000100', 'NATURAL_PERSON',
     'Packaged Root Person', 'DRAFT', '2026-09-19T12:00:00.123456Z', '2026-09-19T12:00:00.123456Z', 'fixture', 'fixture'),
    ('0198d5f0-0000-7000-8000-000000000102', '0198d5f0-0000-7000-8000-000000000100', 'LEGAL_ENTITY',
     'Packaged Root Company', 'DRAFT', '2026-09-19T12:00:00.123456Z', '2026-09-19T12:00:00.123456Z', 'fixture', 'fixture');

INSERT INTO natural_person_details (party_id, given_names, family_names, created_by, updated_by)
VALUES ('0198d5f0-0000-7000-8000-000000000101', 'Packaged', 'Root Person', 'fixture', 'fixture');

INSERT INTO legal_entity_details (party_id, legal_name, incorporation_country_code, created_by, updated_by)
VALUES ('0198d5f0-0000-7000-8000-000000000102', 'Packaged Root Company', 'GB', 'fixture', 'fixture');

INSERT INTO party_identifiers (tenant_id, party_id, identifier_scheme_id, encrypted_value, encryption_key_version,
                              normalized_value_hash, masked_value, status, verified_at, verified_by, created_by, updated_by)
SELECT tenant_id, id, '0198d111-08f1-7e48-b291-399bbb9cd606', 'test-only-unread-ciphertext', 1,
       repeat(replace(id::text, '-', ''), 2), '***1234', 'VERIFIED', '2026-09-19T12:01:00Z', 'fixture', 'fixture', 'fixture'
FROM parties WHERE tenant_id = '0198d5f0-0000-7000-8000-000000000100';
