package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.partyregistry.api.model.request.InitialPartyIdentifierCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierCreateRequest;
import com.alexastudillo.partyregistry.api.model.response.InitialPartyIdentifierResponse;
import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;

/**
 * Maps identifier API inputs and safe application projections across the HTTP boundary.
 */
@ApplicationScoped
public class PartyIdentifierApiMapper {

    /**
     * Maps the initial identifier request to its transport-neutral application input.
     */
    public InitialPartyIdentifierInput toInput(InitialPartyIdentifierCreateRequest request) {
        Objects.requireNonNull(request, "request");
        return new InitialPartyIdentifierInput(
                request.identifierSchemeCode(),
                request.value(),
                request.issuerCode(),
                request.issuedOn(),
                request.expiresOn(),
                request.isPrimary());
    }

    /**
     * Maps an additional identifier request to its transport-neutral application input.
     */
    public InitialPartyIdentifierInput toInput(PartyIdentifierCreateRequest request) {
        Objects.requireNonNull(request, "request");
        return new InitialPartyIdentifierInput(
                request.identifierSchemeCode(),
                request.value(),
                request.issuerCode(),
                request.issuedOn(),
                request.expiresOn(),
                request.isPrimary());
    }

    /**
     * Maps every declared safe identifier field to the ordinary response schema.
     */
    public PartyIdentifierResponse toResponse(PartyIdentifierResult result) {
        Objects.requireNonNull(result, "result");
        return new PartyIdentifierResponse(
                result.identifierId().value(),
                result.partyId().value(),
                result.identifierSchemeId().value(),
                result.schemeCode(),
                result.maskedValue(),
                result.status().name(),
                result.isPrimary(),
                result.issuerCode(),
                result.issuedOn(),
                result.expiresOn(),
                result.verifiedAt(),
                result.verifiedBy(),
                result.version().value(),
                result.createdAt(),
                result.updatedAt());
    }

    /**
     * Maps and verifies the identifier projection used only by Party creation responses.
     */
    public InitialPartyIdentifierResponse toInitialResponse(PartyIdentifierResult result) {
        Objects.requireNonNull(result, "result");
        if (result.status() != PartyIdentifierStatus.PENDING_VERIFICATION) {
            throw new IllegalArgumentException("Initial identifier must be pending verification");
        }
        return new InitialPartyIdentifierResponse(
                result.identifierId().value(),
                result.partyId().value(),
                result.identifierSchemeId().value(),
                result.schemeCode(),
                result.maskedValue(),
                result.status().name(),
                result.isPrimary(),
                result.issuerCode(),
                result.issuedOn(),
                result.expiresOn(),
                result.verifiedAt(),
                result.verifiedBy(),
                result.version().value(),
                result.createdAt(),
                result.updatedAt());
    }
}
