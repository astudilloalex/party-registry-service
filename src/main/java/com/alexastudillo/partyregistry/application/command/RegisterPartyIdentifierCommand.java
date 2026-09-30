package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/**
 * Requests registration of an additional identifier for an existing Party.
 */
public record RegisterPartyIdentifierCommand(
        RequestMetadata requestMetadata,
        String idempotencyKey,
        PartyId partyId,
        InitialPartyIdentifierInput identifier) {

    /** Stable operation key for additional PartyIdentifier registration. */
    public static final String OPERATION = "REGISTER_PARTY_IDENTIFIER";

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    public RegisterPartyIdentifierCommand {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(identifier, "identifier");
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
