<!--
Sync Impact Report

- Version change: 1.1.0 -> 1.2.0
- Modified principles:
  - V. Traceable Quality and Observability -> V. Traceable Quality and Observability
    (added the mandatory application log format and reactive MDC propagation)
- Added sections: none
- Removed sections: none
- Modified sections:
  - Delivery and Compliance Workflow
- Follow-up TODOs:
  - Align the approved LikeC4 technology declaration, which currently names Hibernate
    Reactive with Panache, with the PostgreSQL Reactive Client mandate before persistence
    implementation begins.
  - Confirm in the approved deployment architecture that Party Registry Service has no
    public ingress or Internet exposure.
-->
# Party Registry Service Constitution

## Core Principles

### I. Strict Clean Architecture
Production code MUST be organized into `domain`, `application`, `infrastructure`, and
`api` layers. Dependencies MUST point inward: `api` and `infrastructure` may depend on
`application` and `domain`; `application` may depend on `domain`; `domain` MUST NOT
depend on any outer layer. Framework wiring and configuration MUST remain in an outer
composition boundary. Infrastructure and API concerns MUST enter the application only
through explicit ports and adapters.

The domain MUST remain compilable and testable without Quarkus, Mutiny, PostgreSQL,
dependency injection, transport libraries, persistence libraries, or other external
frameworks. This separation is non-negotiable because business behavior must remain
independent of delivery and storage technology.

### II. Domain-Driven Design and Model Integrity
`Party` MUST be modeled as a domain aggregate with explicit invariants and behavior.
Natural-person details, legal-entity details, nationalities, official identifiers, and
identifier schemes MUST follow the ownership, lifecycle, and integrity rules established
by approved requirements and the approved DBML. Business invariants MUST reside in domain
objects, domain services, or application use cases; REST resources and persistence
adapters MUST NOT decide business policy.

Transport DTOs, domain models, and persistence records MUST be distinct types with
explicit mappings at their boundaries. Persistence records MUST NOT become domain
entities, and API DTOs MUST NOT cross into the domain. The service MUST NOT expand the
Party bounded context to customers, suppliers, employees, user accounts, authentication,
authorization, addresses, contacts, or other concepts excluded by approved sources.
This protects aggregate consistency and prevents accidental coupling between bounded
contexts.

### III. End-to-End Reactive Execution
Java 25, Quarkus, and Mutiny are mandatory for application and adapter code. Every I/O
operation MUST be non-blocking and expose `Uni` for an asynchronous zero-or-one result or
`Multi` for an asynchronous stream. Reactive composition MUST be preserved from the API
adapter through application ports to infrastructure adapters; code MUST NOT block, call
`await` or equivalent synchronization APIs, use worker-thread offloading to conceal
blocking I/O, or invoke blocking APIs inside a reactive flow.

Persistence adapters MUST use the Quarkus PostgreSQL Reactive Client. Hibernate ORM,
Hibernate Reactive with Panache, JDBC, and other alternate or blocking persistence APIs
MUST NOT be used. Reactive transaction boundaries MUST be explicit in application or
infrastructure orchestration and MUST preserve aggregate mutation and transactional
outbox insertion atomically when the approved use case requires an event.

Flyway is the sole database schema migration mechanism. Flyway migration execution is an
operational activity outside request, event-processing, and other reactive business flows;
its database connection MUST NOT be reused by application persistence adapters. This
isolated migration boundary does not permit JDBC or blocking database access in production
application code.

### IV. Explicit Internal API and Tenant Boundaries
REST APIs MUST have an explicit approved contract, an explicit version, deterministic
validation, and one consistent error representation. API adapters MUST translate requests
and responses without containing business rules. Contract changes MUST be assessed for
compatibility and MUST NOT be inferred from implementation convenience.

Every tenant-owned operation MUST require and propagate `tenantId` through the API,
application, domain context, persistence predicates, and integration-event metadata.
Queries and mutations MUST fail closed when tenant context is absent or invalid, and no
tenant-owned row may be accessed by identifier alone. Complete official identifiers MUST
never appear in API responses, logs, traces, metrics, or integration events unless an
approved contract explicitly authorizes a protected representation.

Party Registry Service is an internal microservice. It MUST NOT expose a public Internet
route, public listener, or direct public ingress. Network access and outbound calls MUST
be limited to approved internal consumers and dependencies. An API version does not imply
public availability.

### V. Traceable Quality and Observability
Every behavior, data field, invariant, endpoint, and integration MUST trace to an approved
requirement, ADR, LikeC4 element or relationship, or DBML definition. Missing or conflicting
authority MUST stop the affected specification or implementation; contributors MUST NOT
invent requirements, business rules, data structures, failure semantics, or integrations.

Automated tests MUST provide evidence at every layer: framework-free unit tests for domain
invariants, use-case tests for application orchestration, integration tests for reactive
PostgreSQL and external adapters, API contract tests for versioning and errors, and
architecture tests for dependency direction and prohibited technologies. Tests MUST cover
tenant isolation, negative paths, concurrency, and non-blocking behavior where applicable.

API and infrastructure boundaries MUST emit structured logs, metrics, and distributed
trace context with correlation and causation identifiers where available. Observability
MUST avoid secrets, complete identifiers, and unnecessary personal data. Domain code MUST
remain free of observability-framework dependencies; outer adapters are responsible for
instrumentation.

Every human-readable application log sink that uses pattern-based output MUST use this
exact format:

```text
%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [user=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```

Outer adapters MUST place `processId`, `userId`, and `tenantId` in the mapped diagnostic
context whenever each value is available and MUST preserve that context across Mutiny
asynchronous boundaries. They MUST NOT fabricate unavailable user or tenant values.
Configuration or integration tests MUST verify the exact pattern and context propagation
for applicable request and event-processing paths.

## Technology and Source Constraints

- The mandatory runtime stack is Java 25 and Quarkus with Mutiny-based reactive flows.
- PostgreSQL access MUST use the Quarkus PostgreSQL Reactive Client and non-blocking
  transaction APIs.
- `docs/database/v1-scheme.dbml` is the authoritative database structure for the current
  approved version. Persistence records, SQL, constraints, and migrations MUST correspond
  to it exactly and MUST NOT silently alter its design.
- Every database schema or reference-data migration MUST be delivered and executed through
  Flyway. Migrations MUST be versioned, immutable after application, and consistent with the
  approved DBML. Manual DDL, automatic ORM schema generation, and application-driven schema
  mutation outside Flyway are prohibited; corrections MUST use a new Flyway migration.
- Approved requirements, architecture and ADR documents under `docs`, and the LikeC4 model
  retrieved through MCP are authoritative within their declared scopes.
- An explicit recorded human decision has precedence over an older project artifact. Any
  resulting inconsistency MUST be reconciled in the affected authoritative artifact before
  implementation proceeds.
- The currently approved LikeC4 model declares Hibernate Reactive with Panache for this
  service. That declaration conflicts with Principle III and MUST be amended or resolved by
  an explicit governance decision before persistence implementation begins.
- The DBML and approved architecture define the Party bounded context. Other services may
  retain `partyId` only as an opaque reference and MUST NOT create cross-service foreign
  keys into the Party Registry database.
- The deployment design MUST preserve internal-only reachability. Reverse proxies, DNS,
  firewall rules, container ports, and service discovery MUST NOT make this service
  Internet-accessible.

## Delivery and Compliance Workflow

1. Before specification or planning, contributors MUST identify the approved requirements,
   ADRs, LikeC4 views, API contracts, and DBML sections governing the requested change.
2. Specifications MUST state observable behavior, tenant boundaries, error semantics, and
   traceability without selecting unsupported rules or data structures.
3. Plans MUST map responsibilities to `domain`, `application`, `infrastructure`, and `api`,
   identify every I/O boundary, and demonstrate that each dependency points inward.
4. Persistence planning MUST verify DBML approval, define each schema change as a Flyway
   migration, and resolve the known LikeC4 persistence technology conflict before code or
   migrations are produced.
5. Implementation MUST keep DTO mappings, domain behavior, use-case orchestration, and
   reactive adapters separate. No convenience shortcut may cross a layer boundary.
6. Reviews MUST reject blocking calls, Hibernate or JDBC application persistence, database
   changes outside Flyway, modified applied migrations, missing tenant predicates, public
   exposure, noncompliant pattern-based logging, lost reactive logging context, business
   rules in adapters, unapproved schema changes, or absent traceability.
7. Relevant automated tests, architecture checks, and static analysis MUST pass before a
   change is considered complete. Test deletion, disabling, or weakened assertions MUST NOT
   be used to obtain a passing result.
8. Changes MUST use the repository's Pull Request workflow. Review evidence MUST identify
   the applicable constitutional principles and any approved exception or amendment.

## Governance

This constitution governs design, specification, planning, implementation, and review for
Party Registry Service. It takes precedence over generated templates, local conventions,
and implementation shortcuts. It does not authorize contributors to invent product
behavior or override approved requirements, architecture, ADRs, API contracts, or DBML;
conflicts between authorities MUST be documented and resolved before affected work
continues.

Amendments MUST be proposed as an explicit documentation change containing the rationale,
affected principles, compatibility or migration impact, and required synchronization of
dependent authoritative artifacts. Approval requires human review through the repository's
Pull Request process. Exceptions MUST be specific, time-bounded, documented, and approved;
an undocumented exception is non-compliance.

Constitution versions follow semantic versioning:

- MAJOR for removal or backward-incompatible redefinition of a principle or governance rule.
- MINOR for a new principle, section, or materially expanded mandatory guidance.
- PATCH for non-semantic clarification, wording, or typographical correction.

Every specification, plan, task set, and Pull Request review MUST include a constitution
compliance check. Reviewers MUST require correction or an approved amendment when evidence
does not demonstrate compliance. The constitution MUST be reviewed whenever approved
requirements, architecture, DBML, runtime technology, API exposure, or deployment boundaries
change.

**Version**: 1.2.0 | **Ratified**: 2026-08-21 | **Last Amended**: 2026-08-21
