## Purpose

This capability deactivates or archives tenant-owned Parties without deleting their identity history. It also defines the common optional idempotency contract for activation, deactivation, and archival.

## ADDED Requirements

### Requirement: Party deactivation transition

**User Story:** As a registry operator, I want to deactivate an active Party, so that its status reflects that it is no longer active while its identity remains available.

WHILE a Party is `ACTIVE`, WHEN a new `POST /v1/parties/{partyId}/deactivate` operation is accepted at its current version,
THE Party Registry SHALL change its status to `INACTIVE`, increment its version exactly once, update its modification audit information, and return `200 successful` with the common Party detail representation.

IF a new deactivation operation targets `DRAFT`, `INACTIVE`, or `ARCHIVED` at the current version,
THEN THE Party Registry SHALL return `409 invalid-party-lifecycle` without changes. Deactivation SHALL NOT require qualifying identifier evidence. A completed idempotent replay SHALL follow the lifecycle replay requirement instead of performing another transition.

#### Scenario: Active Party becomes inactive

- **GIVEN** a tenant-owned active natural person or legal entity and its current version
- **WHEN** deactivation is accepted
- **THEN** HTTP/body status are 200, code is `successful`, status is `INACTIVE`, and version increases by one
- **AND** `updatedBy` identifies the accepted requesting user

#### Scenario: Invalid deactivation states are rejected

- **GIVEN** a Party is `DRAFT`, `INACTIVE`, or `ARCHIVED`
- **WHEN** a new deactivation is requested with the current version
- **THEN** the response is `409 invalid-party-lifecycle` without status, version, or audit changes

### Requirement: Party archival transition

**User Story:** As a registry operator, I want to archive retained identity records from any prior lifecycle state, so that obsolete records remain traceable without remaining operational.

WHILE a Party is `DRAFT`, `ACTIVE`, or `INACTIVE`, WHEN a new `POST /v1/parties/{partyId}/archive` operation is accepted at its current version,
THE Party Registry SHALL change its status to `ARCHIVED`, increment its version exactly once, update its modification audit information, and return `200 successful` with the common Party detail representation.

WHILE a Party is `ARCHIVED`,
THE Party Registry SHALL reject new activate, deactivate, and archive operations with `409 invalid-party-lifecycle` when their version precondition matches. Archival SHALL NOT require qualifying identifier evidence. Completed idempotent replays SHALL return their historical accepted result without leaving `ARCHIVED` or making a new transition.

#### Scenario: Every nonarchived state can be archived directly

- **GIVEN** tenant-owned Parties in `DRAFT`, `ACTIVE`, and `INACTIVE`
- **WHEN** each is archived using its current version
- **THEN** each becomes `ARCHIVED` at the next version, including direct archival of an active Party

#### Scenario: Archived state is terminal for new lifecycle actions

- **GIVEN** an archived Party and its current version
- **WHEN** a new activation, deactivation, or archival operation is requested
- **THEN** the response is `409 invalid-party-lifecycle`
- **AND** the Party remains archived at the same version

### Requirement: Lifecycle request validation

**User Story:** As an API consumer, I want the same lifecycle input rules on all three actions, so that validation remains predictable.

WHEN activate, deactivate, or archive is requested,
THE Party Registry SHALL apply the shared root Party context/response contract, require no request body, and validate an optional `Idempotency-Key`, canonical `partyId`, and required `If-Match`, in that order after trusted-context and applicable framework checks.

THE Party Registry SHALL accept an absent idempotency key. If present, it SHALL be exactly one nonblank value of at most 128 Unicode code points; duplication, blankness, and excess length SHALL return `400 idempotency-key-duplicated`, `400 idempotency-key-blank`, and `400 idempotency-key-too-long`, respectively. Valid key values SHALL be compared exactly without trimming or case conversion.

THE Party Registry SHALL reject a noncanonical Party UUID with `400 party-id-invalid`. `If-Match` SHALL be exactly one value matching `^(0|[1-9][0-9]*)$` in the inclusive range 0–9223372036854775807; absence, duplication, malformed values, and overflow SHALL return the corresponding `if-match-required`, `if-match-duplicated`, `if-match-invalid`, and `if-match-out-of-range` codes with HTTP `400`.

#### Scenario: Optional key does not become mandatory

- **GIVEN** an otherwise valid lifecycle request without `Idempotency-Key`
- **WHEN** the request is submitted
- **THEN** it passes key validation and follows normal current-version and transition checks

#### Scenario: Optional key boundaries apply to every lifecycle action

- **WHEN** any lifecycle request supplies duplicate, blank, or 129-code-point key values
- **THEN** the corresponding key-validation error is returned with HTTP `400`
- **AND** a safe nonblank 128-code-point key passes this validation

#### Scenario: Invalid syntax is checked even for a saved result

- **GIVEN** a lifecycle result already exists for a key
- **WHEN** another request with that key supplies a malformed Party ID or invalid `If-Match`
- **THEN** the relevant `400` validation error is returned instead of replaying the result

### Requirement: Lifecycle tenant isolation and failure precedence

**User Story:** As a tenant administrator, I want lifecycle failures to preserve isolation and concurrency guarantees, so that invalid requests cannot reveal or overwrite identity records.

WHEN a syntactically valid lifecycle request does not resolve to an existing idempotent result or idempotency conflict,
THE Party Registry SHALL evaluate failures in the order tenant-scoped absence, version mismatch, lifecycle transition, and then any action-specific business precondition.

IF the requested Party is absent or belongs to another tenant,
THEN THE Party Registry SHALL return `404 party-not-found` without disclosing its actual owner, type, state, or version.

IF the expected version differs from the current Party version or a distinct operation loses a concurrent version race,
THEN THE Party Registry SHALL return `412 stale-party-version` without changing the Party. These codes SHALL apply consistently to activation, deactivation, and archival.

#### Scenario: Cross-tenant lifecycle targets are concealed

- **GIVEN** a Party belongs to another tenant
- **WHEN** it is targeted by any lifecycle action with syntactically valid input
- **THEN** the response is the same `404 party-not-found` object as for an absent Party

#### Scenario: Stale version takes precedence over invalid transition

- **GIVEN** a Party is already inactive or archived and has a newer version
- **WHEN** a new deactivation or archival is submitted with an old valid version
- **THEN** the response is `412 stale-party-version`, rather than a lifecycle conflict

#### Scenario: Distinct competing mutations have one winner

- **GIVEN** deactivation, archival, or another Party mutation competes for the same current version using no replay key or different keys
- **WHEN** both operations are otherwise eligible
- **THEN** at most one advances that Party version
- **AND** a lifecycle action losing the race returns `412 stale-party-version`

### Requirement: Lifecycle idempotency scope and request equivalence

**User Story:** As an API consumer, I want lifecycle retry keys scoped predictably, so that accidental key reuse cannot execute a different command or disclose another tenant's result.

WHERE a lifecycle request supplies a valid `Idempotency-Key`,
THE Party Registry SHALL scope that key by requesting tenant and lifecycle action. Within that scope, effective request equivalence SHALL consist of the canonical Party ID and expected version. `Process-Id` and `User-Id` SHALL NOT alter request equivalence; all retries SHALL still supply valid trusted context.

IF a key with a completed successful result is reused within its tenant/action scope for a different Party ID or expected version,
THEN THE Party Registry SHALL return `409 idempotency-key-conflict` before evaluating current Party state, without revealing the original result or making a mutation.

THE Party Registry SHALL keep lifecycle replay scope independent from registration replay scope. A key used for activation SHALL be usable independently for deactivation or archival, and keys used by another tenant SHALL confer no access to that tenant's results.

WHEN different effective requests compete for the same tenant/action/key,
THE Party Registry SHALL accept at most one successful outcome for that key. Once one succeeds, a conflicting contender SHALL receive `409 idempotency-key-conflict` without leaving any Party mutation, successful replay result, or event from its own attempt.

#### Scenario: Changed target or expected version conflicts

- **GIVEN** a successful archival is associated with a tenant/action/key
- **WHEN** the key is reused for a different Party ID or expected version
- **THEN** the response is `409 idempotency-key-conflict`
- **AND** the saved Party result is not disclosed and neither Party is changed

#### Scenario: Different actions and tenants have independent key scopes

- **GIVEN** a key was used successfully for activation
- **WHEN** the same tenant uses it for an eligible deactivation, or another tenant uses it for its own eligible activation
- **THEN** the previous result does not create a conflict or supply the other operation's response

#### Scenario: Concurrent conflicting keys cannot accept two mutations

- **GIVEN** two eligible Parties and concurrent archival requests using the same tenant/action/key for different Party IDs
- **WHEN** one archival succeeds
- **THEN** the other request receives `409 idempotency-key-conflict`
- **AND** the losing Party retains its prior status, version, and audit information without an archival event

### Requirement: Lifecycle successful result replay

**User Story:** As an API consumer, I want retries to reproduce an accepted lifecycle result, so that lost responses do not cause duplicate changes or inconsistent answers.

WHEN a valid tenant/action/key is repeated with equivalent effective input after its lifecycle operation succeeded,
THE Party Registry SHALL return the original `200 successful` Party data, version, and audit values, regardless of subsequent Party changes or the now-stale expected version. The accepted process identifier of the current request SHALL be echoed. Replay SHALL NOT change current state, increment a version, modify audit information, re-evaluate activation evidence, or produce another logical lifecycle event.

WHEN equivalent keyed lifecycle requests compete,
THE Party Registry SHALL accept at most one transition and return that same successful result to every equivalent successful retry, including a request whose version becomes stale because the equivalent operation won. Completed replay behavior SHALL remain available across service restarts.

IF a lifecycle attempt is rejected or fails before successful acceptance,
THEN THE Party Registry SHALL leave no completed successful replay result for that attempt; a later request using the key SHALL be evaluated normally. Requests without a key SHALL NOT replay an earlier success.

#### Scenario: Replay returns the original result after later transitions

- **GIVEN** a keyed activation succeeded and the Party was subsequently archived
- **WHEN** the original activation request is repeated with its original key and expected version
- **THEN** the original active representation and activation version are returned with `200 successful`
- **AND** the actual Party remains archived at its current version and no new event is produced

#### Scenario: Retry correlation is fresh while audit remains historical

- **GIVEN** a keyed deactivation succeeded for one user and process
- **WHEN** an equivalent valid request is retried with another user and process in the same tenant
- **THEN** the response data retains the original modification user and timestamp
- **AND** the echoed `Process-Id` identifies the retry

#### Scenario: Concurrent equivalent retries converge

- **GIVEN** equivalent activation, deactivation, or archival requests share the same tenant/action/key and expected version
- **WHEN** they compete for an eligible Party
- **THEN** one logical transition and one successful replay result become observable
- **AND** both successful responses contain identical Party data and versions
- **AND** the equivalent loser is not reported as a stale-version failure

#### Scenario: Failed attempt does not consume the key

- **GIVEN** a keyed activation failed for missing qualifying evidence without changing the Party
- **WHEN** qualifying evidence later exists and the same otherwise valid request is retried
- **THEN** the key does not replay the failure or cause an idempotency conflict
- **AND** the activation is evaluated against current state and evidence

#### Scenario: Accepted result survives a lost response and restart

- **GIVEN** a keyed archival was accepted but its HTTP response was lost
- **WHEN** the service restarts and the equivalent request is retried
- **THEN** the original successful result is returned without another version increment or logical event

#### Scenario: Unkeyed repeated actions retain concurrency behavior

- **GIVEN** an unkeyed deactivation succeeded
- **WHEN** it is repeated with the original expected version
- **THEN** the response is `412 stale-party-version`
- **AND** a separate new attempt with the current inactive version returns `409 invalid-party-lifecycle`

### Requirement: Lifecycle retention and atomic outcomes

**User Story:** As a data steward, I want lifecycle actions to retain identity history and agree with their events, so that no partial outcome or accidental deletion becomes observable.

WHEN deactivation or archival succeeds,
THE Party Registry SHALL preserve identity, tenant, type, display name, type-specific details, creation audit information, nationalities, identifiers and their independent states/versions, and prior registration replay results. The new status, next Party version, modification audit information, successful keyed replay result when applicable, and every enabled corresponding lifecycle event SHALL become observable as one complete outcome.

IF acceptance fails before that complete outcome is established,
THEN THE Party Registry SHALL leave the previous Party state/version/audit information intact and SHALL expose no successful replay result or event from the failed attempt. A lost HTTP response after acceptance SHALL NOT undo the accepted outcome; an equivalent keyed retry SHALL replay it.

THE Party Registry SHALL retain archived Parties for tenant-scoped retrieval and listing, SHALL NOT physically delete their identity records, and SHALL NOT alter identifier lifecycle states merely because the Party becomes inactive or archived.

#### Scenario: Archival retains identity and independent identifiers

- **GIVEN** a Party with type-specific details, nationalities, identifiers, and a saved registration result
- **WHEN** it is archived
- **THEN** all those records remain intact and the Party remains available through root retrieval and applicable list filters
- **AND** the identifiers retain their previous states and versions

#### Scenario: Enabled event and replay result agree with the accepted version

- **GIVEN** a keyed lifecycle action with its corresponding event enabled
- **WHEN** the action succeeds
- **THEN** the current Party and original replay result identify the same accepted version
- **AND** the logical event identifies that Party, transition, and version without confidential identifier values

#### Scenario: Late failure rolls back the whole observable outcome

- **GIVEN** a lifecycle action fails before complete acceptance
- **WHEN** the failure response is returned
- **THEN** the Party retains its previous state, version, and audit information
- **AND** no successful keyed result or lifecycle event from that attempt is observable
