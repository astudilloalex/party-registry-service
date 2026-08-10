# PRS-REQ-001 — Amendment 001

## 1. Status and authority

| Field | Value |
|---|---|
| Applies to | `PRS-REQ-001` version 0.4 |
| Amendment | 001 |
| Status | Human-authority clarification pending factoryctl decision recording |
| Issue | `#4` — Align identifier uniqueness and remove external logging capability |
| Date | 2026-08-10 |
| Persistence authority | `docs/database/v1-scheme.dbml` |

This amendment preserves the already-gated bytes of `docs/requirements/requirements-specification.md` version 0.4. It does not rewrite the historical Requirements Gate evidence. Once the project owner records the corresponding `HUMAN_DECISION_RECORDED` event through `factoryctl`, this amendment has higher source precedence than the superseded RG-104 wording and the former external logging-platform assumption.

## 2. Human decisions

### HD-001 — No command-level idempotency in Party Registry V1

Party Registry V1 MUST NOT require, accept, persist, replay, or interpret an `Idempotency-Key` for Party creation or any other command.

Party Registry V1 MUST NOT introduce:

- an idempotency table;
- an idempotency cache;
- a request-fingerprint/replay-result store;
- an idempotency application port;
- a command-replay API contract;
- `IDEMPOTENCY_CONFLICT` as a V1 error category.

RG-104, BR-026, FR-047, AC-074, AC-075, AC-076, and every derived command-replay statement in PRS-REQ-001 v0.4 are superseded by this decision.

Event-consumer deduplication by outbox `event_id` remains unchanged. That integration reliability mechanism is not client-command idempotency.

### HD-002 — Permanent official-identifier uniqueness

For Party Identifiers, the business uniqueness key is:

`tenant + Identifier Scheme + normalized identifier value`

Because the complete normalized identifier is restricted and MUST NOT be stored in plaintext, the persistence enforcement key is:

`tenant_id + identifier_scheme_id + normalized_value_hash`

where `normalized_value_hash` is the tenant-isolated HMAC-SHA-256 fingerprint of the normalized identifier value.

Within one tenant and one Identifier Scheme, the same normalized identifier value MUST NEVER be registered more than once. Lifecycle status does not release the value for reuse. A prior identifier in `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED`, `EXPIRED`, or `REVOKED` blocks a second row with the same uniqueness key.

The same normalized identifier value MAY exist:

- in a different tenant; or
- under a different Identifier Scheme.

Examples:

- Tenant A + `EC_CEDULA` + normalized `0123456789` → first registration may succeed.
- Tenant A + `EC_CEDULA` + normalized `0123456789` → every later registration is rejected, even if the first row is revoked or expired.
- Tenant B + `EC_CEDULA` + normalized `0123456789` → may succeed.
- Tenant A + a different Identifier Scheme + the same normalized string → may succeed if that Scheme's own validation permits it.

Concurrent attempts with the same uniqueness key MUST converge through the PostgreSQL unique constraint: at most one row may be committed. No replay of a successful command result is required.

### HD-003 — No external Platform Logging Capability

Party Registry V1 has no external or centralized `Platform Logging Capability` as an architectural dependency or C4 software system.

Party Registry emits its required operational and decryption-security logs through the configured local application logging path using the AlexAstudillo enterprise logging convention. The service does not make a remote logging call and does not require acknowledgement from a separate logging platform.

For decryption, the application MUST successfully execute the required structured log emission through its local logging adapter before returning plaintext. If the local application logging operation fails synchronously, plaintext is withheld. This is not a guarantee of remote collection, durable centralized storage, or external log retention.

Party Registry still MUST NOT add an audit table, audit database, or separate audit persistence capability.

## 3. Effective requirement replacements

The following clauses supersede the corresponding v0.4 wording.

### BR-008 replacement — Identifier uniqueness

A tenant MUST NOT have more than one Party Identifier with the same Identifier Scheme and normalized-value fingerprint, regardless of lifecycle status. The uniqueness scope is exactly `(tenant_id, identifier_scheme_id, normalized_value_hash)`. A Party MUST NOT have more than one primary `VERIFIED` identifier for one scheme.

### BR-010 replacement — Protected lookup and uniqueness

Complete identifier values MUST be protected with authenticated encryption and MUST NOT be persisted in plaintext. Exact lookup and permanent identifier uniqueness MUST use the tenant-isolated HMAC-SHA-256 fingerprint.

### BR-024 replacement — Decryption logging

V1 decryption evidence is a structured application/security log containing tenant ID, user ID, process ID, Party Identifier ID, timestamp, action/outcome, and no plaintext. The log is emitted through the local application logging path before plaintext is returned. Party Registry owns no audit persistence and has no external logging-platform dependency.

### BR-026 replacement — No command idempotency

Party creation has no `Idempotency-Key` or command-replay semantics in V1. Duplicate prevention is not inferred from Party creation attributes. Official-identifier duplicate prevention is governed exclusively by the permanent identifier-uniqueness rule above.

### FR-006 replacement — Identifier uniqueness

WHEN creation or mutation of a Party Identifier would duplicate `(tenant, Identifier Scheme, normalized identifier value)`, THE SYSTEM MUST reject the operation without creating or changing a conflicting identifier or outbox effect. This applies regardless of the lifecycle status of an existing matching identifier.

### FR-010 replacement — Party creation

WHEN a valid Party creation request is committed, THE SYSTEM MUST create one tenant-scoped Party of the requested immutable type in `DRAFT` state. No command idempotency key or replay behavior is required.

### FR-047 replacement — Permanent identifier duplicate prevention

WHEN a Party Identifier is submitted, THE SYSTEM MUST enforce permanent uniqueness by tenant, Identifier Scheme, and normalized identifier value through the tenant-isolated HMAC fingerprint. A duplicate in the same uniqueness scope MUST be rejected atomically. The same normalized identifier value under another tenant or another Identifier Scheme is outside that uniqueness scope.

### VR-007 replacement impact

`IDEMPOTENCY_CONFLICT` is removed from the V1 error categories. A permanent identifier uniqueness violation belongs to the existing `CONFLICT` category. The detailed API contract may define a stable operation-specific code within `CONFLICT` without changing this requirement.

### VR-008 replacement

Duplicate RabbitMQ event delivery remains expected and consumers MUST deduplicate by event ID. Party Registry V1 defines no client-command idempotency behavior.

### SR-003 replacement impact

Exact identifier lookup and permanent identifier uniqueness MUST use the tenant-isolated HMAC-SHA-256 fingerprint. The previous term `active uniqueness` is superseded.

### OR-009 replacement

Party Registry MUST emit the required decryption-security log through its configured local application logging path and MUST NOT add audit persistence. No centralized logging platform, centralized retention service, or remote log-acknowledgement dependency is required by V1.

## 4. Acceptance criteria replacements

### AC-008 replacement

GIVEN an identifier already exists for one tenant, Identifier Scheme, and normalized identifier value, WHEN another identifier with the same uniqueness scope is submitted, THEN the second operation is rejected regardless of the existing row's lifecycle status. A verified-primary uniqueness violation remains independently rejected.

### AC-030 replacement

GIVEN a valid tenant-scoped request for either Party type, WHEN Party creation succeeds, THEN exactly one new Party for that request is returned in `DRAFT` state with audit facts and version; no `Idempotency-Key` is required or interpreted.

### AC-074 replacement

GIVEN Tenant A already stores Identifier Scheme S with normalized identifier N, WHEN another Party Identifier for Tenant A, Scheme S, and normalized N is submitted, THEN it is rejected atomically and no duplicate Party Identifier or corresponding outbox side effect is committed.

### AC-075 replacement

GIVEN two concurrent requests attempt to create the same Tenant A + Scheme S + normalized identifier N, WHEN both transactions complete, THEN the PostgreSQL uniqueness constraint permits at most one Party Identifier row to commit for that key.

### AC-076 replacement

GIVEN the normalized identifier N exists for Tenant A and Scheme S, WHEN N is submitted under Tenant B or under a different Identifier Scheme, THEN this permanent-uniqueness rule alone does not reject it; all other scheme, tenant, validation, and business rules still apply.

## 5. Database contract consequence

The authoritative DBML MUST enforce an unconditional unique constraint/index on:

`(tenant_id, identifier_scheme_id, normalized_value_hash)`

The previous partial uniqueness limited to `PENDING_VERIFICATION` and `VERIFIED` is superseded. No idempotency table or other persistence structure is authorized.

## 6. Architecture consequence

AR-001, as previously defined as a missing durable Party-creation command-idempotency store, is resolved by superseding the requirement that created it. It MUST NOT be replaced by another idempotency implementation.

The C4 model MUST NOT contain a `Platform Logging Capability`. The internal `Decryption Security Log Adapter` remains an application component because emit-before-return behavior must be testable, but it targets the local application logging path rather than an external software system.

## 7. Unchanged decisions

This amendment does not change:

- V1 no-login/no-authentication/no-authorization posture;
- tenant scoping by `tenant-id`;
- encryption/HMAC separation and runtime secret injection;
- masking policy;
- lifecycle transitions;
- logical deletion/no automatic purge;
- Geographic Reference cache behavior;
- transactional outbox and RabbitMQ semantics;
- event-consumer deduplication by event ID;
- strict Clean Architecture;
- reactive execution;
- PostgreSQL ownership and forward-only database governance.
