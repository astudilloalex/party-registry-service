# Saved Java Revision Inventory

## Scope and collection

Inventory every new or modified Java file under `src/main/java`, `src/test/java`, `src/contractTest/java`, and `src/integrationTest/java`, including untracked files. Record all class/interface/record/enum declarations, including nested types. Source-set separation must match `build.gradle.kts`; shared contract sources are compiled for both test and integration test, and unit-test CDI fixtures must not enter packaged verification.

Before marking a Java-producing task complete:

1. Save the complete file and record its exact blob using `git hash-object <path>`.
2. Record the top-level and nested types, source set, owning task, and scenario IDs.
3. Obtain completed standalone Sonar analysis for that saved blob, recording timestamp, analyzed-file completion, active configuration, issue/Hotspot counts, and output location. A historical baseline or empty Problems panel does not prove analysis of this revision.
4. Independently record JDTLS validation for the same saved blob, including errors/warnings and project-model state. Investigate disagreements with Gradle.
5. Link focused executable red/green/refactor commands and outcomes in `verification.md`; characterize existing behavior separately.
6. After Java changes run `./gradlew test`, and before task completion run `./gradlew build`. Preserve command environment/deadline and report paths.
7. After any fix, replace the saved blob and rerun invalidated analysis/tests. Developer-supplied IDE evidence is acceptable only for the exact saved file inventory.

Do not use blanket exclusions, NOSONAR, unjustified suppressions, or weakened null analysis. New/modified valid date fixtures use `java.time.Month`; derived instants and clocks must preserve that convention. Every added/modified type has responsibility Javadoc.

## Changed Java files

The inventory was created before Java edits. Task 2.1 subsequently added the unit-test file below. No production/shared-contract/integration-test Java file or nested type has been changed.

| Path | Source set | Types, including nested declarations | Task / scenarios | Saved Git blob | Sonar completion / findings / evidence | JDTLS completion / diagnostics / evidence | Focused red / green / refactor evidence | Full test / build evidence |
|---|---|---|---|---|---|---|---|---|
| `src/test/java/com/alexastudillo/partyregistry/PartyIdentifierOpenApiStructureTest.java` | test | `PartyIdentifierOpenApiStructureTest`; no nested types | 2.1; Q01, Q09–Q10, M03–M06, M18–M20, M25–M26, M30, L01–L03, L19–L20 | `76d17934183320a020b204e414d1cd13f54fd7d9` | Full 1/1 test-file analysis completed 2026-10-03 12:24:58.629: **0 issues**, 0 Hotspots; `verification.md` links `.log:128–158` | Validation 2026-10-03 12:25:24.409: **0 problems**; `verification.md` links `.log:18786–18796` | Original six-test red followed by contract green; 9 structural checks pass. Full matrix/precedence/schema characterization added after initial green; `verification.md` task 2.1 | Full `./gradlew test`: 1,373 passing tests; `./gradlew build`: 28 packaged JVM tests pass, unit tests up-to-date; no native acceptance claimed |
| `src/test/java/com/alexastudillo/partyregistry/PartyIdentifierOpenApiExamplesTest.java` | test | `PartyIdentifierOpenApiExamplesTest`; no nested types | 2.2; Q01, Q16–Q21, M25–M26 | `b0b7995483b63ee4bb1eff29e4c171aa29461732` | Full 1/1 analysis 2026-10-03 12:39:09.680: **0 issues**, 0 Hotspots; current Sonar `.log:234–264` | Validation 12:44:43.733: **0 problems**; JDTLS `.log:18828–18838` | Six executable red failures; six example/schema tests green within 49 focused checks; `verification.md` task 2.2 | 1,379 full tests and 28 packaged JVM tests pass; no native acceptance claimed |
| `src/test/java/com/alexastudillo/partyregistry/OpenApiContractTest.java` | test | `OpenApiContractTest`; no nested types | 2.2; Q01 compatibility | `7bd4e4408c6fc8063f483adcb2538d558b1e8d30` | Full 1/1 analysis 2026-10-03 12:45:55.484: **0 issues**, 0 Hotspots; current Sonar `.log:265–295` | Validation 12:45:55.397: **0 problems**; JDTLS `.log:18840–18853` | Four additional-registration response-reference expectations updated to approved scoped components; all unrelated checks retained; focused/full green | 1,379 full tests and 28 packaged JVM tests pass |
| `src/test/java/com/alexastudillo/partyregistry/testing/PartyIdentifierFixtures.java` | test | `PartyIdentifierFixtures`; no nested types | 2.3; test foundation | `411d68a44f5a2250939033256cfaeacac5ff1c44` | Full 1/1 13:05:52.330: **0 issues/Hotspots**; Sonar `.log:349–379` | 13:05:53.168: **0 problems**; JDTLS `.log:18873–18883` | Controlled fixtures characterized; missing legal-country fixture input corrected without production changes | 1,383 full tests and 28 packaged JVM tests pass |
| `src/contractTest/java/com/alexastudillo/partyregistry/contract/PartyIdentifierContractAssertions.java` | shared contract, test + integrationTest | `PartyIdentifierContractAssertions`; no nested types | 2.3; Q18–Q21 foundation | `ab51dff5bdd6e1a37a41c2b1f83702cf6c4c09a5` | Full 1/1 13:06:10.583: **0 issues/Hotspots**; Sonar `.log:380–410` | 13:06:11.063: **0 problems**; JDTLS `.log:18885–18898` | Exact envelope and adversarial page/error checks; no CDI fixture | Both source sets compile; full/package checks pass |
| `src/test/java/com/alexastudillo/partyregistry/testing/PartyIdentifierFixturesTest.java` | test | `PartyIdentifierFixturesTest`; no nested types | 2.3; controlled clock/owner/projection foundation | `ae63ec50b3996cfed3a5e41a3db6893afcc8b470` | Full 1/1 13:06:43.048: **0 issues/Hotspots**; Sonar `.log:411–441` | 13:06:43.524: **0 problems**; JDTLS `.log:18900–18913` | Two characterization tests pass | Full/package checks pass |
| `src/test/java/com/alexastudillo/partyregistry/contract/PartyIdentifierContractAssertionsTest.java` | test | `PartyIdentifierContractAssertionsTest`; no nested types | 2.3; Q16–Q21 assertion foundation | `124da95f2503ed2b64e01babbdf942c0194d8446` | Full 1/1 13:16:03.418: **0 issues/Hotspots**; Sonar `.log:442–472` | 13:16:03.203: **0 problems**; JDTLS `.log:18914–18928` | Missing cursor/count overflow/status drift/leaked errors are rejected; two tests pass | Full/package checks pass |

## Baseline reference (not a changed file)

The existing `PartyIdentifierResource` baseline blob and actual standalone Sonar/JDTLS execution are recorded in `verification.md`, task 1.1. They do not satisfy analysis of future saved revisions.
