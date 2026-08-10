# Party Registry Service V1 — Solution Architecture

## 1. Document Control

| Field | Value |
|---|---|
| Status | Proposed |
| Approval | Pending independent `clean-architecture-gate-agent` evaluation |
| Architecture identifier | PRS-ARCH-001 |
| Version | 0.6 |
| Author | `solution-architecture-agent` |
| Historical requirements baseline | PRS-REQ-001 v0.4 |
| Effective requirements amendments | `requirements-amendment-001.md`, `requirements-amendment-002.md` |
| Persistence authority | `docs/database/v1-scheme.dbml` |
| Architecture model | `architecture/model.c4` |
| Authoritative human decision | `.factory/decisions.json`, decision `5bceb7ac-7c81-4dcc-af56-c6f87c7e7d42` |

This architecture preserves the independently gated PRS-REQ-001 v0.4 bytes as historical evidence. The factoryctl-recorded human decision above approves Amendments 001 and 002: Amendment 001 supersedes command-level idempotency and the assumed external logging platform, while Amendment 002 supersedes every runtime Identifier Scheme administration use case. This document is a proposal and does not approve those requirements, itself, or a workflow transition.

### 1.1 Review identity and lifecycle

The reviewable PRS-ARCH-001 v0.6 source set is this document, `architecture/model.c4`, and ADR-001 through ADR-005 listed in section 22. Its controlling inputs are the manifest, recorded human decision, effective requirements and authoritative DBML listed above. Version 0.6 supersedes PRS-ARCH-001 v0.5 as an architecture proposal; it does not supersede requirements or DBML.

The independent `clean-architecture-gate-agent`, not this author, must bind the exact reviewed bytes and repository revision to cryptographic digests in its gate evidence. This author does not embed a self-referential digest or create a separate package manifest. Any later byte change requires a new independent binding.

## 2. Source Precedence

1. Recorded human decisions in `.factory/decisions.json`, including approval of Amendments 001 and 002.
2. `.factory/project.yaml`.
3. Effective requirements: PRS-REQ-001 v0.4 plus Amendments 001 and 002.
4. `docs/database/v1-scheme.dbml` for physical persistence facts.
5. Architecture, stack, persistence and deployment profiles.
6. Verified repository facts.

No architecture statement may infer a runtime CRUD/use case solely because a DBML table exists.

### 2.1 Statement classification

The labels below apply throughout this document and its ADRs:

- **CONFIRMED REQUIREMENT (CRQ):** effective PRS-REQ-001 v0.4 behavior after the factoryctl-recorded Amendments 001 and 002.
- **EXISTING FACT (EF):** repository evidence verified during architecture analysis; it is not automatically TO-BE authority.
- **ARCHITECTURE CONSTRAINT (ACN):** mandatory manifest, profile, DBML-authority, or effective-requirement boundary.
- **TO-BE DECISION (TBD):** proposed architecture pending the independent architecture gate.
- **DESIGN ASSUMPTION (DA):** a low-risk temporary assumption with explicit validation and false-case consequence.
- **PENDING DECISION (PD):** an unresolved choice requiring the named later authority.
- **OUT OF SCOPE (OOS):** explicitly excluded V1 responsibility.

Unless a paragraph is prefixed otherwise, sections 3–4 state **CRQ/OOS**, sections 5–17 and 19–20 state **TBD** implementing cited **CRQ/ACN**, section 18 is a downstream handoff constrained by **ACN**, and section 21 explicitly classifies risks and pending decisions. Physical structures quoted from DBML are **EF** and remain governed exclusively by DBML.

### 2.2 Baseline evidence and known supersession

- **EF:** `.factory/runs/oc-409818fa-64f6-430b-88eb-be8e97ddcd1b/result.json` records an independent requirements-gate `PASS` for PRS-REQ-001 v0.4.
- **CRQ:** `.factory/decisions.json` decision `5bceb7ac-7c81-4dcc-af56-c6f87c7e7d42` subsequently approves Amendments 001 and 002 and explicitly supersedes RG-104 and runtime Scheme administration.
- **EF:** each amendment's document-control status still says decision recording is pending. The later factoryctl decision is higher-precedence authority; this architecture does not edit requirements artifacts.
- **ACN:** `docs/database/v1-scheme.dbml` is the exclusive physical PostgreSQL design authority and was not modified.

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

### 4.1 Ownership and trust boundaries

| Boundary | Classification | Ownership and trust rule |
|---|---|---|
| Internal consumer → REST entry point | CRQ | Callers supply trusted `tenant-id`, `user-id`, and optional `process-id`; V1 authenticates and authorises none of them. Network access control is external, and tenant-qualified application/persistence behavior remains mandatory. |
| Application → PostgreSQL | ACN/TBD | Party Registry owns its data contract; the runtime role is least privileged and cannot mutate `identifier_schemes`; migration/administration credentials are separate. |
| Application → Geographic Reference | CRQ/TBD | Geographic Reference owns country truth; Party Registry owns cache/failure translation and stores logical codes only. |
| Application → RabbitMQ | CRQ/TBD | Party Registry owns publication through broker confirmation; consumers own queues, DLQs, and event-ID deduplication. |
| Runtime → injected secrets | CRQ/ACN | VPS operations owns provision/protection; application receives versioned encryption/HMAC masters without logging or embedding them. |
| Local application logging path | CRQ/TBD | Party Registry owns synchronous emission attempt only; no external logging system, audit store, centralized durability, or retention dependency is claimed. |

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
11. Operational and decryption-security logs are emitted through the local application logging path and carry the effective requirements' structured fields; no unverified external logging format is made normative here.
12. No CQRS, event sourcing, distributed transaction, saga, extra microservice or durable cache is introduced without a new approved decision/ADR.

The single deployable is selected because API, publisher, and persistence responsibilities share one bounded context, operational owner, release, and local transaction authority; no approved independent deployment/scaling/trust requirement justifies a second service. Clean Architecture areas remain logical dependency boundaries, not C4 containers.

### 5.1 Canonical technology profile

**ARCHITECTURE CONSTRAINT:** the supplied `quarkus-java25` profile remains the canonical technology checklist; this architecture does not replace or fork it. The repository pins Quarkus plugin/platform `3.33.3` in `gradle.properties:3-7` and Java 25 in `build.gradle.kts:24-27`. Feature work must retain those pins unless a separately governed profile/version decision authorises change.

Profile responsibilities are placed as follows:

| Profile capability | Architecture placement and required downstream evidence |
|---|---|
| Reactive Quarkus runtime | Bootstrap and inbound/outbound adapters; nonblocking path, blocking isolation, context propagation, transaction and performance evidence under ADR-001. |
| OpenAPI | Inbound REST contract source of truth; API-contract gate verifies the effective V1 surface and compatibility. |
| PostgreSQL and Flyway | PostgreSQL adapter and separately governed migration capability; Flyway is forward-only and cannot run before DBML/database-contract approval. Runtime code does not own migration execution. |
| JUnit | Domain/application unit and adapter test execution mechanism selected by the profile. |
| Testcontainers | PostgreSQL, RabbitMQ and relevant adapter integration evidence without replacing provider contract tests. |
| ArchUnit | Automated strict dependency-direction and prohibited-import evidence. |
| JaCoCo | Coverage evidence supporting, but never replacing, behavior and architecture assertions. |
| OCI packaging | Bootstrap/runtime packaged once; one immutable digest is promoted without rebuild through development, staging and production. |

The current scaffold does not yet declare every profile tool. This architecture records mandatory placement and validation responsibility, not a dependency change; build/dependency modification belongs to downstream planning and implementation within their authorised scope.

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

### 6.3 Explicit core ownership and dependency direction

The LikeC4 component view represents the core boundaries as first-class elements rather than leaving them implicit in relationship text:

- `partyDomain` is the framework-independent Domain component.
- `partyCapability` and `identifierCapability` are Application-layer use-case components.
- `partyInputPorts` and `identifierInputPorts` are Application-owned input-port components.
- `partyPersistencePorts`, `identifierPersistencePorts`, `geographicReferencePort`, `identifierProtectionPort`, `securityLogPort`, `outboxStorePort`, `eventPublisherPort`, and `clockPort` are Application-owned output-port components.
- REST, PostgreSQL, Geographic Reference, protection, local logging, and RabbitMQ components are adapters that depend inward on those core-owned contracts.
- `runtimeComposition` is the Bootstrap boundary and alone selects and wires concrete adapters.

The directional rules are: inbound adapter → input port → application use case → domain; application use cases invoke output-port contracts; outbound adapters → output ports because adapters implement core-owned contracts; bootstrap → concrete runtime elements for composition. No Domain or Application element depends on an adapter or framework. Runtime call direction through an output port does not reverse compile-time ownership: the port remains defined by the Application core and the adapter imports/implements it.

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

Stable component responsibilities are:

- **Domain:** Party Registry Domain.
- **Application use cases:** Party Application Capability, Identifier Application Capability, and Outbox Publication Capability.
- **Application-owned input ports:** Party Input Ports and Identifier Input Ports.
- **Application-owned output ports:** Party Persistence and Unit-of-Work Ports, Identifier and Scheme Catalog Ports, Geographic Reference Port, Identifier Protection Port, Decryption Security Log Port, Outbox Store Port, Event Publisher Port, and Clock Port.
- **Inbound adapter:** Reactive REST Adapter.
- **Outbound adapters:** PostgreSQL Adapter, Geographic Reference Adapter/cache, Identifier Protection Adapter, RabbitMQ Publisher Adapter, Decryption Security Log Adapter, and System Clock Adapter.
- **Bootstrap:** Runtime Composition and Readiness.

There is no Scheme Administration Capability. Scheme reads occur as part of Identifier processing through the read-only catalog port implemented by the PostgreSQL adapter.

## 10. API Surface

The V1 REST API is rooted under `/api/v1` and follows the approved ApiResponse/ETag/If-Match conventions.

Runtime REST surface includes approved Party, Party-detail, nationality and Party Identifier operations.

The API MUST NOT expose Identifier Scheme administration. Amendment 002 also removes the previously generated Scheme lookup/search resources from V1; runtime Scheme access is internal reference-data access unless a later explicit requirement introduces a read-only public/internal discovery contract.

No `Idempotency-Key` is accepted or interpreted by V1 commands.

RG-401 remains a nonblocking downstream API-contract authority issue for exact pagination/filter/sort defaults. Architecture does not manufacture those values.

### 10.1 Contract-first rule

The OpenAPI document is the downstream source of truth for the detailed HTTPS/JSON contract and is owned by Party Registry; internal consumers are its consumers. It must version the `/api/v1` surface, preserve `ApiResponse`, context, ETag/If-Match, error, masking, no-store, and tenant non-disclosure semantics, and exclude Scheme administration, outbox CRUD, command idempotency, stack traces, and plaintext outside the decryption response. Published V1 meanings are backward compatible; an incompatible change requires a new approved contract version. Transport DTOs remain inside the inbound adapter and are mapped to boundary-neutral application inputs/results.

## 11. Transaction Boundaries

| Flow | Atomicity / behavior |
|---|---|
| Party creation | Local transaction creates Party/approved initial data and required Party outbox event. |
| Party/detail/nationality mutation | Local transaction updates component, Party version, audit facts and required outbox event. |
| Party Identifier creation/mutation | Local transaction preserves tenant/Party/Scheme invariants, permanent uniqueness, version/audit state and required outbox event. |
| Identifier Scheme access | Read-only query of database-managed reference data; no business mutation transaction exists. |
| Outbox delivery | A short claim transaction reserves eligible rows using only DBML fields and commits before broker I/O; publication occurs with no open database transaction or row lock; a separate outcome transaction records confirmation/failure. |
| Exact identifier search | Tenant+Scheme HMAC lookup; no decrypt/scan. |
| Decryption | Tenant-qualified read -> ciphertext authentication/decrypt -> required local security log -> no-store plaintext response. |

There is no Scheme mutation flow, no distributed transaction, no saga and no compensation design.

For every country-dependent state change, Geographic Reference resolution (including cache lookup and any remote call) completes **before** the state-changing PostgreSQL transaction begins. The application first obtains usable country evidence or returns the approved unchanged 503 outcome. Only after evidence is available may it open the local mutation/outbox transaction; the evidence is then consumed without another remote call inside that transaction. Every other remote interaction likewise occurs outside state-changing PostgreSQL transactions. RabbitMQ follows the separately committed claim/broker/outcome boundaries in section 12.2. No database connection transaction or row lock is held while awaiting Geographic Reference, RabbitMQ, or any other remote system.

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

### 12.1 Integration contracts and failure ownership

| Integration | Contract / security | Timeout, retry, duplicates, ordering and recovery |
|---|---|---|
| Internal reactive REST | OpenAPI is source of truth; internal HTTPS/JSON is proposed; V1 trusts validated context and performs no authn/authz. Restricted plaintext is allowed only on approved request fields and the no-store decryption response. | Request deadlines are bounded operational configuration. Clients receive stable error categories. No command replay/idempotency is offered. Correlation uses `process-id`. |
| Geographic Reference | Provider-owned country contract; Party Registry anti-corruption adapter maps only active ISO alpha-2 evidence. Data is internal. Country evidence is resolved before opening any state-changing PostgreSQL transaction. | Each call has a bounded timeout. The 24-hour cache and seven-day stale fallback are authoritative; no usable cache yields unchanged HTTP 503. Cache is non-durable and restart-safe by failing according to the cold-cache rule. No remote call occurs while a state-changing database transaction or lock is open. |
| RabbitMQ publication | Party Registry owns immutable versioned event schemas and publisher behavior; persistent messages to the approved durable topic exchange use publisher confirms. Payload is minimized and excludes full identifiers. | Each publish attempt and concurrent claim batch are bounded; persistent eligibility is not attempt-limited because the approved policy forbids automatic discard. Unknown/transient outcomes retain the same ID and retry eligibility with backoff/jitter; delivery is at least once and ordered only by aggregate version evidence, not globally. Consumers deduplicate by event ID and own queues/DLQs. Non-recoverable failures remain operator-visible until authorised same-event recovery. |
| PostgreSQL | DBML is physical source of truth; Party Registry owns runtime access and local ACID consistency. TLS is required when the connection crosses a trust boundary. | No distributed retry encloses an unknown commit. Safe transaction retries, if introduced, must be bounded and tested against unique/version conflicts. Recovery uses backups, restore evidence, and reconciliation of outbox/business state. |
| Local security logging | Application-owned structured log contract; local logger only, containing approved identifiers, timestamp, action/outcome, and no plaintext. | Decryption invokes it once before disclosure. Synchronous rejection/failure returns no plaintext. No remote retry, external acknowledgement, or durable collection is claimed. |

Detailed OpenAPI and event schemas are deliberately not generated in this phase. Published event schemas are immutable; incompatible evolution uses a new versioned event type. Event correlation/causation and aggregate version provide traceability; they do not guarantee global ordering.

### 12.2 Outbox claim, publication and outcome boundaries

The publisher uses three separate boundaries; RabbitMQ I/O is never performed while a PostgreSQL transaction or row lock is open:

1. **Claim transaction:** select a bounded batch of currently eligible `PENDING` rows with `SELECT ... FOR UPDATE SKIP LOCKED`; for each selected row, set the existing `next_attempt_at` to a bounded future visibility deadline, set `last_attempt_at`, increment `publish_attempts` and `version`, then commit. The committed version is the claim token. All row locks are released at commit.
2. **Broker operation:** only after the claim transaction commits, publish each persistent message and await its bounded publisher-confirm outcome. No database transaction is open during serialization, network I/O or confirmation wait.
3. **Outcome transaction:** update the same event using its ID and claimed version. Positive confirmation sets `PUBLISHED` and `published_at`; a classified non-recoverable failure sets `FAILED` with non-sensitive evidence; transient, negative, timeout or unknown outcomes remain `PENDING` and become eligible according to bounded backoff through `next_attempt_at`. The optimistic version predicate prevents a stale publisher outcome from overwriting a later claim/outcome.

If the process crashes after claim commit but before outcome recording, the row becomes eligible again after the committed `next_attempt_at`; this can duplicate publication after an unknown outcome, which is required at-least-once behavior and preserves the same event ID. The claim batch size, visibility deadline, publish timeout and concurrency are bounded operational configuration validated against event-lag targets. This design uses only DBML-supplied fields (`status`, `next_attempt_at`, `last_attempt_at`, `publish_attempts`, `version`) and does not invent claim-owner, lease, status, table or external store semantics. Database validation must confirm these fields and optimistic update semantics safely support the sequence; if not, it must report the exact DBML conflict rather than alter physical design.

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

### 13.1 Threat controls and security evidence

| Threat / abuse case | Architectural control | Required evidence |
|---|---|---|
| Cross-tenant enumeration or mutation | Validate canonical context before business access; tenant-qualify every repository operation; return non-disclosing 404 across tenants. | API, persistence integration, and negative tenant-isolation tests. |
| Caller impersonation / unauthorised decrypt | **CRQ accepted residual boundary:** no V1 authn/authz; bind only to internal loopback/nginx path and never describe caller assertions as identity proof. | Security gate verifies no accidental public binding, the explicit residual risk, and no invented 401/403 behavior. |
| Plaintext, ciphertext, fingerprint, or key leakage | Minimise plaintext lifetime; separate transport/application/domain/persistence/provider/audit models; prohibit sensitive telemetry/events; mask exactly; inject secrets outside image/repository. | Security tests and telemetry/event inspection. |
| Replay of state change | If-Match protects aggregate modifications; permanent DB uniqueness protects official identifiers; no command-idempotency promise exists. | Concurrency, uniqueness, and contract tests. |
| Injection / malformed input | Typed transport validation, parameterised persistence adapter, strict external DTO parsing, stable non-sensitive errors. | API fuzz/negative tests and database adapter review. |
| Scheme validation-policy tampering | Query-only application port and PostgreSQL runtime privilege denial for catalog writes. | Architecture test plus database privilege integration evidence. |
| Denial of service / reactive starvation | Bounded request/publisher concurrency, bounded timeouts, connection-pool/resource limits, blocking-call isolation, payload/page bounds. | Performance, saturation, and blocking-path evidence. |

Personal-data minimisation is limited to DBML-authorised attributes. Restricted identifiers are excluded from non-production fixtures unless irreversibly synthetic; production copies are not an approved test-data source. V1 performs logical retention with no purge and makes no legal-retention claim. A later retention/disposal or data-subject process requires privacy/compliance authority and a requirements/DBML decision.

## 14. Local Structured Logging

The effective requirements refer to the AlexAstudillo logging convention but do not supply that standard as an independently verifiable repository artifact. This proposal therefore makes only the confirmed fields and behavior normative: request correlation propagates `processId`, `userId`, and `tenantId`; decryption evidence additionally carries Party Identifier ID, timestamp, action and outcome; and no plaintext or other prohibited sensitive value is emitted. The later implementation plan must use a supplied, approved logging standard if one becomes available and must not invent a conflicting format.

MDC/SLF4J/Quarkus logging remains in inbound/infrastructure/bootstrap concerns, not domain/application core. No `Platform Logging Capability` exists in C4. Decryption is fail-closed on synchronous local log-emission failure; V1 does not claim remote log delivery or centralized persistence/retention.

## 15. Geographic Reference

Country-code validation uses Geographic Reference Service with the approved:

- 24-hour normal in-memory cache;
- up-to-seven-day stale fallback during dependency unavailability;
- 503/fail-unchanged behavior when no usable cache exists;
- historical-code preservation.

The cache is not a source of truth and is lost on process restart.

The Geographic Reference output port returns boundary-neutral evidence or an explicit unavailable outcome. A country-dependent command resolves that outcome before transaction opening. A failed/absent resolution therefore cannot partially mutate business state or create an outbox event, and a successful resolution is passed into the local transaction without remote I/O. Adapter integration tests must instrument transaction state and prove that provider calls never overlap a state-changing PostgreSQL transaction.

## 15.1 Data architecture and lineage

| Data concept | Owner / system of record | Classification, lineage and lifecycle |
|---|---|---|
| Party, matching details and nationalities | Party Registry / PostgreSQL | Names, dates and nationalities are confidential. Internal REST command → domain/application validation → local ACID state → minimal event when required. Logical end/archive only; no V1 purge. |
| Party Identifier | Party Registry / PostgreSQL | Full value is restricted; ciphertext, protected fingerprint and mask follow DBML. Input → Scheme normalization/validation → encryption/HMAC/mask → local ACID row/outbox. Plaintext is transient and only separately decrypted. |
| Identifier Scheme catalog | Party Registry database-managed reference data / PostgreSQL | Runtime reads only; governed migration/administration writes. It is not tenant event data and no runtime API owns its lifecycle. |
| Country reference | Geographic Reference Service | Party Registry stores logical ISO codes and bounded non-durable cache evidence; it does not replicate the authoritative catalog. |
| Outbox | Party Registry / PostgreSQL | Minimal internal integration state. Retained with business history in V1; no automatic purge/DLQ. |
| Decryption log | Local application logging output | Approved internal identifiers and outcome only; no Party Registry audit persistence or retention guarantee. |

The database-contract phase must validate physical constraints, cardinality, privileges, PostgreSQL compatibility, and data-quality enforcement directly against DBML. Architecture does not invent tables, columns, indexes, retention periods, replication, reporting exports, or a cache beyond the approved geographic cache.

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

The same logical topology applies to development, staging, and production: environment-specific configuration and secrets remain outside the OCI artifact; the candidate is built once, identified by immutable digest, verified in staging, and promoted unchanged to production. Local development may use profile-approved local mechanisms but does not redefine production trust boundaries. Each Quadlet unit runs rootless and non-privileged with explicit CPU/memory, restart, and connection/concurrency limits; exact values are operational configuration validated against NFR-008 rather than invented here. The service binds to loopback and is reached only through nginx; public binding is prohibited. Persistent database storage and backup ownership remain operations-managed.

Database rollout is forward-only. When DBML-authorised changes are later required, use an expand-and-contract compatibility window where needed: apply backward-compatible migration, validate, promote the same compatible application digest, backfill/reconcile if authorised, and contract only after old compatibility is no longer required. Rollback means application roll-forward/re-promotion under approval and must never assume database reversal.

## 17. Readiness and Resilience

Readiness must fail or report unavailable when required capabilities/configuration are unusable, including:

- mandatory PostgreSQL access unavailable;
- required encryption/HMAC secret unavailable or invalid;
- persisted Scheme configuration references required normalizer/validator implementation unavailable in the running version;
- startup cannot establish mandatory configuration.

Transient RabbitMQ failure is represented through dependency/lag signals rather than restart loops. Geographic unavailability follows the approved cache semantics.

No automatic failover, multi-region topology, distributed cache or premature horizontal-scaling architecture is introduced.

Liveness reports process viability and must not fail merely because RabbitMQ or Geographic Reference is transiently unavailable. Readiness covers mandatory PostgreSQL, secrets, and usable required Scheme/rule configuration; dependency-specific degradation is exposed separately where the approved cache/outbox semantics permit continued service. All synchronous dependency calls and individual publication attempts use bounded deadlines; retries use backoff/jitter and bounded concurrency. Outbox retry eligibility may persist indefinitely because that is an explicit requirement, but no single retry loop or request may be unbounded.

Recovery uses daily PostgreSQL backups, recurring restore tests, RPO ≤24 hours and RTO ≤8 hours. After restore, operators reconcile aggregate/outbox state, preserve event IDs, and allow at-least-once redelivery; no automated compensation or database rollback is assumed. Runbook ownership is operations-owned where stated in requirements; exact alert thresholds and restore-test recurrence remain operational configuration.

## 17.1 Observability and auditability

- Structured logs carry `processId`, `userId`, and `tenantId`; business transaction/resource/event IDs are added only where needed and never include plaintext, ciphertext, fingerprints, secret values, or confidential payloads.
- Metrics cover request rate/latency/error class, event-loop or worker saturation, PostgreSQL pool saturation, dependency latency/failures, geographic cache age/status, outbox status/oldest age/attempts/lag, publisher confirmations, and readiness reasons.
- Traces propagate process/correlation context across reactive boundaries and external calls with sensitive fields filtered; event correlation and causation continue across asynchronous publication.
- Startup, readiness, and liveness checks are distinct. Alerts support approved latency, publication lag, startup, RPO/RTO and backlog indicators; alert ownership is operations, while exact unapproved thresholds remain configuration.
- Record audit metadata and operational logs are not interchangeable. DBML audit fields prove stored mutation facts using caller assertions. The decryption security log records actor assertion, action, timestamp, source/process correlation, identifier ID, and outcome before disclosure; no before/after plaintext is allowed and no tamper-resistant audit store is claimed.

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
| LikeC4 | Architecture phase documents a manual consistency review when repository tooling is unavailable; the independent quality gate must execute project-approved LikeC4 parse/validate/render tooling and verify no Identifier Scheme Administrator actor/capability. |
| Database | PostgreSQL constraints plus runtime `identifier_schemes` SELECT-only privilege. |
| Adapter integration | Scheme reads succeed; Scheme runtime writes are impossible; Geographic/crypto/logging/RabbitMQ faults behave as specified. |
| Contract | No Scheme administration/discovery API in V1; approved Party/Identifier contracts remain versioned. |
| Security/privacy | Tenant isolation, plaintext exclusion, runtime secret separation, no-auth posture, Scheme write privilege denial. |
| Performance/reliability | Approved load/latency/error/lag targets and publisher recovery. |
| Deployment | Digest identity, rootless/loopback posture, runtime-secret injection, DB role separation, health/smoke/stabilization and backup/restore. |

Automated architecture dependency checks are mandatory: domain may depend on nothing outward; application may depend only on domain; adapters may depend on application/domain contracts; bootstrap alone wires concrete implementations. A project-approved architecture-test mechanism may be selected later without this document adding a build dependency. Reactive verification must detect/block hidden critical-path blocking, verify context propagation, bounded concurrency, transaction behavior and adapter fault handling.

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
17. Structured logs propagate `processId`, `userId`, and `tenantId`; decryption logs include the additional approved fields and never plaintext.
18. LikeC4 source is mandatory; this phase records `NOT_RUN` when no repository-controlled executable exists, and the independent quality gate must execute parse/validate/render before approval.
19. Database-managed Scheme changes create no Party Registry tenant business event.
20. A future runtime Scheme administration feature requires a new product/security/API/database decision and ADR.

## 21. Risks, Assumptions and Pending Decisions

| ID / class | Trigger and impact | Confidence | Mitigation / validation gate | Residual disposition |
|---|---|---|---|---|
| AR-002 Security, CRQ residual risk | Any internally connected caller can assert tenant/user and decrypt; compromise can disclose restricted identifiers. | High confidence; explicitly approved posture. | Internal-only loopback/nginx boundary, tenant scoping, no-store, fail-closed log, security and production gates. | HIGH residual risk remains accepted only for the approved V1 scope; architecture does not re-accept it. |
| AR-003 Privacy, PD | A retention/disposal obligation arises; no V1 purge can cause non-compliance or excess retention. | Policy evidence absent. | Preserve records without claiming legal retention; privacy/compliance authority decides before such obligation is implemented. | Latest safe point: before production use subject to such policy. |
| AR-004 Operational, EF | Existing CI uses direct SSH/SCP, mutable tags or cross-service references; release could violate deployment controls. | Verified by intake, implementation may change later. | Release/deployment phases remediate; immutable-digest, restricted-wrapper, health and stabilization gates. | Must be closed before deployment approval. |
| AR-005 Integration | RabbitMQ outage/unknown outcome creates backlog or duplicate delivery. | High confidence inherent to at-least-once delivery. | Same event ID, confirms, bounded attempts/concurrency, backlog metrics, recovery, consumer dedupe. | Duplicate delivery remains expected. |
| AR-006 Availability | Secrets/logging/geography failures deny affected operations. | High confidence by fail-closed requirements. | Readiness, bounded cache, local logging outcome, safe errors, operational runbooks and fault tests. | Intentional consistency/security-over-availability trade-off. |
| AR-007 Contract, PD | Exact pagination values in historical BR-029 trace to gate completion rather than product authority. | Requirements gate marked nonblocking. | API-contract authority confirms or defers exact values while preserving bounds, deterministic order, validation and tenant isolation. | Latest safe point: before API-contract gate approval. |
| AR-008 Data | DBML constraints, PostgreSQL 18 compatibility, or Scheme privileges fail validation; persistence could violate invariants. | Unknown until database gate. | Database-contract validation; report conflict and never auto-edit DBML. | High if found; database work remains prohibited until gate. |
| AR-009 Data/compatibility | Database-managed Scheme references unsupported application implementation key. | Credible configuration risk. | Coordinated release/migration validation, readiness and fail-unchanged operation; runtime never rewrites catalog. | Operational correction required. |
| DA-001 Assumption | The project-provided LikeC4 grammar accepts the existing custom deployment-node types. If false, model validation fails but business boundaries do not change. | Low-risk syntax assumption because no repository validator/tool is present. | The independent quality gate must execute repository-approved parse/validate/render tooling; correct syntax only, preserving semantics. | No runtime impact; executable quality-gate approval remains blocked until validation succeeds. |

The former AR-001 command-idempotency conflict is resolved by the recorded approval of Amendment 001 and MUST NOT be reintroduced as an implementation mechanism. Runtime Scheme administration is likewise removed by Amendment 002. PD AR-007 does not alter the architecture topology; AR-003 requires human/compliance authority if its trigger occurs.

## 21.1 Requirements-to-architecture traceability

| Critical effective requirement | Controlling decision | LikeC4 element/relation | Component or port boundary | Planned validation |
|---|---|---|---|---|
| FR-001, SR-001 — tenant isolation and non-disclosure | Sections 4.1/13; accepted V1 trust posture in ADR-003 | `internalConsumer -> partyRegistry.application`; `reactiveApi -> partyInputPorts/identifierInputPorts`; capabilities → tenant-scoped persistence ports | Reactive REST Adapter, Application-owned input ports, and every tenant-scoped persistence port | Cross-tenant API/persistence negative tests; architecture review of tenant-qualified methods |
| FR-008, BR-013, NFR-002 — mutation/outbox atomicity | ADR-002; sections 11/12 | capabilities → `partyPersistencePorts`/`identifierPersistencePorts`; `postgresAdapter` → those ports and `database` | Application-owned unit-of-work/output ports implemented by PostgreSQL Adapter | PostgreSQL rollback/commit integration tests proving both-or-neither |
| VR-015..018, NFR-001/003/009 — safe at-least-once publication | ADR-002; section 12.2 three-boundary sequence | `eventPublication -> outboxStorePort/eventPublisherPort`; adapters → their implemented ports; `rabbitAdapter -> rabbitMq` | Application-owned Outbox Store and Event Publisher ports | Concurrent claim, lock-release-before-I/O instrumentation, confirm/timeout/crash/restart/same-ID tests and lag load test |
| Amendment 001 FR-006/047 — permanent identifier uniqueness | ADR-004 | `identifierCapability`; `postgresAdapter -> database` | Identifier use-case port, protection port and tenant-scoped persistence port | DB unique-constraint, lifecycle, cross-tenant/scheme and concurrent-write tests |
| FR-013, SR-002/003 — protected exact lookup | ADR-003/004 | `identifierCapability -> identifierProtectionPort/identifierPersistencePorts`; protection/PostgreSQL adapters → their implemented ports | Identifier Protection port plus read-only Scheme and identifier persistence ports | HMAC tenant-separation, no-decrypt scan, plaintext-leak and exact-match tests |
| FR-014, BR-024, SR-005 — fail-closed decryption disclosure | ADR-003 | `reactiveApi -> identifierInputPorts`; `identifierCapability -> identifierProtectionPort/securityLogPort`; adapters → implemented ports | Decryption input port, protection port and Decryption Security Log port | Ordered log-before-response test, synchronous logger-failure denial, no-store and telemetry inspection |
| Amendment 002 HD-004/FR-019/040..045 — Scheme ownership | ADR-005 | `identifierCapability -> identifierPersistencePorts`; `postgresAdapter -> identifierPersistencePorts`; `postgresAdapter -> database`; absence of Scheme actor/capability | Application-owned read-only Identifier Scheme Catalog output port; no mutation input port | ArchUnit/API inventory and PostgreSQL runtime privilege tests |
| FR-015, BR-019, IR-001 — country authority/cache failure | Sections 11/12.1/15 pre-transaction resolution decision | `partyCapability -> geographicReferencePort`; `geographicAdapter -> geographicReferencePort`; `geographicAdapter -> geographicReference` | Application-owned Geographic Reference output port and anti-corruption adapter/cache | Provider contract, TTL/stale/cold-cache/timeout tests with unchanged business state plus instrumentation proving resolution completes before mutation transaction opening |
| NFR-006/008/011 — reactive execution and bounded capacity | ADR-001 | `partyRegistry.application` and all reactive external relations | Bootstrap, Reactive REST Adapter and nonblocking outbound adapters | Blocking-call detection, context propagation, startup and pilot load/saturation tests |
| OR-002..005 — rootless deployment and immutable promotion | ADR-001; sections 16/17 | `productionDeployment`, `rootlessPodman`, `reverseProxy`, `runtimeSecrets` | Bootstrap/runtime boundary; no domain/application dependency | Quadlet policy checks, digest equality, liveness/readiness/smoke and stabilization evidence |
| DR-008/009, CR-005 — DBML authority and forward-only migration | Sections 15.1/16/18; canonical PostgreSQL profile | `partyRegistry.database`; `application -> database` | PostgreSQL Adapter; Flyway remains separate migration capability | Database-contract gate, DBML comparison, forward-only Flyway validation and restore/reconciliation test |

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
- Every outbox claim transaction commits and releases locks before RabbitMQ I/O; outcome recording is a separate transaction.
- Geographic Reference resolution and every other remote interaction complete outside state-changing PostgreSQL transactions; country evidence is available before the local mutation/outbox transaction begins.
- LikeC4 explicitly models the Domain, Application use cases, core-owned input/output ports, adapters and Bootstrap with inward compile-time dependency direction.
- Critical transaction, trust, ownership and restricted-data requirements identify ADR/decision, LikeC4, port/component and validation boundaries.
- The architecture reflects the canonical supplied profiles and repository-pinned Quarkus 3.33.3 without changing build dependencies.
- Architecture remains Proposed pending independent gate evaluation.
