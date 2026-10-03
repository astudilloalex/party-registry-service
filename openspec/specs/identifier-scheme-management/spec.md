# identifier-scheme-management Specification

## Purpose

This capability provides a complete administrative API for creating, discovering, retrieving, and partially maintaining the global identifier-scheme catalog. It defines observable validation, concurrency, replay, and response behavior shared with identifier-scheme lifecycle operations.

## Requirements

### Requirement: Complete published identifier-scheme contract

**User Story:** As an API consumer, I want documentation that matches every available operation, so that integrations do not depend on incomplete contracts.

THE Party Registry SHALL publish and implement `POST` and `GET /v1/identifier-schemes`, `GET /v1/identifier-schemes/by-code/{code}`, `GET` and `PATCH /v1/identifier-schemes/{schemeId}`, and `POST /v1/identifier-schemes/{schemeId}/activate`, `/deprecate`, and `/retire`.

THE published OpenAPI SHALL explicitly define each operation's required context and operation headers, path/query parameters, request-body presence or absence, request/response schemas, property constraints and nullability, success status, applicable error statuses and stable codes, and representative success/error examples. Creation SHALL document `201`; other successful operations SHALL document `200`. All operation errors SHALL reference the standard error schema. The served OpenAPI and Swagger documentation SHALL match the delivered behavior and contain no unresolved references for these operations. Lifecycle requests SHALL require no body.

#### Scenario: All eight operations are documented and executable

- **WHEN** a consumer inspects the served OpenAPI and exercises each operation with valid input and an eligible resource
- **THEN** all eight operations are present and return their documented success representation and status
- **AND** `schemeId` and `code` are required path parameters on their respective routes, not request-body or query properties

#### Scenario: Examples and errors conform to their schemas

- **WHEN** the published create, PATCH, retrieval, listing, lifecycle, and error examples are checked against their declared schemas
- **THEN** all examples conform, including HTTP/body status agreement and the documented nullable or omitted properties

### Requirement: Trusted context and process correlation

**User Story:** As an operator, I want consistent request context and correlation, so that catalog operations remain attributable and traceable.

WHEN any identifier-scheme operation is requested,
THE Party Registry SHALL require exactly one `Process-Id`, `Tenant-Id`, and `User-Id`, validating them in that order before business evaluation. Process and tenant values SHALL be canonical lowercase UUIDs without trimming; the user value SHALL be nonblank, contain at most 128 Unicode code points, and contain no C0, DEL, or C1 control character. Header names SHALL be case-insensitive. Absence, duplication, and value validation SHALL follow the existing header-specific `400` codes and precedence.

WHERE `Process-Id` is accepted,
THE Party Registry SHALL echo its unchanged value on success and error responses, including replay and failures in later header validation, and retain the accepted request context throughout completion logging. A missing, duplicated, or invalid process value SHALL NOT be echoed. Context SHALL NOT carry over to another request. Management endpoints under `/q` SHALL remain exempt.

#### Scenario: Accepted correlation survives an operation failure

- **WHEN** a request has valid context and targets an absent scheme
- **THEN** the error response echoes the exact accepted `Process-Id`
- **AND** completion logging identifies the accepted process, tenant, and user

#### Scenario: Invalid context is rejected before mutation

- **WHEN** a context header is absent, repeated, or invalid
- **THEN** the response is HTTP `400` with the corresponding existing `process-id-*`, `tenant-id-*`, or `user-id-*` code
- **AND** no catalog mutation occurs, and no invalid process value is echoed

### Requirement: Global catalog ownership

**User Story:** As a registry operator, I want one authoritative scheme catalog, so that stable scheme codes have the same meaning across tenants.

THE Party Registry SHALL treat schemes and scheme-code uniqueness as global rather than tenant-owned. Valid tenant context SHALL attribute requests and isolate idempotency results, but SHALL NOT create tenant-specific copies, filter catalog records by tenant ownership, or allow the same exact code to be created independently in another tenant.

#### Scenario: Different tenants observe the same catalog entry

- **GIVEN** a scheme created through a request with one tenant context
- **WHEN** another valid tenant retrieves that ID or code, or lists matching schemes
- **THEN** it observes the same global scheme identity, configuration, lifecycle status, and version

### Requirement: Creation request and initial state

**User Story:** As a catalog administrator, I want valid schemes created in a predictable initial state, so that they can be reviewed before activation.

WHEN a valid new creation request is accepted,
THE Party Registry SHALL create exactly one scheme with a generated UUID, the supplied globally unique code and configuration, status `DRAFT`, version `0`, and equal initial creation/modification timestamps attributable to the accepted user, returning `201 successful` with the scheme representation.

THE creation body SHALL be a JSON object with required nonnull, nonblank `code`, `issuingCountryCode`, `category`, `applicableSubjectType`, `name`, `normalizerKey`, and `validatorKey`. Optional `description`, `minimumLength`, and `maximumLength` SHALL accept omission or null. Optional `requiresExpiration` SHALL default to `false` only when omitted and SHALL reject null. Required strings SHALL retain their submitted values without implicit trimming or uppercasing; their maximum lengths SHALL count Unicode code points: code and rule keys 64, name 150, description 500. Country SHALL contain exactly two uppercase ASCII letters. Category SHALL be one of `NATIONAL_ID`, `TAX_ID`, `PASSPORT`, `RESIDENCE_PERMIT`, `LEGAL_REGISTRATION_NUMBER`, or `OTHER`; subject type SHALL be `NATURAL_PERSON`, `LEGAL_ENTITY`, or `BOTH`.

IF a body is missing or null, structurally invalid, contains duplicate or unknown properties, incompatible JSON types, invalid enums, blank required text, or exceeded text limits,
THEN THE Party Registry SHALL reject it without creating a scheme. Missing/null bodies SHALL return `400 request-body-required`; missing/null/blank code and oversized code SHALL respectively return `400 identifier-scheme-code-required` and `400 identifier-scheme-code-too-long`; other structural or field-format failures SHALL return `400 bad-request`. Client-supplied IDs, status, version, and audit properties SHALL be rejected as unknown creation properties.

#### Scenario: Valid creation returns a retrievable draft

- **WHEN** creation supplies a new code, valid country/category/subject type, supported rule keys, and all required text with a valid creation key
- **THEN** the response is `201 successful`, `data.status` is `DRAFT`, and `data.version` is `0`
- **AND** subsequent ID and code lookups return that scheme

#### Scenario: Optional fields and exact string values

- **WHEN** creation omits optional values and uses a valid mixed-case nonblank code
- **THEN** expiration metadata is false, optional description and bounds are absent, and the exact submitted code is retained
- **AND** no Party-name normalization changes catalog text or rule keys

#### Scenario: Strict body validation

- **WHEN** creation supplies a missing/null body, a client-owned status, duplicate JSON properties, a string length bound, a null boolean, or an unsupported enum
- **THEN** the documented `400` response is returned and no scheme is created

### Requirement: Supported configuration and coherent length bounds

**User Story:** As a data steward, I want schemes to reference usable processing rules and valid bounds, so that catalog entries do not fail unexpectedly during identifier processing.

WHEN new scheme configuration or a permitted configuration update is evaluated,
THE Party Registry SHALL require supplied length bounds to be integers from 1 through 32767, and require minimum length not to exceed maximum length when both resulting bounds exist. Type failures SHALL return `400 bad-request`; out-of-range or incoherent resulting bounds SHALL return `422 identifier-scheme-length-range-invalid` without mutation. The published schemas SHALL document these bounds.

THE Party Registry SHALL require the resulting `normalizerKey` and `validatorKey` to name supported versioned rules, using exact case-sensitive values. The initial supported normalizer SHALL be `TRIM_UPPERCASE_V1`; initial supported validators SHALL be `ALPHANUMERIC_V1`, `EC_NATIONAL_ID_V1`, and `EC_TAX_ID_V1`. Unknown keys SHALL return `422 invalid-identifier-scheme-configuration`. Key validation SHALL NOT accept executable expressions or create new processing rules. Country validation for these endpoints SHALL enforce the declared format without adding an undocumented country-lookup dependency.

#### Scenario: Boundary values and equal bounds are accepted

- **WHEN** an otherwise valid draft configuration supplies minimum and maximum lengths both equal to 1 or both equal to 32767
- **THEN** the bounds pass validation and are represented without truncation

#### Scenario: Resulting PATCH range is validated against retained values

- **GIVEN** a draft has minimum length 10 and maximum length 20
- **WHEN** a PATCH changes only maximum length to 5
- **THEN** the response is `422 identifier-scheme-length-range-invalid`
- **AND** the prior bounds, version, and audit values remain unchanged

#### Scenario: Unsupported processing keys are predictable failures

- **WHEN** creation or a permitted draft update names an unknown normalizer or validator key
- **THEN** the response is `422 invalid-identifier-scheme-configuration`, not an unexpected `500`
- **AND** no new or changed scheme is observable

### Requirement: Global code uniqueness and creation atomicity

**User Story:** As a catalog administrator, I want stable codes to identify only one scheme, so that retries and competing creates cannot introduce ambiguous entries.

IF a new creation attempts to use an exact code already assigned to any scheme, including a deprecated or retired one,
THEN THE Party Registry SHALL return `409 identifier-scheme-code-conflict` without changing the existing scheme or creating another scheme. Completed equivalent creation replay SHALL take precedence over this uniqueness check.

WHEN independent creations compete for the same exact code,
THE Party Registry SHALL accept at most one scheme and return a documented code conflict for the losing creation. Scheme creation and its completed successful replay result SHALL become observable together; failed acceptance SHALL leave neither a scheme nor a completed replay result from that attempt.

#### Scenario: Another tenant cannot reuse a retired code

- **GIVEN** a global retired scheme retains a code
- **WHEN** another tenant requests a new creation with that exact code and an independent key
- **THEN** the response is `409 identifier-scheme-code-conflict`
- **AND** the original scheme remains unchanged

#### Scenario: Concurrent independent creates have one winner

- **WHEN** independent creation requests with different keys compete for the same code
- **THEN** exactly one valid request creates the scheme and the losing request receives the documented `409` conflict
- **AND** the losing request leaves no completed successful replay result

### Requirement: Safe current representation and lookup

**User Story:** As an API consumer, I want current scheme data by ID or code, so that I can inspect configuration and use the current version for maintenance.

WHEN an existing scheme is retrieved by canonical UUID or exact, case-sensitive stable code,
THE Party Registry SHALL return `200 successful` with the same current scheme representation regardless of lifecycle status. Required representation properties SHALL be `id`, `code`, `issuingCountryCode`, `category`, `applicableSubjectType`, `name`, `normalizerKey`, `validatorKey`, `status`, `version`, `createdAt`, and `updatedAt`. The representation SHALL include boolean legacy `requiresExpiration`; absent nullable `description`, `minimumLength`, and `maximumLength` SHALL be omitted, as explicitly documented by their schemas. Timestamps SHALL be date-time strings and version SHALL be a nonnegative 64-bit integer.

IF a syntactically valid scheme ID or code identifies no scheme,
THEN THE Party Registry SHALL return `404 identifier-scheme-not-found` for retrieval, PATCH, and lifecycle actions without dereferencing absence, returning success with null data, or producing an unexpected `500`. Reads SHALL NOT change text, lifecycle, version, or audit values, and SHALL NOT disclose complete Party identifier values or internal storage details.

#### Scenario: Both lookups agree across every lifecycle state

- **GIVEN** an existing draft, active, deprecated, or retired scheme
- **WHEN** it is retrieved by ID and by its exact code
- **THEN** both responses contain its current matching representation and version with `200 successful`
- **AND** the scheme is not modified

#### Scenario: Unknown targets have a standard not-found response

- **WHEN** a valid absent UUID is used for retrieval, PATCH, or a lifecycle action, or an unknown valid code is retrieved
- **THEN** the response is exactly `{ "status": 404, "code": "identifier-scheme-not-found" }`
- **AND** no scheme mutation or successful replay result is created

### Requirement: Listing and filter semantics

**User Story:** As an API consumer, I want a filtered catalog list, so that I can discover schemes relevant to my workflow.

WHEN the catalog is listed,
THE Party Registry SHALL return `200 successful` with a `data` array of current safe scheme representations, including all lifecycle states when `status` is omitted. Optional `issuingCountryCode`, `category`, `applicableSubjectType`, and `status` filters SHALL be combined with logical AND and match exact stored values. `applicableSubjectType=NATURAL_PERSON` or `LEGAL_ENTITY` SHALL NOT implicitly include `BOTH`; consumers requiring that compatibility view SHALL use separate exact filters or unfiltered data. An empty match SHALL return an empty array, not `404`.

#### Scenario: Combined filters return only exact matches

- **GIVEN** schemes vary in country, category, subject type, and lifecycle status
- **WHEN** all four filters are supplied
- **THEN** every returned scheme matches every supplied filter
- **AND** a `BOTH` scheme is not returned by an exact `NATURAL_PERSON` filter

#### Scenario: Empty and unfiltered lists

- **WHEN** no scheme matches a valid filter combination
- **THEN** the response is `200 successful` with an empty `data` array and zero elements
- **AND** an unfiltered list includes draft, active, deprecated, and retired schemes

### Requirement: Cursor pagination and list metadata

**User Story:** As an API consumer, I want predictable continuation pages, so that catalog traversal does not silently change scope or duplicate stable records.

WHEN listing without a cursor,
THE Party Registry SHALL use `limit=50` unless one valid limit from 1 through 200 is supplied, order results by ascending creation timestamp and then ascending UUID, and return at most the effective limit. Continuations SHALL retain that ordering and the original requesting tenant, filter values, and effective limit. Cursors SHALL be opaque and usable only for identifier-scheme listing; altering a cursor, changing its scope, or supplying another resource's cursor SHALL return `400 bad-request`.

THE collection envelope SHALL include `numberOfElements` equal to the size of `data`. It SHALL expose `nextCursor` or `prevCursor` only when another forward or backward page is available and SHALL omit unavailable cursors. `totalElements` and `totalPages` SHALL be documented as optional and SHALL be omitted when totals are not supplied; any supplied totals SHALL count the entire matching catalog, with pages calculated using the effective limit. Pagination SHALL NOT promise a frozen snapshot across catalog changes.

#### Scenario: Forward and backward traversal of an unchanged catalog

- **GIVEN** more matching schemes than the requested limit and no intervening catalog changes
- **WHEN** a consumer follows forward cursors and then the provided backward cursor
- **THEN** pages preserve the documented order without skipping or duplicating schemes
- **AND** the backward page reproduces the corresponding prior page and unavailable boundary cursors are omitted

#### Scenario: Invalid cursor scope is rejected

- **WHEN** a consumer alters a cursor or reuses it with another tenant, filters, limit, or resource
- **THEN** the response is `400 bad-request` and no page outside the original scope is returned

### Requirement: Path and query validation

**User Story:** As an API consumer, I want malformed selectors rejected explicitly, so that invalid input is distinguished from absent resources.

IF `schemeId` is not a canonical lowercase UUID,
THEN THE Party Registry SHALL return `400 identifier-scheme-id-invalid`. IF a supplied `code` selector is blank or longer than 64 Unicode code points, THEN THE Party Registry SHALL return `400 identifier-scheme-code-required` or `400 identifier-scheme-code-too-long`, respectively. Valid code selectors SHALL be compared exactly without implicit trimming or uppercasing.

IF a listing parameter is unknown, repeated, blank, has an invalid country format or enum, contains a malformed cursor, or supplies a nondecimal, fractional, zero, negative, or oversized limit,
THEN THE Party Registry SHALL return `400 bad-request` rather than ignoring or coercing that value. Defaults SHALL apply only to absent parameters.

#### Scenario: Malformed ID differs from valid absent ID

- **WHEN** a shortened, uppercase, or otherwise noncanonical UUID is submitted as `schemeId`
- **THEN** the response is `400 identifier-scheme-id-invalid`, whereas a canonical unknown UUID returns `404 identifier-scheme-not-found`

#### Scenario: Invalid filters and repeated values fail predictably

- **WHEN** listing supplies an unknown parameter, two status values, lowercase country, unknown enum, blank limit, or fractional limit
- **THEN** the response is `400 bad-request` and no default or partial filter result is substituted

### Requirement: Partial update presence and immutable fields

**User Story:** As a data steward, I want PATCH to update only explicitly submitted editable properties, so that maintenance cannot replace omitted data or bypass lifecycle actions.

WHEN a permitted PATCH is accepted,
THE Party Registry SHALL modify only supplied `name`, `description`, `normalizerKey`, `validatorKey`, `minimumLength`, `maximumLength`, or `requiresExpiration` properties and preserve omitted values. Explicit null SHALL clear only description or either length bound; null for any other supported property SHALL return `400 bad-request`. The resulting representation SHALL satisfy all applicable configuration rules.

IF a PATCH body is missing/null, empty, nonobject, contains duplicate/unknown properties, wrong JSON types, or any identity, code, issuing-country, category, subject-type, status, version, or audit property,
THEN THE Party Registry SHALL reject the whole request without partially applying allowed values. Missing/null bodies SHALL return `400 request-body-required`; an empty object SHALL return `400 patch-property-required`; the remaining structural failures SHALL return `400 bad-request`.

#### Scenario: Omission preserves and null clears

- **GIVEN** a draft with a description and valid length bounds
- **WHEN** a permitted PATCH supplies `description: null` and a new name while omitting both bounds
- **THEN** description is cleared, name changes, and both bounds retain their values

#### Scenario: Immutable and unknown fields reject the entire patch

- **WHEN** PATCH supplies an allowed name together with code, status, or an unknown property
- **THEN** the response is `400 bad-request`
- **AND** no submitted value is applied

### Requirement: State-dependent maintenance

**User Story:** As a data steward, I want operational processing rules protected after activation, so that existing identifier semantics cannot be changed by catalog maintenance.

WHILE a scheme is `DRAFT`, WHEN a new PATCH with its current version is evaluated,
THE Party Registry SHALL permit all seven supported PATCH properties subject to validation.

WHILE a scheme is `ACTIVE` or `DEPRECATED`, WHEN a new PATCH with its current version is evaluated,
THE Party Registry SHALL permit only `name` and `description`. Supplying any processing-rule, length-bound, or expiration-metadata property SHALL return `409 identifier-scheme-rules-locked`, even when the supplied value equals the stored value.

For a permitted name/description-only update in `ACTIVE` or `DEPRECATED`, THE Party Registry SHALL preserve historical processing configuration without requiring its retained rule keys to remain supported. Supported-rule admission SHALL apply to creation, resulting DRAFT configuration updates, and activation; descriptive maintenance SHALL NOT readmit an operational scheme or unlock its processing properties.

WHILE a scheme is `RETIRED`, WHEN a new structurally valid PATCH with its current version is evaluated,
THE Party Registry SHALL return `409 identifier-scheme-retired` without changing it. Metadata updates SHALL NOT reactivate or otherwise change lifecycle status.

#### Scenario: Draft processing configuration remains editable

- **WHEN** a valid current-version draft PATCH changes supported rule keys, bounds, or expiration metadata
- **THEN** it succeeds with `200 successful` and returns the updated draft

#### Scenario: Active and deprecated schemes accept only descriptive updates

- **WHEN** a current-version active or deprecated scheme receives a valid name/description PATCH
- **THEN** it succeeds without changing processing configuration or status
- **AND** a request also supplying a rule, bound, or expiration property returns `409 identifier-scheme-rules-locked` without mutation

#### Scenario: Retired scheme cannot be patched

- **WHEN** any otherwise valid PATCH targets a retired scheme at its current version
- **THEN** the response is `409 identifier-scheme-retired` and all values remain unchanged

#### Scenario: Descriptive maintenance retains obsolete historical rules

- **GIVEN** an active or deprecated scheme retains a processing key no longer supported by the local catalog
- **WHEN** a current-version PATCH supplies only a valid name or description
- **THEN** it succeeds without revalidating or changing the retained processing keys
- **AND** processing properties remain locked and the lifecycle state is unchanged

### Requirement: Mutation concurrency and audit outcome

**User Story:** As an operator, I want edits to respect the scheme version, so that concurrent administration cannot overwrite accepted changes.

WHEN PATCH or a new lifecycle action is requested,
THE Party Registry SHALL require exactly one `If-Match` containing a bare decimal version matching `^(0|[1-9][0-9]*)$` from 0 through 9223372036854775807. Missing, repeated, malformed, and overflowing values SHALL return HTTP `400` with `if-match-required`, `if-match-duplicated`, `if-match-invalid`, and `if-match-out-of-range`, respectively.

IF the expected version differs from the current scheme version or an independent operation loses a concurrent version race,
THEN THE Party Registry SHALL return `412 expected-version-mismatch` without mutation. For syntactically valid new mutations, absence SHALL precede version mismatch, which SHALL precede state restrictions and semantic configuration checks. Completed lifecycle replay SHALL follow the lifecycle replay requirements instead.

WHEN PATCH succeeds, including a valid update submitting values identical to the existing representation,
THE Party Registry SHALL increment the scheme version exactly once, refresh modification audit information for the accepted user, preserve creation audit information and immutable properties, and return the accepted representation with `200 successful`. Failed acceptance SHALL leave no partial field, version, or audit changes. A scheme at the maximum version SHALL reject a new mutation with `409 identifier-scheme-version-exhausted` without overflowing its version.

#### Scenario: Missing and malformed preconditions

- **WHEN** a mutation omits `If-Match` or supplies a duplicate, quoted, signed, leading-zero, wildcard, or overflowing value
- **THEN** the corresponding documented `400` code is returned without mutation

#### Scenario: Stale version precedes state restriction

- **GIVEN** a retired scheme has a newer version than the valid requested version
- **WHEN** a new PATCH is submitted
- **THEN** the response is `412 expected-version-mismatch` rather than a retired-state conflict

#### Scenario: Competing independent mutations have one winner

- **GIVEN** two eligible PATCH or lifecycle operations use the same current scheme version with independent replay identities
- **WHEN** they compete
- **THEN** at most one advances that version and the loser receives `412 expected-version-mismatch`
- **AND** later retrieval shows the complete winning outcome

#### Scenario: Identical-value update still records one accepted change

- **WHEN** a permitted current-version PATCH supplies an existing name value
- **THEN** it returns the next version and updated modification audit information, preserving creation information

#### Scenario: Version exhaustion remains a business conflict

- **GIVEN** a scheme is at version 9223372036854775807
- **WHEN** a new otherwise valid mutation uses that current version
- **THEN** the response is `409 identifier-scheme-version-exhausted` without changes or an unexpected `500`

### Requirement: Durable creation idempotency

**User Story:** As an API consumer, I want a repeated creation to return its original accepted result, so that a lost response cannot create a duplicate scheme.

WHEN creation is requested,
THE Party Registry SHALL require exactly one nonblank `Idempotency-Key` of at most 128 Unicode code points. Absence, duplication, blankness, and excess length SHALL return the corresponding existing `idempotency-key-required`, `idempotency-key-duplicated`, `idempotency-key-blank`, and `idempotency-key-too-long` codes with HTTP `400`. Valid key values SHALL be compared exactly without trimming or case conversion.

WHERE creation supplies a valid key,
THE Party Registry SHALL scope its result by requesting tenant and identifier-scheme creation operation, independently of Party creation and every lifecycle action. Effective request equivalence SHALL consist of all accepted create properties, treating omission and null equivalently only for the three nullable fields and treating omitted `requiresExpiration` as false. Other accepted values SHALL be compared exactly. JSON property order, requesting user, and process correlation SHALL NOT alter equivalence.

WHEN an equivalent valid request repeats a completed creation key,
THE Party Registry SHALL return the original `201 successful` data, initial version, and timestamps even after subsequent scheme changes or service restart, echoing the current request's accepted process ID without creating or changing a scheme. A changed effective payload in the same scope SHALL return `409 idempotency-key-conflict` before uniqueness or current-configuration evaluation and SHALL NOT disclose the previous result.

WHEN equivalent creation requests compete,
THE Party Registry SHALL create one scheme and one successful replay outcome and return that same result to equivalent successful retries. Conflicting contenders for the same scoped key SHALL NOT leave another creation behind. Failed attempts SHALL NOT reserve a completed successful result or permanently consume a key.

#### Scenario: Creation replay retains its historical draft result

- **GIVEN** keyed creation succeeded and the scheme was later activated or retired
- **WHEN** the equivalent creation is retried after a restart with valid current context
- **THEN** the original `201` draft representation and version zero are returned
- **AND** the current scheme state remains unchanged and the retry's `Process-Id` is echoed

#### Scenario: Changed effective payload conflicts

- **GIVEN** a creation key has a successful result in the requesting tenant
- **WHEN** the same key is reused with a different code, name, rule, bound, or other effective property
- **THEN** the response is `409 idempotency-key-conflict` and no scheme is created or changed

#### Scenario: Equivalent concurrent creates converge

- **WHEN** equivalent creation requests share the same tenant and creation key
- **THEN** one scheme is created and every equivalent successful response identifies the same scheme and initial result

### Requirement: Standard response and failure boundary

**User Story:** As an API consumer, I want stable public outcomes, so that expected failures are actionable without exposing internal causes.

THE Party Registry SHALL return successful business JSON as `{ "status": <http-status>, "code": "successful", "data": ... }`, with only documented collection metadata on listing, and errors as `{ "status": <http-status>, "code": "<stable-code>" }`. Actual HTTP status SHALL equal body status. Errors SHALL contain no data, field-error arrays, rejected values, exception messages, stack traces, SQL details, or internal causes.

IF framework routing, method, media-type, authentication, required dependency, or unexpected execution failures occur,
THEN THE Party Registry SHALL use the same error structure, including `404 not-found` for unknown routes, `405 method-not-allowed`, `415 unsupported-media-type`, `401 unauthorized` when authentication is enforced, `503 dependency-unavailable` for classified dependency outages, and sanitized `500 server-error` for unexpected failures. Expected domain or absence failures SHALL use their specified codes and SHALL NOT be reported as unexpected `500` errors.

THE Party Registry SHALL retain the approved `412` precondition and `422` business-validation statuses for this resource and explicitly document every resource-specific code introduced by these specifications. Trusted context and applicable framework checks SHALL precede operation input validation; valid completed replay or key conflicts SHALL precede current-state business checks. For creation, code uniqueness SHALL precede semantic configuration checks; for updates, absence, version, state, and semantic configuration checks SHALL be evaluated in that order. Structural body checks SHALL precede path and operation-header checks; lifecycle key validation SHALL precede scheme ID and `If-Match` validation.

#### Scenario: Success envelope status matches HTTP

- **WHEN** any operation succeeds
- **THEN** creation uses HTTP/body status 201 and all other successes use 200, with `code: successful` and documented `data`
- **AND** no internal entity fields or confidential identifier values appear

#### Scenario: Framework and unexpected failures stay sanitized

- **WHEN** an unsupported method, unsupported media type, classified dependency outage, or unexpected failure is produced
- **THEN** the corresponding documented status and stable code use the standard error object
- **AND** no exception text, SQL details, stack trace, or error data is exposed

#### Scenario: Input precedence is deterministic

- **WHEN** a creation or PATCH has both an invalid body and invalid operation headers
- **THEN** the structural body failure takes precedence after trusted-context and applicable framework validation
- **AND** a syntactically valid update targeting an absent scheme returns not-found before version or lifecycle evaluation
