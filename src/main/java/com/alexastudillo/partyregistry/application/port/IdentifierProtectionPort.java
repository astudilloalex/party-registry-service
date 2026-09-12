package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;

/**
 * Protects validated identifier material for persistence and safe presentation.
 */
public interface IdentifierProtectionPort {

    /**
     * Encrypts, fingerprints, and masks one validated identifier without I/O.
     *
     * @param request transient identifier protection input
     * @return opaque persistence material and a safe mask
     */
    ProtectedIdentifierValue protect(IdentifierProtectionRequest request);
}
