package com.alexastudillo.partyregistry.architecture.fixture.responses.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.partyregistry.architecture.fixture.responses.api.model.response.SampleResponse;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

/** Declares valid JSON signatures and a protocol-native binary exception for architecture checks. */
@Produces(MediaType.APPLICATION_JSON)
public abstract class ValidResponses {

    @GET
    public abstract Uni<RestResponse<ApiResponse<SampleResponse>>> item();

    @GET
    public abstract Uni<RestResponse<ApiResponse<List<SampleResponse>>>> collection();

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public abstract Multi<byte[]> binary();

    public abstract String internalHelper();
}
