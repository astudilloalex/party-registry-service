# party-identifier-queries Specification

## Purpose

This capability defines tenant- and Party-scoped discovery of official identifiers through safe detail and paginated collection responses. It also defines the public context, confidentiality, and error contract shared by all eight identifier operations.

Each requirement's first scenario states shared normative rules followed by its observable outcome. Those shared rules apply to every scenario of that requirement unless a clause explicitly limits its scope.

## Requirements

### Requirement: Complete published party-identifiers contract

**User Story:** As an API consumer, I want documentation that matches every identifier operation, so that I can integrate against a complete contract.

THE Party Registry SHALL implement all eight approved identifier operations and publish matching inputs, semantics, statuses, examples, and safe response schemas under the shared normative rules below.

#### Scenario: Eight operations agree with their published contract

##### Shared normative rules

THE Party Registry SHALL publish and implement `POST` and `GET /v1/parties/{partyId}/identifiers`, `GET` and `PATCH /v1/parties/{partyId}/identifiers/{identifierId}`, and `POST /v1/parties/{partyId}/identifiers/{identifierId}/verify`, `/reject`, `/revoke`, and `/set-primary`.

THE published OpenAPI SHALL define the context headers, path/query inputs, body requiredness, strict property types and nullability, identifier-version preconditions, idempotency semantics, transition matrix, primary scope, validation precedence, and success/error examples specified by this capability, `party-identifier-maintenance`, and `party-identifier-lifecycle`. It SHALL declare `201` for registration and `200` for other successes. It SHALL declare the approved new codes `identifier-not-found`, `invalid-identifier-lifecycle`, `primary-identifier-conflict`, and `identifier-version-exhausted` with their specified statuses before the new operations are implemented. The served document SHALL match the approved source and contain no unresolved references in the affected operations.

##### Observable outcome

- **WHEN** a consumer inspects the served OpenAPI and exercises each route with valid input and an eligible target
- **THEN** all eight operations are present and return the documented status and safe response shape
- **AND** examples conform to their declared schemas, including nullability, pagination, and HTTP/body status agreement

### Requirement: Trusted context and process correlation

**User Story:** As an operator, I want consistent attribution and correlation, so that concurrent identifier requests remain traceable.

WHEN an identifier operation is requested, THE Party Registry SHALL validate trusted context in the declared order, isolate accepted attribution, and echo only an accepted Process-Id under the shared normative rules below.

#### Scenario: Header precedence and accepted echo

##### Shared normative rules

WHEN any of the eight identifier operations is requested,
THE Party Registry SHALL require exactly one `Process-Id`, `Tenant-Id`, and `User-Id`, checking them in that order. Within each header, absence SHALL precede duplication and then value validation. Header names SHALL be case-insensitive. Process and tenant values SHALL be exact canonical lowercase UUIDs without surrounding whitespace; user values SHALL be nonblank, at most 128 Unicode code points, and free of C0, DEL, and C1 control characters, checked in that order.

IF a context rule fails,
THEN THE Party Registry SHALL return the corresponding existing `400 process-id-*`, `tenant-id-*`, or `user-id-*` code before operation validation or business evaluation. Only accepted values SHALL enter completion logging. Context SHALL remain attributed to its own request and SHALL NOT leak to subsequent or concurrent requests. `/q` management endpoints SHALL remain exempt.

WHERE `Process-Id` is accepted,
THE Party Registry SHALL echo it unchanged on every success or error response, including later header failures and historical replay. Missing, duplicated, and invalid process values SHALL NOT be echoed.

##### Observable outcome

- **WHEN** a request has an accepted Process-Id, a duplicated Tenant-Id, an unsafe User-Id, and invalid operation input
- **THEN** the response is `400 tenant-id-duplicated` and echoes the accepted Process-Id unchanged
- **AND** no identifier operation occurs and rejected values are absent from completion logs

#### Scenario: Invalid process is not echoed

- **WHEN** Process-Id is missing, duplicated, uppercase, whitespace-padded, or malformed
- **THEN** the respective required, duplicated, or invalid process code is returned with HTTP 400
- **AND** the response has no Process-Id header

#### Scenario: Concurrent context remains isolated

- **WHEN** concurrent successes, replays, and failures use different accepted processes, users, and tenants
- **THEN** each response and completion log is attributed only to its own accepted context
- **AND** a later request inherits none of their values

### Requirement: Tenant and Party ownership concealment

**User Story:** As a tenant administrator, I want identifier access isolated by both tenant and Party, so that nested routes cannot disclose another owner's records.

THE Party Registry SHALL enforce tenant and Party ownership before non-replay identifier evaluation, conceal foreign records, and support both Party types and every owner lifecycle state under the shared normative rules below.

#### Scenario: Missing and cross-tenant Party are indistinguishable

##### Shared normative rules

WHEN a syntactically valid non-replay identifier operation is evaluated,
THE Party Registry SHALL first establish that the Party belongs to the accepted tenant. An absent or cross-tenant Party SHALL produce `404 party-not-found`. On a single-identifier route, an identifier absent under that owned Party, belonging to another Party, or belonging to another tenant SHALL produce `404 identifier-not-found` without exposing its actual owner, scheme, status, or version.

THE Party Registry SHALL support owned natural-person and legal-entity Parties in `DRAFT`, `ACTIVE`, `INACTIVE`, and `ARCHIVED`. Party state SHALL NOT itself change the identifier's read or maintenance eligibility. Historical keyed replay SHALL follow its tenant-qualified action scope rather than reevaluating current target state.

##### Observable outcome

- **WHEN** list or detail is requested for an absent Party or a Party owned by another tenant
- **THEN** both responses are `404 party-not-found` with no owner or identifier information

#### Scenario: Nested identifier cannot escape its Party

- **GIVEN** the requested Party belongs to the caller's tenant
- **WHEN** detail or a mutation targets an absent identifier, another Party's identifier, or another tenant's identifier
- **THEN** the response is `404 identifier-not-found` without disclosing that target

#### Scenario: Both Party types and every Party state remain readable

- **WHEN** identifiers are listed or retrieved for owned Parties of either type in each Party lifecycle state
- **THEN** read eligibility depends on ownership rather than type or Party lifecycle status

### Requirement: Strict read selectors and filters

**User Story:** As an API consumer, I want unambiguous filters, so that malformed input cannot silently widen my query.

THE Party Registry SHALL accept only canonical selectors and the declared exact conjunctive filters, reject ambiguous or invalid inputs, and apply bounded page limits under the shared normative rules below.

#### Scenario: Filter intersection is exact

##### Shared normative rules

WHEN any identifier operation supplies path selectors,
THE Party Registry SHALL require canonical lowercase UUID path selectors without trimming. Invalid `partyId` SHALL return `400 party-id-invalid`; invalid `identifierId` SHALL return `400 bad-request`.

WHEN listing is requested,
THE Party Registry SHALL accept only `identifierSchemeId`, `schemeCode`, `status`, `isPrimary`, `cursor`, and `limit`, each at most once. Scheme ID SHALL be a canonical lowercase UUID. Scheme code SHALL be nonblank exact case-sensitive text of at most 64 Unicode code points, without trimming or case conversion. Status SHALL be exactly `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED`, `EXPIRED`, or `REVOKED`; primary SHALL be exactly the query value `true` or `false`. A supplied limit SHALL be a decimal integer from 1 through 200; default 50 SHALL apply only when it is absent. All supplied filters SHALL combine with AND. Valid absent schemes and inconsistent scheme ID/code combinations SHALL match no rows rather than fail scheme eligibility.

IF a query parameter is unknown, repeated, blank, invalid, or has an unsupported value,
THEN THE Party Registry SHALL return `400 bad-request` without returning a partial or widened page. A supplied cursor SHALL be nonblank and valid for its continuation scope.

##### Observable outcome

- **GIVEN** a Party has identifiers in several schemes, statuses, and primary states
- **WHEN** scheme ID, exact scheme code, status, and primary filters are supplied together
- **THEN** only identifiers satisfying every supplied filter appear
- **AND** an otherwise valid unknown scheme or mismatched ID/code returns an empty page

#### Scenario: Limit boundaries and absence

- **WHEN** limit is absent, 1, or 200 on a valid list request
- **THEN** the effective maximum page size is respectively 50, 1, or 200

#### Scenario: Invalid selectors and query values

- **WHEN** a read supplies an invalid path UUID, repeated filter, unknown parameter, blank scheme code, invalid status, `isPrimary=1`, or a blank, fractional, negative, zero, or excessive limit
- **THEN** the response uses the specified 400 code and returns no data

### Requirement: Complete historical listing and masked detail

**User Story:** As a registry operator, I want to inspect current and historical identifiers, so that lifecycle decisions can use the complete record history.

THE Party Registry SHALL return safe stored historical identifiers without changing their state or audit, while preserving the separate narrower current natural-person projection under the shared normative rules below.

#### Scenario: Historical and past-date identifiers remain visible

##### Shared normative rules

WHEN `GET /v1/parties/{partyId}/identifiers` is requested without a status filter,
THE Party Registry SHALL include all matching identifiers regardless of stored status or date-based expiration, subject to pagination. When detail is requested for an owned identifier, it SHALL return its safe stored representation in any identifier state. Reads SHALL NOT change status, primary flags, versions, audit information, verification evidence, or replay history; they SHALL NOT convert a past-date identifier to `EXPIRED`.

THE Party Registry SHALL distinguish these historical reads from the existing natural-person current-identifier projection, which SHALL continue to include only `PENDING_VERIFICATION` or `VERIFIED` identifiers without an expiration or with expiration on/after its one UTC evaluation date.

##### Observable outcome

- **GIVEN** a Party has identifiers in all five states and a VERIFIED identifier whose expiration is in the past
- **WHEN** the unfiltered collection and each detail are read
- **THEN** all records are discoverable through pagination with their stored statuses and flags
- **AND** no read changes a version, status, audit value, or event history

#### Scenario: Current natural-person projection remains narrower

- **GIVEN** a natural person has pending, verified, rejected, revoked, expired, and past-date identifiers
- **WHEN** its existing natural-person detail is retrieved
- **THEN** only its qualifying current pending or verified identifiers are included
- **AND** their masked item representation remains compatible with standalone identifier detail

### Requirement: Deterministic scoped cursor navigation

**User Story:** As an API consumer, I want reliable continuation, so that matching identifiers are neither duplicated nor skipped on an unchanged dataset.

THE Party Registry SHALL provide deterministic full-precision ordering, snapshot-consistent pages, and authenticated live continuation bound to the complete effective query scope under the shared normative rules below.

#### Scenario: Timestamp ties navigate without duplication

##### Shared normative rules

WHEN a valid list request is evaluated,
THE Party Registry SHALL order results by creation instant descending and then canonical unsigned identifier UUID descending, preserving full stored timestamp precision. Forward and backward pages SHALL retain that same result ordering. The current page and its counts SHALL describe one consistent read view; subsequent requests SHALL be live continuations rather than a frozen historical dataset.

WHERE continuation is available,
THE Party Registry SHALL return opaque authenticated cursors bound to this resource, tenant, Party, every effective filter, and effective limit. Changing any scope component, tampering with a cursor, using a cursor from another resource, or using an unsupported cursor version or key SHALL return `400 bad-request` without exposing token contents. Equivalent effective requests, including an absent limit and explicit 50, SHALL share cursor scope. Unchanged valid boundaries SHALL remain usable if their original record changes or disappears. This change SHALL introduce no cursor expiry guarantee.

##### Observable outcome

- **GIVEN** several matching identifiers have equal creation timestamps and the dataset remains unchanged
- **WHEN** the consumer traverses every forward page and then backward pages
- **THEN** each identifier appears once per complete traversal in the specified descending order
- **AND** backward navigation recovers the preceding page in that order

#### Scenario: Cursor cannot cross scope

- **WHEN** a valid cursor is altered or reused with a different tenant, Party, effective filter, limit, or resource
- **THEN** the response is `400 bad-request` with no token payload or partial page

#### Scenario: Boundary changes do not invalidate navigation

- **GIVEN** a valid next cursor was returned and its boundary row subsequently changes or disappears
- **WHEN** continuation is requested with the unchanged effective scope
- **THEN** navigation uses the represented boundary against current matching data rather than requiring that row to remain present

### Requirement: Exact collection metadata and empty outcomes

**User Story:** As an API consumer, I want complete pagination metadata, so that empty results and unavailable directions are explicit.

THE Party Registry SHALL return exact bounded collection counts and all declared pagination fields, including explicit unavailable cursors and empty outcomes, and fail safely on count overflow under the shared normative rules below.

#### Scenario: Empty owned collection succeeds

##### Shared normative rules

WHEN a successful list response is returned,
THE Party Registry SHALL return HTTP/body status `200`, code `successful`, an array in `data`, and all five fields `nextCursor`, `prevCursor`, `totalElements`, `totalPages`, and `numberOfElements`. Totals SHALL count all matches before continuation; total pages SHALL be the ceiling of total elements divided by the effective limit, and number of elements SHALL equal the returned array size. Unavailable directions SHALL be explicit nulls. An empty dataset SHALL return an empty array, null cursors, and zero counts. An empty continuation page SHALL retain current total counts and expose navigation only where a matching page exists.

THE Party Registry SHALL support pagination counts from 0 through 2147483647 and SHALL declare that supported range in the affected published collection schema.

IF exact counts exceed that supported published response range,
THEN THE Party Registry SHALL return a sanitized `500 server-error` rather than truncate, cap, or silently omit them.

##### Observable outcome

- **WHEN** an existing owned Party has no matching identifiers
- **THEN** the response is `200 successful` with `data: []`, both cursors null, and all counts zero
- **AND** the response is not a not-found error

#### Scenario: First middle and final pages have exact metadata

- **GIVEN** an unchanged dataset has five matches and limit is 2
- **WHEN** the first, middle, and final pages are retrieved
- **THEN** totalElements is 5, totalPages is 3, and numberOfElements is respectively 2, 2, and 1
- **AND** only available navigation directions have nonnull cursors

### Requirement: Safe shared envelopes and failure behavior

**User Story:** As a privacy stakeholder, I want stable safe responses, so that identifier operations never disclose sensitive identity values or internal failures.

THE Party Registry SHALL return status-consistent shared safe success envelopes and stable status/code-only failures, protecting identifier and internal material across HTTP, logs, traces, and events under the shared normative rules below.

#### Scenario: Every operation returns only its safe envelope

##### Shared normative rules

WHEN any identifier operation succeeds,
THE Party Registry SHALL return `status`, `code: successful`, and `data` with matching HTTP/body status. Single-identifier data SHALL contain exactly the required identifier ID, Party ID, scheme ID/code, masked value, status, primary flag, independent version, and creation/modification timestamps, plus only the declared optional issuer, issue/expiration dates, and verification actor/time. Null optional single-item properties MAY be omitted. Single-item responses SHALL NOT contain pagination, Party subtype details, audit reason text, or storage-only properties.

WHEN an identifier operation fails,
THE Party Registry SHALL return only matching `status` and one stable `code`, without `data`, field errors, rejected values, exception messages, SQL, stack traces, or internal causes. Supported malformed/binding failures SHALL use `400 bad-request`; unknown routes `404 not-found`; unsupported methods `405 method-not-allowed`; unsupported request media `415 unsupported-media-type`. Classified dependency unavailability and operation timeouts SHALL use `503 dependency-unavailable`; unexpected or corrupt stored-result failures SHALL use sanitized `500 server-error`. Conditional authentication failures SHALL retain `401 unauthorized` when deployment authentication is enabled; context headers SHALL NOT themselves provide authentication.

THE Party Registry SHALL NOT disclose complete or normalized identifier values, ciphertext, lookup hashes, key versions/material, internal replay material, or decoded cursor payloads through responses, logs, traces, or enabled events. Only the declared safe replay result and opaque pagination tokens SHALL be exposed through their documented response fields. Internally recorded unexpected failures SHALL remain attributable to accepted request context without exposing sensitive material.

##### Observable outcome

- **WHEN** registration, listing, retrieval, PATCH, verify, reject, revoke, and set-primary succeed
- **THEN** their statuses and schemas conform to the shared contract
- **AND** no complete, normalized, reversible, or storage-only identifier material is present

#### Scenario: Framework and dependency failures retain the envelope

- **WHEN** identifier requests encounter malformed input, an unknown route, unsupported method/media, or classified dependency unavailability
- **THEN** the specified HTTP status and stable code appear in a status/code-only JSON body
- **AND** an accepted Process-Id is echoed unchanged

#### Scenario: Unexpected failure is sanitized

- **WHEN** an unexpected failure contains sensitive identifier material or a stored completion is corrupt
- **THEN** the response is exactly `500 server-error` without that material or internal details

#### Scenario: Unrepresentable collection count does not wrap

- **WHEN** a collection's exact count exceeds the supported published response range
- **THEN** the response is sanitized `500 server-error`, not a successful page with truncated metadata
