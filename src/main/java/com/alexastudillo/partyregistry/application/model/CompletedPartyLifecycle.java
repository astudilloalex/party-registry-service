package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;

import java.util.Objects;

/**
 * Retains a successful effective request and its original, internally consistent safe Party result.
 */
public record CompletedPartyLifecycle(PartyLifecycleRequest request, PartyDetailsResult result) {

    public CompletedPartyLifecycle {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(result, "result");
        if (!request.partyId().equals(result.partyId()) || !request.expectedVersion().next().equals(result.version())) {
            throw new IllegalArgumentException("Completed lifecycle identity or version is inconsistent");
        }
        PartyRecordStatus acceptedStatus = switch (request.action()) {
            case ACTIVATE -> PartyRecordStatus.ACTIVE;
            case DEACTIVATE -> PartyRecordStatus.INACTIVE;
            case ARCHIVE -> PartyRecordStatus.ARCHIVED;
        };
        PartyType resultType = switch (result) {
            case NaturalPersonResult _ -> PartyType.NATURAL_PERSON;
            case LegalEntityResult _ -> PartyType.LEGAL_ENTITY;
        };
        if (result.recordStatus() != acceptedStatus || result.type() != resultType) {
            throw new IllegalArgumentException("Completed lifecycle action or subtype is inconsistent");
        }
    }
}
