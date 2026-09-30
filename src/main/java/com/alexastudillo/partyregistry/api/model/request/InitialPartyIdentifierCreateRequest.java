package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Represents the required official identifier supplied during Party creation.
 */
public record InitialPartyIdentifierCreateRequest(
        @NotBlank(message = "identifier-scheme-code-required")
        @Size(max = 64, message = "identifier-scheme-code-too-long")
        String identifierSchemeCode,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "identifier-value-required")
        @Size(max = 256, message = "identifier-value-too-long")
        String value,
        @Nullable @Size(max = 64, message = "issuer-code-too-long") String issuerCode,
        @Nullable LocalDate issuedOn,
        @Nullable LocalDate expiresOn,
        boolean isPrimary) {

    @Override
    public String toString() {
        return "InitialPartyIdentifierCreateRequest[identifierSchemeCode=" + identifierSchemeCode
                + ", value=<redacted>, issuerCode=" + issuerCode
                + ", issuedOn=" + issuedOn
                + ", expiresOn=" + expiresOn
                + ", isPrimary=" + isPrimary + "]";
    }
}
