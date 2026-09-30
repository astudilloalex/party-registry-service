package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;

import java.time.LocalDate;
import java.util.Objects;

/** Changes only supplied bounds of a tenant-owned nationality. */
public record PatchNationalityCommand(
        RequestMetadata requestMetadata, PartyId partyId, NationalityId nationalityId,
        FieldUpdate<LocalDate> validFrom, FieldUpdate<LocalDate> validUntil) {

    public PatchNationalityCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(nationalityId, "nationalityId");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(validUntil, "validUntil");
        if (!validFrom.isPresent() && !validUntil.isPresent()) {
            throw new IllegalArgumentException("At least one nationality bound must be supplied");
        }
    }
}
