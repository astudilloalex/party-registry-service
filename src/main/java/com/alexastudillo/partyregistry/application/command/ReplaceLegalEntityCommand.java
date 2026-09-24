package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/** Carries a complete legal-detail replacement without prematurely validating semantic state. */
public record ReplaceLegalEntityCommand(
        RequestMetadata requestMetadata,
        PartyId partyId,
        PartyVersion expectedVersion,
        String legalName,
        @Nullable String tradeName,
        @Nullable String legalFormCode,
        String incorporationCountryCode,
        @Nullable LocalDate incorporatedOn,
        @Nullable LocalDate dissolvedOn) {

    public ReplaceLegalEntityCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(legalName, "legalName");
        Objects.requireNonNull(incorporationCountryCode, "incorporationCountryCode");
    }

    public TenantId tenantId() {
        return requestMetadata.tenantId();
    }

    /**
     * Constructs canonical replacement details after the use case has checked the expected version.
     *
     * @return canonical replacement values
     * @throws com.alexastudillo.partyregistry.domain.error.DomainValidationException
     *         when the replacement violates legal-detail invariants
     */
    public LegalEntityDetails replacementDetails() {
        return LegalEntityDetails.forWrite(legalName, tradeName, legalFormCode,
                incorporationCountryCode, incorporatedOn, dissolvedOn);
    }
}
