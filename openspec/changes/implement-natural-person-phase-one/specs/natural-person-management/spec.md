## Purpose

This capability defines the externally observable behavior for creating, retrieving, replacing, and partially updating tenant-scoped natural persons. It ensures valid natural-person data, consistent API responses, optimistic concurrency, idempotent creation, and strict separation from legal entities.

## ADDED Requirements

### Requirement: Trusted request context

**User Story:** As an API consumer, I want every natural-person operation to use validated request context, so that requests are traceable and tenant-scoped.

WHEN a client invokes a natural-person endpoint,
THE Party Registry SHALL require exactly one `Tenant-Id`, one `User-Id`, and one `Process-Id` header.

IF `Tenant-Id` or `Process-Id` is not a canonical UUID, or `User-Id` is blank, unsafe, or longer than 128 characters,
THEN THE Party Registry SHALL reject the request with HTTP `400` and code `bad-request`.

WHEN a valid `Process-Id` is accepted,
THE Party Registry SHALL echo it unchanged in the response.

IF `Process-Id` is missing, duplicated, or invalid,
THEN THE Party Registry SHALL omit `Process-Id` from the response.

#### Scenario: Valid request context is accepted

- **GIVEN** a request containing one canonical `Tenant-Id`, one safe `User-Id`, and one canonical `Process-Id`
- **WHEN** the client invokes a natural-person endpoint
- **THEN** the request is evaluated within the indicated tenant
- **AND** the response echoes the accepted `Process-Id` unchanged

#### Scenario: Missing request context is rejected

- **GIVEN** a request is missing one required context header
- **WHEN** the client invokes a natural-person endpoint
- **THEN** the response has HTTP status `400` and code `bad-request`
- **AND** the operation produces no natural-person state change

#### Scenario: Duplicated request context is rejected

- **GIVEN** a request contains more than one value for a required context header
- **WHEN** the client invokes a natural-person endpoint
- **THEN** the response has HTTP status `400` and code `bad-request`

### Requirement: Natural-person request validation

**User Story:** As a registry operator, I want malformed natural-person requests rejected consistently, so that invalid representations never enter the registry.

WHEN a create or replacement request is submitted,
THE Party Registry SHALL require nonblank `givenNames` and `familyNames` values of at most 200 characters each.

WHEN optional natural-person fields are submitted,
THE Party Registry SHALL enforce `displayName` at no more than 300 characters, `preferredName` at no more than 200 characters, valid ISO calendar dates, and `birthCountryCode` as exactly two uppercase letters.

IF a request body is missing, malformed, contains unsupported properties, violates a field constraint, or contains only whitespace for a required name,
THEN THE Party Registry SHALL reject the request with HTTP `400` and code `bad-request`.

IF a path `partyId` is not a canonical UUID,
THEN THE Party Registry SHALL reject the request with HTTP `400` and code `bad-request`.

#### Scenario: Required name is missing

- **GIVEN** a create or replacement request omits `givenNames` or `familyNames`
- **WHEN** the request is submitted
- **THEN** the response has HTTP status `400` and code `bad-request`
- **AND** no natural-person state is changed

#### Scenario: Whitespace-only name is rejected

- **GIVEN** a create or replacement request contains only whitespace in a required name
- **WHEN** the request is submitted
- **THEN** the response has HTTP status `400` and code `bad-request`

#### Scenario: Invalid path identifier is rejected

- **GIVEN** `partyId` is not a canonical UUID
- **WHEN** a retrieve, replacement, or partial-update request is submitted
- **THEN** the response has HTTP status `400` and code `bad-request`

#### Scenario: Unknown request property is rejected

- **GIVEN** a natural-person request body contains a property not defined by the operation contract
- **WHEN** the request is submitted
- **THEN** the response has HTTP status `400` and code `bad-request`

### Requirement: Natural-person lifecycle date consistency

**User Story:** As a data steward, I want natural-person lifecycle dates to be coherent, so that the registry does not accept impossible timelines.

WHEN `birthDate` or `dateOfDeath` is provided,
THE Party Registry SHALL require each date not to be later than the calendar date on which the request is evaluated.

IF both lifecycle dates are present in the resulting natural-person state and `dateOfDeath` is earlier than `birthDate`,
THEN THE Party Registry SHALL reject the request with HTTP `422` and code `unprocessable-entity`.

WHEN a partial update changes either lifecycle date,
THE Party Registry SHALL validate the resulting state using both changed and retained values.

#### Scenario: Coherent lifecycle dates are accepted

- **GIVEN** `birthDate` is not in the future and `dateOfDeath` is on or after `birthDate`
- **WHEN** a natural-person create or update request is submitted
- **THEN** the lifecycle dates pass business validation

#### Scenario: Death before birth is rejected

- **GIVEN** the resulting `dateOfDeath` is earlier than the resulting `birthDate`
- **WHEN** a natural-person create or update request is submitted
- **THEN** the response has HTTP status `422` and code `unprocessable-entity`
- **AND** no natural-person state is changed

#### Scenario: Future lifecycle date is rejected

- **GIVEN** `birthDate` or `dateOfDeath` is later than the request evaluation date
- **WHEN** a natural-person create or update request is submitted
- **THEN** the response has HTTP status `422` and code `unprocessable-entity`

### Requirement: Birth-country validation

**User Story:** As a data steward, I want birth-country codes validated against authoritative reference data, so that natural-person records use recognized countries.

WHEN a non-null `birthCountryCode` is supplied or changed,
THE Party Registry SHALL accept it only when the Geographic Reference Service confirms the code as a valid country reference.

IF the Geographic Reference Service does not recognize the supplied code as a valid country reference,
THEN THE Party Registry SHALL reject the request with HTTP `422` and code `unprocessable-entity`.

IF country validation cannot be completed because the Geographic Reference Service is unavailable,
THEN THE Party Registry SHALL reject the request with HTTP `503` and code `dependency-unavailable` without changing natural-person state.

#### Scenario: Recognized birth country is accepted

- **GIVEN** `birthCountryCode` has the required uppercase two-letter format
- **AND** the Geographic Reference Service confirms the country reference
- **WHEN** a natural-person create or update request is submitted
- **THEN** the birth country passes business validation

#### Scenario: Unknown birth country is rejected

- **GIVEN** the Geographic Reference Service does not recognize `birthCountryCode`
- **WHEN** a natural-person create or update request is submitted
- **THEN** the response has HTTP status `422` and code `unprocessable-entity`
- **AND** no natural-person state is changed

#### Scenario: Country dependency is unavailable

- **GIVEN** a request requires birth-country validation
- **AND** the Geographic Reference Service is unavailable
- **WHEN** the request is submitted
- **THEN** the response has HTTP status `503` and code `dependency-unavailable`
- **AND** no natural-person state is changed

### Requirement: Natural-person creation

**User Story:** As a registry operator, I want to create a natural person, so that an individual is represented in the Party Registry.

WHEN a valid `POST /v1/natural-person` request is accepted,
THE Party Registry SHALL create one tenant-scoped party classified as `NATURAL_PERSON` together with exactly one matching natural-person detail record.

WHEN natural-person creation succeeds,
THE Party Registry SHALL return HTTP `201` with code `successful`, initial record status `DRAFT`, initial version `0`, and the created natural-person data.

WHEN `displayName` is omitted or null during creation,
THE Party Registry SHALL derive it from `givenNames` and `familyNames` separated by one space after removing surrounding whitespace from both values.

WHEN a nonblank `displayName` is supplied during creation,
THE Party Registry SHALL use the supplied value.

IF any part of natural-person creation fails,
THEN THE Party Registry SHALL leave neither a party root nor natural-person details observable as created.

#### Scenario: Natural person is created

- **GIVEN** valid request context, a valid idempotency key, and a valid create body
- **WHEN** the client submits `POST /v1/natural-person`
- **THEN** the response has HTTP status `201`, body status `201`, and code `successful`
- **AND** the response data has type `NATURAL_PERSON`, record status `DRAFT`, and version `0`
- **AND** the response contains the created natural-person details and audit information

#### Scenario: Display name is derived

- **GIVEN** a valid create request omits `displayName`
- **WHEN** the natural person is created
- **THEN** the response display name consists of the validated given names and family names separated by one space

#### Scenario: Failed creation leaves no partial party

- **GIVEN** a create request passes structural validation but fails a business or dependency validation
- **WHEN** creation is rejected
- **THEN** no party root or natural-person detail created by that request can be retrieved

### Requirement: Idempotent natural-person creation

**User Story:** As an API consumer, I want safe creation retries, so that transport failures do not create duplicate natural persons.

WHEN a client submits `POST /v1/natural-person`,
THE Party Registry SHALL require exactly one nonblank `Idempotency-Key` of at most 128 characters.

WHEN the same tenant repeats a create request with the same idempotency key and the same effective request body,
THE Party Registry SHALL return the original HTTP `201` success response without creating another party.

IF the same tenant reuses an idempotency key for a different effective request body,
THEN THE Party Registry SHALL reject the request with HTTP `409` and code `conflict` without changing the original result.

WHEN equivalent requests with the same tenant and idempotency key are processed concurrently,
THE Party Registry SHALL make only one created natural person observable and SHALL return that result to each successful replay.

#### Scenario: Missing idempotency key is rejected

- **GIVEN** a create request has no `Idempotency-Key`
- **WHEN** the client submits `POST /v1/natural-person`
- **THEN** the response has HTTP status `400` and code `bad-request`
- **AND** no natural person is created

#### Scenario: Identical retry returns the original result

- **GIVEN** a natural person was created for a tenant and idempotency key
- **WHEN** the same tenant repeats the request with the same effective body and key
- **THEN** the response reproduces the original `201 successful` result
- **AND** no additional party is created

#### Scenario: Idempotency key payload conflict is rejected

- **GIVEN** an idempotency key already identifies a completed create request for the tenant
- **WHEN** the same tenant submits a different effective body with that key
- **THEN** the response has HTTP status `409` and code `conflict`
- **AND** the original natural person remains unchanged

### Requirement: Natural-person retrieval

**User Story:** As an authorized consumer, I want to retrieve a natural person, so that I can use the current registry data.

WHEN `GET /v1/natural-person/{partyId}` identifies a natural person belonging to the requesting tenant,
THE Party Registry SHALL return HTTP `200` with code `successful` and the current natural-person aggregate.

IF `partyId` does not identify a natural person belonging to the requesting tenant,
THEN THE Party Registry SHALL return HTTP `404` and code `not-found`.

#### Scenario: Existing natural person is returned

- **GIVEN** a natural person exists under the requesting tenant
- **WHEN** the client retrieves it by `partyId`
- **THEN** the response has HTTP status `200`, body status `200`, and code `successful`
- **AND** response data contains type `NATURAL_PERSON`, natural-person details, current version, timestamps, and audit users

#### Scenario: Cross-tenant natural person is concealed

- **GIVEN** `partyId` belongs to a different tenant
- **WHEN** the client retrieves it using the requesting tenant context
- **THEN** the response has HTTP status `404` and code `not-found`

#### Scenario: Legal entity is not exposed as a natural person

- **GIVEN** `partyId` identifies a party classified as `LEGAL_ENTITY`
- **WHEN** the client invokes the natural-person retrieval endpoint
- **THEN** the response has HTTP status `404` and code `not-found`
- **AND** no legal-entity data is exposed

### Requirement: Complete natural-person replacement

**User Story:** As a registry operator, I want to replace natural-person details, so that the complete current representation can be corrected safely.

WHEN a valid `PUT /v1/natural-person/{partyId}` request identifies a natural person in the requesting tenant and supplies its current version,
THE Party Registry SHALL replace all natural-person detail fields with the submitted representation and return HTTP `200` with code `successful`.

WHEN an optional field is omitted from a replacement request,
THE Party Registry SHALL clear that field in the resulting natural-person state.

WHEN replacement changes `givenNames` or `familyNames`,
THE Party Registry SHALL derive the resulting display name from the resulting given names and family names separated by one space.

IF replacement fails validation or persistence,
THEN THE Party Registry SHALL preserve the complete pre-request natural-person state and version.

#### Scenario: Natural-person details are replaced

- **GIVEN** an existing natural person and an `If-Match` value equal to its current version
- **WHEN** the client submits a valid replacement body
- **THEN** the response has HTTP status `200`, body status `200`, and code `successful`
- **AND** the returned details equal the submitted representation
- **AND** the returned version is incremented by one

#### Scenario: Omitted optional replacement fields are cleared

- **GIVEN** an existing natural person has optional detail fields populated
- **WHEN** a valid replacement request omits those fields
- **THEN** those optional fields are null in the resulting natural-person data

### Requirement: Partial natural-person update

**User Story:** As a registry operator, I want to update selected natural-person fields, so that unrelated data remains unchanged.

WHEN a valid `PATCH /v1/natural-person/{partyId}` request identifies a natural person in the requesting tenant and supplies its current version,
THE Party Registry SHALL update only the submitted natural-person fields and return HTTP `200` with code `successful`.

WHEN an optional nullable field is explicitly set to null in a partial update,
THE Party Registry SHALL clear that field.

IF a partial-update body contains no supported property,
THEN THE Party Registry SHALL reject the request with HTTP `400` and code `bad-request`.

IF `givenNames` or `familyNames` is submitted as null or blank,
THEN THE Party Registry SHALL reject the request with HTTP `400` and code `bad-request`.

WHEN a partial update changes `givenNames` or `familyNames`,
THE Party Registry SHALL derive the resulting display name from the resulting given names and family names separated by one space.

#### Scenario: One field is updated

- **GIVEN** an existing natural person and an `If-Match` value equal to its current version
- **WHEN** the client submits a partial update containing one valid changed field
- **THEN** the submitted field is updated
- **AND** omitted fields retain their prior values
- **AND** the returned version is incremented by one

#### Scenario: Nullable field is cleared

- **GIVEN** an existing natural person has a preferred name
- **WHEN** the client submits a valid partial update with `preferredName` set to null
- **THEN** the returned preferred name is null
- **AND** all omitted fields retain their prior values

#### Scenario: Empty partial update is rejected

- **GIVEN** a partial-update body has no supported properties
- **WHEN** the client submits the request
- **THEN** the response has HTTP status `400` and code `bad-request`
- **AND** the natural person and version remain unchanged

### Requirement: Optimistic concurrency

**User Story:** As an API consumer, I want updates protected by an expected version, so that concurrent changes are not silently overwritten.

WHEN a client submits a replacement or partial update,
THE Party Registry SHALL require exactly one `If-Match` header containing the nonnegative decimal representation of the expected aggregate version.

IF `If-Match` is missing, duplicated, or malformed,
THEN THE Party Registry SHALL reject the request with HTTP `400` and code `bad-request`.

IF the expected version does not equal the current aggregate version,
THEN THE Party Registry SHALL reject the request with HTTP `412` and code `precondition-failed` without changing natural-person state.

WHEN a replacement or partial update succeeds,
THE Party Registry SHALL increment the aggregate version by exactly one and return the new version.

#### Scenario: Current expected version permits update

- **GIVEN** `If-Match` equals the natural person's current version
- **WHEN** a valid replacement or partial update is submitted
- **THEN** the update succeeds
- **AND** the returned version is the prior version plus one

#### Scenario: Stale expected version is rejected

- **GIVEN** `If-Match` does not equal the natural person's current version
- **WHEN** a replacement or partial update is submitted
- **THEN** the response has HTTP status `412` and code `precondition-failed`
- **AND** the natural person and version remain unchanged

#### Scenario: Concurrent updates do not overwrite each other

- **GIVEN** two update requests use the same current version for one natural person
- **WHEN** the requests are processed concurrently
- **THEN** at most one update succeeds for that version
- **AND** each request that loses the version race receives HTTP `412` and code `precondition-failed`

### Requirement: Natural-person and legal-entity exclusivity

**User Story:** As a data steward, I want party classifications to be mutually exclusive, so that a party cannot represent both an individual and a legal entity.

THE Party Registry SHALL keep a party's classification immutable after creation.

THE Party Registry SHALL ensure that a party classified as `NATURAL_PERSON` has natural-person details and no legal-entity details.

IF a natural-person retrieve or update operation identifies a party classified as `LEGAL_ENTITY`,
THEN THE Party Registry SHALL return HTTP `404` and code `not-found` without exposing or changing the legal entity.

IF an operation would produce both natural-person and legal-entity details for one party,
THEN THE Party Registry SHALL reject the operation without making a partial state change.

#### Scenario: Created party has only natural-person details

- **GIVEN** a valid natural-person create request
- **WHEN** creation succeeds
- **THEN** the created party is classified as `NATURAL_PERSON`
- **AND** it has exactly one natural-person detail representation
- **AND** it has no legal-entity detail representation

#### Scenario: Legal entity cannot be updated through natural-person endpoint

- **GIVEN** a party is classified as `LEGAL_ENTITY`
- **WHEN** a replacement or partial update is submitted through its natural-person endpoint
- **THEN** the response has HTTP status `404` and code `not-found`
- **AND** the legal entity remains unchanged

### Requirement: Standard API responses

**User Story:** As an API consumer, I want consistent response envelopes, so that success and failure handling is predictable.

WHEN a natural-person operation succeeds,
THE Party Registry SHALL return an envelope containing `status`, code `successful`, and natural-person `data`, with the body status equal to the HTTP status.

WHEN a natural-person operation fails,
THE Party Registry SHALL return an envelope containing matching HTTP and body statuses and a stable error code without `data`, exception messages, stack traces, SQL details, or internal causes.

IF caller authentication is missing or invalid,
THEN THE Party Registry SHALL return HTTP `401` and code `unauthorized`.

IF an unsupported HTTP method targets a natural-person path,
THEN THE Party Registry SHALL return HTTP `405` and code `method-not-allowed`.

IF an unexpected failure occurs,
THEN THE Party Registry SHALL return HTTP `500` and code `server-error` without exposing internal details.

#### Scenario: Creation success envelope is consistent

- **GIVEN** a valid natural-person create request
- **WHEN** creation succeeds
- **THEN** both HTTP and body status are `201`
- **AND** the code is `successful`
- **AND** `data` contains an API natural-person representation rather than an internal entity

#### Scenario: Retrieval and update success envelopes are consistent

- **GIVEN** a valid natural-person retrieval or update request
- **WHEN** the operation succeeds
- **THEN** both HTTP and body status are `200`
- **AND** the code is `successful`
- **AND** `data` contains the natural-person representation

#### Scenario: Unexpected failure is sanitized

- **GIVEN** an unexpected failure occurs while processing a natural-person request
- **WHEN** the failure response is returned
- **THEN** both HTTP and body status are `500`
- **AND** the code is `server-error`
- **AND** the response contains no internal failure detail or `data`
