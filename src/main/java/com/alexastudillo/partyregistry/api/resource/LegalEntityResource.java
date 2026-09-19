package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.contract.CommonResponseCode;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.mapper.LegalEntityApiMapper;
import com.alexastudillo.partyregistry.api.mapper.PartyIdentifierApiMapper;
import com.alexastudillo.partyregistry.api.model.request.LegalEntityCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.LegalEntityPutRequest;
import com.alexastudillo.partyregistry.api.model.request.LegalEntityPatchRequest;
import com.alexastudillo.partyregistry.api.model.response.LegalEntityCreateResponse;
import com.alexastudillo.partyregistry.api.model.response.LegalEntityResponse;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
import com.alexastudillo.partyregistry.application.command.RegisterLegalEntityCommand;
import com.alexastudillo.partyregistry.application.command.GetLegalEntityCommand;
import com.alexastudillo.partyregistry.application.command.ReplaceLegalEntityCommand;
import com.alexastudillo.partyregistry.application.command.PatchLegalEntityCommand;
import com.alexastudillo.partyregistry.application.usecase.CreateLegalEntityUseCase;
import com.alexastudillo.partyregistry.application.usecase.GetLegalEntityUseCase;
import com.alexastudillo.partyregistry.application.usecase.ReplaceLegalEntityUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchLegalEntityUseCase;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Exposes legal-entity registration and tenant-qualified reactive detail operations.
 */
@Path("/v1/legal-entity")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class LegalEntityResource {

    private final CreateLegalEntityUseCase createUseCase;
    private final GetLegalEntityUseCase getUseCase;
    private final ReplaceLegalEntityUseCase replaceUseCase;
    private final PatchLegalEntityUseCase patchUseCase;
    private final RequestMetadataContext metadataContext;
    private final LegalEntityApiMapper mapper;
    private final PartyIdentifierApiMapper identifierMapper;
    private final PartyApiErrorTranslator errorTranslator;
    private final ResponseManager responseManager;
    private final ApiRequestSupport requestSupport;

    @Inject
    public LegalEntityResource(
            CreateLegalEntityUseCase createUseCase,
            GetLegalEntityUseCase getUseCase,
            ReplaceLegalEntityUseCase replaceUseCase,
            PatchLegalEntityUseCase patchUseCase,
            RequestMetadataContext metadataContext,
            LegalEntityApiMapper mapper,
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
     * Creates or replays a legal entity and its required initial identifier.
     *
     * @param request strict creation body
     * @param headers raw request headers used to enforce exact idempotency-key cardinality
     * @return reactive {@code 201 successful} envelope
     */
    @POST
    @WithSpan("legal-entity.create")
    public Uni<RestResponse<ApiResponse<LegalEntityCreateResponse>>> createLegalEntity(
            LegalEntityCreateRequest request,
            @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> createCommand(request, headers))
                .flatMap(createUseCase::execute)
                .invoke(result -> metadataContext.recordIdempotencyOutcome(result.outcome()))
                .map(mapper::toCreateResponse)
                .map(response -> responseManager.customHttp(PartyResponseCode.CREATED, response))
                .onFailure().transform(errorTranslator::translate);
    }

    /**
     * Retrieves current legal details without creation-only identifier data.
     *
     * @param partyId canonical Party identifier for the requesting tenant
     * @return the current legal-detail envelope or a cause-specific error
     */
    @GET
    @Path("/{partyId}")
    @WithSpan("legal-entity.retrieve")
    public Uni<RestResponse<ApiResponse<LegalEntityResponse>>> getLegalEntity(@PathParam("partyId") String partyId) {
        return Uni.createFrom().item(() -> new GetLegalEntityCommand(
                metadataContext.metadata(), requestSupport.parsePartyId(partyId)))
                .flatMap(getUseCase::execute)
                .map(mapper::toResponse)
                .map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    /**
     * Replaces legal details using the current Party version supplied in If-Match.
     *
     * @param partyId canonical Party identifier
     * @param request complete replacement body
     * @param headers raw headers used to enforce exact version cardinality
     * @return the accepted representation and version, or a cause-specific error
     */
    @PUT
    @Path("/{partyId}")
    @WithSpan("legal-entity.replace")
    public Uni<RestResponse<ApiResponse<LegalEntityResponse>>> replaceLegalEntity(
            @PathParam("partyId") String partyId, LegalEntityPutRequest request, @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> replaceCommand(partyId, request, headers))
                .flatMap(replaceUseCase::execute)
                .map(mapper::toResponse)
                .map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    /**
     * Applies only supplied legal fields using the current Party version.
     *
     * @param partyId canonical Party identifier
     * @param request presence-aware partial update body
     * @param headers raw headers used to enforce exact version cardinality
     * @return the accepted representation and version, or a cause-specific error
     */
    @PATCH
    @Path("/{partyId}")
    @WithSpan("legal-entity.patch")
    public Uni<RestResponse<ApiResponse<LegalEntityResponse>>> patchLegalEntity(
            @PathParam("partyId") String partyId, LegalEntityPatchRequest request, @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> patchCommand(partyId, request, headers))
                .flatMap(patchUseCase::execute)
                .map(mapper::toResponse)
                .map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    private ReplaceLegalEntityCommand replaceCommand(
            String partyId, LegalEntityPutRequest request, HttpHeaders headers) {
        LegalEntityPutRequest original = requestSupport.validateBody(request, LegalEntityPutRequest::normalizedForValidation);
        return new ReplaceLegalEntityCommand(metadataContext.metadata(), requestSupport.parsePartyId(partyId),
                requestSupport.requireExpectedVersion(headers), original.legalName(), original.tradeName(),
                original.legalFormCode(), original.incorporationCountryCode(), original.incorporatedOn(), original.dissolvedOn());
    }

    private PatchLegalEntityCommand patchCommand(String partyId, LegalEntityPatchRequest request, HttpHeaders headers) {
        LegalEntityPatchRequest original = requestSupport.validateBody(request, LegalEntityPatchRequest::normalizedForValidation);
        return new PatchLegalEntityCommand(metadataContext.metadata(), requestSupport.parsePartyId(partyId),
                requestSupport.requireExpectedVersion(headers), original.toPatch());
    }

    /**
     * Routes strict JSON binding failures through the shared bad-request envelope.
     *
     * @param ignoredFailure Jackson request binding failure
     * @return standard {@code 400 bad-request} response
     */
    @ServerExceptionMapper
    public RestResponse<ApiResponse<Void>> mapInvalidJson(MismatchedInputException ignoredFailure) {
        return responseManager.errorHttp(CommonResponseCode.BAD_REQUEST);
    }

    private RegisterLegalEntityCommand createCommand(
            LegalEntityCreateRequest request,
            HttpHeaders headers) {
        LegalEntityCreateRequest original = requestSupport.validateBody(
                request, LegalEntityCreateRequest::normalizedForValidation);
        // Idempotency compares original input, not the normalized validation copy.
        return new RegisterLegalEntityCommand(
                metadataContext.metadata(),
                requestSupport.requireIdempotencyKey(headers),
                original.displayName(),
                original.legalName(),
                original.tradeName(),
                original.legalFormCode(),
                original.incorporationCountryCode(),
                original.incorporatedOn(),
                original.dissolvedOn(),
                identifierMapper.toInput(original.initialIdentifier()));
    }
}
