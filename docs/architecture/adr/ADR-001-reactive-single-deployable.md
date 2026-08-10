# ADR-001: Reactive execution in one cohesive application deployable

## Status

Proposed

## Context

**CONFIRMED REQUIREMENT:** NFR-006 requires a reactive REST API. The manifest selects Java 25/Quarkus, strict Clean Architecture and execution model `auto`; the stack profile requires blocking isolation. The bounded context owns one cohesive data/transaction boundary and includes API and outbox publication responsibilities.

## Decision Drivers

- Preserve the approved reactive API requirement end to end.
- Satisfy the pilot concurrency/latency profile on bounded resources.
- Keep local PostgreSQL transactions simple and observable.
- Avoid runtime boundaries with no independent ownership, scaling or security justification.

## Considered Options

1. One reactive application deployable containing REST and an internal publisher.
2. Separate API and publisher deployables over the same database.
3. Blocking API with worker-thread execution.

## Decision

**TO-BE DECISION:** select option 1. REST, PostgreSQL access, Geographic client and RabbitMQ publication are non-blocking end to end. The publisher is a separately scheduled component, not a separate deployment unit. Unavoidable blocking or CPU-heavy adapter work must use explicit bounded isolation with context propagation and tests.

## Consequences

### Positive

- One artifact, release and operational unit.
- No hidden distributed transaction or deployment coupling.
- Reactive resource use aligns with the approved API and pilot validation.

### Negative

- API and publisher share process resources and failure domain.
- Reactive transaction/context discipline is required across adapters.

### Risks

Publisher backlog or crypto work could starve request processing if concurrency is unbounded.

## Security and Data Impact

No new data store or trust zone is introduced. Bounded concurrency and strict context propagation protect tenant and sensitive-operation context.

## Operational Impact

Expose separate API, pool, publisher/backlog and dependency saturation metrics. Liveness must not be coupled to transient RabbitMQ failure; readiness follows mandatory capability rules.

## Validation

Architecture dependency tests, blocking-call detection/review, reactive adapter integration tests, and NFR-008/009 performance evidence.

## Reversal or Replacement Strategy

Split the publisher only after measured independent scaling/failure-isolation evidence and an approved ADR that preserves database ownership and outbox claiming semantics.

## Traceability

`.factory/project.yaml:8-25`; requirements NFR-004..009, NFR-011, OR-006; supplied Quarkus profile; `docs/architecture/solution-architecture.md` sections 6, 8, 9 and 14.
