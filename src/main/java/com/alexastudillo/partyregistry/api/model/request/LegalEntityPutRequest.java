package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.api.serialization.LegalDetailDateDeserializer;
import com.alexastudillo.partyregistry.api.serialization.LegalDetailStringDeserializer;
import com.alexastudillo.partyregistry.domain.normalization.PartyTextNormalization;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/** Represents a complete legal-detail replacement with normalized structural validation. */
public record LegalEntityPutRequest(
        @JsonDeserialize(using = LegalDetailStringDeserializer.class)
        @NotBlank(message = "legal-name-required")
        @Size(min = 1, max = 300, message = "legal-name-invalid") String legalName,
        @JsonDeserialize(using = LegalDetailStringDeserializer.class)
        @Nullable @Size(max = 300, message = "trade-name-too-long") String tradeName,
        @JsonDeserialize(using = LegalDetailStringDeserializer.class)
        @Nullable @Size(max = 64, message = "legal-form-code-too-long") String legalFormCode,
        @JsonDeserialize(using = LegalDetailStringDeserializer.class)
        @NotNull(message = "incorporation-country-code-required")
        @Pattern(regexp = "^[A-Z]{2}$", message = "incorporation-country-code-invalid") String incorporationCountryCode,
        @JsonDeserialize(using = LegalDetailDateDeserializer.class) @Nullable LocalDate incorporatedOn,
        @JsonDeserialize(using = LegalDetailDateDeserializer.class) @Nullable LocalDate dissolvedOn) {

    /** Returns a canonical validation copy while retaining original command inputs. */
    public LegalEntityPutRequest normalizedForValidation() {
        return new LegalEntityPutRequest(PartyTextNormalization.uppercase(legalName),
                PartyTextNormalization.uppercase(tradeName), PartyTextNormalization.uppercase(legalFormCode),
                PartyTextNormalization.countryCode(incorporationCountryCode), incorporatedOn, dissolvedOn);
    }
}
