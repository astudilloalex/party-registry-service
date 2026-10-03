## Why

The approved OpenAPI declares eight identifier-scheme operations, but the service does not expose an identifier-scheme REST resource. Existing domain and persistence support resolves catalog entries for Party identifier registration rather than providing the documented catalog-management workflow. Consumers therefore cannot reliably create, retrieve, maintain, or manage the lifecycle of schemes through the published API.

Completing this resource closes the gap between documentation and runtime behavior. Registry operators gain a predictable catalog-management interface, while API consumers receive explicit validation, lifecycle, concurrency, and absence outcomes instead of partially implemented operations or unexpected internal-server errors.

## What Changes

- Deliver all eight declared operations:
  - `POST /v1/identifier-schemes`: create a valid scheme in its initial `DRAFT` state and return `201 successful` with its representation.
  - `GET /v1/identifier-schemes`: list available schemes, including the declared country, category, applicable-subject-type, and lifecycle-status filters, cursor pagination, and empty-result behavior.
  - `GET /v1/identifier-schemes/by-code/{code}`: retrieve a scheme by its stable catalog code.
  - `GET /v1/identifier-schemes/{schemeId}`: retrieve a scheme by its UUID.
  - `PATCH /v1/identifier-schemes/{schemeId}`: update only contract-permitted fields, preserving omitted values and defining explicit-null and empty-patch behavior.
  - `POST /v1/identifier-schemes/{schemeId}/activate`: activate a scheme when its current state and configuration permit it.
  - `POST /v1/identifier-schemes/{schemeId}/deprecate`: deprecate a scheme through an allowed lifecycle transition.
  - `POST /v1/identifier-schemes/{schemeId}/retire`: retire a scheme through an allowed lifecycle transition.
- Complete the approved OpenAPI with required and optional request properties, response representations, enum values, field constraints, path and query parameters, headers, success/error statuses, and representative request/response examples. Swagger documentation must describe the behavior actually delivered by each operation.
- Make the mutable-field allowlist explicit: `name`, `description`, `normalizerKey`, `validatorKey`, `minimumLength`, `maximumLength`, and legacy `requiresExpiration`. Scheme identity, code, issuing country, category, applicable subject type, lifecycle status, version, and audit fields cannot be changed through PATCH.
- Define and enforce scheme uniqueness, required text, length limits, issuing-country format, supported processing-rule configuration, coherent minimum/maximum lengths, and state-dependent maintenance rules. Preserve the existing rule that document expiration remains optional regardless of legacy `requiresExpiration` metadata.
- Specify the allowed and rejected lifecycle transitions among `DRAFT`, `ACTIVE`, `DEPRECATED`, and `RETIRED`, including repeated-action behavior and effects on future identifier eligibility. Rejected changes must leave persisted state, version, and audit information unchanged.
- Preserve the declared context headers, required creation idempotency, optional lifecycle idempotency, and `If-Match` concurrency preconditions. Document replay behavior, code-uniqueness conflicts, stale versions, and accepted process correlation consistently with the approved contract.
- Return standard success envelopes containing `status`, `code`, and `data`, with the declared pagination fields for listing. Errors contain only `status` and a stable `code`; HTTP and body statuses must match. Missing resources return documented `404` responses, invalid transitions return documented conflicts, and unexpected failures remain sanitized as `500 server-error`.
- Complete missing backend behavior and automated verification so OpenAPI, REST delivery, application operations, domain rules, and persistence agree for every in-scope operation. Cover successful operations, invalid input, forbidden updates, lifecycle rejection, unknown IDs/codes, concurrency, idempotency, and framework-level error responses.

## User Stories

- As a registry operator, I want to create identifier schemes with validated configuration, so that the catalog can support approved identification documents.
- As an API consumer, I want to list and retrieve schemes by code or ID, so that I can discover their current configuration, lifecycle status, and version.
- As a data steward, I want to update permitted scheme fields without overwriting omitted values or immutable identity, so that catalog maintenance is controlled and predictable.
- As a registry operator, I want to activate, deprecate, and retire schemes through valid transitions, so that their availability follows the intended domain lifecycle.
- As an API consumer, I want complete documentation and stable validation, not-found, conflict, and concurrency responses, so that integrations can handle failures reliably.

## Capabilities

### New Capabilities

- `identifier-scheme-management`: Creation, filtered and paginated listing, lookup by code or ID, and partial maintenance of identifier schemes, including field validation, immutable-field protection, uniqueness, context headers, concurrency, idempotency, and public response behavior.
- `identifier-scheme-lifecycle`: Activation, deprecation, and retirement of identifier schemes, including the transition matrix, configuration eligibility, repeated actions, version/audit outcomes, replay, and rejection without mutation.

### Modified Capabilities

None. There is no existing identifier-scheme capability under `openspec/specs/`. Existing Party registration and identifier workflows require regression coverage for catalog changes, but their established behavioral requirements are not being expanded by this proposal.

## Impact

- Consumers of `docs/contracts/party-registry.openapi.yaml` and `/q/openapi` gain a complete, executable contract for all eight identifier-scheme operations.
- Catalog administration affects which scheme configurations and lifecycle states subsequent Party identifier operations observe. Existing catalog identity, historical identifier references, and optional-expiration behavior must remain compatible.
- Catalog documentation must distinguish API-managed catalog operations from migration-controlled schema changes and reference-data provisioning.
- Strict Clean Architecture is a mandatory delivery constraint: domain rules remain framework- and transport-independent, application orchestration depends inward, and REST/persistence concerns remain at their boundaries. Apply the relevant global Clean Architecture and Quarkus reactive steerings before every programming activity, and preserve the project's end-to-end reactive and native-compatible baseline.
- Every implementation increment must begin with a failing behavioral test and follow the TDD red-green-refactor cycle. Completion requires automated coverage at the appropriate domain, application, API-contract, and persistence boundaries, maintained native integration coverage, architectural validation, clean modified-file diagnostics, and successful `./gradlew test` and `./gradlew build` checks.

## Out of Scope

- New routes beyond the eight declared identifier-scheme operations, including deletion, bulk administration, and additional lifecycle actions.
- Changes to Party lifecycle workflows, nationality management, legal-entity or natural-person detail maintenance, or identifier verification workflows.
- New jurisdiction-specific catalogs, new normalizer/validator algorithms, rewriting historical identifiers, or making document expiration mandatory.
- Unrelated architectural refactoring, blocking request processing, manual schema changes, or editing applied Flyway migrations.
