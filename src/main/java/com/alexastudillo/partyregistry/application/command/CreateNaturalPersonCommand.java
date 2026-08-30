package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Requests the idempotent creation of one natural person.
 */
public record CreateNaturalPersonCommand(
        RequestMetadata requestMetadata,
        String idempotencyKey,
        @Nullable String displayName,
        String givenNames,
        String familyNames,
        @Nullable String preferredName,
        @Nullable LocalDate birthDate,
        @Nullable LocalDate dateOfDeath,
        @Nullable String birthCountryCode) {

    /**
     * Stable operation key persisted with idempotency records for this command.
     */
    public static final String OPERATION = "CREATE_NATURAL_PERSON";

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    public CreateNaturalPersonCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(givenNames, "givenNames");
        Objects.requireNonNull(familyNames, "familyNames");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        if (idempotencyKey.codePointCount(0, idempotencyKey.length()) > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency key exceeds the maximum length");
        }
    }

    public TenantId tenantId() {
        return requestMetadata.tenantId();
    }
}
