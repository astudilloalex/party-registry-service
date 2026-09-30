CREATE TABLE api_idempotency_records (
    tenant_id uuid NOT NULL,
    operation varchar(64) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    party_id uuid NOT NULL,
    result_snapshot_schema_version smallint DEFAULT 1 NOT NULL,
    result_snapshot jsonb NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT pk_api_idempotency_records
        PRIMARY KEY (tenant_id, operation, idempotency_key),
    CONSTRAINT fk_api_idempotency_records_party
        FOREIGN KEY (tenant_id, party_id)
        REFERENCES parties (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_api_idempotency_nonblank_operation
        CHECK (btrim(operation) <> ''),
    CONSTRAINT ck_api_idempotency_nonblank_key
        CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_api_idempotency_request_hash
        CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_api_idempotency_positive_snapshot_schema_version
        CHECK (result_snapshot_schema_version > 0),
    CONSTRAINT ck_api_idempotency_snapshot_object
        CHECK (jsonb_typeof(result_snapshot) = 'object'),
    CONSTRAINT ck_api_idempotency_nonblank_created_by
        CHECK (btrim(created_by) <> '')
);

CREATE INDEX ix_api_idempotency_records_party
    ON api_idempotency_records (tenant_id, party_id);
