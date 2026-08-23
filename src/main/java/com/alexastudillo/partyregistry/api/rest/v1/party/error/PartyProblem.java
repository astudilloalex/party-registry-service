package com.alexastudillo.partyregistry.api.rest.v1.party.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartyProblem(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        String traceId,
        List<Violation> violations) {

    public static final String MEDIA_TYPE = "application/problem+json";

    public static PartyProblem creationFailure() {
        return new PartyProblem(
                "urn:party-registry:problem:creation-failure",
                "Party creation failed",
                500,
                "Party creation did not complete.",
                null,
                "CREATION_FAILURE",
                null,
                null);
    }

    public static PartyProblem invalidPartyData(String path) {
        return new PartyProblem(
                "urn:party-registry:problem:invalid-party-data",
                "Invalid Party data",
                422,
                "The Party data violates one or more creation rules.",
                null,
                "INVALID_PARTY_DATA",
                null,
                List.of(new Violation("UNKNOWN_FIELD", "body", path)));
    }
}
