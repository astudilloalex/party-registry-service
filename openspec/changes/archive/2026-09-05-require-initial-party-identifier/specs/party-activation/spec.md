## Purpose

This capability defines when a tenant-scoped Party may become active. It ensures that activation requires a qualifying verified official identifier and that stale or concurrent activation attempts cannot partially change Party state.

## ADDED Requirements

### Requirement: Activation requires a qualifying verified identifier

**User Story:** As a data steward, I want only sufficiently identified Parties to become active, so that active registry records have verified identity evidence.

WHILE a Party is `DRAFT`, WHEN activation is requested,
THE Party Registry SHALL require at least one identifier associated with the Party that is `VERIFIED`, not expired on the request evaluation date, and associated with a scheme compatible with the Party type.

IF a `DRAFT` Party has no qualifying verified identifier,
THEN THE Party Registry SHALL reject activation with HTTP `422` and code `unprocessable-entity` and SHALL leave the Party in `DRAFT`.

#### Scenario: Draft Party with verified identifier is eligible

- **GIVEN** a `DRAFT` Party has a `VERIFIED`, non-expired identifier whose scheme is compatible with its type
- **WHEN** activation is requested with the current Party version
- **THEN** the identifier satisfies the activation identity precondition

#### Scenario: Pending identifier does not permit activation

- **GIVEN** a `DRAFT` Party has only a `PENDING_VERIFICATION` identifier
- **WHEN** activation is requested
- **THEN** the response has HTTP status `422` and code `unprocessable-entity`
- **AND** the Party remains `DRAFT` at its current version

#### Scenario: Expired verified identifier does not permit activation

- **GIVEN** a `DRAFT` Party has only a `VERIFIED` identifier whose expiration date is earlier than the request evaluation date
- **WHEN** activation is requested
- **THEN** the response has HTTP status `422` and code `unprocessable-entity`
- **AND** the Party remains `DRAFT`

#### Scenario: Incompatible verified identifier does not permit activation

- **GIVEN** a `DRAFT` Party has only a `VERIFIED` identifier whose scheme is incompatible with the Party type
- **WHEN** activation is requested
- **THEN** the response has HTTP status `422` and code `unprocessable-entity`
- **AND** the Party remains `DRAFT`

### Requirement: Party activation transition

**User Story:** As a registry operator, I want eligible draft Parties activated predictably, so that their lifecycle state accurately represents registration readiness.

WHEN an eligible `DRAFT` Party is activated with its current version,
THE Party Registry SHALL change its record status to `ACTIVE`, increment its aggregate version by exactly one, and return HTTP `200` with code `successful` and the updated Party representation.

IF the requested transition from the Party's current lifecycle state is not permitted,
THEN THE Party Registry SHALL reject the request with HTTP `409` and code `conflict` without changing the Party.

#### Scenario: Eligible draft Party becomes active

- **GIVEN** a `DRAFT` Party satisfies the verified-identifier precondition
- **AND** the request supplies the current Party version
- **WHEN** `POST /v1/parties/{partyId}/activate` is submitted
- **THEN** the response has HTTP status `200`, body status `200`, and code `successful`
- **AND** the returned Party status is `ACTIVE`
- **AND** the returned Party version is the prior version plus one

#### Scenario: Invalid lifecycle transition is rejected

- **GIVEN** a Party is in a state from which activation is not permitted
- **WHEN** activation is requested
- **THEN** the response has HTTP status `409` and code `conflict`
- **AND** the Party status and version remain unchanged

### Requirement: Activation optimistic concurrency

**User Story:** As an API consumer, I want activation protected by the expected Party version, so that concurrent lifecycle changes are not overwritten.

WHEN a client requests Party activation,
THE Party Registry SHALL require exactly one `If-Match` header containing the nonnegative decimal representation of the expected Party version.

IF `If-Match` is missing, duplicated, or malformed,
THEN THE Party Registry SHALL reject the request with HTTP `400` and code `bad-request` without changing the Party.

IF the valid expected version does not equal the current Party version,
THEN THE Party Registry SHALL reject activation with HTTP `412` and code `precondition-failed` without changing the Party.

WHEN concurrent activation or lifecycle requests use the same current version,
THE Party Registry SHALL permit at most one state change for that version.

#### Scenario: Missing expected version is rejected

- **GIVEN** an otherwise valid activation request omits `If-Match`
- **WHEN** activation is requested
- **THEN** the response has HTTP status `400` and code `bad-request`
- **AND** the Party remains unchanged

#### Scenario: Stale expected version is rejected

- **GIVEN** `If-Match` contains a valid version different from the current Party version
- **WHEN** activation is requested
- **THEN** the response has HTTP status `412` and code `precondition-failed`
- **AND** the Party remains unchanged

#### Scenario: Concurrent activation permits one winner

- **GIVEN** two activation requests use the same current Party version
- **WHEN** the requests are processed concurrently
- **THEN** at most one request changes the Party status and version
- **AND** each request that loses the version race receives HTTP `412` and code `precondition-failed`

### Requirement: Activation is tenant-scoped

**User Story:** As a tenant administrator, I want activation isolated by tenant, so that one tenant cannot discover or change another tenant's Party.

IF `partyId` does not identify a Party belonging to the requesting tenant,
THEN THE Party Registry SHALL reject activation with HTTP `404` and code `not-found` without exposing or changing Party data.

#### Scenario: Cross-tenant Party is concealed

- **GIVEN** `partyId` belongs to a different tenant
- **WHEN** activation is requested in the current tenant
- **THEN** the response has HTTP status `404` and code `not-found`
- **AND** the Party remains unchanged

### Requirement: Activation has one observable outcome

**User Story:** As a registry operator, I want Party activation and its enabled event to succeed or fail together, so that lifecycle consumers do not observe contradictory state.

WHEN Party activation succeeds,
THE Party Registry SHALL make the `ACTIVE` state, incremented Party version, and each enabled activation event observable as one complete outcome.

IF activation fails before the complete outcome is accepted,
THEN THE Party Registry SHALL leave the Party status and version unchanged and SHALL make no activation event from that request observable.

THE Party Registry SHALL NOT expose complete, normalized, or reversibly protected identifier values in an activation response or event.

#### Scenario: Successful activation and event agree

- **GIVEN** an eligible `DRAFT` Party and an enabled activation event
- **WHEN** activation succeeds
- **THEN** the Party is observable as `ACTIVE` at the incremented version
- **AND** the enabled event identifies that same Party and aggregate version
- **AND** neither output exposes the complete identifier value

#### Scenario: Failed activation produces no partial outcome

- **GIVEN** an activation request fails before completion
- **WHEN** the failure response is returned
- **THEN** the Party retains its previous status and version
- **AND** no activation event from that request is observable
