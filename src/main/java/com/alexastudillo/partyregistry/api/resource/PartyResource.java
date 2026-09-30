package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.mapper.PartyApiMapper;
import com.alexastudillo.partyregistry.api.model.request.PartyUpdateRequest;
import com.alexastudillo.partyregistry.api.model.response.PartyDetailResponse;
import com.alexastudillo.partyregistry.api.model.response.PartySummaryResponse;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
import com.alexastudillo.partyregistry.api.support.PartyQueryParameters;
import com.alexastudillo.partyregistry.application.command.ChangePartyLifecycleCommand;
import com.alexastudillo.partyregistry.application.command.PatchPartyCommand;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.usecase.ChangePartyLifecycleUseCase;
import com.alexastudillo.partyregistry.application.usecase.GetPartyUseCase;
import com.alexastudillo.partyregistry.application.usecase.ListPartiesUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchPartyUseCase;
import com.alexastudillo.partyregistry.application.query.GetPartyQuery;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Consumes;
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
 * Exposes the six tenant-qualified root Party operations through Application workflows and explicit shared envelopes.
 */
@Path("/v1/parties")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyResource {

    private final ChangePartyLifecycleUseCase lifecycleUseCase;
    private final GetPartyUseCase getUseCase;
    private final ListPartiesUseCase listUseCase;
    private final PatchPartyUseCase patchUseCase;
    private final RequestMetadataContext metadataContext;
    private final PartyApiMapper mapper;
    private final PartyApiErrorTranslator errorTranslator;
    private final ResponseManager responseManager;
    private final ApiRequestSupport requestSupport;

    @Inject
    public PartyResource(
            ChangePartyLifecycleUseCase lifecycleUseCase,
            GetPartyUseCase getUseCase,
            ListPartiesUseCase listUseCase,
            PatchPartyUseCase patchUseCase,
            RequestMetadataContext metadataContext,
            PartyApiMapper mapper,
            PartyApiErrorTranslator errorTranslator,
            ResponseManager responseManager,
            ApiRequestSupport requestSupport) {
        this.lifecycleUseCase = lifecycleUseCase;
        this.getUseCase = getUseCase;
        this.listUseCase = listUseCase;
        this.patchUseCase = patchUseCase;
        this.metadataContext = metadataContext;
        this.mapper = mapper;
        this.errorTranslator = errorTranslator;
        this.responseManager = responseManager;
        this.requestSupport = requestSupport;
    }

    /** Lists safe tenant-owned summaries with strict query parsing and exact shared pagination metadata. */
    @GET
    @WithSpan("party.list")
    public Uni<RestResponse<ApiResponse<List<PartySummaryResponse>>>> listParties(@Context UriInfo uri) {
        return Uni.createFrom().item(() -> PartyQueryParameters.parse(metadataContext.metadata(), uri.getQueryParameters()))
                .flatMap(listUseCase::execute)
                .map(page -> {
                    var data = mapper.toSummaryResponses(page);
                    var pagination = mapper.toPaginationMetadata(page);
                    return responseManager.paginatedHttp(data, pagination);
                }).onFailure().transform(errorTranslator::translate);
    }

    /** Retrieves the current safe common representation of either Party subtype without requiring mutation headers. */
    @GET
    @Path("/{partyId}")
    @WithSpan("party.retrieve")
    public Uni<RestResponse<ApiResponse<PartyDetailResponse>>> getParty(@PathParam("partyId") String partyId) {
        return Uni.createFrom().item(() -> new GetPartyQuery(metadataContext.metadata(), requestSupport.parsePartyId(partyId)))
                .flatMap(getUseCase::execute).map(mapper::toResponse).map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    /**
     * Activates one tenant-owned Party at exactly the requested aggregate version.
     *
     * @param partyId raw path identifier
     * @param headers raw request headers used to enforce exact precondition
     *                cardinality
     * @return reactive {@code 200 successful} envelope
     */
    @POST
    @Path("/{partyId}/activate")
    @WithSpan("party.activate")
    public Uni<RestResponse<ApiResponse<PartyDetailResponse>>> activateParty(
            @PathParam("partyId") String partyId,
            @Context HttpHeaders headers) {
        return lifecycle(partyId, headers, PartyLifecycleAction.ACTIVATE);
    }

    /** Corrects only the label, validating context/body/path/version before delegating business precedence. */
    @PATCH
    @Path("/{partyId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithSpan("party.patch")
    public Uni<RestResponse<ApiResponse<PartyDetailResponse>>> patchParty(
            @PathParam("partyId") String partyId, PartyUpdateRequest request, @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> {
            var metadata = metadataContext.metadata();
            var valid = requestSupport.validateBody(request, PartyUpdateRequest::normalizedForValidation);
            return new PatchPartyCommand(metadata, requestSupport.parsePartyId(partyId),
                    requestSupport.requireExpectedVersion(headers), valid.displayName());
        }).flatMap(patchUseCase::execute).invoke(metadataContext::recordMutationDisposition)
                .map(PartyMutationOutcome::party).map(mapper::toResponse)
                .map(responseManager::successHttp).onFailure().transform(errorTranslator::translate);
    }

    /** Deactivates a currently active Party or replays the original equivalent keyed result. */
    @POST
    @Path("/{partyId}/deactivate")
    @WithSpan("party.deactivate")
    public Uni<RestResponse<ApiResponse<PartyDetailResponse>>> deactivateParty(
            @PathParam("partyId") String partyId, @Context HttpHeaders headers) {
        return lifecycle(partyId, headers, PartyLifecycleAction.DEACTIVATE);
    }

    /** Archives a retained Party or replays the original equivalent keyed result without another mutation. */
    @POST
    @Path("/{partyId}/archive")
    @WithSpan("party.archive")
    public Uni<RestResponse<ApiResponse<PartyDetailResponse>>> archiveParty(
            @PathParam("partyId") String partyId, @Context HttpHeaders headers) {
        return lifecycle(partyId, headers, PartyLifecycleAction.ARCHIVE);
    }

    private Uni<RestResponse<ApiResponse<PartyDetailResponse>>> lifecycle(String partyId, HttpHeaders headers, PartyLifecycleAction action) {
        return Uni.createFrom().item(() -> command(partyId, headers, action))
                .flatMap(lifecycleUseCase::execute)
                .invoke(metadataContext::recordMutationDisposition)
                .map(PartyMutationOutcome::party)
                .map(mapper::toResponse)
                .map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    private ChangePartyLifecycleCommand command(String partyId, HttpHeaders headers, PartyLifecycleAction action) {
        var metadata = metadataContext.metadata();
        var key = requestSupport.optionalIdempotencyKey(headers);
        return new ChangePartyLifecycleCommand(
                metadata,
                requestSupport.parsePartyId(partyId),
                requestSupport.requireExpectedVersion(headers), action, key);
    }
}
