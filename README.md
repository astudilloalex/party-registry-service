# Party Registry Service

Party Registry Service is a Quarkus 3 microservice for tenant-scoped civil and legal identity. The current baseline establishes the executable platform, database schema, API conventions, and architecture boundaries. Business endpoints are intentionally not implemented yet.

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

## Database

Flyway is the only schema authority. The initial immutable migration is located at `src/main/resources/db/migration/V1__create_party_registry_schema.sql` and is derived from `docs/database/v1-scheme.dbml`.

The application configures the same PostgreSQL database through two access paths:

- the reactive URL is used by runtime request processing;
- the JDBC URL is used by Flyway at startup.

Hibernate schema generation is disabled and set to validation only. PostgreSQL 18 is used by test Dev Services. The migration also includes a portable UUIDv7 implementation for older supported PostgreSQL installations.

## Configuration profiles

Development defaults to a local `party_registry` database and accepts these overrides:

```text
DEV_DB_USERNAME
DEV_DB_PASSWORD
DEV_DB_REACTIVE_URL
DEV_DB_JDBC_URL
```

Tests start an isolated PostgreSQL Dev Service. `TEST_DB_IMAGE`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD` customize that service. Standard Quarkus datasource environment variables can provide an external test database instead.

Production requires all of these variables and has no credential fallback:

```text
DB_USERNAME
DB_PASSWORD
DB_REACTIVE_URL=postgresql://database-host:5432/party_registry
DB_JDBC_URL=jdbc:postgresql://database-host:5432/party_registry
```

`OTEL_EXPORTER_OTLP_ENDPOINT` configures the collector endpoint. Trace export is disabled by default and can be enabled with `OTEL_TRACES_EXPORTER`.

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

The console log format is fixed to:

```text
%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [userId=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```
