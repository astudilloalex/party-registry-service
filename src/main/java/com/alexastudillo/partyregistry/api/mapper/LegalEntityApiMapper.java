package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.partyregistry.api.model.response.LegalEntityCreateResponse;
import com.alexastudillo.partyregistry.api.model.response.LegalEntityDetailsResponse;
import com.alexastudillo.partyregistry.api.model.response.LegalEntityResponse;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;

/**
 * Maps legal-entity results to their distinct creation and detail API representations.
 */
@ApplicationScoped
public class LegalEntityApiMapper {

    private final PartyIdentifierApiMapper identifierMapper;

    @Inject
    public LegalEntityApiMapper(PartyIdentifierApiMapper identifierMapper) {
        this.identifierMapper = Objects.requireNonNull(identifierMapper, "identifierMapper");
    }

    /**
     * Maps a legal-entity registration result to every declared create-response field.
     */
    public LegalEntityCreateResponse toCreateResponse(PartyRegistrationResult result) {
        Objects.requireNonNull(result, "result");
        if (!(result.party() instanceof LegalEntityResult legalEntity)) {
            throw new IllegalArgumentException("Legal-entity registration result required");
        }
        return new LegalEntityCreateResponse(
                legalEntity.partyId().value(),
                legalEntity.type().name(),
                legalEntity.displayName(),
                legalEntity.recordStatus().name(),
                legalEntity.version().value(),
                legalEntity.createdAt(),
                legalEntity.updatedAt(),
                legalEntity.createdBy(),
                legalEntity.updatedBy(),
                toDetails(legalEntity),
                identifierMapper.toInitialResponse(result.initialIdentifier()));
    }

    /** Maps current legal details without adding identifiers or normalizing historical text. */
    public LegalEntityResponse toResponse(LegalEntityResult result) {
        Objects.requireNonNull(result, "result");
        return new LegalEntityResponse(result.partyId().value(), result.type().name(), result.displayName(),
                result.recordStatus().name(), result.version().value(), result.createdAt(), result.updatedAt(),
                result.createdBy(), result.updatedBy(), toDetails(result));
    }

    LegalEntityDetailsResponse toDetails(LegalEntityResult result) {
        return new LegalEntityDetailsResponse(
                result.legalName(),
                result.tradeName(),
                result.legalFormCode(),
                result.incorporationCountryCode(),
                result.incorporatedOn(),
                result.dissolvedOn());
    }
}
