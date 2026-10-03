package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemeCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemePatchRequest;
import com.alexastudillo.partyregistry.api.model.response.IdentifierSchemeBoundaryReceipt;
import com.alexastudillo.partyregistry.api.support.IdentifierSchemeRequestSupport;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

/** Exercises the real body readers and shared response boundary exclusively in test sources. */
@Path("/v1/identifier-scheme-binding-verification")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class IdentifierSchemeBindingVerificationResource {
    @Inject
    IdentifierSchemeRequestSupport support;
    @Inject
    RequestMetadataContext metadata;
    @Inject
    ResponseManager responses;

    @POST
    @Path("/create")
    public Uni<RestResponse<ApiResponse<IdentifierSchemeBoundaryReceipt>>> create(
            IdentifierSchemeCreateRequest request, @jakarta.ws.rs.core.Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> {
            var input = support.createCommand(metadata.metadata(), request, headers).effectiveRequest();
            var receipt = new IdentifierSchemeBoundaryReceipt(Context.isOnEventLoopThread(), input.code(),
                    input.minimumLength() == null ? null : input.minimumLength().toString(), false, false);
            return responses.customHttp(PartyResponseCode.CREATED, receipt);
        });
    }

    @POST
    @Path("/patch/{id}")
    public Uni<RestResponse<ApiResponse<IdentifierSchemeBoundaryReceipt>>> patch(IdentifierSchemePatchRequest request,
            @PathParam("id") String id, @jakarta.ws.rs.core.Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> {
            var changes = support.patchCommand(metadata.metadata(), request, id, headers).changes();
            return responses.successHttp(new IdentifierSchemeBoundaryReceipt(Context.isOnEventLoopThread(), null,
                    changes.minimumLength().value() == null ? null : changes.minimumLength().value().toString(),
                    changes.name().isPresent(), changes.minimumLength().isPresent()));
        });
    }

    @POST
    @Path("/lifecycle/{id}")
    public Uni<RestResponse<ApiResponse<IdentifierSchemeBoundaryReceipt>>> lifecycle(byte[] body,
            @PathParam("id") String id, @jakarta.ws.rs.core.Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> {
            support.lifecycleCommand(metadata.metadata(), body, id, IdentifierSchemeLifecycleAction.RETIRE, headers);
            return responses.successHttp(new IdentifierSchemeBoundaryReceipt(Context.isOnEventLoopThread(), null, null, false, false));
        });
    }
}
