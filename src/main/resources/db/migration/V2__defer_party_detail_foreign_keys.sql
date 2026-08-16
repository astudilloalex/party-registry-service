-- T010 / docs/database/v1-scheme.dbml: permit either approved Party/detail
-- insertion order while preserving ON DELETE RESTRICT.
-- V1 is committed Flyway history and remains immutable; this is a forward-only
-- metadata correction with no data rewrite or backfill.

ALTER TABLE public.natural_person_details
    ALTER CONSTRAINT fk_natural_person_details_party
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.legal_entity_details
    ALTER CONSTRAINT fk_legal_entity_details_party
    DEFERRABLE INITIALLY DEFERRED;
