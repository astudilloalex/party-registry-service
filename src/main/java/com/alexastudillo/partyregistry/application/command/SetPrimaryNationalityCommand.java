package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Designates an effective nationality, optionally replaying an earlier successful result. */
public record SetPrimaryNationalityCommand(
        RequestMetadata requestMetadata, PartyId partyId, NationalityId nationalityId,
        LocalDate asOfDate, Optional<String> idempotencyKey) {

    public SetPrimaryNationalityCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(nationalityId, "nationalityId");
        Objects.requireNonNull(asOfDate, "asOfDate");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        idempotencyKey.ifPresent(key -> {
            if (key.isBlank() || key.codePointCount(0, key.length()) > 128) {
                throw new IllegalArgumentException("Nationality primary replay key is invalid");
            }
        });
    }
}
