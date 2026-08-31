package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.contract.ApiResponseCode;

/**
 * Defines the stable response codes owned by the natural-person API boundary.
 */
public enum NaturalPersonResponseCode implements ApiResponseCode {
    CREATED("successful", 201),
    NOT_FOUND("not-found", 404),
    IDEMPOTENCY_CONFLICT("conflict", 409),
    PRECONDITION_FAILED("precondition-failed", 412),
    UNPROCESSABLE_ENTITY("unprocessable-entity", 422),
    DEPENDENCY_UNAVAILABLE("dependency-unavailable", 503);

    private final String code;
    private final int status;

    NaturalPersonResponseCode(String code, int status) {
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
