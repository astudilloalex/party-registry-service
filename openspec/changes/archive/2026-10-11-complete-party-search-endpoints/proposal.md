## Why

The approved `docs/contracts/party-registry.openapi.yaml` publishes exact and prefix identifier searches, but the service does not implement either route. Consumers cannot reliably locate an existing Party by an official identifier or discover candidates from a partial identifier.

Complete these two operations as a focused delivery with predictable results, tenant isolation, protected identifier handling, and verified correctness. Preserve the project's strict Clean Architecture, reactive Java 25 baseline, and native compatibility while making Java and SonarQube quality acceptance explicit.

## What Changes

- Implement `POST /v1/parties/search/by-identifier` using the required `identifierSchemeCode` and `value`, with the declared identifier-status and Party-record-status filters.
- Implement `POST /v1/parties/search/by-identifier-prefix` using the required `identifierSchemeCode` and `prefix`, the same status filters, and scoped cursor navigation. Preserve the declared default limit of 50 and allowed range of 1 through 200.
- Respect the published input limits: scheme code at most 64 characters, exact value at most 256, and prefix at most 64. Reject malformed input rather than silently widening a search.
- Return the declared Party search items with their matching identifier's ID, scheme code, stored masked value, status, and primary flag. Complete or normalized identifier values, ciphertext, lookup hashes, and keys must not appear in responses, logs, or traces.
- Specify and document currently undefined search semantics: normalization, omitted/null/empty status filters, historical and expired identifier eligibility, scheme eligibility, result cardinality and ordering, empty outcomes, totals, cursor scope, and exact-search pagination metadata despite its lack of pagination inputs. Keep the approved operations and response fields as the baseline.
- Apply the existing trusted context and process-correlation contract to both routes. Return tenant-qualified `200 successful` search envelopes with the declared pagination fields and stable, status-consistent errors, including sanitized unexpected failures.
- Keep searches read-only: preserve Party and identifier state, versions, audit values, and event history. Verify bounded resource use and representative search performance without inventing an unapproved latency guarantee.

## User Stories

- As a registry consumer, I want to find Parties by an exact official identifier, so that I can select the intended record without retrieving sensitive identifier values.
- As a registry operator, I want to search by identifier prefix and navigate filtered candidates, so that I can locate a Party when I have only part of its identifier.
- As a maintainer, I want contract, architecture, Java, and SonarQube checks to provide reproducible evidence, so that fast delivery remains reliable and maintainable.

## Capabilities

### New Capabilities

- `party-exact-identifier-search`: Tenant-qualified exact identifier matching, documented status and scheme semantics, safe results, complete response metadata, and stable validation/failure behavior.
- `party-prefix-identifier-search`: Tenant-qualified literal identifier-prefix matching, documented eligibility and filtering, deterministic bounded pagination, safe results, and stable validation/failure behavior.

### Modified Capabilities

None. Existing Party listing/detail and nested identifier query capabilities describe different operations.

## Impact

- API consumers gain the two published search operations. Their OpenAPI descriptions and examples must explain the completed behavior and agree with actual HTTP responses.
- Existing identifier protection and historical data constrain search compatibility; previously registered identifiers must remain discoverable under the specified eligibility rules.
- Implementation acceptance requires strict inward dependencies, end-to-end nonblocking behavior, native-compatible contracts, and meaningful tests at the affected boundaries.
- Every changed Java file requires resolved JDTLS diagnostics, no new compiler warnings, and actual SonarQube analysis of its final saved revision. Resolve applicable findings without blanket suppressions or weakened rules; record local IDE evidence separately from any server Quality Gate claim. Required test/build and native verification must be evidenced before implementation is declared complete.

## Out of Scope

- New search routes, fuzzy/substring/wildcard matching, or new identifier-validation algorithms.
- Changes to identifier registration, lifecycle, primary selection, Party lifecycle, or unrelated query contracts.
- Plaintext identifier storage or disclosure, repository-wide refactoring, and deployment of a new SonarQube server.
