# ADR-003: Sensitive identifier protection and fail-closed decryption boundary

## Status

Proposed

## Context

**CONFIRMED REQUIREMENT:** complete identifiers are restricted, encrypted with authenticated encryption, exactly searched and uniquely checked through tenant-effective HMAC, masked by BR-025, and returned in plaintext only by a separate no-store decryption operation. Decryption audit emission must succeed before plaintext return. Party Registry implements no V1 authentication/authorization and owns no audit store.

## Decision Drivers

- Prevent plaintext persistence and telemetry leakage.
- Keep key material and provider APIs outside domain/application layers.
- Make exact lookup tenant-isolated without record scanning/decryption.
- Make audit-before-disclosure ordering testable and fail closed.

## Considered Options

1. Explicit application ports with a key/crypto adapter and a security-event adapter.
2. Embed cryptography and logging in REST resources or persistence repositories.
3. Store decrypted or reversibly masked lookup fields for convenience.

## Decision

**TO-BE DECISION:** select option 1. Application use cases orchestrate boundary-neutral protection outcomes. Outbound adapters own key retrieval/derivation, authenticated encryption/decryption, tenant HMAC and structured event emission. Decryption order is tenant-qualified read -> authenticated decrypt -> acknowledged audit emission -> no-store plaintext response. Any failure returns no plaintext.

## Consequences

### Positive

- Provider/key/logging technology does not leak into domain logic.
- Plaintext exposure paths are explicit and testable.
- Exact search does not scan/decrypt records.

### Negative

- Mapping and controlled transient plaintext handling add boundary complexity.
- Logging capability failure intentionally denies decryption.

### Risks

The approved no-authentication V1 posture leaves high residual risk for any internally connected consumer. Loss of key versions can make data unavailable.

## Security and Data Impact

Separate encryption/HMAC masters, tenant derivation, key versions, minimal plaintext lifetime, no sensitive telemetry, exact mask policy, no-store response and least-privilege secret mount are mandatory.

## Operational Impact

Readiness reports unusable mandatory keys. Operators monitor non-sensitive decrypt failures and audit-emission failures; centralized log collection/retention remains platform-owned.

## Validation

Security tests for ciphertext authentication, tenant HMAC separation, masks at boundaries, plaintext absence, key failure/rotation versions, no-store, event fields/order, logging failure and cross-tenant non-disclosure.

## Reversal or Replacement Strategy

Any identity-provider integration, authorization, external key service, audit store or different rotation model requires new requirements, threat analysis and an approved ADR. Existing encrypted data must retain readable key-version compatibility.

## Traceability

DBML lines 23-28, 269-328; BR-010/021/024/025/027; FR-005/013/014/046/048/049; SR-001..007; DR-004/005/007; RG-102/103/105.
