package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.model.response.IdentifierActionBoundaryReceipt;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
import com.alexastudillo.partyregistry.api.support.PartyIdentifierActionBodies;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

/** Exercises BOM-owned body buffering/strict optional decoding and shared responses exclusively in test sources. */
@Path("/v1/identifier-action-binding-verification")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PartyIdentifierActionBindingVerificationResource {
    @Inject ResponseManager responses;
    @Inject ApiRequestSupport inputs;
    @Inject ObjectMapper json;

    @POST
    @Path("/verify/{party}")
    public Uni<RestResponse<ApiResponse<IdentifierActionBoundaryReceipt>>> verify(byte[] body, @PathParam("party") String party) {
        return Uni.createFrom().item(() -> {
            PartyIdentifierActionBodies.verify(body); inputs.parsePartyId(party);
            return responses.successHttp(new IdentifierActionBoundaryReceipt(Context.isOnEventLoopThread(), false, 0, false, 0));
        });
    }

    @POST
    @Path("/reason/{party}")
    public Uni<RestResponse<ApiResponse<IdentifierActionBoundaryReceipt>>> reasons(byte[] request, @PathParam("party") String party) {
        return Uni.createFrom().item(() -> {
            var body = PartyIdentifierActionBodies.reasons(request, json); inputs.parsePartyId(party);
            return responses.successHttp(new IdentifierActionBoundaryReceipt(Context.isOnEventLoopThread(), body.reasonCode() != null,
                    points(body.reasonCode()), body.reasonDetail() != null, points(body.reasonDetail())));
        });
    }

    private static int points(String value) { return value == null ? 0 : value.codePointCount(0, value.length()); }
}
