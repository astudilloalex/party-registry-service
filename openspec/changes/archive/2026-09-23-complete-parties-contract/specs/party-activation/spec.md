## MODIFIED Requirements

### Requirement: Activation requires a qualifying verified identifier

**User Story:** As a data steward, I want only sufficiently identified Parties to become active, so that active registry records have verified identity evidence.

WHILE a Party is `DRAFT`, WHEN a new activation operation is evaluated,
THE Party Registry SHALL require at least one identifier associated with that tenant and Party that is `VERIFIED`, has no expiration or expires on/after one UTC evaluation date captured for the operation, and references a matching scheme applicable to the Party type or `BOTH`.

THE Party Registry SHALL NOT require that qualifying identifier to be primary or its compatible scheme to remain `ACTIVE`; scheme lifecycle state alone SHALL NOT qualify or disqualify verified evidence. Pending, rejected, expired, revoked, cross-tenant, unrelated, and incompatible identifiers SHALL NOT satisfy the precondition.

IF a `DRAFT` Party has no qualifying verified identifier,
THEN THE Party Registry SHALL reject activation with `422 missing-qualifying-identifier` and leave its state, version, and audit information unchanged. An equivalent completed keyed replay SHALL return its saved result without re-evaluating this evidence.

#### Scenario: Draft Party with verified identifier is eligible

- **GIVEN** a draft Party has an associated verified, type-compatible, non-expired identifier
- **WHEN** a new activation is requested with the current Party version
- **THEN** the identifier satisfies the activation identity precondition

#### Scenario: Pending identifier does not permit activation

- **GIVEN** a draft Party has only a pending-verification identifier
- **WHEN** a new activation is requested with the current version
- **THEN** the response is `422 missing-qualifying-identifier`
- **AND** the Party remains draft at its current version

#### Scenario: Expired verified identifier does not permit activation

- **GIVEN** the only verified identifier expired before the operation's UTC evaluation date
- **WHEN** a new activation is requested
- **THEN** the response is `422 missing-qualifying-identifier` without changing the Party

#### Scenario: Incompatible verified identifier does not permit activation

- **GIVEN** the only verified identifier references a scheme incompatible with the Party type
- **WHEN** a new activation is requested
- **THEN** the response is `422 missing-qualifying-identifier` without changing the Party

#### Scenario: Expiration boundary and absent expiration qualify

- **GIVEN** otherwise qualifying verified identifiers expire exactly on the evaluation date or have no expiration
- **WHEN** new activations are evaluated
- **THEN** those identifiers satisfy the expiration precondition using the same UTC date throughout each operation

#### Scenario: Primary flag and scheme status do not replace evidence rules

- **GIVEN** a nonprimary verified identifier is non-expired and its compatible scheme is deprecated or retired
- **WHEN** a new activation is evaluated
- **THEN** that identifier remains qualifying evidence
- **AND** a primary pending-verification identifier alone would not qualify

#### Scenario: Another Party's evidence cannot qualify

- **GIVEN** all otherwise qualifying identifiers belong to another Party or tenant
- **WHEN** the draft Party is activated at its current version
- **THEN** the response is `422 missing-qualifying-identifier`

### Requirement: Party activation transition

**User Story:** As a registry operator, I want eligible draft Parties activated predictably, so that their lifecycle state accurately represents registration readiness.

WHEN a new activation of an eligible `DRAFT` Party succeeds at its current version,
THE Party Registry SHALL change its status to `ACTIVE`, increment the Party version by exactly one, update its modification audit information, and return `200 successful` with the common root Party detail representation.

IF a new activation operation targets `ACTIVE`, `INACTIVE`, or `ARCHIVED` with its current version,
THEN THE Party Registry SHALL return `409 invalid-party-lifecycle` without changes. This contract SHALL NOT introduce reactivation from `INACTIVE`. Equivalent completed keyed retries SHALL follow the lifecycle replay contract instead of making another transition.

#### Scenario: Eligible draft Party becomes active

- **GIVEN** an eligible draft Party and the current Party version
- **WHEN** `POST /v1/parties/{partyId}/activate` is accepted
- **THEN** HTTP/body status are 200, code is `successful`, status is `ACTIVE`, and version increases by exactly one
- **AND** modification audit information identifies the accepted user and operation time

#### Scenario: Invalid lifecycle transition is rejected

- **GIVEN** a Party is active, inactive, or archived
- **WHEN** a new activation is requested with the current version
- **THEN** the response is `409 invalid-party-lifecycle`
- **AND** state, version, and audit information remain unchanged

### Requirement: Activation optimistic concurrency

**User Story:** As an API consumer, I want activation protected by the expected Party version, so that concurrent lifecycle changes are not overwritten.

WHEN a client requests activation,
THE Party Registry SHALL require exactly one `If-Match` matching `^(0|[1-9][0-9]*)$` in the inclusive range 0–9223372036854775807, including on requests that may replay a saved result.

IF `If-Match` is missing, duplicated, malformed, or out of range,
THEN THE Party Registry SHALL return HTTP `400` with `if-match-required`, `if-match-duplicated`, `if-match-invalid`, or `if-match-out-of-range`, respectively, without changing the Party.

IF a new activation has a valid expected version different from the current version,
THEN THE Party Registry SHALL return `412 stale-party-version` without changes. An equivalent completed keyed retry SHALL instead reproduce its original successful result, as defined by the lifecycle replay requirements.

WHEN distinct activation, lifecycle, or detail-update operations compete for the same current Party version,
THE Party Registry SHALL permit at most one mutation at that version. Activation requests losing that race SHALL return `412 stale-party-version`, except equivalent keyed requests that resolve to the winner's saved result.

#### Scenario: Missing expected version is rejected

- **GIVEN** an otherwise valid activation request omits `If-Match`
- **WHEN** the request is submitted
- **THEN** the response is `400 if-match-required` and the Party remains unchanged

#### Scenario: Stale expected version is rejected

- **GIVEN** a new activation request has a valid version different from the current Party version
- **WHEN** it is evaluated
- **THEN** the response is `412 stale-party-version` without changes

#### Scenario: Concurrent activation permits one winner

- **GIVEN** concurrent eligible activation requests use the same current version and no key or different keys
- **WHEN** they compete
- **THEN** at most one activates the Party
- **AND** each request losing the version race receives `412 stale-party-version`

#### Scenario: Equivalent keyed activation returns its accepted version

- **GIVEN** activation already succeeded for the same tenant, action, key, Party ID, and expected version
- **WHEN** the valid original request is retried after the current version advances
- **THEN** the original `200 successful` result is returned instead of `412`
- **AND** no additional mutation or event occurs

### Requirement: Activation is tenant-scoped

**User Story:** As a tenant administrator, I want activation isolated by tenant, so that one tenant cannot discover or change another tenant's Party.

IF a syntactically valid new activation request targets an absent Party or one belonging to another tenant,
THEN THE Party Registry SHALL return `404 party-not-found` without exposing or changing Party information. Idempotency keys and saved results SHALL remain tenant-scoped under the lifecycle replay requirements.

#### Scenario: Cross-tenant Party is concealed

- **GIVEN** a Party belongs to another tenant
- **WHEN** a new activation is requested in the current tenant
- **THEN** the response is `404 party-not-found`
- **AND** the other tenant's Party and saved results are neither changed nor disclosed

### Requirement: Activation has one observable outcome

**User Story:** As a registry operator, I want activation and its enabled event to succeed or fail together, so that lifecycle consumers do not observe contradictory state.

WHEN activation succeeds,
THE Party Registry SHALL make the active state, next Party version, modification audit information, optional successful keyed replay result, and each enabled activation event observable as one complete outcome.

IF activation fails before complete acceptance,
THEN THE Party Registry SHALL leave the prior Party state/version/audit information intact and SHALL make no successful replay result or activation event from that attempt observable.

THE Party Registry SHALL preserve type-specific details, display name, creation audit information, independent identifier states and versions, and stored registration results. Activation responses and events SHALL NOT expose complete, normalized, or reversibly protected identifier values. The root detail response SHALL NOT acquire an identifier collection.

#### Scenario: Successful activation and event agree

- **GIVEN** an eligible draft Party, a valid idempotency key, and an enabled activation event
- **WHEN** activation succeeds
- **THEN** the Party, original replay result, and event identify the same accepted version
- **AND** identifier values and protection material are absent from public outputs

#### Scenario: Failed activation produces no partial outcome

- **GIVEN** an activation attempt fails before acceptance
- **WHEN** the failure response is returned
- **THEN** the Party retains its previous state, version, and audit information
- **AND** no successful replay result or activation event from that attempt is observable

## ADDED Requirements

### Requirement: Activation follows the shared lifecycle request and replay contract

**User Story:** As an API consumer, I want activation to use the same validation and retry rules as the other lifecycle actions, so that one client strategy works for all three operations.

WHEN activation is requested,
THE Party Registry SHALL apply the root Party context/response requirements and the lifecycle request validation, key scope, request equivalence, successful result replay, and failure precedence defined by `party-deactivation-and-archival`.

WHEN a valid activation request is neither a completed replay nor an idempotency conflict,
THE Party Registry SHALL evaluate tenant-scoped absence, stale version, invalid lifecycle, and missing qualifying identifier in that order.

#### Scenario: Invalid key precedes Party lookup

- **GIVEN** valid context and an absent Party ID
- **WHEN** activation supplies a blank idempotency key
- **THEN** the response is `400 idempotency-key-blank` without revealing Party existence

#### Scenario: State failure precedes missing evidence

- **GIVEN** an active Party has no qualifying identifier
- **WHEN** a new activation is submitted with its current version
- **THEN** the response is `409 invalid-party-lifecycle`, not a missing-evidence failure

#### Scenario: Equivalent activation retries share one result

- **GIVEN** equivalent keyed activation requests compete for an eligible draft Party
- **WHEN** they complete successfully
- **THEN** they return the same accepted active representation and version
- **AND** only one logical activation and enabled activation event become observable
