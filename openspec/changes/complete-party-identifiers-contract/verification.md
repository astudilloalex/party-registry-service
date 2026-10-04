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
