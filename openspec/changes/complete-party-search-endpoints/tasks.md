## Execution and Evidence

Execute sections in order and respect the dependencies stated by each task. Check a task only after its implementation and verification are evidenced. Focused tests support iteration; after Java changes, resolve JDTLS diagnostics for every affected file, introduce no compiler warnings, and run `./gradlew test`. Before implementation completion, run `./gradlew build` and the native commands in section 8.

Every new or modified Java type, including test and nested types, needs concise responsibility Javadoc and English code, messages, and technical documentation. Track saved revisions and actual JDTLS/SonarQube results throughout implementation; rerun affected checks after fixes. An unavailable check remains an evidenced blocker, not a passing result.

Requirement references below resolve to the named requirements in the [exact specification](specs/party-exact-identifier-search/spec.md) and [prefix specification](specs/party-prefix-identifier-search/spec.md). Design references resolve to [design.md](design.md).

| Reference | Requirement |
|---|---|
| E1 | Published exact identifier search contract |
| E2 | Trusted exact-search context and correlation |
| E3 | Strict exact-search request validation |
| E4 | Exact scheme-qualified normalized matching |
| E5 | Explicit historical status selection |
| E6 | Tenant-qualified result cardinality and ordering |
| E7 | Safe exact-search envelope and complete metadata |
| E8 | Safe exact-search failures and finite execution |
| P1 | Published identifier-prefix search contract |
| P2 | Trusted prefix-search context and correlation |
| P3 | Strict prefix-search request validation |
| P4 | Literal scheme-normalized prefix matching |
| P5 | Explicit historical prefix-search filters |
| P6 | Tenant-qualified hit cardinality and deterministic ordering |
| P7 | Strict bounded prefix page size |
| P8 | Scoped stable prefix continuation |
| P9 | Safe prefix response and exact collection metadata |
| P10 | Safe prefix failures and finite execution |

## 1. Foundation and Published Contract

- [x] 1.1 Confirm the current Java 25, Quarkus, Gradle, Hibernate Reactive, and native-builder baseline and the APIs needed for scoped reads, cancellation, and originating-context dispatch.
  - Requirements: E8, P10; Design: Context, Architecture, Resilience.
  - Verification: record actual resolved versions, existing worktree changes, and available JVM/native test prerequisites; confirm the proposed session/context APIs against the resolved BOM rather than an assumed artifact version.

- [x] 1.2 Initialize change-local verification evidence and a Java-file inventory for production, unit, shared-contract, and integration changes.
  - Requirements: proposal quality constraints; Design: Java, SonarQube, and verification evidence.
  - Verification: evidence files identify command/environment results, file paths, saved Git blobs, separate JDTLS/Sonar analysis fields, and unresolved acceptance items without claiming checks that have not run.

- [x] 1.3 Complete the two OpenAPI request schemas and operation descriptions with strict properties/types, raw UTF-16 limits, normalization, historical filter defaults, read-only behavior, and prefix limit/cursor semantics.
  - Requirements: E1, E3, E4, E5, P1, P3, P4, P5, P7, P8.
  - Verification: source contract defines closed requests, write-only value/prefix, the length-unit extension, omitted versus null/empty behavior, and no exact-search pagination inputs; affected references resolve.

- [x] 1.4 Complete the OpenAPI safe response schemas, process-echo headers, operation-specific error documentation, and representative success/empty/continuation/failure examples.
  - Requirements: E1, E2, E7, E8, P1, P2, P9, P10.
  - Verification: declared successes have status 200, closed safe fields, required nullable cursors and nonnull supported counts; examples cover both operations and applicable stable codes without sensitive fields.

- [x] 1.5 Add reusable test-only IdentifierSearchContractAssertions under src/contractTest/java and positive/negative schema/example fixtures.
  - Requirements: E1, E3, E7, E8, P1, P3, P7, P9, P10.
  - Verification: focused assertion tests accept valid examples and reject unresolved references, raw-unit violations, wrong tokens/statuses, extra fields, missing metadata, status mismatches, and private material; existing validation helpers are reused where compatible.

## 2. Domain Policy and Application Contracts

- [x] 2.1 Add normalizer-only resolution and a pure identifier search selector policy without invoking registration eligibility, complete-value validation, checksum, or expiration admission.
  - Requirements: E4, P4; Design: Domain search policy.
  - Verification: synchronous unit tests cover supported/unsupported normalization, complete equality versus literal prefix, whitespace/case, accents/punctuation/interior whitespace, short fragments, and case expansion; existing registration-policy tests remain valid.

- [x] 2.2 Implement immutable exact/prefix query, effective filter, and search-scope models with redacted value/cursor diagnostics.
  - Requirements: E3, E5, P3, P5, P7, P8.
  - Verification: unit tests prove omitted/full-set equivalence, empty-set distinction, duplicate/order canonicalization, default limit 50, normalization identity in scope, and no raw/normalized selectors or tokens in diagnostic output.

- [x] 2.3 Implement safe search-hit, full-precision position/boundary, exact-slice, prefix-slice, and page result models with checked count/page arithmetic.
  - Requirements: E6, E7, P6, P8, P9.
  - Verification: tests prove immutable safe payloads, descending timestamp/unsigned-UUID ordering, nullable navigation, exact 0/1 page metadata, prefix ceiling division, and overflow handling; reuse existing position types only when their contract matches.

- [x] 2.4 Define IdentifierSearchReadPort, its operation-scoped read context, and IdentifierSearchCursorPort using Application/Domain contracts and Mutiny only.
  - Requirements: E4, E6, E8, P4, P8, P9, P10; Design: Read and cursor ports.
  - Verification: port doubles compile for use-case tests, the callback owns one consistent logical read, and architecture checks find no HTTP, CDI, session, ORM entity, cryptographic key, hash, or ciphertext exposure in the contracts.

## 3. Existing Cryptography and Search Cursors

- [x] 3.1 Expose narrowly scoped Infrastructure lookup derivation using the existing tenant-derived identifier HMAC implementation and lossless selector encoding.
  - Requirements: E4, E6, E7, E8; Design: Exact lookup, Cryptography and CPU dispatch.
  - Verification: compatibility tests reproduce hashes generated by current registration, distinguish tenants, preserve existing protection output, and handle unrepresentable selectors without replacement-character matches or false dependency errors.

- [x] 3.2 Add authenticated internal identifier matching with identity-only AAD context and retained protection versions, reusing the existing AES-GCM implementation.
  - Requirements: P4, P6, P9, P10; Design: Cryptography and CPU dispatch, Security.
  - Verification: tests cover real ciphertext, historical key versions, literal matching, wrong owner/AAD/tag/version/key failures, safe diagnostics, and mutable-byte cleanup; matching returns only safe hits and does not fabricate registration plaintext input.

- [x] 3.3 Implement HmacIdentifierSearchCursorAdapter with search-specific authentication domains, a keyed sensitive scope digest, bounded canonical token parsing, and retained-key verification.
  - Requirements: P8, P9, P10.
  - Verification: tests cover both directions, precision, every scope dimension, equivalent effective inputs, tampering, cross-resource reuse, unknown formats/keys, key rotation, and maximum token bounds; token payloads expose neither selector plaintext nor an unkeyed prefix digest.

## 4. Reactive Persistence and Bounded Prefix Processing

- [x] 4.1 Add the next unused immutable Flyway migration, expected V9__add_identifier_search_order_index.sql, for the tenant/scheme/created-at/identifier ordering index.
  - Requirements: P6, P8, P9; Design: Prefix candidates and ordering index.
  - Verification: Flyway applies the additive index to an isolated database, migration/catalog assertions pass, and previously applied migrations and stored protected values remain intact.

- [x] 4.2 Implement the read-port adapter and guarded per-subscription context with exact scheme resolution in one read-only repeatable-read session and a finite subscription-to-completion budget.
  - Requirements: E4, E6, E8, P3, P4, P9, P10.
  - Verification: reactive tests prove same-view scheme access, finite acquisition plus remaining transaction deadlines, sequential session ownership, rejection of context use after completion, and cleanup on success/failure/cancellation.

- [x] 4.3 Implement tenant/Party-qualified exact count and full safe-hit projections using the existing lookup index and effective status sets.
  - Requirements: E4, E5, E6, E7, E8.
  - Verification: real database tests return all eligible historical hits, preserve timestamp/UUID order and same-view counts, exclude foreign records, short-circuit empty sets after preceding validation, and avoid decryption, first-result selection, Party deduplication, or page caps.

- [x] 4.4 Implement lifecycle-owned bounded CPU dispatch for detached batches, rejecting admission, cancellation handling, and return to the captured originating Vert.x context.
  - Requirements: P4, P9, P10; Design: Prefix search and CPU/session boundary, Resilience.
  - Verification: dispatcher tests prove finite configured worker/queue capacity, one outstanding batch per request, typed rejection, discarded late completion, resource cleanup, and no session/request-scoped bean access or caller-thread fallback on workers.

- [x] 4.5 Implement bound, joined prefix candidate projections and strict descending keyset traversal in batches of at most 256, filtering tenant/scheme/states before decryption.
  - Requirements: P4, P5, P6, P7, P9.
  - Verification: database tests prove qualified joins, full stored precision, unsigned UUID ties, bounded projections, candidate-boundary advancement across nonmatching batches, and exclusion of unrelated ciphertext without per-hit enrichment queries.

- [x] 4.6 Implement the pure bounded prefix-page accumulator with complete long totals, nearest previous-page selection, and real neighbor availability.
  - Requirements: P6, P7, P8, P9.
  - Verification: large-fixture tests prove page retention never exceeds the effective limit, complete counting, first/next/previous order, disappeared boundaries, empty continuation, and zero-global-match metadata without retaining all matching hits.

- [x] 4.7 Compose qualified candidate fetching, detached authenticated matching, original-context resumption, and accumulation into the prefix read operation with typed failure translation.
  - Requirements: P4, P5, P6, P8, P9, P10.
  - Verification: reactive adapter tests produce correct complete totals and bounded pages over several batches; all database calls stay sequential on the originating context, required corrupt candidates fail safely, and no partial success or indefinite retry occurs.

## 5. Use Cases and Explicit HTTP Delivery

- [x] 5.1 Implement SearchPartiesByIdentifierUseCase with same-view scheme lookup, supported normalization, complete historical matching, neutral failures, and exact-result metadata.
  - Requirements: E4, E5, E6, E7, E8.
  - Verification: port-double tests cover scheme/normalization precedence, empty filters, no matches, more than 200 hits, exact counts/null cursors, and absence of registration or mutation behavior.

- [x] 5.2 Implement SearchPartiesByIdentifierPrefixUseCase with canonical scope, cursor validation after scheme/normalization, safe page assembly, and available cursor encoding.
  - Requirements: P3, P4, P5, P7, P8, P9, P10.
  - Verification: tests prove unknown scheme precedes altered-token failure, equivalent scopes continue, empty filters still validate supplied tokens, changed scope rejects, and totals/navigation follow the safe slice with checked arithmetic.

- [x] 5.3 Implement separate strict exact/prefix request models and token-aware decoders with a search-specific binding translator and native registrations.
  - Requirements: E3, P3, P5, P7.
  - Verification: decoder tests reject malformed/trailing/duplicate/unknown/wrong-type input, null or invalid state arrays/items, oversized raw text, and invalid integer limits; body/cursor absence versus null follows the declared contract and diagnostics redact submitted values.

- [x] 5.4 Implement the two safe response DTOs and IdentifierSearchApiMapper with checked shared PaginationMetadata conversion.
  - Requirements: E7, P9, E8, P10.
  - Verification: mapping/serialization tests emit only declared stored summary/masked fields and all metadata; overflow fails instead of wrapping or omitting counts, and existing null-cursor writers preserve explicit unavailable directions.

- [ ] 5.5 Implement both POST methods in IdentifierSearchResource and compose their use cases through a focused Infrastructure CDI producer using existing context/error/response services.
  - Requirements: E1, E2, E3, E7, E8, P1, P2, P3, P9, P10.
  - Verification: both methods return `Uni<RestResponse<ApiResponse<List<IdentifierSearchResponse>>>>`, map DTOs before CDI ResponseManager wrapping, delegate behavior to Application, and pass architecture/HTTP smoke checks without overlapping global handlers.

## 6. Automated HTTP, Integration, and Runtime Verification

- [ ] 6.1 Integrate bounded search operation/span labels and numeric work/capacity observations with the existing request completion and MDC mechanisms.
  - Requirements: E2, E7, E8, P2, P9, P10; Design: Observability.
  - Verification: observation tests distinguish terminal outcomes and partial work statistics, preserve the exact configured console format, and reveal no selector/token/protected material or high-cardinality identities in metric dimensions.

- [ ] 6.2 Add real HTTP context and strict-input contract tests for both routes using the shared search assertions.
  - Requirements: E2, E3, P2, P3, P5, P7.
  - Verification: tests cover header name/cardinality/value precedence, accepted-only echo, concurrent attribution and management exemption, body/query restrictions, raw-unit boundaries, null/empty arrays, and all invalid limit/cursor token cases with stable status/code-only failures.

- [ ] 6.3 Add exact-search historical matching, ownership, ordering, and complete-collection HTTP/database cases using actually protected test data.
  - Requirements: E4, E5, E6, E7.
  - Verification: both Party types, every stored status, withdrawn schemes, normalization variants, historical duplicate identifiers, timestamp ties, empty/foreign-only results, and at least 201 same-value hits return the specified safe complete metadata.

- [ ] 6.4 Add prefix-search literal matching and forward/backward continuation HTTP/database cases across multiple candidate batches.
  - Requirements: P4, P5, P6, P7, P8, P9.
  - Verification: short official-ID fragments, punctuation, case expansion, all filters/states, duplicate/shared-Party hits, limit boundaries, equivalent/changed scopes, deleted boundaries, empty continuations, and exact counts satisfy the EARS scenarios.

- [ ] 6.5 Add controlled known/framework/dependency/unexpected failure cases for both routes, checking public envelopes and safe internal diagnostics.
  - Requirements: E8, P10, E4, P4, P8.
  - Verification: 422 scheme/normalization, 400 cursor, 404/405/415 framework, 503 classified unavailability, and injected/corrupt/count-overflow 500 cases return only matching status/code and accepted process echo; exercise 401 only under a real enabled authentication profile and document its conditional applicability.

- [ ] 6.6 Prove same-response snapshot consistency, live subsequent requests, and complete read-only behavior with deterministically coordinated concurrent writes.
  - Requirements: E6, E7, P6, P8, P9.
  - Verification: an intervening writer cannot split one response's scheme/data/count/navigation view; later requests observe current data, and search attempts themselves change no versions, audits, states, primary flags, replay records, or logical events.

- [ ] 6.7 Add runtime thread/context, CPU saturation, exhausted-pool acquisition, timeout, cancellation, late-completion, and subsequent resource-reuse tests.
  - Requirements: E8, P10; Design: Resilience, Prefix search and CPU/session boundary.
  - Verification: work terminates within configured finite budgets, sessions and CPU admission recover, subsequent requests succeed with fresh attribution, and no blocked-thread warning is disabled or session is used concurrently/off-context.

- [ ] 6.8 Add PackagedIdentifierSearchContractIT for the existing packaged/native source set, reusing shared assertions and excluding unit-test CDI alternatives.
  - Requirements: E1, E2, E3, E4, E7, E8, P1, P2, P3, P4, P8, P9, P10.
  - Verification: packaged JVM execution exercises actual HTTP, codecs, JCA, pre-existing protected data, cursor rotation, and null-navigation serialization; approved/packaged/served OpenAPI agree and negative fixtures catch private-field/schema drift.

- [ ] 6.9 Measure exact/prefix query plans and representative protected-data search costs, including dense/sparse/no-match prefix traversal and retained memory bounds.
  - Requirements: E6, E8, P6, P7, P9, P10; Design: Risks / Trade-offs.
  - Verification: record actual volumes, candidate/decrypt/batch counts, timings, CPU/pool pressure, index usage, maximum retained page/batch state, and deadline outcomes; explain linear exact-count work without inventing an SLA or silently capping results.

## 7. Consumer and Operational Handoff

- [ ] 7.1 Add an English search consumer guide with synthetic request examples, historical/normalization/filter semantics, complete exact results, prefix navigation, safe fields, and stable failures.
  - Requirements: E1, E3, E4, E5, E7, E8, P1, P3, P4, P5, P7, P8, P9, P10.
  - Verification: examples agree with the completed OpenAPI and actual contract tests; the guide explains UTF-16 units, omitted/null/empty differences, partial-prefix eligibility, and live cursor scope without publishing protected data.

- [ ] 7.2 Document Flyway rollout, retained encryption/signing keys, finite query/CPU settings, measured capacity, native verification prerequisites, and safe rollback.
  - Requirements: E8, P8, P10; Design: Migration / Rollout / Rollback.
  - Verification: operations documentation identifies the actual additive migration, key/version compatibility, configured limits and rejection behavior, current build baseline, measured trade-offs, and rollback that preserves protected history and applied migrations.

- [ ] 7.3 Record the service-local central-architecture handoff for historical search eligibility and the corresponding existing sequence location.
  - Requirements: E1, P1; Design: Central architecture alignment.
  - Verification: handoff evidence identifies the active-only sequence assumption and its required historical refinement while retaining the existing system relationships; the architecture-repository follow-up is explicitly identified within this service's documentation.

## 8. Final Quality and Completion Evidence

- [ ] 8.1 Run ./gradlew test after the final Java changes and resolve all affected unit, architecture, contract, migration, and reactive integration failures.
  - Requirements: all E/P requirements; Design: Testing Strategy.
  - Verification: record a successful full `./gradlew test` result for the final saved source revisions, including new search cases and existing behavior regressions; no relevant failing test is skipped to obtain success.

- [ ] 8.2 Run ./gradlew build and resolve compilation, packaging, and packaged JVM integration failures.
  - Requirements: E1, E7, E8, P1, P9, P10; proposal implementation validation constraints.
  - Verification: record a successful `./gradlew build`, actual packaged search HTTP results, and no new compiler warnings on the final implementation baseline.

- [ ] 8.3 Complete resolved-model JDTLS review for every affected production, unit, shared-contract, and integration Java file, including nested types.
  - Requirements: proposal Java quality constraints; Design: Java, SonarQube, and verification evidence.
  - Verification: final saved-file inventory has actual zero-problem diagnostics, responsibility Javadoc, and reconciled Java 25 Gradle/IDE results; any mismatch or nullability/type-safety warning is investigated and resolved without blanket suppression.

- [ ] 8.4 Complete actual SonarQube analysis and remediation for every final saved affected Java revision using the current resolved model and active rules.
  - Requirements: proposal SonarQube quality constraints; Design: Java, SonarQube, and verification evidence.
  - Verification: per-file evidence records analyzer/rules/parameters, Git blob, actual completion, findings/hotspots, fixes and reanalysis with no unresolved acceptance finding; neither incomplete-model zero counts nor historical/standalone results are presented as a server Quality Gate.

- [ ] 8.5 Run ./gradlew buildNative -Dquarkus.native.container-build=true with the configured Java 25 native builder and resolve search-related native compatibility failures.
  - Requirements: proposal native compatibility constraints; Design: Contract Tests.
  - Verification: record successful `./gradlew buildNative -Dquarkus.native.container-build=true` output and the actual native artifact; affected codecs, projections, JCA, resources, and reflection registrations build correctly.

- [ ] 8.6 Run ./gradlew testNative -Dquarkus.native.container-build=true against the actual native executable and resolve affected packaged/native contract failures.
  - Requirements: all E/P public contract requirements; proposal native validation constraints.
  - Verification: record successful `./gradlew testNative -Dquarkus.native.container-build=true` with actual search HTTP evidence, encrypted-history lookup, strict binding, protected cursors, status-consistent envelopes, null metadata, and sanitized failures.

- [ ] 8.7 Inspect both final resource signatures, DTO mapping/wrapping order, inward dependencies, reactive execution boundaries, and requirement-to-test coverage.
  - Requirements: all E/P requirements; Design: Architecture, REST boundary, Testing Strategy.
  - Verification: evidence maps every EARS scenario to passing checks or its documented conditional applicability, confirms shared DTO envelopes and global handling, and finds no Domain/entity/bare-response leakage, blocking wait, manual subscription, or unbounded session/CPU concurrency.

- [ ] 8.8 Run openspec validate complete-party-search-endpoints --type change --strict --no-interactive and reconcile the final task, source, diagnostics, test, performance, and handoff evidence.
  - Requirements: E1, P1; Design: Requirements Traceability, Java, SonarQube, and verification evidence.
  - Verification: strict validation succeeds, all checked tasks have actual completion evidence for current revisions, any post-verification code fix triggers the affected checks again, and implementation is declared complete only when required JVM/native/IDE/Sonar acceptance is satisfied.
