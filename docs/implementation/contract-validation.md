# Contract Validation Harness — T036

This repository uses a build-time-only Java harness to validate OpenAPI 3.1 parsing and reference resolution, JSON Schema Draft 2020-12 structure, and every JSON event example discovered in the configured examples directory. It changes no runtime boundary or contract semantics.

## Test-scoped dependencies

| Dependency | Pinned version | Purpose |
|---|---:|---|
| `io.swagger.parser.v3:swagger-parser-v3` | `2.1.34` | Parse OpenAPI 3.1 YAML/JSON and resolve references without the umbrella artifact's Swagger 2 converter. |
| `com.networknt:json-schema-validator` | `1.5.6` | Compile Draft 2020-12 schemas and validate event examples. |

Both declarations use `testImplementation`; they are available to tests and the validation `JavaExec` task but are excluded from production compile and runtime classpaths. They are Apache-2.0 licensed upstream projects. Versions are fixed in `build.gradle.kts`, not dynamically selected and not allowed to alter the pinned Quarkus BOM.

## Commands

After the dependencies have been resolved once through the repositories declared in `build.gradle.kts`, run the self-tests and repository contracts without network access:

```text
./gradlew --offline test --tests com.alexastudillo.partyregistry.contractvalidation.ContractValidationHarnessTest
./gradlew --offline validateContracts
./gradlew --offline dependencies --configuration runtimeClasspath
./gradlew --offline dependencies --configuration testRuntimeClasspath
```

`validateContracts` defaults to these T003 locations:

```text
api/openapi/v1/party-registry.openapi.yaml
api/events/v1/party-registry-events.schema.json
api/events/v1/examples
```

Alternative repository paths can be supplied without editing the harness:

```text
./gradlew --offline validateContracts \
  -PcontractOpenApi=<openapi-file> \
  -PcontractEventSchema=<event-schema-file> \
  -PcontractEventExamples=<examples-directory>
```

The task exits nonzero for malformed OpenAPI, any unresolved OpenAPI reference, a non-Draft-2020-12 or invalid JSON Schema catalog, no discovered JSON examples, malformed examples, or an example that violates the catalog.

## Verification record

Executed for T036 on 2026-08-15:

| Command | Exit | Result |
|---|---:|---|
| `./gradlew test --tests com.alexastudillo.partyregistry.contractvalidation.ContractValidationHarnessTest` | `0` | Resolved the pinned test dependencies through the declared repositories; six positive/negative self-tests passed. |
| `./gradlew --offline test --tests com.alexastudillo.partyregistry.contractvalidation.ContractValidationHarnessTest --rerun-tasks` | `0` | Offline self-test rerun succeeded; the XML report records 6 tests, 0 failures, 0 errors, and 0 skipped. |
| `./gradlew --offline validateContracts` | `0` | Parsed the T003 OpenAPI 3.1 document with references resolved, compiled the Draft 2020-12 event catalog, discovered and validated both event examples. |
| `./gradlew --offline dependencyInsight --dependency io.swagger.parser.v3:swagger-parser-v3 --configuration runtimeClasspath` | `0` | No matching production runtime dependency. |
| `./gradlew --offline dependencyInsight --dependency com.networknt:json-schema-validator --configuration runtimeClasspath` | `0` | No matching production runtime dependency. |
| `./gradlew --offline dependencyInsight --dependency io.swagger.parser.v3:swagger-parser-v3 --configuration testRuntimeClasspath` | `0` | Resolved pinned version `2.1.34` only on the test runtime classpath. |
| `./gradlew --offline dependencyInsight --dependency com.networknt:json-schema-validator --configuration testRuntimeClasspath` | `0` | Resolved pinned version `1.5.6` only on the test runtime classpath. |

The independent dependency and supply-chain gate remains responsible for final vulnerability and license approval.
