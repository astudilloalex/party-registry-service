package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreationRequest;
import com.alexastudillo.partyregistry.domain.model.TenantId;

/** Produces bounded private keyed effective-request identities without persisting canonical raw input. */
public interface PartyIdentifierFingerprintPort {

    /** Fingerprints tenant and exact original creation inputs under the versioned identifier creation namespace. */
    String fingerprint(TenantId tenantId, PartyIdentifierCreationRequest request);

    /** Fingerprints tenant, action, targets, independent version and effective exact body defaults. */
    String fingerprint(TenantId tenantId, PartyIdentifierActionRequest request);

    /** Compares decoded supported digests in constant time; never exposes either digest through an error message. */
    boolean matches(String storedFingerprint, String candidateFingerprint);
}
