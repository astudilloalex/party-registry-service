# Requirements Specification

## 1. Document Control

| Field | Value |
|---|---|
| Project | party-registry-service |
| Specification identifier | PRS-REQ-001 |
| Version | 0.1 |
| Status | Draft; unapproved and pending stakeholder decisions and independent requirements gate |
| Source intake result | `.factory/runs/oc-24279e57-a682-44b6-9ab5-2a1f253f0480/result.json` |
| Factory attempt | SPECIFICATION attempt 1 of 3 |
| Persistence authority | `docs/database/v1-scheme.dbml` |

This document distinguishes requirements supported by authoritative evidence from unresolved decisions. It does not approve the DBML, define an API, select an execution model, or provide architecture design.

## 2. Problem Statement

The repository has an authoritative persistence model for a tenant-scoped registry of civil and legal identity, but it has no approved product use cases, public contracts, security policy, service levels, or testable requirements baseline. The affected stakeholders and downstream systems therefore cannot establish what interactions are authorised or how externally observable behaviour must work. The intended outcome of this specification is to capture the constraints that are verifiable now and expose the decisions required before implementation can safely begin.

## 3. Objectives

- Define the Party Registry bounded context and its evidence-backed data invariants without deriving unapproved use cases from table structure.
- Make tenant isolation, sensitive identifier protection, concurrency, outbox reliability, deployment, and quality constraints testable where sources support them.
- Identify all material stakeholder decisions needed for a complete product and public-contract baseline.
- Prevent persistence implementation and migration work until the DBML is explicitly confirmed and independently validated.

The original factory acceptance criterion is preserved verbatim:

> Requirements and architecture are explicit, internally consistent, traceable to repository evidence, compliant with strict Clean Architecture and LikeC4 standards, and no unresolved high-risk contradiction remains before database validation.

For this phase, mandated architecture constraints are recorded below. Architecture design, LikeC4 views, ADRs, explicit port design, and execution-model selection remain responsibilities of the later ARCHITECTURE phase.

## 4. Scope

### 4.1 Verified In Scope

- Tenant-scoped Party identity records classified as natural persons or legal entities.
- Natural-person details, legal-entity details, and natural-person nationalities.
- Official Party identifiers and the global identifier-scheme catalog used to normalize and validate them.
- Tenant-scoped business-event outbox records for Party and Party Identifier aggregates.
- Logical country-code references to the Geographic Reference Service using ISO 3166-1 alpha-2 codes.
- Audit metadata, optimistic concurrency, sensitive identifier protection, and data invariants explicitly defined by the DBML.
- Local, development, staging, and production environments governed by the supplied deployment profile.

The stakeholder-approved commands, queries, lifecycle use cases, actors, public API, and event catalog within this data boundary are unresolved (OD-001 and OD-005).

### 4.2 Explicitly Out of Scope

The Party Registry does not own customers, suppliers, employees, user accounts, authentication, authorization, operating organizations, addresses, contacts, subscriptions, audit storage, tax configuration, ownership structures, beneficial ownership, or legal/corporate relationship management. Other services keep `party_id` as an opaque reference and do not create foreign keys to this database. The relationship between an authenticated subject and `party_id` belongs to the Access Management Service.

Database-schema changes, Flyway events, platform events, infrastructure events, and identifier-scheme administration changes are not tenant Party integration events.

### 4.3 Deferred Scope

- Product operations and lifecycle transitions not yet approved by stakeholders.
- Detailed public API and integration-event contracts.
- Architecture design, LikeC4 views, ADRs, port definitions, and execution-model selection.
- Physical database validation, persistence implementation, and forward-only migrations.
- Deployment implementation and production verification.

### 4.4 Ownership Boundaries

- Party Registry owns the in-scope identity data maintained within each tenant.
- Geographic Reference Service owns country reference data; Party Registry stores only logical country-code references.
- Keycloak authenticates accounts but is not the registry of persons.
- Access Management Service owns authenticated-subject-to-Party relationships.
- External consumers own their local opaque Party references and event-consumption idempotency.
- Key ownership and operational responsibility for encryption and HMAC are unresolved (OD-003).

## 5. Actors and External Systems

| Identifier | Actor/system | Evidence-backed interaction and boundary | Unresolved aspects |
|---|---|---|---|
| ACT-001 | Tenant-scoped caller | May interact only through future approved use cases and within a trusted tenant context. | Caller classes, credentials, permissions, and permitted operations (OD-001, OD-002). |
| EXT-001 | Keycloak | Authenticates accounts; does not own Party records. | Authentication protocol and service identity policy (OD-002). |
| EXT-002 | Access Management Service | Owns subject-to-`party_id` relationships. | How authorisation and tenant context are conveyed and verified (OD-002). |
| EXT-003 | Geographic Reference Service | Owns country references identified by ISO 3166-1 alpha-2 codes; no remote database foreign keys. | Validation mode, availability semantics, caching, and failure handling (OD-006). |
| EXT-004 | Key/secret management capability | Supplies versioned keys for authenticated encryption and tenant-isolated HMAC. | Owner, technology, access policy, rotation, recovery, and reprocessing (OD-003). |
| EXT-005 | Integration-event consumer | Receives approved tenant-scoped events at least once and must deduplicate by event ID. | Consumers, transport, schemas, compatibility, ordering, and retry contract (OD-005). |
| ACT-002 | Identifier-scheme administrator | Maintains a global validation catalog; changes are not emitted through the tenant outbox. | Authorisation, workflow, and approved catalog lifecycle use cases (OD-001, OD-002). |

Unavailability behaviour is not authoritative for any external system and requires decisions under OD-005, OD-006, and OD-007.

## 6. Glossary

| Term | Meaning |
|---|---|
| Party | A tenant-owned identity record for exactly one natural person or legal entity; not a commercial role, user, tenant, or operating company. |
| Party Identifier | An official identifier associated with a Party and governed by an Identifier Scheme. |
| Identifier Scheme | Global metadata naming the normalization and validation rules applicable to an official identifier. It contains versioned rule keys, not executable code. |
| Tenant context | The trusted tenant identity used to isolate tenant-owned data. Its authoritative source is unresolved. |
| Active nationality | A nationality row considered active for uniqueness purposes. Its exact temporal predicate is unresolved (OD-009). |
| Outbox event | A tenant-scoped business-event record committed atomically with its business mutation for later at-least-once publication. |
| Aggregate version | A non-negative value used to detect conflicting mutations and identify event ordering within an aggregate. |

## 7. Business Rules

| ID | Normative rule | Source | Related requirements / criteria |
|---|---|---|---|
| BR-001 | Every tenant-owned Party and Party Identifier MUST be isolated by `tenant_id`; a reference to a Party from another service MUST remain opaque and MUST NOT be enforced by a cross-service database foreign key. | DBML lines 5-24, 97-115, 269-301, 408 | FR-001, DR-001, SR-001; AC-001 |
| BR-002 | A Party MUST have exactly one type: `NATURAL_PERSON` or `LEGAL_ENTITY`; its detail kind MUST match that type, and both detail kinds MUST NOT coexist. | DBML lines 42-45, 121-157, 180-184 | FR-002, DR-002; AC-002 |
| BR-003 | A death or dissolution date MUST NOT precede its corresponding birth or incorporation date when both dates exist. | DBML lines 145-147, 175-177 | VR-001; AC-003 |
| BR-004 | Country codes stored by this bounded context MUST use two uppercase ISO 3166-1 alpha-2 characters and remain logical references to Geographic Reference Service data. | DBML lines 20-21, 137-147, 166-177, 191-208, 226-250 | VR-002, IR-001; AC-004 |
| BR-005 | Nationality records MUST belong only to natural-person Parties. No Party MAY have duplicate simultaneously active rows for the same country or more than one simultaneously active primary nationality. | DBML lines 187-219 | FR-003, DR-003; AC-005; OD-009 |
| BR-006 | An Identifier Scheme's code, issuing country, category, and applicable subject type MUST become immutable after the scheme is activated. | DBML lines 222-266 | FR-004; AC-006 |
| BR-007 | Identifier input MUST be normalized and validated using the versioned keys of its scheme, satisfy configured positive length bounds, and be compatible with the Party type. | DBML lines 222-255, 269-325 | FR-005, VR-003; AC-007 |
| BR-008 | A tenant MUST NOT have more than one `PENDING_VERIFICATION` or `VERIFIED` Party Identifier with the same scheme and normalized-value fingerprint. A Party MUST NOT have more than one primary `VERIFIED` identifier for one scheme. | DBML lines 297-323 | FR-006, DR-004; AC-008 |
| BR-009 | A `VERIFIED` identifier MUST record verifier and verification time; an `EXPIRED` identifier MUST record an expiry date; expiry MUST NOT precede issue date. | DBML lines 283-309 | VR-004; AC-009 |
| BR-010 | Complete identifier values MUST be protected with authenticated encryption and MUST NOT be persisted in plaintext. Exact lookup and active uniqueness MUST use a tenant-isolated HMAC-SHA-256 fingerprint. | DBML lines 23-24, 277-281 | SR-002, SR-003; AC-010 |
| BR-011 | Every mutable business record MUST record creation and update timestamps and opaque authenticated subjects or service principals. Aggregate roots MUST use non-negative optimistic-concurrency versions. | DBML lines 30-32 and mutable table definitions | FR-007, DR-005; AC-011 |
| BR-012 | A mutation to Party details or nationalities MUST increment the Party aggregate version in the same transaction. | DBML lines 121-127, 383-386 | FR-007; AC-011 |
| BR-013 | An approved tenant business mutation requiring publication and its outbox record MUST commit in one PostgreSQL transaction. Publication MUST be at least once, and consumers MUST deduplicate using the outbox event ID. | DBML lines 331-397 | FR-008, IR-002, RR-001; AC-012, AC-013 |
| BR-014 | Public integration events MUST NOT contain complete decrypted identifiers and MUST contain no unnecessary personal data. | DBML lines 327-328, 339-342 | SR-004, IR-003; AC-014 |

No lifecycle transitions beyond these structural states and invariants are authoritative. Permitted state changes and their actors require OD-001.

## 8. Functional Requirements

These requirements constrain any operation later approved by stakeholders; they do not assert that an unapproved public operation exists.

- **FR-001 — Tenant boundary:** WHEN an approved operation accesses tenant-owned Party, Party Identifier, or outbox data, THE SYSTEM MUST limit the operation to the verified tenant context and MUST NOT disclose whether another tenant holds a matching record. Source: BR-001. Failure semantics require OD-002 and OD-005.
- **FR-002 — Party kind consistency:** WHEN an approved operation creates or changes Party identity details, THE SYSTEM MUST preserve exactly one Party type and the matching detail kind and MUST reject a conflicting detail kind. Source: BR-002.
- **FR-003 — Nationality consistency:** WHEN an approved operation maintains a nationality, THE SYSTEM MUST preserve BR-005, including natural-person applicability and active uniqueness. Source: BR-005. The active predicate requires OD-009.
- **FR-004 — Scheme immutability:** WHEN an approved administration operation attempts to change an activated scheme's immutable attributes, THE SYSTEM MUST reject the change without modifying the scheme. Source: BR-006.
- **FR-005 — Identifier validation:** WHEN an approved operation submits an identifier, THE SYSTEM MUST apply the selected scheme's versioned normalization and validation rules and verify subject-type compatibility before accepting it. Source: BR-007.
- **FR-006 — Identifier uniqueness:** WHEN an approved operation would violate either uniqueness rule in BR-008, THE SYSTEM MUST reject the operation without creating or changing an identifier into a conflicting state. Source: BR-008.
- **FR-007 — Concurrency and audit:** WHEN a mutable record is changed, THE SYSTEM MUST record the responsible opaque subject or service principal and update time. WHEN an aggregate mutation supplies a stale version, THE SYSTEM MUST reject that mutation without overwriting the intervening change. Party component changes MUST increment the Party version atomically. Source: BR-011 and BR-012.
- **FR-008 — Reliable event recording:** WHEN a stakeholder-approved business mutation requires an integration event, THE SYSTEM MUST either commit both the mutation and one corresponding outbox event or commit neither. Source: BR-013. Which mutations require which events is unresolved under OD-005.
- **FR-009 — Outbox delivery state:** WHEN an outbox event is processed, THE SYSTEM MUST preserve the DBML delivery-state invariants: a published event has a publication time, a non-published event has none, and a failed event has an error code. Source: DBML lines 331-374.

## 9. Validation and Error Requirements

- **VR-001:** The system MUST reject inconsistent life dates described by BR-003.
- **VR-002:** The system MUST reject a supplied country code that is not exactly two uppercase characters. Whether a syntactically valid code must be synchronously confirmed by EXT-003 is unresolved (OD-006).
- **VR-003:** Configured minimum and maximum identifier lengths MUST be positive; maximum MUST NOT be less than minimum. Identifier values MUST satisfy applicable configured bounds and versioned normalization/validation rules before acceptance.
- **VR-004:** The system MUST reject identifier state data that violates BR-009.
- **VR-005:** Normalization versions, encryption-key versions, and event-schema versions MUST be positive; aggregate versions and publication-attempt counts MUST be non-negative.
- **VR-006:** Validation or concurrency failure MUST leave the affected business aggregate and its outbox record unmodified as one atomic outcome.
- **VR-007:** Public machine-readable error semantics, stable error identifiers, retryability, and information-disclosure rules MUST be established in the approved public contract (OD-005); no status code or schema is specified here.
- **VR-008:** Duplicate event delivery is expected under at-least-once delivery. Consumers MUST treat repeated event IDs idempotently. Producer command idempotency is unresolved (OD-005).

## 10. Data Requirements

- **DR-001:** Tenant-owned data MUST preserve tenant identity and tenant-scoped references as specified by BR-001.
- **DR-002:** Party identity data MUST preserve the exclusive Party-type/detail relationship in BR-002.
- **DR-003:** Natural-person nationality data consists of country, primary designation, optional validity dates, and audit metadata and MUST preserve BR-005.
- **DR-004:** Party Identifier data MUST preserve scheme, tenant, Party, protected value, key and normalization versions, safe masked representation, primary designation, status, relevant dates, verification evidence, audit metadata, and aggregate version as defined by the DBML.
- **DR-005:** All mutable business data MUST preserve the audit and version facts in BR-011. Audit storage outside these record-level fields is explicitly not owned by this service.
- **DR-006:** Outbox data MUST preserve event identity, tenant, aggregate identity and version, versioned event type/schema, minimal payload, occurrence time, optional correlation/causation, delivery state, attempt evidence, audit metadata, and delivery version.
- **DR-007:** The confidentiality classification, lawful purpose, retention, deletion, subject-rights handling, and audit-evidence retention for personal and identifier data require OD-004. No deletion behaviour is authorised by this draft.
- **DR-008:** Physical tables, columns, types, constraints, indexes, and relationships are governed exclusively by the DBML and MUST NOT be redesigned in requirements or implementation.
- **DR-009:** Persistence implementation and Flyway migration generation MUST remain prohibited until explicit DBML confirmation and database-contract validation are recorded. Later migrations MUST be immutable and forward-only under the supplied PostgreSQL profile.

## 11. API and Integration Requirements

- **IR-001:** Country references MUST use ISO 3166-1 alpha-2 logical identifiers and MUST NOT create a remote database foreign key. The interaction contract with Geographic Reference Service requires OD-006.
- **IR-002:** Approved Party and Party Identifier business events MUST use stable event IDs and versioned business event types and schema versions, support at-least-once delivery, and identify tenant and aggregate version.
- **IR-003:** Event payloads MUST meet BR-014. Event fields, event catalog, transport, ordering scope, retries, dead-letter handling, and compatibility require OD-005.
- **IR-004:** Identifier-scheme administration, schema, migration, platform, and infrastructure changes MUST NOT be published through the tenant Party outbox.
- **IR-005:** No public endpoint, method, path, request/response schema, or breaking change is approved by this draft. Existing-client compatibility cannot be assessed because no existing public contract was found.

## 12. Security and Privacy Requirements

- **SR-001:** Every tenant-owned operation MUST derive tenant context from an approved trusted source, enforce tenant isolation for reads, writes, lookups, concurrency checks, and events, and MUST NOT accept an unverified caller assertion as authoritative. The source and verification method require OD-002.
- **SR-002:** Complete identifier values MUST be protected by authenticated encryption before persistence and MUST be decrypted only for an approved purpose and authorised principal. Logs, errors, metrics, traces, and events MUST NOT contain complete decrypted values.
- **SR-003:** Exact identifier lookup and active uniqueness MUST use a tenant-isolated HMAC-SHA-256 fingerprint; plaintext and unkeyed hashes MUST NOT be used for this purpose. Keys MUST be external to persisted business data and versioned. Ownership, isolation derivation, rotation, recovery, access, and destruction require OD-003.
- **SR-004:** Only masked identifier representations and the minimum personal data approved for a business contract MAY leave the service in integration events.
- **SR-005:** Creation, update, verification, and publication state changes MUST retain the opaque authenticated subject or service-principal evidence required by the DBML. The audit policy and audit-store integration require OD-004.
- **SR-006:** Authentication, service authentication, role/permission rules, identifier-decryption permission, scheme-administration permission, tenant-context trust, and denial semantics MUST be decided under OD-002 before exposing operations.
- **SR-007:** Privacy classification, lawful basis/purpose, minimisation, retention, deletion, consent if applicable, data-subject rights, and regulatory jurisdiction MUST be decided under OD-004 before production processing.

## 13. Non-Functional Requirements

- **NFR-001 — Concurrency:** Optimistic concurrency MUST ensure that a stale aggregate version cannot overwrite a newer aggregate state. Concurrent outbox processing MUST prevent two publishers from claiming the same pending record at the same time while retaining at-least-once delivery semantics. Source: DBML lines 30-32, 109, 295, 359, 379-397.
- **NFR-002 — Transactional reliability:** The business mutation and required outbox insertion MUST have all-or-nothing durability. Source: BR-013.
- **NFR-003 — Event reliability:** Event delivery MUST be at least once; event consumers MUST be able to deduplicate using event ID. No numeric delivery-time or retry target is approved. Source: BR-013 and OD-007.
- **NFR-004 — Technology constraints:** Production code MUST use Java 25 and Quarkus, and build configuration MUST use Gradle Kotlin DSL. Source: manifest lines 14-19 and supplied quarkus-java25 profile.
- **NFR-005 — Architecture constraints:** The system MUST comply with strict Clean Architecture: domain behaviour remains framework-independent, dependencies point inward, external capabilities are separated through explicit ports, and bounded-context ownership remains explicit. Architecture design and evidence MUST later include LikeC4 context, container, and deployment views. Source: manifest lines 8-12 and supplied strict Clean Architecture profile.
- **NFR-006 — Execution model:** No reactive or blocking model is selected here. The architecture phase MUST record the approved execution-model decision; a non-reactive choice requires the justification mandated by the manifest. Source: manifest lines 18-19 and supplied quarkus-java25 profile.
- **NFR-007 — Quality evidence:** Downstream gates MUST include applicable unit, integration, architecture, API-contract, security, and performance validation. Numeric coverage or performance thresholds are unresolved and MUST NOT be invented. Source: supplied quarkus-java25 profile and intake required actions.
- **NFR-008 — Service levels:** Latency, throughput, concurrency volume, data volume/growth, availability, recovery-time, recovery-point, event-delivery lag, and support targets require OD-007 because no authoritative measurable target exists.

## 14. Compatibility Requirements

- **CR-001:** Other services MUST treat `party_id` as opaque and MUST NOT depend on this database through foreign keys.
- **CR-002:** Event type and schema version MUST identify the public business-event contract version. Compatibility and deprecation policy require OD-005.
- **CR-003:** Identifier normalization and encryption key versions MUST remain identifiable for existing records. Rotation and renormalization compatibility require OD-003.
- **CR-004:** No breaking public-contract change is authorised. No existing approved API, event contract, persisted migration history, or client compatibility matrix was found.
- **CR-005:** Later persistence changes MUST conform to confirmed DBML and use immutable forward-only migrations; rollback MUST NOT assume database reversal.

## 15. Operational and Deployment Requirements

- **OR-001:** Deployment MUST target the manifest environments: local, development, staging, and production.
- **OR-002:** VPS deployment MUST use the supplied Podman Quadlet profile's rootless, restricted mechanism and loopback service binding; unrestricted direct host mutation is not an approved deployment interface.
- **OR-003:** The same immutable OCI image digest MUST be promoted across environments without rebuilding.
- **OR-004:** Environment promotion and production verification MUST include defined health/readiness checks, smoke checks, and stabilization evidence. Exact checks and thresholds depend on OD-007.
- **OR-005:** Rollback MUST be approval-governed, MUST preserve evidence, and MUST account for forward-only database changes rather than assuming migration reversal.
- **OR-006:** Logging, metrics, tracing, alerts, backup verification, recovery exercises, production ownership, and incident support targets require OD-007. Telemetry MUST comply with SR-002 and SR-004.
- **OR-007:** The intake-identified workflow behaviour using direct SSH/SCP, mutable image tags, cross-service deployment references, and absent verification controls MUST NOT be treated as an approved operational contract.

## 16. Acceptance Criteria

- **AC-000 (original factory criterion):** GIVEN the requirements and later architecture artifacts are presented for database validation, WHEN their traceability and consistency are reviewed, THEN they are explicit, internally consistent, traceable to repository evidence, compliant with strict Clean Architecture and LikeC4 standards, and contain no unresolved high-risk contradiction. Current status: not satisfiable until OD-001 through OD-009 are resolved and architecture work is completed.
- **AC-001 (FR-001):** GIVEN two tenants have records, WHEN an approved caller operating in one verified tenant attempts any supported read, lookup, mutation, or event access using the other tenant's identifier, THEN no other-tenant data is returned or changed and the response does not disclose record existence.
- **AC-002 (FR-002):** GIVEN a Party has one approved type, WHEN matching details are accepted, THEN only matching details exist; WHEN conflicting or dual details are submitted, THEN the operation is rejected with no aggregate change.
- **AC-003 (VR-001):** GIVEN both lifecycle dates are supplied, WHEN death precedes birth or dissolution precedes incorporation, THEN the operation is rejected; equal dates and chronological dates satisfy this rule.
- **AC-004 (VR-002):** GIVEN a country-code field is supplied, WHEN its value is not exactly two uppercase characters, THEN it is rejected; a syntactically valid value proceeds to any later approved geographic validation.
- **AC-005 (FR-003):** GIVEN a natural-person Party has an active nationality, WHEN another simultaneously active row for the same country or another active primary nationality would be accepted, THEN it is rejected. Final test data depends on OD-009's active predicate.
- **AC-006 (FR-004):** GIVEN an Identifier Scheme is active, WHEN an operation attempts to alter code, issuing country, category, or applicable subject type, THEN the change is rejected and the stored scheme is unchanged.
- **AC-007 (FR-005):** GIVEN an identifier and selected scheme, WHEN subject type, normalized length, or the versioned validator is incompatible, THEN no Party Identifier is accepted; WHEN all checks pass, it may proceed under an approved use case.
- **AC-008 (FR-006):** GIVEN an active tenant identifier fingerprint already exists for a scheme, WHEN a duplicate would enter pending or verified state, THEN it is rejected; GIVEN a verified primary exists for a Party and scheme, WHEN another would become verified primary, THEN it is rejected.
- **AC-009 (VR-004):** GIVEN an identifier is moved to verified state without verifier/time, expired state without expiry, or has expiry before issue, WHEN the change is attempted, THEN it is rejected atomically.
- **AC-010 (SR-002/SR-003):** GIVEN a complete identifier is accepted, WHEN persistence and observable telemetry/event outputs are inspected, THEN the complete plaintext is absent, ciphertext records a positive key version, exact lookup uses a tenant-isolated HMAC-SHA-256 fingerprint, and only an approved mask is externally observable.
- **AC-011 (FR-007):** GIVEN two mutations use the same aggregate version, WHEN one commits first, THEN the second is rejected as stale without overwriting it; a committed Party component change updates audit facts and increments the Party version in that same transaction.
- **AC-012 (FR-008):** GIVEN an approved mutation requires an event, WHEN commit succeeds, THEN both business state and exactly one corresponding outbox record are committed; WHEN either write fails, THEN neither is committed.
- **AC-013 (IR-002/NFR-003):** GIVEN the same event is delivered more than once, WHEN a conforming consumer processes it, THEN the event ID permits duplicate detection and no duplicate consumer business effect occurs.
- **AC-014 (SR-004/IR-003):** GIVEN any public integration event, WHEN its payload is inspected, THEN it contains no complete decrypted identifier and no personal attributes beyond the approved versioned event contract.
- **AC-015 (OR-003/OR-004):** GIVEN an artifact is promoted, WHEN environment evidence is reviewed, THEN each environment references the same immutable digest and required health/readiness, smoke, and stabilization checks have passed before further promotion.

## 17. Assumptions

No low-risk assumptions define business behaviour. The repository path and supplied invocation metadata are treated as invocation facts. All material unknowns are decisions below rather than assumptions.

## 18. Unknowns and Open Decisions

| ID | Human decision required | Why material / affected areas |
|---|---|---|
| OD-001 | Approve stakeholders, actor roles, product outcomes, commands/queries, Party and identifier lifecycle transitions, scheme-administration workflow, and success/failure scenarios. | Defines scope and business behaviour; blocks a complete functional baseline. |
| OD-002 | Approve authentication, service authentication, authorisation permissions, trusted tenant-context source/verification, cross-tenant denial semantics, and decryption authority. | High-risk tenant isolation and access control; affects ACT-001, FR-001, SR-001, SR-006. |
| OD-003 | Approve encryption and HMAC key owner, tenant isolation strategy, KMS/secret-management boundary, access, rotation, re-encryption/re-fingerprinting, compromise recovery, and destruction policy. | High-risk confidentiality, lookup continuity, data integrity, and operations; affects BR-010, SR-002, SR-003, CR-003. |
| OD-004 | Approve data classifications, lawful purpose/basis, minimisation, retention/deletion, legal holds, subject rights, audit scope/retention, and regulatory jurisdictions. | High-risk personal-data privacy and compliance; affects DR-007 and SR-005 to SR-007. |
| OD-005 | Approve public API operations and schemas, event catalog/payload schemas, consumers, transport, authentication, idempotency, ordering, timeout/retry/failure semantics, versioning, compatibility, and deprecation. | High-risk public contract and integration behaviour; affects FR-008, VR-007/008, IR-002/003/005, CR-002. |
| OD-006 | Approve Geographic Reference Service interaction, code-validation freshness, unavailable-service behaviour, and whether historical codes remain valid. | External integrity and availability; affects BR-004, EXT-003, IR-001. |
| OD-007 | Approve measurable latency, throughput, concurrency, volume/growth, availability, recovery, backup, event-delivery, retry, health/readiness, stabilization, observability, alerting, and support targets. | Material architecture, capacity, cost, recovery, and production commitments; affects NFR-008 and OR-004 to OR-006. |
| OD-008 | Explicitly confirm the DBML for independent database-contract validation, including all intended physical constraints and migration prerequisites. | DBML is exclusive but not recorded as human-approved; persistence and migration work remain prohibited. |
| OD-009 | Define the temporal predicate for an “active” nationality and boundary handling for `valid_from`/`valid_until`. | The DBML requires partial uniqueness for active rows but does not define the predicate, affecting data integrity and physical validation. |

These decisions are intentionally unresolved. They MUST NOT be inferred from implementation conventions or the DBML structure.

## 19. Traceability Matrix

| Source | Requirements | Acceptance criteria | Decision/risk | Verification method |
|---|---|---|---|---|
| `.factory/project.yaml:8-25` | DR-008/009, NFR-004/005/006 | AC-000 | OD-008 | Manifest and artifact review |
| `.factory/project.yaml:35-40`; supplied VPS profile | OR-001 to OR-006 | AC-015 | OD-007 | Deployment-contract review later |
| DBML lines 4-37, 97-219 | BR-001 to BR-005, FR-001 to FR-003, DR-001 to DR-003 | AC-001 to AC-005 | OD-001/002/006/009 | Requirements tests and DB contract validation later |
| DBML lines 222-329 | BR-006 to BR-010, FR-004 to FR-006, SR-002 to SR-004 | AC-006 to AC-010 | OD-001/003/004 | Security review, contract tests, DB validation later |
| DBML lines 30-32 and 331-397 | BR-011 to BR-014, FR-007 to FR-009, IR-002 to IR-004, NFR-001 to NFR-003 | AC-011 to AC-014 | OD-005/007 | Concurrency, transaction, contract, and reliability tests later |
| Intake result findings 20-32 and actions 38-46 | Entire draft, especially unknowns and prohibitions | AC-000 | OD-001 to OD-009 | Independent requirements gate |
| Supplied strict Clean Architecture profile | NFR-005 | AC-000 | Later architecture phase | LikeC4/ADR and architecture-gate review later |
| Supplied quarkus-java25 profile | NFR-004/006/007 | AC-000 | OD-007; later architecture phase | Build/profile and quality-gate review later |
| Supplied PostgreSQL profile | DR-008/009, CR-005 | AC-000 | OD-008/009 | Database-contract validation later |

## 20. Risks

- **HIGH:** Missing product use cases and lifecycle authority prevent approval of a complete functional baseline (OD-001).
- **HIGH:** Authentication, authorisation, and trusted tenant-context policy are unresolved (OD-002).
- **HIGH:** Encryption/HMAC key governance and privacy/retention/audit policy are unresolved (OD-003/004).
- **HIGH:** Public API and event contracts and compatibility are unresolved (OD-005).
- **HIGH:** Measurable capacity, reliability, recovery, and production commitments are unresolved (OD-007).
- **HIGH:** DBML confirmation and the active-nationality predicate are unresolved; persistence work before resolution risks data-integrity defects (OD-008/009).
- **MEDIUM:** The current CI/deployment evidence conflicts with supplied deployment constraints, but remediation belongs to later phases.

## 21. Evidence and Consistency Review

Evidence reviewed:

- `.factory/project.yaml:3-44` — project, architecture, stack, database, environment, and workflow constraints.
- `.factory/state.json:2` — current state verified as `SPECIFICATION`; file was read only.
- `.factory/decisions.json:2` — no recorded human decisions.
- `docs/database/v1-scheme.dbml:1-410` — authoritative bounded context, persistence facts, invariants, security notes, and outbox semantics.
- `.factory/runs/oc-24279e57-a682-44b6-9ab5-2a1f253f0480/result.json:1-49` — approved intake baseline and unresolved findings.
- Supplied strict Clean Architecture, quarkus-java25, PostgreSQL, and VPS Podman Quadlet profile constraints in the current invocation.

Consistency review performed:

- Requirements remain within verified DBML and manifest scope; table structure was not converted into unapproved public use cases.
- Normative requirements use stable identifiers and trace to exact sources.
- Principal functional constraints have success, failure, boundary, or concurrency acceptance scenarios where testable.
- High-risk security, privacy, contract, product, SLO, and data questions remain visible as human decisions.
- No physical database change, API schema, architecture design, LikeC4 model, ADR, execution-model choice, implementation, test, migration, or deployment work is included.
- Persistence and migration implementation remains explicitly prohibited pending OD-008/009 and database-contract validation.

The draft is internally consistent as a constraints baseline but is not an approvable complete product specification until the listed human decisions are supplied.
