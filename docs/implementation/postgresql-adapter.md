# Reactive PostgreSQL adapter construction contract

## Stable construction seam

Runtime composition and independent PostgreSQL black-box tests construct all six application port views through:

```java
PostgreSqlAdapterBundle ports = PostgreSqlAdapters.create(pool, settings, observer);
```

`pool` is an already-created reactive Vert.x `io.vertx.sqlclient.Pool`. `settings` is a
`PostgreSqlAdapterSettings` containing only bounded, non-secret operational values:

- a positive outbox claim lease;
- a positive maximum claim batch size;
- a positive maximum Party/Scheme association-result count; and
- a non-secret publisher actor identifier of at most 128 characters for outbox audit columns.

The two-argument `create(pool, settings)` overload installs a no-op transaction observer. The returned
`PostgreSqlAdapterBundle` exposes `PartyQueryPort`, `PartyUnitOfWorkPort`, `IdentifierQueryPort`,
`IdentifierUnitOfWorkPort`, query-only `IdentifierSchemeCatalogPort`, and `OutboxStorePort`. Tests and
bootstrap code do not reflect into adapter internals or use persistence records.

## Synthetic T037 fixture

T037 may create a Vert.x PostgreSQL pool against its migrated synthetic PostgreSQL 18 Testcontainer and use,
for example, a short positive claim lease, small positive result/batch bounds, and a synthetic actor such as
`test-outbox-publisher`. These are fixture values, not production defaults. Database URL, username and password
come from the disposable container and are never committed. Apply the existing Flyway migrations with the
migration role before constructing the runtime-role pool.

The fixture owns the pool lifecycle. It must close the pool after each fixture or test class, then stop the
container. The adapter owns neither the pool nor Flyway and performs no runtime schema creation or migration.

## Transaction instrumentation

`PostgreSqlTransactionObserver.opened(operation)` runs after Vert.x has opened the reactive transaction.
`completed(operation)` runs after commit or rollback completes. A T037 observer can maintain an active count and
assert that `claimEligible` completes (and the count returns to zero) before broker I/O begins. The same seam can
verify that no remote Geographic or RabbitMQ work overlaps a state-changing transaction. Observer callbacks
receive only a bounded operation name and no tenant, identifier, payload, SQL or secret data.

## Persistence behavior

- Every Party, nationality and Party Identifier read or mutation is tenant-qualified directly or through the
  tenant-qualified Party root.
- Party, Party-detail and nationality query rows map all four authoritative audit columns to the shared
  boundary-neutral `AuditFacts` value. No audit fact is fabricated from mutation input.
- Nationality query rows derive the explicit output `active` fact exactly as `valid_until IS NULL`; the mapping
  reads no clock and persists no additional active column.
- Application-supplied canonical display names are persisted with their matching detail source fields in one
  transaction; the adapter contains no display-name normalization policy.
- Aggregate mutation, optimistic version increment and minimal V1 outbox payload insertion are atomic.
- Party and Party Identifier mutations return their aggregate resource ID. Nationality add, update and end
  mutations return the nationality resource ID while their outbox rows retain `PARTY` and the owning Party ID as
  aggregate identity.
- Outbox claim uses a bounded `FOR UPDATE SKIP LOCKED` transaction and returns only after commit. Outcome writes
  use event ID plus claimed version, so stale outcomes cannot overwrite later state.
- Identifier Scheme access is `SELECT`-only. No Scheme mutation SQL or method exists.
- SQL is parameterized, ordinary pages are bounded by `PageRequest`, exact protected lookup is indexed and
  limited to one row, and Party/Scheme association results use the configured bound.
- Named PostgreSQL integrity failures are translated to stable application categories without exposing SQL,
  SQLSTATE, constraint names or protected values to callers.

## Party-detail construction boundary

Party create and detail-update mutations consume the unaudited application-owned `PartyDetailsInput`. Query
operations return the distinct audited `PartyDetailsView`. This keeps caller-supplied source facts separate from
database-authoritative audit output while preserving canonical display-name derivation in Domain/Application.
