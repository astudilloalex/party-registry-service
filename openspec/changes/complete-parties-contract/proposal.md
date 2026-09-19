## Why

The approved OpenAPI declares six root Party operations, but the service currently exposes only activation. Consumers cannot list Parties, retrieve their common representation, correct their display names, deactivate them, or archive them through the declared API. This leaves the tenant-scoped identity maintenance workflow incomplete.

Completing this contract gives consumers predictable validation, lifecycle outcomes, pagination, and protection against concurrent overwrites. The existing activation specification also needs alignment with the cause-specific error codes already approved in OpenAPI.

## What Changes

- Complete the six operations under the `parties` tag:
  - `GET /v1/parties`: list tenant-owned Party summaries using the declared type, record-status, display-name prefix/contains, creation-date, cursor, and limit filters. Define deterministic pagination and return the declared pagination fields, including empty-result behavior.
  - `GET /v1/parties/{partyId}`: retrieve the Party identity, display name, status, version, audit information, and details corresponding to its immutable type.
  - `PATCH /v1/parties/{partyId}`: update the required `displayName`, applying the existing trimming, locale-independent uppercase, nonblank, and normalized-length rules. Preserve identity, type, lifecycle status, and type-specific details.
  - `POST /v1/parties/{partyId}/activate`: complete contract coverage around the existing activation behavior, preserving its qualifying verified, non-expired, type-compatible identifier requirement.
  - `POST /v1/parties/{partyId}/deactivate`: perform a permitted transition to `INACTIVE`.
  - `POST /v1/parties/{partyId}/archive`: perform a permitted transition to `ARCHIVED` without physically deleting identity records.
- Specify and enforce required context headers, canonical Party IDs, strictly typed request inputs, supported properties, enum values, text limits, coherent creation-date filters, valid cursors, and the declared limit range of 1–200 with a default of 50. Make validation precedence and applicable public codes explicit in the contract.
- Require the current Party version through `If-Match` for updates and lifecycle actions. Define permitted lifecycle transitions and the optional `Idempotency-Key` validation and retry behavior for lifecycle commands. Successful mutations increment the version once and update audit information; rejected or losing concurrent writes leave no partial changes or enabled events.
- Apply tenant isolation to every operation, conceal absent and cross-tenant Parties consistently, and echo an accepted `Process-Id` unchanged, including on error responses.
- Return the approved success envelopes with matching HTTP/body status, stable cause-specific errors containing only `status` and `code`, and sanitized unexpected failures. Align the activation specification with `party-not-found`, `stale-party-version`, `invalid-party-lifecycle`, and `missing-qualifying-identifier`, retaining their approved HTTP statuses.
- Complete the six operations' OpenAPI semantics, examples, and service documentation so that validation, response shapes, and implemented availability agree.

## User Stories

- As a registry operator, I want to filter and page through my tenant's Parties, so that I can locate records reliably.
- As an API consumer, I want to retrieve a Party without choosing a type-specific route, so that I can inspect its current details and version.
- As a data steward, I want to correct a Party's display name without changing its identity or legal details, so that the registry presents an appropriate label.
- As a registry operator, I want validated activation, deactivation, and archival actions, so that lifecycle changes preserve identity history and business invariants.
- As an API consumer, I want invalid input, stale versions, and forbidden transitions rejected predictably, so that retries and corrections do not overwrite accepted changes.

## Capabilities

### New Capabilities

- `party-queries`: Tenant-scoped listing and common detail retrieval, including filters, deterministic pagination, type-appropriate representations, validation, and public response behavior.
- `party-basic-update`: Display-name maintenance with canonical text validation, version preconditions, audit outcomes, and preservation of other Party information.
- `party-deactivation-and-archival`: Validated deactivation and archival, including permitted transitions, concurrency, optional idempotency behavior, retention, and atomic observable outcomes.

### Modified Capabilities

- `party-activation`: Align documented errors with the approved cause-specific codes and complete activation's request and retry contract while preserving verified-identifier eligibility, tenant isolation, concurrency protection, and atomic outcomes.

## Impact

- Consumers of the Party Registry gain the five missing root operations and a consistent contract across all six operations. `docs/contracts/party-registry.openapi.yaml` remains the API source of truth; the existing `party-activation` specification must be reconciled with it.
- Registry maintenance and enabled lifecycle-event consumers gain version-consistent outcomes without changes to identifier confidentiality or independent identifier lifecycles.
- Delivery must preserve strict Clean Architecture, inward dependencies, domain-owned business invariants, end-to-end reactive processing, and native compatibility. These are mandatory constraints for the subsequent design and tasks.
- New or modified date-based tests must use `java.time.Month` constants such as `Month.SEPTEMBER` and `Month.JANUARY` rather than numeric month literals.
- Every new or modified Java class must be checked with SonarQube and JDTLS/LSP diagnostics. Delivery requires zero unresolved SonarQube findings in those classes and zero new compiler or IDE warnings, without unjustified suppressions. Verification evidence must include contract tests, architecture checks, applicable native integration coverage, `./gradlew test`, and `./gradlew build`.

## Out of Scope

- New Party creation routes, changes to registration requirements, or rewriting stored creation/idempotency snapshots.
- Editing natural-person or legal-entity details through the root PATCH, changing Party type, or exposing additional identifier collections through the root detail contract.
- Identifier search, registration, verification, or other identifier lifecycle operations; nationality and identifier-scheme management.
- Physical deletion, bulk operations, historical normalization/backfills, or new lifecycle actions beyond the three declared routes.
- Repository-wide architectural restructuring or remediation of unrelated pre-existing quality findings.
