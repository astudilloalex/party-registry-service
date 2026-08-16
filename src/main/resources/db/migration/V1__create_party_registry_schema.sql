-- T010 / docs/database/v1-scheme.dbml: initial Party Registry V1 schema.
-- PostgreSQL 18 supplies uuidv7() in core; no extension is required.
-- Precondition: the separately provisioned NOLOGIN/group role
-- party_registry_runtime exists. The Flyway connection role owns all objects.

CREATE TYPE public.party_type AS ENUM (
    'NATURAL_PERSON',
    'LEGAL_ENTITY'
);

CREATE TYPE public.party_record_status AS ENUM (
    'DRAFT',
    'ACTIVE',
    'INACTIVE',
    'ARCHIVED'
);

CREATE TYPE public.identifier_category AS ENUM (
    'NATIONAL_ID',
    'TAX_ID',
    'PASSPORT',
    'RESIDENCE_PERMIT',
    'LEGAL_REGISTRATION_NUMBER',
    'OTHER'
);

CREATE TYPE public.identifier_subject_type AS ENUM (
    'NATURAL_PERSON',
    'LEGAL_ENTITY',
    'BOTH'
);

CREATE TYPE public.identifier_scheme_status AS ENUM (
    'DRAFT',
    'ACTIVE',
    'DEPRECATED',
    'RETIRED'
);

CREATE TYPE public.party_identifier_status AS ENUM (
    'PENDING_VERIFICATION',
    'VERIFIED',
    'REJECTED',
    'EXPIRED',
    'REVOKED'
);

CREATE TYPE public.party_outbox_aggregate_type AS ENUM (
    'PARTY',
    'PARTY_IDENTIFIER'
);

CREATE TYPE public.outbox_status AS ENUM (
    'PENDING',
    'PUBLISHED',
    'FAILED'
);

CREATE TABLE public.parties (
    id uuid DEFAULT uuidv7() NOT NULL,
    tenant_id uuid NOT NULL,
    type public.party_type NOT NULL,
    display_name varchar(300) NOT NULL,
    record_status public.party_record_status DEFAULT 'DRAFT' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    created_by varchar(128) NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    updated_by varchar(128) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_parties PRIMARY KEY (id),
    CONSTRAINT uq_parties_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_parties_nonnegative_version CHECK (version >= 0)
);

CREATE INDEX ix_parties_tenant_type_status
    ON public.parties (tenant_id, type, record_status);
CREATE INDEX ix_parties_tenant_display_name
    ON public.parties (tenant_id, display_name);

COMMENT ON TABLE public.parties IS
    'Root of the Party aggregate. A Party is either NATURAL_PERSON or LEGAL_ENTITY. display_name is derived deterministically by Domain/Application from its matching detail row; detail or nationality mutations increment parties.version in the same transaction.';
COMMENT ON COLUMN public.parties.tenant_id IS
    'Opaque reference to the tenant that owns this Party representation.';
COMMENT ON COLUMN public.parties.display_name IS
    'Canonical denormalized Party display name derived by Domain/Application from the corresponding detail row and persisted atomically.';
COMMENT ON COLUMN public.parties.created_by IS
    'Opaque authenticated subject or service principal.';
COMMENT ON COLUMN public.parties.updated_by IS
    'Opaque authenticated subject or service principal.';
COMMENT ON COLUMN public.parties.version IS
    'Optimistic concurrency version of the Party aggregate.';

CREATE TABLE public.natural_person_details (
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
    CONSTRAINT ck_natural_person_life_dates
        CHECK (date_of_death IS NULL OR birth_date IS NULL OR date_of_death >= birth_date),
    CONSTRAINT ck_natural_person_birth_country_code
        CHECK (birth_country_code IS NULL OR birth_country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT fk_natural_person_details_party
        FOREIGN KEY (party_id) REFERENCES public.parties (id)
        ON UPDATE NO ACTION ON DELETE RESTRICT
);

COMMENT ON TABLE public.natural_person_details IS
    'One-to-one component of the Party aggregate for NATURAL_PERSON. Commit-time type/detail integrity is enforced by fn_enforce_party_detail_type and deferred constraint triggers.';
COMMENT ON COLUMN public.natural_person_details.birth_country_code IS
    'ISO 3166-1 alpha-2 code.';

CREATE TABLE public.legal_entity_details (
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
    CONSTRAINT ck_legal_entity_lifecycle_dates
        CHECK (dissolved_on IS NULL OR incorporated_on IS NULL OR dissolved_on >= incorporated_on),
    CONSTRAINT ck_legal_entity_incorporation_country_code
        CHECK (incorporation_country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT fk_legal_entity_details_party
        FOREIGN KEY (party_id) REFERENCES public.parties (id)
        ON UPDATE NO ACTION ON DELETE RESTRICT
);

COMMENT ON TABLE public.legal_entity_details IS
    'One-to-one component of the Party aggregate for LEGAL_ENTITY. It does not represent tenant configuration, tax configuration, ownership, corporate hierarchy, or commercial roles.';
COMMENT ON COLUMN public.legal_entity_details.legal_name IS
    'Official registered name.';
COMMENT ON COLUMN public.legal_entity_details.trade_name IS
    'Primary trade name for V1; it does not replace legal_name.';
COMMENT ON COLUMN public.legal_entity_details.legal_form_code IS
    'Jurisdiction-qualified code, for example EC_SAS, EC_SA or US_LLC.';
COMMENT ON COLUMN public.legal_entity_details.incorporation_country_code IS
    'ISO 3166-1 alpha-2 code.';

CREATE TABLE public.party_nationalities (
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
    CONSTRAINT ck_party_nationality_country_code
        CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_party_nationality_validity
        CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from),
    CONSTRAINT fk_party_nationalities_party
        FOREIGN KEY (party_id) REFERENCES public.parties (id)
        ON UPDATE NO ACTION ON DELETE RESTRICT
);

CREATE INDEX ix_party_nationalities_country
    ON public.party_nationalities (party_id, country_code);
CREATE INDEX ix_party_nationalities_primary
    ON public.party_nationalities (party_id, is_primary);
CREATE UNIQUE INDEX uq_party_nationalities_active_country
    ON public.party_nationalities (party_id, country_code)
    WHERE valid_until IS NULL;
CREATE UNIQUE INDEX uq_party_nationalities_active_primary
    ON public.party_nationalities (party_id)
    WHERE is_primary = true AND valid_until IS NULL;

COMMENT ON TABLE public.party_nationalities IS
    'Nationalities known and maintained by the tenant for a natural-person Party. Tenant ownership is inherited through the globally unique party_id; active rows have valid_until IS NULL.';
COMMENT ON COLUMN public.party_nationalities.country_code IS
    'ISO 3166-1 alpha-2 code.';

CREATE TABLE public.identifier_schemes (
    id uuid DEFAULT uuidv7() NOT NULL,
    code varchar(64) NOT NULL,
    issuing_country_code char(2) NOT NULL,
    category public.identifier_category NOT NULL,
    applicable_subject_type public.identifier_subject_type NOT NULL,
    name varchar(150) NOT NULL,
    description varchar(500),
    normalizer_key varchar(64) NOT NULL,
    validator_key varchar(64) NOT NULL,
    minimum_length smallint,
    maximum_length smallint,
    requires_expiration boolean DEFAULT false NOT NULL,
    status public.identifier_scheme_status DEFAULT 'DRAFT' NOT NULL,
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
    ON public.identifier_schemes
    (issuing_country_code, category, applicable_subject_type, status);

COMMENT ON TABLE public.identifier_schemes IS
    'Global database-managed Identifier Scheme reference catalog. Party Registry runtime reads this table but cannot insert, update, or delete rows; governed database credentials manage catalog changes.';
COMMENT ON COLUMN public.identifier_schemes.code IS
    'Global stable code, for example EC_CEDULA, EC_RUC_NATURAL or US_EIN.';
COMMENT ON COLUMN public.identifier_schemes.issuing_country_code IS
    'Logical ISO 3166-1 alpha-2 reference.';
COMMENT ON COLUMN public.identifier_schemes.normalizer_key IS
    'Versioned implementation key, for example DIGITS_ONLY_V1.';
COMMENT ON COLUMN public.identifier_schemes.validator_key IS
    'Versioned implementation key, for example ECUADOR_CEDULA_V1. It does not contain executable code.';

CREATE TABLE public.party_identifiers (
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
    status public.party_identifier_status DEFAULT 'PENDING_VERIFICATION' NOT NULL,
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
    CONSTRAINT uq_party_identifier_tenant_scheme_hash
        UNIQUE (tenant_id, identifier_scheme_id, normalized_value_hash),
    CONSTRAINT ck_party_identifier_validity_dates
        CHECK (expires_on IS NULL OR issued_on IS NULL OR expires_on >= issued_on),
    CONSTRAINT ck_party_identifier_verification
        CHECK ((status = 'VERIFIED' AND verified_at IS NOT NULL AND verified_by IS NOT NULL)
               OR status <> 'VERIFIED'),
    CONSTRAINT ck_party_identifier_expired_date
        CHECK ((status = 'EXPIRED' AND expires_on IS NOT NULL) OR status <> 'EXPIRED'),
    CONSTRAINT ck_party_identifier_hash_upper_hex
        CHECK (normalized_value_hash ~ '^[0-9A-F]{64}$'),
    CONSTRAINT ck_party_identifier_normalization_version CHECK (normalization_version > 0),
    CONSTRAINT ck_party_identifier_encryption_key_version CHECK (encryption_key_version > 0),
    CONSTRAINT ck_party_identifier_nonnegative_version CHECK (version >= 0),
    CONSTRAINT fk_party_identifiers_party
        FOREIGN KEY (tenant_id, party_id) REFERENCES public.parties (tenant_id, id)
        ON UPDATE NO ACTION ON DELETE RESTRICT,
    CONSTRAINT fk_party_identifiers_scheme
        FOREIGN KEY (identifier_scheme_id) REFERENCES public.identifier_schemes (id)
        ON UPDATE NO ACTION ON DELETE RESTRICT
);

CREATE INDEX ix_party_identifiers_party_scheme_status
    ON public.party_identifiers (tenant_id, party_id, identifier_scheme_id, status);
CREATE INDEX ix_party_identifiers_primary
    ON public.party_identifiers (tenant_id, party_id, is_primary);
CREATE UNIQUE INDEX uq_party_identifiers_verified_primary_scheme
    ON public.party_identifiers (tenant_id, party_id, identifier_scheme_id)
    WHERE is_primary = true AND status = 'VERIFIED';

COMMENT ON TABLE public.party_identifiers IS
    'Official Party identifiers. Complete values are authenticated-encrypted; exact lookup and permanent tenant-and-scheme uniqueness use the uppercase tenant-isolated HMAC-SHA-256 fingerprint.';
COMMENT ON COLUMN public.party_identifiers.issuer_code IS
    'Issuing authority, for example SRI or REGISTRO_CIVIL.';
COMMENT ON COLUMN public.party_identifiers.encrypted_value IS
    'Authenticated encryption; never plain text.';
COMMENT ON COLUMN public.party_identifiers.encryption_key_version IS
    'Version of the runtime-injected encryption key used for this ciphertext.';
COMMENT ON COLUMN public.party_identifiers.normalized_value_hash IS
    'Tenant-isolated HMAC-SHA-256 fingerprint encoded as exactly 64 uppercase hexadecimal characters; used for exact lookup and permanent uniqueness within the tenant and Identifier Scheme.';
COMMENT ON COLUMN public.party_identifiers.masked_value IS
    'Safe representation for UI and logs.';
COMMENT ON COLUMN public.party_identifiers.is_primary IS
    'Primary identifier within the same Party and Identifier Scheme.';
COMMENT ON COLUMN public.party_identifiers.version IS
    'Optimistic concurrency version of the Party Identifier aggregate.';

CREATE TABLE public.party_outbox_events (
    id uuid DEFAULT uuidv7() NOT NULL,
    tenant_id uuid NOT NULL,
    aggregate_type public.party_outbox_aggregate_type NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_version bigint NOT NULL,
    event_type varchar(128) NOT NULL,
    event_schema_version smallint DEFAULT 1 NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz DEFAULT now() NOT NULL,
    correlation_id varchar(128),
    causation_id varchar(128),
    status public.outbox_status DEFAULT 'PENDING' NOT NULL,
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
    CONSTRAINT ck_party_outbox_nonnegative_aggregate_version CHECK (aggregate_version >= 0),
    CONSTRAINT ck_party_outbox_positive_schema_version CHECK (event_schema_version > 0),
    CONSTRAINT ck_party_outbox_nonnegative_attempts CHECK (publish_attempts >= 0),
    CONSTRAINT ck_party_outbox_nonnegative_version CHECK (version >= 0),
    CONSTRAINT ck_party_outbox_published_at
        CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL)
               OR (status <> 'PUBLISHED' AND published_at IS NULL)),
    CONSTRAINT ck_party_outbox_failed_error
        CHECK ((status = 'FAILED' AND last_error_code IS NOT NULL) OR status <> 'FAILED')
);

CREATE INDEX ix_party_outbox_delivery
    ON public.party_outbox_events (status, next_attempt_at, created_at);
CREATE INDEX ix_party_outbox_aggregate
    ON public.party_outbox_events
    (tenant_id, aggregate_type, aggregate_id, aggregate_version);
CREATE INDEX ix_party_outbox_correlation
    ON public.party_outbox_events (correlation_id);

COMMENT ON TABLE public.party_outbox_events IS
    'Transactional Outbox for tenant-scoped Party and Party Identifier events. Business mutation and event insertion commit together; a separate publisher uses bounded SELECT FOR UPDATE SKIP LOCKED claims and at-least-once delivery.';
COMMENT ON COLUMN public.party_outbox_events.id IS
    'Integration event identifier.';
COMMENT ON COLUMN public.party_outbox_events.event_type IS
    'Versioned business contract, for example party.created.v1 or party.identifier-verified.v1.';
COMMENT ON COLUMN public.party_outbox_events.payload IS
    'Minimal integration payload without full identifiers or unnecessary personal data.';
COMMENT ON COLUMN public.party_outbox_events.created_by IS
    'Service principal that created the business event.';
COMMENT ON COLUMN public.party_outbox_events.updated_by IS
    'Publisher instance or service principal that last changed delivery state.';
COMMENT ON COLUMN public.party_outbox_events.version IS
    'Optimistic concurrency version for delivery-state transitions.';

CREATE FUNCTION public.fn_enforce_party_detail_type()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
DECLARE
    affected_party_id uuid;
    affected_party_ids uuid[];
    party_kind public.party_type;
    natural_detail_count integer;
    legal_detail_count integer;
BEGIN
    IF TG_TABLE_NAME = 'parties' THEN
        affected_party_ids := ARRAY[NEW.id];
    ELSIF TG_OP = 'DELETE' THEN
        affected_party_ids := ARRAY[OLD.party_id];
    ELSIF TG_OP = 'UPDATE' AND OLD.party_id IS DISTINCT FROM NEW.party_id THEN
        affected_party_ids := ARRAY[OLD.party_id, NEW.party_id];
    ELSE
        affected_party_ids := ARRAY[NEW.party_id];
    END IF;

    FOREACH affected_party_id IN ARRAY affected_party_ids
    LOOP
        SELECT p.type
          INTO party_kind
          FROM public.parties AS p
         WHERE p.id = affected_party_id;

        IF NOT FOUND THEN
            CONTINUE;
        END IF;

        SELECT count(*)
          INTO natural_detail_count
          FROM public.natural_person_details AS npd
         WHERE npd.party_id = affected_party_id;

        SELECT count(*)
          INTO legal_detail_count
          FROM public.legal_entity_details AS led
         WHERE led.party_id = affected_party_id;

        IF (party_kind = 'NATURAL_PERSON'
            AND (natural_detail_count <> 1 OR legal_detail_count <> 0))
           OR (party_kind = 'LEGAL_ENTITY'
               AND (natural_detail_count <> 0 OR legal_detail_count <> 1)) THEN
            RAISE EXCEPTION USING
                ERRCODE = 'integrity_constraint_violation',
                MESSAGE = 'party detail type invariant violated',
                DETAIL = format('party_id=%s', affected_party_id),
                CONSTRAINT = 'ct_parties_detail_type';
        END IF;
    END LOOP;

    RETURN NULL;
END;
$function$;

COMMENT ON FUNCTION public.fn_enforce_party_detail_type() IS
    'Deferred commit-time enforcement that every Party has exactly one detail row matching its immutable Party type.';

CREATE CONSTRAINT TRIGGER ct_parties_detail_type
AFTER INSERT OR UPDATE OF type ON public.parties
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION public.fn_enforce_party_detail_type();

CREATE CONSTRAINT TRIGGER ct_natural_person_details_party_type
AFTER INSERT OR UPDATE OF party_id OR DELETE ON public.natural_person_details
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION public.fn_enforce_party_detail_type();

CREATE CONSTRAINT TRIGGER ct_legal_entity_details_party_type
AFTER INSERT OR UPDATE OF party_id OR DELETE ON public.legal_entity_details
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION public.fn_enforce_party_detail_type();

-- PUBLIC receives no object access. PostgreSQL grants EXECUTE on new functions
-- to PUBLIC by default, so remove that implicit grant explicitly.
REVOKE ALL ON TABLE
    public.parties,
    public.natural_person_details,
    public.legal_entity_details,
    public.party_nationalities,
    public.identifier_schemes,
    public.party_identifiers,
    public.party_outbox_events
FROM PUBLIC;
REVOKE ALL ON FUNCTION public.fn_enforce_party_detail_type() FROM PUBLIC;

-- The runtime can perform only logical lifecycle changes; no table DELETE is
-- granted. Identifier Schemes are query-only for the runtime by Amendment 002.
GRANT SELECT, INSERT, UPDATE ON TABLE
    public.parties,
    public.natural_person_details,
    public.legal_entity_details,
    public.party_nationalities,
    public.party_identifiers,
    public.party_outbox_events
TO party_registry_runtime;
GRANT SELECT ON TABLE public.identifier_schemes TO party_registry_runtime;
