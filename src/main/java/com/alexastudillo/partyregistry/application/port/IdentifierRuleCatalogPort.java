package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.IdentifierSchemeMetadata;
import com.alexastudillo.partyregistry.application.NormalizedIdentifier;

public interface IdentifierRuleCatalogPort {
    NormalizedIdentifier normalizeAndValidate(IdentifierSchemeMetadata scheme, String plaintext);
}
