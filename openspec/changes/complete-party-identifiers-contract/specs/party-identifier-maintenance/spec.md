## Purpose

This capability completes additional identifier registration, partial metadata correction, and primary selection. It protects independently versioned identifiers, deterministic retries, and atomic primary ownership without changing the owning Party.

## ADDED Requirements

### Requirement: Maintenance follows the common identifier boundary

**User Story:** As an API consumer, I want consistent maintenance validation, so that corrections and registration use predictable public outcomes.

WHEN registration, metadata PATCH, or set-primary is requested,
THE Party Registry SHALL apply the context, canonical path selectors, tenant/Party ownership, safe response, confidentiality, and error requirements in `party-identifier-queries`. Context and applicable framework checks SHALL precede operation validation. Body syntax, requiredness, and text limits SHALL precede path and operation-header validation; operation-specific ordering below SHALL govern those remaining syntax checks. Path order SHALL be Party then identifier where applicable. After syntax validation, completed replay or key conflict SHALL precede new business evaluation. New operations SHALL check Party absence, identifier absence where applicable, expected identifier version where required, then business eligibility.

#### Scenario: Body validation precedes a missing version

- **WHEN** a metadata PATCH has an unknown property, a malformed identifier path, and no If-Match
- **THEN** the response is `400 bad-request` from the body rather than an existence or version result
- **AND** no maintenance occurs

#### Scenario: Absence precedes a stale version

- **WHEN** a structurally valid mutation with a valid expected version targets an absent Party or an absent identifier under an owned Party
- **THEN** the response is respectively `404 party-not-found` or `404 identifier-not-found`, not a version failure

### Requirement: Strict additional registration input

**User Story:** As a registry operator, I want a precise creation input, so that invalid values cannot be silently coerced into identity evidence.

WHEN `POST /v1/parties/{partyId}/identifiers` is requested,
THE Party Registry SHALL require an object body containing nonnull nonblank string `identifierSchemeCode` and `value`; only optional `issuerCode`, `issuedOn`, `expiresOn`, and `isPrimary` SHALL also be accepted. Scheme code and raw value SHALL retain their existing raw-input maximum lengths of 64 and 256 UTF-16 units respectively. Issuer SHALL identify the issuing entity rather than a country and SHALL remain optional. Its submitted string SHALL contain at most 64 Unicode code points. Null or omission SHALL mean no issuer; an empty or Java-blank string within that bound SHALL be written as null. Other issuer strings SHALL consist of ASCII letter groups separated by single underscores, equivalent to `[A-Za-z]+(?:_[A-Za-z]+)*`, and SHALL be written in locale-independent uppercase. Leading/trailing/repeated underscores, digits, non-ASCII letters, controls and whitespace in a nonblank code SHALL be invalid; nonblank codes SHALL NOT be trimmed. Issuer SHALL NOT use Geographic Reference validation. Nullable dates SHALL accept omission or null. Dates SHALL be valid ISO calendar-date strings. Primary SHALL default to false only when omitted and SHALL otherwise be a JSON boolean, not null or a coerced scalar. Duplicate/unknown properties, trailing content, nonobject bodies, incompatible property types, and invalid date syntax SHALL return `400 bad-request`.

IF the required body is missing or null,
THEN THE Party Registry SHALL return `400 request-body-required`. Missing/null/blank scheme or value SHALL respectively use `identifier-scheme-code-required` or `identifier-value-required`; exceeded field limits SHALL use `identifier-scheme-code-too-long`, `identifier-value-too-long`, or `issuer-code-too-long`, all with HTTP 400. Invalid issuer structure SHALL use `400 issuer-code-invalid`. Multiple field violations SHALL retain required-rule priority, then property-name order, then rule-code order.

THE Party Registry SHALL require exactly one nonblank `Idempotency-Key` of at most 128 Unicode code points. Missing, duplicated, blank, and oversized keys SHALL respectively return `400 idempotency-key-required`, `idempotency-key-duplicated`, `idempotency-key-blank`, or `idempotency-key-too-long`. Body validation SHALL precede creation-key validation, which SHALL precede Party path validation, preserving existing additional-registration syntax precedence. Creation SHALL NOT require If-Match.

#### Scenario: Optional input and primary default

- **WHEN** valid registration omits issuer, both dates, and isPrimary
- **THEN** those nullable fields are absent, the primary flag is false, and the request remains eligible for registration

#### Scenario: Required fields and raw length boundaries

- **WHEN** registration omits a required field, submits a blank required string, or exceeds a declared raw-input limit
- **THEN** the corresponding field-specific 400 code is returned without creating an identifier
- **AND** stripping or normalizing the identifier value does not make an oversized raw value acceptable

#### Scenario: Strict input rejects client-owned properties and coercion

- **WHEN** registration supplies status, version, a duplicate property, a string boolean, null isPrimary, or an impossible date
- **THEN** the response is `400 bad-request` without any new identifier or replay outcome

#### Scenario: Existing creation-key precedence is preserved

- **WHEN** a valid registration body accompanies an absent Idempotency-Key and a malformed Party path
- **THEN** the response is `400 idempotency-key-required` before path syntax evaluation

### Requirement: Eligible independent registration

**User Story:** As a data steward, I want additional identifiers to obey the same identity rules as initial identifiers, so that both kinds of evidence remain compatible.

WHEN a valid new additional registration is evaluated for an owned Party,
THE Party Registry SHALL resolve the exact submitted scheme code, require a known ACTIVE scheme compatible with the Party type or BOTH, and apply its supported normalization, value validation, and normalized length rules. Unknown, inactive, and incompatible schemes SHALL respectively return `422 unknown-identifier-scheme`, `inactive-identifier-scheme`, and `incompatible-identifier-scheme`.

THE Party Registry SHALL validate supplied metadata against one captured UTC evaluation date. Expiration SHALL remain optional regardless of legacy requiresExpiration metadata. A supplied expiration SHALL NOT precede that evaluation date or a supplied issue date. The issue date SHALL NOT acquire a new future-date restriction in this change. Semantic value/metadata failures SHALL use `422 identifier-validation-failure`. The protected new identifier representation SHALL correspond to the validated normalized value rather than the original raw value.

WHEN registration succeeds,
THE Party Registry SHALL return `201 successful` with a newly generated identifier ID, status PENDING_VERIFICATION, version 0, equal creation/modification timestamps attributable to the accepted user, and the requested primary flag. A true flag on a pending identifier SHALL be provisional; registration SHALL NOT displace a verified primary. The Party, its subtype details, nationalities, independent version/audit, and previous identifiers SHALL remain unchanged.

#### Scenario: Both Party types receive additional identifiers

- **WHEN** an owned natural person or legal entity registers a different valid identifier under a compatible ACTIVE scheme
- **THEN** a retrievable PENDING_VERIFICATION identifier at version 0 is returned with HTTP/body status 201
- **AND** the Party and all prior identifiers remain unchanged

#### Scenario: Scheme errors retain their specific codes

- **WHEN** otherwise valid registration references an unknown, non-ACTIVE, or type-incompatible scheme
- **THEN** the corresponding declared 422 scheme code is returned with no created outcome

#### Scenario: Expiration boundaries and optionality

- **WHEN** otherwise valid registrations omit expiration, submit null, or expire on the operation's UTC evaluation date
- **THEN** those expiration inputs are accepted regardless of legacy scheme metadata
- **AND** an expiration before that date or before the supplied issue date instead gives `422 identifier-validation-failure`

#### Scenario: Provisional primary coexists with existing evidence

- **GIVEN** a VERIFIED primary already exists in the Party and scheme
- **WHEN** a different eligible identifier is registered with isPrimary true
- **THEN** the new record is PENDING_VERIFICATION with a provisional true flag
- **AND** the existing verified primary and its version are unchanged

### Requirement: Active-value uniqueness and release

**User Story:** As a data steward, I want duplicate live document values rejected, so that one tenant cannot register conflicting identity evidence.

WHILE an identifier's stored state is PENDING_VERIFICATION or VERIFIED,
THE Party Registry SHALL reserve its normalized value within its tenant and scheme, including against additional registration for the same Party. Date-based expiration alone SHALL NOT release that reservation. A duplicate new registration SHALL return `409 identifier-uniqueness-conflict` without changing existing evidence. Other tenants and other schemes SHALL have independent uniqueness scopes.

WHEN an identifier becomes REJECTED or REVOKED, or already has stored state EXPIRED,
THE Party Registry SHALL permit otherwise eligible new registration of the same normalized value with a new identifier ID rather than reactivate or rewrite the historical record.

#### Scenario: Same-Party and other-Party duplicates conflict

- **GIVEN** a pending or verified identifier reserves a value in one tenant and scheme
- **WHEN** a new registration for that Party or a different owned Party uses the same normalized value
- **THEN** the response is `409 identifier-uniqueness-conflict` and neither existing record changes

#### Scenario: Time passing alone preserves reservation

- **GIVEN** a VERIFIED identifier has a past expiration date but retains its stored state
- **WHEN** the same tenant registers its normalized value again in that scheme
- **THEN** the response remains `409 identifier-uniqueness-conflict`

#### Scenario: Withdrawal releases uniqueness without deleting history

- **GIVEN** the only matching historical identifier is REJECTED, REVOKED, or stored EXPIRED
- **WHEN** an otherwise eligible new registration supplies that value
- **THEN** a new pending identifier can be accepted while the historical identifier remains unchanged and readable

#### Scenario: Distinct-key duplicate race has one winner

- **WHEN** valid additional registrations with different keys race for the same tenant, scheme, and normalized value
- **THEN** at most one new identifier is created and losers receive `409 identifier-uniqueness-conflict`

### Requirement: Durable additional registration replay

**User Story:** As an API consumer, I want retries to return the original created identifier, so that lost responses do not create duplicate records.

WHERE a registration key is supplied as required,
THE Party Registry SHALL scope completed results by tenant, additional-identifier create action, and exact key, independently of Party registration and all other actions. Effective request identity SHALL consist of Party ID and every accepted creation property. Omitted/null nullable fields SHALL be equivalent, and absent isPrimary SHALL equal false. Other submitted strings SHALL compare exactly before write normalization; JSON property order, User-Id, and Process-Id SHALL NOT affect equivalence. Key values SHALL NOT be trimmed or case-folded.

WHEN an equivalent validated request repeats a completed key,
THE Party Registry SHALL return the original `201 successful` masked result, including original ID, status, flag, metadata, version, and timestamps, even after later identifier/Party/scheme changes, dependency unavailability during new registration, or service restart. It SHALL echo the retry's accepted Process-Id and SHALL NOT mutate current records or emit another logical event.

IF a completed key is reused with different effective input,
THEN THE Party Registry SHALL return `409 idempotency-key-conflict` before current existence, uniqueness, or scheme eligibility checks, without disclosing the saved result. Failed attempts SHALL consume no successful key. Equivalent concurrent same-key requests SHALL converge on one created identifier and its original result; changed-input same-key requests SHALL have only one accepted result.

#### Scenario: Historical retry survives restart and mutation

- **GIVEN** keyed registration succeeded and its identifier was subsequently corrected, verified, or revoked
- **WHEN** the equivalent original registration is retried after service restart with a different accepted user and process
- **THEN** its original 201 pending/version-0 result is returned and the retry's process is echoed
- **AND** current state and logical event counts remain unchanged

#### Scenario: Normalization does not erase request differences

- **GIVEN** registration completed for one exact raw identifier value
- **WHEN** the same key is reused with a different raw representation that normalizes to the same value, another Party, or changed metadata
- **THEN** the response is `409 idempotency-key-conflict`

#### Scenario: Concurrent replay and failed key reuse

- **WHEN** equivalent validated requests compete for one creation key
- **THEN** one new identifier and one original completion are accepted and equivalent successes return that result
- **AND** a rejected attempt under an unused key does not prevent a later corrected request from succeeding with it

### Requirement: Strict partial metadata correction

**User Story:** As a data steward, I want omission and null to mean different things, so that I can correct or clear metadata deliberately.

WHEN `PATCH /v1/parties/{partyId}/identifiers/{identifierId}` is requested,
THE Party Registry SHALL require an object with at least one of issuerCode, issuedOn, or expiresOn and no other properties. Omitted properties SHALL retain their stored values; null SHALL clear the corresponding nullable property. Supplied issuer strings SHALL follow the issuing-entity format and 64-Unicode-code-point bound defined for additional registration: a bounded empty/Java-blank string SHALL clear the issuer, and a nonblank valid ASCII letter/underscore code SHALL be uppercased without trimming. Omitted historical issuer values SHALL remain unchanged even when they do not follow the new format. Dates SHALL be valid ISO calendar-date strings or null. Identity, value, scheme, status, primary flags, verification evidence, version, and audit properties SHALL NOT be writable through this body. PATCH syntax order SHALL be body validation, Party path, identifier path, then If-Match.

IF the body is missing/null, empty, has an oversized issuer, an invalid issuer structure, or contains malformed/duplicate/unknown/incompatible input,
THEN THE Party Registry SHALL return respectively `400 request-body-required`, `patch-property-required`, `issuer-code-too-long`, `issuer-code-invalid`, or `bad-request`. PATCH SHALL NOT create replay outcomes or acquire replay behavior from an Idempotency-Key header.

#### Scenario: Omission preserves and null clears

- **GIVEN** an eligible identifier has issuer, issue date, and expiration metadata
- **WHEN** PATCH supplies only issuerCode null
- **THEN** issuer is cleared and both stored dates are retained
- **AND** no scheme, value, status, flag, or verification property changes

#### Scenario: Partial update validates the resulting interval

- **GIVEN** the retained issue date is later than a newly supplied expiration
- **WHEN** PATCH is evaluated at the current identifier version
- **THEN** the response is `422 identifier-validation-failure` and all prior metadata remains unchanged

#### Scenario: Non-metadata input is rejected

- **WHEN** PATCH is empty or attempts to write isPrimary, status, value, identifierSchemeCode, version, or an unknown property
- **THEN** the corresponding empty-patch or bad-request response is returned without modification

### Requirement: Metadata correction eligibility and audit

**User Story:** As an operator, I want valid corrections to retain verification evidence, so that descriptive maintenance does not silently change identity lifecycle.

WHILE an identifier is PENDING_VERIFICATION or VERIFIED, WHEN a current-version metadata PATCH is evaluated,
THE Party Registry SHALL permit corrections whose resulting expiration is absent or on/after one captured UTC evaluation date and is not before a resulting issue date. It SHALL preserve the stored lifecycle state, protected value, scheme, primary flag, and any verifiedAt/verifiedBy. Inactive scheme lifecycle status SHALL NOT prevent descriptive correction of previously accepted evidence. A resulting past expiration or inverted interval SHALL return `422 identifier-validation-failure`.

IF the stored identifier state is REJECTED, REVOKED, or EXPIRED,
THEN THE Party Registry SHALL return `409 invalid-identifier-lifecycle` without correction. An EXPIRED identifier SHALL NOT lose its expiration date through PATCH. A pending/verified identifier with a past expiration MAY be corrected by supplying a valid future/current date or clearing expiration, but an issuer-only change SHALL NOT bypass resulting-date validation.

WHEN a correction succeeds, including one identical to current metadata,
THE Party Registry SHALL increment the identifier version exactly once, update modification audit actor/time, preserve creation audit, and return the accepted `200 successful` representation. It SHALL NOT change any owning Party field or version.

#### Scenario: Verified metadata is corrected without reverification

- **GIVEN** a verified primary identifier with recorded verification evidence and a compatible non-ACTIVE scheme
- **WHEN** a valid current-version PATCH changes its issuer or dates
- **THEN** its status, primary flag, verifiedAt, and verifiedBy are preserved while its identifier version increases once
- **AND** its Party version and scheme remain unchanged

#### Scenario: Terminal records cannot be corrected

- **WHEN** current-version PATCH targets each of REJECTED, REVOKED, and EXPIRED
- **THEN** the response is `409 invalid-identifier-lifecycle` and the historical record is unchanged

#### Scenario: Past-date evidence must be repaired coherently

- **GIVEN** a pending or verified identifier has a past expiration
- **WHEN** PATCH supplies a valid non-expired replacement expiration or explicitly clears it
- **THEN** the correction can succeed if the resulting interval is coherent
- **AND** an issuer-only PATCH instead returns `422 identifier-validation-failure`

#### Scenario: Identical correction still advances once

- **WHEN** an eligible PATCH supplies the existing metadata with the current identifier version
- **THEN** it succeeds, advances that version once, and updates modification audit without another field change

### Requirement: Identifier-version preconditions and exhaustion

**User Story:** As an API consumer, I want independent identifier versions enforced, so that identifier changes cannot overwrite competing corrections or transitions.

WHEN PATCH, set-primary, verify, reject, or revoke is requested,
THE Party Registry SHALL require exactly one If-Match containing the target identifier version as a bare decimal from 0 through 9223372036854775807, without quotes, signs, whitespace, leading zeros except 0 itself, or wildcard. Missing, duplicated, malformed, and overflowing values SHALL return the respective existing `400 if-match-required`, `if-match-duplicated`, `if-match-invalid`, or `if-match-out-of-range` code, including on possible replay requests.

IF a new mutation's valid expected identifier version differs from current state or loses an independent same-version race,
THEN THE Party Registry SHALL return `412 expected-version-mismatch` without changes. The owning Party version SHALL NOT substitute for the identifier version. Equivalent completed keyed replay SHALL instead return the historical result under its replay contract.

IF a current-version mutation is permitted in the target's stored state but the target is already at version 9223372036854775807, or an explicit replacement would demote a former primary at that maximum version,
THEN THE Party Registry SHALL return `409 identifier-version-exhausted` without wrapping versions or partially changing any identifier. Version syntax SHALL allow the maximum value. Target-version exhaustion SHALL follow current version/state checks and precede resulting-date and primary-conflict checks; former-primary exhaustion SHALL be checked additionally when an explicit replacement requires its demotion.

#### Scenario: Party version is not the identifier precondition

- **GIVEN** the Party version differs from the target identifier's version
- **WHEN** PATCH or an action submits the Party version instead of the identifier's current version
- **THEN** it receives `412 expected-version-mismatch` unless that numeric value also equals the current identifier version

#### Scenario: Syntax boundaries and exhaustion are distinct

- **WHEN** mutation supplies a missing, repeated, quoted, wildcard, signed, whitespace-padded, leading-zero, or overflowing version
- **THEN** the corresponding 400 version code is returned
- **AND** an otherwise eligible current maximum-version target instead gives `409 identifier-version-exhausted`

#### Scenario: PATCH and lifecycle race share one identifier version

- **WHEN** distinct eligible PATCH and verify/reject/revoke/set-primary requests compete using the same current target version
- **THEN** at most one new mutation is accepted at that version and independent losers receive `412 expected-version-mismatch`
- **AND** no loser changes audit, primary ownership, reason history, completion, or enabled events

### Requirement: Strict primary-selection input and eligibility

**User Story:** As an operator, I want primary selection limited to usable verified evidence, so that primary status cannot be mistaken for verification.

WHEN set-primary is requested,
THE Party Registry SHALL accept no body or an object containing only optional boolean replaceCurrentPrimary, defaulting to false when the body or property is absent. An empty object SHALL be equivalent to no body. A literal null body, null property, duplicate/unknown property, scalar coercion, or nonobject body SHALL return `400 bad-request`. It SHALL require the target identifier If-Match and validate any optional Idempotency-Key according to the action replay contract in `party-identifier-lifecycle`.

WHEN a new current-version selection is evaluated,
THE Party Registry SHALL require stored state VERIFIED and no expiration or an expiration on/after the operation's captured UTC evaluation date. Non-VERIFIED states SHALL return `409 invalid-identifier-lifecycle`; an expired-by-date VERIFIED target SHALL return `422 identifier-validation-failure`. Scheme lifecycle status SHALL NOT invalidate previously verified compatible evidence.

#### Scenario: Only usable verified identifiers can be selected

- **WHEN** current-version set-primary targets pending, rejected, revoked, or stored-expired identifiers
- **THEN** the response is `409 invalid-identifier-lifecycle`
- **AND** a VERIFIED target expiring before the evaluation date instead produces `422 identifier-validation-failure`

#### Scenario: Expiration boundary and absent expiration permit selection

- **WHEN** eligible verified targets have no expiration or expire exactly on the operation's UTC date
- **THEN** they satisfy primary-selection validity

#### Scenario: Replacement option has a strict false default

- **WHEN** set-primary omits the body, uses an empty object, or supplies replaceCurrentPrimary false
- **THEN** all three inputs have the same effective replacement choice
- **AND** a string true or null instead yields `400 bad-request`

### Requirement: Scoped atomic primary ownership

**User Story:** As a data steward, I want one verified primary per Party and scheme, so that replacement cannot leave competing authoritative documents.

WHEN an eligible primary selection succeeds,
THE Party Registry SHALL maintain at most one VERIFIED identifier with isPrimary true per tenant, Party, and scheme. Primaries in other tenants, Parties, or schemes and provisional pending flags SHALL NOT be displaced. An existing VERIFIED flagged identifier SHALL remain the current primary for conflict/replacement purposes even if its date has passed; time passing alone SHALL NOT remove its stored flag.

IF a different VERIFIED primary exists in that scope and replaceCurrentPrimary is false,
THEN THE Party Registry SHALL return `409 primary-identifier-conflict` without any mutation. With replaceCurrentPrimary true, it SHALL atomically set the candidate's flag true and the former primary's flag false, increment each affected identifier version exactly once, and update each modification audit to the same accepted actor/time while preserving their state, value, metadata, verification, and creation audit. Former-primary expiration SHALL NOT prevent demotion. The response SHALL contain only the candidate's accepted safe representation.

WHEN the eligible target is already the verified primary,
THE Party Registry SHALL return a successful versioned selection, advancing that target once without changing another identifier. No primary action SHALL change Party or scheme versions. Concurrent selections on different targets SHALL have outcomes consistent with a serial order and SHALL never leave multiple verified primaries; explicit replacements MAY both succeed sequentially if their own target versions remain valid.

#### Scenario: Different schemes have independent primaries

- **GIVEN** a Party has a verified primary in one scheme
- **WHEN** a verified identifier from another scheme is selected without replacement
- **THEN** both scopes have their own verified primary and the original identifier is unchanged

#### Scenario: Replacement is explicit and atomic

- **GIVEN** a different verified primary exists in the same scheme
- **WHEN** the candidate is selected first with false and then with true at a still-current candidate version
- **THEN** the first request gives `409 primary-identifier-conflict` without changes
- **AND** the second atomically promotes the candidate, demotes the former primary, and advances both versions once

#### Scenario: Expired-by-date former primary is explicitly displaced

- **GIVEN** the current primary retains VERIFIED and a true flag but has a past expiration
- **WHEN** an eligible current-version candidate is selected with replaceCurrentPrimary true
- **THEN** replacement succeeds without changing the former primary's lifecycle or dates

#### Scenario: Concurrent first selections cannot create two primaries

- **GIVEN** two different eligible verified candidates have no existing primary in their shared scope
- **WHEN** both request set-primary without replacement concurrently
- **THEN** one selection succeeds and the other receives `409 primary-identifier-conflict`
- **AND** exactly one verified primary and only winner-side version changes are observable

#### Scenario: Former-primary exhaustion blocks the whole replacement

- **GIVEN** the former primary is at the maximum supported identifier version
- **WHEN** a valid candidate requests replacement
- **THEN** the response is `409 identifier-version-exhausted` and both prior flags, versions, and audit values remain unchanged

### Requirement: Maintenance has one durable observable outcome

**User Story:** As an operator, I want state and integration history to agree, so that failures and retries cannot create partial identifier maintenance.

WHEN registration, PATCH, or primary selection succeeds,
THE Party Registry SHALL make its accepted identifier changes, modification audit, applicable durable replay result, and each enabled logical integration event observable as one complete outcome. Primary replacement SHALL include both affected identifiers in that outcome. Replays SHALL add no event or mutation. Event delivery redelivery SHALL NOT count as a new logical action.

IF any operation fails, times out, or is cancelled before complete acceptance,
THEN THE Party Registry SHALL preserve the prior identifier and primary state and SHALL make no accepted completion or enabled event from that attempt observable. A transport failure after acceptance SHALL be recoverable through the applicable keyed replay. Stored registration snapshots SHALL remain historical, and Party identity, type, lifecycle, details, versions/audit, nationalities, and unrelated identifiers SHALL remain unchanged.

#### Scenario: Late replacement failure restores both identifiers

- **GIVEN** primary replacement reaches a failure before complete acceptance after preparing both flag changes
- **WHEN** its failure is returned
- **THEN** both identifiers retain their prior flags, versions, and audit values
- **AND** no completion or enabled event from that replacement is accepted

#### Scenario: Missing response does not duplicate creation or replacement

- **GIVEN** keyed creation or set-primary was accepted but its HTTP response was lost
- **WHEN** the equivalent request is retried
- **THEN** its original safe result is returned without another identifier change or logical event

#### Scenario: Independent Party and existing registration results are preserved

- **WHEN** an identifier is registered, corrected, or selected as primary
- **THEN** the Party and its earlier Party-creation replay remain unchanged
- **AND** unrelated identifiers and scheme configuration remain unchanged
