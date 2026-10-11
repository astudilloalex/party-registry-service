# Final SonarQube for IDE Evidence

This is actual standalone IDE analysis, not a remote server Quality Gate.
Source revisions are the saved blobs in [the final inventory](java-final-revision-inventory.md).

## Analyzer and effective model

- SonarQube for IDE extension: 5.7.0, installed Linux x64 distribution.
- Java analyzer: **8.36.0.45637**, manifest implementation build
  `8ad0e752c36821bbd87f475d6683a90fad615341`, verified from installed sonarjava.jar.
- Active Java rules: **571**, as recorded in each qualifying run's configuration.
- Parameters: analyzer defaults; inspected workspace/user settings contain no
  Sonar rule exclusions, suppression, activation or parameter overrides. None
  were introduced during this change.
- Java source/JDK 25, preview disabled; current Quarkus **3.40.1** libraries and
  resolved main/test/integration outputs are present in the logged model.
- Every qualifying record performs full analysis and completes with **0 issues,
  0 Security Hotspots**. Missing-model or cancelled runs are not accepted.
- The existing `No workDir in SonarLint` runtime notice is independent of source
  findings; full AST analysis explicitly completes afterward.

Logs reside under:
`/home/alex/.config/Antigravity IDE/logs/20261010T184745/window1/exthost/SonarSource.sonarlint-vscode/`.
`L1` means `SonarQube for IDE.1.log`; `L0` means `SonarQube for IDE.log`.
All dates below are 2026-10-10. The latest completed record is retained for each
saved revision: unchanged files with an already completed current-model analysis
keep that record when the editor does not schedule another identical analysis.
Sixty files were reanalyzed in the final reopen sweep; fifteen retain the stated
earlier completed analyses of their unchanged final revisions in this same
resolved-model session. These are explicit timestamps, not historical baseline
zero counts or an assertion that every reopen triggered Sonar.

The earlier incremental findings and user remediations are preserved in the
main verification ledger. Current rows require no further remediation. No rule
was weakened and no blanket exclusion or suppression was added.

## Per-file completion

Basenames uniquely identify the 75 full paths/revisions in the final inventory.
Every row has Java 25, 571 active rules, completed full analysis, 0 issues and 0
hotspots; ranges include its input path, model and terminal result.

| File | Log lines | Analysis started |
| --- | --- | --- |
| IdentifierSearchContractAssertions.java | L1:2129–2159 | 21:19:47.123 |
| PackagedIdentifierSearchContractIT.java | L1:1710–1740 | 20:08:54.902 |
| PackagedIdentifierSearchResource.java | L1:1679–1709 | 20:08:32.619 |
| IdentifierSearchJsonReaderInterceptor.java | L1:2160–2190 | 21:19:51.785 |
| IdentifierSearchApiMapper.java | L1:2191–2221 | 21:19:53.352 |
| IdentifierSearchExactRequest.java | L1:2222–2252 | 21:19:54.912 |
| IdentifierSearchExactRequestDeserializer.java | L1:2253–2283 | 21:19:57.145 |
| IdentifierSearchJsonFields.java | L1:2284–2314 | 21:19:58.224 |
| IdentifierSearchPrefixRequest.java | L1:2315–2345 | 21:19:59.697 |
| IdentifierSearchPrefixRequestDeserializer.java | L1:2346–2376 | 21:20:01.177 |
| IdentifierSearchIdentifierResponse.java | L1:2377–2407 | 21:20:02.686 |
| IdentifierSearchResponse.java | L1:2408–2438 | 21:20:04.125 |
| PartyHttpObservability.java | L1:2439–2469 | 21:20:05.588 |
| IdentifierSearchResource.java | L1:1405–1435 | 19:44:02.294 |
| ExactIdentifierSearchQuery.java | L1:2470–2500 | 21:20:09.287 |
| ExactIdentifierSearchSlice.java | L1:2501–2531 | 21:20:10.761 |
| IdentifierSearchFilters.java | L1:2532–2562 | 21:20:12.384 |
| IdentifierSearchHit.java | L1:2563–2593 | 21:20:14.024 |
| IdentifierSearchPage.java | L1:2594–2624 | 21:20:15.845 |
| IdentifierSearchPositions.java | L1:2625–2655 | 21:20:17.682 |
| IdentifierSearchScope.java | L1:2656–2686 | 21:20:19.408 |
| ObservedOperation.java | L1:2687–2717 | 21:20:21.176 |
| PrefixIdentifierSearchQuery.java | L1:2718–2748 | 21:20:22.919 |
| PrefixIdentifierSearchSlice.java | L1:2749–2779 | 21:20:24.721 |
| IdentifierSearchCursorPort.java | L1:2780–2810 | 21:20:26.356 |
| IdentifierSearchReadContext.java | L1:2811–2841 | 21:20:28.121 |
| IdentifierSearchReadPort.java | L1:2842–2872 | 21:20:30.054 |
| SearchPartiesByIdentifierPrefixUseCase.java | L1:2873–2903 | 21:20:31.854 |
| SearchPartiesByIdentifierUseCase.java | L1:2904–2934 | 21:20:33.191 |
| IdentifierRuleCatalog.java | L1:2935–2965 | 21:20:34.699 |
| IdentifierSearchSelector.java | L0:1–31 | 21:20:35.953 |
| IdentifierSearchSelectorPolicy.java | L0:32–62 | 21:20:37.278 |
| IdentifierSearchUseCaseProducer.java | L0:63–93 | 21:20:38.741 |
| IdentifierSearchWorkObserver.java | L0:94–124 | 21:20:40.419 |
| MicrometerOpenTelemetryOperationObserver.java | L0:125–155 | 21:20:41.804 |
| HibernateReactiveIdentifierSearchReadAdapter.java | L0:156–186 | 21:20:43.238 |
| IdentifierSearchPrefixAccumulator.java | L0:187–217 | 21:20:44.672 |
| IdentifierSearchReadSession.java | L0:218–248 | 21:20:46.236 |
| IdentifierSearchSql.java | L0:249–279 | 21:20:47.876 |
| HmacIdentifierSearchCursorAdapter.java | L0:280–310 | 21:20:49.576 |
| IdentifierProtectionIdentity.java | L0:311–341 | 21:20:51.247 |
| IdentifierSearchCpuDispatcher.java | L0:342–372 | 21:20:52.923 |
| JcaIdentifierProtectionAdapter.java | L0:373–403 | 21:20:54.472 |
| IdentifierSchemeTestFixtureTest.java | L1:1935–1965 | 20:48:57.894 |
| MigrationRegressionTest.java | L0:404–434 | 21:20:58.363 |
| PartyIdentifierContractMigrationTest.java | L1:1988–2018 | 21:07:52.839 |
| IdentifierSearchApiMapperTest.java | L0:435–465 | 21:21:01.956 |
| IdentifierSearchBindingTest.java | L0:466–496 | 21:21:03.505 |
| PartyHttpObservabilityTest.java | L0:497–527 | 21:21:05.100 |
| IdentifierSearchFailureContractTest.java | L1:1436–1466 | 19:45:04.624 |
| IdentifierSearchFaultContractTest.java | L1:1308–1338 | 19:36:22.162 |
| IdentifierSearchFaultReadPort.java | L0:528–558 | 21:21:09.451 |
| IdentifierSearchHistoricalContractTest.java | L1:999–1029 | 19:24:22.884 |
| IdentifierSearchInputContractTest.java | L1:937–967 | 19:18:13.285 |
| IdentifierSearchPerformanceTest.java | L1:1794–1824 | 20:14:50.008 |
| IdentifierSearchPrefixContractTest.java | L1:1083–1113 | 19:34:56.190 |
| IdentifierSearchResourceContractTest.java | L0:559–589 | 21:21:17.441 |
| IdentifierSearchRuntimeContractTest.java | L1:1595–1625 | 20:01:03.377 |
| IdentifierSearchQueriesTest.java | L0:590–620 | 21:21:20.544 |
| IdentifierSearchResultsTest.java | L0:621–651 | 21:21:21.997 |
| IdentifierSearchReadStub.java | L0:652–682 | 21:21:23.642 |
| IdentifierSearchUseCasesTest.java | L0:683–713 | 21:21:24.992 |
| IdentifierSearchContractAssertionsTest.java | L0:714–744 | 21:21:26.437 |
| IdentifierSearchSelectorPolicyTest.java | L0:745–775 | 21:21:27.828 |
| IdentifierSearchWorkObserverTest.java | L0:776–806 | 21:21:29.206 |
| MicrometerOpenTelemetryOperationObserverTest.java | L0:807–837 | 21:21:30.513 |
| IdentifierSearchPrefixAccumulatorTest.java | L0:838–868 | 21:21:32.021 |
| IdentifierSearchReadAdapterTest.java | L0:869–899 | 21:21:33.523 |
| IdentifierSearchReadSessionTest.java | L0:900–930 | 21:21:35.295 |
| IdentifierSearchSnapshotContractTest.java | L1:1520–1550 | 19:54:38.422 |
| HmacIdentifierSearchCursorAdapterTest.java | L0:931–961 | 21:21:38.599 |
| IdentifierSearchCpuDispatcherTest.java | L1:1626–1656 | 20:02:35.298 |
| IdentifierSearchMigrationTest.java | L0:962–992 | 21:21:41.781 |
| JcaIdentifierProtectionAdapterTest.java | L0:993–1023 | 21:21:43.578 |
| IdentifierSearchDatabaseFixture.java | L1:1467–1497 | 19:46:22.536 |
