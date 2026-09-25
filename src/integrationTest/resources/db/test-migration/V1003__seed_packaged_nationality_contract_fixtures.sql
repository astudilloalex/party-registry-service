-- Retained fixtures for packaged JVM/native nationality routes, isolated from root-contract tenants.
INSERT INTO parties (id, tenant_id, type, display_name, record_status, created_at, updated_at, created_by, updated_by)
VALUES
    ('0198d5f0-0000-7000-8000-000000000201', '0198d5f0-0000-7000-8000-000000000200', 'NATURAL_PERSON',
     'Nationality Person', 'ARCHIVED', '2026-09-19T12:00:00Z', '2026-09-19T12:00:00Z', 'fixture', 'fixture'),
    ('0198d5f0-0000-7000-8000-000000000202', '0198d5f0-0000-7000-8000-000000000200', 'LEGAL_ENTITY',
     'Nationality Company', 'INACTIVE', '2026-09-19T12:00:00Z', '2026-09-19T12:00:00Z', 'fixture', 'fixture'),
    ('0198d5f0-0000-7000-8000-000000000204', '0198d5f0-0000-7000-8000-000000000200', 'LEGAL_ENTITY',
     'Nationality Page Fixture', 'ARCHIVED', '2026-09-19T12:00:00Z', '2026-09-19T12:00:00Z', 'fixture', 'fixture');

INSERT INTO natural_person_details (party_id, given_names, family_names, created_by, updated_by)
VALUES ('0198d5f0-0000-7000-8000-000000000201', 'Packaged', 'Nationality', 'fixture', 'fixture');

INSERT INTO legal_entity_details (party_id, legal_name, incorporation_country_code, created_by, updated_by)
VALUES ('0198d5f0-0000-7000-8000-000000000202', 'Nationality Company', 'GB', 'fixture', 'fixture'),
       ('0198d5f0-0000-7000-8000-000000000204', 'Nationality Page Fixture', 'GB', 'fixture', 'fixture');

INSERT INTO party_nationalities (id, party_id, country_code, is_primary, valid_from, valid_until,
                                 created_at, updated_at, created_by, updated_by)
VALUES ('0198d5f0-0000-7000-8000-000000000203', '0198d5f0-0000-7000-8000-000000000202',
        'EC', false, '2025-01-01', '2025-12-31', '2025-01-01T10:00:00Z', '2025-01-01T10:00:00Z', 'fixture', 'fixture'),
       ('0198d5f0-0000-7000-8000-000000000205', '0198d5f0-0000-7000-8000-000000000204',
        'EC', false, '2025-01-01', '2025-12-31', '2025-01-01T10:00:00Z', '2025-01-01T10:00:00Z', 'fixture', 'fixture');
