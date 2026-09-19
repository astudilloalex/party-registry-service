## Purpose

This capability enables consumers to retrieve and maintain the legal details of a tenant-owned legal entity after registration. It defines complete and partial updates with predictable validation, concurrency protection, audit outcomes, and confidential public responses.

## ADDED Requirements

### Requirement: Retrieve legal-entity details

**User Story:** As a registry operator, I want to retrieve a registered legal entity, so that I can review its current information and version.

WHEN a valid `GET /v1/legal-entity/{partyId}` identifies a legal entity owned by the requesting tenant,
THE Party Registry SHALL return HTTP `200`, body status `200`, code `successful`, and the current legal-entity representation in `data`.

THE Party Registry SHALL include `partyId`, `type` equal to `LEGAL_ENTITY`, `displayName`, `recordStatus`, `version`, `createdAt`, `updatedAt`, `createdBy`, `updatedBy`, and `legalEntityDetails`, following the declared `LegalEntityApiResponse` contract.

WHEN legal-entity details are returned,
THE Party Registry SHALL represent `legalName` and `incorporationCountryCode` as non-null strings and represent the optional `tradeName`, `legalFormCode`, `incorporatedOn`, and `dissolvedOn` values according to their declared nullable schemas.

THE Party Registry SHALL expose this retrieval as an individual-resource operation without required query parameters, request body, `If-Match`, `Idempotency-Key`, or pagination fields.

#### Scenario: Retrieve an existing legal entity

- **GIVEN** a legal entity exists in the requesting tenant with version `3`
- **WHEN** a valid GET identifies that Party
- **THEN** the response is `200 successful` with its current legal details and version `3`
- **AND** the returned Party type is `LEGAL_ENTITY`

#### Scenario: Read the result of a completed update

- **GIVEN** a legal-detail update has completed successfully
- **WHEN** the same tenant subsequently retrieves that legal entity
- **THEN** the returned details, display name, version, and audit information reflect the accepted update

#### Scenario: Retrieval preserves historical values

- **GIVEN** a legal entity contains historical mixed-case text and nullable optional fields
- **WHEN** the entity is retrieved
- **THEN** stored text is returned without write normalization
- **AND** the read changes neither details, Party version, nor audit information

### Requirement: Trusted request context and path validation

**User Story:** As a tenant administrator, I want every legal-detail request associated with a validated tenant, user, and process, so that operations remain attributable and correctly scoped.

WHEN GET, PUT, or PATCH is submitted to `/v1/legal-entity/{partyId}`,
THE Party Registry SHALL require exactly one `Process-Id`, `Tenant-Id`, and `User-Id`, with case-insensitive header names and the shared trusted-context validation rules.

THE Party Registry SHALL accept only canonical lowercase UUID values for `Process-Id`, `Tenant-Id`, and `partyId`, without trimming their values, and SHALL require a nonblank `User-Id` of at most 128 Unicode code points without C0, DEL, or C1 control characters.

IF a trusted-context header is invalid,
THEN THE Party Registry SHALL return HTTP `400` with its existing header-specific code, selecting Process-Id before Tenant-Id before User-Id, and absence before duplicate detection before value validation within each header.

WHEN Process-Id has been accepted,
THE Party Registry SHALL echo that exact value on success and subsequent failure responses.

IF Process-Id is missing, duplicated, or invalid,
THEN THE Party Registry SHALL omit Process-Id from the response.

IF the path Party identifier is invalid after earlier request validation has succeeded,
THEN THE Party Registry SHALL return `400 party-id-invalid` without reading or changing another Party.

#### Scenario: Accepted process survives a later header failure

- **GIVEN** Process-Id is valid and Tenant-Id is absent
- **WHEN** any legal-detail operation is submitted
- **THEN** the response is `400 tenant-id-required`
- **AND** it echoes the accepted Process-Id unchanged

#### Scenario: Invalid process takes precedence

- **GIVEN** Process-Id is `not-a-uuid` and Tenant-Id is also missing
- **WHEN** the request is submitted
- **THEN** the response is `400 process-id-invalid`
- **AND** no Process-Id response header is present

#### Scenario: Duplicate and unsafe context values are rejected

- **GIVEN** all earlier context headers are valid
- **WHEN** the request contains two User-Id values, even if identical
- **THEN** the response is `400 user-id-duplicated`
- **WHEN** instead a single User-Id exceeds 128 Unicode code points or contains a prohibited control character
- **THEN** the response is respectively `400 user-id-too-long` or `400 user-id-unsafe`

#### Scenario: Noncanonical path identifier is rejected

- **GIVEN** valid context and otherwise valid operation inputs
- **WHEN** partyId is malformed, uppercase, or surrounded by whitespace
- **THEN** the response is `400 party-id-invalid`
- **AND** no Party changes

### Requirement: Tenant and Party-type isolation

**User Story:** As a tenant administrator, I want legal-detail operations restricted to my legal entities, so that another tenant's records and natural-person details remain inaccessible.

IF a syntactically valid partyId does not identify a `LEGAL_ENTITY` belonging to the requesting tenant,
THEN THE Party Registry SHALL return `404 legal-entity-not-found` for GET, PUT, and PATCH after applicable input validation, without disclosing the actual owner, type, details, or version.

#### Scenario: Absent legal entity

- **GIVEN** partyId is a valid UUID that does not identify an existing Party
- **WHEN** a valid GET, PUT, or PATCH targets that ID
- **THEN** the response is `404 legal-entity-not-found`
- **AND** PUT and PATCH do not create a replacement Party

#### Scenario: Cross-tenant entity is concealed

- **GIVEN** partyId belongs to a legal entity in a different tenant
- **WHEN** a valid GET, PUT, or PATCH targets that ID
- **THEN** the response is `404 legal-entity-not-found`
- **AND** the other tenant's details, status, version, and audit information remain unchanged

#### Scenario: Natural-person Party cannot be treated as a legal entity

- **GIVEN** partyId belongs to a natural person in the requesting tenant
- **WHEN** a valid GET, PUT, or PATCH uses the legal-entity detail route
- **THEN** the response is `404 legal-entity-not-found`
- **AND** the natural person remains unchanged

### Requirement: Complete replacement of legal details

**User Story:** As a data steward, I want to submit a complete legal-detail representation, so that omitted optional information is deliberately cleared.

WHEN a valid `PUT /v1/legal-entity/{partyId}` supplies the current Party version,
THE Party Registry SHALL replace the six declared legal-detail fields with the accepted representation and return the resulting legal entity as `200 successful`.

THE Party Registry SHALL require nonblank `legalName` and non-null `incorporationCountryCode` in every PUT body and SHALL clear omitted or explicitly null `tradeName`, `legalFormCode`, `incorporatedOn`, and `dissolvedOn` values.

#### Scenario: Replace all legal details

- **GIVEN** an existing legal entity and its current version
- **WHEN** PUT supplies valid values for all six declared fields
- **THEN** the returned and subsequently retrieved legal details contain the accepted replacement values

#### Scenario: Omitted optional values are cleared

- **GIVEN** all four optional fields currently contain values
- **WHEN** a valid PUT supplies only legalName and incorporationCountryCode
- **THEN** all four optional values are cleared
- **AND** the response and later retrieval do not retain their previous values

#### Scenario: Replacement cannot omit mandatory fields

- **GIVEN** an otherwise valid PUT body and request context
- **WHEN** legalName is missing, null, or blank
- **THEN** the response is `400 legal-name-required`
- **WHEN** instead incorporationCountryCode is missing or null
- **THEN** the response is `400 incorporation-country-code-required`
- **AND** each rejected request leaves the legal entity unchanged

### Requirement: Presence-aware partial legal-detail updates

**User Story:** As a registry operator, I want to correct selected legal fields, so that unrelated information is retained.

WHEN a valid `PATCH /v1/legal-entity/{partyId}` supplies the current Party version,
THE Party Registry SHALL update only submitted supported properties and return the resulting legal entity as `200 successful`.

THE Party Registry SHALL retain omitted values exactly, including historical text, and SHALL permit explicit null to clear only `tradeName`, `legalFormCode`, `incorporatedOn`, and `dissolvedOn`.

IF an otherwise well-formed PATCH object contains no supported property,
THEN THE Party Registry SHALL return `400 patch-property-required`; unknown properties remain subject to the strict-body rule below.

IF a PATCH explicitly clears legalName or incorporationCountryCode,
THEN THE Party Registry SHALL return respectively `400 legal-name-required` or `400 incorporation-country-code-required` without accepting any part of the update.

#### Scenario: Correct one optional field

- **GIVEN** an existing legal entity with historical mixed-case legalName and a custom displayName
- **WHEN** a valid PATCH supplies only `tradeName: "  New Brand  "`
- **THEN** tradeName becomes `NEW BRAND`
- **AND** the omitted legalName and displayName retain their existing representations

#### Scenario: Explicit null clears only the selected optional field

- **GIVEN** tradeName and legalFormCode both contain values
- **WHEN** a valid PATCH supplies only `tradeName: null`
- **THEN** tradeName is cleared
- **AND** legalFormCode and the other omitted details retain their values

#### Scenario: Empty patch is rejected

- **GIVEN** valid request context and version input
- **WHEN** PATCH supplies `{}`
- **THEN** the response is `400 patch-property-required`
- **AND** the Party and its version remain unchanged

#### Scenario: Partial update cannot clear mandatory information

- **GIVEN** an otherwise valid PATCH
- **WHEN** it supplies `legalName: null` or `incorporationCountryCode: null`
- **THEN** it returns the corresponding required-field code
- **AND** no other submitted field is applied

### Requirement: Strict body validation and canonical text

**User Story:** As an API consumer, I want predictable field rules and canonical text, so that accepted corrections have consistent public representations.

WHEN PUT or PATCH accepts newly supplied text,
THE Party Registry SHALL remove exterior whitespace and use locale-independent uppercase for legalName, tradeName, legalFormCode, and incorporationCountryCode, preserving accents, punctuation, interior whitespace, and allowed nulls.

THE Party Registry SHALL enforce the following public rules on PUT fields and on properties explicitly submitted in PATCH:

| Input condition | HTTP status and code |
| --- | --- |
| Missing or null required body | `400 request-body-required` |
| Missing, null, or blank required legalName | `400 legal-name-required` |
| legalName exceeds 300 normalized UTF-16 code units | `400 legal-name-invalid` |
| tradeName exceeds 300 normalized UTF-16 code units | `400 trade-name-too-long` |
| legalFormCode exceeds 64 normalized UTF-16 code units | `400 legal-form-code-too-long` |
| Missing or null required incorporationCountryCode | `400 incorporation-country-code-required` |
| Supplied country is not exactly two ASCII letters after exterior whitespace removal | `400 incorporation-country-code-invalid` |
| Empty PATCH object | `400 patch-property-required` |
| Malformed JSON, non-object body, incompatible JSON value type, invalid date syntax, or unknown property | `400 bad-request` |

THE Party Registry SHALL count uppercase expansion toward normalized text limits, accept a value exactly at its limit, and reject non-ASCII country letters even when uppercase expansion could produce two ASCII letters.

WHEN a deserialized body has multiple validation failures,
THE Party Registry SHALL select required-rule codes first, then the first lexicographic property path, then the first lexicographic rule code, and SHALL report that single code before operation-header validation. Earlier context and JSON-binding failures SHALL retain their precedence.

#### Scenario: Text and country are canonicalized

- **GIVEN** a valid version and otherwise valid update
- **WHEN** supplied fields include `legalName: "  Compañía Águila S.A.  "`, `legalFormCode: " sa "`, and `incorporationCountryCode: " ec "`
- **THEN** accepted values are `COMPAÑÍA ÁGUILA S.A.`, `SA`, and `EC`
- **AND** punctuation and accents are preserved

#### Scenario: Normalized length boundaries are enforced

- **GIVEN** an otherwise valid update
- **WHEN** its normalized legalName or tradeName has exactly 300 UTF-16 code units, or legalFormCode has exactly 64
- **THEN** the respective length constraint is satisfied
- **WHEN** an otherwise equivalent value exceeds its limit after uppercasing, including through uppercase expansion
- **THEN** the corresponding length-error code is returned and nothing is changed

#### Scenario: Required body fields precede version-header errors

- **GIVEN** valid trusted context
- **WHEN** PUT supplies `{}` and omits If-Match
- **THEN** the response is `400 incorporation-country-code-required`, the first required property by path
- **AND** the response does not replace it with an If-Match error

#### Scenario: Strict JSON rejects unsupported fields and types

- **GIVEN** valid trusted context
- **WHEN** PUT or PATCH contains an unknown property such as `displayName`, `recordStatus`, `version`, or `initialIdentifier`, even alongside supported fields
- **THEN** the response is `400 bad-request` and the update is not applied
- **WHEN** instead a legal-name property is a number or a date has an invalid calendar representation
- **THEN** the response is also `400 bad-request`

#### Scenario: Invalid country representation is rejected before geographic validation

- **GIVEN** an otherwise valid update
- **WHEN** incorporationCountryCode is blank, contains embedded whitespace, has the wrong length, or contains non-ASCII letters
- **THEN** the response is `400 incorporation-country-code-invalid`
- **AND** it remains a representation error rather than a geographic-dependency failure

### Requirement: Derived display-name behavior

**User Story:** As a registry operator, I want meaningful legal-name changes reflected in the display name, so that the visible Party label remains consistent without losing custom labels on unrelated edits.

WHEN an accepted PUT or PATCH changes legalName to a value different from the existing legalName after applying the same canonical text rules to both,
THE Party Registry SHALL set displayName to the resulting canonical legalName.

WHEN legalName is omitted from PATCH or remains canonically equivalent in an accepted update,
THE Party Registry SHALL preserve the existing displayName, including a custom or historical representation.

#### Scenario: Changed legal name replaces a custom display name

- **GIVEN** legalName is `OLD COMPANY S.A.` and displayName is `Custom Label`
- **WHEN** an accepted update supplies `legalName: "New Company S.A."`
- **THEN** legalName and displayName both become `NEW COMPANY S.A.`

#### Scenario: Canonically equivalent name preserves a custom label

- **GIVEN** legalName is `Example Company` and displayName is `Custom Label`
- **WHEN** an accepted update supplies `legalName: "  example company  "`
- **THEN** legalName becomes `EXAMPLE COMPANY`
- **AND** displayName remains `Custom Label`

### Requirement: Coherent legal lifecycle dates

**User Story:** As a data steward, I want organization dates validated together, so that a partial correction cannot create an impossible legal history.

WHEN PUT or PATCH is evaluated,
THE Party Registry SHALL validate the complete resulting legal details against one trusted UTC evaluation date for that update.

IF a resulting non-null incorporatedOn is in the future, dissolvedOn is in the future, or dissolvedOn precedes incorporatedOn when both are present,
THEN THE Party Registry SHALL return HTTP `422` with respectively `incorporation-date-in-future`, `dissolution-date-in-future`, or `dissolution-before-incorporation`, without changing the Party.

WHEN multiple semantic date rules fail,
THE Party Registry SHALL report dissolution-before-incorporation first, then incorporation-date-in-future, then dissolution-date-in-future, following the existing legal-detail validation order.

THE Party Registry SHALL allow nullable dates and equal incorporation/dissolution dates, including the evaluation date, when all other constraints are satisfied.

#### Scenario: Partial change is checked against retained dates

- **GIVEN** incorporatedOn is `2020-01-15`
- **WHEN** PATCH supplies only `dissolvedOn: "2019-12-31"`
- **THEN** the response is `422 dissolution-before-incorporation`
- **AND** neither date, Party version, nor audit information changes

#### Scenario: Future legal date is rejected

- **GIVEN** an update is evaluated on a known UTC date
- **WHEN** either resulting legal date is later than that date
- **THEN** the response has HTTP status `422` and the applicable `incorporation-date-in-future` or `dissolution-date-in-future` code

#### Scenario: Date boundaries and nullable values are accepted

- **GIVEN** valid context, version, and other legal details
- **WHEN** both dates equal the UTC evaluation date, or a valid update explicitly clears a nullable date
- **THEN** date validation succeeds without requiring both dates to be present

### Requirement: Incorporation-country recognition

**User Story:** As a data steward, I want changed incorporation countries checked against the authoritative country reference, so that organization records use recognized countries.

WHEN the resulting canonical incorporationCountryCode differs from the current country's canonical value in PUT or PATCH,
THE Party Registry SHALL require the Geographic Reference Service to recognize the new code before accepting the update.

IF the reference service establishes that the code is unrecognized,
THEN THE Party Registry SHALL return `422 unrecognized-incorporation-country` without changing the legal entity.

IF required country validation cannot be completed because the reference service is unavailable or fails to provide a usable result,
THEN THE Party Registry SHALL return `503 dependency-unavailable` without treating the country as accepted or unrecognized.

WHEN GET is requested, or an otherwise valid update retains the same canonical incorporation country,
THE Party Registry SHALL not require a fresh geographic validation result for that operation.

#### Scenario: Recognized country change succeeds

- **GIVEN** a legal entity's incorporation country is `EC` and the reference service recognizes `GB`
- **WHEN** an otherwise valid update supplies `incorporationCountryCode: " gb "`
- **THEN** the new country is validated as `GB` and the accepted representation contains `GB`

#### Scenario: Unknown country is not accepted

- **GIVEN** a changed country is syntactically valid but the reference service reports it as unrecognized
- **WHEN** PUT or PATCH requests that change
- **THEN** the response is `422 unrecognized-incorporation-country` and the previous details remain observable

#### Scenario: Country dependency failure leaves no partial update

- **GIVEN** an update changes both tradeName and incorporationCountryCode
- **AND** required geographic validation is unavailable
- **WHEN** the update is submitted
- **THEN** the response is `503 dependency-unavailable`
- **AND** neither field nor the Party version or audit information changes

#### Scenario: Unchanged country does not make unrelated maintenance dependent on availability

- **GIVEN** the current country is `EC` and Geographic Reference is unavailable
- **WHEN** an otherwise valid PUT supplies the same country as `" ec "`, or PATCH retains that country while changing tradeName
- **THEN** the update can succeed without a fresh country validation result
- **AND** an otherwise valid GET also remains available

### Requirement: Version preconditions and concurrent updates

**User Story:** As an API consumer, I want stale corrections rejected, so that concurrent work cannot silently overwrite an accepted change.

WHEN PUT or PATCH is submitted,
THE Party Registry SHALL require exactly one If-Match value containing the expected Party version as a bare decimal matching `^(0|[1-9][0-9]*)$`, in the range 0 through 9223372036854775807.

IF If-Match is absent, duplicated, malformed, or outside that range after preceding input validation,
THEN THE Party Registry SHALL return HTTP `400` with respectively `if-match-required`, `if-match-duplicated`, `if-match-invalid`, or `if-match-out-of-range`.

IF an existing tenant-owned legal entity's version differs from the valid expected version,
THEN THE Party Registry SHALL return `412 expected-version-mismatch` before evaluating proposed semantic changes and without modifying the record.

WHEN concurrent legal-detail updates or an existing Party lifecycle operation target the same current version,
THE Party Registry SHALL permit at most one accepted Party update for that version and SHALL reject a legal-detail update that loses the version race with `412 expected-version-mismatch`.

THE Party Registry SHALL not require Idempotency-Key for these detail operations or treat a creation idempotency key as a substitute for the current Party version.

#### Scenario: Invalid version-header forms have stable codes

- **GIVEN** valid context, path, and update body
- **WHEN** If-Match is missing or appears twice
- **THEN** the response is respectively `400 if-match-required` or `400 if-match-duplicated`
- **WHEN** it is instead blank, quoted, negative, a wildcard, a decimal fraction, or has leading zeroes other than `0`
- **THEN** the response is `400 if-match-invalid`
- **WHEN** it is instead `9223372036854775808`
- **THEN** the response is `400 if-match-out-of-range`

#### Scenario: Valid zero version succeeds without an idempotency key

- **GIVEN** a newly registered legal entity at version `0`
- **WHEN** a valid update supplies If-Match `0` and omits Idempotency-Key
- **THEN** the update succeeds with `200 successful`

#### Scenario: Stale version takes precedence over semantic country validation

- **GIVEN** the Party is at version `2` and Geographic Reference is unavailable
- **WHEN** a structurally valid update with If-Match `1` proposes a changed country
- **THEN** the response is `412 expected-version-mismatch`, not a geographic-dependency error
- **AND** the Party remains unchanged

#### Scenario: Concurrent PUT and PATCH have one winner

- **GIVEN** two otherwise valid updates target the same legal entity at version `4`
- **WHEN** PUT and PATCH concurrently submit If-Match `4`
- **THEN** one update succeeds at version `5`
- **AND** the other receives `412 expected-version-mismatch`
- **AND** the final legal details reflect the winner without fields from the rejected update

### Requirement: Atomic update and audit outcome

**User Story:** As a data steward, I want each accepted correction to have one complete, attributable result, so that versions and audit information agree with the legal details.

WHEN a legal-detail PUT or PATCH succeeds,
THE Party Registry SHALL make its details, applicable display-name change, Party version increment of exactly one, and updated audit information observable together and SHALL return that accepted result.

THE Party Registry SHALL retain partyId, owning tenant, Party type, recordStatus, createdAt, and createdBy, and SHALL set updatedBy to the accepted User-Id and updatedAt to the trusted time of the update.

WHEN a valid update is accepted with values identical to the existing representation,
THE Party Registry SHALL still record that accepted update with the next Party version and updated audit information.

IF an update fails before its complete outcome is accepted,
THEN THE Party Registry SHALL leave the previous details, display name, version, and audit information unchanged by that request.

#### Scenario: Accepted correction has one consistent audit result

- **GIVEN** a legal entity at version `5` with a known creator and creation timestamp
- **WHEN** user `registry-editor` successfully updates its details
- **THEN** the response and subsequent retrieval show version `6`, updatedBy `registry-editor`, and the accepted update timestamp
- **AND** the original creator, creation timestamp, Party identity, type, and recordStatus remain unchanged

#### Scenario: Identical accepted replacement still advances the version

- **GIVEN** a legal entity at version `6`
- **WHEN** a valid PUT repeats its existing complete canonical details with If-Match `6`
- **THEN** the result is `200 successful` at version `7` with updated audit information
- **AND** another submission of If-Match `6` is stale

#### Scenario: Late failure does not expose a partial correction

- **GIVEN** an otherwise valid update is being accepted
- **WHEN** an unexpected failure prevents completion
- **THEN** the response is `500 server-error`
- **AND** the rejected request contributes no change to legal details, display name, Party version, or audit information

### Requirement: Consistent public responses and confidential failures

**User Story:** As an API consumer, I want stable public responses, so that I can handle outcomes without depending on internal error details.

WHEN any of the three legal-detail operations succeeds,
THE Party Registry SHALL return a JSON envelope containing status `200`, code `successful`, and the declared legal-entity representation in data.

WHEN a legal-detail request fails,
THE Party Registry SHALL return a JSON envelope containing only `status` and one stable `code`, with body status equal to the actual HTTP status and no data, field-error collection, rejected value, exception message, stack trace, SQL detail, or internal cause.

WHEN a known legal-detail business or validation failure is returned,
THE Party Registry SHALL identify its cause using the applicable service-owned code rather than collapsing it to `not-found`, `precondition-failed`, `unprocessable-entity`, or `invalid-business-state`.

THE Party Registry SHALL document the following business failure codes alongside the field-specific 400 codes defined above:

| Business condition | HTTP status and code |
| --- | --- |
| Absent, cross-tenant, or wrong-type Party | `404 legal-entity-not-found` |
| Expected version differs or an update loses the version race | `412 expected-version-mismatch` |
| Incorporation country is definitively unrecognized | `422 unrecognized-incorporation-country` |
| Resulting dissolution precedes incorporation | `422 dissolution-before-incorporation` |
| Resulting incorporation date is in the future | `422 incorporation-date-in-future` |
| Resulting dissolution date is in the future | `422 dissolution-date-in-future` |

Framework JSON/method/media errors keep their established framework codes, dependency failures keep `dependency-unavailable`, and unexpected internal failures keep sanitized `server-error`; cause-specific business codes do not disclose technical causes.

IF an unexpected failure occurs,
THEN THE Party Registry SHALL record the failure internally and expose only `500 server-error` through the public response.

WHEN a framework-level client error is produced for these business routes,
THE Party Registry SHALL preserve its applicable HTTP status and shared stable error code in the same error envelope, including `405 method-not-allowed` for an unsupported method and HTTP `415` for an unsupported request media type.

WHERE authentication is enforced for the service, IF a request fails authentication,
THEN THE Party Registry SHALL retain the established `401 unauthorized` contract.

#### Scenario: Success is a legal-entity envelope

- **WHEN** GET, PUT, or PATCH succeeds
- **THEN** both HTTP and body status are `200`, code is `successful`, and data contains legalEntityDetails and the required Party fields
- **AND** data contains neither naturalPersonDetails, creation-only initialIdentifier, nor an added identifiers collection

#### Scenario: Invalid JSON uses a sanitized error

- **GIVEN** valid trusted context
- **WHEN** PUT or PATCH receives malformed JSON
- **THEN** the response has exactly `status: 400` and `code: "bad-request"`
- **AND** it echoes the accepted Process-Id without exposing the malformed input

#### Scenario: Business failure codes distinguish the cause for consumers

- **GIVEN** requests independently target a missing legal entity, a stale version, an unrecognized country, or an invalid legal lifecycle date
- **WHEN** each failure is returned
- **THEN** its status and code match the business failure table
- **AND** the code identifies the cause without a message or internal details

#### Scenario: Framework failures keep the common envelope

- **GIVEN** valid trusted context
- **WHEN** DELETE is requested on the legal-entity detail route
- **THEN** the response is `405 method-not-allowed` in the standard error envelope
- **WHEN** PUT or PATCH instead supplies an unsupported request Content-Type
- **THEN** the response has HTTP/body status `415` and the shared media-type error code in that envelope

#### Scenario: Internal failure is not disclosed

- **GIVEN** a legal-detail operation encounters an unexpected internal failure containing diagnostic details
- **WHEN** the public response is returned
- **THEN** it contains only status `500` and code `server-error`
- **AND** none of those diagnostic details appear in the body

### Requirement: Compatibility with registration and independent identifiers

**User Story:** As a registry operator, I want legal-detail maintenance to preserve registration and identifier history, so that corrections do not recreate identities or alter verification evidence.

WHEN legal details are retrieved or updated,
THE Party Registry SHALL preserve all associated identifiers, their values, statuses, versions, and verification metadata, and SHALL not expose complete, normalized, or reversibly protected identifier values through these responses.

WHEN a previously completed legal-entity creation is replayed after a detail update,
THE Party Registry SHALL continue to return the original creation result and original versions under the existing registration contract rather than rewriting that snapshot to reflect current details.

#### Scenario: Details change independently from identifiers and lifecycle

- **GIVEN** a DRAFT or ACTIVE legal entity with an existing identifier and recorded identifier version
- **WHEN** a valid legal-detail update succeeds
- **THEN** the Party retains its recordStatus and the identifier retains its value, status, version, and metadata
- **AND** the response does not include identifier values or protection material

#### Scenario: Creation replay remains the original snapshot

- **GIVEN** a legal entity was created at version `0` and later updated to version `1`
- **WHEN** the original creation request is repeated with its original idempotency key
- **THEN** the existing creation operation returns its original `201 successful` result at version `0`
- **AND** a legal-entity GET returns the current details at version `1`

### Requirement: Consumer documentation reflects the implemented operations

**User Story:** As an API consumer, I want complete documentation grouped by entity, so that I can discover and correctly use legal-detail operations.

WHEN these operations are delivered,
THE Party Registry documentation SHALL describe GET, PUT, and PATCH as implemented operations with their declared paths, fields, normalization, null semantics, version rules, response shapes, validation codes, and relevant dependency failures.

THE Party Registry documentation SHALL extend the existing legal-field validation-code applicability to PUT and PATCH, document every cause-specific business code and their `503 dependency-unavailable` outcome and accepted Process-Id response header, and preserve compatibility of the existing creation contract.

WHEN the Postman collection `15834347-8f36abef-3a94-4646-a4f2-eecd36c74313` is updated for this capability,
THE published collection SHALL include one request for each new operation under **Legal Entities**, alongside its existing creation request, with documented variables, headers, bodies where applicable, and illustrative success and failure examples.

#### Scenario: All legal-entity operations appear in their own folder

- **WHEN** a consumer opens the updated collection
- **THEN** Legal Entities contains creation, retrieval, complete replacement, and partial update requests without duplicate operation entries
- **AND** Parties and Party Identifiers retain their separate folders and existing requests
- **AND** the new examples demonstrate tenant scoping, nullable-field handling, version preconditions, and standard envelopes without sensitive identifier data
