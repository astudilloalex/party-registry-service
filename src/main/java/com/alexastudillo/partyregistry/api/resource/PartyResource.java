package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.mapper.PartyApiMapper;
import com.alexastudillo.partyregistry.api.model.response.PartyDetailResponse;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
import com.alexastudillo.partyregistry.application.command.ActivatePartyCommand;
import com.alexastudillo.partyregistry.application.usecase.ActivatePartyUseCase;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

/**
 * Exposes common reactive Party lifecycle operations.
 */
@Path("/v1/parties/{partyId}")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyResource {

    private final ActivatePartyUseCase activateUseCase;
    private final RequestMetadataContext metadataContext;
    private final PartyApiMapper mapper;
    private final PartyApiErrorTranslator errorTranslator;
    private final ResponseManager responseManager;
    private final ApiRequestSupport requestSupport;

    @Inject
    public PartyResource(
            ActivatePartyUseCase activateUseCase,
            RequestMetadataContext metadataContext,
            PartyApiMapper mapper,
            PartyApiErrorTranslator errorTranslator,
            ResponseManager responseManager,
            ApiRequestSupport requestSupport) {
        this.activateUseCase = activateUseCase;
        this.metadataContext = metadataContext;
        this.mapper = mapper;
        this.errorTranslator = errorTranslator;
        this.responseManager = responseManager;
        this.requestSupport = requestSupport;
    }

    /**
     * Activates one tenant-owned Party at exactly the requested aggregate version.
     *
     * @param partyId raw path identifier
     * @param headers raw request headers used to enforce exact precondition cardinality
     * @return reactive {@code 200 successful} envelope
     */
    @POST
    @Path("/activate")
    @WithSpan("party.activate")
    public Uni<RestResponse<ApiResponse<PartyDetailResponse>>> activateParty(
            @PathParam("partyId") String partyId,
            @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> command(partyId, headers))
                .flatMap(activateUseCase::execute)
                .map(mapper::toResponse)
                .map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    private ActivatePartyCommand command(String partyId, HttpHeaders headers) {
        requestSupport.optionalIdempotencyKey(headers);
        return new ActivatePartyCommand(
                metadataContext.metadata(),
                requestSupport.parsePartyId(partyId),
                requestSupport.requireExpectedVersion(headers));
    }
}
