# Data Model: Party Registration

**Date**: 2026-08-23
**Authority**: `docs/database/v1-scheme.dbml`, feature specification, approved planning decisions

## Model Boundaries

The Party aggregate represents tenant-scoped civil or legal identity. It does not represent a
customer, supplier, employee, account, authenticated user, tenant, address, contact, organization
hierarchy, ownership structure, or geographic catalog entry.

Three model families remain separate:

- **Domain model**: behavior and invariants, without framework annotations.
- **Application model**: creation command, trusted context, port outcomes, and creation result.
- **Persistence model**: Hibernate Reactive entities corresponding to the approved DBML.

API request/response types are defined in the OpenAPI contract and do not cross into the domain.
Creation uses a `NewParty` domain aggregate without persistence identity. IDs and foreign keys shown
below describe the persisted representation unless explicitly identified as domain data.

## Aggregate: Party

### Party Root

| Field | Type | Required | Creation Rule |
|-------|------|----------|---------------|
| `id` | UUID version 7 | Yes after persistence begins | Assigned by Hibernate when the root entity is persisted |
| `tenantId` | UUID | Yes | Comes only from the singular trusted `Tenant-Id` header |
| `type` | `NATURAL_PERSON` or `LEGAL_ENTITY` | Yes | Immutable after construction |
| `displayName` | String, max 300 | Yes | Derived by type; never accepted from the body |
| `recordStatus` | `DRAFT` | Yes | Always `DRAFT` at creation |
| `createdAt` | Instant | Yes | One trusted command timestamp |
| `createdBy` | String, 1..128 | Yes | Comes only from the singular trusted `User-Id` header |
| `updatedAt` | Instant | Yes | Same value as `createdAt` at creation |
| `updatedBy` | String, 1..128 | Yes | Same value as `createdBy` at creation |
| `version` | Long | Yes | Always `0` at creation |

### Composition

- A natural-person Party has exactly one `NaturalPersonDetails`, no `LegalEntityDetails`, and zero
  or more `PartyNationality` values.
- A legal-entity Party has exactly one `LegalEntityDetails`, no `NaturalPersonDetails`, and no
  nationalities.
- Exactly-one-detail and no-legal-nationality are enforced by the domain factory, application
  orchestration, persistence mapping, and tests.
- The current DBML deferred trigger still prevents an incompatible detail row and prevents both
  detail types; its scope is not expanded by this plan.

### Invariants

- `tenantId`, Party type, and audit subject are non-null during domain construction; persistence
  identity is assigned inside the ORM adapter before any dependent outbox entity is created.
- Type cannot change after creation.
- Display name is `givenNames` followed by `familyNames` for a natural person and `legalName` for a
  legal entity.
- Display name length must fit the 300-character persistence limit. A derived value exceeding the
  limit is invalid Party data; it is not silently truncated.
- Initial status is `DRAFT`; initial aggregate version is `0`.
- Initial aggregate construction does not count as a post-creation mutation for version increment
  purposes.

## Component: NaturalPersonDetails

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `partyId` | UUID version 7 | Persistence only | Foreign key assigned from the persisted root; domain composition holds no duplicate ID |
| `givenNames` | String, 1..200 | Yes | Non-empty |
| `familyNames` | String, 1..200 | Yes | Non-empty |
| `preferredName` | String, max 200 | No | Must fit persistence limit |
| `birthDate` | Date | No | Must not be after `dateOfDeath` when both exist |
| `dateOfDeath` | Date | No | Must not be before `birthDate` when both exist |
| `birthCountryCode` | ISO alpha-2 country code | No | Syntactically valid and confirmed active externally |
| Audit fields | Instant/String | Yes | Same command timestamp and `User-Id` as root |

Natural-person details are a one-to-one aggregate component and have no independent lifecycle in
this feature.

## Component: LegalEntityDetails

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `partyId` | UUID version 7 | Persistence only | Foreign key assigned from the persisted root; domain composition holds no duplicate ID |
| `legalName` | String, 1..300 | Yes | Non-empty; source of display name |
| `tradeName` | String, max 300 | No | Must fit persistence limit |
| `legalFormCode` | String, max 64 | No | Opaque jurisdiction-qualified code |
| `incorporationCountryCode` | ISO alpha-2 country code | Yes | Syntactically valid and confirmed active externally |
| `incorporatedOn` | Date | No | Must not be after `dissolvedOn` when both exist |
| `dissolvedOn` | Date | No | Must not be before `incorporatedOn` when both exist |
| Audit fields | Instant/String | Yes | Same command timestamp and `User-Id` as root |

Legal-entity details are a one-to-one aggregate component and have no independent lifecycle in this
feature.

## Component: PartyNationality

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `id` | UUID version 7 | Persistence only | Assigned by Hibernate when the nationality entity is persisted |
| `partyId` | UUID version 7 | Persistence only | Foreign key assigned from the persisted root; domain composition holds no duplicate ID |
| `countryCode` | ISO alpha-2 country code | Yes | Confirmed active externally |
| `isPrimary` | Boolean | Yes in domain | Request omission maps to the DBML default `false` |
| `validFrom` | Date | No | Open lower bound when absent |
| `validUntil` | Date | No | Open upper bound when absent; cannot precede `validFrom` |
| Audit fields | Instant/String | Yes | Same command timestamp and `User-Id` as root |

### Activity and Temporal Rules

A nationality is active for date `D` when its closed validity interval contains `D`; missing bounds
are open. Domain validation rejects duplicate active country intervals and overlapping active
primary intervals within the submitted aggregate.

The approved database design decision strengthens concurrent integrity with temporal exclusion:

- Intervals for the same Party and country cannot overlap.
- Primary intervals for the same Party cannot overlap across countries.

The current DBML records these rules as `ex_party_nationalities_country_validity` and
`ex_party_nationalities_primary_validity`. Independent database-contract validation passed for
DBML commit `a167e5bea93eeccbd4513c1fb91a6d9f08e2412d`. No migration may infer a change from this
document alone; Flyway design must use the reviewed DBML and pass its separate migration gate.

### Tenant Isolation

`party_nationalities` does not carry a separate tenant column in the current DBML. Tenant ownership
is inherited from the globally identified Party root. Creation writes nationalities only through
the new aggregate; future reads or mutations must scope through the Party root and trusted tenant.

## Entity: PartyCreationOutboxEvent

The stored event is infrastructure committed atomically with the Party; it is not a domain
aggregate and is not published by this feature. The application exposes only a technology-neutral
event-recording intent. The persistence adapter maps that intent to this entity after Hibernate
assigns the Party ID.

| Field | Type | Required | Creation Rule |
|-------|------|----------|---------------|
| `id` | UUID version 7 | Yes after persistence begins | Assigned by Hibernate when the outbox entity is persisted |
| `tenantId` | UUID | Yes | Same trusted tenant as Party |
| `aggregateType` | `PARTY` | Yes | Fixed for Party creation |
| `aggregateId` | UUID version 7 | Yes | New Party ID |
| `aggregateVersion` | Long | Yes | `0` |
| `eventType` | String | Yes | `party.created.v1` |
| `eventSchemaVersion` | Positive small integer | Yes | `1` |
| `payload` | JSON object | Yes | Closed object containing only `partyType` |
| `occurredAt` | Instant | Yes | Same command timestamp |
| `correlationId` | String, max 128 | No | Set only when an approved source exists |
| `causationId` | String, max 128 | No | Set only when an approved source exists |
| `status` | `PENDING` | Yes | Initial delivery state |
| `publishAttempts` | Non-negative integer | Yes | `0` |
| Attempt/publication fields | Various nullable | No | All absent at recording time |
| `createdAt` / `updatedAt` | Instant | Yes | Same command timestamp |
| `createdBy` / `updatedBy` | String, 1..128 | Yes | Same trusted `User-Id` for initial record |
| `version` | Non-negative long | Yes | `0` |

### Event Uniqueness

When event recording is enabled for `party.created.v1`, exactly one event may exist for the tuple:

```text
tenantId + aggregateType + aggregateId + aggregateVersion + eventType
```

The current DBML records this rule as `uq_party_outbox_event_identity`, defines trusted `User-Id`
audit semantics, and constrains the closed creation payload through
`ck_party_outbox_created_event_shape`. Independent database-contract validation remains required
before Flyway migration design.

### Event Payload

```json
{
  "partyType": "NATURAL_PERSON"
}
```

The payload excludes tenant ID, Party ID, version, timestamps, names, dates, country codes,
nationalities, audit subject, and official identifiers because metadata already carries aggregate
identity and unnecessary personal data is prohibited.

## External Value: CountryReferenceValidation

Country data is not persisted as an owned entity. The application sends the distinct set of
provided ISO alpha-2 codes and the trusted tenant, audit-subject, and process context to an outbound
port and receives one of these outcomes:

- `AllActive`: every code explicitly exists and is active.
- `InvalidReferences`: one or more codes are explicitly unknown or inactive.
- `ValidationUnavailable`: no complete authoritative decision was obtained.

Only `AllActive` permits persistence. Country records remain owned by Geographic Reference Service
and no cross-service foreign key is introduced.

The infrastructure adapter maps this request to one
`GET /api/v1/countries/by-alpha2/{alpha2Code}` call per distinct code under Postman collection
`15834347-d3591c82-bd52-46a9-973d-a7a102d4b9b3`. It maps only `data.status: ACTIVE` to an active
reference. `DRAFT`, `DEPRECATED`, and `RETIRED` are inactive; the documented `404` not-found outcome
is unknown. The adapter propagates trusted `Tenant-Id`, `User-Id`, and `Process-Id` unchanged to each
provider lookup and verifies the echoed process identifier. It never generates a missing process
identifier; those three values are its complete business-context header contract.

## Application Inputs and Outputs

### TrustedCreationContext

- `tenantId`: parsed UUID from exactly one `Tenant-Id` header.
- `userId`: non-empty string up to 128 characters from exactly one `User-Id` header.
- `processId`: parsed UUID from exactly one `Process-Id` header and propagated unchanged.
- `occurredAt`: trusted application clock value used consistently for the command.
- `registrationDate`: UTC date derived once from the same clock for nationality activity checks.

This context is never accepted from the request body.

### CreatePartyCommand

- Contains exactly one Party type and its matching detail variant.
- May contain nationalities only for a natural person.
- Contains no more than 10 nationalities.
- Does not contain Party ID, tenant ID, user ID, display name, record status, audit fields, or
  version.

### CreatePartyResult

- `partyId`: generated Party UUID version 7.
- `version`: `0`.

The result is emitted only after successful commit.

### CreatePartyPersistencePort

- Accepts `NewParty` plus an optional `PartyCreationEventIntent`.
- Returns `Uni<CreatePartyResult>` after commit.
- Exposes no Hibernate entity, session, transaction, generated child ID, table, or SQL concept.
- The infrastructure adapter owns root ID generation, foreign-key propagation, outbox mapping, and
  transaction execution.

### PartyCreationEventIntent

- Exists only when event recording and `party.created.v1` are enabled.
- Contains tenant, event type, schema version, Party type, occurrence time, and audit context.
- Contains no ORM entity, table name, delivery status, generated event ID, or aggregate ID.
- Is mapped by the persistence adapter after the Party entity receives its generated ID.

## State Transitions

### Party

```text
not present -> DRAFT(version 0)
```

No Party update, type change, status transition, or deletion is in scope.

### Outbox Event

```text
not present -> PENDING(version 0)
```

Publication, retry, failure, published status, and cleanup transitions are outside scope.

## Atomicity Boundary

One reactive database transaction contains:

1. Party root insert.
2. Exactly one matching details insert.
3. Zero or more nationality inserts for a natural person.
4. Zero or one enabled creation-event insert.
5. Flush and commit.

Any mapping, persistence, constraint, flush, or commit failure produces no rows from the command.
Geographic validation and request validation complete before this transaction starts.

## Persistence Mapping Rules

- Hibernate entities live only in infrastructure.
- PostgreSQL enum columns use explicit named-enum mappings; ordinals are prohibited.
- Outbox payload uses an explicit JSON mapping.
- Relationships use aggregate-owned persistence mappings and never expose lazy ORM proxies outside
  the adapter.
- Production application persistence uses Hibernate Reactive ORM only; direct/native SQL is
  prohibited.
- Flyway migrations and test administration are the only approved SQL boundaries.
- Hibernate schema generation is disabled; schema validation must pass against Flyway output.
