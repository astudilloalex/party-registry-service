# Party Identifier Contract Implementation Evidence

## Task 1.1: Current standalone Java analysis baseline

The baseline was collected on 2026-10-03 before any production or test Java edits. The existing active Antigravity project window was reused to open `PartyIdentifierResource.java`; opening the saved file triggered fresh Sonar and JDTLS analysis without changing its contents.

| Item | Observed evidence |
|---|---|
| IDE | Antigravity IDE 1.107.0, Linux x64, active `party-registry-service` workspace |
| Sonar extension | Installed `sonarsource.sonarlint-vscode` 5.7.0, Linux x64 |
| Mode/configuration | Standalone; inspected user and repository settings contain no connected-mode binding or Sonar rule override |
| Sonar runtime | Bundled Java 21.0.12 runtime, independent of project source level |
| Effective local rule count | Fresh analysis configuration reports 571 Java rules and 30 secrets rules |
| Project JDK | `/usr/lib/jvm/java-25-openjdk-amd64`; shell Java reports OpenJDK 25.0.4.1 |
| Analysis source level | Java 25, preview disabled |
| Project resolution | Fresh Sonar classpath contains project `bin/main`, `bin/test`, `bin/integrationTest` and Gradle-resolved Quarkus 3.33.3.1 dependencies |
| Java language support | Installed Red Hat Java 1.55.0; current JDTLS session initializes in Standard mode and completes Gradle import/build |
| Baseline file | `src/main/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierResource.java` |
| Saved Git blob | `5650958396cb7a9658b6e608bb391a3b000d6e58` |
| Fresh Sonar execution | 2026-10-03 10:56:45.435–10:56:48.315, timestamps as recorded in the IDE log |
| Fresh Sonar result | Full 1/1 Java file analyzed; 0 issues and 0 Security Hotspots reported |
| Fresh JDTLS result | Validation at 10:56:46.567 reports 0 problems for the same baseline file |

Sonar source: `~/.config/Antigravity IDE/logs/20261003T081856/window1/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log`, lines 517–547. JDTLS source: `~/.config/Antigravity IDE/User/workspaceStorage/fc73fbd3bb7d62718087c857851a4deb/redhat.java/jdt_ws/.metadata/.log`, current session lines 17859–17988 and fresh baseline lines 18414–18427. That workspace's `workspace.json` identifies this repository.

The fresh baseline reports the analyzer runtime diagnostic `No workDir in SonarLint`; this is separate from source findings and JDTLS diagnostics. The baseline contains no unresolved-import or missing-binary warnings. Earlier startup analyses in the same log did have unresolved-model warnings and are not used as acceptance evidence. No settings, analysis thresholds, or suppressions were changed.

This is verified standalone local analysis, not a server Quality Gate or a claim of server-equivalent analysis coverage. The zero-Hotspot count is the analyzer's reported result, not proof that connected-mode-only checks ran. No feature behavior, test execution, migration, or native result is inferred from this baseline.

## Task 1.2: Effective installed rule research

The installed analyzer archives were inspected directly without changing IDE settings or rules. `sonarjava.jar` includes a 550-rule `Sonar_way_profile.json`; `sonarjavasymbolicexecution.jar` includes a 21-rule profile. Their 571 combined defaults agree with the fresh runtime Java-rule count, and the inspected user/repository settings contain no local Sonar overrides. Archive metadata and bundled HTML descriptions were read for the relevant rules; S2259 and S2095 are supplied by the symbolic-execution archive, not the primary Java archive.

| Active default rule | Observed parameter or preventive practice |
|---|---|
| S3776: cognitive complexity | Installed RuleProperty default `Threshold=15`; keep workflow/policy helpers focused and avoid deeply nested reactive callbacks |
| S107: too many parameters | Installed defaults `max=7`, `constructorMax=7`; group semantically related inputs and avoid unnecessary collaborators rather than suppressing the rule |
| S2259: null dereference | Check nullable results once, retain them in local variables, and avoid unchecked nullable unboxing; no configured override |
| S2201: ignored pure return values | Use returned immutable date/string transformations and return/compose every business pipeline |
| S5778: runtime-exception assertions | Prepare fixtures, dates, and expected input outside the lambda so only the intended invocation is tested |
| S6213: restricted identifiers | Avoid `record`, `yield`, and `var` as variable/method names, including persistence and test helper parameters |
| S2095: resource cleanup | Close AutoCloseable resources with supported ownership; reactive session cancellation/cleanup requires explicit tests as well as static analysis |
| S2139: log-or-rethrow | Avoid logging an exception at every translation boundary; preserve the shared sanitized global handler |
| S2629: eager logging arguments | Use bounded parameterized observation instead of concatenating evaluated request content |
| S2699: tests need assertions | Assert observable outcomes and unchanged loser state; do not treat runnability or a subscriber-only completion as behavior proof |
| S2925: sleeps in tests | Use bounded Awaitility/reactive assertions and barriers instead of Thread.sleep |
| S2245 and S2068: cryptography and credentials | Retain supported secure key material and no production hardcoded credentials; never log values or keys |

S3776 and S107 numeric defaults were verified with `javap -v` against the installed check classes, not inferred from current web documentation. Other reviewed rules above have no task-specific parameter override in the effective inspected configuration. Rule membership was checked in each archive's bundled profile; the actual fresh execution count remains the runtime corroboration. This does not claim availability of server-only taint, project architecture, or commercial dataflow checks.

Archive SHA-256 identities:

- `sonarjava.jar`: `fd7fff8651da43673e62c4295bffd292f8b1047aac865675081c5b7aca22ca15`.
- `sonarjavasymbolicexecution.jar`: `51421c751a0b3f6f3b57dfbdbb3db77ab85904b8b55860410b9723c5a0336423`.

Both are under `~/.antigravity-ide/extensions/sonarsource.sonarlint-vscode-5.7.0-linux-x64/analyzers/`. The extension manifest confirms that `sonarlint.rules` represents changes from default activation/parameters. No rule was disabled, threshold relaxed, or source suppression introduced.

## Task 1.3: Baseline attempt and execution deadline

Observed prerequisites before feature Java edits:

- Gradle 9.3.1 with launcher/daemon Java 25.0.4.1.
- Quarkus BOM/plugin 3.33.3.1 and declared published shared response module `api-response-quarkus-errors:1.0.0-SNAPSHOT`.
- Podman 5.4.2 and the accessible runtime socket path `/run/user/1000/podman/podman.sock`.
- Installed PostgreSQL 18/18.4/18-alpine and Java-25 Mandrel builder container images. Their presence is not native execution evidence.
- Current migration history contains V1–V7; their SHA-256 identities were collected without changing migrations.

The following existing-suite baseline was attempted:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test
```

It reached `:test`, but the shell execution deadline of 240000 milliseconds expired. The Gradle daemon log reports client disconnection, build cancellation while executing `:test`, and test executor termination with exit value 143. This is an interrupted baseline, not a confirmed feature failure, successful suite, or TDD red result. An existing XML result timestamp predates this attempt and is not used as current evidence.

Source: `~/.gradle/daemon/9.3.1/daemon-42805.out.log`, cancellation around lines 4616–4622 and executor termination at line 4711. Task 1.3 remains unchecked pending guidance on rerunning the baseline with a longer deadline and diagnostic progress. Baseline build, full results, resolved shared-library characterization, and task 1.4 are not complete.

Migration SHA-256 baseline:

| Migration | SHA-256 |
|---|---|
| V1 | `7da1a66b7cddb2389da0032c5f3ccef8049e75eaa77783eda37e89758235e4d7` |
| V2 | `9dd1164d81535fcebd9b76851e910264a116c8fbf37acdd05a57960f40b3e723` |
| V3 | `8ad6ac7d0b7d461c617d5ad48e9ed9f0fe3fab25b2019c0ef22b4cc6a9f02d22` |
| V4 | `01a3268e611b67e85de112e7e343ce28e3d66eb28cc13982b3cc2f07a3e08734` |
| V5 | `e26b9f31f7d8f9da8d8501e9c47d35a32d2313ddde97e3ad3188aec7ce266e50` |
| V6 | `eddda2f94edaab77d72b171c263a827bc010798db5c1d6ee64684664f1bbd405` |
| V7 | `775bde73b7344fbc032bcc297c2ba10ee2c24fa9c676f4069bedab8156346965` |

## Task 1.3: Completed reproducible baseline

On 2026-10-03 the baseline was rerun with a 1,200,000-millisecond command deadline and diagnostic progress. Repository HEAD was `4cfa594a9d6e480837a136bf286b3b749c689c22`; the initial working tree was clean. No production or test sources were edited.

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --info --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --info --console=plain
./gradlew dependencyInsight --dependency api-response-quarkus-errors --configuration runtimeClasspath --console=plain
podman run --rm --pull=never --entrypoint native-image quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25 --version
```

| Check | Actual result |
|---|---|
| Standalone baseline test command | BUILD SUCCESSFUL in 5m 11s; full output `tool_102927bd3001B3llZ16wQPmFwi` under `~/.local/share/opencode/tool-output/` |
| Baseline build | BUILD SUCCESSFUL in 9m 51s; full output `tool_102978ea7001I2MP666zEfBVgx` in the same directory; `:test` and `:quarkusIntTest` executed |
| Latest full test report after build | `build/reports/tests/test/index.html`: 1,364 tests, 0 failures, 0 skipped |
| Packaged JVM report | `build/reports/tests/quarkusIntTest/index.html`: 28 tests, 0 failures, 0 skipped, including existing process-restart tests |
| Java / Gradle / Quarkus | OpenJDK 25.0.4.1; Gradle 9.3.1; Quarkus 3.33.3.1 |
| Database/runtime access | Podman 5.4.2, socket `unix:///run/user/1000/podman/podman.sock`; actual PostgreSQL 18.4 containers, migrations, reactive tests, and packaged HTTP tests executed |
| Native tooling availability | Existing `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25` image `ad96e4466e17` successfully ran `native-image --version`: Mandrel-25.0.4.1-Final, Java 25.0.4.1. This is availability evidence, not native compilation/integration acceptance |
| Migration identities | All V1–V7 SHA-256 values match the table above; production migration directory contains exactly V1–V7, so V8 remains unused |
| Shared dependency selection | `dependencyInsight` resolves `com.alexastudillo.libraries:api-response-quarkus-errors:1.0.0-SNAPSHOT`, runtimeElements, Java 25 |
| Library-only pagination characterization | Existing `SharedResponsePaginationTest`: 5 tests pass; null cursors are omitted by default, flat counts/status/data are retained, checked integer overflow fails |
| Service-managed pagination characterization | Existing `PartyPaginationSerializationTest`: 4 tests pass; all five page fields and explicit null directions are present; single-item/error/unrelated objects preserve null omission; both customizer orders work |

The four library JARs copied into `build/quarkus-app/lib/main` by the packaged build are pinned below. Their SHA-256 identities match the corresponding installed Maven-local JARs:

| Module (1.0.0-SNAPSHOT) | SHA-256 |
|---|---|
| api-response-contract | `cc76e1036054bbfb1c7e0c99b483be33aeba13bab7169e52f6032eccb45fe9e8` |
| api-response-core | `525521ec6ae514f5db5f2637b3f00e7b843b0af76126b6073f6dc06a8f7f0b1a` |
| api-response-quarkus | `15dcd67726b7983f5a0baae65d8e92590f86b91151f2e3ffc14c0dd55cc01996` |
| api-response-quarkus-errors | `20a74cf3bb715b51fa949db9310d698e62fc6baa1839502c1d5999e8d8ec1fa2` |

### Baseline runtime diagnostics

The passing build is not a claim of warning-free runtime execution. The latest test XML records one blocked-thread warning in the existing `IdentifierSchemeConcurrencyContractTest` at 11:29:24.380: 5,051 milliseconds against a 2,000-millisecond limit. The captured stack runs through Groovy compilation, RestAssured `JsonPath.getInt`, `IdentifierSchemeHttpAssertions.success:40`, and `IdentifierSchemeConcurrencyContractTest:235` inside `UniAsserter`; it is test assertion work on a Vert.x event loop. This is recorded separately from production/compiler/IDE findings; no checker or logging threshold was changed. Source: `build/test-results/test/TEST-com.alexastudillo.partyregistry.api.resource.IdentifierSchemeConcurrencyContractTest.xml:188–258`.

Existing duplicate-meter warnings and packaged Dev Services build-time/runtime configuration warnings also occur. The latter concern `quarkus.datasource.devservices.enabled`, `image-name`, and `reuse`. Expected failure-injection logs are not test failures. These baseline observations are not feature TDD red evidence. Old `testNative` XML files predate this session and are excluded from current native evidence.

## Task 1.4: Evidence convention and planned coverage

`scenario-evidence.md` maps all 27 exact requirement titles and 87 exact scenario titles (Q: 8/21; M: 11/38; L: 8/28) to planned test boundaries and numeric task references. Every scenario evidence entry is Pending; baseline tests are not claimed as implementation evidence. `java-inventory.md` defines the four source-set inventory, nested-type coverage, exact saved Git blob identities, separate current Sonar/JDTLS evidence, and focused/full test evidence. The changed-Java inventory is empty because no Java sources have been modified.

For each subsequent increment, append a subsection with:

- task IDs and scenario IDs;
- changed paths/types and saved blobs;
- red command, expected missing behavior, actual result, and output location;
- smallest conforming implementation and green command/results;
- refactor details and retained passing evidence (or an explicit statement that no refactor was needed);
- separate Sonar completion and JDTLS diagnostics for the saved revision;
- required test/build outcomes and runtime diagnostics;
- native evidence when required by that task, without substituting JVM output.

An environmental interruption or unresolved prerequisite is recorded as a blocker, never as TDD red or acceptance. Update the task checkbox only after all required verification is available. Final scenario acceptance requires links to actual tests, not just planned boundaries.

## Task 2.1: Executable contract red and unresolved IDE finding

Added `src/test/java/com/alexastudillo/partyregistry/PartyIdentifierOpenApiStructureTest.java` with responsibility Javadoc and five structural/semantic tests. No production Java or approved OpenAPI content has been changed. The tests target canonical selectors, operation/context declarations, identifier-specific If-Match/replay headers, closed nullable metadata bodies, strict action defaults, historical listing/cursor capacity, and lifecycle/primary semantics. A separate identifier reason schema is planned to avoid coupling future identifier binding to a generic schema name.

### Red evidence

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.PartyIdentifierOpenApiStructureTest --console=plain
```

The focused command compiled successfully and executed all five tests; each failed on an expected missing contract rule (raw-length semantics, canonical identifier path, creation body precedence, canonical scheme query, and the missing `PartyIdentifierIfMatch` declaration). It completed in 15 seconds. Initial red blob: `a331b109bfd4cae86498daae21b17d8ae0414dd4`. The test was then corrected to characterize the exact unchanged generic If-Match description and assert consistent page semantics without requiring an implementation-specific isolation keyword. Current saved blob: `018733569637d39fb05332976b5aa4eafd02dcad`.

The required full post-Java-change command executed on that saved blob:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
```

It completed in 5m 22s with 1,369 tests and exactly the five new expected contract failures. Full output: `~/.local/share/opencode/tool-output/tool_102b027fb001a28ktCPl7enDuI`. Current XML: `build/test-results/test/TEST-com.alexastudillo.partyregistry.PartyIdentifierOpenApiStructureTest.xml`; report: `build/reports/tests/test/index.html`. Existing cleanup diagnostics (`HR000090` dropped exceptions during timeout/cancellation tests) appeared separately from test failures and are not suppressed. No green/refactor result or build acceptance is claimed for task 2.1.

### Current saved-revision IDE evidence and blocker

Opening the saved test through the installed Antigravity CLI led to fresh analysis in the current repository window:

```bash
/opt/antigravity-ide/bin/antigravity-ide --reuse-window "/home/alex/Documents/Development/backend/quarkus/party-registry-service/src/test/java/com/alexastudillo/partyregistry/PartyIdentifierOpenApiStructureTest.java"
```

- Sonar source: `~/.config/Antigravity IDE/logs/20261003T114758/window1/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log:44–74`.
- Actual analyzed input is the new test, classified `[test] [java]`, Java 25, preview disabled, with 571 Java rules and resolved project test libraries.
- Full 1/1 analysis completed at 11:48:46.833 and reports **1 issue and 0 Security Hotspots**. The log contains no rule key, source location, or finding message; those details must be obtained from the IDE Problems view/current diagnostics before a targeted fix can be made.
- Separate JDTLS source: `~/.config/Antigravity IDE/User/workspaceStorage/fc73fbd3bb7d62718087c857851a4deb/redhat.java/jdt_ws/.metadata/.log:18738–18745`. Validation at 11:48:37.964 reports **0 problems** for this test after resolved Gradle import/build.
- No rule configuration, suppression, or nullability setting was changed. `git diff --check` passes.

Implementation initially paused on task 2.1 pending the finding details for this saved revision. The developer subsequently supplied the assertion-count finding, resolved below. Task 2.1 remains unchecked because contract implementation and green verification are pending.

### Assertion-count finding resolved

The developer's Problems-view screenshot identifies `closesIdentifierBodiesAndPreservesNullableMetadataAndStrictActionDefaults` and the message: "Refactor this method to reduce the number of assertions from 27 to less than 25." The rule key was not shown in the screenshot and is not inferred as observed evidence.

Split the test by responsibility into `closesIdentifierBodiesAndPreservesNullableMetadata` and `closesOptionalActionBodiesAndPreservesStrictDefaults`. Every assertion and helper call is retained. Each method is below the configured assertion limit; no assertion was removed, hidden behind a new helper, or suppressed. The saved test blob is now `3d2dbbe79281de7135980ddaef464e80a8613258`.

Current saved-revision verification:

- Sonar: full 1/1 test-file analysis completed at **2026-10-03 12:01:50.670**, reporting **0 issues and 0 Security Hotspots**, with the same 571 Java rules and Java 25 configuration. Source: `~/.config/Antigravity IDE/logs/20261003T114758/window1/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log:75–105`.
- JDTLS: validation at **2026-10-03 12:01:50.713** reports **0 problems** for the same saved test. Source: `~/.config/Antigravity IDE/User/workspaceStorage/fc73fbd3bb7d62718087c857851a4deb/redhat.java/jdt_ws/.metadata/.log:18762–18772`.
- `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain`: **1,370 tests, 6 expected contract failures**, completed in 5m 10s. Output: `~/.local/share/opencode/tool-output/tool_102b92e50001BOKpxOgEEZxJLg`.
- `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain`: **1,370 tests, the same 6 expected contract failures**, completed in 4m 55s and stopped at `:test`. Output: `~/.local/share/opencode/tool-output/tool_102be04b4001uQ6ec0DhcbCVVj`. No passing build or packaged execution is inferred.
- Splitting the original test exposes a sixth failure for the missing `PartyIdentifierReasonRequest` schema; that assertion already existed but was previously behind an earlier failure. The other failures retain their missing-contract causes. Production behavior and OpenAPI remain unchanged during this refactor.
- `git diff --check` passes; no compiler warnings were emitted for the modified test.

The local-analysis blocker is resolved. The structural contract remains in its recorded TDD red state until task 2.1 implements the missing contract and passes its verification.

### Task 2.1: Contract green and completed verification

Completed the eight identifier operations in `docs/contracts/party-registry.openapi.yaml`: explicit trusted context/ownership and process echoes, per-operation syntax/business precedence, independent If-Match, durable exact creation/action key scope, strict nullable metadata and optional action objects, full lifecycle matrix, inclusive validity/primary rules, historical filter/cursor behavior and supported count range. Added identifier-only header/navigation components and a dedicated reason schema; generic Party-version and unrelated pagination components retain their original contracts. No production Java was changed.

The original six failing structural tests pass after the contract changes. Additional characterization now pins every matrix row, terminality, action precedence and optional request-schema references, bringing the class to nine tests. The earlier assertion-count refactor is retained. No additional production refactor was needed for this static-contract increment.

Saved blobs:

- Contract: `87ed66c197d6b3c5f1716f6fa4b018e3e4a1ac8d`.
- `PartyIdentifierOpenApiStructureTest.java`: `76d17934183320a020b204e414d1cd13f54fd7d9`.

Focused green command (successful in 8 seconds):

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.PartyIdentifierOpenApiStructureTest --tests com.alexastudillo.partyregistry.OpenApiContractTest --tests com.alexastudillo.partyregistry.IdentifierSchemeOpenApiStructureTest --console=plain
```

This includes parser checks with no unresolved-reference messages and compatibility assertions for existing Party/initial-identifier/scheme contracts. Full verification then executed:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Full test: BUILD SUCCESSFUL in **5m 43s**, **1,373 tests, 0 failures, 0 skipped**.
- Build: BUILD SUCCESSFUL in **2m 29s**, unit tests up-to-date, **28 packaged JVM tests, 0 failures, 0 skipped**. Existing packaged served/source OpenAPI equality checks execute with the changed static contract.
- Combined output: `~/.local/share/opencode/tool-output/tool_102ceeaff001ucsFqIolF3G3Ur`; HTML/XML reports remain under `build/reports/tests/` and `build/test-results/`.
- Current Sonar: full 1/1 test-file analysis at **12:24:58.629**, **0 issues and 0 Hotspots**, Java 25 and 571 active Java rules. Source: current `20261003T114758/.../SonarQube for IDE.log:128–158`.
- Current JDTLS: **0 problems** at **12:25:24.409** after explicit validation; source: the existing project `.metadata/.log:18786–18796`.
- No new compiler warnings. Existing controlled failure/cleanup diagnostics remain recorded separately and unsuppressed; they do not fail the suite. `git diff --check` passes.

Task 2.1 is complete. These structural and packaged-document checks do not claim implementation of the seven missing resource operations or their native behavior; subsequent tasks provide that evidence.

## Task 2.2: Schema-valid examples and scoped failure declarations

Added `PartyIdentifierOpenApiExamplesTest.java` before completing examples/schemas. An initial compile error from a raw Swagger schema chain was corrected by retaining a `Schema<?>` local; compilation is not counted as behavioral red. The subsequent focused command executed six tests and all six failed on missing examples/framework declarations/scoped codes/closed output schemas. After adding the documented behavior, the same class passes.

Implemented safe examples for all eight successes (original pending version-0 creation, complete empty/nonempty pages, historical detail, metadata correction, verification, withdrawal and primary selection). Added explicit 405/415/500/503 declarations, scoped 404/409/412/422 components, stable field-specific 400 examples, accepted Process-Id response references, closed safe item/envelope schemas and version/count bounds. Creation uses an explicit 201/PENDING_VERIFICATION/version-0 composed schema. Error examples contain exactly status/code; optional reasons and protected material are excluded from success examples. YAML documentation dates remain wire examples; no valid numeric-month Java fixtures were introduced.

Four existing `OpenApiContractTest` additional-registration response-reference expectations required adjustment from broad components to approved identifier-specific components. The old assertions failed before that scoped compatibility update. Natural-person/legal/root/activation/scheme expectations remain intact. The new examples use the existing shared schema validator without adding test CDI fixtures or production types.

Saved blobs:

- Contract: `b9fa24050ca666f1c07e5cee714f83e0ad7ec438`.
- New example test: `b0b7995483b63ee4bb1eff29e4c171aa29461732`.
- Existing general contract test: `7bd4e4408c6fc8063f483adcb2538d558b1e8d30`.

Red command (six tests fail on missing contract behavior; seven seconds):

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.PartyIdentifierOpenApiExamplesTest --console=plain
```

Focused green command (49 checks pass; 12 seconds):

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.PartyIdentifierOpenApiExamplesTest --tests com.alexastudillo.partyregistry.PartyIdentifierOpenApiStructureTest --tests com.alexastudillo.partyregistry.OpenApiContractTest --tests com.alexastudillo.partyregistry.IdentifierSchemeOpenApiExamplesTest --console=plain
```

Full verification on those saved revisions:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Test: BUILD SUCCESSFUL in **7m 23s**, **1,379 tests, 0 failures/skips**.
- Build: BUILD SUCCESSFUL in **3m 5s**, unit tests up-to-date, **28 packaged JVM tests, 0 failures/skips**.
- Combined output: `~/.local/share/opencode/tool-output/tool_102e2ddbe0016GoHGbGzKIYG0S`; current HTML/XML reports under the normal build report directories.
- New example test Sonar: full 1/1 analysis **12:39:09.680**, **0 issues/Hotspots**, current Sonar `.log:234–264`; JDTLS **0 problems** at **12:44:43.733**, project `.metadata/.log:18828–18838`.
- Updated general contract test Sonar: full 1/1 analysis **12:45:55.484**, **0 issues/Hotspots**, current Sonar `.log:265–295`; JDTLS **0 problems** at **12:45:55.397**, project `.metadata/.log:18840–18853`.
- No suppressions/configuration changes or new compiler warnings. Existing controlled failure/cleanup diagnostics remain unsuppressed. `git diff --check` passes.

Task 2.2 is complete. Runtime implementation, API code translation and native operation coverage are still required by later tasks; static example validation is not substituted for those checks.

## Task 2.3: Controlled fixture and safe assertion foundation

Added unit-only `PartyIdentifierFixtures` with Month-based UTC dates, a fixed microsecond-precision clock, independent natural/legal owner IDs, synthetic protected material and detached safe results. The legal fixture initially omitted its required incorporation country; its characterization test exposed that setup defect, corrected with the existing EC domain fixture convention. This is a fixture defect, not a new business TDD red result or production-policy change.

Added pure `PartyIdentifierContractAssertions` under `src/contractTest/java` for exact safe item/error envelopes and complete null-aware/count-bounded pages. It has no CDI annotations or production test fixture bindings. Unit-only fixture tests characterize both owner types and existing masked DTO mapping; adversarial assertion tests reject omitted cursor fields, inconsistent/overflowing counts, HTTP/body status disagreement and leaked error payloads. These checks establish test infrastructure and do not claim runtime implementation of identifier operations.

Focused verification (four tests pass; eight seconds):

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.testing.PartyIdentifierFixturesTest --tests com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertionsTest --console=plain
```

Required full verification:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Test: BUILD SUCCESSFUL in **6m 12s**, **1,383 tests, 0 failures/skips**.
- Build: BUILD SUCCESSFUL in **2m 44s**; shared assertions compile for `integrationTest`, **28 packaged JVM tests pass**. Unit fixture classes remain outside packaged test output.
- Combined output: `~/.local/share/opencode/tool-output/tool_102f45dd30013W62502iRJHfJj`.
- `java-inventory.md` records the four exact saved blobs and individual completed full 1/1 Sonar analyses (all **0 issues/Hotspots**) and separate JDTLS validations (all **0 problems**), from current Sonar lines 349–472 and JDTLS lines 18873–18928.
- All type responsibility Javadoc is present; public helper contracts are documented. No new compiler warnings, suppressed findings, real credentials, native claims or production behavior changes.

Task 2.3 is complete; section 3 now starts Domain behavior TDD.

## Task 3.1: Checked independent versions and immutable audit preparation

Added the version/audit tests first. Initial compile-red identified the missing `next()` operation and neutral overflow violation. With an initial ordinary increment available, the tests reached executable red: maximum-version advancement produced the existing negative-version violation after wrapping instead of the required exhaustion classification. The focused run executed four tests with exactly that failure. Added the maximum guard before addition; the same tests then pass. There is no unchecked/wrapping implementation left in the saved code.

`PartyIdentifierVersion.next()` now advances exactly once and emits transport-neutral `PARTY_IDENTIFIER_VERSION_OVERFLOW` at Long.MAX_VALUE. It changes no prior value and introduces no framework/HTTP dependency. Existing immutable `AuditInfo.updated` is characterized using the controlled identifier fixture: creation attribution and prior aggregate/protected/verification identity remain intact while prospective modification audit uses the supplied actor/instant. Invalid/backward audit input leaves history unchanged. Concrete metadata/lifecycle/primary aggregate changes remain in tasks 3.2–3.4; this task establishes their checked version/audit foundation without adding a generic public aggregate setter.

Focused red/green command and final architecture check:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersionTest --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersionTest --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierTest --tests com.alexastudillo.partyregistry.CleanArchitectureTest --console=plain
```

Executable red completed in 16 seconds with 1/4 failures; final focused green/architecture completed in 14 seconds. Javadoc documents exhaustion. No further production refactor was needed.

Required full verification:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Test: BUILD SUCCESSFUL in **6m 15s**, **1,387 tests, 0 failures/skips**.
- Build: BUILD SUCCESSFUL in **2m 19s**, unit tests up-to-date, **28 packaged JVM tests pass**.
- Combined output: `~/.local/share/opencode/tool-output/tool_103032a120017btd4ZSJ9JcjYj`.
- Inventory records saved blobs for `PartyIdentifierVersion`, `DomainViolation`, and the new test, with individual completed Sonar **0 issues/Hotspots** and JDTLS **0 problems**. Domain source remains synchronous, independent of Mutiny/CDI/HTTP/persistence/shared responses.
- `git diff --check` passes; no new compiler warnings or suppressions. Native mutation behavior remains unclaimed.

Task 3.1 is complete.

## Implementation progress

- Task 1.1 baseline verification completed.
- Task 1.2 effective installed rule research completed.
- Task 1.3 reproducible baseline completed after the longer rerun; actual runtime warnings recorded separately.
- Task 1.4 evidence convention, empty changed-Java inventory, and complete pending scenario map created.
- Task 2.1 complete: contract structure/semantics implemented and verified with focused/full/package checks and saved-revision IDE evidence.
- Task 2.2 complete: safe success/error examples, scoped declarations and output schemas implemented and verified.
- Task 2.3 complete: controlled fixture and safe shared assertion foundation verified.
- Task 3.1 complete: checked version advancement and immutable audit foundation verified.
- Tasks 3.2–14.6 remain incomplete.
- Contract/test foundation and identifier version foundation implemented; identifier mutation routes and durable storage remain pending.
