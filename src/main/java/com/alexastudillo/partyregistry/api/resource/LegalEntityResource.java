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
import com.alexastudillo.partyregistry.api.model.response.LegalEntityCreateResponse;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
import com.alexastudillo.partyregistry.application.command.RegisterLegalEntityCommand;
import com.alexastudillo.partyregistry.application.usecase.CreateLegalEntityUseCase;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Exposes reactive legal-entity registration through the approved REST contract.
 */
@Path("/v1/legal-entity")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class LegalEntityResource {

    private final CreateLegalEntityUseCase createUseCase;
    private final RequestMetadataContext metadataContext;
    private final LegalEntityApiMapper mapper;
    private final PartyIdentifierApiMapper identifierMapper;
    private final PartyApiErrorTranslator errorTranslator;
    private final ResponseManager responseManager;
    private final ApiRequestSupport requestSupport;

    @Inject
    public LegalEntityResource(
            CreateLegalEntityUseCase createUseCase,
            RequestMetadataContext metadataContext,
            LegalEntityApiMapper mapper,
            PartyIdentifierApiMapper identifierMapper,
            PartyApiErrorTranslator errorTranslator,
            ResponseManager responseManager,
            ApiRequestSupport requestSupport) {
        this.createUseCase = createUseCase;
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
