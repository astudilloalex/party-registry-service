-- Historical and exhausted records are JVM test-only; normal administration scenarios use the API.
INSERT INTO identifier_schemes (
    id, code, issuing_country_code, category, applicable_subject_type, name, description,
    normalizer_key, validator_key, minimum_length, maximum_length, requires_expiration, status,
    version, created_at, updated_at, created_by, updated_by
)
SELECT CAST(f.id AS uuid), f.code, 'QV', 'OTHER', 'BOTH', 'Historical scheme', 'Retained description',
       f.normalizer_key, 'ALPHANUMERIC_V1', 10, 20, false, CAST(f.status AS identifier_scheme_status),
       f.version, TIMESTAMPTZ '2020-01-01 00:00:00+00', TIMESTAMPTZ '2020-01-01 00:00:00+00',
       'historical-creator', 'historical-creator'
FROM (VALUES
    ('0198d112-08f1-7e48-b291-399bbb9cd601', 'HTTP_OBSOLETE_PATCH_ACTIVE', 'OBSOLETE_V1', 'ACTIVE', 0::bigint),
    ('0198d112-08f1-7e48-b291-399bbb9cd602', 'HTTP_OBSOLETE_PATCH_DEPRECATED', 'OBSOLETE_V1', 'DEPRECATED', 0::bigint),
    ('0198d112-08f1-7e48-b291-399bbb9cd603', 'HTTP_EXHAUSTED_DRAFT', 'TRIM_UPPERCASE_V1', 'DRAFT', 9223372036854775807::bigint),
    ('0198d112-08f1-7e48-b291-399bbb9cd604', 'HTTP_EXHAUSTED_ACTIVE', 'TRIM_UPPERCASE_V1', 'ACTIVE', 9223372036854775807::bigint),
    ('0198d112-08f1-7e48-b291-399bbb9cd605', 'HTTP_EXHAUSTED_DEPRECATED', 'TRIM_UPPERCASE_V1', 'DEPRECATED', 9223372036854775807::bigint),
    ('0198d112-08f1-7e48-b291-399bbb9cd606', 'HTTP_OBSOLETE_LIFECYCLE_DRAFT', 'OBSOLETE_V1', 'DRAFT', 0::bigint),
    ('0198d112-08f1-7e48-b291-399bbb9cd607', 'HTTP_OBSOLETE_LIFECYCLE_ACTIVE', 'OBSOLETE_V1', 'ACTIVE', 0::bigint),
    ('0198d112-08f1-7e48-b291-399bbb9cd608', 'HTTP_OBSOLETE_LIFECYCLE_DEPRECATED', 'OBSOLETE_V1', 'DEPRECATED', 0::bigint)
) AS f(id, code, normalizer_key, status, version);
