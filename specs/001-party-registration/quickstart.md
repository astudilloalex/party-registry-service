# Quickstart Validation Guide: Party Registration

**Purpose**: Guide test-driven development and reproduce end-to-end evidence without contacting
production

## Prerequisites

- JDK 25 selected by `JAVA_HOME`.
- Podman or Docker available for PostgreSQL 18 and controlled HTTP test containers.
- No production credentials or endpoints configured.
- The final DBML has incorporated and passed independent validation for:
  - Temporal nationality exclusion constraints.
  - Unique `party.created.v1` event identity.
  - `User-Id` initial outbox audit semantics.
- Flyway migrations generated from that validated DBML have passed their independent migration
  gate.
- The Geographic Reference adapter is mapped to an approved provider contract or a controlled local
  stub.
- LikeC4 has been synchronized with a Clean Architecture component view, the upstream trusted-header
  and internal-ingress boundary, explicit natural/legal alternatives, remote validation before the
  local transaction, and Party database ownership that excludes customers.
- The architecture owner has approved the Hibernate Reactive execution-model ADR, including
  transaction, resource, debugging, operational, and packaging consequences.

## Design References

- [Feature specification](./spec.md)
- [Implementation plan](./plan.md)
- [Research decisions](./research.md)
- [Data model](./data-model.md)
- [Traceability matrix](./traceability.md)
- [Party registration OpenAPI](./contracts/party-registration.openapi.yaml)
- [Party-created payload schema](./contracts/party-created-v1.schema.json)
- [Geographic validation port](./contracts/geographic-reference-port.md)

## Always-TDD Development Loop

Implement each production behavior as one small Red -> Green -> Refactor cycle at the lowest layer
that can prove it:

1. **Red**: Add one focused test and run only that test. It must fail because the behavior is
   missing, not because the test environment or compilation is broken.
2. **Green**: Add only enough production code to satisfy that test, then rerun the targeted test and
   its affected layer suite until both pass.
3. **Refactor**: Improve the design while the affected suite remains green, then run applicable
   architecture, contract, PostgreSQL, HTTP, or packaged checkpoints.

Use filtered Gradle execution for the inner loop, replacing the examples with the actual test class
and method:

```bash
./gradlew test --tests 'com.alexastudillo.partyregistry.SomeTest.someBehavior'
./gradlew test --tests 'com.alexastudillo.partyregistry.SomeTest'
```

Record the targeted command, the intended Red failure reason, and the passing Green result in task
or Pull Request evidence. An intentional Red command has a non-zero exit; final gate commands must
exit successfully. Pure refactoring starts from a recorded green baseline and remains green. Test
harness setup or compile-only scaffolding may come first but must not implement business behavior.

## 1. Verify the Toolchain

```bash
java -version
./gradlew --version
```

Expected result:

- Java reports version 25.
- Gradle starts successfully with the repository wrapper.

## 2. Run the Complete JVM Test Suite Checkpoint

```bash
./gradlew clean test
```

The test suite must start isolated PostgreSQL 18 and Geographic Reference stub instances and must
not use H2 or a production service.

Expected evidence:

- Domain tests cover type/detail compatibility, lifecycle dates, derived display name,
  nationality intervals and the 10-item limit, status `DRAFT`, and version `0`.
- Application tests prove context and country validation occur before persistence.
- Geographic adapter tests cover active, inactive, unknown, partial, malformed, delayed, and failed
  responses.
- Persistence tests prove Party, details, nationalities, and optional outbox atomicity.
- Flyway applies from an empty PostgreSQL 18 database and Hibernate schema validation passes.
- Architecture tests reject outward dependencies, blocking APIs, direct/native SQL,
  direct reactive-client access, blocking Hibernate ORM, and JDBC outside the Flyway boundary.
- API tests conform to the OpenAPI request variants, headers, responses, and RFC 9457 errors.
- Logging tests verify the exact constitutional pattern and reactive MDC propagation.

## 3. Run the Packaged Black-Box Checkpoint

```bash
./gradlew quarkusIntTest
```

Expected evidence:

- Packaged tests execute from `src/integrationTest/java`.
- The packaged service exposes only the documented internal Party creation operation for this
  feature.
- Successful responses are emitted only after commit.
- Live OpenAPI output matches the checked-in contract.
- Test probes remain on non-blocking event-loop execution across API, application, geographic
  validation, and persistence stages.

## 4. Run the Full Local Gate

```bash
./gradlew clean check quarkusIntTest
```

Expected result: all compilation, unit, integration, architecture, contract, and packaged tests
pass without disabled checks.

## 5. Optional Local Manual Smoke Test

Start only a local development profile wired to local PostgreSQL and a controlled Geographic
Reference stub:

```bash
./gradlew --console=plain quarkusDev
```

Do not expose the development listener beyond the local machine.

### Create a Minimal Natural Person

```bash
curl --fail-with-body \
  -X POST 'http://localhost:8080/internal/v1/parties' \
  -H 'Content-Type: application/json' \
  -H 'Tenant-Id: 018f47ea-4e72-7f52-9f2c-6b95849f1180' \
  -H 'User-Id: local-validator' \
  --data '{
    "type": "NATURAL_PERSON",
    "naturalPersonDetails": {
      "givenNames": "Ana Maria",
      "familyNames": "Example"
    }
  }'
```

Expected status: `201`.

Expected body shape:

```json
{
  "partyId": "a-generated-uuid-v7",
  "version": 0
}
```

### Create a Legal Entity

Configure the local Geographic Reference stub to report `EC` as active, then run:

```bash
curl --fail-with-body \
  -X POST 'http://localhost:8080/internal/v1/parties' \
  -H 'Content-Type: application/json' \
  -H 'Tenant-Id: 018f47ea-4e72-7f52-9f2c-6b95849f1180' \
  -H 'User-Id: local-validator' \
  --data '{
    "type": "LEGAL_ENTITY",
    "legalEntityDetails": {
      "legalName": "Example Company",
      "incorporationCountryCode": "EC"
    }
  }'
```

Expected status: `201`; response version is `0`.

### Reject Missing Tenant Context

```bash
curl --include \
  -X POST 'http://localhost:8080/internal/v1/parties' \
  -H 'Content-Type: application/json' \
  -H 'User-Id: local-validator' \
  --data '{
    "type": "NATURAL_PERSON",
    "naturalPersonDetails": {
      "givenNames": "Ana Maria",
      "familyNames": "Example"
    }
  }'
```

Expected status: `400` with `application/problem+json` and code `INVALID_CONTEXT`. The Geographic
Reference stub and persistence adapter must record no call.

### Reject an Inactive Country

Configure the local Geographic Reference stub to report `ZZ` as unknown or inactive and submit a
request containing that syntactically valid test code.

Expected status: `422` with code `INVALID_GEOGRAPHIC_REFERENCE`; no Party or outbox row is created.

## 6. Required Atomicity Matrix

The automated suite must prove each row below. Manual database manipulation is not a substitute.

| Scenario | Expected Party State | Expected Outbox State |
|----------|----------------------|-----------------------|
| Recording disabled | Complete aggregate committed | No event |
| Recording enabled, creation type excluded | Complete aggregate committed | No event |
| Recording enabled, `party.created.v1` included | Complete aggregate committed | Exactly one `PENDING` event |
| Detail insert failure | No rows from command | No event |
| Nationality insert failure | No rows from command | No event |
| Outbox insert failure | No rows from command | No event |
| Commit failure | No success response; verify rollback outcome | No partial event |

## 7. Tenant Isolation Matrix

Use at least two tenant UUIDs and distinct `User-Id` values. Verify:

- Each created Party and enabled event carries only its request's trusted tenant.
- A body-level `tenantId` is rejected.
- Missing, malformed, or repeated `Tenant-Id` is rejected before dependency or persistence calls.
- Missing, blank, oversized, or repeated `User-Id` is rejected before dependency or persistence
  calls.
- A request with 11 nationalities is rejected before geographic validation; requests with 0 and 10
  valid nationalities reach their expected validation path.
- A failed command for tenant A does not affect committed state for tenant B.

## 8. Observability Evidence

For applicable request paths, capture local test logs and verify the configured pattern is exactly:

```text
%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3}] (%t) [pid=%X{processId}] [user=%X{userId}] [tenantId=%X{tenantId}] %s%e%n
```

Verify `processId`, `userId`, and `tenantId` survive asynchronous boundaries when available. Logs,
errors, traces, and event payloads must not contain complete identifiers, names, dates, country
lists, provider payloads, SQL, or stack traces returned to clients.

## Completion Criteria

Validation is complete only when:

- Every final gate command exits with status `0`; each recorded Red command failed for its expected
  missing behavior.
- Every acceptance scenario in `spec.md`, including all 14 user-supplied minimum scenarios, is
  represented by automated tests.
- The full suite proves zero partial state for every injected failure boundary.
- OpenAPI and event-payload contracts validate.
- The final DBML, Flyway migrations, and Hibernate mappings correspond exactly.
- No test contacts a production endpoint or uses a production secret.
