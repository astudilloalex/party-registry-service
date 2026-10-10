package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.mapper.IdentifierSearchApiMapper;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSearchExactRequest;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSearchPrefixRequest;
import com.alexastudillo.partyregistry.api.model.response.IdentifierSearchResponse;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
import com.alexastudillo.partyregistry.application.model.IdentifierSearchPage;
import com.alexastudillo.partyregistry.application.usecase.SearchPartiesByIdentifierUseCase;
import com.alexastudillo.partyregistry.application.usecase.SearchPartiesByIdentifierPrefixUseCase;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

/** Delivers strict tenant-qualified search requests through Application and explicitly wrapped safe shared JSON responses. */
@ApplicationScoped
@Path("/v1/parties/search")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class IdentifierSearchResource {
    private final SearchPartiesByIdentifierUseCase exact;
    private final SearchPartiesByIdentifierPrefixUseCase prefix;
    private final RequestMetadataContext metadata;
    private final IdentifierSearchApiMapper mapper;
    private final ApiRequestSupport requests;
    private final PartyApiErrorTranslator errors;
    private final ResponseManager responses;

    @Inject
    public IdentifierSearchResource(SearchPartiesByIdentifierUseCase exact, SearchPartiesByIdentifierPrefixUseCase prefix,
            RequestMetadataContext metadata, IdentifierSearchApiMapper mapper, ApiRequestSupport requests,
            PartyApiErrorTranslator errors, ResponseManager responses) {
        this.exact = exact;
        this.prefix = prefix;
        this.metadata = metadata;
        this.mapper = mapper;
        this.requests = requests;
        this.errors = errors;
        this.responses = responses;
    }

    /** Returns the complete exact match collection with explicit null cursors and matching HTTP/body status. */
    @POST
    @Path("/by-identifier")
    @WithSpan("party.search-by-identifier")
    public Uni<RestResponse<ApiResponse<List<IdentifierSearchResponse>>>> byIdentifier(IdentifierSearchExactRequest request, @Context UriInfo uri) {
        return Uni.createFrom().item(() -> {
            var accepted = metadata.metadata();
            var body = requests.validateBody(request);
            requireNoQuery(uri);
            return mapper.toQuery(accepted.tenantId(), body);
        }).flatMap(exact::execute).map(this::response).onFailure().transform(errors::translate);
    }

    /** Returns a safe bounded literal-prefix page with complete live counts and authenticated neighbor directions. */
    @POST
    @Path("/by-identifier-prefix")
    @WithSpan("party.search-by-identifier-prefix")
    public Uni<RestResponse<ApiResponse<List<IdentifierSearchResponse>>>> byPrefix(IdentifierSearchPrefixRequest request, @Context UriInfo uri) {
        return Uni.createFrom().item(() -> {
            var accepted = metadata.metadata();
            var body = requests.validateBody(request);
            requireNoQuery(uri);
            return mapper.toQuery(accepted.tenantId(), body);
        }).flatMap(prefix::execute).map(this::response).onFailure().transform(errors::translate);
    }

    private RestResponse<ApiResponse<List<IdentifierSearchResponse>>> response(IdentifierSearchPage page) {
        var data = mapper.toResponses(page);
        var pagination = mapper.toPagination(page);
        return responses.paginatedHttp(data, pagination);
    }

    private static void requireNoQuery(UriInfo uri) {
        if (!uri.getQueryParameters().isEmpty()) throw new ApiResponseException(PartyResponseCode.BAD_REQUEST);
    }
}
