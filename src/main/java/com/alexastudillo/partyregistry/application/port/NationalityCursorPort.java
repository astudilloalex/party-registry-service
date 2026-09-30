package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.NationalityPageBoundary;
import com.alexastudillo.partyregistry.application.model.NationalitySearchScope;

/** Signs and verifies nationality-only scoped, bounded continuation positions. */
public interface NationalityCursorPort {

    /** Authenticates and scopes a token before any nationality data read. */
    NationalityPageBoundary decode(String token, NationalitySearchScope expectedScope);

    /** Encodes a tuple position and the complete effective search scope. */
    String encode(NationalityPageBoundary boundary, NationalitySearchScope scope);
}
