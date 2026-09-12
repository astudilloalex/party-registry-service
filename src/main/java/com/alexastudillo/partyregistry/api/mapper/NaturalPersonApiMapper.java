package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.partyregistry.api.model.response.NaturalPersonDetailsResponse;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonCreateResponse;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonResponse;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;

/**
 * Maps transport-neutral application results to natural-person API DTOs.
 */
@ApplicationScoped
public class NaturalPersonApiMapper {

    private static final String NATURAL_PERSON_TYPE = "NATURAL_PERSON";

    private final PartyIdentifierApiMapper identifierMapper;

    @Inject
    public NaturalPersonApiMapper(PartyIdentifierApiMapper identifierMapper) {
        this.identifierMapper = Objects.requireNonNull(identifierMapper, "identifierMapper");
    }

    /**
     * Maps every application result field while fixing the public party type.
     *
     * @param result application result
     * @return API-only natural-person representation
     */
    public NaturalPersonResponse toResponse(NaturalPersonResult result) {
        Objects.requireNonNull(result, "result");
        NaturalPersonDetailsResponse details = new NaturalPersonDetailsResponse(
                result.givenNames(),
                result.familyNames(),
                result.preferredName(),
                result.birthDate(),
                result.dateOfDeath(),
                result.birthCountryCode());
        return new NaturalPersonResponse(
                result.partyId().value(),
                NATURAL_PERSON_TYPE,
                result.displayName(),
                result.recordStatus().name(),
                result.version().value(),
                result.createdAt(),
                result.updatedAt(),
                result.createdBy(),
                result.updatedBy(),
                details);
    }

    /**
     * Maps a natural-person registration result to its create-only response.
     *
     * @param result safe application registration result
     * @return API-only natural-person creation representation
     */
    public NaturalPersonCreateResponse toCreateResponse(PartyRegistrationResult result) {
        Objects.requireNonNull(result, "result");
        if (!(result.party() instanceof NaturalPersonResult naturalPerson)) {
            throw new IllegalArgumentException("Natural-person registration result required");
        }
        NaturalPersonResponse party = toResponse(naturalPerson);
        return new NaturalPersonCreateResponse(
                party.partyId(),
                party.type(),
                party.displayName(),
                party.recordStatus(),
                party.version(),
                party.createdAt(),
                party.updatedAt(),
                party.createdBy(),
                party.updatedBy(),
                party.naturalPersonDetails(),
                identifierMapper.toInitialResponse(result.initialIdentifier()));
    }
}
