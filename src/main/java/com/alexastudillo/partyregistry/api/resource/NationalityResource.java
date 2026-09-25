package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.mapper.NationalityApiMapper;
import com.alexastudillo.partyregistry.api.model.request.NationalityCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.NationalityPatchRequest;
import com.alexastudillo.partyregistry.api.model.response.NationalityResponse;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
import com.alexastudillo.partyregistry.api.support.NationalityQueryParameters;
import com.alexastudillo.partyregistry.api.support.NationalityRequestParameters;
import com.alexastudillo.partyregistry.application.command.CreateNationalityCommand;
import com.alexastudillo.partyregistry.application.command.GetNationalityQuery;
import com.alexastudillo.partyregistry.application.command.PatchNationalityCommand;
import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import com.alexastudillo.partyregistry.application.usecase.CreateNationalityUseCase;
import com.alexastudillo.partyregistry.application.usecase.GetNationalityUseCase;
import com.alexastudillo.partyregistry.application.usecase.ListNationalitiesUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchNationalityUseCase;
import com.alexastudillo.partyregistry.application.usecase.SetPrimaryNationalityUseCase;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.Clock;
import java.util.List;

/** Exposes the five tenant-scoped nationality operations through application workflows and shared envelopes. */
@Path("/v1/parties")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class NationalityResource {

    private final CreateNationalityUseCase create;
    private final ListNationalitiesUseCase list;
    private final GetNationalityUseCase get;
    private final PatchNationalityUseCase patch;
    private final SetPrimaryNationalityUseCase setPrimary;
    private final RequestMetadataContext metadata;
    private final ApiRequestSupport requests;
    private final NationalityRequestParameters nationalityRequests;
    private final NationalityApiMapper mapper;
    private final PartyApiErrorTranslator errors;
    private final ResponseManager responses;
    private final Clock clock;

    @Inject
    public NationalityResource(CreateNationalityUseCase create, ListNationalitiesUseCase list,
            GetNationalityUseCase get, PatchNationalityUseCase patch, SetPrimaryNationalityUseCase setPrimary,
            RequestMetadataContext metadata, ApiRequestSupport requests,
            NationalityRequestParameters nationalityRequests, NationalityApiMapper mapper,
            PartyApiErrorTranslator errors, ResponseManager responses, Clock clock) {
        this.create = create;
        this.list = list;
        this.get = get;
        this.patch = patch;
        this.setPrimary = setPrimary;
        this.metadata = metadata;
        this.requests = requests;
        this.nationalityRequests = nationalityRequests;
        this.mapper = mapper;
        this.errors = errors;
        this.responses = responses;
        this.clock = clock;
    }

    /** Creates or replays one country-validated nationality under the supplied Party. */
    @POST
    @Path("/{partyId}/nationalities")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithSpan("nationality.create")
    public Uni<RestResponse<ApiResponse<NationalityResponse>>> createNationality(
            @PathParam("partyId") String partyId, NationalityCreateRequest request, @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> {
            var context = metadata.metadata();
            String code = nationalityRequests.countryCode(request);
            var id = requests.parsePartyId(partyId);
            String key = requests.requireIdempotencyKey(headers);
            metadata.markNationalityKeyed();
            return new CreateNationalityCommand(context, id, code, request.isPrimary(),
                    nationalityRequests.period(request), key);
        }).flatMap(create::execute).invoke(metadata::recordNationalityDisposition)
                .map(NationalityMutationOutcome::nationality).map(mapper::toResponse)
                .map(body -> responses.customHttp(PartyResponseCode.CREATED, body))
                .onFailure(DomainValidationException.class).transform(ApplicationException::of)
                .onFailure().transform(errors::translate);
    }

    /** Lists exact count, bounded rows and signed navigation under one effective UTC date. */
    @GET
    @Path("/{partyId}/nationalities")
    @WithSpan("nationality.list")
    public Uni<RestResponse<ApiResponse<List<NationalityResponse>>>> listNationalities(
            @PathParam("partyId") String partyId, @Context UriInfo uri) {
        return Uni.createFrom().item(() -> NationalityQueryParameters.parse(metadata.metadata().tenantId(),
                        requests.parsePartyId(partyId), uri.getQueryParameters(), clock))
                .flatMap(list::execute)
                .map(page -> responses.paginatedHttp(mapper.toResponses(page), mapper.toPagination(page)))
                .onFailure().transform(errors::translate);
    }

    /** Retrieves ended, current or future nationality detail without country revalidation. */
    @GET
    @Path("/{partyId}/nationalities/{nationalityId}")
    @WithSpan("nationality.retrieve")
    public Uni<RestResponse<ApiResponse<NationalityResponse>>> getNationality(
            @PathParam("partyId") String partyId, @PathParam("nationalityId") String nationalityId) {
        return Uni.createFrom().item(() -> new GetNationalityQuery(metadata.metadata().tenantId(),
                        requests.parsePartyId(partyId), requests.parseNationalityId(nationalityId)))
                .flatMap(get::execute).map(mapper::toResponse).map(responses::successHttp)
                .onFailure().transform(errors::translate);
    }

    /** Applies only supplied validity bounds without any version-precondition or country lookup. */
    @PATCH
    @Path("/{partyId}/nationalities/{nationalityId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithSpan("nationality.patch")
    public Uni<RestResponse<ApiResponse<NationalityResponse>>> patchNationality(
            @PathParam("partyId") String partyId, @PathParam("nationalityId") String nationalityId,
            NationalityPatchRequest request) {
        return Uni.createFrom().item(() -> {
            var context = metadata.metadata();
            var body = nationalityRequests.patch(request);
            return new PatchNationalityCommand(context, requests.parsePartyId(partyId),
                    requests.parseNationalityId(nationalityId), body.validFrom(), body.validUntil());
        }).flatMap(patch::execute).map(NationalityMutationOutcome::nationality)
                .map(mapper::toResponse).map(responses::successHttp)
                .onFailure().transform(errors::translate);
    }

    /** Selects an effective primary with optional exact replay key and no request body. */
    @POST
    @Path("/{partyId}/nationalities/{nationalityId}/set-primary")
    @WithSpan("nationality.set-primary")
    public Uni<RestResponse<ApiResponse<NationalityResponse>>> setPrimary(
            @PathParam("partyId") String partyId, @PathParam("nationalityId") String nationalityId,
            @Context HttpHeaders headers) {
        return Uni.createFrom().deferred(() -> {
            var context = metadata.metadata();
            var party = requests.parsePartyId(partyId);
            var nationality = requests.parseNationalityId(nationalityId);
            var key = requests.optionalIdempotencyKey(headers);
            if (key.isPresent()) {
                metadata.markNationalityKeyed();
            }
            return setPrimary.execute(context, party, nationality, key);
        }).invoke(metadata::recordNationalityDisposition)
                .map(NationalityMutationOutcome::nationality).map(mapper::toResponse)
                .map(responses::successHttp).onFailure().transform(errors::translate);
    }
}
