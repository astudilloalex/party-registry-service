## Execution Rules

Requirement names below refer to `specs/identifier-scheme-management/spec.md` and `specs/identifier-scheme-lifecycle/spec.md` in this change. Execute sections in order; later tasks depend on the contracts and behavior established by earlier sections.

Every behavioral implementation task requires TDD: write its meaningful failing test first, run it and confirm the intended missing behavior, implement the minimum passing change, then refactor with the relevant tests green. Record the test command and red/green evidence in task progress before marking the task complete. Verification-only regression tasks may already be green; record that result without manufacturing a failure. Reread relevant global steerings and project decisions before programming each increment. If a later fix changes implementation or invalidates an earlier diagnostic/test/build result, rerun the affected verification gates and refresh their evidence before declaring completion.

Use strict Clean Architecture and the selected reactive profile throughout. Domain owns synchronous policies; Application owns orchestration and I/O ports; API owns HTTP validation/mapping; Infrastructure owns reactive persistence, transactions, cryptography, and composition. Never expose framework sessions through inner-layer ports. Add concise English Javadoc to every new type and resolve modified-file diagnostics as work proceeds. A checked task means its implementation and stated verification are complete, not merely that files exist.

## 1. Foundation

- [ ] 1.1 Read the global Clean Architecture and Quarkus reactive steerings, project rules, and approved change artifacts; confirm the layer owners, global catalog scope, transition matrix, PATCH restrictions, and narrow HTTP-contract overrides before implementation.
  - Requirements: Global catalog ownership; State-dependent maintenance; Identifier-scheme lifecycle transition matrix; Standard response and failure boundary.
  - Verification: Record the applicable steering paths and architectural/contract constraints, including Domain independence, permitted Application Mutiny dependency, 412 preconditions, and status/code-only errors.

- [ ] 1.2 Establish the implementation baseline: inspect the current migration sequence, published response-library APIs, native test wiring, and existing registration/serialization patterns; run `./gradlew test` before production changes.
  - Requirements: Complete published identifier-scheme contract; Standard response and failure boundary; Lifecycle effects on new identifier admission.
  - Verification: Record the baseline result, next available migration version, actual `ResponseManager`/`PaginationMetadata` API, and existing environment prerequisites; classify any preexisting failure.

- [ ] 1.3 Add test-first architectural guards for business JSON resource signatures and API DTO payloads, reusing existing inward-dependency, framework-isolation, resource-delegation, and cycle rules.
  - Requirements: Standard response and failure boundary.
  - Verification: Negative fixtures prove prohibited Domain/bare DTO/raw-response signatures are detected; production architecture tests pass without widening existing dependency allowlists.

## 2. Domain Rules

- [ ] 2.1 Test and complete scheme creation invariants for exact text, Unicode code-point limits, country/enums, optional fields, initial DRAFT/version-zero state, equal creation timestamps, and legacy expiration metadata.
  - Requirements: Creation request and initial state; Supported configuration and coherent length bounds; Lifecycle effects on new identifier admission.
  - Verification: Fast Domain tests cover required/blank/oversized values and boundary lengths without a framework or database; existing valid factories and optional-expiration behavior remain compatible.

- [ ] 2.2 Implement a pure length-bound validation path that accepts exact integral input, validates 1–32767 and resulting minimum/maximum coherence, and converts to bounded Domain values only after validation.
  - Requirements: Supported configuration and coherent length bounds; Partial update presence and immutable fields.
  - Verification: Tests cover omitted/null/equal bounds, zero/negative/32768 values, values beyond Java integer capacity, and PATCH coherence against retained bounds without overflow or truncation.

- [ ] 2.3 Implement supported-rule configuration checks against the existing exact normalizer/validator key sets without evaluating a fabricated sample identifier or coupling record reconstruction to current rule availability.
  - Requirements: Supported configuration and coherent length bounds; Draft activation and configuration eligibility; Active scheme deprecation; Direct retirement and terminal state.
  - Verification: Tests accept each supported key, reject unknown/case-altered keys during admission, and allow restoration and withdrawal of historical schemes with obsolete keys.

- [ ] 2.4 Add Domain-owned presence-aware scheme changes and merge behavior using `FieldUpdate`, preserving omitted values and clearing only nullable description/length fields.
  - Requirements: Partial update presence and immutable fields.
  - Verification: Unit tests distinguish omission, explicit null, and supplied values and prove identity/code/country/category/subject type are not editable through the changes model.

- [ ] 2.5 Implement state-dependent maintenance: all seven PATCH fields in DRAFT, name/description only in ACTIVE or DEPRECATED, and no PATCH in RETIRED.
  - Requirements: State-dependent maintenance; Partial update presence and immutable fields.
  - Verification: Parameterized tests cover every state/property combination, equal-value attempts on locked properties, mixed allowed/locked fields, and rejection without altering the original model.

- [ ] 2.6 Add checked scheme-version advancement and accepted-update audit behavior, including identical-value edits, creation-audit retention, and a transport-neutral exhausted-version failure.
  - Requirements: Mutation concurrency and audit outcome; Lifecycle atomicity and historical retention.
  - Verification: Domain tests prove exactly one advance per candidate, nonnegative versions, maximum-version rejection without overflow, and unchanged identity/creation audit values.

- [ ] 2.7 Implement DRAFT-to-ACTIVE behavior with state and exhaustion checks before pure configuration eligibility.
  - Requirements: Identifier-scheme lifecycle transition matrix; Draft activation and configuration eligibility.
  - Verification: Tests accept eligible drafts, reject each other state, classify unsupported configuration/range failures, and preserve the original scheme on rejection.

- [ ] 2.8 Implement ACTIVE-to-DEPRECATED behavior without requiring supported historical processing keys or changing scheme configuration.
  - Requirements: Identifier-scheme lifecycle transition matrix; Active scheme deprecation; Lifecycle atomicity and historical retention.
  - Verification: Tests cover success and every invalid source state, retain configuration/audit history, and deprecate an active scheme with obsolete rules.

- [ ] 2.9 Implement direct retirement from DRAFT, ACTIVE, or DEPRECATED and terminal rejection from RETIRED; complete parameterized coverage of all 12 state/action pairs.
  - Requirements: Identifier-scheme lifecycle transition matrix; Direct retirement and terminal state; Lifecycle atomicity and historical retention.
  - Verification: All permitted matrix entries produce the next-version target state; all disallowed entries emit the expected neutral violation without changing prior values or references.

## 3. Application Contracts and Orchestration

- [ ] 3.1 Add typed scheme commands/queries, selectors, lifecycle actions, exact effective-request models, detached scheme/page results, and applied/replayed outcomes without HTTP, JSON, CDI, or persistence dependencies.
  - Requirements: Safe current representation and lookup; Durable creation idempotency; Lifecycle idempotency scope and equivalence; Cursor pagination and list metadata.
  - Verification: Model tests prove create defaults/null equivalence, exact-value inequality, lifecycle target/version equivalence, and safe detached results; architecture isolation tests pass.

- [ ] 3.2 Define read, cursor, mutation, and mutation-context ports with sequential scoped capabilities and postcommit outcome semantics, keeping registration-facing code lookup compatible.
  - Requirements: Global catalog ownership; Global code uniqueness and creation atomicity; Mutation concurrency and audit outcome; Lifecycle atomicity and historical retention.
  - Verification: Port contract tests and scoped doubles support the approved workflows using Domain/Application types only; no session, transaction, HTTP, or event-publication type leaks through the ports.

- [ ] 3.3 Add scheme-specific transport-neutral Application failures and deliberate Domain-violation translation; update affected exhaustive switches while preserving existing failure classifications.
  - Requirements: Standard response and failure boundary; Supported configuration and coherent length bounds; State-dependent maintenance; Identifier-scheme lifecycle transition matrix.
  - Verification: Failure tests distinguish absence, duplicate code, stale version, locked/retired edits, lifecycle conflict, configuration/range failure, and exhaustion; unknown/cancellation failures retain their original meaning.

- [ ] 3.4 Implement the ID-or-code retrieval use case, converting optional absence to the scheme-specific failure and returning safe current results in every lifecycle state.
  - Requirements: Safe current representation and lookup; Global catalog ownership.
  - Verification: Bounded Application tests prove both selector paths, exact code behavior, all-state retrieval, not-found handling, and no write/audit side effects.

- [ ] 3.5 Implement listing orchestration with exact conjunctive criteria, tenant/filter/limit cursor scope, bounded ordered slices, available navigation, and count-only metadata.
  - Requirements: Listing and filter semantics; Cursor pagination and list metadata; Global catalog ownership.
  - Verification: Port-double tests cover unfiltered/all-state and empty lists, exact BOTH filtering, default/max limits, forward/backward navigation, and invalid cursor scope before any read.

- [ ] 3.6 Implement creation orchestration in the scoped mutation callback: resolve completed key/equivalence first, serialize/check global code next, then validate configuration, create the draft, insert it, and record completion atomically.
  - Requirements: Creation request and initial state; Global code uniqueness and creation atomicity; Durable creation idempotency; Supported configuration and coherent length bounds.
  - Verification: Tests prove replay/key-conflict before uniqueness/configuration, code conflict before semantic configuration, one UUID/time capture for a new success, and no completed outcome for a failed attempt.

- [ ] 3.7 Implement PATCH orchestration with ordered absence/version/state/exhaustion/configuration evaluation, retained-bound merging, a controlled clock, and exactly one accepted version/audit advance.
  - Requirements: Partial update presence and immutable fields; State-dependent maintenance; Mutation concurrency and audit outcome; Supported configuration and coherent length bounds.
  - Verification: Application tests assert failure precedence, unchanged rejected records, identical-value success, exact audit attribution, and no replay completion for PATCH.

- [ ] 3.8 Implement new unkeyed activation, deprecation, and retirement orchestration inside the mutation callback, delegating all state/configuration decisions to Domain.
  - Requirements: Draft activation and configuration eligibility; Active scheme deprecation; Direct retirement and terminal state; Lifecycle request validation and failure precedence; Lifecycle atomicity and historical retention.
  - Verification: Tests cover all actions and ordered absence/version/state/exhaustion/eligibility failures, including withdrawal of obsolete rules and no Party/outbox interaction.

- [ ] 3.9 Add keyed lifecycle resolution before scheme locking: validate effective intent, preserve tenant/action scope, replay historical success, record new successful completion, and leave failed keys reusable.
  - Requirements: Lifecycle idempotency scope and equivalence; Durable successful lifecycle replay.
  - Verification: Tests prove replay after later retirement, original versions/timestamps/actor retention, changed-target/version conflicts, action/tenant independence, and no Domain reexecution during completed replay.

- [ ] 3.10 Extend semantic operation observation for scheme use cases, preserving the existing neutral observation port and subscription-scoped applied/replayed/error/cancelled outcomes.
  - Requirements: Trusted context and process correlation; Standard response and failure boundary; Durable successful lifecycle replay.
  - Verification: Tests prove observation sees the correct disposition/context and cannot replace a success, failure, or cancellation; Application remains free of concrete telemetry dependencies.

## 4. Flyway and Reactive Persistence

- [ ] 4.1 Write fresh/upgrade migration tests, then add the next available immutable production migration for `identifier_scheme_idempotency_records`, its correct scheme FK/checks/indexes, and the ascending `(created_at, id)` scheme-list index.
  - Requirements: Global code uniqueness and creation atomicity; Durable creation idempotency; Durable successful lifecycle replay; Cursor pagination and list metadata.
  - Verification: Flyway upgrades the previous production version and initializes a fresh database; the new PK/FK/checks/indexes exist, Party replay storage remains valid, and applied migration checksums are unchanged.

- [ ] 4.2 Map the dedicated completed-operation entity/identity and preserve scheme entity/mapper compatibility, including checked SMALLINT conversion and the activated-identity trigger.
  - Requirements: Supported configuration and coherent length bounds; Global catalog ownership; Lifecycle atomicity and historical retention.
  - Verification: Mapping tests round-trip allowed/null/boundary values and retain global identities/audit/version fields; defensive narrowing failures cannot silently corrupt values.

- [ ] 4.3 Implement deterministic effective-request fingerprint encoding for scheme create and each lifecycle action with explicit operation domains and full input equivalence.
  - Requirements: Durable creation idempotency; Lifecycle idempotency scope and equivalence.
  - Verification: Tests prove exact-value differences change identity, allowed omitted/null/default forms compare equivalently, JSON order/user/process do not affect intent, and Party/nationality fingerprints remain unchanged.

- [ ] 4.4 Implement the version-one immutable scheme completion codec with explicit fields and validation of operation, tenant, target, effective input/digest, accepted result, and snapshot version.
  - Requirements: Durable creation idempotency; Durable successful lifecycle replay; Standard response and failure boundary.
  - Verification: Codec tests preserve historical configuration/version/full audit information and reject corrupted, cross-scope, or unsupported-version snapshots without exposing API DTOs or ORM objects.

- [ ] 4.5 Implement the reactive mutation boundary and internal scoped context using programmatic Hibernate Reactive/Panache session/transaction management and sequential callbacks.
  - Requirements: Global code uniqueness and creation atomicity; Mutation concurrency and audit outcome; Lifecycle atomicity and historical retention.
  - Verification: Vert.x-context tests prove lazy subscription, outcome after commit, rollback on failure/cancellation, resource cleanup, and no escaped context or nested independent session.

- [ ] 4.6 Implement transaction-scoped full-key serialization, global exact-code serialization, and global scheme-row locking with the fixed key-before-code/row order.
  - Requirements: Global catalog ownership; Global code uniqueness and creation atomicity; Lifecycle idempotency scope and equivalence; Durable successful lifecycle replay.
  - Verification: Reactive lock tests prove tenant/action key separation, cross-tenant code serialization, full-identity checks despite forced lock-hash collisions, and row lookup without a tenant ownership predicate.

- [ ] 4.7 Implement global ID/code read operations and preserve the existing registration lookup port through the same exact catalog semantics.
  - Requirements: Safe current representation and lookup; Global catalog ownership; Lifecycle effects on new identifier admission.
  - Verification: Reactive repository tests return current representations/absence in every state across tenant contexts, match exact case-sensitive codes, and preserve existing registration lookup behavior.

- [ ] 4.8 Implement parameterized filtered keyset slices, limit-plus-one retrieval, descending backward fetch/reversal, and sequential opposite-boundary probes.
  - Requirements: Listing and filter semantics; Cursor pagination and list metadata.
  - Verification: Database tests cover equal creation timestamps with UUID tie-breaking, exact combined filters, empty/boundary pages, forward/backward traversal without duplicates, and bounded result size.

- [ ] 4.9 Implement draft insertion and conditional scheme updates guarded by ID/expected version; set the validated next version once, clear stale managed state, and reload before returning results.
  - Requirements: Creation request and initial state; Mutation concurrency and audit outcome; Lifecycle atomicity and historical retention.
  - Verification: Reactive tests prove initial version zero, one advance including identical edits, no `@Version` double increment, typed zero-row conflicts, unchanged immutable fields, and accurate reloaded audit values.

- [ ] 4.10 Implement scoped completion lookup/recording in the dedicated table inside the same transaction as the accepted create or lifecycle mutation.
  - Requirements: Durable creation idempotency; Durable successful lifecycle replay; Global code uniqueness and creation atomicity; Lifecycle atomicity and historical retention.
  - Verification: Integration tests prove completed snapshots match accepted versions, equivalent lookup returns original data, unkeyed/PATCH paths create no result, and completion failure rolls back the scheme write.

- [ ] 4.11 Apply existing finite query/mutation timeout settings and translate only named scheme-code uniqueness, classified dependency outages/timeouts, and version-write failures into the appropriate neutral failures.
  - Requirements: Standard response and failure boundary; Global code uniqueness and creation atomicity; Mutation concurrency and audit outcome.
  - Verification: Tests recognize `uq_identifier_schemes_code`, preserve unrelated integrity/programming/cancellation failures, and show configured bounds terminate lock/query/transaction work with rollback and cleanup.

## 5. Cursor and API Boundary Components

- [ ] 5.1 Implement the scheme-specific authenticated cursor adapter using existing signing key material and a separate scheme-list authentication domain.
  - Requirements: Cursor pagination and list metadata; Path and query validation.
  - Verification: Unit tests cover direction/tuple round-trip, every tenant/filter/limit mismatch, tampering, malformed/oversized encoding, unknown/retained signing keys, and rejection of Party/nationality cursors.

- [ ] 5.2 Implement strict create request decoding/validation with exact text/enums/defaults and raw `BigInteger` bounds, rejecting duplicate/unknown properties and coercion.
  - Requirements: Creation request and initial state; Supported configuration and coherent length bounds; Standard response and failure boundary.
  - Verification: Decoder tests distinguish missing/null bodies, code-specific failures, blank/oversized text, wrong tokens/enums/country, null boolean, and arbitrary integral values without performing early semantic range checks.

- [ ] 5.3 Implement strict presence-aware PATCH decoding with the seven-property allowlist, explicit null only for description/bounds, and raw integral bounds preserved until business evaluation.
  - Requirements: Partial update presence and immutable fields; Supported configuration and coherent length bounds.
  - Verification: Tests reject empty/nonobject/missing/null bodies, duplicate/unknown/immutable fields, wrong types and nonnullable nulls; omitted and cleared fields remain distinguishable in the Application command.

- [ ] 5.4 Add scheme ID/code and list-query parsing helpers and reuse exact creation/lifecycle key and expected-version header validation.
  - Requirements: Path and query validation; Listing and filter semantics; Mutation concurrency and audit outcome; Durable creation idempotency; Lifecycle request validation and failure precedence.
  - Verification: Tests cover canonical UUIDs, exact code limits, unknown/repeated/blank queries, country/enums, decimal limit boundaries, and every documented key/If-Match cardinality/syntax/overflow code and ordering rule.

- [ ] 5.5 Add type-specific JSON binding failure translation and nonblocking lifecycle body-presence validation without another request-context filter or global mapper.
  - Requirements: Creation request and initial state; Partial update presence and immutable fields; Lifecycle request validation and failure precedence; Standard response and failure boundary.
  - Verification: Focused tests show only scheme DTO binding failures are translated, zero-byte lifecycle bodies pass, and any nonempty bytes including `{}`, whitespace, and `null` produce `400 bad-request` without blocking stream reads.

- [ ] 5.6 Add the approved scheme response DTO and mapper, including optional-field omission, legacy boolean metadata, list mapping, and count-only shared pagination metadata.
  - Requirements: Safe current representation and lookup; Cursor pagination and list metadata; Standard response and failure boundary.
  - Verification: Serialization/mapping tests expose only approved fields, omit absent optional values/cursors/totals, return accurate `numberOfElements`, and preserve existing complete-count Party/nationality null-cursor behavior.

- [ ] 5.7 Extend the API-owned response codes and existing failure translator for all declared scheme outcomes, including 201 successful creation, 412 preconditions, 422 configuration, and sanitized unexpected failures.
  - Requirements: Standard response and failure boundary; State-dependent maintenance; Identifier-scheme lifecycle transition matrix; Supported configuration and coherent length bounds.
  - Verification: Translator tests assert the exact status/code mapping for each scheme failure and preserve unknown/cancellation failures for the shared global boundary; no response-library type appears in Domain/Application.

## 6. REST Resources and Composition

- [ ] 6.1 Wire the five scheme use cases through the existing Infrastructure producer with plain constructors, reactive port adapters, trusted clock, and neutral observation; verify CDI composition.
  - Requirements: Complete published identifier-scheme contract; Standard response and failure boundary.
  - Verification: Quarkus composition tests resolve all required beans without HTTP/CDI annotations in inner layers and without ambiguous registration-facing lookup adapters.

- [ ] 6.2 Add test-first `POST /v1/identifier-schemes` resource delivery with strict input ordering, Application delegation, DTO mapping, creation/replay disposition, and CDI-managed `ResponseManager` 201 wrapping.
  - Requirements: Creation request and initial state; Durable creation idempotency; Global code uniqueness and creation atomicity; Standard response and failure boundary.
  - Verification: HTTP tests prove valid draft creation, exact 201 envelope/status agreement, code/key conflicts, malformed/body/key errors, semantic 422 failures, and historical creation replay without another write.

- [ ] 6.3 Add test-first GET by ID and GET by code resource methods with canonical selectors, current safe DTO mapping, and explicit 200 shared envelopes.
  - Requirements: Safe current representation and lookup; Path and query validation; Global catalog ownership.
  - Verification: HTTP tests cover both routes in every state, cross-tenant global visibility, exact code behavior, invalid IDs/codes, and consistent absent-target 404 without null success or unexpected 500.

- [ ] 6.4 Add test-first GET collection delivery with strict query parsing, listing use-case delegation, API list mapping, and shared `paginatedHttp` count-only metadata.
  - Requirements: Listing and filter semantics; Cursor pagination and list metadata; Path and query validation; Standard response and failure boundary.
  - Verification: HTTP tests cover filters, default/boundary limits, empty/all-state pages, forward/backward continuation, altered scope/tokens, and exact omission of unavailable metadata.

- [ ] 6.5 Add test-first PATCH delivery with strict presence-aware input, body/path/header ordering, Application delegation, applied disposition, DTO mapping, and explicit 200 envelope.
  - Requirements: Partial update presence and immutable fields; State-dependent maintenance; Mutation concurrency and audit outcome; Standard response and failure boundary.
  - Verification: HTTP tests cover allowed fields by state, omission/null, equal-value edits, locked/retired/immutable fields, stale/max versions, huge integral bounds, and precedence over later semantic errors.

- [ ] 6.6 Add test-first activate/deprecate/retire resource methods with no-body validation, optional key before ID/version parsing, shared lifecycle delegation, DTO mapping, and explicit 200 envelopes.
  - Requirements: Identifier-scheme lifecycle transition matrix; Draft activation and configuration eligibility; Active scheme deprecation; Direct retirement and terminal state; Lifecycle request validation and failure precedence; Durable successful lifecycle replay.
  - Verification: Parameterized HTTP tests cover all 12 state/action pairs, eligible and unsupported drafts, direct retirement, repeated unkeyed actions, body rejection, invalid headers/targets, historical replay, and exact response status/code/data.

- [ ] 6.7 Verify trusted-context validation, accepted Process-Id echo, completion MDC attribution, and cleanup across all scheme routes using the existing single context filter.
  - Requirements: Trusted context and process correlation.
  - Verification: HTTP/log tests cover missing/duplicated/invalid context, later-header failures, successes/errors/replays, concurrent requests, and `/q` exemption; no rejected value or prior request context leaks.

- [ ] 6.8 Verify framework/error-boundary behavior for scheme routes and close any resource-specific gaps through the published shared error contract.
  - Requirements: Standard response and failure boundary; Safe current representation and lookup.
  - Verification: HTTP tests assert exact status/code-only JSON for unsupported methods/media types, unknown routes, classified 503 failures, sanitized unexpected 500 failures, and 401 where the existing authentication profile enforces it; no overlapping global mapper is introduced.

## 7. Concurrency, Atomicity, and Regression Verification

- [ ] 7.1 Exercise concurrent equivalent and conflicting creation requests sharing one tenant/create/key, including effective optional defaults and changed payloads.
  - Requirements: Durable creation idempotency; Global code uniqueness and creation atomicity.
  - Verification: Coordinated bounded tests prove one creation/result for equivalent input, identical accepted responses, and no mutation/completion from a conflicting contender; no sleeps or duplicate logical writes.

- [ ] 7.2 Exercise concurrent equivalent lifecycle retries and conflicting same-key targets/versions, together with action and tenant namespace independence.
  - Requirements: Lifecycle idempotency scope and equivalence; Durable successful lifecycle replay; Global catalog ownership.
  - Verification: Tests prove equivalent contenders return the winner's result rather than stale errors, conflicting contenders receive key conflict without losing writes, and another tenant cannot obtain a saved historical result through key reuse.

- [ ] 7.3 Exercise independent same-code creations with different keys and tenant contexts, including an existing deprecated/retired scheme and uniqueness-before-configuration precedence.
  - Requirements: Global catalog ownership; Global code uniqueness and creation atomicity; Standard response and failure boundary.
  - Verification: One globally unique scheme exists, every losing independent attempt has the documented code conflict, and no successful replay record is created for a loser.

- [ ] 7.4 Exercise independent PATCH/lifecycle races at the same expected version and assert exact winning field/status/version/audit outcomes.
  - Requirements: Mutation concurrency and audit outcome; Lifecycle atomicity and historical retention.
  - Verification: At most one mutation advances the contested version, each independent loser receives 412, and the accepted row/snapshot reflects one complete outcome with no double version advance.

- [ ] 7.5 Inject failures during completion recording and before transaction acceptance to prove create and lifecycle atomicity and failed-key reuse.
  - Requirements: Global code uniqueness and creation atomicity; Durable creation idempotency; Lifecycle atomicity and historical retention; Durable successful lifecycle replay.
  - Verification: Failures leave no partial scheme/replay change, later valid requests can reuse failed keys, and accepted work is distinguished from a subsequently lost HTTP response.

- [ ] 7.6 Verify finite lock/query/operation timeouts and cancellation cleanup using coordinated reactive integration tests.
  - Requirements: Standard response and failure boundary; Mutation concurrency and audit outcome; Lifecycle atomicity and historical retention.
  - Verification: Genuine outages/timeouts reach the documented 503 boundary after cleanup, cancellation remains cancellation, pending work does not leave partial completion, and subsequent operations can obtain released resources.

- [ ] 7.7 Verify catalog lifecycle effects against existing Party/identifier workflows and absence of administrative Party outbox events using versioned test fixtures for historical evidence.
  - Requirements: Lifecycle effects on new identifier admission; Lifecycle atomicity and historical retention; Direct retirement and terminal state.
  - Verification: New initial/additional admission rejects nonactive schemes, activation admits otherwise eligible schemes, deprecated/retired historical verified evidence still qualifies under existing rules, expiration stays optional, and Party/identifier state/version/data plus tenant outbox counts remain unaffected by catalog changes.

## 8. OpenAPI and Consumer Documentation

- [ ] 8.1 Write failing contract-structure assertions, then complete the eight operations' schemas, explicit path/query/header semantics, closed create/PATCH objects, numeric/text constraints, required/optional output fields, pagination shape, and lifecycle/PATCH state descriptions in `docs/contracts/party-registry.openapi.yaml`.
  - Requirements: Complete published identifier-scheme contract; Path and query validation; Creation request and initial state; Partial update presence and immutable fields; State-dependent maintenance; Cursor pagination and list metadata.
  - Verification: Swagger parser/reference checks and operation/schema assertions pass; `schemeId`/`code` are required path parameters, all declared properties match runtime semantics, and unrelated resource definitions are not altered by scheme-specific edits.

- [ ] 8.2 Add test-first documented status/code coverage and representative create/PATCH/read/list/lifecycle/error examples; validate examples and observed HTTP JSON against their declared schemas.
  - Requirements: Complete published identifier-scheme contract; Standard response and failure boundary; Supported configuration and coherent length bounds; Lifecycle request validation and failure precedence.
  - Verification: Every resource-specific code and applicable framework status is explicitly documented through the standard schemas, successful HTTP/body statuses agree, and example/runtime schema validation covers omitted optional values and count-only pagination rather than relying on parser success alone.

- [ ] 8.3 Update English consumer/operations documentation for all eight routes, version/key behavior, global ownership, valid transitions, state-limited PATCH, cursor signing prerequisites, and additive migration/rollback behavior.
  - Requirements: Complete published identifier-scheme contract; Global catalog ownership; Mutation concurrency and audit outcome; Durable creation idempotency; Durable successful lifecycle replay.
  - Verification: README and relevant operations guidance distinguish Flyway-controlled schema/initial reference provisioning from API-managed catalog business writes and explain retained data/replay history on rollback without adding new routes or catalog algorithms.

## 9. Packaged, Native, and Completion Gates

- [ ] 9.1 Add `PackagedIdentifierSchemeContractIT` covering all eight routes, strict decoding, authenticated navigation, response shapes, and equality of served/packaged OpenAPI definitions in the existing JVM/native integration source set.
  - Requirements: Complete published identifier-scheme contract; Standard response and failure boundary; Cursor pagination and list metadata.
  - Verification: Packaged HTTP tests run independently against the real artifact and exercise actual serializers/resources; any necessary native metadata is explicit and minimal, with no blanket reflection registration.

- [ ] 9.2 Add real-process restart coverage with retained PostgreSQL data for lost creation and lifecycle responses, later retirement, fresh retry context, and historical result replay.
  - Requirements: Durable creation idempotency; Durable successful lifecycle replay; Trusted context and process correlation; Lifecycle atomicity and historical retention.
  - Verification: JVM/native-capable restart tests observe a changed process ID, identical original accepted data/audit/version after relaunch, current retired state on GET, fresh Process-Id echo, and unchanged scheme/replay/Party-event counts.

- [ ] 9.3 Resolve JDTLS/LSP diagnostics for every modified Java file, inspect compiler warnings/Javadoc, and review architecture and all eight resource signatures explicitly.
  - Requirements: Standard response and failure boundary; Complete published identifier-scheme contract.
  - Verification: Record clean modified-file diagnostics and no new compiler warnings/unjustified suppressions; inward-dependency, framework-isolation, cycle, delegation, and API DTO/shared-envelope checks pass, with any LSP/Gradle discrepancy investigated.

- [ ] 9.4 Run `./gradlew test` on the final implementation and resolve every change-related failure.
  - Requirements: All requirements in both identifier-scheme delta specifications.
  - Verification: The full JVM suite passes, including Domain/Application/API/persistence/architecture/contract and regression coverage; record the command and result after the latest implementation edits.

- [ ] 9.5 Run `./gradlew build` after the final JVM test gate and resolve build or packaged integration failures.
  - Requirements: Complete published identifier-scheme contract; Standard response and failure boundary; Durable creation idempotency; Durable successful lifecycle replay.
  - Verification: The JVM artifact and configured packaged integration checks complete successfully with documented environment overrides where required; no unexecuted check is reported as passed.

- [ ] 9.6 Run `./gradlew buildNative -Dquarkus.native.container-build=true` and resolve native compilation or resource/serialization metadata failures.
  - Requirements: Complete published identifier-scheme contract; Standard response and failure boundary.
  - Verification: A native executable is produced using the project's Java/Quarkus baseline and documented runtime configuration; required contract resources are packaged and only necessary reflection metadata is registered.

- [ ] 9.7 Run `./gradlew testNative -Dquarkus.native.container-build=true` against the native artifact and resolve native route/decoder/cursor/snapshot/restart failures.
  - Requirements: Complete published identifier-scheme contract; Cursor pagination and list metadata; Durable creation idempotency; Durable successful lifecycle replay; Standard response and failure boundary.
  - Verification: The maintained native integration suite passes all scheme operations and real restart replay checks; report a genuine environment blocker instead of marking this gate complete without execution.

- [ ] 9.8 Review requirement/scenario coverage, TDD evidence, migration immutability, and final public-contract consistency; run strict OpenSpec validation and record completion evidence before declaring implementation done.
  - Requirements: All requirements in both identifier-scheme delta specifications.
  - Verification: Every requirement has passing appropriate-boundary tests, every completed implementation task has its verification evidence, strict `openspec validate complete-identifier-schemes-api-contract --type change --strict --json --no-interactive` passes, and OpenAPI → REST → Application → Domain → persistence is consistent without partially implemented operations.
