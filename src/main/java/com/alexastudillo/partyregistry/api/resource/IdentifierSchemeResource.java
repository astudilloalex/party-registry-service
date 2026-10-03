package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.mapper.IdentifierSchemeApiMapper;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemeCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemePatchRequest;
import com.alexastudillo.partyregistry.api.model.response.IdentifierSchemeResponse;
import com.alexastudillo.partyregistry.api.support.IdentifierSchemeRequestSupport;
import com.alexastudillo.partyregistry.api.support.IdentifierSchemeRequestParameters;
import com.alexastudillo.partyregistry.api.support.IdentifierSchemeQueryParameters;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSelector;
import com.alexastudillo.partyregistry.application.query.GetIdentifierSchemeQuery;
import com.alexastudillo.partyregistry.application.usecase.CreateIdentifierSchemeUseCase;
import com.alexastudillo.partyregistry.application.usecase.GetIdentifierSchemeUseCase;
import com.alexastudillo.partyregistry.application.usecase.ListIdentifierSchemesUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchIdentifierSchemeUseCase;
import com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCase;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
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

/** Delivers global scheme workflows through strict API inputs, safe DTO mapping, and explicit shared envelopes. */
@Path("/v1/identifier-schemes")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class IdentifierSchemeResource {
    private final CreateIdentifierSchemeUseCase createUseCase;
    private final GetIdentifierSchemeUseCase getUseCase;
    private final ListIdentifierSchemesUseCase listUseCase;
    private final PatchIdentifierSchemeUseCase patchUseCase;
    private final ChangeIdentifierSchemeLifecycleUseCase lifecycleUseCase;
    private final IdentifierSchemeRequestParameters parameters;
    private final RequestMetadataContext metadataContext;
    private final IdentifierSchemeRequestSupport requestSupport;
    private final IdentifierSchemeApiMapper mapper;
    private final PartyApiErrorTranslator errorTranslator;
    private final ResponseManager responseManager;

    @Inject
    public IdentifierSchemeResource(CreateIdentifierSchemeUseCase createUseCase, GetIdentifierSchemeUseCase getUseCase,
            ListIdentifierSchemesUseCase listUseCase, PatchIdentifierSchemeUseCase patchUseCase,
            ChangeIdentifierSchemeLifecycleUseCase lifecycleUseCase,
            IdentifierSchemeRequestParameters parameters, RequestMetadataContext metadataContext,
            IdentifierSchemeRequestSupport requestSupport, IdentifierSchemeApiMapper mapper,
            PartyApiErrorTranslator errorTranslator, ResponseManager responseManager) {
        this.createUseCase = createUseCase;
        this.getUseCase = getUseCase;
        this.listUseCase = listUseCase;
        this.patchUseCase = patchUseCase;
        this.lifecycleUseCase = lifecycleUseCase;
        this.parameters = parameters;
        this.metadataContext = metadataContext;
        this.requestSupport = requestSupport;
        this.mapper = mapper;
        this.errorTranslator = errorTranslator;
        this.responseManager = responseManager;
    }

    /** Creates a draft or returns its historical equivalent replay with the same 201 contract. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @WithSpan("identifier-scheme.create")
    public Uni<RestResponse<ApiResponse<IdentifierSchemeResponse>>> createIdentifierScheme(
            IdentifierSchemeCreateRequest request, @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> requestSupport.createCommand(metadataContext.metadata(), request, headers))
                .flatMap(createUseCase::execute)
                .invoke(metadataContext::recordIdentifierSchemeDisposition)
                .map(IdentifierSchemeMutationOutcome::scheme)
                .map(mapper::toResponse)
                .map(dto -> responseManager.customHttp(PartyResponseCode.CREATED, dto))
                .onFailure().transform(errorTranslator::translate);
    }

    /** Retrieves the safe current representation in any lifecycle state by canonical global identity. */
    @GET
    @Path("/{schemeId}")
    @WithSpan("identifier-scheme.retrieve")
    public Uni<RestResponse<ApiResponse<IdentifierSchemeResponse>>> getIdentifierScheme(@PathParam("schemeId") String schemeId) {
        return Uni.createFrom().item(() -> new GetIdentifierSchemeQuery(metadataContext.metadata(),
                        new IdentifierSchemeSelector.ById(requestSupport.schemeId(schemeId))))
                .flatMap(getUseCase::execute).map(mapper::toResponse).map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    /** Retrieves by the exact decoded code, retaining case, padding, and Unicode code-point limits. */
    @GET
    @Path("/by-code/{code}")
    @WithSpan("identifier-scheme.retrieve-by-code")
    public Uni<RestResponse<ApiResponse<IdentifierSchemeResponse>>> getIdentifierSchemeByCode(@PathParam("code") String code) {
        return Uni.createFrom().item(() -> new GetIdentifierSchemeQuery(metadataContext.metadata(),
                        new IdentifierSchemeSelector.ByCode(parameters.code(code))))
                .flatMap(getUseCase::execute).map(mapper::toResponse).map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    /** Lists exact global matches through the complete decoded query and available count-only navigation. */
    @GET
    @WithSpan("identifier-scheme.list")
    public Uni<RestResponse<ApiResponse<List<IdentifierSchemeResponse>>>> listIdentifierSchemes(@Context UriInfo uri) {
        return Uni.createFrom().item(() -> IdentifierSchemeQueryParameters.parse(metadataContext.metadata(), uri.getQueryParameters()))
                .flatMap(listUseCase::execute)
                .map(page -> responseManager.paginatedHttp(mapper.toResponses(page), mapper.toPagination(page)))
                .onFailure().transform(errorTranslator::translate);
    }

    /** Validates presence-aware body, identity, and precondition before Application-owned maintenance decisions. */
    @PATCH
    @Path("/{schemeId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithSpan("identifier-scheme.patch")
    public Uni<RestResponse<ApiResponse<IdentifierSchemeResponse>>> patchIdentifierScheme(
            @PathParam("schemeId") String schemeId, IdentifierSchemePatchRequest request, @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> requestSupport.patchCommand(metadataContext.metadata(), request, schemeId, headers))
                .flatMap(patchUseCase::execute).invoke(metadataContext::recordIdentifierSchemeDisposition)
                .map(IdentifierSchemeMutationOutcome::scheme)
                .map(mapper::toResponse).map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    /** Activates an eligible draft or returns the original equivalent keyed acceptance. */
    @POST
    @Path("/{schemeId}/activate")
    @WithSpan("identifier-scheme.activate")
    public Uni<RestResponse<ApiResponse<IdentifierSchemeResponse>>> activateIdentifierScheme(
            @PathParam("schemeId") String schemeId, byte[] body, @Context HttpHeaders headers) {
        return lifecycle(schemeId, body, headers, IdentifierSchemeLifecycleAction.ACTIVATE);
    }

    /** Withdraws an active scheme without readmitting historical processing configuration. */
    @POST
    @Path("/{schemeId}/deprecate")
    @WithSpan("identifier-scheme.deprecate")
    public Uni<RestResponse<ApiResponse<IdentifierSchemeResponse>>> deprecateIdentifierScheme(
            @PathParam("schemeId") String schemeId, byte[] body, @Context HttpHeaders headers) {
        return lifecycle(schemeId, body, headers, IdentifierSchemeLifecycleAction.DEPRECATE);
    }

    /** Retires any nonterminal scheme while retaining its code and original replay history. */
    @POST
    @Path("/{schemeId}/retire")
    @WithSpan("identifier-scheme.retire")
    public Uni<RestResponse<ApiResponse<IdentifierSchemeResponse>>> retireIdentifierScheme(
            @PathParam("schemeId") String schemeId, byte[] body, @Context HttpHeaders headers) {
        return lifecycle(schemeId, body, headers, IdentifierSchemeLifecycleAction.RETIRE);
    }

    private Uni<RestResponse<ApiResponse<IdentifierSchemeResponse>>> lifecycle(
            String schemeId, byte[] body, HttpHeaders headers, IdentifierSchemeLifecycleAction action) {
        return Uni.createFrom().item(() -> requestSupport.lifecycleCommand(metadataContext.metadata(), body, schemeId, action, headers))
                .flatMap(lifecycleUseCase::execute).invoke(metadataContext::recordIdentifierSchemeDisposition)
                .map(IdentifierSchemeMutationOutcome::scheme)
                .map(mapper::toResponse).map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }
}
