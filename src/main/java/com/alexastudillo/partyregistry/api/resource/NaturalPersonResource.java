package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.mapper.NaturalPersonApiMapper;
import com.alexastudillo.partyregistry.api.mapper.PartyIdentifierApiMapper;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonPatchRequest;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonPutRequest;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonCreateResponse;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonResponse;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
import com.alexastudillo.partyregistry.application.command.GetNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.command.PatchNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.command.ReplaceNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.usecase.CreateNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.GetNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.ReplaceNaturalPersonUseCase;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

/**
 * Exposes reactive natural-person operations through the approved REST
 * contract.
 */
@Path("/v1/natural-person")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class NaturalPersonResource {

    private final CreateNaturalPersonUseCase createUseCase;
    private final GetNaturalPersonUseCase getUseCase;
    private final ReplaceNaturalPersonUseCase replaceUseCase;
    private final PatchNaturalPersonUseCase patchUseCase;
    private final RequestMetadataContext metadataContext;
    private final NaturalPersonApiMapper mapper;
    private final PartyIdentifierApiMapper identifierMapper;
    private final PartyApiErrorTranslator errorTranslator;
    private final ResponseManager responseManager;
    private final ApiRequestSupport requestSupport;

    @Inject
    public NaturalPersonResource(
            CreateNaturalPersonUseCase createUseCase,
            GetNaturalPersonUseCase getUseCase,
            ReplaceNaturalPersonUseCase replaceUseCase,
            PatchNaturalPersonUseCase patchUseCase,
            RequestMetadataContext metadataContext,
            NaturalPersonApiMapper mapper,
            PartyIdentifierApiMapper identifierMapper,
            PartyApiErrorTranslator errorTranslator,
            ResponseManager responseManager,
            ApiRequestSupport requestSupport) {
        this.createUseCase = createUseCase;
        this.getUseCase = getUseCase;
        this.replaceUseCase = replaceUseCase;
        this.patchUseCase = patchUseCase;
        this.metadataContext = metadataContext;
        this.mapper = mapper;
        this.identifierMapper = identifierMapper;
        this.errorTranslator = errorTranslator;
        this.responseManager = responseManager;
        this.requestSupport = requestSupport;
    }

    /**
     * Creates or replays a natural person under one tenant and idempotency key.
     *
     * @param request strict creation body
     * @param headers raw request headers used to enforce exact key cardinality
     * @return reactive `201 successful` envelope
     */
    @POST
    @WithSpan("natural-person.create")
    public Uni<RestResponse<ApiResponse<NaturalPersonCreateResponse>>> createNaturalPerson(
            NaturalPersonCreateRequest request,
            @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> createCommand(request, headers))
                .flatMap(createUseCase::execute)
                .invoke(result -> metadataContext.recordIdempotencyOutcome(result.outcome()))
                .map(mapper::toCreateResponse)
                .map(response -> responseManager.customHttp(PartyResponseCode.CREATED, response))
                .onFailure().transform(errorTranslator::translate);
    }

    /**
     * Retrieves one tenant-scoped natural person.
     *
     * @param partyId raw path identifier
     * @return reactive `200 successful` envelope
     */
    @GET
    @Path("/{partyId}")
    @WithSpan("natural-person.retrieve")
    public Uni<RestResponse<ApiResponse<NaturalPersonResponse>>> getNaturalPerson(
            @PathParam("partyId") String partyId) {
        return Uni.createFrom().item(() -> new GetNaturalPersonCommand(
                metadataContext.metadata(),
                requestSupport.parsePartyId(partyId)))
                .flatMap(getUseCase::execute)
                .map(mapper::toResponse)
                .map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    /**
     * Replaces every natural-person detail field using optimistic concurrency.
     *
     * @param partyId raw path identifier
     * @param request complete replacement body
     * @param headers raw request headers used to enforce exact precondition
     *                cardinality
     * @return reactive `200 successful` envelope
     */
    @PUT
    @Path("/{partyId}")
    @WithSpan("natural-person.replace")
    public Uni<RestResponse<ApiResponse<NaturalPersonResponse>>> replaceNaturalPerson(
            @PathParam("partyId") String partyId,
            NaturalPersonPutRequest request,
            @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> replaceCommand(partyId, request, headers))
                .flatMap(replaceUseCase::execute)
                .map(mapper::toResponse)
                .map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    /**
     * Applies only supplied natural-person detail fields using optimistic
     * concurrency.
     *
     * @param partyId raw path identifier
     * @param request presence-aware patch body
     * @param headers raw request headers used to enforce exact precondition
     *                cardinality
     * @return reactive `200 successful` envelope
     */
    @PATCH
    @Path("/{partyId}")
    @WithSpan("natural-person.patch")
    public Uni<RestResponse<ApiResponse<NaturalPersonResponse>>> patchNaturalPerson(
            @PathParam("partyId") String partyId,
            NaturalPersonPatchRequest request,
            @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> patchCommand(partyId, request, headers))
                .flatMap(patchUseCase::execute)
                .map(mapper::toResponse)
                .map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    private RegisterNaturalPersonCommand createCommand(
            NaturalPersonCreateRequest request,
            HttpHeaders headers) {
        NaturalPersonCreateRequest validRequest = requestSupport.validateBody(request);
        String idempotencyKey = requestSupport.requireIdempotencyKey(headers);
        return new RegisterNaturalPersonCommand(
                metadataContext.metadata(),
                idempotencyKey,
                validRequest.displayName(),
                validRequest.givenNames(),
                validRequest.familyNames(),
                validRequest.preferredName(),
                validRequest.birthDate(),
                validRequest.dateOfDeath(),
                validRequest.birthCountryCode(),
                identifierMapper.toInput(validRequest.initialIdentifier()));
    }

    private ReplaceNaturalPersonCommand replaceCommand(
            String partyId,
            NaturalPersonPutRequest request,
            HttpHeaders headers) {
        NaturalPersonPutRequest validRequest = requestSupport.validateBody(request);
        return new ReplaceNaturalPersonCommand(
                metadataContext.metadata(),
                requestSupport.parsePartyId(partyId),
                requestSupport.requireExpectedVersion(headers),
                validRequest.givenNames(),
                validRequest.familyNames(),
                validRequest.preferredName(),
                validRequest.birthDate(),
                validRequest.dateOfDeath(),
                validRequest.birthCountryCode());
    }

    private PatchNaturalPersonCommand patchCommand(
            String partyId,
            NaturalPersonPatchRequest request,
            HttpHeaders headers) {
        NaturalPersonPatchRequest validRequest = requestSupport.validateBody(request);
        return new PatchNaturalPersonCommand(
                metadataContext.metadata(),
                requestSupport.parsePartyId(partyId),
                requestSupport.requireExpectedVersion(headers),
                validRequest.toPatch());
    }
}
