CREATE TABLE identifier_scheme_idempotency_records (
    tenant_id uuid NOT NULL,
    operation varchar(64) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    identifier_scheme_id uuid NOT NULL,
    result_snapshot_schema_version smallint DEFAULT 1 NOT NULL,
    result_snapshot jsonb NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT pk_identifier_scheme_idempotency_records
        PRIMARY KEY (tenant_id, operation, idempotency_key),
    CONSTRAINT fk_identifier_scheme_idempotency_scheme
        FOREIGN KEY (identifier_scheme_id)
        REFERENCES identifier_schemes (id) ON DELETE RESTRICT,
    CONSTRAINT ck_identifier_scheme_idempotency_nonblank_operation
        CHECK (btrim(operation) <> ''),
    CONSTRAINT ck_identifier_scheme_idempotency_nonblank_key
        CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_identifier_scheme_idempotency_request_hash
        CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_identifier_scheme_idempotency_positive_snapshot_version
        CHECK (result_snapshot_schema_version > 0),
    CONSTRAINT ck_identifier_scheme_idempotency_snapshot_object
        CHECK (jsonb_typeof(result_snapshot) = 'object'),
    CONSTRAINT ck_identifier_scheme_idempotency_nonblank_created_by
        CHECK (btrim(created_by) <> '')
);

CREATE INDEX ix_identifier_scheme_idempotency_scheme
    ON identifier_scheme_idempotency_records (identifier_scheme_id);

CREATE INDEX ix_identifier_schemes_created_id
    ON identifier_schemes (created_at ASC, id ASC);
