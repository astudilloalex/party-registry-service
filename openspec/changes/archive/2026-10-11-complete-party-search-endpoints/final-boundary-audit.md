# Final Identifier Search Boundary and Coverage Audit

Reviewed on 2026-10-11 for task 8.7. Source revisions match all 75 saved blobs
in [the final inventory](java-final-revision-inventory.md), recomputed with
`git diff --name-only 6a04cc8 -- '*.java' | git hash-object --stdin-paths`.
Passing execution references below are the final full JVM run and packaged/native
runs recorded in [verification.md](verification.md), sections 8.1, 8.2 and 8.6.
This inspection does not represent a new test execution or analyzer run.

## HTTP and dependency boundaries

- `IdentifierSearchResource.byIdentifier` and `byPrefix` both return
  `Uni<RestResponse<ApiResponse<List<IdentifierSearchResponse>>>>` (lines 65/79).
  Their shared response method maps Application hits to API DTOs, converts checked
  pagination metadata, then invokes the injected `ResponseManager.paginatedHttp`
  (lines 89–93). No entity, Application page, raw Response or bare DTO is exposed.
- The two response records contain only the declared five Party and five nested
  identifier fields. `IdentifierSearchApiMapper` does not export ordering instants,
  selector values, hashes, ciphertext, key versions or private cursor boundaries.
  Counts use `Math.toIntExact`; exact collections are not capped at 200.
- Resources delegate to Application use cases. Scheme resolution, normalization,
  effective scope, cursor sequencing and result assembly belong to Application;
  supported normalization/literal matching belongs to synchronous Domain policy.
  Application uses Domain and its own contracts plus the explicitly selected
  Mutiny profile; no HTTP, CDI, persistence or cryptographic implementation enters
  those contracts. Infrastructure implements read/cursor ports and CDI composition.
- Known neutral failures pass through `PartyApiErrorTranslator`; final envelopes
  are produced by the published shared error module. Search unexpected failures
  retain only fixed diagnostics and original stack locations, removing sensitive
  messages/causes/suppressed chains before the shared logger. The narrow JSON reader
  interceptor translates binding failures, not global success/error responses.
- `CleanArchitectureTest` enforces inward dependencies, framework-free Domain,
  isolated Application, cycle freedom, use-case delegation and shared envelopes.
  The final full JVM acceptance includes these rules.

## Reactive ownership and resource bounds

- `IdentifierSearchReadSession.read` creates state per subscription, captures the
  originating Vert.x context, bounds acquisition and remaining work, configures
  read-only repeatable-read/statement timeout, and closes owned sessions after
  transaction termination. Late acquisition remains owned and closes after a
  cancelled/timed-out waiter. `ReadView.query` guards lifetime, identical context
  and sequential query admission with an atomic busy flag.
- Exact count/full scalar projection and prefix batch queries are sequential.
  Both identifier and Party tenant ownership, scheme and effective states are
  bound before matching. Exact lookup does not decrypt. Prefix keysets advance
  by the final candidate, including nonmatches, with batches at most 256.
- `IdentifierSearchCpuDispatcher` uses a fixed worker pool, bounded
  `ArrayBlockingQueue` and rejecting `AbortPolicy`, with no caller-thread fallback.
  It emits completion on the captured context. Cancellation removes queued work
  and discards late completion. Prefix retains at most one outstanding batch per
  request and a page bounded by the effective limit; the accumulator counts all
  qualified matches without retaining them all.
- Inspection found no blocking wait, request-path JDBC, manual subscription or
  concurrent Hibernate session use in the search flow. `Uni.join().first` races
  cancellation against owned work asynchronously; `memoize().indefinitely()`
  retains the result of finite owned work, not an indefinite blocking wait.
- Runtime checks cover origin/worker identity, timeout, cancellation, saturation,
  escaped context, late allocation/completion and subsequent resource reuse.
  Blocked-thread diagnostics remain enabled.

## Scenario-to-check coverage

Names are the exact EARS scenario headings. Test class names identify files in
`src/test/java`; `PackagedIdentifierSearchContractIT` is in `src/integrationTest/java`.
Each listed class's relevant methods and assertions were inspected against the
scenario and the existing passing execution ledger. Shared context rules cover
the additional UUID padding/control/code-point cases at their owning boundary.

| Exact-search scenario | Checks |
| --- | --- |
| Published contract describes the completed operation | IdentifierSearchContractAssertionsTest; PackagedIdentifierSearchContractIT.sourcePackagedAndServedSearchSchemasAgreeAndPrivateDriftIsRejected |
| Header precedence preserves an accepted process | IdentifierSearchInputContractTest.headerCardinalityAndCanonicalValuesPrecedeInvalidBodies |
| Header spelling and rule precedence remain consistent | IdentifierSearchInputContractTest; RequestContextFilterTest; RequestContextFilterContractTest |
| Process and user boundaries are enforced | IdentifierSearchInputContractTest; RequestContextFilterTest; RequestContextFilterContractTest |
| Concurrent request context remains isolated | IdentifierSearchInputContractTest.concurrentSuccessAndFailureKeepOnlyTheirAcceptedCompletionContext |
| Management endpoints remain exempt | IdentifierSearchInputContractTest.acceptsCaseInsensitiveNamesAndUserBoundaryAndExemptsManagement |
| Required body and malformed input have stable failures | IdentifierSearchInputContractTest.missingNullMalformedAndAmbiguousBodiesHaveOnlyStableErrors; IdentifierSearchBindingTest |
| Supported inputs remain body-only read selectors | IdentifierSearchInputContractTest.prefixLimitsAndCursorTokensAreStrictAndExactCannotBePaginated; IdentifierSearchSnapshotContractTest |
| Raw length boundaries are explicit | IdentifierSearchInputContractTest.rawUtf16BoundariesApplyBeforeSchemeAndNormalization; IdentifierSearchBindingTest |
| Unsupported pagination inputs cannot truncate exact search | IdentifierSearchInputContractTest.prefixLimitsAndCursorTokensAreStrictAndExactCannotBePaginated |
| Canonically equivalent complete values match | IdentifierSearchHistoricalContractTest; IdentifierSearchSelectorPolicyTest |
| Scheme selectors remain exact and explicit | IdentifierSearchHistoricalContractTest; IdentifierSearchReadSessionTest; IdentifierSearchFailureContractTest; exact bound code lookup inspection |
| Search does not readmit historical identifiers | IdentifierSearchHistoricalContractTest.everyStoredStatePartyTypeAndWithdrawnSchemeRemainsSearchableWithoutReadmission; IdentifierSearchSelectorPolicyTest |
| Unsupported normalization fails explicitly | IdentifierSearchFailureContractTest.bothRoutesPreserveKnownAndFrameworkEnvelopesAndAcceptedProcessEcho; IdentifierSearchUseCasesTest |
| Omitted filters include retained history | IdentifierSearchHistoricalContractTest; IdentifierSearchSnapshotContractTest |
| Filter dimensions intersect | IdentifierSearchHistoricalContractTest; IdentifierSearchQueriesTest; IdentifierSearchReadAdapterTest |
| Empty and invalid arrays are distinct | IdentifierSearchInputContractTest.filterTokensAndEmptySetsAreDistinctOverHttp; IdentifierSearchBindingTest |
| Cross-tenant matches are concealed | IdentifierSearchHistoricalContractTest.returnsAll201DuplicatesWithUnsignedUuidTiesAndConcealsForeignMatches |
| Historical duplicates remain individually discoverable | IdentifierSearchHistoricalContractTest; IdentifierSearchSnapshotContractTest.interveningWriterCannotSplitExactCountAndFullProjection |
| Complete exact results are not capped | IdentifierSearchHistoricalContractTest; IdentifierSearchReadAdapterTest; IdentifierSearchSnapshotContractTest |
| Successful items contain only safe declared fields | IdentifierSearchApiMapperTest.serializesOnlySafeStoredFieldsAndCompleteExplicitMetadata; shared IdentifierSearchContractAssertions |
| Empty and multiple exact matches have explicit metadata | IdentifierSearchResourceContractTest; IdentifierSearchApiMapperTest; IdentifierSearchResultsTest; shared assertions |
| Sensitive search material is never exported | Shared closed-field assertions; IdentifierSearchFaultContractTest; IdentifierSearchWorkObserverTest; redacted query/selector/candidate inspection |
| Framework failures retain the envelope | IdentifierSearchFailureContractTest.bothRoutesPreserveKnownAndFrameworkEnvelopesAndAcceptedProcessEcho |
| Unavailability and timeout do not become incomplete success | IdentifierSearchRuntimeContractTest.exhaustedPoolTimesOutBothRoutesAndLateAcquisitionsReturnCapacity; IdentifierSearchReadSessionTest; IdentifierSearchFaultContractTest |
| Unexpected and unrepresentable outcomes are sanitized | IdentifierSearchFaultContractTest; IdentifierSearchApiMapperTest; JcaIdentifierProtectionAdapterTest; IdentifierSearchFailureContractTest |
| Conditional authentication is independent of context | Conditional: no authentication mechanism is enabled in the current service/test profiles. Headers do not establish credentials; see verification task 6.5. No unconditional 401 test claim. |

| Prefix-search scenario | Checks |
| --- | --- |
| Published prefix examples agree with actual responses | IdentifierSearchContractAssertionsTest; PackagedIdentifierSearchContractIT.sourcePackagedAndServedSearchSchemasAgreeAndPrivateDriftIsRejected |
| Context validation precedes prefix and cursor validation | IdentifierSearchInputContractTest.headerCardinalityAndCanonicalValuesPrecedeInvalidBodies |
| Header spelling and rule precedence are deterministic | IdentifierSearchInputContractTest; RequestContextFilterTest; RequestContextFilterContractTest |
| Invalid process is not echoed | IdentifierSearchInputContractTest; RequestContextFilterTest; RequestContextFilterContractTest |
| User boundaries and concurrent attribution remain safe | IdentifierSearchInputContractTest; RequestContextFilterTest.acceptsUnicodeCodePointBoundaryAndClearsOnlyOwnedContext |
| Management endpoints remain exempt | IdentifierSearchInputContractTest.acceptsCaseInsensitiveNamesAndUserBoundaryAndExemptsManagement |
| Missing bodies and strict token failures are explicit | IdentifierSearchInputContractTest; IdentifierSearchBindingTest; PackagedIdentifierSearchContractIT |
| Supported inputs are strict body-only read selectors | IdentifierSearchInputContractTest; IdentifierSearchSnapshotContractTest |
| Raw prefix and scheme boundaries are enforced | IdentifierSearchInputContractTest.rawUtf16BoundariesApplyBeforeSchemeAndNormalization; IdentifierSearchBindingTest |
| Selector failure precedes continuation scope failure | IdentifierSearchFailureContractTest; IdentifierSearchUseCasesTest.prefixUnknownSchemeAndUnsupportedNormalizerPrecedeAlteredCursor |
| Partial official identifiers do not need a complete checksum | IdentifierSearchPrefixContractTest.partialNationalIdAndDuplicateTimestampTiesTraverseTwoTwoOneWithoutCollapse; IdentifierSearchSelectorPolicyTest |
| Canonical equivalence and literal characters are preserved | IdentifierSearchPrefixContractTest.literalUnicodeAndAllHistoricalFiltersAreAppliedBeforeAdmission; IdentifierSearchSelectorPolicyTest |
| Full values and expanding normalization remain valid selectors | IdentifierSearchPrefixContractTest.partialNationalIdAndDuplicateTimestampTiesTraverseTwoTwoOneWithoutCollapse; literalUnicodeAndAllHistoricalFiltersAreAppliedBeforeAdmission |
| Known withdrawn schemes remain searchable | IdentifierSearchSelectorPolicyTest; IdentifierSearchPrefixContractTest; IdentifierSearchFailureContractTest; exact same-view scheme lookup inspection |
| Default prefix search includes stored historical states | IdentifierSearchPrefixContractTest.literalUnicodeAndAllHistoricalFiltersAreAppliedBeforeAdmission; IdentifierSearchSnapshotContractTest |
| Status dimensions combine predictably | IdentifierSearchPrefixContractTest; IdentifierSearchQueriesTest |
| Empty filters cannot silently widen results | IdentifierSearchInputContractTest.filterTokensAndEmptySetsAreDistinctOverHttp; IdentifierSearchPrefixContractTest; IdentifierSearchUseCasesTest |
| Foreign-only matches have an empty local outcome | IdentifierSearchPrefixContractTest; PackagedIdentifierSearchContractIT.packagedStrictBindingEmptyResultsAndCorruptionUseOnlySharedSafeContracts |
| Shared Party and creation timestamps do not collapse hits | IdentifierSearchPrefixContractTest.partialNationalIdAndDuplicateTimestampTiesTraverseTwoTwoOneWithoutCollapse; IdentifierSearchReadAdapterTest; IdentifierSearchSnapshotContractTest |
| Default and boundary page sizes apply exactly | IdentifierSearchPrefixContractTest.multiBatchNavigationKeepsCompleteCountsEquivalentScopesAndDeletedBoundarySemantics |
| Invalid limits are not coerced | IdentifierSearchInputContractTest.prefixLimitsAndCursorTokensAreStrictAndExactCannotBePaginated; IdentifierSearchBindingTest.rejectsEveryInvalidLimitTokenWithoutNarrowingOverflow |
| Omitted and null cursors request the first page | IdentifierSearchBindingTest.preservesDefaultsEmptyFiltersAndNullBodyOrCursor; IdentifierSearchInputContractTest; IdentifierSearchPrefixContractTest |
| Forward and backward traversal preserve order | IdentifierSearchPrefixContractTest; IdentifierSearchPrefixAccumulatorTest; PackagedIdentifierSearchContractIT |
| Cursor scope cannot cross tenant or search | HmacIdentifierSearchCursorAdapterTest; IdentifierSearchPrefixContractTest; IdentifierSearchInputContractTest |
| Equivalent selector forms retain continuation | IdentifierSearchPrefixContractTest.multiBatchNavigationKeepsCompleteCountsEquivalentScopesAndDeletedBoundarySemantics; IdentifierSearchQueriesTest |
| Live continuation survives boundary changes | IdentifierSearchPrefixContractTest.multiBatchNavigationKeepsCompleteCountsEquivalentScopesAndDeletedBoundarySemantics; IdentifierSearchPrefixAccumulatorTest |
| Successful prefix hits contain only declared safe fields | IdentifierSearchApiMapperTest; shared IdentifierSearchContractAssertions; PackagedIdentifierSearchContractIT |
| First middle and final pages have exact metadata | IdentifierSearchPrefixContractTest.partialNationalIdAndDuplicateTimestampTiesTraverseTwoTwoOneWithoutCollapse; IdentifierSearchSnapshotContractTest.interveningWriterCannotSplitPrefixBatchesCountsOrNavigation |
| Empty initial and vanished datasets are explicit | IdentifierSearchResourceContractTest; IdentifierSearchPrefixContractTest; IdentifierSearchPrefixAccumulatorTest |
| Empty live continuation does not erase remaining totals | IdentifierSearchPrefixContractTest.multiBatchNavigationKeepsCompleteCountsEquivalentScopesAndDeletedBoundarySemantics; IdentifierSearchPrefixAccumulatorTest |
| Sensitive prefix material is never exported | Shared closed-field assertions; IdentifierSearchFaultContractTest; HmacIdentifierSearchCursorAdapterTest; IdentifierSearchWorkObserverTest; redacted representations inspection |
| Framework failures preserve correlation and safe envelopes | IdentifierSearchFailureContractTest.bothRoutesPreserveKnownAndFrameworkEnvelopesAndAcceptedProcessEcho |
| Expensive search or dependency failure does not return partial success | IdentifierSearchRuntimeContractTest; IdentifierSearchReadSessionTest; IdentifierSearchCpuDispatcherTest; IdentifierSearchFaultContractTest |
| Unexpected failure and count overflow are sanitized | IdentifierSearchFaultContractTest; IdentifierSearchFailureContractTest; IdentifierSearchApiMapperTest |
| Conditional authentication remains separate from tenant context | Conditional: same disabled authentication applicability as exact search; verification task 6.5. |

## Outcome

Task 8.7's final boundary, dependency, runtime ownership and scenario coverage
inspection is complete. Final strict validation and evidence reconciliation are
tracked independently by task 8.8.
