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
