# Identifier-Scheme Consumer and Operations Guide

The [approved OpenAPI](../contracts/party-registry.openapi.yaml) is the wire-contract authority. Gradle copies that document to `META-INF/openapi.yaml`; annotation scanning is disabled through `mp.openapi.scan.disable=true`. The service exposes it at `/q/openapi` and uses it for Swagger documentation. All eight routes are implemented:

| Method/path | Inputs after trusted context | Success |
|---|---|---|
| `POST /v1/identifier-schemes` | Closed create JSON, required `Idempotency-Key` | `201`, generated DRAFT, version 0 |
| `GET /v1/identifier-schemes` | Four optional exact filters, cursor, limit | `200`, current global page |
| `GET /v1/identifier-schemes/by-code/{code}` | Required exact decoded code path value | `200`, current scheme in any state |
| `GET /v1/identifier-schemes/{schemeId}` | Required canonical UUID path value | `200`, current scheme in any state |
| `PATCH /v1/identifier-schemes/{schemeId}` | Closed presence-aware JSON, required `If-Match` | `200`, next accepted version |
| `POST /v1/identifier-schemes/{schemeId}/activate` | No body, required `If-Match`, optional key | `200`, ACTIVE or original replay |
| `POST /v1/identifier-schemes/{schemeId}/deprecate` | No body, required `If-Match`, optional key | `200`, DEPRECATED or original replay |
| `POST /v1/identifier-schemes/{schemeId}/retire` | No body, required `If-Match`, optional key | `200`, RETIRED or original replay |

## Global ownership, context, and public data

Catalog rows and exact-code uniqueness are **global**. All tenants read and operate on the same identities; deprecated and retired codes remain assigned. Tenant context attributes requests and scopes replay/cursors only. It does not create tenant-owned catalog copies. Context headers are not an authentication provider; existing deployment authentication remains conditional, and current application/test profiles do not enforce it.

Every request requires exactly one `Process-Id`, `Tenant-Id`, and `User-Id`, validated in that order before operation input. Names are case-insensitive. Process and tenant values must be canonical lowercase UUIDs, without trimming. The user must be nonblank, at most 128 Unicode code points, without C0, DEL, or C1 controls. Accepted `Process-Id` is echoed unchanged on **every** success, error, and replay, including later tenant/user failures. Missing, duplicated, or invalid process values are never echoed. `/q` management paths are exempt.

Successes are `{ "status": 201|200, "code": "successful", "data": ... }`, with the actual HTTP status equal to body status. Errors contain exactly `{ "status": <http-status>, "code": "<stable-code>" }`, never `data`, `errors`, messages, rejected values, SQL, stack traces, or internal causes. Known failures translate at the API boundary and the published shared handler sanitizes unexpected failures.

Every scheme DTO requires `id`, `code`, `issuingCountryCode`, `category`, `applicableSubjectType`, `name`, `normalizerKey`, `validatorKey`, boolean `requiresExpiration`, `status`, nonnegative 64-bit `version`, `createdAt`, and `updatedAt`. Absent nullable `description`, `minimumLength`, and `maximumLength` are **omitted**, not null. Timestamps are date-time strings. Audit actors remain stored internally and are not public fields. Reads do not normalize historical text, revalidate obsolete rule admission, or change audits.

## Create and maintain exact configuration

Required create fields are nonnull/nonblank `code`, `issuingCountryCode`, `category`, `applicableSubjectType`, `name`, `normalizerKey`, and `validatorKey`. Text is exact: no implicit trimming, case conversion, or Party-name normalization. Limits count Unicode code points: code/rule keys 64, name 150, description 500. Country must already match `[A-Z]{2}`; no external country lookup occurs.

Categories are `NATIONAL_ID`, `TAX_ID`, `PASSPORT`, `RESIDENCE_PERMIT`, `LEGAL_REGISTRATION_NUMBER`, and `OTHER`. Subject types are `NATURAL_PERSON`, `LEGAL_ENTITY`, and `BOTH`. Initial supported processing keys are normalizer `TRIM_UPPERCASE_V1` and validators `ALPHANUMERIC_V1`, `EC_NATIONAL_ID_V1`, and `EC_TAX_ID_V1`; values are exact and case-sensitive. Keys identify local rules, not executable expressions or new algorithms.

Description and either bound accept omission or null on create. `requiresExpiration` defaults to false **only when omitted**, rejects null, and remains legacy metadata. A true value never makes document expiration mandatory. Bound tokens must be JSON integers: wrong token/coercion is `400 bad-request`; integral values outside `1..32767` or resulting minimum greater than maximum are `422 identifier-scheme-length-range-invalid`. Bounds are evaluated exactly, including integers larger than Java integer capacity. Unknown processing keys yield `422 invalid-identifier-scheme-configuration`. A new draft has a generated UUID, version 0, and equal creation/modification timestamps.

Create and PATCH bodies are closed objects. Duplicate/unknown properties, nonobjects, wrong tokens, or client-owned identity/status/version/audit properties reject the whole request. Missing/null body yields `400 request-body-required`; missing/null/blank code yields `400 identifier-scheme-code-required`, oversized code `400 identifier-scheme-code-too-long`; other structure/format errors use `400 bad-request`.

PATCH permits exactly seven properties: `name`, `description`, `normalizerKey`, `validatorKey`, `minimumLength`, `maximumLength`, and `requiresExpiration`. Omission preserves a value; null clears only description or either bound. Empty `{}` yields `400 patch-property-required`. Schema bounds describe valid resulting values; business validation merges supplied bounds with retained ones.

| Current state | PATCH maintenance |
|---|---|
| DRAFT | All seven fields; resulting bounds and supported rule keys must pass admission |
| ACTIVE | Name/description only; preserve obsolete historical keys without readmission |
| DEPRECATED | Name/description only; preserve obsolete historical keys without readmission |
| RETIRED | No PATCH; `409 identifier-scheme-retired` |

Any attempted processing/bound/expiration field in ACTIVE/DEPRECATED returns `409 identifier-scheme-rules-locked`, even if equal to the stored value or mixed with an allowed field. Descriptive maintenance never unlocks rules or changes status. A valid identical-value update still increments version once and refreshes modification audit while preserving immutable identity and creation audit. PATCH stores no replay result.

## Lifecycle matrix and historical identifiers

| Current state | activate | deprecate | retire |
|---|---|---|---|
| DRAFT | ACTIVE | Rejected | RETIRED |
| ACTIVE | Rejected | DEPRECATED | RETIRED |
| DEPRECATED | Rejected | Rejected | RETIRED |
| RETIRED | Rejected | Rejected | Rejected |

Rejected new current-version actions return `409 invalid-identifier-scheme-lifecycle`, not a successful no-op. DRAFT activation validates stored supported-rule configuration and bounds, including historical drafts. Deprecation and direct retirement do not require supported historical rules, an example identifier, intermediate states, or removal of references. Retirement is terminal; it retains the row and code for lookup/listing.

Actions require **zero body bytes**. Do not send `{}`, `null`, whitespace, or a status property; any nonempty body returns `400 bad-request`. Successful new actions advance version once, retaining identity, all configuration, creation audit, existing Party/identifier fields and their independent versions, and prior replay outcomes. Catalog administration emits no tenant Party outbox events.

New initial/additional identifier admissions still require an ACTIVE compatible scheme and all existing registration rules. DRAFT/DEPRECATED/RETIRED reject new admission through the existing inactive-scheme contract. Qualifying verified historical evidence for Party activation does not acquire a new requirement that the scheme remain active. Expiration remains optional even with historical `requiresExpiration=true`.

## Version, validation precedence, and durable keys

`schemeId` is a required **path-only** canonical lowercase UUID; malformed values yield `400 identifier-scheme-id-invalid`. The decoded `code` path selector is exact, nonblank, and at most 64 code points. Valid absent IDs/codes return `404 identifier-scheme-not-found`.

PATCH and all actions require exactly one bare decimal `If-Match` matching `^(0|[1-9][0-9]*)$`, from 0 through `9223372036854775807`. Quotes, signs, whitespace, leading zeros, wildcards, repetitions, and overflow are invalid. Missing/repeated/malformed/overflowing values produce `if-match-required`, `if-match-duplicated`, `if-match-invalid`, and `if-match-out-of-range` (400). New stale versions/lost independent races return `412 expected-version-mismatch`. Otherwise eligible maximum-version mutations return `409 identifier-scheme-version-exhausted`.

After trusted context and applicable framework checks:

1. Create validates body, then required key. Completed replay/key conflict precedes exact-code uniqueness, which precedes semantic bounds/rule admission.
2. PATCH validates body, then scheme ID, then If-Match. New business evaluation is absence → version → state → exhaustion → resulting configuration.
3. Actions validate zero-byte body, then optional key, then scheme ID, then If-Match, **even on replay**. Completed replay/key conflict precedes current business checks. New actions evaluate absence → version → transition → exhaustion → activation configuration, where applicable.

Creation keys are required; lifecycle keys are optional. Supplied `Idempotency-Key` must occur exactly once, be nonblank and at most 128 Unicode code points, compared without trimming/case conversion. Missing required keys use `idempotency-key-required`; supplied invalid keys use `idempotency-key-duplicated`, `idempotency-key-blank`, or `idempotency-key-too-long` (400).

Replay identity is tenant/operation/exact key, independently for `identifier-scheme.create.v1`, `.activate.v1`, `.deprecate.v1`, and `.retire.v1`, and independently of Party/nationality namespaces. Creation equivalence compares all accepted properties, materializes omitted false metadata, and treats omitted/null description/bounds identically. Other text remains exact. Lifecycle equivalence is action-scoped scheme ID and expected version. JSON ordering, current user, and process do not change intent.

Equivalent completed create retries return the **original 201 DRAFT/version 0 and timestamps**, even after activation/retirement or restart. Equivalent keyed actions return their **original 200 state/version/timestamps**, even after later changes make the expected version stale. Original audit attribution stays stored; response correlation belongs to the current retry. Replay performs no new transition, revision, or event. Changed completed input produces `409 idempotency-key-conflict` before current-state/uniqueness checks and does not expose the saved result. Failed attempts save no completion and do not consume the key.

Concurrent equivalent keyed attempts converge on one accepted outcome. Independent writes arbitrate on the version; no field merging or double advance occurs. Keyless actions cannot replay: an old-version repeat returns 412, while a new current-version same-action repeat returns lifecycle conflict. Use GET for the current version after a keyless lost response; a historical replay version is not the latest version for maintenance.

## Requests and pagination

Set `$BASE`, `$TENANT`, and `$PROCESS` to actual deployment/canonical UUID values. An example create is:

```shell
curl -X POST "$BASE/v1/identifier-schemes" \
  -H "Tenant-Id: $TENANT" -H "Process-Id: $PROCESS" -H 'User-Id: catalog-operator' \
  -H 'Idempotency-Key: reviewed-catalog-create-1' -H 'Content-Type: application/json' \
  --data '{"code":"Example-Passport","issuingCountryCode":"EC","category":"PASSPORT","applicableSubjectType":"BOTH","name":"Example Passport","normalizerKey":"TRIM_UPPERCASE_V1","validatorKey":"ALPHANUMERIC_V1"}'
```

Use its returned `$SCHEME_ID` and version for later operations. A bodyless action is:

```shell
curl -X POST "$BASE/v1/identifier-schemes/$SCHEME_ID/activate" \
  -H "Tenant-Id: $TENANT" -H "Process-Id: $PROCESS" -H 'User-Id: catalog-operator' \
  -H 'If-Match: 0' -H 'Idempotency-Key: reviewed-catalog-activate-1'
```

Each operation has a representative header/path request example, success examples, and explicit error examples in OpenAPI. Lifecycle examples have no request body.

Listing accepts only `issuingCountryCode`, `category`, `applicableSubjectType`, `status`, `cursor`, and `limit`, each at most once. The four exact filters combine with AND. `NATURAL_PERSON`/`LEGAL_ENTITY` never implicitly include `BOTH`; use separate exact queries or unfiltered data for a compatibility view. Omitted status includes all states. Unknown/repeated/blank/invalid queries return `400 bad-request`; defaults apply only to absent parameters.

Limit is a decimal integer `1..200`, default 50. Order is `createdAt ASC`, then unsigned UUID `id ASC`. Forward/backward server-issued cursors are opaque and authenticated against the requesting tenant, all four exact filters, effective limit and scheme resource. Tampering, another tenant/filter/limit, or a Party/nationality cursor yields `400 bad-request`. There is no frozen snapshot across catalog changes and no cursor expiry policy.

Scheme pages always include `numberOfElements` equal to `data.length`. Emit `nextCursor`/`prevCursor` only when another page is available; unavailable cursors are **omitted**. `totalElements`/`totalPages` are optional and omitted when not supplied; current pages supply no totals. If supplied in a contract-compatible implementation, totals describe the entire matching catalog and pages use the effective limit. Empty matches are `{ "status": 200, "code": "successful", "data": [], "numberOfElements": 0 }`. Party/nationality complete-count/null-cursor contracts keep their own shapes.

## Signing, resilience, and observability

Provision a Base64-encoded **32-byte** independent pagination signing key through deployment secrets on every replica. Existing configuration is:

```properties
party-registry.pagination.current-signing-key-id=${PARTY_CURSOR_CURRENT_SIGNING_KEY_ID:v1}
party-registry.pagination.signing-keys.v1=${PARTY_CURSOR_SIGNING_KEY_V1}
```

There is no production/development key fallback. Test-profile deterministic keys are test-only. Scheme tokens use separate `identifier-scheme-list-cursor-v1` and `identifier-scheme-list-scope-v1` authentication domains with the existing ring. Do not reuse encryption/index/registration-fingerprint secrets. Distribute new verification keys to all replicas before changing the current ID; retain verification keys needed by outstanding tokens and rollback builds. Removing a key deliberately invalidates its outstanding cursors and requires an operational consumer policy because there is no automatic expiry.

Existing bounded settings apply:

| Environment override | Default | Scope |
|---|---|---|
| `PARTY_QUERY_STATEMENT_TIMEOUT` | 5s | Read SQL |
| `PARTY_QUERY_TRAVERSAL_TIMEOUT` | 15s | Complete read/page traversal |
| `PARTY_MUTATION_LOCK_TIMEOUT` | 5s | Mutation lock wait |
| `PARTY_MUTATION_STATEMENT_TIMEOUT` | 10s | Mutation SQL |
| `PARTY_MUTATION_OPERATION_TIMEOUT` | 15s | Complete mutation |

Scheme pages use bounded limit-plus-one keysets and sequential neighbor probes, without full-catalog counting or offsets. Writes serialize scoped keys before global code/row locks, use guarded next-version writes, and commit scheme/completion atomically before success. Cancellation/failure releases owned sessions and rolls back pending work. There are no automatic mutation retries. Classified connection/lock/statement/traversal/operation failures return `503 dependency-unavailable`; unknown failures/corrupted stored completions are logged and sanitized as `500 server-error`. Framework unknown-route/method/media failures use `404 not-found`, `405 method-not-allowed`, and create/PATCH `415 unsupported-media-type`; `401 unauthorized` is conditional on actual authentication enforcement.

The existing single request filter owns context, completion logs, and cleanup. The console format remains exactly:

```text
%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [userId=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```

Bounded spans cover `identifier-scheme.create`, `.retrieve`, `.retrieve-by-code`, `.list`, `.patch`, `.activate`, `.deprecate`, and `.retire`; mutation outcomes distinguish APPLIED/REPLAYED. Do not log keys, cursors, request bodies, rejected values, or secrets, or use IDs/codes/users/tenants as metric dimensions. Monitor pool pressure, lock/query duration and failure rates.

## V7 rollout and rollback

Apply `V7__add_identifier_scheme_management.sql` through Flyway before enabling the new catalog routes. V7 is additive: dedicated `identifier_scheme_idempotency_records` references the **global scheme**, not a fake Party, and the listing index orders `(created_at, id)` ascending. Initial/reference-data provisioning and every schema/index/constraint change remain versioned immutable Flyway migrations. Normal approved catalog maintenance is API business DML. Never run manual DDL/DML, edit applied migrations, enable schema generation, or use the API as schema/reference-seed provisioning.

Deploy compatible route/snapshot readers and signing keys to all replicas before advertising durable replay. No new jurisdiction catalog or algorithm is introduced by this release. Test migrations are never production seeds: V1000 is immutable; V1001–V1003 are packaged-owned fixtures, V1004 supplies API legacy/exhaustion cases, and V1005 supplies historical Party evidence.

An application rollback retains V7, every accepted catalog row/code/state/version, all replay history/snapshots, old/new foreign keys and historical identifiers. Do not downgrade lifecycle states, reset versions, delete completed keys, drop replay storage, or create a fresh seed to accommodate an older binary. Existing older code lookup can read the same global rows; pause affected scheme routes until a contract-compatible binary is restored. Party/identifier data and historical Party snapshots must remain intact.

## Contract verification and packaged handoff

JVM tests parse OpenAPI with the existing Swagger parser, check unresolved references and exact operation structures, validate all request/success/error examples against referenced/composed schemas, and validate real HTTP JSON from all eight routes. API-created scenarios cover optional omission, null clearing, exact bounds, historical creation replay and available navigation. Profile-scoped existing fault ports validate real sanitized 500/503 bodies for every route; conditional authentication documentation does not invent an authentication profile.

`src/contractTest/java/com/alexastudillo/partyregistry/contract/IdentifierSchemeContractValidator.java` is a reusable **test-only** helper using existing Jackson/Swagger/JUnit dependencies. Both JVM unit/contract tests and packaged/native integration tests compile this shared source root. Construct it with a parsed `OpenAPI`; `readContract(Path)` resolves the source document, `assertRequest(path, HttpMethod, json)` validates request JSON, and `assertResponse(path, HttpMethod, httpStatus, json)` checks declared response schemas/status/codes and omission/page invariants. `assertSchema(name, JsonNode)` also supports direct negative fixtures. It resolves `$ref`/`allOf`/`oneOf`/`anyOf`/`not` and checks nullability, required/closed properties, enums, scalar tokens, exact numeric bounds, Unicode code-point limits, patterns, arrays and formats. Negative fixtures prove drift is rejected rather than accepting parser success alone.

`PackagedIdentifierSchemeContractIT` reuses this helper in the external test process against HTTP from the JVM/native artifact and parsed served/packaged documents, with schemas `IdentifierSchemeCreateRequest`, `IdentifierSchemeUpdateRequest`, `IdentifierSchemeResponse`, `IdentifierSchemeCreatedApiResponse`, `IdentifierSchemeApiResponse`, `IdentifierSchemeCollectionApiResponse`, and shared `ApiErrorResponse`. Unit-test CDI fixtures are excluded from packaged/native execution, and no validator is copied into production or added as a runtime dependency. `PartyLifecycleRestartIT` retains PostgreSQL data across real JVM/native process restarts and verifies historical creation and all lifecycle replays with original audit, fresh correlation, unchanged retired current state, and unchanged persisted counts. Run the JVM and native commands documented in the README to verify these contracts on the selected artifact.
