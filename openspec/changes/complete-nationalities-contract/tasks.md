## 1. Foundation

- [ ] 1.1 Add framework-independent nationality identity, inclusive-period validation/overlap, effective-date eligibility, and presence-aware date-merge behavior in Domain.
  - Requirements: Inclusive nationality validity and conflict prevention; Validity PATCH changes only supplied dates; Primary designation and optional idempotency.
  - Verification: Domain unit tests cover open bounds, equal dates, shared endpoints, inverted intervals, explicit-null PATCH, and current/future/ended eligibility without HTTP or Mutiny dependencies.

- [ ] 1.2 Define transport-neutral nationality commands, queries, detached results, typed failures, and reactive read/mutation/cursor ports in Application; extend the API's stable response-code catalog and typed failure translator.
  - Requirements: Nationality identity and tenant isolation; Nationality create input and country validation; Inclusive nationality validity and conflict prevention; Primary designation and optional idempotency; Nationality response and error contract.
  - Verification: Application contracts contain no HTTP, Hibernate, or persistence models; translator tests assert the specified 400/404/409/422/503 mappings and unchanged generic failures.

- [ ] 1.3 Add strict nationality create and presence-aware PATCH request models plus API parsing for canonical Party ID, nationality UUID, body shape, ASCII country normalization, date values, required/optional idempotency keys, and nonempty PATCH.
  - Requirements: Nationality create input and country validation; Nationality identity and tenant isolation; Creation response and required idempotency; Validity PATCH changes only supplied dates.
  - Verification: API parser tests distinguish omission from null, reject duplicate/unknown JSON fields and wrong types, and assert exact field-specific codes versus `bad-request` without changing unrelated DTO behavior.

- [ ] 1.4 Implement strict six-parameter nationality list parsing, capturing a single UTC evaluation date and rejecting repeated/unknown/blank/invalid query values.
  - Requirements: Nationality list filters and input validation.
  - Verification: Parser tests cover defaults, uppercase ASCII country, boolean literals, ISO dates, limit 1..200, cursor cardinality, and malformed values.

- [ ] 1.5 Map `party_nationalities` to a Hibernate Reactive persistence entity and verify it against the existing Flyway V1/V2 schema without changing existing migrations or enabling generated DDL.
  - Requirements: Nationality identity and tenant isolation; Inclusive nationality validity and conflict prevention; Nationality detail and public representation.
  - Verification: Mapping/integration tests round-trip nullable dates, UUID identity, normalized country, primary flag, and audit timestamps against Flyway-managed PostgreSQL.

## 2. Core Implementation

- [ ] 2.1 Implement tenant-qualified nationality detail reads, including an explicit Party existence check and detached result mapping; never consult the geographic service on reads.
  - Requirements: Nationality identity and tenant isolation; Nationality detail and public representation.
  - Verification: Reactive persistence tests distinguish `party-not-found` from `nationality-not-found` and retrieve ended/future records for both Party types and retained statuses without leaking another tenant's data.

- [ ] 2.2 Implement bounded, canonical nationality HMAC cursors with a distinct domain separator, direction and `(createdAt, nationalityId)` boundary, bound to tenant, Party, normalized filters, effective date, and limit using the existing key ring.
  - Requirements: Scoped nationality pagination and collection envelope.
  - Verification: Cursor tests accept authentic forward/back tokens, reject altered/truncated/root-Party/cross-scope tokens, and verify a newly inferred UTC date invalidates the previous day's cursor.

- [ ] 2.3 Implement tenant-qualified list count and keyset page/navigation reads in one repeatable-read snapshot, with inclusive-date filtering, stored primary filtering, bounded timeouts, and descending `(createdAt, nationalityId)` order.
  - Requirements: Nationality list filters and input validation; Scoped nationality pagination and collection envelope.
  - Verification: PostgreSQL tests verify AND filters, expired/current/future behavior, tied timestamps, forward/back traversal, matching counts, empty results, and consistent metadata under one read snapshot.

- [ ] 2.4 Inspect the nationality list/count/navigation query plan; add a next-version Flyway migration for `(party_id, created_at DESC, id DESC)` only if the measured plan needs the supporting index.
  - Requirements: Scoped nationality pagination and collection envelope.
  - Verification: Record the query-plan evidence in the relevant test or implementation notes; if added, verify the new migration applies and the index is used, with V1–V5 left immutable.

- [ ] 2.5 Implement application list/detail orchestration, including scoped cursor verification before data reads, one UTC date per list request, Party-versus-nationality absence, and detached page boundaries/results.
  - Requirements: Nationality identity and tenant isolation; Nationality list filters and input validation; Scoped nationality pagination and collection envelope; Nationality detail and public representation.
  - Verification: Use-case tests with a fixed clock and port doubles assert no reads for invalid cursors, correct scope/date on generated cursors, and distinct 404 failures.

- [ ] 2.6 Implement a nationality mutation transaction boundary that acquires a tenant-qualified Party root lock, serializes same-Party writes, validates stored periods, advances root version once per changed accepted mutation, and translates only the two named PostgreSQL exclusion constraints to typed conflicts.
  - Requirements: Nationality identity and tenant isolation; Inclusive nationality validity and conflict prevention.
  - Verification: Reactive tests show the root lock coordinates a competing Party mutation, changed writes increment once, both named constraints map to their respective 409 failures, and unrelated storage errors remain unexpected failures.

- [ ] 2.7 Add separately versioned create and set-primary nationality result-snapshot codecs, distinct operation scopes, unambiguous effective-input fingerprints, and transaction-scoped `(tenant, operation, key)` locking using `api_idempotency_records`.
  - Requirements: Creation response and required idempotency; Primary designation and optional idempotency.
  - Verification: Codec/adapter tests verify effective-default equivalence, Party/target mismatch conflicts, action separation, historical timestamps, and no user/process contribution to fingerprints.

- [ ] 2.8 Implement keyed nationality creation: resolve completed replay before current-state and country checks, verify tenant Party ownership and normalized-country recognition on new attempts, then insert the nationality and immutable result snapshot atomically under the prescribed key-to-root lock order.
  - Requirements: Nationality create input and country validation; Inclusive nationality validity and conflict prevention; Creation response and required idempotency; Nationality identity and tenant isolation.
  - Verification: Integration tests verify one country lookup only on a new attempt, no lookup on replay, recognized inactive codes accepted, unknown/unavailable results typed correctly, failed attempts consume no key, and equivalent retries preserve the original ID/timestamps.

- [ ] 2.9 Implement atomic validity PATCH by loading the tenant-owned record under the root lock, merging only supplied bounds, rechecking inclusive same-country/primary overlaps, and persisting modification audit without changing identity, country, primary flag, or creation audit.
  - Requirements: Validity PATCH changes only supplied dates; Inclusive nationality validity and conflict prevention; Nationality identity and tenant isolation.
  - Verification: Integration tests verify omitted versus explicit-null bounds, unchanged accepted PATCH updates its timestamp, and rejected empty/inverted/overlapping changes preserve rows and audit values.

- [ ] 2.10 Implement atomic set-primary for a single captured UTC date: reject ineffective targets, demote only intersecting other primaries before promoting the target, preserve nonintersecting history, and leave audit/version unchanged on an unkeyed no-op.
  - Requirements: Primary designation and optional idempotency; Inclusive nationality validity and conflict prevention.
  - Verification: Transaction tests cover past/future targets, partial-history demotion, one root-version advance for changed transfers, and rollback of all designation/audit changes on failure.

- [ ] 2.11 Connect optional set-primary idempotency to the mutation transaction, resolving completed snapshots before current eligibility/state checks and storing successful transfers or no-op results for replay.
  - Requirements: Primary designation and optional idempotency.
  - Verification: Integration tests show an equivalent keyed retry returns the original 200 result after an intervening transfer without touching current rows, while a changed target yields 409 and a failed attempt consumes no key.

- [ ] 2.12 Wire nationality create/PATCH/set-primary application use cases to their ports and domain policies with typed failure propagation and finite, nonblocking dependency/transaction budgets.
  - Requirements: Nationality create input and country validation; Inclusive nationality validity and conflict prevention; Creation response and required idempotency; Validity PATCH changes only supplied dates; Primary designation and optional idempotency.
  - Verification: Use-case tests cover normalized input, failed/unusable geographic lookup, changed-key conflict before lookup, rollback/no-op outcomes, and cancellation/unknown failure preservation without manual subscription or blocking waits.

- [ ] 2.13 Expose exactly the five approved nationality routes with strict request binding, tenant context from the existing filter, API DTO mapping, and CDI `ResponseManager` success/pagination responses; keep set-primary bodyless and omit `If-Match`.
  - Requirements: Nationality request context and correlation; Nationality identity and tenant isolation; Nationality detail and public representation; Scoped nationality pagination and collection envelope; Nationality response and error contract.
  - Verification: Signature tests show every business endpoint returns `Uni<RestResponse<ApiResponse<T>>>` with API DTO `T`; HTTP tests show 201/200 matching body statuses, exact DTO fields, list-only metadata, accepted Process-Id echo, and error bodies with only `status`/`code`.

- [ ] 2.14 Add bounded nationality route classification and applied/replayed/conflict observability, preserving the existing single request-context filter, MDC propagation/cleanup, log format, and `/q` exclusion.
  - Requirements: Nationality request context and correlation; Creation response and required idempotency; Primary designation and optional idempotency.
  - Verification: Observability/filter tests distinguish all five route shapes, accept no raw IDs/keys/cursors as labels, assert ordered header errors and Process-Id echo/absence, and show context is not carried into the next request.

- [ ] 2.15 Update the approved static OpenAPI nationality operations/schemas/examples and packaged contract for all declared query, date, idempotency, primary, country-outage, success-header, and code-specific failure semantics.
  - Requirements: All requirements in `specs/party-nationalities/spec.md`.
  - Verification: `OpenApiContractTest` asserts source and packaged OpenAPI expose five implemented operations, strict create/PATCH schemas, list metadata, Process-Id success headers, 503 create outcome, and nationality-specific 400/404/409/422 codes; remove the obsolete not-implemented assertion.

## 3. Verification

- [ ] 3.1 Add nationality HTTP contract tests for strict context/header/body/path/query validation, 400/404 tenant concealment, 405/415/unknown route, and sanitized unexpected 500 responses.
  - Requirements: Nationality request context and correlation; Nationality identity and tenant isolation; Nationality create input and country validation; Nationality list filters and input validation; Validity PATCH changes only supplied dates; Nationality response and error contract.
  - Verification: `@QuarkusTest` requests assert exact status/body code/keys and permitted Process-Id echo for all five routes; malformed inputs cause no writes and no raw exception/SQL/detail leakage.

- [ ] 3.2 Add end-to-end nationality success/temporal contract tests for both Party types and retained statuses, country recognition/outage, inclusive same-country/primary conflicts, PATCH audit, and effective primary transfer/no-op.
  - Requirements: Nationality create input and country validation; Inclusive nationality validity and conflict prevention; Nationality detail and public representation; Validity PATCH changes only supplied dates; Primary designation and optional idempotency; Nationality response and error contract.
  - Verification: `@QuarkusTest` exercises create/get/PATCH/set-primary with exact 201/200 envelopes and preserved historical rows, dates, timestamps, and root Party fields.

- [ ] 3.3 Add HTTP list and replay contract tests for filter intersection, empty and multi-page metadata, signed next/previous cursor scope, required create key, optional set-primary key, and equivalent/changed retries.
  - Requirements: Creation response and required idempotency; Nationality list filters and input validation; Scoped nationality pagination and collection envelope; Primary designation and optional idempotency.
  - Verification: `@QuarkusTest` reproduces page order without gaps on unchanged data, rejects cross-tenant/Party/date/limit/tampered cursors, and confirms original replay results without duplicate or additional writes.

- [ ] 3.4 Add separate-session PostgreSQL concurrency/restart tests for competing creates, validity PATCHes, primary transfers, and shared/different keyed attempts; exercise timeout/cancellation and failure rollback where supported.
  - Requirements: Inclusive nationality validity and conflict prevention; Creation response and required idempotency; Primary designation and optional idempotency.
  - Verification: Concurrent tests observe one accepted mutation or a typed conflict, no overlapping records/primary periods or partial transfer, no key consumed on failure, and equivalent replay after restart without event-loop blocking.

- [ ] 3.5 Extend packaged JVM and native integration tests for the five nationality routes, real packaged geographic-reference response, conflict, completed keyed replay, and scoped pagination with Flyway-managed data.
  - Requirements: Nationality create input and country validation; Inclusive nationality validity and conflict prevention; Creation response and required idempotency; Scoped nationality pagination and collection envelope; Nationality response and error contract.
  - Verification: Packaged/native test targets pass and prove response DTOs and versioned snapshots serialize without public Domain/persistence fields.

- [ ] 3.6 Inspect all modified Java files for JDTLS/LSP diagnostics and new compiler warnings, run `./gradlew test` followed by `./gradlew build`, and run the project's native build/test targets.
  - Requirements: All requirements in `specs/party-nationalities/spec.md`.
  - Verification: Diagnostics are resolved, required Gradle/native checks pass, and nationality HTTP signature/contract tests confirm the shared response boundary.
