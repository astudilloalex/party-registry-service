# Party Registry V1 migration mapping

## Migration identity and classification

- Authoritative model: `docs/database/v1-scheme.dbml`.
- Inventory result: no existing `src/main/resources/db/migration/**` files existed when T010 selected the initial immutable version.
- Migration: `V1__create_party_registry_schema.sql`.
- Classification: `ADDITIVE`.
- PostgreSQL baseline: 18. `uuidv7()` is a PostgreSQL 18 core function; the migration adds no extension or compatibility shim.
- Flyway execution: one transactional versioned migration. It contains no concurrent index operation, data seed, backfill, destructive statement, callback, repeatable object, or down migration.

The Flyway connection role is the object owner and migration/administration identity. The separately provisioned `party_registry_runtime` group role is the application privilege identity. The migration intentionally does not create or alter login roles because role provisioning and credentials are infrastructure responsibilities.

## Object-by-object DBML mapping

| DBML object or normative note | Migration object/evidence |
|---|---|
| Enum `party_type` | `public.party_type`, exact two labels and order |
| Enum `party_record_status` | `public.party_record_status`, exact four labels and order |
| Enum `identifier_category` | `public.identifier_category`, exact six labels and order |
| Enum `identifier_subject_type` | `public.identifier_subject_type`, exact three labels and order |
| Enum `identifier_scheme_status` | `public.identifier_scheme_status`, exact four labels and order |
| Enum `party_identifier_status` | `public.party_identifier_status`, exact five labels and order |
| Enum `party_outbox_aggregate_type` | `public.party_outbox_aggregate_type`, exact two labels and order |
| Enum `outbox_status` | `public.outbox_status`, exact three labels and order |
| Table `parties` | All 10 columns, types, dimensions, nullability and defaults; `pk_parties`; named `uq_parties_tenant_id`, `ix_parties_tenant_type_status`, `ix_parties_tenant_display_name`, and `ck_parties_nonnegative_version`; table and noted-column comments |
| Table `natural_person_details` | All 11 columns, types, dimensions, nullability and defaults; `pk_natural_person_details`; both named checks; `fk_natural_person_details_party` with `ON DELETE RESTRICT`; table and country-code comments |
| Table `legal_entity_details` | All 12 columns, types, dimensions, nullability and defaults; `pk_legal_entity_details`; both named checks; `fk_legal_entity_details_party` with `ON DELETE RESTRICT`; table and noted-column comments |
| Table `party_nationalities` | All 12 columns, types, nullability and defaults; `pk_party_nationalities`; both named checks; `fk_party_nationalities_party` with `ON DELETE RESTRICT`; both ordinary indexes; exact partial unique indexes `uq_party_nationalities_active_country` and `uq_party_nationalities_active_primary`; table and country-code comments |
| Table `identifier_schemes` | All 19 columns, exact dimensions, nullability and defaults; `pk_identifier_schemes`; explicit `uq_identifier_schemes_code`; all four named checks; named country/category index; table and noted-column comments |
| Table `party_identifiers` | All 23 columns, exact types/dimensions/nullability/defaults; `pk_party_identifiers`; unconditional named `uq_party_identifier_tenant_scheme_hash`; all six named checks; composite tenant-qualified `fk_party_identifiers_party` and `fk_party_identifiers_scheme`, both `ON DELETE RESTRICT`; both ordinary indexes; exact partial unique `uq_party_identifiers_verified_primary_scheme`; table and noted-column comments |
| Table `party_outbox_events` | All 25 columns, exact types/dimensions/nullability/defaults; `pk_party_outbox_events`; all six named checks; all three named indexes; table and noted-column comments; no foreign key invented for polymorphic aggregate identity |
| Five DBML references | Implemented by the five explicitly named foreign keys above; unspecified update action is explicit `NO ACTION`; every approved delete action is `RESTRICT`; no cross-service or country-code foreign key exists |
| UUIDv7 defaults | All five DBML-generated IDs call PostgreSQL 18 core `uuidv7()` exactly |
| Deferred Party/detail invariant | One `public.fn_enforce_party_detail_type()` function and exact constraint-trigger names `ct_parties_detail_type`, `ct_natural_person_details_party_type`, and `ct_legal_entity_details_party_type`; each is `AFTER`, row-level, `DEFERRABLE INITIALLY DEFERRED`, and covers the exact events stated by the DBML |
| Party display-name derivation and component version increments | Persisted columns support the rules; DBML assigns calculation and atomic mutation behavior to Domain/Application, so no unapproved generated expression or trigger is added |
| Natural-person-only nationality and clock-relative dates | DBML/approved decision assigns contextual and current-clock rules to Domain/Application; SQL enforces only the approved row checks, FK, and exact partial uniqueness mechanisms |
| Party type/Scheme compatibility, masking and lifecycle transitions | Domain/Application-owned contextual rules; migration creates the exact storage enums/columns/checks and does not invent database state-machine triggers |
| Identifier protection and permanent uniqueness | Ciphertext, key version, canonical uppercase HMAC field/check, mask and normalization version are exact; unconditional unique constraint applies in every lifecycle status; no plaintext or idempotency store exists |
| Transactional outbox | Exact storage, checks and polling/aggregate indexes support atomic mutation/event insertion and `SELECT ... FOR UPDATE SKIP LOCKED`; no Scheme-change event mechanism, purge, DLQ or invented lease field exists |
| Database-managed Scheme ownership | Runtime receives `SELECT` only on `identifier_schemes`; no Scheme seed rows are present |
| Least privilege and role separation | PUBLIC table access and implicit function execution are revoked; runtime receives `SELECT/INSERT/UPDATE` but no physical `DELETE` on mutable runtime tables, `SELECT` only on Schemes, and no direct trigger-function execution; the executing Flyway role remains owner and is distinct from runtime |
| Project/bounded-context notes | No remote/cross-service FK, audit table, idempotency table, Scheme seed, generic audit addition, soft-delete addition, extension, RLS policy, partition, or unapproved object is introduced |

## Validation and representative integrity plan

T011 must execute these cases with the exact pinned PostgreSQL 18 Testcontainers image and repository-controlled Flyway 12 channel. An in-memory database is not acceptable.

### Clean database and Flyway history

1. Provision isolated migration and `party_registry_runtime` roles without login credentials in fixtures.
2. Apply the complete Flyway chain to an empty database and assert one successful `V1` history row and valid checksum/order.
3. Run Flyway a second time and assert no versioned migration executes.
4. Query `pg_type`, `pg_enum`, `pg_class`, `pg_attribute`, `pg_attrdef`, `pg_constraint`, `pg_index`, `pg_proc`, `pg_trigger`, `information_schema.role_table_grants`, and comments to compare every mapping row above rather than searching migration text.
5. Assert no runtime auto-DDL/ORM schema generation is configured; Flyway remains the sole schema-evolution channel.

### Supported prior schema and data preservation

There is no prior supported Party Registry schema or earlier Flyway migration in the current inventory. The supported-prior-schema case is therefore an empty database at V0. Upgrade evidence must migrate V0 to V1 once, preserve the precondition that there are zero application rows, and prove a second run is a no-op. Future upgrades must start from V1 with representative rows and may not edit this migration.

### Representative integrity cases

- Commit valid natural/legal Parties with detail-before-Party and Party-before-detail ordering; reject missing, wrong, dual, moved, deleted, or type-updated detail states at deferred-constraint evaluation.
- Reject invalid life dates and country-code syntax.
- Reject duplicate active nationality country and active primary rows; retain ended history and permit a replacement active row after ending the prior row.
- Reject cross-tenant Party Identifier references through the composite FK.
- Reject lowercase, mixed-case, non-hex, prefixed or non-64-character hashes.
- Reject duplicate tenant+Scheme+hash values in every identifier status; permit the same hash in another tenant or Scheme; verify concurrent duplicates allow at most one commit.
- Reject a second verified primary identifier for one tenant+Party+Scheme while permitting non-primary and non-VERIFIED history.
- Exercise every positive/nonnegative, date, verification, expiration, published-state and failed-state check.
- Verify runtime reads Schemes and is denied Scheme `INSERT`, `UPDATE`, `DELETE`; verify runtime has no physical `DELETE` on any table and no direct function execution.
- Verify migration-owner catalog changes create no outbox event automatically.

### Failure, atomicity and recovery

- Execute V1 transactionally in an isolated database with a controlled precondition failure (missing runtime role) and prove no V1 schema object or successful Flyway history row remains. Recreate the required role and rerun forward successfully; do not use `clean`, `repair`, `baseline`, checksum edits, or reverse SQL.
- A production failure stops application rollout. Recovery is correction of the external role precondition followed by the same immutable migration. Once V1 commits, defects are repaired only by a later forward migration.

## Lock, duration and operational analysis

- Risk classification: `LOW` for an initial additive empty schema; operational rollout is `MEDIUM` because role preconditions and application ordering must be coordinated.
- V1 creates new types, empty tables, constraints and indexes only. It scans/backfills no existing application data, rewrites no existing application table, generates no WAL from row transformation, and requires no maintenance window for an existing Party Registry schema.
- DDL takes catalog locks for the transaction and exclusive locks only on newly created objects. It does not use `CREATE INDEX CONCURRENTLY` because every index is built on a new empty table inside the atomic migration.
- Pre-deployment must validate PostgreSQL major version 18, Flyway checksums/order, empty/V0 starting state, existence and separation of `party_registry_runtime` from the migration login, backup/restore readiness, and compatible application deployment order.
- Completion signals are successful Flyway history for V1, zero invalid indexes/constraints, complete schema-fidelity query results, runtime Scheme write denials, and application readiness. Stop on any unexpected pre-existing object, checksum mismatch, role mismatch, partial state, or integrity failure.
- Database reversal is not an approved rollback. Before application data is accepted, correct failures and roll forward. After data exists, retain V1 and repair through a new immutable migration; application rollback/re-promotion must remain schema-compatible and operations-owned.
