package com.alexastudillo.partyregistry.application;

import java.util.Objects;
import java.util.UUID;

public record PartyIdentifierView(
        UUID id,
        UUID tenantId,
        UUID partyId,
        UUID schemeId,
        String ciphertext,
        String maskedValue,
        int encryptionKeyVersion,
        long version) {
    public PartyIdentifierView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(schemeId, "schemeId");
        if (ciphertext == null || ciphertext.isBlank() || maskedValue == null || maskedValue.isBlank()
                || encryptionKeyVersion < 1 || version < 0) {
            throw new IllegalArgumentException("Invalid Party Identifier view");
        }
    }
}
