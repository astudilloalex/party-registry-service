# Party Identifier Scenario Evidence Map

This map covers 27 Q/M/L requirements and 87 scenarios plus the confirmed issuing-entity refinement: **28 requirements and 90 scenarios**. The final acceptance table below is authoritative for delivered scenario evidence. The original planning/increment tables retain their historical Pending/partial states for chronology and are superseded by the final table, not represented as current blockers. Source scenario titles and requirement groupings remain in those original tables.

## Final scenario acceptance (task 14.6)

**Workflow status: 60/60, final task complete.** Runtime/quality scenario evidence below is reconciled. After the user-authorized artifact-only reorganization, `openspec validate complete-party-identifiers-contract --strict` passes; its JSON result is `valid: true` with `issues: []`. All 28 requirement titles, 90 scenario titles, original normative clauses, transition tables and observable outcome assertions remain intact. Concise requirement summaries refer to explicit shared normative rules within their first scenario; each specification declares those rules applicable to all scenarios of the requirement. The prior length-warning blocker is resolved without weakening behavior or strict validation.

All 90 scenarios are reconciled to the exact passing tests below. Test aliases link to their source files; `Alias.method` names identify executable cases, not proposed tests. **J** refers to the successful full 1,766-test JVM/reactive/HTTP/architecture milestone in [verification.md](verification.md#task-141-user-authorized-correction-and-successful-final-jvm-suite). **F** refers to the 18 final corrected-test cases/focused build after the two-method primary test split. **P** refers to the 34 packaged JVM tests in task 14.2. **N** refers to the 34 actual native tests in task 14.4. Final saved blobs and separate Sonar/JDTLS acceptance are in [java-inventory.md](java-inventory.md#task-145-final-saved-revision-acceptance).

P/N are executed compatibility/serialization/public-boundary coverage, not a claim that every internal race or extreme-count fixture runs inside a native process. Pure policies, impossible-count guards and coordinated database/fault scenarios use their appropriate J/F boundaries. Packaged suites exercise all eight routes, strict binding, safe pages/snapshots, framework failures, schema equality and actual restart. Each row combines the appropriate boundary evidence rather than replacing it with compilation or a baseline.

### Final test-source aliases

- [Read](../../../src/test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierReadResourceContractTest.java): actual historical GET/ownership/header/page HTTP tests.
- [Current](../../../src/test/java/com/alexastudillo/partyregistry/api/resource/NaturalPersonResourceContractTest.java): unchanged complete ascending current-identifier detail compatibility.
- [Page](../../../src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierHistoricalPageTest.java): real snapshot/keyset PostgreSQL tests.
- [Cursor](../../../src/test/java/com/alexastudillo/partyregistry/infrastructure/security/HmacPartyIdentifierCursorAdapterTest.java): authenticated scope/precision/rotation tests.
- [Query](../../../src/test/java/com/alexastudillo/partyregistry/api/support/PartyIdentifierQueryParametersTest.java): strict selector/filter/limit tests.
- [Mapping](../../../src/test/java/com/alexastudillo/partyregistry/api/mapper/PartyIdentifierPaginationMappingTest.java): managed safe DTO/pagination/count checks.
- [Create](../../../src/test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierCreateResourceContractTest.java): strict POST/admission/replay HTTP tests.
- [RegistrationDB](../../../src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/ReactivePartyIdentifierRegistrationAdapterTest.java): reservation/history/composition PostgreSQL tests.
- [Patch](../../../src/test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierPatchResourceContractTest.java): metadata/precondition/preservation HTTP tests.
- [Primary](../../../src/test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierPrimaryResourceContractTest.java): final split consent/preservation/audit and historical selection HTTP tests.
- [Lifecycle](../../../src/test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierLifecycleResourceContractTest.java): full transition matrix, validity, exact private audit and preservation HTTP tests.
- [DomainLifecycle](../../../src/test/java/com/alexastudillo/partyregistry/domain/model/PartyIdentifierLifecycleTest.java): deterministic pure transition/UTC validity tests.
- [ApplicationLifecycle](../../../src/test/java/com/alexastudillo/partyregistry/application/usecase/ChangePartyIdentifierLifecycleUseCaseTest.java): admitted-evidence orchestration with no catalog readmission/protection dependency.
- [Replay](../../../src/test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierLifecycleReplayContractTest.java): exact action identity and original-result HTTP tests.
- [Compatibility](../../../src/test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierCompatibilityContractTest.java): activation/withdrawal/released uniqueness compatibility HTTP tests.
- [Framework](../../../src/test/java/com/alexastudillo/partyregistry/api/resource/PartyIdentifierFrameworkContractTest.java): actual safe framework/unexpected/corruption HTTP tests.
- [Races](../../../src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierHttpConcurrencyTest.java): 37 observed-lock scenarios in eight coordinated HTTP/PostgreSQL cases.
- [Resilience](../../../src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PartyIdentifierWorkflowResilienceTest.java): real flushed failure/timeout/cancellation/recovery workflows.
- [Telemetry](../../../src/test/java/com/alexastudillo/partyregistry/api/observability/PartyIdentifierTelemetryContractTest.java): real accepted outcomes and concurrent correlation HTTP tests.
- [Issuer](../../../src/test/java/com/alexastudillo/partyregistry/api/resource/IssuerCodeResourceContractTest.java): initial/additional issuer normalization, validation and raw identity HTTP tests.
- [Packaged](../../../src/integrationTest/java/com/alexastudillo/partyregistry/PackagedPartyIdentifierContractIT.java): all eight routes, strict/chunked binding, historical recovery, safe pages and served schemas on actual JVM/native artifacts.
- [Restart](../../../src/integrationTest/java/com/alexastudillo/partyregistry/PartyLifecycleRestartIT.java): `identifierCreationLifecyclePrimaryAndExactReasonAuditSurviveTwoActualProcesses` on both JVM/native.

### Queries and shared boundary: Q01–Q21

| Scenario | Exact passing case(s) | Execution evidence |
|---|---|---|
| Q01 | Packaged.allEightOperationsForBothPartyTypesPreserveSafeHistoricalReplayAfterLaterReplacementAndWithdrawal; Packaged.servedIdentifierPathsParametersAndSchemasEqualTheApprovedStaticContract | P/N; J OpenAPI examples/structure |
| Q02 | Read.headerFailuresRetainAcceptedProcessEchoAndRejectInvalidProcess | J; P/N strict framework/context boundary |
| Q03 | Read.headerFailuresRetainAcceptedProcessEchoAndRejectInvalidProcess | J; P/N |
| Q04 | Telemetry.concurrentTenantsActorsAndFreshReplayProcessesRemainAttributedOnlyToTheirOwnRequests | J/F; P/N replay correlation |
| Q05 | Read.concealsCrossTenantAndWrongPartyTargets | J; P/N ownership errors |
| Q06 | Read.concealsCrossTenantAndWrongPartyTargets; Lifecycle.ownedAbsenceAndWrongOwnerAreConcealedBeforeVersionOrLifecycle | J; P/N |
| Q07 | Read.listsCompleteHistoryForBothTypesAndEveryPartyState; Read.retrievesEveryHistoricalStateWithoutReadmissionOrMutation | J; P/N both owner types |
| Q08 | Read.exactFilterIntersectionReturnsOnlyVerifiedPrimaryEvidence; Read.validUnknownAndMismatchedSchemesProduceEmptyOwnedPages | J; P/N filtered pages |
| Q09 | Query.acceptsDecimalRangeAndRejectsInvalidSuppliedLimits; Query.absentFiltersRetainTriStateAndDefaultOnlyTheLimit | J; P/N page serialization |
| Q10 | Read.invalidQueriesNeverReturnData; Read.canonicalParentAndChildSyntaxPrecedeLookups | J; P/N strict errors |
| Q11 | Read.listingPreservesTheCompleteStoredPartyAndIdentifierRows; Read.retrievesEveryHistoricalStateWithoutReadmissionOrMutation | J; P/N historical reads |
| Q12 | Current.retrievesInitialAndAdditionalCurrentIdentifiersWithoutDecryptingOrFilteringInactiveSchemes; Current.retrievesMoreThanFiftyIdentifiersInCreationAndIdentifierIdOrder; Current.returnsAnEmptyIdentifierArrayForLegacyPartiesAndPartiesWithoutCurrentIdentifiers | J; P/N existing packaged person detail |
| Q13 | Read.forwardPagesHaveExactCountsAndUnsignedTieOrdering; Read.backwardCursorRecoversThePreviousPage; Page.navigatesForwardDescendingMicrosecondAndUnsignedUuidTies | J; P/N signed forward/backward pages |
| Q14 | Cursor.rejectsEveryChangedScopeDimensionAndExactCase; Cursor.rejectsOtherResourceTokensEvenWithSharedKeysAndMatchingTuple; Read.cursorCannotCrossEffectiveLimitScope | J; P/N tampered cursors |
| Q15 | Page.deletingBoundaryDoesNotInvalidateLiveContinuation; Page.changingBoundaryFilterMembershipPreservesItsContinuationTuple | J PostgreSQL; P/N cursor encoding/navigation |
| Q16 | Page.emptyAndWrongOwnerScopesReturnBoundedZeroSlices; Mapping.emptyIdentifierPageRetainsAllFiveMetadataKeysAndExplicitNullDirections | J; P/N empty filtered pages |
| Q17 | Read.forwardPagesHaveExactCountsAndUnsignedTieOrdering; Page.continuedEmptyPagesRetainTotalsAndOnlyRealNeighborDirections | J; P/N exact/null-aware pages |
| Q18 | Framework.regroupedRoutingKeepsAllEightPublicOperationsAndTheirSafeSuccessContracts; Mapping.optionalNullPropertiesDisappearWithoutAddingStorageReasonOrPaginationFields; Packaged.allEightOperationsForBothPartyTypesPreserveSafeHistoricalReplayAfterLaterReplacementAndWithdrawal | J; P/N; generic resource/architecture assertions |
| Q19 | Framework.missingRoutesUnsupportedMethodsAndMediaRetainSharedEnvelopesAndAcceptedEcho; Resilience.realStatementAndOperationWaitsReturn503AfterRollbackAndPermitPoolAndKeyReuse | J; P/N framework envelopes |
| Q20 | Framework.unexpectedActualWorkflowFailureIsSanitizedAndHasNoAcceptedEffects; Framework.corruptCompletionFailsClosedWithoutRemutatingOrDisclosingStoredPrivateContent | J; P/N safe error serialization |
| Q21 | Mapping.mapsMaximumSupportedCountsExactly; Mapping.rejectsUnsupportedCountsBeforeConstructingSharedMetadata; Framework.unexpectedActualWorkflowFailureIsSanitizedAndHasNoAcceptedEffects | J managed count guard and shared unknown-failure boundary; P/N ordinary page serialization |

### Maintenance: M01–M38

| Scenario | Exact passing case(s) | Execution evidence |
|---|---|---|
| M01 | Patch.bodyThenSelectorsThenVersionUseStableCodesBeforeAnyMutation | J; P/N strict binding |
| M02 | Patch.absentWrongOwnerAndCrossTenantTargetsAreConcealedBeforeBusinessEvaluation; Primary.ownershipAndIdentifierAbsencePrecedeStaleVersion | J/F; P/N |
| M03 | Create.nullableDefaultsAndPropertyOrderingHaveOneHistoricalRetryIdentity | J; P/N default binding |
| M04 | Create.rawLimitsAndRequiredRulesUseTheirDeclaredPrecedenceWithoutNormalization | J; P/N required/strict body |
| M05 | Create.strictShapeTypesDuplicatesAndTrailingTokensFailBeforeKeysAndPaths | J; P/N strict decoders |
| M06 | Create.creationKeyValidationPrecedesPartyPathAndPreservesCardinalityAndBounds | J; P/N operation header errors |
| M07 | Create.everyOwnedPartyTypeAndStateReceivesIndependentSafeVersionZeroEvidence | J; P/N both-type creation |
| M08 | Create.failedKeyIsReusableAndSchemeFailuresRetainTheirExactStableCodes | J; P/N scheme/validation failures |
| M09 | Create.expirationIsOptionalAndInclusiveUsingOneControlledUtcDate | J; P/N absent/nullable dates |
| M10 | Create.provisionalCreationDoesNotDisplaceAnExistingVerifiedPrimary | J; P/N safe primary flags |
| M11 | Create.normalizedDuplicatesConflictWithoutConsumingAnotherKeyOrMutatingHistory; RegistrationDB.pendingVerifiedAndPastDateEvidenceKeepTheirActiveValueReservation | J; P/N ordinary registration compatibility |
| M12 | RegistrationDB.pendingVerifiedAndPastDateEvidenceKeepTheirActiveValueReservation | J actual PostgreSQL reservation; P/N ordinary lifecycle serialization |
| M13 | Compatibility.rejectionAndRevocationReleaseUniquenessWhileKeepingCompleteHistoricalRowsAndCreationResults; RegistrationDB.terminalHistoryReleasesUniquenessWithoutDeletingOriginalRecords | J; P/N withdrawal/history |
| M14 | Races.distinctKeyNormalizedDuplicateCreationRaceLeavesOneIdentifierAndNoLosingCompletion | J coordinated independent requests |
| M15 | Create.originalCreationReplaysAfterCurrentEvidenceAndPartyChangeWithFreshCorrelation; Restart.identifierCreationLifecyclePrimaryAndExactReasonAuditSurviveTwoActualProcesses | J; P/N actual restart |
| M16 | Create.issuerCanonicalizationAndExactRawValuesDoNotRewriteFingerprintIdentity | J; P/N historical creation recovery |
| M17 | Races.equivalentAndChangedCreationKeysHaveExactlyOneAcceptedOriginalForBothTypes; Create.failedKeyIsReusableAndSchemeFailuresRetainTheirExactStableCodes | J; P/N historical recovery |
| M18 | Patch.suppliedNullClearsAndOmittedMetadataAndVerificationStayIntact | J; P/N patch/null binding |
| M19 | Patch.expiredByDateEvidenceNeedsRepairAndResultingIntervalsCannotBeInverted | J; P/N patch serialization |
| M20 | Patch.bodyThenSelectorsThenVersionUseStableCodesBeforeAnyMutation | J; P/N closed decoders |
| M21 | Patch.bothTypesAndEligibleStatesCorrectOnlyMetadataAuditAndOneIdentifierVersion | J; P/N retained verification |
| M22 | Patch.terminalAndExhaustedTargetsRejectWithoutAnyStoredOrLogicalEffect | J; P/N lifecycle errors |
| M23 | Patch.expiredByDateEvidenceNeedsRepairAndResultingIntervalsCannotBeInverted | J; P/N validity metadata |
| M24 | Patch.identicalCorrectionAdvancesOnceAndEveryReplayHeaderIsIgnored | J; P/N independent versions |
| M25 | Patch.staleVersionPrecedesStateAndPartyVersionNeverSubstitutesForIdentifierVersion | J; P/N version preconditions |
| M26 | Patch.bodyThenSelectorsThenVersionUseStableCodesBeforeAnyMutation; Lifecycle.versionStateExhaustionAndExpirationHaveTheirDeclaredPrecedence; Primary.stateVersionExhaustionAndDateFailuresPreserveEveryPriorFieldAndEffect | J/F; P/N canonical If-Match |
| M27 | Races.independentPatchAndTransitionAtOneVersionHaveOneWinnerAndOnlyMatchingEffects; Races.replacementVersusFormerCorrectionOrWithdrawalMatchesAnAllowedSerialOutcome | J coordinated PostgreSQL/HTTP |
| M28 | Primary.stateVersionExhaustionAndDateFailuresPreserveEveryPriorFieldAndEffect | J/F; P/N selection eligibility |
| M29 | Primary.explicitReplacementAdvancesBothVersionsWithOneActorInstantAndCompleteEffectsForBothTypes; Primary.alreadyPrimaryAdvancesOnceAndAbsentEmptyOrFalseBodiesReplayOneResult | J/F; P/N absent expiration selection |
| M30 | Primary.strictOptionalBodyPrecedesSelectorsVersionAndOptionalKey; Primary.alreadyPrimaryAdvancesOnceAndAbsentEmptyOrFalseBodiesReplayOneResult | J/F; P/N strict false defaults |
| M31 | Primary.distinctSchemesKeepIndependentPrimariesAndProvisionalFlagsAreNotDisplaced; Races.unrelatedSchemeScopeProgressesWhileAnotherPrimaryRequestIsActuallyWaiting | J/F; P/N scoped replacement |
| M32 | Primary.replacementWithoutConsentPreservesBothRecordsAndDoesNotConsumeKeyForBothTypes; Primary.explicitReplacementOfPastDatePrimaryPreservesBothRecordsAndParentForBothTypes; Primary.explicitReplacementAdvancesBothVersionsWithOneActorInstantAndCompleteEffectsForBothTypes | J/F; P/N atomic replacement |
| M33 | Primary.explicitReplacementOfPastDatePrimaryPreservesBothRecordsAndParentForBothTypes | J/F; P/N replacement wire shape |
| M34 | Races.differentFirstPrimaryCandidatesAndProvisionalVerifiesHaveOneScopedWinner | J coordinated PostgreSQL/HTTP |
| M35 | Primary.exhaustedFormerPrimaryPreventsBothWritesAndKeyConsumption | J/F; P/N selection binding/result |
| M36 | Resilience.everyFlushedPrimaryCheckpointRollsBackBothRecordsAndAllowsSameKeyRecovery | J actual flushed fault checkpoints |
| M37 | Resilience.lostPostCommitDeliveryKeepsAcceptedStateAndRetryRecoversWithoutFalseRollback; Primary.historicalReplayAfterLaterReplacementTouchesNeitherCurrentRecordAndEchoesFreshProcess; Restart.identifierCreationLifecyclePrimaryAndExactReasonAuditSurviveTwoActualProcesses | J/F; P/N actual restart |
| M38 | Patch.bothTypesAndEligibleStatesCorrectOnlyMetadataAuditAndOneIdentifierVersion; Primary.explicitReplacementOfPastDatePrimaryPreservesBothRecordsAndParentForBothTypes; Compatibility.rejectionAndRevocationReleaseUniquenessWhileKeepingCompleteHistoricalRowsAndCreationResults | J/F; P/N complete historical result/storage |

### Lifecycle and action replay: L01–L28

| Scenario | Exact passing case(s) | Execution evidence |
|---|---|---|
| L01 | Lifecycle.strictBodySyntaxPrecedesSelectorsVersionsAndOptionalKeys; Packaged.bodylessVerificationUsesActualChunkedBytesOnPackagedExecution | J; P/N actual chunked transport |
| L02 | Lifecycle.withdrawalsRetainVerificationAndExactBinaryReasonsWithEventsDisabled; Replay.exactReasonsIncludingControlsAndReorderedPropertiesReplayButChangedReasonsConflict | J; P/N reason binding and restart audit |
| L03 | Replay.syntaxStillPrecedesCompletedReplayAndFailedKeyRemainsReusable; Primary.strictOptionalBodyPrecedesSelectorsVersionAndOptionalKey | J/F; P/N strict body/header binding |
| L04 | Lifecycle.ownedAbsenceAndWrongOwnerAreConcealedBeforeVersionOrLifecycle; Lifecycle.versionStateExhaustionAndExpirationHaveTheirDeclaredPrecedence | J; P/N absence/version failures |
| L05 | Lifecycle.fullMatrixForBothTypesPreservesProtectedHistoryAndParentWithExactEffects | J full 30-entry HTTP matrix; P/N successful transitions |
| L06 | Lifecycle.fullMatrixForBothTypesPreservesProtectedHistoryAndParentWithExactEffects | J every forbidden state/action with full preservation; P/N lifecycle error serialization |
| L07 | Lifecycle.verificationUsesAcceptedActorAndOneMicrosecondInstantAndRepeatConflicts | J; P/N conflict/replay boundary |
| L08 | Lifecycle.verificationUsesAcceptedActorAndOneMicrosecondInstantAndRepeatConflicts | J; P/N original attribution |
| L09 | DomainLifecycle.verificationValidityUsesInclusiveUtcDateAndAllowsNoExpiration; Lifecycle.versionStateExhaustionAndExpirationHaveTheirDeclaredPrecedence | J controlled UTC policy/HTTP; P/N date serialization |
| L10 | ApplicationLifecycle.verificationUsesOneUtcInstantAndAcceptedActorWithoutCatalogOrProtection; Restart.identifierCreationLifecyclePrimaryAndExactReasonAuditSurviveTwoActualProcesses | J no catalog readmission in new verification; P/N retired scheme historical recovery |
| L11 | Lifecycle.provisionalConflictPreservesBothRowsAndNonprimaryVerificationStillWorks | J; P/N verification/flag serialization |
| L12 | Lifecycle.provisionalConflictPreservesBothRowsAndNonprimaryVerificationStillWorks | J; P/N primary conflict boundary |
| L13 | Lifecycle.provisionalConflictPreservesBothRowsAndNonprimaryVerificationStillWorks | J; P/N nonprimary verification |
| L14 | Races.differentFirstPrimaryCandidatesAndProvisionalVerifiesHaveOneScopedWinner | J coordinated PostgreSQL/HTTP |
| L15 | Lifecycle.fullMatrixForBothTypesPreservesProtectedHistoryAndParentWithExactEffects | J; P/N rejection clears flag |
| L16 | Lifecycle.withdrawalsRetainVerificationAndExactBinaryReasonsWithEventsDisabled; Restart.identifierCreationLifecyclePrimaryAndExactReasonAuditSurviveTwoActualProcesses | J; P/N actual private binary audit restart |
| L17 | DomainLifecycle.withdrawalAllowsPastDatesClearsFlagsAndRetainsPriorVerificationEvidence; Lifecycle.fullMatrixForBothTypesPreservesProtectedHistoryAndParentWithExactEffects | J; P/N withdrawal preservation |
| L18 | Compatibility.nonprimaryVerificationFeedsExistingActivationAndLastEvidenceRevocationNeverChangesParty | J; P/N Party compatibility |
| L19 | Replay.changedPartyIdentifierAndVersionConflictBeforeCurrentAbsenceOrState; Replay.exactReasonsIncludingControlsAndReorderedPropertiesReplayButChangedReasonsConflict; Primary.alreadyPrimaryAdvancesOnceAndAbsentEmptyOrFalseBodiesReplayOneResult | J/F; P/N action key identity |
| L20 | Replay.withdrawalDefaultBodiesReplayWithoutReevaluationOrDuplicateAuditAndEvents; Primary.alreadyPrimaryAdvancesOnceAndAbsentEmptyOrFalseBodiesReplayOneResult | J/F; P/N optional defaults |
| L21 | Replay.sameKeyHasIndependentCreationActionAndTenantScopes; Telemetry.concurrentTenantsActorsAndFreshReplayProcessesRemainAttributedOnlyToTheirOwnRequests | J/F; P/N fresh correlation |
| L22 | Replay.originalVerificationReplaysAfterCorrectionRevocationAndPartyArchivalWithFreshCorrelation; Restart.identifierCreationLifecyclePrimaryAndExactReasonAuditSurviveTwoActualProcesses | J; P/N actual restart |
| L23 | Primary.historicalReplayAfterLaterReplacementTouchesNeitherCurrentRecordAndEchoesFreshProcess; Restart.identifierCreationLifecyclePrimaryAndExactReasonAuditSurviveTwoActualProcesses | J/F; P/N actual restart |
| L24 | Replay.syntaxStillPrecedesCompletedReplayAndFailedKeyRemainsReusable; Framework.corruptCompletionFailsClosedWithoutRemutatingOrDisclosingStoredPrivateContent; Resilience.everyFlushedWithdrawalCheckpointRollsBackAuditCompletionAndEventAndKeyIsReusable | J; P/N immutable snapshots/recovery |
| L25 | Races.allKeyedActionsConvergeOrConflictOnChangedEffectiveInputsWithoutDuplicateEffects | J coordinated keys/versions/audit/events |
| L26 | Races.independentPatchAndTransitionAtOneVersionHaveOneWinnerAndOnlyMatchingEffects | J coordinated independent actions |
| L27 | Resilience.everyFlushedWithdrawalCheckpointRollsBackAuditCompletionAndEventAndKeyIsReusable; Resilience.cancellingActualWithdrawalAfterFlushedAuditClosesSessionBeforePoolAndKeyReuse | J real flushed failure/cancellation/pool reuse |
| L28 | Compatibility.nonprimaryVerificationFeedsExistingActivationAndLastEvidenceRevocationNeverChangesParty | J; P/N existing activation contract |

### Confirmed issuing-entity refinement: R01–R03

| Scenario | Exact passing case(s) | Execution evidence |
|---|---|---|
| R01 | Issuer.bothOwnerTypesAndAdditionalRegistrationReturnCanonicalOrAbsentIssuer | J; P/N initial/additional safe DTO/binding |
| R02 | Issuer.bothOwnerTypesAndAdditionalRegistrationReturnCanonicalOrAbsentIssuer; Issuer.creationReplayRetainsOriginalParsedIssuerIdentityBeforeWriteCanonicalization | J; P/N retained creation snapshots |
| R03 | Issuer.newIssuerSyntaxFailuresKeepSpecificSafeEnvelopesBeforeBusinessEvaluation; Issuer.initialIssuerSyntaxFailsBeforeCountryValidationForBothPartyTypes | J; P/N strict creation decoders |

The source titles in the historical tables below account for every row (21 Q + 38 M + 28 L + 3 R = 90). No scenario is deferred or accepted solely from planned coverage. Foundation migration/upgrade/codec/cursor/Domain/Application tests and all-eight generic HTTP signatures remain complementary evidence in the corresponding completed task sections of verification.md. Final review preserves the approved exception for status/code-only validation responses, separate historical/current reads, immutable V1–V7 migrations, additive V8 and documented compatible rollout/rollback.

## Historical planning and increment evidence

## Section-13 implementation handoff

### Final runtime acceptance update (2026-10-10)

Tracking now reaches **58/60**. Tasks 14.1–14.4 pass on the delivered source after the user-authorized standalone fixture mapper correction: 1,766 full JVM tests, 34 packaged JVM tests, successful new Mandrel 25 native executable generation and 34 actual native integration tests, all with zero failures/skips. The native identifier contract suite and retained-data two-process identifier restart case execute successfully; see the final task entries in [verification.md](verification.md) and Gradle test reports. The section-13 notes below remain historical handoff context. Final per-scenario acceptance is still pending because 14.5 requires resolved-model saved-revision Sonar/JDTLS evidence, followed by 14.6 reconciliation and strict validation. Native success is now established by native execution, not inferred from JVM results.

Implementation tracking is **54/60**. Accepted focused evidence through 13.4 is recorded in [verification.md](verification.md) and final saved-file revisions in [java-inventory.md](java-inventory.md). The newer developer instruction defers unavailable/nonblocking local Sonar review to final 14.5; pending diagnostics are not called passing. Native execution and complete per-scenario acceptance remain explicitly pending in 14.4–14.6. Do not interpret an old Pending table cell as absent implementation or promote it to complete native acceptance from a JVM result alone.

- Query/maintenance/lifecycle routes: all eight generic DTO envelopes and actual successful public paths are pinned after the Quarkus routing correction; selected framework/body/ownership/corrupt-completion failures and actual production-budget 503 waits pass.
- Scope/version/key arbitration: 37 observed-lock HTTP/PostgreSQL scenarios cover both Party types, same-key/default/changed-input outcomes, duplicate values, one-target versions, different-target/provisional primaries, explicit replacements and mixed correction/withdrawal races.
- Complete acceptance/cleanup: flushed real-workflow checkpoints, statement/lock/work timeouts, cancellation/session closure, pool/key reuse and postcommit delivery recovery pass without weakened budgets.
- Compatibility: 71 related selected regression cases include existing Party/subtype/current-identifier/nationality/catalog behavior and new last-evidence/released-uniqueness preservation.
- Packaged JVM/native preparation: five actual packaged contract/classpath cases and one actual two-process retained-data restart case pass, including strict/chunked bodies, source schemas, signed pages, nine original create/action/primary results, exact reasons and nonduplicated events. Explicit request/decoder/response metadata is prepared; actual native build/runtime remains unexecuted.

Final 14.6 must reconcile each existing requirement/scenario row with its exact final saved-file tests, local analysis and required native results. This handoff is evidence context, not a blanket completed scenario claim or an archive authorization.

Sources: **Q** = [queries](specs/party-identifier-queries/spec.md), **M** = [maintenance](specs/party-identifier-maintenance/spec.md), **L** = [lifecycle](specs/party-identifier-lifecycle/spec.md). Each group heading is the exact source requirement title. Boundary abbreviations: **contract** = static OpenAPI/schema checks; **domain** = pure aggregate/policy tests; **application** = workflow/port-double tests; **API** = parser/mapper tests; **DB** = real reactive PostgreSQL tests; **HTTP** = live resource tests; **packaged/native** = actual executable integration tests. Task references use the numeric IDs in `tasks.md`.

## Q: Complete published party-identifiers contract

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q01 | Eight operations agree with their published contract | contract, HTTP, packaged/native | 2.1–2.2, 6.7, 8.3, 9.3, 10.3, 11.3, 13.1, 14.4 | Pending |

## Q: Trusted context and process correlation

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q02 | Header precedence and accepted echo | HTTP, packaged/native | 2.3, 12.4–12.5, 13.1, 14.4 | Pending |
| Q03 | Invalid process is not echoed | HTTP, packaged/native | 2.3, 12.4–12.5, 13.1, 14.4 | Pending |
| Q04 | Concurrent context remains isolated | coordinated HTTP, observation, packaged/native | 12.1–12.2, 12.5, 13.1–13.2, 14.4 | Pending |

## Q: Tenant and Party ownership concealment

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q05 | Missing and cross-tenant Party are indistinguishable | application, DB, HTTP, packaged/native | 6.2–6.4, 6.7, 13.1, 14.4 | Pending |
| Q06 | Nested identifier cannot escape its Party | application, DB, HTTP, packaged/native | 6.2, 6.4, 6.7, 7.4–7.5, 9.3, 10.3, 11.3, 13.1, 14.4 | Pending |
| Q07 | Both Party types and every Party state remain readable | application, DB, HTTP, packaged/native | 6.2–6.4, 6.7, 13.1, 14.4 | Pending |

## Q: Strict read selectors and filters

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q08 | Filter intersection is exact | API, DB, HTTP, packaged/native | 6.3, 6.5, 6.7, 13.1, 14.4 | Pending |
| Q09 | Limit boundaries and absence | API, application, HTTP, packaged/native | 6.1, 6.3–6.5, 6.7, 13.1, 14.4 | Pending |
| Q10 | Invalid selectors and query values | API, HTTP, packaged/native | 2.1, 6.5, 6.7, 13.1, 14.4 | Pending |

## Q: Complete historical listing and masked detail

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q11 | Historical and past-date identifiers remain visible | DB, HTTP, packaged/native | 6.2–6.3, 6.7, 13.1, 14.4 | Pending |
| Q12 | Current natural-person projection remains narrower | DB, HTTP compatibility, packaged/native | 6.2, 12.6, 13.1, 14.4 | Pending |

## Q: Deterministic scoped cursor navigation

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q13 | Timestamp ties navigate without duplication | cursor, DB, HTTP, packaged/native | 6.1, 6.3, 6.7, 13.1, 14.4 | Pending |
| Q14 | Cursor cannot cross scope | cursor, application, HTTP, packaged/native | 6.1, 6.4, 6.7, 13.1, 14.4 | Pending |
| Q15 | Boundary changes do not invalidate navigation | cursor, DB, HTTP, packaged/native | 6.1, 6.3, 6.7, 13.1, 14.4 | Pending |

## Q: Exact collection metadata and empty outcomes

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q16 | Empty owned collection succeeds | application, DB, API, HTTP, packaged/native | 6.3–6.4, 6.6–6.7, 13.1, 14.4 | Pending |
| Q17 | First middle and final pages have exact metadata | DB, API, HTTP, packaged/native | 6.3, 6.6–6.7, 13.1, 14.4 | Pending |

## Q: Safe shared envelopes and failure behavior

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| Q18 | Every operation returns only its safe envelope | API, HTTP, outbox codec, architecture, packaged/native | 2.2–2.3, 6.6–6.7, 7.6, 8.3, 9.3, 10.3, 11.3, 13.1, 14.4, 14.6 | Pending |
| Q19 | Framework and dependency failures retain the envelope | HTTP, packaged/native | 4.3, 12.3–12.4, 13.1, 14.4 | Partial foundation: task 4.3 [verification](verification.md) / [saved revisions](java-inventory.md), `PartyIdentifierErrorContractTest` pins known-error exact envelopes/echoes. Actual identifier-route framework/dependency and packaged/native cases remain pending. |
| Q20 | Unexpected failure is sanitized | completion codec, HTTP, packaged/native | 7.2, 12.4, 13.1–13.2, 14.4 | Partial HTTP foundation: task 4.3 [verification](verification.md), unknown identifier invariant produces exact 500/server-error without its private cause. Completion corruption, actual routes and packaged/native cases remain pending. |
| Q21 | Unrepresentable collection count does not wrap | API, HTTP failure injection, packaged/native | 6.6, 12.4, 13.1, 14.4 | Pending |

## M: Maintenance follows the common identifier boundary

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M01 | Body validation precedes a missing version | API, HTTP, packaged/native | 9.1, 9.3, 13.1, 14.4 | Pending |
| M02 | Absence precedes a stale version | application, HTTP, packaged/native | 9.2–9.3, 10.2–10.3, 11.2–11.3, 13.1, 14.4 | Pending |

## M: Strict additional registration input

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M03 | Optional input and primary default | API, application, HTTP, packaged/native | 2.1, 4.1, 8.1, 8.3, 13.1, 14.4 | Task 8.3 binding/default/equivalent-retry cases pass; [verification](verification.md). Expanded packaged/native scenarios remain designated later checks. |
| M04 | Required fields and raw length boundaries | API, HTTP, packaged/native | 2.1, 8.3, 13.1, 14.4 | Task 8.3 real raw-limit/required-priority cases pass; no rows/completions accepted. Expanded packaged/native checks remain later tasks. |
| M05 | Strict input rejects client-owned properties and coercion | API, HTTP, packaged/native | 2.1, 8.3, 13.1, 14.4 | Six managed binding cases plus real strict-shape case pass after executable red, including null-primary, escaped duplicates and literal-null trailing input. Packaged/native expansion remains later. |
| M06 | Existing creation-key precedence is preserved | API, HTTP, packaged/native | 2.1, 8.3, 13.1, 14.4 | Task 8.3 real key-validation and malformed-path/body cases prove body→key→Party; 128 accepted /129 rejected. Packaged/native expansion remains later. |

## M: Eligible independent registration

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M07 | Both Party types receive additional identifiers | application, DB, HTTP, packaged/native | 8.1–8.3, 12.6, 13.1, 14.4 | Task 8.3 live matrix covers both types/all Party states, safe pending/version-0 201 and unchanged root; task 8.2 retains complete subtype/database preservation. Expanded packaged/native cases remain later. |
| M08 | Scheme errors retain their specific codes | application, HTTP, packaged/native | 4.3, 8.1, 8.3, 13.1, 14.4 | Task 8.3 real unknown/inactive/incompatible codes and zero accepted effects pass. Expanded packaged/native cases remain later. |
| M09 | Expiration boundaries and optionality | domain, application, HTTP, packaged/native | 8.1, 8.3, 12.6, 13.1, 14.4 | Fixed non-UTC-clock real HTTP accepts null/absent/UTC-today despite legacy requirement, rejects previous-day/inversion and retains future issue-only eligibility. Expanded packaged/native cases remain later. |
| M10 | Provisional primary coexists with existing evidence | domain, application, DB, HTTP, packaged/native | 3.4, 8.1, 8.3, 13.1, 14.4 | Task 8.3 real provisional true beside verified true passes with original verified stored row/version unchanged. Expanded packaged/native cases remain later. |

## M: Active-value uniqueness and release

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M11 | Same-Party and other-Party duplicates conflict | DB, HTTP, packaged/native | 7.5, 8.3, 12.1, 13.1, 14.4 | Task 8.3 real same-Party normalized duplicate, unchanged detail and failed-key reuse pass; both-owner database reservation retained from 8.2. Race/expanded packaged/native acceptance remains later. |
| M12 | Time passing alone preserves reservation | DB, HTTP, packaged/native | 7.5, 8.3, 12.6, 13.1, 14.4 | Pending |
| M13 | Withdrawal releases uniqueness without deleting history | DB, HTTP compatibility, packaged/native | 7.5, 10.3, 12.6, 13.1, 14.4 | Pending |
| M14 | Distinct-key duplicate race has one winner | coordinated DB/HTTP | 12.1 | Pending |

## M: Durable additional registration replay

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M15 | Historical retry survives restart and mutation | application, completion codec, HTTP, two-process packaged/native | 7.1–7.2, 8.1, 8.3, 13.2, 14.4 | Task 8.3 real original 201 after current revocation/correction and Party archival, both types/fresh actor/process, unchanged current stored record/counts. Actual two-process/native acceptance remains 13.2/14.4. |
| M16 | Normalization does not erase request differences | fingerprint, application, HTTP, packaged/native | 4.1, 7.1, 8.1, 8.3, 13.1, 14.4 | Task 8.3 real original raw issuer/value conflicts and default/property-order equivalence pass; existing HMAC/workflow foundations retained. Expanded packaged/native cases remain later. |
| M17 | Concurrent replay and failed key reuse | application, coordinated DB/HTTP, packaged/native | 7.4, 7.7, 8.1, 8.3, 12.1, 12.3, 13.1, 14.4 | Task 8.3 real scheme/uniqueness rejection key reuse passes with exact counts. Actual workflow races, resilience and expanded packaged/native acceptance remain later tasks. |

## M: Strict partial metadata correction

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M18 | Omission preserves and null clears | domain, API, application, HTTP, packaged/native | 3.2, 9.1–9.3, 13.1, 14.4 | Pending |
| M19 | Partial update validates the resulting interval | domain, application, HTTP, packaged/native | 3.2, 9.2–9.3, 13.1, 14.4 | Pending |
| M20 | Non-metadata input is rejected | API, HTTP, packaged/native | 2.1, 9.1, 9.3, 13.1, 14.4 | Pending |

## M: Metadata correction eligibility and audit

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M21 | Verified metadata is corrected without reverification | domain, application, DB, HTTP, packaged/native | 3.1–3.2, 7.5, 9.2–9.3, 13.1, 14.4 | Pending |
| M22 | Terminal records cannot be corrected | domain, application, HTTP, packaged/native | 3.2, 9.2–9.3, 13.1, 14.4 | Pending |
| M23 | Past-date evidence must be repaired coherently | domain, application, HTTP, packaged/native | 3.2, 9.2–9.3, 13.1, 14.4 | Pending |
| M24 | Identical correction still advances once | domain, application, DB, HTTP, packaged/native | 3.1–3.2, 7.5, 9.2–9.3, 13.1, 14.4 | Pending |

## M: Identifier-version preconditions and exhaustion

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M25 | Party version is not the identifier precondition | API, application, HTTP, packaged/native | 4.1, 9.1–9.3, 10.2–10.3, 11.2–11.3, 13.1, 14.4 | Partial typed-contract foundation: tasks 4.1/4.3 [verification](verification.md), commands and mismatch failures use `PartyIdentifierVersion`; known HTTP probe yields 412. Real request preconditions/races and packaged/native behavior remain pending. |
| M26 | Syntax boundaries and exhaustion are distinct | domain, API, application, HTTP, packaged/native | 3.1, 3.4, 4.3, 9.1, 9.3, 10.3, 11.3, 13.1, 14.4 | Partial Domain/translation foundation: tasks 3.1/3.4/4.3 [verification](verification.md) pin checked capacity and exact 409 identifier-version-exhausted. Version-header parsing, real routes and packaged/native cases remain pending. |
| M27 | PATCH and lifecycle race share one identifier version | coordinated DB/HTTP | 7.4–7.5, 12.1–12.2 | Pending |

## M: Strict primary-selection input and eligibility

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M28 | Only usable verified identifiers can be selected | domain, application, HTTP, packaged/native | 3.4, 11.2–11.3, 13.1, 14.4 | Pending |
| M29 | Expiration boundary and absent expiration permit selection | domain, application, HTTP, packaged/native | 3.4, 11.2–11.3, 13.1, 14.4 | Pending |
| M30 | Replacement option has a strict false default | API, fingerprint, application, HTTP, packaged/native | 4.1, 7.1, 11.1–11.3, 13.1, 14.4 | Pending |

## M: Scoped atomic primary ownership

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M31 | Different schemes have independent primaries | domain, application, DB, HTTP, packaged/native | 3.4, 7.4, 11.2–11.3, 13.1, 14.4 | Pending |
| M32 | Replacement is explicit and atomic | domain, application, DB, HTTP, packaged/native | 3.4, 7.5, 11.2–11.3, 13.1, 14.4 | Pending |
| M33 | Expired-by-date former primary is explicitly displaced | domain, application, DB, HTTP, packaged/native | 3.4, 11.2–11.3, 13.1, 14.4 | Pending |
| M34 | Concurrent first selections cannot create two primaries | coordinated DB/HTTP | 7.4–7.5, 12.2 | Pending |
| M35 | Former-primary exhaustion blocks the whole replacement | domain, application, DB, HTTP, packaged/native | 3.4, 11.2–11.3, 13.1, 14.4 | Pending |

## M: Maintenance has one durable observable outcome

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| M36 | Late replacement failure restores both identifiers | transaction context, DB/HTTP fault injection | 7.7, 12.3 | Pending |
| M37 | Missing response does not duplicate creation or replacement | completion codec, DB, HTTP, two-process packaged/native | 7.2, 8.3, 11.3, 13.2, 14.4 | Pending |
| M38 | Independent Party and existing registration results are preserved | migration, DB, HTTP compatibility, packaged/native | 5.2, 7.5, 9.3, 11.3, 12.6, 13.1–13.2, 14.4 | Pending |

## L: Lifecycle requests follow the common boundary

Task 10.3 runtime evidence is recorded in [verification.md](verification.md): seven real HTTP cases pass, including the 30-entry matrix, body/header precedence, safe envelopes, ownership, one verification instant, private exact reason audit and preserved parent/protected history. The eight saved-file quality analyses are pending; rows below are not promoted to completed scenario acceptance, and packaged/native/restart/race coverage remains assigned to subsequent tasks. Task 10.2 Application evidence is accepted after developer-confirmed six-file analyses and 21 passing selected workflow checks.

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L01 | Bodyless verification is strict | API, HTTP including chunked input, packaged/native | 2.1, 10.1, 10.3, 13.1, 14.4 | Pending |
| L02 | Optional reasons accept absence and exact strings | API, fingerprint, audit codec/DB, HTTP, packaged/native | 4.1, 7.1, 7.3, 10.1–10.3, 13.1, 14.4 | Pending |
| L03 | Syntax precedes business and replay | API, HTTP, packaged/native | 9.1, 10.1, 10.3–10.4, 11.1, 13.1, 14.4 | Pending |
| L04 | Absence and version precede state | application, HTTP, packaged/native | 10.2–10.3, 13.1, 14.4 | Pending |

## L: Explicit identifier transition matrix

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L05 | All permitted transitions work for both Party types | domain, application, HTTP, packaged/native | 3.3, 10.2–10.3, 13.1, 14.4 | Pending |
| L06 | Every forbidden matrix entry rejects without mutation | domain, application, DB/HTTP preservation, packaged/native | 3.3, 10.2–10.3, 13.1, 14.4 | Pending |
| L07 | Repeating a completed transition without replay conflicts | domain, application, HTTP, packaged/native | 3.3, 10.2–10.4, 13.1, 14.4 | Pending |

## L: Operator-confirmed verification and validity

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L08 | Accepted user and one instant define verification | domain, application, DB, HTTP, packaged/native | 3.1, 3.3, 10.2–10.3, 13.1, 14.4 | Pending |
| L09 | UTC midnight and inclusive expiration are deterministic | domain, application, HTTP controlled clock, packaged/native | 3.3, 10.2–10.3, 13.1, 14.4 | Pending |
| L10 | Scheme withdrawal does not erase admitted evidence | application, DB, HTTP compatibility, packaged/native | 7.4, 10.2–10.3, 12.6, 13.1, 14.4 | Pending |

## L: Verification respects provisional primary ownership

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L11 | First provisional primary becomes verified | domain, application, DB, HTTP, packaged/native | 3.4, 10.2–10.3, 13.1, 14.4 | Pending |
| L12 | Conflicting provisional verification is rejected | domain, application, DB/HTTP preservation, packaged/native | 3.4, 10.2–10.3, 13.1, 14.4 | Pending |
| L13 | Nonprimary verification is independent | domain, application, DB, HTTP, packaged/native | 3.4, 10.2–10.3, 13.1, 14.4 | Pending |
| L14 | Concurrent provisional primaries have one verified winner | coordinated DB/HTTP | 7.4–7.5, 12.2 | Pending |

## L: Rejection and revocation retain safe history

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L15 | Rejection removes a provisional flag | domain, application, DB, HTTP, packaged/native | 3.3, 10.2–10.3, 13.1, 14.4 | Pending |
| L16 | Revocation retains verification and audit reason | domain, audit codec/DB, HTTP, two-process packaged/native | 3.3, 7.3, 10.2–10.3, 13.2, 14.4 | Pending |
| L17 | Past-date evidence can be withdrawn | domain, application, HTTP, packaged/native | 3.3, 10.2–10.3, 13.1, 14.4 | Pending |
| L18 | Withdrawing last evidence does not change the Party automatically | DB, HTTP compatibility, packaged/native | 10.3, 12.6, 13.1, 14.4 | Pending |

## L: Durable action key scope and request equivalence

Accepted task 10.4 [runtime/quality evidence](verification.md) adds six real HTTP replay cases: historical verification after correction/revocation/archival for both Party types, original attribution plus retry process echoes, exact/default reason equivalence, changed-input conflicts and independent tenant/create/verify/revoke key scopes with no duplicate audit/events. This establishes live lifecycle coverage for L19–L21 and the nonrestart portion of L23/L25; packaged restart/native and primary-action coverage remain their assigned later tasks. Accepted 11.1 binding/mapping evidence proves strict primary defaults; 11.2 workflow runtime is green but its local quality gate remains pending, so it is not claimed as accepted primary implementation evidence.

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L19 | All effective action inputs participate in key conflicts | fingerprint, application, HTTP, packaged/native | 4.1, 7.1, 10.4, 11.3, 13.1, 14.4 | Pending |
| L20 | Default-equivalent bodies replay | API, fingerprint, application, HTTP, packaged/native | 4.1, 7.1, 10.1, 10.4, 11.1, 11.3, 13.1, 14.4 | Pending |
| L21 | Actor changes and independent scopes | fingerprint, application, DB, HTTP, packaged/native | 7.1, 7.4, 10.4, 11.3, 13.1–13.2, 14.4 | Pending |

## L: Historical action replay survives changes and restart

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L22 | Verify replay remains verified after revocation | application, completion codec, HTTP, two-process packaged/native | 7.2, 10.4, 13.2, 14.4 | Pending |
| L23 | Primary replay does not repeat demotion | application, DB/HTTP preservation, two-process packaged/native | 11.2–11.3, 13.2, 14.4 | Pending |
| L24 | Failed key is reusable and corrupt result is sanitized | completion codec, DB/HTTP fault injection, packaged/native | 7.2, 10.4, 12.3–12.4, 13.1–13.2, 14.4 | Pending |

## L: Atomic lifecycle concurrency and complete outcomes

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| L25 | Equivalent concurrent actions converge | coordinated DB/HTTP, outbox | 7.4, 7.6–7.7, 12.1 | Pending |
| L26 | Competing distinct actions cannot both use one version | coordinated DB/HTTP, audit/outbox | 7.4–7.7, 12.1 | Pending |
| L27 | Late failure rolls back the whole lifecycle outcome | transaction context, DB/HTTP fault injection | 7.7, 12.3 | Pending |
| L28 | Verification feeds existing activation without changing its policy | application, DB, HTTP compatibility, packaged/native | 10.2–10.3, 12.6, 13.1, 14.4 | Pending |

## R: Initial identifier issuer metadata is canonical issuing-entity code

Source: [party-registration issuer refinement](specs/party-registration/spec.md).

| ID | Source scenario title | Planned boundaries | Tasks | Evidence |
|---|---|---|---|---|
| R01 | Optional issuer and blank issuer produce no stored value | Domain, API, application, HTTP, packaged/native | 2.4, 12.6, 13.1, 14.4 | Pending |
| R02 | Lowercase issuing-entity code is canonicalized | Domain, API, application, fingerprint compatibility, HTTP, packaged/native | 2.4, 7.1, 12.6, 13.1–13.2, 14.4 | Pending |
| R03 | Invalid issuer fails before registration side effects | Domain, API, HTTP preservation, packaged/native | 2.4, 12.6, 13.1, 14.4 | Pending |

## Evidence entry convention

Replace a Pending entry with links to an increment in `verification.md` and the saved-file inventory rows. Record test class/method, command, outcome, artifact path, source blob identity, and execution mode. Preserve separate red, green, characterization, refactor, JVM, native, Sonar, and JDTLS outcomes. A single row can reference several tests. A planned boundary or passing baseline does not prove a new scenario. Record additional granular cases (controls/U+0000, maximum versions, lock collisions, cancellation, count overflow, and replay scope) within the relevant increment rather than inventing extra approved scenarios.

## Persistence-foundation evidence (tasks 5.1–5.2)

Task 5.1's `PartyIdentifierContractMigrationTest` verifies completion/audit constraints, qualified ownership and historical ordering indexes, with passing runtime and saved-revision local analysis evidence in `verification.md` and `java-inventory.md`. Task 5.2's `PartyIdentifierContractUpgradeTest` verifies V7 retention for both Party types/all identifier states, protected columns, snapshots, checksums and existing indexes; runtime passes but one Sonar finding blocks task completion. These are infrastructure prerequisites for the maintenance/replay/history scenarios, not evidence that their unimplemented HTTP/workflow/native behavior passes. Scenario acceptance entries remain pending until those boundaries execute.

Task 5.2 continuation resolves the supplied java:S5961 finding with separate data-retention and schema-history tests. The final saved revision passes focused/full checks and zero-finding Sonar/JDTLS; both persistence-foundation tasks are complete. Workflow/HTTP/native scenario acceptance still requires subsequent tasks.

## Historical query foundation evidence (tasks 6.1–6.3)

Task 6.1's cursor tests prove authenticated complete scope, precise boundaries, defaults/rotation and fail-closed token handling. Task 6.2's detail tests prove both owner types/all Party and identifier states, qualified concealment, safe scalar projection and complete nonmutation. Both tasks have focused/full/package and saved-revision zero Sonar/JDTLS evidence in `verification.md` and `java-inventory.md`.

Task 6.3's nine page tests cover exact intersections, unknown schemes, time/unsigned-ID ordering, both directions, empty outcomes, missing/changed boundaries and consistent snapshots with an independent writer. Full tests and current source analysis pass; a real packaged-container startup failure keeps its acceptance pending. These support Q history/navigation/metadata requirements at unit/database boundaries; Application/HTTP/native scenario acceptance remains pending rather than being inferred from internal adapter tests.

Task 6.3's runtime continuation reconciles that setup blocker with all 28 actual packaged JVM cases passing against a stable test-scoped Podman API. Its internal query task is now complete; public Application/HTTP/native scenario acceptance remains its subsequent work.

Task 6.4's nine port-double cases establish cursor-before-I/O and owner-before-child precedence, neutral absence, empty/safe page assembly and exact long totals, with passing full/package and zero local findings. Task 6.5's ten parser/support cases establish canonical selectors and closed exact query syntax at API unit boundaries; runtime passes but one production-parser Sonar finding keeps acceptance pending. New GET HTTP/native scenario execution remains task 6.7 and later packaged/native work.

The 6.5 concise-character-class continuation resolves its finding with zero final Sonar/JDTLS. Task 6.6 adds seven managed safe-item/page/count cases and schema-based current-projection compatibility. Task 6.7 executes 37 actual GET cases for Q ownership/history/filters/navigation/metadata/safe-envelope/context requirements, with passing full/JVM package and zero saved-source findings. These entries now supply Application/API/HTTP evidence for the query scenarios; complete all-eight-operation and required native scenario acceptance still depends on subsequent mutation/packaged/native tasks.

## Identifier completion foundation evidence (task 7.2, incomplete local gate)

`PartyIdentifierCompletionCodecTest` passes eight cases for original create/action results, exact safe allowlists, authoritative row/snapshot scope/acceptance/version coherence, detached trees and cause-free rejection of corruption. `PartyIdentifierReplayPersistenceTest` passes two reactive PostgreSQL cases for same-session joint commit, fresh-session durability, exact key/tenant/action isolation and rollback after completion flush. These provide codec/storage foundation evidence for M15/M37 and L22/L24; historical workflow/HTTP/two-process/native scenarios remain pending. Final full test/build execute 1,571 cases and 28 packaged JVM cases successfully. One saved-revision Sonar finding in the storage test prevents task completion, as documented in `verification.md` and `java-inventory.md`; internal passing tests do not close the full scenario or local quality gate.

Task 7.2 continuation resolves the developer-supplied java:S1612 finding with a method reference, fresh zero Sonar/JDTLS and passing full test/build. Its codec/storage foundation is now complete; the workflow/HTTP/restart/native scenario boundaries above remain subsequent verification.

## Private reason-audit foundation evidence (task 7.3)

Four `PartyIdentifierReasonCodecTest` cases establish independent exact big-endian bytes, null/empty/control/NUL/supplementary/unmatched-surrogate preservation, maximum bounds and cause-free corrupt decode rejection. Four `PartyIdentifierAuditPersistenceTest` cases establish fresh-session private reason/actor/time/action/version retention with outbox disabled, qualified owner constraints, accepted-audit uniqueness and rollback. Full 1,579-test/28-packaged JVM gates and all four final saved-source zero Sonar/JDTLS checks pass; see `verification.md`/`java-inventory.md`. This is codec/database foundation for L02/L16/L25–L27; actual accepted lifecycle/replay, service-process restart and native scenario boundaries remain subsequent tasks.

## Identifier locking/restoration foundation evidence (task 7.4)

Three lock-identity, three qualified-reader and four coordinated PostgreSQL cases pass. Actual waiting advisory locks and independent root/child NOWAIT probes establish replay/owner/scope/child order, forced-collision serialization without false replay, two-integer namespace independence, shared-owner locking, full persisted completion lookup after commit and fresh target version restoration. Historical target/current-primary and creation-scheme readers retain protected/audit/legacy metadata and do not increment Party versions. All five final saved Java analyses and the 1,589-test/build gates pass; see `verification.md` and `java-inventory.md`. These establish mechanics for M17/M27/M31–M35 and L13/L19–L26; guarded writes, complete Application workflows, actual mutation races and packaged/native acceptance remain subsequent tasks.

## Guarded mutation storage foundation evidence (task 7.5)

Nine `PartyIdentifierMutationPersistenceTest` cases prove qualified one-increment writes/reloads, initial insert shape, protected/creation/verification retention, stale/nonowned rejection, nullable metadata bindings, exact known value/primary conflicts, demotion-before-promotion and demotion rollback after a stale promotion. Full 1,598-test/build and both final saved-source zero Sonar/JDTLS checks pass; see `verification.md`/`java-inventory.md`. These provide storage evidence for M07/M11/M21/M25/M27/M32/M36/M38 and L08/L15–L17/L27. Full Application precedence, accepted events/audit/replay and HTTP/concurrent/native boundaries remain their later tasks.

## Identifier event codec foundation (task 7.6 complete)

Four `PartyIdentifierChangedEventCodecTest` cases and two `PartyIdentifierChangedMessageTest` cases pass for exact existing creation compatibility, five new names, safe four-field payloads and event metadata, strict corruption handling, typed broker primary flags and all three optional storage modes. Full 1,604-test/28-packaged JVM gates pass; one saved serializer Sonar finding prevents task acceptance. See `verification.md`/`java-inventory.md` for current evidence and diagnostic request. Actual mutation event assembly/counts, replay nonduplication and native/HTTP scenario acceptance remain their subsequent context/workflow checks.

The supplied java:S3824 diagnostic is now resolved by a behavior-preserving computeIfPresent refactor. Current saved serializer Sonar/JDTLS both report zero findings; focused event/outbox/architecture and final **1,604 full / 28 packaged JVM** checks pass. This completes task 7.6's codec/mode foundation; complete workflow/HTTP/native scenarios still require their designated subsequent checks.

## Sequential mutation context foundation (task 7.7, quality gate incomplete)

Eleven `HibernateReactivePartyIdentifierMutationAdapterTest` cases pass for per-subscription session ownership, replay-before-owner ordering, exact complete withdrawal audit, closed/concurrent/recovered/foreign-context guards, accepted completion/event attribution, late rollback and failed-key reuse, two-record primary effect completeness, READ COMMITTED/local deadlines, real lock/statement/work timeouts, cancellation cleanup and subsequent pool use. Full **1,615 tests / 28 packaged JVM cases** pass. All seven saved revisions have zero JDTLS problems; one current Sonar finding in `PartyIdentifierMutationJournal.java` prevents task acceptance. See `verification.md` and `java-inventory.md`. These are transaction-mechanics foundations for M/L atomic-outcome scenarios; full Application/HTTP/concurrent/native scenario acceptance remains its designated subsequent work.

The supplied java:S6213 journal diagnostic is resolved with recordAcceptedWrite; both changed revisions now have full zero Sonar/JDTLS results. All eleven adapter cases, architecture and final **1,615 full / 28 packaged JVM** checks pass again, completing task 7.7's transaction-mechanics foundation. Full mutation Application/HTTP/native scenarios remain their designated subsequent work.

## Replay-first registration workflow foundation (task 8.1, build gate incomplete)

Ten `PartyIdentifierRegistrationWorkflowTest` cases verify internal replay-before-owner/catalog/protection/time ordering, changed-input key conflicts, one lazy fingerprint and one microsecond UTC clock capture, locked-scheme identity/admission/normalized limits, both Party types, optional/inclusive expiration, provisional flags, exact safe completion/event metadata and failed-key reuse. Existing additional and initial registration plus architecture tests pass; full report contains **1,625 tests, zero failures/skips** and all four saved revisions have zero Sonar/JDTLS findings. The combined command's shell deadline interrupted final build/package acceptance, so task 8.1 remains unchecked. See `verification.md`/`java-inventory.md`. CDI rollout, public historical replay and packaged/native scenarios require tasks 8.2–8.3 and their later verification.

The authorized separate build completes in **3m 51s** with unit tests up-to-date and **28 actual packaged JVM cases** passing, resolving task 8.1's gate without another source change. Task 8.1's internal workflow foundation is complete; the public CDI rollout and full HTTP/native replay scenarios remain their designated subsequent tasks.

## Durable composition and consumer retirement (task 8.2, standalone quality gate incomplete)

CDI selects the replay-first identifier mutation/fingerprint workflow with repository-free preparation. Retired production registration port/candidate/adapter/write-sequence types have no remaining Java consumers. Existing policy/cryptography/observation/port/model checks now use current contracts, eight reactive registration regressions establish real durable creation/replay and complete effects, and the packaged flow now compares original/replayed 201 data while retaining a distinct-key 409 uniqueness assertion. Final **74 focused checks / 1,622 full tests / 28 actual packaged JVM cases** pass. All sixteen changed saved files have zero JDTLS source problems. One reactive test Sonar finding and explicit completion of an incomplete-model six-file batch keep task 8.2 unchecked; see `verification.md` and `java-inventory.md` for the coverage replacement table and exact diagnostics. Full strict binding, mutation HTTP scenarios, restart and native acceptance remain the designated later tasks.

The assertion-correction continuation supersedes the reactive test's prior source-analysis row: supplied java:S5785 is corrected with assertNotEquals, and focused/full tests plus build pass again on saved blob `95406f5557c4f14dddf8f69aa85580ed94a69479`. Current IDE output still reports one Sonar issue and one JDTLS problem with no details, in disagreement with the compiling/passing saved source. Neither that discrepancy nor the six explicit resolved-model analyses is accepted as complete; task 8.2 remains unchecked. See the current continuation in `verification.md` and `java-inventory.md`.

The developer's current unused-import diagnostic is resolved on final test blob `42f37b2768977bfa317f2f183afe490faefde8bf`: full saved-revision Sonar/JDTLS both report zero findings. Final 1,622 cases execute and pass; build succeeds up-to-date with retained actual 28 packaged JVM results. Task 8.2 is pending solely for the six explicit resolved-model analyses, not the now-resolved reactive-test assertion/import findings. See final revision evidence and log rotation in `verification.md`/`java-inventory.md`.

The developer confirms completion/no findings for all six requested individual analyses on unchanged rechecked blobs; available new resolved-model single-file logs corroborate the production files and recording double. Current source evidence is accepted in `verification.md`/`java-inventory.md`; task 8.2's composition/consumer-retirement foundation is complete. The full strict-body, historical mutation/restart and native scenarios remain their designated subsequent work.

## Strict creation and real historical replay (task 8.3)

Six managed strict-token/default cases and thirteen real POST cases pass, including both types/all Party states, exact safe 201/status-code-only failure envelopes, trusted correlation, syntax order, raw limits, nullable defaults, effective/raw identity, scheme/validity eligibility, ownership concealment, unchanged verified primary, duplicate conflict/failed-key reuse, and original creation result after current evidence/Party changes. The existing unknown-property DTO assertion retains rejection and now also requires the constant safe diagnostic. Final **1,641 full / 28 actual packaged JVM cases** and all eight saved-revision Sonar/JDTLS zero results pass; see `verification.md`/`java-inventory.md`. M03–M11/M15–M17 rows record the actual stage/boundary; concurrency, restart and expanded native scenarios are not prematurely accepted.

## Presence-aware metadata input and independent version syntax (task 9.1)

Four strict binding/four support cases prove omission/clearing, exact raw issuer/date transport, closed duplicate/coercion/trailing rejection, required/null/empty distinctions, raw issuer/code-point rules and body→Party→identifier→If-Match. Bare maximum version maps directly to PartyIdentifierVersion, malformed/duplicate/overflow cases use distinct existing codes, and replay headers are ignored. Existing parser/registration checks plus **1,649 full / 28 actual packaged JVM cases** and all eight saved-revision Sonar/JDTLS zero results pass. This establishes the API/input foundations for M01/M18/M20/M25/M26; actual metadata decision/HTTP and final native scenarios remain 9.2–9.3/later tasks, not accepted from parser tests alone. See `verification.md`/`java-inventory.md`.

## Current-version correction orchestration (task 9.2)

Nine focused workflow cases prove both types/eligible states, ordered absence/version/state/capacity/validity, immutable protected/verification/creation evidence, coherent repair/clearing/identical increments, one UTC operation instant, guarded-write failure propagation, one matching update intent and no replay/catalog/protection/withdrawal effects. Both saved main/test analyses and separate JDTLS are zero. Per the developer's iteration scope, only the changed test class executes and the focused build retains its valid result; no new global/package/native execution is claimed. This is Application evidence for M02/M18–M19/M21–M26/M36–M38; actual HTTP/restart/race/native acceptance remains the designated subsequent tasks. See `verification.md`/`java-inventory.md`.

## Actual metadata PATCH delivery (task 9.3)

Eight real HTTP/PostgreSQL cases now pass after eight route failures, plus changed signature/composition checks (**17 focused cases**) and focused build. They prove exact safe 200/failure envelopes/echoes, pending/verified and both types, clearing/retained metadata, terminal/stale/exhausted precedence, past-date repair/inversion, identical increment, ignored replay headers, strict syntax and owned-target concealment. Complete private/immutable/verification/creation identifier columns, Party/subtype snapshots, original POST result and logical completion/event counts are asserted unchanged where required. All eight saved-source Sonar/JDTLS results are zero; see `verification.md`/`java-inventory.md`. This provides live foundations for M01–M02/M18–M26/M38; scheme-withdrawal compatibility, concurrency/restart and packaged/native completion remain their designated later acceptance.

## Optional reasons and actual buffered/chunked presence foundation (task 10.1, source gate pending)

Five strict unit/four actual HTTP binding cases pass after seven behavior failures and the BOM's optional-reader whitespace edge. Probe receipts demonstrate exact optional defaults/empty/control/supplementary strings, 64/1000 code-point boundaries, null/duplicate/unknown/coerced/trailing rejection, zero/nonzero verify bytes before selectors and unknown-length HTTP/1.1 chunked whitespace/object/zero-body behavior on the event loop. Buffered bytes are used without Content-Length or synchronous transport reads; reason text is not exposed. Focused build and all seven JDTLS zero results pass, but one decoder Sonar finding prevents task 10.1 acceptance. Actual action transactions/HTTP routes/restart/native remain their subsequent tasks; see `verification.md`/`java-inventory.md` for saved evidence and pending actual diagnostic.

The supplied separate-declaration diagnostic is resolved on decoder blob 2336819be86988b796e18360ddf597b2e2bef8de with completed zero Sonar/JDTLS, five affected unit checks and focused build passing. Unchanged actual HTTP/chunked results remain valid; all seven saved source gates are accepted. Task 10.1's L01–L02 optional/body-presence foundation is complete, while actual identifier actions/reasons/replay/native remain 10.2 onward.
