# ADR-002: Local transaction with confirmed at-least-once outbox publication

## Status

Proposed

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

**TO-BE DECISION:** select option 1. A use case records the mutation and required event in one local PostgreSQL transaction. The internal publisher claims eligible records using the DBML-prescribed concurrency mechanism, publishes persistent messages, and changes delivery state only after classifying the confirmation outcome.

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

Transaction atomicity, positive/negative/unknown confirmation, crash/restart, concurrent claim, same-ID retry, FAILED recovery, payload minimisation and performance tests.

## Reversal or Replacement Strategy

Replacement requires a new approved consistency contract, event migration/coexistence plan and ADR; direct dual writes remain prohibited.

## Traceability

DBML `party_outbox_events` and notes at lines 331-397; BR-013/014/028; FR-008/009/050; IR-002..004; VR-015..018; NFR-002/003.
