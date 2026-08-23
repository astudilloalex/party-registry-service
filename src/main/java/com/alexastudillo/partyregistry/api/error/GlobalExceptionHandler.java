package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.partyregistry.api.response.ApiResponseCode;
import com.alexastudillo.partyregistry.api.response.ResponseManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Maps expected and unexpected reactive REST failures to protected standard envelopes.
 */
public class GlobalExceptionHandler {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class);

    private final ResponseManager responseManager;

    @Inject
    public GlobalExceptionHandler(ResponseManager responseManager) {
        this.responseManager = responseManager;
    }

    @ServerExceptionMapper
    public Uni<Response> handleApiException(ApiException exception) {
        return error(exception.status(), exception.code());
    }

    @ServerExceptionMapper
    public Uni<Response> handleJsonProcessingException(JsonProcessingException ignored) {
        return error(Response.Status.BAD_REQUEST, ApiResponseCode.BAD_REQUEST);
    }

    @ServerExceptionMapper
    public Uni<Response> handleValidationException(ValidationException ignored) {
        return error(Response.Status.BAD_REQUEST, ApiResponseCode.BAD_REQUEST);
    }

    @ServerExceptionMapper
    public Uni<Response> handleBadRequestException(BadRequestException ignored) {
        return error(Response.Status.BAD_REQUEST, ApiResponseCode.BAD_REQUEST);
    }

    @ServerExceptionMapper
    public Uni<Response> handleNotSupportedException(NotSupportedException ignored) {
        return error(Response.Status.BAD_REQUEST, ApiResponseCode.BAD_REQUEST);
    }

    @ServerExceptionMapper
    public Uni<Response> handleNotFoundException(NotFoundException ignored) {
        return error(Response.Status.NOT_FOUND, ApiResponseCode.NOT_FOUND);
    }

    @ServerExceptionMapper
    public Uni<Response> handleNotAllowedException(NotAllowedException ignored) {
        return error(Response.Status.METHOD_NOT_ALLOWED, ApiResponseCode.METHOD_NOT_ALLOWED);
    }

    @ServerExceptionMapper
    public Uni<Response> handleServiceUnavailableException(ServiceUnavailableException ignored) {
        return error(Response.Status.SERVICE_UNAVAILABLE, ApiResponseCode.DEPENDENCY_UNAVAILABLE);
    }

    @ServerExceptionMapper
    public Uni<Response> handleUnexpectedFailure(Throwable failure) {
        LOG.error("Unexpected API failure", failure);
        return error(Response.Status.INTERNAL_SERVER_ERROR, ApiResponseCode.SERVER_ERROR);
    }

    private Uni<Response> error(Response.Status status, ApiResponseCode code) {
        return Uni.createFrom().item(() -> responseManager.error(status, code));
    }
}
