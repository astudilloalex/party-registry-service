# Party Registry V1 migration mapping

## Migration identity and classification

- Authoritative model: `docs/database/v1-scheme.dbml`.
- Initial inventory result: no migration existed when T010 selected `V1`. The current inventory contains the committed, immutable `V1__create_party_registry_schema.sql`; review found that its immediate detail foreign keys did not permit the DBML-required detail-before-Party insertion order.
- Migrations: `V1__create_party_registry_schema.sql` creates the schema; the next valid version, `V2__defer_party_detail_foreign_keys.sql`, corrects the two foreign-key timing attributes without editing `V1`.
- Classification: `ADDITIVE` (`V1`) and `COMPATIBILITY_PHASE` (`V2`).
- PostgreSQL baseline: 18. `uuidv7()` is a PostgreSQL 18 core function; the migration adds no extension or compatibility shim.
- Flyway execution: two ordered transactional versioned migrations. Neither contains a concurrent index operation, data seed, backfill, destructive statement, callback, repeatable object, or down migration.

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
| Table `natural_person_details` | All 11 columns, types, dimensions, nullability and defaults; `pk_natural_person_details`; both named checks; `fk_natural_person_details_party` with `ON DELETE RESTRICT`, made `DEFERRABLE INITIALLY DEFERRED` by `V2`; table and country-code comments |
| Table `legal_entity_details` | All 11 columns, types, dimensions, nullability and defaults; `pk_legal_entity_details`; both named checks; `fk_legal_entity_details_party` with `ON DELETE RESTRICT`, made `DEFERRABLE INITIALLY DEFERRED` by `V2`; table and noted-column comments |
| Table `party_nationalities` | All 10 columns, types, nullability and defaults; `pk_party_nationalities`; both named checks; `fk_party_nationalities_party` with `ON DELETE RESTRICT`; both ordinary indexes; exact partial unique indexes `uq_party_nationalities_active_country` and `uq_party_nationalities_active_primary`; table and country-code comments |
| Table `identifier_schemes` | All 18 columns, exact dimensions, nullability and defaults; `pk_identifier_schemes`; explicit `uq_identifier_schemes_code`; all four named checks; named country/category index; table and noted-column comments |
| Table `party_identifiers` | All 21 columns, exact types/dimensions/nullability/defaults; `pk_party_identifiers`; unconditional named `uq_party_identifier_tenant_scheme_hash`; all six named checks; composite tenant-qualified `fk_party_identifiers_party` and `fk_party_identifiers_scheme`, both `ON DELETE RESTRICT`; both ordinary indexes; exact partial unique `uq_party_identifiers_verified_primary_scheme`; table and noted-column comments |
| Table `party_outbox_events` | All 23 columns, exact types/dimensions/nullability/defaults; `pk_party_outbox_events`; all six named checks; all three named indexes; table and noted-column comments; no foreign key invented for polymorphic aggregate identity |
| Five DBML references | Implemented by the five explicitly named foreign keys above; unspecified update action is explicit `NO ACTION`; every approved delete action is `RESTRICT`; no cross-service or country-code foreign key exists |
| UUIDv7 defaults | All five DBML-generated IDs call PostgreSQL 18 core `uuidv7()` exactly |
| Deferred Party/detail invariant | One `public.fn_enforce_party_detail_type()` function and exact constraint-trigger names `ct_parties_detail_type`, `ct_natural_person_details_party_type`, and `ct_legal_entity_details_party_type`; each is `AFTER`, row-level, `DEFERRABLE INITIALLY DEFERRED`, and covers the exact events stated by the DBML. `V2` also defers both detail-to-Party foreign keys so either approved insertion ordering can reach commit-time invariant evaluation. |
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
2. Apply the complete Flyway chain to an empty database and assert successful `V1` and `V2` history rows with valid checksums/order.
3. Run Flyway a second time and assert no versioned migration executes.
4. Query `pg_type`, `pg_enum`, `pg_class`, `pg_attribute`, `pg_attrdef`, `pg_constraint`, `pg_index`, `pg_proc`, `pg_trigger`, `information_schema.role_table_grants`, and comments to compare every mapping row above rather than searching migration text.
5. Assert no runtime auto-DDL/ORM schema generation is configured; Flyway remains the sole schema-evolution channel.

### Supported prior schema and data preservation

The initial supported-prior-schema case is an empty database at V0. Clean-chain evidence must migrate V0 through V1 and V2 once and prove a second run is a no-op. Because committed V1 is now an immutable supported intermediate schema, upgrade evidence must also create a V1-only database, insert representative valid Party/detail data in the ordering V1 permits, apply V2 exactly once, prove rows are unchanged, prove both detail foreign keys are deferred in `pg_constraint`, and prove a second run is a no-op. Future corrections may not edit either migration.

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

- Execute V1 transactionally in an isolated database with a controlled precondition failure (missing runtime role) and prove no V1 schema object or successful Flyway history row remains. Recreate the required role and rerun the complete chain successfully. Separately force a V2 execution failure in a disposable V1-only database and prove its two `ALTER CONSTRAINT` statements roll back atomically; do not use `clean`, `repair`, `baseline`, checksum edits, or reverse SQL.
- A production failure stops application rollout. Recovery is correction of the external precondition or migration defect followed by forward execution. V1 is not edited; V2 is the immutable roll-forward correction. Any later defect requires the next immutable migration.

## Lock, duration and operational analysis

- Risk classification: `LOW` for V1's initial additive empty schema and `MEDIUM` for V2 because altering foreign-key timing acquires table locks on an existing V1 schema; operational rollout is therefore `MEDIUM`.
- V1 creates new types, empty tables, constraints and indexes only. V2 changes only foreign-key catalog attributes: it scans or backfills no rows, does not recreate or revalidate the foreign keys, rewrites no table, and generates no row-transformation WAL.
- V1 takes catalog locks for the transaction and exclusive locks only on newly created objects. V2 requires `ACCESS EXCLUSIVE` locks on `natural_person_details` and `legal_entity_details` for two short metadata-only statements; apply before enabling application traffic or in an approved bounded window if a V1 environment already accepts traffic. No index uses `CONCURRENTLY` because V1 builds each index on a new empty table.
- Pre-deployment must validate PostgreSQL major version 18, immutable V1 checksum and Flyway order, the actual starting version (V0 or V1), existence and separation of `party_registry_runtime` from the migration login, backup/restore readiness, absence of long transactions that could delay V2 locks, and compatible application deployment order.
- Completion signals are successful Flyway history through V2, both detail foreign keys reporting `condeferrable=true` and `condeferred=true`, zero invalid indexes/constraints, complete schema-fidelity query results, runtime Scheme write denials, and application readiness. Stop on any unexpected pre-existing object, checksum mismatch, role mismatch, lock timeout, partial state, or integrity failure.
- Database reversal is not an approved rollback. Before application data is accepted, correct failures and roll forward. After data exists, retain V1/V2 and repair through a new immutable migration; application rollback/re-promotion must remain schema-compatible and operations-owned.
