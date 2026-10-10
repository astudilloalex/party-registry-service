package com.alexastudillo.partyregistry.infrastructure.configuration;

import com.alexastudillo.partyregistry.application.port.IdentifierSearchCursorPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchReadPort;
import com.alexastudillo.partyregistry.application.usecase.SearchPartiesByIdentifierUseCase;
import com.alexastudillo.partyregistry.application.usecase.SearchPartiesByIdentifierPrefixUseCase;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelectorPolicy;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/** Composes framework-independent search use cases through their read/cursor ports at the outer CDI boundary. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class IdentifierSearchUseCaseProducer {
    private final IdentifierSearchSelectorPolicy selectors = new IdentifierSearchSelectorPolicy(new IdentifierRuleCatalog());

    @Produces
    @Singleton
    public SearchPartiesByIdentifierUseCase exactSearch(IdentifierSearchReadPort reads) {
        return new SearchPartiesByIdentifierUseCase(reads, selectors);
    }

    @Produces
    @Singleton
    public SearchPartiesByIdentifierPrefixUseCase prefixSearch(IdentifierSearchReadPort reads, IdentifierSearchCursorPort cursors) {
        return new SearchPartiesByIdentifierPrefixUseCase(reads, cursors, selectors);
    }
}
