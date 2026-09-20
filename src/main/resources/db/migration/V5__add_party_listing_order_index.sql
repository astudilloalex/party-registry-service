CREATE INDEX ix_parties_tenant_created_id
    ON parties (tenant_id, created_at DESC, id DESC);
