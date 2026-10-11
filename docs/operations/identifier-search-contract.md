# Identifier Search Consumer Guide

The approved [OpenAPI document](../contracts/party-registry.openapi.yaml) defines
two read-only JSON operations:

| Operation | Route | Selection |
| --- | --- | --- |
| Complete equality | `POST /v1/parties/search/by-identifier` | Entire normalized identifier |
| Literal prefix | `POST /v1/parties/search/by-identifier-prefix` | Beginning of the complete normalized identifier |

Both operations search the accepted tenant only. They return identifier hits,
so a Party can occur more than once when its historical identifiers match.
Searches do not register, verify, expire, revoke, activate, designate a primary,
write replay records, or emit logical events. Mutation headers do not enable
those behaviors.

## Trusted request context

Send exactly one `Process-Id`, `Tenant-Id`, and `User-Id`. Header names are
case-insensitive. Process and tenant UUIDs must be canonical lowercase text
without padding; users must be nonblank, no longer than 128 Unicode code points,
and contain no C0, DEL or C1 controls. Checks run in process/tenant/user order,
then absence/duplication/value order within each header. Accepted process text
is echoed unchanged, including when later input fails; missing, duplicate or
invalid process text is never echoed. `/q` management paths are exempt.

These headers provide routing and attribution, not authentication. Credentials
remain necessary when the deployment enables authentication.

## Strict JSON and normalization

Exact requests allow only `identifierSchemeCode`, `value`, `identifierStatuses`
and `partyRecordStatuses`. Prefix requests replace `value` with `prefix` and
add `cursor` and `limit`. No query parameter is supported. Duplicate/unknown
properties, trailing content, nonobject input and token coercions are rejected.

Required strings are nonnull and nonblank. Raw limits count UTF-16 code units:
scheme code 64, exact value 256, prefix 64. Supplementary characters occupy two
units, and exterior whitespace counts before normalization. OpenAPI declares
`x-string-length-unit: utf16-code-units`; a code-point-only validator cannot
prove this stricter rule.

Scheme codes are exact case-sensitive catalog text: do not trim or change their
case. For `TRIM_UPPERCASE_V1`, selectors use the established exterior trim and
locale-independent uppercase rule. Accents, punctuation and interior whitespace
remain significant. Uppercase expansion is not truncated or subjected to a
second raw-input length limit. Percent and underscore are literal characters,
not SQL wildcards. A mask, substring or fuzzy equivalent is not an exact match.

Supported normalization is sufficient for discovery. Registration validators,
complete checksums, current scheme length bounds, subject-type admission,
scheme withdrawal, expiration dates and primary designation do not readmit or
filter historical evidence. A short national-ID prefix such as `17` is valid.
Known DRAFT, ACTIVE, DEPRECATED and RETIRED schemes remain searchable; unknown
codes and unsupported normalization have distinct 422 failures.

## Historical filters

Both dimensions use exact enum strings and set membership:

- Identifier states: PENDING_VERIFICATION, VERIFIED, REJECTED, EXPIRED, REVOKED.
- Party states: DRAFT, ACTIVE, INACTIVE, ARCHIVED.
- Omission includes every stored state in that dimension.
- `[]` matches nothing; it never expands to the default.
- Explicit null arrays, null/nonstring elements, unknown values and wrong casing
  are invalid.
- Duplicate/order differences do not change membership. The two dimensions
  intersect, and both Party types remain eligible.

Empty filters still require a known scheme and supported normalization; supplied
prefix cursors are still authenticated against their effective scope.

## Exact search example

All values and identifiers in this guide are synthetic. Supply deployment
credentials separately when required.

```http
POST /v1/parties/search/by-identifier
Content-Type: application/json
Tenant-Id: 0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1
Process-Id: 0198ce2b-d6a3-7d6e-80ba-d97b21d793e5
User-Id: registry-search-consumer

{
  "identifierSchemeCode": "EC_NATIONAL_ID",
  "value": "1710034065",
  "identifierStatuses": ["VERIFIED", "REVOKED"]
}
```

Exact search returns the entire matching collection, including more than 200
historical hits. It accepts neither limit nor cursor input. Ordering is matching
identifier creation instant descending, then unsigned UUID descending; stored
timestamp precision is preserved internally. Data and counts share one read view.

```json
{
  "status": 200,
  "code": "successful",
  "data": [{
    "partyId": "00000000-0000-4000-8000-000000000913",
    "type": "NATURAL_PERSON",
    "displayName": "Synthetic Party",
    "recordStatus": "ARCHIVED",
    "identifier": {
      "identifierId": "00000000-0000-4000-8000-000000000915",
      "schemeCode": "EC_NATIONAL_ID",
      "maskedValue": "******4065",
      "status": "REVOKED",
      "isPrimary": false
    }
  }],
  "nextCursor": null,
  "prevCursor": null,
  "totalElements": 1,
  "totalPages": 1,
  "numberOfElements": 1
}
```

`totalElements` and `numberOfElements` equal data length. `totalPages` is zero
for an empty collection and one otherwise. Both cursor properties are present
and null. No matches returns 200 with `data: []` and three zero counts, not 404.

## Prefix navigation

```json
{
  "identifierSchemeCode": "EC_NATIONAL_ID",
  "prefix": "17",
  "limit": 2
}
```

Omitted limit defaults to 50. Supplied limit must be an integer JSON token in
1..200; null, strings, booleans, `1.0`, `1e0`, zero, negatives and overflow are
invalid. Omitted/null cursor selects the first page. A nonnull cursor must be a
nonblank opaque server-issued string no longer than 256 characters.

Copy `nextCursor` or `prevCursor` verbatim into the next POST body with the same
effective scope. Stop in that direction when its cursor is null. Do not invent,
decode or edit tokens. For five unchanged matches and limit two, forward pages
contain 2/2/1 hits, totalElements remains five, totalPages remains three, and
previous navigation restores the nearest preceding page in the same descending
order.

Cursor scope includes resource, tenant, exact scheme identity/code, normalization,
normalized prefix, both effective state sets and limit. Equivalent normalized
prefixes, omitted versus explicit full filters, reordered/duplicate members and
omitted versus explicit limit 50 are compatible. User/process may change and are
validated normally but are not cursor scope dimensions. Altering any effective
scope dimension rejects continuation.

Each response is one consistent snapshot; separate requests are live. A deleted
or no-longer-matching boundary need not exist for its cursor to work. Counts
always describe current complete matches before continuation. An empty live page
can retain nonzero totals and offer navigation only toward existing matches.
When all matches disappear, data/counts are empty/zero and both cursors are null.
Tokens have no newly promised automatic expiry or retained cross-request snapshot.

## Safe responses and failures

Every success has exactly status/code/data and all five pagination properties.
Items expose only Party ID/type/stored display name/record state and matching
identifier ID/scheme code/stored mask/state/primary flag. Counts are nonnull
integers in 0..2147483647. HTTP status equals body status.

Responses never include raw/normalized identifiers, selectors, ciphertext,
lookup hashes, key/protection versions, decoded tokens, internal positions,
reason fields, subtype details or storage models. Issued opaque tokens appear
only in response cursor fields. Sensitive selectors/tokens must not enter logs,
traces or metric dimensions.

| Status | Stable code | Meaning |
| --- | --- | --- |
| 400 | `process-id-*`, `tenant-id-*`, `user-id-*` | First trusted-context failure |
| 400 | `request-body-required` | Absent or JSON-null body |
| 400 | `bad-request` | Strict input, unsupported query or invalid cursor scope |
| 401 | `unauthorized` | Conditional deployment authentication failure |
| 404 | `not-found` | Unknown route; an empty search is still 200 |
| 405 | `method-not-allowed` | Unsupported method |
| 415 | `unsupported-media-type` | Unsupported request representation |
| 422 | `unknown-identifier-scheme` | Unknown exact catalog code |
| 422 | `identifier-validation-failure` | Unsupported selected normalization |
| 500 | `server-error` | Unexpected failure, required corrupt material or count capacity overflow |
| 503 | `dependency-unavailable` | Classified dependency failure, deadline or rejected CPU admission |

Errors contain only matching status and code, for example
`{"status":503,"code":"dependency-unavailable"}`. No data, field-error details,
rejected values, messages, SQL, causes or stack traces are public. Searches never
replace a failure with capped totals, skipped corrupt matches, partial success
or automatic indefinite retry.

Operational rollout and measurement guidance is in
[identifier-search-operations.md](identifier-search-operations.md).
