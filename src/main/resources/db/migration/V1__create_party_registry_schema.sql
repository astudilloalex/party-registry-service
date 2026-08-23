CREATE EXTENSION IF NOT EXISTS btree_gist;

-- PostgreSQL 18 provides pg_catalog.uuidv7(). This public implementation keeps
-- the migration portable to supported PostgreSQL versions that do not provide it.
CREATE OR REPLACE FUNCTION public.uuidv7()
RETURNS uuid
LANGUAGE plpgsql
VOLATILE
PARALLEL SAFE
AS $$
DECLARE
    unix_timestamp_ms bigint;
    random_hex text;
    variant_hex text;
    uuid_hex text;
BEGIN
    unix_timestamp_ms := floor(extract(epoch FROM clock_timestamp()) * 1000);
    random_hex := replace(gen_random_uuid()::text, '-', '');
    variant_hex := substr(
        '89ab',
        ((get_byte(decode(substr(random_hex, 1, 2), 'hex'), 0) & 3) + 1),
        1
    );
    uuid_hex := right(lpad(to_hex(unix_timestamp_ms), 12, '0'), 12)
        || '7'
        || substr(random_hex, 2, 3)
        || variant_hex
        || substr(random_hex, 5, 15);

    RETURN (
        substr(uuid_hex, 1, 8) || '-'
        || substr(uuid_hex, 9, 4) || '-'
        || substr(uuid_hex, 13, 4) || '-'
        || substr(uuid_hex, 17, 4) || '-'
        || substr(uuid_hex, 21, 12)
    )::uuid;
END;
$$;

CREATE TYPE party_type AS ENUM (
    'NATURAL_PERSON',
    'LEGAL_ENTITY'
);

CREATE TYPE party_record_status AS ENUM (
    'DRAFT',
    'ACTIVE',
    'INACTIVE',
    'ARCHIVED'
);

CREATE TYPE identifier_category AS ENUM (
    'NATIONAL_ID',
    'TAX_ID',
    'PASSPORT',
    'RESIDENCE_PERMIT',
    'LEGAL_REGISTRATION_NUMBER',
    'OTHER'
);

CREATE TYPE identifier_subject_type AS ENUM (
    'NATURAL_PERSON',
    'LEGAL_ENTITY',
    'BOTH'
);

CREATE TYPE identifier_scheme_status AS ENUM (
    'DRAFT',
    'ACTIVE',
    'DEPRECATED',
    'RETIRED'
);

CREATE TYPE party_identifier_status AS ENUM (
    'PENDING_VERIFICATION',
    'VERIFIED',
    'REJECTED',
    'EXPIRED',
    'REVOKED'
);

CREATE TYPE party_outbox_aggregate_type AS ENUM (
    'PARTY',
    'PARTY_IDENTIFIER'
);

CREATE TYPE outbox_status AS ENUM (
    'PENDING',
    'PUBLISHED',
    'FAILED'
);

CREATE TABLE parties (
    id uuid DEFAULT uuidv7() NOT NULL,
    tenant_id uuid NOT NULL,
    type party_type NOT NULL,
    display_name varchar(300) NOT NULL,
    record_status party_record_status DEFAULT 'DRAFT' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_parties PRIMARY KEY (id),
    CONSTRAINT uq_parties_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_parties_nonblank_display_name CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_parties_nonblank_created_by CHECK (btrim(created_by) <> ''),
    CONSTRAINT ck_parties_nonblank_updated_by CHECK (btrim(updated_by) <> ''),
    CONSTRAINT ck_parties_nonnegative_version CHECK (version >= 0)
);

CREATE INDEX ix_parties_tenant_type_status
    ON parties (tenant_id, type, record_status);
CREATE INDEX ix_parties_tenant_display_name
    ON parties (tenant_id, display_name);

CREATE TABLE natural_person_details (
    party_id uuid NOT NULL,
    given_names varchar(200) NOT NULL,
    family_names varchar(200) NOT NULL,
    preferred_name varchar(200),
    birth_date date,
    date_of_death date,
    birth_country_code char(2),
    created_at timestamptz DEFAULT now() NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_natural_person_details PRIMARY KEY (party_id),
    CONSTRAINT fk_natural_person_details_party
        FOREIGN KEY (party_id) REFERENCES parties (id) ON DELETE RESTRICT,
    CONSTRAINT ck_natural_person_nonblank_given_names CHECK (btrim(given_names) <> ''),
    CONSTRAINT ck_natural_person_nonblank_family_names CHECK (btrim(family_names) <> ''),
    CONSTRAINT ck_natural_person_nonblank_created_by CHECK (btrim(created_by) <> ''),
    CONSTRAINT ck_natural_person_nonblank_updated_by CHECK (btrim(updated_by) <> ''),
    CONSTRAINT ck_natural_person_life_dates
        CHECK (date_of_death IS NULL OR birth_date IS NULL OR date_of_death >= birth_date),
    CONSTRAINT ck_natural_person_birth_country_code
        CHECK (birth_country_code IS NULL OR birth_country_code ~ '^[A-Z]{2}$')
);

CREATE TABLE legal_entity_details (
    party_id uuid NOT NULL,
    legal_name varchar(300) NOT NULL,
    trade_name varchar(300),
    legal_form_code varchar(64),
    incorporation_country_code char(2) NOT NULL,
    incorporated_on date,
    dissolved_on date,
    created_at timestamptz DEFAULT now() NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_legal_entity_details PRIMARY KEY (party_id),
    CONSTRAINT fk_legal_entity_details_party
        FOREIGN KEY (party_id) REFERENCES parties (id) ON DELETE RESTRICT,
    CONSTRAINT ck_legal_entity_nonblank_legal_name CHECK (btrim(legal_name) <> ''),
    CONSTRAINT ck_legal_entity_nonblank_created_by CHECK (btrim(created_by) <> ''),
    CONSTRAINT ck_legal_entity_nonblank_updated_by CHECK (btrim(updated_by) <> ''),
    CONSTRAINT ck_legal_entity_lifecycle_dates
        CHECK (dissolved_on IS NULL OR incorporated_on IS NULL OR dissolved_on >= incorporated_on),
    CONSTRAINT ck_legal_entity_incorporation_country_code
        CHECK (incorporation_country_code ~ '^[A-Z]{2}$')
);

CREATE TABLE party_nationalities (
    id uuid DEFAULT uuidv7() NOT NULL,
    party_id uuid NOT NULL,
    country_code char(2) NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    valid_from date,
    valid_until date,
    created_at timestamptz DEFAULT now() NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT pk_party_nationalities PRIMARY KEY (id),
    CONSTRAINT fk_party_nationalities_party
        FOREIGN KEY (party_id) REFERENCES parties (id) ON DELETE RESTRICT,
    CONSTRAINT ck_party_nationality_nonblank_created_by CHECK (btrim(created_by) <> ''),
    CONSTRAINT ck_party_nationality_nonblank_updated_by CHECK (btrim(updated_by) <> ''),
    CONSTRAINT ck_party_nationality_country_code CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_party_nationality_validity
        CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from),
    CONSTRAINT ex_party_nationalities_country_validity
        EXCLUDE USING gist (
            party_id WITH =,
            country_code WITH =,
            daterange(valid_from, valid_until, '[]') WITH &&
        ),
    CONSTRAINT ex_party_nationalities_primary_validity
        EXCLUDE USING gist (
            party_id WITH =,
            daterange(valid_from, valid_until, '[]') WITH &&
        ) WHERE (is_primary)
);

CREATE INDEX ix_party_nationalities_country
    ON party_nationalities (party_id, country_code);
CREATE INDEX ix_party_nationalities_primary
    ON party_nationalities (party_id, is_primary);

CREATE TABLE identifier_schemes (
    id uuid DEFAULT uuidv7() NOT NULL,
    code varchar(64) NOT NULL,
    issuing_country_code char(2) NOT NULL,
    category identifier_category NOT NULL,
    applicable_subject_type identifier_subject_type NOT NULL,
    name varchar(150) NOT NULL,
    description varchar(500),
    normalizer_key varchar(64) NOT NULL,
    validator_key varchar(64) NOT NULL,
    minimum_length smallint,
    maximum_length smallint,
    requires_expiration boolean DEFAULT false NOT NULL,
    status identifier_scheme_status DEFAULT 'DRAFT' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_identifier_schemes PRIMARY KEY (id),
    CONSTRAINT uq_identifier_schemes_code UNIQUE (code),
    CONSTRAINT ck_identifier_scheme_country_code
        CHECK (issuing_country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_identifier_scheme_minimum_length
        CHECK (minimum_length IS NULL OR minimum_length > 0),
    CONSTRAINT ck_identifier_scheme_maximum_length
        CHECK (maximum_length IS NULL OR maximum_length > 0),
    CONSTRAINT ck_identifier_scheme_length_range
        CHECK (minimum_length IS NULL OR maximum_length IS NULL OR maximum_length >= minimum_length),
    CONSTRAINT ck_identifier_schemes_nonnegative_version CHECK (version >= 0)
);

CREATE INDEX ix_identifier_schemes_country_category
    ON identifier_schemes (
        issuing_country_code,
        category,
        applicable_subject_type,
        status
    );

CREATE TABLE party_identifiers (
    id uuid DEFAULT uuidv7() NOT NULL,
    tenant_id uuid NOT NULL,
    party_id uuid NOT NULL,
    identifier_scheme_id uuid NOT NULL,
    issuer_code varchar(64),
    encrypted_value text NOT NULL,
    encryption_key_version smallint NOT NULL,
    normalized_value_hash char(64) NOT NULL,
    masked_value varchar(64) NOT NULL,
    normalization_version smallint DEFAULT 1 NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    status party_identifier_status DEFAULT 'PENDING_VERIFICATION' NOT NULL,
    issued_on date,
    expires_on date,
    verified_at timestamptz,
    verified_by varchar(128),
    created_at timestamptz DEFAULT now() NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_party_identifiers PRIMARY KEY (id),
    CONSTRAINT fk_party_identifiers_party
        FOREIGN KEY (tenant_id, party_id)
        REFERENCES parties (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_party_identifiers_scheme
        FOREIGN KEY (identifier_scheme_id)
        REFERENCES identifier_schemes (id) ON DELETE RESTRICT,
    CONSTRAINT ck_party_identifier_validity_dates
        CHECK (expires_on IS NULL OR issued_on IS NULL OR expires_on >= issued_on),
    CONSTRAINT ck_party_identifier_verification
        CHECK ((status = 'VERIFIED' AND verified_at IS NOT NULL AND verified_by IS NOT NULL)
            OR status <> 'VERIFIED'),
    CONSTRAINT ck_party_identifier_expired_date
        CHECK ((status = 'EXPIRED' AND expires_on IS NOT NULL) OR status <> 'EXPIRED'),
    CONSTRAINT ck_party_identifier_normalization_version CHECK (normalization_version > 0),
    CONSTRAINT ck_party_identifier_encryption_key_version CHECK (encryption_key_version > 0),
    CONSTRAINT ck_party_identifier_nonnegative_version CHECK (version >= 0)
);

CREATE INDEX ix_party_identifier_tenant_hash_status
    ON party_identifiers (tenant_id, identifier_scheme_id, normalized_value_hash, status);
CREATE INDEX ix_party_identifiers_party_scheme_status
    ON party_identifiers (tenant_id, party_id, identifier_scheme_id, status);
CREATE INDEX ix_party_identifiers_primary
    ON party_identifiers (tenant_id, party_id, is_primary);
CREATE UNIQUE INDEX uq_party_identifiers_active_value
    ON party_identifiers (tenant_id, identifier_scheme_id, normalized_value_hash)
    WHERE status IN ('PENDING_VERIFICATION', 'VERIFIED');
CREATE UNIQUE INDEX uq_party_identifiers_verified_primary
    ON party_identifiers (tenant_id, party_id, identifier_scheme_id)
    WHERE is_primary = true AND status = 'VERIFIED';

CREATE TABLE party_outbox_events (
    id uuid DEFAULT uuidv7() NOT NULL,
    tenant_id uuid NOT NULL,
    aggregate_type party_outbox_aggregate_type NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_version bigint NOT NULL,
    event_type varchar(128) NOT NULL,
    event_schema_version smallint DEFAULT 1 NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz DEFAULT now() NOT NULL,
    correlation_id varchar(128),
    causation_id varchar(128),
    status outbox_status DEFAULT 'PENDING' NOT NULL,
    publish_attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamptz,
    last_attempt_at timestamptz,
    published_at timestamptz,
    last_error_code varchar(64),
    last_error_detail varchar(1000),
    created_at timestamptz DEFAULT now() NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_party_outbox_events PRIMARY KEY (id),
    CONSTRAINT uq_party_outbox_event_identity UNIQUE (
        tenant_id,
        aggregate_type,
        aggregate_id,
        aggregate_version,
        event_type
    ),
    CONSTRAINT ck_party_outbox_nonblank_event_type CHECK (btrim(event_type) <> ''),
    CONSTRAINT ck_party_outbox_nonblank_created_by CHECK (btrim(created_by) <> ''),
    CONSTRAINT ck_party_outbox_nonblank_updated_by CHECK (btrim(updated_by) <> ''),
    CONSTRAINT ck_party_outbox_nonnegative_aggregate_version CHECK (aggregate_version >= 0),
    CONSTRAINT ck_party_outbox_positive_schema_version CHECK (event_schema_version > 0),
    CONSTRAINT ck_party_outbox_nonnegative_attempts CHECK (publish_attempts >= 0),
    CONSTRAINT ck_party_outbox_nonnegative_version CHECK (version >= 0),
    CONSTRAINT ck_party_outbox_published_at CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL)
    ),
    CONSTRAINT ck_party_outbox_failed_error CHECK (
        (status = 'FAILED' AND last_error_code IS NOT NULL)
        OR status <> 'FAILED'
    ),
    CONSTRAINT ck_party_outbox_created_event_shape CHECK (
        event_type <> 'party.created.v1'
        OR (
            aggregate_type = 'PARTY'
            AND aggregate_version = 0
            AND event_schema_version = 1
            AND payload IN (
                '{"partyType":"NATURAL_PERSON"}'::jsonb,
                '{"partyType":"LEGAL_ENTITY"}'::jsonb
            )
        )
    )
);

CREATE INDEX ix_party_outbox_delivery
    ON party_outbox_events (status, next_attempt_at, created_at);
CREATE INDEX ix_party_outbox_correlation
    ON party_outbox_events (correlation_id);

CREATE OR REPLACE FUNCTION reject_party_type_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.type IS DISTINCT FROM NEW.type THEN
        RAISE EXCEPTION 'Party type is immutable'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_parties_type_immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_parties_type_immutable
BEFORE UPDATE OF type ON parties
FOR EACH ROW
EXECUTE FUNCTION reject_party_type_change();

CREATE OR REPLACE FUNCTION enforce_party_detail_shape()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    affected_party_id uuid;
    affected_party_type party_type;
    has_natural_person_details boolean;
    has_legal_entity_details boolean;
BEGIN
    IF TG_TABLE_NAME = 'parties' THEN
        affected_party_id := (to_jsonb(NEW) ->> 'id')::uuid;
    ELSE
        affected_party_id := (to_jsonb(NEW) ->> 'party_id')::uuid;
    END IF;

    SELECT parties.type
    INTO affected_party_type
    FROM parties
    WHERE parties.id = affected_party_id
    FOR NO KEY UPDATE;

    IF NOT FOUND THEN
        RETURN NEW;
    END IF;

    SELECT
        EXISTS (
            SELECT 1
            FROM natural_person_details
            WHERE party_id = affected_party_id
        ),
        EXISTS (
            SELECT 1
            FROM legal_entity_details
            WHERE party_id = affected_party_id
        )
    INTO has_natural_person_details, has_legal_entity_details;

    IF has_natural_person_details AND has_legal_entity_details
        OR affected_party_type = 'NATURAL_PERSON' AND has_legal_entity_details
        OR affected_party_type = 'LEGAL_ENTITY' AND has_natural_person_details THEN
        RAISE EXCEPTION 'Party detail shape does not match Party type'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_parties_detail_shape';
    END IF;

    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_parties_detail_shape
AFTER INSERT ON parties
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_party_detail_shape();

CREATE CONSTRAINT TRIGGER ct_natural_person_details_party_shape
AFTER INSERT OR UPDATE OF party_id ON natural_person_details
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_party_detail_shape();

CREATE CONSTRAINT TRIGGER ct_legal_entity_details_party_shape
AFTER INSERT OR UPDATE OF party_id ON legal_entity_details
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_party_detail_shape();

CREATE OR REPLACE FUNCTION reject_active_identifier_scheme_identity_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status <> 'DRAFT'
        AND (
            OLD.code IS DISTINCT FROM NEW.code
            OR OLD.issuing_country_code IS DISTINCT FROM NEW.issuing_country_code
            OR OLD.category IS DISTINCT FROM NEW.category
            OR OLD.applicable_subject_type IS DISTINCT FROM NEW.applicable_subject_type
        ) THEN
        RAISE EXCEPTION 'Activated identifier scheme identity fields are immutable'
            USING ERRCODE = '23514',
                CONSTRAINT = 'ck_identifier_scheme_activated_identity_immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_identifier_scheme_activated_identity_immutable
BEFORE UPDATE OF code, issuing_country_code, category, applicable_subject_type
ON identifier_schemes
FOR EACH ROW
EXECUTE FUNCTION reject_active_identifier_scheme_identity_change();
