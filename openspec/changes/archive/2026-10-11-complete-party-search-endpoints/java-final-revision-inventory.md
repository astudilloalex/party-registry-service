# Final Identifier Search Java Revisions

The affected set is the Java diff from baseline `6a04cc8`, including the two V9
regression expectation corrections. Saved worktree blobs were obtained with
`git diff --name-only 6a04cc8 -- '*.java' | git hash-object --stdin-paths`.

All 75 files were reopened individually through the installed IDE CLI. Fresh
JDTLS Standard diagnostics report **zero problems for every file**, including
nested types, at 21:19:47–21:21:45 on 2026-10-10. Evidence is in
`/home/alex/.config/Antigravity IDE/User/workspaceStorage/fc73fbd3bb7d62718087c857851a4deb/redhat.java/jdt_ws/.metadata/.log`,
lines 4200–5272. The resolved Java 25 / Quarkus 3.40.1 model agrees with full
Gradle unit/build results. Responsibility Javadoc remains present on affected
types; no nullability/type-safety suppression or diagnostic weakening was added.

Each row's JDTLS result is 0. Sonar final acceptance is recorded separately in
verification.md; a zero JDTLS count is not a Sonar result or remote Quality Gate.

Relative paths use these roots:

- `main`: `src/main/java/com/alexastudillo/partyregistry/`
- `test`: `src/test/java/com/alexastudillo/partyregistry/`
- `contract`: `src/contractTest/java/com/alexastudillo/partyregistry/`
- `integration`: `src/integrationTest/java/com/alexastudillo/partyregistry/`

| Root / file | Saved Git blob |
| --- | --- |
| contract `contract/IdentifierSearchContractAssertions.java` | `91438a120588a40a17c0f20f89a64e98690746c5` |
| integration `PackagedIdentifierSearchContractIT.java` | `4ef524b5c2f90a8763e9a4e49e6357fd1a634230` |
| integration `PackagedIdentifierSearchResource.java` | `d16f9b475c57cebba865a869d9b7ce84ee98911f` |
| main `api/error/IdentifierSearchJsonReaderInterceptor.java` | `31be10a0dfdac0a986d885367aa6f0bbd709ed10` |
| main `api/mapper/IdentifierSearchApiMapper.java` | `690eac266c99eccd0f5b0860375d52f4f1cea0f8` |
| main `api/model/request/IdentifierSearchExactRequest.java` | `d29fd195dee20fbf4fdb8b487023c1135fb58880` |
| main `api/model/request/IdentifierSearchExactRequestDeserializer.java` | `a73dd78d03c31d884d869190ea00fc45fe05395b` |
| main `api/model/request/IdentifierSearchJsonFields.java` | `98073f255523c41f29b213c294d1703561c2bf51` |
| main `api/model/request/IdentifierSearchPrefixRequest.java` | `4e892581b98c1d413b7583900d2a7f3abb577e72` |
| main `api/model/request/IdentifierSearchPrefixRequestDeserializer.java` | `214ed6f2935da1af6bacb505da342301301f256d` |
| main `api/model/response/IdentifierSearchIdentifierResponse.java` | `7d1c23c87b5bd831ad16c7a8408743dd2290f2a1` |
| main `api/model/response/IdentifierSearchResponse.java` | `aa87d89231a8c29c22dbc34c2d2f6012cf5bce28` |
| main `api/observability/PartyHttpObservability.java` | `530d47362d9cc1bd61a4b8ed1703dfb74445b109` |
| main `api/resource/IdentifierSearchResource.java` | `819cb54a63d458065c6c133f92afac0cea689117` |
| main `application/model/ExactIdentifierSearchQuery.java` | `f0ba0a6606e1c446a0ef9f07f02e8304ebbf3286` |
| main `application/model/ExactIdentifierSearchSlice.java` | `96a3b1406ee9b6ac1e5ff77c909a65321c1013ba` |
| main `application/model/IdentifierSearchFilters.java` | `cd9b8dcabbcabc6c6bed8b8e8138f4cba4eaeaa2` |
| main `application/model/IdentifierSearchHit.java` | `c1894775c75c71ac534eee03f83ea4b79d147af1` |
| main `application/model/IdentifierSearchPage.java` | `ef64ac7d1bf6c5dc44c71c518aa15fc5763e1a57` |
| main `application/model/IdentifierSearchPositions.java` | `4459948b7e5f7db69be3f6f9be55792a582260a5` |
| main `application/model/IdentifierSearchScope.java` | `ba1b6f8583797ce7815f1ca727f6468a182a69c3` |
| main `application/model/ObservedOperation.java` | `6712df8b142ec7cb2b88acc49641034191a97fb4` |
| main `application/model/PrefixIdentifierSearchQuery.java` | `3135d9dd329caff23f1b2ecfe446389fb72ddf9b` |
| main `application/model/PrefixIdentifierSearchSlice.java` | `c131c0d1ea2de02692418613ef463cddf96d0ba9` |
| main `application/port/IdentifierSearchCursorPort.java` | `abf9cde05a42f9c6f2b80583524c23b6fa86ff5b` |
| main `application/port/IdentifierSearchReadContext.java` | `acf880ff832b304463f59cfad76f8eb0fde8ad46` |
| main `application/port/IdentifierSearchReadPort.java` | `a5f27c46c3f20af942cebc8bd57a820df13a81ae` |
| main `application/usecase/SearchPartiesByIdentifierPrefixUseCase.java` | `5556eb3a27947a1464c9b8423157d58243c1a1e4` |
| main `application/usecase/SearchPartiesByIdentifierUseCase.java` | `d63da492eca201a6faedbb5b86d5b1aa9014dc4c` |
| main `domain/policy/IdentifierRuleCatalog.java` | `5e11fea02ebf7ff08df67358310f7259d25ab136` |
| main `domain/policy/IdentifierSearchSelector.java` | `7a802c27d84b0593ec950d234a677da9ad0d242f` |
| main `domain/policy/IdentifierSearchSelectorPolicy.java` | `bc36e2bbcb25b3bf66747956fb3eae870f4d5fbd` |
| main `infrastructure/configuration/IdentifierSearchUseCaseProducer.java` | `72ac0e7d03c88a05e6ad40ab8965f6b61454cdc1` |
| main `infrastructure/observability/IdentifierSearchWorkObserver.java` | `419449471eed7008ef8ea77d26db5198b5cc349a` |
| main `infrastructure/observability/MicrometerOpenTelemetryOperationObserver.java` | `af368c312041112fffa97c99c0a807f55d4f8e07` |
| main `infrastructure/persistence/HibernateReactiveIdentifierSearchReadAdapter.java` | `a0267f5c6ad80f8405de213c6e5389783bef8b47` |
| main `infrastructure/persistence/IdentifierSearchPrefixAccumulator.java` | `64d3d49c144d47aa856dc566fe50ea9c22890c12` |
| main `infrastructure/persistence/IdentifierSearchReadSession.java` | `66de42311765c5c2b392ee39df02a466a7deb6dc` |
| main `infrastructure/persistence/IdentifierSearchSql.java` | `f6d2dd83323e06a5148d27bd28f003baa04a92e1` |
| main `infrastructure/security/HmacIdentifierSearchCursorAdapter.java` | `77972a3adfbbbc6a1b677f88e6fe6665d0e5fa7a` |
| main `infrastructure/security/IdentifierProtectionIdentity.java` | `1854822a3f1f5c33e4e92342eebcf82a98924496` |
| main `infrastructure/security/IdentifierSearchCpuDispatcher.java` | `38391ba4a4b534a2d14ec62984047843f7ff0b40` |
| main `infrastructure/security/JcaIdentifierProtectionAdapter.java` | `9ca47aa62d833160853e4e4cf3b5c9fa70bbb49a` |
| test `IdentifierSchemeTestFixtureTest.java` | `725a99b7cd44425d9ee60a7d2b88d732053a52a1` |
| test `MigrationRegressionTest.java` | `56ae8648ee08625ae3457385e0d8596522e0645c` |
| test `PartyIdentifierContractMigrationTest.java` | `7392daf921d80ef74ca29b87b6b98f96914488af` |
| test `api/mapper/IdentifierSearchApiMapperTest.java` | `a8aac69716e3ba9509d3427357d226affb382883` |
| test `api/model/request/IdentifierSearchBindingTest.java` | `5174e0c4ee1ccee8b8e0c90271a223fdebd4ca32` |
| test `api/observability/PartyHttpObservabilityTest.java` | `2e4df4f53fb22b32b9a15b486cca38444dfe530d` |
| test `api/resource/IdentifierSearchFailureContractTest.java` | `d67cd75e7747d2e44d0232ca2880663b559579ec` |
| test `api/resource/IdentifierSearchFaultContractTest.java` | `d86ffd7156b8531a6cc9c00253504d20d4aa8d09` |
| test `api/resource/IdentifierSearchFaultReadPort.java` | `6d26fb643a980f843b9cd6e6a9bbd5444b7120a3` |
| test `api/resource/IdentifierSearchHistoricalContractTest.java` | `c2ebb175907728a8168f26c13c253b8c2ea7e777` |
| test `api/resource/IdentifierSearchInputContractTest.java` | `7c3f0aebed8f45d1b051634a6589c678ed58054e` |
| test `api/resource/IdentifierSearchPerformanceTest.java` | `d2b030b6c418168b5ab93974c3fe00672afc704a` |
| test `api/resource/IdentifierSearchPrefixContractTest.java` | `777e54abbbd678c028c5da67e02d8ddadcc9f310` |
| test `api/resource/IdentifierSearchResourceContractTest.java` | `fb2c53c534810f0246e884cb186a10e7285d0888` |
| test `api/resource/IdentifierSearchRuntimeContractTest.java` | `5958a6c06917c878d585b541393aff86638eaf6a` |
| test `application/model/IdentifierSearchQueriesTest.java` | `dab01d0f756dcb6d308eb7c5c24fd61b0efb0eda` |
| test `application/model/IdentifierSearchResultsTest.java` | `d02b7beeada77752e22cf95714a2b56265834e32` |
| test `application/support/IdentifierSearchReadStub.java` | `87cca6cbea91bbb9f9357e8ef198ddb2676ea756` |
| test `application/usecase/IdentifierSearchUseCasesTest.java` | `12dcc0a16148ea6fccb4acc4aedcfcccfd5dca0c` |
| test `contract/IdentifierSearchContractAssertionsTest.java` | `4783579b1d05d46cdd0e5705c829d8270c49726a` |
| test `domain/policy/IdentifierSearchSelectorPolicyTest.java` | `1abf1fc6373dc5066a71b507791f9144e7e633b3` |
| test `infrastructure/observability/IdentifierSearchWorkObserverTest.java` | `f6bd71b62b49b2fa622836707b779b509445a175` |
| test `infrastructure/observability/MicrometerOpenTelemetryOperationObserverTest.java` | `92383b08b76e06d970ea42a861e3920cf85decdd` |
| test `infrastructure/persistence/IdentifierSearchPrefixAccumulatorTest.java` | `14c4f3b391b40f2c9f4ba92d608aecf278e72542` |
| test `infrastructure/persistence/IdentifierSearchReadAdapterTest.java` | `5050d968fb774c38481d1f841c3d8749926bbaf0` |
| test `infrastructure/persistence/IdentifierSearchReadSessionTest.java` | `32e6e9de98e4c6c18500a4955333ad84bdc0b65e` |
| test `infrastructure/persistence/IdentifierSearchSnapshotContractTest.java` | `80ab30f55a5b447b6c920e43a82f679597d9028c` |
| test `infrastructure/security/HmacIdentifierSearchCursorAdapterTest.java` | `330576c0c868b65fec67d78ec1d94964c0346301` |
| test `infrastructure/security/IdentifierSearchCpuDispatcherTest.java` | `b40ecf55989a336bfaf8176a1d33970e9641c4b6` |
| test `infrastructure/security/IdentifierSearchMigrationTest.java` | `7a4370611c549f18c7af6945d23d54ebe266f3e3` |
| test `infrastructure/security/JcaIdentifierProtectionAdapterTest.java` | `45d3f20a7a3cd01493c42c49ebdfdd36a8e3b714` |
| test `support/IdentifierSearchDatabaseFixture.java` | `b9a885255b9682d036ba38e94a56fb0d9906c413` |
