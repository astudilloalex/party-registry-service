package com.alexastudillo.partyregistry.application.command;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Carries one complete official identifier into an application operation.
 */
public record InitialPartyIdentifierInput(
        String identifierSchemeCode,
        String value,
        @Nullable String issuerCode,
        @Nullable LocalDate issuedOn,
        @Nullable LocalDate expiresOn,
        boolean isPrimary) {

    public InitialPartyIdentifierInput {
        if (identifierSchemeCode == null || identifierSchemeCode.isBlank()) {
            throw new IllegalArgumentException("Identifier scheme code is required");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Identifier value is required");
        }
    }

    @Override
    public String toString() {
        return "InitialPartyIdentifierInput[identifierSchemeCode=" + identifierSchemeCode
                + ", value=<redacted>, issuerCode=" + issuerCode
                + ", issuedOn=" + issuedOn
                + ", expiresOn=" + expiresOn
                + ", isPrimary=" + isPrimary + "]";
    }
}
