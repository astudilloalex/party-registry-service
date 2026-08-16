package com.alexastudillo.partyregistry.application;

public record DecryptedIdentifierResult(String plaintext, boolean noStore) {
    public DecryptedIdentifierResult {
        if (plaintext == null || plaintext.isBlank() || !noStore) {
            throw new IllegalArgumentException("A decrypted identifier result must be nonblank and no-store");
        }
    }
}
