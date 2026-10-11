# Identifier Search Operations

Public behavior and synthetic examples are in the
[consumer guide](identifier-search-contract.md). The implementation uses Java
25, Gradle 9.7.0, Quarkus 3.40.1, Hibernate Reactive 3.4.2.Final and Mutiny
3.3.0. Native container builds select
`quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`.

## Flyway rollout and compatibility

1. Review the additive
   [`V9__add_identifier_search_order_index.sql`](../../src/main/resources/db/migration/V9__add_identifier_search_order_index.sql)
   on a production-sized clone. It creates
   `ix_party_identifiers_scheme_created_id` on tenant/scheme/creation descending/
   identifier descending. Measure index build time, table locking and storage
   overhead for the intended rollout window. Previously applied migrations are
   immutable; Flyway remains the sole schema authority.
2. Deploy approved OpenAPI, codecs, use cases, migration and verified executable
   together. No value rewrite, plaintext column/cache, registration change or
   historical backfill is needed. Operational reads remain Hibernate Reactive;
   the JDBC datasource is for Flyway, not request processing.
3. Retain encryption keys for every required stored version. Prefix discovery
   authenticates each selected candidate using actual tenant/Party/identifier/
   scheme IDs and stored encryption/normalization versions. Missing retained
   keys, corrupt tags/AAD or unsupported stored normalization fail safely with
   server-error; never skip those candidates or silently substitute a current
   key. Current supported scheme normalization is resolved independently of
   new-registration eligibility.
4. Preserve the existing tenant-derived identifier index HMAC key and byte
   format. Exact equality uses the existing lookup predicate and does not
   decrypt ciphertext. Changing the index key without a separately specified
   compatible data migration would make existing lookups inconsistent.
5. Provision pagination signing keys independently from protection/index keys.
   Distribute retained verification keys across replicas before rotating the
   current signing-key ID. Prefix tokens use resource-specific outer HMAC and
   keyed sensitive-scope authentication. Retain required previous signing keys
   for accepted continuation; no automatic expiry was introduced.

## Finite execution and capacity settings

| Property | Default | Scope |
| --- | --- | --- |
| `party-registry.queries.statement-timeout` | `5s` | Transaction-local SQL statement budget |
| `party-registry.queries.traversal-timeout` | `15s` | Subscription-to-completion operation budget, including acquisition and CPU queue/work |
| `party-registry.search.cpu-workers` | `2` | Fixed detached-batch CPU workers |
| `party-registry.search.cpu-queue-capacity` | `32` | Bounded waiting batches with rejecting admission |
| `quarkus.datasource.reactive.max-size` | `20` | Existing shared reactive datasource pool |

Use positive finite values; query durations must fit supported millisecond/nano
conversions. Each request has at most one CPU batch outstanding. Candidate
batches contain at most 256 rows and retained prefix pages at most effective
limit (1..200). Worker work contains detached immutable projection/identity
data, never an open session or request-scoped object. Results and failures
resume the identical originating Vert.x context before the next session call.

Queue admission never runs expensive work on the caller, blocks the event loop
waiting for capacity or uses an unbounded queue. Rejection and classified
deadlines return dependency-unavailable without partial data. Cancellation removes
queued work, running work checks cancellation between records, and late results
are discarded. Scoped reads own rollback/session cleanup; late acquired sessions
are closed. Healthy requests must remain usable after failures/cancellation.
No automatic query retry or capped/approximate total is configured.

## Observability

The single RequestContextFilter owns validation, accepted-only process echo,
completion logging and MDC cleanup. Preserve the exact logging format:

```text
%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [userId=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```

Bounded operation/span labels are `party.search-by-identifier` and
`party.search-by-identifier-prefix`. Existing HTTP and Application observers
record duration and stable terminal outcomes. Numeric
`party.registry.identifier.search.work` summaries record SQL statements,
candidate batches, inspected/decrypted candidates, matches and maximum retained
batch/page, including partial work on failure/timeout/cancellation. Its dimensions
are closed operation/measurement/outcome labels. CPU gauges expose active/queued
work, workers and capacity; a counter records rejected admission. Reactive-pool
queue measurement is available through the existing datasource metrics.

Do not add scheme codes, request identities, raw/normalized selectors, cursors,
private storage fields or keys as metric dimensions or trace/log data. Expected
codes remain stable and unexpected search failures reach the shared mapper with
fixed diagnostics and safe stack locations, without value-bearing throwable
chains. Keep blocked-thread warnings enabled.

## Measured workload checkpoint

The focused `IdentifierSearchPerformanceTest` generates actual AES/HMAC-protected
synthetic data and writes sanitized operator/index/timing/work evidence to
`build/reports/identifier-search/performance.json`. The change-local
[verification ledger](../../openspec/changes/complete-party-search-endpoints/verification.md)
retains the implementation checkpoint independently of later regenerated reports.

On the recorded Java 25/Quarkus 3.40.1/PostgreSQL 18.4 run, with 12 available
processors, 2 GiB test heap, two CPU workers and 32 queued-batch capacity:

| Qualified candidates | Prefix density | Matches | Decrypt attempts | Candidate queries | HTTP sample ms | Max batch / page |
| ---: | --- | ---: | ---: | ---: | ---: | --- |
| 512 | Dense | 512 | 512 | 3 | 436.42 | 256 / 50 |
| 512 | Sparse | 1 | 512 | 3 | 167.47 | 256 / 1 |
| 512 | None | 0 | 512 | 3 | 124.78 | 256 / 0 |
| 4096 | Dense | 4096 | 4096 | 17 | 376.59 | 256 / 50 |
| 4096 | Sparse | 15 | 4096 | 17 | 549.47 | 256 / 15 |
| 4096 | None | 0 | 4096 | 17 | 323.85 | 256 / 0 |

Exact queries returned all 201 matching rows with three SQL statements and no
decryption: the initial client-inclusive sample was 4252.47ms and a subsequent
sample 118.10ms. These are individual local measurements with initialization/JIT
and cache/noise effects, not percentiles, monotonic scaling or an SLA. All measured
requests returned 200 under configured budgets; CPU/pool queues were zero after
each request. Separate runtime tests verify rejecting one-worker/one-slot CPU
saturation and two-second exhausted one-connection-pool deadlines, followed by
healthy reuse.

Prefix first/keyset batch plans used the V9 ordering index. The single-Party
exact-history fixture's optimizer selected the existing Party/scheme/status
index rather than the available tenant/scheme/hash/status index. No planner hint
was forced. Review actual production distributions/statistics and query plans.
Sanitized evidence deliberately excludes query predicates containing tenant or
lookup material.

Exact prefix totals require O(qualified candidates) authenticated comparisons
for every page, including sparse/no-match requests. Retained state is O(batch +
page), not O(tenant size); exact response memory is O(all matches). An empty
terminal query follows a full multiple of 256 candidates. Measure actual tenant/
scheme volumes, pool pressure, concurrency, queue rejection and deadline outcomes
before sizing deployment capacity. If costs exceed the budget, retain exact
semantics and plan a separately approved indexing design rather than truncating
results or weakening historical eligibility.

## Verification prerequisites

Use Java 25 and the configured Gradle wrapper. Provide the documented rootless
Podman/Testcontainers socket (this machine uses
`DOCKER_HOST=unix:///run/user/1000/podman/podman.sock`) and PostgreSQL 18 image.
`QUARKUS_HTTP_TEST_PORT=0` avoids a fixed test-port conflict. Native builds require
the configured Mandrel JDK 25 builder image and sufficient local resources.

Run focused tests during iteration and all final acceptance commands on saved
revisions:

```shell
./gradlew test
./gradlew build
./gradlew buildNative -Dquarkus.native.container-build=true
./gradlew testNative -Dquarkus.native.container-build=true
openspec validate complete-party-search-endpoints --type change --strict --no-interactive
```

PackagedIdentifierSearchContractIT shares schema assertions, excludes unit CDI
alternatives and starts a real executable. Its isolated resource explicitly
supplies a synthetic retained key ring because the current packaged harness
launches prod; do not assume `%test` keys are selected. It seeds older protected
history before startup and verifies old-key cursor continuation, new-key issuance,
current JCA registration/search, reflection/codecs, null writers, sanitized errors
and approved/packaged/served OpenAPI agreement.

Compilation, JVM tests or native metadata preparation are not native execution
evidence. Final acceptance also requires current-model JDTLS and actual Sonar IDE
analysis for saved affected revisions; a standalone IDE result is not a remote
server Quality Gate. Consult the change ledger for actual completion status.

## Safe rollback

Restore a compatible previously verified executable or temporarily remove search
traffic routing. Retain the additive index, all applied migration history,
protected identifier history, index material, required encryption versions and
accepted signing verification keys. Do not edit applied migrations, issue manual
DDL, rewrite identifier values/statuses or delete historical evidence to make an
older executable work.
