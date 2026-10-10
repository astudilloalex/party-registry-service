# Party Identifier Consumer and Operations Guide

The [approved OpenAPI](../contracts/party-registry.openapi.yaml) is the wire-contract authority. It is copied to `META-INF/openapi.yaml` and served at `/q/openapi`, with annotation scanning disabled. All eight identifier operations are implemented:

| Method/path | Required operation input after context | Success |
|---|---|---|
| `POST /v1/parties/{partyId}/identifiers` | Closed create object and required key | `201`, generated pending identifier, version 0 |
| `GET /v1/parties/{partyId}/identifiers` | Canonical Party selector; optional declared filters/cursor/limit | `200`, historical page |
| `GET /v1/parties/{partyId}/identifiers/{identifierId}` | Canonical Party and identifier selectors | `200`, stored safe detail |
| `PATCH /v1/parties/{partyId}/identifiers/{identifierId}` | Nonempty metadata object and identifier `If-Match` | `200`, next identifier revision |
| `POST /v1/parties/{partyId}/identifiers/{identifierId}/verify` | Zero body bytes; identifier `If-Match`; optional key | `200`, verified result or original replay |
| `POST /v1/parties/{partyId}/identifiers/{identifierId}/reject` | Optional strict reason object; identifier `If-Match`; optional key | `200`, rejected result or original replay |
| `POST /v1/parties/{partyId}/identifiers/{identifierId}/revoke` | Optional strict reason object; identifier `If-Match`; optional key | `200`, revoked result or original replay |
| `POST /v1/parties/{partyId}/identifiers/{identifierId}/set-primary` | Optional strict replacement object; identifier `If-Match`; optional key | `200`, selected candidate or original replay |

## Trusted context, ownership and safe output

Require exactly one `Process-Id`, `Tenant-Id`, then `User-Id`, validated in that order before operation inputs. Header names are case-insensitive. Process/tenant must be exact canonical lowercase UUIDs without whitespace. User must be nonblank, at most 128 Unicode code points, and free of C0, DEL and C1 controls. Only accepted context enters MDC. Echo accepted `Process-Id` unchanged on success/error/replay, including later tenant/user failure; missing/duplicated/invalid process values are never echoed. `/q` management endpoints remain exempt. Context attributes requests and tenant scope; it is not an authentication mechanism. Conditional deployment authentication retains the existing shared challenge/error contract.

Every non-replay operation establishes owned Party existence before identifier business evaluation. Absent/cross-tenant Party yields `404 party-not-found`; an identifier absent under an owned Party, including another owner's identifier, yields `404 identifier-not-found`. Both Party types and DRAFT/ACTIVE/INACTIVE/ARCHIVED owners are supported. Identifier operations never change Party/subtype/nationality/scheme versions or state.

Successes contain exactly matching HTTP/body `status`, `code: successful` and `data`, plus declared pagination only for lists. Safe items require identifier/Party/scheme IDs, exact scheme code, masked value, stored status, primary flag, independent nonnegative int64 version and creation/modification instants. Issuer, validity dates and verification actor/time may be omitted when null. Errors contain **only status and one stable code**, never data, field errors, rejected values, SQL, causes or stack traces. No complete/normalized value, ciphertext, lookup hash, key version, replay digest or reason text is public. Service resources use typed DTO envelopes and CDI `ResponseManager`; the published global module owns final failures.

## Register and correct metadata

Creation requires nonnull/nonblank strings `identifierSchemeCode` and `value`. Their raw limits are 64 and 256 **UTF-16 units**, before normalization. Only optional `issuerCode`, `issuedOn`, `expiresOn` and boolean `isPrimary` are additionally allowed. Nullable issuer/dates accept omission/null; omitted primary defaults to false, while explicit null or scalar coercion is invalid. Dates must be valid exact ISO calendar strings. Duplicate/unknown properties, impossible dates, trailing content and incompatible tokens fail without effects. Missing/null body uses `request-body-required`; required/oversized fields use their declared field-specific 400 codes.

Issuer is an optional **issuing-entity code**, not a country. Limit submitted input to 64 Unicode code points. Bounded empty/Java-blank input becomes null. Other new/supplied codes must match ASCII letter groups separated by single underscores, `[A-Za-z]+(?:_[A-Za-z]+)*`, without trimming, and are written in locale-independent uppercase. Invalid structure gives `400 issuer-code-invalid`, excess length `400 issuer-code-too-long`. No Geographic Reference lookup occurs. Restoration, omitted metadata, public reads and historical snapshots preserve accepted legacy issuer text. Original parsed creation strings remain fingerprint inputs before new-write normalization.

New registration requires a known ACTIVE scheme compatible with the Party type or BOTH, plus its supported value processing rules. Expiration is optional even with legacy `requiresExpiration=true`; supplied expiration must be on/after the captured UTC date and not before supplied issue date. Issue date has no additional future-date restriction. A new record is PENDING_VERIFICATION/version 0 with equal creation/modification instants and accepted actor. A true creation flag is provisional and does not displace an existing verified primary.

PATCH accepts only `issuerCode`, `issuedOn`, `expiresOn`, with at least one property. Omission preserves; explicit null clears. It cannot change value, scheme, status, flags, verification, version or audit directly. Only pending/verified records can be corrected. The **resulting** retained/supplied date interval must be coherent and expiration absent/on-or-after the operation's UTC date; an expired-by-date record must be repaired or cleared, not bypassed by an issuer-only edit. Verified evidence and its prior verification/primary/value/creation fields remain intact. Valid identical correction still advances once. PATCH ignores replay headers and stores no completion.

Stored PENDING_VERIFICATION/VERIFIED reserves normalized value per tenant/scheme, regardless of date. New same- or other-Party duplicates return `409 identifier-uniqueness-conflict`. Rejection/revocation or an already stored EXPIRED state releases that reservation; new admission gets a new ID and retains the old record. Time passing alone releases neither a value nor a verified-primary flag.

## Closed lifecycle and primary ownership

| Stored state | verify | reject | revoke | set-primary |
|---|---|---|---|---|
| PENDING_VERIFICATION | VERIFIED if usable | REJECTED | Forbidden | Forbidden |
| VERIFIED | Forbidden | Forbidden | REVOKED | Eligible if usable |
| REJECTED | Forbidden | Forbidden | Forbidden | Forbidden |
| REVOKED | Forbidden | Forbidden | Forbidden | Forbidden |
| EXPIRED | Forbidden | Forbidden | Forbidden | Forbidden |

Forbidden current-version actions return `409 invalid-identifier-lifecycle`, not a no-op. No expire/reactivate endpoint or scheduled expiration workflow is added. Reads never infer/persist EXPIRED. Verification is operator confirmation, with `verifiedBy` equal to accepted User-Id and verification/modification time equal to one captured microsecond instant; it does not call an external verifier or decrypt/re-register the value. Absent expiration and expiration equal to the captured UTC date qualify. Past expiration/incoherent interval yields `422 identifier-validation-failure`. Scheme withdrawal does not erase previously admitted compatible evidence.

VERIFY requires **zero actual body bytes**, including with chunked transport. `{}`, literal null and whitespace are invalid. REJECT/REVOKE accept actual absent body, `{}`, or only optional string `reasonCode`/`reasonDetail`. Limits are 64/1000 Unicode code points. Strings are exact, including empty/control/supplementary values, without trimming or case conversion. Literal/root/property null, unknown/duplicate properties, coercion and trailing tokens are invalid. Reasons are independently durable private audit, encoded as bounded UTF-16 binary payloads to preserve all accepted strings including U+0000; they remain absent from public DTOs/events/completion logs.

Reject/revoke clear the primary flag, advance one identifier version and preserve identity, protected/masked value, metadata, creation audit and prior verification. Past-date pending/verified evidence can be withdrawn. No substitute primary is selected, and revoking the last qualifying evidence leaves an ACTIVE Party unchanged. Separate Party activation retains its existing version/policy: usable verified evidence qualifies without requiring primary.

Only usable VERIFIED evidence can be selected. Absent body, `{}`, omitted replacement property and `replaceCurrentPrimary:false` are equivalent. Literal/property null, nonbooleans, duplicates/unknowns or trailing tokens return `400 bad-request`. One verified primary exists per **tenant/Party/scheme**; other schemes/owners and provisional flags are independent. Past-date verified flagged evidence still owns the scope. Without explicit true replacement, another primary yields `409 primary-identifier-conflict`. With true, demotion precedes promotion in one transaction; both versions advance once and share actor/time while retaining all other fields. Response contains only the candidate. Already-primary selection is still a versioned success with one event.

A pending provisional primary cannot be verified while a different verified primary owns the scope, even if that owner is past-date. Verification preserves the provisional flag and never implicitly demotes either record. Register nonprimary evidence when it must be verified alongside an existing primary, then use explicit set-primary replacement.

## Syntax, version and durable replay precedence

Canonical lowercase UUID paths are required without trimming. Invalid Party path uses `400 party-id-invalid`; invalid identifier path uses `400 bad-request`. Identifier mutation If-Match must occur exactly once and contain bare `0|[1-9][0-9]*` through `9223372036854775807`. Quotes, signs, whitespace, leading zeros, wildcard and overflow are rejected by `if-match-required`, `if-match-duplicated`, `if-match-invalid`, `if-match-out-of-range` as applicable (400). The Party version is not this precondition.

After context/applicable framework checks:

1. Creation: body → required key → Party path.
2. PATCH: body → Party path → identifier path → If-Match; ignore keys.
3. Actions: body presence/shape → Party path → identifier path → If-Match → optional key.
4. Reads: Party path → identifier path where present → complete strict query/cursor validation.

Syntax is checked on historical retries too. Then completed replay/changed-input key conflict precedes live Party/identifier absence, actual version, lifecycle, target exhaustion, validity and primary conflict. Actual stale versions/independent losers yield `412 expected-version-mismatch`. Otherwise eligible maximum-version targets give `409 identifier-version-exhausted`, before date/primary checks. Replacement also validates former-primary exhaustion **before either write**. Rejected/timed-out/cancelled preacceptance attempts leave all prior records/flags/versions/audits/completions/enabled events unchanged.

Creation keys are required; VERIFY/REJECT/REVOKE/SET_PRIMARY keys are optional. Present values must occur exactly once, be nonblank, at most 128 code points and compare exactly without trimming/case folding. Keys are scoped by tenant and specific operation, not by target. Independent namespaces are `party.identifier.create.v1`, `.verify.v1`, `.reject.v1`, `.revoke.v1`, `.set-primary.v1`, also separate from Party/catalog/nationality operations.

Creation identity includes Party and all effective accepted properties. Nullable omission/null and omitted/false primary are equivalent; other parsed strings remain exact before normalization. Action identity includes Party, identifier, expected identifier version and exact effective reason/replacement. Absent/empty reason properties are equivalent, supplied empty string is distinct; replacement absent/false is equivalent. JSON property order and user/process do not change identity. Different target/version/reason/replacement under a completed key yields `409 idempotency-key-conflict` before current-state checks and exposes no saved result.

Equivalent create replay returns original **201 pending/version-zero**; action replay returns original **200 result**, including old flag/metadata/verification/time/version after correction, replacement, withdrawal, scheme retirement, Party archival or restart. Attribution stays original; response process belongs to the retry. Replay creates no revision/audit/logical event. Corrupt completion fails closed as sanitized 500. Unkeyed repeats have no historical recovery: read current GET before a new edit. A transport failure after commit is not proof of rollback; retry the original key/input to recover accepted state.

## Historical queries and exact pagination

List accepts only `identifierSchemeId`, `schemeCode`, `status`, `isPrimary`, `cursor`, `limit`, each at most once. Scheme ID is canonical UUID; scheme code is exact nonblank case-sensitive text, at most 64 code points, without trimming. Status is one of the five exact uppercase states; boolean query is exactly true/false. All filters combine with AND; valid unknown/mismatched schemes yield an empty page. Limit defaults to 50 only when absent and supports 1..200. Unknown/repeated/blank/invalid parameters yield 400 without widening results.

Order is createdAt DESC then PostgreSQL unsigned UUID DESC, at full stored precision, in both traversal directions. One read-only repeatable-read transaction supplies page/count/navigation; later requests are live continuations. Cursor is authenticated, bounded to 256 characters and scoped to resource/tenant/Party/every effective filter/limit. Absence and false are distinct, absent limit and explicit 50 equivalent. Changed/tampered/unknown-key/version/cross-resource cursor yields 400 without contents. Boundary row need not remain present; no cursor expiry guarantee is introduced.

Every list response has `status`, `code`, `data`, `nextCursor`, `prevCursor`, `totalElements`, `totalPages`, `numberOfElements`. Unavailable cursors are explicit null. Totals count all matches **before continuation**; pages use exact ceiling division and returned count equals data size. Empty owned collections succeed with zero counts; empty continuations retain current totals and only real available directions. Exact counts beyond int32 range yield sanitized 500, never truncation. Returned memory is page-bounded, but total counts can scan all matches; use the existing listing index and monitor query cost rather than add an index for every filter.

Natural-person current detail remains a separate complete **ascending** non-expired pending/verified projection. Historical list/detail includes every stored state and past-date evidence without decryption or side effects.

## Stable failure catalog

| Condition | HTTP/code |
|---|---|
| Body/path/query/header syntax | Declared field-specific `400` or `bad-request` |
| Missing/cross-tenant Party | `404 party-not-found` |
| Missing/wrong-owner identifier | `404 identifier-not-found` |
| Changed completed key | `409 idempotency-key-conflict` |
| Active-value duplicate | `409 identifier-uniqueness-conflict` |
| Forbidden lifecycle/maintenance state | `409 invalid-identifier-lifecycle` |
| Scoped primary ownership conflict | `409 primary-identifier-conflict` |
| Target/former cannot advance | `409 identifier-version-exhausted` |
| Stale independent identifier version | `412 expected-version-mismatch` |
| New scheme unknown/inactive/incompatible | Existing specific `422` scheme codes |
| Resulting validity/qualification failure | `422 identifier-validation-failure` |
| Unknown route / unsupported method / media | `404 not-found` / `405 method-not-allowed` / `415 unsupported-media-type` |
| Classified database/lock/statement/work unavailability | `503 dependency-unavailable` |
| Unexpected/corrupt snapshot/count overflow | `500 server-error` |

## Storage, secrets, rollout and safe rollback

V8 is additive and Flyway-owned: dedicated identifier completion/audit storage, restrictive qualified owner FKs and descending history index. V1–V7 remain immutable; no expiration/protection/text/replay backfill is performed. No TTL/cleanup policy is introduced for these completions or audits. Pre-cutover additional requests that never recorded their original completion cannot acquire a historical replay guarantee retroactively.

Provision the existing encryption/index/registration-HMAC/cursor material before traffic. Identifier fingerprints reuse `PARTY_REGISTRATION_IDEMPOTENCY_HMAC_KEY` with independent versioned domain separation. Keep this secret stable across replicas/restarts while records are retained; replacing it alone cannot verify old completions. A future rotation requires explicit retained verification material/key-version compatibility. Retain all encryption versions and long-lived index key material needed for stored identity. Cursors use existing `PARTY_CURSOR_CURRENT_SIGNING_KEY_ID`/key ring with identifier-only authentication scope; retain accepted older signing keys during rotation. No new secret requirement is introduced.

Deploy V8 and compatible completion/event codecs before enabling affected traffic. Mixed-version traffic must consistently reach handlers implementing durable registration/action semantics; older non-idempotent registration cannot share the guarantee. Events are optional (`disabled`, `stored-only`, `published`), but completions/private audit are independent. Preserve `party.identifier-created.v1`; new kinds are `party.identifier-updated.v1`, `party.identifier-verified.v1`, `party.identifier-rejected.v1`, `party.identifier-revoked.v1` and `party.identifier-primary-changed.v1`. Transition produces one event; replacement one per affected identifier. Payloads exclude values, hashes, keys and reasons. Enable new publication only after decoder compatibility; broker acknowledgement never occurs inside mutation acceptance.

Rollback retains V8, accepted identifiers/flags/versions, reasons, completions, outbox history and original verification secrets. Restore a contract-compatible binary or pause affected writes/publication; never drop storage, reset versions, rewrite history or reseed the catalog. Unknown postcommit delivery is resolved by keyed recovery, not by deleting accepted records.

## Budgets, telemetry and verification

Production defaults are lock 5s, statement 10s, complete mutation 15s; query statement 5s/traversal 15s. These are configured through existing `PARTY_MUTATION_*`/`PARTY_QUERY_*` variables. Limits are transaction-local, cleanup releases session/context on failure/cancellation, and writers are not automatically retried. Observe actual key/scope contention and valid serial outcomes rather than infer acceptance from response timing.

The existing request filter owns completion/correlation/MDC cleanup. Eight route labels/spans and created/applied/replayed/conflict/failure/cancellation outcomes are bounded; request context retains only committed disposition/key presence, never saved result/key. Lock metrics distinguish replay-key/primary-scope; page metrics contain numeric duration/rows/matches only. No raw identifiers, cursors, reasons or values become metric dimensions. Keep console format exactly:

```text
%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [userId=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```

Shared contract assertions live in `src/contractTest/java`; packaged/native assertions in `PackagedPartyIdentifierContractIT` and the selected identifier case of `PartyLifecycleRestartIT`. The latter discards original responses, relaunches two actual processes over retained isolated data, checks original replay/attribution and exact nonduplicated audit/events. Strict request/decoder and response reflection registration is explicit at API boundaries; unit alternative ports/clocks remain excluded from packaged/native classpaths. JVM success/metadata preparation is not native evidence.

Focused packaged checks can be selected without rerunning unrelated integration cases:

```shell
./gradlew quarkusIntTest --tests '*PackagedPartyIdentifierContractIT'
./gradlew quarkusIntTest --tests '*PartyLifecycleRestartIT.identifierCreationLifecyclePrimaryAndExactReasonAuditSurviveTwoActualProcesses'
```

Final acceptance retains full test/build, native build/test, architecture/public-envelope inspection and OpenSpec validation. In the confirmed standalone IDE workflow, use Java 25/resolved Gradle dependencies and Standard-mode JDTLS, run actual Sonar analysis on each final saved production/test/shared/integration file, and record saved Git blobs plus separate Sonar and JDTLS results. Reanalyze after fixes; an empty Problems panel or historical batch with unresolved types is not proof of completion. No blanket suppressions, rule exclusions or silent null-analysis changes are acceptable. Deferred nonblocking Sonar review during iteration does not mark final quality acceptance as passing.
