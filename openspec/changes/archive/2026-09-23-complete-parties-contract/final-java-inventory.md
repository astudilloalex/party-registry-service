# Final Java Revision Inventory

Snapshot taken on 2026-09-20 after task 10.4 and reconciled after the final nationality-retention fixture enhancement. Paths below are relative to `src/<source set>/java/com/alexastudillo/partyregistry/`. The inventory includes tracked modifications and untracked additions: **143 files: 77 main, 64 test, 2 integrationTest**. Retired files are excluded from analysis and documented in the activation cutover evidence.

Every listed saved blob has **zero JDTLS problems and zero unresolved standalone Sonar issues/security hotspots**. The final review reopened every file individually in the resolved Java 25 IDE project. Logs contain 137 fresh per-file analyses in the 14:35–14:40 wave; the six already-open unchanged files retain their separately recorded final-revision analyses in `verification.md` (OpenAPI, both architecture files, root framework errors, and both packaged tests). The later test-only nationality fixture change was separately reanalyzed: Sonar zero at 15:29:04.221 and JDTLS zero at 15:29:27.922. These are local results from SonarQube for IDE 5.7.0 with 571 Java rules, not a remote Quality Gate.

The final wave is recorded under `~/.config/Antigravity IDE/logs/20260919T084521/window1/exthost/SonarSource.sonarlint-vscode/` in the active log and its `.1.log`/`.2.log` rotations; JDTLS uses the reimported workspace session beginning 13:32:21. Per-task timestamps, fixes and checks remain in `verification.md`.

Scoped fixture audit found no numeric month arguments, literal valid `Instant`/`LocalDate` parsing, or `@SuppressWarnings` in these files. The only literal date-shaped input is the deliberately invalid `2026-02-30T12:00:00Z` parser rejection. Responsibility Javadoc and non-obvious public contracts were reviewed throughout implementation and reconciled in the final audit.

## Main

| Relative Java file | Saved Git blob |
|---|---|
| `api/context/RequestMetadataContext.java` | `3c62c678e66ccd1923ec0d5e0074ee4baf8d18e9` |
| `api/error/PartyApiErrorTranslator.java` | `b3230cc9dfe6b0397ec384f307e03bc42b193849` |
| `api/error/PartyResponseCode.java` | `65a5f13161b6653a8a51502ca1cad11a82544c36` |
| `api/error/PartyUpdateJsonReaderInterceptor.java` | `292b9d5aea4ef38a5eacc154bb369a6673dd2067` |
| `api/filter/RequestContextFilter.java` | `825fa3f5abec824234e4f734cc6e3386086a9a43` |
| `api/mapper/PartyApiMapper.java` | `2079e666282808af808badab1c842a16a5f587ec` |
| `api/model/request/PartyUpdateRequest.java` | `ddea180c5cb279e208b583077bfea6d3677fdae4` |
| `api/model/request/PartyUpdateRequestDeserializer.java` | `63521ce3e38d1507d31225612c77b6d9d27cbd07` |
| `api/model/response/PartySummaryResponse.java` | `16cf15e97bd76e6888742f5868e601d9e3ba6bf4` |
| `api/observability/PartyHttpObservability.java` | `0436e155684e2039b1b73426cf8b31e3f3e48927` |
| `api/resource/PartyResource.java` | `a178f67cb914ff21207af8b0fb2eda04fd293d07` |
| `api/serialization/PartyPaginationObjectMapperCustomizer.java` | `fea431e1ce827d44a7bf8a2a4dcd7aa7e049c7a2` |
| `api/support/PartyQueryParameters.java` | `936fda8c4e3a1f672af1b29f7b361310422b2850` |
| `application/command/ChangePartyLifecycleCommand.java` | `a89d1ee2719288341d652dfbf8cd04854502442e` |
| `application/command/PatchPartyCommand.java` | `3812c81c8956bd964b3cb01769985d401a8aaba0` |
| `application/error/ApplicationException.java` | `8711dfa47fe178c13669c7fcc29377bd267b71d5` |
| `application/error/ApplicationFailure.java` | `598a98ab106a0a841f39e659df5e120364035c30` |
| `application/model/CompletedPartyLifecycle.java` | `1189379b212196f6a631f046a055d0c26392e58b` |
| `application/model/ObservedOperation.java` | `458a4301eb538a31a3b4d0a0b90273eb3c046334` |
| `application/model/OperationOutcome.java` | `5f3101c5deac404f85af3236d95867004af8f820` |
| `application/model/OutboxEventCandidate.java` | `bdda216ddd2b6b4dfefef7c9e163cc3e373a7ce0` |
| `application/model/PartyChangedOutboxCandidate.java` | `dffdc1e36c17241f2d81215cc2933e29800346d3` |
| `application/model/PartyLifecycleAction.java` | `4d676c3541baaa00ac08b95ce303da8a4660d253` |
| `application/model/PartyLifecycleRequest.java` | `d1a00e7165fc438ffe248a1074b7b6bcba433095` |
| `application/model/PartyMutationOutcome.java` | `ecce2d90062e892957463dd180f7f6f17c935b79` |
| `application/model/PartyNamePredicate.java` | `a1ea788668fe965d680cd1dc906778a6746089a1` |
| `application/model/PartyPageBoundary.java` | `880dc80feff39f2581d92daad2cede1a8cc80c11` |
| `application/model/PartyPagePosition.java` | `609cb1541db3820385d3b24114e8b3ea2a108b7f` |
| `application/model/PartyPageResult.java` | `5f24bdb876ae00b2c02b4b5951afebb991a19307` |
| `application/model/PartyPageSlice.java` | `436ab021e85bf72416c8fa16e595018ecf0f8b5b` |
| `application/model/PartySearchCriteria.java` | `67be2e48592474f84ae98f4f3c68f3d21565537a` |
| `application/model/PartySearchScope.java` | `fff17274cac54c239be253f7589a26b4a0303a3f` |
| `application/model/PartySummaryResult.java` | `713c754befa930c604cf283c6f615f98866bc4ec` |
| `application/observability/OperationObservation.java` | `e0faf82629c980b4b3824dce84eccb94a737900e` |
| `application/port/PartyCursorPort.java` | `c044afbedda3c197b753b5f28bbfa2ce9f973f53` |
| `application/port/PartyMutationContext.java` | `d03b5e726af8a53e469b1040c8789379fdfd61da` |
| `application/port/PartyMutationPort.java` | `fb41f74f1a1ad05f2f01a6aa976652ee1a400772` |
| `application/port/PartyQueryPort.java` | `979cb64df4ee16ff1bf02766ec43a4e7423fe908` |
| `application/query/GetPartyQuery.java` | `2bbdc725110ba020b8ae639e17e376fcabea77e9` |
| `application/query/ListPartiesQuery.java` | `73ce75e3092135b35e81a991466a91291cbfa35f` |
| `application/usecase/ChangePartyLifecycleUseCase.java` | `a760ecfb43888ff67c2497948842c509b5b5ded4` |
| `application/usecase/GetPartyUseCase.java` | `12ef34fa818673dc4969559451799cb9712e590a` |
| `application/usecase/ListPartiesUseCase.java` | `df78f3dd6640975048e12e7c39b3d066a58afb8d` |
| `application/usecase/PatchPartyUseCase.java` | `60b1fde62e33eca701889124adfe8beb09221dae` |
| `domain/error/DomainViolation.java` | `77242b1244a728db09d07c4244700f93d62ab49b` |
| `domain/model/LegalEntity.java` | `5cdf89b44b71b1f60b898dd598d7c2201275fc7d` |
| `domain/model/NaturalPerson.java` | `202688b2ec4c4a2ac765c619fdfcfbd6b694d5b8` |
| `domain/model/Party.java` | `82ea1e13da98a23c70d9dc1680d96eb3c0a4e0ce` |
| `domain/policy/PartyActivationEvidence.java` | `bac6bc5280d3f733516aaf0275578a4353c4ea30` |
| `domain/policy/PartyActivationPolicy.java` | `405304bc1c93675535704adcf8bdf63887976227` |
| `infrastructure/configuration/ApplicationUseCaseProducer.java` | `552bd98afa73a129ec2f46c54d7ecad681d48e18` |
| `infrastructure/messaging/OutboxMessageSerializer.java` | `18652252619d2b9026ff9fd37024bad9887e183f` |
| `infrastructure/observability/MicrometerOpenTelemetryOperationObserver.java` | `d45fb1f58f20f64df9fdc4773063c69c3683e438` |
| `infrastructure/observability/PartyPersistenceObservability.java` | `908c2f7d9a7ce08c0503a7d8ee5107b31bebc916` |
| `infrastructure/persistence/ApiIdempotencyRecordEntity.java` | `0a7e612cfc73471d351eff28c27cc84e3e3a5620` |
| `infrastructure/persistence/ApiIdempotencyRecordId.java` | `f57f88cab049c065a871eab0743b19a717ecf97d` |
| `infrastructure/persistence/HibernateReactivePartyMutationAdapter.java` | `8a38555199bb14916429a69764ed980ffb5c6286` |
| `infrastructure/persistence/HibernateReactivePartyMutationContext.java` | `62a58a78a613957f765c6f8ae6005baeadd51122` |
| `infrastructure/persistence/HibernateReactivePartyQueryAdapter.java` | `5d6a6c95b50cedde3a82ad8bd37e6f0f9e5b69e3` |
| `infrastructure/persistence/IdempotencyResultSnapshotCodec.java` | `eb7dcda3a14d859094c0bcf0c6df732aff618f93` |
| `infrastructure/persistence/PartyEntity.java` | `1d0af38da755c7dd1c44ee7846c0331c69742365` |
| `infrastructure/persistence/PartyLifecycleFingerprint.java` | `7002d9847363346a4076608d0d2261cf16c60509` |
| `infrastructure/persistence/PartyLifecycleIdempotencyPersistence.java` | `904b8b42062836d5e2d414be907b7eca0cf47622` |
| `infrastructure/persistence/PartyLifecycleSnapshotCodec.java` | `dea46e1173c9b5c6f278db8c410d4fe6e46fe3b2` |
| `infrastructure/persistence/PartyMutationConfiguration.java` | `2efd1d51c8afb932bc5f955ad06447cfa91c38a4` |
| `infrastructure/persistence/PartyMutationTimeouts.java` | `41cf9985d03fed8afbedbcf42bf0a9048d93d568` |
| `infrastructure/persistence/PartyNamePageAccumulator.java` | `c84bad297240fd4667e54ab981fd1afc24ab665b` |
| `infrastructure/persistence/PartyOutboxEventPersistenceMapper.java` | `ade11512074c9bc3445eb307b7974d516c2e2838` |
| `infrastructure/persistence/PartyPageSql.java` | `ff4e9b438c0a1b403cb0539360cfbe73e5011311` |
| `infrastructure/persistence/PartyQueryConfiguration.java` | `611afc9a64d0104b1b59770004b80061d9447c2e` |
| `infrastructure/persistence/PartyQueryTimeouts.java` | `fd97b3e9a7346491aaa00c09f83f1ef149abcf7f` |
| `infrastructure/persistence/PartyRootMutationPersistence.java` | `15294d7d0d9d78aa97c4cbbec5be0c79027d28ef` |
| `infrastructure/persistence/PartyRootOutboxPersistence.java` | `166e337f31c7d4b4dd401d3724669e3f3b3ca801` |
| `infrastructure/persistence/PartyRootReader.java` | `ff54502992cee7a2d14e0c5f9baf08772c9ef090` |
| `infrastructure/security/HmacPartyCursorAdapter.java` | `8e852e164fc19c42832b501372172cca39378522` |
| `infrastructure/security/PartyCursorKeyMaterial.java` | `f3dab4a65c25008d69002fb9f66f744137107762` |
| `infrastructure/security/PartyPaginationConfiguration.java` | `4f4f88ed2f779ef019be574ec761d370e0f0ad8d` |

## Test

| Relative Java file | Saved Git blob |
|---|---|
| `IdentifierSchemeTestFixtureTest.java` | `b20ff74d45907465cb8dc096f25c2e4e2627c228` |
| `MigrationRegressionTest.java` | `67b4e037e7caef9f1dcef0008d5522db3adcfe72` |
| `OpenApiContractTest.java` | `053073de59dd8692270d85865fbb4c45120b6b98` |
| `PartyListingMigrationTest.java` | `80719f50741eb0dafeb5f8173c6125b0316e93d9` |
| `api/error/PartyApiErrorTranslatorTest.java` | `b491f31ad3181badcc9a59d6ae16ce36a3c74726` |
| `api/error/RootErrorContractTest.java` | `35bd2fce300ffb1e77864711e5475fd122566417` |
| `api/error/RootErrorVerificationResource.java` | `4e570ca90438c2de79ea773724617afc23cb4308` |
| `api/error/RootFrameworkErrorContractTest.java` | `797c41efcad9bc2d20c6e164be6dc08f6da614fa` |
| `api/filter/RootPartyContextContractTest.java` | `d0d112c036f0cb7d70c10be058c8002bc8404bab` |
| `api/mapper/PartyPageMappingTest.java` | `5930ed38510b22368ea0c58a8d5ad50485135b02` |
| `api/mapper/SharedResponsePaginationTest.java` | `82cb45b433ddc8914620a6bfaf47aff3993dea94` |
| `api/model/request/PartyUpdateRequestTest.java` | `7330a1026103e0b73c4cd21b05a7a5427d80937c` |
| `api/observability/PartyHttpObservabilityTest.java` | `284019a0e3078fa9b0aeb803155f97205e7731a7` |
| `api/observability/RootPartyHttpObservabilityTest.java` | `64d35f392be2afec780960cbd874bec32d9bf458` |
| `api/resource/PartyResourceSignatureTest.java` | `feae348bc8e8820673190da6b4d804fed27393ed` |
| `api/resource/PartyRootDetailRaceContractTest.java` | `c2a4482cffa3519a25c1cab50ec5d18d040d3686` |
| `api/resource/PartyRootLifecycleContractTest.java` | `ee5dcb660fec228055fe373eea06e1d48eeffa2d` |
| `api/resource/PartyRootMutationSmokeTest.java` | `ea84bdf2daf0c10171b056860b83dbe989a57912` |
| `api/resource/PartyRootPatchContractTest.java` | `9eba7b7cdfe5a622cb2da73be8929f8ab50e0c62` |
| `api/resource/PartyRootReadContractTest.java` | `c2bd940a1a5611264eb2254173d40a6fa6318cdd` |
| `api/resource/PartyRootReadSmokeTest.java` | `b3be265040950b555ead70244605e1249c24af1c` |
| `api/serialization/PartyPaginationSerializationTest.java` | `052757d98eeca0efda9c4b263aae884eeabbdafc` |
| `api/support/PartyQueryParametersTest.java` | `b3176081fa212d5e835ad8f886653fcab0fdc53b` |
| `application/error/ApplicationExceptionTest.java` | `49a65c183bfbe4d69dfdba1cf9ad3e750ed058ed` |
| `application/model/PartyMutationContractTest.java` | `5a05cded01c344ccf92e06b9cd75fac15ded7f21` |
| `application/model/PartyQueryContractTest.java` | `6557dc2ec2bf91a0d8ba3cd6570660c2c2afb424` |
| `application/observability/OperationObservationTest.java` | `303d33b00f9b7ac887a3300be57acdfc8155b4ea` |
| `application/port/ApplicationPortContractTest.java` | `4514d059d847a9af88c964e2ebbc5fcb5d4a490f` |
| `application/usecase/ChangePartyLifecycleUseCaseTest.java` | `ed00994871a81a0c2efab39082669a6539c85819` |
| `application/usecase/PartyLifecycleObservationTest.java` | `26ae0bff175e19efd61985acb6d94e6af251fdf1` |
| `application/usecase/PartyMutationPortStub.java` | `f5482aa9568a75b31cfc5538184d6b697169ad2e` |
| `application/usecase/PartyReadUseCasesTest.java` | `f32c39ee8930424bf0742cecffe5573213953f66` |
| `application/usecase/PatchPartyUseCaseTest.java` | `ae2a04d17b7b09b0709a65ca07520e3b30c4a234` |
| `architecture/ArchitectureRules.java` | `18e65d12e7b160e06b1336bfa243a44c92dda3b1` |
| `architecture/CleanArchitectureTest.java` | `030d86e8786f4b94c0510bb5d175c7b327024774` |
| `domain/model/PartyDisplayNameCorrectionTest.java` | `e17aa7bb2465801408b62ce4af6afa40d5c3c248` |
| `domain/model/PartyLifecycleTest.java` | `2a514c56a2ec53ee258353a3913b795c55022b68` |
| `domain/policy/PartyActivationPolicyTest.java` | `39fa6c76693b64157e0872a79ff49fa4550e55f2` |
| `infrastructure/configuration/ApplicationUseCaseProducerTest.java` | `a484e3c3c97162884eb569f41eb35b158d79d789` |
| `infrastructure/messaging/OutboxMessageSerializerTest.java` | `bfff242a43ae132b4c998373e673ddc510db9dcc` |
| `infrastructure/messaging/PartyOutboxPublisherTest.java` | `e3bcd042e9a799e34c99852eab39d69dcdfb885e` |
| `infrastructure/observability/PartyPersistenceObservabilityTest.java` | `521266e208864401a23f270e22fbdebd0795feee` |
| `infrastructure/persistence/HibernateReactiveLegalEntityRepositoryTest.java` | `cdbcf335970ff4d7589b64f7d33527cb08562560` |
| `infrastructure/persistence/HibernateReactivePartyMutationAdapterTest.java` | `2a340267c996109d85b0ff65d3de9397fbc15bfe` |
| `infrastructure/persistence/PartyLifecycleConcurrencyTest.java` | `aebb94e16a6d5946f04db7e67545b66543fc4b37` |
| `infrastructure/persistence/PartyLifecycleIdempotencyPersistenceTest.java` | `c09a707cc9aa5045950cc8403e96c48117c3fd24` |
| `infrastructure/persistence/PartyLifecycleSnapshotCodecTest.java` | `98cc7b87063f5f54a47eafe206642b81871a327c` |
| `infrastructure/persistence/PartyMutationTimeoutsTest.java` | `dd9f7016ba3d65f913d91fd1a1ec0c219b682d4f` |
| `infrastructure/persistence/PartyNamePageAccumulatorTest.java` | `2a59783af50abad67fc2bc57e6426f6f2a758204` |
| `infrastructure/persistence/PartyNameQueryTest.java` | `688319b5ba5e997c52ae9664b354f3dc5ebe7f5b` |
| `infrastructure/persistence/PartyPageReaderTest.java` | `a5069981c23b7ebc1d695fdb5bdf0f1fe403dbf1` |
| `infrastructure/persistence/PartyQuerySessionProbe.java` | `77d1647eeffd2edaccfdc9c387f161ad123ac556` |
| `infrastructure/persistence/PartyQuerySnapshotTest.java` | `559edcf17408fb0126e3dda2980638794bf90b4f` |
| `infrastructure/persistence/PartyQueryTimeoutsTest.java` | `ffed8eddd9d08fd4383730381344be47619187a3` |
| `infrastructure/persistence/PartyQueryVolumeTest.java` | `a4b16bfd5f9147c43659d5cec3bca0732d402134` |
| `infrastructure/persistence/PartyRootMutationPersistenceTest.java` | `f58dffcbe76c28200e7b1397e6050dd5080fdba7` |
| `infrastructure/persistence/PartyRootOutboxPersistenceTest.java` | `675ac04ffc6b25782fa95d370519909ecc7c1fb6` |
| `infrastructure/persistence/PartyRootReaderTest.java` | `c7093d3f24e99697c8681b4fa8bfed7b443d1b4b` |
| `infrastructure/persistence/PersistenceAdapterObservabilityTest.java` | `c5b1273e0ec51128668e3b0ef718b19c08a09136` |
| `infrastructure/persistence/ReactivePartyActivationWorkflowTest.java` | `434192b308b093a80606a95ee44f8b2867dbadb3` |
| `infrastructure/security/HmacPartyCursorAdapterTest.java` | `61b9f10351c070d15a14435de8747c1b10ab03a4` |
| `infrastructure/security/PartyCursorKeyMaterialTest.java` | `33d7c83d9bbeb0c2582579854a3b114d1bc972b6` |
| `support/FixedLifecycleClockProducer.java` | `b90157324125374492d7c382ca8b28c1f076984b` |
| `support/RootPartyFixtures.java` | `6bf620527ab30c8fd83d5b1b19bac56ef66bf7f0` |

## IntegrationTest

| Relative Java file | Saved Git blob |
|---|---|
| `PackagedRootContractIT.java` | `f3747fe291ecbbf86b1ba0bd210bcabbaf1b3c03` |
| `PartyLifecycleRestartIT.java` | `bea910d5e28df32bb8ffab31b5ba1c7a5f69353b` |
