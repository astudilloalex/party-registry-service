# Feature Specification: Party Registration

**Feature Branch**: `11-ft-1` (existing branch; no `before_specify` hook was configured)

**Created**: 2026-08-21

**Status**: Ready for planning

**Input**: Create a tenant-owned natural-person or legal-entity Party with validated identity
details, geographic references, audit data, and optional transactional outbox recording.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register a Natural Person (Priority: P1)

An authorized internal caller registers a natural person for the caller's tenant using the
minimum identity details and, when known, birth-country and nationality information. The caller
receives the new Party identifier and initial version only after the complete Party aggregate is
accepted.

**Why this priority**: Natural-person registration is a primary purpose of the Party Registry and
provides the smallest independently valuable Party creation flow.

**Independent Test**: Submit natural-person creation commands for one tenant and verify that each
accepted command returns a Party identifier and version `0`, while the resulting Party contains
exactly the matching natural-person details and optional nationalities.

**Acceptance Scenarios**:

1. **Given** a valid tenant and audit context, **When** a caller creates a `NATURAL_PERSON` with
   given names and family names only, **Then** one `DRAFT` Party and its natural-person details are
   created and the result contains its `partyId` and version `0`.
2. **Given** an active birth-country code, **When** a caller creates a natural person with that
   birth country, **Then** the country is validated and stored with the Party details.
3. **Given** multiple distinct active country codes and valid intervals, **When** a caller creates
   a natural person with multiple nationalities, **Then** all nationalities are created for that
   Party.
4. **Given** multiple valid nationalities, **When** exactly one active nationality is marked
   primary, **Then** the Party is created with that single active primary nationality.
5. **Given** two nationalities that are active on the registration date and both marked primary,
   **When** the caller submits the creation command, **Then** the command is rejected and no Party
   data is created.
6. **Given** natural-person lifecycle or nationality dates whose end precedes their start,
   **When** the caller submits the creation command, **Then** the command is rejected and no Party
   data is created.

---

### User Story 2 - Register a Legal Entity (Priority: P1)

An authorized internal caller registers a legal entity for the caller's tenant using its legal
name and country of incorporation. The caller receives the new Party identifier and initial
version only after the complete Party aggregate is accepted.

**Why this priority**: Legal-entity registration is the other required Party type and has equal
business importance to natural-person registration.

**Independent Test**: Submit a legal-entity creation command with a valid incorporation country
and verify that the accepted Party contains exactly the legal-entity details, has version `0`, and
contains no natural-person details or nationalities.

**Acceptance Scenarios**:

1. **Given** an active incorporation-country code, **When** a caller creates a `LEGAL_ENTITY` with
   a legal name and that country, **Then** one `DRAFT` Party and its legal-entity details are
   created and the result contains its `partyId` and version `0`.
2. **Given** a Party type and details belonging to the other type, or details for both types,
   **When** the caller submits the creation command, **Then** the command is rejected and no Party
   data is created.
3. **Given** incorporation and dissolution dates where dissolution precedes incorporation,
   **When** the caller submits the creation command, **Then** the command is rejected and no Party
   data is created.

---

### User Story 3 - Preserve Tenant and Transaction Integrity (Priority: P1)

An authorized internal caller can create a Party only inside its trusted tenant context. All Party
records, audit information, and any enabled creation event become visible together or not at all.

**Why this priority**: Tenant isolation, auditability, and atomic persistence are mandatory safety
properties; a creation flow without them is not releasable.

**Independent Test**: Exercise accepted and rejected creation commands under two tenant contexts,
with events both disabled and enabled, and inject failures at each persistence boundary to verify
tenant ownership and all-or-nothing outcomes.

**Acceptance Scenarios**:

1. **Given** a request without a valid trusted tenant context, **When** Party creation is requested,
   **Then** the request is rejected before geographic validation or any state change.
2. **Given** a tenant identifier in client-supplied Party data, **When** Party creation is
   requested, **Then** that value is not trusted or used to establish ownership.
3. **Given** two valid tenant contexts, **When** each creates a Party, **Then** each Party belongs
   only to the tenant from its own trusted context and neither creation can affect the other.
4. **Given** an unknown or inactive country code in any Party field, **When** creation is requested,
   **Then** the command is rejected and no Party data or event is created.
5. **Given** the geographic reference dependency cannot complete validation, **When** creation is
   requested, **Then** the operation fails without creating Party data or an event.
6. **Given** a failure while storing any part of the Party, its details, nationalities, or enabled
   event, **When** the operation ends, **Then** every state change from that command is rolled back.
7. **Given** Party creation events are disabled or the creation event type is not enabled,
   **When** a valid Party is created, **Then** the complete Party is committed without an outbox
   event.
8. **Given** the Party creation event type is enabled, **When** a valid Party is created, **Then**
   exactly one tenant-scoped `PENDING` creation event and the complete Party are committed in the
   same atomic operation.
9. **Given** a successful creation, **When** the caller receives the result, **Then** it contains
   the generated `partyId` and initial Party version `0`.

### Edge Cases

- The Party type is absent or is not one of `NATURAL_PERSON` and `LEGAL_ENTITY`.
- Required type-specific details are absent, empty, or supplied for the wrong Party type.
- A legal entity includes nationalities, which belong only to natural persons.
- The same active nationality country is supplied more than once for one Party.
- More than 10 nationalities are supplied in one creation command.
- A nationality starts in the future or ended before registration; its primary-status evaluation
  follows the clarified active-nationality rule.
- A birth or incorporation country is syntactically valid but unknown or inactive.
- Tenant or audit context is missing, malformed, or conflicts with client-supplied data.
- Geographic validation succeeds for some codes and fails for another; no state is created.
- The effective event configuration changes between requests; each request follows the
  configuration effective for its own atomic operation.
- A failure occurs after the Party root is prepared but before details, nationalities, or the
  enabled event can be completed; no partial state remains.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The service MUST accept one Party creation command for exactly one trusted tenant
  context.
- **FR-002**: The service MUST obtain the tenant identifier exclusively from one mandatory
  `Tenant-Id` request header. The value MUST be a valid tenant UUID; a missing, malformed, or
  duplicate header MUST be rejected as invalid context.
- **FR-003**: The creation payload MUST NOT establish or override Party ownership. If a tenant
  identifier is supplied as Party data, the service MUST reject the command as invalid Party data.
- **FR-004**: The service MUST obtain the audit subject exclusively from one mandatory `User-Id`
  request header and MUST use that value for `createdBy` and `updatedBy`. The value MUST be
  non-empty and no longer than 128 characters; a missing, empty, oversized, or duplicate header
  MUST be rejected as invalid context.
- **FR-005**: The service MUST reject the command before any state change when trusted tenant or
  audit context is missing or invalid.
- **FR-006**: The service MUST accept only `NATURAL_PERSON` or `LEGAL_ENTITY` as the Party type and
  MUST preserve the chosen type as immutable after creation.
- **FR-007**: A `NATURAL_PERSON` command MUST include natural-person details and MUST NOT include
  legal-entity details.
- **FR-008**: Natural-person details MUST include non-empty given names and family names; preferred
  name, birth date, date of death, and birth country are optional.
- **FR-009**: A `LEGAL_ENTITY` command MUST include legal-entity details and MUST NOT include
  natural-person details or nationalities.
- **FR-010**: Legal-entity details MUST include a non-empty legal name and incorporation-country
  code; trade name, legal-form code, incorporation date, and dissolution date are optional.
- **FR-011**: The service MUST derive the Party display name by type. For a natural person, it MUST
  use given names followed by family names; for a legal entity, it MUST use the legal name. The
  creation payload MUST NOT override the derived display name.
- **FR-012**: The service MUST reject a natural person when date of death precedes birth date and
  MUST reject a legal entity when dissolution date precedes incorporation date.
- **FR-013**: The service MUST reject a nationality when its validity end precedes its validity
  start.
- **FR-014**: Before persistence, the service MUST confirm that every supplied birth,
  incorporation, and nationality country code exists and is active in the Geographic Reference
  Service.
- **FR-015**: The service MUST fail creation without any state change when a supplied country is
  unknown or inactive or when geographic validation cannot complete.
- **FR-016**: Nationalities MAY be supplied only for a natural person and MUST belong to the same
  Party created by the command.
- **FR-017**: On the registration date, a nationality whose validity interval includes that date,
  with an omitted bound treated as open, is considered active.
- **FR-018**: The service MUST reject more than one active primary nationality and more than one
  active nationality for the same country within the new Party.
- **FR-019**: An accepted Party MUST start with record status `DRAFT` and aggregate version `0`.
- **FR-020**: The service MUST record creation and update audit timestamps and the same trusted
  audit subject on every mutable record created by the command.
- **FR-021**: The Party root, exactly one matching details record, all applicable nationalities,
  and any enabled creation event MUST be committed atomically.
- **FR-022**: When Party creation events are disabled or the creation event type is excluded, the
  service MUST create the Party without an outbox event.
- **FR-023**: When the Party creation event type is enabled, the service MUST create exactly one
  tenant-scoped outbox event with status `PENDING`, aggregate type `PARTY`, the new `partyId`, and
  aggregate version `0` in the same atomic operation as the Party.
- **FR-024**: The creation event payload MUST contain only the minimum approved integration data
  and MUST NOT contain unnecessary personal data or complete official identifiers.
- **FR-025**: Any validation, dependency, or persistence failure MUST leave no Party root, details,
  nationalities, or event created by the failed command.
- **FR-026**: A successful result MUST contain the generated `partyId` and aggregate version `0`.
- **FR-027**: The service MUST produce one consistent error category for each of these outcomes:
  invalid context, invalid Party data, invalid geographic reference, dependency unavailable, and
  creation failure. Transport-specific mappings require the approved API contract.
- **FR-028**: A natural-person creation command MUST contain no more than 10 nationalities; a
  larger collection MUST be rejected as invalid Party data before geographic validation.

### Key Entities

- **Party**: Tenant-owned aggregate root with a generated identifier, immutable Party type,
  display name, lifecycle status, audit data, and optimistic-concurrency version.
- **Natural Person Details**: One-to-one Party details containing names and optional birth and
  death information; valid only for a natural-person Party.
- **Legal Entity Details**: One-to-one Party details containing legal identity and incorporation
  information; valid only for a legal-entity Party.
- **Party Nationality**: A country association for a natural-person Party, with primary status,
  optional validity bounds, and audit data.
- **Country Reference**: An externally owned country identified by an ISO alpha-2 code whose
  existence and active status must be confirmed before Party creation.
- **Party Creation Event**: Optional tenant-scoped integration record for the new Party aggregate,
  initially pending publication and committed atomically with the Party.
- **Tenant Context**: Trusted ownership context supplied independently from Party data.
- **Audit Subject**: Trusted opaque identifier for the authenticated person or service principal
  responsible for creation.

### Scope Boundaries

- This feature creates Parties only; updates, status changes, identifier registration, searches,
  event publication, and event retry processing are outside scope.
- This feature does not create or manage tenants, user accounts, authentication, authorization,
  customers, suppliers, employees, addresses, contacts, or geographic reference data.
- This feature records an enabled outbox event but does not publish it.
- Official Party identifiers are outside this creation feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every acceptance scenario in this specification, including all 14 minimum scenarios
  supplied for Party registration, has a repeatable pass/fail outcome with no unresolved partial
  result.
- **SC-002**: 100% of accepted creation commands produce exactly one Party root, exactly one
  type-compatible details record, the requested valid nationalities, and a result containing the
  same Party identifier and version `0`.
- **SC-003**: 100% of commands rejected for context, data, country, dependency, or persistence
  failures leave zero Party and outbox records attributable to that command.
- **SC-004**: Across tests using at least two tenants, 100% of created Parties and enabled events
  are associated only with the trusted tenant that submitted each command.
- **SC-005**: When events are disabled, zero creation events are recorded; when the creation event
  is enabled, exactly one `PENDING` event is recorded atomically for every accepted Party.
- **SC-006**: Every accepted Party record and related mutable record can be traced to one tenant,
  one opaque audit subject, and one creation time without exposing unnecessary personal data.
- **SC-007**: Internal callers receive an unambiguous success result or a consistent failure
  category for every attempted creation, with no outcome that reports success before the atomic
  operation completes.

## Assumptions

- The registration date used to evaluate active nationality intervals is the service's trusted
  business date at command processing time; an omitted start or end is an open bound.
- Country codes use the ISO 3166-1 alpha-2 representation established by the approved data model.
- The Geographic Reference Service is the authority for country existence and active status.
- Event behavior follows the effective event mode and enabled-event-type configuration for the
  command; publication is handled separately.
- A generated Party identifier is globally opaque, while tenant ownership remains mandatory for
  every access decision.
- The exact internal route and transport status mapping must be defined by the approved API
  contract during planning without changing the behavior in this specification.

## Dependencies

- Approved Party Registry database contract in `docs/database/v1-scheme.dbml`.
- Approved LikeC4 `createPartySequence` and `partyRegistryOverview` views.
- Geographic Reference Service country lookup and active-status semantics.
- Trusted `Tenant-Id` and `User-Id` request headers supplied to the internal service.
- Effective configuration for Party creation events.
