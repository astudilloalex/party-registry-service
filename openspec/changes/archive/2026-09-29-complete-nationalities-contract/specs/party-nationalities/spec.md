## Purpose

This capability lets a tenant register, inspect, maintain, and designate primary nationalities for its Parties through the five approved nationality operations. It defines safe request validation, inclusive temporal rules, repeatable writes, and stable public responses.

## ADDED Requirements

### Requirement: Nationality request context and correlation

**User Story:** As a tenant administrator, I want nationality requests attributed to one trusted tenant, user, and process, so that another tenant's data cannot be exposed or changed.

WHEN any nationality operation is requested,
THE Party Registry SHALL require exactly one `Process-Id`, `Tenant-Id`, and `User-Id` and apply the approved shared request-context rules before operation-specific validation. It SHALL check headers in that order, checking absence, duplicate values, and value validity in that order within each header. Header names SHALL be case-insensitive; even equal repeated values SHALL count as duplicates.

THE Party Registry SHALL accept only canonical lowercase UUIDs without whitespace for `Process-Id` and `Tenant-Id`; it SHALL require a nonblank `User-Id` of at most 128 Unicode code points without C0, DEL, or C1 control characters. It SHALL use the shared HTTP `400` `*-required`, `*-duplicated`, `process-id-invalid`, `tenant-id-invalid`, `user-id-blank`, `user-id-too-long`, and `user-id-unsafe` codes, as applicable.

WHEN a valid `Process-Id` has been accepted,
THE Party Registry SHALL echo it unchanged on success and failure, including later validation failures. It SHALL NOT echo a missing, duplicated, or invalid process identifier, and SHALL retain accepted request context in its completion log without leaking it to another request. These rules SHALL NOT change `/q` management endpoints.

#### Scenario: Shared context protects each nationality operation

- **GIVEN** otherwise valid requests for create, list, get, PATCH, and set-primary
- **WHEN** each is submitted without `Tenant-Id` but with a valid `Process-Id`
- **THEN** each returns `400 tenant-id-required` with the accepted process identifier echoed
- **AND** none reads or changes nationality data

#### Scenario: Invalid and duplicated headers are distinguished

- **WHEN** a nationality request supplies two identical `Process-Id` values, a noncanonical tenant UUID, or an unsafe `User-Id` in separate requests
- **THEN** it returns `400 process-id-duplicated`, `400 tenant-id-invalid`, or `400 user-id-unsafe` respectively
- **AND** no process identifier is echoed for the duplicated-process request

### Requirement: Nationality identity and tenant isolation

**User Story:** As an API consumer, I want records identified only within my tenant and their parent Party, so that IDs do not reveal someone else's data.

WHEN a nationality operation receives `partyId`,
THE Party Registry SHALL require a canonical lowercase UUID and return `400 party-id-invalid` for malformed or noncanonical values. For operations with `nationalityId`, it SHALL require a valid UUID and return `400 bad-request` for an invalid value; it SHALL NOT require an additional version-precondition header.

IF a syntactically valid `partyId` is absent or belongs to another tenant,
THEN THE Party Registry SHALL return `404 party-not-found`. IF the Party belongs to the tenant but the requested nationality is absent, belongs to another Party, or belongs to another tenant, THEN THE Party Registry SHALL return `404 nationality-not-found` without disclosing ownership. Both natural-person and legal-entity Parties SHALL be eligible, regardless of their retained record status.

#### Scenario: Party absence and nationality absence are concealed separately

- **GIVEN** a Party and nationality owned by another tenant, and a nationality of another Party in the current tenant
- **WHEN** the caller addresses the other tenant's Party, then the current tenant's Party with either foreign nationality ID
- **THEN** it receives `404 party-not-found` for the concealed Party and `404 nationality-not-found` for the concealed nationalities
- **AND** no response exposes the owner or record details

#### Scenario: Valid retained Parties remain eligible

- **GIVEN** tenant-owned natural-person and legal-entity Parties, including an archived Party
- **WHEN** a nationality is created for each Party with otherwise valid input
- **THEN** each Party can hold its own nationality without changing its type or lifecycle status

### Requirement: Nationality create input and country validation

**User Story:** As a registry operator, I want only recognized, well-formed countries registered, so that Party nationalities have reliable meaning.

WHEN `POST /v1/parties/{partyId}/nationalities` receives a request,
THE Party Registry SHALL require a JSON object with a nonnull `countryCode` string. It SHALL accept exactly two ASCII letters in either case after removal of surrounding Java `String.strip()` whitespace, uppercase the resulting code without locale dependence, and reject non-ASCII letters or interior whitespace. Omitted `isPrimary` SHALL mean `false`; supplied `isPrimary` SHALL be a nonnull boolean. Omitted or null `validFrom` and `validUntil` SHALL denote open bounds; supplied dates SHALL use ISO `YYYY-MM-DD` calendar dates.

IF the body is absent or JSON null, THEN THE Party Registry SHALL return `400 request-body-required`. IF `countryCode` is missing or null, THEN THE Party Registry SHALL return `400 country-code-required`; IF it is not two ASCII letters after stripping, THEN THE Party Registry SHALL return `400 country-code-invalid`. Malformed JSON, unknown or repeated object properties, wrong JSON types, and invalid date strings SHALL return `400 bad-request` without creating a nationality.

WHEN a syntactically valid country is submitted for a tenant-owned Party,
THE Party Registry SHALL verify that the normalized code is recognized by the geographic country reference. An unrecognized code SHALL return `422 unrecognized-nationality-country`; inability to complete that validation SHALL return `503 dependency-unavailable`. Country activity status SHALL NOT alone disqualify a recognized code, allowing historical nationality records. Retrieval, listing, validity updates, and primary designation SHALL NOT require fresh country validation.

#### Scenario: Country input is normalized before recognition

- **GIVEN** the geographic reference recognizes `EC`
- **WHEN** a nationality is created with `countryCode: "  ec  "` and omitted optional properties
- **THEN** it succeeds with `countryCode: "EC"`, `isPrimary: false`, and open validity bounds
- **AND** the reference lookup uses `EC`

#### Scenario: Country input distinguishes syntax, recognition, and outage

- **WHEN** separate create requests submit a missing code, an accented or internally spaced code, an unknown two-letter code, and a recognized code during reference unavailability
- **THEN** the respective results are `400 country-code-required`, `400 country-code-invalid`, `422 unrecognized-nationality-country`, and `503 dependency-unavailable`
- **AND** none creates a nationality or consumes an idempotency key

#### Scenario: Invalid create shapes cannot be silently coerced

- **WHEN** separate requests submit a missing body, an unknown field, duplicate `countryCode`, `isPrimary: null`, or an impossible `validFrom` date
- **THEN** the missing body returns `400 request-body-required` and each other invalid shape returns `400 bad-request`
- **AND** no record is created

### Requirement: Inclusive nationality validity and conflict prevention

**User Story:** As a data steward, I want periods to remain coherent and nonconflicting, so that a Party cannot hold contradictory nationality history.

WHEN a nationality is created or its dates are updated,
THE Party Registry SHALL interpret null `validFrom` as unbounded past and null `validUntil` as unbounded future. Both supplied dates SHALL be inclusive. Equal dates SHALL form a valid single-day interval; `validUntil` before `validFrom` SHALL return `422 nationality-validity-invalid` without changing any record. Future starts and historical ends SHALL be allowed.

IF a create or validity update would give the same Party overlapping inclusive periods for the same country,
THEN THE Party Registry SHALL return `409 nationality-validity-conflict`. IF a create or validity update of a primary record would produce overlapping inclusive primary periods for that Party, THEN THE Party Registry SHALL return `409 primary-nationality-conflict`. Different countries MAY have overlapping periods when at most one is primary for any overlapping date. Simultaneous competing writes SHALL have the same observable guarantees; rejected writes SHALL leave all affected records unchanged.

#### Scenario: Boundary sharing is an overlap

- **GIVEN** a Party has country `EC` from January 1 through January 31 inclusive
- **WHEN** another `EC` nationality starts on January 31, or starts on February 1, in separate requests
- **THEN** the first returns `409 nationality-validity-conflict` and the second may succeed
- **AND** a validity update cannot create the rejected overlap either

#### Scenario: Primary intervals cannot overlap, including open bounds

- **GIVEN** a Party has a primary `EC` nationality ending on January 31
- **WHEN** a different country's primary nationality starts on January 31, or on February 1, in separate create requests
- **THEN** the shared-boundary request returns `409 primary-nationality-conflict` and the following-day request may succeed
- **AND** an unbounded primary interval is subject to the same overlap rule

#### Scenario: Inverted and single-day intervals differ

- **WHEN** a create or PATCH sets both bounds to the same day or sets `validUntil` before `validFrom`
- **THEN** the equal-bound interval is accepted if it causes no overlap, while the inverted interval returns `422 nationality-validity-invalid`

### Requirement: Creation response and required idempotency

**User Story:** As an API consumer, I want retries of nationality creation to be safe, so that a network failure cannot create duplicate records.

WHEN creating a nationality,
THE Party Registry SHALL require exactly one nonblank `Idempotency-Key` of at most 128 Unicode code points, with no trimming or case conversion. Missing, repeated, blank, and oversized values SHALL return the shared HTTP `400` `idempotency-key-required`, `idempotency-key-duplicated`, `idempotency-key-blank`, and `idempotency-key-too-long` codes.

WHEN a new create request succeeds,
THE Party Registry SHALL return `201 successful` with the new nationality representation. It SHALL associate the key with the tenant and create action, and with the effective `partyId`, normalized `countryCode`, boolean `isPrimary`, and nullable validity bounds; omission and explicit default/null values SHALL have the same effective meaning. `User-Id` and `Process-Id` SHALL NOT change that identity. An equivalent completed retry SHALL return the original `201` response, including ID, values, and timestamps, without another write; reusing the key with different effective input, including another Party, SHALL return `409 idempotency-key-conflict` without applying the request. Failed attempts SHALL NOT consume a key, and concurrent requests with the same key SHALL have the same outcomes.

#### Scenario: An equivalent create retry preserves the result

- **GIVEN** a successful create with key `K`, country `ec`, omitted `isPrimary`, and open bounds
- **WHEN** the same tenant retries key `K` for the same Party with country `EC`, `isPrimary: false`, and explicitly null bounds using a different process and user
- **THEN** it receives the original `201` response with the same nationality ID and timestamps
- **AND** only one nationality exists

#### Scenario: A changed keyed request conflicts even across Parties

- **GIVEN** key `K` was used successfully for one Party and country
- **WHEN** the tenant reuses `K` for a different country or Party, including while requests compete
- **THEN** it receives `409 idempotency-key-conflict` without creating another nationality

### Requirement: Nationality list filters and input validation

**User Story:** As an API consumer, I want controlled access to current and historical nationality records, so that filtering produces a trustworthy result.

WHEN `GET /v1/parties/{partyId}/nationalities` is requested,
THE Party Registry SHALL accept only `countryCode`, `isPrimary`, `asOfDate`, `includeExpired`, `cursor`, and `limit`, with at most one value per parameter. Supplied `countryCode` SHALL be exactly two uppercase ASCII letters; `isPrimary` and `includeExpired` SHALL be `true` or `false`; `asOfDate` SHALL be an ISO `YYYY-MM-DD` calendar date. `includeExpired` SHALL default to `false`, `limit` to 50, and `asOfDate` to the UTC date captured once for the request. The limit SHALL be a decimal integer from 1 through 200.

WHEN listing nationalities for a tenant-owned Party,
THE Party Registry SHALL combine supplied filters with AND semantics. It SHALL select only records whose `validFrom` is null or no later than the effective `asOfDate`, excluding future records. With `includeExpired: false`, it SHALL also require `validUntil` to be null or no earlier than that date; with `includeExpired: true`, it SHALL additionally include records that ended before that date. `isPrimary` SHALL filter the stored primary designation and `countryCode` the stored normalized code; an expired record SHALL NOT become primary merely because it is included in a list. Other tenants' records SHALL NOT affect results or counts.

IF a list parameter is unknown, repeated, blank when a value is required, ill-typed, or out of range, or a supplied cursor is invalid,
THEN THE Party Registry SHALL return `400 bad-request` rather than silently ignoring it or returning a partial collection. Listing SHALL NOT require an `Idempotency-Key` or `If-Match`.

#### Scenario: Current, expired, and future records are distinguished

- **GIVEN** a Party has one expired, one current, and one future nationality relative to `2026-09-23`
- **WHEN** it lists with `asOfDate=2026-09-23` and omitted `includeExpired`, then with `includeExpired=true`
- **THEN** the first result contains only the current record and the second contains the expired and current records
- **AND** neither result contains the future record

#### Scenario: Filters intersect and malformed values are rejected

- **GIVEN** the Party has primary and non-primary records for different countries
- **WHEN** the caller filters by uppercase `countryCode` and `isPrimary=true`
- **THEN** only records matching both conditions and the effective-date rule appear
- **AND** separate requests with lowercase country code, repeated `isPrimary`, `limit=201`, or an unknown parameter return `400 bad-request`

### Requirement: Scoped nationality pagination and collection envelope

**User Story:** As an API consumer, I want stable pages of nationality history, so that I can traverse all matching records without losing the filter context.

WHEN listing nationalities,
THE Party Registry SHALL order matches by `createdAt` descending then canonical `nationalityId` descending, return at most the effective limit, and use opaque cursors scoped to the tenant, Party, effective filters including the evaluation date, and page size. An absent cursor SHALL select the first page; empty, malformed, altered, or scope-mismatched cursors SHALL return `400 bad-request`. Forward and backward traversal over unchanged data SHALL reproduce the declared order without missing or repeating records; no immutable snapshot across requests is promised.

WHEN a nationality collection is returned,
THE Party Registry SHALL include `status: 200`, `code: successful`, a `data` array, and `nextCursor`, `prevCursor`, `totalElements`, `totalPages`, and `numberOfElements`. Unavailable cursors SHALL be explicit nulls. Totals SHALL describe all tenant-owned records matching the filters before the page position, `totalPages` SHALL reflect the effective limit, and `numberOfElements` SHALL equal the array length; an empty result SHALL return zero counts and null cursors. Data and metadata in a single response SHALL reflect one consistent view.

#### Scenario: Multi-page traversal is stable with tied creation times

- **GIVEN** more matching nationalities than a page holds, including equal `createdAt` timestamps
- **WHEN** a caller follows next cursors and then the previous cursor without intervening changes
- **THEN** each record appears once in the declared timestamp/ID order and the previous cursor restores the preceding page

#### Scenario: Cursor scope and empty metadata are enforced

- **GIVEN** a cursor issued for one tenant, Party, effective date, filters, and limit
- **WHEN** it is reused for another Party, tenant, date, or limit, or altered
- **THEN** it returns `400 bad-request` with no collection data
- **AND** a separate matching-empty request returns `data: []`, both cursors null, and all three counts zero

### Requirement: Nationality detail and public representation

**User Story:** As a registry operator, I want to inspect a nationality including its history and audit timestamps, so that I can verify the stored result.

WHEN `GET /v1/parties/{partyId}/nationalities/{nationalityId}` identifies a tenant-owned record under its Party,
THE Party Registry SHALL return `200 successful` and a `data` object with `nationalityId`, `partyId`, normalized `countryCode`, `isPrimary`, `createdAt`, and `updatedAt`, plus `validFrom` and `validUntil` when present. The same representation SHALL be returned after successful create, PATCH, and set-primary. Reads SHALL include ended and future nationalities and SHALL NOT change audit values or depend on live geographic validation.

#### Scenario: Ended nationality remains retrievable

- **GIVEN** a tenant-owned Party has a nationality whose `validUntil` is in the past
- **WHEN** its detail is requested while the country reference is unavailable
- **THEN** it returns the stored nationality and its original timestamps with `200 successful`
- **AND** no pagination properties, tenant ID, or internal storage fields appear

### Requirement: Validity PATCH changes only supplied dates

**User Story:** As a data steward, I want to correct or end a nationality without losing its identity, so that historical information remains available.

WHEN `PATCH /v1/parties/{partyId}/nationalities/{nationalityId}` receives a JSON object,
THE Party Registry SHALL require at least one of `validFrom` or `validUntil`. Omitted properties SHALL retain their values; explicit null SHALL clear the corresponding bound; supplied values SHALL be valid ISO `YYYY-MM-DD` dates. The resulting interval SHALL satisfy the inclusive validity and overlap requirements, including primary-period exclusivity if the record is primary. `countryCode`, `isPrimary`, Party identity, nationality identity, and creation audit information SHALL remain unchanged; there SHALL be no physical deletion. A successful PATCH, including one that supplies an unchanged value, SHALL return `200 successful` with the stored outcome and updated modification timestamp.

IF the PATCH body is missing or null, THEN THE Party Registry SHALL return `400 request-body-required`. IF it is empty, THEN THE Party Registry SHALL return `400 patch-property-required`. Unknown or duplicate properties, wrong JSON types, and invalid dates SHALL return `400 bad-request`. Rejected changes SHALL leave the record and its audit information unchanged.

#### Scenario: Explicit null differs from omission

- **GIVEN** a nationality has both bounds
- **WHEN** a PATCH supplies `validUntil: null` but omits `validFrom`
- **THEN** the end bound is cleared and the start bound is retained if the expanded interval causes no conflict
- **AND** its country, primary designation, ID, and creation audit information are unchanged

#### Scenario: Empty, inverted, or conflicting PATCH does not alter history

- **GIVEN** an existing nationality with a neighboring record of the same country
- **WHEN** separate PATCH requests submit `{}`, an inverted interval, or a new bound overlapping the neighbor
- **THEN** they return `400 patch-property-required`, `422 nationality-validity-invalid`, and `409 nationality-validity-conflict` respectively
- **AND** neither nationality nor its audit timestamp changes

### Requirement: Primary designation and optional idempotency

**User Story:** As a data steward, I want to explicitly designate an effective nationality as primary, so that a Party has no conflicting primary assignment.

WHEN `POST /v1/parties/{partyId}/nationalities/{nationalityId}/set-primary` is requested,
THE Party Registry SHALL require the target's validity period to contain the UTC date captured once for the request. A future or ended target SHALL return `422 nationality-not-effective` without changing any record. For an effective target, the registry SHALL mark it primary and remove the primary designation from each other nationality of that Party whose stored period intersects the target's period, without changing those records' dates or deleting them. All affected representations and modification timestamps SHALL reflect one complete successful outcome. No other Party's primary assignments SHALL change; no overlapping primary periods SHALL remain, including under concurrent requests. If the target was already primary and no other designation needs changing, an unkeyed repeat SHALL return `200 successful` without modifying audit timestamps.

WHERE `Idempotency-Key` is supplied for set-primary,
THE Party Registry SHALL validate one nonblank key of at most 128 Unicode code points using the same shared HTTP `400` duplicate, blank, and length codes as creation. It SHALL scope the key to tenant and set-primary action and identify the effective request by Party and nationality ID, excluding user and process identifiers. A completed equivalent retry SHALL return the original `200 successful` result without additional changes, even if current designations have since changed. Reuse for a different target SHALL return `409 idempotency-key-conflict`; failed attempts SHALL NOT consume a key. An absent key SHALL allow the action without replay guarantees.

#### Scenario: Switching primary updates only intersecting designations

- **GIVEN** a Party has one currently primary nationality overlapping an effective non-primary target, and another nonoverlapping historical primary
- **WHEN** the target is set primary
- **THEN** the target becomes primary, the overlapping former primary becomes non-primary, and the nonoverlapping historical primary remains primary
- **AND** dates, country codes, and nationality IDs remain unchanged

#### Scenario: Ineligible targets cannot be designated

- **GIVEN** one nationality ended yesterday and another starts tomorrow
- **WHEN** each is set primary
- **THEN** each returns `422 nationality-not-effective` without changing any designation or audit timestamp

#### Scenario: Optional keyed retry does not repeat a transfer

- **GIVEN** a successful set-primary action with key `K` and a recorded response
- **WHEN** the same tenant retries `K` for the same Party and nationality after another primary change, or reuses `K` for another target
- **THEN** the equivalent retry returns the original `200` result without changing the current state, while the changed target returns `409 idempotency-key-conflict`

### Requirement: Nationality response and error contract

**User Story:** As an API consumer, I want stable and sanitized outcomes, so that successes and failures can be processed without leaking internal data.

WHEN a nationality operation succeeds,
THE Party Registry SHALL return the declared HTTP status (`201` for creation, `200` for all other operations), matching body `status`, `code: successful`, and the declared `data`. Only the list operation SHALL include pagination properties. When it fails, the response SHALL contain exactly `status` and one stable `code`, with HTTP status matching body status and without `data`, field errors, rejected values, exception messages, stack traces, SQL details, or internal causes.

IF the request has malformed JSON, an unsupported method or media type, an unknown business route, or an unexpected failure,
THEN THE Party Registry SHALL return the appropriate `400 bad-request`, `405 method-not-allowed`, `415 unsupported-media-type`, `404 not-found`, or sanitized `500 server-error` envelope. Known validation and business failures SHALL retain their requirement-specific codes and statuses; an unexpected failure SHALL be recorded internally without disclosing details. The approved OpenAPI SHALL document the nationality-specific codes and the `503 dependency-unavailable` create outcome.

#### Scenario: Success and business errors use exact envelopes

- **WHEN** create succeeds, list/get/PATCH/set-primary succeed, and a primary conflict occurs in separate requests
- **THEN** each success has its declared HTTP/body status, `successful`, and `data`, while the conflict has `409 primary-nationality-conflict` with only `status` and `code`
- **AND** pagination fields appear only on the list success

#### Scenario: Framework and unexpected failures remain safe

- **GIVEN** accepted request context
- **WHEN** a caller uses an unsupported nationality method or media type, and another request encounters an unexpected internal failure
- **THEN** the corresponding responses use `405 method-not-allowed`, `415 unsupported-media-type`, and `500 server-error` envelopes
- **AND** the accepted process identifier is echoed without any internal diagnostic content in the body
