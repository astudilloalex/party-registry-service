# Party Registry Service V1 — Solution Architecture

## 1. Document Control

| Field | Value |
|---|---|
| Status | Proposed |
| Approval | Pending `clean-architecture-gate-agent` |
| Architecture identifier | PRS-ARCH-001 |
| Version | 0.2 |
| Author | `solution-architecture-agent`, corrected by architecture review |
| Requirements baseline | PRS-REQ-001 v0.4, independently gated by `.factory/runs/oc-409818fa-64f6-430b-88eb-be8e97ddcd1b/result.json` |
| Persistence authority | `docs/database/v1-scheme.dbml` (unchanged) |
| Architecture model | `architecture/model.c4` |

This proposal does not approve architecture, modify workflow state, modify the DBML, or authorize implementation. Material statements use the classifications defined below.

## 2. Classification and Source Precedence

| Classification | Meaning in this document |
|---|---|
| **CONFIRMED REQUIREMENT (CR)** | Approved requirements, manifest, recorded human decision, mandatory profile, or confirmed contract. |
| **EXISTING FACT (EF)** | Verified current-repository evidence; not automatically a TO-BE requirement. |
| **ARCHITECTURE CONSTRAINT (AC)** | Mandatory boundary derived from a confirmed source. |
| **TO-BE DECISION (TD)** | Proposed architecture choice pending the independent architecture gate. |
| **DESIGN ASSUMPTION (DA)** | Low-risk temporary assumption with explicit validation and consequence. |
| **PENDING DECISION (PD)** | Choice requiring an authority outside this agent. |
| **OUT OF SCOPE (OS)** | Explicit exclusion. |

Source precedence follows recorded human decisions, the factory invocation, `.factory/project.yaml`, the approved requirements package and gate, the DBML for physical persistence, then profiles and verified repository facts. The requirements-gate finding about BR-029 is preserved rather than silently resolved.

The architecture-review corrections in version 0.2 clarify only deployment DSL, approved secret-injection mechanisms, logging conventions and terminology. They do not change business requirements, persistence design or the human decision still required by AR-001.

## 3. Sources and Traceability

| Source | Architecture use |
|---|---|
| `.factory/project.yaml` | AC: backend service, strict Clean Architecture, generated LikeC4, Java 25/Quarkus, PostgreSQL DBML authority, VPS/Podman/Quadlet, environments and immutable governance. |
| `.factory/decisions.json` | CR: approved lifecycle, V1 trust posture, decryption logging, masking, idempotency, rule-catalog and outbox decisions. |
| `docs/requirements/requirements-specification.md` | CR: business, functional, data, integration, security, NFR, compatibility and operational requirements. |
| `.factory/runs/oc-409818fa-64f6-430b-88eb-be8e97ddcd1b/result.json` | EF: independent requirements-gate PASS retaining RG-401/RG-402. |
| `docs/database/v1-scheme.dbml` | CR for physical persistence facts only; no physical design is added here. |
| Supplied architecture/stack/persistence/deployment profiles | AC: inward dependencies, required LikeC4 views, reactive isolation, PostgreSQL controls, rootless Quadlet and immutable digest promotion. |
| `astudilloalex/geographic-reference-service` main | AC from explicit human direction: enterprise logging baseline represented by its `MDCRequestFilter`, `LogUtil` message convention and `application.properties` console format, adapted from `companyId` to Party Registry `tenantId`. |
| LikeC4 deployment DSL and CLI | AC: deployment nodes belong in `deployment {}`; deployed logical elements use `instanceOf`; architecture validation uses `likec4 validate`. |

### 3.1 Requirement-to-architecture map

| Requirement groups | Boundary / responsibility | Controls and verification |
|---|---|---|
| Party lifecycle and tenant operations | Party capability | Domain invariants, trusted request context, tenant-qualified ports, local transactions, optimistic concurrency. |
| Official identifiers | Identifier capability | Rule catalog, authenticated encryption, tenant-effective HMAC, exact lookup, masking, fail-closed security logging. |
| Identifier schemes | Scheme administration capability | Global scheme aggregate, code-owned normalizer/validator catalog, readiness checks, no tenant outbox event. |
| Integration events | Event publication capability | Transactional outbox, publisher confirms, same-ID retry/recovery, consumer dedupe. |
| Geographic references | Geographic adapter/cache | Reactive provider boundary, bounded in-memory cache, approved stale-data behavior. |
| Security/privacy | Request context, protection and logging boundaries | No V1 authentication/authorization, tenant predicates, secret separation, plaintext minimisation, structured decryption log. |
| Runtime/NFR/operations | Runtime composition | Reactive execution, bounded resources, health, telemetry, backup/recovery and immutable promotion. |
| Persistence governance | Database-contract handoff | DBML remains exclusive physical authority; validation precedes persistence implementation or migrations. |

## 4. Scope and System Boundary

### 4.1 Owned capability

- **CR:** Party Registry is the tenant-scoped system of record for Parties, matching natural-person/legal-entity details, natural-person nationalities, official identifiers, identifier schemes, and tenant business-event outbox state.
- **TD:** These responsibilities remain one bounded context and one application deployable. Clean Architecture layers are logical boundaries, not independently deployed services.
- **CR:** Other systems keep `party_id` as an opaque reference and never access the Party Registry database directly.

### 4.2 Actors and external systems

| Element | Classification | Responsibility / relationship |
|---|---|---|
| Internal service consumer | CR | Submits trusted tenant/user/process context and V1 reactive REST commands/queries. |
| Identifier-scheme administrator consumer | CR | Maintains the global identifier-scheme catalog through the same internal API; Party Registry performs no role check in V1. |
| Geographic Reference Service | CR | Owns active ISO country reference data. |
| RabbitMQ | CR | Accepts persistent versioned events with publisher confirms; consumer queues and DLQs are externally owned. |
| Event consumer | CR | Consumes at-least-once events and deduplicates by event ID. |
| Platform logging capability | CR | Collects structured operational and decryption security logs; centralized storage/retention are operations-owned. |
| Keycloak / Access Management Service | OS | Authentication and subject-to-Party association owners; not invoked in V1 request processing. |

Runtime secret injection is **not** an external software system. Encryption/HMAC masters are deployment configuration supplied through approved `.env`/Podman secret mechanisms and consumed locally by the application.

### 4.3 Explicit exclusions

**OS:** Customers, suppliers, employees, accounts, authentication, authorization, operating organizations, addresses, contacts, subscriptions, tax configuration, ownership structures, beneficial ownership, corporate relationships, audit storage, consumer queues/DLQs, geographic ownership and a V1 remote key-management service remain outside Party Registry.

## 5. AS-IS Summary and Migration Constraints

| Type | Evidence-backed statement |
|---|---|
| EF | No functional Java application implementation or migrations exist yet for the Party Registry behavior represented by this architecture. |
| EF | Existing CI/deployment material predates the approved factory deployment model and contains cross-service contamination. |
| AC | Existing CI/deployment behavior is not an approved TO-BE contract and must not bypass immutable digest promotion or restricted deployment interfaces. |
| AC | No persistence adapter or migration may be implemented before DBML validation. Forward-only rollout and roll-forward recovery are mandatory; database rollback must not be assumed. |

This is greenfield application behavior over a confirmed data contract, not a replacement of an operating Party Registry.

## 6. Architecture Drivers and Selected Architecture

1. **CR:** tenant isolation and restricted identifier protection outrank convenience and delivery speed.
2. **CR:** mutation plus required outbox event is a local atomic consistency boundary.
3. **AC:** domain and application logic remain framework independent with dependencies pointing inward.
4. **CR:** REST is reactive; hidden blocking work is prohibited on event-loop paths.
5. **AC:** physical persistence is exclusively governed by the DBML.
6. **CR:** pilot load and recovery targets must be demonstrated, not replaced with invented capacity claims.
7. **TD:** use one cohesive Quarkus deployable containing REST inbound adapters and an internal background outbox publisher.
8. **TD:** PostgreSQL is service-owned persistence; RabbitMQ, Geographic Reference and platform logging remain external dependencies.
9. **AC:** encryption/HMAC master keys are injected as runtime secrets, not fetched from an invented network key service.
10. **TD:** do not add CQRS, event sourcing, distributed transactions, additional microservices or caches beyond the approved geographic cache without measured need and a new ADR.

### 6.1 Alternatives

| Decision | Selected option and reason | Rejected option and consequence |
|---|---|---|
| Runtime decomposition | One service deployable | Separate API/publisher deployables add operational coupling while sharing the same database. |
| Execution model | End-to-end reactive API, persistence and integrations, with bounded isolation only for unavoidable CPU/blocking work | Blocking-by-default conflicts with the approved runtime requirement. |
| Integration reliability | Local PostgreSQL transaction + transactional outbox + confirmed at-least-once publication | Direct dual write or distributed transaction creates unnecessary failure modes. |
| Geographic lookup | Reactive lookup with approved bounded in-memory cache | Durable local replica changes data ownership. |
| Identifier lookup | Tenant-effective HMAC exact lookup | Decryption scan or unkeyed hash violates protection requirements. |
| Key supply | Runtime `.env`/Podman secret injection | Invented V1 network KMS/key service adds an unapproved integration. |

## 7. Domain and Capability Boundaries

| Capability | Classification | Responsibilities and invariants |
|---|---|---|
| Party Management | TD | Party lifecycle, immutable type/ownership, matching detail, nationality rules, aggregate version, logical archive. |
| Official Identifier Management | TD | Identifier lifecycle, Party/Scheme compatibility, normalized validation, active uniqueness, immutable identifier association, masking and protected representation. |
| Identifier Scheme Administration | TD | Global catalog lifecycle, activation immutability and supported implementation-key references. |
| Integration Event Publication | TD | Record approved events atomically and deliver unchanged event identity at least once. |
| Request Context | TD | Validate canonical tenant/process context and user ID at the inbound boundary; it authenticates nothing. |

**CR:** Framework entities, REST DTOs, persistence entities, provider DTOs, event schemas and security-log representations are not domain entities.

## 8. Strict Clean Architecture

### 8.1 Logical areas and dependency rules

| Area | Owned responsibilities | Forbidden dependencies |
|---|---|---|
| Domain | Entities/value objects, lifecycle policies, invariants, domain errors and domain facts. | Quarkus, Jakarta, CDI, REST, JSON, logging, ORM/Panache, database, messaging, configuration, crypto/provider APIs. |
| Application | Commands/queries, input ports, use cases, transaction intent, tenant scoping, idempotency orchestration, output ports, boundary-neutral results/errors. | Concrete adapters, transport/persistence/provider DTOs, Quarkus APIs. |
| Inbound adapters | Reactive REST parsing, headers, transport validation, OpenAPI DTO mapping, ETag/error/envelope mapping. | Business-rule ownership or direct database/client access. |
| Outbound adapters | PostgreSQL mapping, Geographic client/cache, runtime-secret/crypto, RabbitMQ publication, structured security logging and clock. | Redefining domain rules or exposing adapter models inward. |
| Bootstrap | Quarkus wiring, configuration, adapter selection, startup/readiness and telemetry registration. | Business decisions. |

Dependency direction is `inbound adapters -> application -> domain`, while outbound adapters implement application output ports. Domain and application must not import concrete logging, secret-management, transport or persistence technology.

### 8.2 Port boundaries

| Port family | Direction | Contract responsibility |
|---|---|---|
| Party, Identifier, Scheme use-case ports | Inbound | Boundary-neutral commands/queries and outcomes. |
| Tenant-scoped state ports | Outbound | Query/mutate only through tenant-qualified operations; expose no persistence entity. |
| Unit-of-work / outbox recording port | Outbound | Commit aggregate mutation and required outbox event atomically in PostgreSQL. |
| Party-creation idempotency port | Outbound | Claim tenant+operation+key, compare effective-request identity and replay committed result. **PD:** no DBML-governed durable implementation exists. |
| Geographic reference port | Outbound | Resolve active country codes with explicit available/unavailable outcomes. |
| Identifier protection port | Outbound | Authenticated encrypt/decrypt, tenant-effective HMAC, key-version/readiness outcomes using runtime-injected secrets. |
| Integration event publisher port | Outbound | Publish persistent versioned events and classify confirmation outcomes without changing event identity. |
| Decryption security-log port | Outbound | Acknowledge mandatory non-plaintext log emission before plaintext crosses the application boundary. |
| Clock port | Outbound | Supply testable current time; no automatic lifecycle scheduler is implied. |

### 8.3 Model and package guardrails

Start with one Gradle module and enforce boundaries with architecture tests. Transport DTOs, application inputs/results, domain models, persistence rows/entities, provider DTOs, event schemas and security-log representations remain distinct. Mapping is explicit at each boundary.

## 9. Runtime Containers and Components

| C4 container | Status | Responsibility | Owned data / trust / deployment |
|---|---|---|---|
| Party Registry Application | Proposed | Reactive internal REST API, use cases, background outbox publication, health and telemetry. | One rootless Podman/Quadlet deployment unit. |
| Party Registry PostgreSQL Database | Confirmed engine/ownership; physical contract pending validation | Service-owned Party Registry business state and outbox. | Accessed only by Party Registry runtime/migration roles. |
| Geographic Cache | In-process component | Bounded non-durable active-country cache. | No system-of-record ownership; lost on restart. |

Stable application components are the Reactive REST Adapter, Party Application Capability, Identifier Application Capability, Scheme Administration Capability, Outbox Publication Capability, PostgreSQL Adapter, Geographic Reference Adapter, Identifier Protection Adapter, RabbitMQ Publisher Adapter, Decryption Security Log Adapter and Runtime Composition/Readiness.

## 10. Integration and Contract Architecture

| Integration | Contract and ownership | Failure, security, compatibility |
|---|---|---|
| Internal REST | Consumer -> Party Registry; downstream OpenAPI under `/api/v1` is provider contract source of truth. | Trusted context, bounded validation, stable envelope/errors, ETag/If-Match, no V1 auth/authz. |
| Geographic Reference | Party Registry -> provider through anti-corruption adapter/cache. | Reactive timeout and approved cache/stale behavior; no confidential payload logging. |
| RabbitMQ events | Party Registry -> exchange -> consumer-owned queues. | Persistent message, publisher confirm, at-least-once, event-ID dedupe, minimized payload. |
| Platform logging | Party Registry -> operations logging capability. | Company-standard operational logs plus fail-closed decryption security logs; no plaintext. |
| Runtime secrets | Deployment configuration -> Identifier Protection Adapter. This is not a network integration. | Separate encryption/HMAC masters; `.env`/Podman secret injection; fail-closed missing/invalid secret handling; no secret telemetry. |

**AC:** detailed API/event/provider contracts remain downstream contract artifacts. Exact OpenAPI payloads and event fields are not invented here.

**PD (nonblocking for architecture):** RG-401 identifies that BR-029's exact pagination defaults, limits, filters, order and error parameters lack authorized product-source traceability. API-contract approval must obtain an authorized technical-contract decision or explicitly defer those values while preserving tenant isolation, bounded pagination, deterministic ordering and stable validation semantics.

## 11. Data Architecture and DBML Authority

- **CR:** Party Registry owns in-scope Party records; Geographic Reference owns countries; operations owns centralized decryption-log retention.
- **CR:** complete identifiers are restricted; UUIDs, tenant IDs and masked values are internal.
- **AC:** `docs/database/v1-scheme.dbml` is the exclusive authority for PostgreSQL tables, columns, relationships, constraints and indexes.
- **TD:** persistence adapters map DBML records to domain/application models and expose tenant-qualified access only.
- **CR:** no physical deletion or automatic purge occurs in V1.
- **AC:** database-contract validation must verify cross-table invariants, active uniqueness, tenant-qualified references, optimistic versions, outbox checks and PostgreSQL 18 compatibility against the DBML.

### 11.1 Database contract handoff

Database validation must answer, without editing DBML:

1. Can the DBML enforce declared tenant/cardinality/type/detail/active-uniqueness and outbox invariants on PostgreSQL 18?
2. Does it support tenant-qualified queries and optimistic update predicates?
3. Can Party component updates, aggregate version and required outbox effects commit in one local transaction?
4. **High-risk:** where is the durable tenant+operation+Idempotency-Key claim, request identity and replay result required by BR-026 stored?
5. What forward-compatible expand-and-contract sequencing is required by eventual migrations?

## 12. Transaction, Consistency, and Idempotency

| Flow | Transaction / consistency | Failure and retry behavior |
|---|---|---|
| Party creation | Party, initial data, creation outbox event and durable idempotency outcome must converge in one local atomic boundary. | Same key/request replays original success; different request returns 409; concurrent duplicates converge. **PD:** persistence unsupported by current DBML. |
| Party/detail/nationality mutation | One local PostgreSQL transaction updates component, Party version, audit facts and required outbox event. | If-Match/invariant failures commit nothing. |
| Identifier mutation | One local transaction preserves tenant/scheme/Party consistency, uniqueness, version, audit and outbox event. | Concurrency/constraint failure commits nothing; plaintext never persists. |
| Scheme mutation | One local transaction changes global scheme only. | Unsupported implementation keys fail unchanged. |
| Outbox delivery | Publisher claims eligible rows, publishes outside the business transaction and updates delivery state after outcome. | Confirm -> PUBLISHED; transient/unknown -> same-ID retry eligibility; non-recoverable -> FAILED; authorized recovery preserves ID. |
| Exact search | Tenant-qualified HMAC lookup; no decrypt/scan. | Missing/invalid secret fails without state change. |
| Decryption | Tenant-qualified read -> ciphertext authentication/tag verification -> decrypt -> acknowledged security log -> plaintext response. | Any lookup, secret, ciphertext-authentication or logging failure returns no plaintext. |

No distributed transaction, saga or compensation is needed.

### 12.1 High-risk persistence conflict

**PD:** BR-026/FR-047 require durable replay and concurrent one-result convergence. `docs/database/v1-scheme.dbml` defines no idempotency record or equivalent fields. In-memory state cannot survive restart or atomically commit with Party/outbox state; RabbitMQ is post-commit and cannot own command replay. Adding an ungoverned table, cache or database would violate DBML authority.

Required human/database authority must choose one of:

1. authorize a DBML revision modeling service-owned durable idempotency state and revalidate it;
2. supply an already approved durable idempotency system with the required ownership/atomicity contract; or
3. explicitly revise BR-026/FR-047 semantics.

Recommended default remains option 1 because it preserves local ACID consistency and clear ownership. This architecture does not make that decision or alter the DBML.

## 13. Security and Privacy Architecture

### 13.1 Trust boundaries and assets

| Boundary / asset | Controls |
|---|---|
| Internal consumer -> nginx/service | Internal network only; canonical context validation; request bounds; no V1 authentication/authorization. |
| Service -> PostgreSQL | Tenant predicates; least-privilege runtime role; TLS when crossing a trust boundary; no direct external access. |
| Service -> external systems | Explicit adapters, timeouts, non-sensitive errors and correlation without sensitive payloads. |
| Restricted plaintext | Transient only for approved operations; never logged, traced, measured, persisted or emitted. |
| Runtime secrets | Separate versioned encryption/HMAC masters injected through approved `.env`/Podman mechanisms; never in Git/image/logs/traces/metrics; missing/invalid secrets fail closed. |

### 13.2 Threat controls

- **CR:** tenant confusion is mitigated by tenant-qualified repository methods and 404 non-disclosure, not authorization.
- **CR:** injection is mitigated by typed validation and parameterized persistence.
- **CR:** replay is controlled for Party creation by the pending durable idempotency boundary and for events by stable event ID.
- **TD:** resource exhaustion is controlled by bounded pagination/payloads, reactive backpressure/concurrency, connection pools, dependency timeouts and outbox batches.
- **CR:** decryption logs tenant ID, user ID, process ID, identifier ID, timestamp and outcome before plaintext return; no Party Registry audit store is added.
- **CR:** responses reveal no stack trace, SQL error, hostname, credential, token, plaintext identifier or unapproved personal data.

**High residual risk preserved:** any consumer with internal connectivity can assert tenant/user context and request decryption. This is explicit V1 scope. Any public exposure or authentication-policy change requires new requirements and architecture decisions.

## 14. Resilience, Recovery, Performance, and Capacity

- Geographic calls use reactive timeout handling and only approved cache freshness branches.
- RabbitMQ attempts use bounded timeout/concurrency and configurable backoff/jitter while preserving no automatic discard/publisher DLQ policy.
- Connection pools, REST concurrency and outbox batches are bounded and tuned through performance evidence rather than invented values.
- PostgreSQL backup/recovery requirements remain operational constraints.
- Readiness fails for missing/invalid mandatory runtime secrets, unsupported active rule configuration, unavailable mandatory persistence and startup failure.
- Transient RabbitMQ outage is represented by dependency/lag signals, not restart loops.
- No failover, multi-region, distributed cache or horizontal scaling topology is introduced without measured need and an ADR.

## 15. Observability and Auditability

### 15.1 Enterprise logging baseline

Party Registry **MUST** preserve the AlexAstudillo enterprise logging convention already used by `geographic-reference-service`, adapted to Party Registry terminology:

```properties
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [user=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```

The request logging boundary owns MDC population and cleanup:

- `processId`: canonical request correlation identifier; generated when `process-id` is absent according to the approved request-context rule.
- `userId`: trusted caller-supplied opaque actor identifier, maximum 128 characters.
- `tenantId`: canonical UUID derived from `tenant-id`; this replaces the Geographic Reference `companyId` field because Party Registry's authoritative term is tenant.
- response propagation returns `process-id` where required by the API contract.

Application log messages follow the enterprise `[LOCATION] message` convention. The logging utility/filter implementation belongs in API/infrastructure/bootstrap concerns. Domain and application code **MUST NOT** import MDC, SLF4J, Quarkus logging APIs or concrete logging adapters.

### 15.2 Signals

| Signal | Required evidence and restrictions |
|---|---|
| Structured operational logs | Process/user/tenant context, stable error class, dependency/outbox state; no plaintext, keys or confidential payloads. |
| Decryption security log | `user-id`, tenant, process, Party Identifier ID, timestamp, action/outcome; emission acknowledged before plaintext return; centralized retention external. |
| Metrics | Request latency/error class, PostgreSQL pool, outbox age/status/attempts, RabbitMQ outcomes/lag, geographic cache, readiness; no tenant/identifier/personal labels. |
| Traces | Correlation across requests/dependencies without confidential bodies. |
| Health | Startup, liveness and readiness with non-sensitive reason categories. |
| Alerts/runbooks | Aged/FAILED outbox, secret/readiness failure, geographic cache exhaustion, pool saturation, backup/restore failures. |

Logs are diagnostic/security evidence, not a substitute for DBML audit fields or outbox state.

## 16. Deployment Architecture

- **AC:** deploy Party Registry as a non-privileged rootless Podman container managed by version-controlled Quadlet.
- **AC:** bind the application to loopback; nginx is the reverse-proxy boundary and must not expose the service publicly.
- **AC:** runtime secrets are deployment configuration, not a logical external software system.
- **AC:** local/development may use an uncommitted `.env` file for secret injection. VPS runtime may use Podman secrets or a protected host-only environment file. Secret values never enter Git or the OCI artifact; host-side secret files use least-privilege filesystem permissions.
- **TD:** PostgreSQL, RabbitMQ, Geographic Reference and logging are represented as operations-managed/external deployment boundaries without inventing physical co-location or HA topology.
- **AC:** non-secret configuration is externalized; resource/restart limits and database ownership/backup controls are preserved.
- **AC:** local, development, staging and production use environment-specific configuration outside the OCI artifact.
- **AC:** the same immutable OCI digest moves development -> staging -> production; production promotion never rebuilds the artifact.
- **AC:** deployment uses restricted wrappers rather than unrestricted SSH shell; database changes are forward-only.

`architecture/model.c4` contains logical context/container/component views plus a corrected physical `deployment {}` model. The deployment model uses deployment-node kinds and `instanceOf` according to LikeC4 deployment semantics.

## 17. Database Deployment Sequencing

After DBML validation, migration design must define expand-and-contract sequencing where required, migration/application ordering, lock/downtime analysis, backfill verification, roll-forward remediation and manual stop points. No migration is presumed reversible. AR-001 must be resolved before a release can claim Party-creation idempotency compliance.

## 18. Verification Strategy

| Layer | Required checks |
|---|---|
| Domain unit | Lifecycle matrices, type/detail/date rules, identifier compatibility, masking, mutability and rule-catalog policies. |
| Application use case | Tenant propagation, transaction intent, idempotency orchestration, If-Match outcomes, security-log-before-return decryption. |
| Architecture dependency | Domain imports no framework/logging/configuration; application imports no concrete adapter; adapter direction remains inward. |
| LikeC4 | `likec4 validate` must succeed; `likec4 format --check` should be used in CI when the CLI is available. Manual review alone is insufficient evidence. |
| Adapter integration | PostgreSQL atomicity, Geographic cache/failures, runtime-secret/crypto faults, security-log emission failure, RabbitMQ confirms/restart. |
| Contract | OpenAPI context/ETag/error compatibility, minimized event contracts, provider contracts. |
| Database/migration | DBML semantic validation, constraints/triggers/indexes, transaction behavior and forward-only migration compatibility. |
| Security/privacy | Tenant isolation, no plaintext leakage, HMAC separation, encryption/secret separation, no-store and logging minimisation. |
| Performance/reliability | Approved load/latency/error/lag targets, saturation and publisher recovery. |
| Deployment/production | Digest identity, rootless/loopback posture, runtime-secret injection, health/readiness, smoke/stabilization and backup/restore. |

## 19. Architecture Guardrails

1. Domain packages must not import Quarkus, Jakarta, CDI, REST, JSON, ORM, database, messaging, logging, configuration or crypto-provider APIs.
2. Application packages may depend only on domain and boundary-neutral ports; they must not import concrete adapters.
3. Inbound adapters call application input ports only; outbound adapters implement application output ports.
4. Every tenant-owned persistence operation requires tenant ID in its port signature/predicate; no unscoped repository method is allowed.
5. Transport, application, domain, persistence, provider, event and security-log models remain distinct.
6. Business logic must not live in REST resources, repositories, mappers, publisher loops or deployment scripts.
7. Mutation and required outbox insertion commit atomically; direct business-event publication from use cases is forbidden.
8. Plaintext identifiers and keys never enter logs, traces, metrics, events, ordinary responses or persistence.
9. Blocking calls on reactive paths are forbidden; unavoidable blocking/CPU work requires bounded isolation and tests.
10. No public outbox CRUD, direct consumer DB access, cross-service FK, shared database write or frontend DB access.
11. Retries are bounded per attempt and cannot duplicate irreversible effects or change event identity.
12. Public contracts are versioned and contract-first.
13. DBML physical facts cannot be added/altered by implementation; database changes require human authority and validation.
14. The same OCI digest is promoted across environments.
15. V1 implements no login, authentication, authorization, role model or Keycloak request dependency.
16. Encryption/HMAC keys are supplied only by approved runtime secret injection; no invented V1 network key service.
17. Enterprise logs use `processId`, `userId`, `tenantId` MDC context and `[LOCATION]` message convention; domain/application remain logging-framework free.
18. `likec4 validate` is mandatory architecture-model evidence before architecture approval.

## 20. Risks and Pending Decisions

| ID | Classification | Trigger / impact | Mitigation / gate / residual risk |
|---|---|---|---|
| AR-001 | PD — High data integrity | Durable Party-creation idempotency has no DBML representation. | Human/database decision before persistence validation completion; residual High. |
| AR-002 | CR risk — High security/privacy | Trusted internal caller can assert tenant and decrypt restricted data. | Internal-only network, tenant predicates, no-store, security logging, security/production gates. |
| AR-003 | CR risk — High compliance | No purge/legal retention policy. | Data minimisation; compliance decision before policy change/production approval. |
| AR-004 | AC risk — High deployment | Existing CI conflicts with restricted deployment/immutable promotion. | Later release/deployment remediation and gates. |
| AR-005 | CR risk — Medium integration | RabbitMQ outage/unknown outcome creates duplicates/backlog. | Same-ID retry, confirms, metrics, recovery, consumer dedupe. |
| AR-006 | CR risk — Medium availability | Missing/invalid runtime secrets, logging failure or Geographic failure denies affected operations. | Fail closed, stale-cache rules, readiness and alerts. |
| AR-007 | PD — Medium contract | Exact BR-029 parameters lack authorized source per RG-401. | Authorized API-contract decision or explicit deferral before API approval. |
| AR-008 | AC risk — High database | DBML may not validate against PostgreSQL 18. | Database-contract gate; no automatic DBML edits. |

No low-risk design assumption decides business behavior, security policy, data ownership, public contracts, persistence design, topology, cutover or rollback.

## 21. ADR Index

| ADR | Status | Decision |
|---|---|---|
| `ADR-001-reactive-single-deployable.md` | Proposed | End-to-end reactive execution in one cohesive application deployable. |
| `ADR-002-transactional-outbox.md` | Proposed | Local transaction plus DBML-governed outbox and confirmed at-least-once publication. |
| `ADR-003-sensitive-identifier-boundary.md` | Proposed | Runtime-secret-backed protection/HMAC, exact search and fail-closed enterprise security logging. |

## 22. Implementation Planning Handoff

Planning may decompose work by Party, Identifier, Scheme, Event Publication and cross-cutting adapter capabilities, but must not invent final API/event schemas, persistence facts, dependencies or migrations. Persistence-related implementation remains blocked until database validation, and Party creation remains blocked until AR-001 receives authoritative resolution.

Downstream plans must include architecture tests, LikeC4 validation, enterprise logging/MDC tests, runtime-secret failure tests, all verification layers in section 18, deployment-profile remediation and immutable digest evidence.

## 23. Internal Consistency Review

- Scope and external ownership match requirements and DBML.
- V1 contains no authentication/authorization request dependency.
- LikeC4 context contains actors/system/external systems; deployment configuration is modeled only in the deployment model.
- The deployment model uses `deployment {}` and `instanceOf` rather than treating deployment nodes as logical elements.
- One application deployable is not confused with Clean Architecture layers.
- Runtime `.env`/Podman secret injection is not modeled as a network key-management service.
- Enterprise logging follows the Geographic Reference baseline with `tenantId` replacing `companyId` for Party Registry.
- Integration direction, ownership, transactions, retry/idempotency and outbox state are consistent across narrative, ADRs and LikeC4.
- DBML authority is preserved; AR-001 remains visible and unsolved by the architecture.
- RG-401 remains a downstream API-contract authority issue rather than an invented requirement.
- Architecture remains Proposed and pending independent review.
