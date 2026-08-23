package com.alexastudillo.partyregistry.api.rest.v1.party.error;

import com.alexastudillo.partyregistry.api.rest.v1.party.mapper.PartyRequestMapper.InvalidPartyRequestException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class PartyExceptionMapper implements ExceptionMapper<InvalidPartyRequestException> {

    @Override
    public Response toResponse(InvalidPartyRequestException exception) {
        PartyProblem problem = PartyProblem.invalidPartyData(exception.path());
        return Response.status(problem.status())
                .type(PartyProblem.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
