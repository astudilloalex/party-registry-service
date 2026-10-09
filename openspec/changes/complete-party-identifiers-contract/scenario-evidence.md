# Party Identifier Scenario Evidence Map

This map originally covered 27 Q/M/L requirements and 87 scenarios. The confirmed issuing-entity refinement adds one R requirement and three scenarios, for 28 requirements and 90 scenarios. Titles are copied from the approved specifications; test boundaries and task references are plans, not passing evidence. Every row starts pending. Update evidence only after the relevant saved source has executable test results and the required IDE diagnostics. Final acceptance also requires packaged/native coverage where applicable.

Sources: **Q** = [queries](specs/party-identifier-queries/spec.md), **M** = [maintenance](specs/party-identifier-maintenance/spec.md), **L** = [lifecycle](specs/party-identifier-lifecycle/spec.md). Each group heading is the exact source requirement title. Boundary abbreviations: **contract** = static OpenAPI/schema checks; **domain** = pure aggregate/policy tests; **application** = workflow/port-double tests; **API** = parser/mapper tests; **DB** = real reactive PostgreSQL tests; **HTTP** = live resource tests; **packaged/native** = actual executable integration tests. Task references use the numeric IDs in `tasks.md`.

## Q: Complete published party-identifiers contract

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q01 | Eight operations agree with their published contract | contract, HTTP, packaged/native | 2.1–2.2, 6.7, 8.3, 9.3, 10.3, 11.3, 13.1, 14.4 | Pending |

## Q: Trusted context and process correlation

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q02 | Header precedence and accepted echo | HTTP, packaged/native | 2.3, 12.4–12.5, 13.1, 14.4 | Pending |
| Q03 | Invalid process is not echoed | HTTP, packaged/native | 2.3, 12.4–12.5, 13.1, 14.4 | Pending |
| Q04 | Concurrent context remains isolated | coordinated HTTP, observation, packaged/native | 12.1–12.2, 12.5, 13.1–13.2, 14.4 | Pending |

## Q: Tenant and Party ownership concealment

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q05 | Missing and cross-tenant Party are indistinguishable | application, DB, HTTP, packaged/native | 6.2–6.4, 6.7, 13.1, 14.4 | Pending |
| Q06 | Nested identifier cannot escape its Party | application, DB, HTTP, packaged/native | 6.2, 6.4, 6.7, 7.4–7.5, 9.3, 10.3, 11.3, 13.1, 14.4 | Pending |
| Q07 | Both Party types and every Party state remain readable | application, DB, HTTP, packaged/native | 6.2–6.4, 6.7, 13.1, 14.4 | Pending |

## Q: Strict read selectors and filters

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q08 | Filter intersection is exact | API, DB, HTTP, packaged/native | 6.3, 6.5, 6.7, 13.1, 14.4 | Pending |
| Q09 | Limit boundaries and absence | API, application, HTTP, packaged/native | 6.1, 6.3–6.5, 6.7, 13.1, 14.4 | Pending |
| Q10 | Invalid selectors and query values | API, HTTP, packaged/native | 2.1, 6.5, 6.7, 13.1, 14.4 | Pending |

## Q: Complete historical listing and masked detail

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q11 | Historical and past-date identifiers remain visible | DB, HTTP, packaged/native | 6.2–6.3, 6.7, 13.1, 14.4 | Pending |
| Q12 | Current natural-person projection remains narrower | DB, HTTP compatibility, packaged/native | 6.2, 12.6, 13.1, 14.4 | Pending |

## Q: Deterministic scoped cursor navigation

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q13 | Timestamp ties navigate without duplication | cursor, DB, HTTP, packaged/native | 6.1, 6.3, 6.7, 13.1, 14.4 | Pending |
| Q14 | Cursor cannot cross scope | cursor, application, HTTP, packaged/native | 6.1, 6.4, 6.7, 13.1, 14.4 | Pending |
| Q15 | Boundary changes do not invalidate navigation | cursor, DB, HTTP, packaged/native | 6.1, 6.3, 6.7, 13.1, 14.4 | Pending |

## Q: Exact collection metadata and empty outcomes

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q16 | Empty owned collection succeeds | application, DB, API, HTTP, packaged/native | 6.3–6.4, 6.6–6.7, 13.1, 14.4 | Pending |
| Q17 | First middle and final pages have exact metadata | DB, API, HTTP, packaged/native | 6.3, 6.6–6.7, 13.1, 14.4 | Pending |

## Q: Safe shared envelopes and failure behavior

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q18 | Every operation returns only its safe envelope | API, HTTP, outbox codec, architecture, packaged/native | 2.2–2.3, 6.6–6.7, 7.6, 8.3, 9.3, 10.3, 11.3, 13.1, 14.4, 14.6 | Pending |
| Q19 | Framework and dependency failures retain the envelope | HTTP, packaged/native | 4.3, 12.3–12.4, 13.1, 14.4 | Partial foundation: task 4.3 [verification](verification.md) / [saved revisions](java-inventory.md), `PartyIdentifierErrorContractTest` pins known-error exact envelopes/echoes. Actual identifier-route framework/dependency and packaged/native cases remain pending. |
| Q20 | Unexpected failure is sanitized | completion codec, HTTP, packaged/native | 7.2, 12.4, 13.1–13.2, 14.4 | Partial HTTP foundation: task 4.3 [verification](verification.md), unknown identifier invariant produces exact 500/server-error without its private cause. Completion corruption, actual routes and packaged/native cases remain pending. |
| Q21 | Unrepresentable collection count does not wrap | API, HTTP failure injection, packaged/native | 6.6, 12.4, 13.1, 14.4 | Pending |

## M: Maintenance follows the common identifier boundary

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M01 | Body validation precedes a missing version | API, HTTP, packaged/native | 9.1, 9.3, 13.1, 14.4 | Pending |
| M02 | Absence precedes a stale version | application, HTTP, packaged/native | 9.2–9.3, 10.2–10.3, 11.2–11.3, 13.1, 14.4 | Pending |

## M: Strict additional registration input

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M03 | Optional input and primary default | API, application, HTTP, packaged/native | 2.1, 4.1, 8.1, 8.3, 13.1, 14.4 | Pending |
| M04 | Required fields and raw length boundaries | API, HTTP, packaged/native | 2.1, 8.3, 13.1, 14.4 | Pending |
| M05 | Strict input rejects client-owned properties and coercion | API, HTTP, packaged/native | 2.1, 8.3, 13.1, 14.4 | Pending |
| M06 | Existing creation-key precedence is preserved | API, HTTP, packaged/native | 2.1, 8.3, 13.1, 14.4 | Pending |

## M: Eligible independent registration

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M07 | Both Party types receive additional identifiers | application, DB, HTTP, packaged/native | 8.1–8.3, 12.6, 13.1, 14.4 | Pending |
| M08 | Scheme errors retain their specific codes | application, HTTP, packaged/native | 4.3, 8.1, 8.3, 13.1, 14.4 | Pending |
| M09 | Expiration boundaries and optionality | domain, application, HTTP, packaged/native | 8.1, 8.3, 12.6, 13.1, 14.4 | Pending |
| M10 | Provisional primary coexists with existing evidence | domain, application, DB, HTTP, packaged/native | 3.4, 8.1, 8.3, 13.1, 14.4 | Pending |

## M: Active-value uniqueness and release

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M11 | Same-Party and other-Party duplicates conflict | DB, HTTP, packaged/native | 7.5, 8.3, 12.1, 13.1, 14.4 | Pending |
| M12 | Time passing alone preserves reservation | DB, HTTP, packaged/native | 7.5, 8.3, 12.6, 13.1, 14.4 | Pending |
| M13 | Withdrawal releases uniqueness without deleting history | DB, HTTP compatibility, packaged/native | 7.5, 10.3, 12.6, 13.1, 14.4 | Pending |
| M14 | Distinct-key duplicate race has one winner | coordinated DB/HTTP | 12.1 | Pending |

## M: Durable additional registration replay

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M15 | Historical retry survives restart and mutation | application, completion codec, HTTP, two-process packaged/native | 7.1–7.2, 8.1, 8.3, 13.2, 14.4 | Pending |
| M16 | Normalization does not erase request differences | fingerprint, application, HTTP, packaged/native | 4.1, 7.1, 8.1, 8.3, 13.1, 14.4 | Pending |
| M17 | Concurrent replay and failed key reuse | application, coordinated DB/HTTP, packaged/native | 7.4, 7.7, 8.1, 8.3, 12.1, 12.3, 13.1, 14.4 | Pending |

## M: Strict partial metadata correction

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M18 | Omission preserves and null clears | domain, API, application, HTTP, packaged/native | 3.2, 9.1–9.3, 13.1, 14.4 | Pending |
| M19 | Partial update validates the resulting interval | domain, application, HTTP, packaged/native | 3.2, 9.2–9.3, 13.1, 14.4 | Pending |
| M20 | Non-metadata input is rejected | API, HTTP, packaged/native | 2.1, 9.1, 9.3, 13.1, 14.4 | Pending |

## M: Metadata correction eligibility and audit

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M21 | Verified metadata is corrected without reverification | domain, application, DB, HTTP, packaged/native | 3.1–3.2, 7.5, 9.2–9.3, 13.1, 14.4 | Pending |
| M22 | Terminal records cannot be corrected | domain, application, HTTP, packaged/native | 3.2, 9.2–9.3, 13.1, 14.4 | Pending |
| M23 | Past-date evidence must be repaired coherently | domain, application, HTTP, packaged/native | 3.2, 9.2–9.3, 13.1, 14.4 | Pending |
| M24 | Identical correction still advances once | domain, application, DB, HTTP, packaged/native | 3.1–3.2, 7.5, 9.2–9.3, 13.1, 14.4 | Pending |

## M: Identifier-version preconditions and exhaustion

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M25 | Party version is not the identifier precondition | API, application, HTTP, packaged/native | 4.1, 9.1–9.3, 10.2–10.3, 11.2–11.3, 13.1, 14.4 | Partial typed-contract foundation: tasks 4.1/4.3 [verification](verification.md), commands and mismatch failures use `PartyIdentifierVersion`; known HTTP probe yields 412. Real request preconditions/races and packaged/native behavior remain pending. |
| M26 | Syntax boundaries and exhaustion are distinct | domain, API, application, HTTP, packaged/native | 3.1, 3.4, 4.3, 9.1, 9.3, 10.3, 11.3, 13.1, 14.4 | Partial Domain/translation foundation: tasks 3.1/3.4/4.3 [verification](verification.md) pin checked capacity and exact 409 identifier-version-exhausted. Version-header parsing, real routes and packaged/native cases remain pending. |
| M27 | PATCH and lifecycle race share one identifier version | coordinated DB/HTTP | 7.4–7.5, 12.1–12.2 | Pending |

## M: Strict primary-selection input and eligibility

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M28 | Only usable verified identifiers can be selected | domain, application, HTTP, packaged/native | 3.4, 11.2–11.3, 13.1, 14.4 | Pending |
| M29 | Expiration boundary and absent expiration permit selection | domain, application, HTTP, packaged/native | 3.4, 11.2–11.3, 13.1, 14.4 | Pending |
| M30 | Replacement option has a strict false default | API, fingerprint, application, HTTP, packaged/native | 4.1, 7.1, 11.1–11.3, 13.1, 14.4 | Pending |

## M: Scoped atomic primary ownership

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M31 | Different schemes have independent primaries | domain, application, DB, HTTP, packaged/native | 3.4, 7.4, 11.2–11.3, 13.1, 14.4 | Pending |
| M32 | Replacement is explicit and atomic | domain, application, DB, HTTP, packaged/native | 3.4, 7.5, 11.2–11.3, 13.1, 14.4 | Pending |
| M33 | Expired-by-date former primary is explicitly displaced | domain, application, DB, HTTP, packaged/native | 3.4, 11.2–11.3, 13.1, 14.4 | Pending |
| M34 | Concurrent first selections cannot create two primaries | coordinated DB/HTTP | 7.4–7.5, 12.2 | Pending |
| M35 | Former-primary exhaustion blocks the whole replacement | domain, application, DB, HTTP, packaged/native | 3.4, 11.2–11.3, 13.1, 14.4 | Pending |

## M: Maintenance has one durable observable outcome

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M36 | Late replacement failure restores both identifiers | transaction context, DB/HTTP fault injection | 7.7, 12.3 | Pending |
| M37 | Missing response does not duplicate creation or replacement | completion codec, DB, HTTP, two-process packaged/native | 7.2, 8.3, 11.3, 13.2, 14.4 | Pending |
| M38 | Independent Party and existing registration results are preserved | migration, DB, HTTP compatibility, packaged/native | 5.2, 7.5, 9.3, 11.3, 12.6, 13.1–13.2, 14.4 | Pending |

## L: Lifecycle requests follow the common boundary

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L01 | Bodyless verification is strict | API, HTTP including chunked input, packaged/native | 2.1, 10.1, 10.3, 13.1, 14.4 | Pending |
| L02 | Optional reasons accept absence and exact strings | API, fingerprint, audit codec/DB, HTTP, packaged/native | 4.1, 7.1, 7.3, 10.1–10.3, 13.1, 14.4 | Pending |
| L03 | Syntax precedes business and replay | API, HTTP, packaged/native | 9.1, 10.1, 10.3–10.4, 11.1, 13.1, 14.4 | Pending |
| L04 | Absence and version precede state | application, HTTP, packaged/native | 10.2–10.3, 13.1, 14.4 | Pending |

## L: Explicit identifier transition matrix

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L05 | All permitted transitions work for both Party types | domain, application, HTTP, packaged/native | 3.3, 10.2–10.3, 13.1, 14.4 | Pending |
| L06 | Every forbidden matrix entry rejects without mutation | domain, application, DB/HTTP preservation, packaged/native | 3.3, 10.2–10.3, 13.1, 14.4 | Pending |
| L07 | Repeating a completed transition without replay conflicts | domain, application, HTTP, packaged/native | 3.3, 10.2–10.4, 13.1, 14.4 | Pending |

## L: Operator-confirmed verification and validity

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L08 | Accepted user and one instant define verification | domain, application, DB, HTTP, packaged/native | 3.1, 3.3, 10.2–10.3, 13.1, 14.4 | Pending |
| L09 | UTC midnight and inclusive expiration are deterministic | domain, application, HTTP controlled clock, packaged/native | 3.3, 10.2–10.3, 13.1, 14.4 | Pending |
| L10 | Scheme withdrawal does not erase admitted evidence | application, DB, HTTP compatibility, packaged/native | 7.4, 10.2–10.3, 12.6, 13.1, 14.4 | Pending |

## L: Verification respects provisional primary ownership

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L11 | First provisional primary becomes verified | domain, application, DB, HTTP, packaged/native | 3.4, 10.2–10.3, 13.1, 14.4 | Pending |
| L12 | Conflicting provisional verification is rejected | domain, application, DB/HTTP preservation, packaged/native | 3.4, 10.2–10.3, 13.1, 14.4 | Pending |
| L13 | Nonprimary verification is independent | domain, application, DB, HTTP, packaged/native | 3.4, 10.2–10.3, 13.1, 14.4 | Pending |
| L14 | Concurrent provisional primaries have one verified winner | coordinated DB/HTTP | 7.4–7.5, 12.2 | Pending |

## L: Rejection and revocation retain safe history

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L15 | Rejection removes a provisional flag | domain, application, DB, HTTP, packaged/native | 3.3, 10.2–10.3, 13.1, 14.4 | Pending |
| L16 | Revocation retains verification and audit reason | domain, audit codec/DB, HTTP, two-process packaged/native | 3.3, 7.3, 10.2–10.3, 13.2, 14.4 | Pending |
| L17 | Past-date evidence can be withdrawn | domain, application, HTTP, packaged/native | 3.3, 10.2–10.3, 13.1, 14.4 | Pending |
| L18 | Withdrawing last evidence does not change the Party automatically | DB, HTTP compatibility, packaged/native | 10.3, 12.6, 13.1, 14.4 | Pending |

## L: Durable action key scope and request equivalence

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L19 | All effective action inputs participate in key conflicts | fingerprint, application, HTTP, packaged/native | 4.1, 7.1, 10.4, 11.3, 13.1, 14.4 | Pending |
| L20 | Default-equivalent bodies replay | API, fingerprint, application, HTTP, packaged/native | 4.1, 7.1, 10.1, 10.4, 11.1, 11.3, 13.1, 14.4 | Pending |
| L21 | Actor changes and independent scopes | fingerprint, application, DB, HTTP, packaged/native | 7.1, 7.4, 10.4, 11.3, 13.1–13.2, 14.4 | Pending |

## L: Historical action replay survives changes and restart

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L22 | Verify replay remains verified after revocation | application, completion codec, HTTP, two-process packaged/native | 7.2, 10.4, 13.2, 14.4 | Pending |
| L23 | Primary replay does not repeat demotion | application, DB/HTTP preservation, two-process packaged/native | 11.2–11.3, 13.2, 14.4 | Pending |
| L24 | Failed key is reusable and corrupt result is sanitized | completion codec, DB/HTTP fault injection, packaged/native | 7.2, 10.4, 12.3–12.4, 13.1–13.2, 14.4 | Pending |

## L: Atomic lifecycle concurrency and complete outcomes

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L25 | Equivalent concurrent actions converge | coordinated DB/HTTP, outbox | 7.4, 7.6–7.7, 12.1 | Pending |
| L26 | Competing distinct actions cannot both use one version | coordinated DB/HTTP, audit/outbox | 7.4–7.7, 12.1 | Pending |
| L27 | Late failure rolls back the whole lifecycle outcome | transaction context, DB/HTTP fault injection | 7.7, 12.3 | Pending |
| L28 | Verification feeds existing activation without changing its policy | application, DB, HTTP compatibility, packaged/native | 10.2–10.3, 12.6, 13.1, 14.4 | Pending |

## R: Initial identifier issuer metadata is canonical issuing-entity code

Source: [party-registration issuer refinement](specs/party-registration/spec.md).

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| R01 | Optional issuer and blank issuer produce no stored value | Domain, API, application, HTTP, packaged/native | 2.4, 12.6, 13.1, 14.4 | Pending |
| R02 | Lowercase issuing-entity code is canonicalized | Domain, API, application, fingerprint compatibility, HTTP, packaged/native | 2.4, 7.1, 12.6, 13.1–13.2, 14.4 | Pending |
| R03 | Invalid issuer fails before registration side effects | Domain, API, HTTP preservation, packaged/native | 2.4, 12.6, 13.1, 14.4 | Pending |

## Evidence entry convention

Replace a Pending entry with links to an increment in `verification.md` and the saved-file inventory rows. Record test class/method, command, outcome, artifact path, source blob identity, and execution mode. Preserve separate red, green, characterization, refactor, JVM, native, Sonar, and JDTLS outcomes. A single row can reference several tests. A planned boundary or passing baseline does not prove a new scenario. Record additional granular cases (controls/U+0000, maximum versions, lock collisions, cancellation, count overflow, and replay scope) within the relevant increment rather than inventing extra approved scenarios.

## Persistence-foundation evidence (tasks 5.1–5.2)

Task 5.1's `PartyIdentifierContractMigrationTest` verifies completion/audit constraints, qualified ownership and historical ordering indexes, with passing runtime and saved-revision local analysis evidence in `verification.md` and `java-inventory.md`. Task 5.2's `PartyIdentifierContractUpgradeTest` verifies V7 retention for both Party types/all identifier states, protected columns, snapshots, checksums and existing indexes; runtime passes but one Sonar finding blocks task completion. These are infrastructure prerequisites for the maintenance/replay/history scenarios, not evidence that their unimplemented HTTP/workflow/native behavior passes. Scenario acceptance entries remain pending until those boundaries execute.

Task 5.2 continuation resolves the supplied java:S5961 finding with separate data-retention and schema-history tests. The final saved revision passes focused/full checks and zero-finding Sonar/JDTLS; both persistence-foundation tasks are complete. Workflow/HTTP/native scenario acceptance still requires subsequent tasks.

## Historical query foundation evidence (tasks 6.1–6.3)

Task 6.1's cursor tests prove authenticated complete scope, precise boundaries, defaults/rotation and fail-closed token handling. Task 6.2's detail tests prove both owner types/all Party and identifier states, qualified concealment, safe scalar projection and complete nonmutation. Both tasks have focused/full/package and saved-revision zero Sonar/JDTLS evidence in `verification.md` and `java-inventory.md`.

Task 6.3's nine page tests cover exact intersections, unknown schemes, time/unsigned-ID ordering, both directions, empty outcomes, missing/changed boundaries and consistent snapshots with an independent writer. Full tests and current source analysis pass; a real packaged-container startup failure keeps its acceptance pending. These support Q history/navigation/metadata requirements at unit/database boundaries; Application/HTTP/native scenario acceptance remains pending rather than being inferred from internal adapter tests.

Task 6.3's runtime continuation reconciles that setup blocker with all 28 actual packaged JVM cases passing against a stable test-scoped Podman API. Its internal query task is now complete; public Application/HTTP/native scenario acceptance remains its subsequent work.

Task 6.4's nine port-double cases establish cursor-before-I/O and owner-before-child precedence, neutral absence, empty/safe page assembly and exact long totals, with passing full/package and zero local findings. Task 6.5's ten parser/support cases establish canonical selectors and closed exact query syntax at API unit boundaries; runtime passes but one production-parser Sonar finding keeps acceptance pending. New GET HTTP/native scenario execution remains task 6.7 and later packaged/native work.

The 6.5 concise-character-class continuation resolves its finding with zero final Sonar/JDTLS. Task 6.6 adds seven managed safe-item/page/count cases and schema-based current-projection compatibility. Task 6.7 executes 37 actual GET cases for Q ownership/history/filters/navigation/metadata/safe-envelope/context requirements, with passing full/JVM package and zero saved-source findings. These entries now supply Application/API/HTTP evidence for the query scenarios; complete all-eight-operation and required native scenario acceptance still depends on subsequent mutation/packaged/native tasks.

## Identifier completion foundation evidence (task 7.2, incomplete local gate)

`PartyIdentifierCompletionCodecTest` passes eight cases for original create/action results, exact safe allowlists, authoritative row/snapshot scope/acceptance/version coherence, detached trees and cause-free rejection of corruption. `PartyIdentifierReplayPersistenceTest` passes two reactive PostgreSQL cases for same-session joint commit, fresh-session durability, exact key/tenant/action isolation and rollback after completion flush. These provide codec/storage foundation evidence for M15/M37 and L22/L24; historical workflow/HTTP/two-process/native scenarios remain pending. Final full test/build execute 1,571 cases and 28 packaged JVM cases successfully. One saved-revision Sonar finding in the storage test prevents task completion, as documented in `verification.md` and `java-inventory.md`; internal passing tests do not close the full scenario or local quality gate.

Task 7.2 continuation resolves the developer-supplied java:S1612 finding with a method reference, fresh zero Sonar/JDTLS and passing full test/build. Its codec/storage foundation is now complete; the workflow/HTTP/restart/native scenario boundaries above remain subsequent verification.

## Private reason-audit foundation evidence (task 7.3)

Four `PartyIdentifierReasonCodecTest` cases establish independent exact big-endian bytes, null/empty/control/NUL/supplementary/unmatched-surrogate preservation, maximum bounds and cause-free corrupt decode rejection. Four `PartyIdentifierAuditPersistenceTest` cases establish fresh-session private reason/actor/time/action/version retention with outbox disabled, qualified owner constraints, accepted-audit uniqueness and rollback. Full 1,579-test/28-packaged JVM gates and all four final saved-source zero Sonar/JDTLS checks pass; see `verification.md`/`java-inventory.md`. This is codec/database foundation for L02/L16/L25–L27; actual accepted lifecycle/replay, service-process restart and native scenario boundaries remain subsequent tasks.

## Identifier locking/restoration foundation evidence (task 7.4)

Three lock-identity, three qualified-reader and four coordinated PostgreSQL cases pass. Actual waiting advisory locks and independent root/child NOWAIT probes establish replay/owner/scope/child order, forced-collision serialization without false replay, two-integer namespace independence, shared-owner locking, full persisted completion lookup after commit and fresh target version restoration. Historical target/current-primary and creation-scheme readers retain protected/audit/legacy metadata and do not increment Party versions. All five final saved Java analyses and the 1,589-test/build gates pass; see `verification.md` and `java-inventory.md`. These establish mechanics for M17/M27/M31–M35 and L13/L19–L26; guarded writes, complete Application workflows, actual mutation races and packaged/native acceptance remain subsequent tasks.

## Guarded mutation storage foundation evidence (task 7.5)

Nine `PartyIdentifierMutationPersistenceTest` cases prove qualified one-increment writes/reloads, initial insert shape, protected/creation/verification retention, stale/nonowned rejection, nullable metadata bindings, exact known value/primary conflicts, demotion-before-promotion and demotion rollback after a stale promotion. Full 1,598-test/build and both final saved-source zero Sonar/JDTLS checks pass; see `verification.md`/`java-inventory.md`. These provide storage evidence for M07/M11/M21/M25/M27/M32/M36/M38 and L08/L15–L17/L27. Full Application precedence, accepted events/audit/replay and HTTP/concurrent/native boundaries remain their later tasks.

## Identifier event codec foundation (task 7.6 complete)

Four `PartyIdentifierChangedEventCodecTest` cases and two `PartyIdentifierChangedMessageTest` cases pass for exact existing creation compatibility, five new names, safe four-field payloads and event metadata, strict corruption handling, typed broker primary flags and all three optional storage modes. Full 1,604-test/28-packaged JVM gates pass; one saved serializer Sonar finding prevents task acceptance. See `verification.md`/`java-inventory.md` for current evidence and diagnostic request. Actual mutation event assembly/counts, replay nonduplication and native/HTTP scenario acceptance remain their subsequent context/workflow checks.

The supplied java:S3824 diagnostic is now resolved by a behavior-preserving computeIfPresent refactor. Current saved serializer Sonar/JDTLS both report zero findings; focused event/outbox/architecture and final **1,604 full / 28 packaged JVM** checks pass. This completes task 7.6's codec/mode foundation; complete workflow/HTTP/native scenarios still require their designated subsequent checks.

## Sequential mutation context foundation (task 7.7, quality gate incomplete)

Eleven `HibernateReactivePartyIdentifierMutationAdapterTest` cases pass for per-subscription session ownership, replay-before-owner ordering, exact complete withdrawal audit, closed/concurrent/recovered/foreign-context guards, accepted completion/event attribution, late rollback and failed-key reuse, two-record primary effect completeness, READ COMMITTED/local deadlines, real lock/statement/work timeouts, cancellation cleanup and subsequent pool use. Full **1,615 tests / 28 packaged JVM cases** pass. All seven saved revisions have zero JDTLS problems; one current Sonar finding in `PartyIdentifierMutationJournal.java` prevents task acceptance. See `verification.md` and `java-inventory.md`. These are transaction-mechanics foundations for M/L atomic-outcome scenarios; full Application/HTTP/concurrent/native scenario acceptance remains its designated subsequent work.

The supplied java:S6213 journal diagnostic is resolved with recordAcceptedWrite; both changed revisions now have full zero Sonar/JDTLS results. All eleven adapter cases, architecture and final **1,615 full / 28 packaged JVM** checks pass again, completing task 7.7's transaction-mechanics foundation. Full mutation Application/HTTP/native scenarios remain their designated subsequent work.

## Replay-first registration workflow foundation (task 8.1, build gate incomplete)

Ten `PartyIdentifierRegistrationWorkflowTest` cases verify internal replay-before-owner/catalog/protection/time ordering, changed-input key conflicts, one lazy fingerprint and one microsecond UTC clock capture, locked-scheme identity/admission/normalized limits, both Party types, optional/inclusive expiration, provisional flags, exact safe completion/event metadata and failed-key reuse. Existing additional and initial registration plus architecture tests pass; full report contains **1,625 tests, zero failures/skips** and all four saved revisions have zero Sonar/JDTLS findings. The combined command's shell deadline interrupted final build/package acceptance, so task 8.1 remains unchecked. See `verification.md`/`java-inventory.md`. CDI rollout, public historical replay and packaged/native scenarios require tasks 8.2–8.3 and their later verification.

The authorized separate build completes in **3m 51s** with unit tests up-to-date and **28 actual packaged JVM cases** passing, resolving task 8.1's gate without another source change. Task 8.1's internal workflow foundation is complete; the public CDI rollout and full HTTP/native replay scenarios remain their designated subsequent tasks.

## Durable composition and consumer retirement (task 8.2, standalone quality gate incomplete)

CDI selects the replay-first identifier mutation/fingerprint workflow with repository-free preparation. Retired production registration port/candidate/adapter/write-sequence types have no remaining Java consumers. Existing policy/cryptography/observation/port/model checks now use current contracts, eight reactive registration regressions establish real durable creation/replay and complete effects, and the packaged flow now compares original/replayed 201 data while retaining a distinct-key 409 uniqueness assertion. Final **74 focused checks / 1,622 full tests / 28 actual packaged JVM cases** pass. All sixteen changed saved files have zero JDTLS source problems. One reactive test Sonar finding and explicit completion of an incomplete-model six-file batch keep task 8.2 unchecked; see `verification.md` and `java-inventory.md` for the coverage replacement table and exact diagnostics. Full strict binding, mutation HTTP scenarios, restart and native acceptance remain the designated later tasks.

The assertion-correction continuation supersedes the reactive test's prior source-analysis row: supplied java:S5785 is corrected with assertNotEquals, and focused/full tests plus build pass again on saved blob `95406f5557c4f14dddf8f69aa85580ed94a69479`. Current IDE output still reports one Sonar issue and one JDTLS problem with no details, in disagreement with the compiling/passing saved source. Neither that discrepancy nor the six explicit resolved-model analyses is accepted as complete; task 8.2 remains unchecked. See the current continuation in `verification.md` and `java-inventory.md`.

The developer's current unused-import diagnostic is resolved on final test blob `42f37b2768977bfa317f2f183afe490faefde8bf`: full saved-revision Sonar/JDTLS both report zero findings. Final 1,622 cases execute and pass; build succeeds up-to-date with retained actual 28 packaged JVM results. Task 8.2 is pending solely for the six explicit resolved-model analyses, not the now-resolved reactive-test assertion/import findings. See final revision evidence and log rotation in `verification.md`/`java-inventory.md`.

The developer confirms completion/no findings for all six requested individual analyses on unchanged rechecked blobs; available new resolved-model single-file logs corroborate the production files and recording double. Current source evidence is accepted in `verification.md`/`java-inventory.md`; task 8.2's composition/consumer-retirement foundation is complete. The full strict-body, historical mutation/restart and native scenarios remain their designated subsequent work.
