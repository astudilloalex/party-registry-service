## Purpose

This capability activates, deprecates, or retires global identifier schemes through explicit valid transitions while retaining catalog identity and historical identifier references. It defines lifecycle eligibility, concurrency, durable optional replay, and atomic accepted outcomes.

## ADDED Requirements

### Requirement: Identifier-scheme lifecycle transition matrix

**User Story:** As a catalog administrator, I want explicit allowed transitions, so that lifecycle operations cannot accidentally restore withdrawn schemes.

WHEN a new lifecycle action is evaluated at the current scheme version,
THE Party Registry SHALL permit only the transitions marked below; a dash SHALL represent a rejected action, not a successful no-op.

| Current state | activate | deprecate | retire |
| --- | --- | --- | --- |
| `DRAFT` | `ACTIVE` | — | `RETIRED` |
| `ACTIVE` | — | `DEPRECATED` | `RETIRED` |
| `DEPRECATED` | — | — | `RETIRED` |
| `RETIRED` | — | — | — |

IF a new current-version action is not allowed by this matrix,
THEN THE Party Registry SHALL return `409 invalid-identifier-scheme-lifecycle` without changing status, configuration, version, or audit information. Deprecation SHALL NOT be reversible through activation, retirement SHALL be terminal, and PATCH SHALL NOT substitute for lifecycle actions. Completed equivalent replay SHALL return its original successful result instead of performing a new transition.

#### Scenario: All allowed matrix entries succeed

- **GIVEN** an otherwise eligible scheme for each allowed matrix entry
- **WHEN** its indicated action is submitted with the current version
- **THEN** the scheme reaches the indicated target state with `200 successful`

#### Scenario: Every disallowed matrix entry is rejected

- **GIVEN** a scheme for each disallowed state/action pair
- **WHEN** a new action is submitted with that scheme's current version
- **THEN** the response is `409 invalid-identifier-scheme-lifecycle`
- **AND** all scheme values and historical identifiers remain unchanged

### Requirement: Draft activation and configuration eligibility

**User Story:** As a registry operator, I want activation to admit usable scheme configurations, so that newly available schemes support identifier processing.

WHILE a scheme is `DRAFT`, WHEN a new current-version `POST /v1/identifier-schemes/{schemeId}/activate` is evaluated,
THE Party Registry SHALL require its stored configuration to satisfy required-field, country-format, enum, text-limit, supported-rule-key, and coherent-length rules before changing its status to `ACTIVE`. Successful activation SHALL increment its version exactly once, refresh modification audit information, and return `200 successful` with the accepted active representation.

IF an eligible draft references unsupported rule keys or otherwise fails activation configuration checks,
THEN THE Party Registry SHALL return `422 invalid-identifier-scheme-configuration`, using `422 identifier-scheme-length-range-invalid` for invalid length bounds, without changing the scheme or saving a successful replay result. Eligibility checks SHALL apply to existing catalog entries as well as schemes created through the API. Invalid lifecycle state SHALL be evaluated before configuration eligibility.

#### Scenario: Valid draft becomes active

- **GIVEN** a draft scheme at version zero with supported rules and coherent configuration
- **WHEN** activation is accepted with `If-Match: 0`
- **THEN** the response contains `ACTIVE` at version one with `200 successful`
- **AND** creation information and all configuration values are retained

#### Scenario: Legacy unsupported draft cannot activate

- **GIVEN** an existing draft references an unsupported processing rule
- **WHEN** activation is requested with its current version
- **THEN** the response is `422 invalid-identifier-scheme-configuration`
- **AND** it remains draft at its prior version, with unchanged audit information

#### Scenario: Invalid state precedes invalid configuration

- **GIVEN** a deprecated scheme has unsupported historical rules
- **WHEN** a new activation uses its current version
- **THEN** the response is `409 invalid-identifier-scheme-lifecycle`, not a configuration error

### Requirement: Active scheme deprecation

**User Story:** As a registry operator, I want to deprecate an active scheme, so that new identifier admissions stop while historical references remain available.

WHILE a scheme is `ACTIVE`, WHEN a new current-version `POST /v1/identifier-schemes/{schemeId}/deprecate` is accepted,
THE Party Registry SHALL change its status to `DEPRECATED`, increment its version exactly once, refresh modification audit information, and return `200 successful` with the accepted deprecated representation. Deprecation SHALL NOT require revalidation of its processing keys or a usable sample identifier and SHALL NOT change the scheme's configuration.

#### Scenario: Active scheme becomes deprecated

- **GIVEN** an active scheme with existing Party identifier references
- **WHEN** deprecation is accepted using its current version
- **THEN** it becomes deprecated at the next version and remains retrievable by ID and code
- **AND** referenced identifiers remain unchanged

#### Scenario: Unsupported historical rules do not prevent withdrawal

- **GIVEN** an active scheme's historical processing key is no longer supported
- **WHEN** deprecation is requested using its current version
- **THEN** deprecation succeeds without requiring the configuration to become eligible for new admissions

### Requirement: Direct retirement and terminal state

**User Story:** As a catalog administrator, I want to retire a scheme directly from any nonretired state, so that obsolete definitions can be withdrawn without artificial intermediate steps.

WHILE a scheme is `DRAFT`, `ACTIVE`, or `DEPRECATED`, WHEN a new current-version `POST /v1/identifier-schemes/{schemeId}/retire` is accepted,
THE Party Registry SHALL change its status to `RETIRED`, increment its version exactly once, refresh modification audit information, and return `200 successful` with the accepted retired representation. Retirement SHALL NOT require activation, prior deprecation, supported processing keys, or an absence of historical identifier references.

WHILE a scheme is `RETIRED`,
THE Party Registry SHALL retain it for ID/code retrieval and listing, retain its code assignment, and reject every new current-version lifecycle action with `409 invalid-identifier-scheme-lifecycle`. Retirement SHALL NOT physically delete or reidentify the scheme, and a completed historical replay SHALL NOT leave the retired state.

#### Scenario: Direct retirement from each nonterminal state

- **GIVEN** schemes in draft, active, and deprecated states
- **WHEN** each is retired using its current version
- **THEN** each becomes retired at the next version with `200 successful`
- **AND** direct active-to-retired and draft-to-retired transitions require no intermediate operation

#### Scenario: Retired scheme remains identifiable but terminal

- **GIVEN** a retired scheme and its current version
- **WHEN** a new activation, deprecation, or retirement is requested
- **THEN** the response is `409 invalid-identifier-scheme-lifecycle`
- **AND** subsequent lookup returns the unchanged retired scheme and its code remains unavailable for another creation

### Requirement: Lifecycle request validation and failure precedence

**User Story:** As an API consumer, I want consistent lifecycle inputs and failure precedence, so that I can distinguish invalid requests, absent schemes, stale versions, and invalid transitions.

WHEN any lifecycle operation is requested,
THE Party Registry SHALL apply the trusted-context, standard response, canonical scheme-ID, and version-precondition requirements of identifier-scheme management. Lifecycle operations SHALL require no request body; a supplied nonempty body SHALL return `400 bad-request` rather than silently changing configuration. Optional `Idempotency-Key` SHALL be validated before scheme ID and `If-Match` after trusted-context and applicable framework checks.

THE Party Registry SHALL accept absence of the optional key. Supplied keys SHALL be exactly one nonblank value of at most 128 Unicode code points, compared exactly without trimming or case conversion; duplicates, blankness, and excess length SHALL return the existing `400 idempotency-key-duplicated`, `idempotency-key-blank`, and `idempotency-key-too-long` codes. Input syntax SHALL still be validated for requests eligible for replay.

WHEN a syntactically valid lifecycle request does not resolve to a completed equivalent replay or key conflict,
THE Party Registry SHALL evaluate scheme absence, expected-version mismatch, transition eligibility, version exhaustion, and action-specific configuration eligibility in that order. The absence response SHALL be `404 identifier-scheme-not-found`; stale versions or lost independent races SHALL return `412 expected-version-mismatch`; disallowed actions SHALL return `409 invalid-identifier-scheme-lifecycle`; maximum-version schemes SHALL return `409 identifier-scheme-version-exhausted` for otherwise allowed new actions.

#### Scenario: Optional key remains optional

- **WHEN** a valid eligible current-version action omits `Idempotency-Key`
- **THEN** it follows normal transition evaluation and succeeds without creating replay semantics for future unkeyed requests

#### Scenario: Syntax is checked before saved result lookup

- **GIVEN** a successful result exists for a lifecycle key
- **WHEN** a retry has malformed scheme ID, duplicated key, or invalid `If-Match`
- **THEN** it receives the corresponding documented `400` error instead of the saved success

#### Scenario: Absence and stale version precede lifecycle checks

- **WHEN** a syntactically valid action targets an absent scheme
- **THEN** it returns `404 identifier-scheme-not-found`
- **AND** an action on an existing retired scheme with a stale version returns `412 expected-version-mismatch` before a lifecycle conflict

#### Scenario: Bodies do not supply lifecycle parameters

- **WHEN** an action includes a nonempty JSON body, including an empty object or requested status
- **THEN** it returns `400 bad-request` without mutation

### Requirement: Lifecycle idempotency scope and equivalence

**User Story:** As an API consumer, I want retry keys to identify one lifecycle intent, so that reused keys cannot execute a different transition.

WHERE a lifecycle request supplies a valid key,
THE Party Registry SHALL scope its result by requesting tenant and identifier-scheme action, independently of scheme creation, Party operations, and the other scheme lifecycle actions. Within that scope, effective request equivalence SHALL consist of canonical scheme ID and expected version; accepted user and process IDs SHALL NOT alter equivalence. Global scheme ownership SHALL NOT make saved replay results available across tenants.

IF a completed successful key is reused in its scope for a different scheme or expected version,
THEN THE Party Registry SHALL return `409 idempotency-key-conflict` before current scheme/state checks, without exposing the original result or causing a mutation. Concurrent conflicting intents using the same scoped key SHALL accept at most one successful outcome and SHALL leave no scheme mutation or completed result from a losing intent.

#### Scenario: Changed scheme or version conflicts

- **GIVEN** a completed activation result for a tenant/action/key
- **WHEN** that key is reused for another scheme or expected version
- **THEN** the response is `409 idempotency-key-conflict` and neither scheme is changed by that request

#### Scenario: Action scopes are independent

- **GIVEN** activation succeeded with a key
- **WHEN** the same tenant uses that key for a valid deprecation at the active scheme's current version
- **THEN** the deprecation is evaluated as a separate operation rather than replaying activation or reporting a key conflict

#### Scenario: Tenant replay isolation does not isolate catalog ownership

- **GIVEN** one tenant has a completed activation replay result for a global scheme
- **WHEN** another tenant submits the same key and original version for that scheme
- **THEN** it does not receive the first tenant's result and follows current-version checks
- **AND** a correctly versioned valid request still operates on the same global scheme

#### Scenario: Concurrent conflicting keyed intents have one outcome

- **GIVEN** two otherwise eligible schemes
- **WHEN** concurrent retirement requests use the same tenant/action/key for different schemes
- **THEN** at most one retirement succeeds and the conflicting contender receives `409 idempotency-key-conflict`
- **AND** the losing scheme retains its prior status, version, and audit values

### Requirement: Durable successful lifecycle replay

**User Story:** As an API consumer, I want retries to reproduce the original accepted result, so that response loss does not trigger repeated state changes.

WHEN a syntactically valid request repeats a completed tenant/action/key with equivalent effective input,
THE Party Registry SHALL return the original `200 successful` scheme representation, accepted version, and timestamps, even if the scheme has since changed or the original expected version is now stale. It SHALL echo the retry's accepted process ID, preserve original audit attribution, and SHALL NOT evaluate the old action again, change current state, increment a version, or update audit information. Successful replay SHALL remain available across service restarts.

WHEN equivalent keyed actions compete,
THE Party Registry SHALL accept one logical transition and return the same accepted result to equivalent successful retries, including a contender whose version becomes stale because the equivalent action won. A successful keyed transition and its replay result SHALL become observable together.

IF an action fails before successful acceptance,
THEN THE Party Registry SHALL save no completed successful replay result and SHALL allow later use of the key to be evaluated normally. Unkeyed requests SHALL NOT replay prior results; repeating an accepted action with its old version SHALL return `412 expected-version-mismatch`, while a new same-action request at the new current version SHALL return the corresponding lifecycle conflict.

#### Scenario: Replay after retirement returns historical activation only

- **GIVEN** keyed activation succeeded and the scheme was subsequently retired
- **WHEN** the original activation is retried with its original key and expected version
- **THEN** the response returns the original active representation and activation version with `200 successful`
- **AND** current retrieval still returns the retired scheme at its latest version

#### Scenario: Retry uses fresh correlation and historical audit

- **GIVEN** a keyed deprecation succeeded for one user and process
- **WHEN** an equivalent request is retried with a different valid user and process in the same tenant
- **THEN** the result retains original accepted timestamps and audit attribution
- **AND** `Process-Id` echoes the retry's accepted value

#### Scenario: Concurrent equivalent retries do not become stale failures

- **WHEN** equivalent activation, deprecation, or retirement requests share the same tenant/action/key and current expected version
- **THEN** one transition occurs and equivalent successful responses return identical accepted scheme data and versions
- **AND** the equivalent contender is not reported as an independent stale-version error

#### Scenario: Lost response and restart preserve replay

- **GIVEN** a keyed retirement was accepted but its HTTP response was lost
- **WHEN** the service restarts and the equivalent request is retried
- **THEN** the original accepted retirement result is returned without another version or audit change

#### Scenario: Failed activation does not consume its key

- **GIVEN** keyed activation failed for unsupported configuration without mutation
- **WHEN** a permitted draft edit repairs the configuration and activation is retried with the now-current version and the same key
- **THEN** the request is evaluated normally rather than replaying the failure or returning an idempotency-key conflict

### Requirement: Lifecycle atomicity and historical retention

**User Story:** As a data steward, I want lifecycle changes to retain identifier history and complete atomically, so that withdrawal cannot damage existing identity records.

WHEN any lifecycle action succeeds,
THE Party Registry SHALL retain scheme identity, exact code, issuing country, category, applicable subject type, descriptive/processing configuration, creation audit information, historical identifier references, and prior successful replay outcomes. Only status, the next scheme version, and modification audit information SHALL change. Accepted state and any successful keyed result SHALL form one complete outcome; failed acceptance SHALL expose no partial status, version, audit, or replay change.

THE Party Registry SHALL NOT change Party lifecycle, independently versioned Party identifier states, values, protection metadata, or audit information merely because a scheme is activated, deprecated, or retired. Administrative catalog changes SHALL NOT emit tenant Party outbox events.

#### Scenario: Retirement preserves identity history

- **GIVEN** an active scheme referenced by registered Parties and independently versioned identifiers
- **WHEN** retirement is accepted
- **THEN** the scheme retains its identity and configuration, all references remain intact, and Parties and identifiers retain their values, states, and versions
- **AND** no tenant Party event is produced for the catalog change

#### Scenario: Failed acceptance rolls back the whole observable change

- **GIVEN** a lifecycle operation fails before complete acceptance
- **WHEN** its failure response is returned
- **THEN** the prior scheme state, version, and audit values remain observable
- **AND** no completed successful replay result from that attempt is observable

### Requirement: Lifecycle effects on new identifier admission

**User Story:** As a registry operator, I want scheme withdrawal to stop future use without rewriting history, so that identifier admission follows current catalog availability.

WHILE a scheme is `DRAFT`, `DEPRECATED`, or `RETIRED`,
THE Party Registry SHALL continue to reject its use for new initial or additional Party identifier registration under the existing inactive-scheme contract. WHEN a valid scheme becomes `ACTIVE`, THE Party Registry SHALL admit it for those operations only when the existing subject compatibility, identifier validity, uniqueness, and other registration rules also pass.

THE Party Registry SHALL preserve the existing behavior of verified historical identifier evidence for Party activation; this catalog-management change SHALL NOT introduce a new requirement that a previously registered identifier's scheme remain active. Legacy `requiresExpiration`, including a historical true value, SHALL remain metadata only and SHALL NOT make an omitted document expiration invalid.

#### Scenario: Activation makes an otherwise eligible scheme usable

- **GIVEN** a valid draft scheme and a new identifier satisfying its configuration and existing registration rules
- **WHEN** the scheme is activated and that identifier is registered
- **THEN** the scheme passes active-state eligibility and registration proceeds under the existing rules

#### Scenario: Withdrawal prevents new identifier registration

- **GIVEN** a scheme has been deprecated or retired
- **WHEN** a new initial or additional identifier references its code
- **THEN** the request is rejected by the existing inactive-scheme response contract without creating a Party or identifier from that attempt

#### Scenario: Historical verified evidence remains governed by existing rules

- **GIVEN** a draft Party already has qualifying nonexpired verified identifier evidence whose scheme is subsequently deprecated or retired
- **WHEN** Party activation is requested under the existing Party activation preconditions
- **THEN** this catalog change introduces no new inactive-scheme rejection for that historical evidence

#### Scenario: Expiration stays optional

- **GIVEN** an active scheme retains legacy `requiresExpiration=true`
- **WHEN** an otherwise valid initial or additional identifier omits expiration
- **THEN** the existing optional-expiration rule continues to accept that absence
