# Requirements Specification

## 1. Document Control

| Field | Value |
|---|---|
| Project | party-registry-service |
| Specification identifier | PRS-REQ-001 |
| Version | 0.2 |
| Status | Draft; stakeholder decisions recorded; pending independent requirements gate |
| Source intake result | `.factory/runs/oc-24279e57-a682-44b6-9ab5-2a1f253f0480/result.json` |
| Factory attempt | SPECIFICATION attempt 1 of 3 |
| Persistence authority | `docs/database/v1-scheme.dbml` |

This document incorporates the human decisions recorded in `.factory/decisions.json`. It defines requirements-level API and integration constraints but does not create detailed OpenAPI or event schemas, provide architecture design, or approve its own contents.

## 2. Problem Statement

The Party Registry pilot needs a testable behavioural baseline for maintaining tenant-scoped civil and legal identity while preserving the confirmed persistence invariants, protecting official identifiers, and making approved changes available to internal consumers. Without that baseline, consumers, architecture work, database validation, and tests cannot determine permitted operations or observable success and failure behaviour. This specification establishes that baseline without redesigning the database or prescribing internal solution structure.

## 3. Objectives

- Define the Party Registry bounded context and its evidence-backed data invariants without deriving unapproved use cases from table structure.
- Make tenant isolation, sensitive identifier protection, concurrency, outbox reliability, deployment, and quality constraints testable where sources support them.
- Incorporate the recorded stakeholder decisions for V1 operations, context handling, security posture, key management, privacy, integrations, service targets, and nationality semantics.
- Prevent persistence implementation and migration work until the confirmed DBML is independently validated.

The original factory acceptance criterion is preserved verbatim:

> Requirements and architecture are explicit, internally consistent, traceable to repository evidence, compliant with strict Clean Architecture and LikeC4 standards, and no unresolved high-risk contradiction remains before database validation.

For this phase, mandated architecture constraints are recorded below. Architecture design, LikeC4 views, ADRs, and explicit port design remain responsibilities of the later ARCHITECTURE phase. The approved reactive API constraint is an input to that phase.

## 4. Scope

### 4.1 In Scope

- Tenant-scoped Party identity records classified as natural persons or legal entities.
- Natural-person details, legal-entity details, and natural-person nationalities.
- Official Party identifiers and the global identifier-scheme catalog used to normalize and validate them.
- Tenant-scoped business-event outbox records for Party and Party Identifier aggregates.
- Logical country-code references to the Geographic Reference Service using ISO 3166-1 alpha-2 codes.
- Audit metadata, optimistic concurrency, sensitive identifier protection, and data invariants explicitly defined by the DBML.
- Local, development, staging, and production environments governed by the supplied deployment profile.
- Internal reactive REST operations under `/api/v1` for creation, ID lookup, paginated search, update, status changes, and logical deletion of approved V1 resources.
- Exact identifier search, masked identifier retrieval, and separately auditable identifier decryption.
- RabbitMQ publication of the approved V1 Party and Party Identifier event catalog through the transactional outbox.

### 4.2 Explicitly Out of Scope

The Party Registry does not own customers, suppliers, employees, user accounts, authentication, authorization, operating organizations, addresses, contacts, subscriptions, audit storage, tax configuration, ownership structures, beneficial ownership, or legal/corporate relationship management. Other services keep `party_id` as an opaque reference and do not create foreign keys to this database. The relationship between an authenticated subject and `party_id` belongs to the Access Management Service.

Database-schema changes, Flyway events, platform events, infrastructure events, and identifier-scheme administration changes are not tenant Party integration events.

### 4.3 Deferred Scope

- Detailed OpenAPI request/response schemas and detailed event payload schemas beyond the requirements-level contract in this document.
- Architecture design, LikeC4 views, ADRs, and port definitions that satisfy the approved reactive execution constraint.
- Physical database validation, persistence implementation, and forward-only migrations.
- Deployment implementation and production verification.

### 4.4 Ownership Boundaries

- Party Registry owns the in-scope identity data maintained within each tenant.
- Geographic Reference Service owns country reference data; Party Registry stores only logical country-code references.
- Keycloak authenticates accounts but is not the registry of persons.
- Access Management Service owns authenticated-subject-to-Party relationships.
- External consumers own their local opaque Party references and event-consumption idempotency.
- The VPS operator owns provision and protection of encryption and HMAC master keys; tenant-effective keys are derived from the applicable master key and tenant identifier.

## 5. Actors and External Systems

| Identifier | Actor/system | Evidence-backed interaction and boundary | Downstream detail or ownership |
|---|---|---|---|
| ACT-001 | Internal service consumer | Uses approved V1 REST operations over the `internal-services` network and supplies required tenant and user context. Party Registry trusts this context and performs no authentication or authorisation in V1. | Consumer-specific ownership remains external. |
| EXT-001 | Keycloak | Authenticates accounts outside Party Registry; it is not used during Party Registry request processing and does not own Party records. | None for V1 Party Registry processing. |
| EXT-002 | Access Management Service | Owns subject-to-`party_id` relationships and is not consulted during V1 Party Registry request processing. | None for V1 Party Registry processing. |
| EXT-003 | Geographic Reference Service | Owns country references. Party Registry validates new references through a bounded local cache with the approved freshness and failure rules. | Detailed external transport contract belongs to integration design. |
| EXT-004 | VPS operator key/secret capability | Supplies separate protected versioned master keys for authenticated encryption and HMAC outside the repository and image. | Operational key material is never part of requirements evidence. |
| EXT-005 | RabbitMQ and event consumer | RabbitMQ receives persistent approved events with publisher confirms; consumers own queues and deduplicate at-least-once deliveries by event ID. | Detailed payload schemas remain a downstream contract artifact. |
| ACT-002 | Identifier-scheme administrator consumer | Uses the internal V1 operations to maintain the global validation catalog. Party Registry applies no role check; scheme changes are not tenant outbox events. | Consumer-side access governance is externally owned. |

Geographic Reference Service unavailability follows BR-019. RabbitMQ delivery follows the approved lag targets and at-least-once retry semantics; a failed publication does not roll back an already committed business transaction and remains represented by outbox delivery state.

## 6. Glossary

| Term | Meaning |
|---|---|
| Party | A tenant-owned identity record for exactly one natural person or legal entity; not a commercial role, user, tenant, or operating company. |
| Party Identifier | An official identifier associated with a Party and governed by an Identifier Scheme. |
| Identifier Scheme | Global metadata naming the normalization and validation rules applicable to an official identifier. It contains versioned rule keys, not executable code. |
| Tenant context | The mandatory canonical UUID supplied as `tenant-id`, trusted without authentication in V1, and used to isolate all tenant-owned operations. |
| Process ID | The canonical UUID supplied as `process-id` or generated when absent and used for correlation and sensitive-operation audit evidence. |
| Active nationality | A nationality whose `valid_until` is null. Assigning any `valid_until` ends it immediately. |
| Outbox event | A tenant-scoped business-event record committed atomically with its business mutation for later at-least-once publication. |
| Aggregate version | A non-negative value used to detect conflicting mutations and identify event ordering within an aggregate. |

## 7. Business Rules

| ID | Normative rule | Source | Related requirements / criteria |
|---|---|---|---|
| BR-001 | Every tenant-owned Party and Party Identifier MUST be isolated by `tenant_id`; a reference to a Party from another service MUST remain opaque and MUST NOT be enforced by a cross-service database foreign key. | DBML lines 5-24, 97-115, 269-301, 408 | FR-001, DR-001, SR-001; AC-001 |
| BR-002 | A Party MUST have exactly one type: `NATURAL_PERSON` or `LEGAL_ENTITY`; its detail kind MUST match that type, and both detail kinds MUST NOT coexist. | DBML lines 42-45, 121-157, 180-184 | FR-002, DR-002; AC-002 |
| BR-003 | A death or dissolution date MUST NOT precede its corresponding birth or incorporation date when both dates exist. | DBML lines 145-147, 175-177 | VR-001; AC-003 |
| BR-004 | Country codes stored by this bounded context MUST use two uppercase ISO 3166-1 alpha-2 characters and remain logical references to Geographic Reference Service data. | DBML lines 20-21, 137-147, 166-177, 191-208, 226-250 | VR-002, IR-001; AC-004 |
| BR-005 | Nationality records MUST belong only to natural-person Parties. A nationality is active exactly when `valid_until` is null. No Party MAY have duplicate active rows for the same country or more than one active primary nationality. | DBML lines 187-219; OD-009 | FR-003, DR-003; AC-005 |
| BR-006 | An Identifier Scheme's code, issuing country, category, and applicable subject type MUST become immutable after the scheme is activated. | DBML lines 222-266 | FR-004; AC-006 |
| BR-007 | Identifier input MUST be normalized and validated using the versioned keys of its scheme, satisfy configured positive length bounds, and be compatible with the Party type. | DBML lines 222-255, 269-325 | FR-005, VR-003; AC-007 |
| BR-008 | A tenant MUST NOT have more than one `PENDING_VERIFICATION` or `VERIFIED` Party Identifier with the same scheme and normalized-value fingerprint. A Party MUST NOT have more than one primary `VERIFIED` identifier for one scheme. | DBML lines 297-323 | FR-006, DR-004; AC-008 |
| BR-009 | A `VERIFIED` identifier MUST record verifier and verification time; an `EXPIRED` identifier MUST record an expiry date; expiry MUST NOT precede issue date. | DBML lines 283-309 | VR-004; AC-009 |
| BR-010 | Complete identifier values MUST be protected with authenticated encryption and MUST NOT be persisted in plaintext. Exact lookup and active uniqueness MUST use a tenant-isolated HMAC-SHA-256 fingerprint. | DBML lines 23-24, 277-281 | SR-002, SR-003; AC-010 |
| BR-011 | Every mutable business record MUST record creation and update timestamps and the opaque `user-id` or service-principal identifier supplied by the caller. Aggregate roots MUST use non-negative optimistic-concurrency versions. For V1, the recorded actor identifier is trusted but not authenticated, as explicitly decided in OD-002 notwithstanding the DBML field note. | DBML lines 30-32 and mutable table definitions; OD-002 precedence | FR-007, DR-005; AC-011 |
| BR-012 | A mutation to Party details or nationalities MUST increment the Party aggregate version in the same transaction. | DBML lines 121-127, 383-386 | FR-007; AC-011 |
| BR-013 | An approved tenant business mutation requiring publication and its outbox record MUST commit in one PostgreSQL transaction. Publication MUST be at least once, and consumers MUST deduplicate using the outbox event ID. | DBML lines 331-397 | FR-008, IR-002; AC-012, AC-013 |
| BR-014 | Public integration events MUST NOT contain complete decrypted identifiers and MUST contain no unnecessary personal data. | DBML lines 327-328, 339-342 | SR-004, IR-003; AC-014 |
| BR-015 | V1 deletion MUST be logical: Party to `ARCHIVED`, Identifier Scheme to `RETIRED`, Party Identifier to `REVOKED`, and nationality by assigning `valid_until`. Party details MUST NOT be deleted independently. Archived Parties, retired schemes, and revoked identifiers MUST NOT be reactivated in V1. | OD-001 | FR-010; AC-018 |
| BR-016 | Every business operation MUST receive a canonical UUID `tenant-id` and a non-empty `user-id` of at most 128 characters. `process-id` MUST be a canonical UUID when supplied and MUST be generated when absent. V1 MUST trust these values without authenticating them and MUST use `tenant-id` for isolation and `user-id` for record audit fields. | OD-002 | FR-011, SR-001; AC-019 |
| BR-017 | Every modification of an existing versioned aggregate MUST require an `If-Match` value equal to the current aggregate version. Absence MUST produce HTTP 428; mismatch MUST produce HTTP 412 with a stable domain version-conflict code; neither case may change state. | OD-005 | FR-012; AC-020 |
| BR-018 | `valid_from` and `valid_until` MUST NOT be future dates. `valid_until` MUST NOT precede `valid_from`; assigning it ends the nationality immediately. V1 MUST NOT schedule nationality activation or termination. | OD-009 | FR-003, VR-009; AC-005 |
| BR-019 | New country references MUST resolve to an active Geographic Reference Service code. Party Registry MUST use a 24-hour in-memory cache TTL and MAY use cached data up to seven days old during dependency unavailability. With no usable cache, a country-dependent write MUST fail with HTTP 503 and a retryable stable code. Existing stored historical codes MUST remain valid. | OD-006 | FR-015, IR-001; AC-023 |
| BR-020 | V1 MUST perform no physical deletion or automatic purge. Archived, revoked, expired, and ended records and their associated audit metadata MUST remain stored. This operational behaviour MUST NOT be represented as a definitive legal retention policy. | OD-004 | DR-007; AC-024 |
| BR-021 | A complete identifier value MAY be returned only by the separate decryption operation. Every decryption MUST record tenant ID, user ID, process ID, and Party Identifier ID, and MUST NOT record the decrypted value. | OD-003/005 | FR-014, SR-005; AC-022 |

The approved lifecycle endpoints are the logical transitions in BR-015 plus the state changes represented by the approved V1 event catalog in OD-005. Every transition MUST preserve the DBML state invariants; no unlisted reactivation behaviour is permitted.

## 8. Functional Requirements

These requirements define V1 observable obligations. Detailed transport schemas remain downstream contract artifacts and MUST conform to them.

- **FR-001 — Tenant boundary:** WHEN an operation accesses tenant-owned Party, Party Identifier, nationality, or event-related data, THE SYSTEM MUST limit the operation to the supplied tenant context and MUST NOT disclose whether another tenant holds a matching record. A resource outside that tenant MUST produce HTTP 404 through tenant-scoped lookup. Source: BR-001 and OD-002.
- **FR-002 — Party kind consistency:** WHEN an approved operation creates or changes Party identity details, THE SYSTEM MUST preserve exactly one Party type and the matching detail kind and MUST reject a conflicting detail kind. Source: BR-002.
- **FR-003 — Nationality consistency:** WHEN an operation maintains a nationality, THE SYSTEM MUST preserve BR-005 and BR-018, including natural-person applicability, active uniqueness, non-future dates, and immediate ending when `valid_until` is assigned. Source: BR-005, BR-018.
- **FR-004 — Scheme immutability:** WHEN an approved administration operation attempts to change an activated scheme's immutable attributes, THE SYSTEM MUST reject the change without modifying the scheme. Source: BR-006.
- **FR-005 — Identifier validation:** WHEN an approved operation submits an identifier, THE SYSTEM MUST apply the selected scheme's versioned normalization and validation rules and verify subject-type compatibility before accepting it. Source: BR-007.
- **FR-006 — Identifier uniqueness:** WHEN an approved operation would violate either uniqueness rule in BR-008, THE SYSTEM MUST reject the operation without creating or changing an identifier into a conflicting state. Source: BR-008.
- **FR-007 — Concurrency and audit:** WHEN a mutable record is changed, THE SYSTEM MUST record the responsible opaque subject or service principal and update time. WHEN an aggregate mutation supplies a stale version, THE SYSTEM MUST reject that mutation without overwriting the intervening change. Party component changes MUST increment the Party version atomically. Source: BR-011 and BR-012.
- **FR-008 — Reliable event recording:** WHEN a mutation represented in the approved V1 Party or Party Identifier event catalog succeeds, THE SYSTEM MUST either commit both the mutation and one corresponding outbox event or commit neither. Source: BR-013 and OD-005.
- **FR-009 — Outbox delivery state:** WHEN an outbox event is processed, THE SYSTEM MUST preserve the DBML delivery-state invariants: a published event has a publication time, a non-published event has none, and a failed event has an error code. Source: DBML lines 331-374. Acceptance: AC-016.
- **FR-010 — Party operations:** THE SYSTEM MUST provide internal operations to create, look up by ID, search with pagination, update, change the valid DBML Party status, and archive a Party. An archived Party MUST be terminal in V1. Source: OD-001. Acceptance: AC-018.
- **FR-011 — Request context:** WHEN a business operation is requested, THE SYSTEM MUST validate and apply BR-016 before accessing business data. Source: OD-002. Acceptance: AC-019.
- **FR-012 — Conditional modification:** WHEN an existing aggregate is modified, THE SYSTEM MUST enforce BR-017 against its current version and MUST return the updated quoted version as `ETag` after success. Source: OD-005. Acceptance: AC-020.
- **FR-013 — Exact identifier search:** WHEN exact search receives a scheme and plaintext identifier, THE SYSTEM MUST normalize the input, compute the tenant-effective HMAC fingerprint, and return only matching references and masked values without scanning or decrypting stored records. Source: OD-003/005. Acceptance: AC-021.
- **FR-014 — Identifier decryption:** WHEN decryption is requested by Party Identifier ID within the supplied tenant, THE SYSTEM MUST return the complete value only through that separate operation, set `Cache-Control: no-store`, and create the audit evidence in BR-021. Source: OD-003/005. Acceptance: AC-022.
- **FR-015 — Country validation:** WHEN a write introduces a country reference, THE SYSTEM MUST apply BR-019; dependency failure MUST NOT invalidate historical stored codes. Source: OD-006. Acceptance: AC-023.
- **FR-016 — Party-detail operations:** THE SYSTEM MUST provide internal operations to create, retrieve, and update the detail record matching a Party type. Details MUST NOT have an independent deletion or lifecycle operation and MUST follow their Party lifecycle. Source: OD-001. Acceptance: AC-018.
- **FR-017 — Nationality operations:** THE SYSTEM MUST provide internal operations to add, retrieve, search with pagination, update, and end nationalities under BR-005 and BR-018. Source: OD-001/009. Acceptance: AC-005, AC-018.
- **FR-018 — Party Identifier operations:** THE SYSTEM MUST provide internal operations to create, look up by ID, look up by Party and scheme, search with pagination, update, change to a valid DBML status, and revoke a Party Identifier. Lookup by Party and scheme MUST support returning all matches or the verified primary when explicitly requested. A revoked identifier MUST be terminal in V1. Source: OD-001/005. Acceptance: AC-018.
- **FR-019 — Identifier Scheme operations:** THE SYSTEM MUST provide internal operations to create, look up by ID, search with pagination, update, change to a valid DBML status, and retire an Identifier Scheme. A retired scheme MUST be terminal in V1 and scheme administration MUST NOT create tenant Party outbox events. Source: OD-001/005. Acceptance: AC-006, AC-018.
- **FR-020 — Outbox boundary:** THE SYSTEM MUST NOT expose public CRUD operations for outbox records; delivery-state processing is internal infrastructure. Source: OD-001/008. Acceptance: AC-018.

## 9. Validation and Error Requirements

- **VR-001:** The system MUST reject inconsistent life dates described by BR-003.
- **VR-002:** The system MUST reject a supplied country code that is not exactly two uppercase characters. A syntactically valid new reference MUST also satisfy BR-019.
- **VR-003:** Configured minimum and maximum identifier lengths MUST be positive; maximum MUST NOT be less than minimum. Identifier values MUST satisfy applicable configured bounds and versioned normalization/validation rules before acceptance.
- **VR-004:** The system MUST reject identifier state data that violates BR-009.
- **VR-005:** Normalization versions, encryption-key versions, and event-schema versions MUST be positive; aggregate versions and publication-attempt counts MUST be non-negative.
- **VR-006:** Validation or concurrency failure MUST leave the affected business aggregate and its outbox record unmodified as one atomic outcome.
- **VR-007:** REST responses MUST use the approved `ApiResponse` envelope with `status`, `code`, `data`, and pagination metadata when applicable. Errors MUST use stable codes and MUST NOT include stack traces or complete identifiers. HTTP 404, 412, 428, and 503 MUST be used in the circumstances defined by FR-001, BR-017, and BR-019.
- **VR-008:** Duplicate event delivery is expected under at-least-once delivery. Consumers MUST treat repeated event IDs idempotently. No general client-command idempotency obligation is approved for V1.
- **VR-009:** The system MUST reject future nationality validity dates and a `valid_until` earlier than `valid_from`. Assigning a current or past valid `valid_until` MUST immediately end the nationality.
- **VR-010:** The system MUST reject a missing or non-canonical `tenant-id`, a missing, empty, or over-128-character `user-id`, and a supplied non-canonical `process-id` before business data is accessed.

## 10. Data Requirements

- **DR-001:** Tenant-owned data MUST preserve tenant identity and tenant-scoped references as specified by BR-001.
- **DR-002:** Party identity data MUST preserve the exclusive Party-type/detail relationship in BR-002.
- **DR-003:** Natural-person nationality data consists of country, primary designation, optional validity dates, and audit metadata and MUST preserve BR-005.
- **DR-004:** Party Identifier data MUST preserve scheme, tenant, Party, protected value, key and normalization versions, safe masked representation, primary designation, status, relevant dates, verification evidence, audit metadata, and aggregate version as defined by the DBML.
- **DR-005:** All mutable business data MUST preserve the audit and version facts in BR-011. Audit storage outside these record-level fields is explicitly not owned by this service.
- **DR-006:** Outbox data MUST preserve event identity, tenant, aggregate identity and version, versioned event type/schema, minimal payload, occurrence time, optional correlation/causation, delivery state, attempt evidence, audit metadata, and delivery version.
- **DR-007:** Complete identifiers MUST be classified restricted; names, personal dates, and nationalities confidential; UUIDs, tenant identifiers, and masked identifier values internal. V1 MUST retain records under BR-020. Lawful basis and jurisdiction-specific obligations remain owned by registering systems and organisations rather than Party Registry.
- **DR-008:** Physical tables, columns, types, constraints, indexes, and relationships are governed exclusively by the DBML and MUST NOT be redesigned in requirements or implementation.
- **DR-009:** The DBML is human-confirmed for independent validation against PostgreSQL 18. Persistence implementation and Flyway migration generation MUST remain prohibited until database-contract validation is satisfactory. Later migrations MUST be immutable and forward-only under the supplied PostgreSQL profile.

## 11. API and Integration Requirements

- **IR-001:** Country references MUST use ISO 3166-1 alpha-2 logical identifiers, MUST NOT create a remote database foreign key, and MUST follow BR-019's cache and dependency-failure semantics.
- **IR-002:** Approved Party and Party Identifier business events MUST use stable event IDs and versioned business event types and schema versions, support at-least-once delivery, and identify tenant and aggregate version.
- **IR-003:** Event payloads MUST meet BR-014 and the approved V1 catalog in OD-005. Publication MUST use RabbitMQ persistent messages through a durable topic exchange with publisher confirms. Consumers MUST own their queues. Event schema evolution MUST use new versioned event types rather than mutating a published version.
- **IR-004:** Identifier-scheme administration, schema, migration, platform, and infrastructure changes MUST NOT be published through the tenant Party outbox.
- **IR-005:** The internal reactive REST API MUST be rooted at `/api/v1`, documented through OpenAPI, and use the response, context, conditional-request, lookup, exact-search, and decryption semantics in this specification. Detailed paths and schemas MUST be defined later without changing these requirements.

## 12. Security and Privacy Requirements

- **SR-001:** Every tenant-owned operation MUST use the caller-supplied `tenant-id` for reads, writes, lookups, concurrency checks, and events. V1 intentionally trusts this value and `user-id` without authentication or authorisation; it MUST NOT accept a different tenant identifier in the body. Tenant predicates MUST be included in searches and mutations.
- **SR-002:** Complete identifier values MUST be protected by authenticated encryption before persistence. Plaintext MAY enter only identifier submission, exact-search request processing, and the separate decryption response; it MUST NOT be persisted, logged, measured, traced, emitted to RabbitMQ, or returned in searches or listings. V1 applies the access posture in SR-006 rather than principal authorisation.
- **SR-003:** Exact identifier lookup and active uniqueness MUST use a tenant-isolated HMAC-SHA-256 fingerprint derived from the active HMAC master key and `tenant-id`; plaintext and unkeyed hashes MUST NOT be used. Encryption and HMAC keys MUST be separate, supplied through a protected VPS-managed environment file or secret, mounted at runtime, and excluded from repository and image. V1 MUST use one active HMAC version; HMAC rotation MUST occur in a maintenance window and recalculate all fingerprints before activation. Prior encryption keys MUST remain available until re-encryption completes.
- **SR-004:** Only masked identifier representations and the minimum personal data approved for a business contract MAY leave the service in integration events.
- **SR-005:** Creation and update changes MUST retain the supplied `user-id`; decryption MUST retain BR-021 evidence. Because V1 does not authenticate the caller, these fields are caller-asserted identifiers and MUST NOT be represented as cryptographic proof of actor identity.
- **SR-006:** V1 MUST NOT authenticate or authorise requests, integrate Keycloak or Access Management during request processing, apply role/permission checks, or return HTTP 401/403. Any consumer connected to the internal network MAY invoke decryption. These are explicitly accepted V1 boundaries, not claims of caller identity assurance.
- **SR-007:** The system MUST enforce the classifications and minimisation in DR-007, MUST NOT add personal data beyond DBML-defined data to logs, traces, metrics, or events, and MUST perform no physical deletion or automatic purge in V1.

## 13. Non-Functional Requirements

- **NFR-001 — Concurrency:** Optimistic concurrency MUST ensure that a stale aggregate version cannot overwrite a newer aggregate state. Concurrent outbox processing MUST prevent two publishers from claiming the same pending record at the same time while retaining at-least-once delivery semantics. Source: DBML lines 30-32, 109, 295, 359, 379-397.
- **NFR-002 — Transactional reliability:** The business mutation and required outbox insertion MUST have all-or-nothing durability. Source: BR-013.
- **NFR-003 — Event reliability:** Event delivery MUST be at least once; event consumers MUST be able to deduplicate using event ID. Publication lag MUST meet NFR-009. Source: BR-013 and OD-007.
- **NFR-004 — Technology constraints:** Production code MUST use Java 25 and Quarkus, and build configuration MUST use Gradle Kotlin DSL. Source: manifest lines 14-19 and supplied quarkus-java25 profile.
- **NFR-005 — Architecture constraints:** The system MUST comply with strict Clean Architecture: domain behaviour remains framework-independent, dependencies point inward, external capabilities are separated through explicit ports, and bounded-context ownership remains explicit. Architecture design and evidence MUST later include LikeC4 context, container, and deployment views. Source: manifest lines 8-12 and supplied strict Clean Architecture profile.
- **NFR-006 — Execution model:** The REST API MUST use a reactive execution model. Blocking calls, if any approved adapter requires them, MUST be isolated in accordance with the stack profile. Source: OD-005 and supplied quarkus-java25 profile.
- **NFR-007 — Quality evidence:** Downstream gates MUST include applicable unit, integration, architecture, API-contract, security, and performance validation against this baseline. Source: supplied quarkus-java25 profile and intake required actions.
- **NFR-008 — Pilot load profile:** On one instance allocated 1 CPU and 512 MiB, with 100,000 Parties, 300,000 identifiers, and 200,000 nationalities, the system MUST sustain 20 requests/second for 15 minutes at concurrency 50. Successful reads MUST have p95 no greater than 500 ms, writes p95 no greater than 1 second, exact searches p95 no greater than 750 ms, and all requests p99 no greater than 2 seconds. Unexpected technical errors MUST remain below 1% during the run. These are pilot validation targets, not contractual SLAs. Source: OD-007.
- **NFR-009 — Event delivery target:** Under the pilot baseline, RabbitMQ publication lag MUST have p95 no greater than 60 seconds and p99 no greater than 5 minutes. Source: OD-007.
- **NFR-010 — Recovery target:** The pilot operational baseline MUST support daily backups, RPO no greater than 24 hours, and RTO no greater than 8 hours. Restore tests MUST run on a documented recurring schedule and retain evidence; no exact recurrence interval was approved. Source: OD-007 and PostgreSQL profile.
- **NFR-011 — Startup target:** Readiness MUST be achieved within 60 seconds after startup under the approved pilot environment. Source: OD-007.

## 14. Compatibility Requirements

- **CR-001:** Other services MUST treat `party_id` as opaque and MUST NOT depend on this database through foreign keys.
- **CR-002:** Event type and schema version MUST identify the business-event contract version. A published event version MUST remain immutable; an incompatible event change MUST use a new versioned event type.
- **CR-003:** Identifier normalization and encryption-key versions MUST remain identifiable for existing records. Prior encryption versions MUST remain usable until re-encryption completes; HMAC rotation MUST follow SR-003.
- **CR-004:** No breaking V1 REST or event-contract change is authorised. The detailed OpenAPI and event schemas established downstream MUST preserve this requirements baseline.
- **CR-005:** Later persistence changes MUST conform to confirmed DBML and use immutable forward-only migrations; rollback MUST NOT assume database reversal.

## 15. Operational and Deployment Requirements

- **OR-001:** Deployment MUST target the manifest environments: local, development, staging, and production.
- **OR-002:** VPS deployment MUST use the supplied Podman Quadlet profile's rootless, restricted mechanism and loopback service binding; unrestricted direct host mutation is not an approved deployment interface.
- **OR-003:** The same immutable OCI image digest MUST be promoted across environments without rebuilding.
- **OR-004:** Environment promotion and production verification MUST include liveness, readiness, smoke checks, and a 10-minute stabilization window. Readiness MUST meet NFR-011 before the stabilization window is considered successful.
- **OR-005:** Rollback MUST be approval-governed, MUST preserve evidence, and MUST account for forward-only database changes rather than assuming migration reversal.
- **OR-006:** Operations MUST provide structured logs, correlation by process ID, latency and error metrics, PostgreSQL connection-pool usage, outbox size and age, RabbitMQ retry metrics, and geographic-cache status. Liveness and readiness signals MUST be exposed. Telemetry MUST comply with SR-002 and SR-004.
- **OR-007:** The intake-identified workflow behaviour using direct SSH/SCP, mutable image tags, cross-service deployment references, and absent verification controls MUST NOT be treated as an approved operational contract.
- **OR-008:** Daily backups and recurring restore tests MUST retain evidence sufficient to verify NFR-010. The operator MUST document the restore-test recurrence before production operation.

## 16. Acceptance Criteria

- **AC-000 (original factory criterion):** GIVEN the requirements and later architecture artifacts are presented for database validation, WHEN their traceability and consistency are reviewed, THEN they are explicit, internally consistent, traceable to repository evidence, compliant with strict Clean Architecture and LikeC4 standards, and contain no unresolved high-risk contradiction. The requirements portion is addressed by this draft; LikeC4 architecture evidence remains assigned to the architecture phase.
- **AC-001 (FR-001):** GIVEN two tenants have records, WHEN a caller supplying one tenant ID attempts any supported read, lookup, mutation, or event access using the other tenant's resource identifier, THEN no other-tenant data is returned or changed and HTTP 404 does not disclose record existence.
- **AC-002 (FR-002):** GIVEN a Party has one approved type, WHEN matching details are accepted, THEN only matching details exist; WHEN conflicting or dual details are submitted, THEN the operation is rejected with no aggregate change.
- **AC-003 (VR-001):** GIVEN both lifecycle dates are supplied, WHEN death precedes birth or dissolution precedes incorporation, THEN the operation is rejected; equal dates and chronological dates satisfy this rule.
- **AC-004 (VR-002):** GIVEN a country-code field is supplied, WHEN its value is not exactly two uppercase characters, THEN it is rejected; a syntactically valid new reference proceeds to the BR-019 geographic validation.
- **AC-005 (FR-003):** GIVEN a natural-person Party has a nationality with null `valid_until`, WHEN another active row for the same country or another active primary nationality would be accepted, THEN it is rejected; WHEN a non-future `valid_until` not earlier than `valid_from` is assigned, THEN the nationality immediately becomes inactive and remains as history.
- **AC-006 (FR-004):** GIVEN an Identifier Scheme is active, WHEN an operation attempts to alter code, issuing country, category, or applicable subject type, THEN the change is rejected and the stored scheme is unchanged.
- **AC-007 (FR-005):** GIVEN an identifier and selected scheme, WHEN subject type, normalized length, or the versioned validator is incompatible, THEN no Party Identifier is accepted; WHEN all checks pass, it may proceed under an approved use case.
- **AC-008 (FR-006):** GIVEN an active tenant identifier fingerprint already exists for a scheme, WHEN a duplicate would enter pending or verified state, THEN it is rejected; GIVEN a verified primary exists for a Party and scheme, WHEN another would become verified primary, THEN it is rejected.
- **AC-009 (VR-004):** GIVEN an identifier is moved to verified state without verifier/time, expired state without expiry, or has expiry before issue, WHEN the change is attempted, THEN it is rejected atomically.
- **AC-010 (SR-002/SR-003):** GIVEN a complete identifier is accepted, WHEN persistence and observable telemetry/event outputs are inspected, THEN the complete plaintext is absent, ciphertext records a positive key version, exact lookup uses a tenant-isolated HMAC-SHA-256 fingerprint, and only an approved mask is externally observable.
- **AC-011 (FR-007):** GIVEN two mutations use the same aggregate version, WHEN one commits first, THEN the second is rejected as stale without overwriting it; a committed Party component change updates audit facts and increments the Party version in that same transaction.
- **AC-012 (FR-008):** GIVEN an approved mutation requires an event, WHEN commit succeeds, THEN both business state and exactly one corresponding outbox record are committed; WHEN either write fails, THEN neither is committed.
- **AC-013 (IR-002/NFR-003):** GIVEN the same event is delivered more than once, WHEN a conforming consumer processes it, THEN the event ID permits duplicate detection and no duplicate consumer business effect occurs.
- **AC-014 (SR-004/IR-003):** GIVEN any public integration event, WHEN its payload is inspected, THEN it contains no complete decrypted identifier and no personal attributes beyond the approved versioned event contract.
- **AC-015 (OR-003/OR-004):** GIVEN an artifact is promoted, WHEN environment evidence is reviewed, THEN each environment references the same immutable digest, readiness occurred within 60 seconds, and liveness/readiness, smoke, and the 10-minute stabilization window passed before further promotion.
- **AC-016 (FR-009):** GIVEN an outbox event delivery-state change, WHEN the event becomes published, THEN it has a publication time; WHEN it is not published, THEN it has no publication time; and WHEN it becomes failed, THEN it has an error code. A state change violating any of these conditions is rejected without changing the event.
- **AC-017 (VR-005):** GIVEN a mutation supplies a normalization version, encryption-key version, event-schema version, aggregate version, or publication-attempt count, WHEN a required-positive value is zero or negative or a required-non-negative value is negative, THEN the mutation is rejected without changing business or outbox state; the respective boundary values of one and zero satisfy this numeric rule.
- **AC-018 (FR-010/016-020, BR-015):** GIVEN the V1 resource operations, WHEN contract scenarios are exercised, THEN Parties support create, ID lookup, paginated search, update, valid status change, and archive; matching details support create, retrieve, and update but no independent deletion; nationalities support add, retrieve, paginated search, update, and immediate end; Party Identifiers support create, ID lookup, Party-and-scheme lookup with all/verified-primary selection, paginated search, update, valid status change, and revoke; Identifier Schemes support create, ID lookup, paginated search, update, valid status change, and retire. Archived Parties, retired schemes, and revoked identifiers cannot reactivate, and no outbox CRUD operation is exposed.
- **AC-019 (FR-011):** GIVEN a business request, WHEN `tenant-id` or `user-id` is missing or invalid or a supplied `process-id` is not a canonical UUID, THEN the request is rejected before business-data access; WHEN valid context is supplied and `process-id` is absent, THEN the system generates one and uses the supplied tenant and user values for isolation and audit.
- **AC-020 (FR-012):** GIVEN an existing versioned aggregate, WHEN modification omits `If-Match`, THEN HTTP 428 is returned; WHEN it differs from the current version, THEN HTTP 412 and the stable version-conflict code are returned; in both cases state is unchanged. A successful matching modification returns the new quoted version as `ETag`.
- **AC-021 (FR-013):** GIVEN a scheme and plaintext identifier for exact search, WHEN a match exists in the supplied tenant, THEN only references and masked values are returned and evidence confirms lookup used normalized tenant-effective HMAC without scanning or decrypting stored values; another tenant's equal plaintext produces no disclosed match.
- **AC-022 (FR-014):** GIVEN a Party Identifier in the supplied tenant, WHEN decryption succeeds, THEN the response has `Cache-Control: no-store`, only the separate operation returns plaintext, and audit evidence contains tenant ID, user ID, process ID, and Party Identifier ID but not plaintext; an identifier outside the tenant produces HTTP 404.
- **AC-023 (FR-015):** GIVEN a new country reference, WHEN an active code is available from fresh cache or Geographic Reference Service, THEN it is accepted; WHEN the dependency is unavailable, cached data no older than seven days may be used, otherwise HTTP 503 with a retryable stable code is returned and no write occurs. A stored historical code remains valid.
- **AC-024 (BR-020/DR-007):** GIVEN logical deletion or record expiry/end, WHEN persisted state and audit evidence are inspected, THEN no business record was physically deleted or automatically purged and its audit metadata remains associated.
- **AC-025 (NFR-008/009):** GIVEN the approved pilot instance, dataset, 15-minute duration, throughput, and concurrency, WHEN performance evidence is collected, THEN every p95/p99, technical-error-rate, and RabbitMQ publication-lag target in NFR-008 and NFR-009 is met.
- **AC-026 (NFR-010/OR-008):** GIVEN production backup and restore evidence, WHEN the operational baseline is reviewed, THEN backups occur daily, the documented recurring restore test succeeds, recoverable data meets the 24-hour RPO, and recovery completes within the 8-hour RTO.
- **AC-027 (SR-001/SR-006):** GIVEN an internal request with syntactically valid caller-supplied context and no authentication credential, WHEN any supported operation including decryption is invoked, THEN Party Registry applies no authentication or role decision and does not return HTTP 401/403; tenant scoping and all applicable business validation still apply.
- **AC-028 (VR-007/IR-005):** GIVEN each success, pagination, validation, concurrency, not-found, and dependency-failure scenario, WHEN its REST response is inspected, THEN it uses the approved `ApiResponse` fields, uses pagination metadata when applicable, emits the required stable code/status semantics, and contains neither a stack trace nor complete identifier.
- **AC-029 (IR-002/IR-003):** GIVEN a committed approved event, WHEN RabbitMQ publication evidence is inspected, THEN the message is persistent, uses the durable topic exchange, carries stable event ID, tenant, aggregate version, event type and schema version, and is acknowledged with publisher-confirm handling; redelivery retains the same event ID for consumer deduplication.

## 17. Assumptions

No low-risk assumption defines business behaviour. The repository path and supplied invocation metadata are treated as invocation facts; material requirements are supported by the manifest, DBML, profiles, intake evidence, or recorded human decisions.

## 18. Unknowns and Decision Status

No unresolved material requirements decision was identified after applying the recorded human decision that approves OD-001 through OD-009 as written below. Detailed OpenAPI schemas, event payload schemas, architecture models, ports, ADRs, physical database validation, and implementation details are deliberate downstream artifacts rather than unresolved business requirements.

The approved V1 posture intentionally provides no in-service authentication or authorisation and retains no automatic purge. These are explicit decisions, not assumptions. Any expansion beyond the internal network or any later legal retention policy requires a new authoritative decision and specification change.

## 18.1 Resolved Human Decisions

| ID | Decision | Approved by | Date |
| --- | --- | --- | --- |
| OD-001 | **Actors, operations, and lifecycles**<br><br>Party Registry Service V1 will be an internal microservice consumed by any service with connectivity to `internal-services`. It will expose full CRUD over Parties, natural person or legal entity details, nationalities, identifiers, and Identifier Schemes through application use cases; the API will not expose the tables directly.<br><br>All deletion will be logical:<br><br>- Party → ARCHIVED.<br>- Identifier Scheme → RETIRED.<br>- Party Identifier → REVOKED.<br>- Nationality → assignment of `valid_until`.<br>- Natural Person Details and Legal Entity Details are not deleted independently; they follow the lifecycle of their Party.<br><br>`party_outbox_events` is internal infrastructure and will not have public CRUD.<br><br>Creation, lookup by ID, paginated search, update, status changes, and logical deletion will be supported. Transitions must preserve the DBML invariants. An archived Party, a retired scheme, and a revoked identifier cannot be reactivated in V1. | Alex | 2026-08-09 |
| OD-002 | **Internal context without security**<br><br>Party Registry Service V1 will not implement authentication, authorization, roles, or permissions. It will not integrate Keycloak or Access Management during request processing and will not produce 401 or 403 responses.<br><br>All business operations will require:<br><br>- `tenant-id`: mandatory canonical UUID.<br>- `user-id`: mandatory, non-empty identifier with a maximum of 128 characters.<br>- `process-id`: optional canonical UUID; the service will generate it when it is not provided.<br><br>The service will fully trust `tenant-id` and `user-id`. `tenant-id` will be the source of data isolation and a different tenant will never be accepted inside the body. `user-id` will be used as `created_by` and `updated_by`. Searches and mutations will always include the tenant in their persistence conditions.<br><br>If an identifier does not belong to the indicated tenant, a 404 response will be returned as a consequence of query scoping, not as an authorization control.<br><br>The separate decryption operation may be invoked by any consumer connected to the internal network; Party Registry will not apply additional permissions. | Alex | 2026-08-09 |
| OD-003 | **Encryption and keys for the VPS**<br><br>V1 will use a simple key solution managed by the VPS operator. Keys will be supplied through an environment file or protected secret with restrictive permissions, mounted in the container and excluded from the repository and the image.<br><br>There will be separate keys for:<br><br>- Authenticated encryption of the full value.<br>- HMAC-SHA-256 used for exact search and uniqueness.<br><br>Effective keys will be isolated by tenant through derivation from the master key and `tenant-id`. The encrypted value will store the required nonce, ciphertext, and tag. `encryption_key_version` will identify the encryption key used.<br><br>V1 will maintain a single active HMAC version. It will not support online HMAC rotation. A rotation must be executed during a maintenance window, recalculating all fingerprints before activating the new key. Previous encryption versions will remain available until re-encryption is completed.<br><br>The plaintext:<br><br>- Will never be written to PostgreSQL.<br>- Will never appear in logs, metrics, traces, or RabbitMQ.<br>- Will never be included in searches or listings.<br>- Will only be returned through the separate decryption operation.<br><br>Each decryption will record `tenant-id`, `user-id`, `process-id`, and `party_identifier_id`, but never the decrypted value. | Alex | 2026-08-09 |
| OD-004 | **Privacy and retention for V1**<br><br>Full identifiers are classified as restricted data. Names, personal dates, and nationalities are classified as confidential. UUIDs, tenant, and masked representations are considered internal data.<br><br>Party Registry stores only the data defined by its DBML. It will not add additional personal information to logs, traces, metrics, or events.<br><br>In V1:<br><br>- There will be no automatic purge.<br>- No physical deletion will be performed.<br>- No arbitrary retention period will be introduced.<br>- Parties are archived, identifiers are revoked or expire, and nationalities are ended through `valid_until`.<br>- Audit data remains associated with the record.<br>- Corrections or requests regarding data will be initiated by internal consumer systems through normal operations.<br><br>The absence of purging is the approved technical behavior for V1, but it does not represent an indefinite retention policy. A later governance, privacy, or compliance policy may introduce purge processes without altering the main domain rules.<br><br>Party Registry does not determine the legal basis nor apply jurisdiction-specific rules; that responsibility belongs to the systems and organizations that register the data.<br><br>This formulation allows the factory to move forward without stating that a definitive legal retention policy exists. | Alex | 2026-08-09 |
| OD-005 | **REST, concurrency, and RabbitMQ**<br><br>The API will be reactive REST under `/api/v1`, documented through OpenAPI and following the same standard as Geographic Reference Service.<br><br>All responses will use `ApiResponse` with `status`, `code`, `data`, and pagination metadata when applicable. Errors will have stable codes and will not include stack traces or full identifiers.<br><br>Versioned resources will return:<br><br>`ETag: "<version>"`<br><br>Every modification of an existing aggregate will require `If-Match`. The version will not be duplicated in the body. The absence of `If-Match` will produce `428 Precondition Required`; a different version will produce `412 Precondition Failed` with a domain version-conflict code. No change will be applied when the precondition fails.<br><br>Sensitive operations will be:<br><br>- Lookup by `party_identifier_id`: canonical locator.<br>- Lookup by Party and scheme: may return multiple identifiers or the verified primary identifier when explicitly requested.<br>- Exact search: will receive scheme and plaintext in the body, normalize it, calculate HMAC, and return references and masked values.<br>- Decryption: separate operation by `party_identifier_id`, with `Cache-Control: no-store`.<br><br>The exact search will not scan or decrypt records.<br><br>Events will be published through Transactional Outbox and RabbitMQ. A durable topic exchange will be used, for example `party.registry.events`, with persistent messages and publisher confirms. Consumers will manage their own queues and deduplicate using `event_id`.<br><br>V1 catalog:<br><br>- `party.created.v1`<br>- `party.updated.v1`<br>- `party.activated.v1`<br>- `party.inactivated.v1`<br>- `party.archived.v1`<br>- `party.nationality-added.v1`<br>- `party.nationality-updated.v1`<br>- `party.nationality-removed.v1`<br>- `party.identifier-created.v1`<br>- `party.identifier-updated.v1`<br>- `party.identifier-verified.v1`<br>- `party.identifier-rejected.v1`<br>- `party.identifier-revoked.v1`<br>- `party.identifier-expired.v1`<br><br>Events will contain event ID, tenant, aggregate ID, aggregate version, type, schema version, date, correlation/causation, and minimum payload. They will never contain plaintext. Identifier Scheme changes will not be published to the outbox.<br><br>Party Registry will connect simultaneously to:<br><br>- `internal-services`, for communication between microservices.<br>- Its own PostgreSQL network.<br>- `messaging.network`, to connect to RabbitMQ through its internal alias.<br><br>RabbitMQ will remain on `messaging.network`; it is not necessary to add it to `internal-services`. This alternative keeps the broker isolated and grants access only to the services that need it. | Alex | 2026-08-09 |
| OD-006 | **Geographic validation**<br><br>Country codes will be validated against Geographic Reference Service.<br><br>Party Registry will use a local in-memory cache to reduce calls and tolerate temporary unavailability. As a technical baseline:<br><br>- Normal TTL: 24 hours.<br>- Maximum use of stale cache: 7 days.<br>- Reactive refresh when the TTL expires.<br><br>If Geographic Reference does not respond:<br><br>- The available cache will be used within the maximum age limit.<br>- If no usable cache exists, the write will fail with 503 and a retryable code.<br><br>For new references, only active codes will be accepted. Historical codes already stored will remain valid and will not be invalidated by an unavailability or subsequent change in the catalog.<br><br>The cache will not become a new source of truth and may be lost when the process restarts. | Alex | 2026-08-09 |
| OD-007 | **Technical baseline for the pilot**<br><br>V1 is a pilot without a contractual availability, capacity, or performance SLA. A technical baseline will be implemented to validate the design, not as a business commitment.<br><br>Initial baseline for an instance with 1 CPU and 512 MiB:<br><br>- Test dataset: 100,000 Parties, 300,000 identifiers, and 200,000 nationalities.<br>- Sustained load: 20 requests per second for 15 minutes.<br>- Concurrency: 50 requests.<br>- Read p95: maximum 500 ms.<br>- Write p95: maximum 1 second.<br>- Exact search p95: maximum 750 ms.<br>- Overall p99: maximum 2 seconds.<br>- Unexpected technical error rate: below 1%.<br>- RabbitMQ publication p95: maximum 60 seconds.<br>- RabbitMQ publication p99: maximum 5 minutes.<br>- Readiness after startup: maximum 60 seconds.<br>- Stabilization window after deployment: 10 minutes.<br><br>Daily backups and periodic restore tests will be performed. The initial baseline will be an RPO of 24 hours and an RTO of 8 hours.<br><br>There will be liveness, readiness, smoke tests, structured logs, correlation ID, latency/error metrics, PostgreSQL pool usage, outbox size and age, RabbitMQ retries, and geographic cache status.<br><br>The final values will be reviewed after obtaining real sizing, traffic, volume, and observability data. The architecture will not introduce premature scaling. | Alex | 2026-08-09 |
| OD-008 | **DBML confirmation**<br><br>Text required for human approval:<br><br>`docs/database/v1-scheme.dbml` is confirmed as the source of truth for Party Registry Service V1 and its independent validation against PostgreSQL 18 is authorized.<br><br>The approved complementary interpretations are:<br><br>- Exclusively logical deletion.<br>- Nationality is active when `valid_until IS NULL`.<br>- Future dates in `valid_from` and `valid_until` are prohibited.<br>- Decryption through a separate operation.<br>- Tenant-isolated HMAC with one active version in V1.<br>- Identifier Schemes outside the event catalog.<br>- Outbox managed exclusively as infrastructure.<br><br>No automatic design changes are authorized. Any contradiction discovered during validation must return to human decision. Flyway migrations may only be generated after contractual validation is satisfactory. | Alex | 2026-08-09 |
| OD-009 | **Active nationality**<br><br>A nationality is active exclusively when `valid_until IS NULL`.<br><br>`valid_from` represents the first effective day and cannot be in the future. `valid_until` represents the effective termination date; when any value is assigned, the nationality immediately ceases to be active. `valid_until` cannot be in the future or earlier than `valid_from`.<br><br>V1 does not support scheduled activations or terminations.<br><br>PostgreSQL will implement:<br><br>- A partial unique index by Party and country where `valid_until IS NULL`.<br>- A partial unique index by Party for the primary nationality where `is_primary = true AND valid_until IS NULL`.<br><br>Ended records remain as history. If scheduled periods or overlapping intervals are later required, the temporal model must be explicitly reviewed. | Alex | 2026-08-09 |

## 19. Traceability Matrix

| Source | Requirements | Acceptance criteria | Decision/risk | Verification method |
|---|---|---|---|---|
| `.factory/project.yaml:8-25` | DR-008/009, NFR-004/005/006 | AC-000 | OD-008 | Manifest and artifact review |
| `.factory/project.yaml:35-40`; supplied VPS profile | OR-001 to OR-006 | AC-015 | OD-007 | Deployment-contract review later |
| DBML lines 4-37, 97-219 | BR-001 to BR-005, FR-001 to FR-003, DR-001 to DR-003 | AC-001 to AC-005 | OD-001/002/006/009 | Requirements tests and DB contract validation later |
| DBML lines 222-329 | BR-006 to BR-010, FR-004 to FR-006, SR-002 to SR-004 | AC-006 to AC-010 | OD-001/003/004 | Security review, contract tests, DB validation later |
| DBML lines 30-32 and 331-397 | BR-011 to BR-014, FR-007 to FR-009, VR-005, IR-002 to IR-004, NFR-001 to NFR-003 | AC-011 to AC-014, AC-016, AC-017 | OD-005/007 | Concurrency, transaction, contract, and reliability tests later |
| Intake result findings 20-32 and actions 38-46 | Entire draft, especially unknowns and prohibitions | AC-000 | OD-001 to OD-009 | Independent requirements gate |
| Supplied strict Clean Architecture profile | NFR-005 | AC-000 | Later architecture phase | LikeC4/ADR and architecture-gate review later |
| Supplied quarkus-java25 profile | NFR-004/006/007 | AC-000 | OD-007; later architecture phase | Build/profile and quality-gate review later |
| Supplied PostgreSQL profile | DR-008/009, CR-005 | AC-000 | OD-008/009 | Database-contract validation later |
| `.factory/decisions.json:2-10`; approved OD-001 to OD-009 below | BR-015 to BR-021, FR-010 to FR-020, VR-007 to VR-010, DR-007/009, IR-001/003/005, SR-001 to SR-007, NFR-006/008-011, CR-002-004, OR-004/006/008 | AC-018 to AC-029 and applicable earlier criteria | Recorded human decision | Requirements gate, downstream contract, security, performance, and operational verification |

## 20. Risks

- **Accepted V1 security exposure:** Party Registry performs no authentication or authorisation and permits any `internal-services` consumer to invoke decryption. The architecture and security gates MUST verify that the service is not publicly bound and that the approved internal ownership boundary is preserved; they MUST NOT silently add a different role model.
- **Key-operation risk:** HMAC rotation requires a maintenance window and complete fingerprint recalculation before key activation. Downstream operational design MUST preserve lookup and uniqueness continuity without exposing key material.
- **Retention governance risk:** V1 performs no purge and defines no legal retention period. Any later governance policy may require a new purge capability and an explicit requirements/database decision.
- **Contract-detail risk:** Detailed OpenAPI and event payload schemas do not yet exist. Their downstream definition MUST remain compatible with this requirements-level contract and the complete approved event catalog in OD-005.
- **Deployment remediation risk:** Current CI/deployment evidence conflicts with supplied deployment constraints. Remediation belongs to later phases and MUST occur before deployment approval.
- No unresolved high-risk requirements contradiction was found. Physical DBML compatibility and architecture feasibility remain subject to their independent downstream agents.

## 21. Evidence and Consistency Review

Evidence reviewed:

- `.factory/project.yaml:3-44` — project, architecture, stack, database, environment, and workflow constraints.
- `.factory/state.json:2` — current state verified as `SPECIFICATION`; file was read only.
- `.factory/decisions.json:2-10` — records the human approval of OD-001 through OD-009 as written in this specification.
- `docs/database/v1-scheme.dbml:1-410` — authoritative bounded context, persistence facts, invariants, security notes, and outbox semantics.
- `.factory/runs/oc-24279e57-a682-44b6-9ab5-2a1f253f0480/result.json:1-49` — verified intake baseline and original findings addressed by OD-001 through OD-009.
- `.factory/runs/oc-20d0d5d6-bfb6-4dc2-b774-ae5a0ab86cef/result.json:15-33` — prior specification findings and actions traced to the now-recorded decisions.
- Supplied strict Clean Architecture, quarkus-java25, PostgreSQL, and VPS Podman Quadlet profile constraints in the current invocation.

Consistency review performed:

- Requirements remain within the DBML, manifest, and recorded human-decision scope; no additional use case was inferred from table structure.
- Normative requirements use stable identifiers and trace to exact sources.
- Principal functional constraints have success, failure, boundary, or concurrency acceptance scenarios where testable.
- The prior high-risk security, privacy, contract, product, service-target, DBML-confirmation, and nationality questions are preserved verbatim as resolved human decisions and translated into normative requirements.
- No physical database change, detailed API/event schema, architecture design, LikeC4 model, ADR, implementation, test, migration, or deployment work is included. The reactive execution model appears only as the recorded V1 requirement and architecture input.
- Persistence and migration implementation remains explicitly prohibited pending satisfactory independent database-contract validation.

The draft is internally consistent and ready for independent requirements-gate evaluation. This statement does not approve the specification or advance workflow state.
