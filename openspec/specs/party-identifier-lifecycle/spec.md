# party-identifier-lifecycle Specification

## Purpose

This capability defines operator-confirmed verification, rejection, and revocation of independently versioned identifiers. It specifies terminality, reason audit, and durable action replay shared with primary selection.

Each requirement's first scenario states shared normative rules followed by its observable outcome. Those shared rules apply to every scenario of that requirement unless a clause explicitly limits its scope.

## Requirements

### Requirement: Lifecycle requests follow the common boundary

**User Story:** As an API consumer, I want consistent action inputs and validation order, so that invalid requests cannot disclose or mutate identifier records.

THE Party Registry SHALL enforce the common action boundary, strict body presence/reason rules, independent preconditions, and declared syntax/replay/business failure precedence under the shared normative rules below.

#### Scenario: Bodyless verification is strict

##### Shared normative rules

WHEN verify, reject, revoke, or set-primary is requested,
THE Party Registry SHALL apply the context, canonical path selectors, ownership, confidentiality, and response requirements in `party-identifier-queries`, and the identifier If-Match requirements in `party-identifier-maintenance`. After trusted context and applicable framework checks, validation order SHALL be body syntax and field constraints, Party path, identifier path, If-Match syntax, and optional Idempotency-Key syntax. After syntax, completed keyed replay or changed-input key conflict SHALL precede new Party/identifier absence, expected-version, lifecycle, target-version exhaustion, validity, and applicable primary-conflict evaluation, in that order. Former-primary exhaustion SHALL additionally be checked when an explicit replacement would demote it. It SHALL return the first applicable public failure without partial effects.

WHEN verify is requested,
THE Party Registry SHALL require no request body and SHALL reject any supplied body bytes, including an empty object, null, or whitespace-only content, with `400 bad-request`. Reject and revoke SHALL accept an absent body or an object containing only optional string reasonCode and reasonDetail; an empty object SHALL mean no supplied reason. Reason code SHALL have a maximum of 64 Unicode code points and detail 1000, without trimming, case conversion, or a nonblank requirement. Supplied null properties, a literal null body, nonobject or incompatible types, duplicate/unknown properties, trailing content, and exceeded reason limits SHALL return `400 bad-request`.

##### Observable outcome

- **WHEN** verify supplies an empty object, literal null, or whitespace body even with otherwise valid inputs
- **THEN** the response is `400 bad-request` without any verification or completion

#### Scenario: Optional reasons accept absence and exact strings

- **WHEN** eligible reject or revoke omits the body, supplies an empty object, or supplies valid reason strings including empty strings
- **THEN** the optional body passes validation and supplied strings retain their exact representation
- **AND** absent properties are distinguished from supplied empty strings

#### Scenario: Syntax precedes business and replay

- **GIVEN** a completed action key exists
- **WHEN** a retry has an invalid reason body, invalid target UUID, invalid If-Match, or invalid supplied key
- **THEN** its first syntax failure is returned rather than a historical success or current-state result

#### Scenario: Absence and version precede state

- **WHEN** a valid new action targets an absent identifier or uses a stale version on an ineligible state
- **THEN** it returns respectively `404 identifier-not-found` or `412 expected-version-mismatch`, before a lifecycle conflict

### Requirement: Explicit identifier transition matrix

**User Story:** As a data steward, I want a closed transition matrix, so that historical evidence cannot be silently reactivated.

THE Party Registry SHALL permit only the declared current-state/action transitions, reject forbidden repeats without effects, preserve equivalent historical replay, and introduce no implicit expiration or reactivation under the shared normative rules below.

#### Scenario: All permitted transitions work for both Party types

##### Shared normative rules

WHEN a new current-version lifecycle action is evaluated,
THE Party Registry SHALL permit only these state transitions:

| Current stored state | verify | reject | revoke |
| --- | --- | --- | --- |
| PENDING_VERIFICATION | VERIFIED | REJECTED | Forbidden |
| VERIFIED | Forbidden | Forbidden | REVOKED |
| REJECTED | Forbidden | Forbidden | Forbidden |
| EXPIRED | Forbidden | Forbidden | Forbidden |
| REVOKED | Forbidden | Forbidden | Forbidden |

IF an action is forbidden by this matrix,
THEN THE Party Registry SHALL return `409 invalid-identifier-lifecycle` without changing identifier fields, versions, audit, reason history, primary ownership, replay, or events. Repeating an action without an equivalent completed keyed result SHALL NOT be a successful no-op. Equivalent completed retries SHALL return their original result under the replay contract rather than perform another transition.

THE Party Registry SHALL NOT introduce a public expire/reactivate action or scheduled state-expiration workflow. Date-based expiration SHALL affect verification and primary eligibility without automatically rewriting stored state. Correcting dates on an eligible pending/verified identifier SHALL follow metadata correction rules rather than reactivate a terminal identifier.

##### Observable outcome

- **WHEN** eligible current-version verify and reject target pending identifiers, and revoke targets verified identifiers, for either Party type
- **THEN** they produce respectively VERIFIED, REJECTED, and REVOKED with `200 successful`

#### Scenario: Every forbidden matrix entry rejects without mutation

- **WHEN** each action is tested against every forbidden stored state using the current version
- **THEN** every response is `409 invalid-identifier-lifecycle`
- **AND** no identifier, primary, audit, completion, or enabled event changes

#### Scenario: Repeating a completed transition without replay conflicts

- **GIVEN** verify has already made an identifier VERIFIED
- **WHEN** a new unkeyed verify uses its current version
- **THEN** the response is `409 invalid-identifier-lifecycle`, not another verification or successful no-op

### Requirement: Operator-confirmed verification and validity

**User Story:** As a registry operator, I want to record my completed verification, so that the registry records usable evidence and its responsible actor.

THE Party Registry SHALL verify admitted usable pending evidence using the accepted actor and one captured UTC instant, preserve protected/history/owner data, and advance one identifier revision under the shared normative rules below.

#### Scenario: Accepted user and one instant define verification

##### Shared normative rules

WHILE an identifier is PENDING_VERIFICATION, WHEN a new eligible verify succeeds,
THE Party Registry SHALL record status VERIFIED, verifiedBy equal to the accepted User-Id, and verifiedAt equal to the captured operation instant. Verification SHALL require no external verification provider and SHALL NOT request or disclose the complete identifier value.

THE Party Registry SHALL evaluate expiration once using that instant's UTC calendar date. No expiration and expiration equal to that date SHALL qualify. Expiration before that date or an incoherent validity interval SHALL return `422 identifier-validation-failure` with no verification or timestamp changes. Previously accepted matching type-compatible scheme evidence SHALL remain verifiable after scheme deprecation or retirement; verification SHALL NOT re-register the document or change scheme configuration.

WHEN verification succeeds,
THE Party Registry SHALL preserve identifier and Party identity, scheme, masked/protected value, issuer and dates, creation audit, and existing primary flag, subject to the primary conflict requirement. It SHALL advance the identifier version once and update modification audit to the accepted actor and same operation instant. The Party's status, version, and audit SHALL remain unchanged.

##### Observable outcome

- **GIVEN** a valid pending identifier and accepted User-Id
- **WHEN** verify succeeds
- **THEN** verifiedBy is that exact user, verifiedAt and modification time identify the same operation instant, and version increases once
- **AND** no provider call, value disclosure, Party activation, or Party version change occurs

#### Scenario: UTC midnight and inclusive expiration are deterministic

- **GIVEN** otherwise eligible pending identifiers with no expiration, expiration equal to the operation's UTC date, and expiration on the preceding date
- **WHEN** each verify is evaluated using one instant even if processing crosses UTC midnight
- **THEN** the first two qualify and the past-date identifier returns `422 identifier-validation-failure`
- **AND** no later clock reading changes that operation's evaluation date

#### Scenario: Scheme withdrawal does not erase admitted evidence

- **GIVEN** the identifier was admitted under an ACTIVE compatible scheme that is now DEPRECATED or RETIRED
- **WHEN** current-version verify is otherwise eligible
- **THEN** it can succeed without readmitting the identifier under current scheme registration rules

### Requirement: Verification respects provisional primary ownership

**User Story:** As a data steward, I want provisional flags checked when evidence becomes verified, so that verification cannot silently replace another authoritative identifier.

THE Party Registry SHALL preserve provisional flags on verification only without a different verified primary in the same scope, reject ownership conflicts without implicit demotion, and arbitrate concurrent candidates under the shared normative rules below.

#### Scenario: First provisional primary becomes verified

##### Shared normative rules

WHILE a pending identifier has isPrimary true, WHEN a new verify is evaluated,
THE Party Registry SHALL preserve that flag only if no different VERIFIED primary exists in its tenant, Party, and scheme. Another pending flag or a primary in another scope SHALL NOT create a verified-primary conflict. An existing VERIFIED flagged identifier SHALL count even if its expiration date has passed.

IF a different VERIFIED primary exists in the same scope,
THEN THE Party Registry SHALL return `409 primary-identifier-conflict`, leaving the pending candidate and existing primary unchanged. Verify SHALL NOT implicitly demote either identifier or interpret an unknown replacement property. Explicit replacement SHALL be performed only through set-primary on an eligible verified candidate.

##### Observable outcome

- **GIVEN** a pending primary has no other verified primary in its scope
- **WHEN** verify succeeds
- **THEN** the identifier becomes VERIFIED with isPrimary true and one version increment

#### Scenario: Conflicting provisional verification is rejected

- **GIVEN** a pending primary and a different verified primary share one tenant, Party, and scheme
- **WHEN** the candidate requests verify with its current version
- **THEN** the response is `409 primary-identifier-conflict` with both prior records unchanged
- **AND** the attempt does not consume its optional replay key

#### Scenario: Nonprimary verification is independent

- **GIVEN** a verified primary exists in the same scheme
- **WHEN** another eligible pending identifier with isPrimary false is verified
- **THEN** verification succeeds without changing the existing primary

#### Scenario: Concurrent provisional primaries have one verified winner

- **GIVEN** two eligible pending primaries share a scope with no verified primary
- **WHEN** both verify concurrently at their own current versions
- **THEN** one becomes the verified primary and the other receives `409 primary-identifier-conflict`
- **AND** neither request silently replaces the winner

### Requirement: Rejection and revocation retain safe history

**User Story:** As an operator, I want withdrawn evidence retained with its reason and audit, so that its history stays attributable without remaining authoritative.

THE Party Registry SHALL withdraw permitted evidence with a cleared primary flag, one audited revision, retained verification/history and exact durable private reasons, without automatically changing the Party or deleting history under the shared normative rules below.

#### Scenario: Rejection removes a provisional flag

##### Shared normative rules

WHEN a permitted reject or revoke succeeds,
THE Party Registry SHALL change the state to REJECTED or REVOKED, set isPrimary false, increment that identifier version once, and update modification actor/time. It SHALL retain identity, scheme, protected/masked value, metadata, creation audit, and any prior verification actor/time. It SHALL NOT automatically select a substitute primary or change Party status/version, even when the withdrawn identifier was the Party's only activation evidence.

WHERE a reason is supplied,
THE Party Registry SHALL durably retain its exact reasonCode and reasonDetail with the accepted action, identifier, actor, operation instant, and resulting version as attributable audit history. That history SHALL survive restart, SHALL be absent for failed actions, and SHALL NOT be duplicated by replay. Reasons SHALL NOT be added to PartyIdentifierResponse or echoed through failure bodies, completion logs, or traces. Absence of a reason SHALL NOT prevent either permitted action.

THE Party Registry SHALL permit rejection of stored pending evidence and revocation of stored verified evidence even when their expiration is in the past, since those actions withdraw rather than qualify evidence. Historical records SHALL remain retrievable and SHALL NOT be physically deleted. Withdrawal SHALL release active-value reservation under the maintenance uniqueness contract.

##### Observable outcome

- **GIVEN** a pending identifier has isPrimary true
- **WHEN** current-version reject succeeds without a reason
- **THEN** it becomes REJECTED with isPrimary false and its version advances once
- **AND** it remains readable and no replacement primary is chosen

#### Scenario: Revocation retains verification and audit reason

- **GIVEN** a verified primary and valid reason strings
- **WHEN** revoke succeeds and the service later restarts
- **THEN** the identifier is REVOKED and nonprimary, its original verification remains intact, and attributable reason history is retained
- **AND** reason text is absent from the public identifier representation and logs

#### Scenario: Past-date evidence can be withdrawn

- **WHEN** reject targets stored pending evidence or revoke targets stored verified evidence with a past expiration
- **THEN** the permitted withdrawal succeeds without converting it to EXPIRED first

#### Scenario: Withdrawing last evidence does not change the Party automatically

- **GIVEN** an ACTIVE Party has only one qualifying verified identifier
- **WHEN** that identifier is revoked
- **THEN** the Party remains ACTIVE with unchanged version and audit, and no substitute primary is selected

### Requirement: Durable action key scope and request equivalence

**User Story:** As an API consumer, I want explicit retry identity, so that action keys cannot be reused to change another identifier or reason.

THE Party Registry SHALL validate optional exact action keys, scope completed outcomes independently by tenant/action, compare every effective input, and reject changed-key reuse before live evaluation under the shared normative rules below.

#### Scenario: All effective action inputs participate in key conflicts

##### Shared normative rules

WHERE Idempotency-Key is supplied for verify, reject, revoke, or set-primary,
THE Party Registry SHALL require exactly one nonblank value of at most 128 Unicode code points, returning existing duplicated, blank, or too-long 400 key codes on failure. Absence SHALL be allowed and SHALL provide no historical replay behavior. Key values SHALL compare exactly without trimming or case conversion.

THE Party Registry SHALL scope completed action results by accepted tenant, specific identifier action, and key, independently of additional registration, Party creation/lifecycle, scheme operations, and other identifier actions. Effective request identity SHALL consist of Party ID, identifier ID, expected identifier version, and the action's accepted effective body. For reject/revoke, no body, an empty object, and omitted reason properties SHALL be equivalent; supplied reason strings, including empty strings, SHALL be exact. For set-primary, absent body/property and explicit false SHALL be equivalent. JSON property order, accepted User-Id, and accepted Process-Id SHALL NOT affect equivalence.

IF a completed key is reused with a different effective target, version, reason, or replacement choice,
THEN THE Party Registry SHALL return `409 idempotency-key-conflict` before evaluating current target existence/version/state and without disclosing the saved result. Distinct tenants and distinct actions SHALL have independent key scopes.

##### Observable outcome

- **GIVEN** a keyed action has completed
- **WHEN** its key is reused for the same action with a different Party, identifier, expected version, supplied reason string, or applicable replacement choice
- **THEN** the response is `409 idempotency-key-conflict` and the saved result is not disclosed

#### Scenario: Default-equivalent bodies replay

- **GIVEN** reject/revoke completed with no body or set-primary completed with default false
- **WHEN** retry uses an empty object, or respectively explicit replaceCurrentPrimary false, with the original target/version
- **THEN** the effective input is equivalent and the original result is returned

#### Scenario: Actor changes and independent scopes

- **WHEN** equivalent retry changes only accepted user/process, or the same key text is used in another tenant or identifier action
- **THEN** the retry remains equivalent while the other tenant/action has its own independent scope

### Requirement: Historical action replay survives changes and restart

**User Story:** As an API consumer, I want the original successful action result after a lost response, so that retry does not reapply a transition or replacement.

THE Party Registry SHALL replay original safe accepted action results after later changes or restart without new effects, preserve original attribution with fresh retry correlation, and sanitize corrupt completions under the shared normative rules below.

#### Scenario: Verify replay remains verified after revocation

##### Shared normative rules

WHEN a syntactically valid action repeats a completed equivalent tenant/action/key,
THE Party Registry SHALL return the original `200 successful` safe identifier result with original accepted version, metadata, primary flag, verification evidence, and timestamps. It SHALL do so after later metadata/primary/lifecycle changes, scheme withdrawal, Party archival, or restart, without reevaluating current expiration or state. It SHALL echo the retry's accepted Process-Id without replacing original verification/audit attribution with the retry user.

THE Party Registry SHALL NOT advance any identifier or former-primary version, add reason audit, or produce another logical integration event on replay. Failed, timed-out, or cancelled attempts before acceptance SHALL consume no successful key. Corrupt completed results SHALL produce sanitized `500 server-error` rather than fabricate a new mutation or disclose corrupted content.

##### Observable outcome

- **GIVEN** keyed verify succeeded and the identifier was later revoked
- **WHEN** the original equivalent verify is retried after restart with a fresh accepted process
- **THEN** the response reproduces the original verified result and its original actor/time/version
- **AND** the current record remains revoked and the fresh process is echoed

#### Scenario: Primary replay does not repeat demotion

- **GIVEN** keyed set-primary replaced a former primary and another replacement later changed the scope
- **WHEN** the first request is replayed with its original input
- **THEN** its original candidate result is returned without touching any current primary or identifier version

#### Scenario: Failed key is reusable and corrupt result is sanitized

- **GIVEN** an action failed before acceptance under an otherwise unused key
- **WHEN** a corrected eligible request uses that key
- **THEN** it can complete successfully
- **AND** a subsequently corrupt completed result produces only `500 server-error`, never a new action

### Requirement: Atomic lifecycle concurrency and complete outcomes

**User Story:** As a registry operator, I want concurrent transitions and replay to agree, so that state, reason audit, and events never contradict one another.

THE Party Registry SHALL commit lifecycle state, audit, replay, and enabled events as one complete outcome, arbitrate concurrent keys/versions, roll back preacceptance failures, and preserve unrelated state under the shared normative rules below.

#### Scenario: Equivalent concurrent actions converge

##### Shared normative rules

WHEN a lifecycle action succeeds,
THE Party Registry SHALL make its next identifier state/version, primary flag, verification/modification audit, optional durable reason history, optional replay result, and each enabled logical event observable as one complete outcome. Rejection, timeout, or cancellation before complete acceptance SHALL preserve all prior records and produce no accepted reason, completion, or enabled event from that attempt.

WHEN equivalent same-key action requests compete,
THE Party Registry SHALL accept one logical action and return that original result to equivalent successful retries. Changed-input requests competing under the same scope/key SHALL accept at most one result; changed-input losers SHALL return `409 idempotency-key-conflict`. Distinct-key or unkeyed actions competing for the same target/version SHALL accept at most one new mutation; independent version losers SHALL return `412 expected-version-mismatch`. Primary ownership arbitration across different identifiers SHALL additionally follow the maintenance scope rules and provisional-verification conflict rules.

WHEN a completed action response is lost after acceptance,
THE Party Registry SHALL preserve the complete accepted outcome for applicable keyed recovery rather than roll back acknowledged state based on transport delivery. Event transport redelivery SHALL NOT create a new logical action. No identifier lifecycle action SHALL mutate Party/subtype/nationality/scheme state, unrelated identifiers, or stored Party-creation results.

##### Observable outcome

- **WHEN** two equivalent keyed verifies or withdrawals are submitted concurrently
- **THEN** both successful responses describe one accepted identifier version and original result
- **AND** there is one logical action, one applicable reason audit, and one event per enabled event kind

#### Scenario: Competing distinct actions cannot both use one version

- **GIVEN** a pending identifier is eligible for verify and reject
- **WHEN** those actions compete with different keys at the same current version
- **THEN** one transition succeeds and the independent loser receives `412 expected-version-mismatch`
- **AND** winner-side state, verification/reason audit, and enabled events agree

#### Scenario: Late failure rolls back the whole lifecycle outcome

- **GIVEN** an action encounters a failure before acceptance after preparing identifier changes and audit
- **WHEN** the failure response is produced
- **THEN** identifier state, flags, version, and audit remain unchanged and no completion, reason, or enabled event from the attempt is accepted

#### Scenario: Verification feeds existing activation without changing its policy

- **GIVEN** a DRAFT Party's pending compatible identifier is verified successfully
- **WHEN** a new Party activation is evaluated under its existing contract
- **THEN** that verified identifier qualifies only while its expiration is absent or on/after activation's UTC evaluation date
- **AND** primary status is not required, rejected/revoked evidence does not qualify, and activation retains its independent Party-version rules
