# Party Registry Service V1 — Solution Architecture

## 1. Document Control

| Field | Value |
|---|---|
| Status | Proposed |
| Approval | Pending `clean-architecture-gate-agent` after human decision recording |
| Architecture identifier | PRS-ARCH-001 |
| Version | 0.4 |
| Author | `solution-architecture-agent`, corrected by architecture review |
| Historical requirements baseline | PRS-REQ-001 v0.4 |
| Effective requirements amendments | `requirements-amendment-001.md`, `requirements-amendment-002.md` |
| Persistence authority | `docs/database/v1-scheme.dbml` |
| Architecture model | `architecture/model.c4` |
| Human change records | GitHub Issues #4 and #6 |

This architecture preserves the already-gated PRS-REQ-001 v0.4 bytes as historical evidence. Amendment 001 supersedes command-level idempotency and the assumed external logging platform. Amendment 002 supersedes every runtime Identifier Scheme administration use case. The project-owner decisions have precedence over older generated statements once recorded through the governed factory decision flow.

## 2. Source Precedence

1. Recorded human decisions and the explicit project-owner clarifications represented by Issues #4/#6 and their amendments.
2. `.factory/project.yaml`.
3. Effective requirements: PRS-REQ-001 v0.4 plus Amendments 001 and 002.
4. `docs/database/v1-scheme.dbml` for physical persistence facts.
5. Architecture, stack, persistence and deployment profiles.
6. Verified repository facts.

No architecture statement may infer a runtime CRUD/use case solely because a DBML table exists.

## 3. System Scope

Party Registry is the tenant-scoped system of record for:

- Parties;
- natural-person details;
- legal-entity details;
- natural-person nationalities;
- official Party Identifiers;
- the database-managed global Identifier Scheme reference catalog;
- tenant business-event outbox state.

The service owns Party and Party Identifier business behavior. `identifier_schemes` is service-owned reference data in PostgreSQL but **not an application-administered business resource**.

Other services retain `party_id` as an opaque reference and never read/write the Party Registry database directly.

## 4. Actors and External Systems

| Element | Relationship |
|---|---|
| Internal service consumer | Calls approved Party and Party Identifier REST operations and supplies trusted tenant/user/process context. |
| Geographic Reference Service | Owns country reference data used by Party Registry validation. |
| RabbitMQ | Receives persistent Party Registry business events with publisher confirms. |
| Event consumer | Owns queues and deduplicates at-least-once delivery by event ID. |

There is **no Identifier Scheme Administrator Consumer** in V1.

There is also no external logging platform and no remote V1 key-management service. Logging is local application behavior; encryption/HMAC master keys are runtime-injected deployment secrets.

## 5. Selected Architecture

1. One cohesive Quarkus Java 25 deployable.
2. Reactive REST and reactive external I/O; unavoidable blocking/CPU work uses bounded isolation.
3. Strict Clean Architecture with dependencies pointing inward.
4. PostgreSQL is service-owned persistence and final business-uniqueness authority.
5. `identifier_schemes` is a database-managed read-only catalog from the application runtime perspective.
6. Mutation plus required outbox insertion is one local PostgreSQL atomic boundary.
7. RabbitMQ publication is asynchronous, persistent, publisher-confirmed and at least once.
8. Complete identifiers are authenticated-encrypted; exact lookup and permanent identifier uniqueness use tenant-isolated HMAC.
9. No client-command `Idempotency-Key`, replay store, request fingerprint, idempotency cache or idempotency port exists in V1.
10. Encryption/HMAC masters are supplied through approved `.env`/Podman-secret mechanisms.
11. Operational and decryption-security logs are emitted through the local application logging path using the AlexAstudillo standard.
12. No CQRS, event sourcing, distributed transaction, saga, extra microservice or durable cache is introduced without a new approved decision/ADR.

## 6. Strict Clean Architecture

### 6.1 Logical areas

| Area | Responsibilities | Forbidden dependencies |
|---|---|---|
| Domain | Entities/value objects, Party/Identifier lifecycle policies, invariants, masking and business errors. | Quarkus, Jakarta, CDI, REST, JSON, logging, ORM/Panache, database, messaging, configuration, crypto-provider APIs. |
| Application | Party and Identifier commands/queries, input ports, use cases, transaction intent, tenant scoping, read-only Scheme catalog contract, output ports and boundary-neutral outcomes. | Concrete adapters, REST/persistence/provider DTOs, Quarkus APIs, MDC/SLF4J. |
| Inbound adapters | Reactive REST parsing, request context, DTO mapping, ETag/If-Match, ApiResponse/error mapping. | Business-rule ownership, direct DB/client access, Scheme catalog administration. |
| Outbound adapters | PostgreSQL, Geographic client/cache, runtime-secret crypto, RabbitMQ, local structured security logging, clock. | Redefining domain rules or exposing adapter models inward. |
| Bootstrap | Quarkus wiring/configuration, adapter selection, runtime privilege/config validation, startup/readiness and telemetry setup. | Business decisions. |

### 6.2 Application ports

| Port | Direction | Responsibility |
|---|---|---|
| Party use-case ports | Inbound | Party/detail/nationality approved commands and queries. |
| Party Identifier use-case ports | Inbound | Identifier lifecycle, lookup, exact search and decryption. |
| Identifier Scheme catalog port | Outbound, read-only | Read database-managed Scheme metadata needed by Party Identifier processing. It exposes queries only. |
| Tenant-scoped persistence ports | Outbound | Tenant-qualified Party/Identifier reads and mutations; no persistence model leakage. |
| Unit-of-work/outbox port | Outbound | Commit represented business mutation and required outbox row atomically. |
| Geographic reference port | Outbound | Resolve active country references with explicit availability outcomes. |
| Identifier protection port | Outbound | Authenticated encryption/decryption and tenant-effective HMAC using runtime secrets. |
| Integration event publisher port | Outbound | Publish persistent versioned messages and classify publisher confirms. |
| Decryption security-log port | Outbound | Emit required non-plaintext local structured log before plaintext return. |
| Clock port | Outbound | Testable current time; no scheduler is implied. |

There is intentionally **no Scheme Administration input port**, no Scheme mutation port and no Party-creation idempotency port.

## 7. Database-Managed Identifier Scheme Catalog

### 7.1 Ownership

`identifier_schemes` contains global reference metadata for supported official-document schemes such as CÉDULA, RUC and PASAPORTE.

The application runtime uses this table only to read the metadata required for Party Identifier processing:

- scheme identity/code;
- issuing country/category;
- applicable subject type;
- normalizer implementation key;
- validator implementation key;
- minimum/maximum length;
- expiration requirement;
- persisted status/usable-state metadata;
- version/audit metadata where required for diagnostics/contract validation.

### 7.2 No runtime administration

Party Registry V1 exposes no REST/application operation to:

- create schemes;
- update schemes;
- activate/deprecate/retire schemes;
- delete schemes;
- search/browse schemes as an administration resource.

The presence of the DBML table does not imply CRUD.

New or changed Scheme data is delivered only through the governed database-management path: reviewed database migrations and/or explicitly authorized database administration.

### 7.3 Runtime database privilege boundary

The runtime PostgreSQL role should enforce:

```text
identifier_schemes
  SELECT: allowed
  INSERT: denied
  UPDATE: denied
  DELETE: denied
```

Migration/database-administration credentials are separate from runtime credentials.

This privilege separation is mandatory architecture/database validation evidence. Application repositories/adapters must not expose Scheme mutation methods even if a broader development database account could technically execute them.

### 7.4 Rule implementation compatibility

Normalizer/validator implementations remain versioned application code. A database-managed Scheme can reference only keys supported by the released application version.

If required persisted Scheme configuration references an unavailable implementation:

- dependent Party Identifier operations fail unchanged;
- readiness reflects unusable required configuration where applicable;
- the application does not mutate/repair the Scheme row.

A new Scheme that requires a new normalizer/validator therefore requires coordinated software release plus governed database change.

## 8. Permanent Party Identifier Uniqueness

The business uniqueness scope is:

```text
tenant + Identifier Scheme + normalized identifier value
```

Since plaintext cannot be persisted, PostgreSQL enforces:

```text
UNIQUE (tenant_id, identifier_scheme_id, normalized_value_hash)
```

`normalized_value_hash` is the tenant-effective HMAC-SHA-256 of the normalized identifier.

The key is permanent across `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED`, `EXPIRED` and `REVOKED`. Status never releases a document number for reuse inside the same tenant and Scheme.

The same normalized value may exist under another tenant or another Identifier Scheme.

Concurrent duplicates are resolved by the PostgreSQL unique constraint: at most one Party Identifier row commits. The losing operation returns the API `CONFLICT` category and creates no duplicate identifier/outbox side effect. This is business uniqueness, not command-response idempotency.

## 9. Runtime Components

Stable application components are:

- Reactive REST Adapter;
- Party Application Capability;
- Identifier Application Capability;
- Outbox Publication Capability;
- PostgreSQL Adapter;
- Geographic Reference Adapter/cache;
- Identifier Protection Adapter;
- RabbitMQ Publisher Adapter;
- Decryption Security Log Adapter;
- Runtime Composition and Readiness.

There is no Scheme Administration Capability. Scheme reads occur as part of Identifier processing through the read-only catalog port implemented by the PostgreSQL adapter.

## 10. API Surface

The V1 REST API is rooted under `/api/v1` and follows the approved ApiResponse/ETag/If-Match conventions.

Runtime REST surface includes approved Party, Party-detail, nationality and Party Identifier operations.

The API MUST NOT expose Identifier Scheme administration. Amendment 002 also removes the previously generated Scheme lookup/search resources from V1; runtime Scheme access is internal reference-data access unless a later explicit requirement introduces a read-only public/internal discovery contract.

No `Idempotency-Key` is accepted or interpreted by V1 commands.

RG-401 remains a nonblocking downstream API-contract authority issue for exact pagination/filter/sort defaults. Architecture does not manufacture those values.

## 11. Transaction Boundaries

| Flow | Atomicity / behavior |
|---|---|
| Party creation | Local transaction creates Party/approved initial data and required Party outbox event. |
| Party/detail/nationality mutation | Local transaction updates component, Party version, audit facts and required outbox event. |
| Party Identifier creation/mutation | Local transaction preserves tenant/Party/Scheme invariants, permanent uniqueness, version/audit state and required outbox event. |
| Identifier Scheme access | Read-only query of database-managed reference data; no business mutation transaction exists. |
| Outbox delivery | Publisher claims durable rows, publishes outside business transaction, then records delivery outcome. |
| Exact identifier search | Tenant+Scheme HMAC lookup; no decrypt/scan. |
| Decryption | Tenant-qualified read -> ciphertext authentication/decrypt -> required local security log -> no-store plaintext response. |

There is no Scheme mutation flow, no distributed transaction, no saga and no compensation design.

## 12. Transactional Outbox

Approved Party and Party Identifier mutations that require events commit the business mutation and outbox insertion in the same PostgreSQL transaction.

Publisher semantics remain:

- persistent RabbitMQ messages;
- publisher confirms;
- at-least-once delivery;
- same event ID on retry/recovery;
- transient/unknown outcomes remain retryable;
- non-recoverable failures become operator-visible `FAILED`;
- no Party Registry publisher DLQ/discard/purge;
- consumers deduplicate by event ID.

Database-managed Identifier Scheme catalog changes produce **no tenant Party outbox event**.

## 13. Security and Privacy

- No login, authentication, authorization, roles, JWT/OIDC/Keycloak request processing or 401/403 behavior in V1.
- `tenant-id` and `user-id` are trusted caller assertions.
- Every tenant-owned Party/Identifier operation is tenant-qualified.
- Complete identifier plaintext is transient only for approved submission/exact-search/decryption paths.
- Plaintext never enters PostgreSQL, RabbitMQ, logs, traces or metrics.
- Encryption and HMAC master keys are separate and runtime-injected.
- Ordinary queries expose masks only.
- Decryption emits tenant ID, user ID, process ID, Party Identifier ID, timestamp and action/outcome through the local structured logger before plaintext return.
- Party Registry owns no audit table/database.
- Runtime DB permissions deny Scheme catalog mutation, reducing validation-policy tampering risk through the application account.

## 14. Enterprise Logging

Party Registry follows the AlexAstudillo pattern established by Geographic Reference Service, adapted to `tenantId`:

```properties
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [user=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```

MDC keys:

- `processId`;
- `userId`;
- `tenantId`.

Message convention:

```text
[LOCATION] message
```

MDC/SLF4J/Quarkus logging remains in API/infrastructure/bootstrap concerns, not domain/application core.

No `Platform Logging Capability` exists in C4. Decryption is fail-closed on synchronous local log-emission failure; V1 does not claim remote log delivery or centralized persistence/retention.

## 15. Geographic Reference

Country-code validation uses Geographic Reference Service with the approved:

- 24-hour normal in-memory cache;
- up-to-seven-day stale fallback during dependency unavailability;
- 503/fail-unchanged behavior when no usable cache exists;
- historical-code preservation.

The cache is not a source of truth and is lost on process restart.

## 16. Deployment

- rootless Podman + Quadlet on the approved VPS;
- application bound to loopback behind internal nginx;
- local/development secrets may use uncommitted `.env`;
- VPS secrets may use Podman secrets or protected host-only env files;
- secret values never enter Git or the OCI image;
- PostgreSQL, RabbitMQ and Geographic Reference are the actual external/dependency boundaries shown in LikeC4;
- same immutable OCI digest is promoted across environments;
- production promotion does not rebuild;
- database changes are forward-only and governed;
- runtime DB credentials are distinct from migration/administration credentials and must not mutate `identifier_schemes`.

## 17. Readiness and Resilience

Readiness must fail or report unavailable when required capabilities/configuration are unusable, including:

- mandatory PostgreSQL access unavailable;
- required encryption/HMAC secret unavailable or invalid;
- persisted Scheme configuration references required normalizer/validator implementation unavailable in the running version;
- startup cannot establish mandatory configuration.

Transient RabbitMQ failure is represented through dependency/lag signals rather than restart loops. Geographic unavailability follows the approved cache semantics.

No automatic failover, multi-region topology, distributed cache or premature horizontal-scaling architecture is introduced.

## 18. Database Contract Handoff

`docs/database/v1-scheme.dbml` remains the exclusive physical source of truth.

Database-contract validation must verify:

1. PostgreSQL 18 compatibility.
2. Party type/detail constraints.
3. nationality uniqueness/date invariants.
4. permanent `(tenant_id, identifier_scheme_id, normalized_value_hash)` uniqueness.
5. verified-primary Identifier uniqueness.
6. tenant-qualified references and optimistic versions.
7. outbox checks and `SKIP LOCKED` delivery semantics.
8. no plaintext identifier persistence.
9. `identifier_schemes` runtime read-only privilege separation.
10. Scheme data changes remain migration/DB-administration governed and produce no tenant Party outbox event.

No automatic DBML redesign is authorized.

## 19. Verification Strategy

| Layer | Mandatory evidence |
|---|---|
| Domain | Party/Identifier lifecycle, type/detail/date rules, masking and permanent identifier uniqueness. |
| Application | Tenant propagation, Scheme read-only catalog use, If-Match, exact lookup, decryption log-before-return. |
| Architecture | Domain/application dependency purity; no Scheme Administration input capability. |
| LikeC4 | `likec4 validate`; no Identifier Scheme Administrator actor/capability. |
| Database | PostgreSQL constraints plus runtime `identifier_schemes` SELECT-only privilege. |
| Adapter integration | Scheme reads succeed; Scheme runtime writes are impossible; Geographic/crypto/logging/RabbitMQ faults behave as specified. |
| Contract | No Scheme administration/discovery API in V1; approved Party/Identifier contracts remain versioned. |
| Security/privacy | Tenant isolation, plaintext exclusion, runtime secret separation, no-auth posture, Scheme write privilege denial. |
| Performance/reliability | Approved load/latency/error/lag targets and publisher recovery. |
| Deployment | Digest identity, rootless/loopback posture, runtime-secret injection, DB role separation, health/smoke/stabilization and backup/restore. |

## 20. Architecture Guardrails

1. Domain imports no Quarkus/Jakarta/REST/JSON/logging/ORM/database/messaging/configuration/crypto-provider APIs.
2. Application imports no concrete adapter implementation.
3. Inbound adapters call application input ports only.
4. Outbound adapters implement application output ports.
5. No Scheme Administration input port, REST resource, service or background mutation exists.
6. Identifier Scheme catalog contracts exposed inward are query-only.
7. Runtime DB role has no `INSERT`/`UPDATE`/`DELETE` privilege on `identifier_schemes`.
8. Every tenant-owned persistence operation is tenant-qualified.
9. Business mutation and required outbox insertion commit atomically.
10. Plaintext identifiers and keys never enter logs/traces/metrics/events/ordinary responses/persistence.
11. Blocking work on reactive paths requires bounded isolation and tests.
12. No public outbox CRUD or cross-service database access/FK.
13. No command-level idempotency mechanism is introduced.
14. Identifier uniqueness remains permanent per tenant+Scheme+normalized value.
15. Runtime secrets remain outside Git/image.
16. V1 contains no login/authentication/authorization/roles.
17. Enterprise logs use `processId`, `userId`, `tenantId` and `[LOCATION] message`.
18. `likec4 validate` is mandatory architecture evidence.
19. Database-managed Scheme changes create no Party Registry tenant business event.
20. A future runtime Scheme administration feature requires a new product/security/API/database decision and ADR.

## 21. Risks and Pending Decisions

| ID | Risk | Treatment |
|---|---|---|
| AR-002 | Internal callers can assert tenant/user and invoke decryption. | Preserve approved internal-only V1 posture; security/production gates evaluate residual risk. |
| AR-003 | No legal purge/retention policy. | Preserve no-purge technical behavior; future governance decision required. |
| AR-004 | Existing CI/deployment may conflict with approved immutable/restricted promotion. | Later release/deployment remediation and gates. |
| AR-005 | RabbitMQ outage/unknown outcomes cause backlog/duplicates. | Same-ID retry, confirms, metrics, recovery, consumer dedupe. |
| AR-006 | Mandatory secret/logging/Geographic failures deny affected operations. | Fail closed, readiness/cache rules and operational evidence. |
| AR-007 | Exact pagination contract parameters lack authoritative source per RG-401. | Resolve/defer at API-contract gate. |
| AR-008 | DBML/PostgreSQL constraints or privileges may fail validation. | Database-contract gate; no automatic DBML changes. |
| AR-009 | Database-managed Scheme data could reference unsupported application rule keys. | Migration/release validation + startup/readiness fail closed; runtime never rewrites catalog. |

The former AR-001 command-idempotency conflict remains resolved by Amendment 001. No new high-risk Scheme-administration conflict remains because Amendment 002 removes that runtime capability.

## 22. ADR Index

| ADR | Decision |
|---|---|
| ADR-001 | Reactive execution in one cohesive deployable. |
| ADR-002 | Local PostgreSQL transaction + transactional outbox + confirmed at-least-once publication. |
| ADR-003 | Runtime-secret-backed identifier protection and fail-closed local security logging. |
| ADR-004 | Permanent tenant+Scheme+normalized-value identifier uniqueness. |
| ADR-005 | Identifier Schemes are database-managed, runtime read-only reference data. |

## 23. Implementation Planning Handoff

Implementation planning may create work for Party, Party Identifier, outbox, Geographic Reference, crypto, local logging and cross-cutting runtime concerns.

It MUST NOT create tasks for a Scheme Administration REST resource/use case. Instead it must include:

- a read-only Identifier Scheme catalog output port;
- PostgreSQL read mapping for Scheme metadata;
- runtime DB privilege validation prohibiting Scheme writes;
- readiness tests for unsupported persisted Scheme implementation keys;
- migration/database-governance tests for Scheme catalog changes.

Persistence implementation/migrations remain subject to the database-contract gate and human DBML governance.

## 24. Internal Consistency Review

- LikeC4 has no Identifier Scheme Administrator actor.
- LikeC4 has no Scheme Administration Capability.
- Party Registry runtime has read-only Scheme catalog access only.
- Scheme changes belong to database migration/administration governance.
- Database Scheme changes are not tenant Party business events.
- Permanent identifier uniqueness remains tenant+Scheme+normalized value.
- No command-level idempotency exists.
- No external logging platform exists.
- No V1 authentication/authorization exists.
- DBML remains authoritative and no table is inferred into an API merely because it exists.
- Architecture remains Proposed pending independent gate evaluation.
