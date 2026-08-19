package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.PartyIdentifierStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PartyIdentifierResult(
        UUID id,
        UUID partyId,
        UUID schemeId,
        String issuerCode,
        String maskedValue,
        Boolean primary,
        PartyIdentifierStatus status,
        LocalDate issuedOn,
        LocalDate expiresOn,
        Instant verifiedAt,
        String verifiedBy,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        long version) {
    public static PartyIdentifierResult from(PartyIdentifierView view) {
        return new PartyIdentifierResult(
                view.id(),
                view.partyId(),
                view.schemeId(),
                view.issuerCode(),
                view.maskedValue(),
                view.primary(),
                view.status(),
                view.issuedOn(),
                view.expiresOn(),
                view.verifiedAt(),
                view.verifiedBy(),
                view.createdAt(),
                view.createdBy(),
                view.updatedAt(),
                view.updatedBy(),
                view.version());
    }
}
