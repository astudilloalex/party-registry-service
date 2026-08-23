package com.alexastudillo.partyregistry.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Represents the standard success or error envelope returned by every API operation.
 *
 * @param status HTTP status repeated in the response body
 * @param code stable machine-readable result code
 * @param data success payload, omitted for failures
 * @param nextCursor optional continuation cursor for paginated responses
 * @param numberOfElements number of items in a paginated response
 * @param <T> payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"status", "code", "data", "nextCursor", "numberOfElements"})
@RegisterForReflection
public record ApiResponse<T>(
        int status,
        ApiResponseCode code,
        T data,
        String nextCursor,
        Integer numberOfElements) {
}
