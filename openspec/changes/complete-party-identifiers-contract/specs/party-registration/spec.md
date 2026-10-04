## Purpose

Apply the confirmed optional issuing-entity code rule to initial identifiers without changing Party identity, lifecycle, protection, or retained creation results.

## ADDED Requirements

### Requirement: Initial identifier issuer metadata is canonical issuing-entity code

The Party Registry SHALL interpret optional `initialIdentifier.issuerCode` as an issuing-entity code, not an ISO country code, and SHALL NOT query Geographic Reference for it. A submitted string SHALL contain at most 64 Unicode code points. Omission/null SHALL write no issuer; a bounded empty or Java-blank string SHALL also write null. Otherwise the code SHALL consist of ASCII letter groups separated by single underscores and SHALL be uppercased independently of locale, without trimming a nonblank code. Invalid structure SHALL return `400 issuer-code-invalid`; oversized input SHALL retain `400 issuer-code-too-long`. Required-rule, property-name, and rule-code validation precedence SHALL remain unchanged.

Historical stored values and completed snapshots SHALL remain unchanged and readable. Original parsed issuer strings SHALL remain part of the existing raw-input idempotency identity; write canonicalization SHALL NOT rewrite existing fingerprints or reapply itself to completed replay.

#### Scenario: Optional issuer and blank issuer produce no stored value

- **WHEN** otherwise valid registration omits the issuer or supplies null, an empty string, or a bounded Java-blank string
- **THEN** the created identifier has no issuer value and no issuer Geographic Reference lookup occurs

#### Scenario: Lowercase issuing-entity code is canonicalized

- **WHEN** otherwise valid registration supplies `civil_registry`
- **THEN** the newly stored issuer and new creation result contain `CIVIL_REGISTRY`
- **AND** original parsed input remains the idempotency comparison representation

#### Scenario: Invalid issuer fails before registration side effects

- **WHEN** registration supplies leading/trailing/repeated underscores, digits, a non-ASCII letter, NUL, nonblank whitespace, or an oversized issuer
- **THEN** the applicable field-specific 400 validation code is returned
- **AND** no new Party, identifier, completed replay result, or enabled event is accepted
