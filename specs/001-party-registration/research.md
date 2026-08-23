# Phase 0 Research: Party Registration

**Date**: 2026-08-23
**Status**: Complete for design; independent DBML validation and the Geographic Reference
trusted-header contract are approved, while the remaining architecture alignment stays as a
pre-implementation gate

## Authoritative Inputs

- [Feature specification](./spec.md)
- [Project constitution](../../.specify/memory/constitution.md), version 2.1.0
- [Approved DBML](../../docs/database/v1-scheme.dbml)
- [Party Registry service context](file:///home/alex/Documents/Development/architecture/alex-astudillo-architecture/architectures/party-registry/party-registry-overview.c4#L2)
- [Create Party sequence](file:///home/alex/Documents/Development/architecture/alex-astudillo-architecture/architectures/party-registry/party-registry-sequences.c4#L2)
- [Optional outbox sequence](file:///home/alex/Documents/Development/architecture/alex-astudillo-architecture/architectures/party-registry/party-registry-sequences.c4#L169)
- Geographic Reference Service Postman collection `geographic-reference-service`, UID
  `15834347-d3591c82-bd52-46a9-973d-a7a102d4b9b3`, updated 2026-07-27
- Human contract clarification dated 2026-08-22: a successful country lookup returns `data.status`
  as one of `DRAFT`, `ACTIVE`, `DEPRECATED`, or `RETIRED`
- Provider-owner contract decision dated 2026-08-23: Party Registry requires exactly one
  `Tenant-Id`, `User-Id`, and `Process-Id` and propagates those three trusted context headers to
  Geographic Reference. This closed header decision governs Party Registry independently of later
  maintenance of the provider's Postman collection.

Only the two sequence views required by this feature were consulted.

## R-001: Reactive ORM Persistence

**Decision**: Use Hibernate Reactive ORM through its Mutiny API. Production
application code must not use direct or native SQL, the PostgreSQL Reactive Client directly,
blocking Hibernate ORM, or JDBC. The PostgreSQL reactive driver remains an infrastructure detail
used by Hibernate Reactive.

**Rationale**: This implements the explicit human decision, constitution 2.1.0, and the updated
LikeC4 technology declaration while preserving non-blocking I/O and ORM-based persistence.

**Alternatives considered**:

- Direct PostgreSQL Reactive Client: rejected by the amended constitution and human decision.
- Blocking Hibernate ORM or JDBC: rejected because request processing must remain non-blocking.

## R-002: Clean Architecture Boundaries

**Decision**: Keep separate API DTOs, application commands/results and ports, framework-free
domain aggregates/value objects, and Hibernate persistence entities. Place all Jakarta REST,
Hibernate, JSON, CDI, configuration, and client annotations in outer adapters or bootstrap code.
Application ports use `Uni`; domain types do not depend on Mutiny or any framework.

**Rationale**: This makes dependency direction executable and prevents transport or persistence
models from becoming the Party domain model.

**Alternatives considered**:

- Annotating domain objects as persistence entities: rejected as a strict Clean Architecture
  violation.
- Passing REST DTOs into the use case or domain: rejected because it leaks transport concerns.

## R-003: Creation Flow and Transaction Boundary

**Decision**: Execute creation in this order:

1. Validate singular `Tenant-Id`, `User-Id`, and `Process-Id` context headers at the API boundary.
2. Map and validate the closed natural-person or legal-entity request variant.
3. Construct and validate a new domain aggregate without a persistence identity, deriving display
   name, `DRAFT`, version `0`, audit values, and distinct country codes.
4. Validate all country codes through the application-owned geographic-reference port.
5. Evaluate the event-recording policy once for the command.
6. Persist the complete aggregate and optional application-owned event-recording intent in one
   `Mutiny.SessionFactory.withTransaction` callback.
7. Return success only after the transaction commits.

Writes sharing a Hibernate Reactive session are composed sequentially, not in parallel. No remote
call occurs while a database transaction is open.

**Rationale**: The sequence implements the approved remote-before-transaction boundary, keeps
network latency outside the transaction, and guarantees all-or-nothing Party and outbox
persistence. The current LikeC4 sequence now shows separate natural-person and legal-entity
validation paths before their Hibernate Reactive transaction steps.

**Alternatives considered**:

- Geographic validation inside the transaction: rejected because it holds database resources
  across remote I/O.
- Separate Party and outbox transactions: rejected because it violates atomicity.
- Concurrent writes on one reactive session: rejected because reactive sessions are not safe for
  parallel stream use.

## R-004: Identifier and Audit Generation

**Decision**: A newly constructed domain Party has no persistence identity. In the persistence
adapter, Hibernate's UUID version 7 generator assigns the root ID when the root entity is persisted;
dependent details and nationalities are mapped as its entity graph. After that persist stage assigns
the root ID, the adapter maps an application-owned event-recording intent to an outbox entity and
persists it sequentially in the same transaction. Nationality and outbox IDs also use Hibernate's
UUID version 7 generator. Keep PostgreSQL 18 `uuidv7()` defaults as defensive schema defaults.

Use one trusted application `Instant`, one UTC `LocalDate` derived from the same injected clock, and
the `User-Id` value for all initial audit and business-date decisions. Initial Party and outbox
delivery versions are `0`.

**Rationale**: Hibernate assigns IDs inside the ORM boundary without direct SQL. Details remain part
of the root entity graph, while the outbox entity is created only after the generated aggregate ID
is available. One clock, UTC business date, and actor make audit and interval decisions deterministic
across the atomic operation.

**Alternatives considered**:

- Relying only on database-default UUIDs: rejected because generated identity retrieval is less
  explicit and would complicate the outbox mapping.
- Requiring a non-null persistence ID in the domain before persistence: rejected because it couples
  new aggregate construction to infrastructure identity generation.
- Random UUID version 4: rejected because the approved DBML establishes UUID version 7.
- Per-entity database timestamps: rejected because they can differ within one command.

**Verification requirement**: An integration test must confirm generated identifiers report UUID
version 7 after Hibernate upgrades because Hibernate's version-7 generator is incubating.

## R-005: ORM Mapping

**Decision**: Map PostgreSQL enums explicitly as named enums using string-valued Java enums and map
the outbox payload as JSON. Persistence records mirror DBML columns but remain package-private to
the persistence adapter. The Party details and nationalities are persisted through explicit
mappers rather than exposing persistence entities to the application.

**Rationale**: Explicit enum and JSON mappings preserve the DBML contract and avoid ordinal or
implicit `VARCHAR` behavior.

**Alternatives considered**:

- Ordinal enums: rejected because enum order changes would corrupt meaning.
- Global implicit native-enum conversion: rejected because it may affect future mappings without
  an explicit decision.

## R-006: Flyway and Schema Ownership

**Decision**: Flyway remains the only schema migration mechanism and runs through an isolated JDBC
migration datasource. Hibernate schema generation is disabled and schema validation is enabled.
The JDBC driver must not be injected or used by production application code.

**Rationale**: Flyway requires JDBC even though runtime persistence is reactive. Isolating that
boundary preserves both the migration requirement and the non-blocking application requirement.

**Alternatives considered**:

- Hibernate schema create/update: rejected by the constitution.
- Runtime application-managed migrations: rejected because migration execution is operational,
  not part of a business flow.

## R-007: Temporal Nationality Integrity

**Decision**: Use the temporal exclusion constraints now recorded by the authoritative DBML:

- For one Party and country, nationality validity intervals must not overlap.
- For one Party, intervals marked primary must not overlap, regardless of country.
- Null starts and ends represent open interval bounds, and equal boundary dates remain inclusive.

The domain validates the submitted command before persistence; the database constraint protects
concurrent and non-application writes.

**Rationale**: PostgreSQL partial-index predicates cannot depend on `CURRENT_DATE` or a
command-supplied registration date and do not reevaluate as time passes. Range exclusion expresses
the invariant for every date and closes concurrency races.

**Alternatives considered**:

- Partial uniqueness only where `validUntil` is null: rejected because it misses finite intervals
  that are active.
- Application-only validation: rejected because concurrent writes can both pass before commit.

**Pre-implementation gate**: PASS. `ex_party_nationalities_country_validity` and
`ex_party_nationalities_primary_validity` are present in reviewed DBML commit
`a167e5bea93eeccbd4513c1fb91a6d9f08e2412d`. The dated
[`independent validation`](../../docs/database/reviews/2026-08-23-v1-scheme-independent-validation.md)
proved finite, open, equal-boundary, and concurrent conflict behavior on PostgreSQL 18.4. No Flyway
migration is created by that validation.

## R-008: Aggregate Shape Enforcement

**Decision**: Keep the DBML's currently specified deferred trigger scope unchanged: prevent an
incompatible detail type and prevent both detail types. Enforce exactly one matching detail and
the prohibition of legal-entity nationalities in the domain, use case, aggregate persistence
adapter, and integration tests. Treat version `0` as initial construction; only post-creation
detail or nationality mutations increment the Party version.

**Rationale**: The database belongs exclusively to this service, and the approved decision avoids
expanding a complex cross-table trigger while maintaining atomic writes and layered verification.

**Alternatives considered**:

- Expand the deferred trigger to enforce the complete aggregate at commit: rejected by the human
  decision because it would require a broader DBML change and additional trigger complexity.

**Residual risk**: A writer that bypasses the service could create a Party without details or add
nationalities to a legal entity. Operational access must therefore remain restricted to the
service and migrations.

## R-009: Internal REST Contract

**Decision**: Define `POST /internal/v1/parties` in OpenAPI 3.1.1. Require exactly one `Tenant-Id`
UUID header, one `User-Id` header of 1 to 128 characters, and one `Process-Id` UUID header. Use a
closed discriminated request union for `NATURAL_PERSON` and `LEGAL_ENTITY`, reject unknown and
server-owned fields, and return `201 Created` with `partyId` and version `0`. Do not emit a
`Location` header until a retrieval contract exists.

**Rationale**: The path makes internal intent and API version explicit; the closed union prevents
incompatible details structurally. `201` is returned only after a completed commit.

**Alternatives considered**:

- A nullable all-fields request: rejected because invalid combinations are ambiguous.
- `200 OK`: rejected because a resource is created.
- `202 Accepted`: rejected because creation is complete before success is returned.
- A body-level tenant, user, display name, status, or version: rejected because those are trusted
  context or server-owned values.

## R-010: Error Contract

**Decision**: Use RFC 9457 `application/problem+json` with stable problem codes and sanitized
details. Map invalid context or malformed JSON to `400`, unsupported media type to `415`, invalid
Party or explicit unknown/inactive country to `422`, unavailable geographic validation to `503`,
and a known rolled-back persistence failure to `500`. Never include rejected personal values,
provider payloads, SQL details, or stack traces.

**Rationale**: This gives all failures one versioned shape and distinguishes malformed transport,
business validation, dependency availability, and known rollback outcomes.

**Alternatives considered**:

- A custom error envelope: rejected in favor of the current HTTP Problem Details standard.
- `404` for an unknown country: rejected because the requested resource is Party creation.
- Treating dependency failure as an invalid country: rejected because validation did not produce
  an authoritative result.

## R-011: Trusted Context and Internal Reachability

**Decision**: Treat `Tenant-Id`, `User-Id`, and `Process-Id` as trusted context supplied to an
internal-only service, not as authentication credentials. Authentication, caller-to-tenant
authorization, and public ingress remain outside this feature and must be enforced upstream and by
deployment policy. The service validates header cardinality, syntax, and length, never accepts
tenant or audit ownership from the body, and propagates the process identifier unchanged.

**Rationale**: The feature specification explicitly excludes authentication and authorization,
while requiring trusted context and no Internet exposure.

**Alternatives considered**:

- Invent OAuth2, mTLS, or another security scheme: rejected because no approved inbound security
  contract exists for this feature.
- Treat context headers as proof of identity: rejected because header syntax is not authentication.

## R-012: Geographic Reference Anti-Corruption Port

**Decision**: Define an application-owned `ActiveCountryReferenceValidationPort` that accepts an
immutable set of distinct uppercase ISO alpha-2 codes plus trusted tenant, audit-subject, and process
context and returns a `Uni` with one typed outcome: all active, explicitly invalid references, or
validation unavailable. Skip the call for an empty set.

The REST adapter implements Postman collection
`15834347-d3591c82-bd52-46a9-973d-a7a102d4b9b3` by issuing
`GET /api/v1/countries/by-alpha2/{alpha2Code}` once for each distinct code, with at most 11 lookups
for one creation command. It sends `Accept: application/json` and propagates the trusted
`Tenant-Id`, `User-Id`, and `Process-Id` unchanged to every lookup. The provider echoes
`Process-Id`, which the adapter verifies. The 2026-08-23 provider-owner decision is authoritative
for Party Registry's three required context headers and supersedes the older Postman header
definition for this integration scope.

For each requested code, a `200` response is active only when the standard envelope has
`status: 200`, `code: successful`, and `data.status: ACTIVE`. A documented `data.status` of `DRAFT`,
`DEPRECATED`, or `RETIRED` is an explicit inactive reference. A `404` standard envelope whose code
ends in `not-found` is an explicit unknown reference. Every other HTTP status, activity value,
media type, envelope, process-ID echo, timeout, connection result, or incomplete response is
validation unavailable.

Only explicit unknown or inactive results are invalid references. Partial, malformed,
contradictory, timed-out, unauthorized, or otherwise incomplete responses mean validation is
unavailable and fail creation before persistence.

**Rationale**: The port isolates provider semantics, deduplicates lookups, carries all required
trusted context explicitly, and keeps HTTP models outside the application and domain. It uses the
provider route, envelope, status field, and status values from Postman together with the newer human
header decision.

**Alternatives considered**:

- Boolean result: rejected because it cannot distinguish invalid data from dependency failure.
- Provider DTOs or exceptions in the port: rejected because they break the anti-corruption
  boundary.
- `GET /api/v1/countries?status=ACTIVE`: rejected because the collection does not define the list
  item shape or a completeness guarantee suitable for authoritative membership checks.
- Treating every `200` as active: rejected because existing but non-active countries are valid
  provider outcomes.
- Relying on any undeclared business-context header: rejected because the Party Registry adapter
  contract is closed to the three approved trusted context headers.

## R-013: Geographic Resilience

**Decision**: Do not automatically retry Party creation or geographic validation. Configure
bounded geographic connection and request deadlines as mandatory environment settings, preserve
cancellation, and fail closed when the dependency does not complete. Do not add fallback or stale
country caches in this feature.

**Rationale**: Creation is non-idempotent and no approved retry budget, freshness policy, or
durable request-deduplication model exists.

**Alternatives considered**:

- Guessed retries and backoff: rejected because they can exceed caller deadlines and are not
  approved.
- Cached country activity: rejected because stale data weakens the authoritative validation.
- Automatic retry of the creation request: rejected because a lost response after commit could
  create duplicates.

## R-014: Optional Outbox Record

**Decision**: Evaluate event configuration once. If recording is disabled or `party.created.v1`
is not enabled, the application creates no event intent. If enabled, the application creates a
technology-neutral event-recording intent containing event type, schema version, Party type,
tenant, audit context, and occurrence time. The persistence adapter maps that intent, after the ORM
assigns the Party ID, to exactly one `PENDING` outbox entity in the Party transaction with aggregate
type `PARTY`, aggregate version `0`, delivery version `0`, and payload containing only `partyType`.
Do not publish or connect to RabbitMQ in this feature.

The current DBML records uniqueness for tenant, aggregate type, aggregate ID, aggregate version,
and event type as `uq_party_outbox_event_identity` and constrains the creation payload through
`ck_party_outbox_created_event_shape`.

**Rationale**: The metadata already carries tenant, aggregate identity, and version. A payload with
only immutable `partyType` avoids names, dates, countries, audit identity, and other unnecessary
personal data while giving consumers minimal classification.

**Alternatives considered**:

- Full Party snapshot: rejected due to PII and coupling.
- Empty payload: rejected because immutable Party classification is useful and non-identifying.
- Publication during creation: rejected by feature scope and the approved outbox sequence.

**Pre-implementation gate**: PASS. Reviewed DBML commit
`a167e5bea93eeccbd4513c1fb91a6d9f08e2412d` records outbox uniqueness, the closed creation-event
shape, and `User-Id` audit semantics. The dated independent validation proved those rules on
PostgreSQL 18.4 and records all material findings as resolved.

## R-015: Verification Strategy

**Decision**: Use four evidence layers:

- Framework-free JUnit tests for domain factories, value objects, and invariants.
- Direct application-use-case tests with hand-written reactive port fakes.
- Quarkus integration tests against PostgreSQL 18 and a Geographic Reference HTTP stub.
- Packaged black-box API and OpenAPI conformance tests.

Use ArchUnit for dependency direction and prohibited technology checks. Exercise transaction
rollback using test-only database failure injection. Verify both tenants, all event modes, UUID
version 7, Flyway schema correspondence, event-loop execution, and exact logging/MDC propagation.

**Rationale**: This proves domain correctness, orchestration, real database behavior, contracts,
tenant isolation, non-blocking execution, and operational requirements independently.

**Alternatives considered**:

- H2: rejected because PostgreSQL enums, JSON, UUID version 7, exclusion constraints, triggers,
  and transactional behavior require PostgreSQL fidelity.
- Repository mocks for rollback: rejected because they cannot prove database atomicity.
- REST-only testing: rejected because failures would be slower and harder to localize.

## R-016: Dependency Set

**Decision**: Use Quarkus BOM-managed extensions for REST/Jackson, REST Client/Jackson, Hibernate
Reactive, reactive PostgreSQL, Hibernate Validator, SmallRye OpenAPI, context propagation, Flyway,
Micrometer Prometheus, OpenTelemetry, and the Flyway-only PostgreSQL JDBC boundary. Use JUnit,
Quarkus reactive test support,
RestAssured, ArchUnit, Testcontainers PostgreSQL, an HTTP stub, an OpenAPI parser, and a JSON Schema
2020-12 validator for tests.

**Rationale**: These dependencies directly support the selected architecture and evidence without direct production SQL access.

**Alternatives considered**:

- Additional repositories, generic mapping frameworks, or resilience frameworks: deferred until a
  concrete requirement cannot be met by the selected minimal set.

## R-017: Request Optionality and Bounds

**Decision**: Limit natural-person creation to 10 nationality entries. Omitted `nationalities`
means an empty collection and omitted `isPrimary` means `false`. For optional scalar fields,
omission and explicit JSON `null` both mean not supplied; required fields never accept null.

**Rationale**: The human-approved collection limit bounds geographic validation and persistence.
The absence/null rule maps cleanly to nullable DBML fields, while explicit defaults match the
approved data model.

**Alternatives considered**:

- Unbounded nationalities: rejected because request and dependency work would be unbounded.
- Treat every explicit null as malformed: rejected because it adds a distinct state not present in
  the approved domain or persistence model.

## R-018: Initial Contract Baseline

**Decision**: Treat the REST contract as the first internal v1 baseline and the JSON Schema as a
recording-only payload v1, not a publication contract. `partyType` and error-code enums are closed;
adding, removing, or reinterpreting values requires compatibility review and a versioning decision.
No external consumers are approved by this plan.

**Rationale**: An explicit first baseline avoids claiming compatibility with nonexistent prior
artifacts and prevents future enum changes from being treated as harmless additions.

**Alternatives considered**:

- Claim backward compatibility with the repository skeleton: rejected because it exposes no Party
  creation contract.
- Treat closed-enum additions as automatically compatible: rejected because generated consumers
  may fail on unknown values.

## R-019: Mandatory TDD Workflow

**Decision**: Implement every new or changed production behavior through a small
Red -> Green -> Refactor cycle:

1. Add the lowest-layer JUnit test that can prove one behavior and run it to verify that it fails
   for the expected missing behavior.
2. Add only enough production code to make the targeted test pass, then run the affected layer
   suite.
3. Refactor only while that suite remains green, then run the applicable architecture, contract,
   integration, and packaged checkpoints.

Domain cycles use framework-free JUnit tests. Application cycles use hand-written reactive port
fakes. API and geographic-adapter cycles use Quarkus tests and a controlled HTTP stub. Hibernate
Reactive persistence cycles use PostgreSQL 18 and Quarkus Vert.x reactive test support. Filtered
Gradle test execution is the inner loop; complete JVM and packaged suites are checkpoint gates.
Pure refactoring begins from a verified green baseline. Test infrastructure or compile-only
scaffolding may precede the first Red cycle but must contain no production behavior.

**Rationale**: Behavior-sized cycles provide faster failure localization than writing all tests or
all implementation in batches. Selecting the lowest capable layer preserves framework-free domain
tests while retaining real PostgreSQL, HTTP, API, and reactive evidence at adapter boundaries. The
explicit Red execution proves that a test can detect the missing behavior rather than merely
passing against code written first.

**Alternatives considered**:

- Tests after implementation: rejected because they do not demonstrate TDD.
- One large batch of tests before a complete user story: rejected because it weakens the short
  feedback loop and delays Green behavior.
- PostgreSQL or packaged tests for every domain cycle: rejected because the lowest capable layer
  gives faster, more precise evidence; boundary checkpoints still remain mandatory.
- A custom Gradle TDD task: rejected because targeted `test` execution and the existing full-suite
  tasks are sufficient.

## References

- [Quarkus Hibernate Reactive](https://quarkus.io/version/3.33/guides/hibernate-reactive)
- [Hibernate Reactive transactions](https://docs.hibernate.org/reactive/3.2/reference/html_single/#_transactions)
- [Hibernate UUID version 7 generator](https://docs.hibernate.org/orm/7.2/javadocs/org/hibernate/annotations/UuidGenerator.Style.html)
- [PostgreSQL 18 UUID functions](https://www.postgresql.org/docs/18/functions-uuid.html)
- [Quarkus Flyway](https://quarkus.io/version/3.33/guides/flyway)
- [Quarkus REST Client asynchronous support](https://quarkus.io/version/3.33/guides/rest-client#async-support)
- [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457)
- [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html)
- Postman collection `geographic-reference-service`, UID
  `15834347-d3591c82-bd52-46a9-973d-a7a102d4b9b3`
