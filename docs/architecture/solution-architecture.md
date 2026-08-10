# Party Registry Service V1 — Solution Architecture

## 1. Document Control

| Field | Value |
|---|---|
| Status | Proposed |
| Approval | Pending `clean-architecture-gate-agent` |
| Architecture identifier | PRS-ARCH-001 |
| Version | 0.1 |
| Author | `solution-architecture-agent` |
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

## 3. Sources and Traceability

| Source | Architecture use |
|---|---|
| `.factory/project.yaml:3-44` | AC: backend service, strict Clean Architecture, generated LikeC4, Java 25/Quarkus, reactive decision policy, PostgreSQL DBML authority, VPS/Podman/Quadlet, environments, immutable governance. |
| `.factory/decisions.json:2-65` | CR: approved V1 lifecycle, sensitive-data, idempotency, rule-catalog, and outbox decisions. |
| `docs/requirements/requirements-specification.md:24-281` | CR: scope, actors, rules, FR/VR/DR/IR/SR/NFR/CR/OR requirements. |
| `docs/requirements/requirements-specification.md:283-365` | CR: acceptance criteria and observable boundaries. |
| `docs/requirements/requirements-specification.md:585-604` | CR: upstream risk register and required downstream controls. |
| `.factory/runs/oc-409818fa-64f6-430b-88eb-be8e97ddcd1b/result.json` | EF: independent requirements gate passed the exact requirements baseline and retained RG-401/RG-402 findings. |
| `docs/database/v1-scheme.dbml:1-410` | CR for confirmed physical persistence facts only; no physical design is added here. |
| Supplied architecture/stack/persistence/deployment profiles | AC: inward dependencies, required LikeC4 views, reactive-call isolation, PostgreSQL controls, rootless Quadlet and immutable digest promotion. |

### 3.1 Requirement-to-architecture map

| Requirement groups | Boundary / responsibility | Controls and verification |
|---|---|---|
| FR-001..004, 010..012, 016..017, 021..033; BR-001..006, 011..023 | Party capability and tenant-scoped use cases | Domain invariants; trusted request context; tenant-qualified ports; local transactions; optimistic concurrency; API/domain/database integration tests. |
| FR-005..006, 013..014, 018, 034..039, 046, 048..049; BR-007..010, 021..027 | Official Identifier capability | Rule catalog, crypto and key ports, exact-HMAC lookup, masking, fail-closed audit emission, no-store decryption, security tests. |
| FR-019, 040..045, 048..049 | Identifier Scheme Administration capability | Global scheme aggregate, code-owned implementation catalog, no tenant outbox events, readiness checks, contract/release tests. |
| FR-008..009, 020, 050; BR-013..014, 028; IR-002..004 | Integration Event Publication capability | Transactional outbox, confirmed at-least-once publisher, same-ID retry/recovery, no public outbox API, event contract/integration tests. |
| FR-015; BR-004/019; IR-001 | Geographic Reference validation | Outbound port and bounded in-memory cache; fail-unchanged 503 branch; dependency fault tests. |
| SR-001..007; DR-007 | Trust, privacy, and data protection | Internal-only ingress, tenant predicates, key separation, plaintext minimisation, structured decryption event, security gate. |
| NFR-001..011; OR-001..009 | Runtime, performance, recovery, operations, deployment | Reactive model, one deployable, health signals, metrics, backups/restore evidence, immutable promotion, performance/resilience/deployment tests. |
| DR-008..009; CR-005 | Persistence governance | DBML remains exclusive physical authority; database-contract validation precedes persistence implementation or migration generation. |

## 4. Scope and System Boundary

### 4.1 Owned capability

- **CR:** Party Registry is the tenant-scoped system of record for Parties, matching natural-person/legal-entity details, natural-person nationalities, official identifiers, identifier schemes, and tenant business-event outbox state.
- **TD:** These responsibilities remain one bounded context and one application deployable. Clean Architecture layers are logical boundaries, not independently deployed services.
- **CR:** Other systems keep `party_id` as an opaque reference and never access the service database directly.

### 4.2 Actors and external systems

| Element | Classification | Responsibility / relationship |
|---|---|---|
| Internal service consumer | CR | Submits trusted tenant/user/process context and V1 reactive REST commands/queries. |
| Identifier-scheme administrator consumer | CR | Maintains the global scheme catalog through the same internal API; Party Registry performs no role check in V1. |
| Geographic Reference Service | CR | Owns active ISO country reference data. |
| RabbitMQ | CR | Accepts persistent versioned events with publisher confirms; consumer queues and DLQs are externally owned. |
| Event consumer | CR | Consumes at-least-once events and deduplicates by event ID. |
| VPS key/secret capability | CR | Supplies separate protected versioned encryption and HMAC master keys. |
| Platform logging capability | CR | Receives structured decryption security events; centralized storage and retention are operations-owned. |
| Keycloak / Access Management Service | OS | Authentication and subject-to-Party association owners; not invoked in V1 request processing. |

### 4.3 Explicit exclusions

**OS:** Customers, suppliers, employees, accounts, authentication, authorization, operating organizations, addresses, contacts, subscriptions, tax configuration, ownership structures, beneficial ownership, corporate relationships, audit storage, consumer queues/DLQs, and geographic reference ownership remain outside Party Registry. No direct frontend-to-database access or cross-service database foreign key is permitted.

## 5. AS-IS Summary and Migration Constraints

| Type | Evidence-backed statement |
|---|---|
| EF | No Java application sources or tests exist under `src/main/java` or `src/test`; `build.gradle.kts` contains only Quarkus Arc and test scaffolding. |
| EF | `application.properties` is empty; no runtime integration is configured. |
| EF | No existing API contract, migrations, architecture model, or deployment directory was found. |
| EF | `.github/workflows/ci.yml:42-79,97-152` rebuilds/publishes mutable tags and uses direct SSH/SCP with cross-service file references. |
| AC | Existing CI/deployment behavior is not an approved TO-BE contract and must not be reused to bypass immutable digest promotion or restricted deployment interfaces. |
| AC | No persistence adapter or migration may be implemented before DBML validation. Forward-only rollout and roll-forward recovery are mandatory; database rollback must not be assumed. |

This is greenfield application behavior over a confirmed data contract, not a big-bang replacement of an operating Party Registry. No production coexistence behavior is evidenced.

## 6. Architecture Drivers and Selected Architecture

1. **CR:** tenant isolation and restricted identifier protection outrank convenience and delivery speed.
2. **CR:** mutation plus required outbox event is a local atomic consistency boundary.
3. **AC:** domain and application logic must remain framework independent with dependencies pointing inward.
4. **CR:** REST is reactive; hidden blocking work is prohibited on event-loop paths.
5. **AC:** physical persistence is exclusively governed by the unchanged DBML.
6. **CR:** the pilot load and recovery targets in NFR-008..011 must be demonstrated, not replaced with invented capacity claims.
7. **TD:** use one cohesive Quarkus deployable containing the REST inbound adapter and an internal background outbox publisher. PostgreSQL is the service-owned data container; RabbitMQ and other capabilities remain external dependencies.
8. **TD:** do not add a broker beyond required RabbitMQ, a cache beyond the approved in-memory geographic cache, an API gateway, CQRS, event sourcing, distributed transactions, or additional microservices.

### 6.1 Alternatives

| Decision | Selected option and reason | Rejected option and consequence |
|---|---|---|
| Runtime decomposition | **TD:** one service deployable; ownership, scaling, security, and transaction requirements do not justify multiple application deployables. | Separate API/publisher deployables add release and operational coupling while using the same database; reconsider only with measured independent-scaling or failure-isolation evidence. |
| Execution model | **TD:** end-to-end reactive API, PostgreSQL access, geographic client, and RabbitMQ publication, with bounded worker isolation only for unavoidable CPU/blocking adapters. | Blocking API conflicts with NFR-006; mixed execution by default obscures thread/transaction semantics. |
| Integration reliability | **CR:** DBML-governed local outbox and at-least-once confirmed publication. | Direct publish inside the business transaction creates an unresolvable dual-write failure; distributed transactions are unjustified. |
| Geographic lookup | **CR:** synchronous reactive lookup with bounded in-memory cache and approved stale fallback. | Durable local replica would introduce unapproved data ownership; per-request uncached calls violate the approved cache behavior. |
| Identifier lookup | **CR:** tenant-effective HMAC exact lookup. | Decryption scan or unkeyed hash violates SR-002/003. |

## 7. Domain and Capability Boundaries

### 7.1 Cohesive capabilities

| Capability | Classification | Responsibilities and invariants |
|---|---|---|
| Party Management | TD | Party lifecycle, immutable type/ownership, matching detail, nationality rules, aggregate version, logical archive. Party is the consistency boundary for Party/details/nationalities. |
| Official Identifier Management | TD | Identifier lifecycle, Party/Scheme compatibility, normalized validation, active uniqueness, immutable plaintext association, masking and protected representation. Party Identifier is its own versioned consistency boundary. |
| Identifier Scheme Administration | TD | Global catalog lifecycle, activation immutability, supported rule-key references. Scheme changes never create tenant Party outbox events. |
| Integration Event Publication | TD | Record approved events atomically and deliver unchanged event identity at least once. This is infrastructure responsibility, not a public domain aggregate/API. |
| Request Context | TD | Validate canonical tenant/process context and user ID at the inbound boundary, then carry boundary-neutral context through use cases. It authenticates nothing. |

**CR:** Domain facts include permitted lifecycle transitions, Party type/detail compatibility, date order, nationality active uniqueness, identifier compatibility and status invariants, and masking policy. Framework entities, REST DTOs, persistence entities, provider DTOs, and audit representations are not domain entities.

## 8. Strict Clean Architecture

### 8.1 Logical areas and dependency rules

| Area | Owned responsibilities | Forbidden dependencies |
|---|---|---|
| Domain | Entities/value objects, lifecycle policies, invariants, domain errors, rule-catalog contracts and domain facts. | Quarkus, Jakarta, CDI, REST, JSON, logging, ORM/Panache, database, messaging, configuration, crypto/provider clients. |
| Application | Commands/queries, input ports, use-case orchestration, transaction intent, tenant scoping, idempotency orchestration, output ports, boundary-neutral results/errors. | Concrete adapters, transport/persistence/provider DTOs, Quarkus APIs. |
| Inbound adapters | Reactive REST parsing, headers, transport validation, OpenAPI DTO mapping, error/envelope/ETag mapping; internal operational trigger for failed-event recovery. | Business-rule ownership or direct database/client access. |
| Outbound adapters | PostgreSQL mapping, Geographic client/cache, cryptography/key access, RabbitMQ publication, structured security-event emission, clock. | Redefining domain rules or exposing adapter models inward. |
| Bootstrap | Quarkus wiring, configuration, adapter selection, startup/readiness and cross-cutting telemetry registration. | Business decisions. |

Dependency direction is `inbound adapters -> application -> domain`, `outbound adapters -> application output ports`, and `bootstrap -> concrete runtime elements`. Outbound adapters may use domain value types only where the application port declares them; the domain never imports an adapter.

### 8.2 Port boundaries

| Port family | Direction | Contract responsibility |
|---|---|---|
| Party, Identifier, Scheme use-case ports | Inbound | Boundary-neutral commands/queries and outcomes for approved V1 operations. |
| Tenant-scoped state ports | Outbound | Query/mutate only through tenant-qualified operations; expose no persistence entity. |
| Unit-of-work / outbox recording port | Outbound | Commit aggregate mutation and required outbox event atomically in one PostgreSQL transaction. |
| Party-creation idempotency port | Outbound | Atomically claim tenant+operation+key, compare effective-request identity, and replay the committed result. **PD:** no DBML-governed durable implementation exists; see section 12. |
| Geographic reference port | Outbound | Resolve active country codes with explicit available/unavailable outcomes. |
| Identifier protection and key-capability ports | Outbound | Normalize/validate through code-owned keys, authenticated encrypt/decrypt, tenant-effective HMAC, key-version/readiness outcomes. |
| Integration event publisher port | Outbound | Publish persistent versioned events and report positive/negative/unknown confirmation without changing event identity. |
| Decryption security-event port | Outbound | Acknowledge successful emission of the mandatory non-plaintext event before plaintext can cross the application boundary. |
| Clock port | Outbound | Supply testable current time for date and audit rules; it does not schedule automatic lifecycle transitions. |

### 8.3 Model and package guardrails

**TD:** Organize packages by capability within `domain`, `application`, `adapter.in`, `adapter.out`, and `bootstrap`; avoid repository-wide `controller/service/repository/model/util` and speculative `common/shared/core` packages. Start with one Gradle module; enforce boundaries with architecture tests rather than speculative modules.

Transport DTOs, application inputs/results, domain models, persistence entities, external-provider DTOs, event schemas, and decryption-audit representations remain separate. Mapping is explicit at each boundary.

## 9. Runtime Containers and Components

| C4 container | Status | Responsibility | Owned data / trust / deployment |
|---|---|---|---|
| Party Registry Application | Proposed (Quarkus/Java 25 confirmed) | Reactive internal REST API, use cases, background outbox publication, health and telemetry. | Owns no durable data in process; trusted internal-service ingress; one rootless Podman/Quadlet deployment unit. |
| Party Registry PostgreSQL Database | Confirmed engine and ownership; physical contract pending validation | Service-owned Parties, details, nationalities, identifiers, schemes, outbox. | Restricted data trust boundary; accessed only by Party Registry runtime/migration roles. |
| Geographic Cache | Confirmed behavior; proposed in-process component | Bounded non-durable active-country cache. | No system-of-record ownership; lost on restart. Not a separate C4 container. |

### 9.1 Stable application components

The Level 3 view is included because security, transaction, and dependency boundaries are stable and directly constrain implementation:

- Reactive REST adapter.
- Party application capability.
- Identifier application capability.
- Scheme administration capability.
- Outbox publication capability.
- PostgreSQL adapter.
- Geographic reference adapter/cache.
- Identifier protection/key adapter.
- RabbitMQ adapter.
- Security audit adapter.
- Runtime composition/readiness.

These are cohesive responsibilities, not class or endpoint designs.

## 10. Integration and Contract Architecture

| Integration | Contract and ownership | Failure, security, compatibility |
|---|---|---|
| Internal REST | Consumer -> Party Registry; source of truth must be downstream OpenAPI under `/api/v1`; Party Registry owns provider contract. | Reactive HTTPS/JSON when crossing a trust boundary; trusted context; bounded validation; stable envelope/errors; ETag/If-Match; no stack traces/plaintext. V1 changes must remain backward compatible. |
| Geographic Reference | Party Registry -> provider; provider contract is authoritative; local adapter is anti-corruption boundary. | Reactive timeout; 24-hour normal cache and approved seven-day stale branch; malformed/unavailable response is dependency failure; no retry that exceeds request budget; correlation without confidential data. |
| RabbitMQ events | Party Registry -> exchange -> consumer queues; detailed event schema is a contract-first downstream artifact; Party Registry owns produced versions, consumers own queues/DLQs. | Persistent message, publisher confirm, at-least-once, event-ID deduplication, aggregate ordering evidence, minimal payload, immutable published version; bounded per-attempt timeout/concurrency and scheduled backoff/jitter. |
| VPS key/secret capability | Operator -> identifier protection adapter; key material stays outside image/repository. | Separate encryption/HMAC masters, tenant derivation, version checks, fail-closed readiness; values never logged/traced. |
| Platform logging | Party Registry -> logging capability; Party Registry owns emission schema, operations owns collection/retention. | Emit-before-return with explicit success/failure; no plaintext; correlation fields required; decryption denied when emission fails. |

**AC:** API, event, provider, and operational recovery contracts must exist before implementation of the corresponding adapter. Each contract must identify owner, consumer, versioning, compatibility, validation, error semantics, security, idempotency, correlation, and change governance. Exact OpenAPI paths/payloads and event payload fields are deliberately not invented here.

**PD (nonblocking for architecture):** requirements-gate RG-401 identifies that BR-029's exact pagination defaults, limits, filters, order and error parameters cite a gate finding rather than product authority. API contract approval must obtain an authorized technical-contract decision or explicitly defer those values while preserving tenant isolation, bounded pagination, deterministic order, supported-resource filters, and stable validation errors. This architecture does not elevate G208 into authority.

## 11. Data Architecture and DBML Authority

- **CR:** Party Registry is business and technical owner of the in-scope records; Geographic Reference owns countries; external consumers own opaque references; operations owns centralized decryption-log retention.
- **CR:** full identifiers are restricted; names, personal dates and nationalities confidential; UUIDs, tenant IDs and masked values internal.
- **AC:** `docs/database/v1-scheme.dbml` is the exclusive authority for PostgreSQL tables, columns, relationships, constraints and indexes. This document introduces none.
- **TD:** persistence adapters map DBML-governed records to domain/application models and expose tenant-qualified access only. Runtime and migration database roles are least-privileged and separate from ownership roles.
- **CR:** no physical deletion or automatic purge occurs in V1. This is technical behavior, not a legal retention interpretation.
- **AC:** database-contract validation must test cross-table type/detail invariants, active-nationality and active-identifier uniqueness, tenant-qualified references, optimistic versions, outbox checks, and PostgreSQL 18 compatibility exactly against DBML.
- **AC:** encrypted values, HMAC fingerprints, masks and public event payloads follow SR-002..004; plaintext has no persistence, telemetry, event, or ordinary-query path.

### 11.1 Database contract handoff

The database-contract phase must answer, without editing DBML:

1. Can the confirmed DBML enforce every declared tenant, cardinality, deferred-detail, active-nationality, identifier uniqueness and outbox invariant on PostgreSQL 18?
2. Does the DBML support tenant-qualified queries and optimistic update predicates required by the use cases?
3. Can Party/details/nationality updates and their Party version/outbox effects commit in one local transaction?
4. **High-risk:** where is the durable tenant+operation+Idempotency-Key claim, effective-request identity and replay result required by BR-026 stored? The current DBML exposes no such physical fact.
5. What expand-and-contract sequencing is required by the eventual validated migration set? Database rollback must remain prohibited.

## 12. Transaction, Consistency, and Idempotency

| Flow | Transaction / consistency | Failure and retry behavior |
|---|---|---|
| Party creation | **CR:** Party, matching initial data, creation outbox event, and durable idempotency outcome must converge in one local atomic boundary. | Same key/request replays original success; different request returns 409; concurrent duplicates converge. **PD:** physical durability is unsupported by current DBML evidence. |
| Party/detail/nationality mutation | **CR:** one local PostgreSQL transaction updates component, audit facts, Party version and one required outbox event. | If-Match absence/mismatch and invariant failures commit nothing. Client retries must use current version and avoid assuming idempotency. |
| Identifier mutation | **CR:** one local transaction preserves tenant/scheme/Party consistency, uniqueness, version, audit and required outbox event. | Constraint/concurrency failure commits nothing; plaintext never enters transaction storage. |
| Scheme mutation | **CR:** one local transaction changes the versioned global scheme only; no tenant outbox event. | Unsupported implementation keys fail unchanged; active missing keys fail readiness/dependent operations. |
| Outbox delivery | **TD:** publisher claims eligible rows with the DBML-defined concurrency mechanism, publishes outside the business transaction, and updates delivery state after outcome. | Positive confirm -> PUBLISHED. Transient/unknown -> same-ID PENDING eligibility with bounded scheduled backoff. Non-recoverable -> FAILED; authorized recovery reuses same row/ID. Business state is never rolled back. |
| Exact search | **CR:** read-only tenant-qualified HMAC lookup; no decrypt/scan. | Key failure returns stable failure and no state change. |
| Decryption | **CR:** tenant-qualified read, authenticated decrypt, mandatory audit emission acknowledgment, then plaintext response. | Any lookup/key/authentication/audit failure returns no plaintext; no retries that could bypass audit. |

No distributed transaction, saga, or compensation is needed. Reconciliation must expose aged/failed outbox events, aggregate/event identity, and operator recovery outcomes. Consumers reconcile duplicates by event ID.

### 12.1 High-risk persistence conflict

**PD:** BR-026/FR-047 require durable replay after a successful Party creation and concurrent one-result convergence. `docs/database/v1-scheme.dbml` defines no idempotency record or equivalent fields. In-memory state cannot survive restart and cannot be atomically committed with Party/outbox state; RabbitMQ is post-commit and cannot own command replay. Adding an ungoverned table, cache, or database would violate DBML authority.

Required human/database authority must choose one of:

1. authorize a DBML revision that models service-owned durable idempotency state and revalidate it;
2. supply an already approved durable idempotency system and ownership/atomicity contract capable of the exact semantics; or
3. explicitly revise BR-026/FR-047 semantics.

Recommended default is option 1 because it preserves local ACID consistency and clear ownership. Postponing beyond database-contract validation blocks persistence planning and implementation. This proposal does not select or implement an option.

## 13. Security and Privacy Architecture

### 13.1 Trust boundaries and assets

| Boundary / asset | Controls |
|---|---|
| Internal consumer -> nginx/service | **AC:** no public service binding; internal network only; syntactic context validation; request/payload bounds; tenant IDs accepted only from header context; no V1 authentication/authorization. |
| Service -> PostgreSQL | Tenant predicates on every tenant-owned query/mutation; least-privilege runtime role; TLS when crossing a trust boundary; no external direct access. |
| Service -> external capabilities | Explicit adapters, timeouts, non-sensitive errors, TLS when crossing trust boundaries, correlation without sensitive payloads. |
| Restricted identifier plaintext | Exists only transiently for submission, exact-search processing and separate decryption response; mutable buffers/lifetimes minimized; never logged, traced, measured, persisted or emitted. |
| Keys | Separate protected versioned masters, runtime mount, no repository/image/config output, tenant derivation, fail-closed readiness and rotation procedure. |

### 13.2 Threat controls

- **CR:** tenant confusion is mitigated by mandatory tenant-qualified repository methods and 404 non-disclosure, not by authorization. Architecture/security tests must prove no unscoped tenant data port is reachable.
- **CR:** injection is mitigated by typed validation and parameterized persistence adapters; external values never become executable rule code.
- **CR:** replay is controlled for Party creation by the pending durable idempotency boundary and for events by stable event ID; no broader command idempotency is invented.
- **TD:** resource exhaustion is controlled through validated pagination/payload limits, reactive backpressure/bounded concurrency, connection-pool limits, bounded dependency timeouts, and outbox batch claims. Numeric settings are performance evidence, not invented requirements.
- **CR:** decryption emits tenant ID, user ID, process ID, identifier ID, timestamp and outcome before plaintext return. Operational logs and security audit events are distinct; no Party Registry audit store is added.
- **CR:** responses reveal no stack trace, SQL error, hostname, credential, token, plaintext identifier, or unapproved personal data.

**High residual risk preserved:** any consumer with internal connectivity can assert tenant/user context and decrypt restricted values. This is explicit V1 scope, not a claim of safety or identity assurance. The security gate and production readiness gate must independently evaluate network isolation and residual risk before production. Any public exposure or authentication-policy change requires new requirements and architecture decisions.

## 14. Resilience, Recovery, Performance, and Capacity

- **CR:** Geographic calls use reactive timeout handling and only the approved cache freshness branches. Retries, if used for safe reads, are bounded by the request deadline and cannot turn an unavailable dependency into a write.
- **TD:** RabbitMQ attempts use bounded timeout/concurrency and configurable backoff/jitter while preserving the confirmed absence of a numeric maximum attempt or Party Registry DLQ/discard. Publisher restart resumes durable non-published state.
- **TD:** connection pools, REST concurrency and outbox batches are bounded to prevent saturation on the 1 CPU/512 MiB pilot instance. Values must be established by NFR-008/009 tests.
- **CR:** daily backup, RPO <= 24 hours, RTO <= 8 hours and recurring restore-test evidence apply to PostgreSQL operations. Exact restore frequency remains an operations decision before production.
- **CR:** liveness reports process health only. Readiness fails for unusable mandatory key capability, unsupported active rule configuration, unavailable mandatory persistence, and startup failure; a transient RabbitMQ outage does not erase committed capability and should be represented by dependency/lag signals rather than restart loops.
- **TD:** no automatic failover, multi-region topology, distributed cache or horizontal scaling is introduced without measured need and an ADR.

## 15. Observability and Auditability

| Signal | Required evidence and restrictions |
|---|---|
| Structured operational logs | Process/correlation ID, stable error class, dependency/outbox state; no plaintext, keys, confidential payloads or stack traces in external responses. |
| Security audit event | Actor assertion (`user-id`), tenant, process, identifier ID, timestamp, action/outcome; emission acknowledged before decryption return; platform collection/retention is external. |
| Metrics | Request latency/error by operation class, PostgreSQL pool/saturation, outbox count/oldest age/status/attempts, RabbitMQ outcomes/lag, geographic cache freshness/failures, readiness transitions. No tenant, identifier or personal labels. |
| Traces | Correlation across inbound request and dependencies without plaintext or confidential request/response bodies. |
| Health | Startup, liveness and readiness endpoints with non-sensitive reason categories. |
| Alerts/runbooks | NFR-009 lag, NFR-011 startup, aged/FAILED outbox, key/readiness failure, geographic cache exhaustion, pool saturation, backup/restore failures. Ownership follows requirements section 22. |

Business transaction ID is aggregate ID/event ID where applicable; process ID is request correlation. Logs are diagnostic evidence and are not substituted for DBML-governed audit fields or outbox records.

## 16. Deployment Architecture

- **AC:** deploy the Party Registry application as a non-privileged rootless Podman container managed by version-controlled Quadlet on the approved VPS profile.
- **AC:** bind the application to loopback; nginx is the confirmed reverse-proxy boundary and must not expose the service publicly. Only approved internal network paths may reach the API.
- **TD:** the logical deployment view leaves PostgreSQL, RabbitMQ, Geographic Reference, logging and key placement in operations-managed boundaries; their physical co-location is not invented. Crossing a host/trust boundary requires encrypted transport where applicable.
- **AC:** externalize non-secret configuration; mount protected secrets with least privilege; set resource and restart limits; preserve database volume ownership and backup controls.
- **AC:** local, development, staging and production use environment-specific configuration outside the OCI artifact.
- **AC:** one build candidate is identified by immutable OCI digest; the same digest moves development -> staging -> production. Production build means promotion/verification, never rebuilding.
- **AC:** every promotion records source revision, digest, deployment actor/interface, environment, configuration version and health/smoke/stabilization result. Readiness must occur within 60 seconds and stabilization lasts 10 minutes.
- **AC:** deployment uses restricted wrappers, not direct SSH/unrestricted shell. Application rollback requires approval and can select a prior compatible digest; database changes are forward-only and are not reversed.

The required context, container, component and deployment views are in `architecture/model.c4`. The production deployment view is logical and contains no hostnames, addresses, credentials or unconfirmed high-availability topology.

## 17. Database Deployment Sequencing

**AC:** after DBML validation, migration design must define an expand-and-contract compatibility window where needed, migration-before/application-after ordering, lock/downtime analysis, backfill verification, roll-forward remediation and manual stop points. No migration is presumed reversible. The same application digest promotion rules remain in force. The unresolved idempotency decision must be settled before a migration or application release can claim Party-creation compliance.

## 18. Verification Strategy

| Layer | Required checks |
|---|---|
| Domain unit | Lifecycle matrices, type/detail and date rules, identifier compatibility, masking, mutability, rule-catalog policies. |
| Application use case | Tenant propagation, transaction intent, idempotency orchestration, If-Match outcomes, emit-before-return decryption, error semantics. |
| Architecture dependency | Domain imports no framework; application imports no adapter; adapters access application through ports; model separation and forbidden generic packages. |
| Adapter integration | PostgreSQL tenant/concurrency/atomicity, Geographic cache/failures, key/crypto faults, audit emission failure, RabbitMQ confirms/ambiguity/restart. |
| Contract | OpenAPI envelope/context/ETag/error compatibility; versioned minimized event contracts; provider interaction contracts. |
| Database/migration | DBML semantic validation, constraints/triggers/indexes, transaction behavior, forward-only migration and upgrade compatibility. |
| Security/privacy | Tenant isolation, no plaintext leakage, exact HMAC, encryption/key separation, no-store, logging minimisation, internal-network posture and abuse tests. |
| Performance/reliability | Exact NFR-008/009 dataset/load/latency/error/lag targets, saturation, publisher backlog/recovery, readiness within 60 seconds. |
| Deployment/production | Digest identity, rootless/loopback posture, health/readiness, smoke, 10-minute stabilization, backup/restore and rollback constraints. |

## 19. Architecture Guardrails

1. Domain packages must not import Quarkus, Jakarta, CDI, REST, JSON, ORM, database, messaging, logging, configuration or crypto-provider APIs.
2. Application packages may depend only on domain and boundary-neutral port models; they must not import adapter implementations.
3. Inbound adapters call application input ports only; outbound adapters implement application output ports.
4. Every tenant-owned persistence operation requires tenant ID in its port signature and predicate; no unscoped repository method is allowed.
5. Transport, application, domain, persistence, provider, event and audit models remain distinct.
6. Business logic must not reside in REST resources, repositories, mappers, publisher loops or deployment scripts.
7. Mutation and required outbox insertion commit atomically; direct business-event publication from use cases is forbidden.
8. Plaintext identifiers and keys must never enter logs, traces, metrics, events, ordinary responses or persistence.
9. Blocking calls on reactive paths are forbidden; unavoidable blocking/CPU work requires bounded isolation, context propagation and tests.
10. No public outbox CRUD, direct consumer database access, cross-service foreign key, shared database write, or frontend database access is permitted.
11. Retries are bounded per attempt and safe for the operation; no retry may duplicate an irreversible effect or change event identity.
12. Public contracts are versioned and contract-first; incompatible event change uses a new versioned event type.
13. DBML physical facts cannot be added or altered by implementation. Architecture exceptions require a proposed ADR and explicit approval.
14. Deployment must use the same OCI digest across environments and restricted rootless Quadlet interfaces.
15. Architecture dependency tests and manual LikeC4/narrative consistency review are release-gate evidence.

## 20. Risks and Pending Decisions

| ID | Classification | Trigger / impact | Mitigation / gate / residual risk |
|---|---|---|---|
| AR-001 | PD — High data integrity | Durable Party-creation idempotency has no DBML representation; restart/concurrency can duplicate Party/outbox effects. | Human/database decision in section 12.1 before DB validation completion; residual High. |
| AR-002 | CR risk — High security/privacy | Trusted internal caller can assert tenant and decrypt restricted data. | Internal-only network, loopback, tenant predicates, no-store, audit, independent security/production gates; residual High by approved V1 scope. |
| AR-003 | CR risk — High compliance | No purge/legal retention policy. | Data minimisation; compliance decision before policy change/production approval; residual unassessed. |
| AR-004 | AC risk — High deployment | Verified current CI violates restricted deployment and immutable promotion. | Do not reuse as TO-BE; later release/deployment remediation and gates; residual High until corrected. |
| AR-005 | CR risk — Medium integration | RabbitMQ outage/unknown outcome creates duplicates/backlog. | Same-ID outbox retry, confirms, metrics, operator recovery, consumer dedupe; integration/performance gates. |
| AR-006 | CR risk — Medium availability | Key/logging/Geographic dependency failure denies operations. | Fail closed, stale-cache rules, readiness and alerts; resilience/security gates. |
| AR-007 | PD — Medium contract | Exact BR-029 parameters lack authoritative source per RG-401. | Authorized API-contract decision before API gate; preserve bounded deterministic semantics. |
| AR-008 | AC risk — High database | DBML may not validate against PostgreSQL 18. | Database-contract gate; no automatic DBML edits or persistence work. |

No low-risk design assumptions are used to decide business, security, data ownership, public contracts, financial consistency, topology, cutover or rollback.

## 21. ADR Index

| ADR | Status | Decision |
|---|---|---|
| `ADR-001-reactive-single-deployable.md` | Proposed | End-to-end reactive execution in one cohesive application deployable. |
| `ADR-002-transactional-outbox.md` | Proposed | Local transaction plus DBML-governed outbox and confirmed at-least-once publication. |
| `ADR-003-sensitive-identifier-boundary.md` | Proposed | Separate protection, key, exact-search and fail-closed decryption-audit boundaries. |

## 22. Implementation Planning Handoff

Planning may decompose work by Party, Identifier, Scheme, Event Publication and cross-cutting adapter capabilities, but must not invent final API/event schemas, persistence facts, dependencies or migrations. Persistence-related implementation remains blocked until database validation, and Party creation remains blocked until AR-001 receives an authoritative resolution. Downstream plans must include architecture tests, all verification layers in section 18, deployment-profile remediation, and immutable digest evidence.

## 23. Internal Consistency Review

- Scope and external ownership match the requirements and DBML.
- LikeC4 context contains only actors/system/external systems; container, component and deployment concerns are separated into their own views.
- One application deployable is not confused with Clean Architecture layers.
- Integration direction, data ownership, trust boundaries, transactions, retry/idempotency and outbox state agree across narrative, ADRs and model.
- DBML authority is preserved; the idempotency conflict is visible and not solved by an invented table/store.
- Reactive execution, security, resilience, observability, performance, VPS/Quadlet deployment and immutable promotion are explicit.
- No code, test, DBML, migration, dependency, runtime configuration, deployment descriptor, Git/GitHub operation or workflow-state change was performed.
- Architecture remains Proposed and pending independent review.
