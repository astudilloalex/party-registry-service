# ADR-004: Permanent tenant-and-scheme identifier uniqueness without command idempotency

## Status

Proposed

## Context

The project owner clarified that Party Registry V1 does **not** require HTTP command idempotency, `Idempotency-Key`, replay-result persistence, request fingerprints, or any separate idempotency store.

The actual business invariant is uniqueness of an official identifier within one tenant and one Identifier Scheme. For example, if Tenant A registers normalized Ecuadorian identification number `0123456789` under the `EC_CEDULA` scheme, that tenant and scheme may never contain a second Party Identifier representing the same normalized value.

Complete identifier values are restricted and are never persisted in plaintext. The existing design already computes `normalized_value_hash` as a tenant-isolated HMAC-SHA-256 fingerprint of the normalized identifier. This gives PostgreSQL a safe deterministic key for exact lookup and uniqueness enforcement.

The previous RG-104 command-idempotency decision and architecture risk AR-001 are superseded by the human-authority clarification documented by Issue #4 and `docs/requirements/requirements-amendment-001.md`.

## Decision Drivers

- Enforce the business identity rule at the authoritative persistence boundary.
- Never persist the complete normalized identifier in plaintext.
- Avoid an idempotency table, Redis/Valkey, replay cache, request fingerprint, or additional datastore.
- Make concurrent duplicate creation deterministic through PostgreSQL.
- Preserve identifier history: revoke, reject, or expire must not make the same identifier reusable inside the same tenant and scheme.
- Keep uniqueness tenant-scoped and scheme-scoped rather than global.

## Considered Options

1. Unconditional PostgreSQL uniqueness on `(tenant_id, identifier_scheme_id, normalized_value_hash)`.
2. Partial uniqueness only for `PENDING_VERIFICATION` and `VERIFIED` rows.
3. Command-level `Idempotency-Key` persistence and replay.
4. External cache/store for duplicate prevention.
5. Plaintext normalized identifier as a database unique key.

## Decision

**TO-BE DECISION:** select option 1.

The permanent Party Identifier business key is:

`tenant_id + identifier_scheme_id + normalized_value_hash`

`normalized_value_hash` is the tenant-isolated HMAC-SHA-256 fingerprint computed after the Identifier Scheme's approved normalization logic.

The DBML therefore defines an unconditional unique constraint/index on:

```text
(tenant_id, identifier_scheme_id, normalized_value_hash)
```

The constraint applies to every lifecycle state: `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED`, `EXPIRED`, and `REVOKED`.

Lifecycle transitions never free the identifier for reuse. Correcting an identifier continues to use revoke-plus-create semantics, but the replacement must represent a different normalized identifier value if it uses the same tenant and scheme.

The same normalized string is permitted in another tenant or under another Identifier Scheme, subject to all validation and subject-type rules.

Party creation itself has no idempotency semantics. Two valid Party-creation requests may create two distinct Parties unless another explicit domain rule rejects them. Official-identifier uniqueness is enforced only when Party Identifier data is created or otherwise reaches the persistence constraint.

## Concurrency Semantics

For two concurrent attempts with the same tenant, Identifier Scheme, and normalized identifier value:

1. both requests may independently normalize and calculate the same tenant-effective HMAC fingerprint;
2. PostgreSQL evaluates the same unconditional unique key;
3. at most one Party Identifier row can commit;
4. the losing transaction maps the uniqueness violation to the stable API `CONFLICT` category and commits no duplicate identifier/outbox side effect.

There is no requirement to replay the winning request's response to the losing caller.

## Consequences

### Positive

- The database is the final concurrency authority for the business uniqueness invariant.
- No additional store or distributed consistency problem is introduced.
- No plaintext unique key is required.
- Rejected, expired, and revoked history cannot be used to bypass uniqueness.
- Exact lookup and uniqueness reuse the same protected deterministic fingerprint.

### Negative

- A mistakenly registered identifier cannot be deleted/revoked and then recreated with the identical normalized value in the same tenant and scheme; correction workflows must update allowed mutable metadata or use explicit future governance if same-value replacement is ever required.
- HMAC rotation requires controlled recalculation while preserving uniqueness throughout the maintenance window.

## Security and Data Impact

The unique key contains only tenant UUID, Identifier Scheme UUID, and tenant-isolated HMAC fingerprint. Plaintext identifiers remain absent from persistence, logs, traces, metrics, and events.

The HMAC master remains separate from the encryption master and is supplied through the approved runtime secret-injection mechanism.

## Operational Impact

Database migrations must create/validate the unconditional unique index before implementation is considered compliant. If pre-existing data violates the new invariant, database validation must stop and require human remediation; migrations must not silently delete or merge records.

Metrics/logging may identify a uniqueness conflict by safe category and correlation identifiers but must not log the submitted plaintext identifier or HMAC fingerprint.

## Validation

Required tests include:

- same tenant + same scheme + same normalized identifier is rejected;
- the duplicate remains rejected when the existing row is `REJECTED`, `EXPIRED`, or `REVOKED`;
- same normalized value in a different tenant is permitted by this rule;
- same normalized value under a different Identifier Scheme is permitted by this rule;
- concurrent duplicates result in at most one committed Party Identifier and corresponding outbox effect;
- database schema contains the unconditional unique key and no command-idempotency table/store;
- exact lookup continues to use tenant-effective HMAC and no plaintext persistence.

## Reversal or Replacement Strategy

Changing this rule to allow identifier reuse, status-scoped uniqueness, global uniqueness, or command-level idempotency requires a new human-approved requirement, DBML revision, data-migration impact analysis, and ADR. It must not be inferred by implementation.

## Traceability

Issue #4; `docs/requirements/requirements-amendment-001.md`; `docs/database/v1-scheme.dbml`; identifier protection decisions OD-003/RG-103/RG-105; strict DBML governance in `.factory/project.yaml`.
