# Implementation Tasks

## Rules and Dependency Legend

Status: proposed tasks only. `factoryctl` controls execution. All paths are repository-relative. T003 has recorded the bounded base package as `com.alexastudillo.partyregistry`, and every Java path below uses its exact filesystem form. Every task prohibits `.factory/**`, `orchestrator/**`, requirements, architecture, DBML, Git metadata, unrelated files, and test weakening unless its explicit allowed paths say otherwise. `Hard:` means execution cannot begin; `Soft:` means coordinate to avoid conflicts. T036 and T037 preserve earlier stable repairs. The current T012-triggered repair adds test-first T038 and production T039 to restore missing application data contracts before persistence implementation. Existing task IDs are not renumbered.

### T001 — Record delegated pagination technical-contract decision

Owner agent: `implementation-planning-agent` (planning disposition complete; not a downstream executable task)
Purpose: Resolve inherited RG-401 using the bounded downstream authority explicitly delegated by the requirements-gate PASS, without editing historical approved artifacts.
Requirements: FR-023/031/036; VR-007; AC-028/033/041/047/081.  
Architecture: PRS-ARCH-001 sections 10/21 AR-007. DBML: none.  
Inputs: requirements-gate RG-401 delegation, BR-029, AC-081, Amendment 002. Allowed paths: `docs/planning/implementation-plan.md`, `docs/planning/tasks.md`. Prohibited paths: requirements, architecture, DBML, runtime source, tests and factory state.
Expected artifacts: TC-001 in `implementation-plan.md`. Dependencies: none. Parallelizable: false; completed during planning.
Instructions: Adopt zero-based page default 0, size default 20/range 1..100, ascending UUID-only V1 order, and effective Party/nationality/Party-Identifier filter semantics; exclude superseded Scheme search and classify invalid bounds/filter/sort as `VALIDATION_ERROR`. Leave reversible parameter spelling/serialization to T003 and API-gate validation.
Acceptance/verification: TC-001 distinguishes delegated technical authority from product authority, preserves controlling invariants, and T003/T027 cite it.
Risks/stop: stop if implementation changes business search scope or restores Scheme API behavior; that exceeds TC-001.

### T002 — Create issue and implementation branch

Owner agent: `github-flow-agent`  
Purpose: Establish governed GitHub Flow container for the functional change.  
Requirements: manifest GitHub policy; all implementation obligations. Architecture: scope/non-goals. DBML: reference only.  
Inputs: this plan and completed TC-001/T001. Allowed paths: Git/GitHub metadata only under agent authority. Prohibited paths: product artifact edits.
Expected artifacts: issue and `feature/<issue-number>-party-registry-v1` branch from verified default branch. Dependencies: Hard T001. Parallelizable: false.  
Instructions: Use suggested title/scope; include no-auth risk, DBML authority, exclusions, gates and human merge controls.  
Acceptance/verification: issue/branch URLs/names and base evidence; no commit, PR, merge or automerge yet.  
Risks/stop: stop on wrong repository/account/base, dirty-scope ambiguity, or request to invent issue number.

### T036 — Add repository-controlled contract validation harness

Owner agent: `quarkus-engineer-agent`
Purpose: Supply the missing executable prerequisite that lets T003 prove OpenAPI 3.1 parsing/reference resolution and JSON Schema 2020-12 schema/example validity without weakening T003 acceptance.
Requirements: IR-002..005; CR-002/004; NFR-004/007; API/event acceptance criteria assigned to T003.
Architecture: sections 5.1/10/12/19; build-time validation only, with no runtime-boundary change. DBML: not applicable.
Inputs: T002 branch evidence, `.factory/runs/oc-d1f62086-42fe-436f-8f7e-0b1eb129c3f0/result.json`, current `build.gradle.kts`, T003 contract locations, and the pinned repository/BOM baseline. Allowed paths: `build.gradle.kts`, `gradle.properties`, `src/test/java/com/alexastudillo/partyregistry/contractvalidation/**`, `src/test/resources/contract-validation/**`, `docs/implementation/contract-validation.md`. Prohibited paths: `api/**`, production source/resources, runtime dependencies/classpaths, approved requirements/architecture/DBML, version drift, package installation, and factory/Git metadata.
Expected artifacts: minimal build/test-scoped validator declarations; a repository-executable validation harness; synthetic valid and intentionally invalid self-test fixtures; a short dependency/command record. Dependencies: Hard T001/T002. Parallelizable: false; it modifies the shared build and must finish before T003/T005.
Instructions: Select the minimum maintained OpenAPI 3.1 and JSON Schema Draft 2020-12 validation capability as a bounded implementation decision, preferring existing BOM alignment where available and pinning any non-BOM build-time version from repository evidence. The harness must fail on malformed OpenAPI, unresolved `$ref`, invalid JSON Schema, and an event example that violates the catalog schema; it must discover and validate every example under the configured event-example path. Keep validators off production/runtime classpaths, add no code generator, generated server/client, runtime framework, network service, or contract semantics. Record why each dependency is required and the exact offline-reproducible Gradle command once dependencies are resolved through approved repositories.
Acceptance/verification: Self-tests prove both positive and negative detection; dependency inspection proves no validator on production runtime classpaths; the harness can be invoked by T003 against `api/openapi/v1/party-registry.openapi.yaml`, `api/events/v1/party-registry-events.schema.json`, and all `api/events/v1/examples/*.json`; exact commands and exit codes are recorded.
Risks/stop: stop if full OpenAPI 3.1/reference or JSON Schema 2020-12/example validation cannot be supported, a dependency is vulnerable/unlicensed/unpinned, validation requires external network service or package installation, or tooling leaks into runtime. Return a bounded implementation failure rather than accepting manual-only validation.

### T003 — Establish contract and foundation placement

Owner agent: `quarkus-engineer-agent`  
Purpose: Define contract-first OpenAPI/event artifacts and record minimal package/path placement before adapters.  
Requirements: effective FR-001..050, VR-001..018, IR-001..005, CR-002/004; Amendments 001/002.  
Architecture: sections 6/10/12; ADR-002/003/005. DBML: names only where contract semantics require them; no physical leakage.  
Inputs: TC-001/T001, approved sources, T036 harness, and existing contract/placement artifacts preserved from prior T003 attempts. Allowed paths: `api/openapi/**`, `api/events/**`, `docs/implementation/contract-placement.md`. Prohibited paths: source, tests, build, DBML, Scheme administration/idempotency contracts.
Expected artifacts: versioned OpenAPI and event schemas; synthetic examples; placement record; T036 validation evidence for the OpenAPI document, resolved references, event schema, and every event example. Dependencies: Hard T001/T002/T036. Parallelizable: false; stabilizes shared contracts.
Instructions: Preserve existing generated artifacts unless T036 demonstrates a defect, and correct only demonstrated contract defects. Define approved operations/context/ETag/errors/TC-001 pagination/decryption no-store and event catalog/security/versioning; choose and document reversible query-parameter names/serialization; exclude Scheme/outbox administration, 401/403, `Idempotency-Key`, plaintext events, and stack traces. Retain the recorded repository paths/base package unless verified conflict requires returning control. Execute the T036 harness; do not substitute manual inspection or `test NO-SOURCE` for validation.
Acceptance/verification: every operation maps to requirement/AC; OpenAPI 3.1 parses with all references resolved; event schema validates as JSON Schema 2020-12; every event example validates against the catalog; compatibility and exclusion inventory exists; exact validation commands and exit codes are recorded.
Risks/stop: stop on missing/unusable T036 tooling, unresolved reference, invalid example/schema, TC-001 semantic drift, invented business behavior, sensitive example, or contract/requirement conflict.

### T004 — Implement contract tests before REST/event adapters

Owner agent: `test-implementation-agent`  
Purpose: Encode observable API/event behavior and prohibited surfaces before implementation.  
Requirements: AC-001, 004, 007..010, 014, 019..023, 027..030, 032..062, 067..081 as effectively amended.  
Architecture: sections 10/12/13/19. DBML: no direct schema assertions except externally observable constraint outcomes.  
Inputs: T003 contracts. Allowed paths: `src/test/**/contract/**`, `src/test/resources/contracts/**`. Prohibited paths: production/build/contracts.  
Expected artifacts: failing contract/API/event tests and fixture catalog. Dependencies: Hard T003. Parallelizable: true after contract freeze; no overlap with build/core tests.  
Instructions: Cover status/envelope/headers/context/tenant non-disclosure/masks/events and explicit absence of Scheme/outbox/idempotency/auth surfaces using synthetic data.  
Acceptance/verification: tests fail only because adapters are absent; requirement-to-test map is complete.  
Risks/stop: stop if test requires behavior not in effective sources or exposes plaintext.

### T005 — Add minimal governed build capabilities

Owner agent: `quarkus-engineer-agent`  
Purpose: Add only BOM-aligned capabilities required by approved architecture and tests.  
Requirements: NFR-004..007; IR-005; OR-006. Architecture: section 5.1; ADR-001. DBML: Flyway/PostgreSQL only.  
Inputs: pinned Quarkus 3.33.3, profiles, T003 placement, and T036 build baseline. Allowed paths: `build.gradle.kts`, `gradle.properties`, `settings.gradle.kts`, `gradle/**`, `gradlew`, `gradlew.bat`. Prohibited paths: version upgrades/build-tool replacement/unapproved libraries or removal/weakening of T036 validation.
Expected artifacts: minimal dependency/tool declarations for reactive REST/OpenAPI/validation/reactive PostgreSQL/Flyway/RabbitMQ/health/metrics/JUnit/Testcontainers/ArchUnit/JaCoCo as justified. Dependencies: Hard T003/T036; Soft serialize all later build edits here. Parallelizable: false; shared bottleneck.
Instructions: Prefer approved Quarkus capabilities and BOM; document each addition; do not add Redis, ORM/JDBC runtime, auth, external logging/KMS, resilience convenience libraries, modules, or speculative clients.  
Acceptance/verification: dependency report/build metadata resolves reproducibly; lock/SBOM impact documented; no unrelated upgrade.  
Risks/stop: stop if a capability needs unapproved technology/version or network installation outside normal governed build resolution.

### T006 — Write pure domain tests

Owner agent: `test-implementation-agent`  
Purpose: Specify pure business behavior before domain code.  
Requirements: BR-002..010, 015, 018, 020, 022, 023, 025, 027 as amended; AC-002..010, 017, 024, 035, 043, 049/050, 067, 069/070, 072/073.  
Architecture: section 6 domain boundary. DBML: enum/state/value constraints as contract facts.  
Inputs: approved sources. Allowed paths: `src/test/java/com/alexastudillo/partyregistry/domain/**`. Prohibited paths: production/infrastructure mocks.
Expected artifacts: deterministic parameterized/property-style domain tests. Dependencies: Hard T005. Parallelizable: true with T008 after stable package placement.  
Instructions: Cover type/detail, dates, nationality, lifecycle matrices, immutability, display-name whitespace, mask boundaries, Scheme compatibility and domain errors; use clock values explicitly.  
Acceptance/verification: tests compile and fail for missing domain behavior, with no Quarkus/Testcontainers dependency in test logic.  
Risks/stop: stop on any inferred business rule or superseded Scheme/idempotency behavior.

### T007 — Implement framework-independent domain

Owner agent: `quarkus-engineer-agent`  
Purpose: Satisfy T006 with pure domain models/policies/errors.  
Requirements/architecture/DBML: same as T006; architecture guardrails 1 and 10.  
Inputs: T006. Allowed paths: `src/main/java/com/alexastudillo/partyregistry/domain/**`. Prohibited paths: Quarkus, Jakarta, Mutiny, REST/JSON, persistence, logging, config, crypto-provider imports.
Expected artifacts: entities/value objects/policies/errors only. Dependencies: Hard T006. Parallelizable: false with T006; true relative to T010.  
Instructions: Implement minimum deterministic behavior; no persistence annotations, transport DTOs, schedulers or domain events beyond approved integration intent.  
Acceptance/verification: T006 passes; import/cycle checks pass.  
Risks/stop: stop on framework leakage or requirement ambiguity.

### T008 — Write application use-case and port tests

Owner agent: `test-implementation-agent`  
Purpose: Define orchestration, transaction intent and boundary behavior before application code.  
Requirements: FR-001..039, 046..050 as amended; VR-006..018; AC mappings; no Scheme mutation.  
Architecture: sections 6.2/11/12/15; ADR-001..005. DBML: transaction/version/uniqueness/outbox obligations.  
Inputs: T003, T007. Allowed paths: `src/test/java/com/alexastudillo/partyregistry/application/**`. Prohibited paths: production, concrete adapters, Quarkus test context.
Expected artifacts: use-case tests with boundary fakes/spies. Dependencies: Hard T003/T007. Parallelizable: true with T010.  
Instructions: Cover context, tenant scoping, If-Match precedence, geography-before-transaction, mutation+outbox intent, read-only Scheme catalog, protection, exact lookup, log-before-decrypt-return, failure unchanged, and outbox claim/outcome orchestration.  
Acceptance/verification: tests prove call ordering and outcomes independently of infrastructure; prohibited port inventory asserted.  
Risks/stop: stop if a concrete adapter or remote call is needed to define application behavior.

### T009 — Implement application ports and use cases

Owner agent: `quarkus-engineer-agent`  
Purpose: Satisfy T008 while preserving inward dependencies.  
Requirements/architecture/DBML: same as T008; LikeC4 application-owned ports.  
Inputs: T007/T008. Allowed paths: `src/main/java/com/alexastudillo/partyregistry/application/**`. Prohibited paths: adapter implementations, REST DTOs/resources, persistence entities, SQL, Quarkus/Jakarta/logging/config/deployment APIs.
Expected artifacts: commands/queries/results, input/output ports, use cases, boundary errors and transaction intent. Dependencies: Hard T008. Parallelizable: false with T008; true relative to completed migration.  
Instructions: Use reactive boundary types consistent with ADR-001 without manual subscription; define query-only Scheme port and no idempotency/Scheme mutation port; order external evidence before transaction and decryption logging before disclosure.  
Acceptance/verification: T008 passes; application imports domain/core contracts only.  
Risks/stop: stop on adapter leakage, unbounded concurrency, remote I/O transaction overlap or plaintext-bearing telemetry model.

### T038 — Specify complete nationality and Party Identifier application data flow

Owner agent: `test-implementation-agent`
Purpose: Add the missing test-first acceptance contract proving that application commands, mutation intents, use cases, and query views preserve every approved nationality and Party Identifier fact needed by OpenAPI and PostgreSQL before T012 is retried.
Requirements: FR-003/006/012/017/018/021/030..039/046/047; VR-004/006/009; DR-003/004/005; AC-005/008/009/011/020/031/039..050/070/074..076.
Architecture: sections 6.2/8/11/19; application owns boundary-neutral ports and orchestration, while transport and persistence models remain outside. DBML: `party_nationalities.is_primary`; `party_identifiers` issuer, primary, status, lifecycle, verification, audit and version facts plus `ck_party_identifier_verification` and `ck_party_identifier_expired_date`.
Inputs: completed T008/T009 artifacts, frozen OpenAPI, final DBML, and triggering implementation result `.factory/runs/oc-84e20a66-8268-4c52-84df-7fc1748268da/result.json`. Allowed paths: `src/test/java/com/alexastudillo/partyregistry/application/**`, `docs/implementation/application-persistence-field-map.md`. Prohibited paths: production, adapters, build, contracts, migrations, approved sources and factory/Git metadata.
Expected artifacts: failing application tests using port fakes/spies; an OpenAPI/DBML-to-command/intent/view/use-case field map covering each repaired fact. Dependencies: Hard T009 and triggering T012 failure evidence. Parallelizable: true with T013/T015/T017 because scopes do not overlap; false with T039.
Instructions: Prove add/update nationality propagates `primary`; identifier creation propagates `issuerCode`, `primary`, `issuedOn` and `expiresOn` with protected value and immutable associations; status transition propagates accepted status plus applicable `verifiedAt`, `verifiedBy` and `expiresOn`; identifier query views can carry the approved persisted status, primary, issuer, issue/expiry, verification, audit and version facts without exposing plaintext in ordinary results. Cover nullable versus required semantics exactly from OpenAPI/DBML. Assert unchanged transaction intent, tenant/context, optimistic version, outbox identity, immutability and no Scheme mutation/idempotency. Do not prescribe adapter constructors, SQL, transport DTOs or persistence records.
Acceptance/verification: targeted tests compile and fail only on the identified T009 omissions; every finding from the triggering T012 result maps to at least one meaningful assertion and one field-map row; no unrelated application behavior is reopened; exact commands/exit codes are recorded.
Risks/stop: stop if OpenAPI and DBML conflict, a requested fact lacks approved authority, a test requires adapter/transport leakage, or a fixture contains real personal data. Do not solve ambiguity with placeholder values.

### T039 — Repair application contracts for complete persistence data

Owner agent: `quarkus-engineer-agent`
Purpose: Satisfy T038 by making the minimum boundary-neutral application changes required for T012 to implement valid DBML/OpenAPI behavior without inventing values.
Requirements/architecture/DBML: same as T038; preserve all T008/T009 orchestration and strict Clean Architecture obligations.
Inputs: T038 failing tests/field map and existing T009 production artifacts. Allowed paths: `src/main/java/com/alexastudillo/partyregistry/application/**`, `docs/implementation/application-persistence-field-map.md`. Prohibited paths: domain, adapters, tests, OpenAPI/events, migrations, build/configuration, requirements/architecture/DBML and factory/Git metadata.
Expected artifacts: repaired boundary-neutral command/input records, `NationalityMutationIntent`, `IdentifierMutationIntent` or typed mutation data, `PartyIdentifierView`, affected use cases and port-facing contracts; completed field-flow map. Dependencies: Hard T038. Parallelizable: false with T038 or any task editing application production contracts; adapters remain blocked until completion.
Instructions: Carry nationality primary through add/update intents; replace any lossy identifier creation signature with a boundary-neutral command carrying issuer/primary/issue/expiry facts; preserve protected data and immutable Party/Scheme/value associations; replace untyped or lossy mutation payloads only as narrowly needed so update and transition retain all approved metadata; propagate verification/expiry facts after domain lifecycle validation; make query views represent the approved persisted/output facts while ensuring ordinary API mapping can exclude ciphertext/plaintext. Preserve reactive return types, pre-transaction geography, log-before-disclosure, tenant qualification, If-Match semantics and outbox event identity. Do not add persistence annotations, SQL, Quarkus/Jakarta/logging imports, adapter constructors, new business defaults, Scheme administration or idempotency.
Acceptance/verification: all T038 and pre-existing T008 application tests pass; field-map rows identify the final application carrier and nullability for every repaired fact; contract validation remains green; import/boundary checks pass; changed-file inventory is confined to allowed paths. Then rerun T011 before T012.
Risks/stop: stop on public-contract or DBML mismatch, need for a new business default, transport/persistence leakage, loss of protected-data separation, changed event meaning, or inability to represent a DBML-valid transition. Return control rather than putting placeholders in an adapter.

### T010 — Generate the initial forward-only Flyway migration

Owner agent: `database-migration-agent`  
Purpose: Translate validated final DBML exactly into the next immutable migration.  
Requirements: DR-001..009, CR-005; Amendments 001/002. Architecture: sections 7/8/11/18; ADR-002/004/005.  
DBML: all enums, tables, references, checks, indexes, notes and named PostgreSQL mechanisms in `docs/database/v1-scheme.dbml`.  
Inputs: database-contract PASS and current migration inventory. Allowed paths: `src/main/resources/db/migration/**`, `docs/database/migration-mapping.md`. Prohibited paths: DBML, existing applied migrations, application source/tests/build.  
Expected artifacts: one or more correctly ordered immutable migrations and object-by-object mapping; final version selected only after inventory. Dependencies: Hard T005; database gate already PASS. Parallelizable: true with T008/T009 due non-overlapping scope.  
Instructions: Include UUIDv7 mechanism compatibility, exact objects/checks/defaults/comments/FKs/delete restrictions, deferred detail function/triggers, partial indexes, unconditional hash uniqueness, and least-privilege grants with separate runtime/migration roles. Analyze lock/downtime/backfill/upgrade/failure; do not generate destructive rollback or Scheme seed data absent authority.  
Acceptance/verification: clean PostgreSQL 18 and supported prior-schema validation plan, representative integrity cases, DBML mapping with zero omissions, no runtime auto-DDL.  
Risks/stop: stop on DBML ambiguity/conflict, extension requirement not authorized, existing violating data, destructive remediation, or uncertain applied version.

### T011 — Write PostgreSQL migration, integrity and adapter-presence tests

Owner agent: `test-implementation-agent`  
Purpose: Prove every DBML/Flyway obligation, PostgreSQL integrity/privilege/concurrency behavior, and the presence of implementations for all six persistence ports without guessing a not-yet-defined concrete adapter constructor.
Requirements: DR-001..009; NFR-001/002; effective AC-001..012, 016/017, 031, 061, 074..079. Architecture: persistence and transaction guardrails.  
DBML: every physical object and normative integration-test note.  
Inputs: T010/T039 and the existing T011 suite. Allowed paths: `src/test/java/com/alexastudillo/partyregistry/adapter/out/postgresql/**`, `src/test/resources/database/**`. Prohibited paths: production/migrations/build.
Expected artifacts: PostgreSQL 18 Testcontainers migration/schema/privilege/transaction/concurrency tests, constraint-error mapping fixture, and a deterministic implementation-presence check. Dependencies: Hard T010/T039. Parallelizable: true with T013/T015/T017 after repaired ports are stable.
Instructions: Cover both detail insertion orders and all invalid commit states; partial uniqueness/history; uppercase hash check; permanent/cross-scope/concurrent identifier uniqueness; verified primary; tenant FKs; versions; atomic outbox; `SKIP LOCKED`; runtime Scheme SELECT and denied writes; constraint error mapping fixtures. Keep the presence check limited to assignability to PartyQueryPort, PartyUnitOfWorkPort, IdentifierQueryPort, IdentifierUnitOfWorkPort, IdentifierSchemeCatalogPort and OutboxStorePort. Do not invent constructor/factory expectations or claim black-box port behavior; T037 owns that evidence after T012.
Acceptance/verification: after T039 and before T012, rerun the suite: schema/integrity tests pass in an authorised environment able to start the pinned PostgreSQL 18 Testcontainers image and the presence check fails only because the adapter is absent; after T012, the presence check passes. Exact commands/exit codes and DBML object coverage are recorded. Socket permission denial is an environment blocker and never grounds for disabling/skipping tests.
Risks/stop: stop if the container runtime cannot start the pinned image, a fixture requires real personal data, a test alters DBML/migration, or the task would infer a concrete construction API before T012.

### T012 — Implement reactive PostgreSQL adapter

Owner agent: `quarkus-engineer-agent`  
Purpose: Implement application persistence/unit-of-work/outbox-store/query-only Scheme ports.  
Requirements: FR-001/006..009/012/013/021..039/047..050; DR-001..006. Architecture: sections 6/7/11/12; ADR-002/004/005.  
DBML: exact tables/constraints/queries; no redesign. Inputs: T010/T038/T039 and revalidated T011.
Allowed paths: `src/main/java/com/alexastudillo/partyregistry/adapter/out/postgresql/**`, `src/main/resources/application.properties` (database/Flyway keys only), `docs/implementation/postgresql-adapter.md`. Prohibited paths: domain/application edits, test edits, blocking ORM/JDBC, runtime auto-DDL, Scheme mutation methods.
Expected artifacts: persistence records/mappers/reactive queries/transactions/error mapping and bounded pages/claims; implementations of all six ports named by T011; and a concise construction/test-fixture contract documenting the concrete constructor or factory, required synthetic non-secret configuration, lifecycle and cleanup needed by T037. Dependencies: Hard T039/T010/revalidated T011. Parallelizable: false with tasks changing `application.properties`; otherwise isolated.
Instructions: Tenant-qualify reads/mutations; map domain separately; enforce optimistic versions; atomically update aggregate/outbox; use parameterized SQL; implement claim commit before broker I/O and optimistic outcome; bound page/batch; expose Scheme queries only. Define the narrowest stable construction seam needed for independent black-box testing without adding a production-only test abstraction, leaking persistence models, or requiring T037 to use reflection into private internals.
Acceptance/verification: T011 migration/integrity and presence checks pass in the approved PostgreSQL environment; construction contract is sufficient for T037; transaction-overlap instrumentation is available; no persistence model leaks inward. Full black-box persistence-port behavior is accepted only after T037.
Risks/stop: stop on SQL/DBML mismatch, any still-missing approved application carrier, need to invent placeholder/default metadata, cross-tenant result, blocking access, unbounded query, Scheme write, stale-outcome overwrite, inaccessible mandatory database tests, or inability to expose a bounded construction seam within this adapter scope.

### T037 — Verify PostgreSQL persistence ports through the concrete adapter

Owner agent: `test-implementation-agent`
Purpose: Restore the mandatory independent persistence-port behavioral coverage that T011 could not author before a concrete adapter construction contract existed.
Requirements: FR-001/006..009/012/013/021..039/047..050; DR-001..006; NFR-001/002; effective AC-001/008/011/012/016/020/021/031/032/040/045/046/074..080.
Architecture: sections 6/7/11/12 and ADR-002/004/005; tenant-qualified reactive ports, query-only Scheme catalog and three-boundary outbox flow. DBML: exact tables, named constraints, indexes, privileges and optimistic versions.
Inputs: completed T011 artifacts, T012 concrete implementation and `docs/implementation/postgresql-adapter.md`. Allowed paths: `src/test/java/com/alexastudillo/partyregistry/adapter/out/postgresql/**`, `src/test/resources/database/**`. Prohibited paths: production, migrations, build, application ports, approved sources, factory/Git metadata and test weakening.
Expected artifacts: black-box PostgreSQL 18 Testcontainers tests that instantiate the T012 adapter only through its documented construction seam and exercise all six persistence ports; requirement/port-to-test mapping. Dependencies: Hard T011/T012. Parallelizable: true with T014 after T012, but false with any task editing the same PostgreSQL test files or shared database fixture.
Instructions: Verify tenant-qualified not-found/non-disclosure, deterministic bounded pages and filters, domain/persistence mapping separation, atomic aggregate-version/audit/outbox mutations, permanent identifier uniqueness and safe named-constraint/SQLSTATE mapping, read-only Scheme lookup and denied mutation capability, exact HMAC lookup without scan/decrypt, bounded `SKIP LOCKED` claim commit before broker I/O, optimistic stale-outcome rejection and same-event recovery. Exercise the public application port interfaces; do not assert private class layout, duplicate T011's full schema inventory, add reflection-only access, or modify production to make assertions pass.
Acceptance/verification: every method of PartyQueryPort, PartyUnitOfWorkPort, IdentifierQueryPort, IdentifierUnitOfWorkPort, IdentifierSchemeCatalogPort and OutboxStorePort has meaningful success, boundary and applicable failure coverage; all targeted tests pass against the pinned PostgreSQL 18 image with exact commands/exit codes; no disabled tests, real personal data, blocking implementation assumption, cross-tenant leakage or Scheme mutation method exists.
Risks/stop: stop if T012 lacks the documented construction seam, Testcontainers cannot access an authorised container runtime, observed behavior conflicts with application ports/DBML, a test would require production redesign outside T012, or sensitive data could be exposed. Return the finding to T012 or the execution environment; do not weaken acceptance.

### T013 — Write protection, logging and geography adapter tests

Owner agent: `test-implementation-agent`  
Purpose: Define external adapter security/failure semantics before implementation.  
Requirements: FR-005/013..015/046/048/049; VR-011..014; SR-001..007; AC-010/021..023/059..062/071..073/077..079.  
Architecture: sections 13-15; ADR-003. DBML: protected fields/hash representation.  
Inputs: T009. Allowed paths: `src/test/java/com/alexastudillo/partyregistry/adapter/out/{protection,logging,geography}/**`, `src/test/resources/integration/**`. Prohibited paths: production/build/secrets/real data.
Expected artifacts: crypto vectors using synthetic values, log ordering/redaction tests, provider/cache/fault tests. Dependencies: Hard T009. Parallelizable: true with T011/T015/T017.  
Instructions: Cover tenant-separated HMAC, authenticated-decryption failure, key versions/missing keys, exact masks, no plaintext telemetry, synchronous logger failure, required fields, TTL/stale/cold cache/malformed provider and transaction non-overlap.  
Acceptance/verification: tests fail for absent adapters only; no secret value or real identifier stored.  
Risks/stop: stop if cryptographic algorithm/provider detail lacks approved security basis or tests print sensitive values.

### T014 — Implement protection, local logging and Geographic adapters

Owner agent: `quarkus-engineer-agent`  
Purpose: Satisfy T013 through bounded outbound adapters.  
Requirements/architecture/DBML: same as T013. Inputs: T012/T013.  
Allowed paths: `src/main/java/com/alexastudillo/partyregistry/adapter/out/{protection,logging,geography}/**`, `src/main/resources/application.properties` (non-secret names/bounds only). Prohibited paths: committed secrets, remote KMS/logger, durable/distributed cache, business policy.
Expected artifacts: reactive provider client/cache, secret-backed protection adapter, local security-log adapter. Dependencies: Hard T012/T013. Parallelizable: false where shared config overlaps.  
Instructions: Use approved runtime injection, separate masters/tenant derivation, fail closed without fallback, bounded CPU isolation and provider timeout; keep remote geography outside state transaction; never log protected values.  
Acceptance/verification: T013 and transaction-overlap tests pass; event-loop safety/redaction evidence exists.  
Risks/stop: stop on secret exposure, unauthenticated ciphertext acceptance, remote call in transaction, or invented external service.

### T015 — Write RabbitMQ/outbox publication tests

Owner agent: `test-implementation-agent`  
Purpose: Specify confirmed at-least-once publication and recovery before adapter code.  
Requirements: FR-008/009/050; VR-015..018; IR-002..004; NFR-001..003/009; AC-012..014/016/029/063..066/080.  
Architecture: section 12; ADR-002. DBML: `party_outbox_events`.  
Inputs: T003/T009/T010. Allowed paths: `src/test/java/com/alexastudillo/partyregistry/adapter/out/messaging/**`, `src/test/resources/events/**`. Prohibited paths: production/migrations/public recovery API.
Expected artifacts: contract/integration/fault/concurrency/restart tests. Dependencies: Hard T003/T009/T010. Parallelizable: true with T011/T013/T017.  
Instructions: Assert bounded claim commit/lock release before broker I/O, persistent publish/confirm, same ID, transient/unknown PENDING, nonrecoverable FAILED, stale outcome rejection, crash recovery, no publisher DLQ/purge and minimal payload.  
Acceptance/verification: tests fail for absent publisher only and distinguish every outcome class.  
Risks/stop: stop on irreversible retry, new event ID, open DB transaction during broker wait, or unbounded concurrency.

### T016 — Implement RabbitMQ publisher and recovery boundary

Owner agent: `quarkus-engineer-agent`  
Purpose: Satisfy T015 using application event publisher/outbox ports.  
Requirements/architecture/DBML: same as T015. Inputs: T012/T015/T037.
Allowed paths: `src/main/java/com/alexastudillo/partyregistry/adapter/out/messaging/**`, `src/main/resources/application.properties` (bounded non-secret settings only). Prohibited paths: consumer queue/DLQ management, public outbox endpoints, new persistence fields.
Expected artifacts: reactive publisher adapter and internal authorised recovery wiring boundary. Dependencies: Hard T012/T015/T037. Parallelizable: false with shared config/bootstrap tasks.
Instructions: Preserve persistent messages, confirms, timeouts, bounded batch/concurrency, configurable backoff/jitter, same-event identity and safe errors; never cap durable eligibility with an unapproved discard limit.  
Acceptance/verification: T015 passes; lag/backlog metrics hooks exist; no manual subscription/event-loop blocking.  
Risks/stop: stop on missing confirm, automatic discard/purge/DLQ, duplicate replacement event or transaction overlap.

### T017 — Write reactive REST and bootstrap tests

Owner agent: `test-implementation-agent`  
Purpose: Verify OpenAPI conformance, context, error mapping and prohibited surfaces before REST implementation.  
Requirements: IR-005; FR/VR/API ACs in T004; NFR-006/011; SR-006. Architecture: sections 6/10/17. DBML: none directly.  
Inputs: T003/T004/T009. Allowed paths: `src/test/java/com/alexastudillo/partyregistry/adapter/in/rest/**`, `src/test/java/com/alexastudillo/partyregistry/bootstrap/**`. Prohibited paths: production/contracts/build.
Expected artifacts: reactive endpoint, context propagation/cleanup, ETag/no-store, readiness and API-inventory tests. Dependencies: Hard T003/T004/T009. Parallelizable: true with other adapter test tasks.  
Instructions: Assert canonical headers/generated process ID, tenant-before-data, envelope/error precedence, pagination decision, masks, decryption headers, no 401/403, no Scheme/outbox/idempotency endpoints and no persistence injection.  
Acceptance/verification: tests fail only for absent inbound/bootstrap behavior; contract operation coverage complete.  
Risks/stop: stop on contract drift, business logic in resource tests, or public/no-auth ambiguity.

### T018 — Implement reactive REST adapter and runtime composition

Owner agent: `quarkus-engineer-agent`  
Purpose: Implement T004/T017 and wire approved ports/adapters in bootstrap only.  
Requirements/architecture/DBML: same as T017 plus architecture section 6.3. Inputs: T012/T014/T016/T017.  
Allowed paths: `src/main/java/com/alexastudillo/partyregistry/adapter/in/rest/**`, `src/main/java/com/alexastudillo/partyregistry/bootstrap/**`, `src/main/resources/application.properties`. Prohibited paths: business policy, direct DB/client access in REST, Scheme/outbox/idempotency/auth endpoints.
Expected artifacts: DTOs/mappers/resources/context filters/error mapper/composition/readiness. Dependencies: Hard T012/T014/T016/T017. Parallelizable: false; integration bottleneck.  
Instructions: Keep DTOs at boundary; invoke input ports only; propagate/clear context reactively; map safe errors/ETags; configure loopback-ready runtime behavior without embedding environment secrets.  
Acceptance/verification: T004/T017 pass; OpenAPI implementation comparison clean; event-loop and context tests pass.  
Risks/stop: stop on adapter-defined business rule, plaintext logging, concrete adapter dependency in core or contract mismatch.

### T019 — Implement security, telemetry, health and architecture checks

Owner agent: `quarkus-engineer-agent`  
Purpose: Complete cross-cutting controls without changing the approved no-auth posture.  
Requirements: SR-001..007; OR-006; NFR-005/006/011. Architecture: sections 13/17/19/20; ADR-001/003/005. DBML: Scheme privilege/readiness facts.  
Inputs: T018. Allowed paths: `src/main/java/com/alexastudillo/partyregistry/bootstrap/**`, `src/main/java/com/alexastudillo/partyregistry/adapter/**/telemetry/**`, `src/test/java/com/alexastudillo/partyregistry/architecture/**`, `src/main/resources/application.properties`. Prohibited paths: auth libraries, external logging platform, audit store, sensitive labels.
Expected artifacts: liveness/readiness, structured safe telemetry, ArchUnit suite, blocking/context checks. Dependencies: Hard T018. Parallelizable: false where bootstrap/config overlaps; docs can follow independently.  
Instructions: Separate telemetry from audit; expose required rates/latencies/pools/cache/outbox/confirm/readiness reasons; prohibit sensitive values and enforce all architecture guardrails including no Scheme mutation/idempotency.  
Acceptance/verification: architecture/security telemetry tests pass; readiness/failure reasons are non-sensitive; liveness tolerates degradable dependencies.  
Risks/stop: stop on accidental auth/401/403, high-cardinality sensitive metric, logger dependency in core or missing mandatory readiness failure.

### T020 — Update implementation and operational documentation

Owner agent: `documentation-agent`  
Purpose: Align developer, API, security, migration, recovery and operations guidance with actual implementation.  
Requirements: OR-001..009; NFR-010; SR-002..007; CR-002..005. Architecture: sections 13-17. DBML: final mapping/read-only Scheme privileges.  
Inputs: T003/T010/T018/T019. Allowed paths: `README.md`, `docs/implementation/**`, `docs/operations/**`, `docs/security/**`, `docs/database/migration-guide.md`. Prohibited paths: approved requirements/architecture/DBML, source/tests/deployment.  
Expected artifacts: configuration reference using secret names only, migration/rollback limits, outbox recovery, backup/restore/reconciliation, troubleshooting and API/event links. Dependencies: Hard T019; T010. Parallelizable: true with T021 after implementation stabilizes.  
Instructions: State no-auth residual risk, loopback-only requirement, no audit durability claim, no DB rollback, same-digest promotion and human merge control; never include secret values or production details.  
Acceptance/verification: links/commands/path references validate; docs match observed configuration/contracts.  
Risks/stop: stop if operational owner/recurrence/threshold is unknown—mark pending rather than invent.

### T021 — Validate architecture and LikeC4 mechanically

Owner agent: `test-implementation-agent`  
Purpose: Produce executable strict-boundary and LikeC4 validation evidence before quality gates.  
Requirements: NFR-005/007; AC-000. Architecture: model and guardrails; CAG-301. DBML: no change.  
Inputs: T019, approved architecture bytes. Allowed paths: `src/test/java/com/alexastudillo/partyregistry/architecture/**`, `docs/implementation/architecture-validation.md`; build path only through a separately scoped T005 repair if tooling is absent. Prohibited paths: architecture source semantic edits.
Expected artifacts: ArchUnit results and repository-controlled LikeC4 parse/validate/render procedure/results. Dependencies: Hard T019. Parallelizable: true with T020.  
Instructions: Verify no cycles/leaks, no Scheme administration/idempotency, reviewed model parses and required views render; syntax correction requires architecture authority, not this task.  
Acceptance/verification: executable checks pass with exact commands/exit codes and artifact digest; manual-only evidence is insufficient for final quality approval.  
Risks/stop: stop if tooling requires unapproved dependency/network installation or semantic model edit.

### T035 — Discover the approved restricted deployment interface

Owner agent: `infrastructure-discovery-agent`
Purpose: Identify the existing authorised VPS/Podman wrapper or deployment interface needed by T022/T034 without changing infrastructure or exposing credentials.
Requirements: OR-001..006; deployment profile prohibition on direct SSH/unrestricted shell and requirement for rootless Podman/Quadlet. Architecture: sections 16/21 AR-004. DBML: not applicable.
Inputs: approved deployment profile, repository deployment inventory, authorised environment metadata supplied by `factoryctl`. Allowed paths: none (read-only discovery). Prohibited paths: repository writes, remote mutation, unrestricted shell, credential/secret output, provisioning and deployment.
Expected artifacts: discovery result naming only the approved interface contract, supported environments, required non-secret inputs, digest handoff, and observed constraints; no secret values. Dependencies: Hard T023 and authorised discovery-phase invocation. Parallelizable: true with independent read-only quality gates after frozen inputs.
Instructions: Verify rather than invent the restricted interface; distinguish observed facts from unavailable details; do not test by deploying.
Acceptance/verification: reproducible repository/environment references establish a usable restricted interface for rootless Quadlet promotion, or the result returns a precise blocker to `factoryctl`.
Risks/stop: stop on missing authorisation, direct-SSH-only access, secret exposure, public binding, privileged execution, or a need to modify infrastructure.

### T022 — Prepare OCI and Quadlet deployment artifacts in later deployment phase

Owner agent: `deployment-engineer-agent`  
Purpose: Implement approved rootless, non-public runtime descriptors without deploying during implementation.  
Requirements: OR-001..006; NFR-008/010/011; AC-015/026. Architecture: section 16; deployment profile. DBML: forward-only startup/migration separation.  
Inputs: validated packaged application, docs and T035 restricted-interface discovery. Allowed paths: `src/main/docker/**`, `deploy/quadlet/**`, `deploy/environments/**`, `docs/operations/**`. Prohibited paths: secrets, public binding, privileged containers, direct SSH scripts, production execution, environment-specific values in image.
Expected artifacts: non-root OCI definition, health check, loopback port, rootless Quadlet, external config/secret references, limits/restart/logging/runbook. Dependencies: Hard T023/T035 and workflow entry to authorised deployment phase. Parallelizable: false with deployment execution.
Instructions: Preserve nginx/internal boundary, separate runtime/migration DB credentials, same digest promotion and rollback image retention; exact resource values must be approved/measured config.  
Acceptance/verification: policy/lint/container smoke evidence in authorised phase; no deployment claimed here.  
Risks/stop: stop on public/privileged exposure, embedded secret, direct unrestricted shell or database reversal assumption.

### T023 — Run full integration and non-functional validation

Owner agent: `integration-engineer-agent`  
Purpose: Validate assembled behavior without redefining contracts.  
Requirements: all effective ACs; NFR-001..011; OR-004/006/008. Architecture: all validation obligations. DBML: validated migration/persistence contract.  
Inputs: T019-T021, T037 and implementation test suites. Allowed paths: `src/test/**/integration/**`, `src/test/resources/performance/**`, `docs/implementation/evidence/**`. Prohibited paths: production behavior/contracts/DBML/migrations.
Expected artifacts: end-to-end, fault, concurrency, security inspection, load/lag/startup, packaged smoke and recovery evidence. Dependencies: Hard T019/T020/T021/T037; migration/persistence/integration suites. Parallelizable: false for shared environment; scenarios may run independently when isolated.
Instructions: Use approved 1 CPU/512 MiB dataset/load profile; verify transaction non-overlap, tenant negatives, no sensitive output, outbox recovery, readiness, and exact contract. Record commands/exit codes; do not modify implementation to make tests pass.  
Acceptance/verification: all applicable ACs map to passing reproducible evidence; failures return to owning implementation task.  
Risks/stop: stop on secret/personal-data exposure, environment mismatch, flaky/unbounded test, or unavailable mandatory dependency evidence.

### T024 — Independently approve the database migration

Owner agent: `database-migration-gate-agent`  
Purpose: Review migration/persistence evidence against final DBML.  
Requirements: DR-001..009; CR-005. Architecture: database handoff. DBML: complete.  
Inputs: T010-T012/T023 outputs. Allowed paths: none (read-only gate). Prohibited paths: all repository and Git metadata writes.  
Expected artifacts: gate result. Dependencies: Hard T023. Parallelizable: true with other independent gates if inputs are frozen.  
Instructions: Compare every object, validate clean/upgrade/representative cases, privileges, locks, immutability and recovery.  
Acceptance/verification: independent PASS with exact evidence.  
Risks/stop: any DBML divergence, destructive migration, edited applied migration or missing privilege test fails the gate.

### T025 — Run code quality gate

Owner agent: `code-quality-gate-agent`  
Purpose: Independently review implementation quality and Clean Architecture.  
Requirements: NFR-004..007. Architecture: all guardrails. DBML: no drift.  
Inputs: frozen implementation/evidence. Allowed paths: none. Prohibited paths: all repository and Git metadata writes. Expected artifacts: gate result. Dependencies: Hard T023. Parallelizable: true with T024/T026-T029/T032.
Instructions/acceptance: review full diff, build/static/ArchUnit/LikeC4 evidence, reactive safety and maintainability; PASS only with no material defect.  
Risks/stop: implementation agent may repair findings but gate remains independent.

### T026 — Run test quality gate

Owner agent: `test-quality-gate-agent`  
Purpose: Independently approve meaningful behavioral, contract, integration and non-functional test coverage.  
Requirements: all effective ACs and NFR-007. Architecture: section 19. DBML: normative integration-test obligations.  
Inputs: T023 evidence. Allowed paths: none. Prohibited paths: all repository and Git metadata writes. Expected artifacts: test-quality gate result. Dependencies: Hard T023. Parallelizable: true with independent read-only gates.  
Instructions: Check requirement-to-test traceability, assertion quality, negative/fault/concurrency coverage, fixture safety, reproducibility and absence of disabled/weakened tests.  
Acceptance/verification: independent PASS.  
Risks/stop: missing material AC coverage, trivial assertions, unsafe fixtures or test weakening fails the gate.

### T032 — Run security gate

Owner agent: `security-gate-agent`  
Purpose: Independently approve restricted-data, tenant, secret, trust-boundary and runtime-privilege controls.  
Requirements: SR-001..007; privacy/security ACs; approved no-auth posture. Architecture: sections 13/19 and ADR-003/005. DBML: tenant/privilege/protection facts.  
Inputs: T023 evidence. Allowed paths: none. Prohibited paths: all repository and Git metadata writes. Expected artifacts: security gate result. Dependencies: Hard T023. Parallelizable: true with T024-T029.
Instructions: Verify no-auth residual boundary and loopback intent, tenant isolation, plaintext/ciphertext/fingerprint/key exclusion, secret injection, no-store/log-before-return, redaction, and Scheme runtime write denial.  
Acceptance/verification: independent PASS with reproducible security evidence.  
Risks/stop: cross-tenant access, public exposure, secret/plaintext leak, Scheme write ability, or invented auth behavior is HIGH and blocks release.

### T027 — Run API contract gate

Owner agent: `api-contract-gate-agent`  
Purpose: Independently approve OpenAPI and event contracts against TC-001.
Requirements: IR-002..005; CR-002/004; API/event ACs. Architecture: section 10/12. DBML: no physical leakage.  
Inputs: TC-001/T001/T003/T004/T017/T023. Allowed paths: none. Prohibited paths: all repository and Git metadata writes. Expected artifacts: gate result. Dependencies: Hard T023 and T001. Parallelizable: true with other gates.
Instructions/acceptance: validate parsing, operation/exclusion inventory, pagination authority, errors/security/versioning/examples and implementation conformance; independent PASS required.  
Risks/stop: TC-001 drift, Scheme/outbox/idempotency surface, breaking meaning or plaintext fails gate.

### T028 — Run performance and reliability gate

Owner agent: `performance-reliability-gate-agent`  
Purpose: Approve reactive, concurrency, outbox and pilot target evidence.  
Requirements: NFR-001..003/008..011; AC-025. Architecture: ADR-001/002. DBML: index/claim behavior.  
Inputs: T023 results. Allowed paths: none. Prohibited paths: all repository and Git metadata writes. Expected artifacts: gate result. Dependencies: Hard T023. Parallelizable: true with other gates.  
Instructions/acceptance: verify approved resource/dataset/load duration/concurrency, p95/p99/error/lag/startup, fault/restart/boundedness and reproducibility; PASS required.  
Risks/stop: wrong environment, omitted percentile/lag, event-loop blocking or lost event invalidates evidence.

### T029 — Run dependency and supply-chain gate

Owner agent: `dependency-supply-chain-gate-agent`  
Purpose: Independently approve build dependencies and supply-chain evidence before an immutable candidate is built.  
Requirements: NFR-004/007; OR-003; deployment artifact profile. Architecture: sections 5.1/16. DBML: migration identity included.  
Inputs: frozen dependency/build metadata including T036 validator rationale/classpath evidence and T023 evidence. Allowed paths: none. Prohibited paths: all repository and Git metadata writes.
Expected artifacts: dependency gate result with BOM, license, vulnerability, secret-scan, SBOM/reproducibility findings. Dependencies: Hard T023. Parallelizable: true with T024-T028/T032.  
Instructions: Verify minimality, BOM alignment, licenses, vulnerabilities, secret exclusion, SBOM plan and reproducibility without changing dependencies; verify T036 validators are pinned/reproducible and absent from production runtime classpaths.
Acceptance/verification: independent dependency PASS; no environment secrets or unapproved drift.  
Risks/stop: critical vulnerability, non-reproducibility, secret, mutable-only tag or source drift.

### T033 — Build the immutable OCI candidate

Owner agent: `build-release-agent`  
Purpose: Produce one traceable OCI candidate after all pre-build independent gates pass, without promotion or deployment.  
Requirements: OR-003; NFR-004/007; immutable artifact profile. Architecture: sections 5.1/16. DBML: migration identity included.  
Inputs: frozen source commit and PASS results T024-T029/T032. Allowed paths: build outputs and release evidence under authorised build phase. Prohibited paths: source/test/contract/DBML changes, production rebuild, promotion or deployment.  
Expected artifacts: reproducible build invocation, source identity, SBOM reference, artifact identifier and OCI digest. Dependencies: Hard T024-T029/T032. Parallelizable: false; artifact identity bottleneck.  
Instructions: Build once, bind digest to exact source and gate evidence, and keep environment configuration/secrets outside the image.  
Acceptance/verification: reproducible candidate digest exists and no source bytes changed during build.  
Risks/stop: non-reproducibility, secret inclusion, mutable-only identity, source drift or request to deploy.

### T034 — Replace the noncompliant product delivery workflow

Owner agent: `build-release-agent` (coordinate restricted deployment steps with `deployment-engineer-agent`)
Purpose: Replace the existing cross-service/direct-SSH workflow with Party Registry build-once and same-digest promotion orchestration; do not execute a deployment in this task.
Requirements: OR-001..006; NFR-004/007/010/011; immutable artifact and restricted-wrapper deployment profiles. Architecture: sections 16/21 AR-004. DBML: forward-only migration identity only.
Inputs: T020/T022/T035, approved environments, repository `.github/workflows/ci.yml`. Allowed paths: `.github/workflows/ci.yml`, `.github/workflows/**`, `docs/operations/**` only when inseparable from pipeline usage. Prohibited paths: source/tests/contracts/DBML, secret values, direct SSH/SCP, mutable-only deployment identity, production rebuild, Geographic Reference paths, merge/automerge.
Expected artifacts: Party Registry workflow using source commit and immutable OCI digest, staged development/staging promotion and later production promotion of the same digest through approved restricted interfaces, with health/smoke/stabilization evidence hooks and external environment configuration. Dependencies: Hard T020/T022/T035; Soft serialize with T033 artifact conventions. Parallelizable: false; shared release-policy bottleneck.
Instructions: Remove direct SSH/SCP and stale cross-service references; do not publish `latest` as deployment authority; separate build from promotion; require staging verification and workflow-authorized post-merge production promotion without rebuild; reference secrets by names only.
Acceptance/verification: workflow syntax/policy checks show no direct SSH/SCP, no Geographic Reference artifact, no public binding, and one digest flows through environments; dry-run/static validation only until authorised phases.
Risks/stop: stop if no approved restricted deployment interface exists, if the workflow would expose secrets/public ports, or if production would rebuild or consume a mutable tag.

### T030 — Run production-readiness gate

Owner agent: `production-readiness-gate-agent`  
Purpose: Review release/deployment/recovery package before any promotion.  
Requirements: OR-001..009; NFR-010/011; AC-015/026. Architecture: sections 16/17. DBML: forward-only and restore/reconciliation.  
Inputs: T020/T022/T033/T034 and all gates. Allowed paths: none. Prohibited paths: all repository and Git metadata writes. Expected artifacts: gate result. Dependencies: Hard T022/T033/T034 and all mandatory gates. Parallelizable: false; final pre-PR readiness review.
Instructions/acceptance: verify rootless/loopback/non-public posture, config/secret separation, same digest plan, health/smoke/stabilization, backup/restore/RPO/RTO, rollback and residual risk; PASS required before release handoff.  
Risks/stop: missing restore evidence, public exposure, database rollback assumption or digest rebuild blocks readiness.

### T031 — Prepare commit and Pull Request handoff

Owner agent: `github-flow-agent`  
Purpose: Publish the reviewed change through GitHub Flow without merge/automerge.  
Requirements: manifest GitHub governance. Architecture: scope/risks. DBML: migration notes.  
Inputs: issue/branch, frozen files, T024-T030/T032 PASS evidence and T033 artifact identity. Allowed paths: intended task outputs and Git/GitHub metadata under agent authority. Prohibited paths: unrelated files, workflow state, merge/automerge.  
Expected artifacts: scoped commits, pushed branch, PR with issue link/evidence/risk/migration/rollout/rollback notes. Dependencies: Hard all implementation and pre-merge gates. Parallelizable: false.  
Instructions: inspect status/diff/log, stage only intended files, preserve human merge confirmation and no-automerge policy.  
Acceptance/verification: PR URL and exact commit/files/gates recorded; PR is not called merged or deployed.  
Risks/stop: dirty unrelated files, missing gate, absent human-merge control or request to merge.

## Traceability Summary, Critical Path, and Global Stop Conditions

The grouped requirement-to-task-to-gate matrix is in `implementation-plan.md` section 13. No executable task lacks a source obligation. Test tasks T004/T006/T008/T011/T013/T015/T017 and repair T038 precede corresponding production tasks; T037 is the documented bounded post-construction exception backed by repaired T038/T039 application contracts and T011 database-first evidence. Migration T010 and persistence T012 are separate. Implementers and gate agents are independent.

Critical path: T001 -> T002 -> T036 -> T003 -> T004/T005 -> T006 -> T007 -> T008 -> T009 -> T038 -> T039 -> T011 revalidation -> T012 -> T037 -> T016 -> T018 -> T019 -> T021 -> T023 -> T024..T029/T032 and T035 -> T022 -> T034 -> T033 -> T030 -> T031. T013/T014, T015 and T017 join this path at their declared adapter dependencies and may run earlier where their write scopes do not overlap.

All agents must stop on authoritative-source conflict, TC-001 semantic drift, DBML divergence, a dropped mandatory application field or adapter-invented placeholder/default, secret/plaintext/personal-data exposure, cross-tenant access, Scheme mutation, destructive migration, inaccessible mandatory PostgreSQL test execution, absent T012 adapter construction contract, public binding, use of the existing direct-SSH/cross-service deployment behavior, remote I/O under mutation transaction, unbounded retry/concurrency, contract incompatibility, missing rollback/recovery, mandatory test/gate failure, unapproved dependency or request to modify protected factory state, merge, or deploy outside the authorised phase.
