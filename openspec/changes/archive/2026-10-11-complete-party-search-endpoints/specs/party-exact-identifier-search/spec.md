## Purpose

This capability locates tenant-owned Parties through exact official-identifier matching without disclosing protected identifier material. It provides complete historical search results, explicit status selection, and a predictable public request and response contract.

## ADDED Requirements

### Requirement: Published exact identifier search contract

**User Story:** As an API consumer, I want an implemented and fully documented exact search, so that I can integrate against predictable behavior.

THE Party Registry SHALL implement `POST /v1/parties/search/by-identifier` as documented in the approved contract, keep the served document equivalent to its source, and provide valid request, success, and error examples.

#### Scenario: Published contract describes the completed operation

- **WHEN** a consumer inspects the served contract and submits its valid exact-search example
- **THEN** operation `searchPartyByIdentifier` is present with the required context/body and `IdentifierSearchApiResponse`, with no unresolved references or invalid examples
- **AND** documentation explains strict inputs, raw length units, normalization, scheme/state eligibility, precedence, ordering, safe fields, counts, empty results, process echo, and failure codes before the operation is advertised
- **AND** complete-result behavior introduces no cursor or limit inputs

### Requirement: Trusted exact-search context and correlation

**User Story:** As a tenant administrator, I want attributable, tenant-qualified searches, so that concurrent requests remain isolated and traceable.

WHEN an exact search is requested,
THE Party Registry SHALL validate exactly one Process-Id, Tenant-Id, and User-Id before operation input, echo only an accepted process unchanged, and isolate accepted request attribution, following the existing trusted-context contract.

#### Scenario: Header precedence preserves an accepted process

- **GIVEN** a valid process identifier, duplicate tenant headers, and an invalid body
- **WHEN** the exact search is submitted
- **THEN** it returns `400 tenant-id-duplicated` and echoes the accepted process identifier
- **AND** no search is evaluated and rejected header values are absent from completion logs

#### Scenario: Header spelling and rule precedence remain consistent

- **WHEN** context headers use different name casing and several header rules fail
- **THEN** names are case-insensitive and evaluation order is Process-Id, Tenant-Id, then User-Id, with absence before duplication before value validation within each header
- **AND** the first failure returns its existing `400 process-id-*`, `tenant-id-*`, or `user-id-*` code before body or business evaluation, subject to applicable framework authentication, method, and media checks

#### Scenario: Process and user boundaries are enforced

- **WHEN** separate exact searches supply a missing, duplicated, uppercase, padded, or malformed process UUID
- **THEN** each returns its corresponding required, duplicated, or invalid process code without a process echo
- **AND** tenant UUIDs also require exact canonical lowercase text without surrounding whitespace
- **AND** with valid process/tenant headers, user checks are nonblank, at most 128 Unicode code points, then absence of C0, DEL, and C1 controls; a safe 128-code-point value passes

#### Scenario: Concurrent request context remains isolated

- **WHEN** successful and failing exact searches overlap with different accepted tenants, users, and processes
- **THEN** each response and completion log contains only its own accepted attribution
- **AND** subsequent requests inherit none of that context

#### Scenario: Management endpoints remain exempt

- **WHEN** `/q` management endpoints are requested without business context headers
- **THEN** search-context validation does not reject those requests

### Requirement: Strict exact-search request validation

**User Story:** As an API consumer, I want malformed selectors rejected, so that an invalid request cannot silently broaden my search.

WHEN an exact search is submitted,
THE Party Registry SHALL accept only its four declared body properties with strict types and raw text limits, reject malformed input with the documented 400 code without coercion or search evaluation, and finish body validation before scheme evaluation.

#### Scenario: Required body and malformed input have stable failures

- **WHEN** separate exact searches submit no body, JSON null, malformed/nonobject JSON, trailing content, unknown/duplicate properties, wrong token types, or missing/null/blank required strings
- **THEN** absent/null bodies return `400 request-body-required` and the other invalid requests return `400 bad-request`
- **AND** all failure bodies contain no submitted selector or result data

#### Scenario: Supported inputs remain body-only read selectors

- **WHEN** a request contains only required nonnull/nonblank string `identifierSchemeCode` and `value`, plus optional `identifierStatuses` and `partyRecordStatuses` arrays
- **THEN** its property selection passes validation without query, If-Match, or Idempotency-Key inputs
- **AND** supplying mutation headers does not enable a write or saved-result replay

#### Scenario: Raw length boundaries are explicit

- **WHEN** exact-search requests contain scheme codes of 64 and 65 raw UTF-16 units or values of 256 and 257 raw UTF-16 units
- **THEN** values at the limits pass structural length validation and values above them return `400 bad-request`
- **AND** supplementary characters count as two UTF-16 units and surrounding whitespace counts before normalization

#### Scenario: Unsupported pagination inputs cannot truncate exact search

- **WHEN** an exact request supplies `cursor` or `limit` in its body, or any query parameter
- **THEN** it returns `400 bad-request`
- **AND** no pagination or silently truncated result is substituted

### Requirement: Exact scheme-qualified normalized matching

**User Story:** As a registry operator, I want equivalent identifier text to locate the same records, so that lookup agrees with previously accepted normalization.

WHEN a structurally valid exact search is evaluated,
THE Party Registry SHALL resolve the exact scheme code and match complete values using its supported normalization semantics, without readmitting historical records through registration rules, and return the documented 422 code for an unusable scheme selector.

#### Scenario: Canonically equivalent complete values match

- **GIVEN** a previously accepted identifier uses supported trim-and-uppercase normalization
- **WHEN** the tenant searches using equivalent case and exterior whitespace with the exact scheme code
- **THEN** the same identifier matches without changing its stored value or mask
- **AND** a proper prefix, substring, or masked representation does not qualify as complete equality
- **AND** `TRIM_UPPERCASE_V1` disregards exterior whitespace recognized by that rule and applies locale-independent uppercase comparison while retaining accents, punctuation, and interior whitespace

#### Scenario: Scheme selectors remain exact and explicit

- **GIVEN** a catalog code whose case and spacing are significant
- **WHEN** a different case or padded variant is not itself a catalog code
- **THEN** the search returns `422 unknown-identifier-scheme`
- **AND** code text is never trimmed or case-converted; exact known DRAFT, ACTIVE, DEPRECATED, and RETIRED schemes remain searchable

#### Scenario: Search does not readmit historical identifiers

- **GIVEN** a known scheme has a supported normalizer and historical records that would not pass its current new-registration validator or bounds
- **WHEN** an exact selector matches one of those stored normalized values
- **THEN** that identifier remains discoverable subject to tenant and status filters
- **AND** a structurally valid nonmatching selector returns an empty success even if it would fail new-registration validation
- **AND** current subject-type, lifecycle, validator, length-bound, and expiration admission rules do not requalify historical records

#### Scenario: Unsupported normalization fails explicitly

- **WHEN** a valid request selects a known scheme whose normalization semantics are unsupported
- **THEN** it returns `422 identifier-validation-failure` without result data
- **AND** no alternative normalization rule or empty successful result is substituted

### Requirement: Explicit historical status selection

**User Story:** As a registry operator, I want exact state filters, so that I can distinguish current evidence from historical identifiers and retained Parties.

WHEN status filters are evaluated,
THE Party Registry SHALL include every stored state for omitted dimensions; supplied valid arrays SHALL have set-membership semantics, intersect across dimensions, and match nothing when empty, without inferring expiration or requiring primary designation.

#### Scenario: Omitted filters include retained history

- **GIVEN** matching PENDING_VERIFICATION, VERIFIED, REJECTED, EXPIRED, and REVOKED identifiers, including past-date evidence, exist under both Party types in DRAFT, ACTIVE, INACTIVE, and ARCHIVED
- **WHEN** both filters are omitted
- **THEN** every tenant-owned matching identifier is eligible, including those under archived Parties
- **AND** stored expiration, state, and primary designation remain unchanged
- **AND** expiration dates and primary flags introduce no hidden filter

#### Scenario: Filter dimensions intersect

- **WHEN** an exact search supplies identifier states `VERIFIED` and `REVOKED` together with Party state `ACTIVE`
- **THEN** it returns only matching verified or revoked identifiers whose Parties are active
- **AND** reordering or repeating either listed state does not change results

#### Scenario: Empty and invalid arrays are distinct

- **WHEN** a valid exact search supplies either status array as `[]`
- **THEN** it returns an empty `200 successful` collection
- **AND** separate requests with null arrays, null/nonstring elements, lowercase enum strings, or unknown states return `400 bad-request`

### Requirement: Tenant-qualified result cardinality and ordering

**User Story:** As a tenant administrator, I want complete and deterministic matches from my tenant, so that historical duplicates do not hide or disclose records.

WHEN an exact search succeeds,
THE Party Registry SHALL return every same-tenant identifier match once, ordered by identifier creation instant and unsigned UUID descending, with consistent counts and no implicit page cap or stored-data changes.

#### Scenario: Cross-tenant matches are concealed

- **GIVEN** another tenant has the same normalized identifier and scheme
- **WHEN** the current tenant searches for that value
- **THEN** only current-tenant matches appear and contribute to counts
- **AND** a search with only foreign matches has the same empty outcome as a search with no matches anywhere
- **AND** every returned identifier and its owning Party belong to the accepted tenant

#### Scenario: Historical duplicates remain individually discoverable

- **GIVEN** several matching historical identifiers share a Party or normalized value, including equal creation timestamps
- **WHEN** the tenant performs an exact search
- **THEN** every qualifying identifier appears once in the declared creation-time/identifier order
- **AND** the Party may occur in multiple items with distinct matching identifier IDs
- **AND** creation instants retain their full stored precision and counts describe the same read view as data

#### Scenario: Complete exact results are not capped

- **GIVEN** 201 historical identifiers match the accepted tenant, scheme, complete value, and status filters
- **WHEN** the exact search completes within its configured deadline
- **THEN** all 201 items are returned
- **AND** stored versions, audit values, states, and logical-event counts remain unchanged
- **AND** subtype, scheme, nationality, verification, primary, and replay information is preserved

### Requirement: Safe exact-search envelope and complete metadata

**User Story:** As an API consumer, I want safe items and explicit collection metadata, so that empty and multiple matches can be handled without guessing.

WHEN an exact search succeeds,
THE Party Registry SHALL return a status-consistent `200 successful` envelope containing only declared safe search items and all five collection metadata fields, with null cursors, complete counts, and no sensitive value disclosure through responses or telemetry.

#### Scenario: Successful items contain only safe declared fields

- **WHEN** an exact search returns a matching historical Party and identifier
- **THEN** its HTTP/body statuses equal 200 and its code is `successful`
- **AND** each item contains exactly `partyId`, `type`, `displayName`, `recordStatus`, and `identifier`; its identifier contains exactly `identifierId`, `schemeCode`, `maskedValue`, `status`, and `isPrimary`
- **AND** returned Party text and masks preserve their stored representations

#### Scenario: Empty and multiple exact matches have explicit metadata

- **WHEN** separate valid exact searches return zero and three matches
- **THEN** both cursor properties are present and null on both responses
- **AND** the first response has `data: []` and all counts zero, while the second has `totalElements: 3`, `numberOfElements: 3`, and `totalPages: 1`
- **AND** every success includes all five metadata properties, with nonnull integer counts in `0..2147483647`; totalElements and numberOfElements equal data length, and totalPages is 0 when empty and 1 otherwise

#### Scenario: Sensitive search material is never exported

- **WHEN** an exact search completes and its HTTP response, logs, traces, and metric dimensions are inspected
- **THEN** no submitted complete value, normalized identifier, ciphertext, lookup hash, protection key/version, reason, subtype detail, or storage-only field is disclosed in the response
- **AND** telemetry contains no sensitive search value or protected identifier material

### Requirement: Safe exact-search failures and finite execution

**User Story:** As an API consumer, I want predictable failures and bounded execution, so that infrastructure problems cannot become partial results or sensitive disclosures.

WHEN an exact search fails,
THE Party Registry SHALL return only matching HTTP/body status and the documented stable failure code, sanitize internal failures, retain conditional authentication behavior, and terminate unavailable or over-deadline work without partial success.

#### Scenario: Framework failures retain the envelope

- **WHEN** requests with accepted context encounter an unsupported method, unsupported media type, or unknown route
- **THEN** responses are respectively `405 method-not-allowed`, `415 unsupported-media-type`, or `404 not-found`, containing only matching status and code
- **AND** the accepted process identifier is echoed unchanged

#### Scenario: Unavailability and timeout do not become incomplete success

- **WHEN** a classified dependency failure occurs or an exact search exceeds its finite configured deadline
- **THEN** it returns `503 dependency-unavailable` with no result data
- **AND** a subsequent healthy request can complete without inheriting the failed request's context or effects
- **AND** finite execution never becomes indefinite retry, omitted matches, or partial success

#### Scenario: Unexpected and unrepresentable outcomes are sanitized

- **WHEN** an unexpected failure includes sensitive identifier text, required stored material is corrupt, or the exact result count exceeds 2147483647
- **THEN** the response is only `{ "status": 500, "code": "server-error" }`
- **AND** no exception content, protected value, wrapped count, or partial data is disclosed
- **AND** internal failure recording reveals no submitted/protected identifier material, and all errors omit data, field errors, rejected values, messages, SQL, stack traces, and causes

#### Scenario: Conditional authentication is independent of context

- **GIVEN** deployment authentication is enabled
- **WHEN** an exact search supplies valid context headers but lacks acceptable credentials
- **THEN** authentication fails with the existing `401 unauthorized` contract
- **AND** context headers are not accepted as credentials
