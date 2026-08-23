# Implementation Plan: Party Registration

**Branch**: `11-ft-1` | **Feature Key**: `001-party-registration` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-party-registration/spec.md`

## Summary

Implement one internal, versioned Party creation operation for natural persons and legal entities.
The API obtains tenant, audit, and process context from trusted headers, maps a closed request
variant to a framework-free Party aggregate, validates distinct country references through a
reactive outbound port, and persists the aggregate plus an optional `party.created.v1` outbox row
atomically.

The implementation uses strict Clean Architecture, Java 25, Quarkus 3.33.3, Mutiny, Hibernate
Reactive ORM, PostgreSQL 18, and Flyway. No direct/native SQL, JDBC, blocking ORM,
or direct PostgreSQL Reactive Client access is permitted in production application code. Country
validation maps the approved Geographic Reference Postman contract through one alpha-2 lookup per
distinct code. Event publication is outside this feature.

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Quarkus 3.33.3 BOM; Quarkus REST/Jackson; REST Client/Jackson; Hibernate
Reactive; reactive PostgreSQL driver; Hibernate Validator; SmallRye OpenAPI; context propagation;
Micrometer Prometheus; OpenTelemetry; Flyway; PostgreSQL JDBC driver isolated to Flyway; Flyway
PostgreSQL support

**Storage**: PostgreSQL 18 `party_registry_service`; Hibernate Reactive ORM for application persistence;
Flyway-only schema migrations; transactional outbox stored with the Party aggregate

**Testing**: Mandatory behavior-level Red -> Green -> Refactor TDD using JUnit 5, Quarkus JUnit and
Vert.x reactive test support, RestAssured, ArchUnit, Testcontainers PostgreSQL 18, an HTTP stub for
Geographic Reference Service, OpenAPI parser/conformance checks, and JSON Schema 2020-12 validation

**Observability**: Quarkus automatic HTTP, REST Client, and Hibernate Reactive metrics/traces plus
trusted `processId`, `userId`, and `tenantId` correlation attributes; no domain instrumentation or
high-cardinality personal-data labels

**Target Platform**: Internal Linux-hosted JVM service; production packaging remains compatible
with the repository's Quarkus container images and rootless container runtime

**Project Type**: Internal reactive REST microservice

**Performance Goals**: No throughput or latency target is approved for this feature. Preserve
event-loop execution, deduplicate country validation requests to at most 11 provider lookups,
perform remote validation before opening a database transaction, and keep the transaction bounded
to local writes and commit.

**Constraints**: Strict Clean Architecture; framework-free domain; all runtime I/O returns `Uni`
or `Multi`; no blocking calls; Hibernate Reactive ORM; no production direct/native
SQL; Flyway-only migrations; exact DTO/domain/persistence separation; fail-closed tenant and
geographic validation; internal-only reachability; exact constitutional logging pattern and MDC
propagation; Geographic Reference contract UID
`15834347-d3591c82-bd52-46a9-973d-a7a102d4b9b3`; mandatory test-first implementation with recorded
Red and Green executions; singular UUID `Process-Id` propagated unchanged to downstream services;
no Party event publication in this feature

**Scale/Scope**: One creation operation, two mutually exclusive Party variants, one aggregate root,
one matching detail, zero to 10 natural-person nationalities, zero or one creation outbox event,
one geographic-reference dependency, and four persistence table families in scope

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Evidence | Pre-Design Result |
|------|----------|-------------------|
| Strict Clean Architecture | Planned `domain`, `application`, `infrastructure`, `api`, and outer `bootstrap` packages; dependencies point inward | PASS |
| Framework-free DDD domain | Party aggregate and value objects have no Quarkus, Mutiny, ORM, JSON, transport, or observability dependencies | PASS |
| Reactive execution | API, application ports, geographic client, and persistence return/compose `Uni`; remote validation occurs outside the transaction | PASS |
| Mandatory reactive ORM | Constitution 2.1.0 and updated LikeC4 both select Hibernate Reactive | PASS |
| Flyway-only schema evolution | Flyway is isolated from runtime persistence; Hibernate schema generation is disabled and validation enabled | PASS |
| Explicit internal API | OpenAPI contract is versioned under `/internal/v1`; no public server authority or public ingress is introduced | PASS |
| Trusted context and tenant isolation | `Tenant-Id`, `User-Id`, and `Process-Id` are required; body ownership is rejected; tenant and process context propagate to approved boundaries | PASS |
| DTO/domain/persistence separation | Explicit mapper boundaries and separate package trees are planned | PASS |
| Traceability and approved sources | `traceability.md` maps every requirement to its owner, DBML/LikeC4 source, Postman provider contract, and planned validation | PASS |
| Mandatory TDD | Each production behavior is planned as a small Red -> Green -> Refactor cycle at the lowest capable layer | PASS |
| Tests and observability | Domain, use-case, architecture, contract, PostgreSQL, rollback, non-blocking, logging, and MDC checks are planned as TDD cycles and checkpoint gates | PASS |

**Pre-design gate result**: PASS for Phase 0 and Phase 1 design.

**Pre-implementation database approval gate**: PENDING independent validation. The authoritative
DBML now records the approved nationality exclusion constraints, creation-event uniqueness,
creation-event payload shape, and `User-Id` audit semantics. No migration or persistence
implementation may precede independent database-contract approval. The existing deferred
detail-type trigger remains unchanged by decision.

## Project Structure

### Documentation (this feature)

```text
specs/001-party-registration/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── traceability.md
├── contracts/
│   ├── geographic-reference-port.md
│   ├── party-created-v1.schema.json
│   └── party-registration.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md                         # Created later as behavior-sized TDD cycles by /speckit.tasks
```

### Source Code (repository root)

```text
src/main/java/com/alexastudillo/partyregistry/
├── domain/party/
│   ├── model/                       # Party aggregate, details, nationality, value objects
│   └── exception/                   # Framework-free invariant failures
├── application/party/
│   ├── command/                     # CreatePartyCommand and type-specific inputs
│   ├── result/                      # CreatePartyResult and application error categories
│   ├── port/in/                     # CreatePartyUseCase
│   ├── port/out/                    # Geography, aggregate persistence, event policy, clock
│   └── service/                     # CreatePartyService orchestration
├── infrastructure/
│   ├── persistence/party/
│   │   ├── entity/                  # Hibernate Reactive persistence entities
│   │   ├── mapper/                  # Domain-to-persistence mapping
│   │   └── adapter/                 # One reactive transaction for aggregate + outbox
│   ├── integration/geographic/
│   │   ├── client/                  # Provider-specific reactive REST client
│   │   ├── dto/                     # Provider transport models
│   │   └── adapter/                 # Anti-corruption outcome mapping
│   ├── configuration/               # Event policy and bounded client configuration
│   └── observability/               # Reactive MDC propagation and adapter instrumentation
├── api/rest/v1/party/
│   ├── dto/                         # Closed natural/legal request variants and response
│   ├── mapper/                      # API-to-application mapping
│   ├── context/                     # Tenant-Id, User-Id, and Process-Id extraction/validation
│   ├── error/                       # RFC 9457 mapping
│   └── PartyResource.java           # Internal v1 inbound adapter
└── bootstrap/                       # CDI producers and dependency wiring

src/main/resources/
├── application.properties
└── db/migration/                    # Created only after final DBML validation

src/test/java/com/alexastudillo/partyregistry/
├── domain/party/                    # Pure domain tests
├── application/party/               # Use-case tests with hand-written reactive fakes
├── architecture/                    # Dependency and prohibited-technology rules
├── infrastructure/
│   ├── persistence/party/           # PostgreSQL 18, Flyway, mapping, atomic rollback
│   └── integration/geographic/      # HTTP serialization and failure mapping
└── api/rest/v1/party/                # OpenAPI, headers, variants, errors, MDC

src/integrationTest/java/com/alexastudillo/partyregistry/
└── api/rest/v1/party/                # Packaged black-box and runtime OpenAPI tests
```

**Structure Decision**: Use one Quarkus service with package-level Clean Architecture boundaries.
The domain owns Party behavior; application owns orchestration and ports; API and infrastructure
are replaceable adapters; bootstrap performs framework wiring. Tests mirror production boundaries
under the configured Gradle unit and integration test source sets.

## Design Overview

### Inbound Flow

`PartyResource` validates singular `Tenant-Id`, `User-Id`, and `Process-Id` headers plus a closed
request variant, sets reactive MDC, maps to `CreatePartyCommand`, invokes `CreatePartyUseCase`, and
maps the committed result to `201`. One exception mapper converts every expected failure category
to the OpenAPI RFC 9457 contract.

### Domain and Application Flow

The domain factory constructs a new Party without a persistence identity and enforces type/detail
compatibility, required values, derived display name, lifecycle dates, a maximum of 10
nationalities, interval rules, active duplicate/primary rules, initial `DRAFT`, and version `0`.
The application gathers distinct country codes, invokes `ActiveCountryReferenceValidationPort`,
passing trusted tenant, audit-subject, and process context required by its approved provider,
evaluates event policy once, creates the optional technology-neutral event-recording intent, and
calls one aggregate persistence port. The geographic adapter executes per-code alpha-2 lookups,
propagates the three context headers unchanged, maps only `ACTIVE` to success, and fails closed for
incomplete or unexpected provider results.

### Persistence Flow

The persistence adapter maps the aggregate to dedicated Hibernate entities and executes one
Hibernate Reactive Mutiny transaction. Persisting the root entity graph assigns its UUID version 7;
the adapter then maps the application event intent with that aggregate ID and persists the optional
outbox entity sequentially in the same transaction. Success is emitted after commit; any write or
flush failure rolls back all rows. No publisher or RabbitMQ client is started by this feature.

### Data Contract Alignment

The current DBML remains authoritative and now contains the approved temporal nationality exclusion
constraints, unique creation-event identity, closed `party.created.v1` payload check, and `User-Id`
outbox audit semantics documented in [research.md](./research.md). It still requires independent
database-contract validation before migration work. The current deferred detail-type trigger is
retained, while exactly-one-detail and no-legal-nationality guarantees remain domain/application
responsibilities backed by integration tests.

### Verification Flow

Every production behavior starts with the lowest-layer test capable of proving it. The targeted
test is run first and must fail for the expected missing behavior (Red); only enough production code
is added to pass it (Green); design improvements then occur while the affected suite remains green
(Refactor). Each cycle retains its targeted command and expected Red/Green outcome. Pure refactoring
starts from a verified green baseline and stays green.

Filtered domain or application tests provide the default inner loop. Quarkus HTTP and PostgreSQL 18
tests drive adapter behavior, and packaged API tests close each independently useful vertical slice.
Full suites are checkpoint gates rather than substitutes for targeted cycles. Architecture tests run
at affected Green and Refactor checkpoints and reject framework imports in the domain, outward
dependencies, blocking APIs, direct/native SQL, direct reactive-client access, blocking
Hibernate, and JDBC outside the Flyway boundary.

## Phase 1 Constitution Re-Check

| Gate | Post-Design Evidence | Result |
|------|----------------------|--------|
| Strict Clean Architecture | `data-model.md` separates domain, application, API, and ORM models; geographic semantics are application-owned | PASS |
| Framework-free DDD domain | Domain fields, invariants, and state transitions are defined without framework types | PASS |
| Reactive ORM and transactions | `research.md` fixes Hibernate Reactive Mutiny transaction sequencing and prohibits parallel session writes | PASS |
| Flyway-only schema evolution | `quickstart.md` blocks migrations until final DBML validation and requires Hibernate validation only | PASS |
| Explicit internal API | `contracts/party-registration.openapi.yaml` defines one versioned internal operation and one RFC 9457 error family | PASS |
| Trusted request context | Contract and data model require singular `Tenant-Id`, `User-Id`, and `Process-Id`; server-owned fields are rejected from the body | PASS |
| Geographic anti-corruption boundary | `contracts/geographic-reference-port.md` maps the approved alpha-2 lookup, propagated context headers, activity states, and fail-closed outcomes without provider DTO leakage | PASS |
| Transactional outbox privacy | JSON schema contains only `partyType`; DBML metadata, uniqueness, and `ck_party_outbox_created_event_shape` are explicit | PASS |
| Mandatory TDD | `research.md` and `quickstart.md` define targeted Red, minimal Green, green Refactor, and layer checkpoint evidence | PASS |
| Tests and observability | `quickstart.md` covers TDD ordering plus all acceptance, rollback, tenant, non-blocking, architecture, contract, log-format, and MDC evidence | PASS |
| Requirement traceability | `traceability.md` covers FR-001 through FR-028 and every measurable success criterion | PASS |
| Approved-source consistency | Current DBML and Postman decisions are cited directly; independent database approval and remaining architecture corrections stay explicit pre-implementation gates | PASS |

**Post-design gate result**: PASS for the Phase 0/Phase 1 planning artifacts. No clarification marker
or unjustified constitutional exception remains in the design.

**Implementation readiness**: BLOCKED until the current DBML passes independent database-contract
validation. The Geographic Reference REST adapter contract is resolved by Postman collection
`15834347-d3591c82-bd52-46a9-973d-a7a102d4b9b3`, the approved `data.status` clarification, and the
2026-08-23 trusted-header decision. Geographic Reference must synchronize its collection and
runtime to require `Tenant-Id`, `User-Id`, and `Process-Id` and remove `company-id` before adapter
integration acceptance. The externally maintained LikeC4 model still must correct the Party
database description so it does not imply customer ownership.

Before implementation, the architecture owner must also add an approved LikeC4 component view for
the Clean Architecture boundaries, ports, adapters, and bootstrap composition; record the upstream
trusted-header/internal-ingress boundary; and approve an execution-model ADR for Hibernate Reactive
and its transaction/resource consequences. The explicit human decision that the service is
internal-only remains binding while those architecture artifacts are synchronized.

## Complexity Tracking

No constitutional violation or exception is accepted by this plan. The temporal database
constraints add justified integrity complexity required to enforce approved concurrent temporal
rules. They are present in the authoritative DBML but require independent validation before
migration work, so they are not treated as an implementation exception.
