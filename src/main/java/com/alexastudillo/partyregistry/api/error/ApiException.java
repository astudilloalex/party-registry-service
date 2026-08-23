package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.partyregistry.api.response.ApiResponseCode;
import jakarta.ws.rs.core.Response;

import java.util.Objects;

/**
 * Represents an expected application failure that has an approved HTTP mapping.
 */
public class ApiException extends RuntimeException {

    private final Response.Status status;
    private final ApiResponseCode code;

    /**
     * Creates an expected API failure without exposing a detailed message.
     *
     * @param status mapped HTTP status
     * @param code stable response code
     */
    public ApiException(Response.Status status, ApiResponseCode code) {
        this(status, code, null);
    }

    /**
     * Creates an expected API failure with an internal diagnostic message.
     *
     * @param status mapped HTTP status
     * @param code stable response code
     * @param internalMessage diagnostic message that is never serialized
     */
    public ApiException(Response.Status status, ApiResponseCode code, String internalMessage) {
        super(internalMessage);
        this.status = Objects.requireNonNull(status, "HTTP status is required");
        this.code = Objects.requireNonNull(code, "Response code is required");
        if (status.getStatusCode() < 400) {
            throw new IllegalArgumentException("An API exception requires a failure HTTP status");
        }
    }

    public Response.Status status() {
        return status;
    }

    public ApiResponseCode code() {
        return code;
    }
}
