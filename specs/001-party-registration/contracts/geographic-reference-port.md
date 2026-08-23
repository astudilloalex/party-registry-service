# Geographic Reference Validation Port

**Owner**: Party Registry application layer
**Direction**: Outbound
**Purpose**: Validate every distinct country reference before Party persistence

## Operation

```text
validateAll(activeCountryCodes) -> asynchronous CountryValidationOutcome
```

The application invokes the port once with an immutable, non-empty set of syntactically valid,
uppercase ISO 3166-1 alpha-2 codes collected from the applicable birth, incorporation, and
nationality fields. It does not invoke the port when no country is supplied.

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
- Tenant and audit headers are not propagated unless the Geographic Reference Service's approved
  contract explicitly requires them.
- Trace context may be propagated by the infrastructure adapter without exposing observability
  framework types through the application port.

## Provider Contract Boundary

LikeC4 approves reactive REST/JSON country validation but does not define the provider method, URI,
status mapping, or JSON schemas. The infrastructure adapter must implement this semantic contract
against Geographic Reference Service's own approved, versioned interface. This plan deliberately
does not invent that external URI or provider payload.

Adapter implementation cannot be accepted until its mapping is verified against that provider
contract. Application and domain work can proceed using this stable port and deterministic fakes.

## Tests

- Empty country set skips the port.
- Distinct active codes produce `AllActive`.
- Explicit unknown and inactive codes produce `InvalidReferences`.
- Partial, malformed, contradictory, delayed, disconnected, or unexpected responses produce
  `ValidationUnavailable`.
- No persistence call occurs after either failure outcome.
- HTTP adapter tests use a controlled stub and prove serialization, response mapping, timeout, and
  cancellation behavior without contacting production.
