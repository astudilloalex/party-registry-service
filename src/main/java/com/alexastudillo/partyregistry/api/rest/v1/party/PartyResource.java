package com.alexastudillo.partyregistry.api.rest.v1.party;

import com.alexastudillo.partyregistry.api.rest.v1.party.context.TrustedRequestContextExtractor;
import com.alexastudillo.partyregistry.api.rest.v1.party.dto.CreatePartyResponse;
import com.alexastudillo.partyregistry.api.rest.v1.party.error.PartyProblem;
import com.alexastudillo.partyregistry.api.rest.v1.party.mapper.PartyRequestMapper;
import com.alexastudillo.partyregistry.application.party.port.in.CreatePartyUseCase;
import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/internal/v1/parties")
@Consumes(MediaType.APPLICATION_JSON)
@Produces({MediaType.APPLICATION_JSON, PartyProblem.MEDIA_TYPE})
public class PartyResource {

    private final PartyRequestMapper requestMapper;
    private final TrustedRequestContextExtractor contextExtractor;
    private final CreatePartyUseCase createPartyUseCase;

    public PartyResource(
            PartyRequestMapper requestMapper,
            TrustedRequestContextExtractor contextExtractor,
            CreatePartyUseCase createPartyUseCase) {
        this.requestMapper = requestMapper;
        this.contextExtractor = contextExtractor;
        this.createPartyUseCase = createPartyUseCase;
    }

    @POST
    public Uni<Response> createParty(JsonNode request, @Context HttpHeaders headers) {
        var context = contextExtractor.extract(headers);
        var command = requestMapper.map(request);
        return createPartyUseCase.create(command, context)
                .map(result -> Response.status(Response.Status.CREATED)
                        .entity(new CreatePartyResponse(result.partyId(), result.version()))
                        .build());
    }
}
