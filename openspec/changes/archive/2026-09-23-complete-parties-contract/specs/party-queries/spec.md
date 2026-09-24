## Purpose

This capability provides tenant-scoped Party listing and common detail retrieval. It defines predictable filtering, pagination, safe representations, and the shared public request/response contract for the six root Party operations.

## ADDED Requirements

### Requirement: Root Party request context

**User Story:** As a tenant administrator, I want every root Party operation to validate its request context, so that identity data remains tenant-isolated and attributable.

WHEN any of the six root Party operations is requested,
THE Party Registry SHALL require exactly one `Process-Id`, `Tenant-Id`, and `User-Id`, applying the approved context-header contract before operation-specific validation.

THE Party Registry SHALL validate headers in the order `Process-Id`, `Tenant-Id`, then `User-Id`; within each header, absence SHALL precede duplicate detection and value validation. Header names SHALL be case-insensitive. Missing and duplicate headers SHALL return HTTP `400` with the corresponding `*-required` and `*-duplicated` codes.

THE Party Registry SHALL require canonical lowercase UUIDs without surrounding whitespace for process and tenant identifiers, returning `400 process-id-invalid` or `400 tenant-id-invalid` for invalid supplied values. For `User-Id`, the registry SHALL validate nonblank content, a maximum of 128 Unicode code points, and absence of C0, DEL, and C1 control characters, in that order, returning `user-id-blank`, `user-id-too-long`, or `user-id-unsafe` with HTTP `400`.

WHEN a `Process-Id` is accepted,
THE Party Registry SHALL echo it unchanged on the response, including when a later validation fails. Missing, duplicated, and invalid process identifiers SHALL NOT be echoed. Request completion logs SHALL retain the accepted process, tenant, and user context, without exposing rejected values or carrying context into another request. These header requirements SHALL NOT apply to `/q` management endpoints.

#### Scenario: Context validation covers every root operation

- **GIVEN** each of list, get, PATCH, activate, deactivate, and archive has otherwise valid input
- **WHEN** it is submitted with a valid process identifier but no tenant header
- **THEN** the response is `400 tenant-id-required`
- **AND** the accepted process identifier is echoed and no Party is changed

#### Scenario: Duplicate and noncanonical headers are rejected

- **WHEN** a root operation contains two `Process-Id` values, even if equal
- **THEN** the response is `400 process-id-duplicated` without a process echo
- **AND** a separate request containing a single uppercase or whitespace-padded process UUID returns `400 process-id-invalid`

#### Scenario: User identifier boundaries are enforced

- **GIVEN** valid process and tenant headers
- **WHEN** `User-Id` is blank, exceeds 128 Unicode code points, or contains a prohibited control character
- **THEN** the corresponding first applicable user-validation code is returned with HTTP `400`
- **AND** a nonblank, safe 128-code-point value passes context validation

#### Scenario: Request context does not leak

- **GIVEN** two requests use different valid tenants, users, and process identifiers
- **WHEN** their completion overlaps, including a failure in one request
- **THEN** each response and completion log identifies only its own accepted request context

### Requirement: Root Party response and error contract

**User Story:** As an API consumer, I want consistent success and failure responses, so that I can handle all root Party operations reliably.

WHEN a root Party operation succeeds,
THE Party Registry SHALL return HTTP `200`, body `status: 200`, code `successful`, and `data` conforming to its declared collection or detail representation. Pagination properties SHALL appear only on the list operation.

WHEN a root Party operation fails,
THE Party Registry SHALL return a JSON object containing only `status` and one stable `code`, with body status equal to HTTP status. The public contract SHALL document the applicable field-specific and business codes, including the display-name update codes and lifecycle idempotency outcomes introduced by this change.

IF a request encounters malformed JSON, an unsupported method or media type, an unknown route, or an unexpected internal failure,
THEN THE Party Registry SHALL use the applicable `400 bad-request`, `405 method-not-allowed`, `415 unsupported-media-type`, `404 not-found`, or `500 server-error` response. Unexpected failures SHALL be logged internally and SHALL NOT expose exception messages, stack traces, rejected values, SQL details, or internal causes.

THE Party Registry SHALL NOT expose complete or normalized identifier values, ciphertext, lookup hashes, or protection keys in root Party responses, events, or operational output.

#### Scenario: Success and error envelopes remain consistent

- **WHEN** each root operation returns a successful response or a known validation/business failure
- **THEN** its actual HTTP status equals the body status
- **AND** success contains the declared data while failure contains only `status` and `code`

#### Scenario: Framework failures retain the public envelope

- **GIVEN** valid request context
- **WHEN** an unsupported method, an unsupported PATCH media type, or an unknown business route is requested
- **THEN** the corresponding `405`, `415`, or `404` response uses the standard error object
- **AND** the accepted process identifier is echoed

#### Scenario: Unexpected failure is sanitized

- **GIVEN** an unexpected internal failure occurs during a root Party operation
- **WHEN** the failure response is returned
- **THEN** it is exactly the `500 server-error` error object
- **AND** no internal diagnostic content is included

### Requirement: Tenant-scoped Party summaries

**User Story:** As a registry operator, I want to list my tenant's Parties, so that I can locate records without retrieving unrelated or sensitive data.

WHEN `GET /v1/parties` is submitted with valid inputs,
THE Party Registry SHALL return only summaries owned by the requesting tenant, each containing `partyId`, `type`, `displayName`, `recordStatus`, `createdAt`, and `version`.

WHERE type or status filters are absent,
THE Party Registry SHALL include both Party types and all four record statuses, including `ARCHIVED`, subject to other supplied filters. Listing SHALL preserve stored text, versions, and audit information and SHALL NOT require `If-Match` or `Idempotency-Key`.

#### Scenario: Default listing includes retained lifecycle states

- **GIVEN** the tenant owns Parties of both types in `DRAFT`, `ACTIVE`, `INACTIVE`, and `ARCHIVED`
- **WHEN** the tenant lists Parties without filters
- **THEN** all those records are eligible for pagination
- **AND** each result contains the summary fields without type-specific details or identifiers

#### Scenario: Other tenants are excluded from results and totals

- **GIVEN** matching Parties also exist in another tenant
- **WHEN** the current tenant lists Parties
- **THEN** neither results, totals, nor continuation information disclose the other tenant's records

### Requirement: Party listing filter semantics

**User Story:** As a registry operator, I want composable identity and creation-date filters, so that I can find the intended subset of Parties.

WHEN listing filters are supplied,
THE Party Registry SHALL combine them with AND semantics. `type` and `recordStatus` SHALL match their exact declared enum values. Name filters SHALL match literal prefixes or substrings case-insensitively, using the existing locale-independent uppercase comparison and exterior-whitespace rules while preserving accents, punctuation, and interior whitespace. Name comparison SHALL NOT rewrite stored values.

THE Party Registry SHALL interpret `%`, `_`, and other punctuation in name filters literally, and SHALL treat an empty or whitespace-only name filter as absent. Each decoded name-filter value SHALL contain at most 300 Unicode code points before normalization.

WHEN `createdFrom` or `createdTo` is supplied,
THE Party Registry SHALL accept an offset-qualified date-time, compare the represented instant, and include the corresponding lower or upper boundary. Equivalent offsets SHALL represent the same boundary. Equal bounds SHALL select records at that instant.

#### Scenario: Filters intersect rather than broaden results

- **GIVEN** Parties differ in type, status, name, and creation time
- **WHEN** the client supplies type, status, both name filters, and a creation interval
- **THEN** only Parties satisfying every supplied filter are eligible

#### Scenario: Historical mixed-case names remain searchable

- **GIVEN** a stored display name contains mixed case, accents, and a literal `%`
- **WHEN** a matching mixed-case name filter with exterior whitespace and the same literal characters is submitted
- **THEN** the Party matches without treating `%` as a wildcard or removing accents
- **AND** the returned display name retains its stored representation

#### Scenario: Creation interval boundaries are inclusive

- **GIVEN** Parties exist exactly at both creation boundaries and outside them
- **WHEN** the inclusive interval is requested, including equivalent offset representations
- **THEN** both boundary Parties qualify and the outside Parties do not

### Requirement: Party listing input validation

**User Story:** As an API consumer, I want invalid filters rejected rather than silently broadened, so that malformed requests cannot return misleading results.

WHEN the list operation is requested,
THE Party Registry SHALL accept only `type`, `recordStatus`, `displayNameStartsWith`, `displayNameContains`, `createdFrom`, `createdTo`, `cursor`, and `limit` as query parameters, with at most one occurrence of each.

IF a query parameter is unknown or duplicated, an enum value is invalid, a name filter exceeds its limit, a creation boundary is malformed or lacks an offset, or `createdFrom` exceeds `createdTo`,
THEN THE Party Registry SHALL return `400 bad-request` without returning a partial collection.

WHEN `limit` is absent,
THE Party Registry SHALL use 50. A supplied limit SHALL be a decimal integer from 1 through 200; invalid, blank, fractional, zero, negative, and oversized values SHALL return `400 bad-request`.

#### Scenario: Invalid queries are not silently ignored

- **WHEN** a list request contains an unknown parameter, duplicate `type`, invalid status, an oversized name filter, or an inverted date interval
- **THEN** the response is `400 bad-request` with no data

#### Scenario: Page size boundaries are explicit

- **WHEN** a list request omits `limit`, supplies 1 or 200, or supplies 0 or 201
- **THEN** omission uses 50, boundary values are accepted, and out-of-range values return `400 bad-request`

### Requirement: Stable scoped Party pagination

**User Story:** As an API consumer, I want predictable continuation and page metadata, so that I can navigate matching Parties without duplicates on unchanged data.

WHEN Parties are listed,
THE Party Registry SHALL order results by `createdAt` descending and then canonical `partyId` descending, returning at most the effective limit. Every page SHALL use the same ordering, including pages reached through a previous cursor.

THE Party Registry SHALL return opaque continuation cursors scoped to the tenant, effective filters, and page size. Omitting `cursor` SHALL request the first page. Empty, malformed, altered, or scope-mismatched cursors SHALL return `400 bad-request` without exposing cursor internals.

WHEN the matching dataset remains unchanged,
THE Party Registry SHALL allow forward traversal without omitting or repeating matching Parties and backward traversal to the preceding page. Pagination across separate requests SHALL reflect current data rather than promise an immutable historical snapshot.

WHEN returning a collection,
THE Party Registry SHALL include all five declared pagination properties: `nextCursor`, `prevCursor`, `totalElements`, `totalPages`, and `numberOfElements`. Unavailable directions SHALL be explicit nulls. Totals SHALL describe all matches for the tenant and filters, before the continuation position; `totalPages` SHALL equal the number of pages required at the effective limit, and `numberOfElements` SHALL equal the returned array length. Each response's data and metadata SHALL describe one consistent view of the matching records.

#### Scenario: Equal creation timestamps have a stable order

- **GIVEN** more matching Parties than fit on one page, including equal creation timestamps
- **WHEN** the client follows all next cursors without intervening data changes
- **THEN** every Party appears exactly once in the declared timestamp/identifier order

#### Scenario: Previous navigation restores the preceding page

- **GIVEN** an unchanged dataset and a client on the second page
- **WHEN** the client follows `prevCursor` with the same filters and page size
- **THEN** it receives the original first page in the same order
- **AND** the first page's `prevCursor` is present and null

#### Scenario: Cursor scope cannot be reused incorrectly

- **GIVEN** a cursor issued for one tenant, effective filter set, and page size
- **WHEN** it is altered or submitted with a different tenant, filter set, or page size
- **THEN** the response is `400 bad-request` without collection data

#### Scenario: Collection metadata includes terminal and empty values

- **GIVEN** three matching Parties and a limit of two
- **WHEN** the first and final pages are requested
- **THEN** both report `totalElements: 3` and `totalPages: 2`, with element counts two and one respectively
- **AND** the final page contains `nextCursor: null`
- **AND** a separate query with no matches returns `data: []`, both cursors as null, and all three counts as zero

### Requirement: Common Party detail retrieval

**User Story:** As an API consumer, I want a complete root Party representation, so that I can inspect either Party type using one route.

WHEN `GET /v1/parties/{partyId}` identifies a tenant-owned Party,
THE Party Registry SHALL return `partyId`, `type`, `displayName`, `recordStatus`, `version`, creation/update timestamps and users, and exactly the detail property corresponding to its immutable type: `naturalPersonDetails` or `legalEntityDetails`.

THE Party Registry SHALL support retrieval in all four record statuses, preserve stored historical text and audit values, and exclude creation-only `initialIdentifier`, identifier collections, nationality collections, and pagination properties. Retrieval SHALL NOT require version or idempotency headers and SHALL NOT depend on fresh geographic-reference validation.

IF the Party ID is not a canonical lowercase UUID,
THEN THE Party Registry SHALL return `400 party-id-invalid`.

IF a syntactically valid ID is absent or belongs to another tenant,
THEN THE Party Registry SHALL return `404 party-not-found` without revealing its owner, type, or version.

#### Scenario: Each Party type returns its own details

- **WHEN** the tenant retrieves a natural-person Party and, separately, a legal-entity Party
- **THEN** each response contains the common fields and only its corresponding detail property
- **AND** neither response includes identifiers, creation-only information, or pagination

#### Scenario: Archived and historical records remain readable

- **GIVEN** an archived Party with historical mixed-case text
- **WHEN** it is retrieved while geographic-reference validation is unavailable
- **THEN** its stored details are returned without text, version, or audit changes

#### Scenario: Invalid and concealed IDs have distinct safe outcomes

- **WHEN** retrieval uses a malformed or noncanonical UUID
- **THEN** the response is `400 party-id-invalid`
- **AND** separate requests for an absent UUID and a cross-tenant UUID both return the same `404 party-not-found` object
