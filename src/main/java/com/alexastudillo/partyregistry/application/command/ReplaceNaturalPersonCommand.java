package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Requests the complete replacement of one natural person's details.
 */
public record ReplaceNaturalPersonCommand(
        RequestMetadata requestMetadata,
        PartyId partyId,
        PartyVersion expectedVersion,
        String givenNames,
        String familyNames,
        @Nullable String preferredName,
        @Nullable LocalDate birthDate,
        @Nullable LocalDate dateOfDeath,
        @Nullable String birthCountryCode) {

    public ReplaceNaturalPersonCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(givenNames, "givenNames");
        Objects.requireNonNull(familyNames, "familyNames");
    }

    public TenantId tenantId() {
        return requestMetadata.tenantId();
    }

    /**
     * Builds the complete replacement details represented by this command.
     *
     * @return the replacement detail values
     * @throws com.alexastudillo.partyregistry.domain.error.DomainValidationException
     *                                                                                when
     *                                                                                the
     *                                                                                replacement
     *                                                                                values
     *                                                                                violate
     *                                                                                a
     *                                                                                domain
     *                                                                                invariant
     */
    public NaturalPersonDetails replacementDetails() {
        return new NaturalPersonDetails(
                givenNames,
                familyNames,
                preferredName,
                birthDate,
                dateOfDeath,
                birthCountryCode);
    }
}
