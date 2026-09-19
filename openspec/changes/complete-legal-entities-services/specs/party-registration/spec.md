## ADDED Requirements

### Requirement: Cause-specific Party data validation failures

**User Story:** As an API consumer, I want invalid Party data identified by a stable cause code, so that I can correct it without receiving internal exception details.

WHEN a structurally valid registration violates a known Party data invariant,
THE Party Registry SHALL retain HTTP `422` and return its specific business code: `birth-date-in-future`, `date-of-death-in-future`, `death-before-birth`, `incorporation-date-in-future`, `dissolution-date-in-future`, `dissolution-before-incorporation`, or `blank-display-name`, as applicable.

IF a supplied country is definitively unrecognized,
THEN THE Party Registry SHALL return `422 unrecognized-birth-country` or `422 unrecognized-incorporation-country` according to the Party field.

WHEN a known failure is returned,
THE Party Registry SHALL expose only status and the stable code, without technical messages, rejected values, or internal causes.

#### Scenario: Client can identify the invalid legal history

- **WHEN** an otherwise valid legal registration supplies a dissolution date before its incorporation date
- **THEN** the response is `422 dissolution-before-incorporation`
- **AND** no Party or identifier is created

#### Scenario: Client can identify the invalid natural-person history

- **WHEN** an otherwise valid natural-person registration supplies a future birth date
- **THEN** the response is `422 birth-date-in-future`
- **AND** the response contains no exception message

## MODIFIED Requirements

### Requirement: Initial official identifier is mandatory

**User Story:** As a registry operator, I want every newly registered Party to include an official identifier, so that incomplete identity records are not created.

WHEN a client submits `POST /v1/natural-person` or `POST /v1/legal-entity`,
THE Party Registry SHALL require exactly one `initialIdentifier` containing a nonblank `identifierSchemeCode` and a nonblank complete `value`.

IF initialIdentifier is missing or null, or its required scheme/value is missing, null, or blank,
THEN THE Party Registry SHALL return HTTP `400` with `initial-identifier-required`, `identifier-scheme-code-required`, or `identifier-value-required`, as applicable, without creating a Party or identifier.

IF JSON structure or value types are invalid,
THEN THE Party Registry SHALL retain the shared `400 bad-request` binding failure response.

#### Scenario: Natural person includes one initial identifier

- **GIVEN** valid request context, Party data, idempotency input, and one structurally valid initialIdentifier
- **WHEN** the client submits `POST /v1/natural-person`
- **THEN** the registry evaluates the person and identifier as one registration request

#### Scenario: Legal entity includes one initial identifier

- **GIVEN** valid request context, Party data, idempotency input, and one structurally valid initialIdentifier
- **WHEN** the client submits `POST /v1/legal-entity`
- **THEN** the registry evaluates the legal entity and identifier as one registration request

#### Scenario: Initial identifier is missing

- **GIVEN** an otherwise valid registration omits initialIdentifier
- **WHEN** the request is submitted
- **THEN** the response is `400 initial-identifier-required`
- **AND** no Party or identifier is created

#### Scenario: Initial identifier value is blank

- **GIVEN** an otherwise valid registration contains a blank initial identifier value
- **WHEN** the request is submitted
- **THEN** the response is `400 identifier-value-required`
- **AND** no Party or identifier is created

### Requirement: Initial identifier scheme eligibility

**User Story:** As a data steward, I want initial identifiers checked against the authoritative scheme catalog, so that each Party uses an applicable official identifier.

WHEN a structurally valid initial identifier is evaluated,
THE Party Registry SHALL require a known ACTIVE scheme applicable to the Party type or BOTH.

IF the scheme is unknown, inactive, or incompatible,
THEN THE Party Registry SHALL reject registration with HTTP `422` and respectively `unknown-identifier-scheme`, `inactive-identifier-scheme`, or `incompatible-identifier-scheme`, without creating a Party or identifier.

#### Scenario: Natural-person scheme is accepted

- **GIVEN** an ACTIVE scheme applicable to NATURAL_PERSON or BOTH
- **WHEN** a natural person is registered with that scheme
- **THEN** scheme eligibility validation succeeds

#### Scenario: Legal-entity scheme is accepted

- **GIVEN** an ACTIVE scheme applicable to LEGAL_ENTITY or BOTH
- **WHEN** a legal entity is registered with that scheme
- **THEN** scheme eligibility validation succeeds

#### Scenario: Incompatible scheme is rejected

- **GIVEN** an ACTIVE scheme applicable only to LEGAL_ENTITY
- **WHEN** a natural person is registered with that scheme
- **THEN** the response is `422 incompatible-identifier-scheme`
- **AND** no Party or identifier is created

#### Scenario: Unknown or inactive scheme is rejected

- **GIVEN** a registration references an unknown or non-ACTIVE scheme
- **WHEN** the request is submitted
- **THEN** the response is respectively `422 unknown-identifier-scheme` or `422 inactive-identifier-scheme`
- **AND** no Party or identifier is created

### Requirement: Initial identifier semantic validity

**User Story:** As a data steward, I want initial identifiers to satisfy their scheme and validity rules, so that malformed or unusable identifiers do not enter the registry.

WHEN an eligible initial identifier is evaluated,
THE Party Registry SHALL require its complete value, length, format, issuing metadata, and validity dates to satisfy the selected scheme and evaluation date.

IF the identifier violates scheme normalization, validation, length, coherent validity dates, or non-expiration rules,
THEN THE Party Registry SHALL return `422 identifier-validation-failure` without creating a Party or identifier.

WHEN a new identifier is protected,
THE Party Registry SHALL use the validated normalized plaintext without rewriting existing protected identifiers or idempotency results.

IF a dependency required for identifier validation or protection is unavailable,
THEN THE Party Registry SHALL return `503 dependency-unavailable` without creating a Party or identifier.

#### Scenario: Semantically valid identifier is accepted

- **GIVEN** an eligible identifier satisfies its scheme and validity rules
- **WHEN** registration is evaluated
- **THEN** identifier semantic validation succeeds

#### Scenario: Scheme validation rejects the value

- **GIVEN** an identifier does not satisfy its scheme rules
- **WHEN** registration is evaluated
- **THEN** the response is `422 identifier-validation-failure`
- **AND** no Party or identifier is created

#### Scenario: Expiration is omitted or null

- **GIVEN** an otherwise valid identifier omits expiresOn or supplies null, regardless of legacy expiration metadata
- **WHEN** registration is submitted
- **THEN** it is accepted without an expiration date and remains pending verification

#### Scenario: Identifier is already expired

- **GIVEN** expiresOn precedes the evaluation date
- **WHEN** registration is submitted
- **THEN** the response is `422 identifier-validation-failure`

#### Scenario: Identifier dependency is unavailable

- **GIVEN** otherwise valid Party/identifier input and an unavailable required dependency
- **WHEN** registration is submitted
- **THEN** the response is `503 dependency-unavailable`
- **AND** no Party or identifier is created

### Requirement: Tenant-scoped initial identifier uniqueness

**User Story:** As a data steward, I want active identifiers unique within their tenant and scheme, so that one identity cannot create duplicate Parties.

WHILE an identifier is PENDING_VERIFICATION or VERIFIED,
THE Party Registry SHALL prevent another Party in the same tenant and scheme from registering the same normalized identifier value.

IF registration conflicts with an existing active identifier in that tenant and scheme,
THEN THE Party Registry SHALL return `409 identifier-uniqueness-conflict` without changing the existing Party or identifier.

WHEN equivalent registrations with different idempotency keys race for that identifier,
THE Party Registry SHALL make at most one new Party and initial identifier observable.

#### Scenario: Duplicate identifier is rejected in one tenant

- **GIVEN** an existing active identifier in a tenant and scheme
- **WHEN** another registration uses the same normalized value in that tenant and scheme
- **THEN** the response is `409 identifier-uniqueness-conflict`
- **AND** no second Party is created and the existing records remain unchanged

#### Scenario: Identifier uniqueness is tenant-isolated

- **GIVEN** an active identifier exists in one tenant and scheme
- **WHEN** another tenant submits an otherwise valid registration with the same scheme and value
- **THEN** the first tenant's identifier creates no conflict for the other tenant

#### Scenario: Concurrent duplicate registrations have one winner

- **GIVEN** valid concurrent registrations with different keys target the same tenant/scheme/value
- **WHEN** they are processed
- **THEN** at most one creates a Party and initial identifier
- **AND** each uniqueness loser receives `409 identifier-uniqueness-conflict`

### Requirement: Idempotent Party registration includes the initial identifier

**User Story:** As an API consumer, I want retries to reproduce the original Party and identifier result, so that network failures do not create duplicates.

WHEN registration equivalence is evaluated,
THE Party Registry SHALL include all effective Party and initial-identifier inputs.

WHEN the same tenant repeats equivalent input with the same Idempotency-Key,
THE Party Registry SHALL return the original HTTP `201` result, IDs, safe identifier data, and versions without creating additional records.

IF that tenant reuses the key with different effective input,
THEN THE Party Registry SHALL return `409 idempotency-key-conflict` without changing or disclosing the original result.

WHEN equivalent requests with one tenant/key are concurrent,
THE Party Registry SHALL make one Party/identifier outcome observable and return that outcome to successful replays.

#### Scenario: Equivalent retry reproduces original creation

- **GIVEN** completed registration for a tenant and key
- **WHEN** equivalent Party and identifier input is repeated with that key
- **THEN** the original `201 successful` result, identifiers, statuses, and versions are returned
- **AND** no additional records are created

#### Scenario: Retry remains deterministic during dependency failure

- **GIVEN** a completed registration and an unavailable dependency required for new registration
- **WHEN** the same effective input is replayed
- **THEN** the original `201 successful` result is returned

#### Scenario: Reused key with different identifier conflicts

- **GIVEN** a completed registration for a key
- **WHEN** the same tenant changes the identifier scheme, value, or metadata while reusing that key
- **THEN** the response is `409 idempotency-key-conflict`
- **AND** the original Party and identifier remain unchanged

#### Scenario: Concurrent equivalent retries converge

- **GIVEN** equivalent concurrent requests for one tenant/key
- **WHEN** registration completes
- **THEN** exactly one Party and initial identifier become observable
- **AND** every successful replay identifies that same outcome
