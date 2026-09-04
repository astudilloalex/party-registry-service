package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.partyregistry.api.model.response.NaturalPersonDetailsResponse;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonResponse;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;

/**
 * Maps transport-neutral application results to natural-person API DTOs.
 */
@ApplicationScoped
public class NaturalPersonApiMapper {

    private static final String NATURAL_PERSON_TYPE = "NATURAL_PERSON";

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
}
