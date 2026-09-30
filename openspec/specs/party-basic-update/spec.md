# Party Basic Update Specification

## Purpose

This capability corrects the display name of an existing tenant-owned Party. It protects canonical text, immutable identity, audit history, and concurrent changes while retaining the root Party representation.

## Requirements

### Requirement: Root Party PATCH input contract

**User Story:** As a data steward, I want only supported display-name corrections accepted, so that malformed requests cannot alter unrelated Party information.

WHEN `PATCH /v1/parties/{partyId}` is submitted,
THE Party Registry SHALL apply the shared root Party context and response contract and require a JSON object containing exactly one supported property, `displayName`, with a string value. No `Idempotency-Key` SHALL be required, and this operation SHALL NOT acquire the lifecycle replay behavior merely because that header is supplied.

IF the body is absent or JSON null,
THEN THE Party Registry SHALL return `400 request-body-required`.

IF `displayName` is missing or null,
THEN THE Party Registry SHALL return `400 display-name-required`. The public PATCH contract SHALL declare this code.

IF the JSON is malformed, the body is not an object, a property has an incompatible type, or an unknown or duplicate property is supplied,
THEN THE Party Registry SHALL return `400 bad-request` without applying any correction. Unknown properties SHALL be rejected even alongside a valid display name.

#### Scenario: Missing body and missing value are distinguished

- **WHEN** PATCH has no body or has a JSON null body
- **THEN** the response is `400 request-body-required`
- **AND** separate requests containing `{}` or `{"displayName": null}` return `400 display-name-required`

#### Scenario: Unsupported or coerced input is rejected

- **WHEN** PATCH contains a numeric display name, an array body, duplicate display-name properties, or a valid display name alongside `recordStatus` or type-specific details
- **THEN** the response is `400 bad-request`
- **AND** no supplied field is applied

### Requirement: Canonical display-name correction

**User Story:** As a registry operator, I want readable and canonical display names, so that corrected labels follow the registry's established conventions.

WHEN a display-name correction is evaluated,
THE Party Registry SHALL apply the existing exterior-whitespace removal and locale-independent uppercase rules, preserving accents, punctuation, and interior whitespace. The resulting value SHALL be nonblank and contain at most 300 UTF-16 code units, including uppercase expansion.

IF the normalized value exceeds 300 UTF-16 code units,
THEN THE Party Registry SHALL return `400 display-name-too-long` during body validation.

IF a structurally valid submitted string becomes blank, including an empty submitted string,
THEN THE Party Registry SHALL return `422 blank-display-name` when business validation is reached. The Party, audit information, and version SHALL remain unchanged on rejection.

#### Scenario: Normalization preserves meaningful text

- **GIVEN** a valid tenant-owned Party and its current version
- **WHEN** the client supplies a mixed-case display name with exterior whitespace, accents, punctuation, and repeated interior spaces
- **THEN** the accepted name is trimmed and uppercase while retaining the accents, punctuation, and interior spaces

#### Scenario: Normalized-length boundaries are enforced

- **WHEN** otherwise valid PATCH requests produce normalized names of 300 and 301 UTF-16 code units
- **THEN** the first passes length validation and the second returns `400 display-name-too-long`
- **AND** uppercase expansion counts toward the limit while removed exterior whitespace does not

#### Scenario: Blank display names cannot clear the label

- **GIVEN** a valid tenant-owned Party and its current version
- **WHEN** a structurally valid PATCH supplies an empty or whitespace-only display name
- **THEN** the response is `422 blank-display-name`
- **AND** the existing label, audit information, and version remain unchanged

### Requirement: Display-name update preconditions and precedence

**User Story:** As an API consumer, I want deterministic validation and version failures, so that I can correct requests without overwriting another user's changes.

WHEN a Party display-name update is requested,
THE Party Registry SHALL validate trusted context first, then body syntax/required value/normalized maximum length, then canonical `partyId`, then `If-Match`. Existing framework checks SHALL retain their established precedence.

THE Party Registry SHALL require exactly one `If-Match` matching `^(0|[1-9][0-9]*)$` and not exceeding `9223372036854775807`. Absence, duplicate values, malformed values, and overflow SHALL return HTTP `400` with `if-match-required`, `if-match-duplicated`, `if-match-invalid`, and `if-match-out-of-range`, respectively. A noncanonical Party ID SHALL return `400 party-id-invalid`.

WHEN a syntactically valid update is evaluated,
THE Party Registry SHALL apply business failure precedence in the order tenant-scoped absence, expected-version mismatch, then display-name business validation. An absent or cross-tenant Party SHALL return `404 party-not-found`; a stale version or lost update race SHALL return `412 expected-version-mismatch`.

#### Scenario: Required body validation precedes operation headers

- **GIVEN** valid trusted context
- **WHEN** PATCH contains `{}` and omits `If-Match`
- **THEN** the response is `400 display-name-required`

#### Scenario: Invalid expected versions are rejected explicitly

- **GIVEN** a valid body and Party ID
- **WHEN** `If-Match` is missing, duplicated, quoted, negative, zero-padded, a wildcard, or above the supported range
- **THEN** the corresponding declared `400` precondition-validation code is returned without changes

#### Scenario: Tenant concealment precedes semantic checks

- **GIVEN** a cross-tenant Party ID, a valid but stale expected version, and a blank display name
- **WHEN** PATCH is evaluated
- **THEN** the response is `404 party-not-found`, without disclosing the actual version or stored information

#### Scenario: Stale version precedes blank-name validation

- **GIVEN** a tenant-owned Party and a valid stale expected version
- **WHEN** PATCH supplies a blank display name
- **THEN** the response is `412 expected-version-mismatch` and no correction is applied

### Requirement: Display-name update observable outcome

**User Story:** As a data steward, I want corrections to preserve identity and audit consistency, so that changing a label cannot change who the Party represents.

WHEN a valid display-name PATCH succeeds with the current version,
THE Party Registry SHALL return `200 successful` with the common Party detail representation, store the canonical display name, increment the Party version exactly once, and record the accepted `User-Id` and update time. Identical canonical input SHALL still be an accepted update with one version increment.

THE Party Registry SHALL preserve Party ID, tenant, immutable type, lifecycle status, type-specific details, identifiers and their independent versions, nationalities, creation audit information, and stored registration replay results. A root display-name correction SHALL be permitted in `DRAFT`, `ACTIVE`, `INACTIVE`, and `ARCHIVED`; archival terminality SHALL apply to lifecycle transitions rather than erase the existing ability to correct descriptive information.

WHERE a corresponding update event is enabled,
THE Party Registry SHALL make the event and accepted Party version observable as one complete outcome. Failure before acceptance SHALL leave no partial field, audit, version, or event change.

#### Scenario: Both Party types preserve unrelated details

- **GIVEN** a natural person or legal entity with identifiers and a stored creation result
- **WHEN** its display name is corrected successfully
- **THEN** only the display name, update audit information, and Party version change
- **AND** the response contains the corresponding unchanged type-specific details
- **AND** replaying the creation request still returns its original result

#### Scenario: Archived descriptive information remains correctable

- **GIVEN** a tenant-owned archived Party and its current version
- **WHEN** a valid display-name correction is accepted
- **THEN** the name and update audit information change and the version increments once
- **AND** the Party remains `ARCHIVED`

#### Scenario: Equivalent correction still advances the version

- **GIVEN** the submitted label normalizes to the current display name
- **WHEN** PATCH succeeds at the current version
- **THEN** the label remains equivalent and the returned version increases by exactly one

#### Scenario: Concurrent corrections cannot overwrite each other

- **GIVEN** two valid corrections, or a correction and another Party mutation, target the same current version
- **WHEN** they compete
- **THEN** at most one mutation succeeds at that version
- **AND** a PATCH losing the version race returns `412 expected-version-mismatch`

#### Scenario: Failed correction leaves no partial outcome

- **GIVEN** the update encounters a failure before acceptance and an update event is enabled
- **WHEN** the failure response is returned
- **THEN** the prior name, audit information, and version remain observable
- **AND** no update event from the rejected request becomes observable
