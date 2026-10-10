ALTER TABLE party_identifiers
    ADD CONSTRAINT uq_party_identifiers_owner_id UNIQUE (tenant_id, party_id, id);

CREATE TABLE party_identifier_idempotency_records (
    tenant_id uuid NOT NULL,
    operation varchar(64) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    party_id uuid NOT NULL,
    identifier_id uuid NOT NULL,
    result_snapshot_schema_version smallint DEFAULT 1 NOT NULL,
    result_snapshot jsonb NOT NULL,
    expected_identifier_version bigint,
    created_at timestamptz DEFAULT now() NOT NULL,
    created_by varchar(128) NOT NULL,
    CONSTRAINT pk_party_identifier_idempotency_records
        PRIMARY KEY (tenant_id, operation, idempotency_key),
    CONSTRAINT fk_party_identifier_idempotency_owner
        FOREIGN KEY (tenant_id, party_id, identifier_id)
        REFERENCES party_identifiers (tenant_id, party_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_party_identifier_idempotency_operation
        CHECK (operation IN (
            'party.identifier.create.v1',
            'party.identifier.verify.v1',
            'party.identifier.reject.v1',
            'party.identifier.revoke.v1',
            'party.identifier.set-primary.v1'
        )),
    CONSTRAINT ck_party_identifier_idempotency_key
        CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_party_identifier_idempotency_hash
        CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_party_identifier_idempotency_schema
        CHECK (result_snapshot_schema_version > 0),
    CONSTRAINT ck_party_identifier_idempotency_snapshot
        CHECK (jsonb_typeof(result_snapshot) = 'object'),
    CONSTRAINT ck_party_identifier_idempotency_expected_version
        CHECK ((operation = 'party.identifier.create.v1' AND expected_identifier_version IS NULL)
            OR (operation <> 'party.identifier.create.v1'
                AND expected_identifier_version IS NOT NULL AND expected_identifier_version >= 0)),
    CONSTRAINT ck_party_identifier_idempotency_actor
        CHECK (btrim(created_by) <> '')
);

CREATE INDEX ix_party_identifier_idempotency_owner
    ON party_identifier_idempotency_records (tenant_id, party_id, identifier_id);

CREATE TABLE party_identifier_action_audit (
    id uuid DEFAULT uuidv7() NOT NULL,
    tenant_id uuid NOT NULL,
    party_id uuid NOT NULL,
    identifier_id uuid NOT NULL,
    action varchar(6) NOT NULL,
    resulting_version bigint NOT NULL,
    actor varchar(128) NOT NULL,
    occurred_at timestamptz DEFAULT now() NOT NULL,
    reason_code bytea,
    reason_detail bytea,
    CONSTRAINT pk_party_identifier_action_audit PRIMARY KEY (id),
    CONSTRAINT fk_party_identifier_audit_owner
        FOREIGN KEY (tenant_id, party_id, identifier_id)
        REFERENCES party_identifiers (tenant_id, party_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_party_identifier_audit_result
        UNIQUE (tenant_id, identifier_id, resulting_version, action),
    CONSTRAINT ck_party_identifier_audit_action CHECK (action IN ('REJECT', 'REVOKE')),
    CONSTRAINT ck_party_identifier_audit_version CHECK (resulting_version > 0),
    CONSTRAINT ck_party_identifier_audit_actor CHECK (btrim(actor) <> ''),
    -- Exact UTF-16 code units retain controls and null-versus-empty reason inputs.
    CONSTRAINT ck_party_identifier_audit_reason_code
        CHECK (reason_code IS NULL
            OR (octet_length(reason_code) <= 256 AND octet_length(reason_code) % 2 = 0)),
    CONSTRAINT ck_party_identifier_audit_reason_detail
        CHECK (reason_detail IS NULL
            OR (octet_length(reason_detail) <= 4000 AND octet_length(reason_detail) % 2 = 0))
);

CREATE INDEX ix_party_identifier_audit_owner
    ON party_identifier_action_audit (tenant_id, party_id, identifier_id);

CREATE INDEX ix_party_identifiers_created_id
    ON party_identifiers (tenant_id, party_id, created_at DESC, id DESC);
