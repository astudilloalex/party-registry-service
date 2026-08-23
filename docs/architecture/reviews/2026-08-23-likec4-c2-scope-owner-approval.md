# LikeC4 C2 Scope and Ownership Alignment Approval

- **Decision date**: 2026-08-23
- **Status**: APPROVED
- **Approver**: Alex Astudillo
- **Authority**: Party Registry Architecture Owner, Product Owner, Developer, and QA
- **Reviewed Party Registry revision**: `46a20d3fdc98a68efc35fd16bb46400b5460dc92`
- **Reviewed LikeC4 project**: `default`
- **Reviewed LikeC4 revision**: `296efca779ae8db48ccc8ab99194e8724af56d37`
- **Approval evidence**: This dated owner-decision record tied to both reviewed revisions

## Approved Modeling Scope

The platform's approved LikeC4 modeling depth currently ends at C2. The
`partyRegistryOverview` view satisfies that scope by representing Party Registry as a container and
showing its existing downstream dependencies on Geographic Reference Service, the Party Registry
database, and RabbitMQ.

Party Registry has no current upstream consumer or approved ingress integration. Its absence from
the model is intentional and accurately describes the platform at this stage. The architecture
must not invent a consumer, ingress route, proxy relationship, or trusted-header origin before a
concrete integration is approved.

A component-level view of application ports, adapters, domain boundaries, and bootstrap composition
would be a C3 view and is outside the approved LikeC4 scope. Strict Clean Architecture remains
mandatory for the implementation and will be verified by package boundaries, ArchUnit rules, and
layer-specific tests in the Party Registry repository.

## Trusted Request Context Boundary

The Party Registry API contract requires exactly one `Tenant-Id`, `User-Id`, and `Process-Id`.
Those inbound validation requirements do not imply that an upstream consumer or ingress already
exists. The concrete trust-establishment boundary, including which component authenticates the
caller and establishes those headers, must be added to the C2 model when the first upstream
integration is designed and approved.

Until then, Party Registry remains internal-only and must not expose a public Internet route or
direct public ingress.

## Party Database Ownership Interpretation

At C2, the Party database element is a storage container used by Party Registry; its short
description is not a definition of bounded-context aggregates. The binding ownership boundary is
defined by the Party Registry constitution and the approved DBML. Party Registry owns Party
aggregates and their natural-person or legal-entity details, nationalities, official identifiers,
and transactional outbox records. It does not own a separate Customer bounded context.

The word `customers` in the current C2 database description must therefore be read only as a
business classification of parties and must not authorize Customer entities, tables, APIs, or
behavior in Party Registry. This recorded owner decision takes precedence for implementation
planning and resolves the ambiguous C2 wording without changing the externally maintained LikeC4
sources.

## Reviewed Evidence

The live LikeC4 MCP project `default` reports:

- `partyRegistryOverview` as the C2 service-context view;
- `alexAstudilloPlatform.partyRegistryService` as a container with no child components;
- no incoming relationship to Party Registry;
- existing outgoing relationships only to Geographic Reference Service, the Party Registry
  database, and RabbitMQ; and
- the current Party database description as a container-level summary.

These observations conform to the approved C2 depth and the current absence of upstream consumers.
No LikeC4 source modification is required to approve implementation readiness for this feature.

## Future Synchronization Triggers

The LikeC4 architecture must be revisited when either of the following occurs:

1. A concrete upstream consumer, internal ingress, or trusted-header establishment mechanism is
   approved. The corresponding C2 elements and relationships must then be modeled before that
   integration is implemented.
2. The platform expands its required modeling depth from C2 to C3. Party Registry components,
   ports, adapters, and bootstrap composition must then be represented consistently with the
   implementation.

## Approval Statement

Alex Astudillo approves the current LikeC4 C2 scope and the ownership interpretation recorded above
for Party Registration feature 1. The absence of nonexistent upstream consumers and C3 components
is not an implementation-readiness defect. Clean Architecture and Party bounded-context ownership
remain enforceable through the approved repository sources and automated architecture tests.
