# Project rules

## 1. Technology, architecture, and language

- Use Quarkus with Java 25.
- Apply strict Clean Architecture with these top-level packages:
  - `domain`: entities, value objects, domain services, and repository ports; no Quarkus or infrastructure dependencies.
  - `application`: use cases, commands/queries, orchestration, and input/output ports; depends only on `domain`.
  - `api`: REST resources, request/response models, validation, and HTTP mapping; delegates all business behavior to `application`.
  - `infrastructure`: persistence, messaging, external clients, configuration, and port adapters.
- Dependencies must point inward: `api` and `infrastructure` may depend on `application`/`domain`; `domain` must remain independent. Do not place business rules in REST resources or adapters.
- The microservice must be reactive end to end using Mutiny (`Uni`/`Multi`) and Hibernate Reactive with Panache. Do not use blocking APIs, synchronous waits, or traditional Hibernate ORM in request processing.
- The microservice must support Quarkus native compilation. All dependencies, serialization models, reflection requirements, and resources must be compatible with native executables, and native integration tests must be maintained.
- Use Flyway exclusively for every database creation or change, including schemas, tables, columns, sequences, indexes, constraints, keys, views, and reference data. Migrations must be versioned, ordered, and immutable under `src/main/resources/db/migration`; never execute manual DDL or enable automatic schema generation.
- Write all source code and its technical documentation in English, including packages, types, methods, fields, variables, constants, tests, comments, logs, and error messages.
- Keep the logging format exactly as `%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [userId=%X{userId}] [tenantId=%X{tenantId}] %s%e%n`, with `processId`, `userId`, and `tenantId` propagated through MDC in every request flow.
- Add concise English Javadoc to every class, interface, record, and enum describing its responsibility. Document public methods when their contract, parameters, result, or failures are not self-evident; comments must explain intent rather than restate the code.

## 2. HTTP API contract and cross-cutting behavior

- Treat the approved OpenAPI document as the source of truth. Apply these conventions to every endpoint unless the contract explicitly states otherwise.
- Require exactly one `Tenant-Id`, `User-Id`, and `Process-Id` header on every API request. Validate canonical UUIDs for tenant/process identifiers and a non-blank, safe user identifier of at most 128 characters. Exclude `/q` management endpoints.
- Use one request filter to validate headers, populate MDC, log request completion, and clear owned context. Echo the accepted `Process-Id` unchanged on every response; do not return it when the incoming value is missing or invalid.
- Return successes as `{ "status": <http-status>, "code": "successful", "data": ... }`. Return errors as `{ "status": <http-status>, "code": "<stable-code>" }`, without `data`, exception messages, stack traces, SQL details, or internal causes. Include pagination fields only when defined by OpenAPI.
- Centralize envelopes and failures through `ApiResponse<T>`, `ResponseManager`, `ApiResponseCode`, `ApiException`, and a global reactive exception handler; resources must not build ad hoc responses or catch cross-cutting exceptions.
- Preserve the global mappings: `400 bad-request`, `404 not-found`, `405 method-not-allowed`, `409 conflict`/`version-conflict`, `500 server-error`, and `503 dependency-unavailable`. Add resource-specific codes only when declared by OpenAPI. The body status must always equal the HTTP status, including `201 successful` responses.
- Map malformed JSON, Bean Validation failures, invalid path/query values, unsupported media types or methods, missing resources, expected domain failures, and unexpected exceptions to the standard envelope. Unexpected failures must be logged internally and exposed only as `500 server-error`.


## Java validation

After modifying Java code:

1. Check and resolve JDTLS/LSP diagnostics for every modified Java file.
2. Do not introduce new compiler warnings.
3. Do not suppress nullability or type-safety warnings with
   @SuppressWarnings unless the suppression is explicitly justified.
4. Run:
   ./gradlew test
5. Before completing an implementation task, run:
   ./gradlew build
6. If LSP diagnostics conflict with Gradle compilation results,
   investigate the discrepancy instead of ignoring either result.
