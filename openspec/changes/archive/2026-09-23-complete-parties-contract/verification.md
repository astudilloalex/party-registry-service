# Implementation Verification

## Baseline and Evidence Sources

- Change: `complete-parties-contract`.
- Base commit: `82f7efdbb2ed9d3ec96a5a54a45cc161efdbf52f`.
- Baseline date: 2026-09-19; timestamps below retain the IDE log's local time.
- The developer saved `PartyResource.java` with a Javadoc-only line-wrap change before implementation. Its saved Git blob is `07c4d298c0f5188623fbab3dbb5ee72ef1ae6d96`; this is the baseline file revision, not the unchanged HEAD blob.
- Evidence is read from the active Antigravity IDE installation under `~/.antigravity-ide` and `~/.config/Antigravity IDE`. The older `~/.antigravity` installation is not this window's evidence source.
- Sonar output rotated during task 4.7. Historical Sonar line references through task 4.6, and the two reader updates at 15:27/15:43, now refer to `SonarQube for IDE.1.log` in the same output directory. New task 4.7 configuration/test records are in `SonarQube for IDE.log`; timestamps identify entries across later rotations.

## Task 1.1: Standalone SonarQube for IDE

The developer supplied the analysis output, and the agent independently located the same output in the active IDE log.

| Item | Observed baseline |
|---|---|
| Extension | `sonarsource.sonarlint-vscode` 5.7.0, Linux x64; verified in its installed `package.json` |
| Mode | Standalone; no connected-mode binding or rule override in the active user or folder settings |
| Effective local rules | Extension defaults; the execution reports 571 Java rules and 30 secrets rules |
| Java source | 25, preview disabled |
| Project JDK | `/usr/lib/jvm/java-25-openjdk-amd64` |
| Project model | Analysis classpath includes Gradle-resolved dependencies and `bin/main`, `bin/test`, and `bin/integrationTest` |
| Analyzed file | `src/main/java/com/alexastudillo/partyregistry/api/resource/PartyResource.java` |
| Execution | 09:27:07.145 to 09:27:08.741; full Java analysis of 1/1 source files and completed text/secrets analysis |
| Result | 0 issues and 0 Security Hotspots; 1602 ms |

Source log: `~/.config/Antigravity IDE/logs/20260919T084521/window1/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log`, lines 260–290.

Relevant completion evidence:

```text
Configured Java source version (sonar.java.source): 25, preview features enabled (sonar.java.enablePreview): false
1/1 source file has been analyzed
Analysis detected 0 issues and 0 Security Hotspots in 1602ms
```

The execution also logs `No workDir in SonarLint`, then completes successfully. This analyzer runtime warning is recorded separately from source findings and Java diagnostics. The later IaC sensor's `There are no files to be analyzed for the Java language` message does not negate the preceding completed Java AST analysis. This is local analysis evidence, not a server Quality Gate or a claim of equivalent server-side coverage.

### Java Project Resolution

The active Red Hat Java extension is 1.55.0. Its workspace log records:

```text
2026-09-19 08:46:04.857 Initializing JDT Language Server (Standard)
Version: 1.59.0-SNAPSHOT (OSGi 1.59.0.202606241329)
2026-09-19 08:46:14.394 Importing Gradle project(s)
2026-09-19 08:46:20.831 Workspace initialized in 6780ms
2026-09-19 08:46:29.122 >> build jobs finished
```

Source: `~/.config/Antigravity IDE/User/workspaceStorage/fc73fbd3bb7d62718087c857851a4deb/redhat.java/jdt_ws/.metadata/.log`, lines 11185–11346. That workspace's `workspace.json` identifies this repository. The language server uses its bundled JRE 21.0.11; the resolved project and Sonar source level use Java 25. These are separate runtimes, not a project-language mismatch.

### Evidence Collection Convention

Maintain a saved-file inventory for every added or modified Java source under `src/main/java`, `src/test/java`, and `src/integrationTest/java`. For each file, record its Git blob identity, analysis timestamp, Sonar completion/results, separate JDTLS diagnostics, and focused test evidence. Reanalysis is required after changes to an analyzed file or the effective analyzer configuration.

Use the active IDE's open/save analysis or a supported batch analysis command. One consolidated log/report may cover all files; sending source code or one message per class is unnecessary. When direct logs are unavailable, the developer may provide equivalent logs/exported diagnostics for the same saved files. A missing completion record remains pending; an empty Problems panel or a successful Gradle command cannot substitute for it.

## Task 1.2: Java Diagnostics and Saved-File Inventory

The Standard-mode JDTLS workspace log above independently records:

```text
!ENTRY org.eclipse.jdt.ls.core 1 0 2026-09-19 09:27:09.380
!MESSAGE 0 problems reported for /PartyResource.java
!ENTRY org.eclipse.jdt.ls.core 1 0 2026-09-19 09:27:09.382
!MESSAGE Validated 1. Took 494 ms
```

This is an actual file-validation result, separate from Sonar output. Further validations at 09:27:24.047 and 09:29:05.247 also report zero problems. Relevant source lines are 11459–11496. Project import and build completion are recorded in the same Standard-mode workspace.

The existing folder setting `java.compile.nullAnalysis.mode` is `disabled`; it predates this change and is not evidence of automated null-analysis coverage. Review null/type safety explicitly and reconcile any actual JDTLS/Gradle disagreement. No analyzer rule, exclusion, or warning suppression was changed during preflight.

### Inventory

Record production (`main`), unit/Quarkus tests (`test`), and packaged integration tests (`integrationTest`) here as they change. `Pending` means that no matching saved-revision evidence has yet been obtained. Test entries must identify the executed suite/command and result; preflight alone does not establish behavioral correctness.

| File | Source set / origin | Saved Git blob | JDTLS evidence | Sonar evidence | Focused tests |
|---|---|---|---|---|---|
| `src/main/java/com/alexastudillo/partyregistry/api/resource/PartyResource.java` | main / developer's baseline Javadoc wrap | `07c4d298c0f5188623fbab3dbb5ee72ef1ae6d96` | 2026-09-19 09:27:09.380: 0 problems; validated | 2026-09-19 09:27:08.741: completed; 0 issues/hotspots | Not run for preflight; no agent-authored Java behavior change |
| `src/test/java/com/alexastudillo/partyregistry/api/mapper/SharedResponsePaginationTest.java` | test / task 1.3 | `82cb45b433ddc8914620a6bfaf47aff3993dea94` | 2026-09-19 09:38:49.057: 0 problems; validated | 2026-09-19 09:38:48.437: completed; 0 issues/hotspots | Focused Gradle suite: 5 tests, 0 failures/errors/skips |
| `src/test/java/com/alexastudillo/partyregistry/OpenApiContractTest.java` | test / tasks 1.4–1.5 | `221e8dd5c84ed3a384663c520eb7436b8cf8a0d2` | 2026-09-19 10:07:26.638: 0 problems; validated | 2026-09-19 10:07:25.595: completed; 0 issues/hotspots | Focused Gradle suite: 28 tests, 0 failures/errors/skips |
| `src/main/java/com/alexastudillo/partyregistry/domain/model/Party.java` | main / tasks 2.1–2.2 | `82ea1e13da98a23c70d9dc1680d96eb3c0a4e0ce` | 2026-09-19 10:18:27.190: 0 problems | 2026-09-19 10:17:48.355: completed; 0 issues/hotspots | Domain suite passes, including 44 lifecycle cases |
| `src/main/java/com/alexastudillo/partyregistry/domain/model/NaturalPerson.java` | main / tasks 2.1–2.2 | `202688b2ec4c4a2ac765c619fdfcfbd6b694d5b8` | 2026-09-19 10:18:27.263: 0 problems | 2026-09-19 10:17:48.355: completed; 0 issues/hotspots | Domain suite passes, including 44 lifecycle cases |
| `src/main/java/com/alexastudillo/partyregistry/domain/model/LegalEntity.java` | main / tasks 2.1–2.2 | `5cdf89b44b71b1f60b898dd598d7c2201275fc7d` | 2026-09-19 10:18:29.383: 0 problems | 2026-09-19 10:17:49.003: completed; 0 issues/hotspots | Domain suite passes, including 44 lifecycle cases |
| `src/test/java/com/alexastudillo/partyregistry/domain/model/PartyDisplayNameCorrectionTest.java` | test / task 2.1 | `e17aa7bb2465801408b62ce4af6afa40d5c3c248` | 2026-09-19 10:14:32.605: 0 problems | 2026-09-19 10:14:30.849: completed; 0 issues/hotspots | 33 cases; no failures/errors/skips |
| `src/main/java/com/alexastudillo/partyregistry/domain/error/DomainViolation.java` | main / task 2.2 | `77242b1244a728db09d07c4244700f93d62ab49b` | 2026-09-19 10:18:33.695: 0 problems | 2026-09-19 10:18:30.232: completed; 0 issues/hotspots | Domain suite passes |
| `src/test/java/com/alexastudillo/partyregistry/domain/model/PartyLifecycleTest.java` | test / task 2.2 | `2a514c56a2ec53ee258353a3913b795c55022b68` | 2026-09-19 10:23:33.725: 0 problems | 2026-09-19 10:23:31.989: completed; 0 issues/hotspots | 44 cases; no failures/errors/skips |
| `src/main/java/com/alexastudillo/partyregistry/domain/policy/PartyActivationEvidence.java` | main / task 2.3 | `bac6bc5280d3f733516aaf0275578a4353c4ea30` | 2026-09-19 10:28:08.733: 0 problems | 2026-09-19 10:28:05.339: completed; 0 issues/hotspots | Policy and activation regression suites pass |
| `src/main/java/com/alexastudillo/partyregistry/domain/policy/PartyActivationPolicy.java` | main / task 2.3 | `405304bc1c93675535704adcf8bdf63887976227` | 2026-09-19 10:28:08.701: 0 problems | 2026-09-19 10:28:07.235: completed; 0 issues/hotspots | 14 policy cases and activation regression suites pass |
| `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyActivationDecision.java` | main / task 2.3 | `b823a638e22817433fc2ae4b7f55e0680080bed1` | 2026-09-19 10:28:11.028: 0 problems | 2026-09-19 10:28:09.571: completed; 0 issues/hotspots | Activation regression suites pass |
| `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/HibernateReactivePartyActivationAdapter.java` | main / task 2.3 | `8982130865ea3036570c6bed771ff0a6f0097a75` | 2026-09-19 10:28:15.556: 0 problems | 2026-09-19 10:28:12.857: completed; 0 issues/hotspots | 8 database cases and 5 adapter unit cases pass |
| `src/test/java/com/alexastudillo/partyregistry/domain/policy/PartyActivationPolicyTest.java` | test / task 2.3 | `39fa6c76693b64157e0872a79ff49fa4550e55f2` | 2026-09-19 10:28:15.658: 0 problems | 2026-09-19 10:28:14.625: completed; 0 issues/hotspots | 14 cases pass |
| `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/ReactivePartyActivationAdapterTest.java` | test / task 2.3 | `56be1182ac253a04e9647d35b943ffc9e42413bc` | 2026-09-19 10:30:08.894: 0 problems | 2026-09-19 10:29:35.394: completed; 0 issues/hotspots | 8 database cases pass |
| `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PersistenceAdapterObservabilityTest.java` | test / task 2.3 | `413478ee0d3a47ebd45e3c76b47753d43e5e89ff` | 2026-09-19 10:30:08.746: 0 problems | 2026-09-19 10:29:35.833: completed; 0 issues/hotspots | 3 cases pass |
| `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/HibernateReactivePartyActivationAdapterTest.java` | test / task 2.3 | `f071973269d7dc7b24ba07ea5f4ebdb0429ec4ae` | 2026-09-19 10:42:22.430: 0 problems | 2026-09-19 10:42:20.653: completed; 0 issues/hotspots | 5 cases pass after final S1612 correction |

To reconcile the final inventory, compare tracked additions/modifications with the base commit and include untracked Java sources as well. Hash each saved file with `git hash-object <path>`. Match the file path and a post-save completion record from each analyzer. Replace stale rows after fixes; retain unresolved or unavailable evidence as pending. If logs rotate or the IDE uses another workspace, obtain the replacement log location or a consolidated developer export rather than assuming old results still apply.

The installed CLI is `/opt/antigravity-ide/bin/antigravity-ide` (not on this shell's PATH). Opening the saved test with `--reuse-window <absolute-file-path>` triggered both analyzers in the existing project window. This provides a working local collection path without requiring per-class messages from the developer. The test is correctly classified as `[test] [java]` with `sonar.java.test.libraries`; completion is in Sonar log lines 291–321 and JDTLS log lines 11498–11511.

## Task 1.3: Shared Pagination Characterization

`./gradlew dependencyInsight --dependency api-response-quarkus-errors --configuration testRuntimeClasspath` resolves `com.alexastudillo.libraries:api-response-quarkus-errors:1.0.0-SNAPSHOT`, Java 25 `runtimeElements`. The matching locally published artifacts have these SHA-256 identities:

| Artifact (all version `1.0.0-SNAPSHOT`) | SHA-256 |
|---|---|
| `api-response-quarkus-errors` | `20a74cf3bb715b51fa949db9310d698e62fc6baa1839502c1d5999e8d8ec1fa2` |
| `api-response-quarkus` | `15dcd67726b7983f5a0baae65d8e92590f86b91151f2e3ffc14c0dd55cc01996` |
| `api-response-core` | `525521ec6ae514f5db5f2637b3f00e7b843b0af76126b6073f6dc06a8f7f0b1a` |
| `api-response-contract` | `cc76e1036054bbfb1c7e0c99b483be33aeba13bab7169e52f6032eccb45fe9e8` |

`SharedResponsePaginationTest` uses the published response manager and its Jackson customizer in isolation to characterize the library baseline. It does not install the service's future pagination serializer or an HTTP endpoint.

Verified behavior:

- `paginatedHttp(List<T>, PaginationMetadata)` returns a typed HTTP 200 response with matching body status, `successful`, data, and flat pagination properties.
- Default serialization omits unavailable cursors, even on an empty page, but preserves zero counts and `data: []`. Task 7.5 must therefore add the specified metadata-conditional null-cursor serialization.
- Ordinary detail responses contain only `status`, `code`, and `data`; the unused subtype detail is omitted. Ordinary errors contain only `status` and `code`.
- `PaginationMetadata` accepts `Integer` counts. A checked `long` conversion preserves `Integer.MAX_VALUE` and rejects values above it before metadata construction. Task 7.4 must apply that checked conversion in the production mapper.
- The current shared boundary cannot express more than 2,147,483,647 matches. Never truncate, wrap, approximate, or replace exact counts with null; the design requires an explicit technical capacity failure and a compatible shared-library enhancement before supporting larger counts.

Focused command: `./gradlew test --tests com.alexastudillo.partyregistry.api.mapper.SharedResponsePaginationTest` — successful, five tests, no skips/failures/errors, no compiler warnings reported. Responsibility Javadoc and Month-based date fixtures were reviewed. The separate IDE results are recorded in the inventory above.

## Runtime Verification Log

- Initial `./gradlew test` failed because Testcontainers selected `unix:///var/run/docker.sock`, which is inaccessible to the current user. Database-dependent suites could not execute; this run is not a successful full verification. The five new unit tests also passed within that run.
- README lines 172–185 document rootless Podman and the `DOCKER_HOST` override. The existing `unix:///run/user/1000/podman/podman.sock` endpoint reports server version 5.4.2. The full-suite retry uses that documented endpoint; no socket permissions or application configuration are changed.
- After task 1.3, `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test` succeeded in 4m 31s. The same environment with `./gradlew build` succeeded in 2m 2s, including `quarkusIntTest`. These establish the pre-contract-edit baseline, not final acceptance of the pending feature.

## Task 1.4: Root Read OpenAPI

The source contract now declares all eight allowed list parameters, cardinality and invalid-input handling, exact enum and canonical literal name comparisons, inclusive instant bounds, stable unsigned tuple order, effective cursor scope, and live continuation behavior. List metadata is required, with non-null exact counts and explicit null cursor directions; empty and single-page examples conform to that shape. Both read successes echo Process-Id, and root retrieval uses the tenant-concealing Party-not-found response. Closed summary/detail schemas exclude unrelated collections and require the detail subtype matching the immutable Party type.

`DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.OpenApiContractTest` succeeded: 24 tests, zero skips/errors/failures and no OpenAPI parser messages. Four new checks cover query/header contracts, filtering/continuation semantics, exact metadata/null examples, and safe subtype representations. No new compiler warnings were reported. Sonar completion is in log lines 322–352, and JDTLS validation is in lines 11513–11526.

## Task 1.5: Root Mutation OpenAPI

Root PATCH now declares a closed, strictly typed body with required displayName, explicit missing/null versus blank behavior, normalized UTF-16 limits, context/body/path/version ordering, and absence/version/business precedence. Its dedicated responses distinguish 412 expected-version-mismatch from lifecycle 412 stale-party-version. Root lifecycle headers define optional exact-value key scope, effective Party-ID/version identity, syntax checks before replay, historical results across later changes/restarts, and conflicting/concurrent reuse behavior. Each action documents its transition/evidence rules, retained data, atomic outcome, and current correlation versus historical audit. Success examples cover both Party subtypes and all three resulting states. Existing registration schemas and request-equivalence rules are preserved.

The same focused OpenAPI command passed all 28 tests with no parser messages or new compiler warnings. Added assertions cover root PATCH binding/error semantics, lifecycle headers/replay, transition/safe success examples, and exact status/code-only business failures. The saved Java revision in the inventory was reanalyzed: Sonar log lines 353–383 report zero issues/hotspots; JDTLS log lines 11531–11541 report zero problems.

## Task 2.1: Domain Display-Name Correction

`Party.correctDisplayName` is implemented by both aggregate types, canonicalizing only the submitted label, enforcing the normalized UTF-16 limit, retaining all four lifecycle states and existing detail objects, updating audit values, and advancing exactly once through `PartyVersion.next()`. Restoration retains its historical code-point limit. English API responsibility/contract Javadoc and null/type handling were reviewed; all new valid date fixtures use Month constants.

With the documented Podman/test-port environment, `./gradlew test --tests com.alexastudillo.partyregistry.domain.model.PartyDisplayNameCorrectionTest --tests com.alexastudillo.partyregistry.domain.model.NaturalPersonTest --tests com.alexastudillo.partyregistry.domain.model.LegalEntityTest --tests com.alexastudillo.partyregistry.domain.model.LegalEntityDetailsUpdateTest` passed 77 cases (33 + 21 + 11 + 12), with no skips/failures/errors or new compiler warnings. Individual IDE file activation verified all four saved revisions; opening multiple paths in one CLI invocation analyzed only the active file, so batch-open alone is not accepted as per-file evidence.

Architectural reference: [Update Party sequence](file:///home/alex/Documents/Development/architecture/alex-astudillo-architecture/architectures/party-registry/party-registry-sequences.c4#L35), queried through the LikeC4 `default` project. The approved root PATCH override changes only the label and therefore requires no geographic lookup. The [lifecycle sequence](file:///home/alex/Documents/Development/architecture/alex-astudillo-architecture/architectures/party-registry/party-registry-sequences.c4#L62) preserves retained identity records and root optimistic concurrency.

## Task 2.2: Domain Lifecycle Matrix

Both Party types now expose explicit deactivation (ACTIVE only) and archival (DRAFT, ACTIVE, or INACTIVE), with dedicated transport-neutral violations for invalid states. Activation remains draft-only. Every accepted transition retains identity, historical labels and the original detail object, updates audit values, and calls the already overflow-checked `PartyVersion.next()` exactly once.

`./gradlew test --tests 'com.alexastudillo.partyregistry.domain.*'` passed with the documented environment. The 44 new lifecycle cases cover all 24 type/state/action combinations, valid maximum-version advancement, overflow rejection, preserved creation data, and nonregressing audit times. Sonar reported one S5778 issue in the initial audit-time failure test; the date calculation was moved outside the exception lambda. The focused 44-case suite passed again, and Sonar log lines 633–663 confirm zero findings on the corrected revision. JDTLS also reports zero problems. No rule or warning was suppressed.

## Task 2.3: Minimal Activation Evidence

`PartyActivationEvidence` replaces the full identifier/scheme wrapper with tenant, Party, referenced/resolved scheme IDs, verification status, optional expiration, and applicable subject type. The policy retains its precedence and exposes a Domain-owned draft-state check for the later Application workflow. The existing activation adapter now selects only these scalar fields; it no longer constructs full identifier/scheme aggregates or needs their persistence mappers. All callers and constructor tests are updated, and no source references to `PartyIdentifierEvidence` remain.

Policy tests cover same-day and null expiration, every nonverified status, other Party/tenant ownership, scheme identity mismatch, incompatible subject types, and non-gating primary/scheme lifecycle values. The projection allowlist excludes all protected identifier material. Existing reactive activation tests continue to pass, including both Party types, rollback, and separate-session concurrency. The changed date fixtures use Month-based construction.

The full regression run initially found an outdated adapter collaborator allowlist still requiring the removed full-identifier/scheme mappers. That assertion was updated to the minimal collaborator set. Full `./gradlew test` then passed in 1m 51s, followed by `./gradlew build` in 49s, including packaged integration tests, with the documented Podman/test-port environment. The affected adapter unit test also had Sonar S1612; replacing `field -> field.getType()` with `Field::getType` resolved it. Its five focused cases and both analyzers were rerun on the final revision. All affected files now have zero recorded findings/diagnostics.

JDTLS logged a transient document-close/file-not-found error when the obsolete evidence source was removed; subsequent validation of its replacement and every caller succeeded with zero problems, consistent with Gradle compilation. This is not counted as a passing source diagnostic by absence alone. Reference: [activation sequence](file:///home/alex/Documents/Development/architecture/alex-astudillo-architecture/architectures/party-registry/party-registry-sequences.c4#L113), LikeC4 `default` project; the approved design narrows the evidence projection to eligibility inputs.

## Task 3.1: Root Query Contracts

Typed read inputs/results and the two query/cursor ports now preserve the inward dependency boundary. The Application-owned name predicate canonicalizes exterior whitespace and root-locale case while matching punctuation literally; effective scope includes tenant, every filter, and limit. Positions retain exact instant precision and compare both UUID halves unsigned. Page collections are immutable and totals remain long-valued until the API boundary.

InvalidPartyCursor carries no client token. Its exception description, observation classification, and API bad-request translation are added together with the sealed-failure test inventory. The initial focused run caught the missing inventory entry; after adding it, all 132 selected tests passed:

```shell
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests 'com.alexastudillo.partyregistry.application.*' --tests com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslatorTest --tests 'com.alexastudillo.partyregistry.architecture.*'
```

The seven query cases cover literal AND filtering, Unicode expansion, accent/interior-whitespace preservation, effective scope equivalence/isolation, inclusive instant bounds, submillisecond ordering, unsigned UUID halves, immutable results, long totals, and token-free failures. Architecture tests pass without new framework or outward dependencies. All new types have responsibility Javadoc and all new valid date fixtures use Month constants. No new compiler warnings or suppressions were introduced.

### Additional Saved-File Inventory: Task 3.1

Each path below is relative to `src/<source set>/java/com/alexastudillo/partyregistry/`. All Sonar executions completed with zero issues/hotspots and all JDTLS validations reported zero problems. Times are local on 2026-09-19. The execution records occupy Sonar log lines 1036–1651 and JDTLS log lines 12132–12418.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `application/model/PartyNamePredicate.java` | `a1ea788668fe965d680cd1dc906778a6746089a1` | 10:50:11.695 | 10:50:13.099 |
| main | `application/model/PartySearchCriteria.java` | `67be2e48592474f84ae98f4f3c68f3d21565537a` | 10:50:13.965 | 10:50:15.373 |
| main | `application/model/PartySearchScope.java` | `fff17274cac54c239be253f7589a26b4a0303a3f` | 10:50:16.556 | 10:50:18.049 |
| main | `application/model/PartyPagePosition.java` | `609cb1541db3820385d3b24114e8b3ea2a108b7f` | 10:50:19.934 | 10:50:21.011 |
| main | `application/model/PartyPageBoundary.java` | `880dc80feff39f2581d92daad2cede1a8cc80c11` | 10:50:22.482 | 10:50:23.485 |
| main | `application/model/PartySummaryResult.java` | `713c754befa930c604cf283c6f615f98866bc4ec` | 10:50:24.534 | 10:50:25.777 |
| main | `application/model/PartyPageSlice.java` | `436ab021e85bf72416c8fa16e595018ecf0f8b5b` | 10:50:27.101 | 10:50:28.157 |
| main | `application/model/PartyPageResult.java` | `5f24bdb876ae00b2c02b4b5951afebb991a19307` | 10:50:28.974 | 10:50:30.318 |
| main | `application/query/GetPartyQuery.java` | `2bbdc725110ba020b8ae639e17e376fcabea77e9` | 10:50:31.522 | 10:50:32.588 |
| main | `application/query/ListPartiesQuery.java` | `73ce75e3092135b35e81a991466a91291cbfa35f` | 10:50:33.623 | 10:50:34.702 |
| main | `application/port/PartyQueryPort.java` | `979cb64df4ee16ff1bf02766ec43a4e7423fe908` | 10:50:35.576 | 10:50:36.631 |
| main | `application/port/PartyCursorPort.java` | `c044afbedda3c197b753b5f28bbfa2ce9f973f53` | 10:50:37.375 | 10:50:38.692 |
| main | `application/error/ApplicationFailure.java` | `598a98ab106a0a841f39e659df5e120364035c30` | 10:50:40.013 | 10:50:40.664 |
| main | `application/error/ApplicationException.java` | `8711dfa47fe178c13669c7fcc29377bd267b71d5` | 10:50:41.682 | 10:50:42.783 |
| main | `application/observability/OperationObservation.java` | `e0faf82629c980b4b3824dce84eccb94a737900e` | 10:50:43.503 | 10:50:50.076 |
| main | `api/error/PartyApiErrorTranslator.java` | `9cd13ec2e3d8a69d61a959f41f258ef3c3a14cfb` | 10:50:44.803 | 10:50:50.119 |
| test | `api/error/PartyApiErrorTranslatorTest.java` | `b491f31ad3181badcc9a59d6ae16ce36a3c74726` | 10:50:46.158 | 10:50:50.134 |
| test | `application/model/PartyQueryContractTest.java` | `6557dc2ec2bf91a0d8ba3cd6570660c2c2afb424` | 10:50:47.421 | 10:50:50.105 |
| test | `application/observability/OperationObservationTest.java` | `303d33b00f9b7ac887a3300be57acdfc8155b4ea` | 10:50:48.763 | 10:50:50.055 |
| test | `application/error/ApplicationExceptionTest.java` | `49a65c183bfbe4d69dfdba1cf9ad3e750ed058ed` | 10:52:00.946 | 10:52:02.284 |

## Task 3.2: Scoped Mutation Contracts

Root PATCH and lifecycle commands, action identity, applied/replayed disposition, and completed safe results are defined without transport or persistence types. Effective lifecycle identity includes action, Party ID, and expected version, excluding current actor/process. Keys retain their exact submitted representation. PATCH retains blank text for the Application workflow's version-before-business-validation ordering. Completed results reject inconsistent identity, version, action outcome, or subtype.

`PartyMutationPort` defines one tenant-bound atomic workflow whose success is emitted after commit. Its context exposes only sequential replay serialization, completed-result lookup, qualified root locking, minimal evidence, guarded root persistence, completion storage, and enabled event intent. Session lifetime, isolation, and concrete persistence remain tasks 5.2–5.5 rather than being implemented in these inner interfaces.

With the documented environment, `./gradlew test --tests com.alexastudillo.partyregistry.application.model.PartyMutationContractTest --tests 'com.alexastudillo.partyregistry.architecture.*'` passed five contract cases plus 13 architecture checks, with no failures/errors/skips or new compiler warnings. Both result subtypes retain historical audit values, and all inner dependencies/cycle checks pass.

The following additional inventory uses the same source-set/path convention as task 3.1. Each execution completed with zero Sonar issues/hotspots and zero JDTLS problems on 2026-09-19 (Sonar lines 1652–1930; JDTLS lines 12420–12553).

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `application/model/PartyLifecycleAction.java` | `4d676c3541baaa00ac08b95ce303da8a4660d253` | 11:00:13.935 | 11:00:14.981 |
| main | `application/model/PartyLifecycleRequest.java` | `d1a00e7165fc438ffe248a1074b7b6bcba433095` | 11:00:15.949 | 11:00:16.952 |
| main | `application/model/PartyMutationOutcome.java` | `ecce2d90062e892957463dd180f7f6f17c935b79` | 11:00:19.056 | 11:00:20.006 |
| main | `application/model/CompletedPartyLifecycle.java` | `1189379b212196f6a631f046a055d0c26392e58b` | 11:00:21.693 | 11:00:22.388 |
| main | `application/command/PatchPartyCommand.java` | `3812c81c8956bd964b3cb01769985d401a8aaba0` | 11:00:23.444 | 11:00:24.114 |
| main | `application/command/ChangePartyLifecycleCommand.java` | `a89d1ee2719288341d652dfbf8cd04854502442e` | 11:00:25.603 | 11:00:26.482 |
| main | `application/port/PartyMutationPort.java` | `fb41f74f1a1ad05f2f01a6aa976652ee1a400772` | 11:00:27.322 | 11:00:28.264 |
| main | `application/port/PartyMutationContext.java` | `d03b5e726af8a53e469b1040c8789379fdfd61da` | 11:00:29.345 | 11:00:30.282 |
| test | `application/model/PartyMutationContractTest.java` | `5a05cded01c344ccf92e06b9cd75fac15ded7f21` | 11:00:30.761 | 11:00:31.786 |

## Task 4.1: Listing Index Migration

The migration inventory was rechecked before adding V5. `V5__add_party_listing_order_index.sql` creates the nonunique B-tree index `ix_parties_tenant_created_id` on `(tenant_id, created_at DESC, id DESC)`. V1–V4 remain unchanged; the fixture regression test now also pins V4's SHA-256 (`01a3268e611b67e85de112e7e343ce28e3d66eb28cc13982b3cc2f07a3e08734`).

With the documented environment, `./gradlew test --tests com.alexastudillo.partyregistry.PartyListingMigrationTest --tests com.alexastudillo.partyregistry.MigrationRegressionTest --tests com.alexastudillo.partyregistry.IdentifierSchemeTestFixtureTest` passed 20 cases (2 + 14 + 4). Isolated PostgreSQL tests use only Flyway for schema creation/upgrade, verify exact index definition after clean installation and V4 upgrade, retain earlier applied checksums and historical Party data, and verify a repeated migration applies nothing.

Gradle passed while the initial JDTLS validation reported one problem in the new migration test. The container was then bound directly as the try-with-resources value before applying its startup-timeout setting, making ownership explicit. Both migration cases passed again, and final JDTLS validation reported zero problems. A transient Java test-discovery plugin exception appeared during the earlier IDE scan; subsequent document validation recovered. No diagnostics were suppressed or treated as successful merely because Gradle passed.

Additional inventory, with paths relative to `src/test/java/com/alexastudillo/partyregistry/`, on 2026-09-19. Every listed final execution reports zero Sonar findings and zero JDTLS problems:

| Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|
| `PartyListingMigrationTest.java` | `80719f50741eb0dafeb5f8173c6125b0316e93d9` | 11:08:51.880 | 11:09:09.115 |
| `MigrationRegressionTest.java` | `67b4e037e7caef9f1dcef0008d5522db3adcfe72` | 11:06:32.033 | 11:06:32.448 |
| `IdentifierSchemeTestFixtureTest.java` | `b20ff74d45907465cb8dc096f25c2e4e2627c228` | 11:06:33.656 | 11:06:34.480 |

## Task 4.2: Cursor Signing Configuration

`PartyPaginationConfiguration` maps the external current key ID and signing-key ring. Startup validates exact IDs (1–64 ASCII letters/digits/underscore/hyphen), Base64-encoded 32-byte HMAC keys, retained-key usability, and membership of the current ID. Decoded temporary arrays are cleared and the retained map is immutable. Invalid configuration produces a sanitized cryptographic configuration failure with no supplied values or underlying cause. Cursor material is independent of identifier protection and registration fingerprints.

Only `%test` supplies deterministic cursor material. Packaged JVM/native tests already select that isolated profile through `src/integrationTest/resources/application.properties`, so repeated launches can use the same test ring without adding a production fallback. Three key/configuration tests verify selection/retention, effective property names and repeatable construction, and missing/malformed/unusable configurations. Those tests plus four Quarkus global-error startup contracts pass. `./gradlew quarkusIntTest` also passed with the documented environment, validating packaged startup with the new configuration.

The following files (same source-set/path convention) completed analysis with zero Sonar findings and zero JDTLS problems on 2026-09-19:

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `infrastructure/security/PartyPaginationConfiguration.java` | `4f4f88ed2f779ef019be574ec761d370e0f0ad8d` | 11:15:38.075 | 11:15:38.993 |
| main | `infrastructure/security/PartyCursorKeyMaterial.java` | `f3dab4a65c25008d69002fb9f66f744137107762` | 11:15:39.869 | 11:15:40.555 |
| test | `infrastructure/security/PartyCursorKeyMaterialTest.java` | `33d7c83d9bbeb0c2582579854a3b114d1bc972b6` | 11:15:41.565 | 11:15:42.486 |

## Task 4.3: Authenticated Cursor Adapter

The cursor adapter uses a versioned header/key ID, fixed-size binary payload (direction, epoch seconds, nanoseconds, UUID bits, page size, scope digest), and canonical unpadded Base64url. HMAC-SHA-256 covers the version, key ID, and encoded payload with a dedicated context prefix. MAC and scope digest comparisons use `MessageDigest.isEqual`. Each operation creates its own MAC/digest instances; no mutable cryptographic engine is shared between requests. Decoding is bounded and fails through token-free InvalidPartyCursor without retaining parser exceptions. No expiry policy is introduced.

The scope digest includes tenant and every effective filter/limit in an unambiguous versioned encoding. Text uses length-prefixed UTF-16 units, avoiding replacement-based collisions for distinct unpaired surrogate input. Cursor positions retain full instant/UUID precision.

Eight cursor cases pass, covering both directions, malformed/blank/oversized/noncanonical tokens, payload/MAC tampering, unknown versions/keys, every scope dimension, normalized name/offset equivalence, Unicode scope distinction, key rotation/new instances, and 40 bounded concurrent round trips. The cursor/key/architecture command passed 24 cases. Sonar reported two S2259 findings for repeated nullable enum accessor calls; storing each value in a local before its null check resolved both, and all eight cursor cases passed again. No suppression or rule change was used.

Final additional inventory (same source-set/path convention), with zero Sonar findings and zero JDTLS problems on 2026-09-19:

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `infrastructure/security/HmacPartyCursorAdapter.java` | `8e852e164fc19c42832b501372172cca39378522` | 13:57:43.415 | 13:57:42.728 |
| test | `infrastructure/security/HmacPartyCursorAdapterTest.java` | `61b9f10351c070d15a14435de8747c1b10ab03a4` | 11:24:08.108 | 11:24:08.862 |

## Task 4.4: Qualified Root/Detail Reading

`PartyRootReader` joins the tenant-qualified root with both possible detail rows in one statement, restores only the matching Domain subtype, and returns its safe Application projection. It distinguishes absent roots from missing/incompatible details and translates corrupt state to an internal persistence failure. No identifier query, decryption, or geographic collaborator is involved. Its existing-session primitive can be reused for later mutation reloads without opening a second session.

`./gradlew test --tests com.alexastudillo.partyregistry.infrastructure.persistence.PartyRootReaderTest` passed with the documented environment. Three reactive tests cover all eight type/state combinations, exact historical text/audit preservation, equal missing/cross-tenant absence, and rejection of a deliberately incomplete root without partial success. All fixtures use Month-based dates and finite waits. No new compiler warnings were reported.

Final additional inventory (same source-set/path convention), with zero Sonar findings and zero JDTLS problems on 2026-09-19:

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `infrastructure/persistence/PartyRootReader.java` | `27c30a134dc211609ccce121d80d9d008e91a9ec` | 14:04:43.050 | 14:04:42.785 |
| test | `infrastructure/persistence/PartyRootReaderTest.java` | `c7093d3f24e99697c8681b4fa8bfed7b443d1b4b` | 14:04:46.932 | 14:04:46.385 |

## Task 4.5: Structural-Filter Snapshot Pages

`PartyPageSql` binds tenant/type/status/time predicates and cursor tuples, selects only the six summary fields, and uses PostgreSQL's unsigned UUID ordering. Submicrosecond time filters ceil the inclusive lower bound and floor the inclusive upper bound to stored microseconds, preserving comparison semantics rather than relying on driver rounding. `PartyPageReader` establishes `REPEATABLE READ, READ ONLY` before its count/page/neighbor statements and executes them sequentially. Empty continuations retain navigation relative to their authenticated boundary; absent matching datasets have zero totals and no neighbors. The canonical name traversal path remains task 4.6.

With the documented environment, `./gradlew test --tests com.alexastudillo.partyregistry.infrastructure.persistence.PartyPageReaderTest` passed three database cases covering first/middle/final/empty pages, previous navigation, timestamp ties across the UUID sign boundary, filter intersection, exact inclusive time bounds, other-tenant exclusion, boundaries whose rows no longer match, and current empty continuation metadata.

Sonar S1612 was resolved with `Objects::nonNull`. Two S5411 findings were resolved by explicitly requiring non-null neighbor results and converting them to primitives before conditional selection; unexpected nulls are not silently treated as unavailable navigation. The database tests passed again, with no new compiler warnings or suppressions.

Final additional inventory (same source-set/path convention), with zero Sonar findings and zero JDTLS problems on 2026-09-19:

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `infrastructure/persistence/PartyPageSql.java` | `ff4e9b438c0a1b403cb0539360cfbe73e5011311` | 14:28:59.167 | 14:28:58.699 |
| main | `infrastructure/persistence/PartyPageReader.java` | `a55bd62a22e4829317eb2305f479bdf2ad3a4770` | 14:29:00.098 | 14:28:58.986 |
| test | `infrastructure/persistence/PartyPageReaderTest.java` | `11f7fdbd90da9d298aebe25b56d81b13bc85b267` | 14:21:38.495 | 14:21:38.395 |

## Task 4.6: Bounded Canonical Name Traversal

The earlier `PartyPageReader` has become `HibernateReactivePartyQueryAdapter`, implementing the complete query port and sharing `PartyRootReader` for details. Its name-filter path traverses structural-filter-qualified scalar summaries in sequential descending keyset batches of at most 256. It invokes the Application-owned predicate, counts every match, and retains only the requested page plus scan/match edge positions. Previous navigation uses a bounded deque to retain the nearest larger matching tuples. All statements remain inside the same explicit read-only repeatable-read transaction; no whole-tenant collection is materialized.

Three accumulator tests cover 10,000 matches in 100 batches while retaining at most three requested rows, nearest previous-page selection, and empty results/continuations. A PostgreSQL test seeds 300 historical rows, combines type/status/time and canonical prefix/contains filters, verifies 150 exact matches, preserves stored case/spacing, exercises next/previous navigation, and confirms accents and wildcard-looking characters are literal. The grouped name/accumulator/structural/root command passes all ten cases; the structural suite also passes through injection of the query port.

The first grouped execution timed out in the name test and then in subsequent database cases. The fixture now gives a distinct diagnostic if its setup transaction times out. Isolated root/structural reads, the isolated name test, and a fresh complete ten-case group subsequently passed without increasing deadlines, reducing data volume, or changing the traversal algorithm. The cause of that initial timeout was not established; deterministic timeout/cancellation cleanup verification remains explicitly required by task 4.7 and final acceptance.

An early Sonar scan of the structural regression test ran before the renamed adapter type was resolved; that scan is not used as final evidence. The test now targets PartyQueryPort and was reanalyzed after compilation without unresolved-import warnings. Final additional inventory (same source-set/path convention) has zero Sonar findings and zero JDTLS problems on 2026-09-19. The new adapter row supersedes the earlier PartyPageReader path for the live file inventory.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `infrastructure/persistence/HibernateReactivePartyQueryAdapter.java` | `62e62006eeea8d290978961ef4d07ef5920a4822` | 14:45:36.011 | 14:45:36.388 |
| main | `infrastructure/persistence/PartyNamePageAccumulator.java` | `c84bad297240fd4667e54ab981fd1afc24ab665b` | 14:45:37.667 | 14:45:38.072 |
| test | `infrastructure/persistence/PartyNameQueryTest.java` | `688319b5ba5e997c52ae9664b354f3dc5ebe7f5b` | 14:53:07.536 | 14:53:08.143 |
| test | `infrastructure/persistence/PartyNamePageAccumulatorTest.java` | `2a59783af50abad67fc2bc57e6426f6f2a758204` | 14:45:41.913 | 14:45:42.414 |
| test | `infrastructure/persistence/PartyPageReaderTest.java` | `a5069981c23b7ebc1d695fdb5bdf0f1fe403dbf1` | 14:56:31.371 | 14:56:31.889 |

## Task 4.7: Snapshot Consistency, Deadlines, and Cleanup

Root reads now configure transaction-local PostgreSQL statement deadlines and a Mutiny traversal budget inside their transactions. Defaults are 5 seconds per statement and 15 seconds for traversal, configurable through `PARTY_QUERY_STATEMENT_TIMEOUT` and `PARTY_QUERY_TRAVERSAL_TIMEOUT`. Startup rejects disabled/negative or unsupported PostgreSQL statement values. Detail reads also use a bounded read-only transaction; the existing-session root primitive remains usable by later write transactions.

Five deterministic database tests verify:

- A separate session commits an insert between the count and page/neighbor statements, while the response retains its original snapshot and a later request sees the new row.
- A separate session commits a matching row between name-scan batches, while the first traversal's exact count remains unchanged.
- Actual PostgreSQL settings report repeatable-read, read-only, and a positive statement timeout.
- A deliberately slow test-only SQL statement is interrupted by PostgreSQL with SQLSTATE 57014; the resulting failure contains no partial page and the transaction is released.
- A gated traversal times out and closes its real session; caller cancellation emits no page and releases the snapshot. Three sequential cancellations with a pool of two connections prove pooled connections remain reusable. PostgreSQL inspection confirms no lingering transaction and reset transaction-local settings.

The test probe wraps real sessions/queries using checked reflection and `Uni<?>` adaptation without unchecked casts, production test hooks, blocking event-loop waits, or schema changes. Two budget unit tests also pass. The original timeout observation from task 4.6 did not recur in the final full regression; its initial cause remains unproven rather than attributed speculatively.

### Framework Cancellation Diagnostic

Hibernate Reactive 3.2.13.Final emits `HR000090` through Mutiny's dropped-exception logger when cancellation closes a live transaction. Bytecode inspection of the resolved dependency confirms this fallback rolls back, still closes/releases the underlying SQL connection, and then reports the diagnostic. The database/session and small-pool reuse assertions above verify those outcomes. This runtime diagnostic remains visible; no handler, logger filter, warning suppression, dependency replacement, or fabricated success was added to hide it. It is separate from compiler/IDE warnings and Sonar findings.

### Reference Volume and Regression

An instrumented local PostgreSQL 18.4/Podman reference run scans 2,000 qualified summaries with 25% name matches and a requested page size of 50. It reports exactly 500 matches, eight nonempty batches, and a maximum batch of 256 rows. Measured query duration was 562 ms in the focused run and 49 ms in the warmed full suite. The separate 10,000-match accumulator test confirms retained page state stays at the requested limit. These are recorded reference-volume observations, not a production throughput guarantee.

With the documented Podman/test-port environment, full `./gradlew test` passed all 696 cases with zero failures and zero skips in 2m 23s, followed by successful `./gradlew build` in 50s, including packaged integration tests. No new compiler warnings were reported.

Final additional inventory (same source-set/path convention), with zero Sonar findings and zero JDTLS problems on 2026-09-19:

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `infrastructure/persistence/PartyQueryConfiguration.java` | `611afc9a64d0104b1b59770004b80061d9447c2e` | 15:51:02.435 | 15:51:03.117 |
| main | `infrastructure/persistence/PartyQueryTimeouts.java` | `fd97b3e9a7346491aaa00c09f83f1ef149abcf7f` | 15:51:03.983 | 15:51:04.669 |
| main | `infrastructure/persistence/HibernateReactivePartyQueryAdapter.java` | `7ff979966bc23075ec2a85176da8784ebf6b1fc5` | 15:27:18.466 | 15:51:06.209 |
| main | `infrastructure/persistence/PartyRootReader.java` | `ff54502992cee7a2d14e0c5f9baf08772c9ef090` | 15:43:25.285 | 15:51:07.678 |
| test | `infrastructure/persistence/PartyQueryTimeoutsTest.java` | `ffed8eddd9d08fd4383730381344be47619187a3` | 15:51:08.356 | 15:51:09.055 |
| test | `infrastructure/persistence/PartyQuerySessionProbe.java` | `77d1647eeffd2edaccfdc9c387f161ad123ac556` | 15:51:09.989 | 15:51:10.613 |
| test | `infrastructure/persistence/PartyQuerySnapshotTest.java` | `1358dc35f1dfd3097727037071c18a80130f54b9` | 15:51:11.791 | 15:51:12.267 |
| test | `infrastructure/persistence/PartyQueryVolumeTest.java` | `91412a2b3f987c059cd4f913decbd9320473f3a3` | 15:51:13.532 | 15:51:14.045 |

## Task 5.1: Lifecycle Snapshot Schema Three

Lifecycle snapshots now store an allowlisted schema-three request and original safe Party result. The codec validates schema column/payload agreement, exact field sets, canonical identity, tenant/action/row identity, expected-to-accepted version, subtype/status consistency, historical Domain restoration invariants, and the saved request fingerprint. Invalid snapshots fail internally with a fixed diagnostic and do not retain parser exceptions containing payload values. Both Party subtypes and all actions preserve original text, audit, nullable fields, and accepted versions.

The versioned SHA-256 input contains the tenant UUID, explicit stable action discriminator, Party UUID, and expected long version, under a dedicated context prefix. It excludes keys, actors, processes, and registration secrets. An independently calculated binary-encoding fixture pins the digest. Only the existing safe Party encode/decode helpers were made package-visible; registration schemas one/two and their encoding statements remain unchanged.

Seven lifecycle codec cases and six existing registration codec cases pass, covering round trips, malformed schemas/types/identity/audit, unexpected/protected fields, mismatched hashes/scopes, invalid result rejection before storage, and legacy-reader separation. The existing reactive idempotency snapshot persistence suite also passed. Repeated field literals were consolidated into constants and final Sonar/JDTLS checks are clean.

Final additional inventory (same source-set/path convention), with zero Sonar findings and zero JDTLS problems on 2026-09-19:

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `infrastructure/persistence/IdempotencyResultSnapshotCodec.java` | `eb7dcda3a14d859094c0bcf0c6df732aff618f93` | 16:15:21.231 | 16:15:21.038 |
| main | `infrastructure/persistence/PartyLifecycleFingerprint.java` | `7002d9847363346a4076608d0d2261cf16c60509` | 16:15:22.198 | 16:15:22.674 |
| main | `infrastructure/persistence/PartyLifecycleSnapshotCodec.java` | `dea46e1173c9b5c6f278db8c410d4fe6e46fe3b2` | 16:18:56.536 | 16:18:56.983 |
| test | `infrastructure/persistence/PartyLifecycleSnapshotCodecTest.java` | `98cc7b87063f5f54a47eafe206642b81871a327c` | 16:17:17.315 | 16:18:03.759 |

## Task 5.2: Serialized Full-Scope Lifecycle Replay Storage

`PartyLifecycleIdempotencyPersistence` acquires a transaction-scoped PostgreSQL advisory lock from a dedicated, versioned tenant/operation/exact-key encoding. The complete composite primary key remains the lookup and uniqueness identity; the lock hash never replaces it. Lifecycle operations use distinct `party.activate.v1`, `party.deactivate.v1`, and `party.archive.v1` names. Stored completions retain the original result and validate snapshot/hash/row identity before returning typed results. Entity changes only expose existing persisted identity fields to this helper.

Five reactive database tests pass against the Flyway-managed schema. Separate sessions coordinate using completion signals and actual granted/waiting rows in `pg_locks`, rather than assuming a timed delay establishes contention. The waiting equivalent scope reads the winner's committed snapshot. Other scopes acquire independent locks while the first is held. A constant lock hash forces collisions while full tenant/action/exact-key lookups remain distinct, including case, surrounding whitespace, and a 128-code-point supplementary-character key. Bypassing the advisory helper still triggers `pk_api_idempotency_records` (expected SQLSTATE 23505), leaving the original completion intact. A failure after snapshot flush rolls back and leaves the key reusable.

Focused lifecycle persistence (5), lifecycle codec (7), and legacy reactive snapshot (1) cases pass. Full `./gradlew test` passes 708 cases with zero failures/skips, followed by successful `./gradlew build` including `quarkusIntTest`, using the documented Podman socket and ephemeral HTTP test port. The initial full-suite invocation was interrupted by the shell's 120-second limit; rerunning with a 600-second command budget completed in 2m27s, then build in 51s. No application/test deadline was widened. Existing expected rejection, cancellation, and duplicate-gauge runtime diagnostics remain visible.

The developer supplied the two Sonar S6213 findings for use of `record` as an identifier. Renaming both variables to `entry` resolved them without suppression. An intermediate edit produced a parse-error analysis; its zero issue total is not accepted as evidence. The final analysis at 16:43:53.604 completed Java AST scanning without parse errors, and JDTLS separately reported zero problems.

Final additional inventory (same source-set/path convention), with zero Sonar findings and zero JDTLS problems on 2026-09-19:

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `infrastructure/persistence/ApiIdempotencyRecordEntity.java` | `0a7e612cfc73471d351eff28c27cc84e3e3a5620` | 16:34:44.414 | 16:34:44.821 |
| main | `infrastructure/persistence/ApiIdempotencyRecordId.java` | `f57f88cab049c065a871eab0743b19a717ecf97d` | 16:34:46.223 | 16:34:46.515 |
| main | `infrastructure/persistence/PartyLifecycleIdempotencyPersistence.java` | `5749b45c224b70ea190a1c5a96514526393c6556` | 16:43:53.604 | 16:43:54.134 |
| test | `infrastructure/persistence/PartyLifecycleIdempotencyPersistenceTest.java` | `f7456d7ca6b9aa3a671960dc8ccbcf6daa43fdd8` | 16:34:50.818 | 16:34:51.109 |

## Task 5.3: Qualified Root Mutation Primitives

`PartyRootMutationPersistence` locks the tenant-qualified root, restores detached safe Domain data, projects only the seven activation-evidence inputs, and conditionally writes only the label or lifecycle status plus update audit and the exact candidate version. It never dirties a managed root alongside the bulk statement. Clearing managed state immediately after that statement and reloading both subtypes verifies that the persisted result equals the candidate, including microsecond audit precision. Candidate guards reject scope/detail/creation changes, simultaneous label/status changes, non-next versions, and submicrosecond update times.

A zero-row conditional write is diagnosed using a separate successful tenant-qualified version query, producing generic `ExpectedVersionMismatch` or concealed `PartyNotFound` input for Application. The lifecycle workflow must translate the generic persistence conflict to its lifecycle-specific failure. No query recovery continues after a database exception. Missing/inconsistent detail structures are internal persistence failures.

Seven reactive tests pass, covering both types in all correction states, all supported transitions, an identical second correction at the same clock instant, original historical fields, exact JSON comparison of retained detail/audit/identifier rows, minimal evidence ownership, missing/cross-tenant rows, rejected candidate corruption, and real row-lock contention observed via PostgreSQL NOWAIT (expected SQLSTATE 55P03). Post-commit reads prove no second dirty-checking version increment. Three existing safe-root reader regressions also pass. An initial Optional type-inference compilation error was resolved explicitly; final Gradle and JDTLS results agree with no suppression.

Full `./gradlew test` passes 715 tests with zero failures/skips; `./gradlew build` including packaged integration passes. Both use the documented runtime overrides. Final Sonar analysis after the generic fix reports no preview-feature warning, zero issues/hotspots, and completed AST analysis; JDTLS separately reports zero problems.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `infrastructure/persistence/PartyRootMutationPersistence.java` | `15294d7d0d9d78aa97c4cbbec5be0c79027d28ef` | 16:55:19.183 | 16:55:19.658 |
| test | `infrastructure/persistence/PartyRootMutationPersistenceTest.java` | `d43ef0f54cf7379c3c51ceac80dee99796cb5e33` | 16:54:59.995 | 16:55:00.189 |

## Task 5.4: Safe Root Change Events

The sealed outbox candidate family now includes `PartyChangedOutboxCandidate` with a closed `Kind` for `party.updated.v1`, `party.deactivated.v1`, and `party.archived.v1`. Resulting-state and positive-version invariants are explicit; correction events support every retained status. The persistence mapper and broker serializer allow only the two safe payload keys, `partyType` and `status`, for these events. Storage retains the original actor, correlation, timestamp, tenant, Party ID, and accepted version. Existing creation/identifier/activation formats and publisher envelope remain compatible.

`PartyRootOutboxPersistence` stores validated root change intent in the caller's session only when the existing mode enables storage. Neither it nor the root mutation helpers have a broker dependency. Eleven new parameterized cases prove exact round trips for both types and all applicable states, immutable original metadata, rejection of unknown/protected payload fields, unknown schemas/zero versions/inconsistent statuses, and disabled/stored-only/published storage behavior. A new publisher test proves the three new event kinds are sent and acknowledged only in published mode. Existing mapping, serializer, publication/retry/cancellation, and mode regressions pass.

The first new storage-mode test incorrectly assumed a Mockito dependency. It was replaced with the project's narrow JDK-proxy test-double convention, without adding dependencies or suppressions; final compilation and JDTLS agree. The first broad outbox run exceeded a 180-second shell budget, so it supplies no completion evidence. The explicit focused suites pass; subsequent full `./gradlew test` and `./gradlew build` with the documented runtime overrides both pass, with 727 tests and zero failures/skips in the final report plus packaged integration. Repeated full checks used a larger command budget, without changing application/test deadlines.

Sonar findings in changed existing files were resolved by extracting the repeated aggregate-name constant and constructing invalid fixture inputs outside exception assertion lambdas. Final AST analyses complete with zero issues/hotspots. A transient Java test-discovery plugin NPE was logged during initial editing; subsequent file validations complete with zero source problems, independently of Gradle and Sonar.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `application/model/PartyChangedOutboxCandidate.java` | `dffdc1e36c17241f2d81215cc2933e29800346d3` | 17:13:05.303 | 17:13:02.405 |
| main | `application/model/OutboxEventCandidate.java` | `bdda216ddd2b6b4dfefef7c9e163cc3e373a7ce0` | 17:13:09.423 | 17:13:06.690 |
| main | `infrastructure/persistence/PartyOutboxEventPersistenceMapper.java` | `ade11512074c9bc3445eb307b7974d516c2e2838` | 17:13:13.630 | 17:13:10.798 |
| main | `infrastructure/messaging/OutboxMessageSerializer.java` | `18652252619d2b9026ff9fd37024bad9887e183f` | 17:18:25.132 | 17:19:04.680 |
| main | `infrastructure/persistence/PartyRootOutboxPersistence.java` | `166e337f31c7d4b4dd401d3724669e3f3b3ca801` | 17:13:31.247 | 17:13:29.020 |
| test | `infrastructure/messaging/OutboxMessageSerializerTest.java` | `bfff242a43ae132b4c998373e673ddc510db9dcc` | 17:18:27.392 | 17:19:07.586 |
| test | `infrastructure/persistence/PartyRootOutboxPersistenceTest.java` | `675ac04ffc6b25782fa95d370519909ecc7c1fb6` | 17:14:18.985 | 17:14:51.978 |
| test | `infrastructure/messaging/PartyOutboxPublisherTest.java` | `e3bcd042e9a799e34c99852eab39d69dcdfb885e` | 17:18:27.392 | 17:19:10.837 |

## Task 5.5: Scoped Reactive Mutation Transaction

`HibernateReactivePartyMutationAdapter` opens a fresh session/transaction for each subscription, configures local `READ COMMITTED, READ WRITE` and finite lock/statement deadlines before Application work, validates the returned storage outcome, flushes pending rows, closes the capability scope, and exposes success only after commit/session cleanup. The feature-specific context binds one trusted tenant and Vert.x context, prevents concurrent use and key-after-root lock acquisition, requires replay resolution before a keyed root lock, and rejects any later use after failure/closure. Stored snapshot/event metadata must match the accepted root and trusted request. Known Application/Domain failures and cancellation retain identity; technical failures are translated outside the transaction, with no recovery on its failed session.

Defaults are `PARTY_MUTATION_LOCK_TIMEOUT:5s`, `PARTY_MUTATION_STATEMENT_TIMEOUT:10s`, and `PARTY_MUTATION_OPERATION_TIMEOUT:15s`. The last budget wraps configured transaction work through flush. Configuration tests reject disabled, negative, submillisecond SQL, and PostgreSQL-unrepresentable limits. Settings are transaction-local; tests verify subsequent pooled use sees the original defaults.

Ten reactive cases prove committed root/snapshot/event visibility before result emission, original-audit replay with a new actor, closed-scope rejection, fixed key/read/root ordering, failed-scope and concurrent-session rejection, identical known failure propagation, rollback/key reuse, actual equivalent-key contention observed in `pg_locks`, server lock timeout (55P03), work timeout, and downstream cancellation. A canceled write leaves no root/snapshot/event changes, closes its real session, and permits the next request to acquire the same key/root successfully. The known Hibernate Reactive rollback-on-close diagnostic remains visible during cancellation; it is not hidden or substituted for the state/lock cleanup assertions.

Two initial generic compilation errors were fixed without casts/suppressions. Focused tests then pass (10 adapter + 2 configuration). Full `./gradlew test` reports 739 tests, zero failures/skips, and `./gradlew build` including `quarkusIntTest` passes with the documented runtime overrides. Final analysis completes with zero Sonar findings and separate zero JDTLS problems for each of the six files.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `infrastructure/persistence/PartyMutationConfiguration.java` | `2efd1d51c8afb932bc5f955ad06447cfa91c38a4` | 17:43:09.522 | 17:43:09.854 |
| main | `infrastructure/persistence/PartyMutationTimeouts.java` | `41cf9985d03fed8afbedbcf42bf0a9048d93d568` | 17:43:11.018 | 17:43:11.264 |
| main | `infrastructure/persistence/HibernateReactivePartyMutationAdapter.java` | `ce2d595c79f24a8aa6d3f9eee89455d7cdcba0ba` | 17:43:12.421 | 17:43:12.952 |
| main | `infrastructure/persistence/HibernateReactivePartyMutationContext.java` | `62a58a78a613957f765c6f8ae6005baeadd51122` | 17:43:30.757 | 17:46:00.726 |
| test | `infrastructure/persistence/HibernateReactivePartyMutationAdapterTest.java` | `a33b102fb429cc331546817c6407d7e7b0270438` | 17:45:42.034 | 17:46:02.437 |
| test | `infrastructure/persistence/PartyMutationTimeoutsTest.java` | `dd9f7016ba3d65f913d91fd1a1ec0c219b682d4f` | 17:46:03.581 | 17:46:04.060 |

## Task 6.1: Root Read Workflows

`GetPartyUseCase` maps qualified absence to `PartyNotFound` while returning the already-safe current detail projection unchanged. `ListPartiesUseCase` decodes an optional cursor against the effective tenant/filter/limit scope before persistence, requests one consistent slice, and encodes only its reported directional boundaries. It preserves empty-continuation navigation and exact long totals; `Math.ceilDiv` avoids overflow when deriving total pages. Both pipelines are lazy and require only the designated read/cursor ports.

Eight port-double cases cover both types in every state, historical values, qualified absence, lazy cursor-before-data validation, exact decoded/encoded scope and direction, empty initial/continuation pages, the long count boundary, and unchanged failure/cancellation identity. The first cancellation assertion exposed the test helper's documented behavior: `UniAssertSubscriber.getFailure()` returns null for `CancellationException`. Inspecting its implementation confirmed this; collecting the raw failure through `awaitFailure(Consumer, Duration)` verifies identity without changing production handling. The corrected eight tests and 13 architecture checks pass.

Full `./gradlew test` and `./gradlew build` including packaged integration pass with the documented runtime overrides; the final report contains 747 tests, zero failures/skips. All three saved files have completed AST analysis with zero Sonar findings and separate zero JDTLS problems.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `application/usecase/GetPartyUseCase.java` | `d3b4d831a24f64b26d97916c460c4137d1b363fb` | 17:54:37.340 | 17:54:37.825 |
| main | `application/usecase/ListPartiesUseCase.java` | `01f952f24ec11e2305c8c0607a9c5fce1793dab4` | 17:54:38.761 | 17:54:39.244 |
| test | `application/usecase/PartyReadUseCasesTest.java` | `0e641a45da8bd1af1629264a3c212b6bc5375d68` | 17:56:43.589 | 17:56:43.941 |

## Task 6.2: Application-Owned Root Correction

`PatchPartyUseCase` owns qualified absence, expected-version, and Domain label validation in that order inside the mutation scope. It reads its injected clock once after the root/version check, truncates the accepted instant to microseconds, stores the Domain candidate, and appends a safe update-event intent before returning an applied result. It never uses lifecycle replay capabilities. Only recognized label violations become typed business failures; overflow and other internal invariants remain technical failures rather than invalid request headers.

Six port-double tests cover both subtypes and all states, historical detail/creation preservation, precise clock capture and event metadata, absence-before-stale-before-blank behavior, identical canonical writes, Domain length protection, internal overflow, and unchanged storage failure/cancellation identity without event intent. The reusable test stub records only inner-capability choreography; real atomicity remains verified by the persistence suites. Six focused cases and 13 architecture checks pass, followed by successful full `test` (753 cases, zero failures/skips) and `build` including packaged integration with the documented runtime overrides. All three files have zero final Sonar findings and separate zero JDTLS problems.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `application/usecase/PatchPartyUseCase.java` | `3dd4073c50e505e5ccee7c7f9a4d772dba7d088b` | 18:09:01.462 | 18:09:01.606 |
| test | `application/usecase/PartyMutationPortStub.java` | `f5482aa9568a75b31cfc5538184d6b697169ad2e` | 18:09:02.146 | 18:09:02.586 |
| test | `application/usecase/PatchPartyUseCaseTest.java` | `a9dd68ea82472ade228288a8f2a0f9abd9f56a11` | 18:09:04.003 | 18:09:04.320 |

## Task 6.3: Shared Lifecycle Workflow

`ChangePartyLifecycleUseCase` resolves a serialized optional key before any current-root access. It compares typed effective requests, preserving original result/audit on replay and rejecting conflicting target/version input without disclosure. New work checks qualified absence and version before Domain transition/evidence rules, captures one microsecond operation instant, derives the activation date in UTC independently of the injected clock's zone, and stores the original success and enabled safe event intent. Unkeyed requests never access replay storage. Only a typed expected-version failure from the guarded root write is translated to lifecycle `StalePartyVersion`; other known failures and cancellation retain identity.

Eight workflow tests cover all 24 type/state/action combinations, original results after later archival/correction with a different retry actor/process, early key conflict, absence/stale precedence, draft-only evidence access, UTC expiration despite a non-UTC clock zone, failed-key reuse, unkeyed repeat behavior, precise audit/event metadata, and internal version overflow. Focused lifecycle/correction and architecture checks pass. The developer supplied Sonar S6878 for the write-conflict record; a record pattern now binds both versions directly, with zero final findings after reanalysis. Full `test` (761 cases, zero failures/skips) and `build` including packaged integration pass after that correction using the documented runtime overrides.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `application/usecase/ChangePartyLifecycleUseCase.java` | `bd266d9566a1b25bb00061820c4ae9def807cb4d` | 18:30:46.264 | 18:30:46.797 |
| test | `application/usecase/ChangePartyLifecycleUseCaseTest.java` | `e7414b7c839b662713091715a4bd52527e130f1b` | 18:20:27.232 | 18:20:27.386 |

## Task 6.4: CDI and Activation Cutover

The producer composes the four new root use cases. `PartyResource.activateParty` now passes the validated optional key to `ChangePartyLifecycleUseCase`, maps `PartyMutationOutcome.party()` to `PartyDetailResponse`, and retains the typed `Uni<RestResponse<ApiResponse<PartyDetailResponse>>>` boundary through `ResponseManager.successHttp`. Its existing HTTP activation/error checks in `PartyRegistrationResourceContractTest` and exact signature check in `PartyResourceSignatureTest` pass.

Reference inspection found no remaining Java uses of `ActivatePartyCommand`, `PartyActivationCandidate`, `PartyActivationPort`, `ActivatePartyUseCase`, `PartyActivationDecision`, `HibernateReactivePartyActivationAdapter`, or `PartyEntity.applyActivation` after caller migration. Those obsolete implementations were retired. All eight existing database activation regression cases were preserved in renamed `ReactivePartyActivationWorkflowTest`, now using the shared Application workflow and scoped mutation adapter. The legal-detail/activation race also uses that workflow with an injected fixed clock. Old adapter-mechanism and activation-facade tests were replaced by the already verified root/workflow suites and explicit lifecycle observation tests; this accounts for the changed test count rather than skipped scenarios.

The cutover retains `application.party-activation` observations and adds bounded deactivation/archival outcomes. The shared transaction adapter now observes `transaction.party-mutation` rather than emitting the retired activation-only adapter's transaction label. Publication and request completion behavior are unchanged. Observation tests verify lazy subscription, accepted/replayed outcomes, known/unexpected failures, downstream cancellation, and committed transaction completion. Remaining read/PATCH/scan/key-wait telemetry stays tracked under task 8.1.

Focused migrated activation/lifecycle/mutation, producer/port, legal-race, observation, and architecture tests pass. Full `./gradlew test` reports 757 cases, zero failures/skips, followed by successful `./gradlew build` with `quarkusIntTest`, using the documented runtime overrides. `git diff --check` is clean.

### Historical Constructor Discrepancy and Developer Handoff

The following records the prior blocker. It is superseded by the verified constructor/observation restoration in the resolution section below.

**Latest diagnostic update:** A subsequent disk read confirms that `ChangePartyLifecycleUseCase.java` now has only `(PartyMutationPort, Clock)`, with saved blob `edc72ea39a8e160960bc3b59f841a6261e9c0339`. The observation field, constructor argument, and execution wrapper are absent. The producer and tests still call the three-argument constructor. `./gradlew compileJava` now fails at `ApplicationUseCaseProducer.java:114`, reporting required `(PartyMutationPort, Clock)` versus supplied `(PartyMutationPort, Clock, OperationObservationPort)`. This is a confirmed saved-source mismatch, not merely a stale IDE diagnostic. The earlier successful build and inventory below describe the prior revision and must not be represented as verification of this current file. The observation integration needs reconciliation while preserving intervening edits; the diagnostic review did not overwrite Java source.

Task 6.4 remains unchecked. At the earlier verification point, the saved source had the public constructor `(PartyMutationPort, Clock, OperationObservationPort)` and Gradle compiled every caller while JDTLS reported undefined-constructor diagnostics. The developer confirmed Java diagnostic 134217858 in `ChangePartyLifecycleUseCaseTest`, at the call `new ChangePartyLifecycleUseCase(port, clock, new RecordingOperationObserver())`. Those validations showed one problem in the producer, one in the lifecycle test, two in the migrated reactive activation test, one in the legal repository test, and seven in the new lifecycle observation test. The subsequent source mismatch above supersedes the initial IDE-only hypothesis.

At that point, the lifecycle source also lacked a new Sonar completion record after its observer-constructor change, although repeatedly opening it requested Java validation. An older editor working copy or stale Java project model was suspected but not proven. The next step is now to reconcile the actual source and observation integration before repeating compilation and IDE analysis. Preserve intervening edits and do not add an unused compatibility parameter just to conceal the mismatch. Earlier task 6.3 evidence is historical and does not cover this revision.

The table records the saved revision and latest available observations, not completion of the blocked task. Sonar entries report zero findings where present; JDTLS entries explicitly retain unresolved diagnostics. Deleted-file document-close exceptions in JDTLS arose during retirement and are separate from remaining-file source diagnostics.

| Source set | Relative Java file | Saved Git blob | Latest Sonar completion | Latest JDTLS result |
|---|---|---|---|---|
| main | `application/model/ObservedOperation.java` | `4dbba8838bb633933e51fcf0541e9501b18bf076` | 18:47:14.420 | 18:47:14.599: 0 |
| main | `application/model/OperationOutcome.java` | `f5e2e3dea29bbfc9c29c6c54a534b68ccb6e26e8` | 18:47:16.274 | 18:47:16.650: 0 |
| main | `infrastructure/observability/MicrometerOpenTelemetryOperationObserver.java` | `5ae86c83fc7909b0709dfadacb3245d198f1444a` | 18:47:18.933 | 18:47:18.580: 0 |
| main | `application/usecase/ChangePartyLifecycleUseCase.java` | `f7a5c2d630fcafdc1141d57000dff1db63e0b9e7` | Pending current revision | Model/working-copy reconciliation pending |
| main | `infrastructure/persistence/HibernateReactivePartyMutationAdapter.java` | `8a38555199bb14916429a69764ed980ffb5c6286` | 18:47:25.413 | 18:47:25.506: 0 |
| main | `infrastructure/configuration/ApplicationUseCaseProducer.java` | `3dc556985070a651f61ddb73a07500433c0ca728` | 18:47:26.157 | 18:47:25.554: 1, unresolved |
| main | `api/resource/PartyResource.java` | `afe4cd94cdaeeb610a6d76936f7cc3a6f1040850` | 18:47:26.698 | 18:47:26.425: 0 |
| main | `infrastructure/persistence/PartyEntity.java` | `1d0af38da755c7dd1c44ee7846c0331c69742365` | 18:47:28.811 | 18:47:28.643: 0 |
| test | `application/usecase/ChangePartyLifecycleUseCaseTest.java` | `b6a516f3e81a2fd9ce5e3e2e5b70816bd0587bcd` | 18:41:07.857 | 18:47:31.176: 1, unresolved |
| test | `infrastructure/persistence/HibernateReactivePartyMutationAdapterTest.java` | `26a0a123f2262b3b3eff84013e15f8d4e4ba36c6` | 18:47:33.937 | 18:47:33.982: 0 |
| test | `infrastructure/configuration/ApplicationUseCaseProducerTest.java` | `bd570a2f5f517f0146d4cb273a9020ee04e77fe1` | 18:47:35.522 | 18:47:35.475: 0 |
| test | `application/port/ApplicationPortContractTest.java` | `4514d059d847a9af88c964e2ebbc5fcb5d4a490f` | 18:47:37.547 | 18:47:37.618: 0 |
| test | `infrastructure/persistence/PersistenceAdapterObservabilityTest.java` | `c5b1273e0ec51128668e3b0ef718b19c08a09136` | 18:47:40.729 | 18:47:40.282: 0 |
| test | `infrastructure/persistence/ReactivePartyActivationWorkflowTest.java` | `434192b308b093a80606a95ee44f8b2867dbadb3` | 18:47:43.640 | 18:47:43.690: 2, unresolved |
| test | `infrastructure/persistence/HibernateReactiveLegalEntityRepositoryTest.java` | `cdbcf335970ff4d7589b64f7d33527cb08562560` | 18:47:47.004 | 18:47:45.438: 1, unresolved |
| test | `application/usecase/PartyLifecycleObservationTest.java` | `94f41fce23e20e4c0d7e5ff3608de71139b0eac4` | 18:47:47.953 | 18:47:47.172: 7, unresolved |

### Resolution: Constructor and Observation Restored

With the developer's approval, the missing `OperationObservationPort` constructor argument, validated field, and actual `OperationObservation.observe` integration were restored while retaining intervening formatting and the record-pattern correction. The workflow reports activation/deactivation/archival outcomes or historical replay, with lazy subscription, unchanged failures, and propagated cancellation. This is functional integration rather than an unused constructor parameter.

The restored lifecycle source has saved blob `a760ecfb43888ff67c2497948842c509b5b5ded4`, unchanged between the start and end of verification. Full `./gradlew test` and `./gradlew build` with the documented Podman/HTTP-port overrides pass, including packaged integration. The final report records 757 tests, zero failures and zero skips. Existing lifecycle and observation regression tests cover the restored behavior; no additional duplicate tests were needed. `git diff --check` is clean.

Sonar completed full Java AST analysis of the restored class at 19:10:29.158 with zero issues/hotspots. JDTLS subsequently reported zero problems for the class and all five previously failing callers. The former Gradle/JDTLS discrepancy is resolved; task 6.4 is complete. These entries supersede the affected historical inventory rows above; the other saved blobs remain unchanged.

| Source set | Relative Java file | Saved Git blob | Sonar completion (0 findings) | JDTLS validation (0 problems) |
|---|---|---|---|---|
| main | `application/usecase/ChangePartyLifecycleUseCase.java` | `a760ecfb43888ff67c2497948842c509b5b5ded4` | 19:10:29.158 | 19:10:29.932 |
| main | `infrastructure/configuration/ApplicationUseCaseProducer.java` | `3dc556985070a651f61ddb73a07500433c0ca728` | 18:47:26.157 | 19:11:22.377 |
| test | `application/usecase/ChangePartyLifecycleUseCaseTest.java` | `ed00994871a81a0c2efab39082669a6539c85819` | 19:03:18.579 | 19:11:24.430 |
| test | `infrastructure/persistence/ReactivePartyActivationWorkflowTest.java` | `434192b308b093a80606a95ee44f8b2867dbadb3` | 18:47:43.640 | 19:11:27.106 |
| test | `infrastructure/persistence/HibernateReactiveLegalEntityRepositoryTest.java` | `cdbcf335970ff4d7589b64f7d33527cb08562560` | 18:47:47.004 | 19:14:11.935 |
| test | `application/usecase/PartyLifecycleObservationTest.java` | `26ae0bff175e19efd61985acb6d94e6af251fdf1` | 19:14:08.835 | 19:14:08.961 |

## Task 7.1: Strict Listing Query Parsing

`PartyQueryParameters` validates the complete decoded multivalue map against the eight approved parameter names before constructing the tenant-bound Application query. It rejects unknown/repeated/missing values, invalid exact enums, malformed or offset-free date-times, inverted intervals, blank cursors, oversized raw name filters, and invalid/out-of-range decimal limits. Absent limit alone defaults to 50. Name length counts raw Unicode code points before canonical comparison, preserving literal wildcard-looking characters and uppercase expansion semantics. Parse failures carry only the stable bad-request code without retaining rejected-value parser causes.

Seven parser tests pass, including 300 supplementary characters, 301-character rejection, expansion characters, Unicode-digit rejection, limits 1/200, equivalent offset instants and exact nanoseconds. Sonar S6353 was resolved using the concise `\\d+` pattern under Java's default ASCII character-class mode; its Unicode-digit rejection regression remains passing. Full `./gradlew test` (764 tests, zero failures/skips) and `./gradlew build` including packaged integration pass after the correction, with the documented runtime overrides. Both files have zero final Sonar findings and JDTLS problems.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `api/support/PartyQueryParameters.java` | `936fda8c4e3a1f672af1b29f7b361310422b2850` | 19:43:16.832 | 19:43:14.437 |
| test | `api/support/PartyQueryParametersTest.java` | `b3176081fa212d5e835ad8f886653fcab0fdc53b` | 19:29:46.270 | 19:43:46.239 |

## Task 7.2: Root Error Catalog and Shared Envelopes

The API catalog now includes `400 display-name-required`. The translator also recognizes the Domain new-write length violation as `400 display-name-too-long`, retaining `422 blank-display-name` for supplied blank strings and the existing distinct PATCH/lifecycle 412 codes. Unknown failures and cancellation retain identity for the shared global boundary; no service-local global mapper was added.

Two new contract tests use a resource present only in test sources to exercise ten root validation/business codes and a controlled unexpected failure through actual HTTP and the published mapper. Responses contain exactly `status` and `code`, HTTP/body statuses agree, and accepted process IDs are echoed. Typed translation regressions and full `./gradlew test` plus `./gradlew build` with packaged integration pass using the documented runtime overrides. All four affected Java files have completed Sonar analysis with zero findings and separate zero JDTLS problems.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `api/error/PartyResponseCode.java` | `65a5f13161b6653a8a51502ca1cad11a82544c36` | 19:51:00.963 | 19:51:01.041 |
| main | `api/error/PartyApiErrorTranslator.java` | `b3230cc9dfe6b0397ec384f307e03bc42b193849` | 19:51:02.761 | 19:51:02.987 |
| test | `api/error/RootErrorVerificationResource.java` | `4e570ca90438c2de79ea773724617afc23cb4308` | 19:51:05.343 | 19:51:05.212 |
| test | `api/error/RootErrorContractTest.java` | `35bd2fce300ffb1e77864711e5475fd122566417` | 19:51:06.987 | 19:51:07.168 |

## Task 7.3: Strict Root PATCH Binding

`PartyUpdateRequest` retains the original nullable string and validates a separate normalized copy with required-value and 300-UTF-16-unit constraints. Blank strings pass structural validation for the later Domain check. Its model-local deserializer accepts only an object with the supported string/null field, rejects duplicates, coercion, unknown properties and trailing root content, and preserves missing/null values for the declared validation codes. The scoped reader interceptor translates only this model's JSON failures to a cause-free shared bad-request exception; unrelated models and transport failures retain their original handling. Reflection registration is limited to the API model/deserializer.

The initial placement in `api.serialization` produced an ArchUnit package cycle through `@JsonDeserialize`. Implementation paused and the developer approved colocating the specific deserializer with the model in `api.model.request`. The cycle is now eliminated without weakening architecture rules. Five binding/validation tests and all architecture checks pass, followed by full `./gradlew test` (771 tests, zero failures/skips) and `./gradlew build` with packaged integration using the documented runtime overrides. All four final files have zero Sonar findings and separate zero JDTLS problems.

Sonar logs rotated again during this task: records immediately before 20:01 are now in `SonarQube for IDE.1.log`, and the preceding older rotation is in `.2.log` where retained. Use timestamps to locate earlier evidence. The following final entries are in the active log.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `api/model/request/PartyUpdateRequest.java` | `ddea180c5cb279e208b583077bfea6d3677fdae4` | 20:05:07.109 | 20:05:07.563 |
| main | `api/model/request/PartyUpdateRequestDeserializer.java` | `63521ce3e38d1507d31225612c77b6d9d27cbd07` | 20:05:08.768 | 20:05:09.153 |
| main | `api/error/PartyUpdateJsonReaderInterceptor.java` | `292b9d5aea4ef38a5eacc154bb369a6673dd2067` | 20:01:21.517 | 20:01:21.629 |
| test | `api/model/request/PartyUpdateRequestTest.java` | `7330a1026103e0b73c4cd21b05a7a5427d80937c` | 20:01:24.874 | 20:01:23.761 |

## Task 7.4: Summary DTO and Checked Pagination Mapping

`PartySummaryResponse` exposes the six approved scalar summary fields, with explicit API-only native reflection registration. `PartyApiMapper` maps the page's immutable summaries before wrapping and converts both long totals using `Math.toIntExact` into shared `PaginationMetadata`; unrepresentable counts fail instead of wrapping, saturating, or disappearing. Existing exhaustive detail mapping is reused unchanged.

Three mapping tests cover both types' exact JSON keys, retained historical labels/timestamp precision, actual result size, empty arrays and zero counts, the largest supported count, and separate total-element/total-page overflow. Full `./gradlew test` reports 774 tests with zero failures/skips, and `./gradlew build` including packaged integration passes using the documented runtime overrides. Final Sonar/JDTLS results are clean for all three files.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `api/model/response/PartySummaryResponse.java` | `16cf15e97bd76e6888742f5868e601d9e3ba6bf4` | 20:13:27.447 | 20:13:27.693 |
| main | `api/mapper/PartyApiMapper.java` | `2079e666282808af808badab1c842a16a5f587ec` | 20:13:29.457 | 20:13:29.522 |
| test | `api/mapper/PartyPageMappingTest.java` | `5930ed38510b22368ea0c58a8d5ad50485135b02` | 20:13:31.900 | 20:13:32.038 |

## Task 7.5: Pagination-Only Explicit Null Cursors

`PartyPaginationObjectMapperCustomizer` installs a narrow Jackson module that decorates only the shared envelope's next/previous cursor property writers. All three populated count fields identify pagination, including zero. Missing cursor values then serialize explicitly as null; other cases delegate to the library-configured writer. No envelope copy, global inclusion override, or response-wrapping filter is used.

Four tests verify actual CDI registration, empty and terminal pages, existing non-null directions, ordinary success/error shapes, unrelated similarly named properties, and both customizer registration orders. The existing default-library characterization remains separate and passing. Focused serialization/architecture checks and full `./gradlew test` plus `./gradlew build` with packaged integration pass using the documented runtime overrides. Both final files have zero Sonar findings and zero JDTLS problems. Native behavior remains part of the later required native acceptance suite.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `api/serialization/PartyPaginationObjectMapperCustomizer.java` | `fea431e1ce827d44a7bf8a2a4dcd7aa7e049c7a2` | 20:22:04.564 | 20:22:04.718 |
| test | `api/serialization/PartyPaginationSerializationTest.java` | `052757d98eeca0efda9c4b263aae884eeabbdafc` | 20:22:06.636 | 20:22:06.520 |

## Task 7.6: Root GET Resources

`PartyResource` now uses `/v1/parties` as its base, retaining the full activation URL through a method path and exposing typed list/detail GETs. Context/query/path mapping precedes the Application workflows; DTO conversion precedes CDI `ResponseManager.paginatedHttp`/`successHttp`. Signature tests explicitly inspect the complete generic envelope and the summary list's DTO type.

Three live HTTP smoke cases cover both safe detail subtypes, retained historical archived data, cross-tenant concealment, summary-only paginated data, exact count/null-cursor fields, empty results and invalid queries/cursors/IDs. Reactive fixtures use only DML in the Flyway-managed schema. The first routing smoke incorrectly assumed an existing identifier GET; inspection confirmed only POST is implemented there. Execution paused, the developer approved correcting the test, and the existing POST's specific validation response now verifies dispatch without adding an identifier route. Existing successful identifier registration and activation regressions also pass.

Full `./gradlew test` reports 782 tests with zero failures/skips, and `./gradlew build` including packaged integration passes using the documented runtime overrides. All four final Java files have zero Sonar findings and zero JDTLS problems.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `api/resource/PartyResource.java` | `8a1fdc66dd497a655140533ff51010979db8c5b9` | 20:34:14.590 | 20:34:14.838 |
| test | `api/resource/PartyResourceSignatureTest.java` | `b23dc1cb8302726655f351ffb911c4fe4485cd55` | 20:34:17.009 | 20:34:16.955 |
| test | `support/RootPartyFixtures.java` | `6bf620527ab30c8fd83d5b1b19bac56ef66bf7f0` | 20:34:19.410 | 20:34:19.186 |
| test | `api/resource/PartyRootReadSmokeTest.java` | `b3be265040950b555ead70244605e1249c24af1c` | 20:38:43.179 | 20:38:43.467 |

## Task 7.7: Root Mutation Resources

PATCH, deactivation, and archival now delegate to their Application workflows. All lifecycle methods share context/key/path/version validation; PATCH preserves context/body/path/version precedence and does not acquire replay behavior from an idempotency header. All four mutation signatures map the safe Party result to `PartyDetailResponse` before CDI `ResponseManager.successHttp`.

Three mutation smoke cases cover both types, archival followed by correction and historical replay, unkeyed/current-version behavior, evidence/state errors, strict malformed/unknown/duplicate/coerced JSON, required body/value distinctions, normalized expansion, 415, and unchanged rejected writes. Signature inspection and registration regressions also pass. Full `./gradlew test` reports 786 tests, zero failures and zero skips; `./gradlew build` including packaged integration passes with the documented Podman/test-port overrides (captured output `tool_0bc83ad31001JZ3knACRbOYoXq`, successful runs in 2m13s and 44s).

The following saved revisions were rechecked against Git blobs. SonarQube for IDE 5.7.0 completed with 571 active Java rules and zero issues/security hotspots for each file; separate JDTLS validation reported zero problems. Timestamps are local on 2026-09-19.

| Source set | Relative Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|---|
| main | `api/resource/PartyResource.java` | `f0d9650141c491fd2da167b6e27265408fbeeb91` | 20:48:52.824 | 20:48:53.135 |
| test | `api/resource/PartyResourceSignatureTest.java` | `feae348bc8e8820673190da6b4d804fed27393ed` | 20:48:54.619 | 20:48:54.779 |
| test | `api/resource/PartyRootMutationSmokeTest.java` | `ea84bdf2daf0c10171b056860b83dbe989a57912` | 20:48:56.300 | 20:48:56.661 |

## Task 8.1: In Progress — Compilation Failure

The working tree now includes bounded root HTTP labels and mutation dispositions, observed list/retrieve/PATCH Application workflows, and Infrastructure scan/key-wait measurements with focused tests. These edits are not verified or quality-complete and task 8.1 remains unchecked. They supersede earlier saved-file evidence wherever a Java file changed.

The focused Gradle test command stopped at `compileJava`: `PartyPersistenceObservability.java:48` passes `Supplier<Uni<Void>>` directly to Mutiny `UniCreate.deferred`, whose parameter is `Supplier<Uni<? extends T>>`. Java's invariant generic supplier type prevents this call from compiling. The intended correction is to adapt the supplier with `work::get`, preserving lazy execution and the existing terminal signals. The apply workflow paused on this error before making that correction. No tests from this attempt ran, and no successful build or final IDE analysis is claimed for these edits. `git diff --check` passed.

### Task 8.1 Compilation Resolution and Local Analysis Handoff

The developer approved continuing. The supplier is now adapted with `work::get`. Test compilation also exposed that the resolved `SimpleMeterRegistry` does not implement `AutoCloseable`; the two new telemetry test classes now use explicit `finally` cleanup, matching existing tests. These compiler failures are resolved, and the earlier JDTLS errors for those files have been reconciled to zero problems.

The focused telemetry/workflow/persistence/HTTP/architecture selection passed. Full `./gradlew test` then passed with **792 tests, zero failures and zero skips**; `./gradlew build`, including `quarkusIntTest`, passed. Both used `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0`. Captured output: `tool_0bc98751f001ZJK1Kd16ZwsgXC` (test 2m28s, build 47s). No compiler warnings were emitted. The known rollback-on-close diagnostic remains visible in cancellation tests. `git diff --check` is clean.

Task 8.1 remains incomplete: standalone Sonar reports **six findings** across three saved files. The accessible log records counts but not rule IDs/messages/locations. Developer handoff requests those details from the IDE Problems panel before fixes and reanalysis; no suppression or rule changes were made. All timestamps below are local on 2026-09-19. Sonar configuration remains version 5.7.0, 571 active Java rules; every run reports zero security hotspots. JDTLS reports zero problems for every revision below.

| Source set | Relative Java file | Saved Git blob | Sonar completion / findings | JDTLS validation |
|---|---|---|---|---|
| main | `application/model/ObservedOperation.java` | `458a4301eb538a31a3b4d0a0b90273eb3c046334` | 21:07:49.225 / 0 | 21:07:49.562 |
| main | `application/model/OperationOutcome.java` | `5f3101c5deac404f85af3236d95867004af8f820` | 21:07:51.148 / 0 | 21:07:51.291 |
| main | `application/usecase/GetPartyUseCase.java` | `12ef34fa818673dc4969559451799cb9712e590a` | 21:07:53.771 / 0 | 21:07:53.763 |
| main | `application/usecase/ListPartiesUseCase.java` | `df78f3dd6640975048e12e7c39b3d066a58afb8d` | 21:07:56.171 / 0 | 21:07:56.342 |
| main | `application/usecase/PatchPartyUseCase.java` | `60b1fde62e33eca701889124adfe8beb09221dae` | 21:07:58.296 / 0 | 21:07:58.245 |
| main | `infrastructure/configuration/ApplicationUseCaseProducer.java` | `552bd98afa73a129ec2f46c54d7ecad681d48e18` | 21:07:59.857 / 0 | 21:07:59.867 |
| main | `infrastructure/observability/MicrometerOpenTelemetryOperationObserver.java` | `d45fb1f58f20f64df9fdc4773063c69c3683e438` | 21:08:01.782 / 0 | 21:08:01.839 |
| main | `infrastructure/observability/PartyPersistenceObservability.java` | `bb9016612f09d556f6cd5efc441822218ec7cd7d` | 21:10:38.133 / **3** | 21:10:38.450 |
| main | `infrastructure/persistence/HibernateReactivePartyQueryAdapter.java` | `5d6a6c95b50cedde3a82ad8bd37e6f0f9e5b69e3` | 21:08:04.946 / 0 | 21:08:05.212 |
| main | `infrastructure/persistence/PartyLifecycleIdempotencyPersistence.java` | `904b8b42062836d5e2d414be907b7eca0cf47622` | 21:08:06.436 / 0 | 21:08:06.741 |
| main | `api/observability/PartyHttpObservability.java` | `868bc238075b3c3f0bbd0d767039b5ad38ffa73c` | 21:13:47.602 / **2** | 21:13:47.110 |
| main | `api/context/RequestMetadataContext.java` | `3c62c678e66ccd1923ec0d5e0074ee4baf8d18e9` | 21:08:09.749 / 0 | 21:08:10.024 |
| main | `api/filter/RequestContextFilter.java` | `825fa3f5abec824234e4f734cc6e3386086a9a43` | 21:08:11.609 / 0 | 21:08:11.713 |
| main | `api/resource/PartyResource.java` | `a178f67cb914ff21207af8b0fb2eda04fd293d07` | 21:08:13.114 / 0 | 21:08:13.328 |
| test | `application/usecase/PartyReadUseCasesTest.java` | `f32c39ee8930424bf0742cecffe5573213953f66` | 21:08:14.825 / 0 | 21:08:14.969 |
| test | `application/usecase/PatchPartyUseCaseTest.java` | `ae2a04d17b7b09b0709a65ca07520e3b30c4a234` | 21:08:16.279 / 0 | 21:08:16.477 |
| test | `infrastructure/configuration/ApplicationUseCaseProducerTest.java` | `a484e3c3c97162884eb569f41eb35b158d79d789` | 21:08:17.860 / 0 | 21:08:18.105 |
| test | `infrastructure/persistence/PartyQuerySnapshotTest.java` | `559edcf17408fb0126e3dda2980638794bf90b4f` | 21:08:19.401 / 0 | 21:08:19.677 |
| test | `infrastructure/persistence/PartyQueryVolumeTest.java` | `a4b16bfd5f9147c43659d5cec3bca0732d402134` | 21:08:21.177 / 0 | 21:08:21.414 |
| test | `infrastructure/persistence/PartyLifecycleIdempotencyPersistenceTest.java` | `c09a707cc9aa5045950cc8403e96c48117c3fd24` | 21:08:23.129 / 0 | 21:08:23.291 |
| test | `api/observability/PartyHttpObservabilityTest.java` | `284019a0e3078fa9b0aeb803155f97205e7731a7` | 21:08:24.414 / 0 | 21:08:24.685 |
| test | `api/observability/RootPartyHttpObservabilityTest.java` | `83444d0aa77f49895d38a99fedf48ded44b40beb` | 21:13:48.784 / **1** | 21:13:48.976 |
| test | `infrastructure/observability/PartyPersistenceObservabilityTest.java` | `521266e208864401a23f270e22fbdebd0795feee` | 21:13:50.544 / 0 | 21:13:50.894 |

### Task 8.1 Completed: Local Findings Resolved

The developer supplied all six finding locations: S1192 for the repeated outcome/PATCH/unsupported literals, S7467 for the two unused catch parameters, and a method-reference recommendation for `Counter::count`. Constants, unnamed catch parameters, and that reference resolve the findings without suppressions. Separate final main/test analysis avoids the mixed-source analysis run's missing-main-classpath warning; the accepted runs resolve Java 25 dependencies normally and report zero issues/security hotspots. The route-label Javadoc also now explicitly describes the unsupported-method result.

All 23 files have zero final JDTLS problems and zero unresolved Sonar findings, using the preceding inventory with these superseding revisions:

| Source set | Relative Java file | Saved Git blob | Sonar completion / findings | JDTLS validation |
|---|---|---|---|---|
| main | `infrastructure/observability/PartyPersistenceObservability.java` | `908c2f7d9a7ce08c0503a7d8ee5107b31bebc916` | 21:23:29.866 / 0 | 21:27:49.441 |
| main | `api/observability/PartyHttpObservability.java` | `0436e155684e2039b1b73426cf8b31e3f3e48927` | 21:28:33.062 / 0 | 21:28:33.111 |
| test | `api/observability/RootPartyHttpObservabilityTest.java` | `64d35f392be2afec780960cbd874bec32d9bf458` | 21:27:57.021 / 0 | 21:27:57.363 |

Full `test` passes with 792 cases and zero failures/skips; `build` including packaged integration passes (captured output `tool_0bca1a673001uEAw6OXSQEvBmF`, 2m22s and 46s, documented runtime overrides). The subsequent Javadoc-only clarification changes no executable behavior. Tests cover six closed HTTP labels/spans, Application read/PATCH terminal outcomes, lifecycle outcomes, mutation dispositions/conflicts, lock success/failure/cancellation, and subscription-local partial/completed scan counts. The actual database reference scan verifies 2,000 received rows, eight nonempty batches, 500 matches, and a 50-row result. The console format at `application.properties:70` remains exactly the required string; no logging configuration changed.

## Task 8.2: Root Context Contracts — Local Analysis Handoff Pending

Six new HTTP contract tests in `api/filter/RootPartyContextContractTest.java` exercise all six actual root methods: missing/duplicate context headers and precedence, canonical process/tenant checks, blank/oversized/unsafe users and the accepted 128-character boundary, case-insensitive names, exact error envelopes and process echoes. Completion capture verifies only accepted MDC fields survive partial initialization, invalid process values leave no correlation, and rejected values are absent. Twelve barrier-released concurrent requests use distinct contexts and verify their individual completion records. A historical deactivation replay after archival preserves its original audit while echoing and logging the new user's/process's context. Management requests bypass business validation/completion, and the configured log format is asserted exactly. Existing filter unit tests retain Unicode code-point and owned-MDC cleanup coverage.

The focused root/filter suites pass. Full `test` reports **798 tests, zero failures and zero skips**, and `build` including `quarkusIntTest` passes with the documented runtime overrides (output `tool_0bcab1d010019voGeolIIRi2aD`, 2m17s and 37s). The Javadoc-only change noted above is included in the compiled revision.

Task 8.2 remains unchecked because Sonar reports one unresolved finding, whose rule/message/location is not in the accessible completion log. Developer handoff requests that single Problems entry; no finding is dismissed based on successful tests.

| Source set | Relative Java file | Saved Git blob | Sonar completion / findings | JDTLS validation |
|---|---|---|---|---|
| test | `api/filter/RootPartyContextContractTest.java` | `756dea672ab7fd6f25854ed1d93a19393010c380` | 2026-09-19 21:32:51.042 / **1**, 0 hotspots | 2026-09-19 21:32:51.366 / 0 problems |

### Task 8.2 Completed: Restricted Identifier Renamed

The developer identified S6213 on `CompletionCapture.publish(LogRecord record)`. Renaming the parameter and its references to `logRecord` resolves the finding without altering capture behavior. Final saved blob `d0d112c036f0cb7d70c10be058c8002bc8404bab` has zero Sonar issues/hotspots (2026-09-20 10:27:44.868) and zero JDTLS problems (10:27:44.928). Full `test` and `build` including packaged integration pass with the documented runtime overrides (output `tool_0bf703027001w0bqFPV2SP0sXj`, 3m34s and 56s). Task 8.2 is complete.

## Task 9.1: Root Read HTTP Contracts

Six new `PartyRootReadContractTest` cases complement the root smoke, context, framework-error, and snapshot-consistency tests. They verify both detail subtypes in all four states, exact top-level/detail/summary key sets, retained historical text/audit, tenant concealment, canonical IDs, stable equal-timestamp ordering across three pages and exact previous-page reconstruction. All filter inputs participate in AND intersections with inclusive microsecond boundaries, equivalent offsets, uppercase expansion, preserved accents/interior spaces, and literal percent/underscore characters. Cursor requests reject tampering and changes to tenant, each effective filter, or limit while accepting equivalent canonical values. Tests also cover default 50 and boundaries 1/200 with 51 rows, explicit terminal/empty null cursors, supplementary-character 300/301-code-point filters, blank filters, all eight duplicate parameter names, unknown parameters, invalid enums/dates/limits/cursors, and inverted intervals.

Focused read contracts pass; full `test` reports **804 cases, zero failures/skips** and `build` including packaged integration passes (output `tool_0bf786a23001dA20lMwc9oQbTI`, 2m22s and 38s, documented runtime overrides). Final saved test blob `c2bd940a1a5611264eb2254173d40a6fa6318cdd` has zero Sonar issues/hotspots (2026-09-20 10:36:13.224, unchanged standalone configuration) and zero JDTLS problems (10:36:13.278).

## Task 9.2: Root PATCH HTTP Contracts

Six new `PartyRootPatchContractTest` cases cover correction and identical canonical writes for both types in all four states, exact version advances and update actor/timestamp consistency, preservation of other root/detail fields, and one enabled update event per accepted correction. ASCII, uppercase-expansion, supplementary-character, exterior-whitespace and blank-string cases establish normalized UTF-16 boundaries. Closed JSON cases reject incompatible bodies/properties, duplicates, unknown assignments and trailing content; required body and value errors remain distinct. Context/body/path/header and absence/version/blankness precedence are asserted, including every If-Match syntax category and maximum long values. Rejections retain the complete previous representation and event count. Supplied or duplicated idempotency headers do not introduce PATCH replay records. Real natural/legal registrations retain their original 201 replay envelopes and unchanged identifier database rows after root correction.

An initial Java overload-inference error in a generic awaited count assertion was resolved by assigning the result to a `long` local before asserting. Focused tests and the full suite now pass: **810 tests, zero failures/skips**, plus `build` with packaged integration (output `tool_0bf8182a4001SBJUw0VJvnMZH7`, 2m17s and 35s; documented runtime overrides). Final saved blob `9eba7b7cdfe5a622cb2da73be8929f8ab50e0c62` has zero Sonar issues/hotspots (2026-09-20 10:45:59.859) and zero JDTLS problems (10:46:00.039). The final compile and JDTLS results agree.

## Task 9.3: Lifecycle HTTP and Evidence Matrices

Six live `PartyRootLifecycleContractTest` cases exercise all 24 Party-type/state/action combinations, preserving details and creation audit on success and the full original result on rejection. A profile-selected test-only CDI alternative injects a fixed clock with zone UTC+14 near a UTC date boundary; exact response timestamps and same-UTC-day expiration prove that the real workflow uses the captured UTC date. Tests cover qualifying nonprimary identifiers with draft/deprecated/retired compatible schemes, optional expiration, pending/rejected/revoked/expired evidence, expired verified evidence, incompatible schemes, and evidence owned by another Party/tenant. Failed activation keys become usable after valid evidence is added. The relational foreign keys and actual scheme join enforce scheme identity in storage; mismatched typed evidence remains covered independently by Domain policy tests rather than weakening database constraints to create an impossible HTTP fixture.

All actions exercise key cardinality/blankness/128-character limits, path/If-Match validation, tenant concealment, absence/version/lifecycle/evidence precedence, conflicting completed-key reuse before current-state evaluation, mandatory syntax checks even with a saved key, and unkeyed stale behavior. The alternative is selected only for this profile; full-suite regressions confirm other profiles retain their production composition.

Focused lifecycle/activation suites pass. Full `test` reports **816 cases, zero failures/skips** and `build` with packaged integration passes (output `tool_0bf89cc5d00141InT0o7DTa3Sc`, 3m2s and 50s, documented runtime overrides). Final standalone Sonar and JDTLS results are zero findings/problems:

| Test Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|
| `support/FixedLifecycleClockProducer.java` | `b90157324125374492d7c382ca8b28c1f076984b` | 2026-09-20 10:54:55.597 | 10:54:56.014 |
| `api/resource/PartyRootLifecycleContractTest.java` | `ee5dcb660fec228055fe373eea06e1d48eeffa2d` | 2026-09-20 10:56:36.932 | 10:56:37.206 |

## Task 9.4: Concurrent Lifecycle Workflows

`PartyLifecycleConcurrencyTest` runs twenty complete workflow races through independent Vert.x contexts and the real per-subscription session/transaction adapter. An explicit two-party start barrier coordinates concurrent attempts; no sleeps or timing-only assertions decide correctness. Equivalent keyed requests cover both types and all three actions, yielding one APPLIED and one REPLAYED disposition with identical original safe data/version. Conflicting archival keys for different Parties yield one accepted result and one `IdempotencyKeyConflict`, with the losing Party completely unchanged. Distinct-key and unkeyed requests cover every type/action and yield one applied mutation plus one `StalePartyVersion`. Database counts assert exactly one event, one successful snapshot when keyed, and none for unkeyed attempts. API translation of these transport-neutral failures to 409/412 is covered by the preceding HTTP contracts. Earlier separate-session lock tests additionally establish observed advisory contention and committed-result visibility.

All three test methods and full `test` pass (**819 cases, zero failures/skips**); `build` including packaged integration passes (output `tool_0bf921c3e001QlRKpIj8mw7wfO`, 2m43s and 39s; documented runtime overrides). Saved blob `aebb94e16a6d5946f04db7e67545b66543fc4b37` has zero Sonar issues/hotspots (2026-09-20 11:04:14.934) and zero JDTLS problems (11:04:15.219).

## Task 9.5: Root Versus Type-Specific HTTP Races

`PartyRootDetailRaceContractTest` coordinates eight HTTP races using an explicit start barrier: root PATCH/activate/deactivate/archive against natural-person preferred-name or legal-entity trade-name PATCH. Each race yields exactly one 200 and one 412, with `expected-version-mismatch` for correction/detail losers and `stale-party-version` for lifecycle losers. Fresh reads verify exactly version one, the winning actor, preserved creation audit, and only the winner's fields/state. Repeating both original requests remains stale without changing that result. Complete identifier rows, including independent status/version/audit, are unchanged. Root-correlated outbox count is one only when that root operation wins.

The targeted race suite and full `test` pass (**820 cases, zero failures/skips**); `build` with packaged integration passes (output `tool_0bf992e29001dfc877oPv3hXkg`, 2m45s and 37s; documented overrides). Saved test blob `c2a4482cffa3519a25c1cab50ec5d18d040d3686` has zero Sonar issues/hotspots (2026-09-20 11:11:58.423) and zero JDTLS problems (11:11:58.443).

## Task 9.6: Flushed Mutation Failure Checkpoints

Two additional adapter tests exercise the actual Application PATCH/lifecycle workflows through a test-only scoped-port proxy. Five checkpoints inject failure after the root write, completed snapshot write, or enabled event write as applicable. Before failing, each checkpoint flushes the real session and asserts the changed version and exact snapshot/event counts inside that transaction. A fresh read after session closure then proves complete rollback to the original representation and zero accepted snapshot/event rows. Retrying the original command applies successfully, including reuse of the failed lifecycle key. Existing tests in the same suite verify actual PostgreSQL lock timeout, operation timeout, caller cancellation, session closure and subsequent key/root acquisition. No production hook, failure endpoint or DDL was introduced.

Full `test` passes with **822 cases, zero failures/skips**, and `build` including packaged integration passes (output `tool_0bfa012590011RX7CtrKfmJNyJ`, 2m43s and 36s; documented overrides). Final `infrastructure/persistence/HibernateReactivePartyMutationAdapterTest.java` blob `2a340267c996109d85b0ff65d3de9397fbc15bfe` has zero Sonar issues/hotspots (2026-09-20 11:19:28.143) and zero JDTLS problems (11:19:28.243).

## Task 9.7: Actual Process Relaunch — Local Analysis Handoff Pending

`src/integrationTest/java/com/alexastudillo/partyregistry/PartyLifecycleRestartIT.java` now launches and terminates two distinct packaged application processes while retaining one isolated PostgreSQL container. Each launch uses Flyway for all schema changes; test fixture setup executes only DML. Both Party types start active. The test deliberately discards successful deactivation and archival response bodies, observes accepted data through independent GETs, terminates the first process, verifies a different process ID after relaunch, and retries both original keyed requests with a new user/process. Replays exactly preserve historical results/audit, current state stays archived, and snapshot/event counts remain four/four. The response-loss simulation discards payloads; it does not assume a disconnected HTTP request rolls back an accepted commit.

The initial launcher incorrectly treated the presence of Gradle's `native.image.path` as proof of native execution. That property was also present in JVM integration execution and selected a pre-change native binary, producing 404 and only V1–V4 migration logs. The corrected launcher reads the generated `build/quarkus-artifact.properties` type/path, selecting the current JVM artifact or current native executable explicitly. The retained diagnostic log is `/tmp/junit-10187761468821697253/first.log`; no older artifact was removed or modified. A preflight GET also verifies the seeded root is reachable before response-discard assertions. Temporary child-process logs are retained only on test failure.

The targeted restart test passes after this correction. `./gradlew test` succeeds (822 previously executed cases retained as up-to-date), and `./gradlew build` runs **14 packaged integration cases with zero failures/skips**, including the real relaunch test. The current artifact descriptor is `type=jar`; this is JVM restart evidence, not a native acceptance claim.

The final saved integration-test blob `f47ed1b93d395fc398769dee3d5bbad905e6fab9` has zero JDTLS problems (2026-09-20 11:31:18.381). Sonar completed on the same revision at 11:31:18.195 with **one issue and zero security hotspots**. The accessible log does not expose its rule/message/location; developer handoff requests that single Problems entry. Task 9.7 remains unchecked until the finding is resolved and the affected checks are repeated.

### Task 9.7: Awaitility Correction and IDE Synchronization Blocker

The developer identified S2925 on the readiness polling sleep. The test now uses Awaitility with a 60-second deadline, immediate first probe, 100ms poll interval, bounded two-second HTTP probes, and fail-fast detection of a terminated child process. Only IOExceptions from startup probes are ignored; assertion failures and other exceptions are not hidden. `testImplementation("org.awaitility:awaitility")` resolves to **4.3.0 through the Quarkus 3.33.3.1 BOM**, confirmed with `dependencyInsight` on `integrationTestCompileClasspath`.

Two targeted runs then failed at the final `execInContainer` SQL-count query with Docker Java Unix-socket `IOException: Broken pipe`, after the relaunched HTTP replay assertions had succeeded. The fixture/control SQL helper now opens bounded, test-thread-only JDBC connections, following the existing migration-test convention and using the already available PostgreSQL driver. This removes the container-exec transport from fixture DML/count reads. Application requests continue using the real reactive service; Flyway remains the sole DDL owner.

After these corrections, full `./gradlew test` and `./gradlew build` pass with the documented overrides (output `tool_0bfbac575001bbyIUI5G83jnVa`, 8m36s and 1m14s). The packaged suite reports **14 tests, zero failures/skips**, including successful relaunch and final unchanged four/four snapshot/event counts.

Final working revisions:

| File | Saved Git blob |
|---|---|
| `build.gradle.kts` | `1f70873689d02039ae6978e40241990d84b34c11` |
| `src/integrationTest/java/com/alexastudillo/partyregistry/PartyLifecycleRestartIT.java` | `bea910d5e28df32bb8ffab31b5ba1c7a5f69353b` |

IDE acceptance remains blocked. JDTLS's Gradle synchronization failed because its cached initialization script `/tmp/opencode/legal-jdtls-x1lvdj7b/configuration/org.eclipse.osgi/58/0/.cp/gradle/init/init.gradle` no longer exists (JDTLS log lines 17208 and 17302). The current test has two IDE problems at 2026-09-20 11:46:31.226 despite successful Gradle compilation. Sonar reports zero issues/hotspots at 11:46:30.958, but explicitly warns of unresolved types/imports; this is not accepted as final resolved-model evidence. The handoff requests a Java language-server workspace restart/reimport in Antigravity, followed by JDTLS and Sonar reanalysis of the same saved revision. No compiler warning suppression, manual IDE classpath edit, or fabricated replacement initialization script was introduced. Task 9.7 stays unchecked.

### Task 9.7 Completed: IDE Model Reconciled

The developer completed the Java workspace restart and Gradle reimport. The new JDTLS session (2026-09-20 13:32:21) initialized the Java 25 project and completed build/import jobs. Saved blobs remain `1f70873689d02039ae6978e40241990d84b34c11` for the build file and `bea910d5e28df32bb8ffab31b5ba1c7a5f69353b` for `PartyLifecycleRestartIT.java`, matching the passing test/build revision. Reopening that integration test produced **zero JDTLS problems at 13:36:55.768** and **zero Sonar issues/hotspots at 13:37:04.745**, with resolved types and no unresolved-import warning. Sonar remains 5.7.0 with 571 active Java rules. This reconciles the earlier tooling discrepancy; task 9.7 is complete. Native execution remains a separate final acceptance task.

## Task 9.8: Framework Failures and Operational Confidentiality

Three `RootFrameworkErrorContractTest` cases exercise the actual six root resource methods with profile-local failing query/mutation ports. Every unexpected failure passes through the real workflows/translator/shared global mapper and returns exactly 500/server-error without internal detail or submitted values. Framework tests cover unknown root subpaths, unsupported collection/item/action methods, unsupported PATCH media, malformed JSON and invalid query/cursor input, asserting status/body agreement and accepted process echoes. Operational capture verifies rejected property names, filters, cursors and lifecycle keys never appear in messages; service metrics retain bounded dimensions without raw Party/tenant/process IDs or submitted canaries. Existing strict-shape, snapshot/event allowlist and context tests cover identifier-protection exclusion and invalid-process echo rules. The failing ports exist only in test sources and are enabled only for this profile.

Full `test` passes with **825 cases, zero failures/skips**, and `build` with packaged integration passes (output `tool_0c024af28001M9jEaznBL94LMU`, 9m13s and 2m35s; documented overrides). Test blob `797c41efcad9bc2d20c6e164be6dc08f6da614fa` has zero Sonar issues/hotspots (2026-09-20 13:40:27.249) and zero JDTLS problems (13:40:26.727).

## Task 10.1: Packaged Root Contracts — Verification Pending

`PackagedRootContractIT` adds two packaged JVM/native contract tests using the existing integration resource and new immutable test-only migration `V1002__seed_packaged_root_contract_fixtures.sql`. Isolated natural/legal roots have qualifying verified evidence. The flow verifies summary-only forward/backward pagination, explicit terminal/empty null cursors, scoped continuation rejection, safe details, all three lifecycle actions, archived root correction, historical replay for all actions and both snapshot subtypes, and unchanged current state after replay. Strict binding and framework failures exercise the packaged deserializer/shared mapper. Existing Gradle integration/native source-set wiring executes this class against either artifact; actual native acceptance is still pending.

The full JVM test suite remains successful (825 cases). The first combined build encountered Docker Java `IOException: Broken pipe` while starting a new PostgreSQL container for the new test resource; it reported 16 packaged cases with one failure and one skipped case. A separate targeted run of `PackagedRootContractIT` then passed both tests with zero failures/skips in 3m18s. This targeted success does not replace a subsequent successful combined build. Investigation found the user Podman API service runs `podman system service` without a timeout override and was inactive after a normal exit; Podman's documented default idle timeout is five seconds. A temporary test-owned API service with `--time=0` is a possible environment-scoped follow-up for repeated combined-run pipe failures; no user service configuration was changed.

Task 10.1 remains unchecked pending a successful combined run and resolution of one Sonar finding. Saved revisions:

| File | Saved Git blob | Diagnostics |
|---|---|---|
| `src/integrationTest/java/com/alexastudillo/partyregistry/PackagedRootContractIT.java` | `9866f47e3ff1ac646dace730185e2deb477d4e1b` | JDTLS: 0 problems at 2026-09-20 13:58:16.125; Sonar: **1 issue**, 0 hotspots at 13:58:20.431 |
| `src/integrationTest/resources/db/test-migration/V1002__seed_packaged_root_contract_fixtures.sql` | `dd63d36d4303a11b488a031e17f3f08b4e2db57a` | Applied successfully by Flyway in the targeted packaged test |

The Sonar completion log contains no rule/message/location; developer handoff requests the single Problems entry for the Java file. `git diff --check` is clean.

### Task 10.1 Completed: Focused Tests and Combined Build

The developer identified S5961 in the combined pagination/correction/replay test. It is now split into an independent navigation/empty-direction test and a historical replay-after-correction test. Every assertion is retained. Navigation depends only on unchanged creation order and root identities, so it is valid before or after the lifecycle test. The strict binding/framework test remains separate; no assertion-limit configuration changed.

A temporary user service `opencode-party-tests-api.service` runs `podman system service --time=0 unix:///tmp/opencode/party-registry-tests.sock`. It is test-owned, transient, and does not modify the user's normal Podman service. With `DOCKER_HOST=unix:///tmp/opencode/party-registry-tests.sock QUARKUS_HTTP_TEST_PORT=0`, `./gradlew test` succeeds (825 existing cases up-to-date) and `./gradlew build` passes **17 packaged cases, zero failures/skips**, in 4m. The temporary service must be stopped when execution finishes or pauses.

Final `PackagedRootContractIT.java` blob `3f6e731b28667808460a70da26a9ccabbcc2a877` has zero Sonar issues/hotspots (2026-09-20 14:09:34.747) and zero JDTLS problems (14:09:30.650). Reachability review confirms explicit `@RegisterForReflection` on `PartySummaryResponse`, `PartyUpdateRequest`, and its deserializer; lifecycle codecs use explicit JSON trees and do not register Domain/ORM hierarchies broadly. Existing integration/native source-set wiring covers this test class; actual native execution is still required by task 11.4.

## Task 10.2: Source and Packaged OpenAPI Equality

`OpenApiContractTest` now pins the exact method sets and operation IDs of the six root operations, successful 200 declarations, and process echoes. Existing focused checks retain all request/schema/error-example semantics. The packaged root suite parses `/q/openapi` and the approved YAML independently, requiring no parser messages and deep equality of the five root PathItems, six root/shared schemas, seven context/mutation parameters, and eight shared/root error response components. These comparisons include descriptions, replay semantics, headers, examples and references; no source contract edits were necessary because the wire behavior matches the approved declarations.

Full `test` and `build` pass with the persistent test-owned Podman endpoint (output `tool_0c041efff001sdqI753j4BI07O`, 3m7s and 1m18s). Final zero-finding IDE revisions:

| Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|
| `src/test/java/com/alexastudillo/partyregistry/OpenApiContractTest.java` | `053073de59dd8692270d85865fbb4c45120b6b98` | 2026-09-20 14:17:35.123 | 14:17:34.971 |
| `src/integrationTest/java/com/alexastudillo/partyregistry/PackagedRootContractIT.java` | `f3747fe291ecbbf86b1ba0bd210bcabbaf1b3c03` | 2026-09-20 14:17:09.611 | 14:22:18.480 |

## Task 10.3: Resource Boundary and Workflow Ownership

ArchUnit now additionally prevents API resources from depending directly on Application I/O ports or Domain policies, enforcing delegation through use cases. Existing strict layer rules, Domain framework exclusion, Mutiny-only Application allowance and all-package cycle checks remain intact. Direct review of `PartyResource` confirms all six asynchronous methods explicitly return shared envelopes containing only summary/detail DTOs, mapping before `ResponseManager`. `ChangePartyLifecycleUseCase` owns replay equivalence, absence/version precedence and event intent; Domain owns lifecycle/evidence rules; Infrastructure owns scoped sessions, locks, guarded writes and transaction acceptance. Root use-case, transaction, HTTP, and signature tests exercise these boundaries rather than relying solely on compilation.

Full `test` and `build` pass (output `tool_0c0481364001vhBeBYNqB6fMZi`, 3m20s and 1m13s, persistent temporary Podman endpoint). The final architecture revisions have zero Sonar issues/hotspots and zero JDTLS problems:

| Test Java file | Saved Git blob | Sonar completion | JDTLS validation |
|---|---|---|---|
| `architecture/ArchitectureRules.java` | `18e65d12e7b160e06b1336bfa243a44c92dda3b1` | 2026-09-20 14:24:13.103 | 14:24:13.350 |
| `architecture/CleanArchitectureTest.java` | `030d86e8786f4b94c0510bb5d175c7b327024774` | 2026-09-20 14:29:03.235 | 14:29:03.618 |

## Task 10.4: Consumer and Operational Documentation

README now advertises all six root operations, V5, and the linked `docs/operations/root-party-contract.md` guide. The guide documents tested request/error precedence, type-safe response shapes, lifecycle/evidence/replay rules, exact pagination and live continuation, external independent cursor key provisioning and staged rotation, positive timeout settings, bounded Unicode scan cost, the observed 2,000-row timing/batch evidence, checked shared-library Integer capacity, bounded telemetry, compatible rollout and retained-data rollback. It also records standalone IDE evidence requirements and the runtime-only persistent Podman endpoint workaround. No production secret values or remote Sonar prerequisites were added. Configuration names/defaults and examples were reviewed against the implementation, approved contract and recorded test outcomes. `git diff --check` and `openspec validate complete-parties-contract --strict` pass.

## Tasks 11.1 and 11.5: Final Java and Local Analysis Audit

The [final inventory](final-java-inventory.md) pins 143 saved Java revisions: 77 main, 64 test and two integrationTest. The inventory was reconciled programmatically through Git: 143 entries, no missing/extra paths and no hash mismatches. All files were individually reopened in the restored Standard-mode Java 25 workspace. There are 137 fresh completed analyses in the final wave, with zero issues/hotspots and no unresolved-type warnings, plus six unchanged already-open revisions whose final saved-file analysis is recorded above. All corresponding JDTLS validations are zero. Sonar remains standalone 5.7.0 with the same 571 active Java rules; no rule exclusions, warning suppressions or remote Quality Gate claims were introduced.

Scoped content audit of the exact inventory found no numeric month constructor arguments, literal valid date parsing or `@SuppressWarnings`. The sole date-shaped string is intentionally invalid February 30 input in `PartyQueryParametersTest`. Class/type responsibility Javadoc and non-obvious contracts were reviewed per implementation task and reconciled with the final source set. The known constructor mismatch and missing temporary Gradle initialization-script incidents have explicitly resolved evidence rather than being dismissed as compiler/IDE disagreement.

## Tasks 11.2 and 11.3: Final JVM Confirmation

The final Java revision has **827 JVM cases and 18 packaged integration cases, zero failures and zero skips**, from the successful complete execution recorded at task 10.3. Subsequent changes were documentation/evidence only. Final `./gradlew test` and `./gradlew build` confirmations both succeed (3s and 4s, tasks correctly up-to-date), using `DOCKER_HOST=unix:///tmp/opencode/party-registry-tests.sock QUARKUS_HTTP_TEST_PORT=0`. The actual preceding full runs, rather than up-to-date output alone, establish runtime coverage. No compiler warnings were added.

## Task 11.4: Initial Native Build Memory Failure (Resolved Below)

The native build was attempted with the exact requested task plus explicit runtime/resource overrides:

```shell
DOCKER_HOST=unix:///tmp/opencode/party-registry-tests.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew buildNative -Dquarkus.native.container-build=true -Dquarkus.native.container-runtime=podman -Dquarkus.native.native-image-xmx=3g
```

The host had approximately 3.5 GiB available physical memory before the attempt (15 GiB total, 11 GiB used, swap already in use). The builder image was `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`, image ID `9c14641b29966b17554268a5c75f2471ca5e9d1088e87679c314c11f22d1dd61`, running Mandrel 25.0.4.1-Final. It reported a 2.86 GB effective heap from `-Xmx3g` and 12 threads. After initialization/analysis it terminated with `java.lang.OutOfMemoryError: Java heap space`, exit code 3. Gradle failed after 9m51s. This is a compiler-memory failure, not evidence of successful native reachability or serialization.

The build also displayed the requested task's deprecation notice and dependency-origin native-image option/duplicate-entry warnings; none was suppressed. `testNative` was not run because this attempt produced no accepted current executable. Any previously existing native runner is not evidence for this revision. Tasks 11.4 and final scenario/native acceptance 11.6 remain incomplete; the confirmed progress is **51/53**.

The test-owned transient Podman service was stopped after the failed attempt. `systemctl --user show opencode-party-tests-api.service` confirms `LoadState=not-found` and `ActiveState=inactive`. Normal user service configuration was never changed. A retry should recreate the temporary persistent endpoint as needed and use a larger compiler heap after sufficient host memory is available; it must rerun both the native build and native integration contracts. Source Java revisions remain the audited inventory, and `git diff --check` remains clean.

### Task 11.4 Completed: Native Build and Contracts Pass

On the developer-requested retry, available host memory was approximately 8.1 GiB. The transient persistent Podman endpoint was recreated and the compiler heap increased to 6 GiB:

```shell
DOCKER_HOST=unix:///tmp/opencode/party-registry-tests.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew buildNative -Dquarkus.native.container-build=true -Dquarkus.native.container-runtime=podman -Dquarkus.native.native-image-xmx=6g
DOCKER_HOST=unix:///tmp/opencode/party-registry-tests.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew testNative -Dquarkus.native.container-build=true -Dquarkus.native.container-runtime=podman -Dquarkus.native.native-image-xmx=6g
```

Both commands succeeded: native build 3m23s, native test task 46s. Mandrel 25.0.4.1-Final completed all eight stages, with peak RSS 5.64 GB and 122.41 MB total executable file size. The generated descriptor identified `type=native` and `path=party-registry-service-1.0.0-SNAPSHOT-runner`, eliminating ambiguity with older binaries. Dependency-origin experimental/deprecation/duplicate-entry messages remained visible; no application compiler warnings or native compatibility failure was hidden.

`build/reports/tests/testNative/index.html` reports **18 tests, zero failures, zero skips**. `PackagedRootContractIT` passed its four native cases covering strict/shared errors, explicit null pagination, root correction, all lifecycle actions and both snapshot subtypes, and published OpenAPI equality. `PartyLifecycleRestartIT` passed its actual native-process relaunch case with retained PostgreSQL data and unchanged four/four snapshot/event counts. The earlier 3 GiB heap failure is resolved.

## Task 11.6: Final Scenario Reconciliation

The [scenario matrix](scenario-evidence.md) maps all **76 EARS scenarios** (21 queries, 14 corrections, 22 deactivation/archival, 19 activation) to passing Domain/Application/database/HTTP checks and applicable packaged/native contracts. The six actual resource signatures, source/published OpenAPI, migration behavior, rollout/rollback prerequisites and final IDE inventory are linked to their evidence.

Reconciliation identified one evidence gap: retained-row database assertions had not included a seeded nationality. `PartyRootMutationPersistenceTest` now seeds a real nationality row through DML and compares its entire JSON representation with details/identifiers before and after every supported correction/transition. No production or packaged/native test code changed. The enhanced test's final blob is `f58dffcbe76c28200e7b1397e6050dd5080fdba7`; Sonar completed with zero issues/hotspots at 2026-09-20 15:29:04.221, and JDTLS reported zero problems at 15:29:27.922. The final inventory was updated accordingly.

Full JVM `test` and `build` passed again after the fixture change (output `tool_0c083aaa9001D80dEZuWc0tTdj`, 2m57s and 1m12s), retaining **827 JVM and 18 packaged JVM cases, zero failures/skips**. The native acceptance above covers the unchanged production and native integration sources. Historical failures and their resolutions remain recorded; none is counted as passing verification.

The final matrix was checked against every `#### Scenario` title in the approved specs: 21/21 queries, 14/14 corrections, 22/22 deactivation/archival and 19/19 activation, no omissions or extra names. The final 143-file hash inventory was reconciled again with Git and has no missing paths, extra paths or hash mismatches. `openspec validate complete-parties-contract --strict` and `git diff --check` pass. The transient test API service was stopped and confirms `LoadState=not-found`, `ActiveState=inactive`.

**Final implementation status: 53/53 tasks complete.** The change remains available for review and explicit archival. External production cursor key provisioning, normal deployment credentials and compatible rollout remain documented operational prerequisites, not missing implementation work.
