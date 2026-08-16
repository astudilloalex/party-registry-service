package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.ProtectedIdentifier;

public record ProtectedIdentifierData(
        String ciphertext,
        String normalizedValueHash,
        String maskedValue,
        int encryptionKeyVersion,
        int normalizationVersion) {
    public ProtectedIdentifierData(
            String ciphertext, String normalizedValueHash, String maskedValue, int encryptionKeyVersion) {
        this(ciphertext, normalizedValueHash, maskedValue, encryptionKeyVersion, 1);
    }

    public ProtectedIdentifierData {
        ProtectedIdentifier.create(
                ciphertext, encryptionKeyVersion, normalizedValueHash, maskedValue, normalizationVersion);
    }
}
