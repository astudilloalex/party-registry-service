package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.api.response.contract.PaginationMetadata;
import com.alexastudillo.partyregistry.api.model.response.LegalEntityDetailsResponse;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonDetailsResponse;
import com.alexastudillo.partyregistry.api.model.response.PartyDetailResponse;
import com.alexastudillo.partyregistry.api.model.response.PartySummaryResponse;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyPageResult;
import com.alexastudillo.partyregistry.application.model.PartySummaryResult;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;
import java.util.List;

/**
 * Maps safe Party detail/summary results to API DTOs and checks shared pagination capacity.
 */
@ApplicationScoped
public class PartyApiMapper {

    /** Projects collection data to API DTOs before the resource constructs the shared envelope. */
    public List<PartySummaryResponse> toSummaryResponses(PartyPageResult page) {
        Objects.requireNonNull(page, "page");
        return page.items().stream().map(PartyApiMapper::summary).toList();
    }

    /** Converts exact long totals or fails explicitly when the shared Integer count capacity is exceeded. */
    public PaginationMetadata toPaginationMetadata(PartyPageResult page) {
        Objects.requireNonNull(page, "page");
        return new PaginationMetadata(page.nextCursor(), page.prevCursor(), Math.toIntExact(page.totalElements()),
                Math.toIntExact(page.totalPages()), page.numberOfElements());
    }

    private static PartySummaryResponse summary(PartySummaryResult result) {
        return new PartySummaryResponse(result.partyId().value(), result.type().name(), result.displayName(),
                result.recordStatus().name(), result.createdAt(), result.version().value());
    }

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
