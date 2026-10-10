# Identifier Search Implementation Evidence

## Task 1.1: Baseline inspection and verification blocker

Baseline collected on 2026-10-10 before implementation edits. The initial
`git status --short` returned no changes. OpenSpec reports a repo-local
`ears-feature` change with 0/44 tracked tasks complete; `openspec list --json`
confirms the project root. All five apply context files and the mandatory
Clean Architecture and Quarkus reactive steering were read.

| Check | Observed result |
| --- | --- |
| `java -version` | Debian OpenJDK 25.0.4.1 |
| `./gradlew --version` | Gradle 9.7.0; launcher and daemon Java 25.0.4.1 |
| Quarkus configuration and dependency resolution | BOM/plugin 3.40.1 |
| `./gradlew dependencyInsight --dependency hibernate-reactive-core --configuration runtimeClasspath` | Successful; Hibernate Reactive 3.4.2.Final |
| `./gradlew dependencyInsight --dependency mutiny --configuration runtimeClasspath` | Successful; resolved dependency report retained in `/home/alex/.local/share/opencode/tool-output/tool_1273d5b45001QDn1hn7AGZ3gCf` |
| `podman info --format json` | Podman 5.4.2, rootless; Docker-compatible socket exists at `/run/user/1000/podman/podman.sock` |
| Configured native builder | `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25` |
| `podman run --rm --pull=never --entrypoint native-image quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25 --version` | Successful; Mandrel-25.0.4.1-Final, Java 25.0.4.1 |
| `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain` | BUILD SUCCESSFUL in 9s, but `:test` was UP-TO-DATE; this invocation did not freshly execute tests |

The resolved Hibernate Reactive JAR was inspected with `javap`: its Mutiny
SessionFactory exposes `openSession`, `withSession`, and `withTransaction`;
Session exposes `setDefaultReadOnly` and `withTransaction`. The cached Mutiny
3.3.0 JAR exposes `runSubscriptionOn`, `emitOn`, `onCancellation`, cancellation
`invoke`/`call`, and emitter termination callbacks. This API inspection does
not prove acquisition cancellation, cleanup, or thread/context behavior; the
specified runtime tests remain pending.

### Fresh IDE model mismatch

The installed Antigravity CLI was used to open an unchanged existing file:

```shell
/opt/antigravity-ide/bin/antigravity-ide --reuse-window "/home/alex/Documents/Development/backend/quarkus/party-registry-service/src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/HibernateReactivePartyIdentifierReadAdapter.java"
```

Fresh Sonar analysis ran at 14:18:21–14:18:22 and reported 0 issues and 0
Security Hotspots, with Java source level 25 and 571 active Java rules.
However, its `sonar.java.libraries` still contains Quarkus **3.33.3.1**
artifacts, inconsistent with the current Gradle-resolved **3.40.1** platform.
The reported zero counts are therefore not accepted as current-model quality
evidence. No fresh JDTLS acceptance is claimed.

Source: `/home/alex/.config/Antigravity IDE/logs/20261010T095302/window1/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log`,
lines 1078–1108; line 1080 records the outdated classpath, line 1083 the
analyzed file, and line 1108 analysis completion.

Implementation is paused pending guidance on refreshing the IDE Gradle/Java
model and obtaining fresh matching JDTLS/Sonar evidence. No Java sources,
OpenAPI contract, database migrations, IDE settings, or task checkboxes were
changed. Task 1.1 remains unchecked; native image availability is not native
build/test acceptance. All implementation and final verification tasks remain
pending.

### IDE model refresh: blocker resolved

Following the user's instruction to investigate and refresh the IDE model,
the workspace setting `.vscode/settings.json` was changed from
`java.configuration.updateBuildConfiguration: interactive` to `automatic`.
This is an ignored local IDE setting. The existing null-analysis setting was
not changed. No Sonar rule, exclusion, suppression, or dependency version was
changed. A temporary blank-line edit to `gradle.properties` notified build
watchers and was reverted; `git diff -- gradle.properties` is empty.

A fresh IDE extension-host session was observed at 14:20:40. The Gradle task
service reported Gradle 9.7.0 and successful configuration; BSP initialization
returned six build targets after 153,684ms. JDTLS updated classpaths, imported
the project, completed workspace initialization at 14:23:54, and reported
build jobs finished at 14:25:12. The delay initially caused Sonar Java-config
timeouts and an incomplete-model analysis; those results are excluded.

After import/build completion, fresh Sonar analysis at 14:25:13–14:25:16
reported Java 25, preview disabled, Quarkus **3.40.1** dependencies, 571 active
Java rules, full analysis of both baseline files, **0 issues**, and **0
Security Hotspots**. A separate follow-up analysis of PartyQueryTimeouts at
14:25:16 likewise reported zero findings. The qualifying runs contain no
missing-library or unresolved-type warnings. The existing `No workDir in
SonarLint` runtime diagnostic remains separate from source findings. These
are standalone IDE results, not a remote server Quality Gate.

| Unchanged baseline Java file | Saved Git blob | Fresh JDTLS evidence |
| --- | --- | --- |
| `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/HibernateReactivePartyIdentifierReadAdapter.java` | `d2c78c35d596d05c4f172be4e0d382c02c2fce46` | 0 problems at 14:23:59 |
| `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyQueryTimeouts.java` | `fd97b3e9a7346491aaa00c09f83f1ef149abcf7f` | 0 problems at 14:25:14 |

Evidence locations:

- Sonar log above, lines 1192–1254: resolved 3.40.1 classpath, Java 25,
  analyzed files, full analysis completion, and zero findings.
- `/home/alex/.config/Antigravity IDE/User/workspaceStorage/fc73fbd3bb7d62718087c857851a4deb/redhat.java/jdt_ws/.metadata/.log`,
  lines 377–432: import, initialization, build completion, and baseline-file
  diagnostics.
- `/home/alex/.config/Antigravity IDE/logs/20261010T095302/window1/exthost/output_logging_20261010T142040/1-Gradle for Java.log`,
  lines 3–8: Java 25 home, Gradle 9.7.0, and successful configuration.
- The corresponding `3-Build Server for Gradle (Log).log`, lines 1–8:
  initialization timing and batched classpath/model requests.

The earlier IDE-model pause is resolved. Baseline-file diagnostics are not
implementation evidence for future Java changes. Search implementation,
per-task verification, and final JVM/native checks remain pending; no task
checkbox was changed during this IDE refresh.

## Resumed baseline inspection: incomplete fresh test run

The subsequent apply session reconfirmed Java 25.0.4.1, Gradle 9.7.0,
Quarkus 3.40.1, Hibernate Reactive 3.4.2.Final, and Mutiny 3.3.0 through
actual version/dependency commands. Rootless Podman 5.4.2 reports an existing
socket at `/run/user/1000/podman/podman.sock`. The configured JDK 25 native
builder successfully reports Mandrel-25.0.4.1-Final. `javap` on the resolved
Hibernate Reactive and Mutiny JARs confirms scoped session/transaction,
read-only, executor dispatch, cancellation, and emitter termination APIs.
Their runtime ownership and cancellation behavior still requires tests.

The initial worktree contained the existing untracked verification document.
During the session, it became staged externally; its earlier contents were
preserved. No application Java file was edited.

Fresh baseline test attempts used the documented Podman socket and an ephemeral
HTTP test port:

```shell
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --rerun-tasks --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
```

The first invocation reached `:test` after compiling production and test
sources, then was terminated by the command runner's 120-second limit.
The second reached `:test`, emitted existing HTTP/database test diagnostics,
and was terminated by the command runner's 600-second limit. Neither returned
a build result. No completed test XML report was found afterward; Gradle
reported both current daemons idle. These are incomplete runs, not passing
tests and not proof of a particular failing assertion or a deadlock.

The second invocation's captured output is retained at
`/home/alex/.local/share/opencode/tool-output/tool_127514a220014C9wuyiXs5wc3w`.
Implementation is paused for guidance on investigating the long-running
baseline or allowing a longer test budget. Task 1.1 remains unchecked and
overall progress remains 0/44. No new JDTLS, Sonar, packaged build, or native
build/test acceptance is claimed.

## Task 1.1: Current baseline and timing investigation

The baseline and resolved API inspection above satisfy task 1.1. The test
suite's duration was investigated before resuming implementation. The Gradle
daemon log (`/home/alex/.gradle/daemon/9.7.0/daemon-270527.out.log`, lines
5683–5780) confirms that the previous ten-minute attempt ended because the
client disconnected, cancelling `:test` and aborting its executor with code
143. Its `BUILD FAILED` is a cancellation result, not a failed assertion.

A monitored baseline run uses a temporary external Gradle init script to
record each test's start, end, result, and duration. No project build or test
configuration was changed:

```shell
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain --info --init-script /tmp/opencode/search-baseline-timing.gradle
```

The log `/tmp/opencode/search-baseline-timing.log` records an initial Quarkus
startup of **204.354 seconds**, before the first test started at
2026-10-10T19:49:56Z. Thread snapshots show class loading/indexing and Quarkus
augmentation during that initial preparation, not a stalled test assertion.
Subsequent profile restarts measured 5.340, 1.959, and 4.474 seconds; observed
PostgreSQL container startups measured approximately 6.5–6.7 seconds. At this
checkpoint the suite is progressing and observed finished tests pass. This
is partial timing evidence; the final suite result is still pending.

Resolved versions: Java 25.0.4.1; Gradle 9.7.0; Quarkus 3.40.1;
Hibernate Reactive 3.4.2.Final; Mutiny 3.3.0; Mandrel 25.0.4.1-Final.
Scoped session/read-only/transaction APIs and Mutiny executor/cancellation
APIs are present in the resolved JARs. Runtime context, acquisition, cleanup,
and cancellation acceptance belongs to subsequent implementation tests.

## Task 1.2: Evidence inventory initialized

[java-file-inventory.md](java-file-inventory.md) tracks production, unit,
shared-contract, and integration Java revisions with explicit saved Git blob,
test, JDTLS, and Sonar evidence fields. It starts empty because no Java source
has been changed. Outstanding final-source JVM/native/IDE acceptance is
explicit; baseline diagnostics do not count as future-file acceptance.

## Task 1.3: Strict request contract completed

The source OpenAPI now describes exact-code resolution, normalizer-only
historical discovery, literal prefix semantics, omitted/null/empty filters,
raw UTF-16 limits, body-only strict input, complete exact results, prefix
limit/cursor scope, live read-only snapshots, ordering, and safe metadata.
Both request objects are closed; value/prefix are write-only and each raw
bounded selector declares `x-string-length-unit: utf16-code-units`.

Python/PyYAML validation successfully parsed the source and checked exact
property sets, object closure, four length-unit extensions, and sensitive
write-only fields. A recursive reference check resolved all **691** local
OpenAPI references. This is source-contract verification only; schema fixture
and real HTTP/native verification remain separate pending tasks.

## Task 1.4: Safe response and failure contract completed

The three search output schemas are closed. Success status is constrained to
200; both nullable cursor properties remain required, and all counts are
nonnull integers in 0..2147483647. Both operations declare accepted-only
Process-Id echo for success and each documented failure. Search-specific
400/401/404/405/415/422/500/503 responses distinguish unknown scheme,
unsupported normalization, framework failures, sanitized corruption/overflow,
and classified finite dependency/CPU exhaustion.

Synthetic examples cover exact historical and empty outcomes, prefix initial,
last, empty and empty-live-continuation results, request inputs and stable
failure codes. Cursor placeholders are illustrative; the continuation request
instructs consumers to substitute a cursor returned for that effective scope.

`python3 /tmp/opencode/verify-search-openapi.py` passed: local references,
closed safe fields, supported count constraints, process headers, and **35**
request/response examples checked against their declared types/properties,
requiredness, enums and length/count bounds. Reusable Java assertion fixtures
and packaged/served contract verification remain pending.

## Completed timing investigation and task 1.5 checkpoint

The monitored run completed in **17m 1s**, executing **1,768 tests**, with one
failure and zero skipped tests. Its test execution duration was 1,011,678ms.
The sole failure was `OpenApiContractTest.packagesTheApprovedNationalityContractVerbatim`:
the source OpenAPI changed during the run while its packaged copy still came
from the run's initial resources. No baseline Java implementation was changed.
The source/package drift was resolved by regenerating resources before the
focused follow-up; no test was skipped or weakened.

Initial preparation/augmentation cost was 204.354s; subsequent profiles and
real PostgreSQL startups add overhead. Longer individual successful tests
included the identifier resiliency test at 25,334ms and an isolated migration
test at 24,063ms. Per-test timestamps continued advancing throughout the run;
the evidence does not support an indefinite test deadlock. Running the suite
under a ten-minute command limit was insufficient in this environment.

Task 1.5 now has a test-only reusable search assertion class and four
positive/negative fixture tests covering published examples, unresolved
references, strict duplicate/trailing tokens, UTF-16 supplementary boundaries,
invalid numeric tokens, leaked fields, wrong statuses, missing/null metadata
and count overflow. Existing Swagger/Jackson schema validation is reused.

```shell
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*IdentifierSearchContractAssertionsTest' --tests '*OpenApiContractTest' --console=plain
```

After correcting Swagger raw-model inference errors using typed `Schema<?>`
locals, the focused command returned **BUILD SUCCESSFUL in 3m 18s**. The four
new fixture tests have zero failures/errors/skips. OpenAPI packaging equality
and included architecture checks also pass. A full `./gradlew test` rerun for
the saved Java revisions was launched with per-test timing and captured in
`/tmp/opencode/search-foundation-tests.log`; its result remains pending at
this checkpoint.

JDTLS reports zero problems for both new Java files. Actual current-model
Sonar analysis reports zero issues/hotspots for the shared helper, but **one
issue and zero hotspots** for its unit test. Analysis uses Java 25, resolved
Quarkus 3.40.1 dependencies, and 571 active Java rules. The logs provide only
the issue count, not its rule/message/location. Per the operations guide,
obtain the IDE Problems entry to remediate it without guessing or suppression.
Task 1.5 remains unchecked until that finding and required verification are
resolved. No production endpoint implementation is claimed.

Evidence locations:

- `/tmp/opencode/search-baseline-timing.log`, lines 39624–39650: completed
  suite counts, failure and total duration.
- `/home/alex/.config/Antigravity IDE/logs/20261010T095302/window1/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log`,
  lines 1418–1448 and 1449–1479: current models and final-file analysis results.
- `/home/alex/.config/Antigravity IDE/User/workspaceStorage/fc73fbd3bb7d62718087c857851a4deb/redhat.java/jdt_ws/.metadata/.log`,
  lines 458–480: final-file zero-problem JDTLS diagnostics.

### Full-run result and incremental verification steering

The already-running final-foundation suite ended in **17m 14s**. The listener
records **1,772 executed tests, zero failed assertions, zero skipped tests**.
However, Gradle failed while finalizing results with:

```text
java.nio.file.NoSuchFileException: build/test-results/test/binary/in-progress-results-generic.bin
```

Source: `/tmp/opencode/search-foundation-tests.log`, lines 10218–10242.
Therefore the full command is **not accepted as a successful build** despite
passing test outcomes. The cause of the missing report file has not been
established; do not attribute it to concurrent execution or a particular
Gradle defect without evidence. The previous focused verification remains
successful. No new full-suite retry was started.

The user subsequently directed task-by-task verification to use only tests
for the changed files/affected behavior. Apply that to iterative checks;
reserve required full-suite/build/native acceptance for the final quality
tasks rather than repeating it per implementation task.

Implementation is paused at task 1.5 for the unresolved Sonar test finding
and the report-finalization failure. Obtain the Sonar Problems rule/message/
line, and investigate the Gradle reporting failure with focused checks before
continuing. Progress remains 4/44; no endpoint implementation is complete.

### Task 1.5 accepted after user remediation

The user corrected the Sonar fixture finding by extracting the serialized
payload before the exception assertion. Its saved blob is now
`4783579b1d05d46cdd0e5705c829d8270c49726a`. Fresh Sonar analysis completed
at 15:25:19 with zero issues/hotspots (log lines 3495–3525). The focused
assertion/OpenAPI command was rerun successfully in 3m 17s on this revision;
this also verifies that focused Gradle result finalization works.

Task 1.5 behavior and focused verification are complete. The earlier full-run
report-finalization failure remains recorded for final acceptance; it does
not invalidate the successful focused run. Future task iteration uses only
affected tests. Per the user's direction, Sonar findings are recorded for
parallel remediation rather than pausing implementation; final Sonar
acceptance still requires the final saved revisions to be verified.

## Task 2.1: Normalizer-only historical selector

`IdentifierRuleCatalog.resolveNormalizer` resolves only the existing supported
normalizer. The pure selector/policy provides exact and literal-prefix
comparison, retained punctuation/accents/interior whitespace, root-locale
normalization expansion, version identity and redacted diagnostics. Unknown
validator keys, all scheme states, registration bounds and checksum-incomplete
fragments do not gate search. Registration `evaluate` behavior is unchanged.

Focused selector and both existing rule-catalog suites passed with
`./gradlew test --tests '*IdentifierSearchSelectorPolicyTest' --tests '*IdentifierRuleCatalogTest' --tests '*IdentifierRuleCatalogConfigurationTest' --console=plain`
(Podman socket and ephemeral port environment), BUILD SUCCESSFUL in 3m 34s.
JDTLS reported zero problems for all four affected files (log lines 582–600).
Final-source full-suite/build/native acceptance remains in section 8.

## Task 2.2: Immutable queries and canonical scope

Exact/prefix query records redact all selector/cursor diagnostics. Effective
filters copy both enum sets, expand omitted dimensions, retain explicit empty
sets, and canonicalize duplicates/order. Prefix defaults to 50 only through
the omitted-limit constructor and validates 1..200 and nonblank bounded
nonnull cursors. Scope contains tenant, exact scheme identity/code, selected
normalization semantics, filters and limit; it excludes user/process and keys.

Focused `IdentifierSearchQueriesTest` passed, BUILD SUCCESSFUL in 3m 56s.
JDTLS reported zero problems for all five affected files (log lines 615–675).
No full-suite rerun was performed during this task.

## Task 2.3: Safe immutable results and precise keysets

Safe hit/slice/page models retain only stored Party summary/mask fields plus
internal precise navigation positions. Existing identifier position/boundary
types are reused. Unsigned UUID comparison matches database tuple order,
including nanosecond ties. Exact slices require complete counts without a
200-row cap. Prefix metadata uses long ceiling division without overflowing
addition, retaining totals on empty continuation; API capacity conversion
remains a later delivery task.

Focused `IdentifierSearchResultsTest` passed in 3m 22s, then again in 3m 19s
after the user's parallel Sonar-only test refactoring. JDTLS reports zero
problems for all six new files (log lines 690–816). No full suite was run.

## Task 2.4: Search read and cursor ports

The read port composes scheme resolution and safe exact/prefix slices through
one operation-scoped context, explicitly owned per subscription by its adapter.
Contracts expose Domain/Application models and Mutiny only; cursor operations
use the existing precise boundary type. A test-only read stub compiles for
subsequent use-case tests. Actual session ownership/cleanup is Infrastructure
verification, not a claim established by the fixture.

Focused `CleanArchitectureTest` passed, BUILD SUCCESSFUL in 3m 29s. All four
affected files have zero JDTLS problems (log lines 897–1029). No HTTP, CDI,
session, entity, cryptographic key/hash/ciphertext leaks through these ports.

## Task 3.1: Compatible lossless lookup derivation

Infrastructure exposes existing tenant-HMAC lookup as an optional hash:
unrepresentable UTF-16 selectors return absence, not a replacement-character
hash or dependency failure. A lossless scheme encoding check is also exposed.
The original HMAC derivation/byte format is unchanged. Mutable encoding/key
buffers now clean up even when derivation fails; the intermediate UTF-8
encoding buffer is cleared after copying.

Focused `JcaIdentifierProtectionAdapterTest` passed, BUILD SUCCESSFUL in
3m 56s, covering new lookup compatibility/tenant/unpaired-surrogate cases
and existing registration/encryption/AAD/mask/rotation behavior. Both
modified files have zero JDTLS problems (log lines 1179–1185).

## Task 3.2: Authenticated identity-only internal matching

`IdentifierProtectionIdentity` contains actual owner IDs and stored protection
versions with redacted diagnostics. Internal prefix matching reuses the
existing AES-GCM implementation/AAD format and retained encryption keys;
it returns only a boolean and never a plaintext Application result. The
registration decrypt helper delegates to that same implementation without
altering persisted bytes. Required unusable versions/keys/AAD/tags fail with
fixed diagnostics and no sensitive cause. Plaintext byte arrays are cleared
in finally along with detached envelope/AAD buffers.

Focused cryptographic regression suite passed, BUILD SUCCESSFUL in 3m 42s.
New tests exercise retained ciphertext, literal matching, every identity/AAD
dimension, unsupported stored normalization, missing retained key, corruption
and redaction. JDTLS reports zero problems for all three affected files.

## Task 3.3: Keyed resource-specific search cursors

The search cursor adapter uses distinct token/scope HMAC domains and the
existing signing ring. Sensitive scope is keyed with the token's verification
key, including retained old keys; no unkeyed prefix digest or selector text
is published. Exact UTF-16 scope units stream into the MAC without a retained
plaintext byte buffer. Bounded canonical parsing validates direction, full
timestamp/UUID precision, scope and limit with constant-time MAC/digest checks.

Focused `HmacIdentifierSearchCursorAdapterTest` passed, BUILD SUCCESSFUL in
3m 53s. It covers both directions, equivalent status sets/normalized inputs,
every scope dimension, resource isolation, malformed/tampered tokens,
authenticated impossible fields, key rotation and the 64-character key-ID
token bound. Both files have zero JDTLS problems (log lines 1296–1326).

## Task 4.1: Additive Flyway ordering index

V9 was unused and now creates only the tenant/scheme/created-at/identifier
ordering index. Clean-install and V8-upgrade tests use real PostgreSQL and
Flyway, retaining earlier migration checksums and actually AES-protected
historical Party/identifier/detail rows byte-for-byte. The existing migration
catalog regression expectation now includes V9; V1–V8 were not edited.

Focused search migration verification passed in 4m 3s; search migration plus
existing `MigrationRegressionTest` passed in 4m 29s after updating its catalog
expectation. JDTLS reports zero problems for the new migration test. The
existing regression file diagnostics are checked separately after its edit.

## Task 4.2: In-progress read owner; focused verification failure

The resolved Hibernate Reactive implementation was inspected, confirming
`openSession` obtains a pooled connection before session construction. The
new `IdentifierSearchReadSession` attempts per-subscription acquisition
ownership, late-allocation closure, remaining transaction deadlines,
read-only repeatable-read configuration, origin-context emission, guarded
sequential reads, and exact scheme resolution. The public read-port adapter
and remaining runtime cases are not complete; task 4.2 is unchecked.

Focused command:

```shell
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*IdentifierSearchReadSessionTest' --console=plain
```

Result: **BUILD FAILED in 4m 12s**. Of its two database cases, exhausted
single-connection acquisition, timeout, late allocation closure and subsequent
reuse passed. The normal snapshot/escaped-context case failed with a translated
Mutiny timeout. The test-only profile configures a 300ms traversal and 200ms
statement deadline for both cases; the normal-read test took 1.314 seconds
including failure/cleanup. The current evidence proves timeout, not a specific
startup/SQL/pool phase as its cause. Investigate before changing budgets or
claiming normal snapshot acceptance.

Report: `build/test-results/test/TEST-com.alexastudillo.partyregistry.infrastructure.persistence.IdentifierSearchReadSessionTest.xml`.
JDTLS reports zero problems for the test and **one problem** for the read
owner (log lines 1548–1554); the log exposes only the count. This is JDTLS,
not Sonar. Gradle compilation succeeded, so the discrepancy needs its actual
diagnostic before acceptance. Sonar findings continue to be handled in
parallel per user instruction, without weakening final acceptance.

Implementation is paused for guidance on this focused runtime failure and
the JDTLS diagnostic. Progress is 13/44; this is not endpoint completion.

### Read-owner corrections: runtime and JDTLS blockers resolved

The screenshot identifies a real JDTLS type-inference discrepancy:
the failed-acquisition branch inferred `Uni<Object>` inside the conditional
flatMap expression, while javac accepted the assignment. Explicit
`Uni.createFrom().<Mutiny.Session>failure(cancelled())` now gives both
branches the same session type without casts or warning suppression.
The constructor's nanos conversion is retained as a used, validated
`budgetNanos` field rather than an ignored pure return value.

Normal snapshot tests now use the existing finite configured budgets.
Only deliberate pool/statement failure cases construct a read owner with
200ms statement and 300ms operation budgets. Production defaults were not
changed. An additional real PostgreSQL test verifies a statement/traversal
failure is followed by connection reuse after transaction cleanup.

The same focused command passed: **BUILD SUCCESSFUL in 4m 1s**, with all
three scoped-read tests passing and no skipped cases. Snapshot read was
45ms, deliberate acquisition timeout/reuse was 322ms, and statement failure
plus reuse was 1.686s in that run. The SQL timeout warning is expected from
the deliberately slow statement, not a failing test.

JDTLS now reports **zero problems for both final saved files**, including
the previously rejected acquisition expression (log lines 1590–1629).
The runtime/type blockers are resolved autonomously. Task 4.2 remains
unchecked because its complete read-port adapter and remaining lifecycle/
snapshot verification are still implementation work, not because of these
fixed failures. Progress remains 13/44.
