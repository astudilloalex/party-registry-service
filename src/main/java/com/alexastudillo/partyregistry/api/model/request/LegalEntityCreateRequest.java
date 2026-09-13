package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.normalization.PartyTextNormalization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Represents the strict JSON body accepted when registering a legal entity.
 */
public record LegalEntityCreateRequest(
        @Nullable @Size(max = 300, message = "display-name-too-long") String displayName,
        @NotBlank(message = "legal-name-required")
        @Size(min = 1, max = 300, message = "legal-name-invalid")
        String legalName,
        @Nullable @Size(max = 300, message = "trade-name-too-long") String tradeName,
        @Nullable @Size(max = 64, message = "legal-form-code-too-long") String legalFormCode,
        @NotNull(message = "incorporation-country-code-required")
        @Size(min = 2, max = 2, message = "incorporation-country-code-invalid")
        @Pattern(regexp = "^[A-Z]{2}$", message = "incorporation-country-code-invalid")
        String incorporationCountryCode,
        @Nullable LocalDate incorporatedOn,
        @Nullable LocalDate dissolvedOn,
        @NotNull(message = "initial-identifier-required") @Valid InitialPartyIdentifierCreateRequest initialIdentifier) {

    /**
     * Returns a normalized validation copy without changing the original idempotency input.
     */
    public LegalEntityCreateRequest normalizedForValidation() {
        return new LegalEntityCreateRequest(
                PartyTextNormalization.uppercase(displayName),
                PartyTextNormalization.uppercase(legalName),
                PartyTextNormalization.uppercase(tradeName),
                PartyTextNormalization.uppercase(legalFormCode),
                PartyTextNormalization.countryCode(incorporationCountryCode),
                incorporatedOn,
                dissolvedOn,
                initialIdentifier);
    }
}
