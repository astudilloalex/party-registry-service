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

## Implementation progress

- Task 1.1 baseline verification completed.
- Task 1.2 effective installed rule research completed.
- Task 1.3 baseline execution interrupted by the shell deadline; no passing result claimed.
- Tasks 1.3–14.6 remain pending.
- No production or test Java changes have been made.
