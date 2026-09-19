package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.api.serialization.LegalDetailDateDeserializer;
import com.alexastudillo.partyregistry.api.serialization.LegalDetailStringDeserializer;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.LegalEntityPatch;
import com.alexastudillo.partyregistry.domain.normalization.PartyTextNormalization;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/** Tracks legal PATCH field presence separately from nullable values without exposing helper JSON properties. */
@ValidLegalEntityPatch
public class LegalEntityPatchRequest {

    @JsonDeserialize(using = LegalDetailStringDeserializer.class)
    private @Nullable @Size(max = 300, message = "legal-name-invalid") String legalName;
    @JsonDeserialize(using = LegalDetailStringDeserializer.class)
    private @Nullable @Size(max = 300, message = "trade-name-too-long") String tradeName;
    @JsonDeserialize(using = LegalDetailStringDeserializer.class)
    private @Nullable @Size(max = 64, message = "legal-form-code-too-long") String legalFormCode;
    @JsonDeserialize(using = LegalDetailStringDeserializer.class)
    private @Nullable @Pattern(regexp = "^[A-Z]{2}$", message = "incorporation-country-code-invalid") String incorporationCountryCode;
    @JsonDeserialize(using = LegalDetailDateDeserializer.class)
    private @Nullable LocalDate incorporatedOn;
    @JsonDeserialize(using = LegalDetailDateDeserializer.class)
    private @Nullable LocalDate dissolvedOn;

    private boolean legalNamePresent;
    private boolean tradeNamePresent;
    private boolean legalFormCodePresent;
    private boolean incorporationCountryCodePresent;
    private boolean incorporatedOnPresent;
    private boolean dissolvedOnPresent;

    /** Records a supplied legal name, including null for subsequent required-field validation. */
    @JsonSetter
    public void setLegalName(@Nullable String value) {
        legalNamePresent = true;
        legalName = value;
    }

    /** Records a supplied trade name, including an explicit clear. */
    @JsonSetter
    public void setTradeName(@Nullable String value) {
        tradeNamePresent = true;
        tradeName = value;
    }

    /** Records a supplied legal-form code, including an explicit clear. */
    @JsonSetter
    public void setLegalFormCode(@Nullable String value) {
        legalFormCodePresent = true;
        legalFormCode = value;
    }

    /** Records a supplied country, including null for subsequent required-field validation. */
    @JsonSetter
    public void setIncorporationCountryCode(@Nullable String value) {
        incorporationCountryCodePresent = true;
        incorporationCountryCode = value;
    }

    /** Records a supplied incorporation date, including an explicit clear. */
    @JsonSetter
    public void setIncorporatedOn(@Nullable LocalDate value) {
        incorporatedOnPresent = true;
        incorporatedOn = value;
    }

    /** Records a supplied dissolution date, including an explicit clear. */
    @JsonSetter
    public void setDissolvedOn(@Nullable LocalDate value) {
        dissolvedOnPresent = true;
        dissolvedOn = value;
    }

    public boolean empty() {
        return !(legalNamePresent || tradeNamePresent || legalFormCodePresent
                || incorporationCountryCodePresent || incorporatedOnPresent || dissolvedOnPresent);
    }

    public boolean legalNamePresent() {
        return legalNamePresent;
    }

    public @Nullable String legalName() {
        return legalName;
    }

    public boolean incorporationCountryCodePresent() {
        return incorporationCountryCodePresent;
    }

    public @Nullable String incorporationCountryCode() {
        return incorporationCountryCode;
    }

    /** Returns a validation copy preserving omission and explicit null for each property. */
    public LegalEntityPatchRequest normalizedForValidation() {
        LegalEntityPatchRequest copy = new LegalEntityPatchRequest();
        if (legalNamePresent) {
            copy.setLegalName(PartyTextNormalization.uppercase(legalName));
        }
        if (tradeNamePresent) {
            copy.setTradeName(PartyTextNormalization.uppercase(tradeName));
        }
        if (legalFormCodePresent) {
            copy.setLegalFormCode(PartyTextNormalization.uppercase(legalFormCode));
        }
        if (incorporationCountryCodePresent) {
            copy.setIncorporationCountryCode(PartyTextNormalization.countryCode(incorporationCountryCode));
        }
        if (incorporatedOnPresent) {
            copy.setIncorporatedOn(incorporatedOn);
        }
        if (dissolvedOnPresent) {
            copy.setDissolvedOn(dissolvedOn);
        }
        return copy;
    }

    /** Maps original supplied values into the transport-neutral Domain patch representation. */
    public LegalEntityPatch toPatch() {
        return new LegalEntityPatch(update(legalNamePresent, legalName), update(tradeNamePresent, tradeName),
                update(legalFormCodePresent, legalFormCode), update(incorporationCountryCodePresent, incorporationCountryCode),
                update(incorporatedOnPresent, incorporatedOn), update(dissolvedOnPresent, dissolvedOn));
    }

    private static <T> FieldUpdate<T> update(boolean present, @Nullable T value) {
        return present ? FieldUpdate.present(value) : FieldUpdate.absent();
    }
}
