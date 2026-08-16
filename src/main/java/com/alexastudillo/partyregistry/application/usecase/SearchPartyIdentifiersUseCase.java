package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.IdentifierSearchQuery;
import com.alexastudillo.partyregistry.application.PageResult;
import com.alexastudillo.partyregistry.application.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.IdentifierQueryPort;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class SearchPartyIdentifiersUseCase {
    private final IdentifierQueryPort identifiers;

    public SearchPartyIdentifiersUseCase(IdentifierQueryPort identifiers) {
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
    }

    public CompletionStage<PageResult<PartyIdentifierResult>> execute(
            RequestContext context, IdentifierSearchQuery query) {
        UseCaseSupport.required(query, "query");
        return identifiers.search(
                        context.tenantId(), query.partyId(), query.schemeId(), query.status(), query.primary(), query.page())
                .thenApply(page -> new PageResult<>(
                        page.items().stream().map(PartyIdentifierResult::from).toList(),
                        page.page(), page.size(), page.total()));
    }
}
