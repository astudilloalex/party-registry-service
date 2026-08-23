# Traceability Matrix: Party Registration

**Date**: 2026-08-21

## Architecture References

- **C2**: [Party Registry service context](file:///home/alex/Documents/Development/architecture/alex-astudillo-architecture/architectures/party-registry/party-registry-overview.c4#L2)
- **CP**: [Create Party sequence](file:///home/alex/Documents/Development/architecture/alex-astudillo-architecture/architectures/party-registry/party-registry-sequences.c4#L2)
- **OB**: [Optional outbox sequence](file:///home/alex/Documents/Development/architecture/alex-astudillo-architecture/architectures/party-registry/party-registry-sequences.c4#L169)
- **DBML**: `docs/database/v1-scheme.dbml`
- **REST**: `contracts/party-registration.openapi.yaml`
- **GEO**: `contracts/geographic-reference-port.md`
- **EVENT**: `contracts/party-created-v1.schema.json`

## Functional Requirements

| Requirement | Owning Design Element | Source/Decision | Planned Validation |
|-------------|-----------------------|-----------------|--------------------|
| FR-001 | `PartyResource`, `CreatePartyUseCase` | REST, CP, R-003 | API and use-case success tests |
| FR-002 | API context adapter | REST, R-009 | Missing, malformed, repeated, and valid `Tenant-Id` contract tests |
| FR-003 | Closed API request and mapper | REST, R-009 | Reject body `tenantId` and all server-owned fields |
| FR-004 | API context adapter and audit context | REST, R-004 | Missing, blank, oversized, repeated, and valid `User-Id` tests |
| FR-005 | API adapter and use-case ordering | CP, R-003 | Verify no geographic or persistence call after invalid context |
| FR-006 | Party domain factory | DBML, R-002 | Type enum and immutability domain tests |
| FR-007 | Natural-person domain factory | REST, DBML | Require natural details; reject legal/both details |
| FR-008 | Natural-person value objects | REST, DBML | Required and bounded name tests |
| FR-009 | Legal-entity domain factory | REST, DBML | Require legal details; reject natural details/nationalities |
| FR-010 | Legal-entity value objects | REST, DBML | Required and bounded legal field tests |
| FR-011 | Party display-name policy | R-003, data model | Natural and legal derivation; reject derived value over 300 |
| FR-012 | Lifecycle date value objects | REST, DBML | Equal, open, valid, and reversed lifecycle date tests |
| FR-013 | Nationality interval value object | REST, DBML | Equal, open, valid, and reversed validity date tests |
| FR-014 | `ActiveCountryReferenceValidationPort` | C2, CP, GEO, R-012 | Active birth/incorporation/nationality HTTP-stub tests |
| FR-015 | Geographic outcome mapping | GEO, R-012/R-013 | Unknown, inactive, partial, malformed, timeout, and connection failure tests |
| FR-016 | Party aggregate composition | DBML, data model | Natural-only nationality domain and persistence tests |
| FR-017 | UTC registration date and interval rules | R-004/R-007 | Boundary-date and open-bound domain tests |
| FR-018 | Domain temporal checks and approved DB exclusion | R-007, DBML gate | Duplicate-country, overlapping-country, primary-overlap, and concurrency tests |
| FR-019 | Party factory | DBML, R-004 | Assert `DRAFT` and version `0` in domain, persistence, and response |
| FR-020 | Trusted creation context and ORM mapper | DBML, R-004 | Same timestamp/`User-Id` on all initial mutable records |
| FR-021 | Aggregate persistence port | CP, OB, R-003 | Real PostgreSQL all-or-nothing transaction tests |
| FR-022 | Event policy port | OB, R-014 | Disabled and excluded event configuration matrix |
| FR-023 | Event intent and outbox mapper | OB, EVENT, R-014 | Exactly one `PENDING` event with tenant/aggregate/version metadata |
| FR-024 | Minimal event payload mapper | EVENT, R-014 | JSON Schema validation and prohibited-data assertions |
| FR-025 | Persistence adapter and error mapping | R-003/R-015 | Failure injection at root, detail, nationality, outbox, flush, and commit |
| FR-026 | `CreatePartyResult` and response mapper | REST, R-004 | `201` response UUID version 7 and version `0` |
| FR-027 | API problem mapper | REST, R-010 | Status/type/code-specific RFC 9457 conformance tests |
| FR-028 | Request schema and Party factory | REST, R-017 | Accept 0 and 10 nationalities; reject 11 before geographic validation |

## Success Criteria

| Criterion | Evidence |
|-----------|----------|
| SC-001 | Automated scenario inventory covers every acceptance scenario in `spec.md` |
| SC-002 | Aggregate cardinality assertions against PostgreSQL 18 after accepted commands |
| SC-003 | Failure-injection suite proves zero attributable rows after every rejected/failed command |
| SC-004 | Two-tenant matrix verifies Party and outbox ownership isolation |
| SC-005 | Event configuration matrix verifies zero-or-one outbox behavior |
| SC-006 | Audit assertions and sanitized logs/errors/event payload checks |
| SC-007 | API/use-case tests prove one committed result or one stable failure category |

## Architecture Fitness Functions

- ArchUnit verifies inward package dependencies and framework-free domain code.
- ArchUnit rejects, direct/native SQL, direct reactive-client persistence, blocking
  Hibernate ORM, `java.sql`, blocking waits, and worker offloading in production code.
- OpenAPI conformance tests compare checked-in and runtime contracts.
- Event payload tests validate both Party types against the checked-in JSON Schema.
- Reactive tests verify event-loop context and MDC propagation across API, application, geographic,
  and persistence boundaries.
- PostgreSQL 18 tests verify Flyway/DBML correspondence, Hibernate schema validation, UUID version
  7, atomic rollback, temporal exclusion, and outbox uniqueness.

## Pre-Implementation Traceability Gates

- The final DBML must incorporate the approved temporal nationality exclusion and outbox
  uniqueness/`User-Id` audit decisions and pass independent validation.
- LikeC4 must make the natural/legal alternatives and remote-before-transaction boundary explicit
  and remove customer ownership from the Party database description.
- LikeC4 must add the Clean Architecture component/port/adapter view and the upstream
  trusted-header/internal-ingress boundary.
- The architecture owner must approve an execution-model ADR for Hibernate Reactive ORM and the
  reactive transaction/resource model.
- The Geographic Reference adapter mapping must cite its provider's approved wire contract.
- Migration and implementation tasks must retain the requirement identifiers in this matrix.
