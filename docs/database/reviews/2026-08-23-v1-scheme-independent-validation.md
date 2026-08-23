# Independent Database-Contract Validation: Party Registry V1

- **Result**: PASS
- **Validation date**: 2026-08-23
- **Reviewer**: Codex by OpenAI
- **Reviewer relationship**: Independent of the DBML author
- **Repository owner authorization**: Alex Astudillo authorized repository and GitHub actions;
  the technical review is not represented as self-review by Alex Astudillo
- **Pull Request**: [PR #13](https://github.com/astudilloalex/party-registry-service/pull/13)
- **Approval comment**:
  [independent PASS evidence](https://github.com/astudilloalex/party-registry-service/pull/13#issuecomment-5387032860)

## Reviewed Artifact

- **Authoritative path**: [`docs/database/v1-scheme.dbml`](../v1-scheme.dbml)
- **Reviewed commit**: `a167e5bea93eeccbd4513c1fb91a6d9f08e2412d`
- **Git blob**: `a3aef3327bc12c70478fc2e47c6c98250164f237`
- **SHA-256**: `c58502586ba00538cc1e135bf3188c62f281148085ed3ae83ae30e51848ae1be`
- **Generated PostgreSQL SQL SHA-256**:
  `147218fac79df607d5934d91a82265978db9bc4f9dde2bba451be4b14ea8e043`

This PASS applies only to the artifact identity above. A material DBML change requires a new
independent validation result.

## Scope

The review validates the four database-contract decisions required by the Party Registration
pre-implementation gate:

1. Temporal exclusion of overlapping validity intervals for the same Party and country.
2. Temporal exclusion of overlapping primary-nationality intervals for the same Party across
   countries.
3. Unique outbox event identity and the closed `party.created.v1` aggregate/version/schema/payload
   shape.
4. Initial outbox audit semantics derived from trusted `User-Id`.

The review also verifies that the DBML compiles to valid PostgreSQL built-in types and that foreign
keys point from aggregate-owned detail records to the Party root.

## Authoritative Inputs

- [Party Registry Service Constitution](../../../.specify/memory/constitution.md), version 2.1.0
- [Party Registration specification](../../../specs/001-party-registration/spec.md)
- [Party Registration research decisions](../../../specs/001-party-registration/research.md)
- [Party Registration data model](../../../specs/001-party-registration/data-model.md)
- [Party Registration traceability](../../../specs/001-party-registration/traceability.md)
- [Party-created V1 JSON Schema](../../../specs/001-party-registration/contracts/party-created-v1.schema.json)

## Validation Environment

| Component | Version |
|-----------|---------|
| DBML CLI (`@dbml/cli`) | 10.1.1 |
| PostgreSQL | 18.4 (`Debian 18.4-1.pgdg13+1`) |
| `btree_gist` | 1.8 |
| PostgreSQL container image | `docker.io/library/postgres:18.4` |
| DBML compiler container image | `docker.io/library/node:24.18.0-alpine` |

All database tests ran in an isolated, temporary PostgreSQL container. The container was removed
after evidence capture. No Flyway migration or production database was used.

## Material Findings and Resolution

### DBML-VAL-001: PostgreSQL built-in types compiled as custom quoted types

- **Severity**: Material
- **Original artifact**: Git blob `9a3ba3db9ce54ae23b996177f2b06d0ee2d981ff`
- **Finding**: Uppercase parameterized and timestamp declarations such as `VARCHAR(300)`,
  `CHAR(2)`, and `TIMESTAMPTZ` compiled as quoted names such as `"VARCHAR(300)"` and
  `"TIMESTAMPTZ"`. PostgreSQL would interpret those names as custom types, so the generated DDL
  was not executable against the intended native types.
- **Resolution**: Every PostgreSQL built-in type was normalized to its canonical lowercase DBML
  form, including `uuid`, `varchar`, `char`, `timestamptz`, `date`, `boolean`, `smallint`,
  `integer`, `bigint`, `text`, and `jsonb`.
- **Verification**: DBML CLI 10.1.1 generated native PostgreSQL type declarations without quoted
  custom type names, and PostgreSQL 18.4 executed the generated schema successfully.
- **Status**: RESOLVED in reviewed commit `a167e5bea93eeccbd4513c1fb91a6d9f08e2412d`.

### DBML-VAL-002: One-to-one detail foreign keys pointed in the wrong direction

- **Severity**: Material
- **Original artifact**: Git blob `9a3ba3db9ce54ae23b996177f2b06d0ee2d981ff`
- **Finding**: The original one-to-one `Ref` order caused the compiler to generate foreign keys
  from `parties.id` to both detail tables. That representation would require a Party root to
  reference detail rows and would contradict aggregate ownership and insertion order.
- **Resolution**: The references now declare `parties.id` first and each detail `party_id` second.
- **Verification**: The compiled DDL creates `natural_person_details.party_id -> parties.id` and
  `legal_entity_details.party_id -> parties.id`.
- **Status**: RESOLVED in reviewed commit `a167e5bea93eeccbd4513c1fb91a6d9f08e2412d`.

No material finding remains open.

## Executed Validation Matrix

### DBML Compilation and Base Schema

| Scenario | Expected | Result |
|----------|----------|--------|
| Parse complete DBML with DBML CLI 10.1.1 | Successful PostgreSQL generation | PASS |
| Execute generated schema on PostgreSQL 18.4 | All enum, table, check, index, comment, and FK statements succeed | PASS |
| Inspect compiled detail FKs | Both detail tables reference `parties.id` | PASS |
| Inspect compiled native types | No quoted `VARCHAR`, `CHAR`, or `TIMESTAMPTZ` custom types | PASS |
| Execute PostgreSQL `uuidv7()` defaults | PostgreSQL 18 accepts generated defaults | PASS |

### Temporal Nationality Integrity

The two DBML-prescribed constraints were installed exactly as immediate, `NOT DEFERRABLE`
PostgreSQL exclusion constraints using `btree_gist` and closed `daterange(..., '[]')` values.

| Scenario | Expected | Result |
|----------|----------|--------|
| Same Party/country, adjacent non-overlapping finite intervals | Accept | PASS |
| Same Party/country, equal-boundary overlap | Reject with `ex_party_nationalities_country_validity` | PASS |
| Same Party/country, non-overlapping open intervals | Accept | PASS |
| Same Party/country, overlapping open intervals | Reject with `ex_party_nationalities_country_validity` | PASS |
| Same Party, non-overlapping primary intervals across countries | Accept | PASS |
| Same Party, overlapping primary intervals across countries | Reject with `ex_party_nationalities_primary_validity` | PASS |
| Different Parties with identical primary intervals | Accept | PASS |
| Concurrent overlapping inserts for one Party/country | One commits; conflict is rejected by country exclusion | PASS |
| Concurrent overlapping primary inserts across countries | One commits; conflict is rejected by primary exclusion | PASS |

Catalog inspection confirmed both exclusions have `condeferrable = false` and
`condeferred = false`.

### Outbox Identity and Creation-Event Shape

| Scenario | Expected | Result |
|----------|----------|--------|
| Valid natural-person `party.created.v1` | Accept | PASS |
| Valid legal-entity `party.created.v1` | Accept | PASS |
| Duplicate tenant/aggregate/version/event identity | Reject with unique violation | PASS |
| Same aggregate identity under a different tenant | Accept | PASS |
| Payload with an additional prohibited field | Reject with check violation | PASS |
| `PARTY_IDENTIFIER` aggregate for `party.created.v1` | Reject with check violation | PASS |
| Aggregate version other than `0` | Reject with check violation | PASS |
| Event schema version other than `1` | Reject with check violation | PASS |

Catalog inspection confirmed `uq_party_outbox_event_identity` covers tenant, aggregate type,
aggregate ID, aggregate version, and event type, and `ck_party_outbox_created_event_shape` enforces
the closed creation-event contract.

### Trusted `User-Id` Audit Semantics

The DBML column and table notes explicitly define `created_by` and the initial `updated_by` as the
trusted command `User-Id`. A valid initial event using the same trusted audit value in both columns
was executed successfully and retained `PENDING` status and delivery version `0`.

Equality between the two initial audit values is intentionally an adapter mapping responsibility,
not a permanent database check, because later publisher transitions may change `updated_by`. The
future persistence integration tests must verify that initial mapping.

## Result

**PASS** — the reviewed DBML revision is internally consistent, compiles to executable PostgreSQL
18 DDL, and correctly records the required nationality exclusions, outbox identity, closed creation
payload, and trusted `User-Id` audit semantics. Both material findings discovered during review were
resolved and revalidated. No material finding remains open.

## Scope Boundary

This result approves the DBML contract for subsequent migration design. It does not approve a
Flyway migration that does not yet exist. The future Flyway migration must independently prove
exact DBML correspondence, deferred detail-shape trigger behavior, clean-database application,
Hibernate schema validation, rollback, and the same concurrency cases before implementation
acceptance.
