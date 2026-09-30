package com.alexastudillo.partyregistry.infrastructure.security;

import java.io.Serial;

/**
 * Reports invalid cryptographic startup configuration without exposing key details.
 */
final class CryptographicConfigurationException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 1L;

    CryptographicConfigurationException(String message) {
        super(message);
    }
}
