package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

/**
 * Holds only opaque persistence material and the safe mask of a protected identifier.
 */
public record ProtectedIdentifierValue(
        String encryptedValue,
        int encryptionKeyVersion,
        String normalizedValueHash,
        String maskedValue,
        IdentifierRuleVersion normalizationVersion) {

    private static final int MAX_MASKED_VALUE_LENGTH = 64;
    private static final int MAX_PERSISTED_KEY_VERSION = Short.MAX_VALUE;

    public ProtectedIdentifierValue {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new DomainValidationException(
                    DomainViolation.ENCRYPTED_IDENTIFIER_VALUE_REQUIRED,
                    "Encrypted identifier value is required");
        }
        if (encryptionKeyVersion <= 0 || encryptionKeyVersion > MAX_PERSISTED_KEY_VERSION) {
            throw new DomainValidationException(
                    DomainViolation.ENCRYPTION_KEY_VERSION_INVALID,
                    "Encryption key version must fit a positive small integer");
        }
        if (normalizedValueHash == null || normalizedValueHash.isBlank()) {
            throw new DomainValidationException(
                    DomainViolation.NORMALIZED_IDENTIFIER_HASH_REQUIRED,
                    "Normalized identifier hash is required");
        }
        if (!normalizedValueHash.matches("^[0-9a-fA-F]{64}$")) {
            throw new DomainValidationException(
                    DomainViolation.NORMALIZED_IDENTIFIER_HASH_INVALID,
                    "Normalized identifier hash must contain 64 hexadecimal characters");
        }
        if (maskedValue == null || maskedValue.isBlank()) {
            throw new DomainValidationException(
                    DomainViolation.MASKED_IDENTIFIER_VALUE_REQUIRED,
                    "Masked identifier value is required");
        }
        if (maskedValue.codePointCount(0, maskedValue.length()) > MAX_MASKED_VALUE_LENGTH) {
            throw new DomainValidationException(
                    DomainViolation.MASKED_IDENTIFIER_VALUE_TOO_LONG,
                    "Masked identifier value exceeds the maximum length");
        }
        if (normalizationVersion == null) {
            throw new DomainValidationException(
                    DomainViolation.NORMALIZATION_VERSION_INVALID,
                    "Normalization version is required");
        }
    }

    @Override
    public String toString() {
        return "ProtectedIdentifierValue[encryptedValue=<redacted>, encryptionKeyVersion=<redacted>"
                + ", normalizedValueHash=<redacted>, maskedValue=" + maskedValue
                + ", normalizationVersion=" + normalizationVersion + "]";
    }
}
