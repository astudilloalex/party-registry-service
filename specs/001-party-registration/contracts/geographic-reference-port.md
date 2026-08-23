# Geographic Reference Validation Port

**Owner**: Party Registry application layer
**Direction**: Outbound
**Purpose**: Validate every distinct country reference before Party persistence

## Operation

```text
validateAll(activeCountryCodes, tenantId, auditSubject, processId) -> asynchronous CountryValidationOutcome
```

The application invokes the port once with an immutable, non-empty set of syntactically valid,
uppercase ISO 3166-1 alpha-2 codes collected from the applicable birth, incorporation, and
nationality fields, plus trusted context already validated from `Tenant-Id`, `User-Id`, and
`Process-Id`. It does not invoke the port when no country is supplied.

The concrete application port returns `Uni<CountryValidationOutcome>`. This document defines
semantics rather than Java signatures so provider transport details remain outside the application.

## Outcomes

### AllActive

Every requested code was authoritatively confirmed to exist and be active. Only this outcome allows
Party persistence to begin.

### InvalidReferences

One or more requested codes were explicitly classified as unknown or inactive. The outcome carries
only the invalid codes and their normalized reason category; it does not expose provider DTOs or
messages. The application maps this to `INVALID_GEOGRAPHIC_REFERENCE`.

### ValidationUnavailable

The adapter could not obtain a complete authoritative answer. Causes include timeout, connection
failure, unsupported response, malformed or contradictory JSON, unexpected status, partial result,
authentication/authorization failure, or cancellation before completion. The application maps this
to `DEPENDENCY_UNAVAILABLE` and performs no persistence.

## Mapping Rules

- Only an explicit provider result of unknown or inactive becomes `InvalidReferences`.
- A requested code missing from a provider response is `ValidationUnavailable`, not unknown.
- Provider HTTP status, DTO, exception, and error-code types never cross this port.
- Duplicate input codes are removed before the port is called.
- The adapter preserves cancellation and never blocks, awaits, sleeps, or manually subscribes.
- The adapter does not automatically retry and does not use stale cached activity data.
- Connection and request deadlines are bounded mandatory configuration supplied per environment.
- The adapter propagates trusted `Tenant-Id`, `User-Id`, and `Process-Id` explicitly without using
  MDC or thread-local state as an application input.
- The adapter reuses the inbound `Process-Id` unchanged for all per-code requests and verifies the
  provider echo; it never generates a replacement.
- The adapter's business-context header contract is limited to `Tenant-Id`, `User-Id`, and
  `Process-Id`.
- Trace context may be propagated by the infrastructure adapter without exposing observability
  framework types through the application port.

## Provider Wire Mapping

The authoritative provider source for this feature is Postman collection
`geographic-reference-service`, UID `15834347-d3591c82-bd52-46a9-973d-a7a102d4b9b3`, updated
2026-07-27. The selected request is `Get country by alpha-2 code`, request UID
`15834347-ed5c0238-71ab-455a-32b8-e63856198cb5`:

```http
GET /api/v1/countries/by-alpha2/{alpha2Code}
Accept: application/json
Tenant-Id: <trusted tenant UUID>
User-Id: <trusted audit subject>
Process-Id: <trusted process UUID>
```

The route, envelope, and activity values come from the cited Postman collection. The header block
comes from the 2026-08-23 provider-owner decision: Party Registry requires and propagates exactly
one `Tenant-Id`, `User-Id`, and `Process-Id`. This decision is authoritative for the Party Registry
integration and does not depend on a later Postman collection update.

The adapter issues one request per distinct code, with at most 11 requests for one Party command.
It consumes only the standard envelope and country activity field approved by the collection and
the 2026-08-22 human clarification:

| Provider result | Port interpretation |
|-----------------|---------------------|
| `200`, JSON, envelope `status: 200`, `code: successful`, `data.status: ACTIVE`, matching `Process-Id` echo | Active |
| Same valid result with `data.status: DRAFT`, `DEPRECATED`, or `RETIRED` | Explicitly inactive |
| `404`, JSON, envelope `status: 404`, code ending in `not-found`, matching `Process-Id` echo | Explicitly unknown |
| Any other status, media type, envelope, activity value, echo, timeout, connection result, or incomplete response | Validation unavailable |

All distinct lookups must complete authoritatively. If any lookup is unavailable, the aggregate
outcome is `ValidationUnavailable`, even when another lookup was explicitly invalid. If every
lookup is authoritative and at least one is inactive or unknown, the aggregate outcome is
`InvalidReferences`; only all `ACTIVE` results produce `AllActive`.

## Tests

- Empty country set skips the port.
- Distinct active codes produce `AllActive`.
- `DRAFT`, `DEPRECATED`, `RETIRED`, and documented `404` not-found results produce
  `InvalidReferences` when all lookups are authoritative.
- Partial, malformed, contradictory, delayed, disconnected, mismatched process-ID, or unexpected
  responses produce `ValidationUnavailable`.
- Adapter requests use the approved route, propagate exactly the three trusted context headers,
  and never exceed one lookup per distinct code.
- No persistence call occurs after either failure outcome.
- HTTP adapter tests use a controlled stub and prove serialization, response mapping, timeout, and
  cancellation behavior without contacting production.
