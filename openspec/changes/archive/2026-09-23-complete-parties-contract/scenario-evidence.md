# Final EARS Scenario Evidence

All **76 scenarios** in the four change specifications are mapped below to passing automated checks. Test names refer to the repository classes; `verification.md` records their execution, saved revisions and IDE results. Edge-case matrices are tested at Domain/Application/reactive database/HTTP boundaries, while packaged JVM and native suites additionally verify executable reachability and wire behavior.

## Test key

Paths are relative to `src/test/java/com/alexastudillo/partyregistry/` unless marked packaged.

| Key | Test class |
|---|---|
| CTX | `api/filter/RootPartyContextContractTest` and `RequestContextFilterTest` |
| READ | `api/resource/PartyRootReadContractTest` and `PartyRootReadSmokeTest` |
| QUERY | `api/support/PartyQueryParametersTest`, `application/usecase/PartyReadUseCasesTest` |
| CURSOR | `infrastructure/security/HmacPartyCursorAdapterTest`, `PartyCursorKeyMaterialTest` |
| PAGE | `infrastructure/persistence/PartyPageReaderTest`, `PartyNameQueryTest`, `PartyQuerySnapshotTest`, `PartyQueryVolumeTest` |
| PATCH | `api/resource/PartyRootPatchContractTest`, `PartyRootMutationSmokeTest` |
| LABEL | `domain/model/PartyDisplayNameCorrectionTest`, `application/usecase/PatchPartyUseCaseTest` |
| LIFE | `api/resource/PartyRootLifecycleContractTest` |
| POLICY | `domain/policy/PartyActivationPolicyTest`, `domain/model/PartyLifecycleTest` |
| WORKFLOW | `application/usecase/ChangePartyLifecycleUseCaseTest`, `PartyLifecycleObservationTest` |
| ROOTDB | `infrastructure/persistence/PartyRootMutationPersistenceTest` |
| ACTDB | `infrastructure/persistence/ReactivePartyActivationWorkflowTest` |
| LOCK | `infrastructure/persistence/PartyLifecycleIdempotencyPersistenceTest` |
| RACE | `infrastructure/persistence/PartyLifecycleConcurrencyTest` |
| CROSS | `api/resource/PartyRootDetailRaceContractTest` |
| ATOMIC | `infrastructure/persistence/HibernateReactivePartyMutationAdapterTest` |
| CODEC | `infrastructure/persistence/PartyLifecycleSnapshotCodecTest`, `IdempotencyResultSnapshotCodecTest`, `PartyRootOutboxPersistenceTest` |
| ERROR | `api/error/RootFrameworkErrorContractTest`, `RootErrorContractTest`, `PartyApiErrorTranslatorTest` |
| NATIVE | Packaged `PackagedRootContractIT` (four cases, passing in JVM and native) |
| RESTART | Packaged `PartyLifecycleRestartIT` (passing actual JVM and native process relaunch) |

## Party queries — 21 scenarios

Source: [party-queries](specs/party-queries/spec.md).

| Scenario | Passing evidence |
|---|---|
| Context validation covers every root operation | CTX: six-route missing/duplicate headers and first-error precedence |
| Duplicate and noncanonical headers are rejected | CTX: process duplication/canonical validation and echo omission |
| User identifier boundaries are enforced | CTX: blank, long, unsafe, accepted boundary and Unicode code-point unit checks |
| Request context does not leak | CTX: twelve barrier-released requests and complete/partial MDC capture |
| Success and error envelopes remain consistent | READ, PATCH, LIFE, ERROR, NATIVE: exact fields and matching status |
| Framework failures retain the public envelope | ERROR and NATIVE: 404/405/415 plus accepted process echoes |
| Unexpected failure is sanitized | ERROR: controlled port failures through every actual root resource |
| Default listing includes retained lifecycle states | READ: both types in all four states and summary-only projection |
| Other tenants are excluded from results and totals | READ, PAGE: isolated tenant data/counts and navigation |
| Filters intersect rather than broaden results | READ, PAGE: all filter intersections with independent near-miss records |
| Historical mixed-case names remain searchable | READ, PAGE: root-locale expansion, accents, literal punctuation and retained stored text |
| Creation interval boundaries are inclusive | READ, PAGE: lower/upper/equal instant bounds and equivalent offsets |
| Invalid queries are not silently ignored | QUERY, READ: all eight duplicate names, unknowns, invalid types/dates/limits and inverted bounds |
| Page size boundaries are explicit | READ: default 50 and limits 1/200/0/201; QUERY parser boundary matrix |
| Equal creation timestamps have a stable order | READ, PAGE, CURSOR: equal timestamps, unsigned UUID ordering and precise boundaries |
| Previous navigation restores the preceding page | READ, PAGE, NATIVE: round-trip previous page in original descending order |
| Cursor scope cannot be reused incorrectly | READ, CURSOR: tampering and each scope dimension; equivalent canonical values accepted |
| Collection metadata includes terminal and empty values | READ, PAGE, NATIVE: exact totals, sizes, page count and explicit null directions |
| Each Party type returns its own details | READ, NATIVE: exact subtype/common field sets without identifier or pagination additions |
| Archived and historical records remain readable | READ, ROOTDB: stored archived text/audit remains unchanged without geographic calls |
| Invalid and concealed IDs have distinct safe outcomes | READ: malformed/case-invalid ID versus same absent/cross-tenant 404 envelope |

## Party basic update — 14 scenarios

Source: [party-basic-update](specs/party-basic-update/spec.md).

| Scenario | Passing evidence |
|---|---|
| Missing body and missing value are distinguished | PATCH, NATIVE: absent/null body versus missing/null property |
| Unsupported or coerced input is rejected | PATCH, NATIVE: nonobjects, wrong tokens, duplicates and unknown assignments |
| Normalization preserves meaningful text | LABEL, PATCH: strip/root-locale uppercase with accents, punctuation and interior spaces |
| Normalized-length boundaries are enforced | LABEL, PATCH: 300/301 UTF-16 units, expansion, supplementary characters and exterior whitespace |
| Blank display names cannot clear the label | LABEL, PATCH: empty/blank rejection retains full state/audit/version and event count |
| Required body validation precedes operation headers | PATCH: missing value before path/If-Match failures |
| Invalid expected versions are rejected explicitly | PATCH: absent/duplicate/quoted/signed/padded/wildcard/overflow versions |
| Tenant concealment precedes semantic checks | PATCH: cross-tenant target with stale input and blank label yields not found |
| Stale version precedes blank-name validation | PATCH, LABEL: stale-before-blank ordering and no write/event |
| Both Party types preserve unrelated details | PATCH: original 201 registration replays and identifier rows; ROOTDB: full detail/identifier/nationality rows retained |
| Archived descriptive information remains correctable | PATCH, NATIVE: archived correction preserves status and advances version |
| Equivalent correction still advances the version | LABEL, PATCH, ROOTDB: identical canonical correction accepted exactly once per version |
| Concurrent corrections cannot overwrite each other | CROSS: root correction versus type-specific write has one winner and exact 412 loser |
| Failed correction leaves no partial outcome | ATOMIC: failures after flushed root/event writes roll back the complete result |

## Party deactivation and archival — 22 scenarios

Source: [party-deactivation-and-archival](specs/party-deactivation-and-archival/spec.md).

| Scenario | Passing evidence |
|---|---|
| Active Party becomes inactive | LIFE, NATIVE: both subtypes, exact next version and accepted audit actor |
| Invalid deactivation states are rejected | LIFE, POLICY: DRAFT/INACTIVE/ARCHIVED matrix with unchanged results |
| Every nonarchived state can be archived directly | LIFE, POLICY: DRAFT/ACTIVE/INACTIVE direct archival |
| Archived state is terminal for new lifecycle actions | LIFE, POLICY: all three actions rejected at current archived version |
| Optional key does not become mandatory | LIFE, RACE: accepted unkeyed operations use ordinary version arbitration |
| Optional key boundaries apply to every lifecycle action | LIFE: duplicate/blank/128/129 boundaries; LOCK and contract tests preserve exact supplementary-character keys |
| Invalid syntax is checked even for a saved result | LIFE: completed archival key cannot bypass path/version syntax checks |
| Cross-tenant lifecycle targets are concealed | LIFE, ACTDB: all actions conceal ownership before other business checks |
| Stale version takes precedence over invalid transition | LIFE, WORKFLOW: version-before-state/evidence ordering |
| Distinct competing mutations have one winner | RACE, CROSS: distinct/unkeyed and root/detail contention |
| Changed target or expected version conflicts | LIFE, WORKFLOW: completed-key mismatch before current-state evaluation |
| Different actions and tenants have independent key scopes | LOCK: full scope and forced lock-hash collision isolation; WORKFLOW/contract identity checks |
| Concurrent conflicting keys cannot accept two mutations | RACE: distinct target Parties with one action/key, losing Party unchanged |
| Replay returns the original result after later transitions | NATIVE, RESTART, WORKFLOW: original representation despite archival/correction |
| Retry correlation is fresh while audit remains historical | CTX, RESTART: new process/user echo/log attribution and original result audit |
| Concurrent equivalent retries converge | RACE: every action/type produces exactly one applied result plus equivalent replay |
| Failed attempt does not consume the key | LIFE: add qualifying evidence then retry; ATOMIC: rollback followed by successful same-key retry |
| Accepted result survives a lost response and restart | RESTART: discarded response payloads, different actual process IDs, retained database and unchanged event counts |
| Unkeyed repeated actions retain concurrency behavior | LIFE, PATCH smoke: old version is stale and current invalid state conflicts |
| Archival retains identity and independent identifiers | ROOTDB: full detail/identifier/nationality row comparisons; LIFE/READ: retained root remains retrievable/listable; CODEC: independent persisted result schemas |
| Enabled event and replay result agree with the accepted version | ATOMIC, ROOTDB, CODEC, RACE: exact version, single snapshot/event and safe allowlisted metadata |
| Late failure rolls back the whole observable outcome | ATOMIC: actual flushed root/snapshot/event checkpoints, session closure and reusable keys |

## Party activation — 19 scenarios

Source: [party-activation](specs/party-activation/spec.md).

| Scenario | Passing evidence |
|---|---|
| Draft Party with verified identifier is eligible | POLICY, LIFE, ACTDB, NATIVE |
| Pending identifier does not permit activation | POLICY, LIFE: unchanged draft and missing-evidence code |
| Expired verified identifier does not permit activation | POLICY, LIFE: deterministic prior-UTC-day expiration |
| Incompatible verified identifier does not permit activation | POLICY, LIFE: both incompatible subtype directions |
| Expiration boundary and absent expiration qualify | LIFE: profile-selected fixed UTC+14 clock near the UTC date boundary, same-day/null expiration |
| Primary flag and scheme status do not replace evidence rules | POLICY, LIFE, ACTDB: nonprimary and draft/deprecated/retired compatible schemes |
| Another Party's evidence cannot qualify | POLICY, LIFE, ROOTDB: ownership and minimal tenant-qualified projection |
| Eligible draft Party becomes active | LIFE, ACTDB, NATIVE: exact ACTIVE result, next version and audit |
| Invalid lifecycle transition is rejected | LIFE, POLICY: ACTIVE/INACTIVE/ARCHIVED remain unchanged |
| Missing expected version is rejected | LIFE: all action/header categories |
| Stale expected version is rejected | LIFE, ACTDB: lifecycle stale code before state/evidence |
| Concurrent activation permits one winner | RACE, ACTDB: distinct/unkeyed contenders and losing event/snapshot absence |
| Equivalent keyed activation returns its accepted version | WORKFLOW, RACE, NATIVE: replay precedes the now-stale version |
| Cross-tenant Party is concealed | LIFE, ACTDB: qualified absence before version/state/evidence |
| Successful activation and event agree | ACTDB, CODEC, RACE: matching accepted version and confidential-value exclusion |
| Failed activation produces no partial outcome | ACTDB: enabled-event write failure; ATOMIC: shared rollback acceptance boundary |
| Invalid key precedes Party lookup | LIFE: blank/duplicate/long keys before malformed or absent targets |
| State failure precedes missing evidence | LIFE, ACTDB, WORKFLOW: current invalid state rejected before evidence eligibility |
| Equivalent activation retries share one result | RACE: equivalent keys with different current actors share one historical result/event |

## Cross-cutting acceptance

- `PartyResourceSignatureTest` inspects all six actual generic return types and DTO boundaries.
- `CleanArchitectureTest` and architecture fixtures enforce inward dependency direction, Domain isolation, Application's deliberate Mutiny allowance, resource delegation, and package-cycle freedom.
- `OpenApiContractTest` and packaged/native OpenAPI equality checks pin source and published declarations/examples.
- `PartyListingMigrationTest` and migration regressions verify fresh/V4 upgrades and immutable prior checksums.
- The final Java inventory covers 143 saved files with separate JDTLS and local Sonar evidence.
- Passed totals: **827 JVM tests**, **18 packaged JVM tests**, **18 native tests**, no failures/skips in the accepted runs.
- The native build used Mandrel 25.0.4.1-Final, explicit Podman and a 6 GiB compiler heap. Production code and packaged tests were unchanged by the final nationality-retention fixture enhancement; its JVM suite and IDE checks were rerun.
- Operational prerequisites, memory/runtime overrides, external cursor secrets, retained-data rollback and local-analysis limits are documented in the operations guide and `verification.md`.
