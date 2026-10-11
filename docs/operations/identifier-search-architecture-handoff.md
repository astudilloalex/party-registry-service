# Identifier Search Central Architecture Handoff

## Source of truth and scope

The central LikeC4 project `default` defines
`alexAstudilloPlatform.partyRegistryService` in the
[service catalog](file:///home/alex/Documents/Development/architecture/alex-astudillo-architecture/catalog/system/microservices.c4#L9).
Its Quarkus/Java 25/Mutiny/Hibernate Reactive stack and confidentiality boundary
agree with this service. Existing Geographic Reference, PostgreSQL and optional
RabbitMQ relationships remain applicable. Read-only identifier search adds no
external system or relationship.

The existing `findPartyByIdentifierSequence` is located at
[party-registry-sequences.c4:177](file:///home/alex/Documents/Development/architecture/alex-astudillo-architecture/architectures/party-registry/party-registry-sequences.c4#L177).
The current sequence contains these active-only assumptions:

- `Load active identifier scheme by stable code`.
- `Find active identifier by tenant, scheme, and normalized-value hash`.
- `Return no active tenant-scoped identifier`.

## Required narrow refinement

The approved service
[exact](../../openspec/changes/complete-party-search-endpoints/specs/party-exact-identifier-search/spec.md)
and [prefix](../../openspec/changes/complete-party-search-endpoints/specs/party-prefix-identifier-search/spec.md)
specifications deliberately allow retained historical discovery. The architecture
repository follow-up should refine this sequence as follows:

1. Resolve the exact known scheme code in the operation's read view, including
   DRAFT, ACTIVE, DEPRECATED and RETIRED schemes. Supported normalization is
   required; registration eligibility/checksum/expiration admission is separate.
2. Preserve tenant-derived HMAC for complete equality and the existing protected
   storage mechanism. Select joined same-tenant Party/identifier history with
   explicit effective identifier and Party status sets. Omitted sets include
   every stored state; empty sets match nothing.
3. Return every qualifying identifier hit, not a single active identifier or a
   deduplicated Party. Preserve identifier creation/unsigned UUID descending
   order and same-view complete counts. No match is an empty 200 success.
4. At the public boundary, document safe stored summary/mask fields and the
   shared status-consistent envelope; no complete/normalized value, protection
   material, private position or storage version is public.
5. Describe scheme/data/count selection in one read-only repeatable-read scope,
   finite acquisition/statement/operation budgets and cleanup. Prefix discovery
   traverses detached protected batches on bounded CPU workers, returns to the
   same Vert.x context and uses authenticated live forward/backward cursors with
   exact full totals. It cannot use LIKE on a hash, ciphertext or mask.

The implemented service behavior is verified by protected historical HTTP tests,
multi-batch prefix navigation, coordinated intervening-writer tests and actual
packaged execution. The
[verification ledger](../../openspec/changes/complete-party-search-endpoints/verification.md)
tracks final JVM/native/IDE acceptance separately.

## Follow-up status

This document is the service-local handoff for the architecture repository's
maintainer to update the existing active-only sequence and, where useful,
represent the prefix flow. That repository's edits are a separate follow-up.
The central relationships and storage/protection decisions are retained; this
repo-local OpenSpec change's implementation edit root remains this service.
