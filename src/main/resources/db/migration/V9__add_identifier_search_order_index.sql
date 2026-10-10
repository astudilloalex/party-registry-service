CREATE INDEX ix_party_identifiers_scheme_created_id
    ON party_identifiers (tenant_id, identifier_scheme_id, created_at DESC, id DESC);
