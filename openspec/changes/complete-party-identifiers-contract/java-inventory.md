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
