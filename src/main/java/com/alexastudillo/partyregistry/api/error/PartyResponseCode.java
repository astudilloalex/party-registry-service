package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.contract.ApiResponseCode;

/**
 * Defines the stable response codes owned by the Party API boundary.
 */
public enum PartyResponseCode implements ApiResponseCode {
    CREATED("successful", 201),
    BAD_REQUEST("bad-request", 400),
    PROCESS_ID_REQUIRED("process-id-required", 400),
    PROCESS_ID_DUPLICATED("process-id-duplicated", 400),
    PROCESS_ID_INVALID("process-id-invalid", 400),
    TENANT_ID_REQUIRED("tenant-id-required", 400),
    TENANT_ID_DUPLICATED("tenant-id-duplicated", 400),
    TENANT_ID_INVALID("tenant-id-invalid", 400),
    USER_ID_REQUIRED("user-id-required", 400),
    USER_ID_DUPLICATED("user-id-duplicated", 400),
    USER_ID_BLANK("user-id-blank", 400),
    USER_ID_TOO_LONG("user-id-too-long", 400),
    USER_ID_UNSAFE("user-id-unsafe", 400),
    REQUEST_BODY_REQUIRED("request-body-required", 400),
    DISPLAY_NAME_TOO_LONG("display-name-too-long", 400),
    GIVEN_NAMES_REQUIRED("given-names-required", 400),
    GIVEN_NAMES_TOO_LONG("given-names-too-long", 400),
    FAMILY_NAMES_REQUIRED("family-names-required", 400),
    FAMILY_NAMES_TOO_LONG("family-names-too-long", 400),
    PREFERRED_NAME_TOO_LONG("preferred-name-too-long", 400),
    BIRTH_COUNTRY_CODE_INVALID("birth-country-code-invalid", 400),
    INITIAL_IDENTIFIER_REQUIRED("initial-identifier-required", 400),
    IDENTIFIER_SCHEME_CODE_REQUIRED("identifier-scheme-code-required", 400),
    IDENTIFIER_SCHEME_CODE_TOO_LONG("identifier-scheme-code-too-long", 400),
    IDENTIFIER_VALUE_REQUIRED("identifier-value-required", 400),
    IDENTIFIER_VALUE_TOO_LONG("identifier-value-too-long", 400),
    ISSUER_CODE_TOO_LONG("issuer-code-too-long", 400),
    LEGAL_NAME_REQUIRED("legal-name-required", 400),
    LEGAL_NAME_INVALID("legal-name-invalid", 400),
    TRADE_NAME_TOO_LONG("trade-name-too-long", 400),
    LEGAL_FORM_CODE_TOO_LONG("legal-form-code-too-long", 400),
    INCORPORATION_COUNTRY_CODE_REQUIRED("incorporation-country-code-required", 400),
    INCORPORATION_COUNTRY_CODE_INVALID("incorporation-country-code-invalid", 400),
    PATCH_PROPERTY_REQUIRED("patch-property-required", 400),
    PARTY_ID_INVALID("party-id-invalid", 400),
    IDEMPOTENCY_KEY_REQUIRED("idempotency-key-required", 400),
    IDEMPOTENCY_KEY_DUPLICATED("idempotency-key-duplicated", 400),
    IDEMPOTENCY_KEY_BLANK("idempotency-key-blank", 400),
    IDEMPOTENCY_KEY_TOO_LONG("idempotency-key-too-long", 400),
    IF_MATCH_REQUIRED("if-match-required", 400),
    IF_MATCH_DUPLICATED("if-match-duplicated", 400),
    IF_MATCH_INVALID("if-match-invalid", 400),
    IF_MATCH_OUT_OF_RANGE("if-match-out-of-range", 400),
    NOT_FOUND("not-found", 404),
    PARTY_NOT_FOUND("party-not-found", 404),
    NATURAL_PERSON_NOT_FOUND("natural-person-not-found", 404),
    CONFLICT("conflict", 409),
    IDEMPOTENCY_KEY_CONFLICT("idempotency-key-conflict", 409),
    PRECONDITION_FAILED("precondition-failed", 412),
    EXPECTED_VERSION_MISMATCH("expected-version-mismatch", 412),
    STALE_PARTY_VERSION("stale-party-version", 412),
    UNPROCESSABLE_ENTITY("unprocessable-entity", 422),
    UNRECOGNIZED_BIRTH_COUNTRY("unrecognized-birth-country", 422),
    UNRECOGNIZED_INCORPORATION_COUNTRY("unrecognized-incorporation-country", 422),
    INACTIVE_IDENTIFIER_SCHEME("inactive-identifier-scheme", 422),
    INCOMPATIBLE_IDENTIFIER_SCHEME("incompatible-identifier-scheme", 422),
    UNKNOWN_IDENTIFIER_SCHEME("unknown-identifier-scheme", 422),
    IDENTIFIER_UNIQUENESS_CONFLICT("identifier-uniqueness-conflict", 422),
    IDENTIFIER_VALIDATION_FAILURE("identifier-validation-failure", 422),
    INVALID_PARTY_LIFECYCLE("invalid-party-lifecycle", 422),
    INVALID_BUSINESS_STATE("invalid-business-state", 422),
    DEPENDENCY_UNAVAILABLE("dependency-unavailable", 503);

    private final String code;
    private final int status;

    PartyResponseCode(String code, int status) {
        this.code = code;
        this.status = status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public int getStatus() {
        return status;
    }
}
