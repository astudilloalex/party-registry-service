# ADR-003: Sensitive identifier protection and fail-closed decryption boundary

## Status

Proposed

## Context

**CONFIRMED REQUIREMENT:** complete identifiers are restricted, encrypted with authenticated encryption, exactly searched and permanently uniqueness-checked through tenant-effective HMAC, masked by BR-025, and returned in plaintext only by a separate no-store decryption operation. Decryption security-log emission through the local application logging path must succeed before plaintext return. Party Registry implements no V1 authentication/authorization, owns no audit store, and depends on no external logging platform.

For V1, encryption and HMAC master keys are runtime secrets, not a remote key-service dependency. Approved secret-injection mechanisms are unversioned `.env` files for local/development use and protected VPS runtime injection through Podman secrets or a host-only protected environment file. Secret values never belong in Git, the OCI image, logs, traces, metrics or generated architecture artifacts.

The service preserves the AlexAstudillo enterprise logging convention established by `geographic-reference-service`: request correlation is carried in MDC, messages use a stable `[LOCATION]` prefix convention, and the console format includes process and caller context. Party Registry adapts the business-context field from `companyId` to the bounded-context term `tenantId`.

There is no `Platform Logging Capability` software system in Party Registry V1. Structured operational and decryption-security messages are emitted by the application through its configured local logging path. Whether the container runtime or host later captures stdout/stderr is an operations detail, not a Party Registry software-system dependency and not part of the C4 model.

## Decision Drivers

- Prevent plaintext persistence and telemetry leakage.
- Keep secret/configuration mechanisms and logging technology outside domain/application layers.
- Make exact lookup and permanent tenant-and-scheme uniqueness possible without record scanning/decryption.
- Make security-log-before-disclosure ordering testable and fail closed.
- Preserve the enterprise logging format without inventing an external logging service.
- Avoid inventing an external KMS/key service for V1 when runtime secret injection is the approved mechanism.

## Considered Options

1. Explicit application ports with a runtime-secret/crypto adapter and a local structured decryption-security-log adapter.
2. Embed cryptography and logging in REST resources or persistence repositories.
3. Store decrypted or reversibly masked lookup fields for convenience.
4. Introduce an external key-management service not required by V1.
5. Introduce an external logging platform not present in the approved deployment.

## Decision

**TO-BE DECISION:** select option 1. Application use cases orchestrate boundary-neutral protection outcomes. Outbound adapters own runtime-secret resolution, authenticated encryption/decryption, tenant HMAC and local structured security-log emission. Decryption order is tenant-qualified read -> authenticated decrypt -> acknowledged local security-log emission -> no-store plaintext response. Any failure before the response returns no plaintext.

Authenticated encryption refers to ciphertext integrity/authentication, such as authenticated-encryption tag verification. It does **not** introduce user authentication, login, authorization, roles or Keycloak into Party Registry V1.

Runtime secret resolution is configuration/deployment behavior, not a network integration. Local/development environments may resolve the versioned masters from an uncommitted `.env` file. VPS environments may resolve them from Podman secrets or a protected host-only environment file with least-privilege filesystem permissions. The application consumes only configured secret values and versions; it does not call a V1 key-management service.

Operational and decryption-security logs follow the enterprise logging baseline. The inbound request filter owns MDC population/cleanup and propagates `processId`, `userId` and `tenantId`; it generates `process-id` when absent under the approved request-context rules. The message convention remains `[LOCATION] message`. Logging helpers, MDC, SLF4J and Quarkus logging configuration remain in API/infrastructure/bootstrap concerns and are forbidden in the pure domain/application core.

The decryption-security-log adapter considers emission successful when the required structured message has been accepted by the configured local application logger without synchronous failure. V1 does not claim remote delivery, centralized persistence, external acknowledgement or centralized retention.

## Consequences

### Positive

- Provider, runtime-secret and logging technology do not leak into domain logic.
- Plaintext exposure paths are explicit and testable.
- Exact search does not scan/decrypt records.
- Production secrets remain outside repository and OCI artifacts.
- Logging is consistent with the enterprise service baseline while using Party Registry terminology.
- The C4 model contains only real external systems.

### Negative

- Mapping and controlled transient plaintext handling add boundary complexity.
- Local application logging failure intentionally denies decryption.
- V1 does not guarantee centralized/durable log retention.
- Secret rotation requires operational coordination between injected secret versions and persisted `encryption_key_version` metadata.

### Risks

The approved no-authentication V1 posture leaves high residual risk for any internally connected consumer. Loss of historical key versions can make encrypted data unavailable. Misconfigured secret mounts or environment files can make readiness fail and must never trigger fallback to committed/default secrets. A host/runtime logging failure after local emission is outside the V1 application acknowledgement boundary.

## Security and Data Impact

Separate encryption/HMAC masters, tenant derivation, key versions, minimal plaintext lifetime, no sensitive telemetry, exact mask policy, no-store response and least-privilege secret injection are mandatory.

Decryption security logs contain tenant ID, user ID, process ID, Party Identifier ID, timestamp, action/outcome and safe diagnostic classification only. They never contain plaintext identifiers, ciphertext, HMAC fingerprints, keys, tokens or confidential payloads.

## Operational Impact

Readiness reports missing, invalid or unusable mandatory runtime secrets without revealing values. Operators can inspect application/container logs and non-sensitive decrypt/security-log-emission failures. No external logging platform is required or modeled by Party Registry V1.

The baseline console pattern is the enterprise pattern used by `geographic-reference-service`, adapted to Party Registry context:

```properties
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [user=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```

## Validation

Security tests must cover ciphertext authentication/tag verification, tenant HMAC separation, permanent identifier uniqueness boundaries, masks, plaintext absence, secret/version failure and rotation compatibility, no-store, security-log fields/order, local logging failure and cross-tenant non-disclosure.

Architecture tests must prove that domain/application core code does not import MDC, SLF4J, Quarkus logging/configuration APIs or concrete secret providers. Request-filter tests must verify MDC creation, propagation and cleanup for `processId`, `userId` and `tenantId`.

## Reversal or Replacement Strategy

Any identity-provider integration, authorization, external key service, external logging service, audit store or different rotation model requires new requirements, threat analysis and an approved ADR. Existing encrypted data must retain readable historical key-version compatibility.

## Traceability

DBML Party Identifier protection/uniqueness facts; BR-010/021/024/025/027 as amended by `docs/requirements/requirements-amendment-001.md`; FR-005/006/013/014/046/047/048/049; SR-001..007; DR-004/005/007; RG-102/103/105; Issue #4 human clarification; approved enterprise logging baseline in `astudilloalex/geographic-reference-service` (`MDCRequestFilter`, `LogUtil`, `application.properties`).
