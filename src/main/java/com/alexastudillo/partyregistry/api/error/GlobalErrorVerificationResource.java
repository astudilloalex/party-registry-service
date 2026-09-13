package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.contract.CommonResponseCode;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonCreateRequest;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.security.AuthenticationFailedException;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.Router;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

/**
 * Provides build-time-gated endpoints for shared global-error contract
 * verification.
 */
@Path("/v1/error-verification")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@IfBuildProperty(name = "party-registry.error-verification.enabled", stringValue = "true")
public class GlobalErrorVerificationResource {

    private static final String INTERNAL_DETAIL = "sensitive-database-detail";

    private final Validator validator;
    private final ResponseManager responseManager;

    @Inject
    public GlobalErrorVerificationResource(Validator validator, ResponseManager responseManager) {
        this.validator = validator;
        this.responseManager = responseManager;
    }

    /**
     * Exercises Bean Validation while preserving the service's `bad-request`
     * contract.
     *
     * @param request constrained natural-person request
     * @return successful empty envelope when the request is valid
     */
    @POST
    @Path("/validation")
    public Uni<RestResponse<ApiResponse<Void>>> validate(NaturalPersonCreateRequest request) {
        return Uni.createFrom().item(() -> {
            if (request == null || !validator.validate(request).isEmpty()) {
                throw new ApiResponseException(CommonResponseCode.BAD_REQUEST);
            }
            return responseManager.successHttp();
        });
    }

    /**
     * Emits an unexpected internal failure for sanitization verification.
     *
     * @return a failed reactive pipeline
     */
    @GET
    @Path("/unexpected")
    public Uni<RestResponse<ApiResponse<Void>>> unexpected() {
        return Uni.createFrom().failure(new IllegalStateException(INTERNAL_DETAIL));
    }

    void registerAuthenticationFailure(@Observes Router router) {
        router.get("/v1/error-verification/authentication-failure")
                .handler(context -> context.fail(new AuthenticationFailedException()));
    }
}
