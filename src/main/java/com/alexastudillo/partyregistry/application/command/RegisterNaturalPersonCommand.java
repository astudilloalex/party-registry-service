package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Requests idempotent natural-person registration with one initial identifier.
 */
public record RegisterNaturalPersonCommand(
        RequestMetadata requestMetadata,
        String idempotencyKey,
        @Nullable String displayName,
        String givenNames,
        String familyNames,
        @Nullable String preferredName,
        @Nullable LocalDate birthDate,
        @Nullable LocalDate dateOfDeath,
        @Nullable String birthCountryCode,
        InitialPartyIdentifierInput initialIdentifier) implements PartyRegistrationCommand {

    /** Stable operation key for identifier-required natural-person registration. */
    public static final String OPERATION_NAME = "REGISTER_NATURAL_PERSON";

    /**
     * Legacy identifier-free operation key retained only for persisted-row
     * detection.
     */
    public static final String LEGACY_OPERATION = "CREATE_NATURAL_PERSON";

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    public RegisterNaturalPersonCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(givenNames, "givenNames");
        Objects.requireNonNull(familyNames, "familyNames");
        Objects.requireNonNull(initialIdentifier, "initialIdentifier");
        validateIdempotencyKey(idempotencyKey);
    }

    @Override
    public String operation() {
        return OPERATION_NAME;
    }

    @Override
    public PartyType partyType() {
        return PartyType.NATURAL_PERSON;
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
