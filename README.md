# Party Registry Service

Party Registry Service is a Quarkus 3 microservice for tenant-scoped civil and legal identity. It registers natural persons and legal entities with one required initial official identifier, supports independently managed additional identifiers, and activates draft Parties from verified identifier evidence.

## Technology baseline

- Java 25 and Gradle 9
- Quarkus REST with Jackson and Mutiny
- Hibernate Reactive with Panache and the Vert.x PostgreSQL client
- JDBC PostgreSQL access reserved for Flyway migrations
- SmallRye OpenAPI, Health, and Context Propagation
- Micrometer Prometheus metrics and OpenTelemetry tracing
- JVM and native executable packaging

## Architecture

Production code is split into inward-facing Clean Architecture packages:

- `domain`: framework-independent business model and repository ports
- `application`: reactive use cases, orchestration, and application ports
- `api`: HTTP models, validation, filters, and response/error mapping
- `infrastructure`: persistence, messaging, clients, and configuration adapters

ArchUnit verifies layer direction, framework isolation for the inner layers, and package cycles.

### Write normalization

New writes strip exterior whitespace and uppercase natural-person names and preferred names, legal names/trade names/legal-form codes, supplied display names, and country codes using `Locale.ROOT`. Accents, punctuation, interior whitespace, and nulls are preserved. Country input must be two ASCII letters after stripping. Validation applies to canonical values before persistence and geographic-reference calls receive canonical country codes.

Creation request values are retained unchanged for the existing idempotency fingerprint; the API validates a separate normalized copy. Reads and idempotency snapshots do not rewrite historical text. PATCH normalizes only supplied values and preserves omitted fields. No historical backfill or new nationality/legal-update endpoints are included; nationality country codes follow the documented normalization rule when that flow is implemented.

For new identifiers, Application supplies the validated, stripped uppercase plaintext to encryption. Lookup hashes and masks already use that same canonical value. Ciphertext/Base64 must never be uppercased, and previously encrypted values are not rewritten. Expiration remains optional for every document.

### Natural-person reads

`GET /v1/natural-person/{partyId}` includes a GET-only `identifiers` array of current masked projections. Current means `PENDING_VERIFICATION` or `VERIFIED`, with no expiration or an expiration on/after the request's UTC evaluation date. Results are complete, ordered by creation timestamp and identifier ID, and empty when none qualify. The query never loads ciphertext, lookup hashes, or key versions, and does not decrypt or change identifier state. Creation/replay, PUT, and PATCH response shapes remain unchanged.

## Database

Flyway is the only schema authority. Production migrations live under `src/main/resources/db/migration`: V1 creates the schema, V2 adds idempotency storage, V3 seeds the Ecuadorian identifier catalog, and V4 makes expiration optional. Applied migrations are immutable. The initial schema is derived from `docs/database/v1-scheme.dbml`.

The application configures the same PostgreSQL database through two access paths:

- the reactive URL is used by runtime request processing;
- the JDBC URL is used by Flyway at startup.

Hibernate schema generation is disabled and set to validation only. PostgreSQL 18 is used by test Dev Services. The migration also includes a portable UUIDv7 implementation for older supported PostgreSQL installations.

## Configuration profiles

Development defaults to a local `party_registry` database and accepts these overrides:

```text
DEV_DB_USERNAME
DEV_DB_PASSWORD
DEV_FLYWAY_DB_USERNAME
DEV_FLYWAY_DB_PASSWORD
DEV_DB_REACTIVE_URL
DEV_DB_JDBC_URL
```

Tests start an isolated PostgreSQL Dev Service. `TEST_DB_IMAGE`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD` customize that service. Standard Quarkus datasource environment variables can provide an external test database instead.

Production requires all of these variables and has no credential fallback:

```text
DB_USERNAME
DB_PASSWORD
FLYWAY_DB_USERNAME
FLYWAY_DB_PASSWORD
DB_REACTIVE_URL=postgresql://database-host:5432/party_registry
DB_JDBC_URL=jdbc:postgresql://database-host:5432/party_registry
GEOGRAPHIC_REFERENCE_BASE_URL=https://geographic-reference.example
RABBITMQ_HOST=rabbitmq-host
RABBITMQ_USERNAME=party-registry
RABBITMQ_PASSWORD=<secret reference>
```

`RABBITMQ_PORT` defaults to `5672`, and `RABBITMQ_VIRTUAL_HOST` defaults to `/`. `PARTY_OUTBOX_MODE` accepts `disabled`, `stored-only`, or `published` and defaults to `disabled`. The `published` mode requires a reachable RabbitMQ broker and publisher permissions for the configured exchange and routing key.

`OTEL_EXPORTER_OTLP_ENDPOINT` configures the collector endpoint. Trace export is disabled by default and can be enabled with `OTEL_TRACES_EXPORTER`.

## Release prerequisites

Registration cannot be enabled safely until both prerequisites below are satisfied.

### Identifier-protection secrets

Provision these values through the deployment secret manager before starting the application:

```text
PARTY_IDENTIFIER_ENCRYPTION_KEY_V1=<base64-encoded 32-byte AES key>
PARTY_IDENTIFIER_CURRENT_ENCRYPTION_KEY_VERSION=1
PARTY_IDENTIFIER_INDEX_HMAC_KEY=<base64-encoded 32-byte HMAC key>
PARTY_REGISTRATION_IDEMPOTENCY_HMAC_KEY=<base64-encoded 32-byte HMAC key>
```

The application validates and loads all key material at startup and fails closed when it is absent or invalid. Do not place production keys in source code, container images, configuration defaults, logs, or migration files. The identifier-index HMAC key is long-lived because the current schema has no fingerprint-key version column; rotating it requires an approved forward migration with dual-fingerprint support. Retain every encryption key version needed to read existing ciphertext before changing the current encryption-key version.

### Identifier-scheme catalog

A fresh production database includes active Ecuadorian national-ID, taxpayer-ID, and passport schemes from V3. V4 clears historical expiration requirements without changing scheme identity or status. The `TEST_*` schemes under `src/test/resources/db/test-migration` are deterministic test fixtures and must never be deployed to production.

Before accepting registration traffic, provision an independently approved, jurisdiction-specific catalog containing the supported scheme codes, Party-type applicability, lifecycle state, normalizer and validator keys, and length constraints. Expiration is optional for every document, including catalogs retaining legacy `requiresExpiration=true` metadata. Supplied expiration dates must not precede the evaluation date or a supplied issue date. Catalog changes must use new reviewed Flyway migrations; never run manual DDL/DML or edit applied migrations.

Deploy the approved catalog before enabling create traffic. Unknown, inactive, incompatible, or internally unsupported schemes are rejected by design.

### Rollback

Registration writes version-two idempotency snapshots and persists Party and PartyIdentifier as separate aggregate roots. Do not delete or rewrite Party, identifier, idempotency, or outbox rows during rollback. An older identifier-free application cannot satisfy the current request contract or decode the new replay result, so rollback requires stopping create traffic or restoring another contract-compatible application version.

## Running and verification

Start development mode:

```shell
./gradlew quarkusDev
```

Run JVM tests, including PostgreSQL migration and concurrency tests:

```shell
./gradlew test
```

Run packaged integration tests:

```shell
./gradlew quarkusIntTest
```

Build the JVM artifact:

```shell
./gradlew build
java -jar build/quarkus-app/quarkus-run.jar
```

Build and test a native executable with a container runtime:

```shell
./gradlew buildNative -Dquarkus.native.container-build=true
./gradlew testNative -Dquarkus.native.container-build=true
```

When using rootless Podman, expose its Docker-compatible socket to Testcontainers, for example:

```shell
export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
```

## API foundation

The approved contract at `docs/contracts/party-registry.openapi.yaml` is packaged unchanged and exposed at `/q/openapi`. Operational endpoints are available under `/q`, including:

- `/q/health`, `/q/health/live`, and `/q/health/ready`
- `/q/metrics`
- `/q/openapi`

Operational endpoints do not require business context headers. All API requests require exactly one `Tenant-Id`, `User-Id`, and `Process-Id`. Accepted context is propagated through MDC, and `Process-Id` is echoed unchanged. Responses use the standard `status`, `code`, and optional `data` envelope.

The implemented business operations are:

- `POST /v1/natural-person`
- `POST /v1/legal-entity`
- `GET /v1/natural-person/{partyId}`
- `PUT /v1/natural-person/{partyId}`
- `PATCH /v1/natural-person/{partyId}`
- `POST /v1/parties/{partyId}/identifiers`
- `POST /v1/parties/{partyId}/activate`

Natural-person and legal-entity creation require exactly one `initialIdentifier`. The Party starts in `DRAFT`, and its independently persisted initial identifier starts in `PENDING_VERIFICATION`. Activation requires at least one compatible, non-expired `VERIFIED` identifier and exact optimistic-concurrency input through `If-Match`.

Complete and normalized identifier values never appear in API responses, idempotency snapshots, integration events, metrics, traces, or logs. Public responses expose only approved identifier metadata such as IDs, scheme code, masked value, status, and version.

The console log format is fixed to:

```text
%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [userId=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```
