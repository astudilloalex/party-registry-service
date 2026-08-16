# Contract and Foundation Placement — T003

## Decision record

This document records bounded, reversible implementation placement decisions. It does not change requirements, architecture, DBML, workflow state, or deployment policy.

| Item | Decision |
|---|---|
| Java base package | `com.alexastudillo.partyregistry` |
| Domain | `com.alexastudillo.partyregistry.domain` |
| Application | `com.alexastudillo.partyregistry.application` |
| Inbound REST adapter | `com.alexastudillo.partyregistry.adapter.in.rest` |
| Outbound adapters | `com.alexastudillo.partyregistry.adapter.out.<capability>` |
| Bootstrap | `com.alexastudillo.partyregistry.bootstrap` |
| OpenAPI source | `api/openapi/v1/party-registry.openapi.yaml` |
| Event schema source | `api/events/v1/party-registry-events.schema.json` |
| Event examples | `api/events/v1/examples/*.json` |

The package choice extends the existing Gradle group `com.alexastudillo` and adds the bounded-context name. It creates no module split. Contracts stay outside generated/runtime source; later adapters map transport models explicitly to application inputs and results.

## REST operation and requirement inventory

Each OpenAPI operation carries machine-readable `x-requirements` and `x-acceptance-criteria` entries. The effective operation inventory is:

| Operation | Requirement / acceptance focus |
|---|---|
| `createParty` | FR-001/002/007/008/010/011/015/016; AC-001/002/011/012/019/023/030/036 |
| `searchParties` | FR-001/011/023; AC-001/019/028/033/081 |
| `getParty` | FR-001/011/022; AC-001/019/032 |
| `updateParty` | FR-001/007/008/011/012/024; AC-001/011/012/019/020/034/070 |
| `transitionPartyStatus` | FR-001/007/008/011/012/025/026; AC-001/011/012/019/020/035/067/069 |
| `getPartyDetails`, `updatePartyDetails` | FR-001/007/008/011/012/021/027..029; AC-001/011/012/019/020/031/037/038/068/070 |
| `addNationality`, `searchNationalities`, `getNationality`, `updateNationality`, `endNationality` | FR-001/003/007/008/011/012/015/017/021/030..033; AC-001/005/011/012/019/020/023/028/031/039..043/070/081 |
| `createPartyIdentifier` | FR-001/005..008/011/018/046..048; AC-001/007/008/010..012/019/044/072..074/076/077 |
| `searchPartyIdentifiers`, `getPartyIdentifier` | FR-001/011/034/036/046; AC-001/019/028/045/047/072/073/081 |
| `findPartyIdentifiersByPartyAndScheme` | FR-001/011/035/046; AC-001/019/046/072/073 |
| `exactSearchPartyIdentifiers` | FR-001/005/011/013/046/048; AC-001/007/019/021/072/073/077 |
| `updatePartyIdentifier`, `transitionPartyIdentifierStatus` | FR-001/006..008/011/012/037..039; AC-001/008/009/011/012/019/020/048..050/069/070 |
| `decryptPartyIdentifier` | FR-001/011/014 and SR-002/005; AC-001/019/022/027/062/071 |

Party creation includes exactly one matching detail object so a successful transaction can satisfy the validated Party/detail integrity contract. Details have retrieval and conditional update but no independent deletion or lifecycle operation. Archive, revoke, and nationality end are explicit logical lifecycle operations; no HTTP DELETE implies physical deletion.

## Context, concurrency, error, and cache contract

- Every business operation accepts mandatory `tenant-id` (canonical lowercase UUID) and `user-id` (1..128), plus optional canonical lowercase UUID `process-id`. The service generates a process ID when absent and returns the effective value in the `process-id` response header. No tenant field exists in a body.
- V1 has no OpenAPI security scheme. No operation defines HTTP 401 or 403.
- Modifications of existing aggregates require `If-Match: "<non-negative-version>"`. Absence is HTTP 428 / `PRECONDITION_REQUIRED`; mismatch is HTTP 412 / `VERSION_CONFLICT`. Successful versioned results expose `ETag` and do not duplicate version in response bodies.
- Errors use the `ApiResponse` shape (`status`, `code`, `data`) and the effective categories `VALIDATION_ERROR`, `NOT_FOUND`, `CONFLICT`, `PRECONDITION_REQUIRED`, `VERSION_CONFLICT`, `DEPENDENCY_UNAVAILABLE`, and `INTERNAL_ERROR`. `IDEMPOTENCY_CONFLICT` is absent. Error data cannot carry stack traces, SQL/provider internals, or complete identifiers.
- Cross-tenant lookup is indistinguishable from absence and returns the standard 404 envelope.
- The decryption operation alone returns plaintext. Its success and failure responses carry `Cache-Control: no-store`; successful disclosure occurs only after local structured security-log emission.

## TC-001 query decision

The reversible parameter spelling and serialization are:

- `page`: one decimal integer, zero-based, default `0`.
- `size`: one decimal integer, default `20`, inclusive range `1..100`.
- Party filters: optional single-value `type` and `status`.
- Nationality filters within path-scoped Party: optional single-value `countryCode`, `primary`, and `active`.
- Party Identifier filters: optional single-value `partyId`, `identifierSchemeId`, `status`, and `primary`.
- Party/Scheme lookup selection: optional `selection=ALL|VERIFIED_PRIMARY`, default `ALL`.
- V1 exposes no client-selectable `sort` parameter. Every paginated result is ordered by resource UUID ascending, which is therefore both the sole order and deterministic tie-breaker.
- Repeated parameters, comma-separated lists, empty values, unknown parameters/filters, unsupported selection values, negative pages, and out-of-range sizes are `VALIDATION_ERROR`. Parameter names are case-sensitive; enum and country-code values use their documented uppercase representation.

These spellings implement only TC-001's delegated technical authority; they do not add search scope. Amendment 002 removes Identifier Scheme browse/search parameters and endpoints.

## Event catalog, publication, and compatibility

The immutable schema catalog contains exactly:

- `party.created.v1`, `party.updated.v1`, `party.activated.v1`, `party.inactivated.v1`, `party.archived.v1`;
- `party.nationality-added.v1`, `party.nationality-updated.v1`, `party.nationality-removed.v1`;
- `party.identifier-created.v1`, `party.identifier-updated.v1`, `party.identifier-verified.v1`, `party.identifier-rejected.v1`, `party.identifier-expired.v1`, `party.identifier-revoked.v1`.

Every event carries stable `eventId`, versioned `eventType`, `schemaVersion=1`, `tenantId`, aggregate identity/type/version, occurrence time, optional correlation/causation, and a minimal typed payload. Identifier payloads contain only IDs and lifecycle state; they contain no plaintext, ciphertext, HMAC fingerprint, mask, issuer, verification actor, or confidential Party attributes. Examples are synthetic.

Messages are persistent, published to the approved durable topic exchange with publisher confirms, and delivered at least once. Retries/recovery retain the same event ID. Consumers own queues/DLQs and deduplicate by event ID. Published V1 schema meanings are immutable; an incompatible change requires a newly approved versioned event type/schema rather than mutation of V1.

## Compatibility and explicit exclusion inventory

The following are intentionally absent and must remain absent from generated interfaces and runtime adapters:

1. Identifier Scheme lookup, browse, search, create, update, status, retire, delete, background mutation, or Scheme event contracts.
2. Public outbox read/write/status/retry/recovery administration. Authorized failed-event recovery is an internal operational boundary, not REST.
3. `Idempotency-Key`, command replay, request fingerprints, replay stores, and `IDEMPOTENCY_CONFLICT`.
4. Authentication/security schemes, login, roles, permissions, Keycloak/Access Management request integration, and HTTP 401/403.
5. Plaintext in ordinary responses, listings, events, telemetry, error messages, examples, or persistence contracts.
6. Ciphertext, fingerprints, key material, stack traces, SQL details, provider bodies, tenant identifiers in request bodies, physical database names, and persistence entities.
7. HTTP DELETE operations for business records, physical deletion, automatic purge, Party-detail deletion/lifecycle, and clock-driven lifecycle transitions.
8. Scheme, database, Flyway, platform, infrastructure, audit-store, or decryption events.

No breaking V1 REST or event meaning is authorized. Additive changes still require requirements traceability and independent API-contract review; incompatible changes require a separately approved contract version.
