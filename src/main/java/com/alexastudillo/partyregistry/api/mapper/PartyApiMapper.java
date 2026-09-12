package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.partyregistry.api.model.response.LegalEntityDetailsResponse;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonDetailsResponse;
import com.alexastudillo.partyregistry.api.model.response.PartyDetailResponse;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;

/**
 * Exhaustively maps each Party result subtype to the shared detail response.
 */
@ApplicationScoped
public class PartyApiMapper {

    /**
     * Maps all common and matching type-specific fields without a fallback branch.
     */
    public PartyDetailResponse toResponse(PartyDetailsResult result) {
        Objects.requireNonNull(result, "result");
        return switch (result) {
            case NaturalPersonResult naturalPerson -> naturalPersonResponse(naturalPerson);
            case LegalEntityResult legalEntity -> legalEntityResponse(legalEntity);
        };
    }

    private static PartyDetailResponse naturalPersonResponse(NaturalPersonResult result) {
        return commonResponse(
                result,
                PartyType.NATURAL_PERSON,
                new NaturalPersonDetailsResponse(
                        result.givenNames(),
                        result.familyNames(),
                        result.preferredName(),
                        result.birthDate(),
                        result.dateOfDeath(),
                        result.birthCountryCode()),
                null);
    }

    private static PartyDetailResponse legalEntityResponse(LegalEntityResult result) {
        return commonResponse(
                result,
                PartyType.LEGAL_ENTITY,
                null,
                new LegalEntityDetailsResponse(
                        result.legalName(),
                        result.tradeName(),
                        result.legalFormCode(),
                        result.incorporationCountryCode(),
                        result.incorporatedOn(),
                        result.dissolvedOn()));
    }

    private static PartyDetailResponse commonResponse(
            PartyDetailsResult result,
            PartyType partyType,
            NaturalPersonDetailsResponse naturalPersonDetails,
            LegalEntityDetailsResponse legalEntityDetails) {
        return new PartyDetailResponse(
                result.partyId().value(),
                partyType.name(),
                result.displayName(),
                result.recordStatus().name(),
                result.version().value(),
                result.createdAt(),
                result.updatedAt(),
                result.createdBy(),
                result.updatedBy(),
                naturalPersonDetails,
                legalEntityDetails);
    }
}
