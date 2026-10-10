package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierPatchRequest;
import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import com.alexastudillo.partyregistry.api.support.PartyIdentifierQueryOperations;
import com.alexastudillo.partyregistry.api.support.PartyIdentifierRegistrationOperations;
import com.alexastudillo.partyregistry.api.support.PartyIdentifierPatchOperations;
import com.alexastudillo.partyregistry.api.support.PartyIdentifierLifecycleOperations;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

/**
 * Exposes independent official-identifier registration, historical reads, metadata correction and lifecycle actions.
 */
@Path("/v1/parties/{partyId}/identifiers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierResource {

    private final PartyIdentifierRegistrationOperations registration;
    private final PartyIdentifierQueryOperations queries;
    private final PartyIdentifierPatchOperations patches;
    private final PartyIdentifierLifecycleOperations lifecycle;

    @Inject
    public PartyIdentifierResource(
            PartyIdentifierRegistrationOperations registration,
            PartyIdentifierQueryOperations queries,
            PartyIdentifierPatchOperations patches,
            PartyIdentifierLifecycleOperations lifecycle) {
        this.registration = registration;
        this.queries = queries;
        this.patches = patches;
        this.lifecycle = lifecycle;
    }

    /** Returns an owned historical page with strict selectors, authenticated continuation and safe shared metadata. */
    @GET
    @WithSpan("party-identifier.list")
    public Uni<RestResponse<ApiResponse<List<PartyIdentifierResponse>>>> listPartyIdentifiers(
            @PathParam("partyId") String partyId, @Context UriInfo uri) {
        return queries.list(partyId, uri.getQueryParameters());
    }

    /** Retrieves the safe stored representation in every identifier state without changing lifecycle or audit. */
    @GET
    @Path("/{identifierId}")
    @WithSpan("party-identifier.retrieve")
    public Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>> getPartyIdentifier(
            @PathParam("partyId") String partyId, @PathParam("identifierId") String identifierId) {
        return queries.detail(partyId, identifierId);
    }

    /**
     * Registers an independently versioned identifier without changing Party identity or type.
     *
     * @param partyId raw path identifier
     * @param request strict identifier creation body
     * @param headers raw request headers used to enforce exact idempotency-key cardinality
     * @return reactive {@code 201 successful} envelope containing only the safe identifier projection
     */
    @POST
    @WithSpan("party-identifier.create")
    public Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>> createPartyIdentifier(
            @PathParam("partyId") String partyId,
            PartyIdentifierCreateRequest request,
            @Context HttpHeaders headers) {
        return registration.register(partyId, request, headers);
    }

    /** Corrects only presence-aware metadata using the identifier's own expected version and shared safe response. */
    @PATCH
    @Path("/{identifierId}")
    @WithSpan("party-identifier.patch")
    public Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>> patchPartyIdentifier(
            @PathParam("partyId") String partyId, @PathParam("identifierId") String identifierId,
            PartyIdentifierPatchRequest request, @Context HttpHeaders headers) {
        return patches.patch(partyId, identifierId, request, headers);
    }

    /** Records operator verification after enforcing a strictly bodyless request and independent expected version. */
    @POST
    @Path("/{identifierId}/verify")
    @WithSpan("party-identifier.verify")
    public Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>> verifyPartyIdentifier(
            @PathParam("partyId") String partyId, @PathParam("identifierId") String identifierId,
            byte[] body, @Context HttpHeaders headers) {
        return lifecycle.change(partyId, identifierId, body, headers, PartyIdentifierOperation.VERIFY);
    }

    /** Rejects pending evidence with optional exact private reasons and durable keyed recovery. */
    @POST
    @Path("/{identifierId}/reject")
    @WithSpan("party-identifier.reject")
    public Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>> rejectPartyIdentifier(
            @PathParam("partyId") String partyId, @PathParam("identifierId") String identifierId,
            byte[] body, @Context HttpHeaders headers) {
        return lifecycle.change(partyId, identifierId, body, headers, PartyIdentifierOperation.REJECT);
    }

    /** Revokes verified evidence while retaining its verification history and optional private reason audit. */
    @POST
    @Path("/{identifierId}/revoke")
    @WithSpan("party-identifier.revoke")
    public Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>> revokePartyIdentifier(
            @PathParam("partyId") String partyId, @PathParam("identifierId") String identifierId,
            byte[] body, @Context HttpHeaders headers) {
        return lifecycle.change(partyId, identifierId, body, headers, PartyIdentifierOperation.REVOKE);
    }
}
