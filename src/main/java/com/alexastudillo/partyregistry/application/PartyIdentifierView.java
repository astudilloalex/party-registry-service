package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.PartyIdentifierStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record PartyIdentifierView(
        UUID id,
        UUID tenantId,
        UUID partyId,
        UUID schemeId,
        String issuerCode,
        String ciphertext,
        String normalizedValueHash,
        String maskedValue,
        int encryptionKeyVersion,
        Integer normalizationVersion,
        Boolean primary,
        PartyIdentifierStatus status,
        LocalDate issuedOn,
        LocalDate expiresOn,
        Instant verifiedAt,
        String verifiedBy,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        long version) {
    public PartyIdentifierView(
            UUID id,
            UUID tenantId,
            UUID partyId,
            UUID schemeId,
            String ciphertext,
            String maskedValue,
            int encryptionKeyVersion,
            long version) {
        this(id, tenantId, partyId, schemeId, null, ciphertext, null, maskedValue, encryptionKeyVersion, null,
                null, null, null, null, null, null, null, null, null, null, version);
    }

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
