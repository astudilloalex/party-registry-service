## Purpose

This capability defines the observable behavior for registering a natural-person or legal-entity Party with one required initial official identifier. It protects registry completeness, tenant-scoped uniqueness, deterministic retries, and identifier confidentiality while preserving the identifier's independent lifecycle.

## ADDED Requirements

### Requirement: Initial official identifier is mandatory

**User Story:** As a registry operator, I want every newly registered Party to include an official identifier, so that incomplete identity records are not created.

WHEN a client submits `POST /v1/natural-person` or `POST /v1/legal-entity`,
THE Party Registry SHALL require exactly one `initialIdentifier` containing a nonblank `identifierSchemeCode` and a nonblank complete `value`.

IF `initialIdentifier`, `identifierSchemeCode`, or `value` is missing, null, structurally invalid, or blank,
THEN THE Party Registry SHALL reject the request with HTTP `400` and code `bad-request` without creating a Party or identifier.

#### Scenario: Natural person includes one initial identifier

- **GIVEN** valid request context, Party data, idempotency input, and one structurally valid `initialIdentifier`
- **WHEN** the client submits `POST /v1/natural-person`
- **THEN** the Party Registry evaluates the natural person and initial identifier as one registration request

#### Scenario: Legal entity includes one initial identifier

- **GIVEN** valid request context, Party data, idempotency input, and one structurally valid `initialIdentifier`
- **WHEN** the client submits `POST /v1/legal-entity`
- **THEN** the Party Registry evaluates the legal entity and initial identifier as one registration request

#### Scenario: Initial identifier is missing

- **GIVEN** an otherwise valid Party creation request omits `initialIdentifier`
- **WHEN** the request is submitted
- **THEN** the response has HTTP status `400` and code `bad-request`
- **AND** no Party or identifier from the request is created

#### Scenario: Initial identifier value is blank

- **GIVEN** a Party creation request contains a blank initial identifier `value`
- **WHEN** the request is submitted
- **THEN** the response has HTTP status `400` and code `bad-request`
- **AND** no Party or identifier from the request is created

### Requirement: Initial identifier scheme eligibility

**User Story:** As a data steward, I want initial identifiers checked against the authoritative scheme catalog, so that each Party uses an applicable official identifier.

WHEN a structurally valid initial identifier is evaluated,
THE Party Registry SHALL require its scheme code to identify a known `ACTIVE` scheme whose applicable subject type is the Party type or `BOTH`.

IF the scheme is unknown, inactive, or incompatible with the Party type,
THEN THE Party Registry SHALL reject the registration with HTTP `422` and code `unprocessable-entity` without creating a Party or identifier.

#### Scenario: Natural-person scheme is accepted

- **GIVEN** an `ACTIVE` identifier scheme applicable to `NATURAL_PERSON` or `BOTH`
- **WHEN** a natural person is registered with that scheme code
- **THEN** the scheme passes eligibility validation

#### Scenario: Legal-entity scheme is accepted

- **GIVEN** an `ACTIVE` identifier scheme applicable to `LEGAL_ENTITY` or `BOTH`
- **WHEN** a legal entity is registered with that scheme code
- **THEN** the scheme passes eligibility validation

#### Scenario: Incompatible scheme is rejected

- **GIVEN** an `ACTIVE` identifier scheme applicable only to `LEGAL_ENTITY`
- **WHEN** a natural person is registered with that scheme code
- **THEN** the response has HTTP status `422` and code `unprocessable-entity`
- **AND** no Party or identifier from the request is created

#### Scenario: Unknown or inactive scheme is rejected

- **GIVEN** an initial identifier references an unknown, `DRAFT`, `DEPRECATED`, or `RETIRED` scheme
- **WHEN** the Party registration is submitted
- **THEN** the response has HTTP status `422` and code `unprocessable-entity`
- **AND** no Party or identifier from the request is created

### Requirement: Initial identifier semantic validity

**User Story:** As a data steward, I want initial identifiers to satisfy their scheme and validity rules, so that malformed or unusable identifiers do not enter the registry.

WHEN an eligible initial identifier is evaluated,
THE Party Registry SHALL require its complete value, length, format, issuing metadata, and validity dates to satisfy the selected scheme and the request evaluation date.

IF the identifier fails scheme normalization or validation, exceeds scheme length limits, has incoherent validity dates, omits an expiration required by the scheme, or is already expired,
THEN THE Party Registry SHALL reject the registration with HTTP `422` and code `unprocessable-entity` without creating a Party or identifier.

IF a dependency required to validate or protect the identifier is unavailable,
THEN THE Party Registry SHALL reject the registration with HTTP `503` and code `dependency-unavailable` without creating a Party or identifier.

#### Scenario: Semantically valid identifier is accepted

- **GIVEN** an initial identifier satisfies its eligible scheme and validity rules
- **WHEN** the Party registration is submitted
- **THEN** the identifier passes semantic validation

#### Scenario: Scheme validation rejects the value

- **GIVEN** an initial identifier value does not satisfy its scheme rules
- **WHEN** the Party registration is submitted
- **THEN** the response has HTTP status `422` and code `unprocessable-entity`
- **AND** no Party or identifier from the request is created

#### Scenario: Required expiration is missing

- **GIVEN** the selected scheme requires expiration and `expiresOn` is absent
- **WHEN** the Party registration is submitted
- **THEN** the response has HTTP status `422` and code `unprocessable-entity`

#### Scenario: Identifier is already expired

- **GIVEN** `expiresOn` is earlier than the request evaluation date
- **WHEN** the Party registration is submitted
- **THEN** the response has HTTP status `422` and code `unprocessable-entity`

#### Scenario: Identifier dependency is unavailable

- **GIVEN** the Party and identifier inputs are otherwise valid
- **AND** required identifier validation or protection cannot be completed because a dependency is unavailable
- **WHEN** the Party registration is submitted
- **THEN** the response has HTTP status `503` and code `dependency-unavailable`
- **AND** no Party or identifier from the request is created

### Requirement: Party and initial identifier creation is all-or-nothing

**User Story:** As a registry operator, I want Party registration to have one outcome, so that no incomplete Party or orphaned identifier becomes observable.

WHEN a Party registration succeeds,
THE Party Registry SHALL create exactly one tenant-scoped Party, exactly one matching type-specific detail representation, exactly one initial identifier associated with that Party, one completed idempotency outcome, and each enabled integration event as one indivisible business outcome.

IF any registration step fails,
THEN THE Party Registry SHALL leave no Party, type-specific details, initial identifier, completed idempotency outcome, or integration event from that request observable as created.

WHEN registration succeeds,
THE Party Registry SHALL initialize the Party with record status `DRAFT` and version `0` and initialize the identifier with status `PENDING_VERIFICATION` and version `0`.

#### Scenario: Natural-person registration succeeds completely

- **GIVEN** a valid natural-person registration request and enabled creation events
- **WHEN** registration succeeds
- **THEN** exactly one natural-person Party and its initial identifier are observable
- **AND** the Party is `DRAFT` at version `0`
- **AND** the identifier is `PENDING_VERIFICATION` at version `0`
- **AND** each enabled creation event is observable once

#### Scenario: Legal-entity registration succeeds completely

- **GIVEN** a valid legal-entity registration request
- **WHEN** registration succeeds
- **THEN** exactly one legal-entity Party and its initial identifier are observable
- **AND** no natural-person details are created for that Party

#### Scenario: Late registration failure leaves no partial state

- **GIVEN** a valid Party registration request reaches a failure before the complete outcome is accepted
- **WHEN** the registration is rejected
- **THEN** no Party, details, identifier, idempotency outcome, or event from the request is observable as created

### Requirement: Initial identifier confidentiality

**User Story:** As a privacy stakeholder, I want complete identifier values protected from disclosure, so that sensitive identity data is not exposed through public or operational outputs.

WHEN the Party Registry accepts a complete initial identifier value,
THE Party Registry SHALL use it only to validate and register a protected identifier representation.

THE Party Registry SHALL expose only safe identifier information, including the masked value, through API responses and integration events.

THE Party Registry SHALL NOT expose the complete value, normalized value, protection material, or unprotected fingerprint through API responses, integration events, application logs, or idempotent replay results.

#### Scenario: Creation response contains only a masked identifier

- **GIVEN** a Party is registered successfully
- **WHEN** the client receives the creation response
- **THEN** `initialIdentifier` contains a masked value
- **AND** the response contains no complete value, normalized value, protection material, or unprotected fingerprint

#### Scenario: Creation event excludes sensitive identifier data

- **GIVEN** Party registration produces an enabled integration event
- **WHEN** the event becomes observable to a consumer
- **THEN** the event contains no complete value, normalized value, protection material, or unprotected fingerprint

#### Scenario: Idempotent replay remains confidential

- **GIVEN** a completed Party registration is replayed
- **WHEN** the original result is returned
- **THEN** the replay contains the same safe masked identifier information
- **AND** no complete or reversible identifier representation is exposed

### Requirement: Tenant-scoped initial identifier uniqueness

**User Story:** As a data steward, I want active official identifiers unique within their tenant and scheme, so that one identity cannot create duplicate Parties.

WHILE an identifier is `PENDING_VERIFICATION` or `VERIFIED`,
THE Party Registry SHALL prevent another Party in the same tenant and identifier scheme from registering the same normalized identifier value.

IF a Party registration conflicts with an existing active identifier in the same tenant and scheme,
THEN THE Party Registry SHALL reject the registration with HTTP `409` and code `conflict` without changing the existing Party or identifier.

WHEN equivalent registrations with different idempotency keys race for the same tenant-scoped identifier,
THE Party Registry SHALL make at most one new Party and initial identifier observable.

#### Scenario: Duplicate identifier is rejected in one tenant

- **GIVEN** one Party owns a `PENDING_VERIFICATION` or `VERIFIED` identifier in a tenant and scheme
- **WHEN** another Party registration uses the same normalized identifier in that tenant and scheme
- **THEN** the response has HTTP status `409` and code `conflict`
- **AND** the existing Party and identifier remain unchanged
- **AND** no second Party is created

#### Scenario: Identifier uniqueness is tenant-isolated

- **GIVEN** an active identifier exists in one tenant and scheme
- **WHEN** a different tenant submits an otherwise valid registration with the same scheme and normalized value
- **THEN** the existing identifier in the first tenant does not create a uniqueness conflict in the second tenant

#### Scenario: Concurrent duplicate registrations have one winner

- **GIVEN** two valid registration requests use different idempotency keys and the same tenant, scheme, and normalized identifier
- **WHEN** the requests are processed concurrently
- **THEN** at most one request creates a Party and initial identifier
- **AND** each losing request receives HTTP `409` and code `conflict`

### Requirement: Idempotent Party registration includes the initial identifier

**User Story:** As an API consumer, I want retries to reproduce the original Party and identifier result, so that network failures do not create duplicates or change accepted identity data.

WHEN a client submits a Party registration,
THE Party Registry SHALL include all effective Party and initial-identifier inputs when determining idempotent request equivalence.

WHEN the same tenant repeats an equivalent request with the same `Idempotency-Key`,
THE Party Registry SHALL return the original HTTP `201` result with the original Party, initial identifier, and versions without creating another Party or identifier.

IF the same tenant reuses an `Idempotency-Key` with different effective Party or initial-identifier input,
THEN THE Party Registry SHALL reject the request with HTTP `409` and code `conflict` without changing or disclosing the original result.

WHEN equivalent requests with the same tenant and idempotency key are processed concurrently,
THE Party Registry SHALL make one Party and one initial identifier observable and SHALL return that original result to each successful replay.

#### Scenario: Equivalent retry reproduces original creation

- **GIVEN** a Party was registered for a tenant and idempotency key
- **WHEN** the tenant repeats the equivalent Party and initial-identifier request with that key
- **THEN** the response reproduces the original HTTP `201 successful` result
- **AND** it returns the original Party ID, identifier ID, masked value, statuses, and versions
- **AND** no additional Party or identifier is created

#### Scenario: Retry remains deterministic during dependency failure

- **GIVEN** a completed Party registration exists for an idempotency key and effective request
- **AND** a dependency used during new identifier registration is now unavailable
- **WHEN** the tenant repeats the equivalent request with the same key
- **THEN** the Party Registry returns the original HTTP `201 successful` result

#### Scenario: Reused key with different identifier conflicts

- **GIVEN** a completed Party registration exists for an idempotency key
- **WHEN** the tenant reuses the key with a different identifier scheme, complete value, or identifier metadata
- **THEN** the response has HTTP status `409` and code `conflict`
- **AND** the original Party and identifier remain unchanged

#### Scenario: Concurrent equivalent retries converge

- **GIVEN** equivalent Party registration requests use the same tenant and idempotency key
- **WHEN** the requests are processed concurrently
- **THEN** exactly one Party and one initial identifier become observable
- **AND** each successful response identifies that same Party and identifier

### Requirement: Party creation success response includes the protected identifier

**User Story:** As an API consumer, I want the creation result to identify the Party and its initial identifier safely, so that I can continue the verification workflow without receiving sensitive values.

WHEN natural-person registration succeeds,
THE Party Registry SHALL return `NaturalPersonCreateApiResponse` with HTTP status `201`, body status `201`, code `successful`, and the created natural person plus its `initialIdentifier`.

WHEN legal-entity registration succeeds,
THE Party Registry SHALL return `LegalEntityCreateApiResponse` with HTTP status `201`, body status `201`, code `successful`, and the created legal entity plus its `initialIdentifier`.

WHEN either creation response is returned,
THE Party Registry SHALL include the identifier ID, Party ID, scheme ID, scheme code, masked value, status `PENDING_VERIFICATION`, declared safe metadata, version, and timestamps without returning the complete value.

#### Scenario: Natural-person creation response is complete and safe

- **GIVEN** a natural person and initial identifier are registered successfully
- **WHEN** the response is returned
- **THEN** the HTTP and body statuses are `201` and the code is `successful`
- **AND** `data` contains the natural-person representation and required `initialIdentifier`
- **AND** the initial identifier status is `PENDING_VERIFICATION`
- **AND** the complete identifier value is absent

#### Scenario: Legal-entity creation response is complete and safe

- **GIVEN** a legal entity and initial identifier are registered successfully
- **WHEN** the response is returned
- **THEN** the HTTP and body statuses are `201` and the code is `successful`
- **AND** `data` contains the legal-entity representation and required `initialIdentifier`
- **AND** the complete identifier value is absent

### Requirement: Additional identifiers remain independently registrable

**User Story:** As a registry operator, I want to add official identifiers after Party creation, so that a Party can hold multiple documents with independent lifecycles.

WHEN a Party already exists,
THE Party Registry SHALL continue to accept eligible additional identifiers through `POST /v1/parties/{partyId}/identifiers` without recreating or reclassifying the Party.

#### Scenario: Additional identifier is registered after creation

- **GIVEN** an existing Party with its initial identifier
- **WHEN** an eligible different identifier is submitted to `POST /v1/parties/{partyId}/identifiers`
- **THEN** the additional identifier is associated with the existing Party
- **AND** the Party ID and immutable Party type remain unchanged
