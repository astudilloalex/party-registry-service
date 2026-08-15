# Implementation Plan

## 1. Document Control

| Field | Value |
|---|---|
| Project | `party-registry-service` |
| Status | Proposed implementation plan |
| Approval | Planning result only; implementation authority remains with `factoryctl` |
| Planning agent | `implementation-planning-agent` |
| Planning attempt | 1 of 3 |
| Requirements baseline | PRS-REQ-001 v0.4 plus human-approved Amendments 001 and 002 |
| Architecture baseline | PRS-ARCH-001 v0.6, `architecture/model.c4`, ADR-001 through ADR-005 |
| Persistence authority | `docs/database/v1-scheme.dbml` |

This plan does not authorize implementation, create an issue or branch, approve a gate, merge, or deploy.

## 2. Sources and Entry Gates

| Entry condition | Result | Evidence |
|---|---|---|
| Factory invocation and phase | PASS | Invocation identifies `implementation-planning-agent`, `PLANNING`, attempt 1/3, scoped-write planning paths, and canonical result schema. |
| Manifest governance | PASS | `.factory/project.yaml:3-44` |
| Requirements | PASS | `docs/requirements/requirements-specification.md`; Amendments 001/002; requirements-gate PASS in `.factory/runs/oc-409818fa-64f6-430b-88eb-be8e97ddcd1b/result.json` |
| Human amendments | PASS | `.factory/decisions.json:76-81` |
| Architecture | PASS | `docs/architecture/solution-architecture.md`; `architecture/model.c4`; ADR-001..005; clean-architecture-gate PASS in `.factory/runs/oc-bae96f91-74cb-4d6d-a506-f208f3dec005/result.json` |
| Database contract | PASS | Database-contract PASS in `.factory/runs/oc-ae767d3d-c531-40a3-9664-411843df2766/result.json`; final DBML at manifest path |
| Profiles | PASS | Supplied Clean Architecture, Quarkus Java 25, PostgreSQL, and VPS Podman Quadlet profiles |
| Planning write boundary | PASS | Only `docs/planning/**` is written |

The gate PASS results approve their own reviewed responsibilities only. The effective requirements baseline applies recorded human amendments over historical v0.4 text. Superseded idempotency and runtime Identifier Scheme administration clauses must not be implemented.

## 3. Change Intent and Scope

**APPROVED REQUIREMENT.** Implement the V1 internal Party Registry for tenant-scoped Parties, matching natural/legal details, natural-person nationalities, protected official Party Identifiers, exact identifier search, fail-closed decryption logging, and approved Party/Identifier integration events.

**APPROVED ARCHITECTURE.** Deliver one reactive Java 25 Quarkus deployable using strict Clean Architecture, reactive PostgreSQL access, a transactional outbox, RabbitMQ publisher confirms, a bounded Geographic Reference cache, runtime-injected encryption/HMAC secrets, and local structured logging.

**DATABASE CONTRACT.** Implement PostgreSQL physical design exactly from `docs/database/v1-scheme.dbml`, including the database-managed read-only Identifier Scheme catalog and all named Flyway obligations.

User-visible outcomes are the approved `/api/v1` Party/detail/nationality/Party Identifier operations, masked ordinary identifier responses, separate no-store decryption, optimistic concurrency, stable errors, and deterministic bounded search governed by technical contract decision TC-001 below.

Operational outcomes are health signals, protected telemetry, outbox recovery, daily-backup/restore evidence, immutable OCI digest promotion, rootless Podman/Quadlet operation, loopback binding, smoke checks, and a 10-minute stabilization window.

### Non-goals

- Authentication, authorization, roles, Keycloak, or Access Management request integration.
- Runtime Identifier Scheme CRUD, browse/search administration, lifecycle use cases, or Scheme events.
- Command-level idempotency, `Idempotency-Key`, replay storage, or `IDEMPOTENCY_CONFLICT`.
- Public outbox CRUD, event sourcing, CQRS, sagas, distributed transactions, DLQ ownership, or automatic outbox purge.
- Audit database/table, remote logging platform, remote KMS, distributed cache, new microservice, public service binding, physical deletion, or automatic purge.
- DBML modification, applied-migration edits, database rollback, production rebuild, or unrelated scaffold refactoring.

## 4. Classification

| Category | Evidence | Agents | Gates / rollout evidence |
|---|---|---|---|
| `new-feature`, `public-api-change` | FR-001..050 as amended; IR-005 | Quarkus, test, integration, documentation | API, code, test, security gates; OpenAPI compatibility and smoke evidence |
| `database-migration` | Final DBML and database-contract PASS | Database migration, test | Database migration gate; empty/upgrade/representative validation; roll-forward recovery |
| `integration-change` | IR-001..004; ADR-002 | Quarkus, integration, test | Contract, reliability, security evidence |
| `security-change`, `privacy-change` | SR-001..007; ADR-003 | Quarkus, test | Security gate; plaintext/redaction/tenant/secret evidence |
| `dependency-change`, `supply-chain-change` | Scaffold lacks required profile capabilities | Quarkus, build/release | Dependency gate, SBOM, license/vulnerability/reproducibility evidence |
| `performance-change`, `reliability-change` | NFR-001..011 | Quarkus, test, integration | Performance/reliability gate and recovery evidence |
| `deployment-change`, `operational-change` | OR-001..009; deployment profile | Deployment, documentation, build/release | Production-readiness gate; digest, health, backup/restore and rollback evidence |

## 5. GitHub Issue Decision and Branch Intent

```text
issueRequired: true
issueRationale: The change adds functionality, API and event contracts, database migration, integrations, restricted-data controls, dependencies, operational behavior, and deployment artifacts.
suggestedIssueTitle: Implement Party Registry Service V1 approved baseline
suggestedIssueScope: Implement PRS-REQ-001 as amended, PRS-ARCH-001 v0.6, and the validated DBML without Scheme administration or command idempotency.
```

The issue must be created only by `github-flow-agent`. Recommended branch intent is `feature/<issue-number>-party-registry-v1`, based on the repository default branch determined by that agent. Scope is limited to the tasks in `docs/planning/tasks.md`, including replacement of the noncompliant existing deployment workflow. Prohibited changes include DBML/architecture/requirements edits, unrelated upgrades, generated Scheme APIs, idempotency mechanisms, public exposure, and factory state.

## 6. Technical and Repository Baseline

| Item | Approved/verified value |
|---|---|
| Language | Java 25 (`build.gradle.kts:24-27`) |
| Framework | Quarkus 3.33.3 pinned by repository (`gradle.properties:3-7`) |
| Build | Gradle Kotlin DSL and wrapper |
| Execution | Reactive end-to-end; bounded isolation for unavoidable blocking/CPU work (ADR-001) |
| Persistence | PostgreSQL 18 contract target; reactive client preferred; Flyway only |
| API | OpenAPI; `/api/v1`; `ApiResponse`; ETag/If-Match |
| Tests | JUnit, Testcontainers, ArchUnit, JaCoCo per profile; current dependencies not yet present |
| Packaging | OCI; current generic Quarkus JVM/native Dockerfiles exist |
| Deployment | Rootless Podman/Quadlet on VPS; loopback behind internal nginx; same digest promotion |
| Integrations | Geographic Reference Service, RabbitMQ; no external logger/KMS |
| Performance | NFR-008..011 quantified pilot targets |

**EXISTING REPOSITORY FACT.** The application is scaffold-only: no `src/main/java`, `src/test`, migration, or OpenAPI artifacts exist. `build.gradle.kts` contains only Quarkus ARC and Quarkus JUnit; `application.properties` is empty. An existing `.github/workflows/ci.yml` is present, but it rebuilds on `main`, publishes a mutable `latest` tag, performs direct SSH/SCP, and references Geographic Reference deployment paths; this conflicts with the approved restricted-wrapper, same-digest Party Registry deployment and must be replaced by T034 before publication or deployment. Package paths below therefore use `<base-package>` until T003 records the bounded implementation placement. The Gradle group `com.alexastudillo` is evidence but does not by itself establish the final package.

### Proposed path status

| Area | Path | Status |
|---|---|---|
| Domain/application/adapters/bootstrap | `src/main/java/<base-package>/partyregistry/{domain,application,adapter,bootstrap}/**` | `new-approved` logical areas; exact package is an implementation decision |
| Tests | `src/test/java/<base-package>/partyregistry/**` | `new-approved` |
| API/event contracts | `api/openapi/**`, `api/events/**` | `new-approved`; repository placement decision in T003 |
| Migrations | `src/main/resources/db/migration/**` | `new-approved` |
| Runtime config | `src/main/resources/application.properties` | `existing` |
| Build | `build.gradle.kts`, `gradle.properties`, wrapper files | `existing` |
| Architecture source | `architecture/model.c4` | `existing`, read-only for implementation |
| Deployment | `src/main/docker/**`; `deploy/**` | Docker path `existing`; Quadlet path `new-approved` for later deployment phase |
| Documentation | `README.md`, `docs/**` | `existing`; only assigned docs may change |
| CI/CD | `.github/workflows/ci.yml` | `existing`; noncompliant cross-service/direct-SSH workflow, replacement bounded by T034 |

## 7. Constitution and Governance Check

| Check | Result | Evidence / plan disposition |
|---|---|---|
| Approved bounded context and non-goals | PASS | Requirements sections 4/21; architecture sections 3-5 |
| Java 25, Gradle Kotlin DSL, pinned Quarkus | PASS | Manifest, profile, repository pins; T004 prohibits drift |
| Strict Clean Architecture and inward dependencies | PASS | Architecture sections 6/20; T005-T009/T021 |
| Reactive execution and event-loop safety | PASS | ADR-001; T008/T013/T015/T018/T023 |
| Contract before adapters | PASS | T003-T004 precede T017 |
| DBML physical match and Flyway-only evolution | PASS | Database-contract PASS; T010 and T024; DBML immutable |
| Applied migrations immutable | PASS | T010 prohibits edits and requires inventory check |
| PostgreSQL checks/triggers/indexes/grants | PASS | DBML normative notes; T010/T011/T024 |
| Tenant isolation | PASS | T007-T009/T011/T016/T017/T026 |
| Idempotency and concurrency | PASS | No command idempotency; permanent uniqueness, If-Match, consumer event-ID dedupe and outbox claim semantics planned |
| Security/privacy/audit | PASS | T012-T013/T018-T019/T026; local log is not audit storage |
| Bounded pagination/query limits | PASS | TC-001 adopts the downstream-owned technical values delegated by requirements-gate PASS; T003 encodes them and T027 independently validates them |
| Observability and resilience | PASS | T018-T019/T023/T028 |
| No speculative infrastructure/dependency | PASS | Tasks forbid caches/stores/services beyond approved boundaries |
| Rootless Podman, Quadlet, loopback | PASS | T022/T030 |
| Same immutable artifact promotion | PASS | T029-T030; no production rebuild |
| Direct SSH prohibited and product pipeline bounded | PASS | Existing conflict is explicitly quarantined; T034 replaces `.github/workflows/ci.yml` with restricted-wrapper, same-digest behavior before T030/T031 |
| PR, no automerge, human merge | PASS | Manifest; T031 |
| Independent gates | PASS | Section 13 and T025-T030 |
| Rollback/recovery | PASS | Forward-only DB, application digest rollback approval, backup/restore and reconciliation planned |
| Documentation | PASS | T020 |

There is no approved exception and no unresolved human decision. TC-001 is a bounded technical-contract decision delegated by the upstream requirements-gate PASS; it does not change business scope, DBML, architecture, security, or deployment policy.

## 8. Implementation Boundaries and Workstreams

| Workstream | Boundary / outputs | Owner | Dependencies | Completion evidence |
|---|---|---|---|---|
| Governance/contracts | TC-001, OpenAPI and immutable event schemas | Planning, Quarkus, test | Upstream baseline | TC-001 record; API/event gate |
| Foundation | Minimal BOM-aligned capabilities and package placement | Quarkus | Stable contract direction | Reproducible build metadata; dependency gate |
| Domain | Pure entities, values, policies, masks, lifecycle/errors | Test then Quarkus | Effective requirements | Pure deterministic tests; ArchUnit |
| Application | Ports/use cases, tenant/context, transactions, concurrency | Test then Quarkus | Domain and contracts | Use-case tests without adapters |
| Database migration | Forward-only SQL derived only from DBML | Database migration | DB contract PASS | Migration gate; empty/upgrade/data evidence |
| Persistence | Reactive mappings/queries/transactions/error mapping | Test then Quarkus | Migration, application ports | PostgreSQL Testcontainers evidence |
| Integrations | Geography/cache, crypto/secrets, local security log, RabbitMQ | Test, Quarkus, integration | Ports/contracts | Provider/fault/ordering tests |
| Inbound API | Reactive REST DTOs/resources/context/errors | Test then Quarkus | OpenAPI/application | Contract tests and API gate |
| Cross-cutting | Health, readiness, telemetry, architecture checks | Test then Quarkus | Adapters | Security/performance/architecture evidence |
| Docs/deployment | Guides, restricted-interface discovery, runbooks, OCI/Quadlet and compliant product workflow artifacts | Documentation/infrastructure-discovery/deployment/build-release | Validated implementation | Discovery, workflow policy and production-readiness evidence |

Business logic is prohibited in REST resources, persistence entities/repositories, mappers, configuration, deployment files, and database triggers except DBML-mandated database invariants. Domain imports no framework/library infrastructure. Application depends only on domain and its own ports.

## 9. Contract, Domain, Application, Data, and Adapter Plan

### Contract-first

T003 must apply TC-001 and define every approved operation, context header, request/response, mask, no-store response, ETag/If-Match behavior, validation/error category, tenant non-disclosure rule, payload bound, and examples containing synthetic data only. It must exclude Scheme/outbox administration and idempotency. Versioned event schemas must cover only the approved Party/Identifier catalog, stable event identity, tenant/aggregate/version/correlation data, minimum payload, and no plaintext. T004 writes failing contract tests before T017.

**TECHNICAL CONTRACT DECISION TC-001.** Acting under the bounded authority explicitly delegated by requirements-gate PASS finding RG-401, V1 uses zero-based `page` with default `0`, `size` with default `20` and inclusive range `1..100`, and ascending resource UUID as the sole V1 order (and deterministic tie-breaker). Effective supported filter semantics are Party type/status, nationality country/primary/active within one Party, and Party Identifier Party/Scheme/status/primary; Amendment 002 removes all Identifier Scheme browse/search parameters with that endpoint. Negative pages, out-of-range sizes, unsupported filters, and unsupported sort fields/directions produce `VALIDATION_ERROR`. T003 may choose non-normative query-parameter spelling and serialization consistent with these semantics as a reversible technical contract decision, and T027 must independently validate it. TC-001 adopts downstream-resolvable technical values; it does not present RG-208 as original product authority.

### Domain and application

T005/T006 cover Party type/detail, dates, nationalities, exact lifecycle matrices, immutability, masking, display-name normalization, identifier compatibility and errors. Domain receives no Quarkus/Jakarta/Mutiny/transport/persistence/logging/configuration imports. T007/T008 define input/output ports and use cases for Party/detail/nationality/Identifier only, pre-resolve geography before transaction, enforce tenant/context and If-Match, orchestrate protection, atomically request mutation/outbox, and order decrypt-log-before-return. No Scheme mutation or command-idempotency port is permitted.

### Migration and persistence

T010 maps all 8 enums, 7 tables, five references, checks, named indexes, partial indexes, deferred detail triggers/function, UUIDv7 requirement, comments/defaults, runtime/migration privilege separation, and outbox semantics from DBML. It must not invent the final Flyway version until current migration inventory is rechecked. It validates a clean database, previous supported schema upgrade when one exists, representative data, locks/downtime, and failure recovery. Existing applied migrations are immutable and rollback is roll-forward only.

T011/T012 keep persistence models separate from domain, use reactive PostgreSQL and reactive transactions, tenant-qualify operations, bound pages/batches, map named constraints/SQLSTATE safely, increment aggregate versions atomically, expose a query-only Scheme catalog, and never hold transactions across remote I/O.

### Integrations, security, observability, resilience

- Geographic calls use bounded reactive timeout, 24-hour cache, at-most-seven-day stale fallback, cold-cache 503, active-code mapping, and no transaction overlap.
- Protection uses separate runtime-injected encryption/HMAC masters, tenant derivation, authenticated encryption, exact uppercase HMAC representation, version handling, minimal plaintext lifetime, and no fallback secret.
- Local decryption logging emits required structured fields before disclosure, reports synchronous failure, and never claims remote durability.
- RabbitMQ uses persistent versioned messages, publisher confirms, bounded claim/publish/outcome phases, `SKIP LOCKED`, optimistic claim token, same event ID, no DLQ/discard/purge, and authorised internal failed-event recovery without public CRUD.
- Logs/metrics/traces omit plaintext, ciphertext, fingerprints, keys and confidential payloads. Health separates liveness, mandatory readiness, and degradable dependencies.

## 10. Testing Strategy and Architecture Enforcement

Tests precede or accompany production tasks as defined in `tasks.md`. Required layers are pure domain tests, application tests with port fakes, OpenAPI/event contract tests, reactive API tests, PostgreSQL migration/persistence tests, provider integration tests, concurrency/fault/restart tests, security/redaction tests, architecture tests, pilot load/lag tests, packaged-runtime smoke tests, and deployment/recovery checks.

ArchUnit must mechanically prohibit framework imports in domain, concrete adapters in application, REST-to-persistence access, persistence model exposure, direct repository injection into use cases, Scheme mutation capability, package cycles, and unapproved dependencies. LikeC4 parse/validate/render remains a mandatory independent quality-gate check; it was previously `NOT_RUN` because repository-controlled tooling was absent. T021 may add the minimum justified validation mechanism only through the governed build task; it must not alter reviewed architecture semantics.

No test may use production data, real identifiers, secrets, trivial assertions, disabled checks, or implementation-exact mocks. Implementers may not delete or weaken tests to pass.

## 11. Phases, Dependency Graph, Waves, and Checkpoints

### Phases and critical path

1. Governance: TC-001/T001 -> T002.
2. Contract/foundation: T003 -> T004.
3. Test-first core: T005 -> T006 -> T007 -> T008.
4. Data: T010 -> T011 -> T012.
5. Integrations/API: T013 -> T014; T015 -> T016; T017 -> T018.
6. Cross-cutting/docs: T019 -> T020/T021.
7. Validation and release preparation: T023 -> T024..T029/T032; after T023 and T035, T022 -> T034; then immutable build T033 and readiness T030.
8. GitHub handoff: T031.

Critical path is TC-001 -> contracts -> application ports -> persistence/integrations -> inbound API -> integration validation -> independent gates -> immutable candidate/readiness -> PR handoff.

### Parallel waves

| Wave | Tasks safe to run after dependencies | Checkpoint |
|---|---|---|
| 0 | T001 | TC-001 is recorded in this proposed plan as a delegated technical-contract decision; approved source files remain unchanged. |
| 1 | T002, then T003 | Issue/branch intent exists and public/event contracts apply TC-001. |
| 2 | T004; T005 and T007 after foundation | Minimal dependencies are fixed; failing core/contract tests express approved behavior. |
| 3 | T006 and T008 sequentially; T010 may run in parallel after T004 | Pure core and DB migration independently match approved sources. |
| 4 | T011, T013, T015, T017 test tasks where write scopes do not overlap | Adapter verification harnesses fail for the intended missing behavior. |
| 5 | T012, T014, T016, T018 after corresponding tests/ports | Adapters satisfy stable ports/contracts without transaction or boundary leakage. |
| 6 | T019, T020, T021 | Cross-cutting evidence and documentation are aligned; full integration validation may begin. |
| 7 | T023, then independent T024-T029/T032; T035 -> T022 -> T034 may proceed in authorised later phases alongside frozen-input gates | Full validation and compliant deployment/release artifacts exist; direct SSH/cross-service references are absent. |
| 8 | T033, then T030 | One immutable candidate is identified and readiness-reviewed with all mandatory gates and deployment evidence. |
| 9 | T031 | PR handoff contains issue, scope, evidence, risks, rollout and rollback; no merge occurs. |

Shared bottlenecks are `build.gradle.kts`, `application.properties`, OpenAPI, migration ordering, and bootstrap wiring. Tasks touching those files are non-parallel or must be serialized by `factoryctl`.

## 12. Quality Gates and Evidence Package

| Gate | Trigger/input | Pass evidence | Repair owner |
|---|---|---|---|
| `code-quality-gate-agent` | All implementation | Clean build/static analysis, architecture boundaries, changed-file inventory | Responsible implementation agent |
| `test-quality-gate-agent` | Tests/behavior | Requirement-to-test coverage and non-trivial passing suites | `test-implementation-agent` |
| `security-gate-agent` | Releasable change | Tenant isolation, no-auth boundary, secret/plaintext/redaction/no-store/DB privilege evidence | Quarkus/test/deployment owner by finding |
| `dependency-supply-chain-gate-agent` | Build/dependencies | BOM alignment, vulnerability/license/secret scan, SBOM, reproducibility | Quarkus or build/release |
| `api-contract-gate-agent` | OpenAPI/events | TC-001, operation inventory, compatibility, examples and contract tests | Contract author |
| `database-migration-gate-agent` | Flyway/persistence | DBML mapping, clean/upgrade/representative tests, privileges, immutability | Database migration agent |
| `performance-reliability-gate-agent` | NFR-001..011 | Load/lag/startup/fault/recovery results under approved pilot profile | Test/integration owner |
| `production-readiness-gate-agent` | Candidate release | Same digest, rootless/loopback, health/smoke/stabilization, backup/restore/runbook | Build/deployment/docs owners |

`multiplatform-compatibility-gate-agent` is N/A: no Flutter or multiplatform behavior is in scope.

Completion evidence must be reproducible and include source commit, issue/branch/PR references when later created, changed-file inventory, exact build and test commands/exit codes, unit/contract/integration/migration/architecture/security/performance results, dependency and secret scans, SBOM, DBML-to-migration mapping, log/event redaction inspection, backup/restore and rollback/reconciliation evidence, OCI identifier/digest, environment promotion records, health/smoke/stabilization results, docs, and every gate result. Planned checks must never be represented as passed.

## 13. Traceability Matrix

| Obligation group | Implementation tasks | Verification tasks | Gates/evidence |
|---|---|---|---|
| AC-000; NFR-004..007; DR-008/009; CR-005 | T004, T010, T021, T022 | T021, T023, T024 | Code, dependency, migration, production-readiness; build/profile/LikeC4 evidence |
| FR-001..003, 007, 010..012, 015..017, 021..033; VR-001/002/006/009..012; DR-001..003/005/007 | T006, T008, T012, T014, T018 | T005, T007, T011, T013, T017, T023 | Test, security, API, performance; AC-001..005, 011/012, 019/020, 023/024, 027, 030..043, 059/060, 067..070 |
| Amendment 001; FR-005/006/013/014/018/034..039/046..049; VR-003..005/007/008/013/014; DR-004; SR-001..007; CR-003 | T006, T008, T012, T014, T018, T019 | T005, T007, T011, T013, T017, T023 | Security, API, migration, test; AC-007..010, 017, 021/022, 028, 044..050, 061/062, 071..079 |
| Amendment 002; effective FR-019/040..045/048/049; IR-004 | T008, T010, T012, T018 | T007, T011, T017, T021, T023/T024 | Architecture/API/security/migration evidence proving query-only catalog and no Scheme API/event |
| FR-008/009/020/050; VR-015..018; DR-006; IR-002..004; NFR-001..003/009; CR-002/004 | T003, T008, T010, T016 | T004, T007, T011, T015, T023 | API, migration, reliability, security; AC-012..014, 016, 025, 029, 058, 063..066, 080 |
| FR-023/031/036 and VR-007 pagination portions | TC-001; T003, T008, T012, T018 | T004, T007, T011, T017, T023 | API/test/performance; AC-028/033/041/047/081 and T027 |
| IR-001/005; Geographic behavior | T003, T008, T014, T018 | T004, T007, T013, T017, T023 | API/security/reliability; AC-004/023/028/059/060 |
| NFR-008..011; OR-001..009; CR-001 | T019, T020, T022, T033, T034 | T023, T028-T030, T035 | Performance/reliability, restricted-interface discovery, workflow-policy and production-readiness; AC-015/025/026 |
| Every DBML enum/table/reference/check/index/trigger/grant obligation | T010, T012 | T011, T024 | Database migration gate and DBML mapping artifact |

Superseded v0.4 statements are traceable to exclusion tests: no command idempotency (T004/T007/T017/T021), no Scheme runtime surface (T004/T007/T011/T017/T021), and no external logging platform (T013/T021/T026).

## 14. Complexity, Risks, and Decisions

### Deliberate complexity

| Item | Why required / simpler alternative | Risks / ownership / review condition |
|---|---|---|
| Transactional outbox | Required atomic event intent; direct publish is inconsistent | ADR-002; Quarkus/database owners; review only via new approved ADR |
| Authenticated encryption plus tenant HMAC | Restricted plaintext and exact protected lookup; plaintext/hash alternative is prohibited | ADR-003/004; security owner; rotation evidence required |
| Bounded Geographic cache | Approved availability semantics; direct-only calls fail BR-019 | Quarkus integration owner; no durable/distributed cache |
| Deferred Party/detail triggers and partial indexes | Explicit DBML invariants; application-only enforcement is concurrency-unsafe | Database migration owner; migration gate |

No new runtime container, broker, cache service, module, external logger, KMS, or privileged capability is planned.

### Risk register

| ID | Risk / trigger / impact | Mitigation and verification | Authority / stop condition |
|---|---|---|---|
| R-001 MEDIUM | Contract implementation drifts from delegated TC-001 or accidentally restores removed Scheme search | Contract/exclusion tests and independent API gate | Contract author/API gate; stop on semantic drift |
| R-002 HIGH | Approved unauthenticated internal decryption is exposed beyond loopback/internal nginx | Security and deployment negative tests; no public binding; no invented auth | Security/operations gates; stop on public exposure |
| R-003 HIGH | Plaintext, fingerprint, ciphertext or key reaches telemetry/event/repository | Model separation, redaction inspection, secret scans, fail closed | Security gate; stop on any exposure |
| R-004 HIGH | Migration diverges from DBML or risks destructive/irreversible data change | Exact mapping, representative upgrade, lock analysis, roll-forward recovery | Database gate/human DB authority; stop on conflict/destructive remediation |
| R-005 HIGH | Tenant predicate or Scheme runtime privilege is missing | Typed tenant ports, negative tests, SELECT-only privilege test | Security/database gates; stop on cross-tenant or catalog-write ability |
| R-006 MEDIUM | Outbox unknown outcome causes duplicate delivery/backlog | Same ID, bounded phases, consumer dedupe contract, lag metrics/recovery | Reliability gate; stop on lost event/unbounded loop |
| R-007 MEDIUM | Reactive starvation under crypto/publisher/load | Bounded isolation/concurrency and blocking detection/load test | Performance gate |
| R-008 MEDIUM | LikeC4 executable validation remains unavailable | Add/use repository-controlled approved validator before quality approval | Code/architecture quality gate; stop before final gate approval |
| R-009 MEDIUM | Unsupported Scheme rule key makes readiness unavailable | Release/migration coordination and readiness integration tests | Build/database owners |
| R-010 MEDIUM | Restore recurrence and operational alert details are absent | Document before production; verify RPO/RTO with restore evidence | Operations authority; stop before production readiness |
| R-011 HIGH | Existing `.github/workflows/ci.yml` can rebuild on `main`, use direct SSH/SCP, mutable `latest`, and Geographic Reference paths | Quarantine from use; T034 replaces it; T030 verifies restricted wrapper and same digest | Build/release and deployment owners; stop publication, merge readiness, or deployment while conflict remains |

### Human decisions

None unresolved. A later change to business search scope, a breaking published contract, security/privacy risk acceptance, DBML, architecture, destructive operations, or production exposure still requires its designated authority; TC-001 does not grant such authority.

## 15. Pull Request and Release Handoff

The later PR must reference the issue, use the approved feature branch, summarize only planned functionality, list changed paths, include requirement/test traceability and all mandatory gate evidence, document restricted-data and no-auth residual risk, explain migration/privilege/compatibility behavior, and include rollout, reconciliation, backup/restore, application rollback, and forward-only database notes. Automerge is prohibited. Human merge confirmation is mandatory, and a human performs the GitHub merge.

After authorised merge, `build-release-agent` records source commit, reproducible build invocation, OCI digest and SBOM. Development and staging receive that exact digest. Production receives the same staging-verified digest without rebuild, only after workflow authority permits it. Environment configuration and secrets remain external. Deployment and production verification belong to later phases and agents.

## 16. Global Stop Conditions and Assumptions

Stop and return control rather than improvise on any requirement/architecture/DBML conflict, TC-001 semantic drift, public-contract incompatibility, secret or personal-data exposure, cross-tenant access, destructive migration, Scheme runtime mutation, unbounded retry/concurrency, remote I/O inside a state-changing transaction, use of the existing direct-SSH/cross-service workflow, production access, missing rollback/recovery evidence, mandatory test/gate failure, unapproved dependency/version drift, or protected factory-state change.

Low-risk planning assumptions:

1. The exact Java base package and new contract-directory placement are bounded implementation decisions because the scaffold establishes no package convention; T003/T004 must record them without altering architecture.
2. No applied migration exists at planning time; T010 must re-inventory immediately before selecting the next immutable version.
3. Operational numeric values not approved by requirements (publisher batch, timeout, backoff, jitter, pool limits, alert thresholds) remain bounded external configuration validated against approved targets, not new business requirements.
