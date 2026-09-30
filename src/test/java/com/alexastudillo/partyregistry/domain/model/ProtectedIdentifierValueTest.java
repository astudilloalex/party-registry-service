package com.alexastudillo.partyregistry.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies protected identifier diagnostics cannot disclose persisted cryptographic material.
 */
class ProtectedIdentifierValueTest {

    @Test
    void redactsCiphertextHashAndKeyVersionFromDiagnostics() {
        String ciphertext = "encrypted-sensitive-value";
        String lookupHash = "ab".repeat(32);
        ProtectedIdentifierValue value = new ProtectedIdentifierValue(
                ciphertext,
                17,
                lookupHash,
                "******56",
                new IdentifierRuleVersion(1));

        String diagnostic = value.toString();

        assertFalse(diagnostic.contains(ciphertext));
        assertFalse(diagnostic.contains(lookupHash));
        assertFalse(diagnostic.contains("17"));
        assertTrue(diagnostic.contains("******56"));
        assertTrue(diagnostic.contains("<redacted>"));
    }
}
