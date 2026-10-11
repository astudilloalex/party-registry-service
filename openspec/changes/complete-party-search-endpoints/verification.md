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

## Reactive read implementation: tasks 4.2–4.7

The read-port adapter now uses the guarded operation owner for both exact and
prefix queries. Scheme lookup and scalar queries share one read-only repeatable
read session; per-subscription, closed-context, concurrent-query, cancellation,
pool-exhaustion/late-allocation reuse and statement/operation deadline tests
pass. A coordinated writer changes a scheme during a callback; the callback
retains its original view and the next request sees the changed value.

Qualified bound joins include both identifier and Party tenant ownership,
scheme and effective states. Exact count precedes complete safe projections,
with checked count capacity and no decryption/deduplication/page cap. A real
201-hit fixture shares one Party and intentionally corrupts ciphertext after
protection, proving exact results do not require decryption. Timestamp ties
retain stored microsecond precision and unsigned UUID order.

Prefix projections contain at most 256 detached candidates and advance strict
timestamp/UUID keysets using the last candidate, not the last match. A 520-row
protected fixture includes an initial nonmatching batch; totals/navigation
remain complete. A tied-timestamp multi-batch fixture verifies precision and
UUID keysets. Status filtering excludes corrupted candidates before decrypt;
required corrupted candidates fail with sanitized internal diagnostics, and
a subsequent read reuses the session successfully.

CPU dispatch owns positive fixed workers and a bounded rejecting queue, never
caller-thread fallback. Tests verify actual worker thread identity, emission
on the identical originating context, typed saturation, queued cancellation,
discarded late result and subsequent admission. Application/session/request
objects are not accessed by the detached matching worker. Defaults are two
workers and 32 queued batches.

The pure accumulator counts with checked long increments, retaining only
the requested page plus extrema. Its 10,000-hit fixture, forward/backward
nearest-page cases and disappeared/empty-boundary cases pass. Prefix traversal
repeats terminated batch operations and collects only the last Boolean step,
avoiding recursively retained previous candidate pipelines.

Focused combined read/session/CPU/accumulator command: BUILD SUCCESSFUL in
4m 20s. Follow-up adapter/CPU tests after precision, corruption and filtering
cases: BUILD SUCCESSFUL in 3m 51s. JDTLS reports zero problems for all new
SQL, adapter, accumulator, dispatcher and corresponding test files. The first
CPU saturation fixture incorrectly resumed its barrier on the worker; that
test-only context handoff was corrected before the successful runs. No full
suite was executed during this work; HTTP/native/observability/performance
acceptance remains in the later tracked tasks.

## Application search orchestration: tasks 5.1–5.2

Exact and prefix use cases resolve the exact scheme and supported normalizer
inside the composed read before selecting historical matches. Exact results
remain complete with null directions and 0/1 page metadata. Prefix authenticates
scope after normalization, including empty filters, and signs only available
navigation after the read has closed. No registration/admission, HTTP, CDI,
persistence or cryptographic implementation dependencies enter Application.

Focused use-case tests passed, BUILD SUCCESSFUL in 3m 26s, covering 201 exact
hits, empty results, unknown/unsupported scheme precedence, canonical scope,
changed empty filters, invalid cursors and exact page metadata. An initial
test failure exposed that the registration-specific failure translator does
not classify unsupported search normalization as the declared 422 failure;
translation is now limited to selector evaluation, preserving unexpected
stored-data failures. JDTLS reports zero problems for both use cases and the
test file. No full-suite iteration was performed.

## Task 5.3: Strict search wire binding

Separate exact/prefix records and token-aware deserializers accept only declared
properties, reject duplicates/trailing content/coercion/null state arrays and
invalid enum tokens, and enforce raw UTF-16 bounds. Only integer JSON tokens
in 1..200 are accepted for supplied limits; overflow is compared before
narrowing. Missing limit defaults to 50; null cursor is an initial page while
blank cursor fails. Request diagnostics are redacted. Actual DTO/deserializer
classes carry native registrations.

The search-specific reader interceptor discards parser diagnostics/causes and
delegates envelope creation to the published global module; it does not overlap
global mapping or success wrapping. Focused binding tests passed, BUILD
SUCCESSFUL in 3m 38s; all seven affected Java files have zero JDTLS problems.
Real HTTP missing/null body mapping and native execution remain separate tasks.

## Task 5.4: Safe DTOs and checked shared metadata

The response records expose exactly the five Party summary and five nested
identifier fields. The API mapper preserves stored labels/masks and maps IDs
and enum text without exposing positions or Application records. Shared
pagination counts use checked conversions. A CDI ResponseManager serialization
test validates both real shared envelopes against the approved schemas,
including explicit null directions and supported integer counts; overflow
fails rather than wrapping or dropping metadata.

Focused mapper tests passed, BUILD SUCCESSFUL in 4m 10s. JDTLS reports zero
problems for both DTOs, mapper and test after model reconciliation. Native
registrations are present; actual native execution remains pending.

## Task 5.5: Actual explicit HTTP delivery

Both published POST paths now delegate to composed Application use cases and
return `Uni<RestResponse<ApiResponse<List<IdentifierSearchResponse>>>>`.
Resources obtain accepted context, validate body-only inputs, map DTOs before
CDI ResponseManager pagination wrapping, and use the existing API translator
and shared global handler. No local global mapper or success filter was added.
The focused Infrastructure producer owns composition; framework-disabled
test profiles exclude the DB-dependent resources consistently with existing
root resources.

Actual HTTP tests validate both empty success envelopes, process echo, null
body, invalid/unknown fields, unknown scheme and forbidden query inputs.
The resource/architecture command passed, BUILD SUCCESSFUL in 6m 42s;
JDTLS reports zero problems for resource, producer and test. Broader HTTP,
runtime/native, observability and performance acceptance remains in section 6.

## Task 6.1: Bounded operation and numeric work observations

Both search routes now participate in the existing HTTP completion observer
with fixed `party.search-by-identifier` and `party.search-by-identifier-prefix`
labels. Application observes per-subscription accepted attribution and bounded
terminal outcomes through the existing port. RequestContextFilter continues
to own completion logging, process echo and MDC cleanup; its code and the
configured logging format were preserved.

The read adapter publishes per-subscription numeric SQL, batch, inspected
candidate, match, maximum batch and maximum retained page observations on
success, failure, timeout and cancellation, including partial failed scans.
Detached workers receive only the numeric observation handle, not request
metadata or a session. CPU gauges expose worker/queue capacity and active/queued
batches; saturation increments a rejection counter. Dimensions contain only
closed operation/measurement/outcome labels; no request identities or selectors
are accepted. Late work cannot create an additional terminal observation.

Focused verification:

```shell
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*IdentifierSearchWorkObserverTest' --tests '*MicrometerOpenTelemetryOperationObserverTest' --tests '*OperationObservationTest' --tests '*IdentifierSearchCpuDispatcherTest' --tests '*IdentifierSearchReadAdapterTest' --tests '*IdentifierSearchResourceContractTest' --tests '*IdentifierSearchUseCasesTest' --console=plain
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*IdentifierSearchCpuDispatcherTest' --tests '*PartyHttpObservabilityTest' --console=plain
```

Both commands passed (3m 56s and 3m 27s). Initial compile failure from using
SimpleMeterRegistry as AutoCloseable was corrected. An initial architecture
failure correctly rejected injecting an observation port into the REST
resource; observation is now Application-owned and the focused architecture
rule passes. No compiler warnings were introduced. Runtime OpenTelemetry usage
and duplicate PostgreSQL gauge notices remain distinct from compiler results.

Fresh Standard JDTLS reports zero problems for all 16 affected files in
`/home/alex/.config/Antigravity IDE/User/workspaceStorage/fc73fbd3bb7d62718087c857851a4deb/redhat.java/jdt_ws/.metadata/.log`,
lines 3093–3445. The session's JUnit discovery plug-in NPE at 18:56:10 is
recorded separately; subsequent source diagnostics and Gradle tests completed.
Actual Sonar IDE analyses in the current 18:47:45 extension session report zero
issues/hotspots for completed runs; final revision/model reconciliation remains
task 8.4. Focused iteration and complete final validation were explicitly
confirmed by the user. Full JVM/native acceptance remains pending.

## Task 6.2: Real HTTP context and strict-input contracts

IdentifierSearchInputContractTest exercises both routes with shared safe-schema
assertions. Seven test methods cover missing/duplicated/noncanonical headers,
context precedence over invalid bodies, case-insensitive names, accepted-only
echo, blank/oversized/C1 user rejection, the 128-character user boundary,
management exemption, strict absent/null/malformed/duplicate/unknown/wrong-type
body handling, forbidden query and exact pagination inputs, both invalid/empty
filter dimensions, raw UTF-16 and supplementary boundaries, invalid integer and
cursor tokens, valid limit boundaries, ignored mutation headers, and overlapping
successful/failing requests with captured completion MDC and later reuse.

The first focused run exposed a test-client encoding substitution: RestAssured
sent `unsafe?user` rather than the intended C1 character. Configuring the HTTP
header element charset to ISO-8859-1 sends the original character and proves
the actual `400 user-id-unsafe` response on both routes. No service validation
was relaxed and no failing case was removed.

`DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --tests '*IdentifierSearchInputContractTest' --console=plain`
passed in 3m 40s: seven search test methods, zero failed/skipped; automatically
included architecture checks also passed. Final saved revision has zero JDTLS
problems at 19:18:13 (current workspace log lines 3591–3598). Final full
acceptance remains pending.

## Task 6.4: Protected prefix HTTP navigation

Three real HTTP/database tests prove complete 520-candidate traversal through
an initial 260 nonmatching candidates, default and 1/200 limit boundaries,
equivalent full filters/normalized selector/user/process scopes, changed scope
and tenant rejection, forward/backward navigation, deleted boundaries, empty
live continuation with remaining totals, and vanished datasets. A five-hit
national-ID fixture shares one Party/value and a microsecond creation timestamp;
unsigned UUID order traverses 2/2/1 without collapse and a short `17` fragment
needs no complete checksum. Literal percent/underscore/accent matching, uppercase
expansion of 64 raw sharp-s characters, every state/type dimension, conjunctive
filters, stored history and foreign concealment are exercised with real AES/HMAC.

Focused `IdentifierSearchPrefixContractTest` passed in 3m 19s, then in 3m 9s
after adding national-ID duplicate/timestamp cases. JDTLS reports zero problems
for the final revision (19:33:12, log lines 3678–3685). No native/full-suite
acceptance is inferred from these focused JVM tests.

## Task 6.3: Protected exact HTTP history

The new test-thread database fixture uses DML against Flyway-managed tables
and the production AES-GCM/HMAC/AAD protection implementation. No manual DDL,
plaintext storage, mock protection or request-path JDBC was introduced.
Exact HTTP tests cover 201 shared-Party historical matches, microsecond timestamp
ties and unsigned UUID ordering, stored masks/labels, foreign isolation, complete
metadata, complete equality versus prefix/substring/mask/accent/spacing changes,
all 40 Party-type/Party-state/identifier-state combinations under each of the
four scheme states, duplicate filter memberships, empty filters, and conjunctive
state rejection. Synthetic evidence includes past expiration dates and current
admission-incompatible schemes/values to prove discovery is normalizer-only.

Focused `IdentifierSearchHistoricalContractTest` passed in 3m 38s. The fixture's
initial JDBC short assignment compilation failure was corrected with setInt;
no narrowing cast or suppression was used. Fresh JDTLS reports zero problems
for fixture and test at 19:24:00 and 19:24:23 (workspace log lines 3609–3631).

## Task 6.5: Failure envelopes and safe internal diagnostics

Real selector/framework tests cover both routes' unknown and unsupported
normalizer 422 codes, scheme-before-cursor precedence, invalid cursor 400,
404/405/415, accepted process echo, required corrupt protected prefix failure,
pre-decrypt status exclusion and subsequent healthy reuse. Exact lookup remains
available when ciphertext is corrupt because ciphertext is not an exact-query
input. A profile-selected test-only read alternative injects classified 503,
asynchronous/synchronous unexpected failures, corruption and count overflow.
Prefix overflow traverses the actual shared metadata conversion; exact overflow
is injected as the count-capacity failure without allocating billions of rows.

The first fault test demonstrated a real confidentiality defect: the public
500 envelope was safe, but the shared unexpected-error logger printed original
selector-bearing messages/causes. Search-specific API translation now preserves
known response exceptions and sends a fixed unexpected diagnostic with safe
original stack locations, no sensitive message/cause/suppressed chain. Global
envelope construction remains in the published shared module.

Focused fault and real failure tests passed together in 3m 54s after the fix;
captured attributed logger output proves no injected sensitive diagnostic remains.
Fresh JDTLS is zero for resource, alternative, both test files and nested types
(current log lines 3720–3847). Authentication 401 scenarios are conditional:
this service/test profile has no enabled authentication mechanism, and context
headers are not converted into credentials. No authentication profile was added.
Runtime deadline/pool/cancellation acceptance remains task 6.7.

## Task 6.6: Coordinated snapshots and complete read-only evidence

Test-only delegating Hibernate proxies insert a completed real writer transaction
after the actual exact count and before full projection, or after the first
256-candidate prefix query and before matching/next batches. They do not fake
query results, change isolation or use timing sleeps. Writers change all target
identifier states/versions, Party states/labels/versions and scheme name/version.
The current read retains original scheme/data/count/navigation (201 exact,
520 prefix) and the following read sees the changed scheme and empty effective
filtered collection. Every Hibernate call remains sequential on its owning
context through the actual guarded read adapter.

A third test fingerprints every column in all 11 business tables, including
protected values, subtypes, nationalities, primary/verification/audit/version
fields and replay/outbox/action records. Successful exact/prefix HTTP, invalid
HTTP, a cancelled operation after real protected evaluation, subsequent healthy
HTTP and corrupt-ciphertext failure leave their respective stored baseline
unchanged. Corruption is an explicit fixture write before its failure baseline.
Fingerprints avoid dumping protected rows into assertion diagnostics.

Focused snapshot tests passed in 3m 8s, then in 3m 14s with complete read-only
checks. Fresh JDTLS reports zero problems for fixture and final test, including
proxy/nested types (19:46:22 / 19:54:39, log lines 3852–3874). No request-path
blocking API or test-only hook was added to production code.

## Task 6.7: Finite runtime resource ownership

Actual HTTP tests hold the only reactive pool connection while both routes
exceed their two-second test operation budget, assert status-consistent 503
envelopes and accepted process echo, release ownership, and verify that late
allocations close and fresh exact/prefix requests succeed. Another HTTP test
occupies the only CPU worker and queue slot; prefix rejects with 503, exact
remains available, cancellation removes owned queued/running work, late results
are discarded, and a fresh prefix request succeeds. Production budgets/capacity
are unchanged and blocked-thread diagnostics remain enabled.

The dispatcher suite adds explicit running-work cancellation/late completion
and healthy admission on the identical originating context. Existing actual
single-pool scoped-read tests additionally verify statement timeout cleanup,
closed-context rejection, sequential ownership, acquisition cancellation and
repeated subscription independence. The focused combined runtime/CPU/read-owner
command passed in 3m 42s. Its SQLState 57014 warning is deliberate statement
deadline verification, not a failed assertion or disabled diagnostic.

Fresh JDTLS reports zero problems for runtime test and updated dispatcher test
(20:01:03 / 20:02:35, workspace log lines 3882–3904). Full final-source JVM/native
and IDE/Sonar acceptance remains pending.

## Task 6.8: Actual packaged JVM search contract

PackagedIdentifierSearchContractIT runs against the built executable process,
sharing only contract assertions rather than unit alternatives. Its resource
uses the existing isolated PostgreSQL/Flyway/geographic harness and actual
production JCA to seed version-one history before service launch; the service
uses version-two encryption/signing settings with retained version-one keys.
Tests prove exact historical discovery, both Party types, 2/2/1 bidirectional
prefix pages, old-key cursor acceptance/new-key cursor issuance, native-capable
current-key registration/search, strict codecs, safe empty/known/corrupt failure
responses, null cursor writers, source/packaged/served schema equivalence, private
field negative assertions and absence of unit-only fixtures on the classpath.

The first run showed that this existing harness actually launches profile prod,
despite the integration resources' test-profile setting. Historical data was
protected with synthetic test keys but runtime inherited externally supplied
production-profile material. The isolated new test resource now explicitly
supplies its full synthetic retained key ring/index key; no production key
defaults, environment values or service configuration were changed.

`./gradlew quarkusIntTest --tests '*PackagedIdentifierSearchContractIT' --console=plain`
(documented Podman/ephemeral-port environment) passed in 38s: four methods,
zero failed/skipped, actual packaged process/HTTP. The existing Dev Services
build-time/runtime configuration notice is recorded independently. JDTLS reports
zero problems for both new integration files (20:08:33 / 20:08:55, log lines
3912–3934). This is packaged JVM evidence; actual native execution remains 8.6.

## Task 6.9: Real protected workload and plan measurements

IdentifierSearchPerformanceTest creates 512 and 4,096 qualified protected rows
and measures actual HTTP, per-subscription SQL/decrypt/batch/match counts, maximum
batch/page retention, CPU workers/capacity/activity/queue and reactive-pool queue.
Sanitized EXPLAIN ANALYZE/BUFFERS evidence retains only operator/index choices,
actual rows, block counts and timings: no predicates, tenant IDs, selectors,
lookup hashes or ciphertext. The generated report is
`build/reports/identifier-search/performance.json`.

Final focused checkpoint: 12 available processors, 2 GiB test heap, PostgreSQL
18.4, Java 25 / Quarkus 3.40.1; two CPU workers, 32 queue slots. All eight
requests returned 200 within the finite production budget; post-request CPU
activity/queue and database queue were zero.

| Qualified rows | Workload | Matches | Decrypt attempts | Candidate queries | SQL statements | HTTP ms | Max batch / retained page |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 512 | Exact | 201 | 0 | 0 | 3 | 4252.47 | 0 / complete 201-item result |
| 512 | Dense prefix | 512 | 512 | 3 | 4 | 436.42 | 256 / 50 |
| 512 | Sparse prefix | 1 | 512 | 3 | 4 | 167.47 | 256 / 1 |
| 512 | No-match prefix | 0 | 512 | 3 | 4 | 124.78 | 256 / 0 |
| 4096 | Exact | 201 | 0 | 0 | 3 | 118.10 | 0 / complete 201-item result |
| 4096 | Dense prefix | 4096 | 4096 | 17 | 18 | 376.59 | 256 / 50 |
| 4096 | Sparse prefix | 15 | 4096 | 17 | 18 | 549.47 | 256 / 15 |
| 4096 | No-match prefix | 0 | 4096 | 17 | 18 | 323.85 | 256 / 0 |

The extra candidate query is the empty terminal batch for a full 256-row
multiple. Exact count/full projection bind the existing tenant HMAC predicate;
this single-Party history fixture's optimizer chose
`ix_party_identifiers_party_scheme_status`, not the available hash index.
Prefix initial/keyset plans selected `ix_party_identifiers_scheme_created_id`.
No planner option or index hint was forced. Deployment statistics and actual
distributions determine planner choices.

Measurements are individual local client-inclusive samples, with first-request
startup/JIT cost and noise; they are not latency percentiles, a throughput
capacity claim or an SLA. Prefix exact totals decrypt all qualified candidates
on every page, even sparse/no-match queries; retained state stays bounded by
batch and limit. Exact response memory scales with all matches. Saturated CPU
and single-slot pool deadline 503/recovery were independently evidenced in 6.7.

Focused performance verification passed in 3m 7s, then 5m 10s after adding real
pool-queue measurement. Final JDTLS is zero (20:20:29, log lines 4053–4060).

## Task 7.1: Consumer guide

`docs/operations/identifier-search-contract.md` documents both operations,
trusted context, strict raw UTF-16 limits and integer tokens, historical
normalizer-only eligibility, omitted/null/empty filters, complete exact results,
literal prefix/live forward/backward scopes, safe fields and stable failures.
Synthetic request/response examples were cross-checked against the approved
closed request/item/envelope schemas and passing historical/prefix/packaged
contracts; opaque continuation is copied from actual server results rather than
published as a fabricated valid token. Documentation introduces no SLA, mutation
behavior, sensitive real data or new response fields.

## Task 7.2: Operational rollout and capacity guide

`docs/operations/identifier-search-operations.md` identifies actual V9, immutable
migration/locking review, protection/index/signing retention and rotation,
finite 5s/15s budgets, two-worker/32-queue CPU defaults and existing 20-slot pool,
rejecting admission/cancellation cleanup, safe numeric observations, measured
work/plan/timing trade-offs, documented JVM/native/IDE prerequisites and rollback
preserving applied migrations and protected history. Property names/defaults,
native image and migration were cross-checked against saved configuration/code;
measurement tables preserve the actual task 6.9 checkpoint without claiming SLA
or production throughput. Native and final quality results remain separately
tracked rather than assumed from packaged JVM evidence.

## Task 7.3: Central architecture handoff

LikeC4 MCP search/read selected project default and confirmed the service FQN,
stack/confidentiality boundary and existing geographic/database/optional messaging
relationships at `catalog/system/microservices.c4:9`. Reading
findPartyByIdentifierSequence confirmed active-only scheme/identifier/no-result
labels and its source location `architectures/party-registry/party-registry-sequences.c4:177`.
`docs/operations/identifier-search-architecture-handoff.md` records the precise
historical/filter/cardinality/empty-result refinement, retained HMAC/protection
and relationships, safe envelope, consistent finite read and prefix CPU/cursor
flow. It links authoritative editor locations/specs and explicitly identifies
the architecture-repository maintainer's separate follow-up within service-local
scope. No central repository model was edited.

## Task 8.1: Final full JVM suite

The first final full command executed 1,839 tests in 12m 41s with two failures:
IdentifierSchemeTestFixtureTest still excluded additive V9 from its migration
catalog and PartyIdentifierContractMigrationTest still expected eight installed
migrations. Only those expectations were updated for V9; existing checksum,
fixture isolation, schema integrity and constraint assertions were preserved.
Their focused verification passed in 2m 48s.

The final unfiltered command then passed:

```shell
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew test --console=plain
```

Result: BUILD SUCCESSFUL in 12m 43s. The generated aggregate report
`build/reports/tests/test/index.html` records **1,846 tests, zero failures and
zero skipped**, including all search, architecture, migration and real reactive
database/HTTP cases. This is a fresh execution, not an UP-TO-DATE test task.
Captured output: `/home/alex/.local/share/opencode/tool-output/tool_128a96ef1001m9AbJTEK5hPZzS`.
The earlier failing output is retained separately at
`/home/alex/.local/share/opencode/tool-output/tool_128990810001hbHtzEoVsFeiM1`.

Existing deliberate timeout suites emit runtime diagnostics. HR000090 dropped
transaction notices were traced in XML output to unchanged
HibernateReactivePartyMutationAdapterTest, NationalityTimeoutTest and
PartyQuerySnapshotTest, not the new search read tests. No warning/diagnostic
was suppressed and no compiler warning was introduced. Packaged/native and
final current-model IDE analysis remain subsequent acceptance tasks.

## Task 8.2: Build paused on Podman/Testcontainers transport failure

```shell
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock QUARKUS_HTTP_TEST_PORT=0 ./gradlew build --console=plain
```

Result: BUILD FAILED in 3m 32s during actual unfiltered packaged integration
execution. Compilation, packaging and the already successful full unit task
were UP-TO-DATE. The integration aggregate reports 38 tests, three failed and
nine skipped due to resource initialization failures. PackagedIdentifierSchemeContractIT,
PackagedNationalityContractIT and PackagedRootContractIT could not create their
PostgreSQL containers: Docker Java's Unix-socket POST failed with
`java.io.IOException: Broken pipe` through PackagedGeographicReferenceResource.
Those suites did not reach the affected HTTP assertions.

PackagedIdentifierSearchContractIT independently executed all four methods
successfully in this same build (zero failures/skips), confirming its real
packaged search contracts. That does not make the full build successful.
Reports: `build/reports/tests/quarkusIntTest/index.html` and corresponding XML.

Read-only environment inspection found the rootless Podman socket listening
at `/run/user/1000/podman/podman.sock`; Podman 5.4.2 is local. The socket-activated
API service had exited successfully after inactivity, with a recorded shutdown
at 21:07:02. Socket activation/inactive service by itself does not establish the
cause of the broken connections; further transport/lifecycle diagnosis is needed.
No service configuration, container cleanup policy or system service was changed.

Task 8.2 remains unchecked and apply is paused for guidance on diagnosing/fixing
this transport blocker and rerunning full build. Final per-file JDTLS/Sonar,
native build/test, requirement audit and strict OpenSpec acceptance remain
uncompleted; no native or overall implementation completion is claimed.
