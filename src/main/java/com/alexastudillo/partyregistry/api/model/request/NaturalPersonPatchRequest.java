package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonPatch;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Tracks PATCH field presence independently from nullable field values.
 */
public class NaturalPersonPatchRequest {

    private @Nullable @Size(max = 200, message = "given-names-too-long") String givenNames;
    private @Nullable @Size(max = 200, message = "family-names-too-long") String familyNames;
    private @Nullable @Size(max = 200, message = "preferred-name-too-long") String preferredName;
    private @Nullable LocalDate birthDate;
    private @Nullable LocalDate dateOfDeath;
    private @Nullable @Pattern(regexp = "^[A-Z]{2}$", message = "birth-country-code-invalid") String birthCountryCode;

    private boolean givenNamesPresent;
    private boolean familyNamesPresent;
    private boolean preferredNamePresent;
    private boolean birthDatePresent;
    private boolean dateOfDeathPresent;
    private boolean birthCountryCodePresent;

    /**
     * Records a supplied given-names property, including explicit null.
     *
     * @param givenNames supplied value
     */
    @JsonSetter
    public void setGivenNames(@Nullable String givenNames) {
        this.givenNamesPresent = true;
        this.givenNames = givenNames;
    }

    /**
     * Records a supplied family-names property, including explicit null.
     *
     * @param familyNames supplied value
     */
    @JsonSetter
    public void setFamilyNames(@Nullable String familyNames) {
        this.familyNamesPresent = true;
        this.familyNames = familyNames;
    }

    /**
     * Records a supplied preferred-name property, including explicit null.
     *
     * @param preferredName supplied value
     */
    @JsonSetter
    public void setPreferredName(@Nullable String preferredName) {
        this.preferredNamePresent = true;
        this.preferredName = preferredName;
    }

    /**
     * Records a supplied birth-date property, including explicit null.
     *
     * @param birthDate supplied value
     */
    @JsonSetter
    public void setBirthDate(@Nullable LocalDate birthDate) {
        this.birthDatePresent = true;
        this.birthDate = birthDate;
    }

    /**
     * Records a supplied date-of-death property, including explicit null.
     *
     * @param dateOfDeath supplied value
     */
    @JsonSetter
    public void setDateOfDeath(@Nullable LocalDate dateOfDeath) {
        this.dateOfDeathPresent = true;
        this.dateOfDeath = dateOfDeath;
    }

    /**
     * Records a supplied birth-country property, including explicit null.
     *
     * @param birthCountryCode supplied value
     */
    @JsonSetter
    public void setBirthCountryCode(@Nullable String birthCountryCode) {
        this.birthCountryCodePresent = true;
        this.birthCountryCode = birthCountryCode;
    }

    @JsonIgnore
    @AssertTrue(message = "patch-property-required")
    public boolean isSupportedFieldPresent() {
        return givenNamesPresent
                || familyNamesPresent
                || preferredNamePresent
                || birthDatePresent
                || dateOfDeathPresent
                || birthCountryCodePresent;
    }

    @JsonIgnore
    @AssertTrue(message = "given-names-required")
    public boolean isGivenNamesValid() {
        return !givenNamesPresent || givenNames != null && !givenNames.isBlank();
    }

    @JsonIgnore
    @AssertTrue(message = "family-names-required")
    public boolean isFamilyNamesValid() {
        return !familyNamesPresent || familyNames != null && !familyNames.isBlank();
    }

    /**
     * Converts this transport representation into the domain presence model.
     *
     * @return presence-aware natural-person patch
     */
    public NaturalPersonPatch toPatch() {
        return new NaturalPersonPatch(
                update(givenNamesPresent, givenNames),
                update(familyNamesPresent, familyNames),
                update(preferredNamePresent, preferredName),
                update(birthDatePresent, birthDate),
                update(dateOfDeathPresent, dateOfDeath),
                update(birthCountryCodePresent, birthCountryCode));
    }

    private static <T> FieldUpdate<T> update(boolean present, @Nullable T value) {
        return present ? FieldUpdate.present(value) : FieldUpdate.absent();
    }
}
