## Why

The registry can create a legal entity, but consumers cannot retrieve or maintain its legal details through the dedicated legal-entity API. The approved OpenAPI already declares these operations, while the service currently exposes only `POST /v1/legal-entity`. This gap prevents operators from reviewing registered organizations and correcting their information through the supported API.

Completing legal-entity retrieval and updates enables a usable registration-and-maintenance workflow with predictable validation, tenant isolation, and protection against concurrent overwrites.

## What Changes

- Deliver the three missing operations declared for `/v1/legal-entity/{partyId}`:
  - `GET`: retrieve the tenant-owned legal entity, including its Party identity, status, version, audit information, and `legalEntityDetails`.
  - `PUT`: replace the complete legal-detail representation, requiring `legalName` and `incorporationCountryCode` and clearing omitted nullable fields.
  - `PATCH`: update only supplied legal-detail fields, retaining omitted values and allowing explicit null only for nullable properties. Require at least one supported property.
- Support the declared legal-detail fields: `legalName`, `tradeName`, `legalFormCode`, `incorporationCountryCode`, `incorporatedOn`, and `dissolvedOn`. Apply the existing canonical text, normalized-length, country-recognition, and coherent lifecycle-date rules to the resulting record. Specify the derived display-name behavior when the legal name changes.
- Require the established tenant, user, and process headers. Conceal absent, cross-tenant, and wrong-type Parties consistently. Preserve accepted process correlation on responses.
- Protect PUT and PATCH with the current Party version in `If-Match`; reject stale or losing concurrent updates with `412 precondition-failed`. Successful updates return the resulting version and audit information; rejected updates leave the record unchanged.
- Return the declared `LegalEntityApiResponse` with matching HTTP/body status and stable success or error codes. Clarify the operation-specific validation and geographic-dependency failure responses in the OpenAPI, including `503 dependency-unavailable` when country validation cannot be completed.
- Complete consumer documentation and request/response examples for these operations in the existing Postman collection, under its **Legal Entities** folder.

## User Stories

- As a registry operator, I want to retrieve a registered legal entity, so that I can review its current legal details and version.
- As a data steward, I want to replace an organization's complete legal details, so that the registry reflects an authoritative corrected representation.
- As a registry operator, I want to correct selected legal fields without overwriting other information, so that targeted maintenance is reliable.
- As an API consumer, I want concurrent updates and invalid input rejected predictably, so that I can resolve conflicts without losing accepted changes.

## Capabilities

### New Capabilities

- `legal-entity-details`: Tenant-scoped retrieval, complete replacement, and partial maintenance of legal-entity details, including validation, canonical values, version preconditions, audit outcomes, and public response behavior.

### Modified Capabilities

## Impact

- Legal-entity consumers gain the previously declared GET, PUT, and PATCH operations and can maintain records after registration.
- The approved contract at `docs/contracts/party-registry.openapi.yaml` gains explicit operational semantics and aligned error documentation for these three operations.
- The Geographic Reference Service participates in validation of applicable incorporation-country updates.
- The `Legal Entities` folder in Postman collection `15834347-8f36abef-3a94-4646-a4f2-eecd36c74313` gains the corresponding documented requests and examples. Service documentation reflects their implemented availability.

## Out of Scope

- Changing existing Party creation, initial-identifier requirements, or stored idempotent creation results.
- Introducing legal-entity listing, search, deletion, bulk operations, or new routes beyond the three declared detail operations.
- Changing Party type or lifecycle status through legal-detail updates, or adding activation, deactivation, or archival workflows.
- Registering, modifying, verifying, or expanding identifier representations through legal-detail endpoints.
- Nationality endpoints, natural-person behavior changes, and historical data normalization or backfills.
