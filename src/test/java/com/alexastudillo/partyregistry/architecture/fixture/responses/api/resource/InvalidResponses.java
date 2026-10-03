package com.alexastudillo.partyregistry.architecture.fixture.responses.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.partyregistry.architecture.fixture.responses.api.model.response.SampleResponse;
import com.alexastudillo.partyregistry.architecture.fixture.responses.domain.SampleDomain;
import com.alexastudillo.partyregistry.architecture.fixture.responses.infrastructure.SampleEntity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

/** Declares deliberately invalid signatures without registering executable REST routes. */
@Produces(MediaType.APPLICATION_JSON)
public abstract class InvalidResponses {

    @GET
    public abstract Uni<SampleDomain> bareDomain();

    @GET
    public abstract Uni<SampleResponse> bareDto();

    @GET
    public abstract RestResponse<SampleResponse> unwrappedRest();

    @GET
    public abstract Response rawResponse();

    @GET
    public abstract Uni<RestResponse<ApiResponse<SampleDomain>>> wrappedDomain();

    @GET
    public abstract Uni<RestResponse<ApiResponse<SampleEntity>>> wrappedEntity();

    @GET
    public abstract Uni<RestResponse<ApiResponse<List<SampleDomain>>>> domainCollection();

    @GET
    public abstract Uni<RestResponse<ApiResponse<?>>> unknownPayload();
}
