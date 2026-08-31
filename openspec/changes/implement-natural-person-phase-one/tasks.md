## 1. Foundation and Contract

- [x] 1.1 Align build dependencies with the reactive and shared-response design, retaining the published `api-response-quarkus-errors` module as the response/error boundary and removing any redundant direct response-module dependency.
  - Requirements: Standard API responses
  - Verification: `./gradlew dependencies` resolves one coherent shared-response dependency graph and `./gradlew compileJava` succeeds.

- [x] 1.2 Configure strict JSON deserialization, shared HTTP failure handling for `/v1/*`, and non-blocking Geographic Reference client URLs and finite timeouts without production fallbacks.
  - Requirements: Natural-person request validation, Birth-country validation, Standard API responses
  - Verification: Configuration tests prove unknown JSON properties are rejected, `/q` remains excluded from business-header handling, and production startup requires the Geographic Reference base URL.

- [x] 1.3 Clarify `docs/contracts/party-registry.openapi.yaml` for canonical `Process-Id`, nonblank `Idempotency-Key`, decimal `If-Match`, strict natural-person request properties, PUT/PATCH semantics, `NATURAL_PERSON` response typing, and required `412`, `422`, and `503` behavior.
  - Requirements: Trusted request context, Natural-person request validation, Birth-country validation, Optimistic concurrency, Standard API responses
  - Verification: The OpenAPI parser reports no errors and automated assertions confirm all four natural-person operations and clarified constraints.

- [x] 1.4 Add immutable Flyway migration `V2__create_api_idempotency_records.sql` with tenant/operation/key uniqueness, request hash, party reference, versioned application-result snapshot, audit fields, and named checks/constraints.
  - Requirements: Idempotent natural-person creation, Natural-person creation
  - Verification: Flyway applies `V1` and `V2` to a clean PostgreSQL instance, validates both migrations, and rejects duplicate tenant/operation/key rows and malformed hashes.

- [x] 1.5 Add migration regression tests proving `V1` remains unchanged, Hibernate schema generation stays disabled, and the existing party-type/detail triggers still reject incompatible or dual detail rows after `V2` is applied.
  - Requirements: Natural-person and legal-entity exclusivity
  - Verification: PostgreSQL integration tests pass for type immutability, incompatible details, dual details, and migration checksum validation.

## 2. Domain Model

- [x] 2.1 Implement framework-independent party and natural-person domain types, including identifiers, party type/status, version, audit values, details, and transport-neutral domain failures, with concise English Javadoc.
  - Requirements: Natural-person creation, Natural-person retrieval, Natural-person and legal-entity exclusivity
  - Verification: Domain sources compile without imports from Quarkus, Mutiny, Jakarta REST, Jackson, Hibernate, or the shared API-response modules.

- [x] 2.2 Implement natural-person creation invariants for immutable `NATURAL_PERSON` type, required names, display-name derivation, initial `DRAFT` status/version `0`, lifecycle dates, and exactly one natural-person detail representation.
  - Requirements: Natural-person request validation, Natural-person lifecycle date consistency, Natural-person creation, Natural-person and legal-entity exclusivity
  - Verification: Domain unit tests cover explicit/derived display names, blank names, future dates, death-before-birth, valid same-day dates, and fixed party type.

- [x] 2.3 Implement complete replacement behavior that clears omitted optional values, re-derives display name when names change, validates the complete resulting state, and preserves the original aggregate when validation fails.
  - Requirements: Complete natural-person replacement, Natural-person lifecycle date consistency
  - Verification: Domain unit tests prove complete replacement, optional-field clearing, display-name changes, and failure atomicity.

- [x] 2.4 Implement presence-aware patch behavior for absent, explicit-null, and supplied values, including required-name protection and validation against retained lifecycle values.
  - Requirements: Partial natural-person update, Natural-person lifecycle date consistency
  - Verification: Domain/application-value unit tests prove omitted fields remain unchanged, nullable fields clear, required names cannot clear, and mixed retained/changed dates are validated.

## 3. Application Layer

- [x] 3.1 Add application request metadata, create/get/replace/patch commands, presence-aware field updates, natural-person results, idempotency outcomes, and transport-neutral application failures.
  - Requirements: Trusted request context, Natural-person creation, Natural-person retrieval, Complete natural-person replacement, Partial natural-person update
  - Verification: Application contracts compile without REST, HTTP, Jackson, Hibernate, persistence-entity, or `ApiResponse` dependencies.

- [x] 3.2 Define Mutiny output ports for tenant-scoped natural-person persistence, atomic idempotent creation, and country-reference validation.
  - Requirements: Birth-country validation, Idempotent natural-person creation, Optimistic concurrency
  - Verification: Port signatures expose only domain/application types and `Uni`, with no concrete database or REST-client types.

- [x] 3.3 Implement canonical effective-create-command serialization and SHA-256 fingerprinting that treats equivalent omitted/null optional inputs consistently and excludes process/user correlation values.
  - Requirements: Idempotent natural-person creation
  - Verification: Unit tests prove deterministic hashes, equivalent request hashes, meaningful-field differences, and tenant/operation scoping behavior.

- [x] 3.4 Implement `CreateNaturalPersonUseCase` to validate a supplied country, construct the aggregate, and invoke atomic idempotent creation without manual subscription or blocking waits.
  - Requirements: Birth-country validation, Natural-person creation, Idempotent natural-person creation
  - Verification: Use-case tests cover no-country creation, recognized/unknown/unavailable country, original creation, replay, payload conflict, and propagated cancellation/failure.

- [x] 3.5 Implement `GetNaturalPersonUseCase` with tenant-scoped retrieval and a single not-found outcome for absent, cross-tenant, and wrong-type parties.
  - Requirements: Natural-person retrieval, Natural-person and legal-entity exclusivity
  - Verification: Use-case tests return an aggregate only for the requesting tenant's natural person and emit the same not-found failure for all concealed cases.

- [x] 3.6 Implement `ReplaceNaturalPersonUseCase` to load current state, apply complete replacement, validate a changed country, and persist with the expected version.
  - Requirements: Complete natural-person replacement, Birth-country validation, Optimistic concurrency
  - Verification: Use-case tests cover success, optional clearing, not-found, country failures, stale version, and no persistence call after validation failure.

- [x] 3.7 Implement `PatchNaturalPersonUseCase` to load current state, apply only present fields, validate resulting dates and a changed non-null country, and persist with the expected version.
  - Requirements: Partial natural-person update, Natural-person lifecycle date consistency, Birth-country validation, Optimistic concurrency
  - Verification: Use-case tests cover absent/null/value fields, empty patch rejection, retained values, country clearing/change, not-found, and stale version.

## 4. Reactive Persistence

- [x] 4.1 Implement Hibernate Reactive persistence entities and mappers for existing `parties`, `natural_person_details`, and new idempotency records, including PostgreSQL enum mappings and `@Version` on the party root.
  - Requirements: Natural-person creation, Natural-person retrieval, Optimistic concurrency, Idempotent natural-person creation
  - Verification: Hibernate validates the Flyway-managed schema and persistence mapping tests round-trip every natural-person and snapshot field.

- [x] 4.2 Implement tenant-and-type-scoped reactive retrieval joining party and natural-person details without exposing legal or cross-tenant rows.
  - Requirements: Natural-person retrieval, Natural-person and legal-entity exclusivity
  - Verification: PostgreSQL integration tests return the matching natural person and return absence for unknown, cross-tenant, legal-entity, and missing-detail cases as designed.

- [x] 4.3 Implement transactional natural-person replacement and patch persistence that updates party audit fields, flushes the detail change, increments aggregate version once, and translates optimistic-lock failures.
  - Requirements: Complete natural-person replacement, Partial natural-person update, Optimistic concurrency
  - Verification: Reactive persistence tests prove successful version increments, atomic rollback, audit updates, stale-version failure, and one winner for concurrent same-version writes.

- [x] 4.4 Implement atomic idempotent creation that persists party, natural-person details, and the versioned application-result snapshot in one reactive transaction.
  - Requirements: Natural-person creation, Idempotent natural-person creation, Natural-person and legal-entity exclusivity
  - Verification: Integration tests prove one committed aggregate/snapshot, initial version `0`, correct audit fields, and no partial rows after any forced failure.

- [x] 4.5 Implement named unique-key race recovery that reloads the committed idempotency winner in a new reactive session, replays equal hashes, and emits conflict for unequal hashes.
  - Requirements: Idempotent natural-person creation
  - Verification: Concurrent PostgreSQL tests prove equivalent requests produce one party and identical creation data, while different bodies produce one success and one conflict.

- [x] 4.6 Translate persistence and constraint failures into transport-neutral failures while preserving cancellation and preventing SQL messages or constraint details from reaching API contracts.
  - Requirements: Natural-person and legal-entity exclusivity, Standard API responses
  - Verification: Adapter tests exercise named optimistic/idempotency constraints and an unexpected database failure without exposing infrastructure exceptions to application results.

## 5. Geographic Reference Adapter

- [x] 5.1 Implement the reactive Geographic Reference REST client and infrastructure-only request/response DTOs for `GET /api/v1/countries/by-alpha2/{alpha2Code}`.
  - Requirements: Birth-country validation
  - Verification: The client compiles as a Quarkus REST Client returning `Uni`, forwards all trusted context headers, and exposes no remote DTO outside infrastructure.

- [x] 5.2 Implement `CountryReferencePort` translation for successful references, `404 country-not-found`, dependency `5xx`, malformed responses, connection failures, and timeouts without retries.
  - Requirements: Birth-country validation
  - Verification: Controlled HTTP-server tests map `200` to valid, `404` to unrecognized, and timeout/connection/`5xx`/malformed responses to dependency unavailable within finite time.

## 6. API Boundary

- [x] 6.1 Implement service-owned natural-person response codes and an API error translator for not found, idempotency conflict, stale version, invalid business state, unrecognized country, and dependency unavailable.
  - Requirements: Natural-person lifecycle date consistency, Birth-country validation, Idempotent natural-person creation, Optimistic concurrency, Standard API responses
  - Verification: Unit tests map each known failure to its exact status/code and leave unexpected failures for the shared global mapper.

- [x] 6.2 Implement the single request-context filter for exact header cardinality, canonical UUIDs, safe user identifiers, request metadata, MDC propagation, completion logging, process echo, and owned-context cleanup outside `/q`.
  - Requirements: Trusted request context, Standard API responses
  - Verification: HTTP tests cover valid, missing, duplicate, malformed, oversized, unsafe, and management-path requests plus accepted/invalid process echo behavior and MDC cleanup.

- [x] 6.3 Implement create, PUT, and PATCH request models with Bean Validation and explicit PATCH presence tracking, including rejection of empty and unknown-only patches.
  - Requirements: Natural-person request validation, Complete natural-person replacement, Partial natural-person update
  - Verification: Deserialization/validation tests distinguish absent/null/value fields and reject malformed JSON, unsupported fields, blank required names, invalid dates/country codes, and length violations.

- [x] 6.4 Implement natural-person response records and API mappers from application results, ensuring the API type is always `NATURAL_PERSON` and no domain/persistence object is serialized directly.
  - Requirements: Natural-person creation, Natural-person retrieval, Standard API responses
  - Verification: Mapper tests assert every OpenAPI response field, null handling, audit data, version, and fixed natural-person type.

- [x] 6.5 Implement `POST`, `GET`, `PUT`, and `PATCH` methods in `NaturalPersonResource` with required request signatures, application delegation, API failure translation, and CDI `ResponseManager` success envelopes.
  - Requirements: Natural-person creation, Natural-person retrieval, Complete natural-person replacement, Partial natural-person update, Standard API responses
  - Verification: Compilation and reflection tests confirm every method returns `Uni<RestResponse<ApiResponse<NaturalPersonResponse>>>`; no method returns a bare DTO, entity, or raw `Response`.

- [x] 6.6 Configure and verify the shared global error module for malformed JSON, Bean Validation, unsupported methods, authentication failures raised by platform security, and sanitized unexpected failures without adding overlapping local mappers or filters.
  - Requirements: Natural-person request validation, Standard API responses
  - Verification: HTTP tests produce `400 bad-request`, `401 unauthorized` through a controlled test security failure, `405 method-not-allowed`, and sanitized `500 server-error` envelopes with matching body/HTTP statuses.

## 7. HTTP Contract Verification

- [x] 7.1 Add creation contract tests for explicit/derived display names, initial status/version, audit fields, optional values, field validation, date validation, and recognized/unknown/unavailable countries.
  - Requirements: Natural-person request validation, Natural-person lifecycle date consistency, Birth-country validation, Natural-person creation
  - Verification: `@QuarkusTest` HTTP scenarios assert exact status/code/data envelopes and no persisted state after rejected requests.

- [x] 7.2 Add idempotency HTTP tests for missing/duplicate/blank/oversized keys, equal sequential replay, different-body conflict, cross-tenant key independence, and concurrent equal requests.
  - Requirements: Idempotent natural-person creation
  - Verification: Tests observe one party for equivalent retries, `409 conflict` for mismatched bodies, independent results per tenant, and original creation data on replay.

- [x] 7.3 Add retrieval contract tests for success, invalid UUID, absent party, cross-tenant party, legal entity, and complete response mapping.
  - Requirements: Natural-person retrieval, Natural-person and legal-entity exclusivity, Standard API responses
  - Verification: Tests return `200 successful` only for the matching natural person and identical `404 not-found` envelopes for all concealed records.

- [x] 7.4 Add PUT contract tests for required body fields, optional clearing, display-name re-derivation, changed-country validation, required `If-Match`, stale version, rollback, and version increment.
  - Requirements: Complete natural-person replacement, Birth-country validation, Optimistic concurrency
  - Verification: HTTP and database assertions prove replacement semantics, exact `400`/`404`/`412`/`422`/`503` mappings, and atomic state/version behavior.

- [x] 7.5 Add PATCH contract tests for one-field changes, omitted-field preservation, explicit null clearing, empty/unknown-only bodies, resulting-date validation, country changes, and display-name re-derivation.
  - Requirements: Partial natural-person update, Natural-person lifecycle date consistency, Birth-country validation
  - Verification: HTTP and database assertions prove tri-state semantics, exact failure envelopes, and unchanged state/version on rejection.

- [x] 7.6 Add concurrent update contract tests proving two requests with one current version yield at most one success and every loser receives `412 precondition-failed` without lost updates.
  - Requirements: Optimistic concurrency
  - Verification: Repeated concurrency tests assert final version increases once and final data matches exactly one winning request.

- [x] 7.7 Add response-boundary regression tests for HTTP/body status equality, process echo, absent error `data`, API DTO-only success data, `405`, dependency failures, and sanitized unexpected errors.
  - Requirements: Trusted request context, Standard API responses
  - Verification: RestAssured assertions cover all standard success/error envelope fields and prove no exception, SQL, stack-trace, domain, or persistence detail leaks.

## 8. Architecture, Observability, and Delivery Verification

- [ ] 8.1 Add operation metrics, tracing spans, and safe completion logs for natural-person use cases, idempotency outcomes, optimistic conflicts, and Geographic Reference calls while preserving the required MDC log format.
  - Requirements: Trusted request context, Standard API responses
  - Verification: Tests or captured telemetry prove context propagation and expected metric/span/log labels without names, dates, bodies, credentials, or authorization data.

- [ ] 8.2 Add ArchUnit tests enforcing `api`/`infrastructure` to `application`/`domain` dependency direction, domain framework isolation, application infrastructure isolation, and absence of package cycles.
  - Requirements: All natural-person requirements
  - Verification: Architecture tests pass and fail against representative forbidden dependency fixtures or rules.

- [ ] 8.3 Update packaged integration tests to use the current OpenAPI title and `/v1/natural-person` paths, then add representative valid-context, create, retrieve, update, validation, and standard-error smoke coverage.
  - Requirements: Trusted request context, Natural-person creation, Natural-person retrieval, Complete natural-person replacement, Partial natural-person update, Standard API responses
  - Verification: `./gradlew quarkusIntTest` passes against the packaged application with PostgreSQL and controlled Geographic Reference configuration.

- [ ] 8.4 Run the complete JVM verification suite and fix every domain, application, persistence, client, API contract, architecture, migration, and OpenAPI validation failure.
  - Requirements: All natural-person requirements
  - Verification: `./gradlew clean test quarkusIntTest` completes successfully with no skipped required test group.

- [ ] 8.5 Build and test the native executable, resolving serialization, reflection, resource-packaging, REST-client, Hibernate Reactive, and Flyway issues without weakening contract coverage.
  - Requirements: All natural-person requirements
  - Verification: `./gradlew buildNative -Dquarkus.native.container-build=true` and `./gradlew testNative -Dquarkus.native.container-build=true` complete successfully.

- [ ] 8.6 Perform final source and contract inspection for English Javadoc on every class/interface/record/enum, prohibited blocking calls, manual subscriptions, JDBC request-path use, resource response signatures, immutable migrations, and unchanged out-of-scope endpoints.
  - Requirements: All natural-person requirements
  - Verification: Automated searches and review find no prohibited API signatures or blocking patterns, `V1` has no diff, only scoped OpenAPI operations changed, and every introduced type has concise English Javadoc.
