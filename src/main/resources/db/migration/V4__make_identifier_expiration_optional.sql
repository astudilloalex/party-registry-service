UPDATE identifier_schemes
SET requires_expiration = false,
    updated_at = now(),
    updated_by = 'system',
    version = version + 1
WHERE requires_expiration = true;
