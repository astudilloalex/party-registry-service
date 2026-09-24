INSERT INTO parties (
    id,
    tenant_id,
    type,
    display_name,
    record_status,
    created_at,
    created_by,
    updated_at,
    updated_by,
    version
) VALUES (
    '0198d5f0-0000-7000-8000-000000000001',
    '0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1',
    'NATURAL_PERSON',
    'Packaged Activation Fixture',
    'DRAFT',
    '2026-09-04T00:00:00Z',
    'packaged-fixture',
    '2026-09-04T00:00:00Z',
    'packaged-fixture',
    0
);

INSERT INTO natural_person_details (
    party_id,
    given_names,
    family_names,
    created_at,
    created_by,
    updated_at,
    updated_by
) VALUES (
    '0198d5f0-0000-7000-8000-000000000001',
    'Packaged',
    'Activation Fixture',
    '2026-09-04T00:00:00Z',
    'packaged-fixture',
    '2026-09-04T00:00:00Z',
    'packaged-fixture'
);

INSERT INTO party_identifiers (
    id,
    tenant_id,
    party_id,
    identifier_scheme_id,
    encrypted_value,
    encryption_key_version,
    normalized_value_hash,
    masked_value,
    normalization_version,
    is_primary,
    status,
    verified_at,
    verified_by,
    created_at,
    created_by,
    updated_at,
    updated_by,
    version
) VALUES (
    '0198d5f0-0000-7000-8000-000000000002',
    '0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1',
    '0198d5f0-0000-7000-8000-000000000001',
    '0198d111-08f1-7e48-b291-399bbb9cd601',
    'v1.cGFja2FnZWRub25j.cGFja2FnZWRhdXRoZW50aWNhdGVkY2lwaGVydGV4dA',
    1,
    'abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789',
    '********5678',
    1,
    false,
    'VERIFIED',
    '2026-09-04T00:01:00Z',
    'packaged-verifier',
    '2026-09-04T00:00:00Z',
    'packaged-fixture',
    '2026-09-04T00:01:00Z',
    'packaged-verifier',
    1
);
