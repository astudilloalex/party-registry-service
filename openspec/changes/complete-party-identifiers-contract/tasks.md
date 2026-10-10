## Execution Rules and Requirement References

Execute sections and tasks in numeric order. A task's prerequisites include preceding sections unless a narrower dependency is stated. Complete the quality preflight in section 1 before creating or modifying any Java production or test file. All checkboxes start unchecked and represent implementation work, not artifact-authoring progress.

Requirement prefixes refer to exact titles in:

- **Q:** [party-identifier-queries](specs/party-identifier-queries/spec.md).
- **M:** [party-identifier-maintenance](specs/party-identifier-maintenance/spec.md).
- **L:** [party-identifier-lifecycle](specs/party-identifier-lifecycle/spec.md).
- **R:** [party-registration issuer refinement](specs/party-registration/spec.md).
- **Quality:** proposal constraints and the testing/quality sections of [design.md](design.md): TDD, strict inward dependencies, reactive/native compatibility, Month-based fixtures, and actual Sonar/JDTLS evidence.

For every behavior-producing task, write the meaningful test first, record its expected failure, implement the smallest conforming behavior, and refactor with that test passing. An initial compile-red step for a new type is allowed but must lead to executable behavioral evidence. Environmental failures do not count as a TDD red result. Tests that characterize existing behavior may already pass; the evidence must distinguish characterization from a new failing requirement.

Every Java-producing task includes English responsibility Javadoc for classes, interfaces, records, enums, and nested types; documented non-obvious public contracts; per-file JDTLS diagnostics; and completed standalone Sonar analysis of the saved revision. Require zero new compiler/IDE warnings and zero unresolved findings from active applicable local rules, without blanket rule exclusions, NOSONAR, or unjustified suppressions. After Java changes run `./gradlew test`; before completing an implementation task run `./gradlew build`, resolving failures and Gradle/LSP disagreements. Final checks in section 14 verify the complete delivered revision.

Use `java.time.Month` constants for every new/modified valid date fixture, including fixed-clock timestamps derived from those dates. Raw malformed date strings are reserved for tests of malformed wire tokens. Use bounded reactive assertions and coordinated concurrency, not sleeps or production synchronous waits.

Developer-directed iteration scope: from task 9.2 onward, execute only new/modified test classes with explicit --tests selection, retain valid saved-revision results, and do not rerun unrelated full/packaged suites per increment. Focused builds retain that selection and exclude unchanged packaged test execution. The full JVM/native/architecture acceptance explicitly listed in section 14 remains a final milestone, not an iteration check. Record focused versus full evidence accurately; this changes verification scheduling, not required behavior or final acceptance.

Updated developer-directed quality scheduling during task 11.3: unavailable Sonar analysis or nonblocking Sonar warnings must not pause implementation; retain their pending evidence for final task 14.5. Complete behavior and focused runtime checks before checking off an increment, resolve compilation/runtime failures, inspect available JDTLS diagnostics, and continue without treating missing Sonar evidence as passing. This explicit scheduling override supersedes the earlier per-increment Sonar stop rule only; final quality acceptance remains required.

Keep an implementation evidence file, scenario map, and saved-file inventory under this change when implementation begins. Do not mark a task complete while its required tests, current IDE evidence, or stated native verification are unavailable. Use developer-collected diagnostics for the same revision when direct IDE access is unavailable. Planning validation and compilation are not substitutes for runtime or local-analysis evidence.

## 1. Quality and Environment Preflight

- [x] 1.1 Confirm SonarQube for IDE standalone and the resolved Java 25 project in the active Antigravity window before Java edits.
  - Requirements: Quality — verified local analyzer baseline.
  - Verification: Record current extension/analyzer version, runtime/source level, Standard-mode Red Hat Java, resolved Gradle classpath, and one completed analysis of an existing project Java file. Reconfirm rather than reuse the historical 5.7.0/571-rule result.
  - Evidence: `verification.md` records fresh Sonar 5.7.0 analysis with 571 active Java rules, Java 25, 0 issues, and separate JDTLS Standard-mode validation with 0 problems for the saved baseline resource.

- [x] 1.2 Research and record effective installed Java rules and parameters before implementing production code or tests.
  - Requirements: Quality — preventive Sonar rule review.
  - Verification: Record active defaults and overrides relevant to complexity, parameter count, nullability, ignored results, exception handling, logging, cryptography, cleanup, and assertions, including applicable S3776/S107/S2259/S2201/S5778/S6213 descriptions and thresholds. Keep standalone coverage distinct from a server profile.
  - Evidence: `verification.md` records installed 550+21 default-profile membership, bundled rule descriptions, S3776 Threshold=15 and S107 max/constructorMax=7 bytecode annotations, analyzer archive identities, and unchanged local settings.

- [x] 1.3 Establish the reproducible baseline and implementation prerequisites.
  - Requirements: Quality; Q: Safe shared envelopes and failure behavior; M: Maintenance has one durable observable outcome.
  - Verification: Using existing tests/tooling, record baseline test/build results, Java/Quarkus versions, resolved shared-response artifact behavior, PostgreSQL/Podman access, native-toolchain availability, and existing migration checksums. Confirm V8 is still unused and characterize shared pagination null handling. Record actual failures as prerequisites to resolve, not feature failures.
  - Evidence: `verification.md` records successful full test/build runs, 1,364 unit/reactive/HTTP/architecture tests and 28 packaged JVM tests without failures/skips, shared pagination characterization, artifact identities, executable Mandrel availability, unchanged V1–V7 checksums, and baseline runtime warnings.

- [x] 1.4 Create the implementation evidence convention, saved-Java inventory, and scenario-to-test map.
  - Requirements: All 27 Q/M/L requirements and 87 scenarios; Quality.
  - Verification: Each scenario has its source title, planned test boundary, task reference, and pending evidence entry; the inventory covers main/test/contractTest/integrationTest and records saved blob, Sonar completion/results, JDTLS results, and focused red/green commands. No planned test is marked passed.
  - Evidence: `scenario-evidence.md` covers 27 requirements and 87 scenarios with pending evidence; `java-inventory.md` defines all four source sets, nested types, saved blobs, separate IDE analyses, and test evidence. Source/map totals both contain 114 requirement/scenario entries; `git diff --check` passes.

## 2. Contract Completion and Test Foundation

- [x] 2.1 Test first and complete the affected OpenAPI paths, headers, strict request models, pagination constraints, and action semantics.
  - Requirements: Q: Complete published party-identifiers contract; Q: Strict read selectors and filters; M: Strict additional registration input; M: Strict partial metadata correction; M: Strict primary-selection input and eligibility; L: Lifecycle requests follow the common boundary.
  - Verification: Source structure tests pin all eight operations, identifier-specific If-Match, creation/action key semantics, 256-character cursor bound, closed properties, body presence/nullability, transition matrix, primary scope, validation order, and 0..2147483647 count range. Parsing reports no unresolved references; unrelated parameter contracts are preserved.
  - Evidence: `verification.md` records the six-test red, completed contract, nine passing structural checks including the full matrix/precedence, preserved general/scheme contracts, 1,373 passing tests and 28 packaged JVM tests, plus current saved-revision Sonar/JDTLS zero findings. Cause-specific examples remain task 2.2; runtime implementation remains in subsequent tasks.
  - Reverified: Issuing-entity schema/semantics and preserved historical response constraints pass 10 structural checks, final full test/build and current zero-finding Sonar/JDTLS; see the issuing-entity continuation in `verification.md`.

- [x] 2.2 Test first and complete OpenAPI success/error examples and the approved cause-specific error declarations.
  - Requirements: Q: Complete published party-identifiers contract; Q: Safe shared envelopes and failure behavior; M: Identifier-version preconditions and exhaustion.
  - Verification: Example/schema tests cover 201/200 status agreement, safe item/page shapes, explicit null cursors, accepted process echoes, 404 identifier-not-found, 409 lifecycle/primary/exhaustion conflicts, 412 expected-version-mismatch, existing 422 errors, and framework/500/503 envelopes. Error examples contain status/code only.
  - Evidence: `verification.md` records six executable example/schema failures before implementation, 49 focused passing checks, 1,379 full tests and 28 packaged JVM tests passing, and zero current Sonar/JDTLS findings for both affected Java files. All eight success examples and cause-specific error examples validate against their declared schemas.
  - Reverified: New example red identified missing initial oversized-issuer coverage; both issuer codes and accepted/rejected schemas now pass 8 example/schema cases, final full test/build and current zero-finding Sonar/JDTLS. See `verification.md`.

- [x] 2.3 Add only the reusable Month-based identifier fixtures and safe HTTP/schema assertions needed by subsequent increments.
  - Requirements: Quality; Q: Safe shared envelopes and failure behavior; Q: Trusted context and process correlation.
  - Verification: Fixture/helper tests cover controlled UTC dates, both Party types, masked representations, complete pagination keys, and exact status/code-only errors. Helpers use supported source-set boundaries, disclose no real secrets, and introduce no test CDI fixtures into packaged verification.
  - Evidence: `verification.md` records four focused helper/fixture checks, 1,383 full tests and 28 packaged JVM tests passing, both-source-set shared assertion compilation, and actual zero-finding Sonar/JDTLS analyses for all four saved Java files.

- [x] 2.4 Implement the confirmed optional issuing-entity refinement with historical compatibility.
  - Requirements: R: Initial identifier issuer metadata is canonical issuing-entity code; M: Strict additional registration input; M: Strict partial metadata correction.
  - Verification: Test first initial/additional DTO validation, null/omission/bounded blank mapping, ASCII letter groups with single underscores, locale-independent uppercase, 64-code-point limits, no trimming of nonblank codes and no Geographic Reference lookup. Domain creation/correction enforce the same new-write rules, while restoration, omitted historical metadata and snapshots remain unchanged. Original parsed strings remain fingerprint inputs. Reconcile fixture expectations, saved-revision IDE diagnostics and full test/build gates without changing V1–V7 or adding issuer binary storage.
  - Evidence: `verification.md` records normalization/DTO/Domain red-green, supplementary length-precedence red-green, 4 real HTTP cases for both Party types/no issuer-country lookup/raw retry identity, retained historical restoration and compatibility checks, 1,434 full tests and 28 packaged JVM tests, and all saved-revision zero Sonar/JDTLS results in `java-inventory.md`.
  - Compatibility clarified: The user confirms a new database with only two existing records, both with null issuer. The hypothetical old-invalid-issuer replay is not a deployment requirement. Keep current request validation and preserved null/snapshot/fingerprint behavior; introduce no grandfathering or migration exception. Existing final gates remain valid; see `verification.md`.

## 3. Domain Rules

- [x] 3.1 Test first and implement checked independent identifier-version advancement and immutable audit preservation.
  - Requirements: M: Identifier-version preconditions and exhaustion; M: Metadata correction eligibility and audit; L: Operator-confirmed verification and validity.
  - Verification: Pure unit tests prove one increment, maximum-minus-one success, maximum exhaustion, unchanged creation audit/protected value/identity, and explicit actor/time contracts. Domain remains free of Mutiny, Quarkus, HTTP, persistence, and envelope dependencies.
  - Evidence: `verification.md` records compile-red then executable overflow-classification red, checked `PartyIdentifierVersion.next()` green, immutable audit/protected-history characterization, passing architecture checks, 1,387 full tests and 28 packaged JVM tests, and current zero-finding Sonar/JDTLS analyses for all three changed Java files.

- [x] 3.2 Test first and implement partial metadata correction on pending and verified identifiers.
  - Requirements: M: Strict partial metadata correction; M: Metadata correction eligibility and audit.
  - Verification: Unit tests cover omission versus null, retained-field interval validation, inclusive UTC expiration, repair of past-date pending/verified metadata, issuer-only rejection of unrepaired expiration, identical corrections, terminal-state rejection, and preserved verification/primary/value information.
  - Evidence: `verification.md` records compile-red and 5/11 executable state/date-precedence failures, immutable `correctMetadata` green, 1,398 full tests and 28 packaged JVM tests passing, inward-dependency checks, and zero current Sonar/JDTLS findings for the aggregate/builder, neutral violation enum and new metadata test.
  - Reverified: Supplied issuer corrections canonicalize uppercase/bounded blank-null; omitted legacy issuer remains unchanged. Revised metadata and new issuer-write tests pass with 1,434 full tests, 28 packaged JVM tests and current zero Sonar/JDTLS findings; see `verification.md`.

- [x] 3.3 Test first and implement the closed verification/rejection/revocation matrix and verification evidence rules.
  - Requirements: L: Explicit identifier transition matrix; L: Operator-confirmed verification and validity; L: Rejection and revocation retain safe history.
  - Verification: Unit tests cover every state/action matrix entry, exact verification actor/instant, date boundaries, withdrawal of past-date evidence, flag clearing on reject/revoke, retained prior verification, and invalid repeats without replay. No implicit expiration, reactivation, substitute primary, or Party change occurs.
  - Evidence: Domain methods and all 20 lifecycle cases pass after 15 executable red failures, with current saved aggregate/test Sonar/JDTLS zero findings. Issuer clarification resolves the representation decision; the final continuation run passes 1,434 tests and 28 packaged JVM tests. Earlier legal setup failures and passing reruns remain recorded separately in `verification.md`; no timeout or assertion was weakened.

- [x] 3.4 Test first and implement primary eligibility, provisional-verification conflict rules, and the two-identifier replacement plan.
  - Requirements: M: Strict primary-selection input and eligibility; M: Scoped atomic primary ownership; L: Verification respects provisional primary ownership.
  - Verification: Pure policy tests cover per-tenant/Party/scheme scope, unrelated/provisional flags, conflicting verified primaries including past-date ones, nonprimary verification, explicit replacement, already-primary selection, retained fields, common actor/time, and target/former exhaustion. Both candidates are validated before any persistence.
  - Evidence: `verification.md` records compile-red then 7 executable policy failures and one constructor-invariant failure, all eight cases green, metadata/lifecycle/architecture regression, 1,442 full tests and 28 packaged JVM tests, and current zero-finding Sonar/JDTLS for all five Java files in `java-inventory.md`.

## 4. Application Contracts and Neutral Failures

- [x] 4.1 Test first and define typed identifier commands, queries, criteria/boundaries/pages, effective requests, safe mutation outcomes, and bounded observation operations.
  - Requirements: M: Durable additional registration replay; L: Durable action key scope and request equivalence; Q: Deterministic scoped cursor navigation; Quality.
  - Verification: Contract tests pin identifier-specific version types, exact raw creation values, omission/null/default equivalence, exact reasons, tri-state primary filters, safe results, and APPLIED/REPLAYED disposition. No command representation exposes values/reasons through generated logging, and no transport/persistence types enter Application.
  - Evidence: `verification.md` records compile-red and 3/7 executable failures, package-cycle correction, 16 focused checks passing, 1,450 full tests and 28 packaged JVM tests, and current full zero-finding Sonar/JDTLS analyses for all 21 Java revisions in `java-inventory.md`.

- [x] 4.2 Define and test the query, cursor, fingerprint, and transaction-context port contracts with focused test doubles.
  - Requirements: Q: Tenant and Party ownership concealment; M: Maintenance has one durable observable outcome; L: Atomic lifecycle concurrency and complete outcomes.
  - Verification: Port/architecture tests show safe qualified read results, sequential callback capabilities, immutable tenant scope, replay-first access, one primary scope per execution, and no session/entity/SQL/HTTP API. Preserve the existing current-identifier port contract.
  - Evidence: `verification.md` records 2/4 executable record/privacy failures, 18 focused checks green and all eight saved Java files with zero Sonar/JDTLS findings. Shared fixture timeout isolation reproduces and resolves the normal-call budget defect; final test/build pass 1,456 full tests and 28 packaged JVM tests, with all three changed fixture/test revisions independently verified. Earlier failed gates remain recorded as historical evidence.

- [x] 4.3 Test first and add neutral identifier failures plus API-only stable-code translation.
  - Requirements: Q: Safe shared envelopes and failure behavior; M: Identifier-version preconditions and exhaustion; L: Explicit identifier transition matrix; M: Scoped atomic primary ownership.
  - Verification: Tests cover identifier absence/version/lifecycle/primary/exhaustion/cursor failures and unchanged unknown failures through the shared global mapper. All affected exhaustive switches compile; Domain/Application import no response code, HTTP status, or ApiResponseException.
  - Evidence: `verification.md` records compile-red and 2/3 executable translation failures, six neutral variants and identifier-specific Domain classification, eight real HTTP failure probes, preserved exhaustive descriptions/architecture checks, 1,460 full tests and 28 packaged JVM tests passing, and current zero-finding Sonar/JDTLS for all nine Java revisions. User requested a stop here; resume with 5.1 tomorrow.

## 5. Flyway Persistence Foundation

- [x] 5.1 Test first and add the next immutable migration for identifier completions, withdrawal audit, qualified owner references, and listing order.
  - Requirements: M: Durable additional registration replay; L: Historical action replay survives changes and restart; L: Rejection and revocation retain safe history; Q: Deterministic scoped cursor navigation.
  - Verification: Flyway clean-install tests verify the completion primary key, qualified restrictive FKs, allowed operations/digest/schema/version constraints, audit uniqueness/binary bounds, and `(tenant_id, party_id, created_at DESC, id DESC)` index. Use tentative V8 only if still available; no manual DDL or schema generation.
  - Evidence: `verification.md` records missing-migration red, eight clean-install/constraint cases green, unchanged V1–V7 hashes, 1,468 full tests and 28 packaged JVM tests passing, and current zero-finding Sonar/JDTLS for all three saved Java revisions in `java-inventory.md`.

- [x] 5.2 Add and pass V7-to-new-migration upgrade and historical-retention tests.
  - Requirements: M: Maintenance has one durable observable outcome; Q: Complete historical listing and masked detail; Quality.
  - Verification: V1–V7 checksums, historical ciphertext/hash/key versions, all identifier states/flags/metadata, original Party snapshots, and existing indexes remain intact. New tables do not fabricate pre-cutover completions, and production migrations contain no test seeds or expiration backfill.
  - Evidence: User-supplied `java:S5961` diagnostic resolved by splitting retained-data and schema-history tests without removing assertions. Both upgrade tests, 1,470 full cases and build pass; the final saved revision has zero Sonar/JDTLS findings. See the resolved task 5.2 continuation in `verification.md` and `java-inventory.md`.

## 6. Historical Query Vertical Slice

- [x] 6.1 Test first and implement the identifier-only authenticated cursor adapter using existing rotation key material.
  - Requirements: Q: Deterministic scoped cursor navigation; Q: Strict read selectors and filters.
  - Verification: Unit tests prove full microsecond boundary round trips, forward/backward directions, resource domain separation, every scope dimension, absent versus false, default-limit equivalence, canonical bounded encoding, tampering/unknown key/version rejection, and retained-key rotation without token contents in failures.
  - Evidence: `verification.md` records compile-red then two executable scope/Unicode failures, seven cursor cases green, cursor/architecture regression, 1,477 full tests and 28 packaged JVM tests passing, and zero Sonar/JDTLS findings for both saved revisions in `java-inventory.md`.

- [x] 6.2 Test first and implement qualified scalar detail reads without loading protected columns.
  - Requirements: Q: Tenant and Party ownership concealment; Q: Complete historical listing and masked detail; Q: Safe shared envelopes and failure behavior.
  - Verification: Reactive PostgreSQL tests retrieve every stored state for owned identifiers and conceal wrong owners; source/query assertions exclude ciphertext, hashes, and key versions. Reads change no state/audit/version and leave the current natural-person projection unchanged.
  - Evidence: `verification.md` records compile-red and three executable historical-state failures, four focused cases green across both Party types/all owner states, current-reader/architecture regression, 1,481 full tests and 28 packaged JVM tests passing, and zero saved-revision Sonar/JDTLS findings.

- [x] 6.3 Test first and implement snapshot-consistent historical list/count/navigation queries.
  - Requirements: Q: Strict read selectors and filters; Q: Deterministic scoped cursor navigation; Q: Exact collection metadata and empty outcomes.
  - Verification: PostgreSQL tests cover exact filter AND semantics, unknown/mismatched schemes, descending timestamp/unsigned UUID ties, both directions, missing/changed boundaries, empty continuations, counts before continuation, and one read-only repeatable-read view. Retained memory is page-bounded; record a representative query plan.
  - Evidence: Nine query cases and 1,490 full tests pass with zero saved-revision Sonar/JDTLS findings. The previous packaged Podman startup failure is reconciled by a fresh build against a test-scoped API service with `--time=0`; all 28 packaged JVM cases execute and pass without skips. See the task 6.3 runtime continuation in `verification.md`.

- [x] 6.4 Test first and implement detail/list Application workflows with owner checks and cursor validation.
  - Requirements: Q: Tenant and Party ownership concealment; Q: Complete historical listing and masked detail; Q: Deterministic scoped cursor navigation.
  - Verification: Port-double tests verify owner-before-identifier access, invalid cursor before data retrieval, Party-not-found versus identifier-not-found, empty owned pages, safe result propagation, and support for both Party types/all Party states without protection/decryption calls.
  - Evidence: `verification.md` records compile-red and six executable precedence failures, nine query-workflow tests and architecture regression green, 1,499 full tests plus 28 packaged JVM tests passing on the stable test-scoped Podman API, and zero Sonar/JDTLS findings for all three saved revisions.

- [x] 6.5 Test first and implement API-only canonical identifier selectors and strict list query parsing.
  - Requirements: Q: Strict read selectors and filters; Q: Tenant and Party ownership concealment.
  - Verification: Parser tests reject noncanonical IDs, duplicate/unknown/blank filters, wrong case/status/boolean tokens, and invalid limits; accept limits 1/200/default 50 and exact scheme codes with code-point boundaries. Public codes and raw-value non-disclosure match OpenAPI.
  - Evidence: Ten parser/support cases, compatibility/architecture regression, 1,509 full tests and 28 packaged JVM tests pass. The supplied concise-character-class diagnostic is resolved with `\\d+`; current Sonar/JDTLS both report zero findings on the final saved parser revision, and the other three saved-file analyses remain valid. See the task 6.5 continuation in `verification.md` and `java-inventory.md`.

- [x] 6.6 Test first and extend identifier DTO/page mapping with checked counts and null-aware pagination characterization.
  - Requirements: Q: Exact collection metadata and empty outcomes; Q: Safe shared envelopes and failure behavior.
  - Verification: Managed-Jackson tests prove exact safe item properties, no reason/storage fields, optional-null omission, all five pagination fields, both explicit null cursors, empty counts, checked maximum/overflow behavior, and unchanged ordinary/error/unrelated envelopes.
  - Evidence: Seven managed-mapper cases pass after executable overflow/null-omission red; existing current-identifier HTTP assertions now enforce required/allowlisted fields per the approved optional-null schema. Final 1,516 full tests and 28 packaged JVM tests pass, with zero saved-revision Sonar/JDTLS for all five changed files; see `verification.md` and `java-inventory.md`.

- [x] 6.7 Test first and expose both identifier GET routes through the Application workflows and ResponseManager.
  - Requirements: Q: Complete published party-identifiers contract; Q: Trusted context and process correlation; Q: Complete historical listing and masked detail.
  - Verification: Live HTTP tests cover both Party types/all Party states, every identifier state, filtering/navigation, exact safe 200 envelopes, owner concealment, invalid reads, and process echoes. Signature/ArchUnit assertions prove explicit `Uni<RestResponse<ApiResponse<...>>>` and resource delegation.
  - Evidence: Thirty-seven live GET cases pass after missing-route and missing-pagination red, including both types/all owner states/all identifier states, exact filters/opaque cursors, ownership, syntax and process echoes. Explicit shared-DTO signatures and architecture are inspected; final 1,553 full tests plus 28 packaged JVM cases pass with zero Sonar/JDTLS for all four changed revisions. See `verification.md` and `java-inventory.md`.

## 7. Atomic Mutation Infrastructure

- [x] 7.1 Test first and implement versioned HMAC effective-request fingerprints for identifier creation and actions.
  - Requirements: M: Durable additional registration replay; L: Durable action key scope and request equivalence; Q: Safe shared envelopes and failure behavior.
  - Verification: Unit tests pin exact code-unit encoding, all effective properties/defaults, raw representations that normalize equally but conflict, reason/target/version/replacement changes, actor/process exclusion, action/tenant isolation, and constant-time digest comparison. Reuse the existing external HMAC key with identifier domain separation; persist no raw canonical request.
  - Evidence: Eight fingerprint cases pass after executable reason/code-unit red, including independently computed byte-exact HMAC vectors, raw/default/scope bounds and constant-length decoded comparison. Existing fingerprint/architecture regression and final 1,561 full tests plus 28 packaged JVM cases pass with zero saved-source Sonar/JDTLS; see `verification.md` and `java-inventory.md`.

- [x] 7.2 Test first and implement schema-1 safe identifier completion encoding/decoding and same-session completion storage.
  - Requirements: M: Durable additional registration replay; L: Historical action replay survives changes and restart; Q: Safe shared envelopes and failure behavior.
  - Verification: Codec/storage tests pin safe allowlists, row/snapshot scope consistency, original timestamps/versions, create versus action coherence, and rejection of unknown/corrupt schema/IDs/enums without payload-bearing causes. Saved completions survive fresh sessions; earlier Party/catalog codecs remain compatible.
  - Evidence: Eight codec/two reactive storage cases and earlier codec/architecture regression pass, with 1,571 full cases and 28 actual packaged JVM results. Supplied java:S1612 is resolved by Assertions::assertNull; all seven final saved revisions report zero Sonar/JDTLS. Full test/build rerun succeeds; see the task 7.2 continuation in `verification.md` and `java-inventory.md`.

- [x] 7.3 Test first and implement exact bounded binary reason-audit encoding and reactive persistence.
  - Requirements: L: Rejection and revocation retain safe history; L: Atomic lifecycle concurrency and complete outcomes.
  - Verification: Unit/PostgreSQL tests round-trip absent, empty, control/U+0000, and supplementary-character reasons with exact actor/time/action/version. Constraints reject invalid owner references or duplicate audit; audit remains independent of outbox mode and cannot leak through result/log representations.
  - Evidence: Four codec/four PostgreSQL cases pass after executable bound/corruption and missing-reason red, including unmatched code units, maximum binary/logical bounds, exact attribution, disabled events, qualified constraints and rollback. Final 1,579 full tests/28 actual packaged JVM cases and all four saved-revision zero Sonar/JDTLS pass; see `verification.md` and `java-inventory.md`.

- [x] 7.4 Test first and implement transaction-scoped replay and tenant/Party/scheme serialization with qualified row restoration.
  - Requirements: M: Scoped atomic primary ownership; L: Verification respects provisional primary ownership; L: Durable action key scope and request equivalence.
  - Verification: Independent-session tests observe replay-before-Party-before-scope-before-child lock order, full-tuple lookup, distinct two-integer namespaces, forced collision serialization without false replay/ownership, shared Party locking, and fresh target state after waiting. No root version is incremented.
  - Evidence: Three identity/three reader/four coordinated cases pass, including observed PostgreSQL waiting locks, exact completion lookup after commit, forced collisions, namespace independence, shared root/NOWAIT child probes and fresh version restoration. The supplied java:S1192 findings are resolved; all five saved revisions have zero Sonar/JDTLS and final 1,589 full tests/build pass with 28 retained actual packaged JVM results. See `verification.md` and `java-inventory.md`.

- [x] 7.5 Test first and implement guarded identifier insert/update/reload and demotion-before-promotion storage operations.
  - Requirements: M: Active-value uniqueness and release; M: Identifier-version preconditions and exhaustion; M: Scoped atomic primary ownership.
  - Verification: Reactive tests prove qualified expected-version predicates, exactly one explicit version increment without ORM dirty-version duplication, protected/immutable column preservation, awaited demotion, refreshed accepted results, and neutral diagnosis of unapplied writes. Only the known value/primary constraints map to business conflicts.
  - Evidence: Nine PostgreSQL cases pass after promotion-order and historical-insert red, including guarded exact increments, qualified ownership, protected/creation/verification preservation, dates/nulls, narrow constraints and late-demotion rollback. Final 1,598 tests/build pass with 28 actual packaged JVM results; both saved revisions have zero Sonar/JDTLS. See `verification.md` and `java-inventory.md`.

- [x] 7.6 Test first and extend safe identifier event candidates, outbox encoding/decoding, and message serialization.
  - Requirements: M: Maintenance has one durable observable outcome; L: Atomic lifecycle concurrency and complete outcomes; Q: Safe shared envelopes and failure behavior.
  - Verification: Tests preserve exact `party.identifier-created.v1` compatibility and pin the five new schema-1 event kinds/allowlists, affected identifier/version identities, one transition event or one primary-change event per affected aggregate, and no reason/protection/replay material. All three existing outbox modes remain supported.
  - Evidence: Supplied java:S3824 diagnostic resolved with computeIfPresent; final serializer revision has zero Sonar/JDTLS findings. Event/mode/creation compatibility and architecture regression pass, followed by 1,604 full tests and 28 actual packaged JVM cases without failures/skips. See the task 7.6 continuation in `verification.md` and `java-inventory.md`.

- [x] 7.7 Test first and implement the sequential identifier transaction-context adapter with existing timeout/cleanup policies.
  - Requirements: M: Maintenance has one durable observable outcome; L: Atomic lifecycle concurrency and complete outcomes; Q: Safe shared envelopes and failure behavior.
  - Verification: Adapter tests prove READ COMMITTED setup, one session/tenant/Vert.x context, illegal concurrent/reused/closed scope rejection, no writes after replay, completion/audit/event consistency, flush/commit before outcome emission, and cleanup on failure/cancellation. Infrastructure chooses no business transition or replacement policy.
  - Evidence: Eleven adapter cases and architecture checks pass after executable transaction-context red; real lock/statement/work timeout and cancellation tests prove rollback/cleanup and pool reuse. Supplied java:S6213 is resolved by recordAcceptedWrite; all seven saved files have zero Sonar/JDTLS findings. Final 1,615 full tests and 28 actual packaged JVM cases pass without failures/skips; see `verification.md` and `java-inventory.md`.

## 8. Additional Registration Vertical Slice

- [x] 8.1 Test first and refactor additional registration into the replay-first Application workflow.
  - Requirements: M: Eligible independent registration; M: Durable additional registration replay; M: Active-value uniqueness and release.
  - Verification: Port-double tests prove completed replay before Party/scheme/protection, exact fingerprint conflicts, one new-operation clock capture, locked-scheme preparation in the same context, ACTIVE/type/value/date rules, provisional flags, and safe version-0 outcomes. Initial Party registration preparation remains unchanged.
  - Evidence: Ten workflow cases and registration/architecture regression pass after eight executable red failures. All four saved revisions have zero Sonar/JDTLS findings; 1,625 full tests pass. The user-authorized standalone build completes in 3m 51s with unit tests up-to-date and all 28 packaged JVM cases passing, resolving the combined-command deadline. See `verification.md` and `java-inventory.md`.

- [x] 8.2 Switch CDI composition to the durable registration workflow and retire obsolete additional-registration code only after consumer checks.
  - Requirements: M: Maintenance has one durable observable outcome; Quality — strict composition and compatibility.
  - Verification: Composition/startup, registration, and architecture tests pass with one selected adapter/workflow, no nested repository session during preparation, and no production references to removed types. Equivalent regression tests replace obsolete adapter-specific assertions; no initial-registration path is removed.
  - Pending verification: CDI cutover, retired consumers and replacement regressions are implemented; 74 focused checks, 1,622 full tests and all 28 packaged JVM cases pass. One current Sonar finding in ReactivePartyIdentifierRegistrationAdapterTest and explicit reanalysis of a mixed-source incomplete-model batch keep this task unchecked; see `verification.md` and `java-inventory.md`.
  - Continuation: Supplied java:S5785 is corrected in saved source with assertNotEquals; focused/full tests and build pass again. The IDE still reports one Sonar issue and now one JDTLS problem without diagnostic details, so the saved-source/document discrepancy and six pending explicit analyses remain unresolved.
  - Assertion gate resolved: The supplied unused assertFalse import diagnostic is fixed. Final reactive test has zero Sonar/JDTLS findings; 1,622 tests and build pass. Only explicit resolved-model analysis of the six BATCH-PENDING revisions remains required before task acceptance.
  - Accepted continuation: Developer confirms completion of all six requested individual analyses without findings; new resolved-model logs corroborate the production revisions and recording double. All blobs are unchanged, diagnostics are reconciled, and the passing 1,622 full / 28 packaged JVM checks remain valid. See the final saved-source evidence in `verification.md` and `java-inventory.md`.

- [x] 8.3 Test first and complete strict POST binding, shared 201 envelopes, and live registration/replay coverage.
  - Requirements: M: Strict additional registration input; M: Eligible independent registration; M: Durable additional registration replay; Q: Safe shared envelopes and failure behavior.
  - Verification: HTTP tests cover raw limits, strict types/duplicates/trailing content, default/null distinctions, body→key→Party precedence, required key errors, both types, eligible/ineligible schemes, optional expiration, duplicate conflicts, failed-key reuse, and original 201 replay after later mutations with fresh correlation.
  - Evidence: Six managed binding cases and thirteen real POST cases pass after six executable behavior failures. A prior Jackson-specific rejection assertion is reconciled without dropping unknown-property rejection; its safe constant message is also checked. Final 1,641 full tests and 28 actual packaged JVM cases pass, and all eight saved Java revisions have zero completed Sonar/JDTLS findings. See `verification.md`, `java-inventory.md`, and `scenario-evidence.md`.

## 9. Metadata PATCH Vertical Slice

- [x] 9.1 Test first and implement the strict presence-aware PATCH body and identifier-specific If-Match support.
  - Requirements: M: Strict partial metadata correction; M: Identifier-version preconditions and exhaustion; M: Maintenance follows the common identifier boundary.
  - Verification: Decoder/support tests preserve omission/null, reject non-metadata/duplicate/coerced/trailing input, enforce raw issuer length/calendar dates, distinguish missing/null/empty bodies, and validate header cardinality/canonical decimal/range directly into PartyIdentifierVersion. Shared Party-version parsers remain compatible.
  - Evidence: Compile-red then seven executable failures resolve with strict three-field decoding, supplied-issuer validation and directly typed version parsing. Eight new binding/support cases and existing parser/registration regressions pass; final 1,649 tests and 28 actual packaged JVM cases pass, with all eight saved-source Sonar/JDTLS results zero. Build's regenerated test-index/resource invalidation is recorded in `verification.md`. Actual PATCH orchestration/route remains 9.2–9.3.

- [x] 9.2 Test first and implement current-version metadata correction orchestration using Domain decisions and the mutation context.
  - Requirements: M: Metadata correction eligibility and audit; M: Identifier-version preconditions and exhaustion; M: Maintenance has one durable observable outcome.
  - Verification: Workflow tests pin absence→version→state→exhaustion→resulting dates, one clock read, unchanged protected/verification/Party data, one accepted event, and no completion/key behavior for PATCH. Failed/identical/repair cases match the specs.
  - Evidence: Compile-red followed by nine executable workflow failures; all nine changed-class cases now pass with exact sequence/preservation/effects and neutral precedence. Source review and both saved revisions' Sonar/JDTLS are zero. The developer-requested focused build succeeds in 22s with the selected tests up-to-date; global/package/native acceptance remains its final tasks. See `verification.md` and `java-inventory.md`.

- [x] 9.3 Test first and expose metadata PATCH with live success/failure and preservation assertions.
  - Requirements: M: Strict partial metadata correction; M: Metadata correction eligibility and audit; Q: Trusted context and process correlation.
  - Verification: HTTP tests cover pending/verified correction, terminal rejection, partial null/retained fields, date repair/inversion, identical increment, stale/exhausted versions, syntax precedence, and ignored replay headers. Stored Party/protection fields and previous creation results are byte/value-identical.
  - Evidence: Eight actual HTTP route failures resolve; eight real PATCH cases plus modified signature/composition classes pass 17 focused checks with a successful focused build. Stored protected/immutable/verification and Party/subtype rows, creation snapshots and exact completion/event counts are verified. All eight saved revisions have zero Sonar/JDTLS findings; source/generic boundary inspection is recorded in `verification.md`/`java-inventory.md`.

## 10. Verification and Withdrawal Vertical Slice

- [x] 10.1 Test first and implement strict optional reason decoding and a nonblocking verify body-presence guard supported by the project BOM.
  - Requirements: L: Lifecycle requests follow the common boundary; L: Rejection and revocation retain safe history.
  - Verification: Tests distinguish absent/empty objects from literal null, enforce exact 64/1000-code-point strings and known properties, and reject verify bodies including whitespace/chunked content. Guard does not depend only on Content-Length, synchronously read a transport stream, duplicate the context filter, or become a global error handler.
  - Pending quality gate: Seven executable behavior failures and the BOM whitespace/absence edge resolve; all nine changed-class cases and focused build pass. All seven JDTLS revisions are zero, but one current Sonar finding in PartyIdentifierReasonRequestDeserializer requires its actual rule/message before correction. See `verification.md`/`java-inventory.md`; task remains unchecked.
  - Accepted continuation: Supplied line-25 separate-declaration diagnostic is corrected. Final decoder has full zero Sonar/JDTLS; its five affected unit cases and focused build pass. The unchanged four real buffered/chunked HTTP results and other six analyses remain valid. See final saved revision in `verification.md`/`java-inventory.md`.

- [x] 10.2 Test first and implement shared VERIFY/REJECT/REVOKE Application orchestration with durable action replay and independent audit.
  - Requirements: L: Explicit identifier transition matrix; L: Operator-confirmed verification and validity; L: Verification respects provisional primary ownership; L: Durable action key scope and request equivalence; L: Atomic lifecycle concurrency and complete outcomes.
  - Verification: Port-double tests cover every matrix entry, syntax-independent business precedence, one operation instant, original verification actor/time, provisional-primary conflict, permitted past-date withdrawal, retained verification, exact optional reasons, replay-before-live-state, failed-key reuse, and no automatic Party action.
  - Continuation blocked: Existing implementation inspected; explicitly filtered tests fail during container-dependent Quarkus discovery/bootstrap. The previous temporary Podman socket is absent; current saved-revision IDE evidence is also pending. See `verification.md`; no acceptance is claimed.
  - Runtime blocker resolved: User-authorized temporary Podman API restoration succeeds; 11 lifecycle and 10 changed PATCH tests pass, and the selected build passes. Saved-source Sonar/JDTLS acceptance remains pending for the six inventoried revisions; no full-suite/package/native acceptance is claimed.
  - Accepted continuation: Developer confirms all six requested individual Sonar/JDTLS analyses are clean. Rechecked unchanged saved blobs; the 21 passing selected cases and focused build remain valid. This supersedes the pending IDE gate; see `verification.md` and `java-inventory.md`.

- [x] 10.3 Test first and expose verify/reject/revoke through thin resources and the shared response boundary.
  - Requirements: Q: Complete published party-identifiers contract; L: Lifecycle requests follow the common boundary; L: Rejection and revocation retain safe history.
  - Verification: Live HTTP tests execute all allowed and forbidden transitions for both Party types, check exact 200/400/409/412/422 envelopes and process echoes, verify reason audit with events disabled, and prove failed operations leave flags, versions, evidence, reasons, and events unchanged.
  - Runtime green, quality pending: Seven executable missing-route failures resolve with three thin buffered-body routes, API-only syntax mapping and actual lifecycle CDI composition. Seven real HTTP cases plus seven signature/four producer checks pass (18 selected cases); focused build succeeds. Eight saved Java revisions still require actual Sonar/JDTLS acceptance; see `verification.md` and `java-inventory.md`.
  - Accepted continuation: Developer confirms all eight requested saved-revision Sonar/JDTLS checks are clean. All blobs remain unchanged; the passing 18 selected tests and focused build are retained without rerunning unrelated tests. Proceed to 10.4.

- [x] 10.4 Add live historical lifecycle replay and cross-scope equivalence/conflict tests.
  - Requirements: L: Durable action key scope and request equivalence; L: Historical action replay survives changes and restart; Q: Trusted context and process correlation.
  - Verification: HTTP tests replay original verification after revocation and withdrawals after subsequent changes, retain original attribution while echoing the retry process, exercise exact/empty/default reason identities and changed target/version/reasons, and prove key isolation across tenants/actions/other features without duplicate audit/events.
  - In progress, verification blocked: Six real replay/scope cases added. Five initially pass; one fixed-clock audit-order fixture error is corrected without changing production. The following selected test/build fails writing Gradle binary results (NoSuchFileException); current green evidence and both saved-file IDE analyses are still required. See `verification.md` and `java-inventory.md`.
  - Accepted continuation: User-authorized selected retry passes all six cases and build in 3m 20s. Developer confirms clean Sonar, and independent current JDTLS reports zero for both unchanged saved blobs. The result-writing blocker is resolved by the passing retry; final restart/native evidence remains in its assigned tasks.

## 11. Primary Selection Vertical Slice

- [x] 11.1 Test first and implement strict optional replacement-body decoding and effective-default mapping.
  - Requirements: M: Strict primary-selection input and eligibility; L: Durable action key scope and request equivalence.
  - Verification: Tests equate absent body/object/property and explicit false, distinguish true, and reject literal/property null, duplicate/unknown properties, coercion, and trailing content. Action syntax follows body→Party→identifier→If-Match→key.
  - Evidence: Compile-red followed by five executable binding/mapping failures resolves with native-registered strict replacement decoding, actual-zero-byte defaults and ordered command mapping. Six primary and six changed reason-binding checks pass with focused build; all six saved Java revisions have full resolved-model zero Sonar and independent zero JDTLS diagnostics. See `verification.md` and `java-inventory.md`.

- [x] 11.2 Test first and implement primary-selection orchestration with a validated Domain replacement plan and replay.
  - Requirements: M: Scoped atomic primary ownership; M: Identifier-version preconditions and exhaustion; L: Historical action replay survives changes and restart.
  - Verification: Workflow tests cover eligibility/date boundaries, exact scope, conflict without replacement, demotion before promotion, common actor/instant, already-primary increment, former exhaustion, and historical replay without touching current primaries. No Party/scheme version changes or implicit provisional-flag overrides occur.
  - Runtime green, quality pending: Compile-red and eight executable workflow failures resolve with replay-first Application orchestration, one instant, whole-plan validation and sequential guarded demotion/promotion/completion/events. All eight selected tests and focused build pass; the two new saved revisions still require actual Sonar/JDTLS evidence. No route/CDI cutover is claimed before 11.3.
  - Accepted continuation: Developer confirms both requested saved-file Sonar/JDTLS checks are clean. Blobs remain unchanged and the eight passing selected tests/focused build remain valid; see `verification.md` and `java-inventory.md`.

- [x] 11.3 Test first and expose set-primary with real two-record preservation and replay assertions.
  - Requirements: M: Strict primary-selection input and eligibility; M: Scoped atomic primary ownership; M: Maintenance has one durable observable outcome.
  - Verification: HTTP tests show independent scheme primaries, explicit replacement of past-date former primaries, exact target/former increments, unchanged unrelated scopes/verification/value, safe candidate-only response, and historical replay after a later replacement with unchanged current state and event counts.
  - Evidence: Eight executable missing-route cases resolve with thin buffered-body delivery, API-only syntax mapping and actual workflow composition. Eight real HTTP/effect/preservation cases plus eight signature/five composition cases pass (21 selected checks); focused build succeeds in 4m 17s. Local analysis remains recorded for final 14.5 under the developer's updated nonblocking Sonar schedule; no full/package/native execution is claimed.

## 12. Concurrency, Resilience, and Compatibility Hardening

- [x] 12.1 Add coordinated database and HTTP races for creation/action key equivalence, changed inputs, and same-target versions.
  - Requirements: M: Active-value uniqueness and release; M: Durable additional registration replay; M: Identifier-version preconditions and exhaustion; L: Atomic lifecycle concurrency and complete outcomes.
  - Verification: Barrier-started independent sessions prove one created/action result for equivalent keys, 409 key conflict for changed-input losers, 409 active-value conflict for distinct creation keys, and 412 for independent same-version PATCH/transition losers. Assert exact accepted completion/audit/event counts for both Party types.
  - Evidence: Four actual HTTP/PostgreSQL race cases (26 coordinated scenarios across both Party types) pass with focused build in 4m 5s. Test-only JDBC holds the exact production replay/scope lock and observes two independent request transactions waiting before release; keyed creation/all four actions converge/conflict, distinct duplicate keys yield 409, PATCH/transition losers yield 412, and exact row/version/completion/audit/event counts match. Final local quality review remains scheduled in 14.5.

- [x] 12.2 Add coordinated different-target primary races and primary-versus-correction/withdrawal races.
  - Requirements: M: Scoped atomic primary ownership; L: Verification respects provisional primary ownership; M: Identifier-version preconditions and exhaustion.
  - Verification: Tests prove one first selection or provisional verification winner, proper 409 conflicts, no deadlock/opposite lock order, serially valid explicit replacements, and 412 when a competing demotion already changed the target. Final flags/versions/audit match an allowed serial outcome and unrelated scopes remain independent.
  - Evidence: Four added observed-lock race cases pass alongside the four modified-class earlier cases (37 coordinated scenarios total), with focused build in 4m 8s. Both Party types retain one scoped primary, explicit replacements serialize with valid historical replay, correction/withdrawal outcomes match valid serial order and stale demotion losers receive 412; another scheme completes while the first actually waits. Final local analysis remains scheduled in 14.5.

- [x] 12.3 Add late-failure, timeout, cancellation, and pool-reuse tests for actual mutation workflows.
  - Requirements: M: Maintenance has one durable observable outcome; L: Atomic lifecycle concurrency and complete outcomes; Q: Safe shared envelopes and failure behavior.
  - Verification: Test-only fault checkpoints after demotion/promotion/audit/completion/event preparation prove total rollback and key reuse. Actual lock/statement/operation waits and cancellation release context/session and permit fresh work. Classified failures return 503; ambiguous post-commit delivery is recovered by keyed retry without false rollback claims.
  - Evidence: Six actual workflow resilience cases pass with focused build in 4m 45s, covering flushed primary/withdrawal checkpoints, unchanged complete records/effects on rollback, key reuse, real production-budget lock/statement/work 503 failures and pool reuse, Vert.x cancellation/session closure, and keyed recovery after simulated postcommit delivery failure. No production fault hooks or weakened budgets are introduced; final local analysis remains scheduled in 14.5.

- [x] 12.4 Complete framework and unexpected-error HTTP coverage through the existing published global handler.
  - Requirements: Q: Safe shared envelopes and failure behavior; L: Lifecycle requests follow the common boundary; M: Maintenance follows the common identifier boundary.
  - Verification: Malformed JSON, Bean Validation, paths/queries, missing routes, unsupported methods/media, injected dependency failures, corrupt completions, and unexpected internal exceptions produce exact status/code-only 400/404/405/415/500/503 responses with accepted echoes and no messages/causes. Conditional authentication preserves the existing 401 contract.
  - Contract blocker: Five actual-route framework cases added; four pass, but unsupported PUT on the known identifier collection path returns 404 instead of the required 405. The expectation is retained and no overlapping local global mapper/filter is introduced. Resolve the routing/shared-boundary discrepancy before acceptance; see `verification.md`. Sonar availability is not the blocker.
  - Accepted correction: Quarkus 3.33.3.1 dispatch fallback is confirmed from exact-version source. Grouped identifier methods under the existing /v1/parties root, following NationalityResource, while preserving every public URL/signature. Six actual HTTP framework/all-eight-success cases and nine generic signature cases pass with focused build in 4m 22s. Shared handlers/configuration and conditional authentication remain unchanged; no local global mapper/filter is added. See final evidence in `verification.md`.

- [x] 12.5 Extend bounded operation telemetry and committed identifier disposition in the existing request context.
  - Requirements: Q: Trusted context and process correlation; L: Atomic lifecycle concurrency and complete outcomes; Quality — fixed logging format.
  - Verification: Observation tests cover eight route labels/spans, applied/replayed/conflict/failure/cancellation, key/scope wait and page cost, and concurrent context isolation. No raw keys, IDs, reasons, values, or cursor payloads appear in dimensions. Completion remains owned by the existing filter and the exact configured logging format is unchanged.
  - Evidence: Executable missing-label red resolves with bounded eight-route classification, postcommit disposition/key-presence state, HTTP/core query observations and managed lock/page-cost measurements. Nineteen selected HTTP/unit/query/observation cases pass with focused build in 5m; concurrent actor/tenant/process isolation and private-dimension exclusion are checked. Existing request filter still owns completion/MDC and logging format remains unchanged; final local source review is scheduled for 14.5.

- [x] 12.6 Run and complete focused compatibility regression coverage for existing Party, current-identifier, nationality, and scheme features.
  - Requirements: Q: Complete historical listing and masked detail; M: Maintenance has one durable observable outcome; L: Rejection and revocation retain safe history.
  - Verification: Existing creation snapshots/replays, natural-person current ordering/eligibility, legal/root updates, activation evidence, nationality, and catalog suites pass. New tests prove withdrawals release uniqueness without deleting history, last-evidence revocation leaves ACTIVE Party state/version unchanged, and schemes/Party details remain intact.
  - Evidence: Seventy-one explicitly selected compatibility cases pass with focused build in 7m 31s, including inherited related reader/published-scheme cases. Two new actual HTTP tests cover both Party types, verification feeding existing activation without primary, last-evidence revocation preserving complete Party/subtype/scheme rows, unchanged historical creation replay and released uniqueness with retained identifier history. No unrelated full/package/native suite is executed.

## 13. Packaged Coverage and Operational Documentation

- [x] 13.1 Extend shared contract assertions and packaged JVM HTTP/OpenAPI coverage for all eight operations.
  - Requirements: Q: Complete published party-identifiers contract; Q: Safe shared envelopes and failure behavior; Quality — packaged verification.
  - Verification: Packaged tests use real ports/resources, both Party types, strict optional/missing bodies, safe schemas/null-aware pages, and business/framework errors. Served paths and referenced schemas equal the approved source, and unit-test CDI fixtures are absent from the packaged classpath.
  - Evidence: Two executable shared-assertion failures resolve with canonical selector/status/scalar/version validation while preserving historical issuer text. Four selected assertion tests/build pass; four actual packaged JVM tests pass in 55s covering both types/all eight routes, historical creation/action/primary replay, signed/null-aware pages, strict and chunked bodies, framework/ownership failures and full schema/parameter equality (normalizing only commutative allOf order). No unrelated packaged suite or native execution is claimed.

- [x] 13.2 Add a two-process retained-data restart test for creation, lifecycle, primary replay, and durable reason audit.
  - Requirements: M: Durable additional registration replay; L: Historical action replay survives changes and restart; L: Rejection and revocation retain safe history.
  - Verification: Accept requests, discard their original HTTP payloads, mutate current records, restart the actual packaged service against retained isolated data, and replay original inputs. Original results/attribution survive with fresh process echoes, current state remains unchanged, and completion/audit/logical-event counts are not duplicated.
  - Evidence: One selected actual two-process JVM restart case passes in 40s. Original response bodies are discarded, identities/results recovered through public reads, then correction/replacement/withdrawal/Party archival/scheme retirement precede restart. Nine historical results replay unchanged with fresh actor/process; complete storage remains identical at three identifiers/nine completions/two audits/eleven events, exact binary reasons survive and private text is absent from completions/events/logs. Native execution remains 14.4.

- [x] 13.3 Maintain native-safe binding/reflection metadata and prepare the packaged assertion paths for native integration execution.
  - Requirements: Q: Complete published party-identifiers contract; Quality — native compatibility.
  - Verification: Metadata and executable-selection checks pass, new request/deserializer/codec/DTO registration is explicit at outer boundaries, and the prepared suites include strict bodies, cursor/page serialization, snapshots, audit, and restart cases without unit-test fixture leakage. Actual native compilation/execution evidence is required in 14.3–14.4, not claimed by this preparation task.
  - Evidence: One executable missing-response-registration failure resolves with explicit safe DTO reflection metadata. Both selected metadata tests/build pass; five packaged JVM contract/classpath-isolation cases and the selected two-process restart case pass against the newly augmented artifact in 1m 39s. All four strict request/decoder pairs are explicitly registered; Gradle native source-set isolation and actual artifact-aware launcher are preserved. Native compilation/execution remains 14.3–14.4.

- [x] 13.4 Update README and identifier operations documentation with implemented routes and retention/rollout guidance.
  - Requirements: Q: Complete published party-identifiers contract; M: Durable additional registration replay; M: Scoped atomic primary ownership; Quality.
  - Verification: English documentation covers all routes, errors, identifier If-Match, provisional flags, inclusive expiration, exact key/default semantics, primary replacement, historical versus current queries, existing secret retention/rotation prerequisites, timeouts, query cost, additive migration, pre-cutover replay limits, event compatibility, safe rollback, and local Sonar/JDTLS evidence procedure.
  - Evidence: README lists all eight implemented routes, independent versions/history/replay and additive V8/secret/rollback prerequisites. New English `docs/operations/party-identifier-contract.md` covers every required consumer/operations rule, explicit native/JVM evidence separation and saved-file local analysis. Links/routes/budgets/event kinds are inspected against source/OpenAPI; `git diff --check` passes. Documentation-only step does not rerun Java suites.

## 14. Final Acceptance on the Delivered Revision

- [x] 14.1 Run `./gradlew test` and reconcile the full unit/reactive/HTTP/architecture suite on the final saved source.
  - Requirements: All Q/M/L requirements; Quality.
  - Verification: Record the actual command/environment/results with no unexplained failures or skipped required cases. Review Month-based fixtures, dependency direction, every resource signature, and HTTP assertions; compilation alone is not contract evidence.
  - Continuation blocked: Final full run executes 1,766 tests in 12m 58s with one failure in PartyIdentifierFixturesTest: the standalone mapper emits timestamp values while strengthened shared assertions require textual createdAt. Candidate test-mapper correction awaits guidance under the apply error pause rule; see verification.md. No final acceptance is claimed.
  - Resolved continuation: User-authorized textual date serialization correction passes both focused fixture cases, then all 1,766 full JVM/reactive/HTTP/architecture cases with zero failures/skips in 12m 49s. All eight resource signatures remain explicit safe DTO envelopes; no assertion is weakened. Final independent local-analysis acceptance remains 14.5.

- [x] 14.2 Run `./gradlew build` and resolve packaged JVM or compiler/IDE disagreements.
  - Requirements: Q: Complete published party-identifiers contract; Quality.
  - Verification: Build and wired quarkusIntTest checks pass on the delivered revision with recorded runtime overrides and no new compiler warnings. Investigate stale/unresolved JDTLS project-model diagnostics rather than accepting Gradle as a substitute.
  - Evidence: Full build succeeds in 3m 54s with the documented Podman socket/dynamic HTTP port, preserved full tests up-to-date, and 34 actually executed packaged JVM cases with zero failures/skips. No compiler warning or known compiler/IDE disagreement is reported; independent final IDE evidence remains required in 14.5.

- [x] 14.3 Run `./gradlew buildNative -Dquarkus.native.container-build=true` and resolve actual native build failures.
  - Requirements: Quality — native compilation.
  - Verification: Record selected container/runtime, Java/native toolchain, command overrides, and successful new executable generation. Missing tooling remains an incomplete task, not a successful compilation result.
  - Evidence: Actual Podman container build succeeds in 5m 12s with Mandrel 25.0.4.1-Final/JDK 25.0.4.1, jdk-25 builder and 6g native-image heap. New executable generation completes in 4m 6s; dependency/native option warnings are recorded without suppression. Native runtime acceptance remains 14.4.

- [x] 14.4 Run `./gradlew testNative -Dquarkus.native.container-build=true` with required packaged/restart cases enabled.
  - Requirements: All affected Q/M/L runtime contracts; Quality — native integration.
  - Verification: Native HTTP/binding/cursor/snapshot/audit/restart cases actually execute and pass against the newly built executable. Record failure resolution and runtime prerequisites; no required case is marked complete solely from JVM results.
  - Evidence: Full testNative succeeds in 2m 1s, actually executing 34 native integration cases with zero failures/skips, including five identifier contract cases and all four retained-data restart cases. Artifact descriptor is type=native and restart launcher selects that new executable. Final local/scenario acceptance remains 14.5–14.6.

- [ ] 14.5 Complete final saved-revision SonarQube for IDE and JDTLS verification for every changed Java file and nested type.
  - Requirements: Quality — zero unresolved applicable local findings and zero new compiler/IDE warnings.
  - Verification: The full inventory, including untracked and all source-set files, links final blobs to actual completed analysis and separate diagnostics. Fix and reanalyze findings, resolve model disagreements, and confirm no unjustified exclusions/suppressions. If fixes change source after 14.1–14.4, rerun affected checks and replace invalidated JVM/native evidence before final acceptance. Historic logs and an empty Problems panel alone are insufficient.
  - Blocked continuation: Current IDE log has only a mixed main/test zero-finding analysis with missing main classpath and unresolved-type warnings; it cannot establish final quality acceptance. The corrected PartyIdentifierFixturesTest has no current explicit Sonar/JDTLS result. Developer-collected resolved-model analysis/diagnostics for pending inventoried saved revisions are required; runtime gates 14.1–14.4 pass and remain recorded.
  - Supplied-diagnostic continuation: Seven screenshot findings in four tests are corrected without removing assertions or suppressing rules. All 18 selected cases and focused build pass. Current logs resolve the unchanged fixture correction's zero Sonar/JDTLS evidence; the four newly corrected blobs and remaining pending inventory still require final reanalysis. See verification.md and java-inventory.md; task remains unchecked.

- [ ] 14.6 Complete the scenario evidence map and architectural/public-contract review, then run `openspec validate complete-party-identifiers-contract --strict`.
  - Requirements: All 27 requirements and 87 scenarios; Quality.
  - Verification: Every scenario links to passing appropriate unit/integration/HTTP/native evidence, all eight signatures/envelopes and neutral-error boundaries are inspected, and final migration/docs/rollback checks are satisfied. OpenSpec validation passes with no unresolved required implementation/tooling evidence; only then is implementation ready for verification/archive.
