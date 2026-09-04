## 1. Contract and Test Foundation

- [ ] 1.1 Extend `OpenApiContractTest` to pin the required `initialIdentifier` inputs, create-only natural-person and legal-entity response schemas, protected identifier fields, activation semantics, and every declared error response.
  - Requirements: Initial official identifier is mandatory; Party creation success response includes the protected identifier; Activation optimistic concurrency
  - Verification: `./gradlew test --tests com.alexastudillo.partyregistry.OpenApiContractTest` passes and fails if the required identifier or safe response contract is removed.

- [ ] 1.2 Add controlled test fixtures for active, inactive, natural-person-only, legal-entity-only, and expiration-required identifier schemes without adding production reference data.
  - Requirements: Initial identifier scheme eligibility; Initial identifier semantic validity; Activation requires a qualifying verified identifier
  - Verification: Repository and HTTP tests can select each scheme case deterministically, while the production Flyway migrations contain no invented scheme values.

- [ ] 1.3 Add explicit test-profile cryptographic configuration and production secret-backed configuration keys for the encryption key ring, current encryption key version, identifier-index HMAC key, and idempotency HMAC key.
  - Requirements: Initial identifier confidentiality; Tenant-scoped initial identifier uniqueness; Idempotent Party registration includes the initial identifier
  - Verification: Tests start with controlled keys, production configuration has no key defaults, and startup rejects missing, malformed, or incorrectly sized key material.

- [ ] 1.4 Add only the BOM-managed Quarkus messaging and scheduling dependencies required by the designed RabbitMQ outbox publisher, preserving the existing reactive stack and native-image compatibility.
  - Requirements: Party and initial identifier creation is all-or-nothing; Activation has one observable outcome
  - Verification: Gradle dependency resolution succeeds without RESTEasy Classic, blocking ORM, or independently versioned Quarkus artifacts.

## 2. Domain Model and Policies

- [ ] 2.1 Implement the framework-independent `LegalEntity` aggregate and legal-entity detail value objects with immutable `LEGAL_ENTITY` type, initial `DRAFT` status, version `0`, audit data, and type-specific invariants.
  - Requirements: Party and initial identifier creation is all-or-nothing
  - Verification: Domain unit tests cover valid creation, every invariant failure, and the absence of natural-person details.

- [ ] 2.2 Implement framework-independent identifier-scheme types and policies for stable identity, lifecycle status, subject compatibility, normalized length limits, expiration requirements, and rule versioning.
  - Requirements: Initial identifier scheme eligibility; Initial identifier semantic validity
  - Verification: Domain unit tests accept `ACTIVE` compatible schemes and reject unknown-equivalent, inactive, incompatible, and invalid-length cases with typed domain violations.

- [ ] 2.3 Implement the explicit versioned identifier normalizer and validator catalog as deterministic pure strategies, with no reflection, executable expressions, framework APIs, or external I/O.
  - Requirements: Initial identifier semantic validity
  - Verification: Unit tests cover valid, invalid, and boundary values for every supported normalizer and validator key and treat unsupported active-catalog keys as internal invariant failures.

- [ ] 2.4 Implement the independent `PartyIdentifier` aggregate and protected-value types with initial `PENDING_VERIFICATION` status, version `0`, validity metadata, default `isPrimary=false`, and no plaintext or normalized plaintext fields.
  - Requirements: Party and initial identifier creation is all-or-nothing; Initial identifier confidentiality; Additional identifiers remain independently registrable
  - Verification: Domain unit tests cover construction, date coherence, status/version defaults, primary-flag behavior, and prove Party aggregates do not own identifier collections.

- [ ] 2.5 Implement the Party lifecycle transition and activation eligibility policy for a `DRAFT` Party with at least one compatible, `VERIFIED`, non-expired identifier, without requiring the scheme to remain active or the identifier to be primary.
  - Requirements: Activation requires a qualifying verified identifier; Party activation transition
  - Verification: Domain unit tests cover qualifying, pending, expired, incompatible, deprecated-scheme, non-primary, and invalid-current-state cases and increment the Party version exactly once on success.

- [ ] 2.6 Extend the architecture rules to reject Mutiny, Quarkus, Hibernate, Jackson, HTTP, and cryptographic-provider dependencies from Domain while retaining the existing package and cycle checks.
  - Requirements: Initial identifier confidentiality; Party and initial identifier creation is all-or-nothing
  - Verification: `ArchitectureRulesTest` passes with the new domain types and representative forbidden-dependency fixtures remain rejected.

## 3. Application Contracts and Security Adapters

- [ ] 3.1 Add transport-neutral initial-identifier inputs, natural-person and legal-entity registration commands, registration candidates, and composite safe results that contain Party data plus a masked identifier projection.
  - Requirements: Initial official identifier is mandatory; Party creation success response includes the protected identifier; Initial identifier confidentiality
  - Verification: Application model tests prove generated IDs and safe replay fields are represented while complete values, normalized values, ciphertext, fingerprints, and API envelope types are absent from results.

- [ ] 3.2 Generalize the natural-person-only idempotent creation contract into capability-oriented registration, scheme lookup, identifier protection, registration fingerprint, additional-identifier registration, activation, and outbox ports.
  - Requirements: Party and initial identifier creation is all-or-nothing; Idempotent Party registration includes the initial identifier; Additional identifiers remain independently registrable; Activation has one observable outcome
  - Verification: Application compiles against only Domain and Mutiny contracts, and no port exposes Hibernate, SQL, JCA provider, HTTP, or API response types.

- [ ] 3.3 Extend the sealed Domain/Application failure catalogs and every exhaustive switch with scheme, identifier validation, identifier conflict, Party absence, invalid lifecycle, stale version, dependency-unavailable, and internal catalog failures.
  - Requirements: Initial identifier scheme eligibility; Initial identifier semantic validity; Tenant-scoped initial identifier uniqueness; Activation optimistic concurrency; Activation is tenant-scoped
  - Verification: Failure-description and API translation unit tests cover every new sealed subtype without default branches that hide missing cases.

- [ ] 3.4 Replace the static unkeyed creation hash with an Infrastructure HMAC-SHA-256 registration-fingerprint adapter over the exact canonical effective request, using a dedicated key and constant-time comparison.
  - Requirements: Idempotent Party registration includes the initial identifier; Initial identifier confidentiality
  - Verification: Unit tests prove determinism, canonical escaping, operation and field sensitivity including exact identifier formatting, key separation, and constant-time comparison behavior without exposing canonical sensitive input.

- [ ] 3.5 Implement the native-compatible local JCA identifier-protection adapter using tenant-derived HMAC-SHA-256 lookup fingerprints, AES-256-GCM with random 96-bit nonces and the designed AAD, versioned key selection, and conservative masking.
  - Requirements: Initial identifier confidentiality; Tenant-scoped initial identifier uniqueness
  - Verification: Unit tests prove tenant separation, deterministic lookup fingerprints, non-deterministic ciphertext, successful round trips, AAD/tamper rejection, mask boundaries, and current plus retained encryption-key versions.

- [ ] 3.6 Add startup validation and sanitized failure translation for identifier-protection configuration, ensuring request processing performs no blocking secret lookup and no secret or identifier material reaches logs or exception messages.
  - Requirements: Initial identifier semantic validity; Initial identifier confidentiality
  - Verification: Configuration tests cover valid and invalid key rings, dependency failures map without sensitive text, and native reflection/resource configuration is present only where required.

## 4. Registration Use Cases

- [ ] 4.1 Refactor natural-person creation into identifier-required registration ordered as current-operation replay, legacy-key rejection, Party/country validation, scheme/rule validation, identifier protection, aggregate construction, and one atomic persistence call.
  - Requirements: Initial official identifier is mandatory; Initial identifier scheme eligibility; Initial identifier semantic validity; Party and initial identifier creation is all-or-nothing; Idempotent Party registration includes the initial identifier
  - Verification: `CreateNaturalPersonUseCaseTest` covers the exact call order, early replay during dependency failure, changed-input conflict, each validation/protection failure, and no persistence call after a failure.

- [ ] 4.2 Implement legal-entity registration with the same replay, eligibility, protection, and atomic-persistence semantics while constructing only legal-entity details.
  - Requirements: Initial official identifier is mandatory; Initial identifier scheme eligibility; Party and initial identifier creation is all-or-nothing; Party creation success response includes the protected identifier
  - Verification: Legal-entity use-case tests cover success, replay, incompatible scheme, semantic failure, conflict, and the absence of natural-person persistence candidates.

- [ ] 4.3 Implement additional PartyIdentifier registration using tenant-safe Party type lookup, shared scheme/rule validation, protection, and an independent identifier persistence port without recreating or reclassifying the Party.
  - Requirements: Additional identifiers remain independently registrable; Initial identifier scheme eligibility; Initial identifier semantic validity; Initial identifier confidentiality
  - Verification: Use-case tests cover natural and legal Parties, cross-tenant concealment, eligibility failures, duplicate identifiers, and unchanged Party identity/type/version.

- [ ] 4.4 Add registration observability at application and adapter boundaries with bounded outcomes and preserved `processId`, `userId`, and `tenantId` context, excluding identifier values, masks, scheme-specific values, fingerprints, ciphertext, and keys.
  - Requirements: Initial identifier confidentiality; Idempotent Party registration includes the initial identifier
  - Verification: Observability tests or captured-log assertions show context and bounded outcomes for created, replayed, validation-failed, conflict, and dependency-unavailable flows with no sensitive values.

## 5. Reactive Persistence and Idempotency

- [ ] 5.1 Add Hibernate Reactive mappings and mappers for legal-entity details, identifier schemes, PartyIdentifier, and outbox events, keeping PartyIdentifier outside `PartyEntity` cascades.
  - Requirements: Party and initial identifier creation is all-or-nothing; Additional identifiers remain independently registrable
  - Verification: Persistence mapping tests load and store each model, enforce one matching Party detail type, and confirm no Party-to-identifier aggregate ownership mapping exists.

- [ ] 5.2 Implement tenant-safe reactive scheme and Party lookup adapters, including the projections needed for compatibility checks and activation eligibility.
  - Requirements: Initial identifier scheme eligibility; Activation requires a qualifying verified identifier; Activation is tenant-scoped
  - Verification: Reactive persistence tests cover scheme lifecycle/subject projections, Party type lookup, and concealed cross-tenant or absent results.

- [ ] 5.3 Generalize idempotency persistence to store and decode safe registration snapshot schema version `2` while retaining version `1` natural-person rows and detecting legacy-key reuse without decoding legacy data as version `2`.
  - Requirements: Idempotent Party registration includes the initial identifier; Initial identifier confidentiality
  - Verification: Persistence tests replay exact version-two Party/identifier results, preserve version-one rows, return conflict for legacy operation-key reuse, and show no sensitive identifier material in snapshot JSON.

- [ ] 5.4 Replace the natural-person-only creation adapter with one Hibernate Reactive registration adapter that rechecks scheme identity/version/eligibility and atomically writes Party, matching details, idempotency outcome, PartyIdentifier, and enabled outbox rows.
  - Requirements: Party and initial identifier creation is all-or-nothing; Initial identifier scheme eligibility; Idempotent Party registration includes the initial identifier
  - Verification: Reactive integration tests prove successful natural and legal registration commits every required row and injected failures after each write stage leave no partial state.

- [ ] 5.5 Preserve deterministic same-key race recovery by flushing Party/details and the idempotency row before PartyIdentifier, rolling the loser back, and loading an equal-fingerprint winner in a new reactive session.
  - Requirements: Idempotent Party registration includes the initial identifier; Party and initial identifier creation is all-or-nothing
  - Verification: Concurrent reactive tests produce one Party and identifier for equivalent same-key requests, return the same safe result to each successful replay, and create no orphan rows or duplicate events.

- [ ] 5.6 Translate only the named active-identifier unique constraint into the typed identifier conflict while preserving cancellation and sanitizing all other persistence failures.
  - Requirements: Tenant-scoped initial identifier uniqueness
  - Verification: Reactive tests return one winner and one conflict for different-key concurrent duplicates, allow the same normalized value in different tenants, and do not misclassify unrelated database failures.

- [ ] 5.7 Implement independent reactive persistence for additional identifiers using the existing tenant/scheme active-value uniqueness rule and without mutating Party details or type.
  - Requirements: Additional identifiers remain independently registrable; Tenant-scoped initial identifier uniqueness
  - Verification: Persistence tests add multiple eligible identifiers, reject active duplicates, allow schema-permitted reuse after terminal statuses, and leave the Party aggregate unchanged.

- [ ] 5.8 Extend migration regression coverage for existing scheme, identifier, outbox, and idempotency structures without editing immutable `V1` or `V2` or expecting a `V3` unless an approved catalog is supplied.
  - Requirements: Party and initial identifier creation is all-or-nothing; Tenant-scoped initial identifier uniqueness
  - Verification: `MigrationRegressionTest` validates unchanged checksums and all relied-upon constraints/indexes against a fresh database.

## 6. Activation and Outbox Delivery

- [ ] 6.1 Implement `ActivatePartyUseCase` with UTC evaluation time and failure precedence of concealed Party absence, stale expected version, invalid lifecycle transition, then missing qualifying identifier.
  - Requirements: Activation requires a qualifying verified identifier; Party activation transition; Activation optimistic concurrency; Activation is tenant-scoped
  - Verification: Application tests cover every failure precedence combination, successful version increment, and no output-port call after an earlier failure.

- [ ] 6.2 Implement one tenant-qualified Hibernate Reactive activation transaction that locks or conditionally updates the expected Party version, evaluates qualifying identifiers, updates `DRAFT` to `ACTIVE`, and stores each enabled activation event.
  - Requirements: Activation requires a qualifying verified identifier; Party activation transition; Activation optimistic concurrency; Activation has one observable outcome
  - Verification: Reactive integration tests cover pending, expired, incompatible, absent, qualifying, and later-deprecated scheme cases plus complete rollback before commit.

- [ ] 6.3 Enforce activation concurrency so only one request using a current version changes state and every loser receives the typed stale-version failure without an activation event.
  - Requirements: Activation optimistic concurrency; Activation has one observable outcome
  - Verification: Concurrent reactive tests show exactly one version increment and enabled event, with every losing request translated to `412 precondition-failed`.

- [ ] 6.4 Implement minimal versioned `party.created.v1`, `party.identifier-created.v1`, and `party.activated.v1` outbox payloads and event-mode configuration for disabled, stored-only, and published behavior.
  - Requirements: Party and initial identifier creation is all-or-nothing; Activation has one observable outcome; Initial identifier confidentiality
  - Verification: Serialization and persistence tests prove enabled-event cardinality, stable aggregate/event identities, correct versions, and absence of complete, normalized, encrypted, fingerprint, or key data.

- [ ] 6.5 Implement the reactive RabbitMQ outbox publisher with bounded batches, at-least-once stable event IDs, finite timeout, retry backoff, maximum attempts, cancellation handling, and no effect on committed business outcomes.
  - Requirements: Party and initial identifier creation is all-or-nothing; Activation has one observable outcome
  - Verification: Publisher tests cover disabled/stored-only modes, successful publication, retryable failure, terminal failure, duplicate-delivery identity, and broker unavailability after business commit.

## 7. HTTP API and Error Contract

- [ ] 7.1 Add validated API request and create-only response DTOs plus mappers for initial identifiers, natural-person registration, and legal-entity registration, leaving natural-person GET, PUT, and PATCH response types unchanged.
  - Requirements: Initial official identifier is mandatory; Party creation success response includes the protected identifier; Initial identifier confidentiality
  - Verification: DTO/mapper tests require `@NotNull`/`@Valid` nested input, default optional values correctly, include all declared safe fields, and expose no sensitive persistence or protection fields.

- [ ] 7.2 Update `NaturalPersonResource.createNaturalPerson` to map the required initial identifier, delegate to the registration use case, and return `Uni<RestResponse<ApiResponse<NaturalPersonCreateResponse>>>` through `ResponseManager` with matching HTTP/body status `201`.
  - Requirements: Initial official identifier is mandatory; Party creation success response includes the protected identifier
  - Verification: Resource signature and HTTP contract tests prove create uses the new response while GET, PUT, and PATCH retain `NaturalPersonResponse`.

- [ ] 7.3 Implement `LegalEntityResource` create handling with the same required headers, idempotency, validation, shared envelope, process-ID echo, and reactive failure translation used by natural-person registration.
  - Requirements: Initial official identifier is mandatory; Party and initial identifier creation is all-or-nothing; Party creation success response includes the protected identifier
  - Verification: Resource signature and HTTP tests prove valid `201 successful` responses and standardized `400`, `409`, `422`, `500`, and `503` envelopes.

- [ ] 7.4 Implement `PartyIdentifierResource` for `POST /v1/parties/{partyId}/identifiers`, returning only the safe identifier DTO through the shared reactive response contract.
  - Requirements: Additional identifiers remain independently registrable; Initial identifier confidentiality
  - Verification: HTTP tests cover success, validation, tenant concealment, scheme/semantic rejection, duplicate conflict, dependency failure, and unchanged Party identity/type.

- [ ] 7.5 Implement `PartyResource` activation handling with exact `If-Match` cardinality and nonnegative decimal parsing, tenant/request context mapping, and `Uni<RestResponse<ApiResponse<PartyDetailResponse>>>` success wrapping.
  - Requirements: Activation requires a qualifying verified identifier; Party activation transition; Activation optimistic concurrency; Activation is tenant-scoped
  - Verification: HTTP tests cover missing, duplicate, malformed, stale, cross-tenant, invalid-state, ineligible, and successful activation with matching response statuses and process-ID behavior.

- [ ] 7.6 Add service-owned response codes and API translators for all known registration and activation failures without adding overlapping global exception mappers or a generic success filter.
  - Requirements: Initial identifier scheme eligibility; Initial identifier semantic validity; Tenant-scoped initial identifier uniqueness; Activation optimistic concurrency; Activation is tenant-scoped
  - Verification: Translator and global-error contract tests assert stable `400`, `404`, `409`, `412`, `422`, `500`, and `503` envelopes and sanitized unexpected failures.

## 8. End-to-End Verification

- [ ] 8.1 Expand natural-person and legal-entity HTTP contract tests to cover required/blank identifier input, scheme and semantic failures, atomic success, safe create responses, replay, changed-input conflict, identifier uniqueness, dependency failure, and rollback row counts.
  - Requirements: All party-registration requirements
  - Verification: The resource contract suites pass against PostgreSQL Dev Services and assert HTTP status equals `ApiResponse.status` for every success and failure.

- [ ] 8.2 Add HTTP and persistence confidentiality regression assertions proving complete and normalized identifiers do not appear in API bodies, error details, logs, idempotency JSON, or outbox JSON and exist in identifier storage only as authenticated ciphertext plus approved derived fields.
  - Requirements: Initial identifier confidentiality
  - Verification: Tests inspect serialized responses, captured logs, snapshots, events, and database columns and fail on any forbidden value or internal exception detail.

- [ ] 8.3 Extend packaged JVM integration coverage with controlled scheme/key fixtures and representative natural registration, legal registration, replay, duplicate conflict, additional identifier, and activation flows.
  - Requirements: All party-registration and party-activation requirements
  - Verification: `./gradlew quarkusIntTest` passes against the packaged application with all shared-envelope and process-ID assertions.

- [ ] 8.4 Extend native integration coverage for representative registration, replay, identifier protection, additional identifier, outbox serialization, and activation paths.
  - Requirements: Party and initial identifier creation is all-or-nothing; Initial identifier confidentiality; Activation has one observable outcome
  - Verification: The configured native integration-test task passes without missing reflection metadata, unavailable crypto algorithms, blocking request-path access, or serialization failures.

- [ ] 8.5 Review every added or modified Java type for concise English Javadoc, verify JDTLS diagnostics for each modified Java file, and confirm no new compiler, nullability, type-safety, or event-loop blocking warnings.
  - Requirements: All party-registration and party-activation requirements
  - Verification: Modified Java files have no unresolved LSP diagnostics or new warnings, and architecture plus blocked-thread checks pass.

- [ ] 8.6 Run the complete project verification and document the release prerequisites for secret provisioning and an independently approved production identifier-scheme catalog without adding manual DDL/DML or default secrets.
  - Requirements: Initial identifier scheme eligibility; Initial identifier confidentiality; Party and initial identifier creation is all-or-nothing
  - Verification: `./gradlew test` and `./gradlew build` pass, `git diff --check` passes, immutable migrations remain unchanged, and release documentation identifies both prerequisites.
