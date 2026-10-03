package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchScope;

/** Authenticates resource-specific navigation bound to tenant and effective exact filter/limit scope. */
public interface IdentifierSchemeCursorPort {

    /** Rejects malformed, altered, foreign-resource, or cross-scope tokens before a catalog read. */
    IdentifierSchemePageBoundary decode(String token, IdentifierSchemeSearchScope expectedScope);

    /** Encodes an exclusive tuple position, traversal direction, and complete effective scope. */
    String encode(IdentifierSchemePageBoundary boundary, IdentifierSchemeSearchScope scope);
}
