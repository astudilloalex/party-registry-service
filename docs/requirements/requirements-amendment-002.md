# PRS-REQ-001 — Amendment 002

## 1. Status and authority

| Field | Value |
|---|---|
| Applies to | `PRS-REQ-001` version 0.4 plus Amendment 001 |
| Amendment | 002 |
| Status | Human-authority clarification pending factoryctl decision recording |
| Issue | `#6` — Make Identifier Schemes database-managed and read-only to the service |
| Date | 2026-08-10 |
| Persistence authority | `docs/database/v1-scheme.dbml` |

This amendment preserves the already-gated bytes of `docs/requirements/requirements-specification.md` and the prior Amendment 001. It supersedes only the clauses that treated Identifier Schemes as runtime-administered Party Registry resources.

## 2. Human decision

### HD-004 — Identifier Schemes are database-managed reference data

Identifier Schemes such as CÉDULA, RUC, PASAPORTE and other supported official-document types are a global reference catalog stored in PostgreSQL.

Party Registry V1 runtime MUST treat `identifier_schemes` as **read-only reference data**.

The runtime application MUST NOT expose or implement operations to:

- create an Identifier Scheme;
- update an Identifier Scheme;
- activate an Identifier Scheme;
- deprecate an Identifier Scheme;
- retire an Identifier Scheme;
- delete an Identifier Scheme;
- otherwise mutate `identifier_schemes` through REST, application use cases, messaging, or background jobs.

There is no `Identifier Scheme Administrator Consumer` actor in Party Registry V1 and no Scheme Administration application capability.

New or changed scheme rows are introduced only through the governed database-management path: reviewed database migrations and/or explicitly authorized database administration. Such changes are outside the Party Registry REST API and outside the tenant Party outbox/event catalog.

The Party Registry runtime MAY read scheme metadata internally when required to:

- identify the document type/scheme selected for a Party Identifier;
- obtain normalizer and validator implementation keys;
- verify subject-type compatibility;
- apply configured length/expiration metadata;
- calculate the permanent tenant+scheme identifier uniqueness scope;
- perform exact identifier lookup;
- validate readiness against implementation keys supported by the deployed application version.

Unless separately authorized by a later requirement, V1 does not expose a public/internal REST endpoint whose purpose is to administer or browse the Identifier Scheme catalog.

## 3. Source decisions superseded

This clarification supersedes only the following older meanings:

- the portion of OD-001 that included Identifier Schemes in runtime CRUD;
- the portion of RG-101/BR-022 that treated Identifier Scheme status transitions as application-executable lifecycle operations;
- the portion of BR-023 that described Identifier Scheme mutability as an application update concern;
- ACT-002 / `Identifier-scheme administrator consumer` as a V1 actor;
- FR-019 and FR-040 through FR-045 as runtime REST/application operations;
- Scheme-administration portions of FR-048 and related acceptance criteria.

The `identifier_schemes` table, its columns, status enum, audit fields and version field remain part of the authoritative DBML. Their presence does not imply an application write API.

## 4. Effective requirement replacements

### BR-006 replacement — Database-managed catalog

`identifier_schemes` is a database-managed global reference catalog. Party Registry runtime MUST read it but MUST NOT mutate it. Catalog creation or change is allowed only through the governed database-management path. Runtime database credentials SHOULD be least-privileged so application execution cannot `INSERT`, `UPDATE`, or `DELETE` rows in `identifier_schemes`.

### BR-022 replacement impact — Runtime lifecycle

The Party and Party Identifier transition matrices remain unchanged. Identifier Scheme status values remain persisted catalog metadata, but Party Registry V1 exposes no runtime operation that transitions them. Any database-level change of scheme status is governed outside the Party Registry application API.

### BR-023 replacement impact — Scheme mutability

Identifier Scheme mutability is not an application concern in V1. Party Registry runtime treats all scheme fields as read-only. Database-governed changes must preserve the DBML constraints and compatibility with deployed normalizer/validator implementations.

### BR-027 replacement — Runtime rule compatibility

Party Registry owns the versioned application-code catalog of supported normalizer and validator implementations. The database-managed Identifier Scheme catalog stores only implementation keys, never executable code. During identifier processing and readiness checks, the runtime MUST reject/fail closed when required persisted scheme configuration references an unsupported or unavailable implementation. Adding a new implementation still requires a software release; adding or changing a scheme row requires the governed database-management path rather than a runtime Scheme Administration use case.

### FR-019 replacement — No Scheme creation use case

Party Registry V1 MUST NOT expose a runtime Identifier Scheme creation operation. Scheme records are provisioned through governed database management.

### FR-040 through FR-045 replacement — No Scheme API surface

Party Registry V1 MUST NOT expose Identifier Scheme lookup, search, update, status-transition, retire, delete, or administration operations as application use cases or REST resources. Runtime scheme access is internal read-only reference access used by Party Identifier processing.

### FR-048 replacement impact — Rule-catalog validation

WHEN Party Identifier processing references a database-managed Identifier Scheme, THE SYSTEM MUST verify that its normalizer/validator keys are supported by the running software before accepting the identifier. No Scheme create/update/activation API is implied.

### FR-049 unchanged — Readiness

Persisted scheme configuration that requires an unavailable implementation continues to make readiness unavailable and dependent identifier operations fail without business-state mutation.

### IR-004 replacement impact

Database-managed Identifier Scheme catalog changes are not tenant Party integration events and MUST NOT create Party Registry outbox events.

## 5. Acceptance criteria replacements

### AC-006 replacement

GIVEN the Party Registry V1 API and application use-case surface, WHEN operations are enumerated, THEN no Identifier Scheme create/update/status/delete operation exists and runtime code has no business use case capable of mutating the catalog.

### AC-051 replacement

GIVEN a new Identifier Scheme such as a supported document type is required, WHEN it is introduced, THEN the change occurs through a reviewed database-management artifact/process rather than a Party Registry REST command.

### AC-052 through AC-056 replacement

GIVEN Party Registry runtime is operating, WHEN Identifier Scheme access occurs, THEN it is internal read-only access required by Party Identifier processing. No runtime lookup/search administration resource, update, transition, retire, or delete operation is exposed.

### AC-057 replacement

GIVEN any database-managed Identifier Scheme catalog change, WHEN it is applied, THEN no tenant Party outbox event is generated for that catalog change.

### AC-077 replacement

GIVEN a database-managed scheme references an implementation key unsupported by the running Party Registry version, WHEN identifier processing or readiness validation occurs, THEN the dependent operation fails without changing Party/Identifier/outbox state and readiness reflects the unusable required configuration where applicable.

### AC-078 replacement

GIVEN a database migration/data-administration change introduces or changes an Identifier Scheme, WHEN the change is validated for release, THEN its implementation keys must exist in the deployed/released application catalog before that scheme is usable by Party Identifier processing.

## 6. Database and privilege consequence

The DBML continues to contain `identifier_schemes`; no table/column deletion is authorized by this clarification.

The application runtime access model is:

```text
identifier_schemes
    runtime SELECT: allowed
    runtime INSERT: forbidden
    runtime UPDATE: forbidden
    runtime DELETE: forbidden
```

Database migration/administration credentials are a separate governed capability and may perform explicitly approved catalog changes.

The database-contract and migration phases must validate that the chosen PostgreSQL role/privilege model can enforce this separation without granting the application runtime unnecessary scheme-write privileges.

## 7. Architecture consequence

The LikeC4 model MUST NOT contain:

- `Identifier Scheme Administrator Consumer`;
- `Scheme Administration Capability`;
- relationships representing runtime scheme administration.

The Identifier Application Capability reads database-managed scheme metadata through a read-only outbound catalog/persistence port or equivalent strict Clean Architecture boundary. The PostgreSQL adapter may implement that port but must not expose scheme mutation methods to application use cases.

## 8. Unchanged decisions

This amendment does not change:

- permanent identifier uniqueness from Amendment 001;
- no command-level idempotency from Amendment 001;
- no external logging platform from Amendment 001;
- no-login/no-authentication/no-authorization V1 posture;
- tenant scoping;
- encryption/HMAC and runtime secret injection;
- Party and Party Identifier lifecycles;
- masking;
- Geographic Reference behavior;
- transactional outbox/RabbitMQ semantics;
- no automatic purge;
- strict Clean Architecture and reactive execution.
