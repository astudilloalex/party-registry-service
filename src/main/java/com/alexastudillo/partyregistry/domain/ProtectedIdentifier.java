package com.alexastudillo.partyregistry.domain;

import java.util.regex.Pattern;

public record ProtectedIdentifier(
        String ciphertext,
        PositiveVersion encryptionKeyVersion,
        String normalizedValueHash,
        String maskedValue,
        PositiveVersion normalizationVersion) {
    private static final Pattern HASH_FORMAT = Pattern.compile("[0-9A-F]{64}");

    public ProtectedIdentifier {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new DomainViolation("Identifier ciphertext must not be blank");
        }
        if (encryptionKeyVersion == null) {
            throw new DomainViolation("Encryption key version is required");
        }
        if (normalizedValueHash == null || !HASH_FORMAT.matcher(normalizedValueHash).matches()) {
            throw new DomainViolation("Normalized identifier hash must be exactly 64 uppercase hexadecimal characters");
        }
        if (maskedValue == null || maskedValue.isBlank()) {
            throw new DomainViolation("Masked identifier is required");
        }
        if (normalizationVersion == null) {
            throw new DomainViolation("Normalization version is required");
        }
    }

    public static ProtectedIdentifier create(
            String ciphertext,
            int encryptionKeyVersion,
            String normalizedValueHash,
            String maskedValue,
            int normalizationVersion) {
        return new ProtectedIdentifier(
                ciphertext,
                PositiveVersion.of(encryptionKeyVersion),
                normalizedValueHash,
                maskedValue,
                PositiveVersion.of(normalizationVersion));
    }
}
