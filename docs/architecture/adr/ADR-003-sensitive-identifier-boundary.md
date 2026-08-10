# ADR-003: Sensitive identifier protection and fail-closed decryption boundary

## Status

Proposed

## Context

**CONFIRMED REQUIREMENT:** complete identifiers are restricted, encrypted with authenticated encryption, exactly searched and uniquely checked through tenant-effective HMAC, masked by BR-025, and returned in plaintext only by a separate no-store decryption operation. Decryption security-log emission must succeed before plaintext return. Party Registry implements no V1 authentication/authorization and owns no audit store.

For V1, encryption and HMAC master keys are runtime secrets, not a remote key-service dependency. Approved secret-injection mechanisms are unversioned `.env` files for local/development use and protected VPS runtime injection through Podman secrets or a host-only protected environment file. Secret values never belong in Git, the OCI image, logs, traces, metrics or generated architecture artifacts.

The service must also preserve the AlexAstudillo enterprise logging convention already established by `geographic-reference-service`: request correlation is carried in MDC, messages use a stable `[LOCATION]` prefix convention, and the console format includes process and caller context. Party Registry adapts the business-context field from `companyId` to the bounded-context term `tenantId`.

## Decision Drivers

- Prevent plaintext persistence and telemetry leakage.
- Keep secret/configuration mechanisms and provider APIs outside domain/application layers.
- Make exact lookup tenant-isolated without record scanning/decryption.
- Make security-log-before-disclosure ordering testable and fail closed.
- Preserve the enterprise logging format without introducing logging dependencies into domain or application code.
- Avoid inventing an external KMS/key service for V1 when runtime secret injection is the approved mechanism.

## Considered Options

1. Explicit application ports with a runtime-secret/crypto adapter and a structured decryption-security-log adapter.
2. Embed cryptography and logging in REST resources or persistence repositories.
3. Store decrypted or reversibly masked lookup fields for convenience.
4. Introduce an external key-management service not required by the approved V1 scope.

## Decision

**TO-BE DECISION:** select option 1. Application use cases orchestrate boundary-neutral protection outcomes. Outbound adapters own runtime-secret resolution, authenticated encryption/decryption, tenant HMAC and structured security-log emission. Decryption order is tenant-qualified read -> authenticated decrypt -> acknowledged security-log emission -> no-store plaintext response. Any failure returns no plaintext.

Authenticated encryption in this ADR refers to ciphertext integrity/authentication, such as authenticated-encryption tag verification. It does **not** introduce user authentication, login, authorization, roles or Keycloak into Party Registry V1.

Runtime secret resolution is configuration/deployment behavior, not a network integration. Local/development environments may resolve the versioned masters from an uncommitted `.env` file. VPS environments may resolve them from Podman secrets or a protected host-only environment file with least-privilege filesystem permissions. The application consumes only configured secret values and versions; it does not call a V1 key-management service.

Operational and decryption-security logs must follow the enterprise logging baseline. The inbound request filter owns MDC population/cleanup and propagates `processId`, `userId` and `tenantId`; it generates `process-id` when absent under the approved request-context rules. The message convention remains `[LOCATION] message`. Logging helpers, MDC, SLF4J and Quarkus logging configuration remain in API/infrastructure/bootstrap concerns and are forbidden in the pure domain.

## Consequences

### Positive

- Provider, runtime-secret and logging technology do not leak into domain logic.
- Plaintext exposure paths are explicit and testable.
- Exact search does not scan/decrypt records.
- Production secrets remain outside repository and OCI artifacts.
- Logging is consistent with the enterprise service baseline while using Party Registry terminology.

### Negative

- Mapping and controlled transient plaintext handling add boundary complexity.
- Logging capability failure intentionally denies decryption.
- Secret rotation requires operational coordination between injected secret versions and persisted `encryption_key_version` metadata.

### Risks

The approved no-authentication V1 posture leaves high residual risk for any internally connected consumer. Loss of historical key versions can make encrypted data unavailable. Misconfigured secret mounts or environment files can make readiness fail and must never trigger fallback to committed/default secrets.

## Security and Data Impact

Separate encryption/HMAC masters, tenant derivation, key versions, minimal plaintext lifetime, no sensitive telemetry, exact mask policy, no-store response and least-privilege secret injection are mandatory.

Decryption security logs contain tenant ID, user ID, process ID, Party Identifier ID, timestamp, action/outcome and safe diagnostic classification only. They must never contain plaintext identifiers, ciphertext, HMAC fingerprints, keys, tokens or confidential payloads.

## Operational Impact

Readiness reports missing, invalid or unusable mandatory runtime secrets without revealing values. Operators monitor non-sensitive decrypt failures and security-log-emission failures; centralized log collection/retention remains platform-owned.

The baseline console pattern is the enterprise pattern used by `geographic-reference-service`, adapted to Party Registry context:

```properties
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [user=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```

## Validation

Security tests must cover ciphertext authentication/tag verification, tenant HMAC separation, masks at boundaries, plaintext absence, secret/version failure and rotation compatibility, no-store, security-log fields/order, logging failure and cross-tenant non-disclosure.

Architecture tests must prove that domain/application code does not import MDC, SLF4J, Quarkus logging/configuration APIs or concrete secret providers. Request-filter tests must verify MDC creation, propagation and cleanup for `processId`, `userId` and `tenantId`.

## Reversal or Replacement Strategy

Any identity-provider integration, authorization, external key service, audit store or different rotation model requires new requirements, threat analysis and an approved ADR. Existing encrypted data must retain readable historical key-version compatibility.

## Traceability

DBML lines 23-28, 269-328; BR-010/021/024/025/027; FR-005/013/014/046/048/049; SR-001..007; DR-004/005/007; RG-102/103/105; approved enterprise logging baseline in `astudilloalex/geographic-reference-service` (`MDCRequestFilter`, `LogUtil`, `application.properties`).
