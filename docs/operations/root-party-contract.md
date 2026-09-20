# Root Party Operations and Operations Guide

The approved [OpenAPI](../contracts/party-registry.openapi.yaml) is the wire-contract authority and is served at `/q/openapi`. The six implemented operations are:

| Method/path | Behavior |
|---|---|
| `GET /v1/parties` | Tenant-qualified summaries and exact cursor pagination |
| `GET /v1/parties/{partyId}` | Current safe common detail in any lifecycle state |
| `PATCH /v1/parties/{partyId}` | Display-name correction only, including archived Parties |
| `POST /v1/parties/{partyId}/activate` | DRAFT to ACTIVE with qualifying verified identity evidence |
| `POST /v1/parties/{partyId}/deactivate` | ACTIVE to INACTIVE without an evidence requirement |
| `POST /v1/parties/{partyId}/archive` | DRAFT, ACTIVE or INACTIVE to ARCHIVED without deleting records |

## Context, responses, and concurrency

Every business request requires exactly one `Process-Id`, `Tenant-Id`, and `User-Id`, validated in that order. Process/tenant values are canonical lowercase UUIDs. User IDs must be nonblank, at most 128 Unicode code points, and free of C0/DEL/C1 control characters. Header names are case-insensitive. An accepted process value is echoed unchanged even on a later failure; missing/duplicate/invalid process values are not echoed. Management `/q` paths are exempt.

Successes use `200` and `{ "status": 200, "code": "successful", "data": ... }`. Errors contain only matching HTTP/body `status` and one stable `code`. Unexpected failures return sanitized `500 server-error`. Resources map API DTOs before using the published CDI `ResponseManager`; the published global error implementation remains authoritative.

Every root mutation requires one bare decimal `If-Match` in `0..9223372036854775807`, without quotes, signs, padding, or wildcards. Missing, repeated, invalid and overflowing values have distinct `if-match-*` errors. New operations check tenant-qualified absence before version, lifecycle, and evidence/business rules. Cross-tenant and absent targets both return `404 party-not-found`. A PATCH losing a version race returns `412 expected-version-mismatch`; lifecycle losers return `412 stale-party-version`.

New lifecycle transitions advance the root version exactly once and preserve identity, type, details, creation audit, nationality/identifier records and their independent versions. There is no reactivation. Archived Parties remain readable and their descriptive label remains correctable. Root/detail writers arbitrate on the same Party version.

## Display-name correction

PATCH accepts only `{ "displayName": "..." }`. Unknown/duplicate properties, nonobject JSON and scalar coercions are rejected. Missing/null body gives `400 request-body-required`; missing/null property gives `400 display-name-required`.

New labels use `strip().toUpperCase(Locale.ROOT)`, preserving accents, punctuation and interior whitespace. The normalized maximum is 300 **UTF-16 units**, including uppercase expansion. Exceeding it produces `400 display-name-too-long`. Empty/blank strings reach business validation only after absence/version checks and then produce `422 blank-display-name`. Identical canonical corrections still advance the version once. PATCH ignores lifecycle idempotency headers and does not create replay records.

Validation order is context, body syntax/requiredness/normalized length, path, If-Match, tenant-qualified absence, expected version, and blankness, subject to framework method/media checks. Historical reads and restoration do not normalize or backfill stored data.

## Lifecycle evidence and replay

Activation requires a same-tenant, same-Party VERIFIED identifier with a matching compatible scheme and no expiration or expiration on/after the one captured UTC evaluation date. Primary flags and scheme lifecycle status do not gate verified evidence. Missing evidence yields `422 missing-qualifying-identifier`; invalid state yields `409 invalid-party-lifecycle` before evidence is examined.

Lifecycle `Idempotency-Key` is optional. If supplied, it must occur once, be nonblank and contain at most 128 Unicode code points. Values compare exactly, without trimming or case folding. Scope is tenant/action/key; effective input is Party ID and expected version. User/process are excluded from equivalence but must still be valid on every retry.

Equivalent retries return the original safe result, audit actor/timestamp and version, even after archival or restart. The response echoes the retry's process. Replay never changes current state or emits another logical event. Changed target/version under a completed key produces `409 idempotency-key-conflict` before current-state checks. Syntax validation still precedes replay. Failed attempts do not consume keys.

Infrastructure acquires the transaction-scoped replay lock before a qualified root lock. Mutation transactions use read committed so waiting retries see newly committed snapshots. Root, optional schema-3 completion and enabled outbox intent commit atomically. No request transaction waits for geographic calls or broker acknowledgements. Outbox modes remain `disabled`, `stored-only`, and `published`.

## Listing and cursors

Accepted query parameters, each at most once, are `type`, `recordStatus`, `displayNameStartsWith`, `displayNameContains`, `createdFrom`, `createdTo`, `cursor`, and `limit`. Unknown/repeated/invalid input returns `400 bad-request`. Filters combine with AND. Type/status use exact enums; creation bounds are inclusive offset-qualified instants. Names compare literal canonical prefixes/substrings; `%` and `_` are not wildcards. Blank name filters are absent. Raw decoded name filters have a 300-**code-point** maximum, unlike PATCH's normalized UTF-16 write limit.

The default limit is 50; 1–200 are accepted. Order is creation instant descending, then canonical unsigned UUID descending, in both navigation directions. Cursors retain full stored timestamp precision and are authenticated against tenant, every effective filter and limit. Equivalent canonical filters/offsets have the same scope. Altered/unknown-key/unknown-version/scope-mismatched cursors are rejected without echoing token internals. Cursors have no expiry policy in this release.

Every response includes `nextCursor`, `prevCursor`, `totalElements`, `totalPages`, and `numberOfElements`. Unavailable directions are explicit nulls. Empty datasets return `data: []`, null cursors and zero counts. Counts describe all matching rows before continuation. Each response uses one read-only repeatable-read snapshot; separate requests are live continuation, not a retained historical snapshot. Valid boundaries remain usable after the boundary row changes or disappears.

### Provision and rotate independent signing keys

Configure these properties through deployment secrets before startup (including development when invoking the service outside the test profile):

```properties
party-registry.pagination.current-signing-key-id=v1
party-registry.pagination.signing-keys.v1=${PARTY_CURSOR_SIGNING_KEY_V1}
```

Supply independent Base64-encoded 32-byte key material consistently to every replica. There is no production/development fallback. The deterministic keys under `%test` are only for isolated tests. Do not reuse identifier encryption/index keys or the registration fingerprint key.

Rotation is staged: distribute the new verification key to every replica first, then switch the current signing key ID, retaining older verification keys needed by outstanding cursors and rollback builds. Because this contract introduces no token expiry, retiring a key deliberately invalidates all cursors signed with it and requires an explicit consumer/operational policy. Keep raw keys, cursors, names and filter values out of logs/traces/metric tags.

### Timeouts and capacity

| Setting | Environment override | Default |
|---|---|---|
| `party-registry.queries.statement-timeout` | `PARTY_QUERY_STATEMENT_TIMEOUT` | 5s |
| `party-registry.queries.traversal-timeout` | `PARTY_QUERY_TRAVERSAL_TIMEOUT` | 15s |
| `party-registry.mutations.lock-timeout` | `PARTY_MUTATION_LOCK_TIMEOUT` | 5s |
| `party-registry.mutations.statement-timeout` | `PARTY_MUTATION_STATEMENT_TIMEOUT` | 10s |
| `party-registry.mutations.operation-timeout` | `PARTY_MUTATION_OPERATION_TIMEOUT` | 15s |

Limits must be positive and supported by the database. SQL/lock settings are transaction-local. Cancellation/timeouts release sessions/transactions; no automatic write retry or partial page is substituted for failure. The tested Hibernate Reactive version can log `HR000090` while rollback-on-close releases a cancelled transaction; the diagnostic remains visible, and separate-session/pool-reuse tests verify actual cleanup.

Without name predicates, PostgreSQL handles count/keyset/neighbor queries. Name filtering traverses bounded batches of at most 256 summary rows and applies Java root-locale canonical comparison to historical Unicode. It retains bounded page/neighbor state rather than the complete tenant, but exact totals can require a full qualified scan. Monitor scan duration, rows/batches, pool pressure and timeout rates; benchmark representative tenant volumes before rollout.

The local PostgreSQL 18.4/Podman reference measured 2,000 qualified rows, 500 matches, eight batches, maximum batch 256 and page size 50: 562ms focused and 49ms warmed. These observations establish bounded behavior, not a production throughput/SLA guarantee. The isolated accumulator also processes 10,000 matches with retained page state bounded by the requested limit.

Internal counts use `long`, but the current published response library exposes `Integer` pagination counts. Checked conversion rejects totals above `Integer.MAX_VALUE` as sanitized technical failure; it never truncates, caps or silently omits exact counts. Deployments requiring more than 2,147,483,647 matching rows need a compatible published shared-library enhancement before supporting that capacity.

## Observability

`RequestContextFilter` owns completion logs, accepted process echoes and MDC cleanup. The exact console format remains:

```text
%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [userId=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```

Bounded resource spans are `party.list`, `party.retrieve`, `party.patch`, `party.activate`, `party.deactivate`, and `party.archive`. Application/transaction observations capture terminal success/replay/conflict/failure/cancellation. Metrics include `party.registry.http.operation`, `party.registry.http.mutation`, `party.registry.http.optimistic.conflicts`, `party.registry.operation`, `party.registry.lifecycle.key.wait`, and `party.registry.collection.scan` with `.rows`/`.batches`. Submitted values and raw identities are not metric dimensions. Partial scan counts describe received work on failure/cancellation, not a returned partial page.

## Migration, rollout, and rollback

1. Apply V5 through Flyway to add `(tenant_id, created_at DESC, id DESC)` indexing. Review index-build time/locking on a production-sized clone. Never edit V1–V4 or create the index manually. No name backfill is required.
2. Distribute cursor secrets and compatible lifecycle snapshot/event readers before advertising new behavior.
3. During mixed-version deployment, route keyed lifecycle traffic to the new handlers consistently, or finish deployment before enabling the guarantee. Older activation handlers do not honor the durable replay contract.
4. Enable `published` outbox mode only with readers supporting `party.updated.v1`, `party.activated.v1`, `party.deactivated.v1`, and `party.archived.v1`. Existing transport delivery retries may redeliver an event; API replay creates no additional logical event.
5. Preserve accepted roots, versions, snapshots, outbox rows and the V5 index on rollback. Never delete a completion key or reset data to accommodate an older binary.
6. Restore a compatible handler/publisher build or pause affected lifecycle/publication traffic until one is available. Preserve archived states and original registration snapshots throughout recovery.

Test-only catalog/root migrations under test source sets are never deployed as production migrations.

## Verification and IDE evidence

Required commands are `./gradlew test`, `./gradlew build`, `./gradlew buildNative -Dquarkus.native.container-build=true`, and `./gradlew testNative -Dquarkus.native.container-build=true`. Rootless Podman executions set `DOCKER_HOST` to an accessible Docker-compatible socket and may set `QUARKUS_HTTP_TEST_PORT=0`. Native container builds additionally accept `-Dquarkus.native.container-runtime=podman`.

If a socket-activated Podman API exits between Testcontainers calls and causes `Broken pipe`, use a test-owned persistent endpoint for that run, for example `podman system service --time=0 unix:///tmp/party-tests.sock`, point `DOCKER_HOST` at it, and stop that service after verification. This is an execution prerequisite, not an application timeout change.

The packaged suites exercise both subtype codecs, strict binding, pagination writers, lifecycle replay, and equality of published/source OpenAPI. The restart suite chooses the current executable using generated `build/quarkus-artifact.properties`; the existence of a stale native binary alone does not select native mode. It starts two actual processes against retained isolated data, discards original response payloads, and verifies historical results plus unchanged logical-event counts after relaunch.

For local quality acceptance in Antigravity:

1. Use Red Hat Java in Standard mode with the Java 25 Gradle model and dependencies fully imported. Reconcile compilation and IDE diagnostics rather than accepting one as a substitute for the other.
2. Use SonarQube for IDE standalone mode. Record extension version and active local rules/parameters. The verified baseline is 5.7.0 with 571 active Java rules; this is not a remote Quality Gate or equivalent server coverage.
3. Inventory every new/modified production, unit-test and integration-test Java file. Include untracked files and nested types. Record each saved revision using `git hash-object <path>` and its relevant tests.
4. Open/save each file or use a supported explicit analysis action. Record actual analysis completion and findings separately from JDTLS diagnostics; an empty Problems panel alone is insufficient. Resolve all active-rule findings without blanket exclusions/suppressions, then reanalyze changed revisions.
5. When agent-accessible logs expose only counts, provide the rule, message and line from Problems for the same saved revision. If the IDE model cannot resolve a new dependency, reimport/restart the Java workspace and rerun analysis before accepting zero findings.

The change's [verification evidence](../../openspec/changes/complete-parties-contract/verification.md) records per-task diagnostics, test commands, runtime prerequisites and unresolved acceptance items. Valid Java date fixtures use fixed clocks and `java.time.Month` constants.
