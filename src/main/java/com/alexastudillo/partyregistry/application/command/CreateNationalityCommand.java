package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.NationalityPeriod;
import com.alexastudillo.partyregistry.domain.model.PartyId;

import java.util.Objects;

/** Requests a tenant-owned nationality creation with an exact required replay key. */
public record CreateNationalityCommand(
        RequestMetadata requestMetadata, PartyId partyId, String countryCode,
        boolean isPrimary, NationalityPeriod period, String idempotencyKey) {

    public CreateNationalityCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(period, "period");
        if (countryCode == null || !countryCode.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("Nationality country must be normalized alpha-2");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.codePointCount(0, idempotencyKey.length()) > 128) {
            throw new IllegalArgumentException("Nationality creation key is invalid");
        }
    }
}
