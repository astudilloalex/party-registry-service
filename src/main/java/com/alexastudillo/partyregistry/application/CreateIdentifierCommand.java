package com.alexastudillo.partyregistry.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record CreateIdentifierCommand(
        UUID partyId,
        UUID schemeId,
        String plaintext,
        String issuerCode,
        boolean primary,
        LocalDate issuedOn,
        LocalDate expiresOn) {
    public CreateIdentifierCommand {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(schemeId, "schemeId");
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("plaintext must not be blank");
        }
    }
}
