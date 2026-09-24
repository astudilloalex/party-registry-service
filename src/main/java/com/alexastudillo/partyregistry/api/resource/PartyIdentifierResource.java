package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.contract.CommonResponseCode;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.mapper.PartyIdentifierApiMapper;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierCreateRequest;
import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
import com.alexastudillo.partyregistry.application.command.RegisterPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.usecase.RegisterPartyIdentifierUseCase;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
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
 * Exposes independent official-identifier registration for existing Parties.
 */
@Path("/v1/parties/{partyId}/identifiers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierResource {

    private final RegisterPartyIdentifierUseCase registerUseCase;
    private final RequestMetadataContext metadataContext;
    private final PartyIdentifierApiMapper mapper;
    private final PartyApiErrorTranslator errorTranslator;
    private final ResponseManager responseManager;
    private final ApiRequestSupport requestSupport;

    @Inject
    public PartyIdentifierResource(
            RegisterPartyIdentifierUseCase registerUseCase,
            RequestMetadataContext metadataContext,
            PartyIdentifierApiMapper mapper,
            PartyApiErrorTranslator errorTranslator,
            ResponseManager responseManager,
            ApiRequestSupport requestSupport) {
        this.registerUseCase = registerUseCase;
        this.metadataContext = metadataContext;
        this.mapper = mapper;
        this.errorTranslator = errorTranslator;
        this.responseManager = responseManager;
        this.requestSupport = requestSupport;
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
        return Uni.createFrom().item(() -> command(partyId, request, headers))
                .flatMap(registerUseCase::execute)
                .map(mapper::toResponse)
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

    private RegisterPartyIdentifierCommand command(
            String partyId,
            PartyIdentifierCreateRequest request,
            HttpHeaders headers) {
        PartyIdentifierCreateRequest validRequest = requestSupport.validateBody(request);
        return new RegisterPartyIdentifierCommand(
                metadataContext.metadata(),
                requestSupport.requireIdempotencyKey(headers),
                requestSupport.parsePartyId(partyId),
                mapper.toInput(validRequest));
    }
}
