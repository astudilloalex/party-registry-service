## Execution and Traceability

Requirement prefixes identify the approved specifications:

| Prefix | Specification |
|---|---|
| Q | `specs/party-queries/spec.md` |
| B | `specs/party-basic-update/spec.md` |
| L | `specs/party-deactivation-and-archival/spec.md` |
| A | `specs/party-activation/spec.md` |
| Quality | `proposal.md` Impact and `design.md` Architecture, Testing Strategy, and quality evidence |

Execute sections in order, respecting the specific dependencies stated below. Every Java-producing task includes concise English responsibility Javadoc, review of that file's JDTLS/LSP diagnostics, meaningful focused tests, and correction of new compiler/type/nullability warnings. Record standalone SonarQube for IDE analysis completion and results for every new or modified class, interface, record, and enum, including tests; a task is not quality-complete while findings from the active locally supported rules remain unresolved. Keep Sonar findings separate from Java diagnostics and preserve a changed-file inventory so the final checks cover the exact delivered revision.

Use injected fixed clocks and `java.time.Month` constants for every new or modified valid date/date-time fixture. Derive valid JSON date strings from those values; intentionally malformed date inputs may remain literal strings. Keep Domain framework-independent, Application limited to inner contracts and the approved Mutiny profile, and all persistence/transaction mechanisms in Infrastructure.

All checkboxes start unchecked. Mark a task complete only after its implementation and stated verification succeed. Record unavailable local Sonar analysis, JDTLS/LSP evidence, database, or native-tooling prerequisites as blockers on the affected verification rather than treating compilation or planning validation as equivalent evidence. When the agent cannot access IDE diagnostics directly, obtain developer-collected evidence for the same saved revision before completing that verification. This workflow requires no SonarQube server, scanner integration, credentials, or remote Quality Gate; their absence is not a blocker. Cursor production secrets remain external configuration.

## 1. Verification Foundation and Public Contract

- [ ] 1.1 Establish local Java static analysis with SonarQube for IDE in Antigravity IDE, running in standalone mode, with the Java 25 Gradle project correctly resolved by Red Hat Language Support for Java in standard mode.
  - Requirements: Quality — per-file local Sonar analysis and zero unresolved findings from active locally supported rules.
  - Verification: Confirm the extension is enabled in the active project profile/window and completes analysis on an actual project Java file. Record its version, effective local rules/parameters, analyzed saved revision/files, completion evidence, and baseline findings. Define how to collect evidence for every changed production and test Java file. An empty Problems panel alone is insufficient; analyzer compatibility and successful execution must be verified.

- [ ] 1.2 Establish working JDTLS/LSP diagnostics in Antigravity IDE and a changed-Java-file evidence inventory covering production, unit-test, and packaged integration-test sources, with a developer handoff when direct IDE diagnostic access is unavailable.
  - Requirements: Quality — zero new compiler/IDE warnings, English Javadoc, Month-based fixtures, and diagnostic reconciliation.
  - Verification: Diagnostics can be obtained for an actual project file; record the baseline and an evidence format linking each changed file and saved revision to JDTLS/LSP diagnostics, focused tests, and separate local Sonar analysis completion/results. Developer-collected IDE evidence is acceptable when direct access is unavailable; missing evidence remains incomplete. Document and resolve toolchain disagreements rather than ignoring them.

- [ ] 1.3 Characterize the resolved shared response dependency's pagination API, default null omission, and count range before implementing the list adapter.
  - Requirements: Q / Root Party response and error contract; Stable scoped Party pagination.
  - Verification: Focused tests confirm `ResponseManager.paginatedHttp`, current detail/error envelope shapes, and checked count conversion at the `Integer` boundary; record the resolved dependency identity and the design's capacity limitation.

- [ ] 1.4 Update `docs/contracts/party-registry.openapi.yaml` for root listing and retrieval semantics, including all filters, query cardinality, stable ordering, cursor scope, exact metadata, explicit null cursors, safe detail fields, and process echoes.
  - Requirements: Q / all requirements.
  - Verification: The OpenAPI parses successfully, the read operations reference the intended schemas, and contract assertions cover metadata requiredness, limits, empty results, headers, and root detail exclusions.

- [ ] 1.5 Update the root PATCH and lifecycle OpenAPI declarations for strict body validation, exact field/business codes, validation precedence, allowed states, expected versions, and historical keyed replay.
  - Requirements: B / all requirements; L / all requirements; A / all requirements.
  - Verification: The contract declares `display-name-required`, root applicability of the display-name codes, PATCH's `expected-version-mismatch`, lifecycle `stale-party-version` and `idempotency-key-conflict`, and the approved transition/replay examples without changing unrelated registration contracts.

## 2. Domain Root Behavior

- [ ] 2.1 Add explicit display-name correction behavior to `Party`, `NaturalPerson`, and `LegalEntity`, enforcing canonical new-write text and the normalized 300 UTF-16-unit limit while retaining restoration behavior.
  - Requirements: B / Canonical display-name correction; Display-name update observable outcome.
  - Verification: Domain tests cover blank input, uppercase expansion, supplementary Unicode, boundary lengths, all four statuses, exact next version/audit values, and preservation of historical details and creation metadata.

- [ ] 2.2 Implement Domain deactivation and archival transitions with explicit violations, retaining draft-only activation and guarding version overflow.
  - Requirements: L / Party deactivation transition; Party archival transition; Lifecycle retention and atomic outcomes; A / Party activation transition.
  - Verification: Parameterized tests for both Party types cover every operation/state combination, terminal archival, no reactivation, preserved identity/details, and exactly one version increment for each accepted transition.

- [ ] 2.3 Introduce minimal `PartyActivationEvidence` and adapt the activation policy and affected callers to its ownership, scheme identity, compatibility, verification, and UTC-expiration inputs.
  - Requirements: A / Activation requires a qualifying verified identifier; Activation has one observable outcome.
  - Verification: Policy tests cover same-day/null expiration, pending/rejected/revoked/expired evidence, unrelated ownership, incompatible/mismatched schemes, and non-gating primary/scheme status. Evidence projections contain no encrypted values, hashes, or keys; existing activation tests remain executable during the later workflow cutover.

## 3. Application Contracts

- [ ] 3.1 Define typed root read inputs/results, canonical name predicates, page boundaries/scopes, `PartyQueryPort`, `PartyCursorPort`, and transport-neutral invalid-cursor failure handling; update existing exhaustive failure switches and its API `bad-request` mapping in the same compiling change.
  - Requirements: Q / Tenant-scoped Party summaries; Party listing filter semantics; Stable scoped Party pagination; Common Party detail retrieval.
  - Verification: Unit tests establish filter equivalence, literal prefix/substring behavior, preserved accents/interior spaces, instant-boundary semantics, unsigned UUID ordering, and query/cursor contracts without HTTP, SQL, Jackson, or ORM dependencies.

- [ ] 3.2 Define root mutation commands, `PartyLifecycleAction`, `PartyMutationOutcome`, completed lifecycle request/result identity, `PartyMutationPort`, and the tenant-bound `PartyMutationContext` capabilities.
  - Requirements: B / Display-name update preconditions and precedence; Display-name update observable outcome; L / Lifecycle idempotency scope and request equivalence; Lifecycle retention and atomic outcomes.
  - Verification: Contract tests establish Party-ID/expected-version equivalence, exclusion of process/user from replay equivalence, and the applied/replayed result shape. ArchUnit confirms the ports expose only Application/Domain/JDK/Mutiny types.

## 4. Query Persistence and Cursor Protection

- [ ] 4.1 Add the next available immutable Flyway migration for the `(tenant_id, created_at DESC, id DESC)` listing index, currently planned as `V5__add_party_listing_order_index.sql`.
  - Requirements: Q / Stable scoped Party pagination; design / Flyway migration.
  - Verification: Migration regression tests pass for a fresh database and an upgrade from V4, confirming index shape and unchanged previous migration checksums. Use Flyway for all schema setup; recheck the version before creating the migration.

- [ ] 4.2 Add validated, versioned cursor-signing configuration with an external production key map/current key ID and deterministic test-only key material.
  - Requirements: Q / Stable scoped Party pagination; design / Security and Migration and Rollback.
  - Verification: Configuration tests accept a valid key ring and reject missing current keys or unusable material without exposing secrets; packaged test configuration can supply the same key ring across application launches.

- [ ] 4.3 Implement the Infrastructure `PartyCursorPort` adapter with a versioned exact timestamp/UUID boundary, direction, effective scope digest, page size, key ID, and constant-time HMAC verification.
  - Requirements: Q / Stable scoped Party pagination; Party listing filter semantics.
  - Verification: Tests cover round trips, tampering, blank/malformed tokens, unknown versions/keys, changed tenant/filter/limit, normalized filter and offset equivalence, and preservation of submillisecond boundary precision.

- [ ] 4.4 Implement the tenant-qualified root/detail query and safe subtype projection for common Party retrieval.
  - Requirements: Q / Common Party detail retrieval; Root Party response and error contract.
  - Verification: Reactive database tests return both types in all statuses, conceal absent/cross-tenant records, preserve historical text, and distinguish a missing root from corrupt detail structure without loading identifiers or calling Geographic Reference.

- [ ] 4.5 Implement the no-name-filter collection path with summary-only projections, explicit read-only repeatable-read isolation, parameterized count/keyset/neighbor queries, and exact page metadata.
  - Requirements: Q / Tenant-scoped Party summaries; Party listing filter semantics; Stable scoped Party pagination.
  - Verification: Database tests cover first/middle/last/empty pages, forward/backward navigation, inclusive date bounds, tenant-safe totals, stable timestamp ties, canonical unsigned UUID order, and current data after intervening changes.

- [ ] 4.6 Implement name-filter traversal in bounded sequential keyset batches within the same collection snapshot, applying the Application-owned canonical predicate and retaining bounded page/neighbor state.
  - Requirements: Q / Party listing filter semantics; Stable scoped Party pagination.
  - Verification: Multi-batch tests cover historical mixed case, Unicode expansion, accents, exterior/interior whitespace, literal `%`/`_`, combined filters, exact counts, and bounded accumulation without collecting the entire tenant.

- [ ] 4.7 Add collection snapshot-consistency tests and finite query/traversal timeout configuration with cancellation cleanup.
  - Requirements: Q / Stable scoped Party pagination; Root Party response and error contract; design / Resilience.
  - Verification: A coordinated concurrent write cannot produce contradictory count/page/neighbor metadata within one response. Timeout/cancellation tests release the read transaction and return no partial collection; record representative-volume duration and batch/memory observations for the name-filter path.

## 5. Transactional Mutation Infrastructure

- [ ] 5.1 Implement lifecycle snapshot schema 3 and a versioned effective-request fingerprint, reusing only the safe Party result codec support needed by both subtypes.
  - Requirements: L / Lifecycle idempotency scope and request equivalence; Lifecycle successful result replay; A / Activation has one observable outcome.
  - Verification: Codec tests preserve original data/audit/version values, validate action/identity/type/hash/schema consistency, reject corruption, exclude protected identifier material, and retain unchanged version-one/version-two registration decoding.

- [ ] 5.2 Implement transaction-scoped tenant/action/key advisory locking and full-scope completed-result lookup/persistence helpers using the existing idempotency table and distinct lifecycle operation names.
  - Requirements: L / Lifecycle idempotency scope and request equivalence; Lifecycle successful result replay.
  - Verification: Reactive database tests prove equal keys serialize across separate sessions, different full scopes remain logically independent, hash-lock collisions cannot merge scopes, and the existing primary key remains the uniqueness guard.

- [ ] 5.3 Implement tenant-qualified root locking, minimal activation-evidence reads, conditional root-only persistence, and safe reload helpers for the mutation context.
  - Requirements: B / Display-name update observable outcome; L / Lifecycle retention and atomic outcomes; A / Activation requires a qualifying verified identifier; Activation optimistic concurrency.
  - Verification: Database tests confirm exact candidate-version storage, no additional dirty-checking increment, untouched detail/identifier rows, tenant-qualified predicates and conflict diagnostics, microsecond audit precision, and sanitized handling of inconsistent state.

- [ ] 5.4 Extend allowlisted outbox candidates and codecs for `party.updated.v1`, `party.deactivated.v1`, and `party.archived.v1`, retaining the activation event and current delivery modes.
  - Requirements: B / Display-name update observable outcome; L / Lifecycle retention and atomic outcomes; A / Activation has one observable outcome.
  - Verification: Round-trip mapping and publisher tests accept the new safe type/status payloads, reject unexpected fields, preserve existing events, and confirm the configured disabled/stored-only/published behavior without introducing broker waits into request transactions.

- [ ] 5.5 Compose the scoped `PartyMutationPort` adapter from these helpers, using transaction-local read-committed isolation, sequential session operations, finite waits, and success emission only after commit.
  - Requirements: B / Display-name update observable outcome; L / Lifecycle tenant isolation and failure precedence; Lifecycle retention and atomic outcomes; A / Activation has one observable outcome.
  - Verification: Adapter tests verify active-scope lifetime, key-before-root lock ordering, visibility of a winner's completed result after waiting, commit/rollback boundaries, and preservation of known inner failures/cancellation. No technical failure recovery continues on a failed session.

## 6. Application Workflows and Activation Cutover

- [ ] 6.1 Implement `GetPartyUseCase` and `ListPartiesUseCase`, including absence mapping, cursor validation before data access, exact metadata assembly, and safe result projection.
  - Requirements: Q / Tenant-scoped Party summaries; Stable scoped Party pagination; Common Party detail retrieval.
  - Verification: Port-double tests prove invalid cursors stop data access, effective scopes reach the correct tenant query, missing Parties become `PartyNotFound`, and direction boundaries produce the expected cursors without extra identifier/geographic calls.

- [ ] 6.2 Implement `PatchPartyUseCase` within the scoped mutation port, ordering absence, expected version, and Domain label validation before the guarded write and enabled event.
  - Requirements: B / Canonical display-name correction; Display-name update preconditions and precedence; Display-name update observable outcome.
  - Verification: Unit tests cover stale-before-blank precedence, all-state correction, identical canonical writes, one post-lock clock instant, original creation metadata, preserved failures, and no lifecycle replay behavior for PATCH.

- [ ] 6.3 Implement `ChangePartyLifecycleUseCase` with serialized replay/conflict resolution followed by absence/version/Domain transition/evidence decisions, safe result persistence, and enabled event intent.
  - Requirements: L / all requirements; A / all requirements.
  - Verification: Port-double tests exercise all actions, early historical replay, exact effective-input comparison, failed-key reuse, current request correlation versus original audit, draft-only evidence evaluation, and no mutation/event when validation or replay resolution terminates the workflow.

- [ ] 6.4 Wire the root use cases through `ApplicationUseCaseProducer`, switch the existing activation route to the new workflow, and retire only proven-unused activation-only orchestration and Infrastructure decision code.
  - Requirements: A / Activation follows the shared lifecycle request and replay contract; Quality — strict Clean Architecture.
  - Verification: CDI startup and existing activation contracts pass through the new implementation; reference inspection confirms obsolete types are unused before removal, and ArchUnit plus code review confirm Application owns precedence/replay decisions and Domain owns eligibility.

## 7. HTTP Adapters and Explicit Response Contract

- [ ] 7.1 Implement `PartyQueryParameters` parsing over the raw multivalue query map, rejecting unknown/duplicate parameters, invalid enums/offset date-times, inverted intervals, oversized decoded filters, and invalid limits.
  - Requirements: Q / Party listing input validation; Party listing filter semantics.
  - Verification: Parser tests distinguish absent defaults from invalid values, enforce the 1–200 limit with default 50, count filter code points correctly, and preserve canonical effective criteria without broadening malformed queries.

- [ ] 7.2 Complete the API-owned response-code catalog and typed failure translator for display-name validation and shared lifecycle outcomes, retaining the invalid-cursor mapping from task 3.1 and the published global error handler.
  - Requirements: Q / Root Party response and error contract; B / Display-name update preconditions and precedence; L / Lifecycle tenant isolation and failure precedence; A / Activation optimistic concurrency.
  - Verification: Focused tests assert each declared code/status, PATCH versus lifecycle 412 distinction, exact two-property error bodies, and identity preservation of unexpected failures/cancellation for existing global handling.

- [ ] 7.3 Implement `PartyUpdateRequest`, strict model-local JSON binding, normalized-copy structural validation, and scoped JSON error translation using the response codes established in task 7.2.
  - Requirements: B / Root Party PATCH input contract; Canonical display-name correction; Display-name update preconditions and precedence.
  - Verification: Tests distinguish missing/null body, missing/null property, blank strings, unknown/duplicate properties, incompatible tokens, and normalized length. Binding does not coerce scalars or preempt the required stale-before-blank business check.

- [ ] 7.4 Add `PartySummaryResponse` and summary/page API mapping, reusing the safe root detail mapper and checked conversion into shared `PaginationMetadata`.
  - Requirements: Q / Tenant-scoped Party summaries; Common Party detail retrieval; Root Party response and error contract; Stable scoped Party pagination.
  - Verification: Mapping tests assert exact allowed fields for both types, no Domain/persistence object serialization, correct empty/count metadata, and explicit overflow handling instead of count truncation or silently null totals.

- [ ] 7.5 Implement the pagination-only Jackson property-writer customization for null `nextCursor` and `prevCursor`, preserving the shared library's ordinary null-omission behavior.
  - Requirements: Q / Root Party response and error contract; Stable scoped Party pagination.
  - Verification: Serialization tests prove all five metadata keys exist on empty and terminal pages, cursor nulls survive the library mixin, and ordinary successes/errors acquire no extra pagination fields. No generic response-wrapping filter or replacement envelope is introduced.

- [ ] 7.6 Expose root list and detail GET methods at `/v1/parties` and `/v1/parties/{partyId}`, delegating to Application and mapping DTOs before `ResponseManager.paginatedHttp` or `successHttp`.
  - Requirements: Q / all requirements.
  - Verification: HTTP smoke tests return typed shared envelopes and correct tenant-scoped representations; inspect both asynchronous signatures and confirm existing nested identifier routes still dispatch correctly.

- [ ] 7.7 Expose root PATCH, deactivation, and archival and align activation's shared input handling, using exact context/body/path/header precedence and `Uni<RestResponse<ApiResponse<PartyDetailResponse>>>` results.
  - Requirements: B / all requirements; L / Lifecycle request validation; Party deactivation transition; Party archival transition; A / Activation follows the shared lifecycle request and replay contract.
  - Verification: Each method delegates business behavior to the corresponding application workflow, maps safe DTOs before `successHttp`, and returns the intended 200 result and declared errors through the shared boundary.

## 8. Observability and Context Verification

- [ ] 8.1 Extend bounded HTTP/application operation labels, spans, and observations for all six root operations, including applied/replayed/conflict outcomes, key waits, and collection scan metrics.
  - Requirements: Q / Root Party request context; L / Lifecycle successful result replay; design / Observability and Resilience.
  - Verification: Tests assert operation recognition and bounded metric dimensions without raw IDs, names, keys, cursors, or filter values; the configured logging format remains byte-for-byte equal to the project requirement.

- [ ] 8.2 Extend context-filter contract coverage across all six root routes, including concurrent requests, partially initialized failures, replay correlation, and management exclusions.
  - Requirements: Q / Root Party request context; L / Lifecycle successful result replay.
  - Verification: Accepted process identifiers are echoed unchanged, invalid/duplicate/missing ones are omitted, completion logs contain only their own accepted context, and replay retains original audit data while using current request correlation.

## 9. Behavioral, Concurrency, and Failure Contract Tests

The following tasks depend on the corresponding complete workflows and HTTP adapters in sections 4–8. Use deterministic synchronization rather than sleeps or timing-only assertions for races.

- [ ] 9.1 Add root read HTTP contracts covering all list parameters, exact metadata/null directions, both detail subtypes, archived/historical records, tenant concealment, and cursor tampering/scope.
  - Requirements: Q / all requirements.
  - Verification: Every read scenario in the delta has an executable assertion, including multi-page traversal and exact response-key sets; no identifier, nationality, or creation-only collection leaks into root responses.

- [ ] 9.2 Add root PATCH HTTP contracts for strict binding, required/null/blank/Unicode boundary cases, header/body precedence, stale versions, all-state correction, audit values, and identical writes.
  - Requirements: B / all requirements.
  - Verification: Tests assert the exact code/status and resulting or unchanged record for both types, including archived label correction and preserved original creation replay.

- [ ] 9.3 Add lifecycle HTTP contracts for the complete transition matrix, optional key/header validation, tenant concealment, current-version failures, and the full activation-evidence eligibility matrix.
  - Requirements: L / Party deactivation transition; Party archival transition; Lifecycle request validation; Lifecycle tenant isolation and failure precedence; A / Activation requires a qualifying verified identifier; Party activation transition; Activation is tenant-scoped.
  - Verification: Both Party types produce the declared 200/400/404/409/412/422 outcomes, including direct archival from every prior state, no reactivation, and lifecycle-before-evidence failure precedence.

- [ ] 9.4 Add separate-session/database concurrency tests for equivalent keyed requests, conflicting same-key requests targeting different Parties, and distinct/unkeyed requests using one current version.
  - Requirements: L / Lifecycle idempotency scope and request equivalence; Lifecycle successful result replay; Lifecycle tenant isolation and failure precedence; A / Activation optimistic concurrency.
  - Verification: Equivalent retries return one historical success without a stale-version loser; conflicting keys return 409 with no losing mutation/event; distinct eligible contenders have one version winner and the expected 412 loser.

- [ ] 9.5 Add cross-operation race tests between root PATCH/lifecycle actions and existing natural-person/legal-entity detail updates.
  - Requirements: B / Display-name update observable outcome; L / Lifecycle retention and atomic outcomes; A / Activation optimistic concurrency.
  - Verification: Competing operations cannot overwrite a winning root version or restore stale lifecycle/details; PATCH losers use `expected-version-mismatch`, lifecycle losers use `stale-party-version`, and independent identifier versions stay unchanged.

- [ ] 9.6 Inject controlled test-only failures after root, snapshot, and enabled event writes but before acceptance; verify rollback, reusable failed keys, lock timeout, and cancellation cleanup.
  - Requirements: B / Display-name update observable outcome; L / Lifecycle retention and atomic outcomes; Lifecycle successful result replay; A / Activation has one observable outcome.
  - Verification: Every failed attempt leaves prior state/audit/version intact with no completed result or logical event, and a subsequent request can acquire the key/root. Tests introduce no production failure endpoint or manual DDL.

- [ ] 9.7 Verify durable replay across a lost response and an actual application restart/relaunch against retained test data, including replay after later archival and with a new process/user.
  - Requirements: L / Lifecycle successful result replay; Lifecycle idempotency scope and request equivalence; A / Activation follows the shared lifecycle request and replay contract.
  - Verification: The original successful Party data/version/audit survives a new process, current state remains unchanged, correlation belongs to the retry, and no additional logical event appears. Another session alone is not accepted as restart evidence.

- [ ] 9.8 Extend framework-error and confidentiality contract tests for all affected root paths, covering malformed input, 404/405/415, unexpected 500, and safe operational output.
  - Requirements: Q / Root Party response and error contract; Root Party request context; A / Activation has one observable outcome.
  - Verification: Actual HTTP/body statuses agree; errors contain only `status` and `code`; accepted process echoes follow the contract; no internal exception, SQL detail, rejected value, or identifier-protection material leaks.

## 10. Packaging, Architecture, and Consumer Documentation

- [ ] 10.1 Extend packaged integration coverage for root reads/pagination, PATCH, all lifecycle actions, both snapshot subtypes, and strict/null-aware serialization, and make new DTO/deserializer/codec paths reachable in native images.
  - Requirements: Q / Root Party response and error contract; B / Root Party PATCH input contract; L / Lifecycle successful result replay; Quality — native compatibility.
  - Verification: The packaged suite exercises the same public contract with explicit DTO reachability and no broad Domain/ORM reflection registration; it is wired to both JVM and native execution.

- [ ] 10.2 Complete `OpenApiContractTest` assertions against source and packaged `/q/openapi`, and reconcile root success/error examples with the implemented behavior.
  - Requirements: Q / Root Party response and error contract; B / Root Party PATCH input contract; L / Lifecycle request validation; A / Activation follows the shared lifecycle request and replay contract.
  - Verification: Packaged YAML exposes all six operations with their precise headers, validation semantics, response shapes, lifecycle replay behavior, and codes; annotation changes alone cannot satisfy the task.

- [ ] 10.3 Extend and run architecture checks for the affected slice and inspect actual resource signatures and workflow ownership.
  - Requirements: Quality — strict Clean Architecture and typed reactive response boundary.
  - Verification: ArchUnit preserves inward dependencies, Domain independence, Application's approved Mutiny-only framework allowance, and cycle freedom. Review confirms business precedence/replay decisions live in Application, invariants in Domain, technical persistence in Infrastructure, and all six resource signatures wrap API DTOs explicitly.

- [ ] 10.4 Update README and operational documentation with implemented routes, cursor key provisioning/rotation, standalone SonarQube for IDE verification steps in Antigravity IDE, timeouts, listing capacity/performance evidence, migration behavior, compatible rollout, and rollback retention rules.
  - Requirements: Q / Stable scoped Party pagination; L / Lifecycle successful result replay; Lifecycle retention and atomic outcomes; design / Migration and Rollback; Quality.
  - Verification: Documentation agrees with tested behavior, describes the current shared count limit and bounded-name-scan trade-off, explains local analysis triggers, rule/version recording, per-file evidence and the IDE handoff, and explains mixed-version lifecycle/publisher compatibility without committing secrets or requiring destructive data rollback.

## 11. Final Acceptance Evidence

These tasks verify the final delivered revision after preceding implementation and focused checks. New fixes require the affected checks to run again; unavailable checks remain incomplete.

- [ ] 11.1 Audit every new/modified Java file for final JDTLS/LSP diagnostics, English responsibility Javadoc, public contracts, null/type safety, Month-based valid date fixtures, and unjustified warning suppressions.
  - Requirements: Quality — per-file diagnostics, zero new warnings, Javadoc, and `java.time.Month`.
  - Verification: The changed-file inventory contains resolved diagnostics for every file, no numeric month arguments in new/modified valid fixtures, and documented reconciliation of any Gradle/LSP discrepancy.

- [ ] 11.2 Run `./gradlew test` and resolve failures and new compiler warnings across the root feature and affected regression suites.
  - Requirements: All four capability specifications; Quality — required JVM verification.
  - Verification: The command succeeds and reports include Domain/Application/API/persistence, concurrency, migration, architecture, and existing registration/type-specific regression coverage; no relevant failing or skipped verification is reported as complete.

- [ ] 11.3 Run `./gradlew build` and resolve packaged integration, packaging, or compiler failures.
  - Requirements: Q / Root Party response and error contract; Quality — required build and packaged JVM checks.
  - Verification: The command succeeds with the existing `check` to `quarkusIntTest` wiring, and packaged HTTP/OpenAPI assertions pass on the final code.

- [ ] 11.4 Run `./gradlew buildNative -Dquarkus.native.container-build=true` and `./gradlew testNative -Dquarkus.native.container-build=true`, recording any required environment-specific runtime overrides.
  - Requirements: Quality — native compilation and maintained native integration coverage; all root HTTP contracts.
  - Verification: Both commands succeed, native contract assertions cover pagination writers, strict binding, lifecycle snapshots/replay, and both Party subtypes, and no reflection/serialization incompatibility remains.

- [ ] 11.5 Run standalone SonarQube for IDE analysis in Antigravity IDE on every new/modified production and test Java file in the final saved revision and resolve all findings from the active locally supported rules.
  - Requirements: Quality — zero unresolved local Sonar findings in analyzed changed files and zero new compiler/IDE warnings.
  - Verification: Record the revision/file inventory, extension version, effective local rules/parameters, and per-file analysis completion and zero-unresolved-finding evidence. Reanalyze affected files after fixes. Developer-collected IDE evidence may supply results the agent cannot access directly. An empty Problems panel or a successful build alone does not satisfy this task; do not suppress or exclude code or disable rules to hide findings. This result is local verification, not a remote Quality Gate or equivalent server analysis coverage.

- [ ] 11.6 Reconcile all EARS scenarios with passing automated checks and assemble the final implementation evidence, including local static analysis, Java diagnostics, and operational prerequisites.
  - Requirements: Q, B, L, and A / all requirements; Quality.
  - Verification: Every requirement/scenario maps to the appropriate passing unit/integration/HTTP/native evidence; all six signatures and envelopes are inspected; no missing local Sonar analysis, JDTLS/LSP, or native evidence is marked complete; migration, cursor configuration, and rollout/rollback documentation are ready for review. Absence of a remote SonarQube service does not prevent acceptance of verified standalone analysis.
