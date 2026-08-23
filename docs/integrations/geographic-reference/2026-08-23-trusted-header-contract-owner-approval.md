# Geographic Reference Trusted-Header Contract Owner Approval

- **Decision date**: 2026-08-23
- **Status**: APPROVED
- **Approver**: Alex Astudillo
- **Authority**: Geographic Reference Provider Owner, Party Registry Architecture Owner, Product
  Owner, Developer, and QA
- **Reviewed Party Registry revision**: `b2fc550073c7f48c0c285e2c5bac72b8f5104867`
- **Feature branch**: `11-ft-1`

## Approved Contract

Party Registry requires exactly one instance of each trusted business-context header on the Party
registration operation:

- `Tenant-Id`: mandatory UUID tenant identifier.
- `User-Id`: mandatory, non-blank audit subject with a maximum length of 128 characters.
- `Process-Id`: mandatory UUID process identifier, propagated unchanged to downstream
  microservice calls.

The Geographic Reference adapter propagates those same three values unchanged on every approved
country lookup. They are the complete provider-specific business-context header contract known by
Party Registry. Standard HTTP and observability headers remain infrastructure concerns and do not
alter this decision.

## Source Boundary

Postman collection `15834347-d3591c82-bd52-46a9-973d-a7a102d4b9b3` remains the source for the
approved alpha-2 route, response envelope, and country activity values. The owner decision recorded
here is the authoritative source for Party Registry's three trusted context headers. Updating the
provider collection is separate maintenance and is not a prerequisite for implementing this
feature. No Postman collection was changed as part of this approval.

## Reviewed Evidence

Revision `b2fc550073c7f48c0c285e2c5bac72b8f5104867` consistently records the decision in:

- `specs/001-party-registration/contracts/geographic-reference-port.md`
- `specs/001-party-registration/research.md`
- `specs/001-party-registration/data-model.md`
- `specs/001-party-registration/quickstart.md`
- `specs/001-party-registration/tasks.md`

The checked-in Party registration OpenAPI and requirements already require the same singular
headers. Implementation tasks T017 and T045 retain the executable tests that must prove validation
and unchanged propagation. This approval closes the pre-implementation contract gate; it does not
claim those future implementation tests have already run.

## Approval Statement

As the owner of both sides of this integration decision, Alex Astudillo approves reviewed revision
`b2fc550073c7f48c0c285e2c5bac72b8f5104867` as the binding trusted-header contract for Party
Registration feature 1. No additional provider-specific business-context header is required or
modeled by Party Registry.
