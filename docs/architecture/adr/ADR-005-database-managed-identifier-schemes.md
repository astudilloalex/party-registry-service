# ADR-005: Database-managed Identifier Scheme catalog

## Status

Proposed

## Context

Party Registry stores global Identifier Scheme records such as CÉDULA, RUC, PASAPORTE and other supported official-document types in `identifier_schemes`. Earlier generated requirements and architecture treated these records as runtime-administered resources with a dedicated actor and application capability.

The project owner clarified that this is incorrect for V1: Identifier Schemes are database-level reference data. Party Registry needs the catalog to normalize, validate and classify Party Identifiers, but the application must not create or modify Scheme rows through REST or application use cases.

## Decision Drivers

- Keep stable document-type definitions out of runtime business administration.
- Prevent an internal caller from changing validation semantics through the Party Registry API.
- Preserve one authoritative PostgreSQL catalog for scheme metadata.
- Keep the runtime database role least-privileged.
- Preserve strict Clean Architecture: identifier use cases depend on a read-only catalog port, not on database tables directly.
- Keep catalog changes governed by database review/migration controls and outside tenant business events.

## Decision

Identifier Schemes are **database-managed reference data**.

Party Registry V1 runtime:

- MAY read `identifier_schemes`;
- MUST NOT insert Scheme rows;
- MUST NOT update Scheme rows;
- MUST NOT delete Scheme rows;
- MUST NOT expose Scheme create/update/status/retire/delete use cases or REST resources;
- MUST NOT include an Identifier Scheme Administrator actor or Scheme Administration capability;
- MUST NOT publish database-managed Scheme changes through the tenant Party outbox.

New or changed Scheme data is introduced only through the governed database-management path: reviewed database migration and/or explicitly authorized database administration.

The application runtime database role should enforce the boundary with `SELECT` access to `identifier_schemes` and no `INSERT`, `UPDATE`, or `DELETE` privilege on that table. Migration/administration credentials are separate and are not application runtime credentials.

## Application Boundary

The Identifier Application Capability requires a read-only outbound contract conceptually equivalent to:

```text
IdentifierSchemeCatalogPort
  findById(id)
  findUsableById(id)
```

The exact Java method names are implementation details and are not prescribed by this ADR. The important constraint is that the application-facing contract exposes **queries only** and no Scheme mutation operation.

The PostgreSQL adapter may implement this read-only port while also implementing other Party Registry persistence ports. Persistence rows do not leak into the application or domain model.

## Runtime Semantics

When Party Identifier processing references a Scheme, Party Registry reads the database-managed metadata required for:

- scheme identity/type;
- issuing country/category/subject compatibility;
- normalizer key;
- validator key;
- configured length constraints;
- expiration behavior;
- status/usable-state interpretation already defined by effective requirements;
- permanent tenant+scheme identifier uniqueness.

If persisted required Scheme configuration references a normalizer/validator implementation unavailable in the running version, readiness and dependent identifier processing fail according to the existing fail-closed rule. The application does not attempt to repair or rewrite Scheme data.

## Consequences

### Positive

- RUC/CÉDULA/PASAPORTE definitions cannot be changed through the service API.
- Runtime permissions can technically enforce read-only catalog access.
- Database changes remain reviewable and versioned/governed.
- Scheme changes cannot accidentally create tenant Party business events.
- The C4 model reflects real actors and capabilities rather than an invented administrator consumer.

### Negative

- Adding or changing a document scheme requires the database-management delivery path rather than a runtime API call.
- Application and database release coordination is required when a new Scheme references a newly introduced normalizer/validator implementation.

## Security and Data Impact

The runtime role restriction reduces the impact of application compromise or misuse by preventing mutation of validation reference data. Scheme data contains configuration/reference metadata rather than identifier plaintext, but unauthorized modification could change validation behavior and therefore data integrity.

No new table, datastore, API, authentication mechanism, or sensitive field is introduced.

## Operational Impact

Database migrations/data changes that affect `identifier_schemes` must be validated before promotion. Runtime readiness must detect unusable required Scheme configuration without attempting to mutate it.

## Validation

Architecture/database/integration evidence must prove:

- no Scheme Administration actor/component exists in LikeC4;
- no Scheme mutation input port/use case/resource exists;
- application runtime credentials cannot `INSERT`, `UPDATE`, or `DELETE` `identifier_schemes`;
- identifier processing can read required Scheme data;
- unsupported implementation keys fail closed;
- database-managed Scheme changes produce no tenant Party outbox event.

## Reversal or Replacement Strategy

If a later version requires runtime Scheme administration, it requires a new explicit product decision, API contract, authorization/security analysis, database privilege change, architecture update and ADR. It must not be inferred from the presence of the `identifier_schemes` table.

## Traceability

GitHub Issue #6; `docs/requirements/requirements-amendment-002.md`; `docs/database/v1-scheme.dbml`; `architecture/model.c4`; strict Clean Architecture and PostgreSQL governance profiles.
