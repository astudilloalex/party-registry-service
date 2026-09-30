package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;

/**
 * Protects validated identifier material for persistence and safe presentation.
 */
public interface IdentifierProtectionPort {

    /**
     * Encrypts {@link IdentifierProtectionRequest#completeValue()} verbatim and derives
     * the lookup hash and mask from {@link IdentifierProtectionRequest#normalizedValue()}
     * without I/O. Callers own normalization and supply the scheme-normalized value as
     * encryption plaintext for new registrations; adapters must not normalize it again.
     *
     * @param request transient identifier protection input
     * @return opaque persistence material and a safe mask
     */
    ProtectedIdentifierValue protect(IdentifierProtectionRequest request);
}
