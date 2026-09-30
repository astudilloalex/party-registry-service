## 1. Contract Foundation

- [x] 1.1 Align the GET, PUT, and PATCH declarations for `/v1/legal-entity/{partyId}` in `docs/contracts/party-registry.openapi.yaml` with the approved delta: implemented descriptions, closed update schemas, nullable-field semantics, display-name rules, legal-field validation-code applicability, Process-Id echo headers, and PUT/PATCH `503 dependency-unavailable` responses.
  - Requirements: Retrieve legal-entity details; Complete replacement of legal details; Presence-aware partial legal-detail updates; Consistent public responses and confidential failures; Consumer documentation reflects the implemented operations.
  - Verification: The OpenAPI parses successfully, retains the detail response shape and existing creation contract, and documents the legal-entity absence/version/country/date codes and operation-specific If-Match behavior without adding other routes.

- [x] 1.2 Extend `OpenApiContractTest` with assertions for the legal-detail schemas, required PUT properties, PATCH minimum properties, normalization limits, header references, error-code applicability, and dependency responses.
  - Requirements: Strict body validation and canonical text; Trusted request context and path validation; Incorporation-country recognition; Consumer documentation reflects the implemented operations.
  - Verification: The focused static contract tests pass against the updated document and detect missing schema constraints, response headers, or 503 declarations.

- [x] 1.3 Align the shared business-error catalog and translator with cause-specific codes for existing registration/activation and new legal-detail failures, restoring the approved 409 statuses for identifier uniqueness and invalid lifecycle transitions.
  - Requirements: Cause-specific legal business failures; approved party-registration and party-activation error-contract alignment.
  - Verification: Catalog/translator tests assert exact absence, version, country, date, identifier, idempotency, and missing-evidence codes with approved HTTP statuses; unexpected failures remain sanitized and no business eligibility rule changes.

- [x] 1.4 Update affected existing error-contract tests and OpenAPI documentation to the aligned specific catalog, including the registration/activation delta specifications.
  - Requirements: Consumer documentation reflects the implemented operations; approved party-registration and party-activation error-contract alignment.
  - Verification: Regression assertions retain exact status/envelope checks, expected specific codes match each scenario, static contracts parse, and successful responses and workflows remain compatible.

## 2. Domain Legal-Detail Behavior

- [x] 2.1 Add `domain.model.LegalEntityPatch` using six `FieldUpdate<T>` values, retaining independent omitted, present-null, and present-value states and supporting empty-patch detection.
  - Requirements: Presence-aware partial legal-detail updates.
  - Verification: Domain tests distinguish all three states for each supported field and identify an update with no present fields without importing transport or reactive types.

- [x] 2.2 Implement `LegalEntity.replaceDetails` with canonical complete-replacement values, write-path length guards, cleared optional fields, resulting-date validation, canonical legal-name comparison, and updated audit information.
  - Requirements: Complete replacement of legal details; Strict body validation and canonical text; Derived display-name behavior; Coherent legal lifecycle dates; Atomic update and audit outcome.
  - Verification: Focused Domain tests verify replacement, optional clearing, derived/custom display names, retained identity/type/status/creation metadata, and retention of the current candidate version until persistence increments it.

- [x] 2.3 Implement `LegalEntity.patchDetails` so only supplied fields are normalized, explicit null clears nullable fields, mandatory null and empty patches fail, and the complete resulting state is validated.
  - Requirements: Presence-aware partial legal-detail updates; Strict body validation and canonical text; Derived display-name behavior; Coherent legal lifecycle dates.
  - Verification: Focused Domain tests prove omitted historical values are retained, unrelated fields are not rewritten, mandatory values cannot be cleared, and invalid merged dates produce no accepted candidate.

- [x] 2.4 Extend Domain regression coverage for normalized UTF-16 boundaries, uppercase expansion, supplementary Unicode, equivalent legal names with custom display names, equal/today/future lifecycle dates, and historical restoration.
  - Requirements: Strict body validation and canonical text; Derived display-name behavior; Coherent legal lifecycle dates; Compatibility with registration and independent identifiers.
  - Verification: Boundary cases pass at 300/300/64 normalized code units and fail above them; existing creation and restoration tests continue to pass without normalizing historical data.

## 3. Application Contracts and Use Cases

- [x] 3.1 Add `application.port.LegalEntityRepository` and the get, replace, and patch commands, using existing typed request metadata, Party identity/version values, and the Domain patch representation.
  - Requirements: Retrieve legal-entity details; Tenant and Party-type isolation; Version preconditions and concurrent updates; Atomic update and audit outcome.
  - Verification: Port/command tests confirm tenant-qualified lookup and atomic update contracts, preserve field presence, and expose neither sessions nor HTTP types; update commands do not construct semantically validated replacement aggregates before the version check.

- [x] 3.2 Add `ApplicationFailure.LegalEntityNotFound` and update exhaustive handling in `ApplicationException`, `PartyApiErrorTranslator`, and `OperationObservation` for the new variant.
  - Requirements: Tenant and Party-type isolation; Consistent public responses and confidential failures.
  - Verification: Failure-contract tests cover absent/concealed/wrong-type meaning, all exhaustive switches compile, and existing failure variants retain their prior mappings.

- [x] 3.3 Implement `GetLegalEntityUseCase` to resolve qualified absence and project the retrieved aggregate into the existing `LegalEntityResult`.
  - Requirements: Retrieve legal-entity details; Tenant and Party-type isolation; Compatibility with registration and independent identifiers.
  - Verification: Unit tests cover successful historical reads and legal absence; the use case has no country, identifier, or mutation dependency.

- [x] 3.4 Add changed-incorporation-country coordination in `CountryValidation`, comparing canonical current/resulting codes and preserving recognized, unrecognized, unavailable, and cancellation outcomes.
  - Requirements: Incorporation-country recognition; Consistent public responses and confidential failures.
  - Verification: Port-double tests prove equivalent codes skip lookup, changed codes pass canonical values and original trusted metadata, false becomes `UnrecognizedIncorporationCountry`, null becomes dependency failure, and cancellation propagates.

- [x] 3.5 Implement `ReplaceLegalEntityUseCase` with qualified lookup, early expected-version verification, one trusted UTC evaluation instant, Domain replacement, optional country validation, and one atomic repository update.
  - Requirements: Complete replacement of legal details; Coherent legal lifecycle dates; Incorporation-country recognition; Version preconditions and concurrent updates; Atomic update and audit outcome.
  - Verification: Unit tests assert the ordered calls, stale-version precedence over semantic/dependency failures, no update after validation failure, and projection of the repository's returned incremented version.

- [x] 3.6 Implement `PatchLegalEntityUseCase` with the same update ordering while preserving partial-field semantics and unchanged-country independence from the geographic dependency.
  - Requirements: Presence-aware partial legal-detail updates; Derived display-name behavior; Incorporation-country recognition; Version preconditions and concurrent updates.
  - Verification: Unit tests cover omitted versus null fields, a country outage during an unrelated edit, changed-country failure, one UTC evaluation instant, and unchanged failures/cancellation without retries or extra writes.

## 4. Reactive Persistence and Transaction Guarantees

- [x] 4.1 Implement `HibernateReactiveLegalEntityRepository` with qualified root/legal-detail reads and one short transaction for the guarded root version increment, detail mutation, session clear, and reload through `LegalEntityPersistenceMapper`.
  - Requirements: Retrieve legal-entity details; Tenant and Party-type isolation; Version preconditions and concurrent updates; Atomic update and audit outcome.
  - Verification: Focused reactive PostgreSQL tests verify successful read/update, exactly one affected root/detail row, returned persisted version, and detached Domain results; queries contain tenant/ID/type predicates and no identifier access, and root writes omit lifecycle/identity assignments.

- [x] 4.2 Add repository integration tests for cross-tenant/wrong-type/absent lookups, audit preservation on both rows, identical accepted updates, version arithmetic, and independent identifier data.
  - Requirements: Tenant and Party-type isolation; Atomic update and audit outcome; Compatibility with registration and independent identifiers.
  - Verification: Tests using existing Flyway-managed PostgreSQL fixtures prove each accepted update increments once, retained metadata and identifiers are unchanged, and version overflow cannot produce a negative stored version or partial write.

- [x] 4.3 Exercise transaction rollback and persistence-failure translation with controlled test-only failures after root mutation and after detail mutation before transaction completion.
  - Requirements: Atomic update and audit outcome; Consistent public responses and confidential failures.
  - Verification: Reload/mapping or equivalent test-boundary failures leave both rows, their audit values, and the root version at the previous state; no production failure endpoint or manual DDL is introduced, and cancellation/application failures remain distinguishable.

- [x] 4.4 Add separate-session concurrency tests for competing legal-detail updates and a legal-detail update racing with the existing activation operation.
  - Requirements: Version preconditions and concurrent updates; Atomic update and audit outcome; Compatibility with registration and independent identifiers.
  - Verification: At most one update wins a shared version; a legal-detail loser produces `ExpectedVersionMismatch`, qualified diagnosis conceals other tenants, and a losing detail write cannot revert activation or contribute partial fields.

## 5. API Models, Error Translation, and Resource Wiring

- [x] 5.1 Add `LegalEntityPutRequest` with exactly the six supported fields, canonical validation copies, existing legal-field validation messages, and declared nullability and normalized lengths.
  - Requirements: Complete replacement of legal details; Strict body validation and canonical text.
  - Verification: DTO validation tests cover required properties, optional nulls, ASCII-country rules, length boundaries, and multiple violations selecting the documented JSON property path before If-Match validation.

- [x] 5.2 Add `LegalEntityPatchRequest` with setter-based presence tracking, presence-preserving validation copies, mandatory-field checks attached to JSON property paths, and mapping to `LegalEntityPatch`.
  - Requirements: Presence-aware partial legal-detail updates; Strict body validation and canonical text.
  - Verification: Tests cover each omitted/null/value state, empty objects, mandatory null, preserved original inputs, and deterministic failure ordering that does not depend on helper-method names.

- [x] 5.3 Implement and attach API-local strict string/date deserializers to the two update models and add `LegalEntityUpdateJsonReaderInterceptor` scoped to those models for syntax/binding failure translation.
  - Requirements: Strict body validation and canonical text; Consistent public responses and confidential failures.
  - Verification: Binding tests reject incompatible scalars, arrays, objects, malformed dates, and unknown fields as bad requests; valid strings/null remain available for field validation, null/missing bodies retain the body-required classification, and existing creation/natural-person binding is unaffected.

- [x] 5.4 Add `LegalEntityResponse` and extend `LegalEntityApiMapper` to map `LegalEntityResult` into the declared detail representation while retaining the creation-only mapper.
  - Requirements: Retrieve legal-entity details; Consistent public responses and confidential failures; Compatibility with registration and independent identifiers.
  - Verification: Mapping/serialization tests contain all required Party/legal-detail fields and no tenant internals, naturalPersonDetails, initialIdentifier, identifiers array, or sensitive identifier material; creation mapping tests still pass.

- [x] 5.5 Complete and verify legal-detail failure coverage through the shared `PartyApiErrorTranslator`, reusing the aligned specific version/country/dependency entries and legal absence/date catalog entries.
  - Requirements: Tenant and Party-type isolation; Incorporation-country recognition; Version preconditions and concurrent updates; Consistent public responses and confidential failures.
  - Verification: Unit tests assert `legal-entity-not-found`, `expected-version-mismatch`, `unrecognized-incorporation-country`, `incorporation-date-in-future`, `dissolution-date-in-future`, `dissolution-before-incorporation`, and `dependency-unavailable` with their documented statuses and date-error precedence; unexpected/persistence failures and cancellation pass through without a new global mapper or copied library class.

- [x] 5.6 Wire the three use cases in `ApplicationUseCaseProducer` and add GET, PUT, and PATCH methods to `LegalEntityResource`, using existing context/support validation, the detail mapper, and the shared cause-specific error translator.
  - Requirements: Retrieve legal-entity details; Complete replacement of legal details; Presence-aware partial legal-detail updates; Trusted request context and path validation; Consistent public responses and confidential failures.
  - Verification: CDI resolves the new beans under the existing build-property convention; smoke contract tests reach all three routes; every method returns `Uni<RestResponse<ApiResponse<LegalEntityResponse>>>` and maps the result before `ResponseManager.successHttp`; creation keeps its existing pipeline.

## 6. Correlation and Operational Visibility

- [x] 6.1 Extend `PartyHttpObservability` with bounded legal-detail operation labels and optimistic-conflict counting, and add the three legal-detail resource span names.
  - Requirements: Trusted request context and path validation; Version preconditions and concurrent updates; Consistent public responses and confidential failures.
  - Verification: Observability tests classify only the supported single-ID routes, count `expected-version-mismatch` update conflicts and the specific legal 422 validation codes, retain bounded labels without Party IDs, and preserve existing operation labels.

- [x] 6.2 Extend context/observability tests for concurrent legal-detail requests, accepted Process-Id propagation to Geographic Reference, completion/failure cleanup, and sanitized rejection logging.
  - Requirements: Trusted request context and path validation; Consistent public responses and confidential failures; Incorporation-country recognition.
  - Verification: Distinct tenant/user/process values do not leak between requests; accepted process values echo correctly, invalid process values do not; the existing log format and single-filter ownership remain intact, with no sensitive body or unknown-property content logged.

## 7. HTTP Acceptance and Regression Coverage

- [x] 7.1 Add legal-detail end-to-end contract scenarios for POST followed by GET, PUT, GET, PATCH, GET, and replay of the original creation request.
  - Requirements: Retrieve legal-entity details; Complete replacement of legal details; Presence-aware partial legal-detail updates; Derived display-name behavior; Atomic update and audit outcome; Compatibility with registration and independent identifiers.
  - Verification: Current GET reflects accepted corrections and versions; PUT clears omitted optional values; PATCH preserves omitted values; creation replay returns the original snapshot and identifiers remain unchanged.

- [x] 7.2 Add parameterized HTTP cases for context cardinality/canonicality, path/type/tenant concealment, strict JSON types, required/length/country fields, empty PATCH, and all If-Match forms and precedence rules.
  - Requirements: Trusted request context and path validation; Tenant and Party-type isolation; Strict body validation and canonical text; Version preconditions and concurrent updates.
  - Verification: Exact HTTP/body statuses and stable codes match the delta, including body errors before operation-header errors, context errors before body processing, and stale versions before semantic country validation; rejected writes leave state unchanged.

- [x] 7.3 Add HTTP geographic/lifecycle/framework failure scenarios, including recognized/unrecognized/unavailable/unusable country responses, unchanged-country availability, invalid resulting dates, sanitized unexpected failures, and unsupported methods/media types.
  - Requirements: Incorporation-country recognition; Coherent legal lifecycle dates; Consistent public responses and confidential failures.
  - Verification: 422 and 503 remain distinct, errors contain only status/code, 405 is `method-not-allowed`, and 415 uses the resolved shared module's actual media-type code rather than a guessed mapping; deployed authentication behavior remains covered where applicable.

- [x] 7.4 Add concurrent HTTP PUT/PATCH scenarios with the same expected version, and confirm nonblocking execution and committed-state visibility through a subsequent GET.
  - Requirements: Version preconditions and concurrent updates; Atomic update and audit outcome; Retrieve legal-entity details.
  - Verification: One successful update returns the next version, the loser returns `412 expected-version-mismatch`, GET shows only the winner, and the flow introduces no event-loop blocking or shared-session concurrency.

## 8. Packaged Coverage and Service Documentation

- [x] 8.1 Extend packaged integration tests under `src/integrationTest` using the existing application/geographic fixture conventions, ensuring the same legal-detail coverage can execute against JVM packaging and native executables.
  - Requirements: Retrieve legal-entity details; Presence-aware partial legal-detail updates; Strict body validation and canonical text; Incorporation-country recognition; Consistent public responses and confidential failures; Compatibility with registration and independent identifiers.
  - Verification: Packaged tests exercise creation/read/update/replay, null presence, strict token handling, exact errors and dependency outcomes; new DTOs and deserializers are reachable in native mode through narrowly scoped annotations/registration, and packaged `/q/openapi` reflects the source contract.

- [x] 8.2 Update README and relevant consumer guidance with the ten implemented business operations, legal-detail update semantics, required version handling, deployment availability, and additive rollout/rollback considerations.
  - Requirements: Consumer documentation reflects the implemented operations; Version preconditions and concurrent updates; Compatibility with registration and independent identifiers.
  - Verification: Documentation lists legal GET/PUT/PATCH accurately, explains current versions versus creation snapshots, and agrees with the OpenAPI and design without proposing schema changes or historical rewrites.

## 9. Implementation Quality Gates

- [x] 9.1 Inspect all modified Java files for JDTLS/LSP diagnostics, compiler-warning risks, English Javadoc, inward dependencies, explicit API DTO/envelope signatures, and preservation of the existing Flyway-only schema boundary.
  - Requirements: All legal-entity-details requirements; design architecture and validation constraints.
  - Verification: Diagnostics are resolved, every introduced type has responsibility-focused Javadoc, and no unjustified suppression, blocking request-path API, global mapper, or schema-generation change is present. If diagnostic access is unavailable, record that blocker and leave this check unresolved rather than claiming it passed.
  - Evidence: Direct JDTLS validation imported the Gradle project successfully and received diagnostics for all 49 changed Java files with no errors or warnings. The installed OpenCode CLI's Java-version parser does not recognize this host's four-component 25.0.4.1 version, so an installed JDTLS server and a temporary workspace were used instead of treating empty CLI output as a successful server check.

- [x] 9.2 Run `./gradlew test` and resolve failures attributable to the change while investigating any existing failures separately.
  - Requirements: All legal-entity-details requirements.
  - Verification: The command succeeds, including new Domain/Application/repository/API cases and existing registration, natural-person, activation, geographic, and ArchUnit regression checks; LSP/compiler discrepancies are investigated rather than ignored.

- [x] 9.3 Run `./gradlew build` and verify the packaged integration-test and OpenAPI packaging results.
  - Requirements: Consistent public responses and confidential failures; Consumer documentation reflects the implemented operations; design packaged-runtime constraints.
  - Verification: The command succeeds, `check` includes `quarkusIntTest`, the packaged legal-detail scenarios pass, and no new compiler warnings are introduced.

- [x] 9.4 Run `./gradlew buildNative -Dquarkus.native.container-build=true` and address feature-related native reachability or resource issues with narrowly scoped changes.
  - Requirements: All legal-entity-details requirements; design native-runtime constraints.
  - Verification: Native compilation succeeds without broad reflection registration or public serialization of Domain/persistence types; actual environment blockers remain reported as unresolved rather than marked complete.
  - Evidence: Mandrel 25.0.4.1-Final built the executable successfully with the additional `-Dquarkus.native.container-runtime=podman` flag. Explicit local Podman avoided a failed launcher through automatic runtime selection; application code and reflection configuration required no workaround.

- [x] 9.5 Run `./gradlew testNative -Dquarkus.native.container-build=true` and verify the new packaged legal-detail scenarios against the native executable.
  - Requirements: All legal-entity-details requirements; design native-runtime constraints.
  - Verification: Native tests pass for the new endpoints, serialization, validation/error envelopes, country checks, and creation compatibility; runtime evidence is recorded before claiming native support is verified.
  - Evidence: All 13 packaged tests passed against the native executable, with zero failures or skipped tests; the JVM packaged run also passed all 13 tests.

## 10. Postman Documentation and Final Read-back

- [x] 10.1 After runtime verification, retrieve collection `15834347-8f36abef-3a94-4646-a4f2-eecd36c74313` through Postman MCP and record the current Legal Entities folder ID, existing request/example IDs, scripts, variables, and collection coverage notes.
  - Requirements: Consumer documentation reflects the implemented operations.
  - Verification: A current collection snapshot establishes the entity folder structure and existing content to preserve; the target folder is discovered rather than recreated or inferred from stale IDs.

- [x] 10.2 Add or update exactly one GET, PUT, and PATCH legal-detail request in the existing Legal Entities folder using targeted Postman operations, with headers, variables, bodies, field semantics, and success/failure examples from the verified contract.
  - Requirements: Consumer documentation reflects the implemented operations; Trusted request context and path validation; Presence-aware partial legal-detail updates; Version preconditions and concurrent updates; Consistent public responses and confidential failures.
  - Verification: Legal Entities contains POST/GET/PUT/PATCH once each; examples cover current retrieval, replacement/partial updates, null behavior, the specific legal-entity absence/version/country/date codes and applicable 400/503 outcomes, with sanitized failures and no sensitive identifier values.
  - Evidence: Full Postman MCP read-back confirms GET `5bd29b2e-fa42-daf8-3d78-d57060e1fe3d`, PUT `f5c55a54-0cb1-cc26-9d07-cf72f1336c32`, and PATCH `691e7adf-6d46-4f89-be21-550603e809c5` in the existing Legal Entities folder, with 5, 12, and 13 saved examples respectively. OpenAPI response schemas, success request bodies, version increments, Process-Id echoes, and sanitized error codes were validated. Examples remain illustrative; no requests were executed against the consumer's configured baseUrl.

- [x] 10.3 Update collection coverage notes through a lossless metadata operation, then read back and validate the complete collection against the updated OpenAPI and the snapshot from task 10.1.
  - Requirements: Consumer documentation reflects the implemented operations; Compatibility with registration and independent identifiers.
  - Verification: The collection documents ten implemented operations, no longer calls legal-detail operations future work, and preserves all unrelated folders/requests/scripts/examples/variables. If the MCP tool set cannot perform a lossless metadata edit, report that concrete blocker instead of using a nested-content-stripping replacement. Confirm every delta requirement has implementation/test evidence and leave any unresolved verification or documentation work unchecked.
  - Evidence: Targeted request/example/folder updates and a metadata-only `patchCollection` completed through the user-authorized temporary full MCP connection. Read-back validates 10 unique implemented operations, four entity folders, and 56 examples against OpenAPI and the original seven-request/26-example snapshot. Original folder/request/example IDs, request definitions, scripts, variables, authentication, and unrelated content were preserved; ten existing error-example codes and affected descriptions were aligned with the approved cause-specific catalog. No whole-collection replacement was used.
