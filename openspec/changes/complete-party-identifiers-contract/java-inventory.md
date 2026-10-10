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

The first table retains earlier increment revisions. The issuing-entity table below supersedes its rows for files changed by the refinement; unchanged rows remain valid. Both tables cover production, unit-test and shared-contract sources. Native acceptance remains pending.

| Path | Source set | Types, including nested declarations | Task / scenarios | Saved Git blob | Sonar completion / findings / evidence | JDTLS completion / diagnostics / evidence | Focused red / green / refactor evidence | Full test / build evidence |
|---|---|---|---|---|---|---|---|---|
| `src/test/java/com/alexastudillo/partyregistry/PartyIdentifierOpenApiStructureTest.java` | test | `PartyIdentifierOpenApiStructureTest`; no nested types | 2.1; Q01, Q09–Q10, M03–M06, M18–M20, M25–M26, M30, L01–L03, L19–L20 | `76d17934183320a020b204e414d1cd13f54fd7d9` | Full 1/1 test-file analysis completed 2026-10-03 12:24:58.629: **0 issues**, 0 Hotspots; `verification.md` links `.log:128–158` | Validation 2026-10-03 12:25:24.409: **0 problems**; `verification.md` links `.log:18786–18796` | Original six-test red followed by contract green; 9 structural checks pass. Full matrix/precedence/schema characterization added after initial green; `verification.md` task 2.1 | Full `./gradlew test`: 1,373 passing tests; `./gradlew build`: 28 packaged JVM tests pass, unit tests up-to-date; no native acceptance claimed |
| `src/test/java/com/alexastudillo/partyregistry/PartyIdentifierOpenApiExamplesTest.java` | test | `PartyIdentifierOpenApiExamplesTest`; no nested types | 2.2; Q01, Q16–Q21, M25–M26 | `b0b7995483b63ee4bb1eff29e4c171aa29461732` | Full 1/1 analysis 2026-10-03 12:39:09.680: **0 issues**, 0 Hotspots; current Sonar `.log:234–264` | Validation 12:44:43.733: **0 problems**; JDTLS `.log:18828–18838` | Six executable red failures; six example/schema tests green within 49 focused checks; `verification.md` task 2.2 | 1,379 full tests and 28 packaged JVM tests pass; no native acceptance claimed |
| `src/test/java/com/alexastudillo/partyregistry/OpenApiContractTest.java` | test | `OpenApiContractTest`; no nested types | 2.2; Q01 compatibility | `7bd4e4408c6fc8063f483adcb2538d558b1e8d30` | Full 1/1 analysis 2026-10-03 12:45:55.484: **0 issues**, 0 Hotspots; current Sonar `.log:265–295` | Validation 12:45:55.397: **0 problems**; JDTLS `.log:18840–18853` | Four additional-registration response-reference expectations updated to approved scoped components; all unrelated checks retained; focused/full green | 1,379 full tests and 28 packaged JVM tests pass |
| `src/test/java/com/alexastudillo/partyregistry/testing/PartyIdentifierFixtures.java` | test | `PartyIdentifierFixtures`; no nested types | 2.3; test foundation | `411d68a44f5a2250939033256cfaeacac5ff1c44` | Full 1/1 13:05:52.330: **0 issues/Hotspots**; Sonar `.log:349–379` | 13:05:53.168: **0 problems**; JDTLS `.log:18873–18883` | Controlled fixtures characterized; missing legal-country fixture input corrected without production changes | 1,383 full tests and 28 packaged JVM tests pass |
| `src/contractTest/java/com/alexastudillo/partyregistry/contract/PartyIdentifierContractAssertions.java` | shared contract, test + integrationTest | `PartyIdentifierContractAssertions`; no nested types | 2.3; Q18–Q21 foundation | `ab51dff5bdd6e1a37a41c2b1f83702cf6c4c09a5` | Full 1/1 13:06:10.583: **0 issues/Hotspots**; Sonar `.log:380–410` | 13:06:11.063: **0 problems**; JDTLS `.log:18885–18898` | Exact envelope and adversarial page/error checks; no CDI fixture | Both source sets compile; full/package checks pass |
| `src/test/java/com/alexastudillo/partyregistry/testing/PartyIdentifierFixturesTest.java` | test | `PartyIdentifierFixturesTest`; no nested types | 2.3; controlled clock/owner/projection foundation | `ae63ec50b3996cfed3a5e41a3db6893afcc8b470` | Full 1/1 13:06:43.048: **0 issues/Hotspots**; Sonar `.log:411–441` | 13:06:43.524: **0 problems**; JDTLS `.log:18900–18913` | Two characterization tests pass | Full/package checks pass |
| `src/test/java/com/alexastudillo/partyregistry/contract/PartyIdentifierContractAssertionsTest.java` | test | `PartyIdentifierContractAssertionsTest`; no nested types | 2.3; Q16–Q21 assertion foundation | `124da95f2503ed2b64e01babbdf942c0194d8446` | Full 1/1 13:16:03.418: **0 issues/Hotspots**; Sonar `.log:442–472` | 13:16:03.203: **0 problems**; JDTLS `.log:18914–18928` | Missing cursor/count overflow/status drift/leaked errors are rejected; two tests pass | Full/package checks pass |
| `src/main/java/com/alexastudillo/partyregistry/domain/model/PartyIdentifierVersion.java` | main | `PartyIdentifierVersion`; no nested types | 3.1; M26 version foundation | `56ea77cfe91a169c5be4f2e4a7fac8c2f765c515` | Full 1/1 13:22:16.490: **0 issues/Hotspots**; Sonar `.log:539–569` | 13:22:16.613: **0 problems**; JDTLS `.log:18929–18943` | Executable overflow red then checked-next green; no framework imports | 1,387 full tests and 28 packaged JVM tests pass |
| `src/main/java/com/alexastudillo/partyregistry/domain/error/DomainViolation.java` | main | `DomainViolation`; no nested types | 3.1–3.2; neutral exhaustion/lifecycle classification | `05ca298a7cdafbbc2d8f3282981377b4e2095e7e` | Full 1/1 13:52:39.043: **0 issues/Hotspots**; Sonar `.log:978–1008` | 13:52:39.097: **0 problems**; JDTLS `.log:19221–19234` | Adds only neutral identifier exhaustion/lifecycle violations; affected switches compile; earlier revision retained in task 3.1 evidence | 1,398 full tests and 28 packaged JVM tests pass |
| `src/test/java/com/alexastudillo/partyregistry/domain/model/PartyIdentifierVersionTest.java` | test | `PartyIdentifierVersionTest`; no nested types | 3.1; version/audit/protected-history foundation | `b81a7c5558f259f7bb241ac22fc4dd639b95cae5` | Full 1/1 13:31:55.334: **0 issues/Hotspots**; Sonar `.log:788–818` | 13:31:55.532: **0 problems**; JDTLS `.log:19119–19132` | Four pure tests pass after executable overflow red; existing audit invariants characterized separately | Full/package checks pass |
| `src/main/java/com/alexastudillo/partyregistry/domain/model/PartyIdentifier.java` | main | `PartyIdentifier`, nested `Builder` | 3.2–3.3; metadata/lifecycle rules | `df1e41cfd8e18d90f7f8114dccb98cac81249709` | Full 1/1 14:01:48.078: **0 issues/Hotspots**; Sonar `.log:1151–1181` | 14:01:47.516: **0 problems**; JDTLS `.log:19263–19276` | Metadata regression and 20 lifecycle cases pass; earlier metadata revision remains documented in task 3.2 | Earlier 1,418 full tests and 28 packaged JVM tests pass; latest full gate has one legal-entity setup failure, not accepted as complete |
| `src/test/java/com/alexastudillo/partyregistry/domain/model/PartyIdentifierMetadataTest.java` | test | `PartyIdentifierMetadataTest`; no nested types | 3.2; M18–M24 metadata rules | `a667b6c361b8de9e585a1ad4e5b3fd618fa019a5` | Full 1/1 13:42:56.686: **0 issues/Hotspots**; Sonar `.log:872–902` | 13:47:38.626: **0 problems**; JDTLS `.log:19209–19219` | 11 cases green after 5 executable state/date failures; exception inputs prepared outside lambdas | Full/package checks pass |
| `src/test/java/com/alexastudillo/partyregistry/domain/model/PartyIdentifierLifecycleTest.java` | test | `PartyIdentifierLifecycleTest`, nested `Action` | 3.3; L05–L09, L15, L17 transition foundation | `ce781a68da1fcbc1be6d44b314e51e4d1c26dad4` | Full 1/1 14:23:27.454: **0 issues/Hotspots**, no preview warning in fresh saved-revision analysis; Sonar `.log:1182–1212` | 14:23:22.990: **0 problems**; JDTLS `.log:19290–19300` | 20 cases green after 15 executable failures; all 15 matrix entries exercise both Party types. Responsibility Javadoc clarified and reverified | Current focused lifecycle passes; latest full gate has 1/1,418 failure in existing legal-entity setup. Chained build did not run; task remains unchecked |

## Baseline reference (not a changed file)

The existing `PartyIdentifierResource` baseline blob and actual standalone Sonar/JDTLS execution are recorded in `verification.md`, task 1.1. They do not satisfy analysis of future saved revisions.

## Current issuing-entity refinement revisions

Paths below are relative to `src/`; every named file has responsibility Javadoc. Only `PartyIdentifier.Builder` is nested among these refinement types. The unchanged lifecycle test additionally has its nested `Action` enum as listed above.

Sonar evidence uses `~/.config/Antigravity IDE/logs/20261003T114758/window1/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log` (**S**). Independent JDTLS evidence uses the workspace `.metadata/.log` path recorded in `verification.md` (**J**). Each S range includes input-file identity, Java 25/571-rule configuration, full 1/1 completion and **0 issues/Hotspots**; each J range reports **0 problems**. Earlier findings on the normalizer regex and unused test context were followed by fixed-revision zero analyses, with no rule/configuration changes.

Log rotation note: on 2026-10-03 at 20:12 the earlier S ranges, including tasks 2–4.1, moved unchanged to the adjacent **`SonarQube for IDE.1.log`**. Use that rotated file for the historical line numbers above; it remains available and was inspected. Current task 4.2 evidence below distinguishes rotated and active output explicitly.

| Path / type | Task | Saved Git blob | Sonar result timestamp / lines | JDTLS result timestamp / lines |
|---|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/domain/model/PartyIdentifier.java`; `PartyIdentifier`, `Builder` | 2.4, 3.2–3.3 | `01ed5635b15e86f2b2d0f732afb910a489d4608a` | 16:34:45.633; S1351–1381 | 17:23:30.719; J19500–19510 |
| `main/java/com/alexastudillo/partyregistry/domain/error/DomainViolation.java`; `DomainViolation` | 2.4 | `31a21c22c9e5e9c803915c965140db36c7208876` | 17:23:28.779; S1450–1480 | 17:23:23.931; J19485–19498 |
| `main/java/com/alexastudillo/partyregistry/domain/normalization/IssuerCodeNormalization.java`; `IssuerCodeNormalization` | 2.4 | `1d63a3a7ebe83be1ac6df0b549f2fd1879933742` | 17:19:48.720; S1419–1449 | 18:07:04.664; J19874–19884 |
| `main/java/com/alexastudillo/partyregistry/api/model/request/ValidIssuerCode.java`; `ValidIssuerCode` annotation | 2.4 | `38a03ba56b74321cd520cde7dcccb3f7c4f488d9` | 17:23:39.666; S1481–1511 | 17:23:36.726; J19512–19525 |
| `main/java/com/alexastudillo/partyregistry/api/model/request/IssuerCodeValidator.java`; `IssuerCodeValidator` | 2.4 | `bb92b847bc42ba04d9eea80b2432764cf32eac98` | 18:09:05.538; S2014–2044 | 18:09:54.810; J19937–19947 |
| `main/java/com/alexastudillo/partyregistry/api/model/request/InitialPartyIdentifierCreateRequest.java`; same-named record | 2.4 | `fa8df4342dcaa668cd0a78ed9128e75d8ce969e5` | 17:23:53.370; S1543–1573 | 17:23:51.310; J19542–19555 |
| `main/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierCreateRequest.java`; same-named record | 2.4 | `b915a11ed56a24a4fcb32b7254e0f5dd2add0192` | 17:23:59.475; S1574–1604 | 17:23:56.049; J19557–19570 |
| `main/java/com/alexastudillo/partyregistry/api/error/PartyResponseCode.java`; `PartyResponseCode` enum | 2.4 | `7c8b9c8a9c58749610ca510ad2140722f1ef4d3c` | 17:24:04.031; S1605–1635 | 17:24:00.049; J19572–19585 |
| `test/java/com/alexastudillo/partyregistry/domain/normalization/IssuerCodeNormalizationTest.java`; same-named class | 2.4 | `323bfbef0fec785ee47687c24a339e3609952e8c` | 17:24:09.780; S1636–1666 | 17:24:08.315; J19587–19600 |
| `test/java/com/alexastudillo/partyregistry/api/model/request/IssuerCodeRequestValidationTest.java`; same-named class | 2.4 | `e2bed0a472ed0b3f01bfbf576a6eeac6bed33224` | 18:08:36.827; S1983–2013 | 18:11:01.220; J19949–19959 |
| `test/java/com/alexastudillo/partyregistry/domain/model/IssuerCodeWriteTest.java`; same-named class | 2.4, 3.2 | `7f3393cc426d0dfc34c982b11d4bebf75689eca1` | 17:24:24.598; S1698–1728 | 17:24:22.623; J19617–19630 |
| `test/java/com/alexastudillo/partyregistry/domain/model/PartyIdentifierMetadataTest.java`; same-named class | 3.2 | `082dc9e5f1dea0a63848bed21f9a39628e0de876` | 17:24:32.116; S1729–1759 | 17:24:28.949; J19632–19645 |
| `test/java/com/alexastudillo/partyregistry/api/model/request/PartyRequestDtoTest.java`; same-named class | 2.4 compatibility | `b85b1cec3173768f33f97b4bfc97d1a7bdcefccf` | 17:24:37.880; S1760–1790 | 17:24:35.143; J19711–19718 |
| `test/java/com/alexastudillo/partyregistry/api/model/request/NaturalPersonRequestTest.java`; same-named class | 2.4 compatibility | `adbd8db3370c2154a73e521cc38e1c6d5ff93ee8` | 17:24:47.067; S1791–1821 | 17:24:43.950; J19784–19791 |
| `test/java/com/alexastudillo/partyregistry/api/resource/IssuerCodeResourceContractTest.java`; same-named class | 2.4; R01–R03 | `425089c2cc3d4161f1e579899b3193c5ae0a0cb3` | 18:09:39.889; S2045–2075 | 18:09:39.589; J19925–19935 |
| `test/java/com/alexastudillo/partyregistry/PartyIdentifierOpenApiStructureTest.java`; same-named class | 2.1 | `04bbc17f9b9fabee07fc973bce269260f901dda7` | 17:58:23.604; S1921–1951 | 17:58:23.205; J19856–19872 |
| `test/java/com/alexastudillo/partyregistry/PartyIdentifierOpenApiExamplesTest.java`; same-named class | 2.2 | `5e4f39ce53ca9325cc6fb17f7426f2143f137ca7` | 18:07:34.094; S1952–1982 | 18:07:33.892; J19886–19899 |

Focused executable red/green and final full/package evidence is recorded in `verification.md`, issuing-entity continuation. Final JVM gates: **1,434 tests and 28 packaged tests, zero failures/skips**. The unchanged lifecycle-test blob remains `ce781a68da1fcbc1be6d44b314e51e4d1c26dad4`; its earlier source analysis is retained, and its 20 cases pass in this final full run. Native behavior is not inferred from these JVM gates.

JDTLS's Microsoft Java test-discovery delegate logged an internal `UnresolvedAnnotationBinding` NullPointerException while discovering the two existing DTO test classes at 17:24:34/43. Their document validations separately completed with zero problems and Sonar completed full analysis; Gradle compiled and executed them. This is retained as an IDE test-discovery diagnostic, not claimed as a source compiler warning or concealed by a setting change.

## Task 3.4 current revisions

This table supersedes the issuing-entity rows for the aggregate and violation enum. All five files have responsibility/contract Javadoc; no new nested types were added. S/J refer to the same logs above, and each analysis completed on 2026-10-03 with **0 issues/Hotspots / 0 source problems**. All eight new policy/plan cases and metadata/lifecycle/architecture regression checks pass. Full test and build each execute **1,442 tests**; packaged build additionally executes **28 JVM tests**, with zero failures/skips. See `verification.md` task 3.4 for red/green commands and separate gate durations.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/domain/model/PartyIdentifier.java`; `PartyIdentifier`, `Builder` | `95ba0a29cada11e847b1b7815fa68cf2ed1e82eb` | 18:29:18.070; S2217–2247 | 18:29:17.430; J19985–19995 |
| `main/java/com/alexastudillo/partyregistry/domain/error/DomainViolation.java`; enum | `4d78cf113b525ee7dff8037c5b34a66303071c47` | 18:27:48.558; S2186–2216 | 18:27:48.220; J19973–19983 |
| `main/java/com/alexastudillo/partyregistry/domain/model/PartyIdentifierPrimaryPlan.java`; record | `00050f12b9da8345c0b2ee8d786bb9c033515028` | 18:32:57.552; S2279–2309 | 18:32:57.513; J20012–20025 |
| `main/java/com/alexastudillo/partyregistry/domain/policy/PartyIdentifierPrimaryPolicy.java`; class | `6695daf3cae71426a7456d4fea5828014623287c` | 18:30:49.916; S2248–2278 | 18:30:50.086; J19997–20010 |
| `test/java/com/alexastudillo/partyregistry/domain/policy/PartyIdentifierPrimaryPolicyTest.java`; class | `4fb1ce4fa7ea3fd4e0cb269149cbc815c11232e3` | 18:33:24.286; S2310–2340 | 18:33:23.168; J20027–20040 |

## Task 4.1 current revisions

All paths are relative to `src/`. Types have responsibility Javadoc; nested types are explicitly identified. S/J are the same standalone Sonar and independent JDTLS logs above. Every listed final revision has a full 1/1 Java analysis with **0 issues/Hotspots** and **0 source problems**; no suppression or rule override was added. Final full/package evidence is **1,450 tests / 28 packaged JVM tests, zero failures/skips**, recorded separately in `verification.md`.

| Path / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierOperation.java`; enum | `5f2a57004a50a73dc6a24252acb7f1a7048bef6a` | 19:33:46.820; S2407–2437 | 19:33:42.516; J20042–20055 |
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierReason.java`; record | `d399b0e589c4ef6a48fdf546cfade8b072895e60` | 19:33:50.419; S2438–2468 | 19:33:48.526; J20057–20070 |
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierCreationRequest.java`; record | `8fcf217a11af00409fdc4c2cd674cd98c7165865` | 19:33:55.341; S2469–2499 | 19:33:55.312; J20072–20085 |
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierActionRequest.java`; record | `0c25ed6804f7657678b4869fb863eb32adf32f65` | 19:34:01.732; S2500–2530 | 19:34:01.846; J20087–20100 |
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierSearchCriteria.java`; record | `6ee56620c5ac8ab9fb22991b19a90f0972bee19b` | 19:34:07.732; S2531–2561 | 19:34:08.002; J20102–20115 |
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierSearchScope.java`; record | `ae015dea31e44f6868c96e03416924e92f5eba42` | 19:34:15.088; S2562–2592 | 19:34:15.020; J20117–20130 |
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierPagePosition.java`; record | `d4af141034cf64ffe7bb25b7b240cbe080cc9a36` | 19:34:21.553; S2593–2623 | 19:34:21.697; J20132–20145 |
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierPageBoundary.java`; record, nested `Direction` enum | `46a0d5eedffe86309371a7177435117f3ac58821` | 19:34:28.279; S2624–2654 | 19:34:27.609; J20147–20160 |
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierPageSlice.java`; record | `ab9d73341801c10986cba22fde7f89bd7ff60adf` | 19:34:33.943; S2655–2685 | 19:34:34.059; J20162–20175 |
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierPageResult.java`; record | `55c211483df34aab6006d5522d19b29a679325ed` | 19:34:40.302; S2686–2716 | 19:34:40.483; J20177–20190 |
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierMutationOutcome.java`; record, nested `Disposition` enum | `3b1e4c5d4b46e5a01b3497c205a30b4b29fc0e05` | 19:34:47.527; S2717–2747 | 19:34:47.830; J20192–20205 |
| `main/java/com/alexastudillo/partyregistry/application/query/GetPartyIdentifierQuery.java`; record | `84a2978760db81a1f8559ffb736b466c35d5f191` | 19:34:54.297; S2748–2778 | 19:34:54.494; J20207–20220 |
| `main/java/com/alexastudillo/partyregistry/application/query/ListPartyIdentifiersQuery.java`; record | `68fc7609ea449ec8896ef6daee82b27d7c44f07d` | 19:35:08.064; S2779–2809 | 19:35:08.237; J20222–20253 |
| `main/java/com/alexastudillo/partyregistry/application/command/PartyIdentifierCommandKeys.java`; class | `08a24dad8ac2e338f90103428b411cd2e9653479` | 19:35:08.366; S2810–2840 | 19:35:08.220; J20222–20253 |
| `main/java/com/alexastudillo/partyregistry/application/command/PatchPartyIdentifierCommand.java`; record | `f4eef79e06b5fbd046467be2c08505b5a603ffca` | 19:35:13.960; S2841–2871 | 19:35:14.164; J20255–20268 |
| `main/java/com/alexastudillo/partyregistry/application/command/ChangePartyIdentifierLifecycleCommand.java`; record | `0426a8d1879b6b60ddad6cce4e1fce5897f9e0d5` | 19:35:24.023; S2934–2964 | 19:35:24.226; J20294–20304 |
| `main/java/com/alexastudillo/partyregistry/application/command/SetPrimaryPartyIdentifierCommand.java`; record | `f3fc073fbd8256f93fec66031524d9295d4cbc53` | 19:35:27.755; S2965–2995 | 19:35:27.509; J20306–20319 |
| `main/java/com/alexastudillo/partyregistry/application/command/RegisterPartyIdentifierCommand.java`; record | `ec78227e715d0a1968eba18a2b7e7a85c2102440` | 19:35:33.117; S2996–3026 | 19:35:33.188; J20321–20334 |
| `main/java/com/alexastudillo/partyregistry/application/model/ObservedOperation.java`; enum | `370cf9647d1fc464c8496d5d6ae41dfdbc14d329` | 19:35:39.579; S3027–3057 | 19:35:39.796; J20336–20349 |
| `main/java/com/alexastudillo/partyregistry/infrastructure/observability/MicrometerOpenTelemetryOperationObserver.java`; class, existing nested `TelemetryObservation` | `a34245836d20fa52fdc31f5e4ed999e1db78c431` | 19:35:46.957; S3058–3088 | 19:35:46.574; J20351–20364 |
| `test/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierApplicationContractTest.java`; class | `657128a05da83435495b963d1168e133543e7c4a` | 19:35:53.481; S3089–3119 | 19:35:53.622; J20366–20379 |

The lifecycle command had a transient IDE document parse error (`chpackage`, S2919–2920/J20285–20292). Do not treat that analysis's zero issue count as a successful parse. The subsequent current document fully parsed and completed zero-finding Sonar and independent zero-problem JDTLS; its saved source starts with `package` and the final build compiled/executed it. Current successful analyses above supersede the transient document result.

## Task 4.2 saved revisions: focused green, full gate blocked

All eight files have responsibility/contract Javadoc and no nested declarations. S1 is the rotated `SonarQube for IDE.1.log`; S is the current active log; J is the unchanged JDTLS path above. Every listed revision reports full Java analysis with **0 issues/Hotspots** and independent **0 source problems**. These results do not close the task: the full test run failed in existing legal-entity setup and the chained build did not execute.

| Path relative to src / type | Saved Git blob | Sonar completion / source | JDTLS completion / lines |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/application/model/CompletedPartyIdentifierMutation.java`; record | `0bd736fe3a48d2128d68dbd3eb4be3e4a589bef5` | 20:08:58.676; S1:3235–3265 | 20:12:15.873; J21014–21024 |
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierActionAudit.java`; record | `d308cc12f3e2e8b4fb16cec3bb052b49e4d6f0e4` | 20:09:06.612; S1:3266–3296 | 20:12:25.884; J21026–21036 |
| `main/java/com/alexastudillo/partyregistry/application/port/PartyIdentifierQueryPort.java`; interface | `ef304c03a9845d40be3734b99a407c2bf8fb00b3` | 20:13:19.947; S:116–146 | 20:13:17.800; J21146–21159 |
| `main/java/com/alexastudillo/partyregistry/application/port/PartyIdentifierCursorPort.java`; interface | `47c72b0eab4e444da213a0a3840e90da0db1632a` | 20:12:38.632; configuration at end of S1, completion S:1–22 | 20:12:38.060; J21086–21099 |
| `main/java/com/alexastudillo/partyregistry/application/port/PartyIdentifierFingerprintPort.java`; interface | `093ef04ad53af50df1ff5d240a6febef00d26ed4` | 20:12:46.195; S:23–53 | 20:12:43.881; J21101–21114 |
| `main/java/com/alexastudillo/partyregistry/application/port/PartyIdentifierMutationPort.java`; interface | `c570a0ebdd0d09264045992e0c85339cfa8349ee` | 20:13:11.644; S:85–115 | 20:13:11.264; J21131–21144 |
| `main/java/com/alexastudillo/partyregistry/application/port/PartyIdentifierMutationContext.java`; interface | `89dd8b98bf1dfdadbc2829ec153d3a60694e8347` | 20:12:54.462; S:54–84 | 20:12:51.034; J21116–21129 |
| `test/java/com/alexastudillo/partyregistry/application/port/PartyIdentifierPortContractTest.java`; class | `c97a8c7cc8d4f633efef8a4c10613908ed2e517b` | 20:13:30.181; S:147–177 | 20:13:29.459; J21161–21174 |

## Shared Geographic Reference fixture correction

The previously blocked task 4.2 gate now passes on the same port/model revisions after fixing the shared test fixture's global 100ms timeout override. Only the following test-source revisions were added/modified for that correction. All have responsibility Javadoc; existing nested `TrustedHeaders` and `GeographicReferenceProfile` retain their Javadoc. Each current analysis reports **0 issues/Hotspots** and independent **0 source problems**. S is the current active log and J is the same workspace log above. Required final checks pass **1,456 tests and 28 packaged JVM tests, zero failures/skips**; native final acceptance remains pending.

| Path relative to src / types | Saved Git blob | Sonar completion / lines | JDTLS completion / lines |
|---|---|---|---|
| `test/java/com/alexastudillo/partyregistry/infrastructure/integration/geographic/GeographicReferenceStubResource.java`; class, nested `TrustedHeaders` record | `58e4e5e1a1f5c69ef90f5bfe8d19523199279ad9` | 21:05:25.708; S432–462 | 21:05:24.347; J21185–21198 |
| `test/java/com/alexastudillo/partyregistry/infrastructure/integration/geographic/GeographicReferenceStubResourceTest.java`; class | `a3864050caf7fef58f5e37e312ed5d0a3d916d57` | 21:05:30.366; S463–493 | 21:05:30.406; J21200–21213 |
| `test/java/com/alexastudillo/partyregistry/infrastructure/integration/geographic/GeographicReferenceAdapterTest.java`; class, nested `GeographicReferenceProfile` class | `ad5887ac429f65471aab1591c45e2fc199b35361` | 21:05:37.300; S494–524 | 21:05:37.001; J21215–21228 |

## Task 4.3 final saved revisions

S is the current active Sonar log and J is the workspace JDTLS log above. All nine final revisions have full 1/1 Java analysis with **0 issues/Hotspots** and independent **0 source problems**. New classes and nested failure records have responsibility Javadoc. No settings, exclusions or suppressions were changed. The final gates pass **1,460 tests and 28 packaged JVM tests**, without failures/skips; see `verification.md` task 4.3.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/application/error/ApplicationFailure.java`; sealed interface and existing nested records plus `IdentifierNotFound`, `IdentifierVersionMismatch`, `InvalidIdentifierLifecycle`, `PrimaryIdentifierConflict`, `IdentifierVersionExhausted`, `InvalidIdentifierCursor` | `ca5e0a9b44f987afe9237cd995c434b407ded006` | 21:53:42.769; S888–918 | 21:54:24.623; J21389–21399 |
| `main/java/com/alexastudillo/partyregistry/application/error/ApplicationException.java`; class | `e0c7a8e192d0b36352abec5fd5081dda49c1c11e` | 21:37:22.100; S622–652 | 21:37:21.209; J21257–21270 |
| `main/java/com/alexastudillo/partyregistry/api/error/PartyApiErrorTranslator.java`; class | `d8d5c51f194f20e6d9fecff459fba8032d62d800` | 21:37:28.638; S653–681 | 21:37:27.671; J21272–21285 |
| `main/java/com/alexastudillo/partyregistry/api/error/PartyResponseCode.java`; enum | `860c00c6347e9968cd406f81928b9b9f510fa0a9` | 21:37:33.977; S682–712 | 21:37:33.990; J21287–21300 |
| `main/java/com/alexastudillo/partyregistry/application/observability/OperationObservation.java`; class, no nested declarations | `c56120bd200d00c2635b96a2e41291d96486e761` | 21:37:41.316; S713–741 | 21:37:41.097; J21302–21315 |
| `test/java/com/alexastudillo/partyregistry/api/error/PartyIdentifierFailureTranslationTest.java`; class | `d122059f68babee79e94facc4090ff562dcd10a1` | 21:37:47.993; S742–772 | 21:37:47.940; J21317–21330 |
| `test/java/com/alexastudillo/partyregistry/api/error/PartyIdentifierErrorVerificationResource.java`; test-only class | `68aef73d4f5071e2a88fafbc789d191c0e972024` | 21:37:55.529; S773–803 | 21:37:55.574; J21332–21345 |
| `test/java/com/alexastudillo/partyregistry/api/error/PartyIdentifierErrorContractTest.java`; class | `584a7d4e70ee65b49f604e336043c0f879e18da8` | 21:38:02.596; S804–834 | 21:38:02.741; J21347–21360 |
| `test/java/com/alexastudillo/partyregistry/application/error/ApplicationExceptionTest.java`; class, existing nested `FailureDescription` record | `193d352a2c575ae3e7ea408ccd27a3a209631381` | 21:42:28.816; S857–887 | 21:42:28.930; J21362–21375 |

The first extended failure-interface revision reported two Sonar issues. Identifier-specific version null-check descriptions remove the newly duplicated validation literals, and the final saved interface is reanalyzed with zero findings. Its earlier two-issue analysis is not substituted for final acceptance. No precise rule-key claim is inferred from the count-only output.

## Task 5.1 final saved revisions

S2 is `~/.config/Antigravity IDE/logs/20261004T103628/window2/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log`. J remains the project JDTLS log documented above. All three files are test sources with responsibility Javadoc. Each listed analysis is full 1/1, Java 25/571 rules, **zero issues/Hotspots**; independent JDTLS validation reports **zero problems**. Final test/build pass **1,468 tests / 28 packaged JVM tests**; `verification.md` records red/green, fixture corrections and actual commands.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `test/java/com/alexastudillo/partyregistry/PartyIdentifierContractMigrationTest.java`; class, nested `Owner`, `Completion`, `Audit` records | `edd933dc44e520bb96c493ea814a687af6d6072b` | 10:53:41.445; S2:277–307 | 10:53:40.627; J21662–21675 |
| `test/java/com/alexastudillo/partyregistry/MigrationRegressionTest.java`; class, existing nested `ColumnDefinition` record | `2728a37ab0a87e5db1a6879f0386cab154fa1851` | 10:50:21.150; S2:169–199 | 10:50:19.783; J21641–21654 |
| `test/java/com/alexastudillo/partyregistry/IdentifierSchemeTestFixtureTest.java`; class, no nested types | `874938d60b71e648fc14f6cd52a4e90fd1868102` | 11:08:41.167; S2:795–825 | 11:08:41.955; J21887–21900 |

## Task 5.2 saved revision: runtime passes, Sonar finding pending

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `test/java/com/alexastudillo/partyregistry/PartyIdentifierContractUpgradeTest.java`; class, no nested types | `b2558714f6c637adf034c2202356b7839397e14f` | 11:23:52.924; S2:879–909; full 1/1 Java 25/571 rules, **1 issue / 0 Hotspots**; diagnostic details unavailable in log | 11:23:53.700; J21917–21927; **0 problems** |

The nine focused migration cases and full **1,469 tests** pass. Build succeeds up-to-date; no new packaged/native execution is inferred. Responsibility Javadoc and Month-based valid fixtures are present. The task remains incomplete until Problems-view details identify the current Sonar finding, a corrected revision has zero findings, and invalidated verification is rerun. No guessed rule key or historical zero-result substitutes for that evidence.

### Task 5.2 final revision (supersedes the pending row)

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `test/java/com/alexastudillo/partyregistry/PartyIdentifierContractUpgradeTest.java`; class, no nested types | `ae4447b8b83ac36c569fefea82455b26a5442983` | 11:39:19.989; S2:932–962; full 1/1 Java 25/571 rules, **0 issues/Hotspots** | 11:39:21.271; J21929–21939; **0 problems** |

The supplied java:S5961 assertion-count diagnostic is resolved by two responsibility-focused tests, retaining every assertion. Ten focused migration cases and full **1,470 tests** pass; build succeeds up-to-date. No compiler warning, exclusion, suppression or Month-fixture regression is introduced.

## Task 6.1 final saved revisions

S2/J are the current log paths above. Each listed file has full Java-25/571-rule 1/1 analysis with **zero issues/Hotspots**, independent **zero source problems**, English responsibility Javadoc and no new suppression/configuration change. Full **1,477 tests and 28 packaged JVM tests** pass; red/green and actual commands are in `verification.md`.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/infrastructure/security/HmacPartyIdentifierCursorAdapter.java`; class, no nested types | `f42da0ea6f14d237991554b8ae2822ea8177c47a` | 11:52:18.110; S2:985–1015 | 11:52:16.817; J21941–21954 |
| `test/java/com/alexastudillo/partyregistry/infrastructure/security/HmacPartyIdentifierCursorAdapterTest.java`; class, nested `TestConfiguration` record | `2ae052a117cdc0f832d25f4b4a0185083a1fb325` | 11:55:22.019; S2:1016–1046 | 11:55:20.751; J21956–21969 |

## Task 6.2 final saved revisions

Each S2/J analysis below corresponds to the saved revision, full 1/1 Java-25/571-rule Sonar with **0 issues/Hotspots**, independent **0 JDTLS problems**. Both files have responsibility Javadoc and no nested declarations. Focused detail/current-reader/architecture, **1,481 full tests and 28 packaged JVM tests** pass as recorded in `verification.md`.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierDetailReader.java`; class | `b78aeb32f87773583299b41d580a3ab4b6de2e46` | 12:10:43.057; S2:1069–1099 | 12:10:43.402; J21971–21984 |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierDetailReaderTest.java`; class | `b70390d82053101832d4357b1a9591b162465e92` | 12:12:55.859; S2:1100–1130 | 12:12:56.351; J21986–21999 |

## Task 6.3 saved revisions: source/unit/database green, packaged gate blocked

All three files have responsibility Javadoc and no nested declarations. S2/J are the unchanged current log paths above. Each final revision completes full 1/1 Java-25/571-rule Sonar with **zero issues/Hotspots** and separate **zero JDTLS problems**. Nine query cases, detail/architecture regression and **1,490 full tests** pass. Packaged build fails at container startup (two failures/ten skipped), so task 6.3 remains incomplete; `verification.md` records the actual setup error and subsequent post-import-removal full test.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierPageSql.java`; class | `3683f726aaf6c2452dc3ff9cc2e56095d7b5a8f4` | 12:33:26.177; S2:1462–1492 | 12:33:26.776; J22085–22098 |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/HibernateReactivePartyIdentifierQueryAdapter.java`; class | `e3635db2f29ff4eeeffe4c0328d107993a13a5c6` | 12:36:53.073; S2:1559–1589 | 12:36:53.517; J22100–22113 |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierHistoricalPageTest.java`; class | `82689bfa218465261a233d83947cc83fed8c0ee7` | 12:56:39.018; S2:1621–1651 | 12:56:34.138; J22145–22152 |

The prior test blob `f850ce7a178e6b67372e78b56f76837edb27a8c0` reported one Sonar issue and one JDTLS problem. Code inspection found its unused Optional import; the final saved revision removes it and both independent analyses report zero. No suppression, rule/configuration change or precise inferred rule key is added. Full post-Java tests pass on the final blob; packaged/native acceptance is not inferred.

Task 6.3 runtime continuation supersedes the packaged blocker above: a fresh build using the transient test-scoped Podman API (`--time=0`) executes and passes all **28 JVM packaged cases with no skips**, on these same three verified blobs. No Java source or local analysis configuration changed. The earlier failed attempt and source findings remain historical evidence; native final verification is still pending.

## Task 6.4 final saved revisions

S2/J are the unchanged standalone Sonar/JDTLS paths above. All three saved files complete full 1/1 Java-25/571-rule Sonar with **zero issues/Hotspots**, plus independent **zero JDTLS problems**. Responsibility Javadoc includes the nested Harness. Nine focused workflow cases and architecture, **1,499 full tests and 28 actual packaged JVM tests** pass with the stable test-scoped Podman API; see `verification.md` for separate red/green and full gate executions.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/application/usecase/GetPartyIdentifierUseCase.java`; class | `89a07b404ff2ee9f45b2260228dcdeec46fae869` | 15:41:28.380; S2:1652–1682 | 15:41:27.610; J22154–22167 |
| `main/java/com/alexastudillo/partyregistry/application/usecase/ListPartyIdentifiersUseCase.java`; class | `76f8976299947dec155edcc845dc8f24dd4faf7f` | 15:44:05.156; S2:1683–1713 | 15:44:05.807; J22169–22182 |
| `test/java/com/alexastudillo/partyregistry/application/usecase/PartyIdentifierQueryUseCasesTest.java`; class, nested `Harness` | `3d586630934c201c9c7c9bef1e2335599c46a490` | 15:58:06.960; S2:1714–1744 | 15:58:03.243; J22184–22197 |

## Task 6.5 saved revisions: runtime green, production Sonar finding pending

All four files have responsibility Javadoc and no nested declarations. Each Sonar entry is actual full 1/1 Java-25/571-rule analysis for the saved revision, with separate current JDTLS validation. Ten parser/support cases, compatibility/architecture regression, **1,509 full tests and 28 packaged JVM tests** pass. One production-parser Sonar finding prevents task completion; its rule/message/location is absent from the log and must be supplied from Problems before a targeted fix. No guessed rule key, historic result or compiler pass substitutes for this evidence.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierQueryParameters.java`; class | `2240bf726ff05e2993d62b93249eebc2ded7cb00` | 16:08:20.574; S2:1745–1775; **1 issue / 0 Hotspots** | 16:08:20.689; J22199–22212; **0 problems** |
| `main/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierRequestSupport.java`; class | `8e407d6c83e0921ddb70f012cc34d5212c866ea0` | 16:11:38.223; S2:1776–1806; **0 issues/Hotspots** | 16:11:38.777; J22214–22227; **0 problems** |
| `test/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierQueryParametersTest.java`; class | `b62b3bf3575d645b30ed44884bbbb360b9f28adb` | 16:20:52.804; S2:1807–1837; **0 issues/Hotspots** | 16:20:53.384; J22229–22242; **0 problems** |
| `test/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierRequestSupportTest.java`; class | `2df2bbc682ebbc6ffcec91384c7be67d7b761476` | 16:22:32.144; S2:1838–1868; **0 issues/Hotspots** | 16:22:32.759; J22244–22257; **0 problems** |

### Task 6.5 final parser revision (supersedes the pending row)

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierQueryParameters.java`; class | `469f984dcee2369b46d4f85c534408833100de5b` | 17:14:34.047; S2:1869–1899; full 1/1 Java 25/571 rules, **0 issues/Hotspots** | 17:14:34.718; J22271–22281; **0 problems** |

The supplied concise-character-class finding is resolved with the Java `"\\d+"` literal. Existing malformed/Unicode/limit/filter cases and **1,509 full tests / 28 actual packaged JVM cases** pass on this final source, with no rule configuration or warning suppression. The other three task 6.5 revisions are unchanged; their zero analyses above remain applicable.

## Task 6.6 final saved revisions

Each listed S2/J entry is final saved-source full 1/1 Java-25/571-rule Sonar with **zero issues/Hotspots**, separate **zero JDTLS problems**. All types retain responsibility Javadoc; the new test includes its nested Unrelated record. Seven managed mapping cases, pagination/architecture/current-identifier compatibility, **1,516 full tests and 28 actual packaged JVM cases** pass. `verification.md` preserves each red/failure/green gate and the schema-based nullable-shape reconciliation.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/api/mapper/PartyIdentifierApiMapper.java`; class | `3ae92bc31da737a415a2a916b8859a5f5b3b8a78` | 17:40:28.559; S2:1900–1930 | 17:40:24.490; J22283–22296 |
| `main/java/com/alexastudillo/partyregistry/api/model/response/PartyIdentifierResponse.java`; record | `9fa3b40cdea7ea7f5d749b68f4032a863aef21b0` | 17:48:33.283; S2:1931–1961 | 17:48:31.189; J22298–22311 |
| `test/java/com/alexastudillo/partyregistry/api/mapper/PartyIdentifierPaginationMappingTest.java`; class, nested `Unrelated` record | `bd347c29a28104a2b7528a7678b7404c82e6b4cc` | 18:07:33.016; S2:1962–1992 | 18:07:30.398; J22313–22326 |
| `test/java/com/alexastudillo/partyregistry/api/resource/NaturalPersonResourceContractTest.java`; class, no nested types | `4fbcd1b33cd5cd1cebc295293b949b51458b9d47` | 18:12:00.868; S2:1993–2023 | 18:11:58.203; J22328–22341 |
| `integrationTest/java/com/alexastudillo/partyregistry/PackagedApplicationIT.java`; class, no nested types | `1266875702ba27060df42ebc002962b6838442b5` | 18:34:02.533; S2:2055–2085 | 18:34:03.420; J22358–22371 |

The packaged test's earlier missing-import compilation/JDTLS diagnostics are resolved on the final source above; the prior zero-Sonar count does not substitute for resolved compilation. No date fixture, assertion responsibility, rule setting or suppression was altered beyond the approved optional-null current-item shape.

## Task 6.7 final saved revisions

Each listed S2/J entry represents actual final saved-source full 1/1 Java-25/571-rule Sonar with **zero issues/Hotspots**, separate **zero JDTLS problems**. Responsibility Javadoc covers all types and nested Fixture. Thirty-seven actual GET cases plus architecture, **1,553 full tests and 28 actual packaged JVM cases** pass; `verification.md` records missing-route/pagination red and final commands. No new warning or rule setting/suppression is introduced.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierResource.java`; class | `6d43d52b2789aa948bed0f073cf3cb7e62255d99` | 18:59:21.042; S2:2117–2147 | 18:59:20.919; J22388–22401 |
| `main/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierQueryOperations.java`; class | `bb8e29d94289a81afbbfe1d719dfd3da12384128` | 18:55:34.823; S2:2086–2116 | 18:55:35.192; J22373–22386 |
| `main/java/com/alexastudillo/partyregistry/infrastructure/configuration/ApplicationUseCaseProducer.java`; class | `8dc7dc9286ea7e50fbdbd1f7e72a48eb232b0d8a` | 19:09:02.852; S2:2148–2178 | 19:08:57.826; J22403–22416 |
| `test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierReadResourceContractTest.java`; class, nested `Fixture` record | `8f0eae50488e39673c8134da4990c8ea5ad4ca5c` | 19:09:55.608; S2:2179–2209 | 19:09:55.531; J22424–22431 |

## Task 7.1 final saved revisions

Both S2/J entries are final-source full 1/1 Java-25/571-rule Sonar with **zero issues/Hotspots**, independent **zero JDTLS problems**. Responsibility Javadoc covers both nested technical types. Eight exact-input HMAC cases, existing fingerprint/architecture regression, **1,561 full tests and 28 actual packaged JVM cases** pass. See `verification.md` for independent byte vectors, executable red/green and actual full commands; no new warning/exclusion/suppression is introduced.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/infrastructure/security/HmacPartyIdentifierFingerprintAdapter.java`; class, nested `InputWriter` interface and `MacSink` class | `4c0deffd4a9b0755c8f5a19a204ee7e5ae95e449` | 19:24:44.659; S2:2210–2240 | 19:24:44.310; J22433–22446 |
| `test/java/com/alexastudillo/partyregistry/infrastructure/security/HmacPartyIdentifierFingerprintAdapterTest.java`; class, no nested types | `1c5a4dcb241e02f54e216e6a01bffd1545610181` | 19:27:36.416; S2:2241–2271 | 19:27:36.690; J22448–22461 |

## Task 7.2 saved revisions: runtime green, one test finding pending

S2/J refer to the current log paths above; every analysis is full 1/1 Java 25 with 571 active rules on 2026-10-04. All seven revisions have responsibility Javadoc, no compiler warning and independent **zero JDTLS problems**. Sonar reports **zero issues/Hotspots** for six files and **one issue / zero Hotspots** for the replay persistence test. Rule/message/location are not available in the count-only log; task 7.2 remains incomplete until the same-revision Problems diagnostic is resolved. The final full gates pass **1,571 tests and 28 actual packaged JVM cases, no failures/skips**. Executable corruption red/green, same-session durability/rollback and unchanged earlier codec regressions are recorded in `verification.md`; no native result is inferred.

| Path relative to src / types | Saved Git blob | Sonar completion / range / result | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierCompletionCodec.java`; class | `bc743b4c7a5bf3b39f6a544e941c1ee466b20e58` | 20:41:53.400; S2:2427–2457; zero | 20:41:53.588; J22535–22545; zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierSnapshotFields.java`; class | `283029774ac1ed2c600df37a0f95ea832df4b718` | 20:36:01.696; S2:2334–2364; zero | 20:36:12.926; J22493–22503; zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierIdempotencyRecordId.java`; class | `a731f08db4f4d6e247e4475779544b7c41ac9a35` | 20:39:01.579; S2:2365–2395; zero | 20:39:01.901; J22505–22518; zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierIdempotencyRecordEntity.java`; class | `ed1326b00731fa0583772327eeeb2b0d76d08f84` | 20:39:46.322; S2:2396–2426; zero | 20:39:46.663; J22520–22533; zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierReplayPersistence.java`; class | `310b12f13c13ea6f288190bd585483e7bc0e8e08` | 20:51:58.978; S2:2458–2488; zero | 20:51:57.229; J22559–22572; zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierCompletionCodecTest.java`; class | `06129665e9f81faa62c89b48bf77503fd6647b63` | 20:52:15.867; S2:2489–2519; zero | 20:52:15.582; J22574–22587; zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierReplayPersistenceTest.java`; class, nested `Fixture` record | `c33e3bbc1c4972743481aa151ff6b228361aeae4` | 20:52:33.532; S2:2520–2550; **one issue / zero Hotspots**, diagnostic pending | 20:52:33.257; J22589–22602; zero |

### Task 7.2 final storage-test revision (supersedes the pending row)

| Path relative to src / types | Saved Git blob | Sonar completion / range / result | JDTLS completion / range |
|---|---|---|---|
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierReplayPersistenceTest.java`; class, nested `Fixture` record | `58a0cf3162d2ea918729add00cc4d32d5d543520` | 21:16:23.655; S2:2551–2581; full 1/1 Java 25/571 rules, **zero issues/Hotspots** | 21:16:23.516; J22613–22623; **zero problems** |

The supplied java:S1612 finding is resolved with `Assertions::assertNull` and no assertion removal. Full tests pass all 1,571 cases; build passes up-to-date, retaining the previous 28 actually executed packaged JVM results. All seven final task-7.2 saved revisions now satisfy the local zero-finding/source-warning gate; see the continuation in `verification.md`.

## Task 7.3 final saved revisions

Every S2 range below is full 1/1 Java-25/571-rule standalone analysis on 2026-10-04 with **zero issues/Hotspots**. Independent J validation reports **zero source problems**. Responsibility Javadoc covers the nested Fixture; no suppression/exclusion/configuration change was introduced. Four codec/four PostgreSQL cases, completion/replay/architecture regression, **1,579 full tests and 28 actual packaged JVM cases** pass. `verification.md` records independent byte vectors, behavioral red/green, supported native SQL binding, exact private attribution/constraints/rollback and separate IDE test-discovery internal errors.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierReasonCodec.java`; class | `5154bbbd27b63c4c279585001c6b9c0a5589300d` | 21:42:41.653; S2:2582–2612 | 21:42:40.263; J22625–22638 |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierAuditPersistence.java`; class | `b6ba19ba6854acdc6c4dfbf20f053b0848bec2f3` | 21:54:12.062; S2:2706–2736 | 21:54:12.034; J22797–22813 |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierReasonCodecTest.java`; class | `7f7d2b2349606e66f2e0d081692308121f1a0d31` | 21:53:17.115; S2:2644–2674 | 21:53:12.617; J22655–22668 |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierAuditPersistenceTest.java`; class, nested `Fixture` record | `4febe5ae7821f7a2435397b8d9fddc02f15f86ca` | 21:54:12.420; S2:2737–2767 | 21:54:12.143; J22797–22813 |

The earlier persistence blob `a3a1f372c239fa943f220f5bf55d6fe757ce59b1` reports one issue at 21:45:01.883, S2:2613–2643. Renaming the reviewed restricted method identifier removes that source concern; final analysis above supersedes it without attributing a rule key not present in the output.

## Task 7.4 initial saved revisions: partial behavior, reader findings pending

All four S2 analyses are full 1/1 Java-25/571-rule runs on 2026-10-04; all J validations report **zero source problems**. English responsibility Javadoc covers nested Scope/Fixture; no warning suppression/exclusion/configuration change was introduced. Three identity/two qualified-reader tests and latest full **1,584 tests / 28 actually executed packaged JVM cases** pass. Two production-reader Sonar issues remain unidentified by count-only output, and required coordinated lock/restoration checks remain incomplete; task 7.4 is not accepted. See `verification.md` for separate red/green, API typing correction and pending coverage.

| Path relative to src / types | Saved Git blob | Sonar completion / range / result | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierMutationLocks.java`; class, nested `Scope` record | `05d68063837971a101108bfb2d62045b3e12e6f0` | 22:10:42.769; S2:2768–2798; **zero issues/Hotspots** | 22:10:42.973; J22839–22852; zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierMutationReader.java`; class | `38676fda368d951d22d7a90e106898941b92daa9` | 22:18:52.904; S2:2799–2829; **two issues / zero Hotspots**, diagnostics pending | 22:18:53.209; J22854–22867; zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierMutationLocksTest.java`; class | `ea19ea98473c02a1eb97ea99b899a6b4c8299b05` | 22:32:11.679; S2:2830–2860; **zero issues/Hotspots** | 22:32:11.559; J22869–22882; zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierMutationReaderTest.java`; class, nested `Fixture` record | `f1965daae1736c0e9e3600c8f8d5b7d9b6323fd7` | 22:32:38.636; S2:2861–2891; **zero issues/Hotspots** | 22:32:38.921; J22884–22897; zero |

### Task 7.4 corrected reader revision (supersedes the pending row)

| Path relative to src / types | Saved Git blob | Sonar completion / range / result | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierMutationReader.java`; class | `6d352434b49dcd2d36bec5f4700aeb8c58e453a7` | 22:37:53.313; S2:2892–2922; full 1/1 Java 25/571 rules, **zero issues/Hotspots** | 22:37:53.363; J22911–22921; **zero problems** |

Extracting tenant/party parameter-name constants resolves the supplied java:S1192 findings. All four current task-7.4 Java revisions now have completed zero Sonar/JDTLS results. The new full gates pass **1,584 tests and 28 actual packaged JVM cases**, with no failure/skip or new compiler warning. These clear the targeted quality blocker; task 7.4's remaining coordinated database verification is still pending, as recorded in `verification.md`.

### Task 7.4 completed coordinated/reader revisions

The following rows supersede the earlier reader-test row and add the coordinated database class. Unchanged production locks/reader and lock-identity test retain their zero analyses above. All current files have full Java-25/571-rule saved-source **zero issues/Hotspots**, independent **zero JDTLS problems**, responsibility Javadoc and no warning suppression/configuration change. The final full suite passes **1,589 tests** and build passes up-to-date; the latest **28 actual packaged JVM cases** remain passing from the reader production correction. `verification.md` records independent sessions, observed lock-order/contention, forced collisions, namespace isolation, root/child probes, fresh committed state, wrapper reconciliation and separate runtime diagnostics.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierMutationReaderTest.java`; class, nested `Fixture` record | `97599b9e5132de5fc905a5bd568b19df120d0ff8` | 22:54:29.982; S2:2954–2984; zero | 22:54:30.337; J22938–22954; zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierLockCoordinationTest.java`; class, nested `Fixture` record | `25d50483d37346e66aa27187357892760bbdb36d` | 23:04:56.295; S2:3047–3077; zero | 23:04:56.217; J22968–22978; zero |

## Task 7.5 final saved revisions

Log rotation note: at 2026-10-04 23:39 all previous S2 ranges up to line 3108 moved unchanged to adjacent `SonarQube for IDE.1.log` (**S2R**). Use S2R for historical tasks 5.1–7.4 and the production row below. **S2** is the active log starting again at line 1 for test analysis; both paths share the documented window2 extension directory. J remains the independent project JDTLS log.

Both final files have full 1/1 Java-25/571-rule **zero issues/Hotspots**, independent **zero source problems**, responsibility Javadoc including Fixture, and no warning suppression/exclusion/configuration change. Nine guarded-writer cases and the full **1,598-test** suite pass. Build succeeds up-to-date after the final test-only additions; the preceding actual 28 packaged JVM cases pass. `verification.md` records compile/behavioral red, initial-shape and immediate-index fixes, protected history, qualified guard/reload, narrow constraints, dates/nulls and demotion rollback.

| Path relative to src / types | Saved Git blob | Sonar completion / range | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierMutationPersistence.java`; class | `bf5dbb91ced361120e4494d16a31ba256564a38f` | 23:31:02.494; S2R:3078–3108; zero | 23:30:59.029; J22980–22993; zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierMutationPersistenceTest.java`; class, nested `Fixture` record | `2fed1d1c0f7920f702a280efa626f6ff6fb5c9b2` | 23:44:11.484; S2:63–93; zero | 23:44:23.068; J23022–23032; zero |

## Task 7.6 saved revisions: runtime green, serializer finding pending

All entries use active S2 and independent J paths above, with analysis on 2026-10-05. Every S2 range is full 1/1 Java 25/571 rules and every J validation reports **zero source problems**. Six revisions report **zero Sonar issues/Hotspots**; the serializer reports **one issue / zero Hotspots** without a diagnostic message/rule/location in the log. Task 7.6 remains incomplete until actual Problems output is supplied, corrected source is reanalyzed and affected checks are rerun. Responsibility Javadoc includes Kind; no suppression/exclusion/settings change is introduced. Six new tests plus outbox/architecture regression, **1,604 full tests and 28 actual packaged JVM cases** pass; `verification.md` records exact red/green and pending workflow/event-count/native boundaries.

| Path relative to src / types | Saved Git blob | Sonar completion / range / result | JDTLS completion / range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/application/model/OutboxEventCandidate.java`; sealed interface | `25acc8ebbcd4bc8f206688f367b83e22a34bd25f` | 00:22:12.403; S2:218–248; zero | 00:22:12.336; J23094–23107; zero |
| `main/java/com/alexastudillo/partyregistry/application/model/PartyIdentifierChangedOutboxCandidate.java`; record, nested `Kind` enum | `da6917341ce55d1a5080af431362509e21bbda00` | 00:05:31.635; S2:94–124; zero | 00:05:31.746; J23034–23047; zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyOutboxEventPersistenceMapper.java`; class | `f32cbaf5761fb1027fde54ad36b2812cb6153bba` | 00:12:17.253; S2:156–186; zero | 00:12:17.367; J23064–23077; zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierOutboxPersistence.java`; class | `478a67678b626c68f58fcca19ebae3b3ecefd06c` | 00:21:30.889; S2:187–217; zero | 00:21:27.847; J23079–23092; zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/messaging/OutboxMessageSerializer.java`; class | `db644c28a987e8bf469e02afde837c664f65a299` | 00:09:16.427; S2:125–155; **one issue / zero Hotspots**, diagnostic pending | 00:09:16.585; J23049–23062; zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierChangedEventCodecTest.java`; class | `fbc029e7f1e7519ecbbee7b7f92dc80b848d49d2` | 00:22:46.705; S2:249–279; zero | 00:22:46.781; J23109–23122; zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/messaging/PartyIdentifierChangedMessageTest.java`; class | `574a1af0762d89f1e1198c01bd5fba01492e8387` | 00:23:30.727; S2:280–310; zero | 00:23:31.077; J23124–23137; zero |

### Task 7.6 current serializer analysis, 2026-10-09

The unchanged serializer blob `db644c28a987e8bf469e02afde837c664f65a299` received fresh full **1/1** standalone Java-25/571-rule analysis at **11:13:14.428**, reporting **one issue / zero Hotspots**. Source: `~/.config/Antigravity IDE/logs/20261009T110402/window1/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log:327–357`. Rule/message/location remain absent from the count-only output; obtain the actual Problems diagnostic before correcting it. Historical JDTLS and JVM results above remain historical, with no new acceptance claim or source edit. See the continuation in `verification.md`; task 7.6 remains incomplete.

### Task 7.6 final serializer revision (supersedes the pending row)

| Path relative to src / types | Saved Git blob | Sonar completion / range / result | JDTLS completion / range / result |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/infrastructure/messaging/OutboxMessageSerializer.java`; class, no nested types | `d036f91b253af22633731954cf31f3b0476fef05` | 2026-10-09 11:17:40.484; current `20261009T110402/window1` Sonar log 358–388; full 1/1 Java 25/571 rules, **zero issues/Hotspots** | 2026-10-09 11:17:41.586; documented workspace log 23586–23596; **zero problems** |

The supplied java:S3824 finding is resolved with computeIfPresent. Focused event/mode/outbox/architecture checks and final **1,604 full tests / 28 actual packaged JVM cases** pass without failure/skip; see `verification.md`. All seven task-7.6 revisions now have zero saved-source findings, with no suppression or configuration change.

## Task 7.7 saved revisions: runtime green, journal finding pending

**S3** is `~/.config/Antigravity IDE/logs/20261009T110402/window1/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log`; **J** is the previously documented project workspace `.metadata/.log`. All listed analyses are current full **1/1**, Java 25/571-rule standalone runs on 2026-10-09. Every independent JDTLS validation reports zero source problems. All types have responsibility Javadoc, including the journal's `AcceptedWrite` and the test's `Fixture`/`Budgets`. Final 11 focused adapter cases, architecture, **1,615 full tests / 28 actual packaged JVM cases** pass. One production journal finding prevents acceptance; no rule key is inferred from count-only output.

| Path relative to src / types | Saved Git blob | Sonar completion / S3 range / result | JDTLS completion / J range / result |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierAtomicSession.java`; class | `09381adfa983c17ddf0281b7aee7a345193d1bd6` | 11:46:35.646 / 1899–1929 / zero | 11:46:35.891 / 24431–24444 / zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierPersistenceFailures.java`; class | `74b0bfd090b42e741d486a93f16326c6be8cf4be` | 11:51:10.625 / 1930–1960 / zero | 11:51:10.098 / 24446–24459 / zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierMutationEffects.java`; class | `c3ff4f572561b6252eb7ab6b2f66f50887c8af25` | 11:51:36.131 / 1961–1991 / zero | 11:51:36.208 / 24461–24474 / zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierMutationJournal.java`; class, nested `AcceptedWrite` record | `d3529c40cdd3f1c6b34f5d1135ffe96984e7704b` | 11:55:17.220 / 1992–2022 / **one issue, zero Hotspots; diagnostic pending** | 11:55:13.377 / 24476–24489 / zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/HibernateReactivePartyIdentifierMutationContext.java`; class | `4757158044e5179f723225bb934da5a1b825c873` | 11:55:27.327 / 2023–2053 / zero | 11:55:27.098 / 24491–24504 / zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/HibernateReactivePartyIdentifierMutationAdapter.java`; class | `75dab7cf761ebe8c82d9957a486eef200dec2b6e` | 12:09:06.038 / 2054–2084 / zero | 12:09:02.485 / 24506–24519 / zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/HibernateReactivePartyIdentifierMutationAdapterTest.java`; class, nested `Fixture` and `Budgets` records | `3b705f5a5a5e5b81ea8024937a1ee94bc2c7a340` | 12:09:19.385 / 2085–2115 / zero | 12:09:17.390 / 24521–24534 / zero |

The implementation's qualified nested-type nullable annotation and test entity accessor compilation errors were corrected before the focused/full green runs; they are not behavioral-red evidence or unresolved diagnostics. No unjustified suppression, settings change or native acceptance claim is present. See `verification.md` for the full TDD/runtime chronology and the pending Problems diagnostic.

### Task 7.7 final journal/context revisions (supersede the pending rows)

| Path relative to src / types | Saved Git blob | Sonar completion / S3 range / result | JDTLS completion / J range / result |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierMutationJournal.java`; class, nested `AcceptedWrite` record | `3e8a25a36e784eeaef7ec1b6a3dbfdca2206ccdf` | 2026-10-09 12:16:21.085 / 2160–2190 / zero | 12:16:36.252 / 24566–24576 / zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/HibernateReactivePartyIdentifierMutationContext.java`; class | `d10e18b0faca47ff6ff348b877c97cc7b7c4a2d0` | 2026-10-09 12:16:22.284 / 2191–2221 / zero | 12:16:48.596 / 24578–24588 / zero |

The supplied java:S6213 diagnostic is resolved with recordAcceptedWrite and its two call sites. All seven current task-7.7 files have zero standalone Sonar/JDTLS findings. Eleven adapter cases, architecture, final **1,615 full tests / 28 actual packaged JVM cases** pass without failures/skips; see `verification.md`. No rule/configuration/suppression change or native acceptance claim is made.

## Task 8.1 saved revisions: source and tests green, final build interrupted

S3/J refer to the same current log paths above. Each listed analysis is full 1/1 Java-25/571-rule standalone Sonar with **zero issues/Hotspots** and independent **zero JDTLS problems** on 2026-10-09. Every type has responsibility Javadoc. Ten new workflow cases and existing registration/architecture regression pass; final full report contains **1,625 tests, zero failures/skips**. The combined test/build command exhausted its 20-minute shell deadline before final build/package acceptance, so 8.1 remains incomplete. See `verification.md` for actual red/green commands, staged CDI scope and retry decision.

| Path relative to src / types | Saved Git blob | Sonar completion / S3 range / result | JDTLS completion / J range / result |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/application/usecase/PartyIdentifierRegistrationWorkflow.java`; class | `cd4647c09fd75f5301d4eb98c1b7baf486963307` | 12:41:39.014 / 2244–2274 / zero | 12:41:35.185 / 24590–24603 / zero |
| `main/java/com/alexastudillo/partyregistry/application/usecase/RegisterPartyIdentifierUseCase.java`; class | `10b06863164cb8044f4117be7fb53ea8fcfd4eca` | 12:45:10.332 / 2275–2305 / zero | 12:45:08.230 / 24605–24618 / zero |
| `main/java/com/alexastudillo/partyregistry/application/usecase/PartyIdentifierPreparation.java`; class | `a625b49aac34c5e483e1524240908831955b0406` | 12:45:40.810 / 2306–2336 / zero | 12:45:40.507 / 24620–24633 / zero |
| `test/java/com/alexastudillo/partyregistry/application/usecase/PartyIdentifierRegistrationWorkflowTest.java`; class, nested `Harness` and `CountingClock` classes | `120b0ba4570fc8c778b2bddc4aeaa51df0792bc1` | 12:49:28.875 / 2456–2486 / zero | 12:49:28.664 / 24797–24810 / zero |

The final blobs remain the same after interruption. No source-quality blocker, compiler warning, rule/configuration change or suppression is present. Existing constructor/consumer retirement is task 8.2; public/native behavior and final build acceptance are not inferred from these internal unit checks.

Task 8.1's build-deadline continuation resolves the gate above: the authorized separate `./gradlew build --info --console=plain` completes in **3m 51s**, retaining the up-to-date passing **1,625-test** suite and executing all **28 packaged JVM cases** without failure/skip. No source blobs or IDE analyses changed. The four current revisions satisfy task 8.1; see `verification.md` for exact output and the earlier interruption.

## Task 8.2 saved revisions: runtime cutover green, standalone gate pending

S3 remains the current `20261009T110402/window1` Sonar log. **J2** is the newly rotated active project `.metadata/.log`; previous J line references are historical in adjacent `.bak_0.log`. All sixteen files below have independent **zero source problems**. Valid resolved Sonar runs use Java 25/571 rules; a mixed-source six-file run reports zero but has incomplete-model warnings and stays pending. **BATCH-PENDING** below means full 3/3 main and 3/3 test completion at 13:45:56.332, S3 2562–2603, reported zero findings but missing main classpath/binaries and unresolved-type/preview warnings; it is not accepted quality evidence. Explicit analysis remains required, even though subsequent JDTLS validates those same saved revisions.

| Path relative to src / types | Saved Git blob | Sonar completion / S3 range / result | JDTLS completion / J2 range / result |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/application/usecase/PartyIdentifierPreparation.java`; class | `b93aa556aeffe776ee19a3201c980deb05ee2a6e` | 13:45:43.171 / 2531–2561 / full 1/1 zero | 13:45:18.123 / 175–188 / zero |
| `main/java/com/alexastudillo/partyregistry/application/usecase/RegisterPartyIdentifierUseCase.java`; class | `3b872f8820bc03736304f0831c5ae276c986531d` | BATCH-PENDING | 14:02:51.803 / 494–504 / zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/configuration/ApplicationUseCaseProducer.java`; class | `9fd641bdca98b10af83f8cdb8c9fedb3bf06c500` | BATCH-PENDING | 14:02:59.950 / 506–516 / zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/HibernateReactivePartyIdentifierMutationAdapter.java`; class | `d2b2429f7eb6f06f58f1639188c46f755f3d97bb` | BATCH-PENDING | 14:03:12.819 / 518–528 / zero |
| `test/java/com/alexastudillo/partyregistry/support/PartyIdentifierMutationDouble.java`; class, nested `IdentifierAcceptance` record | `cfd174cc1353af899210fb61a7eb7005fc3f5161` | BATCH-PENDING | 14:03:24.218 / 530–540 / zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/configuration/ApplicationUseCaseProducerTest.java`; class | `f9d9d30907fb809878a690a806391ee1b864c7b9` | BATCH-PENDING | 14:03:36.911 / 542–552 / zero |
| `test/java/com/alexastudillo/partyregistry/application/usecase/RegisterPartyIdentifierUseCaseTest.java`; class, nested `RegistrationFixture` | `f03c65c2a10f1694dacaff22c93e4a103274cc55` | BATCH-PENDING | 14:03:47.435 / 554–564 / zero |
| `test/java/com/alexastudillo/partyregistry/application/usecase/RegistrationUseCaseTestSupport.java`; class and retained nested recording doubles/call records | `e71e107df089d5b1436c9f07dc43db4f94ee6ce3` | 13:46:00.243 / 2604–2635 / full 2/2 test-only zero | 13:45:48.134 / 280–294 / zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/security/JcaIdentifierProtectionAdapterTest.java`; class, existing anonymous SecureRandom double | `0becf114e8072457a7f0bd972a19b0273ab9f20f` | 13:46:00.243 / 2604–2635 / full 2/2 test-only zero | 13:45:52.989 / 295–309 / zero |
| `test/java/com/alexastudillo/partyregistry/application/port/ApplicationPortContractTest.java`; class | `e6d08bbf1595cf975dd8b1f2f92c9617d387f44a` | 13:46:02.439 / 2636–2666 / full 1/1 zero | 13:45:56.871 / 310–324 / zero |
| `test/java/com/alexastudillo/partyregistry/application/model/PartyRegistrationContractTest.java`; class | `be5a5bd68dd097449be488ecf4084678e06b9cf9` | 13:46:05.463 / 2667–2697 / full 1/1 zero | 13:46:02.704 / 325–339 / zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PersistenceAdapterObservabilityTest.java`; class, nested `FingerprintDouble` and `Budgets` | `3b0a5dfb6339a77615a0e7edc68860b6f05fbcc8` | 13:46:08.639 / 2698–2728 / full 1/1 zero | 13:46:07.751 / 340–354 / zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/ReactivePartyIdentifierRegistrationAdapterTest.java`; class, nested `Owner`, controlled anonymous post-event effects checkpoint | `ece406aa70e6ea53c07c5c81482b2b1aa47cbf3a` | 13:46:13.678 / 2729–2759 / full 1/1 **one issue, zero Hotspots; diagnostic pending** | 13:46:12.942 / 419–426 / zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierPersistenceFailuresTest.java`; class | `a6286b0b0d8a54f1273e42f918913e691bdea6da` | 13:46:15.905 / 2760–2790 / full 1/1 zero | 13:46:15.810 / 428–442 / zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/HibernateReactivePartyIdentifierMutationAdapterTest.java`; class, retained `Fixture`/`Budgets` | `2541f1d7341293363d7be66364f0f7506adab728` | 13:46:21.382 / 2791–2821 / full 1/1 zero | 13:46:22.061 / 443–456 / zero |
| `integrationTest/java/com/alexastudillo/partyregistry/PackagedApplicationIT.java`; class | `128c92e6c73125aefe8e52a9c7f278e2e1aa622a` | 14:09:30.484 / 2822–2852 / full 1/1 zero | 14:09:23.265 / 566–579 / zero |

Current executable evidence: **74 focused checks**, **1,622 full tests**, **28 actual packaged JVM cases**, zero failures/skips on final gates. The prior SQL alias failure, missing Podman environment and old packaged 409 expectation remain separately recorded in `verification.md`. Current packaged correction changes only same-key replay expectations and retains distinct-key uniqueness.

### Retired Java types and replacement boundaries

Removed production `PartyIdentifierRegistrationPort`, `PartyIdentifierRegistrationCandidate`, `HibernateReactivePartyIdentifierRegistrationAdapter`, and `PartyIdentifierRegistrationWriteSequence` after consumer migration. Removed their obsolete adapter/write-sequence test classes alongside equivalent new scoped regression evidence. No retired type reference remains in any Java source set. Initial Party-registration candidates/ports remain production contracts; the new `IdentifierAcceptance` is a synthetic test-only assertion capture, not a relocated production adapter. The regression replacement table and full-suite count reconciliation are in `verification.md`. Removed files require no new analysis; current source diagnostics for all replacements are recorded above.

The IDE's retired-file-close error at J2 12–164 and JUnit discovery NullPointerException at J2 361–417 are recorded separately from zero document problems and resolved Gradle execution. No rule setting, suppression, test budget or compilation warning was concealed. Task 8.2 remains incomplete until the current Problems diagnostic is resolved and BATCH-PENDING analysis is explicitly completed.

### Task 8.2 corrected test revision: supersedes the prior test row, IDE discrepancy pending

| Path relative to src / types | Saved Git blob | Sonar completion / S3 range / result | JDTLS completion / J2 range / result |
|---|---|---|---|
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/ReactivePartyIdentifierRegistrationAdapterTest.java`; class, `Owner`, controlled anonymous effects checkpoint | `95406f5557c4f14dddf8f69aa85580ed94a69479` | 2026-10-09 14:23:32.586 / 2853–2883 / full 1/1 Java 25/571 rules, **one issue, zero Hotspots; current diagnostic unknown** | 14:23:31.601 / 608–621 / **one problem; details unknown and disagreement with saved-source Gradle result unresolved** |

The supplied java:S5785 pattern is replaced by assertNotEquals with its static import in saved source. That blob compiles and passes the focused and **1,622-case full suite**, followed by successful **8s up-to-date build**, retaining the unchanged previously executed **28 packaged JVM results**. No compiler warning or source-content change follows the saved-blob recheck. Current IDE finding counts prevent acceptance; the earlier zero JDTLS result belongs to the prior blob and is not substituted. The six BATCH-PENDING revisions also require explicit completion. See `verification.md` for the exact commands and current diagnostic request.

### Task 8.2 final reactive-test revision (supersedes the discrepancy row)

| Path relative to src / types | Saved Git blob | Sonar completion / active S3 range / result | JDTLS completion / J2 range / result |
|---|---|---|---|
| `test/java/com/alexastudillo/partyregistry/infrastructure/persistence/ReactivePartyIdentifierRegistrationAdapterTest.java`; class, `Owner`, controlled anonymous effects checkpoint | `42f37b2768977bfa317f2f183afe490faefde8bf` | 2026-10-09 14:48:32.170 / 32–62 / full 1/1 Java 25/571 rules, **zero issues/Hotspots** | 14:48:30.984 / 686–696 / **zero problems** |

The supplied unused assertFalse import diagnostic is removed after the assertNotEquals correction, resolving this test's complete local gate. Final **1,622 tests** execute and pass in 9m 58s; build passes in 11s up-to-date, retaining the unchanged actual 28 packaged JVM results. No compiler warnings, suppression or configuration change is introduced. Earlier S3 ranges moved unchanged to `SonarQube for IDE.1.log` (S3R) at 14:46:32; use that rotated file for historical ranges above. Only the six BATCH-PENDING revisions remain without accepted explicit resolved-model Sonar completion; task 8.2 stays incomplete until those are verified.

### Task 8.2 final explicit analyses (supersede BATCH-PENDING)

The developer confirms completion/no findings for the exact six requested individual saved-source analyses. All six blobs are rechecked and unchanged. Four new individual full 1/1 Java-25/571-rule resolved-model Sonar executions are locally corroborated; the two remaining test analyses use developer-provided completion/no-finding evidence without invented local timestamps. Both have separate observed zero JDTLS diagnostics. Final task-8.2 executable results remain **1,622 full cases / 28 actual packaged JVM cases, zero failures/skips** on unchanged source.

| Path relative to src | Saved Git blob | Final Sonar evidence | Independent JDTLS evidence |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/application/usecase/RegisterPartyIdentifierUseCase.java` | `3b872f8820bc03736304f0831c5ae276c986531d` | 2026-10-09 15:03:08.940 / active S3 138–168 / full 1/1 zero, resolved model | 15:03:07.702 / J2 890–904 / zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/configuration/ApplicationUseCaseProducer.java` | `9fd641bdca98b10af83f8cdb8c9fedb3bf06c500` | 15:03:24.239 / S3 169–199 / full 1/1 zero, resolved model | 15:03:23.149 / J2 905–919 / zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/persistence/HibernateReactivePartyIdentifierMutationAdapter.java` | `d2b2429f7eb6f06f58f1639188c46f755f3d97bb` | 15:03:32.491 / S3 200–230 / full 1/1 zero, resolved model | 15:03:32.673 / J2 920–933 / zero |
| `test/java/com/alexastudillo/partyregistry/support/PartyIdentifierMutationDouble.java` | `cfd174cc1353af899210fb61a7eb7005fc3f5161` | 15:01:38.991 / S3 63–93 / full 1/1 zero, resolved model | 15:01:34.190 / J2 875–888 / zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/configuration/ApplicationUseCaseProducerTest.java` | `f9d9d30907fb809878a690a806391ee1b864c7b9` | Developer confirms requested explicit individual analysis completed without findings; no extra local timestamp/count claimed | 15:00:32.158 / J2 782–792 / zero |
| `test/java/com/alexastudillo/partyregistry/application/usecase/RegisterPartyIdentifierUseCaseTest.java` | `f03c65c2a10f1694dacaff22c93e4a103274cc55` | Developer confirms requested explicit individual analysis completed without findings; no extra local timestamp/count claimed | 15:00:21.381 / J2 758–768 / zero |

This accepts the resolved current source gate and completes task 8.2. Historic mixed-source warnings remain recorded; no configuration, suppression or source-content change substitutes for analysis completion.

## Task 8.3 strict creation binding and real HTTP evidence

All eight final saved revisions below have individual full 1/1 Java-25/571-rule resolved-model standalone Sonar executions with **zero issues/Hotspots**, and independently completed **zero JDTLS source problems**. S3 is the active post-rotation `20261009T110402/window1` Sonar log; J2 remains the active project `.metadata/.log`. No missing-classpath/type/preview warning occurs in these analyses. Final executable evidence: six binding + thirteen live + fifteen existing DTO cases (**34 focused checks**), **1,641 full cases**, **28 actual packaged JVM cases**, zero failures/skips after the documented private-exception expectation correction.

| Path relative to src / types | Saved Git blob | Sonar completion / S3 range / result | JDTLS completion / J2 range / result |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierJsonFields.java`; class | `9f741b6aadf86d203dcbac3b349d6313a7f956b3` | 2026-10-09 15:35:48.944 / 231–261 / full 1/1 zero | 15:35:45.126 / 935–948 / zero |
| `main/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierCreateRequestDeserializer.java`; class | `5272b257741e229e208a26a82d93b7a09ec1cb39` | 15:36:07.676 / 262–292 / full 1/1 zero | 15:35:58.006 / 950–963 / zero |
| `main/java/com/alexastudillo/partyregistry/api/error/PartyIdentifierCreateJsonReaderInterceptor.java`; class | `ae7ad62ae548ecee4b36813a2afcdc48fbc6771d` | 15:36:12.020 / 293–323 / full 1/1 zero | 15:36:09.359 / 965–978 / zero |
| `main/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierCreateRequest.java`; record | `7c75836696c68df29e597b4bd5591fae6ebe22b5` | 15:36:20.992 / 324–354 / full 1/1 zero | 15:36:20.445 / 980–993 / zero |
| `test/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierCreateBindingTest.java`; class | `675eebc27d4849b02ce9892042944a5f8e2a231b` | 15:36:33.862 / 355–385 / full 1/1 zero | 15:36:33.962 / 995–1008 / zero |
| `test/java/com/alexastudillo/partyregistry/support/FixedPartyIdentifierRegistrationProducer.java`; class | `9c140717f84c45538d2c390d1573bdbbd8db035f` | 15:36:45.956 / 386–416 / full 1/1 zero | 15:36:45.070 / 1010–1023 / zero |
| `test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierCreateResourceContractTest.java`; class, nested `Owner` record and `RegistrationProfile` class | `2787019a0f31a69b6695442da95dde9075618308` | 15:36:58.100 / 417–447 / full 1/1 zero | 15:36:57.794 / 1025–1038 / zero |
| `test/java/com/alexastudillo/partyregistry/api/model/request/PartyRequestDtoTest.java`; class | `2815aa8389de8c220b80f8679d652ff60e6edcb8` | 15:57:14.804 / 514–544 / full 1/1 zero | 15:57:13.856 / 1136–1149 / zero |

The decoder/request have explicit native reflection registration; actual final native verification remains later tasks. Fixed-clock alternative and JDBC fixtures exist only in the test source set and never request processing/packaged composition. Class/nested responsibility Javadoc and Month-based dates are reviewed. No source finding or compiler warning is hidden; expected runtime/fixture/regression failures and final command outputs are detailed in `verification.md`. Initial registration confidentiality assertions remain intact, with the new unknown-property check strengthened by the constant safe diagnostic.

## Task 9.1 metadata input and typed version boundary

All eight final revisions below have individual full 1/1 resolved-model Java-25/571-rule Sonar executions with **zero issues/Hotspots** and separate **zero JDTLS source problems**. Eight new binding/support cases, unchanged shared parser/initial-additional registration checks, final **1,649 full cases / 28 actual packaged JVM cases** pass. Build's observed generated-output/classpath invalidation and genuine executable TDD failures are recorded in `verification.md`; no source issue or compiler warning is omitted.

| Path relative to src / types | Saved Git blob | Sonar completion / S3 range / result | JDTLS completion / J2 range / result |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/api/model/request/IssuerCodeUpdateValidator.java`; class | `7e2bf4debac0069e0b2e4cada3ef7393b5668a2a` | 2026-10-09 16:38:51.747 / 545–575 / full 1/1 zero | 16:38:47.309 / 1151–1164 / zero |
| `main/java/com/alexastudillo/partyregistry/api/model/request/ValidIssuerCode.java`; annotation | `9de45d98d71152444b1d56c9d0817f01c55699ba` | 16:39:01.750 / 576–606 / full 1/1 zero | 16:38:59.328 / 1166–1179 / zero |
| `main/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierPatchRequest.java`; record | `b51bd9749d3d5a1c079bac8c569bf26a1ff9558d` | 16:39:13.766 / 607–637 / full 1/1 zero | 16:39:11.911 / 1181–1194 / zero |
| `main/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierPatchRequestDeserializer.java`; class | `877ba86bc12fda0f6f447eb455637b06e0df0b13` | 16:39:24.268 / 638–668 / full 1/1 zero | 16:39:23.035 / 1196–1209 / zero |
| `main/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierRequestSupport.java`; class | `14827a2701684f74fc43d59c05e0be3e5ecc6e01` | 16:39:35.155 / 669–699 / full 1/1 zero | 16:39:34.806 / 1211–1224 / zero |
| `main/java/com/alexastudillo/partyregistry/api/error/PartyIdentifierJsonReaderInterceptor.java`; renamed class | `3a4ffc2e838d3d4ee61d3b9f1742afad94a84865` | 16:39:47.155 / 700–730 / full 1/1 zero | 16:39:46.785 / 1226–1239 / zero |
| `test/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierPatchBindingTest.java`; class | `39c3619c37d36584e0a1fed0b57fde197eb0781a` | 16:39:59.361 / 731–761 / full 1/1 zero | 16:39:58.966 / 1241–1254 / zero |
| `test/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierPatchRequestSupportTest.java`; class, nested Headers record | `a65236fad03bb0fccc084f80c5820182c1046bb6` | 16:40:11.584 / 762–792 / full 1/1 zero | 16:40:11.090 / 1256–1269 / zero |

`PartyIdentifierCreateJsonReaderInterceptor` is renamed rather than duplicated; its prior creation responsibilities and signature boundary remain and the completed new analysis supersedes its old inventory row. DTO/decoder reflection registration is explicit. Date fixtures, generic FieldUpdate validator type safety, class/nested responsibility Javadoc, non-obvious support contracts and source-set boundaries are inspected. Actual new PATCH workflow/HTTP/native acceptance remains subsequent tasks.

## Task 9.2 current-version correction (focused developer-directed iteration scope)

Both final saved files and nested test types have full individual 1/1 resolved-model Java-25/571-rule **zero Sonar issues/Hotspots** and separate **zero JDTLS source problems**. Exactly **nine changed-class workflow cases** pass; the focused build succeeds with that test selection up-to-date. Global/package/native acceptance is not claimed from these focused checks; the developer's new iteration-only scheduling constraint and exact commands are recorded in `verification.md`.

| Path relative to src / types | Saved Git blob | Sonar completion / S3 range / result | JDTLS completion / J2 range / result |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/application/usecase/PatchPartyIdentifierUseCase.java`; class | `1f267bdb5bb57a2e4aa786ee14b72e60e7acefec` | 2026-10-09 17:20:27.353 / 859–889 / full 1/1 zero | 17:20:26.662 / 1274–1287 / zero |
| `test/java/com/alexastudillo/partyregistry/application/usecase/PatchPartyIdentifierUseCaseTest.java`; class, nested Harness and CountingClock | `3d33c15bb2aa30a8fb8629dea68a097526309966` | 17:20:38.698 / 890–920 / full 1/1 zero | 17:20:38.454 / 1289–1302 / zero |

Review confirms inward framework-free Application code apart from allowed Mutiny, one callback/clock, neutral typed failure mapping, no replay or catalog/protection/withdrawal capability, explicit safe event/outcome and no production blocking/manual subscription. Javadoc covers every class/nested fixture, dates derive from the established Month-based fixture, and no new compiler warning/suppression or analyzer configuration change is introduced. Actual PATCH delivery and CDI composition are the subsequent HTTP task.

## Task 9.3 actual PATCH boundary and stored/history preservation

All eight final saved revisions below have full individual 1/1 resolved-model Java-25/571-rule **zero Sonar issues/Hotspots** and independent **zero JDTLS source problems**. Exactly **17 changed-class checks**, including eight actual PostgreSQL/HTTP cases, pass with a successful focused build. Only the modified signature/producer and new PATCH HTTP classes run; final global/package/native acceptance is not claimed or substituted.

| Path relative to src / types | Saved Git blob | Sonar completion / S3 range / result | JDTLS completion / J2 range / result |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierRegistrationOperations.java`; class | `dd4e692b0b03c86fa9623bb072f12894feaf36dc` | 2026-10-09 17:40:19.787 / 943–973 / full 1/1 zero | 17:40:19.607 / 1304–1317 / zero |
| `main/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierPatchOperations.java`; class | `ac284c6a1a79fde03497fcad44260b4cc9d81827` | 17:40:30.666 / 974–1004 / full 1/1 zero | 17:40:30.260 / 1319–1332 / zero |
| `main/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierResource.java`; class | `7a8aa1750df9b3e743d0431c9a7ff0a7716334aa` | 17:40:44.694 / 1005–1035 / full 1/1 zero | 17:40:42.955 / 1334–1347 / zero |
| `main/java/com/alexastudillo/partyregistry/infrastructure/configuration/ApplicationUseCaseProducer.java`; class | `437f0d5d837f490073492d5e04fbe6aeee0df0a0` | 17:40:54.288 / 1036–1066 / full 1/1 zero | 17:40:53.663 / 1349–1362 / zero |
| `test/java/com/alexastudillo/partyregistry/support/FixedPartyIdentifierPatchProducer.java`; class | `9bcd3bcc1ff52c9da9fd0fae1e9a852bc5ac68e0` | 17:41:08.286 / 1067–1097 / full 1/1 zero | 17:41:06.300 / 1364–1377 / zero |
| `test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierPatchResourceContractTest.java`; class, Fixture record and PatchProfile class | `316e9e16f335540a31d9f0f8934853e89f364f80` | 17:41:17.684 / 1098–1128 / full 1/1 zero | 17:41:17.292 / 1379–1392 / zero |
| `test/java/com/alexastudillo/partyregistry/api/resource/PartyResourceSignatureTest.java`; class | `f6e8d06ab517b49f79f4085457e35af55bda70be` | 17:46:19.010 / 1151–1181 / full 1/1 zero | 17:46:18.611 / 1394–1407 / zero |
| `test/java/com/alexastudillo/partyregistry/infrastructure/configuration/ApplicationUseCaseProducerTest.java`; class | `a9ef762b14c95471ce492be51355a0f983aaeb8c` | 17:46:31.445 / 1182–1212 / full 1/1 zero | 17:46:30.969 / 1409–1422 / zero |

Explicit response generics, DTO-before-envelope/CDI ResponseManager, pure API transport adaptation, default/selected test composition, safe event/completion counts, unchanged stored protection/verification/parent/subtype/history and no production blocking are reviewed. The redundant resource-specific Jackson method has no Java consumers and the scoped model reader/published global handler remains authoritative. Class/nested responsibility Javadoc and new Month-based dates are retained. No rule, null-analysis setting or suppression is changed; original code-only failure expectations are not weakened. See `verification.md` for actual red/green commands and scoped build.

## Task 10.1 optional action bodies (runtime green, decoder quality gate pending)

Exactly nine changed-class cases pass and focused build reuses that result in 21s. Seven saved files have zero independent JDTLS problems. All have complete full 1/1 resolved-model Java-25/571-rule Sonar execution; six are zero and the decoder has **one unresolved issue / zero Hotspots**, with no actual rule/message in the count-only log. No final gate is inferred from compilation/runtime success.

| Path relative to src / types | Saved Git blob | Sonar completion / S3 range / result | JDTLS completion / J2 range / result |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierReasonRequest.java`; record | `a44aab9f1c9760c8e12feba443f1f68b99a23b6b` | 2026-10-09 18:14:15.678 / 3056–3086 / full 1/1 zero | 18:14:15.816 / 1427–1440 / zero |
| `main/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierReasonRequestDeserializer.java`; class | `457de0bde26671a835e3cdad22d02d983adc2042` | 18:14:28.444 / 3087–3117 / full 1/1 **one issue, zero Hotspots; actual diagnostic pending** | 18:14:28.217 / 1442–1455 / zero |
| `main/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierActionBodies.java`; class | `c6977960bd173bc90667eac8b762334f11cf4944` | 18:14:39.084 / 3118–3148 / full 1/1 zero | 18:14:39.204 / 1457–1470 / zero |
| `test/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierReasonBindingTest.java`; class | `054441bd8dd42f2ae735c6eb7250176b93600342` | 18:14:51.928 / 3149–3179 / full 1/1 zero | 18:14:51.839 / 1472–1485 / zero |
| `test/java/com/alexastudillo/partyregistry/api/model/response/IdentifierActionBoundaryReceipt.java`; record | `2caf6e8f202c07c125a1419f678743249f429816` | 18:15:03.337 / 3180–3210 / full 1/1 zero | 18:15:03.244 / 1487–1500 / zero |
| `test/java/com/alexastudillo/partyregistry/api/error/PartyIdentifierActionBindingVerificationResource.java`; class | `decf64104fa1f2d5c14da030c044e2da1b69f5e3` | 18:15:14.754 / 3211–3241 / full 1/1 zero | 18:15:14.766 / 1502–1515 / zero |
| `test/java/com/alexastudillo/partyregistry/api/error/PartyIdentifierActionBindingContractTest.java`; class | `7edaa2f74a249dc4506c1d76052c3702a332af04` | 18:15:27.610 / 3242–3272 / full 1/1 zero | 18:15:27.658 / 1517–1530 / zero |

The attempted optional-reader extension is reverted exactly to its previously accepted 3a4ffc2e838d3d4ee61d3b9f1742afad94a84865 blob; final reasoning/verification uses BOM-buffered bytes and the strict native DTO/decoder with the managed ObjectMapper. No new global filter/mapper, context ownership or transport InputStream is introduced. Test receipt exposes only presence/counts/event-loop evidence; reason data is write-only/redacted and never returned. Detailed TDD/whitespace/chunked proof and exact focused commands are in `verification.md`. Task 10.1 remains incomplete until the actual source diagnostic is corrected/reanalyzed.

### Task 10.1 final decoder revision (supersedes pending row)

| Path relative to src / types | Saved Git blob | Sonar completion / S3 range / result | Independent JDTLS completion / J2 range / result |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierReasonRequestDeserializer.java`; class | `2336819be86988b796e18360ddf597b2e2bef8de` | 2026-10-09 18:23:01.728 / 3273–3303 / full 1/1 resolved Java 25/571 rules, zero issues/Hotspots | 18:23:01.873 / 1544–1554 / zero |

The supplied line-25 separate-declaration message is resolved without changing behavior or guessing a rule ID. Exactly five affected unit cases and focused build pass in 3m 11s; unchanged four actual HTTP/chunked cases and other six source analyses remain valid. All seven final task-10.1 saved revisions now have completed zero local source diagnostics. Full global/package/native acceptance remains its final tasks, not inferred from these focused results.

## Task 10.2 existing saved revisions: focused runtime green, IDE acceptance pending

No Java source changed in the container-restoration continuation. Exactly 11 lifecycle and 10 changed PATCH tests pass; the selected build also succeeds. Explicit resolved-model Sonar completion and separate saved-revision JDTLS acceptance remain required for these six files, including the test nested types. See `verification.md` for actual commands and the incomplete-model batch, which is not substituted for acceptance.

| Path relative to src / types | Saved Git blob | IDE acceptance |
|---|---|---|
| `main/java/com/alexastudillo/partyregistry/application/usecase/ChangePartyIdentifierLifecycleUseCase.java`; class | `dc6f1ba4087babce2dfd0bc68e51a40533c41789` | Pending |
| `main/java/com/alexastudillo/partyregistry/application/usecase/PartyIdentifierMutationScope.java`; class | `a803453116edb6710284d93df49e6245c6bfdf8c` | Pending |
| `main/java/com/alexastudillo/partyregistry/application/error/PartyIdentifierFailures.java`; class | `0e189d7eb8313201443e69ee6be505ca95cdce5d` | Pending |
| `main/java/com/alexastudillo/partyregistry/application/usecase/PatchPartyIdentifierUseCase.java`; class | `cf2fd1435fa62f33d66aed980797a338b3553b88` | Pending |
| `test/java/com/alexastudillo/partyregistry/application/usecase/ChangePartyIdentifierLifecycleUseCaseTest.java`; class, Harness and CountingClock | `739713b56926d06d9b940c3873c5e698b2f71369` | Pending |
| `test/java/com/alexastudillo/partyregistry/application/usecase/PatchPartyIdentifierUseCaseTest.java`; class, Harness and CountingClock | `df5cacdf8677a9434d9a8a19b4da81951e0a522c` | Pending |

### Task 10.2 final developer-supplied analysis confirmation

The developer confirms all six requested individual saved-file Sonar/JDTLS checks are clean: **"Todos estan bien continua"**. All six blobs above remain unchanged on recheck. This confirmation supersedes each pending IDE acceptance cell, without claiming a new local log timestamp or relying on the unresolved-model batch. The passing 11 lifecycle / 10 PATCH selected tests and focused build remain applicable. Task 10.2 is accepted.

## Task 10.3 saved revisions: 18 focused runtime checks green, IDE evidence pending

All eight files compile and the selected build succeeds, with seven actual PostgreSQL/HTTP cases, seven generic signature checks and four composition checks passing. Actual saved-revision standalone Sonar and separate JDTLS results remain pending; no prior blob/batch result is substituted. Production classes and test nested types have responsibility Javadoc, and no suppression/configuration change is introduced.

| Path relative to src / types | Saved Git blob | Sonar / JDTLS acceptance |
|---|---|---|
| `main/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierResource.java`; class | `f5102bfb699b24f1aa1266ffd90a1f63052f6230` | Pending / Pending |
| `main/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierRequestSupport.java`; class | `320cc15a75f09a80ef6488557bd3d65e40711973` | Pending / Pending |
| `main/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierLifecycleOperations.java`; class | `a6f929a1bb27aa029f02dc069d79607e63df530e` | Pending / Pending |
| `main/java/com/alexastudillo/partyregistry/infrastructure/configuration/ApplicationUseCaseProducer.java`; class | `4be9e7de937fd3286428bc6d1e42e50af25cab05` | Pending / Pending |
| `test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierLifecycleResourceContractTest.java`; class, Fixture and LifecycleProfile | `23d35cc7a832c63c06cf702cff0732b2fbb835cf` | Pending / Pending |
| `test/java/com/alexastudillo/partyregistry/support/FixedPartyIdentifierLifecycleProducer.java`; class | `9831396023884951e960ae8f4140999cd9cdfc43` | Pending / Pending |
| `test/java/com/alexastudillo/partyregistry/api/resource/PartyResourceSignatureTest.java`; class | `eaf8ce2ed597bf98ca48ab8b36307fc2369a94ea` | Pending / Pending |
| `test/java/com/alexastudillo/partyregistry/infrastructure/configuration/ApplicationUseCaseProducerTest.java`; class | `5f1d7b834b37f2caddfdba3c04f8b4cf385ba807` | Pending / Pending |

### Task 10.3 final developer-confirmed analysis

**"Todo esta bien"** confirms completed clean Sonar/JDTLS checks for all eight requested saved files above. All blobs remain unchanged on recheck. This supersedes their pending acceptance cells; no new local timestamp is invented. The 18 selected test results and focused build remain valid, and task 10.3 is accepted.

## Task 10.4 saved test revisions: result-writing and IDE gates pending

No production Java changed. Both files compile; the selected post-fixture-correction run fails writing Gradle binary results, so no green test/build result is claimed for these blobs. Sonar/JDTLS acceptance also remains pending. Responsibility Javadoc covers the test, Fixture, ReplayProfile and the test-only producer. Clocks derive from the existing Month-based lifecycle instant; no invariant, budget, suppression or analyzer setting is changed.

| Path relative to src / types | Saved Git blob | Runtime / Sonar / JDTLS acceptance |
|---|---|---|
| `test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierLifecycleReplayContractTest.java`; class, Fixture, ReplayProfile | `393c0e0c22d31c82cebed3dda55b04016bbf78fa` | Pending / Pending / Pending |
| `test/java/com/alexastudillo/partyregistry/support/FixedPartyIdentifierReplayPatchProducer.java`; class | `f316ced631f5b32aa58842b914abed1f1f9ce32c` | Pending / Pending / Pending |

### Task 10.4 final accepted evidence

Both blobs remain unchanged. Six actual HTTP cases and focused build pass in 3m 20s after the developer-authorized retry. Developer confirms no Sonar warnings for both requested saved files; no local Sonar timestamp is fabricated. Independent JDTLS reports zero for the replay test at 2026-10-09 20:04:04.565 (current `.metadata/.log:2085–2097`) and producer at 20:04:06.013 (2147–2154). These results supersede the pending cells; task 10.4 is accepted. Plugin test-discovery errors remain separately recorded in `verification.md`.

## Task 11.1 final saved revisions: runtime and local quality accepted

S4 is the active `~/.config/Antigravity IDE/logs/20261009T184627/window1/exthost/SonarSource.sonarlint-vscode/SonarQube for IDE.log`; J2 is the existing project `.metadata/.log`. Each S4 range is full individual resolved-model Java 25/571-rule analysis with zero issues/Hotspots, and every J2 range reports zero source problems. Twelve selected binding/mapping/regression tests and focused build pass. No blanket suppression, configuration or rule change is introduced.

| Path relative to src / types | Saved Git blob | Sonar completion / S4 range | JDTLS completion / J2 range |
|---|---|---|---|
| `main/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierPrimaryRequest.java`; record | `9eb614ec75e9bad213dcc3588d36c40ccb3530fc` | 2026-10-09 20:21:01.402 / 1536–1566 / zero | 20:21:01.412 / 2261–2274 / zero |
| `main/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierPrimaryRequestDeserializer.java`; class | `d220d6942a4ea92e1bada927b0aea4831733575c` | 20:21:00.052 / 1505–1535 / zero | 20:20:59.766 / 2246–2259 / zero |
| `main/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierActionBodies.java`; class | `f78bb5906e029e7cd70997b9443a6322279af520` | 20:20:58.324 / 1474–1504 / zero | 20:20:58.556 / 2231–2244 / zero |
| `main/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierRequestSupport.java`; class | `adbc0e7d840b6d687b3a7f1d8306c54bd676ab57` | 20:20:57.093 / 1443–1473 / zero | 20:20:57.357 / 2216–2229 / zero |
| `test/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierPrimaryBindingTest.java`; class, Headers | `5882641af2df76d2e8e4371c00503b8a229deaec` | 20:20:55.902 / 1412–1442 / zero | 20:20:56.261 / 2201–2214 / zero |
| `test/java/com/alexastudillo/partyregistry/api/model/request/PartyIdentifierReasonBindingTest.java`; class | `91991881e3785b0d184baf3b17c42e22cbe40592` | 20:20:54.329 / 1381–1411 / zero | 20:20:54.838 / 2186–2199 / zero |
