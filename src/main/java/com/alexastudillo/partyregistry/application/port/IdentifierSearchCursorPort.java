package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.IdentifierSearchScope;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;

/** Authenticates resource-specific opaque continuation without leaking signing keys or sensitive selectors. */
public interface IdentifierSearchCursorPort {
    /** Rejects malformed, altered or mismatched tokens using a transport-neutral failure. */
    PartyIdentifierPageBoundary decode(IdentifierSearchScope scope, String token);

    String encode(IdentifierSearchScope scope, PartyIdentifierPageBoundary boundary);
}
