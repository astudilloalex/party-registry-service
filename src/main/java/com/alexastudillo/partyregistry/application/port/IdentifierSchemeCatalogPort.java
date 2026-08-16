package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.IdentifierSchemeMetadata;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface IdentifierSchemeCatalogPort {
    CompletionStage<IdentifierSchemeMetadata> findUsableById(UUID schemeId);
}
