# ADR-001: Hibernate Reactive Execution Model

- **Status**: Accepted
- **Proposed on**: 2026-08-23
- **Decision owner**: Alex Astudillo — Architecture Owner
- **Technical reviewers**: Not required — single-maintainer project
- **Scope**: Party Registry Service, beginning with Party Registration
- **Supersedes**: None
- **Related readiness gate**: Hibernate Reactive execution-model ADR

## Context

Party Registry Service is an internal Quarkus service whose constitution requires end-to-end
non-blocking I/O, Mutiny composition, and Hibernate Reactive ORM for production persistence. Party
Registration must validate trusted request context and Geographic Reference data, then persist a
Party aggregate and an optional `party.created.v1` outbox record atomically.

Selecting Hibernate Reactive has consequences beyond choosing a persistence dependency. It fixes
the request execution model, session and transaction ownership, resource lifetime, failure and
cancellation behavior, debugging practices, operational telemetry, test strategy, and supported
packaging baseline. Those consequences must be explicit before implementation begins.

The following constraints are already authoritative:

- Production application I/O is non-blocking and returns or composes `Uni` or `Multi`.
- Production persistence uses Hibernate Reactive ORM through its Mutiny API.
- Direct or native SQL, direct PostgreSQL Reactive Client access, blocking Hibernate ORM, and JDBC
  are prohibited in production application code.
- Flyway is the only schema migration mechanism and uses an isolated JDBC boundary outside business
  request flows.
- Geographic Reference validation completes before a database transaction begins.
- Party, matching details, nationalities, and the optional outbox record commit atomically.
- Writes sharing one Hibernate Reactive session are sequential, never parallel.
- Success is returned only after transaction commit.
- Java 25, Quarkus 3.33.3, PostgreSQL 18, internal-only reachability, and rootless container
  compatibility form the deployment baseline.

## Decision Drivers

- Preserve a non-blocking execution chain from the REST adapter to every I/O adapter.
- Meet the constitutional requirement to use ORM rather than direct database-client access.
- Prevent accidental event-loop blocking and unsafe reactive-session use.
- Keep database transactions short and free from remote network latency.
- Guarantee all-or-nothing aggregate and transactional-outbox persistence.
- Keep Flyway schema ownership separate from application persistence.
- Make failures, cancellations, context propagation, and asynchronous debugging operable.
- Define a packaging baseline that can be verified before production deployment.

## Decision

### 1. End-to-End Reactive Execution

API adapters, application services, application I/O ports, and infrastructure adapters will return
or compose Mutiny `Uni` for zero-or-one asynchronous results and `Multi` only for genuine streams.
The Party domain remains framework-free and therefore does not expose Mutiny types.

Production request processing must not:

- Call `await`, `join`, `get`, sleep, spin, or use an equivalent synchronous wait.
- Invoke a blocking API from a reactive callback.
- Move blocking I/O to a worker thread merely to conceal a blocking implementation.
- Convert the creation operation into a blocking endpoint.
- Depend on thread-local identity remaining stable across asynchronous boundaries.

Reactive stages will propagate failures downstream. Recovery is allowed only at the boundary that
owns the relevant failure policy; it must not convert an uncommitted or rolled-back operation into
a successful Party creation response.

### 2. Session Ownership and Concurrency

Hibernate Reactive sessions are infrastructure resources and must not cross the persistence-adapter
boundary. Persistence entities, lazy proxies, and session-bound collections must not escape into
the application or domain layers.

Each creation transaction owns one reactive session for one reactive chain. The session must not be:

- Shared between requests.
- Cached in application or domain objects.
- Used concurrently by multiple asynchronous branches.
- Used by parallel combinators for writes belonging to the same transaction.

Concurrency between independent requests is handled by separate sessions and transactions obtained
through the Quarkus-managed Hibernate Reactive infrastructure. Concurrency inside one aggregate
write is expressed as ordered reactive composition.

### 3. Creation and Transaction Boundary

Party creation executes in this order:

1. Validate exactly one trusted `Tenant-Id`, `User-Id`, and `Process-Id` at the API boundary.
2. Map and validate the closed request variant.
3. Construct and validate the framework-free Party aggregate.
4. Resolve all distinct country codes through Geographic Reference.
5. Evaluate the event-recording policy and create an optional technology-neutral event intent.
6. Enter one `Mutiny.SessionFactory.withTransaction` callback in the persistence adapter.
7. Map and persist the Party root with its matching details and nationalities.
8. After Hibernate assigns the Party UUID version 7, map and persist the optional outbox entity.
9. Flush and commit.
10. Emit the successful creation result only after commit completes.

Steps inside the transaction are composed sequentially. No Geographic Reference request or other
remote I/O occurs while the transaction is open.

Any mapping, persistence, constraint, flush, cancellation, or commit failure terminates the chain
with failure and leaves no rows from the command. Party and outbox persistence must not be split
into separate transactions. Automatic write-transaction retries are not introduced without a
separate, approved idempotency and retry decision.

### 4. Database Access and Schema Ownership

Production application persistence is performed only through Hibernate Reactive ORM using the
Mutiny API. The reactive PostgreSQL driver is an implementation detail managed beneath Hibernate
Reactive.

The following are prohibited in production application code:

- Direct SQL or native SQL queries.
- Direct use of the PostgreSQL Reactive Client.
- Blocking Hibernate ORM.
- JDBC connections, data sources, templates, or repositories.
- Application-driven schema creation or mutation.

Flyway remains the sole migration boundary. It may use the PostgreSQL JDBC driver only for startup
or explicitly invoked operational migration work, never for a request or business-event flow. Its
connection configuration must not be injected into persistence adapters.

Hibernate schema generation is disabled. Hibernate schema validation must succeed against the
schema produced by approved Flyway migrations derived from the independently validated DBML.

### 5. Resource Model

Quarkus owns the reactive datasource, connection pool, Hibernate Reactive session factory, and
their lifecycles. Application code must not instantiate an additional PostgreSQL client or manage
connections manually.

Database resources are acquired only when the local persistence stage begins. Completing request,
domain, and Geographic Reference validation first limits transaction and connection hold time.

Pool sizing, acquisition timeouts, and transaction limits are externalized configuration. Their
production values require environment capacity evidence; this ADR does not invent fixed values.
Configuration changes must preserve bounded resource use and must be supported by integration or
load evidence appropriate to their risk.

Cancellation or timeout of the transaction chain must release the session and connection through
the managed reactive lifecycle and must result in rollback rather than partial success. No code may
swallow cancellation or continue using a session after its transaction terminates.

### 6. Failure Handling and Debugging

Asynchronous failures are diagnosed through the reactive stage and boundary that produced them,
not by assuming a stable request thread. Internal exception causes are preserved for diagnostics,
while API responses use the approved sanitized RFC 9457 representation and never expose SQL,
provider payloads, or stack traces.

Outer adapters establish and propagate the mapped diagnostic context across Mutiny boundaries.
Whenever available, `processId`, `userId`, and `tenantId` use this exact human-readable log pattern:

```text
%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [user=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```

Debugging and error logs must not include complete identifiers, names, dates, country lists,
provider payloads, secrets, or other unnecessary personal data. Domain code remains free of
logging, tracing, and metrics dependencies.

### 7. Operational Consequences

Quarkus-provided HTTP, REST Client, Hibernate Reactive, Micrometer Prometheus, and OpenTelemetry
instrumentation form the observability baseline. Instrumentation belongs to API, bootstrap, and
infrastructure boundaries, not the domain.

Operational verification must make these conditions observable:

- Database connection or transaction failures do not produce partial aggregate state.
- Successful API responses are emitted only after commit.
- Reactive context and correlation values survive API, application, Geographic Reference, and
  persistence boundaries.
- Pool pressure, transaction failures, outbound failures, and latency can be diagnosed without
  high-cardinality personal-data labels.
- Startup fails rather than running against an incompatible schema when Flyway or Hibernate schema
  validation fails.

Blind retries are not part of this decision. Outbound and persistence retry behavior requires an
explicit policy that addresses idempotency, deadlines, cancellation, and duplicate-event risk.

### 8. Packaging and Deployment Consequences

The supported production baseline for this decision is a Java 25 Quarkus JVM application packaged
as the repository's standard Quarkus application and deployed in an internal Linux-hosted,
rootless-compatible container.

The runtime artifact includes both:

- Reactive PostgreSQL and Hibernate Reactive dependencies for application persistence.
- The JDBC PostgreSQL dependency required only by the isolated Flyway operational boundary.

Including the JDBC driver does not authorize JDBC use in production application code. Architecture
tests and dependency reviews must preserve that separation.

Native executable and uber-jar packaging are not acceptance baselines for Party Registration.
Adopting either as a production requirement requires separate compatibility evidence for Hibernate
Reactive, the reactive driver, Flyway, reflection/resource configuration, observability, and the
packaged integration-test suite. Packaging must not change internal-only network reachability or
the reactive execution rules.

## Enforcement and Verification

The implementation must provide automated evidence for this ADR:

- Framework-free unit tests for the domain.
- Application tests using hand-written reactive port fakes.
- Quarkus and Vert.x reactive tests proving event-loop continuity and absence of blocking waits.
- PostgreSQL 18 integration tests proving atomic commit and rollback for failures at root, details,
  nationalities, outbox, flush, and commit boundaries.
- Tests proving Geographic Reference calls finish before the database transaction starts.
- Tests proving Party and outbox writes are ordered and use one transaction.
- Tests proving success is observed only after commit.
- Architecture tests rejecting blocking APIs, blocking Hibernate ORM, direct/native SQL, direct
  PostgreSQL Reactive Client access, and JDBC outside the Flyway boundary.
- Flyway-from-empty and Hibernate schema-validation checks.
- Packaged black-box tests through `quarkusIntTest`.
- Logging and context-propagation tests for the exact required pattern and MDC values.

Every new or changed production behavior follows a recorded Red -> Green -> Refactor cycle. Passing
tests written after the implementation do not satisfy that requirement.

## Consequences

### Positive

- Request processing remains non-blocking from HTTP through persistence.
- ORM mappings preserve the approved model without direct database-client coupling.
- Remote latency does not consume a connection inside the creation transaction.
- Aggregate and outbox persistence have one explicit atomicity boundary.
- Session ownership and sequential composition rules prevent unsafe concurrent session access.
- Flyway and runtime persistence have distinct, reviewable responsibilities.
- Context propagation, telemetry, and packaged tests make asynchronous failures diagnosable.

### Negative and Trade-offs

- Developers must understand Mutiny composition, reactive session constraints, and asynchronous
  failure propagation.
- Debugging is more dependent on stage-level context, traces, and MDC than on linear stack traces.
- JDBC remains in the packaged dependency graph for Flyway and must be policed so it does not leak
  into application code.
- Integration tests require real PostgreSQL behavior; H2 and repository mocks cannot prove the
  required transaction semantics.
- Sequential writes can be less superficially parallel, but they preserve session safety and
  aggregate consistency.
- JVM packaging is the approved baseline; native or alternate packaging requires additional work
  before it can be treated as supported.

### Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| A blocking call reaches the event loop | Architecture tests, reactive execution probes, and review rejection of worker-offloading workarounds |
| A session is used concurrently | Keep the session inside one transaction callback and compose writes sequentially |
| A remote call holds a database resource | Complete Geographic Reference validation before `withTransaction` |
| Party commits without its enabled outbox record | Persist both in one transaction and inject failures at every persistence boundary |
| JDBC leaks from Flyway into application code | Isolated configuration plus architecture tests that prohibit `java.sql` and JDBC outside migration code |
| Reactive context is lost | Context-propagation tests across every adapter and mandatory MDC verification |
| Sensitive data appears in diagnostics | Sanitized RFC 9457 mapping, bounded telemetry attributes, and logging tests |
| Packaging behaves differently from development | Run packaged `quarkusIntTest` against PostgreSQL 18 and controlled HTTP dependencies |

## Alternatives Considered

### Blocking Hibernate ORM or JDBC

Rejected because it violates the service constitution and would require blocking request execution
or worker offloading.

### Direct PostgreSQL Reactive Client

Rejected because the approved persistence boundary requires Hibernate Reactive ORM and explicit
domain-to-persistence mapping.

### Mixed Blocking and Reactive Persistence

Rejected because it creates two execution and transaction models, makes resource ownership
ambiguous, and permits blocking behavior to leak into request flows.

### Geographic Reference Calls Inside the Transaction

Rejected because remote latency and failure would hold database connections and extend transaction
duration without improving atomicity.

### Parallel Writes on One Reactive Session

Rejected because a reactive session is not a parallel-work boundary and aggregate writes require
deterministic sequencing.

### Separate Party and Outbox Transactions

Rejected because a Party could commit without its required outbox record or an outbox record could
exist for an uncommitted Party.

### Hibernate-Managed Schema Creation or Update

Rejected because Flyway is the only approved schema evolution mechanism and the DBML is the
authoritative database contract.

## Approval Record

This ADR does not close the implementation-readiness gate while its status is `Proposed` or any
required approval field remains `TBD`.

| Field | Requirement | Value |
|-------|-------------|-------|
| Architecture owner | Required | Alex Astudillo |
| Technical reviewer | Optional | Not required — single-maintainer project |
| Approval date | Required | 2026-08-23 |
| Review or Pull Request | Required | TBD |
| Reviewed ADR revision | Required | TBD |
| Decision outcome | Required | APPROVED |
| Material conditions or findings | Required; record `None` if there are no findings | None |

To accept this ADR, the architecture owner must review the complete decision, resolve every
material finding, change the status to `Accepted`, set the decision outcome to `APPROVED`, and
record dated, traceable review evidence for the final ADR revision.

## References

- [Party Registry Service Constitution](../../.specify/memory/constitution.md)
- [Party Registration implementation plan](../../specs/001-party-registration/plan.md)
- [Party Registration research decisions](../../specs/001-party-registration/research.md)
- [Party Registration data model](../../specs/001-party-registration/data-model.md)
- [Party Registration quickstart and verification](../../specs/001-party-registration/quickstart.md)
- [Party Registration traceability](../../specs/001-party-registration/traceability.md)
- [Party Registration implementation readiness](../../specs/001-party-registration/implementation-readiness.md)
- [Authoritative Party Registry DBML](../database/v1-scheme.dbml)
- [Quarkus Hibernate Reactive guide](https://quarkus.io/version/3.33/guides/hibernate-reactive)
- [Hibernate Reactive transactions](https://docs.hibernate.org/reactive/3.2/reference/html_single/#_transactions)

## Revision History

| Date | Status | Change |
|------|--------|--------|
| 2026-08-23 | Proposed | Initial execution-model decision prepared for architecture review |
