## Why

The approved Party Registry API declares five nationality operations, but they are not yet available and their contract leaves important validation and conflict behavior unspecified. Consumers cannot reliably register, review, end, or designate a Party's nationality through the API. Completing this contract makes nationality history and primary status usable without allowing ambiguous or inconsistent records.

## What Changes

- Deliver the declared create, list, get, validity-update, and set-primary operations under `/v1/parties/{partyId}/nationalities`, with tenant-scoped access to both the Party and its nationality records.
- Validate request context, identifiers, required and optional idempotency keys, JSON bodies, country-code normalization and recognition, dates, list filters, limits, and cursors. Define predictable errors for malformed input, absent or concealed records, conflicting writes, invalid business states, and unavailable country validation.
- Enforce coherent inclusive validity periods, prevent overlapping periods for the same Party and country, and prevent overlapping primary periods for a Party. Define when a nationality is eligible to be made primary and the observable effect of changing the primary designation.
- Return the declared nationality fields and audit timestamps, paginated list metadata, matching HTTP/body statuses, stable error codes, and accepted `Process-Id` correlation. Clarify filtering by country, primary status, effective date, and expired-record inclusion.
- Complete the approved OpenAPI descriptions and examples for these operations, including the currently incomplete nationality-specific validation, conflict, and dependency-failure outcomes.

## User Stories

- As a registry operator, I want to register a Party's nationality with a defined validity period, so that its history is accurate and duplicate periods are rejected.
- As an API consumer, I want to list and retrieve a Party's nationalities with predictable filters and pagination, so that I can distinguish current and historical records.
- As a data steward, I want to correct or end a nationality's validity and designate a valid primary nationality, so that the Party's nationality information remains consistent over time.
- As an API consumer, I want invalid input, conflicts, and retries to have stable outcomes, so that I can safely correct requests without creating duplicate or contradictory records.

## Capabilities

### New Capabilities

- `party-nationalities`: Tenant-scoped nationality creation, retrieval, filtered listing, validity maintenance, and primary designation, including validation, temporal consistency, idempotency, audit results, and public response/error behavior.

### Modified Capabilities

None. Existing Party registration, root queries, and lifecycle behavior remain as specified.

## Impact

- Consumers of the five declared nationality endpoints gain an operational contract and predictable success, validation, and failure responses.
- `docs/contracts/party-registry.openapi.yaml` requires completed nationality semantics and response documentation; existing Party responses continue to exclude nationality collections where specified.
- The Geographic Reference Service participates in recognition of newly submitted nationality country codes; unavailable validation has a defined public outcome.
- Persisted nationality history and primary designation must remain consistent across concurrent operations and other Party updates.

## Out of Scope

- Physical deletion of nationalities, additional nationality routes, or changing a Party's type or lifecycle status.
- Managing the country catalog, changing existing Party registration or identifier operations, or adding nationalities to root Party response bodies.
- Historical data backfills or introducing new version-precondition headers not declared for nationality operations.
