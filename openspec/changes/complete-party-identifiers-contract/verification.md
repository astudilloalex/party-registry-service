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

## Task 3.2: Immutable partial metadata correction

Added `PartyIdentifierMetadataTest` before aggregate behavior. Initial compile-red identified missing `correctMetadata` and neutral lifecycle classification. The first immutable-copy implementation reached executable red: **11 cases, 5 failures** for unrepaired past-date issuer-only changes and terminal-state-before-exhaustion precedence. Those were behavior failures, not infrastructure setup failures. Added the state/date gates with the checked version foundation; all cases then pass.

`PartyIdentifier.correctMetadata` accepts three `FieldUpdate` values, the operation instant and accepted actor. Omission retains and null clears. Pending/verified state admission precedes target-version exhaustion, then raw submitted issuer and resulting retained/new validity rules. One supplied instant defines UTC expiration qualification and modification audit; expiration equality is inclusive. Past evidence can be coherently repaired/cleared, but issuer-only correction cannot bypass it. Identical correction increments once. Immutable copying retains identity/scope, protected-value object, primary flag, verification and creation audit; no Party/scheme or historical snapshot is touched. Future issuedOn is not newly prohibited when the resulting expiration is absent/coherent.

The internal copy builder is private; no generic public version/audit setter or framework dependency was introduced. Existing constructor restoration rules remain intact, including retained historical issuer representation. Public responsibility Javadoc covers omission/null, results and failures; nested builder responsibility Javadoc remains present.

Focused red and final green/architecture commands:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierMetadataTest --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierMetadataTest --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersionTest --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierTest --tests com.alexastudillo.partyregistry.CleanArchitectureTest --console=plain
```

Executable red completed in 19 seconds; focused green/architecture completed in 15 seconds. Full verification:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Test: BUILD SUCCESSFUL in **6m 3s**, **1,398 tests, 0 failures/skips**.
- Build: BUILD SUCCESSFUL in **2m 26s**, unit tests up-to-date, **28 packaged JVM tests pass**.
- Combined output: `~/.local/share/opencode/tool-output/tool_10315ac02001yW04KNar6MeiUv`.
- Saved aggregate: `4d6f0f1c69a12329260fd3ce82b5ddbb8c64d73b`; enum: `05ca298a7cdafbbc2d8f3282981377b4e2095e7e`; metadata test: `a667b6c361b8de9e585a1ad4e5b3fd618fa019a5`.
- Inventory links each saved file/type to actual completed Sonar **0 issues/Hotspots** and independent JDTLS **0 problems**. No new compiler warnings or suppressions; `git diff --check` passes. Existing cleanup diagnostics remain tracked separately.

Task 3.2 is complete. HTTP PATCH binding/orchestration/persistence remain tasks 9.1–9.3, not inferred from Domain green.

## Task 3.3: Lifecycle Domain green, final gate incomplete

Added `PartyIdentifierLifecycleTest` first with all 15 state/action matrix entries, each exercising both Party types, plus attribution, UTC-midnight/inclusive expiration, no-expiration qualification, past-date withdrawal, retained verification, state/exhaustion precedence and invalid repeats. Initial compile-red identified absent methods. The first immutable transition implementation reached executable red: **20 cases, 15 failures** on missing state gates, qualification and repeat rejection. Added the closed state matrix and validity checks; all cases pass.

`PartyIdentifier.verify` now requires pending state, checks next-version capacity before validity, captures qualification from its supplied instant's UTC date, sets exact accepted verification actor/instant and retains the provisional flag. `reject` permits pending only and `revoke` verified only; both clear flags, increment once, retain all history and permit past expiration. All operations are immutable and preserve creation audit/scope/protection; no external provider, Party operation, event or reason storage occurs in Domain. Shared expiration qualification was extracted privately while metadata regression tests remain green. Provisional-primary arbitration remains task 3.4 and is not claimed here.

Focused red/green commands:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierLifecycleTest --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierLifecycleTest --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierMetadataTest --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersionTest --tests com.alexastudillo.partyregistry.CleanArchitectureTest --console=plain
```

Executable red completed in 12 seconds; focused green/architecture completed in nine seconds. An initial full test/build run then passed (**1,418 tests**, **28 packaged JVM tests**, 5m 41s / 2m 26s); output `~/.local/share/opencode/tool-output/tool_10327bf3c001zGDMzaXQEWLjo6`.

The initial test analysis during compile-red reported a preview-feature runtime warning despite preview disabled. After resolving dependencies and clarifying responsibility Javadoc, a fresh full 1/1 analysis of the final saved test reports **0 issues/Hotspots with no preview warning**. JDTLS independently reports **0 problems**. The source uses stable Java constructs; inspection of the compiled class was also performed. Earlier incomplete-model analysis is not substituted for current evidence.

Final saved aggregate: `df1e41cfd8e18d90f7f8114dccb98cac81249709`. Final test after Javadoc clarification: `ce781a68da1fcbc1be6d44b314e51e4d1c26dad4`. Focused lifecycle rerun on that test revision passes all 20 cases in 12 seconds. Individual current Sonar/JDTLS evidence is linked in the inventory; no compiler warnings or suppressions were introduced.

### Latest full-suite failure

The required full test/build sequence was rerun on the final saved source:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

The shell chained these with `&&`. The test ran **1,418 cases with one failure** and completed BUILD FAILED in **15m 27s**, so the build command did not execute. Output: `~/.local/share/opencode/tool-output/tool_10357f7b60016bOX6YPIVIMO75`.

Failure: existing `LegalEntityDetailsResourceContractTest.typedBindingsAndValidationPrecedenceUseTheSharedErrorEnvelope`, at its fixture creation (`:115`, helper `:395/:426`), expected 201 but received exact `503 dependency-unavailable`. Its XML logs Geographic Reference `outcome=unavailable` for that setup request; later calls in the same class are recognized. Source: `build/test-results/test/TEST-com.alexastudillo.partyregistry.api.resource.LegalEntityDetailsResourceContractTest.xml:4–15,28–33`. No root cause or harmless-flake classification is assumed from those observations. The new lifecycle cases pass in that run.

Task 3.3 remains unchecked until the full-suite failure is reconciled and the required final build gate passes. Native/HTTP lifecycle coverage remains in later tasks.

## Implementation design blocker: issuer representation

Review of subsequent persistence work identified an approved-contract/storage mismatch:

- Maintenance specifies issuer as nullable string, maximum 64 raw UTF-16 units, with exact preservation on correction and no declared character restriction. The approved creation/update schemas have no issuer character pattern. Existing `PartyIdentifierCreateRequest` applies nullable `@Size` only (`:21`); Domain issuer validation likewise does not exclude U+0000.
- V1 stores `party_identifiers.issuer_code` as `varchar(64)` (`V1__create_party_registry_schema.sql:248`). PostgreSQL text cannot represent U+0000.
- Existing registration snapshots use JSONB (`V2__create_api_idempotency_records.sql:8`) and include issuer directly (`IdempotencyResultSnapshotCodec.java:269/289`). New historical completion snapshots also require safe issuer retention.
- The official PostgreSQL 18 JSON documentation explicitly rejects `\u0000` in JSONB because PostgreSQL text cannot represent it, and requires valid surrogate pairs: <https://www.postgresql.org/docs/18/datatype-json.html>. This is confirmed representation documentation plus source inspection, not a claimed new database/runtime test.
- The approved design explicitly supplies binary UTF-16 storage for exact withdrawal reasons, but supplies no compatible issuer-column/snapshot representation. Silently stripping or rejecting otherwise accepted issuer strings would narrow the specification; preserving them requires extra representation/migration/codec work, including compatibility assessment of initial registration and its retained snapshots.

Implementation pauses before further tasks. Required decision: either authorize design/task updates for compatible exact issuer storage and snapshots, or explicitly constrain issuer input through revised specifications/OpenAPI and validation. No such constraint, schema change, codec change or historical rewrite has been implemented. Historical migrations and the untracked deployment work remain untouched by this agent.

## User clarification: optional issuing-entity code

The user retracted the interim country interpretation and confirmed that `issuerCode` is an optional
issuing-entity code, for example `CIVIL_REGISTRY` or another issuer code. It is not an ISO alpha-2
country and does not acquire Geographic Reference validation. Omission/null and the approved
64-UTF-16-unit input limit retain their existing meaning. No new alphabet, case conversion,
catalog-membership rule, or character restriction is inferred from those examples.

The country reinterpretation was cancelled before any specification, OpenAPI, or Java changes for
it were applied. Both unstaged and staged diffs for the identifier request models, preparation
coordinator, and OpenAPI contain no country reinterpretation edits. No code rollback was necessary.
Previously implemented identifier work is not reverted by this clarification.

The separate existing legal-entity setup test was rerun in isolation with the same Podman/dynamic-port
environment and passed in 34 seconds:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests 'com.alexastudillo.partyregistry.api.resource.LegalEntityDetailsResourceContractTest.typedBindingsAndValidationPrecedenceUseTheSharedErrorEnvelope' --console=plain
```

This targeted pass does not replace the failed full-suite result or establish its root cause. No
issuer-country validation code or country-recognition failures were added. The original storage
representation question for unrestricted issuer strings has not been resolved by imposing an
undeclared restriction; full verification and remaining implementation tasks remain pending.

## Historical progress before the confirmed refinement

- Task 1.1 baseline verification completed.
- Task 1.2 effective installed rule research completed.
- Task 1.3 reproducible baseline completed after the longer rerun; actual runtime warnings recorded separately.
- Task 1.4 evidence convention, empty changed-Java inventory, and complete pending scenario map created.
- Task 2.1 complete: contract structure/semantics implemented and verified with focused/full/package checks and saved-revision IDE evidence.
- Task 2.2 complete: safe success/error examples, scoped declarations and output schemas implemented and verified.
- Task 2.3 complete: controlled fixture and safe shared assertion foundation verified.
- Task 3.1 complete: checked version advancement and immutable audit foundation verified.
- Task 3.2 complete: immutable partial metadata correction and state/date preservation rules verified.
- Task 3.3 Domain behavior and focused checks implemented; latest full-suite failure leaves its final verification incomplete.
- Tasks 3.3–14.6 remain incomplete; overall **9/59 tasks complete**.
- Paused on the issuer representation decision and the unexpected existing Geographic Reference setup failure. Identifier mutation routes/durable storage/native execution remain pending.

## Issuing-entity continuation: tasks 2.1, 2.2, 2.4 and Domain gates

The subsequent explicit user decision supersedes the unrestricted-issuer discussion above: issuer is optional issuing-entity metadata, ASCII letter groups with single underscores, uppercase new writes without trimming, bounded Java-blank input as null, maximum **64 Unicode code points before normalization**, and no Geographic Reference validation. Proposal/design/deltas now reflect this rule; the source map contains **28 requirements / 90 scenarios**. No issuer binary storage or migration rewrite is needed for the accepted alphabet.

### Implementation and compatibility

- `IssuerCodeNormalization` validates new/supplied input and uses `Locale.ROOT`. A linear ASCII scanner replaces the initial regex after local Sonar reported a finding; the saved scanner revision has completed zero-finding analysis.
- Both creation DTOs share `@ValidIssuerCode`. The validator reports exceeded length before structure, retains original parsed strings, and does not canonicalize fingerprint input. A 64-code-point supplementary string is structurally invalid, not oversized; a 65-code-point supplementary string is oversized.
- `PartyIdentifier.Builder.created` activates new-write canonicalization. Restoration does not apply the new alphabet; omitted correction, verification and withdrawal preserve historical issuer text. Supplied correction canonicalizes or clears only that field.
- Existing creation mapper/preparation/fingerprint/snapshot paths were inspected; no raw-input fingerprint format or stored snapshot codec was changed. Existing fingerprint, snapshot, persistence and registration compatibility suites pass. HTTP retry with `civil_registry` replays its original result, while changing only its raw representation to `CIVIL_REGISTRY` conflicts.
- OpenAPI includes the nullable ASCII/Java-blank alternatives, code-point bound, canonical-write semantics and unchanged historical response schema. New schema assertions reject NUL, non-ASCII letters, malformed underscores, nonblank spaces, newline suffixes and oversized inputs. Initial and additional registration/correction error examples declare both issuer codes.
- The existing registration resource still explicitly returns `Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>>`, maps Application result to API DTO, and uses CDI `ResponseManager` for 201. Current HTTP checks verify matching status/code/data and accepted Process-Id; field syntax precedes path/business evaluation. The new mutation routes remain later tasks.

### Executable evidence and fixture diagnosis

The earlier normalization/DTO and Domain-write red runs are followed by green focused checks. In this continuation, new example coverage reached executable red: **33 tests, one failure** because the shared initial-registration 400 response lacked the oversized-issuer example. Added that declared example; schema/example checks subsequently pass. The new issuer HTTP failure was a test-fixture context mismatch: its random tenant/operator tuple was not authorized in the strict geographic stub. The test now registers exactly its tuple with a scoped cleanup handle. Counts prove that only legal incorporation country EC is queried, never issuer, and additional registration adds no country call. Initial syntax failures for both Party types make no country call.

The separate earlier legal-detail first-request 503 passed in the 33-case focused run and both subsequent full runs. Its historical cause remains undetermined; this is not relabeled as a harmless flake, nor corrected by increasing timeouts, retries or weakening assertions. Neither its Java source nor production Geographic Reference code was changed.

An added length/structure-precedence case reached executable red independently: **2 DTO tests, one failure** for 65 supplementary code points producing both issuer violations. Fixed the validator to emit only `issuer-code-too-long` before alphabet validation; final HTTP assertions verify that code on both initial Party types. This is behavior evidence, separate from environmental setup failures.

Focused commands (same Podman/dynamic-port environment as below):

```bash
./gradlew test --tests com.alexastudillo.partyregistry.PartyIdentifierOpenApiStructureTest --tests com.alexastudillo.partyregistry.PartyIdentifierOpenApiExamplesTest --tests com.alexastudillo.partyregistry.api.resource.IssuerCodeResourceContractTest --tests com.alexastudillo.partyregistry.api.resource.LegalEntityDetailsResourceContractTest --console=plain
./gradlew test --tests com.alexastudillo.partyregistry.api.model.request.IssuerCodeRequestValidationTest --console=plain
./gradlew test --tests com.alexastudillo.partyregistry.api.model.request.IssuerCodeRequestValidationTest --tests com.alexastudillo.partyregistry.api.resource.IssuerCodeResourceContractTest --tests com.alexastudillo.partyregistry.PartyIdentifierOpenApiStructureTest --tests com.alexastudillo.partyregistry.PartyIdentifierOpenApiExamplesTest --console=plain
```

The first red run completed in 54s, precedence red in 9s, and final focused green (**24 cases**) in 32s. Original fixture assertions about spaces and raw mixed-case correction were reconciled with the confirmed contract, without weakening scheme/value, date, lifecycle or audit checks.

Final required gates after the last Java change:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Test: **BUILD SUCCESSFUL in 5m 52s**, **1,434 tests, zero failures/skips**.
- Build: **BUILD SUCCESSFUL in 2m 34s**, tests up-to-date and **28 packaged JVM tests, zero failures/skips**.
- Combined captured output: `~/.local/share/opencode/tool-output/tool_1040af508001jKiUB1M7ILpE1D`.
- Before the additional precedence fix, a full 1,433-case test run passed in 5m 38s (`tool_103ff59ac001aEBAExmgh7io5G`), followed by a passing build in 2m 18s. Those results are historical, not substituted for the final revision.
- Current saved-file inventory records all 17 refinement Java files with exact blobs, full Sonar completion/zero findings and independent JDTLS zero source problems. No new compiler warnings, suppressions or rule exclusions were introduced. Existing runtime duplicate-meter, injected SQL/error and cancellation-cleanup diagnostics remain visible and separately recorded.
- `git diff --check` and `openspec validate complete-party-identifiers-contract --strict` pass. Existing V1–V7 and response-contract library ownership remain intact. Native/new mutation HTTP acceptance remains pending in its designated tasks.

Task 2.1 contract refinement and 2.2 examples can now be reclosed. Task 2.4 issuer behavior is verified for existing creation and Domain correction/history boundaries. Task 3.2 supplied/omitted issuer correction is reverified; task 3.3's Domain matrix final test/build gate now passes. Each checkbox is updated only after its recorded current quality gates.

## Task 3.4: Scoped primary eligibility and complete immutable plans

Wrote `PartyIdentifierPrimaryPolicyTest` first. Compile-red in 5s identified absent policy/plan and neutral primary-conflict vocabulary. A runnable initial selection/verification skeleton reached **7/7 executable failures in 15s**, covering missing eligibility, checked increments, scoped conflicts, replacement consent and former-version exhaustion. Added aggregate `selectPrimary`/`demotePrimary` and policy orchestration. A separate plan-constructor invariant case then reached **1/8 executable failures in 10s**; the compact constructor now rejects incomplete flags, duplicate identity and cross-scope pairs before persistence can observe a plan. No skeleton remains in saved production code.

The policy checks target state, checked next version and UTC validity before scoped primary conflicts. An already-primary target advances once. Explicit replacement validates both immutable candidates using the same actor/instant; past-date former primaries can be demoted, maximum former version rejects the whole plan, and unrelated/provisional/nonprimary records are not displaced. Verification retains provisional flags only when no different scoped verified primary exists; nonprimary verification is independent, and expired-by-date existing primaries still conflict. The aggregate and originals retain protected-value identity, issuer/date metadata, verification and creation audit. Plan data contains at most two same-tenant/Party/scheme identifiers. Domain stays synchronous and free of framework, HTTP, persistence and logging dependencies.

Focused final regression command:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.domain.policy.PartyIdentifierPrimaryPolicyTest --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierLifecycleTest --tests com.alexastudillo.partyregistry.domain.model.PartyIdentifierMetadataTest --tests '*CleanArchitectureTest' --console=plain
```

Focused green passes the eight new cases, existing metadata/lifecycle and both architecture suites in **1m 39s**. Fixtures derive dates/instants from the Month-based shared fixture; no new numeric-month date literal or synchronous production wait was introduced.

Required final commands used the same environment/deadline and ran sequentially:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Test: **BUILD SUCCESSFUL in 5m 18s**, **1,442 tests, zero failures/skips**.
- Build: **BUILD SUCCESSFUL in 8m 12s**, full tests execute again and **28 packaged JVM tests pass without failures/skips**.
- Output: `~/.local/share/opencode/tool-output/tool_1041f5083001g1v43RHbt6QBRV`, success markers at lines 4763 and 9541; both test executions are recorded, not collapsed into one duration.
- `java-inventory.md` records all five saved blobs with full zero-finding Sonar and independent zero-problem JDTLS validation. `git diff --check` passes; no compiler warnings, suppressions or exclusions were introduced. Existing timeout/cancellation and failure-injection runtime diagnostics remain visible.

Task 3.4 Domain behavior is complete. Database demotion-before-promotion, scope locking/races, API translation and HTTP primary routes remain tasks 4.3, 7.4–7.5, 10–12; this Domain increment does not claim those outcomes.

## Compatibility decision before task 4.1: replay of a previously accepted issuer

Review of the existing registration specification before defining effective request/replay contracts reveals an outcome that needs explicit reconciliation:

- `openspec/specs/party-registration/spec.md:233–275` requires the original 201 result for an equivalent completed Party-registration request, including original initial-identifier data. It has no migration-cutover exception for old issuer metadata.
- The approved refinement requires invalid issuer structure to return `400 issuer-code-invalid` and preserves historical stored text, snapshots and raw fingerprint identity. A previously accepted issuer such as `legacy-authority` is now invalid under the new alphabet.
- `NaturalPersonResource.java:179–193` validates the nested DTO before constructing the original-input command; `CreateNaturalPersonUseCase.java:87–95` looks up completion only after receiving that command. Thus a retry with the exact old key/raw issuer is rejected before the existing saved result can be considered. Legal registration has the same validation boundary. Replacing the raw issuer with a new canonical code would instead change fingerprint identity, so it cannot recover the old result equivalently.

This is a source/specification compatibility finding, not a claimed seeded historical HTTP test. Existing tests prove current invalid-issuer 400 behavior, historical Domain restoration and current-valid-input replay; they do not decide this pre-cutover case. Stored snapshots and fingerprints have not been rewritten.

Pause before adding task 4.1 Application contracts. Task 2.4 is reopened until the user explicitly chooses whether current issuer syntax applies to every retry (old invalid text gives 400 while retained data remains intact), or whether an equivalent pre-cutover completion must still return its original 201 (requiring a narrow issuer-validation/replay-order exception and a historical HTTP regression). Do not silently introduce either migration exception or weakened replay guarantee. Overall acceptance remains **11/60** after reopening; Domain task 3.4 is independently verified.

### User resolution

The user confirms this is a new database with only **two existing records, both with null issuer**. This is user-supplied deployment context, not a claimed inspection of the live database. Consequently the hypothetical previously accepted invalid-issuer completion is not a required cutover case. Retain the specified current-request grammar and existing syntax-before-replay boundary, preserve stored null issuer/snapshots/fingerprints, and add no legacy admission exception, rewrite, or migration. No Java change is needed for this clarification. The previously passing saved-revision gates remain valid; task 2.4 is closed again and overall progress is **12/60**. Resume task 4.1.

## Task 4.1: Typed Application identities, commands, queries and safe outcomes

Added the contract tests first. Compile-red in 6s identified missing identifier-specific request/query/page/outcome types. After those types were runnable, the focused test reached **7 cases / 3 executable failures in 9s**: missing page-capacity validation, absent bounded operation names and generated additional-registration command logging its raw key. Implemented criteria bounds, redacted command output and closed observation vocabulary. The enum extension also identified its exhaustive Infrastructure label switch at compilation; all labels are now explicit and retain old names.

The architecture regression then caught a real cycle: an effective creation model referenced `InitialPartyIdentifierInput` in the command package, while commands already depend on model. Resolved this by expressing effective creation identity directly as its Party ID and six original parsed properties; no existing initial-registration input was moved or rewritten. Both architecture suites pass after the refactor. This is a focused boundary correction, not a repository restructuring.

Current contracts cover metadata PATCH, lifecycle and set-primary commands; qualified detail/list queries; exact filter scope including absent-versus-false primary; full-precision identifier boundaries; immutable safe slices/pages with long totals; original creation identity; exact private reason/action identity and effective defaults; safe APPLIED/REPLAYED outcomes; and eight additional bounded observation names. Creation/action identities exclude accepted actor/process/key, preserve original issuer/value/reason strings and default-equivalent action input, and do not print secrets. Commands use `PartyIdentifierVersion` directly and metadata uses `FieldUpdate` presence/null semantics. Bodies are parsed later in API; these contracts import no transport, persistence or framework type. Existing additional registration namespace/workflow remains unchanged until its designated refactor.

Final focused command:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.application.model.PartyIdentifierApplicationContractTest --tests '*CleanArchitectureTest' --tests '*MicrometerOpenTelemetryOperationObserverTest' --console=plain
```

All **16 focused checks** pass in **1m 39s**, including eight new contract cases, both architecture suites and closed-label telemetry assertions. List defensive copying, precision and existing-safe-result characterization are identified separately from the three new executable-red behaviors. A later case also verifies exact empty/NUL/whitespace reasons and rejects impossible action-body/key combinations. No numeric-month valid fixture was introduced.

Required commands on the final source:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Test: **BUILD SUCCESSFUL in 6m**, **1,450 tests, zero failures/skips**.
- Build: **BUILD SUCCESSFUL in 8m 47s**, full tests execute again and **28 packaged JVM tests pass without failures/skips**.
- Combined output: `~/.local/share/opencode/tool-output/tool_10456c63e001MDjLbgGNDLHTgC`, separate success markers at lines 4661 and 9337.
- All 21 changed Java revisions, including nested types, have exact blobs and completed full zero-finding Sonar/independent zero-problem JDTLS in `java-inventory.md`. The transient IDE lifecycle-command parse error is explicitly excluded from acceptance; the subsequent successful parse/current saved source and final build agree.
- `git diff --check` and strict OpenSpec validation pass. No rule suppression, compiler warning or application dependency on HTTP/persistence was introduced. Existing runtime diagnostics remain visible.

Task 4.1 is complete; functional workflows, port adapters, public parsers and native runtime acceptance are not inferred from these typed contract foundations.

## Task 4.2: Identifier ports and private records, final gate blocked

Added port-contract tests first. Compile-red identified absent query/cursor/fingerprint/mutation capabilities and private completion/audit records. Runnable contracts reached **4 cases / 2 executable failures in 14s**: generated completion output exposed its digest, and withdrawal audit accepted a verification action. Added redacted private representations and coherent operation/version/actor/shape guards. Qualified safe read signatures and one immutable-tenant callback capability are characterized with focused doubles; ports expose no ORM session/entity, SQL or HTTP/envelope type. The mutation callback returns only `PartyIdentifierMutationOutcome` rather than an unrestricted generic result. Existing current-identifier read port remains unchanged.

Focused green command:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.application.port.PartyIdentifierPortContractTest --tests com.alexastudillo.partyregistry.application.model.PartyIdentifierApplicationContractTest --tests '*CleanArchitectureTest' --console=plain
```

The **18 focused checks pass in 7m 48s**. Interface contracts establish sequential replay/owner/scope/child capabilities and safe outcomes; actual locking, closed-context enforcement, rollback/commit and concurrency remain the infrastructure/workflow tasks, not inferred from the focused double. All eight saved Java revisions have completed full zero-finding Sonar and independent zero-problem JDTLS analyses in `java-inventory.md`.

The mandatory full test/build sequence was attempted with the usual Podman socket, dynamic HTTP port and 1,200,000-ms deadline. The test reached **1,454 cases / one failure**, ending **BUILD FAILED in 19m 32s**; `&&` therefore prevented the build from executing. Output: `~/.local/share/opencode/tool-output/tool_1048526a2001CNSUSf1q2MLD3L`, failure at lines 183 / 4660–4673.

Failure is again `LegalEntityDetailsResourceContractTest.typedBindingsAndValidationPrecedenceUseTheSharedErrorEnvelope`: its creation fixture expected 201 and received exactly `503 dependency-unavailable`. XML at `build/test-results/test/TEST-com.alexastudillo.partyregistry.api.resource.LegalEntityDetailsResourceContractTest.xml:4–15,28–33` records Geographic Reference `outcome=unavailable` for that first setup request (741ms), followed by recognized requests in the class. No cause is assumed from that timing. New port/model cases pass in the full run. No timeout, retry, assertion or production Geographic Reference code was changed.

Pause before task 4.3. Task 4.2 stays unchecked until the recurrent legal/geographic setup failure is diagnosed/reconciled and the final full test/build gates pass. Overall accepted progress is **13/60**. Earlier 1,450-case / 28-packaged green is historical task 4.1 evidence and does not substitute for this failed gate. Native execution remains pending.

Concurrent repository activity is recorded, not altered: current HEAD is `628c14e` after externally observed commits `735fec3`, `6a676db`, `d759684` and `628c14e`. This agent issued no commit/staging commands and preserves the external deployment work.

## Geographic Reference test timeout isolation: blocker resolved

The user confirms the real Geographic Reference service responds successfully and requests a concrete solution. Investigation identifies the shared fixture's configuration fault: `GeographicReferenceStubResource.start()` globally supplied **100ms connect/read timeouts** alongside its local URL. The service's normal configuration defines finite **2000ms connect / 5000ms read** defaults. Because the shared resource is used across HTTP contract classes, timeout-fault settings also governed ordinary legal registration.

Added two meaningful regressions before the fix: the fixture must override only its local URL, and a controlled valid provider reply delayed 300ms must succeed with the normal service budget. Both fail in the old setup (**2/2 executable red, 38s**). The delayed-success failure includes an `ApplicationException` caused by **`io.vertx.core.impl.NoStackTraceTimeoutException`**, proving the configuration can turn a valid local reply into dependency-unavailable. This reproduces the shared budget defect directly rather than assuming every earlier unavailable outcome had the same exception.

Correction is limited to test sources:

- Shared resource overrides only the local HTTP provider URL; normal clients retain the existing service timeout configuration.
- The read-timeout test creates and closes a separate reactive Quarkus client with a 2s connect / 1s read budget. It first verifies a valid response, then asserts a real `TimeoutException` translated to neutral dependency-unavailable, exactly one provider request and one unavailable observation. The private registry is closed in finally.
- The controlled timeout reply is delayed 10s, beyond both that isolated budget and the ordinary service's 5s read budget. Existing legal HTTP dependency/timeout failure assertions still execute and pass.
- A regression checks that the fixture cannot reintroduce globally short timeouts. No production client, URL, response mapping, database or deployed microservice setting is changed.

Focused command executes all adapter/fixture and both legal/issuer HTTP suites:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.infrastructure.integration.geographic.GeographicReferenceStubResourceTest --tests com.alexastudillo.partyregistry.infrastructure.integration.geographic.GeographicReferenceAdapterTest --tests com.alexastudillo.partyregistry.api.resource.LegalEntityDetailsResourceContractTest --tests com.alexastudillo.partyregistry.api.resource.IssuerCodeResourceContractTest --console=plain
```

All **26 focused cases pass in 1m 10s**. Final required gates on the saved correction:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Test: **BUILD SUCCESSFUL in 6m 19s**, **1,456 tests, zero failures/skips**.
- Build: **BUILD SUCCESSFUL in 2m 33s**, unit tests up-to-date, **28 packaged JVM tests pass without failures/skips**.
- Captured output: `~/.local/share/opencode/tool-output/tool_104ad81c6001hTANUG10QAZZQC`.
- All three current test-source blobs have complete zero-finding Sonar and independent zero-problem JDTLS in `java-inventory.md`; no compiler warning, suppression, exclusion, retry or removed error assertion was introduced. Expected injected SQL/error and pre-existing cleanup diagnostics remain visible.

The previous full-suite blocker is resolved. Task 4.2's port/model revisions now satisfy their required final gates and can be marked complete; overall progress becomes **14/60**. Remaining functional routes, mutation adapters and native final checks remain incomplete.

## Task 4.3: Neutral identifier failures and API-only stable translation

Added failure-translation tests before implementation. Compile-red identified the missing sealed failure variants and identifier-specific Domain classification helper. Once runnable, the focused test reached **3 cases / 2 executable failures in 13s** for missing typed-failure and Domain-failure HTTP translations. Implemented the six neutral variants, sanitized internal descriptions and `ApplicationException.forIdentifier`; the existing generic `of` contract remains unchanged. Both exhaustive Application observation and API translation switches are extended explicitly.

Identifier absence, independent-version mismatch, invalid lifecycle, primary conflict, version exhaustion and invalid continuation now translate at API only to the approved 404/412/409/400 stable codes. Known Domain lifecycle/capacity/primary/validity violations become corresponding neutral Application failures; unknown invariants and unexpected technical failures remain unchanged for sanitized global handling. Domain/Application import no HTTP, shared response code or response exception. New failure markers retain no cursor payload or owner detail, and versions use `PartyIdentifierVersion`.

A test-only reactive HTTP resource exercises the actual translator and published global handler. Its eight probes verify exact status/code-only 400/404/409/412/422/500 envelopes and accepted Process-Id echoes, including an unknown invariant with a private cause that is absent from the response. No new global mapper or production verification endpoint is added. The existing exhaustive failure-description test initially detected the six added variants; its expected descriptions were extended while retaining the coverage-count assertion.

Focused command:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.api.error.PartyIdentifierFailureTranslationTest --tests com.alexastudillo.partyregistry.api.error.PartyIdentifierErrorContractTest --tests com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslatorTest --tests '*ApplicationExceptionTest' --tests '*OperationObservationTest' --tests '*CleanArchitectureTest' --console=plain
```

New translation and real HTTP probes pass in the focused run; its single exhaustive-description regression was reconciled as above and passes in subsequent full gates. Initial full gates then passed (6m 41s / 2m 39s, output `tool_104cd461b001syCpiWRkKO7Q9C`). The first local interface analysis reported two findings; the new version-specific null-check descriptions remove duplicated literals and a fresh full analysis reports zero findings. Final gates were rerun after that last source fix, rather than reusing the earlier result.

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Final test: **BUILD SUCCESSFUL in 6m 25s**, **1,460 tests, zero failures/skips**.
- Final build: **BUILD SUCCESSFUL in 2m 38s**, tests up-to-date, **28 packaged JVM tests pass without failures/skips**.
- Final output: `~/.local/share/opencode/tool-output/tool_104d7be830013YeK4dZsX8I6oB`.
- All nine saved Java revisions and nested types are inventoried with completed full zero-finding Sonar and independent zero-problem JDTLS. No new compiler warning, suppression, rule exclusion or numeric-month date fixture was introduced. Existing runtime injected-error/cleanup diagnostics remain visible and separate from source findings.

Task 4.3 is complete. **User-requested stop after 4.3:** pause the session here for continuation tomorrow. Accepted progress is **15/60**. Next task is **5.1**, the next immutable Flyway migration; confirm its next available version when resuming. No section 5 implementation has started and native final verification remains pending.

## Task 5.1: Additive identifier completion, audit and listing foundation

Resumed on 2026-10-04. V8 was unused. The current Antigravity repository window completed a fresh Java-25 resource baseline with 571 active Java rules, full 1/1 analysis and zero issues/Hotspots at 10:42:50.254; JDTLS Standard-mode validation independently reports zero problems at 10:42:48.801. Current Sonar log: `~/.config/Antigravity IDE/logs/20261004T103628/window2/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log:76–106`; JDTLS uses the unchanged workspace log, lines 21596–21612. Initial startup unresolved-model warnings at 10:39 are not acceptance evidence.

Wrote `PartyIdentifierContractMigrationTest` first. The initial explicit target-8 run failed because Flyway could not find V8 (1m). The revised latest-migration invocation successfully applied the existing history, then failed the executable eight-versus-seven migration assertion (45s). Added `V8__add_party_identifier_contract_support.sql` without changing V1–V7. It adds qualified restrictive owner references, a tenant/action/exact-key completion primary key, the five allowed operation namespaces, safe JSON-object snapshot/digest/schema/expected-version constraints, independently durable binary withdrawal audit with uniqueness/even-byte/bounded-size constraints, and descending historical and retention indexes. It seeds no identifiers, completions or audit and performs no expiration/protected-data rewrite.

Focused clean-install tests also verify exact column types/nullability, creation/action expected-version semantics, cross-owner and missing references, restrictive deletion, exact key scope, malformed hashes/schemas, null/empty/maximum binary reason sizes and rejected shapes. A candidate initially violated both operation and expected-version checks; the fixture now supplies a valid expected version when testing the operation allowlist. No constraint was weakened. The focused run passes **22 tests** (eight new migration cases and 14 existing migration regressions) in **1m 36s**:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.PartyIdentifierContractMigrationTest --tests com.alexastudillo.partyregistry.MigrationRegressionTest --console=plain
```

The first full run failed only the existing `IdentifierSchemeTestFixtureTest` exact production-file inventory (**1/1,468**, 12m 28s; output `tool_107a5c4a5001gUAePLAyxGvrxJ`). Updated that allowlist to include V8 while retaining every old checksum and test-only seed assertion. The main migration inventory also includes V8 using `Map.ofEntries` because the map now exceeds ten entries. Final commands:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Test: **BUILD SUCCESSFUL in 7m 6s**, **1,468 tests, zero failures/skips**; output `~/.local/share/opencode/tool-output/tool_107afb079001H0obW2oo5wXlow`.
- Build: **BUILD SUCCESSFUL in 2m 24s**, test up-to-date, **28 packaged JVM tests, zero failures/skips**.
- All seven prior SHA-256 migration identities match the baseline table. V8 blob: `098ad5f42e3ad892843827e65f5a092cef627b53`.
- All three Java revisions have complete zero-finding Sonar and independent zero-problem JDTLS analysis in the inventory. No new compiler warnings, exclusions, suppressions, numeric-month fixtures or request-path JDBC were added. Existing injected-error, duplicate-meter and HR000090 cleanup diagnostics remain visible.
- External repository activity advanced HEAD to `86c8537` while verification ran and incorporated some saved files; this agent issued no commit/staging command. The recorded source blobs remain unchanged. Unrelated deployment work is preserved.

Task 5.1 is complete; V7 retained-data upgrade, identifier adapters/routes and native acceptance remain their subsequent tasks.

## Task 5.2: Retained-data upgrade passes; local analysis finding blocks completion

Added `PartyIdentifierContractUpgradeTest`, using an isolated PostgreSQL 18.4 database migrated by Flyway to V7, then seeded through test-only DML with both owner types and all five identifier states. Fixtures preserve synthetic ciphertext, hashes, key/normalization versions, primary flags, legacy issuer metadata, past validity dates, independent versions, verification and creation/modification attribution, plus original Party replay snapshots. Valid dates and microsecond instants derive from Month-based fixtures. V8 is applied once and reapplication executes zero migrations. Full JSON row comparisons, all seven prior Flyway checksums and every prior identifier index/constraint definition remain identical; new completion/audit tables remain empty.

The first focused run failed on an incorrect fixture column (`parties.status` rather than `record_status`); this is a fixture defect, not feature TDD red. Corrected the DML column without changing production code/migrations. The following focused run passes **nine cases in 17s** (one retained-data upgrade plus eight clean-install/constraint cases). This is characterization of the completed additive migration, not a fabricated new production red/green claim:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.PartyIdentifierContractUpgradeTest --tests com.alexastudillo.partyregistry.PartyIdentifierContractMigrationTest --console=plain
```

Required post-Java checks on saved blob `b2558714f6c637adf034c2202356b7839397e14f`:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Test: **BUILD SUCCESSFUL in 5m 34s**, **1,469 tests, zero failures/skips**.
- Build: **BUILD SUCCESSFUL in 5s**, all tasks up-to-date. Packaged tests did not reexecute; task 5.1's unchanged packaged application/source-set result remains the actual 28-case execution evidence.
- Combined output: `~/.local/share/opencode/tool-output/tool_107bd2d5a001q9rJFz2R5EnoT4`. Existing injected-error/HR000090/duplicate-meter diagnostics remain visible and unsuppressed. No new compiler warning was emitted; `git diff --check` passes.
- Current standalone Sonar completes full 1/1 Java-25 analysis at **11:23:52.924** with **1 issue / 0 Hotspots**. Source: current S2 log, lines **879–909**. The log has no diagnostic rule key, message or source location; no specific rule is inferred from its count.
- Independent JDTLS validation at **11:23:53.700** reports **zero problems** for the same saved revision; current project log lines **21917–21927**.

Pause before task 6.1. Obtain the current `PartyIdentifierContractUpgradeTest.java` diagnostic from the Antigravity Problems view (message, rule and location or screenshot), then fix and reanalyze the exact saved revision and rerun invalidated checks. Task 5.2 remains unchecked because required zero-finding local evidence is missing; overall progress is **16/60**. No analysis setting, exclusion, suppression or acceptance criterion is relaxed. Native final acceptance remains pending.

### Task 5.2: Assertion-count finding resolved

The supplied Problems-view screenshot identifies **java:S5961** on the upgrade test: reduce 29 assertions to fewer than 25. Split the original case into retained owner/identifier/snapshot history and prior checksums/indexes/constraints, each using its own isolated V7 database. Every original assertion remains; no assertion is concealed through a new helper, removed or suppressed. Current saved blob: `ae4447b8b83ac36c569fefea82455b26a5442983`.

- Focused migration command above: **10 cases pass in 21s** (two upgrade and eight clean-install cases).
- Required full `./gradlew test` with the existing Podman/dynamic-port environment: **BUILD SUCCESSFUL in 5m 32s**, **1,470 cases, zero failures/skips**.
- Required `./gradlew build`: **BUILD SUCCESSFUL in 5s**, tasks up-to-date; no new packaged/native execution is inferred.
- Combined output: `~/.local/share/opencode/tool-output/tool_107cb8e640017x6AB15aS2zTjT`.
- Full 1/1 Sonar Java-25/571-rule analysis at **11:39:19.989** reports **zero issues/Hotspots**; S2 lines **932–962**.
- Independent JDTLS validation at **11:39:21.271** reports **zero problems**; J lines **21929–21939**.

The blocker is resolved and task 5.2 is complete. No configuration, rule exclusion or suppression changed. Proceed to task 6.1; functional/native scenario acceptance remains pending.

## Task 6.1: Authenticated identifier-only continuation

Wrote seven focused cursor tests first. Compile-red (4s) identifies the missing adapter. The first runnable boundary/key/tenant/Party implementation reaches **7 cases / 2 executable failures in 7s** for missing effective filter scope and exact Unicode representation. Extended the resource-specific scope digest to include optional scheme ID, exact scheme code, optional status, tri-state primary and effective limit. Modified UTF preserves distinct Java code units, including isolated surrogates, rather than replacing them during UTF-8 conversion. No initial incomplete scope encoder remains in saved production code.

`HmacPartyIdentifierCursorAdapter` implements the existing port with CDI composition and eagerly validated `PartyCursorKeyMaterial`. Tokens retain exact epoch-second/nanosecond/unsigned UUID tuples and direction, canonical bounded Base64url, version/current key ID and authenticated scope. HMAC uses a separate identifier resource domain, constant-time verification precedes trusted decoding, and malformed/version/key/scope/direction/timestamp failures expose only neutral `InvalidIdentifierCursor` without token-bearing causes. No cursor expiry or persistent snapshot is introduced; existing resource encoders/key material remain unchanged.

Tests cover both directions and effective 1/50/200 capacities, microseconds and pre-epoch nanoseconds, default-50 equivalence, every scope dimension, case, absent versus false, Unicode code units, canonical bounds/padding/tampering, unknown versions/keys, correctly signed impossible fields, cross-nationality rejection with shared keys, and retained-key rotation. Fixtures use Month-based instants and synthetic test-only secrets. No additional production refactor was needed.

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*Hmac*Cursor*Test' --tests '*CleanArchitectureTest' --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Cursor and architecture regression passes in **2m 43s**; all seven new cases pass.
- Full test: **BUILD SUCCESSFUL in 6m 42s**, **1,477 tests, zero failures/skips**.
- Build: **BUILD SUCCESSFUL in 2m 15s**, tests up-to-date and **28 packaged JVM tests execute and pass**.
- Full output: `~/.local/share/opencode/tool-output/tool_107d9997a001TbGwGVqn5WGLC8`.
- Both saved Java files have full zero-finding Sonar and separate zero-problem JDTLS in the inventory. English responsibility Javadoc is present, no new compiler warning/suppression/exclusion is introduced, and existing runtime failure-injection/cleanup diagnostics remain visible. `git diff --check` passes.

Task 6.1 is complete; live historical query/HTTP/native coverage remains subsequent work.

## Task 6.2: Qualified scalar historical detail

Wrote `PartyIdentifierDetailReaderTest` first. Compile-red (4s) identifies the absent reader. A runnable query retaining current-state admission reaches **4 cases / 3 executable failures in 23s**: terminal history is absent and the source contract rejects the current-state predicate. Removed that predicate for historical detail; no initial state-restricted query remains. The qualified scalar query selects only 15 safe columns plus stable scheme code, with tenant/Party/identifier predicates and no date/state admission. It reuses the existing safe scalar mapper without altering the current-reader query.

`PartyIdentifierDetailReader` owns bounded read-only reactive transaction mechanics and translates only classified persistence failures. It retains no session, loads no protected entity, decrypts no value and performs no update. It is the Infrastructure detail collaborator for the subsequent combined query-port adapter; no incomplete port implementation or production placeholder is added.

Four tests cover every identifier state across both owner types/all four Party states, missing/wrong-owner/cross-tenant concealment, exact preserved historical issuer/date/version/time, retained verification/flags, whole-row identifier/Party nonmutation and unchanged narrower current projections. Source assertions explicitly exclude ciphertext, hashes, normalization/encryption key versions and require the full owner tuple. All valid date/instant fixtures derive from Month constants.

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests com.alexastudillo.partyregistry.infrastructure.persistence.PartyIdentifierDetailReaderTest --tests '*PartyIdentifierReadAdapterTest' --tests '*CleanArchitectureTest' --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Focused detail/current-reader/architecture checks pass in **1m 47s**.
- Full test: **BUILD SUCCESSFUL in 5m 48s**, **1,481 tests, zero failures/skips**.
- Build: **BUILD SUCCESSFUL in 2m 51s**, unit tests up-to-date and **28 packaged JVM tests execute and pass**.
- Combined output: `~/.local/share/opencode/tool-output/tool_107e94b2a001TwAJ2Hy1zlWPce`.
- Both final saved Java revisions have full zero-finding Sonar and independent zero-problem JDTLS in the inventory. No compiler warning, suppression/exclusion or request-path blocking API is added. Existing controlled failure/cleanup logs remain visible. `git diff --check` passes.

Task 6.2 is complete; snapshot-consistent list queries and public query routes remain subsequent tasks.

## Task 6.3: Historical pages pass; packaged container startup blocks acceptance

Added the historical page tests first. Corrected an initial test syntax error before the meaningful compile-red for absent query components. The runnable ascending/current-order query reaches **8 cases / 3 executable failures in 24s** for descending forward traversal, nearest backward pages and deleted-boundary continuation. Changed traversal order to descending forward/ascending backward with response reversal. No ascending historical skeleton remains in saved production code.

`PartyIdentifierPageSql` binds tenant/Party and every exact optional scheme-ID/code/status/tri-state-primary filter, counts all matches before continuation, selects only safe scalar fields, applies strict creation-instant/UUID tuple bounds and retains at most the requested page capacity. `HibernateReactivePartyIdentifierQueryAdapter` implements the full existing query port, delegates detail to the qualified reader and owns one read-only REPEATABLE READ transaction for sequential count, rows and neighbor queries. It applies the existing transaction-local statement/traversal budgets and classified-failure translation. Empty continued pages retain counts and derive actual available neighbors from the requested tuple; no boundary-row existence lookup or expiration/state rewrite is performed.

Nine tests cover exact filter intersections, unknown/mismatched/case-sensitive schemes, false-versus-absent primary filters, all five historical states, microsecond ordering and unsigned UUID ties, forward/backward pages, empty collections/continuations, missing and changed-filter boundary rows and actual stable read isolation while a separately owned session deletes data. The snapshot test confirms `repeatable read/on` and still reads the prior row after the independent delete, while a subsequent request sees the new zero count. This is live continuation, not a server-held frozen dataset.

Focused page/detail/architecture regression passes in **2m 1s**. The final added changed-filter case passes with all **nine page cases in 30s**. A representative PostgreSQL 18.4 `EXPLAIN (ANALYZE, BUFFERS)` uses **Index Only Scan on `ix_party_identifiers_created_id`**, tenant/Party index conditions, **one index search**, a bounded **Limit 2**, planning **0.216ms** and execution **0.089ms** in that synthetic fixture. This is an observed small-fixture plan, not production capacity evidence; no additional filter index is added. Source: `TEST-com.alexastudillo.partyregistry.infrastructure.persistence.PartyIdentifierHistoricalPageTest.xml:12–22` from the focused run.

Required gate attempt:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Test passes **1,490 cases, zero failures/skips**, **10m 53s**.
- Build packages the JVM application but **fails at quarkusIntTest in 5m 31s**: **28 cases, two failures and ten skipped**. Both failing classes (`PackagedIdentifierSchemeContractIT` and `PackagedRootContractIT`) fail before exercising their contracts because `PackagedGeographicReferenceResource.start:62/97` cannot create its container. The root cause shown is **java.io.IOException: Broken pipe** in docker-java's Unix socket write through `/run/user/1000/podman/podman.sock`. This is a runtime setup blocker, not feature TDD red or a passing packaged gate.
- Combined output: `~/.local/share/opencode/tool-output/tool_1080054160017dIF5OroVU0AIV`. Failed XML/HTML remain under `build/test-results/quarkusIntTest` and `build/reports/tests/quarkusIntTest`.
- Read-only runtime inspection after the failure shows the Podman socket listening, the socket-activated service idle after exit status 0, local `podman info` successful and no running containers. Idle socket activation alone does not establish the cause of the failed socket writes. No runtime restart, configuration change or assertion weakening was performed.

Initial source analysis reported one test issue/problem. Inspection identified an unused `java.util.Optional` import; removed it and reanalyzed. All three final source revisions now have zero Sonar issues/Hotspots and zero JDTLS problems, documented in the inventory; no rule-key claim is inferred from the earlier count-only log. After that last Java edit, the mandatory full test runs again: **BUILD SUCCESSFUL in 10m 33s, 1,490 cases, zero failures/skips**, output `~/.local/share/opencode/tool-output/tool_10815ce7e001R4qFlzaKqTFfZQ`. The production sources are unchanged from the failed packaged attempt; no successful replacement build is claimed.

Pause before task 6.4 and keep 6.3 unchecked. Obtain guidance for diagnosing/retrying the packaged Podman startup failure; then rerun the actual build gate without skipping required cases. Overall accepted progress is **19/60**. Native final gates and public routes remain pending. Existing controlled runtime error/cleanup diagnostics remain visible and `git diff --check` passes.

### Task 6.3: Packaged Podman gate reconciled

The user authorized diagnosis and repetition. The installed `/usr/lib/systemd/user/podman.service` launches `podman system service` without a timeout override; installed help confirms the default idle timeout is **5s**. The journal during the failed packaged run records repeated API shutdown/restart cycles and retained port-helper processes. A separate persistent HTTP connection successfully pings the normal socket both initially and after eight idle seconds, so idle timeout alone is not claimed as the proven root cause of every Broken pipe.

Started an isolated transient user unit for the test session, keeping the API process stable without altering the normal systemd unit or application/test code:

```bash
systemd-run --user --unit=party-registry-test-podman-api --collect /usr/bin/podman --log-level=info system service --time=0 unix:///tmp/opencode/party-registry-podman.sock
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

The endpoint returns HTTP 200/OK. Build is **SUCCESSFUL in 2m 3s**, with actual **28 packaged JVM tests, zero failures/skips** (report duration 1m 58.74s); the previously passing 1,490-test suite is up-to-date. All three saved source blobs match their final inventory entries. This actual green gate reconciles the previous environment failure; the earlier failed result remains recorded and is not relabeled as feature red or harmlessness proof.

Use the test-scoped socket for the remaining session gates and stop the transient unit at session completion/pause with `systemctl --user stop party-registry-test-podman-api.service`. No permanent Podman configuration, retry, test exclusion, Java behavior or warning suppression was changed. The lifecycle hypothesis and successful isolated runtime are distinguished explicitly. Task 6.3 is now complete; accepted progress is **20/60**.

## Task 6.4: Application-owned historical query workflows

Wrote `PartyIdentifierQueryUseCasesTest` first. Compile-red identifies the two absent use cases; corrected the test dependency-failure constructor to supply its existing dependency name before executable behavior. The runnable workflows reach **nine cases / six executable failures in 7s** for identifier access preceding ownership and cursor decoding following owner I/O. Reordered detail to owner-before-identifier and list to authenticated cursor-before-owner-before-page. The same nine cases and architecture regression pass in **1m 40s**. No incorrect initial precedence remains in final source.

`GetPartyIdentifierUseCase` distinguishes neutral Party absence from qualified identifier absence and returns the safe stored result unchanged. `ListPartyIdentifiersUseCase` authenticates the complete effective scope before any port read, checks ownership without Party lifecycle admission, requests one safe historical slice, checks actual page capacity, preserves exact long totals, computes ceiling without addition overflow and encodes only available next/previous positions through the cursor port. Both pipelines are lazy and return their Uni; no HTTP, CDI, persistence, protection/decryption, external verification or clock dependency enters Application.

Port doubles cover both Party types/all identifier states, absent owner before child access, absent identifier after owner, invalid cursor before any read, valid continuation not bypassing absent ownership, successful empty owned pages, full boundary/scope delegation, safe data propagation, Long.MAX_VALUE ceiling, rejected oversized port pages and unchanged classified/unexpected failures. The owner port exposes only immutable Party type, not lifecycle; no state restriction is introduced, while the existing task 6.2 database cases already exercise all four Party states. Every asynchronous assertion has a two-second bound and fixtures use the existing Month-based test foundation.

Final commands use the stable test-scoped API:

```bash
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Test: **BUILD SUCCESSFUL in 5m 29s**, **1,499 cases, zero failures/skips**.
- Build: **BUILD SUCCESSFUL in 7m 41s**; unit tests execute again and **28 actual packaged JVM cases pass without failures/skips**. These two unit executions are recorded separately, not collapsed into one duration.
- Combined output: `~/.local/share/opencode/tool-output/tool_108aaaaa200145WAOEHxVyQNfX`, success markers at lines 4839/9693.
- Three final Java revisions and the nested test Harness have full zero-finding Sonar and separate zero-problem JDTLS in the inventory, English responsibility Javadoc and no new compiler warning/suppression/exclusion. Existing controlled error/cleanup logs remain visible.

Task 6.4 is complete; API parsers, page serialization, CDI resource composition and new HTTP/native routes remain subsequent tasks.

## Task 6.5: Strict canonical selectors/query parsing, Sonar finding pending

Wrote the parser/support tests first. Compile-red (5s) identifies the absent API helpers. The runnable UUID parsing reaches **nine cases / two executable failures in 7s** because the JDK accepts uppercase and shortened UUID groups. Added exact canonical-to-input comparison to identifier selectors and scheme-ID filters. Parent parsing reuses the existing `ApiRequestSupport`, preserving parent-before-child-before-query syntax and `party-id-invalid` versus generic `bad-request`. No permissive UUID skeleton remains in saved code.

`PartyIdentifierQueryParameters` accepts exactly the six declared names once, rejects null/empty/blank values, preserves exact scheme-code text and tri-state primary, parses the exact five states and boolean tokens, defaults limit 50 only on absence and accepts decimal limits 1–200. Code lengths use the declared Unicode code-point bound; creation's separate UTF-16 limit remains untouched. Decimal leading zeros are accepted because the approved query contract does not require canonical decimals for limits (unlike If-Match). Cursor shape is bounded at 256 and authentication remains Application-owned. All syntax failures produce API-only stable exceptions without rejected input or parser causes.

`PartyIdentifierRequestSupport` builds typed detail/list queries and delegates parent syntax to the shared existing helper. It adds no owner lookup, lifecycle admission, persistence call, error mapper, success filter or change to unrelated selectors. Tests cover accepted limits/defaults, every state/primary, exact text including whitespace/case/supplementary characters, canonical selectors, unknown/repeated/null/blank filters, overflow/coercion/token failures, parent error precedence, safe failure representation and retained metadata. Split cardinality and null-input tests by responsibility to keep the known assertion-count rule bounded while retaining every assertion; the final increment contains **ten cases**.

Focused parser/support/architecture regression passes in **1m 45s**:

```bash
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*QueryParametersTest' --tests '*RequestSupportTest' --tests '*CleanArchitectureTest' --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

- Final full test: **BUILD SUCCESSFUL in 5m 29s**, **1,509 cases, zero failures/skips**, including the split final parser tests.
- Final build: **BUILD SUCCESSFUL in 2m 13s**, unit tests up-to-date and **28 actual packaged JVM cases execute and pass with no skips**.
- Combined output: `~/.local/share/opencode/tool-output/tool_108c3ee55001BOCaiVcC5TDYmL`.
- All four final Java revisions have independent **zero-problem JDTLS**. Three files complete full zero-finding Sonar. The production query parser completes full 1/1 Java-25/571-rule Sonar at **16:08:20.574** with **one issue / zero Hotspots**, S2 lines **1745–1775**. The log contains no rule key, finding message or source location; no specific cause is guessed.
- English responsibility/contract Javadoc is present, no new compiler warning or suppression/exclusion/configuration change was added, and existing controlled failure/cleanup logs remain visible. `git diff --check` passes.

Pause before 6.6. Obtain the `PartyIdentifierQueryParameters.java` diagnostic from the Antigravity Problems view (rule, message and location or screenshot), fix and reanalyze the exact saved revision, then rerun invalidated checks. Keep 6.5 unchecked and accepted progress at **21/60**. Runtime/native/public-route acceptance is not inferred from parser unit checks.

The test-scoped transient Podman API was stopped with `systemctl --user stop party-registry-test-podman-api.service`; `is-active` reports inactive. The normal Podman unit/configuration was not edited. Restart the documented transient service when continuing runtime verification; no owned API process is left running at this pause.

### Task 6.5: Concise character-class diagnostic resolved

The user supplied the exact production finding: **Use concise character class syntax `\\d` instead of `[0-9]`**, on the decimal-limit matcher. Changed its Java literal to `"\\d+"`, consistent with existing query parsers. No Unicode regex flag is enabled; the existing fullwidth-digit, signed/fractional/overflow, decimal-boundary/default and exact-filter cases still pass. No rule key is inferred from the supplied message/screenshot.

Final parser blob: `469f984dcee2369b46d4f85c534408833100de5b`. Full 1/1 Java-25/571-rule Sonar at **17:14:34.047** reports **zero issues/Hotspots**, S2 lines **1869–1899**. Independent JDTLS validation at **17:14:34.718** reports **zero problems**, J lines **22271–22281**. The other three unchanged saved-file analysis entries remain valid.

Restarted the documented transient Podman API and ran the required full commands on that final revision. Test is **BUILD SUCCESSFUL in 5m 31s**, **1,509 cases, zero failures/skips**. Build is **BUILD SUCCESSFUL in 7m 38s**, with unit tests executing again and **28 actual packaged JVM cases passing without skips**. Combined output: `~/.local/share/opencode/tool-output/tool_108fe1bf3001GaZxRyfOQ4ItUq`, success markers 4841/9697. No suppression, exclusion, semantic narrowing or new compiler warning is introduced; existing runtime error-injection/cleanup diagnostics remain visible.

The blocker is resolved and task 6.5 is complete. Accepted progress is **22/60**. Proceed to 6.6; public GET/native verification remains subsequent work.

## Task 6.6: Safe item/page mapping and checked counts

Wrote `PartyIdentifierPaginationMappingTest` first. Compile-red (5s) identifies missing page mapping. Runnable unchecked integer casts reach **seven cases / two executable failures in 52s**: overflow reaches shared metadata as wrapped negative data rather than failing checked conversion, and optional-null identifier DTO fields are not omitted. Added `Math.toIntExact` before metadata construction and the same DTO-local NON_NULL annotation already used by nationality responses. No unchecked cast remains. The existing pagination-only cursor writer is unchanged; managed characterization proves it supplies both explicit null directions on identifier pages while ordinary/error/unrelated envelopes retain their normal shape.

The seven cases assert exactly the 15 declared populated item fields, exactly ten mandatory fields when optionals are null, historical issuer/verification/version retention, privacy allowlists, flat metadata and HTTP/body status agreement, first/middle/final and empty-continuation directions, zero counts, maximum supported integer totals and rejection of both overflowing count dimensions/Long.MAX_VALUE. API mapping first creates immutable response DTO lists and separately creates checked shared PaginationMetadata; Domain/Application records are not exposed through success envelopes.

Focused pagination/architecture characterization passes in **6m 46s**. The first full run (**1,516 cases / 14 failures**, 18m 6s, output `tool_10921a025001iXwyl4wLMajDHM`) identified one repeated legacy assertion in `NaturalPersonResourceContractTest` requiring all optional properties even when null. The approved OpenAPI `PartyIdentifierResponse` at lines 5561–5577 explicitly permits omission and requires only ten properties; natural-person requirements preserve the complete array, values/order/audit rather than requiring null-property presence. Adjusted only the current-item shape check to require all mandatory fields and reject any property outside the full allowlist. Existing value/privacy/version/state/order assertions and the creation-only exact initial-identifier shape remain intact. Focused natural-person/pagination tests then pass in **2m 5s**.

The subsequent full run passes **1,516 tests in 12m 39s**, but build detects the equivalent old 15-key assertion in the packaged natural-person smoke case (**1/28 failure**, 3m 39s; output `tool_109378e90001oZ6Rxrju6WU83T`). Updated its current-item required/allowlist checks identically, retaining all existing HTTP/value/privacy assertions. Corrected the new assertTrue static import; the compile-error revision is not accepted as source-analysis evidence.

Final required commands use the same stable test-scoped Podman API:

```bash
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

Test succeeds up-to-date in **6s**, preserving the actual **1,516-case zero-failure/zero-skip execution** immediately above. Build is **SUCCESSFUL in 2m 13s**, compiles the final integration source and executes **28 packaged JVM cases, zero failures/skips**. The five final saved revisions have full zero-finding Sonar and separate zero-problem JDTLS in the inventory; no rule/configuration suppression, compiler warning, protected-data rewrite, schema change or unrelated DTO change is introduced. The initial response model and pagination writer remain unchanged. Existing runtime error-injection/cleanup logs remain visible.

Task 6.6 is complete. Accepted progress is **23/60**; public GET composition/contracts and actual native execution remain their subsequent tasks.

## Task 6.7: Both public identifier GET routes

Wrote `PartyIdentifierReadResourceContractTest` before exposing the routes. The real HTTP red run executes **37 cases / 36 failures in 32s** for missing collection/detail GET behavior (405/404 instead of their approved results); the existing context-filter case already passes and is characterization. After adding thin GET methods, CDI Application composition and explicit DTO/shared success envelopes, the runnable list still lacks pagination and produces **15/37 executable failures in 49s**. Switched list success to ResponseManager.paginatedHttp with checked metadata; all 37 cases and architecture pass in **2m 8s**. No missing-metadata success skeleton remains.

`PartyIdentifierResource` now exposes collection GET and nested detail GET with explicit `Uni<RestResponse<ApiResponse<List<PartyIdentifierResponse>>>>` / `Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>>` signatures and bounded spans. Its API-only `PartyIdentifierQueryOperations` collaborator owns syntax adaptation, Application invocation, DTO mapping, shared wrapping and known-failure translation; it owns no lookup, SQL, lifecycle decision or success filter. This groups genuine query delivery responsibilities while keeping the resource constructor within the existing seven-parameter rule. Infrastructure producers compose both plain Application use cases from inner ports under the same existing ORM feature gate; disabled-persistence error profiles remain supported.

The 37 cases exercise both Party types in every owner state, all five identifier states and past-date metadata, safe item/page schemas, exact scheme/status/primary intersections, valid unknown/mismatched schemes, timestamp ties/unsigned UUID navigation in both directions, cursor-limit scope rejection, wrong-owner/cross-tenant concealment, noncanonical/unknown/repeated/blank/range syntax, missing/invalid context and accepted Process-Id echoes. Complete stored Party/identifier JSON snapshots before/after HTTP reads prove no protection/audit/version/state rewrite. Fixture-only JDBC DML executes on ordinary test threads; request handling continues through Hibernate Reactive, with no request-path JDBC. Month-based controlled dates remain explicit.

Final required commands use the stable test-scoped Podman API. **./gradlew test succeeds in 6m 7s, 1,553 cases, zero failures/skips**. **./gradlew build succeeds in 2m 36s**, unit tests up-to-date and **28 actual packaged JVM cases execute and pass without skips**. Combined output: `~/.local/share/opencode/tool-output/tool_1095daaa7001Wk72gtC7ViS69W`. Served OpenAPI compatibility and existing business/error suites execute; this does not substitute for the later all-operation packaged/native tasks.

All four final saved revisions (including nested Fixture) have actual full zero-finding Sonar and independent zero-problem JDTLS in the inventory. Resource generics and transport/neutral-failure boundaries are inspected explicitly and architecture tests pass. No compiler warning, suppression/exclusion, protocol contract change or overlapping mapper/filter was introduced. Existing controlled runtime diagnostics remain visible and `git diff --check` passes.

Task 6.7 is complete; accepted progress is **24/60**. Continue with the atomic mutation infrastructure at 7.1. The remaining five maintenance/action routes and durable additional registration/native acceptance remain pending.

## Task 7.1: Identifier-only exact effective-request HMACs

Wrote eight unit cases first. Compile-red (6s) identifies the missing adapter; a runnable creation/target/version encoder reaches **eight cases / two executable failures in 8s** because exact withdrawal reasons are absent from action identity. Added both reason strings in the defined field order before replacement choice; no incomplete action encoder remains. Fingerprint/architecture regression passes in **2m**.

`HmacPartyIdentifierFingerprintAdapter` implements the existing inner port using the already eagerly validated external registration HMAC key from `CryptographicKeyMaterial`, with distinct `party-identifier-effective-request-fingerprint-v1` MAC domain and the five exact operation namespaces. It streams a version-1 binary header/body directly into a fresh per-call HMAC-SHA-256 rather than retaining/persisting a canonical raw buffer: big-endian UUIDs/versions, length-prefixed exact UTF-16 code units, -1 absence versus zero-length strings, nullable date markers/epoch days and effective booleans. Creation retains every original parsed property before write normalization; actions retain exact target/version/reasons/replacement. Key/user/process are excluded by the effective input contract. Bounds retain creation UTF-16 versus issuer/reason code-point semantics.

Tests pin independently calculated Python stdlib HMAC vectors: a **145-byte creation payload** including NUL/lone surrogate/bounded blank issuer/Month dates yields `e20fa9df77784d5b3136ea5e203427d5f693d0d8c83fcedc540c3cd737b43dbb`; a **139-byte revoke payload** with exact control/surrogate reasons yields `30b06a74f3f6c4628d1c7983de45ad78c3f5566e7efd9478778aea36372c1a7a`, with synthetic 32-byte 0x5a key and the explicit null-terminated identifier domain. The reference uses independently packed version/UUID/length/epoch fields and UTF-16 big-endian surrogate-preserving encoding; it is not generated by production code.

The other cases cover each creation property, normalization-equivalent raw differences, nullable/default-equivalent effective commands, accepted actor/process/key exclusion, tenant/action/key separation, action targets/versions/replacement, exact absent/empty/whitespace/control/surrogate reasons, maximum UTF-16/code-point bounds, different secret material and fixed-length supported hex matching. Comparison uses MessageDigest.isEqual on decoded 32-byte arrays (zero stand-ins for malformed inputs), rejects unsupported shapes without messages and clears comparison arrays. Existing Party-registration canonical fingerprints/key requirements remain unchanged. No request text or reason is stored/logged or exposed in the shared response.

Final commands use the stable test-scoped Podman API. **./gradlew test succeeds in 6m 13s, 1,561 cases, zero failures/skips**. **./gradlew build succeeds in 2m 28s**, unit tests up-to-date and **28 actual packaged JVM cases execute and pass without skips**. Combined output: `~/.local/share/opencode/tool-output/tool_10977b05b001st3EKkZrJ9b6pW`. Both final saved revisions and nested InputWriter/MacSink have actual full zero-finding Sonar/independent zero-problem JDTLS in the inventory, English responsibility Javadoc and no new compiler warning/suppression/rule change. Existing controlled runtime logs remain visible and `git diff --check` passes.

Task 7.1 is complete; accepted progress is **25/60**. Durable completion/audit storage and actual replay workflows remain subsequent tasks.

## Task 7.2: Safe immutable completions; runtime green, one local finding pending

Implemented the identifier-only schema-1 codec, fixed safe result field encoding, immutable completion entity/composite identity, and caller-session replay persistence. The codec's seven root fields and fifteen result fields exclude raw keys, submitted values, reason strings and protection material. It compares tenant/operation, owner/identifier, original actor/time and expected version against authoritative row metadata. Creation requires version zero, pending state, equal creation/acceptance/modification times and no verification history. Action result versions advance from the original expected version exactly once, with matching resulting state, withdrawal flag clearing, selected primary flag and verification attribution. Unsupported or corrupt fields/schema/UUIDs/enums/types fail with the constant cause-free `Stored identifier completion is invalid` failure; no parser payload is retained in the exception.

The initial codec increment used compile-red followed by executable state/result-coherence red and green, then extracted fixed field names without weakening assertions. On resumption, an additional test exposed acceptance of an inverted stored validity interval. The focused command printed that executable assertion failure before its 120000ms shell deadline interrupted the run; this is a demonstrated behavioral failure, not a completed test run. Added bounded stored text, paired verification and coherent interval checks, without reapplying issuer normalization or current expiration eligibility. Codec/storage/earlier-snapshot/architecture regression then passed in **2m 14s**. Another test-first run completed in **1m 58s** with **14 cases / one expected failure** because a creation snapshot could contain modification history. Added creation-specific coherence; the final full run passes that test and the historical past-expiration/nullable-metadata characterization.

Focused red/green commands (all use `DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0`):

```bash
./gradlew test --tests '*PartyIdentifierCompletionCodecTest' --console=plain
./gradlew test --tests '*PartyIdentifierCompletionCodecTest' --tests '*PartyIdentifierReplayPersistenceTest' --tests '*SnapshotCodecTest' --tests '*CompletionCodecTest' --tests '*CleanArchitectureTest' --console=plain
```

The **eight final codec cases** cover every create/action completion, historical issuer retention, exact allowlists, detached snapshot copies, authoritative scope/acceptance/version checks, corrupt required/optional fields and sanitized cause-free errors. The **two reactive PostgreSQL cases** prove that a completion and its identifier commit together, survive fresh sessions, preserve exact whitespace-sensitive key and tenant/action isolation, and both disappear on a late failure after flush. `PartyIdentifierReplayPersistence` opens no independent session or transaction; the eventual mutation context owns these boundaries. The entity is immutable and returns detached JSON trees. Earlier Party/catalog snapshot codecs remain unmodified and their regression tests pass. English responsibility Javadoc includes the nested test Fixture; dates derive from Month constants.

Final saved-source commands:

```bash
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

**Test succeeds in 6m 37s: 1,571 cases, zero failures/skips. Build succeeds in 2m 40s**, unit tests up-to-date and **28 actual packaged JVM cases, zero failures/skips**. Output: `~/.local/share/opencode/tool-output/tool_109bbfeda001yNi1zFeprjaSGM`. Reports: `build/reports/tests/test/index.html` and `build/reports/tests/quarkusIntTest/index.html`; the new codec/storage XML suites report 8/2 passing cases. The earlier task-7.2 output `tool_109a67c59001PEEFNsiXyKsSyi` was also inspected and confirms test/build success in 6m 29s/2m 37s, but predates these final corruption checks and does not replace their gates. Existing controlled constraint/failure-injection, duplicate-meter and timeout/cancellation cleanup diagnostics remain visible. No compiler warning or suppression/exclusion/configuration change was introduced; `git diff --check` passes. Native and actual historical HTTP replay remain later tasks.

All seven saved revisions have completed Java-25/571-rule standalone Sonar analysis and independent **zero-problem JDTLS** validation. Six files have **zero Sonar findings**; `PartyIdentifierReplayPersistenceTest.java` reports **one issue / zero Hotspots** at **2026-10-04 20:52:33.532**, S2 lines **2520–2550**, for blob `c33e3bbc1c4972743481aa151ff6b228361aeae4`. Its JDTLS validation reports zero at **20:52:33.257**, J lines **22589–22602**. The count-only Sonar log provides no rule key, message or location. Obtain the actual Problems-view diagnostic for this saved revision before choosing a fix; passing Gradle and the other files' zero results are not substitutes.

Task **7.2 remains unchecked** and implementation pauses at **25/60 complete** pending that diagnostic, correction/reanalysis and invalidated gates. The saved-file inventory records all current results separately. The temporary test-scoped Podman API is stopped at this pause and can be restarted with the existing documented `--time=0` command.

### Task 7.2 continuation: supplied method-reference diagnostic resolved

The developer's screenshot identifies **java:S1612**, "Replace this lambda with method reference 'Assertions::assertNull'", on the post-rollback identifier assertion. Replaced only that lambda with `Assertions::assertNull`, imported Assertions and removed its now-unused static assertNull import. All assertions remain intact. Final storage-test blob: `58a0cf3162d2ea918729add00cc4d32d5d543520`.

Fresh full 1/1 standalone Sonar (Java 25/571 rules) completes at **2026-10-04 21:16:23.655**, S2 **2551–2581**, with **zero issues/Hotspots**. Independent JDTLS validation at **21:16:23.516**, J **22613–22623**, reports **zero problems**. The other six Java revisions are unchanged, so their completed zero analyses remain applicable. No rule setting, exclusion or suppression changed.

Restarted the documented transient test-scoped Podman API and reran the same required full commands. **./gradlew test succeeds in 5m 23s**, with the unchanged 1,571 passing cases and no failures/skips. **./gradlew build succeeds in 5s, all tasks up-to-date**; the latest 28 packaged JVM results remain the actual passing 2m 40s build above, not a newly executed packaged run. Output: `~/.local/share/opencode/tool-output/tool_109db376c001GNlFb8lIAr1Asi`. This supersedes the local gate blocker and closes task 7.2 at **26/60 complete**; exact binary reason audit follows in 7.3.

## Task 7.3: Exact private binary withdrawal audit

Wrote four codec tests first: compile-red identifies the missing type in **5s**. Explicit big-endian code-unit serialization initially executes **10 cases / two expected failures in 1m 41s** for missing encode/decode bounds and corrupt-payload rejection. Added the 64/1000-code-point and 256/4000-byte limits, even-byte validation and a constant cause-free stored-corruption failure. The independent `A`, NUL and supplementary-character vector is `00 41 00 00 d8 3d de 00`; no charset replacement is used. Null remains null, empty remains zero bytes, and controls, exact case/whitespace, supplementary pairs and unmatched surrogate units round-trip without normalization.

Wrote four reactive storage cases before the helper: compile-red identifies the missing persistence type in **4s**. An initial compile attempt used a native-mutation convenience method absent from the resolved Hibernate Reactive API; switched to the repository's supported `createNativeQuery(...).executeUpdate()` pattern. The first executable storage run fails on test timestamp representation (native timestamptz is OffsetDateTime); after comparing its exact Instant, the next **14 cases / one failure in 1m 58s** exposes the missing stored reason columns. Added explicit binary TypedParameterValue bindings for code/detail, including nulls. Codec/storage/completion/replay/architecture regression passes in **1m 51s**. No incomplete reason-write path remains.

`PartyIdentifierAuditPersistence.recordAudit` executes a fixed bound insert using the supplied Hibernate Reactive session and accepted audit metadata. PostgreSQL generates the audit UUID through V8's existing uuidv7 default. It creates no session/transaction or event, performs no eligibility decision, and checks the exact one-row result. SQL and binary conversion remain in Infrastructure; no entity/DTO/reflection or public audit route is introduced. The private audit model's redacted representation is retained. The database tests prove absent/empty/control/NUL/supplementary/unmatched-surrogate and maximum reasons survive fresh sessions with exact actor/time/action/resulting version; qualified FK and accepted-action uniqueness failures retain no extra history; late transaction failure leaves no audit; disabled outbox still retains audit and produces no event. The migration remains immutable. English responsibility Javadoc includes the nested Fixture, and controlled timestamps derive from Month.OCTOBER.

Focused commands use the stable test-scoped Podman API and QUARKUS_HTTP_TEST_PORT=0:

```bash
./gradlew test --tests '*PartyIdentifierReasonCodecTest' --console=plain
./gradlew test --tests '*PartyIdentifierReasonCodecTest' --tests '*PartyIdentifierAuditPersistenceTest' --console=plain
./gradlew test --tests '*PartyIdentifierReasonCodecTest' --tests '*PartyIdentifierAuditPersistenceTest' --tests '*PartyIdentifierCompletionCodecTest' --tests '*PartyIdentifierReplayPersistenceTest' --tests '*CleanArchitectureTest' --console=plain
```

The first full gate passes **1,579 tests in 5m 26s** and **28 actual packaged JVM cases in the 2m 17s build**, output `tool_109f51a85001r3LsmO64QvwBg7`. Local analysis reports one initial persistence finding. Code review independently identifies its restricted `record` method name, explicitly prohibited by the researched preflight; renamed it to `recordAudit` and updated the two consumers without changing behavior. No finding rule key/message is claimed from the count-only output. The final saved persistence/test revisions both reanalyze with zero issues/Hotspots and zero JDTLS problems; both unchanged codec revisions are already clean. Final **./gradlew test succeeds in 5m 23s (1,579 cases, zero failures/skips)** and **./gradlew build succeeds in 2m 16s (28 actually executed packaged JVM cases, zero failures/skips)**. Combined output: `~/.local/share/opencode/tool-output/tool_109fe08e9001aEalN0O6PcM6i5`. Reports retain the separate unit/reactive and quarkusIntTest results. No compiler warning, suppression or rule configuration change is introduced; native/workflow/HTTP acceptance remains subsequent work.

The Microsoft Java test-discovery delegate again reports an internal BinaryTypeBinding annotation-discovery NullPointerException in J lines 22670–22724 and 22732–22786. Document validation independently reports zero source problems, and Gradle compiles/executes both new tests. This is retained as an IDE test-discovery diagnostic, not a source/compiler warning or a hidden model disagreement. The final saved Java inventory records actual analyses rather than empty Problems-panel assumptions. Task 7.3 is complete at **27/60**; transaction locking/restoration is next.

## Task 7.4 initial increment: exact lock identities and qualified restoration; incomplete

Wrote three lock-identity cases first. Compile-red completes in **5s** for the missing helper. A runnable length-delimited UTF-8 digest executes **9 cases / one expected failure in 1m 44s**, demonstrating that different unmatched surrogate key units collapse to the same encoding. Switched to length-prefixed explicit UTF-16 code units. Lock/architecture regression passes in **1m 40s**. `PartyIdentifierMutationLocks` now uses SHA-256-truncated integer identities in fixed distinct two-integer namespaces (`0x5049524b` replay, `0x50495343` primary scope), avoiding the existing one-bigint lock space. Full persisted key tuples remain authoritative; test constructor functions permit forced hash collisions in the upcoming coordinated database checks. No raw key is logged or treated as ownership evidence.

Wrote two reactive qualified-reader cases before the helper. Compile-red completes in **4s** for the missing reader; a subsequent compile correction uses the actual typed native API's SelectionQuery result rather than Query, with no cast/suppression. Runnable reader tests then execute **8 cases / one expected failure in 1m 51s** because target restoration filters out nonverified history. Removed that storage-layer eligibility restriction: all five states, archived owned Party, past-date metadata, version/protected-value/legacy-issuer preservation and wrong-owner/scheme concealment now pass in the full suite.

`PartyIdentifierMutationReader` uses a qualified scalar FOR SHARE owner/type read without root mutation, a qualified immutable scheme-ID lookup, exact catalog-code resolution and shared creation-scheme locking. Target/current-primary row restoration uses qualified FOR UPDATE and clears stale session-managed state before native reads; the current-primary query includes verified flags regardless of expiration. The helper owns no lifecycle/version eligibility policy. Its calling transaction/context still owns order, lifetime and scope guards. No infrastructure entity or session crosses an inner port, no JDBC request I/O is added, and new fixture dates derive from Month.OCTOBER.

Focused commands use the stable test-scoped Podman API and QUARKUS_HTTP_TEST_PORT=0:

```bash
./gradlew test --tests '*PartyIdentifierMutationLocksTest' --console=plain
./gradlew test --tests '*PartyIdentifierMutationLocksTest' --tests '*CleanArchitectureTest' --console=plain
./gradlew test --tests '*PartyIdentifierMutationReaderTest' --console=plain
```

Full **./gradlew test succeeds in 5m 23s**. **./gradlew build succeeds in 7m 34s**, rerunning the full test suite and packaged JVM cases. Latest reports show **1,584 tests and 28 packaged JVM cases, zero failures/skips**. Output: `~/.local/share/opencode/tool-output/tool_10a140f1e001VO5lKihVI1XdtS`, success markers at lines 4848/9711. The four current Java revisions have completed independent **zero-problem JDTLS** validation; three have clean Sonar. `PartyIdentifierMutationReader.java` reports **two issues / zero Hotspots** at **2026-10-04 22:18:52.904**, S2 **2799–2829**, on blob `38676fda368d951d22d7a90e106898941b92daa9`. Rule/message/location are absent from the count-only log; request the actual Problems diagnostics before selecting fixes. No precise rule is inferred from the count and no source warning is suppressed.

Task **7.4 remains unchecked** and progress stays **27/60 complete**. Beyond resolving these findings, its independent-session observed lock-order/contention, forced-collision/full-tuple lookup, namespace isolation, shared-root blocking, catalog/current-primary and fresh-state-after-wait checks remain required. Existing unit/qualified-reader/full JVM results do not substitute for those pending checks. The temporary test-scoped Podman API is stopped at this quality pause; `git diff --check` passes.

### Task 7.4 continuation: duplicate parameter-name findings resolved

The supplied screenshot identifies **java:S1192** on the repeated `tenant` parameter name and also underlines the repeated `party` name. Extracted `TENANT_PARAMETER` and `PARTY_PARAMETER` constants and used them at all six binding sites. The saved reader blob is `6d352434b49dcd2d36bec5f4700aeb8c58e453a7`; SQL text, query qualification, locks and result mapping are unchanged.

Actual full 1/1 standalone Sonar (Java 25/571 active Java rules) completes at **2026-10-04 22:37:53.313**, S2 **2892–2922**, with **zero issues/Hotspots**. Independent JDTLS validation at **22:37:53.363**, J **22911–22921**, reports **zero source problems**. These replace the reader's earlier two-finding analysis; the other three task-7.4 saved revisions are unchanged and their zero analyses remain applicable. No rule override, exclusion or warning suppression was introduced.

Restarted the documented transient Podman API and ran the required full commands with `DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0`. **./gradlew test succeeds in 5m 19s, 1,584 cases, zero failures/skips**. **./gradlew build succeeds in 2m 11s**, unit tests up-to-date and **28 actually executed packaged JVM cases, zero failures/skips**. Output: `~/.local/share/opencode/tool-output/tool_10a25f031001j63aK06XVPl2An`; current test/quarkusIntTest HTML reports confirm both counts. Existing controlled runtime diagnostics remain visible. `git diff --check` passes. The targeted source-quality blocker is resolved; task 7.4 remains unchecked at **27/60** until its previously listed coordinated locking/restoration checks are completed.

### Task 7.4 coordinated database verification complete

Added `PartyIdentifierLockCoordinationTest` with independent `openSession`/READ COMMITTED transactions, bounded completion-stage barriers and a separate monitor that observes actual granted/waiting PostgreSQL advisory locks (`objsubid = 2`). It does not sleep or run concurrent operations through one session. Four cases prove: a waiting equivalent key sees the newly committed original completion; forced identical hashes serialize distinct exact keys without false replay and do not block the other namespace; shared owner locks permit other readers but block exclusive root writers; and a scope waiter reloads the committed next identifier version/issuer rather than preliminary state. A root NOWAIT probe succeeds while only the replay key is held. During observed scope contention an exclusive root probe fails with SQLState 55P03 while a child NOWAIT probe still succeeds, proving owner-before-scope-before-child locking; the winner subsequently commits and the contender restores fresh child state. Root version remains zero.

The original three coordinated cases pass in the first focused **1m 53s** run and characterize the already implemented lock mechanics. Adding the explicit shared-root writer probe initially fails in **1m 51s** because the test expects an unwrapped PgException; the resolved Hibernate Reactive API exposes SQLException through LockTimeoutException. Updated only the test's SQLState assertion to recognize that observed wrapper. SQL exceptions are inspected only by test code; request processing still uses Hibernate Reactive. Added reader coverage for exact creation scheme resolution/shared recheck and retained past-date VERIFIED primary restoration. There are now **three lock-identity, three qualified-reader and four coordinated cases**.

Final **./gradlew test succeeds in 5m 28s with 1,589 cases, zero failures/skips**; **./gradlew build succeeds in 4s up-to-date**, retaining the latest 28 actually executed passing packaged JVM results from the production-reader correction's 2m 11s build. Output: `~/.local/share/opencode/tool-output/tool_10a3edaf9001RhAxjyqoIwUAxN`. The prior wrapper-fix full run also passes in 5m 29s/build 6s, output `tool_10a37f2e8001qzYEdOuC36FYhU`; final probes supersede that revision. Current coordination XML has four passing cases and only the two expected NOWAIT SQLState 55P03 probe warnings, no source warning or suppressed cleanup failure. Other pre-existing controlled runtime diagnostics remain visible in the full suite.

All five final task-7.4 Java files (including nested Scope/Fixture types) have full saved-source zero Sonar findings and independent zero JDTLS problems. English responsibility Javadoc and Month-derived clocks remain intact. Current final blobs and analyses are in `java-inventory.md`; no suppression/rule override, assertion weakening or production behavior change was introduced by verification. `git diff --check` passes. Task 7.4 is complete at **28/60**, with guarded writes next in 7.5; complete mutation-context/HTTP/native acceptance still belongs to later tasks.

## Task 7.5: Guarded identifier writes and immediate-index-safe replacement

Wrote five database tests first: compile-red identifies the missing helper in **4s**. A runnable bulk writer executes **11 cases / one expected failure in 1m 50s** because promotion precedes demotion and violates the immediate verified-primary index. Reordered the persisted Domain plan to await demotion before promotion. Added wrong-owner/protection-preservation and initial-creation guard cases; the next **13 cases / one failure in 1m 48s** demonstrates that an historical version can be inserted as a new accepted record. Added a deferred initial version/state/audit/microsecond/verification shape guard. No incomplete insertion/replacement path remains.

`PartyIdentifierMutationPersistence` uses the caller's reactive session exclusively. Inserts persist/flush the mapped new candidate, clear managed state, then compare a refreshed qualified safe result. Updates freshly lock the qualified source, check its expected identifier version and immutable protected/creation history, and execute fixed bulk HQL with tenant/Party/identifier/scheme/version predicates. The Domain-computed next version is assigned exactly once; no dirty @Version entity is mutated. Only issuer/dates/state/primary/verification/modification audit/version columns are assigned. Guarded zero/unexpected writes are diagnosed within the same owner/scheme scope; actual stale versions become the neutral typed IdentifierVersionMismatch, inconsistent scope/counts remain technical failures. Reload compares safe fields, full protection and audit before returning. Stable scheme code is read in the same session without catalog lifecycle admission. Only the known active-value/verified-primary constraints map to their approved neutral conflicts; unknown failures remain for the existing outer translation/sanitized response boundary.

Nine PostgreSQL cases now prove initial version-zero insert, verify/revoke exact one-step versions, preserved protected bytes/hash/key/normalization/mask and creation audit, retained verification attribution, unchanged Party version, stale expected-version nonmutation, exact active-value conflict, awaited two-record primary replacement with independent increments, primary-conflict rollback, wrong-owner/protected-candidate rejection, historical-version insert rejection, DATE/null metadata bindings, and full demotion rollback when promotion encounters an advanced target. Domain policy constructs the complete replacement plan; Infrastructure selects no lifecycle or replacement policy. No JDBC request processing, new transaction style, migration change, response mapper or API route is introduced. English responsibility Javadoc covers Fixture; test dates/clocks derive from Month.OCTOBER.

Focused red/green command uses the documented stable test-scoped Podman API and dynamic HTTP port:

```bash
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*PartyIdentifierMutationPersistenceTest' --console=plain
```

The first full green executes **1,596 tests in 5m 23s**, and build in **2m 15s** executes **28 packaged JVM cases**, all without failure/skip. Output: `tool_10a5615e3001pGifkalHoHTBEz`. After adding DATE/null and late-demotion rollback characterization, final **./gradlew test succeeds in 5m 23s with 1,598 cases, zero failures/skips**; final **./gradlew build succeeds in 6s up-to-date**, retaining those latest 28 actually executed passing packaged results. Output: `~/.local/share/opencode/tool-output/tool_10a62729f001RO3Z26Mv3hqGuG`. Existing controlled runtime diagnostics remain visible. Both final saved files have full Java-25/571-rule zero Sonar issues/Hotspots and independent zero JDTLS problems, without exclusions/suppressions or new compiler warnings. `git diff --check` passes. Task 7.5 completes at **29/60**; full mutation-context/workflow/HTTP/native acceptance remains subsequent work.

Sonar log rotation at **2026-10-04 23:39** moved the previous S2 ranges (tasks 5.1–7.5 production, up to line 3108) unchanged to adjacent `SonarQube for IDE.1.log`. That rotated file was inspected; historical ranges must use it. New test analyses are in the active `SonarQube for IDE.log`, starting at line 1. The inventory distinguishes rotated **S2R** and active **S2** for this increment.

## Task 7.6 current increment: safe event codecs/modes; serializer finding pending

Wrote the new codec/message tests before production changes. Compile-red identifies the missing changed-event candidate in **5s**. A runnable candidate plus partial mapper executes **11 cases / three expected failures in 1m 50s**: primary payload field is missing, changed-event decoding lacks the safe corrupt-result failure, and the serializer rejects the five new names. Added the fixed four-field mapping, closed decoder and serializer support. New event plus existing outbox/architecture regression passes in **1m 56s**; mode coverage follows before the new identifier-only helper.

`PartyIdentifierChangedOutboxCandidate` extends the existing sealed hierarchy with UPDATED/VERIFIED/REJECTED/REVOKED/PRIMARY_CHANGED kinds and their five approved v1 names. It carries only affected identifier/version, Party/scheme/status/primary and accepted event metadata; resulting shape validation is not transition eligibility. The original creation candidate, name and three-field payload remain unchanged. Mapper storage keeps its established string-valued payload convention, adding strict `isPrimary` true/false strings only for new kinds. Decoder checks aggregate/version/allowlist/canonical owner/status/flag and emits a constant cause-free `Stored identifier event is invalid` error on corrupt changed-event data. The broker serializer validates the same four safe fields and emits the new flag as a JSON boolean; existing payloads keep their exact previous serialization. Reasons, masked/protected values and replay material enter neither representation.

`PartyIdentifierOutboxPersistence` validates only identifier creation/change candidates and applies the existing disabled/stored-only/published mode in the supplied session. It opens no transaction, performs no broker call, and stores only pending intent when enabled. Four codec/compatibility/mode tests and two broker-shape tests pass for all five new kinds, original event identity/version/actor/time, both primary-change flags, invalid private/enum/UUID/boolean/version fields, and all three storage modes. Complete mutation event assembly/count/replay behavior is still exercised by later context/Application workflows; this increment does not claim their HTTP/native acceptance.

Commands use the stable test-scoped Podman API and dynamic HTTP port:

```bash
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*PartyIdentifierChangedEventCodecTest' --tests '*PartyIdentifierChangedMessageTest' --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*PartyIdentifierChangedEventCodecTest' --tests '*PartyIdentifierChangedMessageTest' --tests '*Outbox*Test' --tests '*CleanArchitectureTest' --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

Full **test succeeds in 5m 37s with 1,604 cases, zero failures/skips**. **Build succeeds in 2m 18s**, unit tests up-to-date and **28 actually executed packaged JVM cases, zero failures/skips**. Output: `~/.local/share/opencode/tool-output/tool_10a7bfc66001PyFrcNKOseNLl6`; latest test/quarkusIntTest reports confirm counts. Existing controlled runtime diagnostics remain visible; no compiler warnings or suppression/rule changes were introduced. English responsibility Javadoc includes Kind and all new fixtures use Month-based dates. `git diff --check` passes.

All seven saved Java revisions have completed full Java-25/571-rule Sonar and independent **zero-problem JDTLS**. Six report **zero Sonar issues/Hotspots**; `OutboxMessageSerializer.java` reports **one issue / zero Hotspots** at **2026-10-05 00:09:16.427**, active S2 **125–155**, on blob `db644c28a987e8bf469e02afde837c664f65a299`. Its J validation at **00:09:16.585**, **23049–23062**, reports zero source problems. The count-only log has no finding rule/message/location. Obtain the actual Problems diagnostic for that saved serializer before choosing a correction, then rerun invalidated checks and complete task-7.6 verification.

Task **7.6 remains unchecked**; accepted progress is **29/60**. Implementation pauses on this real local quality gate, with current source/test evidence recorded in `java-inventory.md`. The temporary test-scoped Podman API is stopped at the pause; no permanent runtime/IDE configuration was altered.

### Task 7.6 continuation: current finding reconfirmed on 2026-10-09

The apply workflow was resumed with a confirmed repository-local OpenSpec root, schema `ears-feature`, and CLI progress **29/60 complete, 31 remaining**. All returned proposal/specification/design/task context files were read before implementation. The saved serializer still has blob `db644c28a987e8bf469e02afde837c664f65a299`; no Java source was changed in this continuation.

Opening `OutboxMessageSerializer.java` with the installed Antigravity CLI triggered a fresh standalone analysis. Source: `~/.config/Antigravity IDE/logs/20261009T110402/window1/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log:327–357`. The configuration identifies this single production file, Java 25 with preview disabled and 571 active Java rules. Full **1/1** analysis completed at **2026-10-09 11:13:14.428**, again reporting **one issue / zero Security Hotspots**. This single-file run reports no unresolved-import/classpath warning; the separate earlier mixed-source startup analyses did report incomplete test-model warnings and are not substituted for acceptance evidence.

The current log still contains only the finding count, without its rule key, message or location. The actual Antigravity Problems diagnostic (text or screenshot) is required before selecting the targeted correction. No new test/build/native execution or fresh JDTLS acceptance is claimed. Task 7.6 stays unchecked, subsequent tasks have not started, and implementation remains paused pending the diagnostic.

### Task 7.6 final correction and verification

The user supplied the actual **java:S3824** diagnostic on `brokerPayload`: replace `Map.containsKey()` with `Map.computeIfPresent()`. Replaced the guarded put with `computeIfPresent`, retaining the approved boolean conversion and the original creation payload's absence of `isPrimary`. This is a behavior-preserving refactor of the already recorded test-first increment; no new behavior-red is claimed for the correction. Final serializer blob: `d036f91b253af22633731954cf31f3b0476fef05`.

Current single-file full **1/1**, Java-25/571-rule Sonar analysis completes at **2026-10-09 11:17:40.484**, with **zero issues/Hotspots**: current `20261009T110402/window1` Sonar log **358–388**. Separate JDTLS validation completes at **11:17:41.586**, with **zero problems**: documented workspace `.metadata/.log` **23586–23596**. No configuration, rule exclusion or suppression changed.

The focused event/mode/outbox/architecture command recorded above passes in **3m 11s**. Required `./gradlew test` passes in **9m 23s**, with **1,604 tests, zero failures/skips**; `./gradlew build` passes in **3m 43s**, with **28 actually executed packaged JVM tests, zero failures/skips**. Both use `DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0`; a temporary `podman system service --time=0` supplies the existing test-scoped socket. Combined full output: `~/.local/share/opencode/tool-output/tool_1217b422c001RnANcuwD75WZms`; reports are under `build/reports/tests/test` and `build/reports/tests/quarkusIntTest`.

No compiler warnings were emitted. Existing controlled failure-injection, duplicate-meter and HR000090 timeout/cancellation cleanup diagnostics remain visible and separate from source findings. Task 7.6 is complete; progress becomes **30/60**. Mutation workflow event assembly/replay and native acceptance remain in their designated later tasks.

## Task 7.7: Sequential identifier context, runtime green and one local finding pending

Added `HibernateReactivePartyIdentifierMutationAdapterTest` before the adapter/context implementation. The first focused command reaches compile-red in **9s** for the missing adapter/effects types. A minimal nonimplementing adapter then executes the eight initial cases: **six expected failures in 3m 8s** for absent commit/replay/audit, failure-identity propagation, cancellation and transaction limits. The reported fourteen total cases include existing automatically selected fixture checks; only eight are new feature tests. These are recorded separately from subsequent implementation compile corrections.

Implemented a fresh per-subscription transaction adapter and sequential context using the existing reader, guarded writes, two-integer locks, replay codec, binary audit and optional outbox stores. `PartyIdentifierMutationEffects` coordinates only same-session completion/audit/event storage; `PartyIdentifierMutationJournal` verifies accepted result/effect identity, attribution, exact original versions, required withdrawal audit and one event per written aggregate. Neither component chooses Domain eligibility or primary replacement. The context enforces replay-before-owner-before-resolved-scope-before-child access, immutable tenant/owner scope, one key and one primary scope, no concurrent/recovered/closed/foreign-context capabilities and no new writes on replay. Completion and emitted outcome must describe the same accepted result.

`PartyIdentifierAtomicSession` follows the existing cancellation-shield transaction mechanism: downstream cancellation becomes transaction failure, rollback finishes before session closure, and safe outcomes emit after commit/closure. The operation timeout stays within the transaction; existing READ COMMITTED/read-write/local lock/statement settings are reused. `PartyIdentifierPersistenceFailures` translates only classified dependency outages/deadlines and preserves known and unexpected failures. No broker call, automatic write retry, manual subscription, blocking wait or HTTP contract enters production processing. The current API composition remains on the existing registration workflow until tasks 8.1–8.2.

The initial eight cases pass in **2m 57s** after implementation. Three additional characterization cases cover an actual foreign-context probe, wrong event attribution, two-record primary replacement with mandatory per-record events, and actual lock/statement timeouts with pool reuse; they pass with the existing guards and are not claimed as additional behavioral-red cycles. Final **11 adapter cases** and architecture checks pass in **3m 26s**. Invalid completion, missing audit/event, incomplete two-record events and deliberately late failure all leave prior rows and effect counts unchanged. Exact NUL/empty reasons persist, Party version remains unchanged, and successful retries reuse failed keys. Production/test responsibility Javadoc and Month-based valid date fixtures are present.

Commands (each uses the temporary test-scoped Podman API and dynamic HTTP port):

```bash
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*HibernateReactivePartyIdentifierMutationAdapterTest' --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*HibernateReactivePartyIdentifierMutationAdapterTest' --tests '*CleanArchitectureTest' --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

Final full **test succeeds in 9m 27s, 1,615 tests with zero failures/skips**. **Build succeeds in 3m 46s**, executing **28 packaged JVM cases with zero failures/skips**. Combined output: `~/.local/share/opencode/tool-output/tool_1219a7e990011jy3eApsrB0Xvs`; actual summaries remain under `build/reports/tests/test` and `build/reports/tests/quarkusIntTest`. No compiler warnings were emitted. Existing full-suite controlled failure/duplicate-meter/HR000090 cleanup diagnostics remain visible; the focused identifier-context run shows only the deliberate SQL lock/statement-timeout diagnostics and duplicate-meter warning.

All seven saved Java files have current full 1/1 standalone Java-25/571-rule Sonar analysis and separate **zero-problem JDTLS** validation, recorded with blobs in `java-inventory.md`. Six files report zero Sonar issues/Hotspots. **`PartyIdentifierMutationJournal.java` reports one issue / zero Hotspots**, full analysis completed **2026-10-09 11:55:17.220**, current `20261009T110402/window1` Sonar log **1992–2022**, blob **`d3529c40cdd3f1c6b34f5d1135ffe96984e7704b`**. Its independent JDTLS reports zero at **11:55:13.377**, lines **24476–24489**. The count-only log does not contain rule/message/location; obtain the actual Problems diagnostic before selecting its targeted correction. No local rule/configuration/exclusion/suppression was changed.

Task **7.7 remains unchecked**, accepted progress stays **30/60**. Pause before 8.1 until the journal finding is identified, fixed, reanalyzed and invalidated gates rerun. No native or complete HTTP mutation-workflow acceptance is inferred from internal adapter tests.

### Task 7.7 final correction and verification

The user supplied the actual **java:S6213** diagnostic on the journal's `record` method. Renamed it to `recordAcceptedWrite` and updated both context call sites, without changing behavior, policies, tests or analysis configuration. Final journal blob: `3e8a25a36e784eeaef7ec1b6a3dbfdca2206ccdf`; context blob: `d10e18b0faca47ff6ff348b877c97cc7b7c4a2d0`.

Current full 1/1 Java-25/571-rule Sonar reports **zero issues/Hotspots** for the journal at **2026-10-09 12:16:21.085**, S3 **2160–2190**, and the context at **12:16:22.284**, S3 **2191–2221**. Independent JDTLS reports **zero problems** for the journal at **12:16:36.252**, J **24566–24576**, and the context at **12:16:48.596**, J **24578–24588**. The five unchanged task-7.7 saved revisions retain their previously recorded zero findings.

Focused adapter/architecture checks pass in **3m 3s**. Required final `./gradlew test` passes in **9m 31s** with **1,615 tests, zero failures/skips**; `./gradlew build` passes in **3m 21s**, with **28 actually executed packaged JVM cases, zero failures/skips**. Both commands use the recorded test-scoped Podman API and dynamic HTTP-port environment; combined output: `~/.local/share/opencode/tool-output/tool_121b0d0ec0018XFWs0ycoelq24`. No new compiler warning, suppression or rule setting was introduced; existing controlled runtime diagnostics remain visible. Task 7.7 is complete and progress becomes **31/60**. Proceed to 8.1; complete new mutation HTTP/native acceptance remains in its later tasks.

## Task 8.1: Replay-first Application workflow, build deadline incomplete

Added `PartyIdentifierRegistrationWorkflowTest` first. The focused command reaches compile-red in **9s** for the new constructor and outcome method. A runnable nonimplementing workflow then produces **eight executable feature failures in 3m 1s**, covering missing replay, eligibility, safe creation, locked-scheme validation and failure identity. Fourteen reported cases include six existing automatically selected fixture checks; only eight are feature-red cases.

Implemented `PartyIdentifierRegistrationWorkflow` behind the new durable constructor of `RegisterPartyIdentifierUseCase`. It captures one keyed fingerprint per subscription from exact original parsed input, serializes/reads the completed creation key first, returns an equivalent original safe result without owner/catalog/protection/clock access, and rejects changed fingerprints before new-operation dependencies. On a miss it resolves the owned Party/type, resolves and serializes the exact scheme identity, read-locks/rechecks the same scheme, applies shared admission policies, captures one UTC instant truncated to PostgreSQL microseconds, normalizes/protects only a newly admitted value, and inserts the identifier, safe completion and original compatible creation-event intent through the same mutation context.

`PartyIdentifierPreparation.requireEligible` now accepts a caller-supplied locked scheme at package scope; existing initial-registration repository lookup and policies remain intact. `RegisterPartyIdentifierUseCase.executeOutcome` exposes the safe committed disposition and observation. The existing constructor remains available only through this staged implementation until the specified task-8.2 CDI cutover and obsolete-consumer checks. No CDI producer, HTTP binding, retired port/adapter or initial-registration path was changed in 8.1; this internal workflow increment does not claim public durable replay rollout.

The eight new cases and existing additional/initial registration plus architecture regression pass in **3m 11s**. Two added characterization cases verify lazy per-subscription fingerprint/clock capture and locked-scheme normalized length limits; the final ten workflow cases and compatibility/architecture checks pass in **3m 30s**. Coverage includes both Party types, exact raw value/issuer fingerprints, replay after owner absence/scheme retirement/dependency failure with a new actor/process, current-scheme identity inconsistency, known eligibility failures, optional/inclusive expiration, future issue dates, coherent interval rejection, provisional flags, original creation completion and event metadata, protected normalized values, and failed-key reuse.

Commands:

```bash
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*PartyIdentifierRegistrationWorkflowTest' --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*PartyIdentifierRegistrationWorkflowTest' --tests '*RegisterPartyIdentifierUseCaseTest' --tests '*CreateNaturalPersonUseCaseTest' --tests '*CreateLegalEntityUseCaseTest' --tests '*CleanArchitectureTest' --console=plain
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain && DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

The combined full command had a **1,200,000ms deadline**. Standalone `test` completed **BUILD SUCCESSFUL in 9m 18s**, output line **4860**. The following `build` reran `compileTestJava` and the complete unit suite, then reached packaged build/`integrationTestClasses` before the shell deadline interrupted it. Current full report contains **1,625 tests, zero failures/skips**. There is no final build success or accepted packaged result for this revision; earlier 28-case packaged results are not substituted. Output: `~/.local/share/opencode/tool-output/tool_121cbc03e001ix1jFdfVc8o7C7`. After interruption `./gradlew --status` reports the daemon IDLE. No root cause is inferred for the rerun beyond the observed tasks, and no test budget/assertion was weakened.

All four saved Java revisions have completed full 1/1 Java-25/571-rule standalone Sonar with **zero issues/Hotspots**, plus independent **zero-problem JDTLS**, recorded in `java-inventory.md`. Responsibility Javadoc covers the two test nested types; valid dates use Month and derived instants. No compiler warnings, exclusions, suppression or rule/configuration changes were introduced. Saved blobs were rechecked after the interrupted gate and remain unchanged.

Task **8.1 remains unchecked**, accepted progress **31/60**. Asked for guidance to rerun `./gradlew build` separately with a 30-minute deadline and continue on success; the developer requested an explanation of the current work before that decision. The workflow and gate status were explained, and no retry or task-8.2 cutover is claimed yet. Native/public replay acceptance remains later work.

### Task 8.1 build deadline resolved

After the requested explanation, the user explicitly authorized execution. Ran the build separately with an **1,800,000ms deadline**, without changing source or skipping required tests:

```bash
DOCKER_HOST=unix:///tmp/opencode/party-registry-podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --info --console=plain
```

**BUILD SUCCESSFUL in 3m 51s**, **17 tasks: one executed, sixteen up-to-date**. Gradle explicitly reports `compileTestJava` and `test` **UP-TO-DATE**, preserving the passing 1,625-case full suite, and actually executes all **28 packaged JVM cases with zero failures/skips**. Output: `~/.local/share/opencode/tool-output/tool_121e17bb1001YWiOwmB7wczKSQ`, unit-test up-to-date evidence at **92–116**, packaged execution at **187 onward**, final success at **984**. The interrupted combined run's redundant compilation/test execution is observed, but its exact invalidation cause is not established by this new log. No runtime budget, assertion, cache setting or acceptance criterion was relaxed.

The four source blobs and completed zero Sonar/JDTLS results remain valid; no further Java edits occurred. Task 8.1 is complete at **32/60**. The prior shell-deadline interruption remains historical evidence. Next is task 8.2's CDI cutover and obsolete-consumer cleanup.
