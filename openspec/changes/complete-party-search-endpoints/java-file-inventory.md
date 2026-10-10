# Identifier Search Java Revision Inventory

## Recording rules

Track every new or modified Java file, including nested types, under production,
unit, shared-contract, and integration source sets. Compute the saved revision
with `git hash-object <path>` after each final edit. A changed revision
invalidates earlier diagnostics and relevant test evidence.

Record JDTLS Standard-mode diagnostics against the resolved Java 25 / Quarkus
3.40.1 Gradle model separately from SonarQube for IDE analysis. Sonar evidence
must identify analyzer version, active rules/parameters, completion, findings,
hotspots, remediation, and reanalysis for that exact saved revision. Standalone
IDE results do not establish a remote server Quality Gate.

## Affected files

Two test-only Java files are affected at the current foundation checkpoint.
They contain no nested types. Source findings remain independent of passing
Gradle tests.

| Source set | File / nested types | Saved Git blob | Relevant tests and result | JDTLS model / diagnostics | Sonar analyzer / rules / completion | Findings / hotspots / fixes / reanalysis |
| --- | --- | --- | --- | --- | --- | --- |
| Shared-contract | `src/contractTest/java/com/alexastudillo/partyregistry/contract/IdentifierSearchContractAssertions.java` | `91438a120588a40a17c0f20f89a64e98690746c5` | Four assertion tests pass; focused OpenAPI and architecture checks pass | Java 25 / Quarkus 3.40.1; 0 problems at 15:04:12 | SonarQube for IDE 5.7.0; 571 active Java rules, current resolved model, full analysis complete at 15:04:10 | 0 issues, 0 hotspots; raw Swagger model inference compilation errors corrected before this revision |
| Unit | `src/test/java/com/alexastudillo/partyregistry/contract/IdentifierSearchContractAssertionsTest.java` | `00a2f7dea3b39cc52a46b1262320382c5dfc06ee` | Four assertion tests pass; full-suite rerun pending | Java 25 / Quarkus 3.40.1; 0 problems at 15:08:43 | SonarQube for IDE 5.7.0; 571 active Java rules, current resolved model, full analysis complete at 15:08:43 | **1 issue**, 0 hotspots. Agent-accessible log exposes no rule/message/location; obtain the Problems entry before remediation/reanalysis. |

## Pending acceptance

- Per-file final-revision JDTLS and Sonar evidence.
- Full final-source `./gradlew test` and `./gradlew build` results.
- Actual native build and native HTTP integration execution.
- Requirement-to-test mapping, query measurements, and final boundary review.

Command/environment evidence and baseline timing investigation are maintained
separately in [verification.md](verification.md).
