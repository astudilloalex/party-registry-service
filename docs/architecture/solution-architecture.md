# Party Registry Service V1 — Solution Architecture

## 1. Document Control

| Field | Value |
|---|---|
| Status | Proposed |
| Approval | Pending `clean-architecture-gate-agent` after human decision recording |
| Architecture identifier | PRS-ARCH-001 |
| Version | 0.3 |
| Author | `solution-architecture-agent`, corrected by architecture review |
| Historical requirements baseline | PRS-REQ-001 v0.4, independently gated by `.factory/runs/oc-409818fa-64f6-430b-88eb-be8e97ddcd1b/result.json` |
| Effective requirements amendment | `docs/requirements/requirements-amendment-001.md` |
| Persistence authority | `docs/database/v1-scheme.dbml` |
| Architecture model | `architecture/model.c4` |
| Human change record | GitHub Issue #4; must be recorded through `factoryctl` after merge |

This architecture preserves the already-gated PRS-REQ-001 v0.4 bytes as historical evidence. Amendment 001 supersedes only the former command-idempotency behavior and the assumed external logging-platform dependency. It does not silently rewrite the prior Requirements Gate artifact.

## 2. Classification and Source Precedence

| Classification | Meaning |
|---|---|
| **CR — Confirmed Requirement** | Manifest, approved human decision, effective requirements/amendment, or confirmed contract. |
| **EF — Existing Fact** | Verified repository evidence; not automatically TO-BE behavior. |
| **AC — Architecture Constraint** | Mandatory boundary derived from confirmed authority. |
| **TD — TO-BE Decision** | Architecture choice pending independent gate approval. |
| **PD — Pending Decision** | Material choice requiring a later authorized decision. |
| **OS — Out of Scope** | Explicit exclusion. |

Human project-owner decisions have precedence over older generated requirements. After this PR is merged, the Issue #4 clarification must be persisted through `factoryctl transition HUMAN_DECISION_RECORDED`; until that occurs the factory state remains correctly blocked.

## 3. Authoritative Sources and Traceability

| Source | Architecture use |
|---|---|
| `.factory/project.yaml` | Strict Clean Architecture, LikeC4, Java 25/Quarkus, PostgreSQL DBML authority, VPS/Podman/Quadlet, no automatic DB design changes. |
| `.factory/decisions.json` | Existing approved lifecycle, trust posture, masking, rule-catalog and outbox decisions. The previous RG-104 command-idempotency decision is superseded by Issue #4/Amendment 001 once recorded through factoryctl. |
| `docs/requirements/requirements-specification.md` v0.4 | Historical gated baseline; clauses superseded by Amendment 001 are not effective. |
| `docs/requirements/requirements-amendment-001.md` | No command idempotency, permanent tenant+scheme+normalized-number uniqueness, no external logging platform. |
| `docs/database/v1-scheme.dbml` | Exclusive physical persistence authority, including unconditional identifier uniqueness. |
| `docs/architecture/adr/ADR-001-reactive-single-deployable.md` | Reactive single deployable. |
| `docs/architecture/adr/ADR-002-transactional-outbox.md` | Local transaction + at-least-once confirmed publication. |
| `docs/architecture/adr/ADR-003-sensitive-identifier-boundary.md` | Runtime-secret crypto and local fail-closed security logging. |
| `docs/architecture/adr/ADR-004-permanent-identifier-uniqueness.md` | Permanent identifier uniqueness without command idempotency. |
| `astudilloalex/geographic-reference-service` main | AlexAstudillo logging-format baseline adapted from `companyId` to `tenantId`. |

## 4. Scope and System Boundary

### 4.1 Owned capability

Party Registry is the tenant-scoped system of record for:

- Parties;
- natural-person and legal-entity details;
- natural-person nationalities;
- official Party Identifiers;
- the global Identifier Scheme catalog;
- tenant business-event outbox state.

Other services keep `party_id` as an opaque reference and never access the Party Registry database directly.

### 4.2 Actors and real external systems

| Element | Responsibility / relationship |
|---|---|
| Internal service consumer | Supplies trusted `tenant-id`, `user-id`, optional `process-id`, and invokes V1 REST operations. |
| Identifier Scheme administrator consumer | Maintains the global scheme catalog through the same internal API; Party Registry performs no role check. |
| Geographic Reference Service | Owns active ISO country reference data. |
| RabbitMQ | Receives persistent Party Registry events using publisher confirms. |
| Event consumer | Owns its queues and deduplicates at-least-once deliveries by event ID. |

There is **no `Platform Logging Capability` external system** in Party Registry V1. Logging is internal application behavior. There is also no V1 remote key-management system; encryption/HMAC masters are runtime deployment secrets.

### 4.3 Explicit exclusions

**OS:** customers, suppliers, employees, user accounts, login, authentication, authorization, roles, permissions, operating organizations, addresses, contacts, subscriptions, tax configuration, ownership structures, beneficial ownership, corporate relationships, audit persistence, an external logging platform, a remote V1 key-management service, public outbox CRUD, consumer queues/DLQs, and Geographic Reference ownership.

## 5. Selected Architecture

1. **TD:** one cohesive Quarkus Java 25 deployable.
2. **CR:** reactive REST and non-blocking external I/O; unavoidable CPU/blocking work uses bounded isolation.
3. **AC:** strict Clean Architecture with inward dependencies.
4. **CR:** PostgreSQL is service-owned persistence and final business-uniqueness authority.
5. **CR:** mutation plus required outbox insertion is one local PostgreSQL atomic boundary.
6. **CR:** RabbitMQ publication is asynchronous, persistent, publisher-confirmed and at least once.
7. **CR:** complete identifier values are authenticated-encrypted; exact lookup and permanent identifier uniqueness use tenant-isolated HMAC.
8. **CR:** no command-level `Idempotency-Key`, replay-result store, idempotency cache, or idempotency port exists in V1.
9. **CR:** encryption/HMAC masters are supplied through `.env`/Podman secret mechanisms; no network KMS is invented.
10. **CR:** operational and decryption-security logs are emitted through the local application logging path; no external logging software system is invented.
11. **TD:** no CQRS, event sourcing, distributed transaction, saga, additional microservice, or durable cache is introduced without measured need and a new ADR.

## 6. Strict Clean Architecture

### 6.1 Logical areas

| Area | Responsibilities | Forbidden dependencies |
|---|---|---|
| Domain | Entities/value objects, lifecycle policies, identifier/business invariants, domain errors. | Quarkus, Jakarta, CDI, REST, JSON, logging, ORM/Panache, database, messaging, configuration, crypto providers. |
| Application | Commands/queries, input ports, use cases, transaction intent, tenant scoping, output ports, boundary-neutral outcomes. | Concrete adapters, REST/persistence/provider DTOs, Quarkus APIs, MDC/SLF4J. |
| Inbound adapters | Reactive REST parsing, headers, transport validation, DTO mapping, ETag/error/envelope mapping. | Business-rule ownership or direct DB/client access. |
| Outbound adapters | PostgreSQL, Geographic cache/client, runtime-secret crypto, RabbitMQ, local structured security logging, clock. | Redefining domain rules or exposing adapter models inward. |
| Bootstrap | Quarkus wiring/configuration, adapter selection, startup/readiness, telemetry setup. | Business decisions. |

Dependency direction remains `inbound -> application -> domain`; outbound adapters implement application output ports. One Gradle module is sufficient initially; architecture tests enforce boundaries.

### 6.2 Port families

| Port | Direction | Responsibility |
|---|---|---|
| Party use-case ports | Inbound | Party/detail/nationality commands and queries. |
| Identifier use-case ports | Inbound | Identifier lifecycle, exact lookup and decryption operations. |
| Scheme use-case ports | Inbound | Scheme administration. |
| Tenant-scoped persistence ports | Outbound | Tenant-qualified reads/mutations; no persistence rows leak inward. |
| Unit-of-work/outbox port | Outbound | Commit business mutation and required outbox row atomically. |
| Geographic reference port | Outbound | Resolve active country references with explicit availability outcomes. |
| Identifier protection port | Outbound | Authenticated encryption/decryption and tenant-effective HMAC using runtime secrets. |
| Integration event publisher port | Outbound | Publish persistent versioned messages and classify confirms. |
| Decryption security-log port | Outbound | Emit mandatory non-plaintext structured log through the local application logger before plaintext return. |
| Clock port | Outbound | Testable current time; no automatic lifecycle scheduler. |

**There is intentionally no Party-creation idempotency port.**

## 7. Identifier Uniqueness Architecture

### 7.1 Business key

The permanent uniqueness scope for one Party Identifier is:

`tenant + Identifier Scheme + normalized identifier value`

Example:

`Tenant A + EC_CEDULA + 0123456789`

may exist at most once for the entire retained history of Tenant A under `EC_CEDULA`.

Because plaintext is forbidden in persistence, the physical enforcement key is:

`(tenant_id, identifier_scheme_id, normalized_value_hash)`

where `normalized_value_hash` is the tenant-effective HMAC-SHA-256 of the normalized value.

### 7.2 Lifecycle semantics

The uniqueness key is status-independent. `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED`, `EXPIRED`, and `REVOKED` all occupy the key permanently. Revoke/expire/reject never makes the same normalized value reusable in the same tenant and scheme.

The same normalized value may exist:

- under another tenant; or
- under another Identifier Scheme.

### 7.3 Concurrency

Concurrent requests can calculate the same fingerprint, but PostgreSQL is the final concurrency authority. The unconditional unique constraint permits at most one matching row to commit. The losing operation returns the API `CONFLICT` category and commits no duplicate identifier/outbox side effect.

This is a **business uniqueness invariant**, not HTTP command idempotency. The service does not replay the winning request's response.

## 8. Runtime Components

Stable application components are:

- Reactive REST Adapter;
- Party Application Capability;
- Identifier Application Capability;
- Scheme Administration Capability;
- Outbox Publication Capability;
- PostgreSQL Adapter;
- Geographic Reference Adapter/cache;
- Identifier Protection Adapter;
- RabbitMQ Publisher Adapter;
- Decryption Security Log Adapter;
- Runtime Composition and Readiness.

The Decryption Security Log Adapter is an internal application adapter, not an external C4 system.

## 9. Integration Architecture

| Integration | Contract / failure semantics |
|---|---|
| Internal REST | `/api/v1`, trusted context, ApiResponse envelope, ETag/If-Match, no V1 auth/authz, no `Idempotency-Key`. |
| Geographic Reference | Reactive provider boundary, 24-hour normal cache, approved stale branch, fail-unchanged dependency error. |
| RabbitMQ | Persistent messages, durable topic exchange, publisher confirms, at-least-once, stable event ID, consumer dedupe. |
| Runtime secrets | Deployment configuration, not network integration; separate encryption/HMAC masters via `.env`/Podman secrets. |
| Application logging | Local Quarkus/SLF4J logging path, not an external integration; fail-closed synchronous emission for decryption security evidence. |

RG-401 remains a **nonblocking API-contract authority issue**: exact pagination defaults/limits/filters/order must be authorized or explicitly deferred before API-contract approval. This architecture does not treat the old gate finding as product authority.

## 10. Data Architecture and DBML Authority

`docs/database/v1-scheme.dbml` is the exclusive source of physical tables, columns, relationships, constraints and indexes.

The updated DBML explicitly defines:

```text
UNIQUE (tenant_id, identifier_scheme_id, normalized_value_hash)
```

for Party Identifiers. Database-contract validation must verify that this unconditional uniqueness behaves correctly on PostgreSQL 18, including concurrency and all lifecycle statuses.

Other mandatory validations remain:

- tenant-qualified Party/Identifier references;
- Party type/detail invariants;
- active-nationality uniqueness;
- verified-primary Identifier uniqueness;
- optimistic versions;
- outbox checks/concurrency;
- PostgreSQL 18 compatibility;
- no plaintext identifier persistence.

No idempotency table, request-fingerprint store, replay-result store, or extra datastore is authorized.

## 11. Transaction and Consistency Boundaries

| Flow | Boundary |
|---|---|
| Party creation | Local transaction creates Party/approved initial state and required Party outbox event. No command-idempotency state participates. |
| Party/detail/nationality mutation | Local transaction updates component, Party version, audit facts and required outbox event. |
| Identifier creation/mutation | Local transaction preserves tenant/scheme/Party invariants, permanent uniqueness, version, audit and required outbox event. |
| Scheme mutation | Local transaction changes global scheme; no tenant Party outbox event. |
| Outbox delivery | Publisher claims durable rows, publishes outside business transaction, then records delivery outcome. |
| Exact identifier search | Tenant+scheme HMAC lookup; no decrypt/scan. |
| Decryption | Tenant-qualified read -> ciphertext authentication/decrypt -> required local security log -> no-store plaintext response. |

No distributed transaction, saga or compensation is needed.

## 12. Security and Privacy Architecture

- V1 has no login, authentication, authorization, roles, permissions, JWT/OIDC/Keycloak processing, or 401/403 behavior.
- `tenant-id` and `user-id` are trusted caller assertions; tenant scoping is mandatory on every tenant-owned operation.
- Complete identifier plaintext is transient only for approved submission/exact-search/decryption operations.
- Plaintext never enters PostgreSQL, RabbitMQ, logs, traces or metrics.
- Encryption and HMAC master keys are separate and runtime-injected.
- `normalized_value_hash` is never used as a user-visible identifier and must not be logged.
- Ordinary queries return masks only; plaintext return is isolated to the separate no-store decryption operation.
- Decryption emits tenant ID, user ID, process ID, Party Identifier ID, timestamp and action/outcome through the local structured logging adapter before plaintext return.
- Party Registry owns no audit database or audit table.

The approved V1 trust posture leaves residual risk: any internal consumer with network connectivity can assert context and invoke decryption. This remains explicit scope rather than an implicit authentication claim.

## 13. Enterprise Logging Baseline

Party Registry uses the AlexAstudillo logging pattern derived from `geographic-reference-service`, replacing that service's `companyId` context with Party Registry's `tenantId`:

```properties
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [user=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```

MDC keys:

- `processId`;
- `userId`;
- `tenantId`.

Message convention:

`[LOCATION] message`

The request logging boundary owns MDC creation, propagation and cleanup. Logging framework classes remain outside domain/application core.

There is no `Platform Logging Capability` software system, no remote log acknowledgement, and no centralized-retention requirement in Party Registry V1. The application considers its decryption-log obligation satisfied only after successful synchronous emission through its configured local logger. A synchronous local logging failure withholds plaintext.

## 14. Resilience, Performance and Recovery

- Geographic dependency behavior follows the approved cache/503 rules.
- RabbitMQ attempts use bounded timeout/concurrency and backoff/jitter while preserving same-event retry and no publisher-owned DLQ/discard policy.
- Connection pools, request concurrency and outbox batches are bounded and tuned from performance evidence.
- Readiness fails on unusable mandatory secrets, unsupported active rule configuration, unavailable mandatory persistence, or startup failure.
- Transient RabbitMQ failure is represented through lag/dependency signals rather than restart loops.
- PostgreSQL backup/RPO/RTO obligations remain unchanged.
- No premature HA/multi-region/distributed-cache topology is introduced.

## 15. Deployment Architecture

- rootless Podman + Quadlet on the approved VPS;
- application bound to loopback behind internal nginx;
- local/development may use an uncommitted `.env` file;
- VPS may use Podman secrets or a protected host-only env file;
- secret values never enter Git or the OCI image;
- PostgreSQL, RabbitMQ and Geographic Reference are the actual external/dependency boundaries shown in LikeC4;
- no logging platform appears in the C4 model;
- same immutable OCI digest is promoted across environments;
- production promotion does not rebuild;
- restricted deployment interfaces are used; database changes are forward-only.

`architecture/model.c4` represents system context, containers, components and deployment using LikeC4 `deployment {}` / `instanceOf` semantics.

## 16. Verification Strategy

| Layer | Mandatory evidence |
|---|---|
| Domain | Lifecycle, type/detail/date, mask, rule-catalog and identifier-uniqueness semantics. |
| Application | Tenant propagation, transaction intent, If-Match, exact lookup, decryption log-before-return. |
| Architecture | Domain/application dependency purity and adapter direction. |
| LikeC4 | `likec4 validate`; `likec4 format --check` where CLI supports it. |
| Database | PostgreSQL 18 schema validation including unconditional tenant+scheme+hash uniqueness and concurrent duplicate attempts. |
| Integration | Geographic failures, runtime-secret failures, RabbitMQ confirms/restart, transactional outbox. |
| Security | No plaintext leakage, HMAC separation, encryption secret separation, no-store, no auth/authz behavior. |
| Logging | MDC process/user/tenant context, `[LOCATION]` format, local decryption-security emission, no external platform dependency. |
| Performance | Approved pilot load/latency/error/event-lag targets. |
| Deployment | Digest identity, rootless/loopback posture, secret injection, health/smoke/stabilization, backup/restore. |

Identifier-specific tests must prove:

1. same tenant + same scheme + same normalized value is rejected;
2. it remains rejected when the old row is rejected, expired or revoked;
3. another tenant may use the same normalized value;
4. another scheme may use the same normalized value;
5. concurrent duplicates yield at most one committed Party Identifier and one corresponding committed side effect;
6. no `Idempotency-Key` or idempotency storage exists.

## 17. Architecture Guardrails

1. Domain imports no framework, persistence, messaging, logging, configuration or crypto-provider APIs.
2. Application core imports no concrete adapters, MDC/SLF4J or Quarkus APIs.
3. Inbound adapters call input ports; outbound adapters implement output ports.
4. Every tenant-owned persistence operation is tenant-qualified.
5. Models remain separated by boundary.
6. Business logic does not live in REST resources, repositories, mappers or publisher loops.
7. Business mutation + required outbox insertion commit atomically.
8. Plaintext identifiers and secrets never enter persistence or telemetry.
9. Blocking work on reactive paths requires bounded isolation.
10. No public outbox CRUD, shared DB write, cross-service FK or frontend DB access.
11. Public contracts are versioned and contract-first.
12. DBML changes require human authority; implementation never silently redesigns persistence.
13. V1 implements no command-level idempotency mechanism.
14. Party Identifier uniqueness is permanent per tenant + scheme + normalized value.
15. V1 has no external logging platform/system dependency.
16. V1 has no login/authentication/authorization request dependency.
17. Runtime keys are injected via approved `.env`/Podman secret mechanisms.
18. LikeC4 validation is required before architecture approval.

## 18. Risks and Pending Decisions

| ID | Classification | Treatment |
|---|---|---|
| AR-001 | **RESOLVED** — former command-idempotency persistence conflict | Requirement was superseded by human authority. Do not implement a substitute idempotency mechanism. Identifier duplicate prevention is now the DBML unconditional uniqueness rule. |
| AR-002 | High security/privacy — trusted internal caller can assert tenant/user and decrypt | Internal-only network, tenant predicates, no-store, local security logging, security/production gates. |
| AR-003 | High compliance — no purge/legal retention policy | Data minimisation; future compliance decision required before policy change. |
| AR-004 | High deployment — existing CI predates approved restricted/immutable promotion | Later deployment/release remediation and gates. |
| AR-005 | Medium integration — RabbitMQ uncertainty/backlog | Same-ID retry, confirms, metrics, operator recovery, consumer dedupe. |
| AR-006 | Medium availability — missing secrets/local logging/geographic failure can deny affected operations | Fail closed, readiness/cache rules and operational visibility. |
| AR-007 | Medium contract — exact pagination parameters lack authoritative product source | Authorized API-contract decision or explicit deferral before API approval. |
| AR-008 | High database — updated DBML still requires PostgreSQL 18 validation | Database-contract gate; no automatic correction. |

The architecture has no remaining high-risk contradiction caused by command idempotency. Database validation remains responsible for proving the updated physical uniqueness contract.

## 19. ADR Index

| ADR | Status | Decision |
|---|---|---|
| ADR-001 | Proposed | Reactive execution in one cohesive deployable. |
| ADR-002 | Proposed | Transactional outbox and confirmed at-least-once publication. |
| ADR-003 | Proposed | Runtime-secret identifier protection and local fail-closed decryption logging. |
| ADR-004 | Proposed | Permanent tenant-and-scheme identifier uniqueness without command idempotency. |

## 20. Factory Handoff

After Issue #4 changes are merged into `1-ft-1`, the project owner must record the new human decision with `factoryctl`. The factory will then return from `BLOCKED_HIGH_RISK` to its previous `ARCHITECTURE` state and reevaluate the architecture using the new authoritative decision and amended DBML.

The architecture agent/gate must not recreate `Idempotency-Key`, an idempotency store, or `Platform Logging Capability`. If architecture passes, the normal workflow can proceed to `ARCHITECTURE_GATE`, `WAITING_FOR_DBML`, and then database validation after explicit DBML confirmation.

## 21. Internal Consistency Review

- One deployable and strict Clean Architecture remain intact.
- No authentication/authorization dependency was introduced.
- No external logging platform exists in narrative or LikeC4.
- Runtime secrets remain deployment configuration, not a network key service.
- Command idempotency is explicitly removed rather than replaced.
- Permanent identifier uniqueness is represented consistently in Amendment 001, ADR-004, DBML, narrative architecture and LikeC4 component descriptions.
- Exact lookup and uniqueness use the same protected tenant-effective HMAC fingerprint without persisting plaintext.
- AR-001 is resolved by superseding its source requirement, not by inventing persistence.
- Transactional outbox/RabbitMQ consumer idempotency remains unchanged and is not confused with client-command idempotency.
- DBML remains the exclusive physical source and must pass independent PostgreSQL 18 validation.
- Architecture remains Proposed until independent gate approval.
