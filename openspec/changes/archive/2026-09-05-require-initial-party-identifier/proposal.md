## Why

The Party Registry currently permits a natural person, and contractually permits a planned legal entity, to be created without an official identifier. This allows incomplete identity records, delays duplicate detection, and leaves no guarantee that a draft Party will ever acquire a verified identity.

Party creation must establish a minimum trustworthy identity while preserving the independent lifecycle of each official identifier. The approved OpenAPI contract now requires one initial identifier, so the behavioral requirements must define consistent creation, validation, idempotency, confidentiality, uniqueness, and activation outcomes for API consumers.

## What Changes

- Require exactly one initial official identifier when creating a natural person or legal entity.
- Validate that the identifier scheme is known, active, and compatible with the Party type, and that the submitted identifier satisfies the scheme and validity rules.
- Create the Party, its type-specific details, the initial identifier, the idempotent result, and any enabled integration events as one all-or-nothing registration outcome.
- Start every new Party in `DRAFT` and its initial identifier in `PENDING_VERIFICATION`.
- Prevent concurrent or repeated registration from making duplicate Parties or active identifiers observable within the same tenant and scheme.
- Include the initial identifier in creation idempotency comparison while ensuring persisted replay data and public outputs do not expose its complete value.
- Return the created Party and a safe masked representation of its initial identifier in the `201 successful` response.
- Permit activation only when the Party has at least one valid `VERIFIED` identifier compatible with its type.
- Preserve post-creation registration of additional identifiers through `POST /v1/parties/{partyId}/identifiers`.
- Return stable `400 bad-request`, `409 conflict`, `422 unprocessable-entity`, and `503 dependency-unavailable` outcomes for the applicable failure categories.

## User Stories

- As a registry operator, I want every newly registered Party to include an official identifier, so that incomplete identity records are not created.
- As an API consumer, I want registration retries and concurrent requests to produce one deterministic Party and identifier result, so that transport failures do not create duplicates.
- As a data steward, I want identifier scheme compatibility, uniqueness, and verification to control Party activation, so that only sufficiently identified Parties become active.
- As a privacy stakeholder, I want complete identifier values excluded from responses, events, logs, and replay data, so that sensitive identity data remains protected.

## Capabilities

### New Capabilities

- `party-registration`: Register natural-person and legal-entity Parties with one required initial official identifier, deterministic idempotency, tenant-scoped uniqueness, safe responses, and all-or-nothing outcomes.
- `party-activation`: Control Party activation using optimistic concurrency and require at least one qualifying verified official identifier.

### Modified Capabilities

None.

## Impact

The change is a breaking request-contract change for consumers of `POST /v1/natural-person` and the planned `POST /v1/legal-entity`, because requests without `initialIdentifier` will be rejected. Their creation responses also gain a required masked `initialIdentifier` result.

Party registration, identifier-scheme validation, identifier uniqueness, idempotent replay, Party activation, and integration-event data are affected. Existing clients must supply a supported identifier scheme and complete value while continuing to consume only protected identifier data from responses.

## Out of Scope

- Combining Party and PartyIdentifier into one domain concept or lifecycle.
- Accepting multiple initial identifiers in a single Party creation request.
- Hardcoding country-specific properties such as `cedula`, `passport`, or `RUC` into Party request models.
- Automatically verifying an identifier or integrating with an external document-verification authority.
- Changing the endpoint used to register additional identifiers after Party creation.
- Adding unrelated customer, supplier, employee, account, address, contact, authorization, nationality, search, or Party-role behavior.
- Automatically backfilling or deleting pre-existing Parties that have no official identifier.
