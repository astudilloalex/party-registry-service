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

CREATE TABLE parties (
    id uuid PRIMARY KEY NOT NULL DEFAULT uuidv7(),
    tenant_id uuid NOT NULL,
    type party_type NOT NULL,
    display_name varchar(300) NOT NULL,
    record_status party_record_status NOT NULL DEFAULT 'DRAFT',
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_parties_nonblank_display_name CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_parties_nonblank_created_by CHECK (btrim(created_by) <> ''),
    CONSTRAINT ck_parties_nonblank_updated_by CHECK (btrim(updated_by) <> ''),
    CONSTRAINT ck_parties_nonnegative_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_parties_tenant_id ON parties (tenant_id, id);
CREATE INDEX ix_parties_tenant_type_status ON parties (tenant_id, type, record_status);
CREATE INDEX ix_parties_tenant_display_name ON parties (tenant_id, display_name);

CREATE FUNCTION reject_party_type_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.type IS DISTINCT FROM NEW.type THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Party type is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_parties_type_immutable
BEFORE UPDATE OF type ON parties
FOR EACH ROW
EXECUTE FUNCTION reject_party_type_change();
