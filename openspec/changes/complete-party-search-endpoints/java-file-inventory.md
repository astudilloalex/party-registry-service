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

The foundation, Domain/Application contracts, cryptographic adapters, Flyway
tests and the in-progress reactive read owner are now affected. Source findings
remain independent of passing Gradle tests. User-applied parallel refactoring
requires final-revision test/analysis reconciliation before section 8 acceptance.

| Source set | File / nested types | Saved Git blob | Relevant tests and result | JDTLS model / diagnostics | Sonar analyzer / rules / completion | Findings / hotspots / fixes / reanalysis |
| --- | --- | --- | --- | --- | --- | --- |
| Shared-contract | `src/contractTest/java/com/alexastudillo/partyregistry/contract/IdentifierSearchContractAssertions.java` | `91438a120588a40a17c0f20f89a64e98690746c5` | Four assertion tests pass; focused OpenAPI and architecture checks pass | Java 25 / Quarkus 3.40.1; 0 problems at 15:04:12 | SonarQube for IDE 5.7.0; 571 active Java rules, current resolved model, full analysis complete at 15:04:10 | 0 issues, 0 hotspots; raw Swagger model inference compilation errors corrected before this revision |
| Unit | `src/test/java/com/alexastudillo/partyregistry/contract/IdentifierSearchContractAssertionsTest.java` | `4783579b1d05d46cdd0e5705c829d8270c49726a` | Focused assertion/OpenAPI rerun passed in 3m 17s | Java 25 / Quarkus 3.40.1; baseline zero problems, final reconciliation pending | SonarQube for IDE 5.7.0; 571 Java rules; reanalysis complete 15:25:19 | User fixed original finding; 0 issues/hotspots on reanalysis |

### Implementation revision ledger

Paths below are relative to `src/main/java/com/alexastudillo/partyregistry/`
for production or `src/test/java/com/alexastudillo/partyregistry/` for unit
tests. All rows have independently observed zero-problem JDTLS diagnostics
unless explicitly marked otherwise. Relevant focused test commands/results
are recorded by task in verification.md. Sonar is tracked for parallel user
remediation; final per-file rule/parameter/finding reconciliation is pending,
not presumed clean.

| Source set / relative file | Saved Git blob | Focused verification / diagnostics |
| --- | --- | --- |
| Production `domain/policy/IdentifierRuleCatalog.java` | `5e11fea02ebf7ff08df67358310f7259d25ab136` | Task 2.1 passed; JDTLS 0 |
| Production `domain/policy/IdentifierSearchSelector.java` | `7a802c27d84b0593ec950d234a677da9ad0d242f` | Task 2.1 passed; JDTLS 0 |
| Production `domain/policy/IdentifierSearchSelectorPolicy.java` | `bc36e2bbcb25b3bf66747956fb3eae870f4d5fbd` | Task 2.1 passed; JDTLS 0 |
| Unit `domain/policy/IdentifierSearchSelectorPolicyTest.java` | `1abf1fc6373dc5066a71b507791f9144e7e633b3` | Task 2.1 passed; JDTLS 0 |
| Production `application/model/IdentifierSearchFilters.java` | `cd9b8dcabbcabc6c6bed8b8e8138f4cba4eaeaa2` | Task 2.2 passed; JDTLS 0 |
| Production `application/model/ExactIdentifierSearchQuery.java` | `f0ba0a6606e1c446a0ef9f07f02e8304ebbf3286` | Task 2.2 passed; JDTLS 0 |
| Production `application/model/PrefixIdentifierSearchQuery.java` | `3135d9dd329caff23f1b2ecfe446389fb72ddf9b` | Task 2.2 passed; JDTLS 0 |
| Production `application/model/IdentifierSearchScope.java` | `ba1b6f8583797ce7815f1ca727f6468a182a69c3` | Task 2.2 passed; JDTLS 0 |
| Unit `application/model/IdentifierSearchQueriesTest.java` | `dab01d0f756dcb6d308eb7c5c24fd61b0efb0eda` | Task 2.2 passed on earlier saved revision; user refactor requires final rerun |
| Production `application/model/IdentifierSearchHit.java` | `c1894775c75c71ac534eee03f83ea4b79d147af1` | Task 2.3 passed; JDTLS 0 |
| Production `application/model/IdentifierSearchPositions.java` | `4459948b7e5f7db69be3f6f9be55792a582260a5` | Task 2.3 passed; JDTLS 0 |
| Production `application/model/ExactIdentifierSearchSlice.java` | `96a3b1406ee9b6ac1e5ff77c909a65321c1013ba` | Task 2.3 passed; JDTLS 0 |
| Production `application/model/PrefixIdentifierSearchSlice.java` | `c131c0d1ea2de02692418613ef463cddf96d0ba9` | Task 2.3 passed; JDTLS 0 |
| Production `application/model/IdentifierSearchPage.java` | `ef64ac7d1bf6c5dc44c71c518aa15fc5763e1a57` | Task 2.3 passed; JDTLS 0 |
| Unit `application/model/IdentifierSearchResultsTest.java` | `d02b7beeada77752e22cf95714a2b56265834e32` | Task 2.3 rerun passed; JDTLS 0 |
| Production `application/port/IdentifierSearchReadPort.java` | `a5f27c46c3f20af942cebc8bd57a820df13a81ae` | Task 2.4 architecture passed; JDTLS 0 |
| Production `application/port/IdentifierSearchReadContext.java` | `acf880ff832b304463f59cfad76f8eb0fde8ad46` | Task 2.4 architecture passed; JDTLS 0 |
| Production `application/port/IdentifierSearchCursorPort.java` | `abf9cde05a42f9c6f2b80583524c23b6fa86ff5b` | Task 2.4 architecture passed; JDTLS 0 |
| Unit `application/support/IdentifierSearchReadStub.java` including `ReadContext` | `87cca6cbea91bbb9f9357e8ef198ddb2676ea756` | Task 2.4 compiled; JDTLS 0 |
| Production `infrastructure/security/JcaIdentifierProtectionAdapter.java` | `9ca47aa62d833160853e4e4cf3b5c9fa70bbb49a` | Tasks 3.1/3.2 passed; JDTLS 0 |
| Production `infrastructure/security/IdentifierProtectionIdentity.java` | `1854822a3f1f5c33e4e92342eebcf82a98924496` | Task 3.2 passed; JDTLS 0 |
| Unit `infrastructure/security/JcaIdentifierProtectionAdapterTest.java` including existing anonymous test fixture | `45d3f20a7a3cd01493c42c49ebdfdd36a8e3b714` | Task 3.2 passed on earlier revision; user refactor requires final rerun |
| Production `infrastructure/security/HmacIdentifierSearchCursorAdapter.java` | `77972a3adfbbbc6a1b677f88e6fe6665d0e5fa7a` | Task 3.3 passed; JDTLS 0 |
| Unit `infrastructure/security/HmacIdentifierSearchCursorAdapterTest.java` including `KeyConfiguration`, `ChangedNormalizer` | `330576c0c868b65fec67d78ec1d94964c0346301` | Task 3.3 passed on earlier revision; user refactor requires final rerun |
| Unit `infrastructure/security/IdentifierSearchMigrationTest.java` | `7a4370611c549f18c7af6945d23d54ebe266f3e3` | Task 4.1 passed; JDTLS 0 |
| Unit `MigrationRegressionTest.java` | `56ae8648ee08625ae3457385e0d8596522e0645c` | Task 4.1 passed; JDTLS 0 |
| Production `infrastructure/persistence/IdentifierSearchReadSession.java` including `ReadView` | `43b2f4f06b8933bde448b52dfcec37dc63004377` | Explicit acquisition failure type resolves JDTLS/javac inference discrepancy; three scoped-read tests pass; JDTLS 0 |
| Unit `infrastructure/persistence/IdentifierSearchReadSessionTest.java` including `SingleConnectionProfile`, `TestDeadlines` | `fac5be7fc0309baf0b353f9c38f02140453fced5` | Normal snapshot, exhausted/late acquisition reuse and statement/traversal cleanup tests pass; JDTLS 0 |

## Pending acceptance

### Task 6.1 revision checkpoint

Paths use the relative roots defined above. All files have fresh zero-problem
Standard JDTLS diagnostics (18:56–19:06); focused verification is recorded in
verification.md. Final Sonar model/rules/revision reconciliation remains pending.

| Source set / relative file | Saved Git blob |
| --- | --- |
| Production `application/model/ObservedOperation.java` | `6712df8b142ec7cb2b88acc49641034191a97fb4` |
| Production `api/resource/IdentifierSearchResource.java` | `fc030243e1df462187ae8acc58128a3ad8120d3d` |
| Production `api/observability/PartyHttpObservability.java` | `36f22ec4f56073cc9ca028b8ddd083aa2284fb35` |
| Production `application/usecase/SearchPartiesByIdentifierUseCase.java` | `d63da492eca201a6faedbb5b86d5b1aa9014dc4c` |
| Production `application/usecase/SearchPartiesByIdentifierPrefixUseCase.java` | `5556eb3a27947a1464c9b8423157d58243c1a1e4` |
| Production `infrastructure/configuration/IdentifierSearchUseCaseProducer.java` | `72ac0e7d03c88a05e6ad40ab8965f6b61454cdc1` |
| Production `infrastructure/observability/IdentifierSearchWorkObserver.java`, including enums and `Sample` | `419449471eed7008ef8ea77d26db5198b5cc349a` |
| Production `infrastructure/observability/MicrometerOpenTelemetryOperationObserver.java` | `af368c312041112fffa97c99c0a807f55d4f8e07` |
| Production `infrastructure/persistence/HibernateReactiveIdentifierSearchReadAdapter.java`, including `ReadContext` | `a0267f5c6ad80f8405de213c6e5389783bef8b47` |
| Production `infrastructure/persistence/IdentifierSearchSql.java` | `f6d2dd83323e06a5148d27bd28f003baa04a92e1` |
| Production `infrastructure/security/IdentifierSearchCpuDispatcher.java` | `38391ba4a4b534a2d14ec62984047843f7ff0b40` |
| Unit `infrastructure/observability/IdentifierSearchWorkObserverTest.java` | `f6bd71b62b49b2fa622836707b779b509445a175` |
| Unit `infrastructure/observability/MicrometerOpenTelemetryOperationObserverTest.java` | `92383b08b76e06d970ea42a861e3920cf85decdd` |
| Unit `application/usecase/IdentifierSearchUseCasesTest.java` | `12dcc0a16148ea6fccb4acc4aedcfcccfd5dca0c` |
| Unit `api/observability/PartyHttpObservabilityTest.java` | `2e4df4f53fb22b32b9a15b486cca38444dfe530d` |
| Unit `infrastructure/security/IdentifierSearchCpuDispatcherTest.java` | `3cbba81738317772178f092ad88d562299ef0133` |

- Per-file final-revision JDTLS and Sonar evidence.
- Full final-source `./gradlew test` and `./gradlew build` results.
- Actual native build and native HTTP integration execution.
- Requirement-to-test mapping, query measurements, and final boundary review.

Command/environment evidence and baseline timing investigation are maintained
separately in [verification.md](verification.md).
