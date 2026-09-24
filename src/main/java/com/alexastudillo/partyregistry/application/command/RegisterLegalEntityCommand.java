package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Requests idempotent legal-entity registration with one initial identifier.
 */
public record RegisterLegalEntityCommand(
        RequestMetadata requestMetadata,
        String idempotencyKey,
        @Nullable String displayName,
        String legalName,
        @Nullable String tradeName,
        @Nullable String legalFormCode,
        String incorporationCountryCode,
        @Nullable LocalDate incorporatedOn,
        @Nullable LocalDate dissolvedOn,
        InitialPartyIdentifierInput initialIdentifier) implements PartyRegistrationCommand {

    /** Stable operation key for identifier-required legal-entity registration. */
    public static final String OPERATION_NAME = "REGISTER_LEGAL_ENTITY";

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    public RegisterLegalEntityCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(legalName, "legalName");
        Objects.requireNonNull(incorporationCountryCode, "incorporationCountryCode");
        Objects.requireNonNull(initialIdentifier, "initialIdentifier");
        validateIdempotencyKey(idempotencyKey);
    }

    @Override
    public String operation() {
        return OPERATION_NAME;
    }

    @Override
    public PartyType partyType() {
        return PartyType.LEGAL_ENTITY;
    }

    private static void validateIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        if (value.codePointCount(0, value.length()) > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency key exceeds the maximum length");
        }
    }
}
