package com.alexastudillo.partyregistry.api.response;

import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Defines the stable response codes declared by the public API contract.
 */
@RegisterForReflection
public enum ApiResponseCode {
    SUCCESSFUL("successful"),
    BAD_REQUEST("bad-request"),
    NOT_FOUND("not-found"),
    PARTY_NOT_FOUND("party-not-found"),
    PARTY_NATIONALITY_NOT_FOUND("party-nationality-not-found"),
    PARTY_IDENTIFIER_NOT_FOUND("party-identifier-not-found"),
    IDENTIFIER_SCHEME_NOT_FOUND("identifier-scheme-not-found"),
    METHOD_NOT_ALLOWED("method-not-allowed"),
    CONFLICT("conflict"),
    VERSION_CONFLICT("version-conflict"),
    SERVER_ERROR("server-error"),
    DEPENDENCY_UNAVAILABLE("dependency-unavailable");

    private final String value;

    ApiResponseCode(String value) {
        this.value = value;
    }

    /**
     * Returns the wire-format value declared by OpenAPI.
     *
     * @return stable lowercase response code
     */
    @JsonValue
    public String value() {
        return value;
    }
}
