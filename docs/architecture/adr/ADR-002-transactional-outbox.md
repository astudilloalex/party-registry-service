# ADR-002: Local transaction with confirmed at-least-once outbox publication

## Status

Proposed

| Field | Value |
|---|---|
| Owner | `solution-architecture-agent` (proposal author; approval remains with the independent architecture gate) |
| Decision date | 2026-08-10 |
| Decision version | 0.6 |
| Supersession | None |

## Context

**CONFIRMED REQUIREMENT:** approved Party and Party Identifier mutations and their outbox records commit atomically. RabbitMQ delivery is persistent, confirmation-based and at least once. Transient/unknown outcomes retain the same event for retry; non-recoverable failures become operator-visible `FAILED`; no Party Registry DLQ, discard or purge is approved.

## Decision Drivers

- Preserve business-state/event consistency.
- Avoid distributed transactions.
- Preserve event identity through uncertainty and recovery.
- Keep consumer-side queue and idempotency ownership external.

## Considered Options

1. DBML-governed local outbox and asynchronous confirmed publication.
2. Publish directly before or after the business database commit.
3. Distributed transaction across PostgreSQL and RabbitMQ.

## Decision

**TO-BE DECISION:** select option 1. A use case records the mutation and required event in one local PostgreSQL transaction. Publication then uses three strictly separated boundaries:

1. A short claim transaction selects a bounded eligible `PENDING` batch with `SELECT ... FOR UPDATE SKIP LOCKED`, reserves each row by updating only existing `next_attempt_at`, `last_attempt_at`, `publish_attempts` and `version` semantics, and commits. The committed version is retained as the claim token and every row lock is released.
2. Only after that commit, the publisher serializes, sends the persistent message and waits for a bounded RabbitMQ confirmation with no open database transaction or lock.
3. A separate outcome transaction updates by event ID plus claimed version: positive confirmation records `PUBLISHED`; non-recoverable error records `FAILED`; transient/negative/timeout/unknown outcome leaves `PENDING` eligible under bounded backoff.

Crash after the claim commit causes eligibility to return after `next_attempt_at`; crash or timeout after broker acceptance may cause duplicate delivery, always with the same event ID. A stale publisher cannot overwrite a later outcome because its version predicate fails. This sequence invents no claim-owner column, status, table, lock store or external lease. Database validation must stop and report a conflict if the DBML's existing eligibility/version fields cannot safely implement it.

## Rationale

The local outbox is the only considered option that atomically preserves business state and publication intent without coupling PostgreSQL commit to RabbitMQ availability or introducing a distributed transaction. Separating claim, broker I/O and outcome transactions prevents remote waits under database locks. Existing DBML eligibility and version fields provide a proposed bounded claim token while preserving the approved same-event at-least-once semantics; database validation remains the authority on whether those physical semantics are sufficient.

## Consequences

### Positive

- No lost event after a committed represented mutation.
- Business transaction is independent from later RabbitMQ availability.
- Crash/restart and duplicate delivery have deterministic identity semantics.

### Negative

- Publication is eventually consistent and consumers must deduplicate.
- Backlog monitoring and authorized failed-event recovery are operational obligations.

### Risks

Unbounded publisher concurrency can overload the database/broker; incorrect outcome classification can lose or duplicate events.

## Security and Data Impact

Event payloads are minimized/versioned and never include full identifiers. Outbox is service-owned internal infrastructure with no public CRUD.

## Operational Impact

Monitor oldest age, status counts, attempts, confirmation outcomes and lag. Retry attempts have bounded timeout/concurrency and configurable backoff/jitter; no invented maximum-attempt/discard policy is introduced.

## Validation

Transaction atomicity, positive/negative/unknown confirmation, crash/restart, concurrent bounded claim, assertion that the claim commit and lock release precede all broker I/O, optimistic stale-outcome rejection, same-ID retry, FAILED recovery, payload minimisation and performance tests.

## Reversal or Replacement Strategy

Replacement requires a new approved consistency contract, event migration/coexistence plan and ADR; direct dual writes remain prohibited.

## Traceability

DBML `party_outbox_events` and notes at lines 353-419; BR-013/014/028; FR-008/009/050; IR-002..004; VR-015..018; NFR-001..003; `docs/architecture/solution-architecture.md` sections 11, 12 and 21.1; LikeC4 relations `eventPublication -> outboxStorePort`, `eventPublication -> eventPublisherPort`, adapter-to-implemented-port relations, and `rabbitAdapter -> rabbitMq`.
