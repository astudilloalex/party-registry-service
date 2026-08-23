package com.alexastudillo.partyregistry.infrastructure.integration.geographic.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestResponse;

import com.alexastudillo.partyregistry.infrastructure.integration.geographic.dto.CountryResponse;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/countries/by-alpha2")
@RegisterRestClient(configKey = "geographic-reference")
@Produces(MediaType.APPLICATION_JSON)
public interface GeographicReferenceClient {

    @GET
    @Path("/{alpha2Code}")
    Uni<RestResponse<CountryResponse>> getCountryByAlpha2(
            @PathParam("alpha2Code") String alpha2Code,
            @HeaderParam("Tenant-Id") String tenantId,
            @HeaderParam("User-Id") String userId,
            @HeaderParam("Process-Id") String processId);
}
