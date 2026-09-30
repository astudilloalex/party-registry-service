package com.alexastudillo.partyregistry.infrastructure.integration.geographic.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Reactive HTTP client for country lookups in the Geographic Reference
 * Service.
 */
@Path("/api/v1/countries/by-alpha2")
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "geographic-reference")
public interface GeographicReferenceClient {

    /**
     * Retrieves a country using its ISO 3166-1 alpha-2 code.
     *
     * @param tenantId   trusted tenant identifier
     * @param userId     trusted audit subject
     * @param processId  trusted process identifier
     * @param alpha2Code uppercase ISO alpha-2 code
     * @return the complete non-blocking remote response
     */
    @GET
    @Path("/{alpha2Code}")
    Uni<Response> findByAlpha2Code(
            @HeaderParam("Tenant-Id") String tenantId,
            @HeaderParam("User-Id") String userId,
            @HeaderParam("Process-Id") String processId,
            @PathParam("alpha2Code") String alpha2Code);
}
