package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchScope;

/** Authenticates identifier-only continuation and its exact tenant/Party/filter/limit scope at a technical boundary. */
public interface PartyIdentifierCursorPort {

    /** Encodes an opaque bounded token retaining the complete boundary precision and direction. */
    String encode(PartyIdentifierSearchScope scope, PartyIdentifierPageBoundary boundary);

    /** Authenticates before returning a boundary; malformed or mismatched tokens emit a neutral cursor failure. */
    PartyIdentifierPageBoundary decode(PartyIdentifierSearchScope scope, String token);
}
