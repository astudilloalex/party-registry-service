## Why

The first delivery phase of the Party Registry must provide the approved API behavior for managing natural persons. Consumers need a reliable way to create, retrieve, and update natural-person records while receiving consistent validation and error responses.

A party must have one unambiguous classification. Natural-person operations must never create natural-person details for a legal entity or expose a legal entity as a natural person.

## What Changes

- Provide creation of natural persons through `POST /v1/natural-person` as parties classified exclusively as `NATURAL_PERSON`.
- Provide retrieval of natural-person details through `GET /v1/natural-person/{partyId}`.
- Provide complete replacement of natural-person details through `PUT /v1/natural-person/{partyId}`.
- Provide partial updates of natural-person details through `PATCH /v1/natural-person/{partyId}`.
- Validate required request headers, identifiers, concurrency preconditions, idempotency inputs, request bodies, field constraints, date consistency, and country codes as applicable to each operation.
- Reject attempts to use natural-person operations with a party classified as `LEGAL_ENTITY`, preserving mutual exclusivity between natural-person and legal-entity classifications.
- Return the success and error envelopes, HTTP statuses, and stable response codes defined by the approved OpenAPI contract.

## User Stories

- As a registry operator, I want to create a validated natural-person record, so that an individual can be represented consistently in the Party Registry.
- As an authorized consumer, I want to retrieve and update a natural person's details, so that the registry remains accurate over time.
- As a data steward, I want natural-person and legal-entity classifications to be mutually exclusive, so that a party cannot hold contradictory identities.

## Capabilities

### New Capabilities

- `natural-person-management`: Create, retrieve, replace, and partially update natural persons with contract validation, concurrency and idempotency behavior, standard API responses, and enforcement of natural-person versus legal-entity type exclusivity.

### Modified Capabilities

None.

## Impact

The change introduces the approved `/v1/natural-person` API operations and their request, response, validation, and error behavior. It affects clients that create or maintain natural-person records and establishes the party-type consistency rule that protects natural-person data from being associated with legal entities.

## Out of Scope

- Legal-entity endpoint implementation.
- Generic party lifecycle and search endpoints.
- Nationality management endpoints.
- Identifier-scheme and party-identifier endpoints.
- Changes to the approved OpenAPI contract beyond clarifying requirements needed to implement its natural-person operations.
