package com.alexastudillo.partyregistry.api.response;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Objects;

/**
 * Builds standard API envelopes while keeping HTTP and body status values consistent.
 */
@ApplicationScoped
public class ResponseManager {

    /**
     * Builds a successful response for a non-paginated payload.
     *
     * @param status successful HTTP status
     * @param data response payload
     * @param <T> payload type
     * @return response with a standard success envelope
     */
    public <T> Response success(Response.Status status, T data) {
        requireSuccess(status);
        return response(status.getStatusCode(), new ApiResponse<>(
                status.getStatusCode(),
                ApiResponseCode.SUCCESSFUL,
                Objects.requireNonNull(data, "Success data is required"),
                null,
                null));
    }

    /**
     * Builds a reactive successful response for use by application-facing resources.
     *
     * @param status successful HTTP status
     * @param data response payload
     * @param <T> payload type
     * @return a Uni containing the standard response
     */
    public <T> Uni<Response> successAsync(Response.Status status, T data) {
        return Uni.createFrom().item(() -> success(status, data));
    }

    /**
     * Builds a successful cursor page and emits pagination fields only for that response shape.
     *
     * @param data current page items
     * @param nextCursor optional continuation cursor
     * @param <T> item type
     * @return response with the standard paginated envelope
     */
    public <T> Response page(List<T> data, String nextCursor) {
        List<T> page = List.copyOf(Objects.requireNonNull(data, "Page data is required"));
        return response(Response.Status.OK.getStatusCode(), new ApiResponse<>(
                Response.Status.OK.getStatusCode(),
                ApiResponseCode.SUCCESSFUL,
                page,
                nextCursor,
                page.size()));
    }

    /**
     * Builds an error response without protected or internal details.
     *
     * @param status failure HTTP status
     * @param code stable error code
     * @return response with a data-free standard envelope
     */
    public Response error(Response.Status status, ApiResponseCode code) {
        if (status.getStatusCode() < 400) {
            throw new IllegalArgumentException("An error response requires a failure HTTP status");
        }
        return response(status.getStatusCode(), new ApiResponse<>(
                status.getStatusCode(),
                Objects.requireNonNull(code, "Error code is required"),
                null,
                null,
                null));
    }

    private static void requireSuccess(Response.Status status) {
        int code = Objects.requireNonNull(status, "HTTP status is required").getStatusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalArgumentException("A success response requires a successful HTTP status");
        }
    }

    private static Response response(int status, ApiResponse<?> entity) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(entity)
                .build();
    }
}
