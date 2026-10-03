## MODIFIED Requirements

### Requirement: Activation requires a qualifying verified identifier

**User Story:** As a data steward, I want only sufficiently identified Parties activated, so that active records have verified identity evidence.

WHILE a Party is DRAFT, WHEN activation is requested,
THE Party Registry SHALL require at least one associated VERIFIED identifier that is not expired on the evaluation date and whose scheme is compatible with the Party type.

IF no qualifying identifier exists,
THEN THE Party Registry SHALL return `422 missing-qualifying-identifier` and leave the Party DRAFT at its current version.

#### Scenario: Draft Party with verified identifier is eligible

- **GIVEN** a DRAFT Party with a VERIFIED, compatible, non-expired identifier
- **WHEN** activation is requested with the current version
- **THEN** the identifier satisfies the activation evidence precondition

#### Scenario: Pending identifier does not permit activation

- **GIVEN** a DRAFT Party has only a PENDING_VERIFICATION identifier
- **WHEN** activation is requested
- **THEN** the response is `422 missing-qualifying-identifier`
- **AND** the Party remains DRAFT at its current version

#### Scenario: Expired verified identifier does not permit activation

- **GIVEN** the only VERIFIED identifier expired before the evaluation date
- **WHEN** activation is requested
- **THEN** the response is `422 missing-qualifying-identifier` and the Party remains DRAFT

#### Scenario: Incompatible verified identifier does not permit activation

- **GIVEN** the only VERIFIED identifier has a scheme incompatible with the Party type
- **WHEN** activation is requested
- **THEN** the response is `422 missing-qualifying-identifier` and the Party remains DRAFT

### Requirement: Party activation transition

**User Story:** As a registry operator, I want eligible draft Parties activated predictably, so that their lifecycle state reflects readiness.

WHEN an eligible DRAFT Party is activated with its current version,
THE Party Registry SHALL change recordStatus to ACTIVE, increment the Party version exactly once, and return `200 successful` with the updated representation.

IF the lifecycle transition is not permitted,
THEN THE Party Registry SHALL return `409 invalid-party-lifecycle` without changing the Party.

#### Scenario: Eligible draft Party becomes active

- **GIVEN** an eligible DRAFT Party and its current version
- **WHEN** activation is submitted
- **THEN** HTTP/body status are 200, code is successful, status becomes ACTIVE, and version increases by one

#### Scenario: Invalid lifecycle transition is rejected

- **GIVEN** a Party is in a state from which activation is not permitted
- **WHEN** activation is requested
- **THEN** the response is `409 invalid-party-lifecycle`
- **AND** status and version remain unchanged

### Requirement: Activation optimistic concurrency

**User Story:** As an API consumer, I want activation protected by the expected version, so that concurrent changes cannot be overwritten.

WHEN activation is requested,
THE Party Registry SHALL require exactly one bare nonnegative decimal If-Match value in the supported version range.

IF If-Match is missing, duplicated, malformed, or out of range,
THEN THE Party Registry SHALL return HTTP `400` with respectively `if-match-required`, `if-match-duplicated`, `if-match-invalid`, or `if-match-out-of-range` without changing the Party.

IF the valid expected version does not match,
THEN THE Party Registry SHALL return `412 stale-party-version` without changing the Party.

WHEN concurrent activation or lifecycle requests target the same version,
THE Party Registry SHALL permit at most one state change for that version.

#### Scenario: Missing expected version is rejected

- **GIVEN** an otherwise valid activation request omits If-Match
- **WHEN** it is submitted
- **THEN** the response is `400 if-match-required` and the Party is unchanged

#### Scenario: Stale expected version is rejected

- **GIVEN** a valid expected version differs from the current version
- **WHEN** activation is requested
- **THEN** the response is `412 stale-party-version` and the Party is unchanged

#### Scenario: Concurrent activation permits one winner

- **GIVEN** concurrent activation requests use the same current version
- **WHEN** they are processed
- **THEN** at most one changes status/version
- **AND** each activation version-race loser receives `412 stale-party-version`

### Requirement: Activation is tenant-scoped

**User Story:** As a tenant administrator, I want activation isolated by tenant, so that another tenant cannot discover or change my Party.

IF partyId does not identify a Party belonging to the requesting tenant,
THEN THE Party Registry SHALL return `404 party-not-found` without exposing or changing Party data.

#### Scenario: Cross-tenant Party is concealed

- **GIVEN** partyId belongs to a different tenant
- **WHEN** activation is requested in the current tenant
- **THEN** the response is `404 party-not-found`
- **AND** the Party remains unchanged
