## Why

The approved OpenAPI declares eight operations under `party-identifiers`, but the service currently exposes only additional identifier registration. Consumers cannot use the declared standalone listing, retrieval, metadata correction, verification, rejection, revocation, or primary-selection routes. This leaves the independent identifier maintenance workflow incomplete.

Completing this contract allows registry operators to maintain official identity evidence without recreating a Party, exposing complete identifier values, or overwriting concurrent changes. Explicit lifecycle and primary-selection requirements will make accepted changes, rejected operations, and retries predictable.

## What Changes

- Complete contract coverage for all eight operations:
  - `POST /v1/parties/{partyId}/identifiers`: preserve additional registration and complete its contract coverage, including required idempotency, scheme eligibility, uniqueness, optional expiration, and protected responses.
  - `GET /v1/parties/{partyId}/identifiers`: list identifiers using the declared scheme ID, scheme code, status, primary flag, cursor, and limit filters, with deterministic pagination and the declared collection metadata.
  - `GET /v1/parties/{partyId}/identifiers/{identifierId}`: retrieve the identifier's masked representation, metadata, status, independent version, and audit timestamps.
  - `PATCH /v1/parties/{partyId}/identifiers/{identifierId}`: partially update issuer and validity metadata, distinguishing omitted fields from explicit nulls and preserving identifier identity and protected value.
  - `POST /v1/parties/{partyId}/identifiers/{identifierId}/verify`: record the operator's confirmation of eligible identity evidence as `VERIFIED`, using the accepted `User-Id` as the verification actor and the operation clock for its timestamp, without calling an external verification provider.
  - `POST /v1/parties/{partyId}/identifiers/{identifierId}/reject`: reject an identifier according to the approved transition rules and optional reason input.
  - `POST /v1/parties/{partyId}/identifiers/{identifierId}/revoke`: revoke an identifier according to the approved transition rules and optional reason input.
  - `POST /v1/parties/{partyId}/identifiers/{identifierId}/set-primary`: select a `VERIFIED`, non-expired primary identifier within its tenant, Party, and identifier scheme. Permit distinct primaries for different schemes and honor the declared `replaceCurrentPrimary` option atomically within the same scope.
- Specify allowed transitions, expiration boundaries, verification-time handling of existing primary flags, metadata update eligibility, optional action idempotency, and validation precedence before implementation. Resolve business decisions that the current OpenAPI leaves implicit instead of deriving them solely from storage constraints.
- Enforce tenant and Party ownership, required request context, accepted `Process-Id` echoes, and identifier-version preconditions. Preserve Party identity, type, details, and independently managed Party versions.
- Return the approved success envelopes with matching HTTP/body status, masked identifier data, stable declared failure codes, and sanitized unexpected errors. Never disclose plaintext, ciphertext, lookup hashes, encryption key versions, or internal exception details.
- Make concurrency failures and rejected operations leave no partial identifier, primary-selection, replay, or enabled-event changes. Preserve registration, natural-person identifier projections, and Party activation evidence compatibility.
- Complete the affected OpenAPI semantics, examples, and operational documentation so that the published contract and implemented behavior agree.
- Apply the confirmed optional issuing-entity code rule consistently to initial/additional registration and metadata correction: up to 64 Unicode code points, ASCII letter groups with single underscore separators, uppercase new writes, bounded blank input as null, and no Geographic Reference lookup. Preserve historical stored issuer values and original raw-input fingerprint semantics.

## User Stories

- As a registry operator, I want to register and query my Party's official identifiers, so that I can manage its identity evidence without exposing sensitive document values.
- As a data steward, I want to correct identifier metadata without replacing its identity or value, so that descriptive and validity information stays accurate.
- As a registry operator, I want explicit verification, rejection, and revocation actions, so that identity evidence reflects an approved lifecycle.
- As an API consumer, I want controlled primary selection, version protection, and predictable retries, so that competing requests cannot silently overwrite accepted changes.

## Capabilities

### New Capabilities

- `party-identifier-queries`: Tenant- and Party-scoped masked listing and retrieval, declared filters, deterministic pagination, input validation, and safe public responses.
- `party-identifier-maintenance`: Additional registration contract coverage, partial metadata correction, and atomic primary selection, including uniqueness, version preconditions, audit outcomes, and preservation of protected identity values.
- `party-identifier-lifecycle`: Verification, rejection, and revocation with explicit transition and expiration rules, verification metadata, optional reasons, concurrency protection, and optional action replay behavior.

### Modified Capabilities

- `party-registration`: add the same confirmed optional initial-identifier issuer validation/canonicalization rule. Party identity, lifecycle, subtype data, protected values, and historical results remain compatibility constraints.

## Impact

- Consumers gain seven missing operations and complete coverage of the existing registration route. `docs/contracts/party-registry.openapi.yaml` remains the wire-contract authority; additional behavioral decisions must be reflected there and in the corresponding delta specifications.
- Identity-evidence consumers, including Party activation and current natural-person identifier projections, must continue to receive compatible masked data and eligibility outcomes.
- Delivery must follow TDD with observable red, green, and refactor steps for meaningful behavior, including negative cases and concurrency at the appropriate test boundaries. Test evidence must trace back to the specified requirements and scenarios.
- Strict Clean Architecture, inward dependencies, framework-independent domain rules, end-to-end reactive processing, native compatibility, and Flyway-only immutable database migrations are mandatory constraints for the subsequent design and tasks.
- Every new or modified date-based test must express months with `java.time.Month` constants, such as `Month.JANUARY`, rather than numeric month literals. Use controlled evaluation dates and clocks for time-dependent behavior.
- Before production or test implementation begins, inspect the effective default Java rules and parameters of SonarQube for IDE in Antigravity, using the user-confirmed standalone workflow. Record the installed analyzer version, active local rule configuration, and baseline; the historical 5.7.0/571-Java-rule evidence must be reconfirmed for this change. Review relevant rules before writing code and keep local analysis evidence distinct from SonarQube Server profiles and remote Quality Gates.
- Target zero unresolved findings from active applicable Sonar rules in changed Java files and zero new compiler or JDTLS/LSP warnings, without unjustified suppressions or rule exclusions. Record actual analysis completion for the saved production and test revisions separately from compilation. Required verification includes architecture and HTTP contract checks, applicable native integration coverage, `./gradlew test`, and `./gradlew build`.

## Out of Scope

- New identifier search, plaintext retrieval, bulk operations, physical deletion, or identifier lifecycle routes beyond those shown in the approved contract.
- Changes to Party registration requirements beyond the confirmed issuer metadata rule, or to Party lifecycle rules, subtype details, nationalities, or identifier-scheme management beyond necessary compatibility coverage.
- External verification-provider integration or a new scheduled expiration workflow.
- Rewriting historical protected values, creation snapshots, or accepted replay results.
- Repository-wide architectural restructuring or remediation of unrelated pre-existing quality findings.
