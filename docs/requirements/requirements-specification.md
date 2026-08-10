# Requirements Specification

## 1. Document Control

| Field | Value |
|---|---|
| Project | party-registry-service |
| Specification identifier | PRS-REQ-001 |
| Version | 0.4 |
| Status | Draft; unapproved; stakeholder decisions recorded; pending independent requirements gate |
| Author | `requirements-specification-agent` |
| Updated | 2026-08-10 |
| Source intake result | `.factory/runs/oc-24279e57-a682-44b6-9ab5-2a1f253f0480/result.json` |
| Factory attempt | SPECIFICATION attempt 1 of 3 |
| Persistence authority | `docs/database/v1-scheme.dbml` |
| Repository revision reviewed by the preceding requirements gate | `482923a5c5cad790a825e66c4cee27cace429bba` |
| Supersedes | PRS-REQ-001 version 0.3, SHA-256 `592d100573066876f5a6a566c2d7c5cd653148b1559981e891ea4148998405f1` |
| Included source manifest | `.factory/project.yaml`; `.factory/decisions.json` including OD-001 through OD-009 and RG-101 through RG-106; `docs/database/v1-scheme.dbml`; intake result above; supplied Clean Architecture, Quarkus Java 25, PostgreSQL, and VPS Podman Quadlet profiles; requirements-gate results `.factory/runs/oc-a87069f6-8ca3-4064-9aeb-a7f251bffe11/result.json`, `.factory/runs/oc-c0056d6f-4454-4e57-bac5-2c6aeb184389/result.json`, and `.factory/runs/oc-48e93284-608b-4d2f-9af7-7907caf08222/result.json` |
| Current artifact digest | To be calculated and recorded by the independent requirements gate against the exact reviewed bytes; a digest cannot be embedded in the file it authenticates without changing those bytes. |
| Open decisions | None identified; failure policies below intentionally avoid unapproved numeric retry limits. |

This document incorporates the human decisions recorded in `.factory/decisions.json`. It defines requirements-level API and integration constraints but does not create detailed OpenAPI or event schemas, provide architecture design, or approve its own contents. Version 0.4 supersedes version 0.3 and repairs RG-201 through RG-208 by incorporating the factoryctl-recorded RG-101 through RG-106 decisions and completing search/error requirements; an independent reviewer must bind the reviewed bytes to a newly calculated digest.

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
- Platform/operations owns centralized collection and retention of Party Registry's structured decryption logs; Party Registry owns only fail-closed emission before plaintext return.

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
| EXT-006 | Platform logging capability | Receives structured decryption security logs emitted by Party Registry without plaintext. If the application emission path cannot emit, decryption fails and returns no plaintext. | Centralized collection, retention, and platform availability are operations-owned; no V1 audit persistence is owned by Party Registry. |

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
| BR-011 | Every mutable business record MUST record creation and update timestamps and the opaque `user-id` or service-principal identifier supplied by the caller. Aggregate roots MUST use non-negative optimistic-concurrency versions. For V1, the recorded actor identifier is trusted but not authenticated, as explicitly decided in OD-002 notwithstanding the DBML field note. | DBML lines 30-32 and mutable table definitions; OD-002 precedence | FR-007, FR-012, DR-005; AC-011, AC-020 |
| BR-012 | A mutation to Party details or nationalities MUST increment the Party aggregate version in the same transaction. | DBML lines 121-127, 383-386 | FR-021; AC-031 |
| BR-013 | An approved tenant business mutation requiring publication and its outbox record MUST commit in one PostgreSQL transaction. Publication MUST be at least once, and consumers MUST deduplicate using the outbox event ID. | DBML lines 331-397 | FR-008, IR-002; AC-012, AC-013 |
| BR-014 | Public integration events MUST NOT contain complete decrypted identifiers and MUST contain no unnecessary personal data. | DBML lines 327-328, 339-342 | SR-004, IR-003; AC-014 |
| BR-015 | V1 deletion MUST be logical: Party to `ARCHIVED`, Identifier Scheme to `RETIRED`, Party Identifier to `REVOKED`, and nationality by assigning `valid_until`. Party details MUST NOT be deleted independently. Archived Parties, retired schemes, and revoked identifiers MUST NOT be reactivated in V1. | OD-001 | FR-026, FR-029, FR-033, FR-039, FR-044; AC-043, AC-050, AC-056, AC-067, AC-068 |
| BR-016 | Every business operation MUST receive a canonical UUID `tenant-id` and a non-empty `user-id` of at most 128 characters. `process-id` MUST be a canonical UUID when supplied and MUST be generated when absent. V1 MUST trust these values without authenticating them and MUST use `tenant-id` for isolation and `user-id` for record audit fields. | OD-002 | FR-011, SR-001; AC-019 |
| BR-017 | Every modification of an existing versioned aggregate MUST require an `If-Match` value equal to the current aggregate version. Absence MUST produce HTTP 428; mismatch MUST produce HTTP 412 with a stable domain version-conflict code; neither case may change state. | OD-005 | FR-012; AC-020 |
| BR-018 | `valid_from` and `valid_until` MUST NOT be future dates. `valid_until` MUST NOT precede `valid_from`; assigning it ends the nationality immediately. V1 MUST NOT schedule nationality activation or termination. | OD-009 | FR-003, VR-009; AC-005 |
| BR-019 | New country references MUST resolve to an active Geographic Reference Service code. Party Registry MUST use a 24-hour in-memory cache TTL and MAY use cached data up to seven days old during dependency unavailability. With no usable cache, a country-dependent write MUST fail with HTTP 503 and a retryable stable code. Existing stored historical codes MUST remain valid. | OD-006 | FR-015, IR-001; AC-023 |
| BR-020 | V1 MUST perform no physical deletion or automatic purge. Archived, revoked, expired, and ended records and their associated audit metadata MUST remain stored. This operational behaviour MUST NOT be represented as a definitive legal retention policy. | OD-004 | DR-007; AC-024 |
| BR-021 | A complete identifier value MAY be returned only by the separate decryption operation. Every decryption MUST record tenant ID, user ID, process ID, and Party Identifier ID, and MUST NOT record the decrypted value. | OD-003/005 | FR-014, SR-005; AC-022 |
| BR-022 | Initial states MUST be Party `DRAFT`, Identifier Scheme `DRAFT`, and Party Identifier `PENDING_VERIFICATION`. Permitted Party transitions are `DRAFT` to `ACTIVE` or `ARCHIVED`, `ACTIVE` to `INACTIVE` or `ARCHIVED`, and `INACTIVE` to `ACTIVE` or `ARCHIVED`; `ARCHIVED` is terminal. Permitted Scheme transitions are `DRAFT` to `ACTIVE` or `RETIRED`, `ACTIVE` to `DEPRECATED` or `RETIRED`, and `DEPRECATED` to `ACTIVE` or `RETIRED`; `RETIRED` is terminal. Permitted Party Identifier transitions are `PENDING_VERIFICATION` to `VERIFIED`, `REJECTED`, or `REVOKED`; `VERIFIED` to `EXPIRED` or `REVOKED`; `REJECTED` to `REVOKED`; and `EXPIRED` to `REVOKED`; `REVOKED` is terminal. V1 MUST perform no automatic clock-driven transition. | RG-101 decision in `.factory/decisions.json` | FR-025, FR-038, FR-043; AC-035, AC-049, AC-055, AC-069 |
| BR-023 | Resource identity, tenant ownership, Party type, and ownership references MUST be immutable. Party Identifier plaintext value, Party association, and Identifier Scheme association MUST be immutable after creation; correction requires revocation and creation of a new identifier. Party descriptive/detail fields MAY be updated subject to invariants and `If-Match`. Scheme mutability after activation MUST follow BR-006. | RG-101 decision | FR-024, FR-028, FR-032, FR-037, FR-042; AC-034, AC-038, AC-042, AC-048, AC-054, AC-070 |
| BR-024 | V1 decryption audit evidence MUST be a structured application/security log containing tenant ID, user ID, process ID, Party Identifier ID, timestamp, and outcome, and MUST NOT contain plaintext. The system MUST emit this evidence before returning plaintext; if the application logging path cannot emit it, decryption MUST fail and return no plaintext. Party Registry MUST NOT own or add audit-storage persistence in V1; centralized collection and retention are platform/operations concerns. | RG-102 decision | FR-014, SR-005, OR-009; AC-022, AC-071 |
| BR-025 | Masking MUST be applied to the normalized identifier. If normalized length is greater than four characters, the mask MUST be exactly four asterisks followed by the final four normalized characters. If normalized length is four characters or fewer, the mask MUST be exactly four asterisks. No prefix or original formatting may be exposed, and schemes MUST NOT override this V1 policy. | RG-103 decision | FR-046, DR-004, SR-002, SR-004; AC-072, AC-073 |
| BR-026 | Party creation MUST require an `Idempotency-Key` scoped by tenant and operation. Reuse with the same effective request MUST return the original successful result and MUST NOT create another Party, aggregate mutation, or outbox event. Concurrent requests using the same key MUST converge on one committed result. Reuse with a different effective request MUST return HTTP 409 with a stable idempotency-conflict code and no mutation. This rule defines command replay, not natural-person or legal-entity deduplication across distinct keys. | RG-104 decision | FR-010, FR-047; AC-030, AC-074 through AC-076 |
| BR-027 | Party Registry MUST own a versioned application-code catalog of supported normalizer and validator implementation keys; the database MUST store keys but no executable code. Adding an implementation requires a software release. Scheme administration MUST reject unknown, retired, or unavailable keys and MUST NOT activate a scheme that references one. A deployed version MUST NOT remove an implementation referenced by an existing non-retired scheme. If active persisted configuration references an unavailable implementation, readiness and dependent identifier operations MUST fail without changing business data. | RG-105 decision | FR-005, FR-048, FR-049; VR-003, VR-013; AC-007, AC-077 through AC-079 |
| BR-028 | `PENDING` means no positive publisher confirmation has been received. Transient RabbitMQ or network failures MUST retain the same event as retry-eligible, increment attempts, and preserve event ID. A non-recoverable serialization or publication error MUST set `FAILED` with stable non-sensitive evidence. Failed events MUST remain stored and operator-visible and MAY be made eligible for publication again by an authorised internal operational recovery after correction, preserving event ID. V1 MUST NOT automatically discard or purge outbox events, and the publisher MUST NOT move its own events to a dead-letter queue. Consumer queues and dead-letter queues are consumer-owned. | RG-106 decision | FR-009, FR-050; VR-015 through VR-018; AC-016, AC-063 through AC-066, AC-080 |
| BR-029 | Every paginated V1 search MUST use a zero-based page number and page size. Omitted page number MUST default to zero; omitted page size MUST default to 20; size MUST be between 1 and 100 inclusive. Results MUST use ascending resource UUID as the stable default order and UUID as the deterministic tie-breaker for any later approved sort. Party search MUST support type and status filters; nationality search MUST support country, primary, and active filters within one Party; Party Identifier search MUST support Party, scheme, status, and primary filters; Identifier Scheme search MUST support code, issuing country, category, applicable subject type, and status filters. Unsupported filters or sort fields MUST be rejected as validation errors. | Specification completion required by RG-208, bounded to approved DBML concepts and V1 search operations | FR-023, FR-031, FR-036, FR-041; VR-007; AC-033, AC-041, AC-047, AC-053, AC-081 |

The approved lifecycle behaviour is fully enumerated by BR-022. Every transition MUST preserve the other DBML state invariants; no unlisted or automatic transition is permitted.

## 8. Functional Requirements

These requirements define V1 observable obligations. Detailed transport schemas remain downstream contract artifacts and MUST conform to them.

- **FR-001 — Tenant boundary:** WHEN an operation accesses tenant-owned Party, Party Identifier, nationality, or event-related data, THE SYSTEM MUST limit the operation to the supplied tenant context and MUST NOT disclose whether another tenant holds a matching record. A resource outside that tenant MUST produce HTTP 404 through tenant-scoped lookup. Source: BR-001 and OD-002.
- **FR-002 — Party kind consistency:** WHEN an approved operation creates or changes Party identity details, THE SYSTEM MUST preserve exactly one Party type and the matching detail kind and MUST reject a conflicting detail kind. Source: BR-002.
- **FR-003 — Nationality consistency:** WHEN an operation maintains a nationality, THE SYSTEM MUST preserve BR-005 and BR-018, including natural-person applicability, active uniqueness, non-future dates, and immediate ending when `valid_until` is assigned. Source: BR-005, BR-018.
- **FR-004 — Scheme immutability:** WHEN an approved administration operation attempts to change an activated scheme's immutable attributes, THE SYSTEM MUST reject the change without modifying the scheme. Source: BR-006.
- **FR-005 — Identifier validation:** WHEN an approved operation submits an identifier, THE SYSTEM MUST apply supported versioned normalization and validation rules from BR-027 and verify subject-type compatibility before accepting it. Source: BR-007, BR-027.
- **FR-006 — Identifier uniqueness:** WHEN an approved operation would violate either uniqueness rule in BR-008, THE SYSTEM MUST reject the operation without creating or changing an identifier into a conflicting state. Source: BR-008.
- **FR-007 — Mutation audit:** WHEN a mutable record is changed, THE SYSTEM MUST record the caller-supplied actor identifier and update time. Source: BR-011.
- **FR-008 — Reliable event recording:** WHEN a mutation represented in the approved V1 Party or Party Identifier event catalog succeeds, THE SYSTEM MUST either commit both the mutation and one corresponding outbox event or commit neither. Source: BR-013 and OD-005.
- **FR-009 — Outbox delivery state:** WHEN an outbox event is processed, THE SYSTEM MUST preserve the DBML delivery-state invariants: a published event has a publication time, a non-published event has none, and a failed event has an error code. Source: DBML lines 331-374. Acceptance: AC-016.
- **FR-010 — Party creation:** WHEN a valid Party creation request carrying the required idempotency key is first committed, THE SYSTEM MUST create one tenant-scoped Party of the requested immutable type in `DRAFT` state. Source: OD-001, BR-022, BR-026. Acceptance: AC-030.
- **FR-011 — Request context:** WHEN a business operation is requested, THE SYSTEM MUST validate and apply BR-016 before accessing business data. Source: OD-002. Acceptance: AC-019.
- **FR-012 — Conditional modification:** WHEN an existing aggregate is modified, THE SYSTEM MUST enforce BR-017 against its current version and MUST return the updated quoted version as `ETag` after success. Source: OD-005. Acceptance: AC-020.
- **FR-013 — Exact identifier search:** WHEN exact search receives a scheme and plaintext identifier, THE SYSTEM MUST normalize the input, compute the tenant-effective HMAC fingerprint, and return only matching references and masked values without scanning or decrypting stored records. Source: OD-003/005. Acceptance: AC-021.
- **FR-014 — Identifier decryption:** WHEN decryption is requested by Party Identifier ID within the supplied tenant, THE SYSTEM MUST return the complete value only through that separate operation, set `Cache-Control: no-store`, and satisfy the emit-before-return and fail-closed structured-log rules in BR-024. Source: OD-003/005, BR-024. Acceptance: AC-022, AC-071.
- **FR-015 — Country validation:** WHEN a write introduces a country reference, THE SYSTEM MUST apply BR-019; dependency failure MUST NOT invalidate historical stored codes. Source: OD-006. Acceptance: AC-023.
- **FR-016 — Party-detail creation:** WHEN a valid detail-creation request is received for a Party without details, THE SYSTEM MUST create only the detail kind matching the Party type. Source: OD-001 and BR-002. Acceptance: AC-036.
- **FR-017 — Nationality addition:** WHEN a valid nationality-addition request is received, THE SYSTEM MUST add it under BR-005 and BR-018. Source: OD-001/009. Acceptance: AC-039.
- **FR-018 — Party Identifier creation:** WHEN a valid Party Identifier creation request is received, THE SYSTEM MUST create it under BR-007 through BR-010. Source: OD-001/005. Acceptance: AC-044.
- **FR-019 — Identifier Scheme creation:** WHEN a valid Identifier Scheme creation request references supported implementation keys, THE SYSTEM MUST create it in `DRAFT` state without creating a tenant Party outbox event. Source: OD-001/005, BR-022, BR-027. Acceptance: AC-051, AC-077.
- **FR-020 — Outbox boundary:** THE SYSTEM MUST NOT expose public CRUD operations for outbox records; delivery-state processing is internal infrastructure. Source: OD-001/008. Acceptance: AC-058.
- **FR-021 — Party component versioning:** WHEN Party details or nationalities change, THE SYSTEM MUST increment the Party aggregate version in the same transaction. Source: BR-012. Acceptance: AC-031.
- **FR-022 — Party lookup:** WHEN a Party ID is looked up, THE SYSTEM MUST return that Party only within the supplied tenant boundary. Source: OD-001/002. Acceptance: AC-032.
- **FR-023 — Party search:** WHEN Party search is requested, THE SYSTEM MUST return a tenant-scoped paginated result using BR-029's Party filters, bounds, and deterministic order. Source: OD-001/002, BR-029. Acceptance: AC-033, AC-081.
- **FR-024 — Party update:** WHEN a valid conditional Party update is requested, THE SYSTEM MUST update only mutable descriptive Party fields and MUST preserve immutable identity, tenant, Party type, ownership references, and type/detail invariants. Source: OD-001/005, BR-023. Acceptance: AC-034, AC-070.
- **FR-025 — Party status transition:** WHEN a conditional Party status change is requested, THE SYSTEM MUST apply only the exact source-to-target transitions in BR-022. Source: RG-101/BR-022. Acceptance: AC-035, AC-069.
- **FR-026 — Party archive:** WHEN a valid conditional archive request is received, THE SYSTEM MUST set the Party to `ARCHIVED`; an archived Party MUST NOT be reactivated in V1. Source: OD-001. Acceptance: AC-067.
- **FR-027 — Party-detail retrieval:** WHEN matching Party details are requested, THE SYSTEM MUST return them only within the Party's tenant boundary. Source: OD-001/002. Acceptance: AC-037.
- **FR-028 — Party-detail update:** WHEN a valid conditional Party-detail update is requested, THE SYSTEM MUST update only descriptive detail fields, preserve ownership references and Party type, and satisfy FR-021. Source: OD-001, BR-023. Acceptance: AC-038, AC-070.
- **FR-029 — Party-detail lifecycle boundary:** THE SYSTEM MUST NOT expose independent deletion or lifecycle transitions for Party details. Source: OD-001. Acceptance: AC-068.
- **FR-030 — Nationality retrieval:** WHEN a nationality is requested, THE SYSTEM MUST return it only under its tenant-scoped natural-person Party. Source: OD-001/002. Acceptance: AC-040.
- **FR-031 — Nationality search:** WHEN nationality search is requested for a Party, THE SYSTEM MUST return a paginated tenant-scoped result using BR-029's nationality filters, bounds, and deterministic order. Source: OD-001/002, BR-029. Acceptance: AC-041, AC-081.
- **FR-032 — Nationality update:** WHEN a valid conditional nationality update is requested, THE SYSTEM MUST preserve its resource identity, tenant/Party ownership, BR-005 and BR-018 and satisfy FR-021. Source: OD-001/009, BR-023. Acceptance: AC-042, AC-070.
- **FR-033 — Nationality end:** WHEN a valid end request is received, THE SYSTEM MUST assign a non-future `valid_until` and immediately make the nationality inactive without physical deletion. Source: OD-001/009. Acceptance: AC-043.
- **FR-034 — Party Identifier lookup by ID:** WHEN a Party Identifier ID is looked up, THE SYSTEM MUST return only its tenant-scoped masked representation unless FR-014 is invoked separately. Source: OD-003/005. Acceptance: AC-045.
- **FR-035 — Party Identifier lookup by Party and scheme:** WHEN lookup by Party and scheme is requested, THE SYSTEM MUST return all matching masked identifiers or, when explicitly selected, the verified primary. Source: OD-005. Acceptance: AC-046.
- **FR-036 — Party Identifier search:** WHEN Party Identifier search is requested, THE SYSTEM MUST return a tenant-scoped paginated result using BR-029's Identifier filters, bounds, and deterministic order and containing no plaintext identifier. Source: OD-001/003, BR-029. Acceptance: AC-047, AC-081.
- **FR-037 — Party Identifier update:** WHEN a valid conditional Party Identifier update is requested, THE SYSTEM MUST preserve resource identity, tenant ownership, Party association, Scheme association, and plaintext value and MUST update only other mutable attributes while preserving identifier invariants and incrementing its version. Source: OD-001/005, BR-023. Acceptance: AC-048, AC-070.
- **FR-038 — Party Identifier status transition:** WHEN a conditional Identifier status change is requested, THE SYSTEM MUST apply only the exact source-to-target transitions in BR-022 while satisfying BR-008 and BR-009. Source: RG-101/BR-022. Acceptance: AC-049, AC-069.
- **FR-039 — Party Identifier revoke:** WHEN a valid conditional revoke request is received, THE SYSTEM MUST set the identifier to `REVOKED`; a revoked identifier MUST NOT be reactivated in V1. Source: OD-001. Acceptance: AC-050.
- **FR-040 — Identifier Scheme lookup:** WHEN an Identifier Scheme ID is looked up, THE SYSTEM MUST return the matching scheme. Source: OD-001. Acceptance: AC-052.
- **FR-041 — Identifier Scheme search:** WHEN Identifier Scheme search is requested, THE SYSTEM MUST return a paginated result using BR-029's Scheme filters, bounds, and deterministic order. Source: OD-001, BR-029. Acceptance: AC-053, AC-081.
- **FR-042 — Identifier Scheme update:** WHEN a valid conditional Identifier Scheme update is requested, THE SYSTEM MUST preserve identity and BR-006, accept only supported implementation keys under BR-027, and update its aggregate version. Source: OD-001/005, BR-023, BR-027. Acceptance: AC-054, AC-070, AC-077.
- **FR-043 — Identifier Scheme status transition:** WHEN a conditional Scheme status change is requested, THE SYSTEM MUST apply only the exact source-to-target transitions in BR-022, preserve activation immutability, and reject activation when BR-027 is not satisfied. Source: RG-101/BR-022, BR-027. Acceptance: AC-055, AC-069, AC-078.
- **FR-044 — Identifier Scheme retire:** WHEN a valid conditional retire request is received, THE SYSTEM MUST set the scheme to `RETIRED`; a retired scheme MUST NOT be reactivated in V1. Source: OD-001. Acceptance: AC-056.
- **FR-045 — Scheme event exclusion:** WHEN an Identifier Scheme changes, THE SYSTEM MUST NOT create a tenant Party outbox event. Source: DBML lines 34-36, 257-266; OD-005. Acceptance: AC-057.
- **FR-046 — Identifier masking:** WHEN the system stores or returns a masked identifier, THE SYSTEM MUST produce exactly the representation defined by BR-025 from the normalized identifier. Source: RG-103/BR-025. Acceptance: AC-072, AC-073.
- **FR-047 — Party-creation replay:** WHEN a Party creation key is reused, THE SYSTEM MUST apply every replay, payload-conflict, and concurrent-convergence rule in BR-026. Source: RG-104/BR-026. Acceptance: AC-074 through AC-076.
- **FR-048 — Rule-catalog validation:** WHEN a Scheme references a normalizer or validator key, THE SYSTEM MUST verify that the running version supports and permits the key before creation, update, activation, or identifier processing. Source: RG-105/BR-027. Acceptance: AC-077, AC-078.
- **FR-049 — Rule-catalog readiness:** WHEN persisted active configuration references an implementation unavailable in the running version, THE SYSTEM MUST report not ready and MUST reject dependent identifier operations without business-state change. Source: RG-105/BR-027. Acceptance: AC-079.
- **FR-050 — Failed-event recovery:** WHEN an authorised internal operator makes a corrected failed event eligible for publication, THE SYSTEM MUST preserve its event ID and payload identity and MUST NOT create a replacement business event. Source: RG-106/BR-028. Acceptance: AC-080.

## 9. Validation and Error Requirements

- **VR-001:** The system MUST reject inconsistent life dates described by BR-003.
- **VR-002:** The system MUST reject a supplied country code that is not exactly two uppercase characters. A syntactically valid new reference MUST also satisfy BR-019.
- **VR-003:** Configured minimum and maximum identifier lengths MUST be positive; maximum MUST NOT be less than minimum. Identifier values MUST satisfy applicable configured bounds and a BR-027-supported normalizer and validator before acceptance. Unknown, retired, or unavailable implementation keys MUST produce a stable validation error and MUST NOT activate a Scheme or accept an identifier.
- **VR-004:** The system MUST reject identifier state data that violates BR-009.
- **VR-005:** Normalization versions, encryption-key versions, and event-schema versions MUST be positive; aggregate versions and publication-attempt counts MUST be non-negative.
- **VR-006:** Validation or concurrency failure MUST leave the affected business aggregate and its outbox record unmodified as one atomic outcome.
- **VR-007:** REST responses MUST use the approved `ApiResponse` envelope with `status`, `code`, `data`, and pagination metadata when applicable. Each error code MUST belong to one of these stable V1 categories: `VALIDATION_ERROR`, `NOT_FOUND`, `CONFLICT`, `IDEMPOTENCY_CONFLICT`, `PRECONDITION_REQUIRED`, `VERSION_CONFLICT`, `DEPENDENCY_UNAVAILABLE`, or `INTERNAL_ERROR`. The detailed OpenAPI contract MAY define operation-specific codes within a category but MUST NOT change a published code's meaning. Errors MUST NOT include stack traces or complete identifiers. HTTP 404, 409, 412, 428, and 503 MUST be used in the circumstances defined by FR-001, BR-026, BR-017, and BR-019; invalid pagination/filter/sort values and unsupported implementation keys MUST use `VALIDATION_ERROR`.
- **VR-008:** Duplicate event delivery is expected under at-least-once delivery. Consumers MUST treat repeated event IDs idempotently. Party creation MUST additionally satisfy BR-026; no client-command idempotency requirement is approved for other V1 operations.
- **VR-009:** The system MUST reject future nationality validity dates and a `valid_until` earlier than `valid_from`. Assigning a current or past valid `valid_until` MUST immediately end the nationality.
- **VR-010:** The system MUST reject a missing or non-canonical `tenant-id`, a missing, empty, or over-128-character `user-id`, and a supplied non-canonical `process-id` before business data is accessed.
- **VR-011:** A Geographic Reference Service timeout, connection failure, or malformed response MUST be treated as dependency unavailability; the system MUST use only a BR-019-compliant cache or reject the write as specified by BR-019. The failed request MUST NOT change business state.
- **VR-012:** When the geographic cache is empty after process start or unavailable after restart, country-dependent writes MUST follow the no-usable-cache branch of BR-019; the cache MUST NOT be treated as durable evidence.
- **VR-013:** If the required encryption or HMAC key capability is absent, inaccessible, or has an unsupported version, the system MUST reject the key-dependent operation without persisting plaintext, ciphertext derived from an unintended key, a fingerprint, or an outbox event. Readiness MUST report unavailable while a mandatory active key capability is unusable.
- **VR-014:** If stored ciphertext fails authenticated decryption, the system MUST return no plaintext, MUST record a non-sensitive operational error correlated by process ID, and MUST NOT alter the identifier. This condition is not client-retryable without operator remediation.
- **VR-015:** An outbox publisher MUST mark an event `PUBLISHED` only after positive publisher confirmation. A transient timeout, connection loss, negative acknowledgement, or unknown publication outcome MUST retain `PENDING` retry eligibility, increment `publish_attempts`, preserve the same event ID, and MUST NOT roll back the committed business mutation.
- **VR-016:** A non-recoverable serialization or publication error MUST move the event to `FAILED` with a stable non-sensitive error code and operator-visible evidence. Transient RabbitMQ/network failures MUST NOT be classified `FAILED`. No failure may automatically discard an event or assign a new event ID.
- **VR-017:** No numeric publication retry limit or automatic dead-letter/discard policy is approved for V1. `FAILED` events MUST remain stored and operator-visible and MAY return to publication eligibility only through authorised internal operational recovery after cause correction, preserving the same event ID. The Party Registry publisher MUST NOT move its own events to a dead-letter queue or purge them; consumer queue and dead-letter policies remain consumer-owned.
- **VR-018:** After publisher crash or restart, delivery processing MUST resume from durable non-published outbox state. Redelivery MAY occur, and the same event ID MUST be preserved for idempotent consumer handling.

## 10. Data Requirements

- **DR-001:** Tenant-owned data MUST preserve tenant identity and tenant-scoped references as specified by BR-001.
- **DR-002:** Party identity data MUST preserve the exclusive Party-type/detail relationship in BR-002.
- **DR-003:** Natural-person nationality data consists of country, primary designation, optional validity dates, and audit metadata and MUST preserve BR-005.
- **DR-004:** Party Identifier data MUST preserve scheme, tenant, Party, protected value, key and normalization versions, the exact BR-025 masked representation, primary designation, status, relevant dates, verification evidence, audit metadata, and aggregate version as defined by the DBML.
- **DR-005:** All mutable business data MUST preserve the audit and version facts in BR-011. Decryption evidence is the BR-024 structured log only. Audit tables, an audit database, and separate audit persistence are explicitly outside V1 Party Registry ownership.
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
- **SR-002:** Complete identifier values MUST be protected by authenticated encryption before persistence. Plaintext MAY enter only identifier submission, exact-search request processing, and the separate decryption response; it MUST NOT be persisted, logged, measured, traced, emitted to RabbitMQ, or returned in searches or listings. Every non-plaintext identifier representation MUST follow BR-025. V1 applies the access posture in SR-006 rather than principal authorisation.
- **SR-003:** Exact identifier lookup and active uniqueness MUST use a tenant-isolated HMAC-SHA-256 fingerprint derived from the active HMAC master key and `tenant-id`; plaintext and unkeyed hashes MUST NOT be used. Encryption and HMAC keys MUST be separate, supplied through a protected VPS-managed environment file or secret, mounted at runtime, and excluded from repository and image. V1 MUST use one active HMAC version; HMAC rotation MUST occur in a maintenance window and recalculate all fingerprints before activation. Prior encryption keys MUST remain available until re-encryption completes.
- **SR-004:** Only masked identifier representations and the minimum personal data approved for a business contract MAY leave the service in integration events.
- **SR-005:** Creation and update changes MUST retain the supplied `user-id`; decryption MUST emit BR-024 evidence before plaintext is returned and MUST fail closed if that evidence cannot be emitted. Because V1 does not authenticate the caller, these fields are caller-asserted identifiers and MUST NOT be represented as cryptographic proof of actor identity.
- **SR-006:** V1 MUST NOT authenticate or authorise requests, integrate Keycloak or Access Management during request processing, apply role/permission checks, or return HTTP 401/403. Any consumer connected to the internal network MAY invoke decryption. These are explicitly accepted V1 boundaries, not claims of caller identity assurance.
- **SR-007:** The system MUST enforce the classifications and minimisation in DR-007, MUST NOT add personal data beyond DBML-defined data and the explicitly authorised BR-024 decryption-log fields to logs, traces, metrics, or events, and MUST perform no physical deletion or automatic purge in V1.

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
- **OR-009:** Platform/operations MUST own centralized collection and retention of BR-024 decryption logs. Party Registry's V1 obligation ends after successful structured-log emission; it MUST NOT add audit persistence to compensate for missing platform collection.

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
- **AC-010 (SR-002/SR-003):** GIVEN a complete identifier is accepted, WHEN persistence and observable telemetry/event outputs are inspected, THEN the complete plaintext is absent, ciphertext records a positive key version, exact lookup uses a tenant-isolated HMAC-SHA-256 fingerprint, and every externally observable mask exactly satisfies BR-025.
- **AC-011 (FR-007):** GIVEN a mutable-record change succeeds, WHEN its audit facts are inspected, THEN the supplied actor identifier and update time are recorded; a failed change does not alter those facts.
- **AC-012 (FR-008):** GIVEN an approved mutation requires an event, WHEN commit succeeds, THEN both business state and exactly one corresponding outbox record are committed; WHEN either write fails, THEN neither is committed.
- **AC-013 (IR-002/NFR-003):** GIVEN the same event is delivered more than once, WHEN a conforming consumer processes it, THEN the event ID permits duplicate detection and no duplicate consumer business effect occurs.
- **AC-014 (SR-004/IR-003):** GIVEN any public integration event, WHEN its payload is inspected, THEN it contains no complete decrypted identifier and no personal attributes beyond the approved versioned event contract.
- **AC-015 (OR-003/OR-004):** GIVEN an artifact is promoted, WHEN environment evidence is reviewed, THEN each environment references the same immutable digest, readiness occurred within 60 seconds, and liveness/readiness, smoke, and the 10-minute stabilization window passed before further promotion.
- **AC-016 (FR-009):** GIVEN an outbox event delivery-state change, WHEN the event becomes published, THEN it has a publication time; WHEN it is not published, THEN it has no publication time; and WHEN it becomes failed, THEN it has an error code. A state change violating any of these conditions is rejected without changing the event.
- **AC-017 (VR-005):** GIVEN a mutation supplies a normalization version, encryption-key version, event-schema version, aggregate version, or publication-attempt count, WHEN a required-positive value is zero or negative or a required-non-negative value is negative, THEN the mutation is rejected without changing business or outbox state; the respective boundary values of one and zero satisfy this numeric rule.
- **AC-019 (FR-011):** GIVEN a business request, WHEN `tenant-id` or `user-id` is missing or invalid or a supplied `process-id` is not a canonical UUID, THEN the request is rejected before business-data access; WHEN valid context is supplied and `process-id` is absent, THEN the system generates one and uses the supplied tenant and user values for isolation and audit.
- **AC-020 (FR-012):** GIVEN an existing versioned aggregate, WHEN modification omits `If-Match`, THEN HTTP 428 is returned; WHEN it differs from the current version, THEN HTTP 412 and the stable version-conflict code are returned; in both cases state is unchanged. A successful matching modification returns the new quoted version as `ETag`.
- **AC-021 (FR-013):** GIVEN a scheme and plaintext identifier for exact search, WHEN a match exists in the supplied tenant, THEN only references and masked values are returned and evidence confirms lookup used normalized tenant-effective HMAC without scanning or decrypting stored values; another tenant's equal plaintext produces no disclosed match.
- **AC-022 (FR-014):** GIVEN a Party Identifier in the supplied tenant, WHEN decryption succeeds, THEN the response has `Cache-Control: no-store`, only the separate operation returns plaintext, and before that return a structured application/security log is emitted with tenant ID, user ID, process ID, Party Identifier ID, timestamp, and outcome but no plaintext; an identifier outside the tenant produces HTTP 404.
- **AC-023 (FR-015):** GIVEN a new country reference, WHEN an active code is available from fresh cache or Geographic Reference Service, THEN it is accepted; WHEN the dependency is unavailable, cached data no older than seven days may be used, otherwise HTTP 503 with a retryable stable code is returned and no write occurs. A stored historical code remains valid.
- **AC-024 (BR-020/DR-007):** GIVEN logical deletion or record expiry/end, WHEN persisted state and audit evidence are inspected, THEN no business record was physically deleted or automatically purged and its audit metadata remains associated.
- **AC-025 (NFR-008/009):** GIVEN the approved pilot instance, dataset, 15-minute duration, throughput, and concurrency, WHEN performance evidence is collected, THEN every p95/p99, technical-error-rate, and RabbitMQ publication-lag target in NFR-008 and NFR-009 is met.
- **AC-026 (NFR-010/OR-008):** GIVEN production backup and restore evidence, WHEN the operational baseline is reviewed, THEN backups occur daily, the documented recurring restore test succeeds, recoverable data meets the 24-hour RPO, and recovery completes within the 8-hour RTO.
- **AC-027 (SR-001/SR-006):** GIVEN an internal request with syntactically valid caller-supplied context and no authentication credential, WHEN any supported operation including decryption is invoked, THEN Party Registry applies no authentication or role decision and does not return HTTP 401/403; tenant scoping and all applicable business validation still apply.
- **AC-028 (VR-007/IR-005):** GIVEN each success, pagination, validation, concurrency, not-found, and dependency-failure scenario, WHEN its REST response is inspected, THEN it uses the approved `ApiResponse` fields, uses pagination metadata when applicable, emits the required stable code/status semantics, and contains neither a stack trace nor complete identifier.
- **AC-029 (IR-002/IR-003):** GIVEN a committed approved event, WHEN RabbitMQ publication evidence is inspected, THEN the message is persistent, uses the durable topic exchange, carries stable event ID, tenant, aggregate version, event type and schema version, and is acknowledged with publisher-confirm handling; redelivery retains the same event ID for consumer deduplication.
- **AC-030 (FR-010):** GIVEN a valid tenant-scoped request for either Party type and a new `Idempotency-Key`, WHEN creation succeeds, THEN exactly one Party of that immutable type is returned in `DRAFT` state with audit facts and version.
- **AC-031 (FR-021):** GIVEN a Party detail or nationality mutation, WHEN it commits, THEN the Party version increments in the same transaction; WHEN the mutation fails, neither component nor Party version changes.
- **AC-032 (FR-022):** GIVEN a Party ID, WHEN lookup occurs in its tenant, THEN that Party is returned; lookup under another tenant returns HTTP 404 without disclosure.
- **AC-033 (FR-023):** GIVEN tenant-owned Parties exceed one page, WHEN search is requested, THEN only that tenant's requested page and pagination metadata are returned.
- **AC-034 (FR-024):** GIVEN a matching `If-Match` and a valid Party update, WHEN it succeeds, THEN the update preserves Party type/detail consistency and returns the new version; an invalid or stale update changes nothing.
- **AC-035 (FR-025):** GIVEN a Party in `DRAFT`, `ACTIVE`, `INACTIVE`, or `ARCHIVED`, WHEN a status transition is requested, THEN only a transition listed for that exact source state in BR-022 succeeds; every unlisted transition is rejected without state change.
- **AC-036 (FR-016):** GIVEN a Party without details, WHEN matching details are created, THEN exactly the matching detail exists; conflicting or second details are rejected.
- **AC-037 (FR-027):** GIVEN matching Party details, WHEN retrieved in their tenant, THEN they are returned; another tenant receives HTTP 404.
- **AC-038 (FR-028):** GIVEN matching details and a valid conditional update, WHEN it succeeds, THEN details and Party version change atomically; an invalid or stale update changes neither.
- **AC-039 (FR-017):** GIVEN a natural-person Party and valid non-duplicate nationality, WHEN addition succeeds, THEN the nationality is active according to BR-005 and the Party version increments.
- **AC-040 (FR-030):** GIVEN a nationality ID, WHEN retrieved under its Party's tenant, THEN it is returned; another tenant or a non-natural-person association yields no record.
- **AC-041 (FR-031):** GIVEN a Party has multiple nationalities, WHEN paginated search is requested, THEN only that Party's tenant-scoped requested page is returned.
- **AC-042 (FR-032):** GIVEN a valid conditional nationality update, WHEN it succeeds, THEN active uniqueness, date boundaries, and Party versioning remain satisfied; an invalid update changes nothing.
- **AC-043 (FR-033):** GIVEN an active nationality, WHEN a valid end request assigns `valid_until`, THEN it immediately becomes inactive and remains stored; a future or pre-`valid_from` date is rejected.
- **AC-044 (FR-018):** GIVEN a valid scheme-compatible identifier, WHEN creation succeeds, THEN only protected and masked forms are persisted and active uniqueness holds.
- **AC-045 (FR-034):** GIVEN a Party Identifier ID, WHEN ordinary lookup succeeds, THEN only the tenant-scoped masked representation is returned and plaintext is absent.
- **AC-046 (FR-035):** GIVEN a Party and scheme with multiple identifiers, WHEN all matches are selected, THEN all tenant-scoped masked matches are returned; WHEN verified-primary is selected, THEN at most that verified primary is returned.
- **AC-047 (FR-036):** GIVEN Party Identifiers exceed one page, WHEN search is requested, THEN only the tenant's requested page of masked results is returned.
- **AC-048 (FR-037):** GIVEN a matching version and valid Identifier update, WHEN it succeeds, THEN all identifier invariants hold and its version increments; failure changes nothing.
- **AC-049 (FR-038):** GIVEN an Identifier in a BR-022 source state, WHEN a status transition is requested, THEN only a transition listed for that exact source state and satisfying verification, expiry, and uniqueness rules succeeds; every unlisted or invariant-breaking transition is rejected unchanged.
- **AC-050 (FR-039):** GIVEN an Identifier, WHEN revoke succeeds, THEN it is `REVOKED`; any V1 reactivation request is rejected.
- **AC-051 (FR-019):** GIVEN valid scheme metadata with supported implementation keys, WHEN scheme creation succeeds, THEN one versioned `DRAFT` scheme is returned and no tenant Party outbox event is created.
- **AC-052 (FR-040):** GIVEN a Scheme ID, WHEN lookup finds it, THEN that scheme is returned; an unknown ID returns the standard not-found envelope.
- **AC-053 (FR-041):** GIVEN schemes exceed one page, WHEN search is requested, THEN the requested page and pagination metadata are returned.
- **AC-054 (FR-042):** GIVEN a matching version and scheme update, WHEN an immutable activated attribute is included, THEN the request is rejected unchanged; otherwise a valid update increments version.
- **AC-055 (FR-043):** GIVEN a Scheme in a BR-022 source state, WHEN a status transition is requested, THEN only a transition listed for that exact source state succeeds, and activation additionally requires supported implementation keys; every unlisted transition is rejected unchanged.
- **AC-056 (FR-044):** GIVEN a Scheme, WHEN retire succeeds, THEN it is `RETIRED`; any V1 reactivation request is rejected.
- **AC-057 (FR-045):** GIVEN any Identifier Scheme creation, update, status change, or retirement, WHEN committed, THEN no tenant Party outbox event represents that change.
- **AC-058 (FR-020):** GIVEN the public API contract, WHEN operations are enumerated, THEN no create, read, update, delete, retry, or status operation exposes an outbox record.
- **AC-059 (VR-011):** GIVEN a geographic timeout, connection failure, or malformed response, WHEN a country-dependent write occurs, THEN only a cache within BR-019's age limit permits it; otherwise HTTP 503 and a retryable code are returned with no write.
- **AC-060 (VR-012):** GIVEN a process restart with an empty geographic cache, WHEN the dependency is unavailable, THEN a country-dependent write fails as no usable cache exists.
- **AC-061 (VR-013):** GIVEN a mandatory key is missing, inaccessible, or unsupported, WHEN a key-dependent operation is requested, THEN no protected business or outbox data is written and readiness reports unavailable.
- **AC-062 (VR-014):** GIVEN ciphertext fails authentication, WHEN decryption is attempted, THEN no plaintext or state change occurs and non-sensitive correlated operator evidence is emitted.
- **AC-063 (VR-015):** GIVEN publication lacks positive confirmation, WHEN the attempt ends, THEN the event is not marked published, remains redeliverable with the same ID, and committed business state remains committed.
- **AC-064 (VR-016):** GIVEN a non-recoverable serialization or publication error, WHEN failure is recorded, THEN the event enters `FAILED`, remains stored and operator-visible, and has a stable non-sensitive error code; GIVEN a transient RabbitMQ/network failure, THEN the event does not enter `FAILED` and remains `PENDING`-eligible with incremented attempts and its original ID.
- **AC-065 (VR-017):** GIVEN an event repeatedly fails, WHEN no new policy has been approved, THEN it is neither discarded, purged, moved by Party Registry to a dead-letter queue, nor assigned a new ID and remains visible for authorised operator recovery.
- **AC-066 (VR-018):** GIVEN publisher crash or restart with non-published durable events, WHEN processing resumes, THEN those events remain eligible for delivery using their original event IDs.
- **AC-067 (FR-026):** GIVEN a Party, WHEN archive succeeds, THEN its status is `ARCHIVED`; any V1 reactivation request is rejected without state change.
- **AC-068 (FR-029):** GIVEN the public Party-detail contract, WHEN operations are enumerated, THEN no independent detail deletion or lifecycle transition is exposed.
- **AC-069 (BR-022):** GIVEN any Party, Scheme, or Party Identifier status, WHEN all possible target statuses are exercised, THEN exactly the BR-022 transition matrix succeeds, each terminal state rejects every transition, and passage of time alone causes no status change.
- **AC-070 (BR-023):** GIVEN an update attempts to change resource identity, tenant ownership, Party type, an ownership reference, or an Identifier's plaintext value, Party association, or Scheme association, WHEN processed, THEN it is rejected unchanged; GIVEN an allowed descriptive/detail update with matching `If-Match`, THEN invariants are preserved and the aggregate version increments.
- **AC-071 (BR-024):** GIVEN the structured logging path cannot emit the complete required decryption event, WHEN decryption is requested, THEN no plaintext is returned; GIVEN emission succeeds, THEN emission precedes plaintext return. Repository and persistence-contract inspection finds no Party Registry audit table, audit database, or separate audit persistence capability.
- **AC-072 (FR-046):** GIVEN a normalized identifier longer than four characters, WHEN its mask is produced, THEN the result is exactly `****` followed by the final four normalized characters and reveals neither prefix nor original formatting.
- **AC-073 (FR-046):** GIVEN a normalized identifier of zero through four characters, WHEN its mask is produced, THEN the result is exactly `****`; changing Scheme configuration cannot alter either masking outcome.
- **AC-074 (FR-047):** GIVEN a successful Party creation, WHEN the same tenant reuses the same `Idempotency-Key` for the same effective request, THEN the original successful result is returned and no additional Party mutation or outbox event exists.
- **AC-075 (FR-047):** GIVEN two concurrent Party creation requests in one tenant use the same key and effective request, WHEN both complete, THEN they converge on one committed Party and one creation side effect; the original committed result is returned for replay.
- **AC-076 (FR-047):** GIVEN a tenant reuses a Party creation key with a different effective request, WHEN processed, THEN HTTP 409 and a stable `IDEMPOTENCY_CONFLICT` code are returned and no state changes; GIVEN distinct keys, THEN no natural-person or legal-entity business deduplication is inferred from this rule.
- **AC-077 (FR-048):** GIVEN Scheme creation or update references an unknown, retired, or unavailable implementation key, WHEN processed, THEN `VALIDATION_ERROR` is returned and the Scheme is not created, changed, or activated; adding a newly supported key requires a deployed software release.
- **AC-078 (FR-048):** GIVEN a Scheme references only supported keys, WHEN activation is requested, THEN it may proceed subject to all other rules; removal of an implementation still referenced by a non-retired Scheme fails release validation.
- **AC-079 (FR-049):** GIVEN active persisted configuration references an implementation unavailable in the running version, WHEN readiness and dependent identifier operations are exercised, THEN readiness is unavailable and operations fail without changing business or outbox state.
- **AC-080 (FR-050):** GIVEN an authorised operator has corrected the cause of a `FAILED` outbox event, WHEN operational recovery makes it publication-eligible, THEN the same stored event and event ID are retried without a replacement business event; unauthorised public API access remains unavailable.
- **AC-081 (BR-029/VR-007):** GIVEN any V1 search, WHEN pagination is omitted, THEN page zero with size 20 is returned in ascending UUID order; sizes 1 and 100 are accepted, while a negative page, size below 1 or above 100, unsupported filter, or unsupported sort produces `VALIDATION_ERROR` unchanged. Supported filters are exactly those listed for that resource in BR-029, and repeated requests over unchanged data return the same ordered page.

## 17. Assumptions

No low-risk assumption defines business behaviour. The repository path and supplied invocation metadata are treated as invocation facts; material requirements are supported by the manifest, DBML, profiles, intake evidence, or recorded human decisions.

## 18. Unknowns and Decision Status

No unresolved material requirements decision was identified after applying the recorded human decisions approving OD-001 through OD-009 and RG-101 through RG-106. Detailed OpenAPI schemas, event payload schemas, architecture models, ports, ADRs, physical database validation, and implementation details are deliberate downstream artifacts rather than unresolved business requirements. RG-104 explicitly leaves durable idempotency feasibility to architecture and database-contract validation; if the current DBML cannot support it, that phase must raise a human database-contract decision rather than alter DBML automatically.

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

### 18.2 Factoryctl-Recorded Gate Decisions Incorporated by Version 0.4

| ID | Authoritative decision incorporated | Normative location | Recorded by / date |
|---|---|---|---|
| RG-101 | Initial states, complete lifecycle transition matrices, no clock-driven transitions, immutable resource/ownership/type fields, immutable Identifier value and associations, and revoke-plus-recreate correction. | BR-022, BR-023; affected FR/AC | Alex / factoryctl, 2026-08-10 |
| RG-102 | Structured application/security log is the only V1 decryption evidence; required fields, no plaintext, emit-before-return, fail closed, and no Party Registry audit persistence. | BR-024, FR-014, SR-005, OR-009, AC-071 | Alex / factoryctl, 2026-08-10 |
| RG-103 | One exact normalized-value masking algorithm with no Scheme override. | BR-025, FR-046, AC-072, AC-073 | Alex / factoryctl, 2026-08-10 |
| RG-104 | Tenant-and-operation-scoped Party-creation idempotency, same-request replay, concurrent convergence, payload-conflict HTTP 409, and no natural-key deduplication inference. | BR-026, FR-010, FR-047, AC-074 through AC-076 | Alex / factoryctl, 2026-08-10 |
| RG-105 | Application-code ownership and release governance of supported rule implementations; unsupported-key rejection; non-removal and readiness/fail-unchanged obligations. | BR-027, FR-048, FR-049, AC-077 through AC-079 | Alex / factoryctl, 2026-08-10 |
| RG-106 | Confirm-based `PENDING`, transient retry classification, non-recoverable `FAILED`, same-event authorised recovery, no Party Registry purge/discard/DLQ, and consumer-owned queues/DLQs. | BR-028, VR-015 through VR-018, FR-050, AC-063 through AC-066 and AC-080 | Alex / factoryctl, 2026-08-10 |

## 19. Purpose

The purpose of PRS-REQ-001 is to provide an independently reviewable statement of observable V1 behaviour and authoritative constraints so that architecture, database-contract validation, implementation planning, implementation, and verification do not invent material behaviour. It does not approve itself or design the solution.

## 20. Business Context

Party Registry is the tenant-scoped system of record for civil and legal identity data registered by internal systems. Internal consumers need stable Party references, protected official identifiers, lifecycle operations, and versioned integration events. The pilot deliberately trusts network-provided context and does not own commercial roles, accounts, access control, geographic reference data, or consumer-side idempotency effects.

## 21. Constraints

- The project MUST use the manifest-selected Java 25, Quarkus, Gradle Kotlin DSL, PostgreSQL, strict Clean Architecture, and deployment profiles.
- The physical persistence contract MUST remain the confirmed DBML; this specification MUST NOT alter it.
- Architecture MUST later provide explicit ports, inward dependencies, a framework-independent domain, bounded-context ownership, and LikeC4 context, container, and deployment views.
- The approved V1 reactive API, internal network boundary, no-authentication posture, logical deletion, protected identifier handling, forward-only migration, immutable artifact promotion, and no-database-rollback constraints MUST be preserved.
- No breaking public V1 contract, automatic physical deletion, public outbox CRUD, or automatic DBML design change is authorised.

## 22. Dependencies

| Dependency | Required behaviour | Unavailability consequence | Owner |
|---|---|---|---|
| Geographic Reference Service | Supplies active ISO country references; cache governed by BR-019. | VR-011 and VR-012 apply. Historical stored codes remain valid. | External Geographic Reference Service owner. |
| VPS key/secret capability | Supplies separate versioned encryption and HMAC master keys. | VR-013 and VR-014 apply; operator remediation is required. | VPS operator. |
| PostgreSQL | Durably stores DBML-governed state and transactional outbox. | A failed transaction commits neither mutation nor required event; recovery must meet NFR-010. | Party Registry/VPS operations. |
| RabbitMQ | Receives persistent, confirmed, at-least-once event publication. | VR-015 through VR-018 apply; business commits are not rolled back. | Messaging operator and Party Registry operations. |
| Platform logging capability | Accepts the BR-024 structured application/security event before decryption returns plaintext. | Decryption fails closed and returns no plaintext when emission cannot occur. | Platform/operations owns centralized collection and retention; Party Registry owns emission. |
| Internal service consumer | Supplies trusted tenant, user, and optional process context and handles command responses. | Invalid context is rejected by VR-010. | Calling service owner. |
| Event consumer | Deduplicates delivery by event ID. | Duplicate business effects are the consumer's responsibility. | Consumer owner. |

## 23. Validation Strategy

- Contract tests MUST verify every AC that concerns REST requests, responses, tenant boundaries, validation, conditional requests, and resource lifecycles.
- Domain and application tests MUST verify BR-002 through BR-012 without depending on framework behaviour.
- Database-contract validation MUST compare every DBML-derived invariant and OD-008/009 interpretation before persistence implementation or migration generation.
- Integration tests MUST exercise PostgreSQL transaction atomicity, Geographic Reference failure branches, key-capability failure branches, RabbitMQ confirmation ambiguity, crash/restart recovery, and duplicate delivery.
- Security validation MUST verify tenant predicates, plaintext exclusion, the exact BR-025 masking boundaries, key separation, no-store decryption responses, emit-before-return/fail-closed decryption logging, audit minimisation, and the explicitly approved absence of in-service authentication.
- Concurrency and integration validation MUST verify Party-creation replay, payload-conflict, and simultaneous-key convergence without duplicate aggregate or outbox effects; database-contract validation MUST report rather than redesign any DBML gap affecting durable semantics.
- Release/readiness validation MUST verify implementation-key support, prohibit removal of implementations referenced by non-retired Schemes, and exercise persisted active configuration that references an unavailable implementation.
- Architecture tests and LikeC4 review MUST verify NFR-005; performance and recovery evidence MUST verify NFR-008 through NFR-011.
- Deployment evidence MUST verify immutable-digest promotion, health checks, smoke checks, stabilization, backup, and restore obligations. Detailed test implementation remains downstream work.

## 24. Audit Considerations

- Record-level creation and update evidence MUST satisfy BR-011 and DR-005.
- Decryption evidence MUST satisfy BR-024, be emitted before plaintext is returned, and MUST NOT contain plaintext. Failure to emit MUST withhold plaintext.
- Correlation MUST use process ID for requests and operational failures; event evidence MUST retain event, tenant, aggregate, and version identity.
- Publication attempts and failures MUST preserve the DBML-defined attempt, time, status, and non-sensitive error evidence.
- Because caller identity is trusted rather than authenticated, audit fields MUST be described as caller assertions, not proof of identity.
- Audit evidence MUST follow DR-007 classifications and MUST NOT introduce additional personal or secret data.

## 25. Failure and Recovery

- Input, business-rule, duplicate, and optimistic-concurrency failures MUST leave affected aggregate and required outbox state unchanged.
- Dependency timeouts, transport failures, malformed geographic responses, cold-cache behaviour, key unavailability, ciphertext-authentication failure, publication uncertainty, serialization failure, poison-event persistence, and crash/restart recovery MUST follow VR-011 through VR-018.
- Retryable client outcomes are limited to the explicitly approved geographic 503 condition and transient service-capability failures represented as stable machine-readable errors. Invalid input, rule violations, version conflicts, and authenticated-decryption failures are not client-retryable without changed input, refreshed version, or operator correction.
- No outbox event may be declared published without positive confirmation. Unknown publication outcomes favour safe redelivery with the same event ID; duplicate delivery is expected.
- Party Registry operations own visibility and recovery of key and outbox failures. VPS or messaging operators own restoration of their capabilities. Consumer owners own event deduplication.
- Numeric RabbitMQ timeout, backoff, jitter, and maximum-attempt values are technical configuration rather than requirements. Architecture and operations MAY select reversible values provided BR-028 remains satisfied. Party Registry MUST NOT discard, purge, or move its own outbox events to a dead-letter queue; any change to that policy or to business-event identity requires a new authoritative decision.

## 26. Operational Monitoring

OR-006 is mandatory. Operators MUST be able to distinguish geographic-cache staleness/unavailability, mandatory-key unavailability, authenticated-decryption failure, pending/aged/failed outbox records, publisher retries, unknown publication outcomes, and RabbitMQ connectivity failures without exposing protected values. Alerts and dashboards MUST support verification of NFR-009 through NFR-011. Exact alert thresholds other than approved NFR targets remain operational configuration, not new business commitments.

## 27. Traceability Matrix

Source keys: `M` = `.factory/project.yaml`; `D` = `docs/database/v1-scheme.dbml`; `Hn` = approved human decision OD-00n; `Rn` = approved human decision RG-10n; all human decisions are recorded in `.factory/decisions.json`; `P` = supplied profile; `I` = intake result; `G208` = bounded specification completion required by requirements-gate finding RG-208. Every row identifies one requirement, its rule/source, acceptance evidence, and planned validation.

| Requirement | Source / rule | Acceptance | Planned validation |
|---|---|---|---|
| FR-001 | D/BR-001; H2 | AC-001 | Tenant contract/integration test |
| FR-002 | D/BR-002 | AC-002 | Domain and DB-contract test |
| FR-003 | D/BR-005; H9/BR-018 | AC-005 | Domain and DB-contract test |
| FR-004 | D/BR-006 | AC-006 | Domain contract test |
| FR-005 | D/BR-007; R5/BR-027 | AC-007, AC-077 | Validator contract test |
| FR-006 | D/BR-008 | AC-008 | Domain and DB-contract test |
| FR-007 | D/BR-011; H2 | AC-011 | Audit integration test |
| FR-008 | D/BR-013; H5 | AC-012 | Transaction integration test |
| FR-009 | D; R6/BR-028 | AC-016, AC-063, AC-064 | DB-contract/integration test |
| FR-010 | H1; R1/BR-022; R4/BR-026 | AC-030, AC-074..076 | API/idempotency contract test |
| FR-011 | H2/BR-016 | AC-019 | API contract test |
| FR-012 | H5/BR-017 | AC-020 | Concurrency contract test |
| FR-013 | H3/H5 | AC-021 | Security/integration test |
| FR-014 | H3/H5; R2/BR-024 | AC-022, AC-071 | Security/audit test |
| FR-015 | H6/BR-019 | AC-023, AC-059, AC-060 | Dependency integration test |
| FR-016 | H1/BR-002 | AC-036 | API/domain test |
| FR-017 | H1/H9 | AC-039 | API/domain test |
| FR-018 | H1/H5; D/BR-007, BR-008, BR-009, BR-010 | AC-044 | API/security test |
| FR-019 | H1/H5; R1/BR-022; R5/BR-027 | AC-051, AC-077 | API/event-absence test |
| FR-020 | H1/H8 | AC-058 | API surface review |
| FR-021 | D/BR-012 | AC-031 | Transaction/concurrency test |
| FR-022 | H1/H2 | AC-032 | API tenant test |
| FR-023 | H1/H2; G208/BR-029 | AC-033, AC-081 | Pagination/tenant test |
| FR-024 | H1/H5; R1/BR-023 | AC-034, AC-070 | API/domain test |
| FR-025 | R1/BR-022 | AC-035, AC-069 | State-transition matrix test |
| FR-026 | H1/BR-015 | AC-067 | Terminal-state test |
| FR-027 | H1/H2 | AC-037 | API tenant test |
| FR-028 | H1; D/BR-012; R1/BR-023 | AC-038, AC-070 | Transaction/mutability test |
| FR-029 | H1/BR-015 | AC-068 | API surface test |
| FR-030 | H1/H2 | AC-040 | API tenant/domain test |
| FR-031 | H1/H2; G208/BR-029 | AC-041, AC-081 | Pagination/tenant test |
| FR-032 | H1/H9; R1/BR-023 | AC-042, AC-070 | Domain/concurrency test |
| FR-033 | H1/H9/BR-018 | AC-043 | Boundary/lifecycle test |
| FR-034 | H3/H5 | AC-045 | Security contract test |
| FR-035 | H5 | AC-046 | Lookup contract test |
| FR-036 | H1/H3; G208/BR-029 | AC-047, AC-081 | Pagination/security test |
| FR-037 | H1/H5; R1/BR-023 | AC-048, AC-070 | Domain/concurrency test |
| FR-038 | R1/BR-022; D/BR-008, BR-009 | AC-049, AC-069 | State-transition matrix test |
| FR-039 | H1/BR-015 | AC-050 | Terminal-state test |
| FR-040 | H1 | AC-052 | API contract test |
| FR-041 | H1; G208/BR-029 | AC-053, AC-081 | Pagination contract test |
| FR-042 | H1/H5; D/BR-006; R1/BR-023; R5/BR-027 | AC-054, AC-070, AC-077 | Domain/concurrency test |
| FR-043 | R1/BR-022; D/BR-006; R5/BR-027 | AC-055, AC-069, AC-078 | State-transition test |
| FR-044 | H1/BR-015 | AC-056 | Terminal-state test |
| FR-045 | D; H5 | AC-057 | Event-absence integration test |
| FR-046 | R3/BR-025 | AC-072, AC-073 | Masking boundary test |
| FR-047 | R4/BR-026 | AC-074..076 | Idempotency concurrency/contract test |
| FR-048 | R5/BR-027 | AC-077, AC-078 | Rule-catalog/release contract test |
| FR-049 | R5/BR-027 | AC-079 | Readiness/failure integration test |
| FR-050 | R6/BR-028 | AC-080 | Operational recovery integration test |
| VR-001 | D/BR-003 | AC-003 | Boundary test |
| VR-002 | D/BR-004; H6 | AC-004, AC-023 | Validation/dependency test |
| VR-003 | D/BR-007; R5/BR-027 | AC-007, AC-077 | Boundary/validator test |
| VR-004 | D/BR-009 | AC-009 | State-validation test |
| VR-005 | D | AC-017 | Numeric boundary test |
| VR-006 | D/BR-013 | AC-012 | Transaction test |
| VR-007 | H5; R4; G208 | AC-028, AC-076, AC-081 | API contract test |
| VR-008 | D/BR-013; H5; R4/BR-026 | AC-013, AC-074..076 | Consumer/client idempotency contract test |
| VR-009 | H9/BR-018 | AC-005, AC-043 | Date boundary test |
| VR-010 | H2/BR-016 | AC-019 | Header validation test |
| VR-011 | H6/BR-019 | AC-059 | Dependency fault test |
| VR-012 | H6/BR-019 | AC-060 | Restart/cold-cache test |
| VR-013 | H3; R5/BR-027 | AC-061, AC-079 | Key/rule-capability readiness test |
| VR-014 | H3 | AC-062 | Authenticated-decryption fault test |
| VR-015 | D/H5; R6/BR-028 | AC-063, AC-064 | Publisher ambiguity/transient-failure test |
| VR-016 | D; R6/BR-028 | AC-064 | Non-recoverable publication fault test |
| VR-017 | R6/BR-028 | AC-065, AC-080 | Failed-event recovery review/test |
| VR-018 | D/H5; R6/BR-028 | AC-066 | Crash/restart integration test |
| DR-001 | D/BR-001 | AC-001 | DB-contract/tenant test |
| DR-002 | D/BR-002 | AC-002 | DB-contract test |
| DR-003 | D/BR-005 | AC-005 | DB-contract test |
| DR-004 | D/BR-007..010; R3/BR-025 | AC-007..010, AC-072, AC-073 | DB-contract/security test |
| DR-005 | D/BR-011; R2/BR-024 | AC-011, AC-022, AC-071 | Audit data/boundary test |
| DR-006 | D/BR-013 | AC-012, AC-016, AC-029 | DB/event integration test |
| DR-007 | H4/BR-020 | AC-024 | Privacy/data review |
| DR-008 | M/D | AC-000 | Artifact governance review |
| DR-009 | M/P/H8 | AC-000 | Database-contract gate |
| IR-001 | D/BR-004; H6 | AC-004, AC-023, AC-059 | Integration contract test |
| IR-002 | D/H5 | AC-013, AC-029 | Event contract test |
| IR-003 | D/BR-014; H5 | AC-014, AC-029 | Event security/contract test |
| IR-004 | D/H5 | AC-057 | Event-absence test |
| IR-005 | H5 | AC-028 | OpenAPI/API gate |
| SR-001 | D/BR-001; H2 | AC-001, AC-027 | Tenant security test |
| SR-002 | D/BR-010; H3; R3/BR-025 | AC-010, AC-072, AC-073 | Security test |
| SR-003 | D/BR-010; H3 | AC-010, AC-061 | Cryptographic/key review |
| SR-004 | D/BR-014; H4/H5; R3/BR-025 | AC-014, AC-072, AC-073 | Event privacy test |
| SR-005 | H2/H3; R2/BR-024 | AC-022, AC-071 | Audit security/fail-closed test |
| SR-006 | H2 | AC-027 | Security boundary review |
| SR-007 | H4; R2/BR-024 | AC-024, AC-071 | Privacy/telemetry review |
| NFR-001 | D | AC-011, AC-016 | Concurrency integration test |
| NFR-002 | D/BR-013 | AC-012 | Transaction integration test |
| NFR-003 | D/H5 | AC-013, AC-025, AC-029 | Reliability/performance test |
| NFR-004 | M/P | AC-000 | Build/profile review |
| NFR-005 | M/P | AC-000 | LikeC4/architecture gate |
| NFR-006 | H5/P | AC-000 | Architecture/performance review |
| NFR-007 | P/I | AC-000 | Downstream gate evidence review |
| NFR-008 | H7 | AC-025 | Performance test |
| NFR-009 | H7 | AC-025 | Event-lag performance test |
| NFR-010 | H7/P | AC-026 | Backup/restore test |
| NFR-011 | H7 | AC-015 | Startup/readiness test |
| CR-001 | D | AC-000 | Contract/architecture review |
| CR-002 | D/H5 | AC-029 | Event compatibility review |
| CR-003 | D/H3 | AC-010, AC-061 | Data/key compatibility review |
| CR-004 | H5 | AC-028, AC-029 | API/event gate |
| CR-005 | M/P/H8 | AC-000 | DB/migration gate |
| OR-001 | M | AC-015 | Deployment review |
| OR-002 | M/P | AC-015 | Deployment security review |
| OR-003 | P | AC-015 | Digest-promotion evidence |
| OR-004 | P/H7 | AC-015 | Deployment verification |
| OR-005 | P | AC-015, AC-026 | Rollback/recovery review |
| OR-006 | H7 | AC-025, AC-059, AC-060, AC-061, AC-062, AC-063, AC-064, AC-065, AC-066 | Monitoring evidence review |
| OR-007 | I/P | AC-015 | Deployment-contract review |
| OR-008 | H7/P | AC-026 | Backup/restore evidence |
| OR-009 | R2/BR-024 | AC-071 | Audit ownership/persistence-boundary review |

## 28. Risks

Likelihood and impact are qualitative because no approved quantitative risk scale exists.

| ID | Cause and consequence | Likelihood / impact | Assets or requirements | Treatment and owner | Acceptance authority / residual risk / review trigger |
|---|---|---|---|---|---|
| R-001 | V1 trusts any `internal-services` consumer and permits decryption without authentication or role checks; a connected consumer could assert another tenant or obtain restricted plaintext. | Possible / High | Restricted identifiers; tenant data; SR-001, SR-006 | Preserve loopback/non-public binding, tenant scoping, no-store responses, minimised audit, and security-gate evidence. Owners: Party Registry architecture, deployment, and security reviewers. | The project-scope behaviour is explicitly approved by Alex/project owner through OD-002; no separate security/privacy risk-acceptance authority is asserted. Residual risk remains High by design. Review before any network-boundary expansion, production security approval, or authentication-policy change. |
| R-002 | Missing, wrong, unavailable, or rotated key material can make exact search or decryption unavailable or corrupt continuity if activated prematurely. | Possible / High | Identifier confidentiality/integrity/availability; SR-002/003, VR-013/014, CR-003 | Fail closed, expose readiness/operational evidence, separate keys, preserve old encryption versions, and complete offline HMAC recalculation before activation. Owner: VPS operator with Party Registry operations. | OD-003 is the project-owner decision. Residual risk is Medium while operator procedure is unverified. Review before key provisioning, rotation, production, or any key-capability change. |
| R-003 | V1 has no purge or legal retention period; stored personal data may later conflict with governance obligations. | Likely / High | Personal data; DR-007, SR-007, BR-020 | Minimise stored/observable data and require a new requirements/database decision before purge behaviour. Owner: registering organisations and future privacy governance owner. | OD-004 approves only technical no-purge behaviour, not legal risk acceptance. Residual legal/compliance risk remains unassessed. Review before production compliance approval or when jurisdiction/retention policy is supplied. |
| R-004 | RabbitMQ uncertainty, poison events, or publisher restart can cause duplicate delivery or prolonged lag. | Possible / Medium | Event consumers; FR-008/009, VR-015..018, NFR-003/009 | Durable outbox, positive confirms, same-ID redelivery, operator visibility, no automatic discard, consumer deduplication. Owner: Party Registry and messaging operations; consumers own idempotency. | OD-005/007 approve semantics and targets. Residual risk is Medium until integration and performance evidence passes. Review on lag breach, repeated failed event, broker topology change, or retry-policy proposal. |
| R-005 | Geographic dependency failure or malformed data can reject new country-dependent writes or accept stale references within the approved window. | Possible / Medium | Country data; BR-019, VR-011/012 | Validate active codes, limit normal cache to 24 hours and stale use to seven days, fail unchanged when no usable cache exists, monitor cache state. Owner: Party Registry operations and Geographic Reference Service owner. | OD-006 accepts the bounded stale-cache behaviour. Residual risk is Medium. Review after repeated 503 outcomes, source contract change, or requested cache-policy change. |
| R-006 | Detailed OpenAPI and event schemas are absent; downstream design could unintentionally break or overexpose the requirements-level contract. | Possible / High | Public/internal contracts; IR-002..005, CR-002/004 | Require API/event contract gates, immutable published event versions, explicit minimisation, and traceability to this document. Owner: solution architecture and integration contract owners. | No breaking change is authorised. Residual risk is Medium until contracts pass independent gates. Review on any schema proposal or consumer compatibility finding. |
| R-007 | Existing CI evidence uses mutable tags, direct SSH/SCP, cross-service references, and incomplete verification, conflicting with the deployment profile. | Likely / High | Production availability and supply chain; OR-002..007 | Remediate only in later authorised phases; require immutable digest promotion, restricted wrappers, service-specific assets, and deployment verification. Owner: deployment/release owners. | No conflicting behaviour is accepted. Residual risk remains High until deployment gates pass; it does not block requirements/architecture analysis. Review before any environment deployment. |
| R-008 | The DBML or its PostgreSQL 18 interpretation may fail independent validation. | Possible / High | Data integrity and migrations; DR-008/009, CR-005 | Keep persistence and migrations blocked; report contradictions without automatic design changes. Owner: database-contract agent and human database authority. | OD-008/009 authorise validation, not automatic correction. Residual risk remains High until database-contract validation passes. Review on every DBML finding. |
| R-009 | Architecture artifacts do not yet exist, so feasibility and strict dependency compliance are not evidenced. | Possible / High | Architecture boundaries; NFR-005/006 | Later architecture agent must create LikeC4 context/container/deployment views, explicit ports, bounded-context ownership, and relevant ADRs; independent gate reviews them. Owner: solution architecture agent. | No architecture is approved by this specification. Residual risk remains High until architecture gate passes. Review before database validation or implementation planning as workflow requires. |
| R-010 | Durable Party-creation idempotency may require persistence capability not represented by the current DBML; an implementation-only shortcut could lose replay semantics after restart or create duplicate side effects. | Possible / High | Data integrity; BR-026, FR-010, FR-047 | Architecture and database-contract validation must prove durable same-key semantics against the unchanged DBML or raise a human database-contract decision. Owner: architecture/database-contract agents. | RG-104 authorises behaviour but no automatic DBML change. Residual risk remains High until feasibility is independently validated. Review before persistence design or implementation. |
| R-011 | Missing implementation keys or a software release that removes a referenced key can make validation inconsistent or make active configuration unusable. | Possible / High | Identifier integrity and readiness; BR-027, FR-048/049 | Version the code catalog, validate releases against non-retired references, fail readiness and dependent operations unchanged. Owner: release and Party Registry operations. | RG-105 approves fail-closed behaviour. Residual risk is Medium pending architecture and release-gate evidence. Review on every catalog or release change. |
| R-012 | Failure or loss in the application/security logging path can deny decryption or leave emitted evidence outside centralized retention. | Possible / High | Restricted identifiers and audit evidence; BR-024, SR-005, OR-009 | Fail closed before plaintext return and require platform-owned collection/retention monitoring. Owners: Party Registry operations and platform operations. | RG-102 approves the V1 boundary. Residual risk is Medium pending operational evidence. Review before production and on logging-path incidents or retention-policy changes. |

No contradiction between authoritative requirements sources remains unresolved. R-001, R-003, R-007 through R-012 preserve material residual risk for their designated independent authorities and downstream gates; this specification does not accept those risks on their behalf.

## 29. Evidence and Consistency Review

Evidence reviewed:

- `.factory/project.yaml:3-44` — project, architecture, stack, database, environment, and workflow constraints.
- `.factory/state.json:2` — current state verified as `SPECIFICATION`; file was read only.
- `.factory/decisions.json:2-65` — records approval of OD-001 through OD-009 and the authoritative RG-101 through RG-106 lifecycle, audit, masking, idempotency, rule-catalog, and outbox decisions incorporated by version 0.4.
- `docs/database/v1-scheme.dbml:1-410` — authoritative bounded context, persistence facts, invariants, security notes, and outbox semantics.
- `.factory/runs/oc-24279e57-a682-44b6-9ab5-2a1f253f0480/result.json:1-49` — verified intake baseline and original findings addressed by OD-001 through OD-009.
- `.factory/runs/oc-20d0d5d6-bfb6-4dc2-b774-ae5a0ab86cef/result.json:15-33` — prior specification findings and actions traced to the now-recorded decisions.
- `.factory/runs/oc-a87069f6-8ca3-4064-9aeb-a7f251bffe11/result.json:15-32` — RG-001 through RG-006 repaired in version 0.3 through package metadata, explicit mandatory sections, per-requirement traceability, atomic functional obligations, failure/recovery rules, and a complete risk register.
- `.factory/runs/oc-c0056d6f-4454-4e57-bac5-2c6aeb184389/result.json:20-40` — identified the six material decisions subsequently recorded by factoryctl and the bounded search/error completion requirement.
- `.factory/runs/oc-48e93284-608b-4d2f-9af7-7907caf08222/result.json:21-42` — RG-201 through RG-208 define the exact version 0.4 repair scope and acceptance defects addressed here.
- Supplied strict Clean Architecture, quarkus-java25, PostgreSQL, and VPS Podman Quadlet profile constraints in the current invocation.

Consistency review performed:

- Requirements remain within the DBML, manifest, and recorded human-decision scope; no additional use case was inferred from table structure.
- Normative requirements use stable identifiers and trace to exact sources.
- Every functional and supporting requirement has an explicit source, acceptance mapping, and planned validation in section 27; principal functional obligations have success, failure, boundary, or concurrency scenarios.
- The prior high-risk security, privacy, contract, product, service-target, DBML-confirmation, nationality, lifecycle, decryption-audit, masking, idempotency, implementation-catalog, and outbox questions are preserved as resolved human decisions and translated into normative requirements.
- Independently decidable Party, detail, nationality, Party Identifier, and Identifier Scheme operations are split into atomic FR-010 and FR-016 through FR-045 with scenario-level AC-030 through AC-058.
- Geographic, key, RabbitMQ, poison-event, unknown-outcome, and crash/restart failures are defined by VR-011 through VR-018 and AC-059 through AC-066 under RG-106, without using a gate finding as business authority.
- Initial states, complete transition matrices, immutable fields, exact masking, fail-closed decryption logging, Party-creation idempotency, supported implementation-key governance, authorised same-event recovery, search bounds/filters/order, and stable error categories have explicit positive, negative, boundary, and concurrency criteria through AC-069 to AC-081.
- No physical database change, detailed API/event schema, architecture design, LikeC4 model, ADR, implementation, test, migration, or deployment work is included. The reactive execution model appears only as the recorded V1 requirement and architecture input.
- Persistence and migration implementation remains explicitly prohibited pending satisfactory independent database-contract validation.

The draft is internally consistent and ready for independent requirements-gate evaluation. This statement does not approve the specification or advance workflow state.
