# party-prefix-identifier-search Specification

## Purpose

This capability discovers tenant-owned Parties from a literal official-identifier prefix without exposing protected identifier material. It defines explicit historical filters, safe bounded result pages, and deterministic continuation scoped to the effective search.

## Requirements

### Requirement: Published identifier-prefix search contract

**User Story:** As an API consumer, I want a complete prefix-search contract, so that I can navigate candidates using the documented operation.

THE Party Registry SHALL implement `POST /v1/parties/search/by-identifier-prefix` as documented in the approved contract, keep the served document equivalent to its source, and provide valid request, success, error, and continuation examples.

#### Scenario: Published prefix examples agree with actual responses

- **WHEN** a consumer inspects the served contract and submits its valid prefix-search and continuation examples
- **THEN** operation `searchPartyByIdentifierPrefix` is present with required context/body and `IdentifierSearchApiResponse`, with no unresolved references or invalid examples
- **AND** documentation explains strict inputs, raw length units, normalization, scheme/state eligibility, precedence, ordering, limits, cursor scope, safe fields, counts, empty results, process echo, and failure codes before advertising the operation
- **AND** prefix is documented as write-only sensitive input, absent from response examples

### Requirement: Trusted prefix-search context and correlation

**User Story:** As a tenant administrator, I want attributable prefix searches, so that concurrent candidate discovery remains isolated and traceable.

WHEN a prefix search is requested,
THE Party Registry SHALL validate exactly one Process-Id, Tenant-Id, and User-Id before operation input, echo only an accepted process unchanged, and isolate accepted request attribution, following the existing trusted-context contract.

#### Scenario: Context validation precedes prefix and cursor validation

- **GIVEN** an accepted process identifier, a missing tenant header, and an invalid prefix or cursor
- **WHEN** the prefix search is submitted
- **THEN** it returns `400 tenant-id-required` and echoes the accepted process identifier
- **AND** no search or continuation is evaluated

#### Scenario: Header spelling and rule precedence are deterministic

- **WHEN** headers use different name casing and multiple context rules fail
- **THEN** names are case-insensitive, evaluation is Process-Id then Tenant-Id then User-Id, and each header checks absence before duplication before value validation
- **AND** the first failure returns its existing `400 process-id-*`, `tenant-id-*`, or `user-id-*` code before operation evaluation, subject to applicable framework authentication, method, and media checks

#### Scenario: Invalid process is not echoed

- **WHEN** separate prefix searches supply no process header, duplicate equal process headers, or a noncanonical process UUID
- **THEN** they return the corresponding required, duplicated, or invalid process code
- **AND** no Process-Id response header is present
- **AND** both process and tenant UUIDs require exact canonical lowercase text without surrounding whitespace

#### Scenario: User boundaries and concurrent attribution remain safe

- **WHEN** prefix searches use blank, excessive, or unsafe users, or concurrent requests use different valid context
- **THEN** invalid users receive the corresponding 400 user code and valid overlapping searches retain only their own response and completion-log attribution
- **AND** user checks are nonblank, at most 128 Unicode code points, then absence of C0, DEL, and C1 controls; a safe boundary value passes
- **AND** only accepted context enters completion logging, owned context is cleared, and subsequent requests inherit no earlier context

#### Scenario: Management endpoints remain exempt

- **WHEN** `/q` management endpoints are requested without business context headers
- **THEN** search-context validation does not reject those requests

### Requirement: Strict prefix-search request validation

**User Story:** As an API consumer, I want malformed prefix inputs rejected, so that a failed selector cannot broaden my candidate set.

WHEN a prefix search is submitted,
THE Party Registry SHALL accept only its six declared body properties with strict types and raw limits, reject malformed input with the documented 400 code without coercion, and validate context, body, scheme/normalization, then continuation before reading results.

#### Scenario: Missing bodies and strict token failures are explicit

- **WHEN** separate prefix searches submit no body, JSON null, malformed/nonobject JSON, trailing content, missing/null/blank required strings, wrong token types, duplicate/unknown properties, invalid status tokens, or a blank cursor
- **THEN** absent/null bodies return `400 request-body-required` and the other invalid requests return `400 bad-request`
- **AND** no invalid selector is ignored or echoed in the failure body

#### Scenario: Supported inputs are strict body-only read selectors

- **WHEN** a request supplies only nonnull/nonblank strings `identifierSchemeCode` and `prefix`, plus optional `identifierStatuses`, `partyRecordStatuses`, `cursor`, and `limit`
- **THEN** the property selection passes validation, with exact-string status arrays, nonblank strings for nonnull cursor, and no query parameters
- **AND** unknown query parameters return `400 bad-request`; If-Match and Idempotency-Key are not required and cannot enable a write or saved-result replay

#### Scenario: Raw prefix and scheme boundaries are enforced

- **WHEN** requests contain a scheme code or prefix of 64 and 65 raw UTF-16 units
- **THEN** 64-unit strings pass structural length validation and 65-unit strings return `400 bad-request`
- **AND** supplementary characters count as two units and exterior whitespace counts before normalization

#### Scenario: Selector failure precedes continuation scope failure

- **GIVEN** structurally valid input with an unknown scheme code and an altered nonblank cursor
- **WHEN** the prefix search is evaluated
- **THEN** it returns `422 unknown-identifier-scheme` before evaluating continuation scope

### Requirement: Literal scheme-normalized prefix matching

**User Story:** As a registry operator, I want a partial identifier to match its literal beginning, so that discovery does not confuse a fragment with a complete identifier or pattern.

WHEN a structurally valid prefix search is evaluated,
THE Party Registry SHALL resolve the exact scheme code and match literal beginnings of complete values using supported scheme normalization, without complete-value admission checks, and return the documented 422 code for an unusable scheme selector.

#### Scenario: Partial official identifiers do not need a complete checksum

- **GIVEN** a supported national-ID scheme and stored complete identifiers
- **WHEN** the tenant submits a valid nonblank partial prefix shorter than a complete identifier
- **THEN** all eligible complete values beginning with that prefix can match
- **AND** complete-value length or checksum validation does not reject the fragment
- **AND** new-registration lifecycle, subject-type, length-bound, and expiration admission rules do not requalify historical identifiers

#### Scenario: Canonical equivalence and literal characters are preserved

- **GIVEN** a supported trim-and-uppercase scheme with historical values containing literal punctuation
- **WHEN** equivalent mixed-case prefixes with exterior whitespace, including literal `%` or `_`, are submitted
- **THEN** only values with the corresponding normalized literal beginning match
- **AND** a prefix appearing only in the middle of a value does not match
- **AND** `TRIM_UPPERCASE_V1` applies its exterior-whitespace and locale-independent uppercase rules while preserving accents, punctuation, and interior whitespace
- **AND** punctuation is literal rather than wildcard syntax; fuzzy matches and masks do not substitute for complete-value beginnings

#### Scenario: Full values and expanding normalization remain valid selectors

- **WHEN** the submitted prefix equals an entire normalized stored value, or a within-limit raw prefix expands during supported normalization
- **THEN** literal beginning comparison applies to the complete normalized prefix without truncation or complete-value readmission
- **AND** normalization expansion does not introduce a second raw-input length restriction

#### Scenario: Known withdrawn schemes remain searchable

- **WHEN** a prefix selects a known DRAFT, ACTIVE, DEPRECATED, or RETIRED scheme with supported normalization
- **THEN** its previously stored matching identifiers remain eligible
- **AND** a known scheme with unsupported normalization returns `422 identifier-validation-failure`
- **AND** scheme code is exact case-sensitive text without trimming; a genuinely unknown code returns `422 unknown-identifier-scheme` and no alternative normalizer or empty success masks either failure

### Requirement: Explicit historical prefix-search filters

**User Story:** As a registry operator, I want explicit state selection, so that I can discover current or historical evidence under retained Parties.

WHEN status filters are evaluated,
THE Party Registry SHALL include every stored state for omitted dimensions; supplied valid arrays SHALL have set-membership semantics, intersect across dimensions, and match nothing when empty, without inferring expiration or requiring primary designation.

#### Scenario: Default prefix search includes stored historical states

- **GIVEN** matching PENDING_VERIFICATION, VERIFIED, REJECTED, EXPIRED, and REVOKED identifiers, including past-date evidence, exist under both Party types in DRAFT, ACTIVE, INACTIVE, and ARCHIVED
- **WHEN** both status filters are omitted
- **THEN** every same-tenant match is eligible for pagination regardless of primary designation
- **AND** no identifier state changes during the search
- **AND** expiration dates and scheme withdrawal introduce no hidden selection rule

#### Scenario: Status dimensions combine predictably

- **WHEN** a prefix search supplies `identifierStatuses: ["VERIFIED", "REVOKED"]` and `partyRecordStatuses: ["ACTIVE"]`
- **THEN** only matching verified or revoked identifiers of active Parties qualify
- **AND** repeated values or changed array ordering produce the same effective search

#### Scenario: Empty filters cannot silently widen results

- **WHEN** a valid prefix request supplies either status filter as `[]`
- **THEN** it returns an empty successful page with zero counts and explicit null cursors
- **AND** separate requests with null filters, null/nonstring elements, unknown states, or incorrect enum casing return `400 bad-request`

### Requirement: Tenant-qualified hit cardinality and deterministic ordering

**User Story:** As a tenant administrator, I want ordered identifier matches from my own tenant, so that discovery remains complete without cross-tenant disclosure.

WHEN a prefix search succeeds,
THE Party Registry SHALL return distinct same-tenant identifier hits ordered by identifier creation instant and unsigned UUID descending on every navigation direction, conceal foreign matches from data/counts/cursors, and preserve stored information.

#### Scenario: Foreign-only matches have an empty local outcome

- **GIVEN** the submitted prefix matches identifiers only in another tenant
- **WHEN** the current tenant searches
- **THEN** it receives the same empty success as when no matching identifier exists anywhere
- **AND** no count or cursor discloses foreign records
- **AND** every returned identifier and its owning Party belong to the accepted tenant

#### Scenario: Shared Party and creation timestamps do not collapse hits

- **GIVEN** multiple matching identifiers share a Party and some have equal creation timestamps
- **WHEN** all pages are traversed over unchanged data
- **THEN** each matching identifier appears exactly once in the declared creation-time/unsigned-UUID order
- **AND** successful or failed searches leave all stored states, versions, audit values, and logical-event counts unchanged
- **AND** identifiers sharing a normalized value remain separate hits, creation instants retain full stored precision, and both navigation directions keep the same output ordering
- **AND** subtype, scheme, nationality, verification, primary, and replay information is preserved

### Requirement: Strict bounded prefix page size

**User Story:** As an API consumer, I want a predictable maximum page size, so that discovery responses remain bounded.

WHEN a prefix page is requested,
THE Party Registry SHALL default an omitted limit to 50; a supplied limit SHALL be a JSON integer token in 1..200, bound the returned page, and produce `400 bad-request` for invalid input rather than be coerced or replaced with a default.

#### Scenario: Default and boundary page sizes apply exactly

- **GIVEN** more than 200 eligible matches
- **WHEN** separate initial requests omit limit, supply 1, or supply 200
- **THEN** their pages contain respectively 50, 1, and 200 hits
- **AND** their total counts still describe every matching identifier before continuation

#### Scenario: Invalid limits are not coerced

- **WHEN** separate requests supply null, `"50"`, `true`, `1.0`, `1e0`, 0, -1, 201, or an overflowing integer as limit
- **THEN** each returns `400 bad-request` with no result data

### Requirement: Scoped stable prefix continuation

**User Story:** As an API consumer, I want reliable continuation tied to my search, so that pages cannot cross tenants or unrelated query scopes.

WHEN prefix continuation is requested,
THE Party Registry SHALL accept only opaque cursors bound to the effective tenant/search scope, preserve forward and backward ordering on unchanged data, and evaluate live continuation against current matches.

#### Scenario: Omitted and null cursors request the first page

- **WHEN** a valid prefix request omits cursor or supplies it as JSON null
- **THEN** both requests select the first page in the same effective search scope

#### Scenario: Forward and backward traversal preserve order

- **GIVEN** five unchanged matching identifiers, including timestamp ties, and limit 2
- **WHEN** the consumer follows next cursors through every page and then follows a previous cursor
- **THEN** forward pages contain two, two, and one hits without omissions or repeats
- **AND** backward navigation restores the preceding page in the same declared descending order

#### Scenario: Cursor scope cannot cross tenant or search

- **GIVEN** a server-issued cursor bound to this resource, accepted tenant, exact scheme code, normalization semantics, normalized prefix, both effective status sets, and effective limit
- **WHEN** it is altered, unrecognized, malformed, blank, empty, or reused under a changed scope or another resource
- **THEN** it returns `400 bad-request` with no decoded token or partial page

#### Scenario: Equivalent selector forms retain continuation

- **GIVEN** a cursor issued with omitted limit and status filters
- **WHEN** it is used with explicit limit 50, explicit complete-state sets in a different order with duplicates, an equivalent normalized prefix, and different valid user/process headers
- **THEN** continuation succeeds in the same effective scope and echoes the new accepted process identifier
- **AND** replacing a complete-state set with an empty set rejects that cursor as scope-mismatched
- **AND** accepted users and processes are not scope dimensions but still undergo ordinary context validation

#### Scenario: Live continuation survives boundary changes

- **GIVEN** a valid cursor and its boundary identifier subsequently disappears or changes out of the matching set
- **WHEN** it is submitted with unchanged effective scope
- **THEN** continuation uses its represented boundary against current matching data rather than requiring that identifier to remain present
- **AND** counts describe current matches rather than the original response's dataset
- **AND** navigation depends on matches relative to the valid boundary, without a retained historical snapshot or newly promised cursor-expiration guarantee

### Requirement: Safe prefix response and exact collection metadata

**User Story:** As an API consumer, I want safe candidate pages with complete metadata, so that empty results and available navigation are explicit.

WHEN a prefix search succeeds,
THE Party Registry SHALL return a status-consistent `200 successful` safe-item envelope with exact complete counts, explicit neighbor cursors, one consistent read view, and no sensitive value disclosure through responses or telemetry.

#### Scenario: Successful prefix hits contain only declared safe fields

- **WHEN** a valid prefix search returns candidate identifiers
- **THEN** HTTP/body status is 200, code is successful, and data is an array whose items contain exactly `partyId`, `type`, `displayName`, `recordStatus`, and `identifier`
- **AND** each identifier contains exactly `identifierId`, `schemeCode`, `maskedValue`, `status`, and `isPrimary`, preserving stored Party text and masks

#### Scenario: First middle and final pages have exact metadata

- **GIVEN** five matches and limit 2
- **WHEN** the three forward pages are requested over unchanged data
- **THEN** each reports `totalElements: 5` and `totalPages: 3`, with numberOfElements respectively 2, 2, and 1
- **AND** the first page has `prevCursor: null`, the final page has `nextCursor: null`, and each safe hit has only its declared summary and masked identifier fields
- **AND** every page includes all five metadata properties; totals count all same-tenant matches before continuation, totalPages is ceiling(totalElements / limit), and numberOfElements is data length
- **AND** counts are nonnull integers in `0..2147483647`; unavailable directions are explicit nulls, and data/counts/navigation describe one consistent read view

#### Scenario: Empty initial and vanished datasets are explicit

- **WHEN** a valid initial search has no matches, or a valid continuation is submitted after all its matches disappear
- **THEN** the response is `200 successful` with `data: []`, both cursors explicitly null, and all counts zero
- **AND** it is not a not-found error and contains no submitted prefix or internal material

#### Scenario: Empty live continuation does not erase remaining totals

- **GIVEN** a valid continuation boundary and current matches only on its opposite side
- **WHEN** the requested direction yields no hits
- **THEN** data is empty but totalElements and totalPages still describe current matches
- **AND** only navigation to an existing matching page is offered

#### Scenario: Sensitive prefix material is never exported

- **WHEN** a prefix search completes and its response, logs, traces, and metric dimensions are inspected
- **THEN** responses contain no submitted prefix, complete/normalized identifier, ciphertext, lookup hash, protection key/version, decoded token, reason, subtype detail, or storage-only field
- **AND** telemetry contains no sensitive selector, raw cursor, or protected material; only issued opaque tokens appear in declared response cursor fields

### Requirement: Safe prefix failures and finite execution

**User Story:** As an API consumer, I want predictable failures and bounded execution, so that costly discovery cannot become incomplete success or disclose protected data.

WHEN a prefix search fails,
THE Party Registry SHALL return only matching HTTP/body status and the documented stable failure code, sanitize internal failures, retain conditional authentication behavior, and terminate unavailable or over-deadline work without partial success.

#### Scenario: Framework failures preserve correlation and safe envelopes

- **WHEN** prefix requests with accepted context encounter unsupported methods/media or an unknown business route
- **THEN** responses are respectively `405 method-not-allowed`, `415 unsupported-media-type`, or `404 not-found`, containing only matching status and code
- **AND** the accepted process identifier is echoed unchanged

#### Scenario: Expensive search or dependency failure does not return partial success

- **WHEN** prefix evaluation exceeds its configured finite deadline or encounters classified dependency unavailability
- **THEN** it returns `503 dependency-unavailable` without data or success metadata
- **AND** a subsequent healthy search can complete without inheriting the failed request's context or effects
- **AND** finite execution never becomes indefinite retry, capped totals, omitted matches, or partial success

#### Scenario: Unexpected failure and count overflow are sanitized

- **WHEN** an unexpected failure contains a submitted prefix or complete identifier, required stored material is corrupt, or exact counts exceed 2147483647
- **THEN** the response is only `{ "status": 500, "code": "server-error" }`
- **AND** it contains no exception content, sensitive value, wrapped count, or partial page
- **AND** internal failure recording reveals no submitted/protected identifier material, and all errors omit data, field errors, rejected values, messages, SQL, stack traces, and causes

#### Scenario: Conditional authentication remains separate from tenant context

- **GIVEN** deployment authentication is enabled
- **WHEN** a prefix search supplies valid context headers but lacks acceptable credentials
- **THEN** it fails with the existing `401 unauthorized` contract
- **AND** context headers are not accepted as credentials
