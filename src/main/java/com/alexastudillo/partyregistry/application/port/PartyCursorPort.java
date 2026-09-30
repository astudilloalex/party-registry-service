package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartySearchScope;

/**
 * Protects opaque Party continuation handles through bounded, synchronous in-memory encoding and validation.
 */
public interface PartyCursorPort {

    /**
     * Verifies cursor authenticity and effective scope before exposing its position to a data query.
     *
     * @param token client-supplied opaque continuation
     * @param expectedScope trusted tenant, effective filters, and page size
     * @return the authenticated direction and exact tuple position
     * @throws ApplicationException with InvalidPartyCursor for malformed, altered, or mismatched tokens
     */
    PartyPageBoundary decode(String token, PartySearchScope expectedScope);

    /** Encodes an exact navigation position bound to the complete effective query scope. */
    String encode(PartyPageBoundary boundary, PartySearchScope scope);
}
