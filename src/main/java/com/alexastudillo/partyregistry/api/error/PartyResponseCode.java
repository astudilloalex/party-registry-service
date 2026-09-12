package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.contract.ApiResponseCode;

/**
 * Defines the stable response codes owned by the Party API boundary.
 */
public enum PartyResponseCode implements ApiResponseCode {
    CREATED("successful", 201),
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
